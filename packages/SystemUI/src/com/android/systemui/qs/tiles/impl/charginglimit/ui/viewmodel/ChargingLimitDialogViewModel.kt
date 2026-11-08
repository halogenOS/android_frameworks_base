/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.qs.tiles.impl.charginglimit.data.repository.ChargingLimitRepository
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.ChargingLimit
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/** The limit shown by the charge limit dialog, and the changes the user makes to it. */
class ChargingLimitDialogViewModel
@AssistedInject
constructor(
    @Assisted initialLimit: ChargingLimit,
    private val repository: ChargingLimitRepository,
) : HydratedActivatable() {

    private var stored by mutableStateOf(initialLimit)

    /** The limit under the finger while the user moves a slider. */
    private var dragged: ChargingLimit? by mutableStateOf(null)

    /**
     * The limit of a released slider while the service stores it, until the stored limit changes or
     * the service rejects it.
     */
    private var storing: ChargingLimit? by mutableStateOf(null)

    val limit: ChargingLimit
        get() = dragged ?: storing ?: stored

    override suspend fun onActivated() {
        repository.limit.collect {
            stored = it
            storing = null
        }
    }

    fun onChange(limit: ChargingLimit) {
        dragged = limit
    }

    fun onRelease() {
        val released = dragged ?: return
        dragged = null
        storing = released
        repository.set(released)
    }

    fun turnOff() = repository.set(ChargingLimit.Off)

    @AssistedFactory
    interface Factory {
        fun create(initialLimit: ChargingLimit): ChargingLimitDialogViewModel
    }
}
