// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode

import kotlinx.coroutines.flow.Flow

interface DimOverlay {
    val state: Flow<DimOverlayState>

    fun setEnabled(isEnabled: Boolean): DimOverlaySetResult
}

sealed interface DimOverlayState {
    val isEnabled: Boolean

    data object Missing : DimOverlayState {
        override val isEnabled = false
    }

    data class Installed(override val isEnabled: Boolean) : DimOverlayState
}

sealed interface DimOverlaySetResult {
    data object Unchanged : DimOverlaySetResult

    data object Changed : DimOverlaySetResult

    data class Refused(val cause: Exception) : DimOverlaySetResult
}
