/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit

import android.os.ServiceManager
import com.android.systemui.dagger.SysUISingleton
import custom.health.charging.IChargingControlManager
import dagger.Module
import dagger.Provides

@Module
object ChargingLimitModule {
    @Provides
    @SysUISingleton
    fun provideChargingControlManager(): IChargingControlManager =
        IChargingControlManager.Stub.asInterface(
            ServiceManager.getService(IChargingControlManager.DESCRIPTOR)
        )
}
