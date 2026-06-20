/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate

import android.bluetooth.BluetoothAdapter
import android.util.Log
import com.android.settingslib.bluetooth.LocalBluetoothManager

/**
 * Reads the battery level of a Bluetooth accessory through the host's [LocalBluetoothManager]. The
 * manager is a process-wide singleton; creating another one here would replace the instance the
 * host's own Bluetooth UI depends on.
 */
internal class LocalBluetoothBatteryProvider(private val bluetoothManager: LocalBluetoothManager?) {

    /** The battery level from 0 to 100, or null when the accessory reports none. */
    fun batteryPercentFor(address: String): Int? {
        val manager = bluetoothManager ?: return null
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return null
        return runCatching {
                val remote = manager.bluetoothAdapter?.getRemoteDevice(address) ?: return null
                val cached = manager.cachedDeviceManager?.findDevice(remote) ?: return null
                // getBatteryLevel() reports unknown and Bluetooth off as negative values.
                cached.batteryLevel.takeIf { it in 0..100 }
            }
            .getOrElse {
                Log.w(TAG, "battery lookup failed", it)
                null
            }
    }

    private companion object {
        const val TAG = "AudioStateBtBattery"
    }
}
