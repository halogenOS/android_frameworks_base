/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.charging

import android.os.Handler
import android.os.IBinder
import android.os.RemoteException
import android.util.Slog
import custom.hardware.health.charging.ChargeMode
import custom.hardware.health.charging.ChargeModeReport
import custom.hardware.health.charging.IChargeLimit
import custom.hardware.health.charging.IChargeModeCollector
import custom.health.charging.ChargeLimitMode
import custom.health.charging.ChargeLimitState
import custom.health.charging.IChargeLimitStateCollector
import custom.health.charging.IChargeLimitStateFlow

private const val TAG = "ChargeLimitStates"

/**
 * Collects the modes the charge limit HAL sets the charger to, and offers them to apps as the
 * charge limit state. Everything runs on [handler], so each collector receives the states in the
 * order the HAL reported them.
 */
internal class ChargeLimitStates(private val handler: Handler) {

    /** Null means unknown. */
    private val states =
        BinderStateFlow<IChargeLimitStateCollector, ChargeLimitState?>(handler, null) { emit(it) }

    /** The charge limit state offered to apps. */
    val flow =
        object : IChargeLimitStateFlow.Stub() {
            override fun collect(collector: IChargeLimitStateCollector) = states.collect(collector)

            override fun cancel(collector: IChargeLimitStateCollector) = states.cancel(collector)
        }

    /** Only read and written on [handler]. The collector created for the latest HAL. */
    private var latestCollector: ModeCollector? = null

    /**
     * Collects the modes of [hal], in place of the modes of every earlier HAL.
     *
     * @throws RemoteException when [hal] died.
     */
    fun collectFrom(hal: IChargeLimit) {
        val collector = ModeCollector()
        handler.post {
            latestCollector = collector
            // The modes of an earlier HAL no longer apply.
            states.publish(null)
        }
        hal.asBinder().linkToDeath(collector, 0)
        hal.modeFlow.collect(collector)
    }

    /**
     * Collects the modes of one HAL. Only the collector created for the latest HAL changes
     * [states].
     */
    private inner class ModeCollector : IChargeModeCollector.Stub(), IBinder.DeathRecipient {
        override fun emit(report: ChargeModeReport?) {
            handler.post { if (latestCollector === this) states.publish(report?.toState()) }
        }

        override fun binderDied() {
            handler.post { if (latestCollector === this) states.publish(null) }
        }

        override fun getInterfaceVersion() = IChargeModeCollector.VERSION

        override fun getInterfaceHash(): String = IChargeModeCollector.HASH
    }
}

/** The charge limit state in this report, or null for a mode this service does not know. */
private fun ChargeModeReport.toState(): ChargeLimitState? {
    val limitMode =
        when (mode) {
            ChargeMode.CHARGE -> ChargeLimitMode.CHARGE
            ChargeMode.HOLD -> ChargeLimitMode.HOLD
            ChargeMode.DRAIN -> ChargeLimitMode.DRAIN
            else -> {
                // The HAL emits only changes, so each unknown mode is logged once.
                Slog.w(TAG, "Unknown charge mode $mode, reading the state as unknown")
                return null
            }
        }
    return ChargeLimitState().also {
        it.mode = limitMode
        it.chargerPresent = chargerPresent
    }
}
