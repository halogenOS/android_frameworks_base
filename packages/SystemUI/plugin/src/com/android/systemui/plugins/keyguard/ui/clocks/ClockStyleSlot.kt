/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.plugins.keyguard.ui.clocks

import android.content.Context
import android.view.View
import com.android.systemui.plugins.annotations.ProtectedInterface
import com.android.systemui.plugins.annotations.ProtectedReturn

/** The view of a clock face for the lockscreen date slot, shown in place of the host's date. */
@ProtectedInterface
interface ClockStyleSlot {
    /**
     * Creates a new view for one date slot. The host calls this each time one of its date views, on
     * the lockscreen or in the lockscreen preview, starts to show this face. Null keeps the host's
     * date.
     */
    @ProtectedReturn("return null;") fun createView(context: Context): View?
}
