/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.audioquality

import android.bluetooth.audioquality.AudioQualityManager
import android.content.Context
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.AudioQualityTile
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.res.R
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface AudioQualityTileModule {
    @Binds
    @IntoMap
    @StringKey(AudioQualityTile.TILE_SPEC)
    fun bindAudioQualityTile(tile: AudioQualityTile): QSTileImpl<*>

    companion object {
        /** Null on devices without Bluetooth. */
        @Provides
        fun provideAudioQualityManager(@Application context: Context): AudioQualityManager? =
            context.getSystemService(AudioQualityManager::class.java)

        @Provides
        @IntoMap
        @StringKey(AudioQualityTile.TILE_SPEC)
        fun provideAudioQualityTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(AudioQualityTile.TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_audio_quality,
                        labelRes = com.android.settingslib.R.string.bluetooth_audio_quality_title,
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.CONNECTIVITY,
            )
    }
}
