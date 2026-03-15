/*
 * Copyright (C) 2024 The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom

import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.pipeline.shared.TileSpec
import com.android.systemui.qs.shared.model.TileCategory
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.ChargingLimitTile
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfig
import com.android.systemui.qs.tiles.base.shared.model.QSTileUIConfig
import com.android.systemui.res.R

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface CustomModule {
    /** Inject ChargingLimitTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ChargingLimitTile.TILE_SPEC)
    fun bindChargingLimitTile(chargingLimitTile: ChargingLimitTile): QSTileImpl<*>

    companion object {
        const val CHARGING_LIMIT_TILE_SPEC = "charging_limit"

        @Provides
        @IntoMap
        @StringKey(CHARGING_LIMIT_TILE_SPEC)
        fun provideChargingLimitTileConfig(uiEventLogger: QsEventLogger): QSTileConfig =
            QSTileConfig(
                tileSpec = TileSpec.create(CHARGING_LIMIT_TILE_SPEC),
                uiConfig =
                    QSTileUIConfig.Resource(
                        iconRes = R.drawable.ic_qs_charging_limit,
                        labelRes = R.string.quick_settings_charging_limit_label
                    ),
                instanceId = uiEventLogger.getNewInstanceId(),
                category = TileCategory.UTILITIES,
            )
    }
}
