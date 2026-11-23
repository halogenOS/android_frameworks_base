// SPDX-FileCopyrightText: The halogenOS Project
// SPDX-License-Identifier: Apache-2.0

package com.android.settingslib.thememode

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.om.OverlayManager
import android.os.PatternMatcher
import android.os.Process
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class OverlayManagerDimOverlay(private val context: Context) : DimOverlay {
    private val overlayManager = context.getSystemService(OverlayManager::class.java)
    private val packageName =
        context.getString(com.android.internal.R.string.config_dimOverlayPackage)
    private val user = Process.myUserHandle()

    override val state: Flow<DimOverlayState> =
        flow {
                val targetPackage =
                    overlayManager.getOverlayInfo(packageName, user)?.targetPackageName
                if (targetPackage == null) {
                    emit(DimOverlayState.Missing)
                } else {
                    emitAll(overlayChanges(targetPackage).map { readState() })
                }
            }
            .distinctUntilChanged()

    override fun setEnabled(isEnabled: Boolean): DimOverlaySetResult {
        if (readState().isEnabled == isEnabled) return DimOverlaySetResult.Unchanged
        return try {
            overlayManager.setEnabled(packageName, isEnabled, user)
            DimOverlaySetResult.Changed
        } catch (e: IllegalStateException) {
            DimOverlaySetResult.Refused(e)
        } catch (e: SecurityException) {
            DimOverlaySetResult.Refused(e)
        }
    }

    private fun readState(): DimOverlayState =
        overlayManager.getOverlayInfo(packageName, user)?.let {
            DimOverlayState.Installed(it.isEnabled)
        } ?: DimOverlayState.Missing

    private fun overlayChanges(targetPackage: String): Flow<Unit> =
        callbackFlow {
                val receiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(context: Context, intent: Intent) {
                            trySend(Unit)
                        }
                    }
                context.registerReceiver(
                    receiver,
                    IntentFilter(Intent.ACTION_OVERLAY_CHANGED).apply {
                        addDataScheme("package")
                        addDataSchemeSpecificPart(targetPackage, PatternMatcher.PATTERN_LITERAL)
                    },
                )
                send(Unit)
                awaitClose { context.unregisterReceiver(receiver) }
            }
            .conflate()
}
