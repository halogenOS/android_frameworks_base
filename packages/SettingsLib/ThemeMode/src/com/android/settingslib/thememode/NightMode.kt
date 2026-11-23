// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode

import kotlinx.coroutines.flow.Flow

interface NightMode {
    val isActive: Boolean

    val isActiveFlow: Flow<Boolean>

    fun trySetActive(isActive: Boolean): Boolean
}
