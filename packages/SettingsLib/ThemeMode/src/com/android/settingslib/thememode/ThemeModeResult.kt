// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode

sealed interface ThemeModeResult {
    data class Applied(val hasChangedNightMode: Boolean, val hasChangedDimOverlay: Boolean) :
        ThemeModeResult {
        val hasChangedSystemColors = hasChangedNightMode || hasChangedDimOverlay
    }
}

sealed interface ThemeModeFailure : ThemeModeResult {
    data object NightModeRefused : ThemeModeFailure

    data class DimOverlayRefused(val cause: Exception) : ThemeModeFailure
}
