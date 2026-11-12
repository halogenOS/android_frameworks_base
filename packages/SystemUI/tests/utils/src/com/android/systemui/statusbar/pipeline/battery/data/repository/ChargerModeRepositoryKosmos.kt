/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.statusbar.pipeline.battery.data.repository

import com.android.systemui.kosmos.Kosmos
import kotlinx.coroutines.flow.MutableStateFlow

/** Set [chargerMode] to make the repository report the mode you want */
class FakeChargerModeRepository : ChargerModeRepository {
    override val chargerMode = MutableStateFlow<ChargerMode?>(null)
}

val Kosmos.chargerModeRepository by Kosmos.Fixture { FakeChargerModeRepository() }
