/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.MediaRecorder
import android.media.MicrophoneInfo

internal fun audioDeviceKindOf(type: Int): AudioDeviceKind? =
    when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioDeviceKind.EARPIECE
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceKind.SPEAKER
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> AudioDeviceKind.SPEAKER_SAFE
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> AudioDeviceKind.BUILTIN_MIC
        AudioDeviceInfo.TYPE_TELEPHONY -> AudioDeviceKind.TELEPHONY
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> AudioDeviceKind.REMOTE_SUBMIX
        AudioDeviceInfo.TYPE_FM_TUNER -> AudioDeviceKind.FM_TUNER
        AudioDeviceInfo.TYPE_TV_TUNER -> AudioDeviceKind.TV_TUNER
        AudioDeviceInfo.TYPE_ECHO_REFERENCE -> AudioDeviceKind.ECHO_REFERENCE
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> AudioDeviceKind.WIRED_HEADSET
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> AudioDeviceKind.WIRED_HEADPHONES
        AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioDeviceKind.LINE_ANALOG
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> AudioDeviceKind.LINE_DIGITAL
        AudioDeviceInfo.TYPE_AUX_LINE -> AudioDeviceKind.AUX_LINE
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioDeviceKind.BLUETOOTH_SCO
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> AudioDeviceKind.BLUETOOTH_A2DP
        AudioDeviceInfo.TYPE_BLE_HEADSET -> AudioDeviceKind.BLE_HEADSET
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> AudioDeviceKind.BLE_SPEAKER
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioDeviceKind.BLE_BROADCAST
        AudioDeviceInfo.TYPE_HEARING_AID -> AudioDeviceKind.HEARING_AID
        AudioDeviceInfo.TYPE_USB_DEVICE -> AudioDeviceKind.USB_DEVICE
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioDeviceKind.USB_ACCESSORY
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioDeviceKind.USB_HEADSET
        AudioDeviceInfo.TYPE_HDMI -> AudioDeviceKind.HDMI
        AudioDeviceInfo.TYPE_HDMI_ARC -> AudioDeviceKind.HDMI_ARC
        AudioDeviceInfo.TYPE_HDMI_EARC -> AudioDeviceKind.HDMI_EARC
        AudioDeviceInfo.TYPE_DOCK -> AudioDeviceKind.DOCK
        AudioDeviceInfo.TYPE_DOCK_ANALOG -> AudioDeviceKind.DOCK_ANALOG
        AudioDeviceInfo.TYPE_FM -> AudioDeviceKind.FM
        AudioDeviceInfo.TYPE_IP -> AudioDeviceKind.IP
        AudioDeviceInfo.TYPE_BUS -> AudioDeviceKind.BUS
        AudioDeviceInfo.TYPE_MULTICHANNEL_GROUP -> AudioDeviceKind.MULTICHANNEL_GROUP
        else -> null
    }

internal fun audioUsageOf(usage: Int): AudioUsage =
    when (usage) {
        AudioAttributes.USAGE_MEDIA -> AudioUsage.MEDIA
        AudioAttributes.USAGE_VOICE_COMMUNICATION -> AudioUsage.CALL
        AudioAttributes.USAGE_ALARM -> AudioUsage.ALARM
        AudioAttributes.USAGE_NOTIFICATION -> AudioUsage.NOTIFICATION
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> AudioUsage.RINGTONE
        AudioAttributes.USAGE_GAME -> AudioUsage.GAME
        AudioAttributes.USAGE_ASSISTANT -> AudioUsage.ASSISTANT
        else -> AudioUsage.OTHER
    }

internal fun captureSourceOf(source: Int): CaptureSource =
    when (source) {
        MediaRecorder.AudioSource.MIC -> CaptureSource.MIC
        MediaRecorder.AudioSource.VOICE_RECOGNITION -> CaptureSource.VOICE_RECOGNITION
        MediaRecorder.AudioSource.VOICE_COMMUNICATION -> CaptureSource.VOICE_COMMUNICATION
        MediaRecorder.AudioSource.CAMCORDER -> CaptureSource.CAMCORDER
        MediaRecorder.AudioSource.UNPROCESSED -> CaptureSource.UNPROCESSED
        MediaRecorder.AudioSource.VOICE_UPLINK -> CaptureSource.VOICE_UPLINK
        MediaRecorder.AudioSource.VOICE_DOWNLINK -> CaptureSource.VOICE_DOWNLINK
        MediaRecorder.AudioSource.VOICE_CALL -> CaptureSource.VOICE_CALL
        MediaRecorder.AudioSource.VOICE_PERFORMANCE -> CaptureSource.VOICE_PERFORMANCE
        MediaRecorder.AudioSource.REMOTE_SUBMIX -> CaptureSource.REMOTE_SUBMIX
        else -> CaptureSource.OTHER
    }

internal fun microphoneLocationOf(location: Int): MicrophoneLocation? =
    when (location) {
        MicrophoneInfo.LOCATION_MAINBODY -> MicrophoneLocation.MAIN_BODY
        MicrophoneInfo.LOCATION_MAINBODY_MOVABLE -> MicrophoneLocation.MAIN_BODY_MOVABLE
        MicrophoneInfo.LOCATION_PERIPHERAL -> MicrophoneLocation.PERIPHERAL
        else -> null
    }

internal fun microphoneDirectionalityOf(directionality: Int): MicrophoneDirectionality? =
    when (directionality) {
        MicrophoneInfo.DIRECTIONALITY_OMNI -> MicrophoneDirectionality.OMNI
        MicrophoneInfo.DIRECTIONALITY_BI_DIRECTIONAL -> MicrophoneDirectionality.BI_DIRECTIONAL
        MicrophoneInfo.DIRECTIONALITY_CARDIOID -> MicrophoneDirectionality.CARDIOID
        MicrophoneInfo.DIRECTIONALITY_HYPER_CARDIOID -> MicrophoneDirectionality.HYPER_CARDIOID
        MicrophoneInfo.DIRECTIONALITY_SUPER_CARDIOID -> MicrophoneDirectionality.SUPER_CARDIOID
        else -> null
    }

/** An AudioFormat.ENCODING_* value, or null for one the platform does not know. */
internal fun sampleEncodingOf(encoding: Int): SampleEncoding? =
    when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT,
        AudioFormat.ENCODING_PCM_16BIT,
        AudioFormat.ENCODING_PCM_24BIT_PACKED,
        AudioFormat.ENCODING_PCM_32BIT,
        AudioFormat.ENCODING_PCM_FLOAT ->
            SampleEncoding.Pcm(
                bitsPerSample = AudioFormat.getBytesPerSample(encoding) * Byte.SIZE_BITS,
                isFloat = encoding == AudioFormat.ENCODING_PCM_FLOAT,
            )
        else ->
            if (AudioFormat.isValidEncoding(encoding)) {
                SampleEncoding.Other(
                    AudioFormat.toLogFriendlyEncoding(encoding).removePrefix("ENCODING_")
                )
            } else {
                null
            }
    }

internal fun AudioFormat.toSummary(): AudioFormatSummary =
    AudioFormatSummary(
        sampleRateHz = sampleRate.takeIf { it > 0 },
        channelCount = channelCount.takeIf { it > 0 },
        encoding = sampleEncodingOf(encoding),
    )
