/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.battery.data.repository

import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.utils.coroutines.flow.conflatedCallbackFlow
import custom.health.charging.ChargeLimitMode
import custom.health.charging.ChargeLimitState
import custom.health.charging.IChargeLimitStateListener
import custom.health.charging.IChargingControlManager
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** What the charge limit set the charger to */
sealed interface ChargerMode {
    /** The charger charges the battery */
    data object Charge : ChargerMode

    /** The charger powers the phone without charging the battery */
    data object Hold : ChargerMode

    /**
     * The phone runs on battery. [isChargerPresent] is true while a cable or pad is connected, and
     * false where the device cannot read it.
     */
    data class Drain(val isChargerPresent: Boolean) : ChargerMode
}

/** The mode set by the charge limit, as reported by the charging control service */
interface ChargerModeRepository {
    /**
     * The current mode, or null while it is unknown. That covers a device without a charge limit
     * HAL that reports modes, a HAL that is not running, and a HAL that failed to set the charger.
     */
    val chargerMode: Flow<ChargerMode?>
}

@SysUISingleton
class ChargerModeRepositoryImpl
@Inject
constructor(
    manager: IChargingControlManager?,
    @Background scope: CoroutineScope,
    @Background bgDispatcher: CoroutineDispatcher,
) : ChargerModeRepository {
    override val chargerMode: StateFlow<ChargerMode?> =
        (manager?.chargerModes() ?: emptyFlow())
            .flowOn(bgDispatcher)
            .stateIn(scope, SharingStarted.WhileSubscribed(), null)
}

/** Emits the current mode reported by the charging control service, then each change. */
private fun IChargingControlManager.chargerModes(): Flow<ChargerMode?> = conflatedCallbackFlow {
    val listener =
        object : IChargeLimitStateListener.Stub() {
            override fun onStateChanged(state: ChargeLimitState?) {
                trySend(state?.toChargerMode())
            }
        }
    registerStateListener(listener)
    awaitClose { unregisterStateListener(listener) }
}

private fun ChargeLimitState.toChargerMode(): ChargerMode? =
    when (mode) {
        ChargeLimitMode.CHARGE -> ChargerMode.Charge
        ChargeLimitMode.HOLD -> ChargerMode.Hold
        ChargeLimitMode.DRAIN -> ChargerMode.Drain(isChargerPresent = chargerPresent)
        // A mode added to the service after this build is unknown here.
        else -> null
    }
