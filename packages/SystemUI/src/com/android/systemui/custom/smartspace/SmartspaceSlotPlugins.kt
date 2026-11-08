/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom.smartspace

import android.app.smartspace.SmartspaceTarget
import android.content.Context
import android.view.View
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.plugins.BcSmartspaceDataPlugin.IntentStarter
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceEventDispatcher
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceEventNotifier
import com.android.systemui.plugins.BcSmartspaceDataPlugin.SmartspaceTargetListener
import javax.inject.Inject

/** Creates the plugin for each slot from the view factories bound to the slots. */
internal class SmartspaceSlotPlugins
@Inject
constructor(
    private val viewFactories: Map<SmartspaceSlot, @JvmSuppressWildcards SmartspaceSlotViewFactory>
) {
    fun create(slot: SmartspaceSlot): BcSmartspaceDataPlugin =
        SmartspaceSlotPlugin(slot, viewFactories[slot])
}

/** Returns the views of one slot from its bound factory, or empty views without a factory. */
private class SmartspaceSlotPlugin(
    private val slot: SmartspaceSlot,
    private val viewFactory: SmartspaceSlotViewFactory?,
) : BcSmartspaceDataPlugin {
    override fun getView(context: Context): SmartspaceSlotView =
        createView(context, ClockSize.SMALL)

    override fun getLargeClockView(context: Context): SmartspaceSlotView =
        createView(context, ClockSize.LARGE)

    private fun createView(context: Context, clockSize: ClockSize): SmartspaceSlotView {
        val view =
            viewFactory?.create(context, clockSize)
                ?: SmartspaceSlotView(context).apply { visibility = View.GONE }
        slot.viewId(clockSize)?.let { view.id = it }
        return view
    }

    // The slots show no smartspace targets, so these methods ignore listeners, targets, events and
    // launches.
    override fun registerListener(listener: SmartspaceTargetListener?) {}

    override fun unregisterListener(listener: SmartspaceTargetListener?) {}

    override fun onTargetsAvailable(targets: List<SmartspaceTarget>?) {}

    override fun setEventDispatcher(eventDispatcher: SmartspaceEventDispatcher?) {}

    override fun setIntentStarter(intentStarter: IntentStarter?) {}

    override fun getEventNotifier(): SmartspaceEventNotifier? = null
}
