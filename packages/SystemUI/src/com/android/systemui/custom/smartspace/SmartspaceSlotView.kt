/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom.smartspace

import android.content.Context
import android.os.Handler
import android.widget.LinearLayout
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceView
import com.android.systemui.plugins.FalsingManager

/**
 * The view of one smartspace slot. On its own it is an empty slot. Content views extend it and
 * override the host methods they need.
 *
 * It is a [LinearLayout] because SystemUI adds the weather view into the date view.
 */
open class SmartspaceSlotView(context: Context) : LinearLayout(context), SmartspaceView {
    override fun registerDataProvider(plugin: BcSmartspaceDataPlugin?) {}

    override fun setPrimaryTextColor(color: Int) {}

    override fun setUiSurface(uiSurface: String?) {}

    override fun setBgHandler(bgHandler: Handler?) {}

    override fun setDozeAmount(amount: Float) {}

    override fun setFalsingManager(falsingManager: FalsingManager?) {}

    // A slot has no card pages. The unlock animation reads -1 as no selected page.
    override fun getSelectedPage(): Int = -1

    override fun getCurrentCardTopPadding(): Int = 0
}

/** Creates the views shown in a slot, one for each view built by the host. */
fun interface SmartspaceSlotViewFactory {
    fun create(context: Context, clockSize: ClockSize): SmartspaceSlotView
}
