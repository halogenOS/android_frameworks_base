/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom.smartspace

import androidx.annotation.IdRes
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.shared.R as sharedR
import dagger.MapKey

/** The smartspace plugin slots SystemUI asks for, with the view ids its layouts find them by. */
enum class SmartspaceSlot(
    @param:IdRes private val smallClockViewId: Int?,
    @param:IdRes private val largeClockViewId: Int?,
) {
    MAIN(
        smallClockViewId = sharedR.id.bc_smartspace_view,
        largeClockViewId = sharedR.id.bc_smartspace_view,
    ),
    DATE(
        smallClockViewId = sharedR.id.date_smartspace_view,
        largeClockViewId = sharedR.id.date_smartspace_view_large,
    ),
    WEATHER(
        smallClockViewId = sharedR.id.weather_smartspace_view,
        largeClockViewId = sharedR.id.weather_smartspace_view_large,
    ),
    DREAM(smallClockViewId = null, largeClockViewId = null),
    DREAM_WEATHER(smallClockViewId = null, largeClockViewId = null),
    GLANCEABLE_HUB(smallClockViewId = null, largeClockViewId = null);

    @IdRes
    fun viewId(clockSize: ClockSize): Int? =
        when (clockSize) {
            ClockSize.SMALL -> smallClockViewId
            ClockSize.LARGE -> largeClockViewId
        }
}

/** The map key of a [SmartspaceSlotViewFactory] binding: the slot filled by the factory. */
@MapKey annotation class SmartspaceSlotKey(val value: SmartspaceSlot)
