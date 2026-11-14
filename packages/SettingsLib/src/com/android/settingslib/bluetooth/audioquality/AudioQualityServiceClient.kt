/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.bluetooth.audioquality

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothCodecType
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothStatusCodes
import android.content.AttributionSource
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.RemoteException
import android.util.Log
import com.android.bluetooth.audioquality.aidl.AudioQuality
import com.android.bluetooth.audioquality.aidl.AudioQualityCall
import com.android.bluetooth.audioquality.aidl.AudioQualityLevel
import com.android.bluetooth.audioquality.aidl.AudioQualityServiceBinding
import com.android.bluetooth.audioquality.aidl.AudioQualityServiceCall
import com.android.bluetooth.audioquality.aidl.IAudioQuality
import com.android.bluetooth.audioquality.aidl.IAudioQualityCollector
import com.android.bluetooth.audioquality.aidl.callService
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

private const val TAG = "AudioQualityServiceClient"

/**
 * Client of the audio quality service of the Bluetooth app. While Bluetooth is on and a flow of
 * this client is collected, the client binds the service as the current user, the user the
 * Bluetooth stack runs in. Every flow of the client uses that one binding, and the flows for one
 * device share one collector in the service.
 *
 * @param scope the scope in which the binding and the shared flows run while they are collected
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioQualityServiceClient(private val context: Context, private val scope: CoroutineScope) {
    /** The service while Bluetooth is on and the binding is connected, and null otherwise. */
    private val service: StateFlow<IAudioQuality?> =
        context
            .bluetoothOnFlow()
            .flatMapLatest { isOn -> if (isOn) context.audioQualityServiceFlow() else flowOf(null) }
            .flowOn(Dispatchers.Default)
            .stateIn(scope, SharingStarted.WhileSubscribed(), initialValue = null)

    private val qualityFlows = ConcurrentHashMap<BluetoothDevice, SharedFlow<AudioQuality?>>()

    /**
     * [Flow] of the audio quality reported for [device]: the current quality first, then every
     * change. It sends null until [device] reports an A2DP codec status, while Bluetooth is off,
     * and while the service is not connected.
     */
    fun qualityFlow(device: BluetoothDevice): Flow<AudioQuality?> =
        qualityFlows.computeIfAbsent(device) {
            service
                .flatMapLatest {
                    it?.qualityFlow(device, context.attributionSource) ?: flowOf(null)
                }
                .flowOn(Dispatchers.Default)
                .shareIn(
                    scope,
                    SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
                    replay = 1,
                )
        }

    /**
     * Sets [device] to [level]. The screens show the level reported by the stack, so after a
     * failure they still show the level used before.
     */
    fun setLevel(device: BluetoothDevice, level: AudioQualityLevel) =
        callService("setLevel") { it.setLevel(device, level, context.attributionSource) }

    /**
     * Sets for every headset whether the phone encodes [codecType] in hardware offload. The screens
     * show the choice reported by the stack, so after a failure they still show the choice made
     * before.
     */
    fun setCodecOffloaded(codecType: BluetoothCodecType, offloaded: Boolean) =
        callService("setCodecOffloaded") {
            it.setCodecOffloaded(codecType, offloaded, context.attributionSource)
        }

    /**
     * Makes [call], which returns a BluetoothStatusCodes value, to the connected service, and logs
     * each outcome other than SUCCESS.
     */
    private fun callService(method: String, call: AudioQualityServiceCall<Int>) {
        when (val result = service.value.callService(call)) {
            is AudioQualityCall.Returned ->
                if (result.value != BluetoothStatusCodes.SUCCESS) {
                    Log.w(TAG, "$method returned status ${result.value}")
                }
            AudioQualityCall.NotConnected -> Log.w(TAG, "$method: the service is not connected")
            is AudioQualityCall.Failed -> Log.w(TAG, "$method failed", result.error)
        }
    }
}

/** [Flow] of whether Bluetooth is on, the only state in which the audio quality service runs. */
private fun Context.bluetoothOnFlow(): Flow<Boolean> =
    callbackFlow {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        trySend(
                            intent.getIntExtra(
                                BluetoothAdapter.EXTRA_STATE,
                                BluetoothAdapter.ERROR,
                            ) == BluetoothAdapter.STATE_ON
                        )
                    }
                }
            registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
            trySend(getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true)
            awaitClose { unregisterReceiver(receiver) }
        }
        .distinctUntilChanged()

/** [Flow] of the service while the binding is connected, and of null while it is not. */
private fun Context.audioQualityServiceFlow(): Flow<IAudioQuality?> = callbackFlow {
    val binding = AudioQualityServiceBinding(this@audioQualityServiceFlow) { trySend(it) }
    if (!binding.bind()) {
        send(null)
    }
    awaitClose { binding.unbind() }
}

/**
 * [Flow] of the audio quality the service reports for [device]. It sends null while the Bluetooth
 * stack does not allow the caller, and when the call fails.
 */
private fun IAudioQuality.qualityFlow(
    device: BluetoothDevice,
    attributionSource: AttributionSource,
): Flow<AudioQuality?> = callbackFlow {
    val collector =
        object : IAudioQualityCollector.Stub() {
            override fun emit(quality: AudioQuality?) {
                trySend(quality)
            }
        }
    val result =
        this@qualityFlow.callService {
            it.getQualityFlow(device, attributionSource)?.apply { collect(collector) }
        }
    val flow =
        when (result) {
            is AudioQualityCall.Returned -> result.value
            AudioQualityCall.NotConnected -> null
            is AudioQualityCall.Failed -> {
                Log.w(TAG, "Collecting the quality for $device failed", result.error)
                null
            }
        }
    if (flow == null) {
        send(null)
    }
    awaitClose {
        try {
            flow?.cancel(collector)
        } catch (e: RemoteException) {
            Log.w(TAG, "Cancelling the collector for $device failed", e)
        }
    }
}
