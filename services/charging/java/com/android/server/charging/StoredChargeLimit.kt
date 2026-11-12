/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.charging

import android.os.Handler
import custom.hardware.health.charging.ChargeLimitTarget
import custom.hardware.health.charging.IChargeLimitCollector
import custom.hardware.health.charging.IChargeLimitFlow
import custom.health.charging.ChargeLimit
import custom.health.charging.IChargeLimitSettingCollector
import custom.health.charging.IChargeLimitSettingFlow

/**
 * Offers the stored charge limit to apps as a flow, and the target derived from it to the charge
 * limit HAL as a flow. Both flows emit the limit returned by [read] at the time of each emission,
 * so a collector that starts after a change receives the changed limit first.
 */
internal class StoredChargeLimit(handler: Handler, read: () -> ChargeLimit?) {

    private val limits =
        BinderStateFlow<IChargeLimitSettingCollector, ChargeLimit?>(handler, read) { emit(it) }

    private val targets =
        BinderStateFlow<IChargeLimitCollector, ChargeLimit?>(handler, read) { emit(it?.toTarget()) }

    /** The stored charge limit offered to apps. */
    val flow =
        object : IChargeLimitSettingFlow.Stub() {
            override fun collect(collector: IChargeLimitSettingCollector) =
                limits.collect(collector)

            override fun cancel(collector: IChargeLimitSettingCollector) = limits.cancel(collector)
        }

    /** The targets offered to the charge limit HAL. */
    val targetFlow =
        object : IChargeLimitFlow.Stub() {
            override fun collect(collector: IChargeLimitCollector) = targets.collect(collector)

            override fun getInterfaceVersion() = IChargeLimitFlow.VERSION

            override fun getInterfaceHash(): String = IChargeLimitFlow.HASH
        }

    /** Emits the stored limit to every collector after it changed. Runs on the handler. */
    fun publishChange() {
        limits.publishChange()
        targets.publishChange()
    }
}

private fun ChargeLimit.toTarget() =
    ChargeLimitTarget().also {
        it.levelPercent = levelPercent
        it.variationPercent = variationPercent
    }
