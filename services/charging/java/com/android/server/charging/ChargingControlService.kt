/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.charging

import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.os.IBinder
import android.os.IServiceCallback
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.os.ServiceManager
import android.provider.Settings
import android.util.Slog
import com.android.internal.os.BackgroundThread
import com.android.server.SystemService
import custom.hardware.health.charging.ChargeLimitTarget
import custom.hardware.health.charging.IChargeLimit
import custom.hardware.health.charging.IChargeLimitCollector
import custom.hardware.health.charging.IChargeLimitFlow
import custom.health.charging.IChargingControlManager

private const val TAG = "ChargingControlService"
private val HAL_INSTANCE = IChargeLimit.DESCRIPTOR + "/default"

/**
 * Publishes the charge limit the user set as a flow of targets, and attaches the flow to the charge
 * limit HAL each time the HAL registers. The HAL sets the charger so the battery follows the
 * target.
 */
class ChargingControlService(context: Context) : SystemService(context) {

    private val halDeclared = ServiceManager.isDeclared(HAL_INSTANCE)

    /** Runs every emission, so each collector receives the targets in the order they were set. */
    private val handler = BackgroundThread.getHandler()

    private val collectors = RemoteCallbackList<IChargeLimitCollector>()

    /** Only read and written on [handler]. */
    private var target: ChargeLimitTarget? = null

    private val flow =
        object : IChargeLimitFlow.Stub() {
            override fun collect(collector: IChargeLimitCollector) {
                handler.post { if (collectors.register(collector)) collector.send(target) }
            }

            override fun getInterfaceVersion() = IChargeLimitFlow.VERSION

            override fun getInterfaceHash(): String = IChargeLimitFlow.HASH
        }

    override fun onStart() {
        publishBinderService(
            IChargingControlManager.DESCRIPTOR,
            object : IChargingControlManager.Stub() {
                override fun isSupported() = halDeclared
            },
        )
    }

    override fun onBootPhase(phase: Int) {
        if (phase != PHASE_SYSTEM_SERVICES_READY || !halDeclared) return
        handler.post(::publishSetting)
        ServiceManager.registerForNotifications(
            HAL_INSTANCE,
            object : IServiceCallback.Stub() {
                override fun onRegistration(name: String, binder: IBinder) =
                    attach(IChargeLimit.Stub.asInterface(binder))
            },
        )
    }

    private fun publishSetting() {
        val resolver = context.contentResolver
        resolver.registerContentObserver(
            Settings.System.getUriFor(IChargingControlManager.LIMIT_SETTING),
            false,
            object : ContentObserver(handler) {
                override fun onChange(selfChange: Boolean) = publish(resolver.readTarget())
            },
        )
        publish(resolver.readTarget())
    }

    private fun publish(newTarget: ChargeLimitTarget?) {
        Slog.i(TAG, "Charge limit target: ${newTarget?.levelPercent ?: "none"}")
        target = newTarget
        collectors.broadcast { it.send(newTarget) }
    }

    private fun attach(hal: IChargeLimit) {
        try {
            hal.attach(flow)
        } catch (e: RemoteException) {
            // The HAL died after it registered. The flow is attached again once the HAL registers
            // anew.
            Slog.w(TAG, "Could not attach the charge limit to the HAL", e)
        }
    }
}

/**
 * Reads the charge limit setting. An absent or malformed setting reads as
 * [IChargingControlManager.NO_LIMIT]. That value, like every value outside the levels allowed by a
 * target, means no limit.
 */
private fun ContentResolver.readTarget(): ChargeLimitTarget? =
    Settings.System.getInt(
            this,
            IChargingControlManager.LIMIT_SETTING,
            IChargingControlManager.NO_LIMIT,
        )
        .takeIf { it in ChargeLimitTarget.MIN_LEVEL_PERCENT..ChargeLimitTarget.MAX_LEVEL_PERCENT }
        ?.let { level -> ChargeLimitTarget().apply { levelPercent = level } }

private fun IChargeLimitCollector.send(target: ChargeLimitTarget?) {
    try {
        emit(target)
    } catch (e: RemoteException) {
        // The HAL of the collector died. The callback list removes dead collectors by itself.
        Slog.w(TAG, "Could not emit the charge limit", e)
    }
}
