// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode

import android.util.Log
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class ThemeModeRepository(private val nightMode: NightMode, private val dimOverlay: DimOverlay) {

    val state: Flow<ThemeModeState> =
        combine(nightMode.isActiveFlow, dimOverlay.state) { isNightMode, dimOverlayState ->
            ThemeModeState(
                selected = ThemeMode.of(isNightMode, dimOverlayState.isEnabled),
                available = ThemeMode.availableWith(dimOverlayState),
            )
        }

    fun apply(mode: ThemeMode): ThemeModeResult =
        when (mode.dimOverlayChange) {
            DimOverlayChange.KEEP ->
                setNightModeKeepingSchedule(mode.isNightMode, hasChangedDimOverlay = false)
            DimOverlayChange.ENABLE ->
                setDimOverlayAndNightMode(
                    isDimOverlayEnabled = true,
                    isNightMode = mode.isNightMode,
                )
            DimOverlayChange.DISABLE ->
                setDimOverlayAndNightMode(
                    isDimOverlayEnabled = false,
                    isNightMode = mode.isNightMode,
                )
        }

    fun applyNightMode(isNightMode: Boolean): ThemeModeResult =
        setNightModeKeepingSchedule(isNightMode, hasChangedDimOverlay = false)

    private fun setDimOverlayAndNightMode(
        isDimOverlayEnabled: Boolean,
        isNightMode: Boolean,
    ): ThemeModeResult =
        when (val dimOverlayResult = dimOverlay.setEnabled(isDimOverlayEnabled)) {
            is DimOverlaySetResult.Refused ->
                ThemeModeFailure.DimOverlayRefused(dimOverlayResult.cause)
            DimOverlaySetResult.Unchanged ->
                setNightModeKeepingSchedule(isNightMode, hasChangedDimOverlay = false)
            DimOverlaySetResult.Changed -> {
                val result = setNightModeKeepingSchedule(isNightMode, hasChangedDimOverlay = true)
                if (result == ThemeModeFailure.NightModeRefused) {
                    restoreDimOverlay(isEnabled = !isDimOverlayEnabled)
                }
                result
            }
        }

    private fun setNightModeKeepingSchedule(
        isNightMode: Boolean,
        hasChangedDimOverlay: Boolean,
    ): ThemeModeResult =
        when {
            nightMode.isActive == isNightMode ->
                ThemeModeResult.Applied(
                    hasChangedNightMode = false,
                    hasChangedDimOverlay = hasChangedDimOverlay,
                )
            nightMode.trySetActive(isNightMode) ->
                ThemeModeResult.Applied(
                    hasChangedNightMode = true,
                    hasChangedDimOverlay = hasChangedDimOverlay,
                )
            else -> ThemeModeFailure.NightModeRefused
        }

    private fun restoreDimOverlay(isEnabled: Boolean) {
        val result = dimOverlay.setEnabled(isEnabled)
        if (result is DimOverlaySetResult.Refused) {
            Log.w(TAG, "The system did not restore the dim overlay", result.cause)
        }
    }

    private companion object {
        const val TAG = "ThemeModeRepository"
    }
}
