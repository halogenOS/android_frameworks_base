/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.charging

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.Binder
import android.os.IBinder
import android.os.IServiceCallback
import android.os.RemoteException
import android.os.ServiceManager
import android.provider.Settings
import android.util.Slog
import com.android.internal.os.BackgroundThread
import com.android.server.SystemService
import custom.hardware.health.charging.ChargeLimitTarget
import custom.hardware.health.charging.IChargeLimit
import custom.health.charging.ChargeLimit
import custom.health.charging.IChargingControlManager

private const val TAG = "ChargingControlService"
private val HAL_INSTANCE = IChargeLimit.DESCRIPTOR + "/default"

/** The version of the HAL interface that added IChargeLimit.getModeFlow. */
private const val MODE_FLOW_VERSION = 3

/** Key of the Settings.System entry that contains the charge limit in percent. */
private const val LIMIT_SETTING = "charging_limit"

/** Value of [LIMIT_SETTING] for no limit, the largest int. */
private const val NO_LIMIT = Int.MAX_VALUE

/**
 * Key of the Settings.System entry that contains the variation above the charge limit in percent.
 */
private const val VARIATION_SETTING = "charging_limit_variation"

/** Value of [VARIATION_SETTING] when it is absent: the battery stays at the limit. */
private const val NO_VARIATION = 0

private val TARGET_SETTINGS = listOf(LIMIT_SETTING, VARIATION_SETTING)

/** Levels a charge limit can set, as the HAL interface defines them. */
private val LEVELS = ChargeLimitTarget.MIN_LEVEL_PERCENT..ChargeLimitTarget.MAX_LEVEL_PERCENT

/** A full battery in percent, the highest top a band can have. */
private const val FULL_PERCENT = 100

/**
 * Stores the charge limit the user set, publishes it as a flow of targets, and attaches the flow to
 * the charge limit HAL each time the HAL registers. The HAL sets the charger so the battery follows
 * the target. This service alone reads and writes the settings that contain the limit. It offers
 * the charge limit to apps through [StoredChargeLimit], and the charge limit state through
 * [ChargeLimitStates].
 */
class ChargingControlService(context: Context) : SystemService(context) {

    private val halDeclared = ServiceManager.isDeclared(HAL_INSTANCE)

    /** Runs every emission, so each collector receives the targets in the order they were set. */
    private val handler = BackgroundThread.getHandler()

    private val storedLimit = StoredChargeLimit(handler, context.contentResolver::readLimit)

    private val states = ChargeLimitStates(handler)

    override fun onStart() {
        publishBinderService(
            IChargingControlManager.DESCRIPTOR,
            object : IChargingControlManager.Stub() {
                override fun isSupported() = halDeclared

                override fun setLimit(limit: ChargeLimit?): Boolean {
                    context.enforceCallingOrSelfPermission(
                        Manifest.permission.WRITE_SECURE_SETTINGS,
                        "Set the charge limit",
                    )
                    return (limit == null || limit.isValid) &&
                        Binder.withCleanCallingIdentity<Boolean> {
                            context.contentResolver.writeLimit(limit)
                        }
                }

                override fun getLimitSettingFlow() = storedLimit.flow

                override fun getLimitStateFlow() = states.flow
            },
        )
    }

    override fun onBootPhase(phase: Int) {
        if (phase != PHASE_SYSTEM_SERVICES_READY) return
        handler.post(::publishSettings)
        if (!halDeclared) return
        ServiceManager.registerForNotifications(
            HAL_INSTANCE,
            object : IServiceCallback.Stub() {
                override fun onRegistration(name: String, binder: IBinder) =
                    attach(IChargeLimit.Stub.asInterface(binder))
            },
        )
    }

    private fun publishSettings() {
        val resolver = context.contentResolver
        val observer =
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = publishChange()
            }
        for (setting in TARGET_SETTINGS) {
            resolver.registerContentObserver(Settings.System.getUriFor(setting), false, observer)
        }
        publishChange()
    }

    private fun publishChange() {
        Slog.i(TAG, "Charge limit: " + (context.contentResolver.readLimit()?.band ?: "none"))
        storedLimit.publishChange()
    }

    private fun attach(hal: IChargeLimit) {
        try {
            hal.attach(storedLimit.targetFlow)
        } catch (e: RemoteException) {
            // The HAL died after it registered. The flow is attached again once the HAL registers
            // anew.
            Slog.w(TAG, "Could not attach the charge limit to the HAL", e)
        }
        try {
            if (hal.interfaceVersion >= MODE_FLOW_VERSION) states.collectFrom(hal)
        } catch (e: RemoteException) {
            // The HAL died after it registered. Its modes are collected again once the HAL
            // registers anew.
            Slog.w(TAG, "Could not collect the charge modes of the HAL", e)
        }
    }
}

/**
 * Reads the charge limit settings. A limit that is absent, malformed or outside [LEVELS] means no
 * limit. A variation that is absent, malformed or does not fit between the limit and a full battery
 * reads as [NO_VARIATION].
 */
private fun ContentResolver.readLimit(): ChargeLimit? {
    val level =
        Settings.System.getInt(this, LIMIT_SETTING, NO_LIMIT).takeIf { it in LEVELS } ?: return null
    val variation =
        Settings.System.getInt(this, VARIATION_SETTING, NO_VARIATION).takeIf {
            it in variationsAbove(level)
        }
    return ChargeLimit().apply {
        levelPercent = level
        variationPercent = variation ?: NO_VARIATION
    }
}

/**
 * Writes [limit] to the settings, or no limit when it is null. Returns whether both writes
 * succeeded.
 */
private fun ContentResolver.writeLimit(limit: ChargeLimit?): Boolean =
    Settings.System.putInt(this, VARIATION_SETTING, limit?.variationPercent ?: NO_VARIATION) &&
        Settings.System.putInt(this, LIMIT_SETTING, limit?.levelPercent ?: NO_LIMIT)

private fun variationsAbove(levelPercent: Int) = 0..FULL_PERCENT - levelPercent

private val ChargeLimit.isValid
    get() = levelPercent in LEVELS && variationPercent in variationsAbove(levelPercent)

/** The levels the battery stays between. */
private val ChargeLimit.band
    get() = levelPercent..levelPercent + variationPercent
