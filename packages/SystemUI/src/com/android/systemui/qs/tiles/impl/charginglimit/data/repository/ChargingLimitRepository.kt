/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.data.repository

import android.util.Log
import androidx.annotation.WorkerThread
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.ChargingLimit
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.Percent
import com.android.systemui.util.settings.SettingsSingleThreadBackground
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import custom.health.charging.ChargeLimit
import custom.health.charging.IChargeLimitListener
import custom.health.charging.IChargingControlManager
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

private const val TAG = "ChargingLimitRepository"

/** Reads and sets the charge limit through the charging control service. */
@SysUISingleton
class ChargingLimitRepository
@Inject
constructor(
    private val manager: IChargingControlManager,
    @Background private val backgroundScope: CoroutineScope,
    @Background private val backgroundContext: CoroutineContext,
    @SettingsSingleThreadBackground private val writeDispatcher: CoroutineDispatcher,
) {
    /** Emits after the service rejected a limit, so the screens show the stored one again. */
    private val rejectedWrites = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits after each change of the charge limit, and after each rejected write. */
    val changes: Flow<Unit> =
        merge(
                conflatedCallbackFlow {
                    val listener =
                        object : IChargeLimitListener.Stub() {
                            override fun onLimitChanged() {
                                trySend(Unit)
                            }
                        }
                    manager.registerListener(listener)
                    awaitClose { manager.unregisterListener(listener) }
                },
                rejectedWrites,
            )
            .flowOn(backgroundContext)

    /** The stored charge limit, then again after each of [changes]. */
    val limit: Flow<ChargingLimit> =
        changes.onStart { emit(Unit) }.map { read() }.flowOn(backgroundContext)

    /** The stored charge limit. */
    @WorkerThread fun read(): ChargingLimit = manager.limit.toChargingLimit()

    /**
     * Stores [limit], in the order of the calls. The write runs on the background scope, so it
     * completes also when the screen that started it closes first. A rejected limit is logged and
     * emitted through [changes].
     */
    fun set(limit: ChargingLimit) {
        backgroundScope.launch(writeDispatcher) {
            if (!manager.setLimit(limit.toChargeLimit())) {
                Log.w(TAG, "The charging control service rejected the charge limit $limit")
                rejectedWrites.emit(Unit)
            }
        }
    }
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
