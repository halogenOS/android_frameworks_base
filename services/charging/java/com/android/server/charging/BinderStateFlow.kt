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
    initialValue: T,
    private val emit: C.(T) -> Unit,
) {
    private val collectors = RemoteCallbackList<C>()

    /** Only read and written on [handler]. */
    private var current = initialValue

    fun collect(collector: C) {
        handler.post { if (collectors.register(collector)) collector.send(current) }
    }

    fun cancel(collector: C) {
        handler.post { collectors.unregister(collector) }
    }

    /** Emits [newValue] to every collector. Runs on [handler]. */
    fun publish(newValue: T) {
        check(handler.looper.isCurrentThread) { "A value was published off the handler thread" }
        current = newValue
        collectors.broadcast { it.send(newValue) }
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
