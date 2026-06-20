/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.audioinfo.ui.model

import android.content.Context
import android.os.Handler
import com.android.settingslib.audiostate.AudioStateRepository
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.AudioInfoTile
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.res.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlin.coroutines.CoroutineContext

@Module
interface AudioInfoTileModule {
    @Binds
    @IntoMap
    @StringKey(AudioInfoTile.TILE_SPEC)
    fun bindAudioInfoTile(tile: AudioInfoTile): QSTileImpl<*>

    companion object {
        @Provides
        @IntoMap
        @StringKey(AudioInfoTile.TILE_SPEC)
        fun provideAudioInfoTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(AudioInfoTile.TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_spatial_audio,
                        labelRes = R.string.quick_settings_audio_info_label,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.DISPLAY,
            )

        @Provides
        @SysUISingleton
        fun provideAudioStateRepository(
            @Application context: Context,
            @Background handler: Handler,
            @Background backgroundContext: CoroutineContext,
            localBluetoothManager: LocalBluetoothManager?,
        ): AudioStateRepository =
            AudioStateRepository(context, handler, localBluetoothManager, backgroundContext)
    }
}
