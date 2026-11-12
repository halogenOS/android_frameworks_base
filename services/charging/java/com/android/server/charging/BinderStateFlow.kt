/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.charging

import android.os.Handler
import android.os.IInterface
import android.os.RemoteCallbackList
import android.os.RemoteException
import android.util.Slog

private const val TAG = "BinderStateFlow"

/**
 * A value emitted to binder collectors, each of which receives the current value first, then every
 * change. Everything runs on [handler]. Each collector receives the values in the order they were
 * published. [cancel] also removes a collector before [collect] has registered it.
 */
internal class BinderStateFlow<C : IInterface, T>(
    private val handler: Handler,
    /** Reads the current value. Runs on [handler]. */
    private val read: () -> T,
    private val emit: C.(T) -> Unit,
) {
    private val collectors = RemoteCallbackList<C>()

    fun collect(collector: C) {
        handler.post { if (collectors.register(collector)) collector.send(read()) }
    }

    fun cancel(collector: C) {
        handler.post { collectors.unregister(collector) }
    }

    /** Emits the current value to every collector after it changed. Runs on [handler]. */
    fun publishChange() {
        check(handler.looper.isCurrentThread) { "A change was published off the handler thread" }
        val value = read()
        collectors.broadcast { it.send(value) }
    }

    private fun C.send(value: T) {
        try {
            emit(value)
        } catch (e: RemoteException) {
            // The process holding the collector died. The callback list removes dead collectors
            // by itself.
            Slog.w(TAG, "Could not emit to a collector", e)
        }
    }
}
