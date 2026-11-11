/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.bluetooth

import android.content.Intent
import android.os.Bundle

private const val ACTION_BLUETOOTH_DEVICE_DETAILS =
    "com.android.settings.BLUETOOTH_DEVICE_DETAIL_SETTINGS"
private const val EXTRA_SHOW_FRAGMENT_ARGUMENTS = ":settings:show_fragment_args"
private const val ARGUMENT_DEVICE_ADDRESS = "device_address"

/** Intent that opens the Settings page for the Bluetooth device with [address]. */
fun bluetoothDeviceDetailsIntent(address: String): Intent =
    Intent(ACTION_BLUETOOTH_DEVICE_DETAILS)
        .putExtra(
            EXTRA_SHOW_FRAGMENT_ARGUMENTS,
            Bundle().apply { putString(ARGUMENT_DEVICE_ADDRESS, address) },
        )
