/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom.smartspace.date

import com.android.systemui.custom.smartspace.SmartspaceSlot
import com.android.systemui.custom.smartspace.SmartspaceSlotKey
import com.android.systemui.custom.smartspace.SmartspaceSlotViewFactory
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap

/** Fills the date slot. */
@Module
internal interface DateSlotModule {
    @Binds
    @IntoMap
    @SmartspaceSlotKey(SmartspaceSlot.DATE)
    fun bindDateSlot(factory: DateSlotViewFactory): SmartspaceSlotViewFactory
}
