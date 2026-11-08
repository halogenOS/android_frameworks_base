/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom.smartspace.date

import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockStyleSlot

/** The date slot of the face shown at [clockSize], or null when that face keeps the host's date. */
internal fun ClockController.dateSlot(clockSize: ClockSize): ClockStyleSlot? =
    when (clockSize) {
        ClockSize.SMALL -> smallClock
        ClockSize.LARGE -> largeClock
    }.clockStyleSlot
