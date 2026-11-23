// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode

enum class ThemeMode(val isNightMode: Boolean, val dimOverlayChange: DimOverlayChange) {
    LIGHT(isNightMode = false, dimOverlayChange = DimOverlayChange.KEEP),
    DARK(isNightMode = true, dimOverlayChange = DimOverlayChange.DISABLE),
    DIM(isNightMode = true, dimOverlayChange = DimOverlayChange.ENABLE);

    companion object {
        internal fun of(isNightMode: Boolean, isDimOverlayEnabled: Boolean): ThemeMode =
            when {
                !isNightMode -> LIGHT
                isDimOverlayEnabled -> DIM
                else -> DARK
            }

        internal fun availableWith(dimOverlayState: DimOverlayState): List<ThemeMode> =
            entries.filter {
                it.dimOverlayChange != DimOverlayChange.ENABLE ||
                    dimOverlayState is DimOverlayState.Installed
            }
    }
}

data class ThemeModeState(val selected: ThemeMode, val available: List<ThemeMode>)

enum class DimOverlayChange {
    KEEP,
    ENABLE,
    DISABLE,
}
