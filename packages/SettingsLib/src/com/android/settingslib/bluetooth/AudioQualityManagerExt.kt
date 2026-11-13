/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.bluetooth

import android.bluetooth.BluetoothCodecType
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.audioquality.AudioQuality
import android.bluetooth.audioquality.AudioQualityLevel
import android.bluetooth.audioquality.AudioQualityManager
import android.util.Log
import com.android.internal.util.ConcurrentUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "AudioQualityManager"

/**
 * [Flow] of the audio quality reported for [device]: the current quality first, then every change.
 * It sends null until [device] reports an A2DP codec status, and while Bluetooth is off.
 */
fun AudioQualityManager.qualityFlow(device: BluetoothDevice): Flow<AudioQuality?> =
    callbackFlow {
            val collector = AudioQualityManager.Collector { trySend(it) }
            this@qualityFlow.collect(device, ConcurrentUtils.DIRECT_EXECUTOR, collector)
            awaitClose { this@qualityFlow.cancel(collector) }
        }
        .buffer(capacity = Channel.CONFLATED)

/**
 * Sets [device] to [level], and logs the status returned by the Bluetooth stack when the level is
 * not set. The screens show the level reported by the stack, so they still show the level used
 * before.
 */
fun AudioQualityManager.setLevelAndLogFailure(device: BluetoothDevice, level: AudioQualityLevel) {
    val status = setLevel(device, level)
    if (status != BluetoothStatusCodes.SUCCESS) {
        Log.w(TAG, "Setting the audio quality level failed with status $status")
    }
}

/**
 * Chooses whether every headset encodes [codecType] in hardware offload, and logs the status
 * returned by the Bluetooth stack when the choice is not stored. The screens show the choice
 * reported by the stack, so they stay at the choice made before.
 */
fun AudioQualityManager.setCodecOffloadedAndLogFailure(
    codecType: BluetoothCodecType,
    offloaded: Boolean,
) {
    val status = setCodecOffloaded(codecType, offloaded)
    if (status != BluetoothStatusCodes.SUCCESS) {
        Log.w(TAG, "Offloaded=$offloaded for ${codecType.codecName} failed with status $status")
    }
}
