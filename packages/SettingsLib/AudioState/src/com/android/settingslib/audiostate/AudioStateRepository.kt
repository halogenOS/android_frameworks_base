/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioRecordingConfiguration
import android.media.MicrophoneInfo
import android.os.Handler
import android.util.Log
import com.android.settingslib.bluetooth.LocalBluetoothManager
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The audio state of the device, rebuilt in full whenever devices, playbacks, recordings or an
 * accessory battery change.
 *
 * The host needs MODIFY_AUDIO_ROUTING for the output threads and the routing queries, and
 * BLUETOOTH_CONNECT for accessory batteries.
 *
 * @param handler the handler the framework callbacks run on
 * @param bluetoothManager the host's shared manager, null when Bluetooth is unsupported
 * @param backgroundContext the context the snapshots are built in, off the main thread
 */
class AudioStateRepository(
    private val context: Context,
    private val handler: Handler,
    bluetoothManager: LocalBluetoothManager?,
    backgroundContext: CoroutineContext,
) {
    private val audioManager: AudioManager = context.getSystemService(AudioManager::class.java)
    private val batteryProvider = LocalBluetoothBatteryProvider(bluetoothManager)
    private val threadProvider = AudioFlingerInfoProvider()

    val audioState: Flow<AudioStateSnapshot> =
        flow {
                // The microphones are fixed hardware, so they are read once per collection.
                val microphones = readMicrophones()
                emitAll(changeTriggers().conflate().map { snapshot(microphones) })
            }
            .distinctUntilChanged()
            .flowOn(backgroundContext)

    private fun snapshot(microphones: List<MicrophoneInfoSummary>?): AudioStateSnapshot {
        val outputs =
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.toAudioDevice() }
        val activeUsages =
            audioManager.activePlaybackConfigurations
                .filter { it.playerState == AudioPlaybackConfiguration.PLAYER_STATE_STARTED }
                .map { it.audioAttributes.usage }
                .distinct()
        val routedByUsage = activeUsages.associateWith { routedDevicesFor(it, outputs) }
        val usagesByDeviceId =
            routedByUsage
                .flatMap { (usage, devices) -> devices.map { it.id to audioUsageOf(usage) } }
                .groupBy({ it.first }, { it.second })
        val outputDevices = outputs.map { it.copy(isActive = it.id in usagesByDeviceId) }

        val recordings = audioManager.activeRecordingConfigurations
        val recordingDeviceIds = recordings.mapNotNull { it.audioDevice?.id }.toSet()
        val inputDevices =
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).map {
                it.toAudioDevice().copy(isActive = it.id in recordingDeviceIds)
            }

        return AudioStateSnapshot(
            outputPaths =
                when (val threads = threadProvider.readOutputThreads()) {
                    is OutputThreads.Read ->
                        OutputPaths.Read(
                            threads.threads.map { routeOf(it, outputDevices, usagesByDeviceId) }
                        )
                    is OutputThreads.Unavailable -> OutputPaths.Unavailable(threads.failure)
                },
            inputRoutes = recordings.map { it.toInputRoute(inputDevices) },
            outputDevices = outputDevices,
            inputDevices = inputDevices,
            microphones = microphones,
        )
    }

    private fun routeOf(
        thread: OutputThread,
        outputDevices: List<AudioDevice>,
        usagesByDeviceId: Map<Int, List<AudioUsage>>,
    ): AudioRoute {
        val devices = outputDevices.filter { it.id in thread.sinkPortIds }
        val isDuplicating = (thread.path as? OutputPath.Mixer)?.kind == MixerKind.DUPLICATING
        return AudioRoute(
            thread = thread,
            sink =
                when {
                    devices.isNotEmpty() -> Sink.Devices(devices)
                    isDuplicating -> Sink.Duplicated
                    thread.sinkPortIds.isEmpty() -> Sink.NotPatched
                    else -> Sink.Unidentified
                },
            usages = devices.flatMap { usagesByDeviceId[it.id].orEmpty() }.distinct(),
        )
    }

    /** Merges every "something changed" signal; emits once on start. */
    private fun changeTriggers(): Flow<Unit> =
        callbackFlow {
                val deviceCallback =
                    object : AudioDeviceCallback() {
                        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) {
                            trySend(Unit)
                        }

                        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) {
                            trySend(Unit)
                        }
                    }
                val playbackCallback =
                    object : AudioManager.AudioPlaybackCallback() {
                        override fun onPlaybackConfigChanged(
                            configs: MutableList<AudioPlaybackConfiguration>?
                        ) {
                            trySend(Unit)
                        }
                    }
                val recordingCallback =
                    object : AudioManager.AudioRecordingCallback() {
                        override fun onRecordingConfigChanged(
                            configs: MutableList<AudioRecordingConfiguration>?
                        ) {
                            trySend(Unit)
                        }
                    }
                val accessoryBatteryReceiver =
                    object : BroadcastReceiver() {
                        override fun onReceive(c: Context?, intent: Intent?) {
                            trySend(Unit)
                        }
                    }

                audioManager.registerAudioDeviceCallback(deviceCallback, handler)
                audioManager.registerAudioPlaybackCallback(playbackCallback, handler)
                audioManager.registerAudioRecordingCallback(recordingCallback, handler)
                context.registerReceiver(
                    accessoryBatteryReceiver,
                    IntentFilter(BluetoothDevice.ACTION_BATTERY_LEVEL_CHANGED),
                    Manifest.permission.BLUETOOTH_CONNECT,
                    handler,
                    Context.RECEIVER_EXPORTED,
                )

                awaitClose {
                    audioManager.unregisterAudioDeviceCallback(deviceCallback)
                    audioManager.unregisterAudioPlaybackCallback(playbackCallback)
                    audioManager.unregisterAudioRecordingCallback(recordingCallback)
                    context.unregisterReceiver(accessoryBatteryReceiver)
                }
            }
            .onStart { emit(Unit) }

    private fun AudioDeviceInfo.toAudioDevice(): AudioDevice {
        val kind = audioDeviceKindOf(type)
        val deviceAddress = address.takeIf { it.isNotBlank() }
        return AudioDevice(
            id = id,
            name = productName?.toString()?.takeIf { it.isNotBlank() },
            kind = kind,
            isActive = false,
            address = deviceAddress,
            sampleRates = sampleRates.sorted(),
            channelCounts = channelCounts.sorted(),
            encodings = encodings.asList().mapNotNull { sampleEncodingOf(it) }.distinct(),
            batteryPercent =
                deviceAddress
                    ?.takeIf { kind?.reportsAccessoryBattery == true }
                    ?.let { batteryProvider.batteryPercentFor(it) },
        )
    }

    /**
     * The devices audio policy routes a usage to now. The playback configurations only refresh
     * their device on player events, so they lag behind during a gapless track change.
     */
    private fun routedDevicesFor(usage: Int, outputDevices: List<AudioDevice>): List<AudioDevice> {
        val attributes = AudioAttributes.Builder().setUsage(usage).build()
        // The policy reports type and address only; a match must name exactly one device.
        return audioManager
            .getDevicesForAttributes(attributes)
            .mapNotNull { routed ->
                val routedKind = audioDeviceKindOf(routed.type) ?: return@mapNotNull null
                val routedAddress = routed.address.takeIf { it.isNotBlank() }
                outputDevices.singleOrNull { it.kind == routedKind && it.address == routedAddress }
            }
            .distinctBy { it.id }
    }

    private fun readMicrophones(): List<MicrophoneInfoSummary>? =
        try {
            audioManager.microphones.map { it.toSummary() }
        } catch (e: IOException) {
            Log.w(TAG, "getMicrophones failed", e)
            null
        }

    private fun MicrophoneInfo.toSummary(): MicrophoneInfoSummary =
        MicrophoneInfoSummary(
            description = description,
            address = address.takeIf { it.isNotBlank() },
            location = microphoneLocationOf(location),
            directionality = microphoneDirectionalityOf(directionality),
        )

    private fun AudioRecordingConfiguration.toInputRoute(
        inputDevices: List<AudioDevice>
    ): AudioInputRoute =
        AudioInputRoute(
            source = captureSourceOf(clientAudioSource),
            captureFormat = format.toSummary(),
            inputDevice = audioDevice?.id?.let { id -> inputDevices.singleOrNull { it.id == id } },
        )

    private companion object {
        const val TAG = "AudioStateRepository"
    }
}
