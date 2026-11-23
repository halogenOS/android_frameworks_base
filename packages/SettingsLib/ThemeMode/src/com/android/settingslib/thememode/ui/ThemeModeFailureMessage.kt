// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode.ui

import androidx.annotation.StringRes
import com.android.settingslib.thememode.R
import com.android.settingslib.thememode.ThemeModeFailure

@get:StringRes
val ThemeModeFailure.messageId: Int
    get() =
        when (this) {
            ThemeModeFailure.NightModeRefused -> R.string.theme_mode_night_mode_refused
            is ThemeModeFailure.DimOverlayRefused -> R.string.theme_mode_dim_overlay_refused
        }
