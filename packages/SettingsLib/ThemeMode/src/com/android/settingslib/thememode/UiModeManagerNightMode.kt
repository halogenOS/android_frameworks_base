// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode

import android.app.UiModeManager
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class UiModeManagerNightMode(private val context: Context) : NightMode {
    private val uiModeManager = context.getSystemService(UiModeManager::class.java)

    override val isActive: Boolean
        get() = context.resources.configuration.isNightModeActive

    override val isActiveFlow: Flow<Boolean> =
        callbackFlow {
                val callbacks =
                    object : ComponentCallbacks {
                        override fun onConfigurationChanged(newConfig: Configuration) {
                            trySend(newConfig.isNightModeActive)
                        }

                        @Deprecated("Deprecated in Java") override fun onLowMemory() {}
                    }
                context.registerComponentCallbacks(callbacks)
                send(isActive)
                awaitClose { context.unregisterComponentCallbacks(callbacks) }
            }
            .distinctUntilChanged()

    override fun trySetActive(isActive: Boolean): Boolean =
        uiModeManager.setNightModeActivated(isActive)
}
