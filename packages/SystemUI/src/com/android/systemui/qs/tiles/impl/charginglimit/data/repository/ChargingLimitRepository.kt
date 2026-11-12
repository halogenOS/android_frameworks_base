/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.data.repository

import android.os.RemoteException
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.ChargingLimit
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.Percent
import com.android.systemui.util.settings.SettingsSingleThreadBackground
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import custom.health.charging.ChargeLimit
import custom.health.charging.IChargeLimitSettingCollector
import custom.health.charging.IChargingControlManager
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

private const val TAG = "ChargingLimitRepository"

/** Reads and sets the charge limit through the charging control service. */
@SysUISingleton
class ChargingLimitRepository
@Inject
constructor(
    private val manager: IChargingControlManager?,
    @Background private val backgroundScope: CoroutineScope,
    @Background private val backgroundContext: CoroutineContext,
    @SettingsSingleThreadBackground private val writeDispatcher: CoroutineDispatcher,
) {
    /** Emits after the service rejected a limit, so the screens show the stored one again. */
    private val rejectedWrites = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /**
     * The stored charge limit, then each change, and the stored one again after each rejected
     * write. A rejected write starts collecting the stored limit anew, so the service emits it
     * again in order with every change.
     */
    val limit: Flow<ChargingLimit> =
        rejectedWrites
            .onStart { emit(Unit) }
            .flatMapLatest { manager?.storedLimits() ?: emptyFlow() }
            .flowOn(backgroundContext)

    /** Whether the device has a charge limit HAL. False when the service cannot be asked. */
    @WorkerThread
    fun isSupported(): Boolean =
        try {
            manager?.isSupported ?: false
        } catch (e: RemoteException) {
            Log.w(TAG, "Could not ask the charging control service", e)
            false
        }

    /**
     * Stores [limit], in the order of the calls. The write runs on the background scope, so it
     * completes also when the screen that started it closes first. A rejected limit is logged.
     * [ChargingLimitRepository.limit] emits the stored one again.
     */
    fun set(limit: ChargingLimit) {
        backgroundScope.launch(writeDispatcher) {
            if (manager?.setLimit(limit.toChargeLimit()) != true) {
                Log.w(TAG, "The charging control service rejected the charge limit $limit")
                rejectedWrites.emit(Unit)
            }
        }
    }
}

/** Emits the stored charge limit, then each change. */
private fun IChargingControlManager.storedLimits(): Flow<ChargingLimit> = conflatedCallbackFlow {
    val limits = limitSettingFlow
    val collector =
        object : IChargeLimitSettingCollector.Stub() {
            override fun emit(limit: ChargeLimit?) {
                trySend(limit.toChargingLimit())
            }
        }
    limits.collect(collector)
    awaitClose { limits.cancel(collector) }
}

private fun ChargeLimit?.toChargingLimit(): ChargingLimit =
    this?.let { ChargingLimit.On(Percent(levelPercent), Percent(variationPercent)) }
        ?: ChargingLimit.Off

private fun ChargingLimit.toChargeLimit(): ChargeLimit? =
    when (this) {
        ChargingLimit.Off -> null
        is ChargingLimit.On ->
            ChargeLimit().also {
                it.levelPercent = level.value
                it.variationPercent = variation.value
            }
    }
