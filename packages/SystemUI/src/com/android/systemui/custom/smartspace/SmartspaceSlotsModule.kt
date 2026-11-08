/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom.smartspace

import com.android.systemui.plugins.BcSmartspaceDataPlugin
import com.android.systemui.smartspace.dagger.SmartspaceModule.Companion.DATE_SMARTSPACE_DATA_PLUGIN
import com.android.systemui.smartspace.dagger.SmartspaceModule.Companion.DREAM_SMARTSPACE_DATA_PLUGIN
import com.android.systemui.smartspace.dagger.SmartspaceModule.Companion.DREAM_WEATHER_SMARTSPACE_DATA_PLUGIN
import com.android.systemui.smartspace.dagger.SmartspaceModule.Companion.GLANCEABLE_HUB_SMARTSPACE_DATA_PLUGIN
import com.android.systemui.smartspace.dagger.SmartspaceModule.Companion.WEATHER_SMARTSPACE_DATA_PLUGIN
import dagger.Module
import dagger.Provides
import dagger.multibindings.Multibinds
import javax.inject.Named

/**
 * Fills every optional smartspace data plugin of SystemUI with a slot. View factories bind into the
 * map with [SmartspaceSlotKey]. A slot without a factory is empty.
 */
@Module
internal interface SmartspaceSlotsModule {
    @Multibinds fun viewFactories(): Map<SmartspaceSlot, SmartspaceSlotViewFactory>

    companion object {
        @Provides
        fun provideMainPlugin(plugins: SmartspaceSlotPlugins): BcSmartspaceDataPlugin =
            plugins.create(SmartspaceSlot.MAIN)

        @Provides
        @Named(DATE_SMARTSPACE_DATA_PLUGIN)
        fun provideDatePlugin(plugins: SmartspaceSlotPlugins): BcSmartspaceDataPlugin =
            plugins.create(SmartspaceSlot.DATE)

        @Provides
        @Named(WEATHER_SMARTSPACE_DATA_PLUGIN)
        fun provideWeatherPlugin(plugins: SmartspaceSlotPlugins): BcSmartspaceDataPlugin =
            plugins.create(SmartspaceSlot.WEATHER)

        @Provides
        @Named(DREAM_SMARTSPACE_DATA_PLUGIN)
        fun provideDreamPlugin(plugins: SmartspaceSlotPlugins): BcSmartspaceDataPlugin =
            plugins.create(SmartspaceSlot.DREAM)

        @Provides
        @Named(DREAM_WEATHER_SMARTSPACE_DATA_PLUGIN)
        fun provideDreamWeatherPlugin(plugins: SmartspaceSlotPlugins): BcSmartspaceDataPlugin =
            plugins.create(SmartspaceSlot.DREAM_WEATHER)

        @Provides
        @Named(GLANCEABLE_HUB_SMARTSPACE_DATA_PLUGIN)
        fun provideGlanceableHubPlugin(plugins: SmartspaceSlotPlugins): BcSmartspaceDataPlugin =
            plugins.create(SmartspaceSlot.GLANCEABLE_HUB)
    }
}
