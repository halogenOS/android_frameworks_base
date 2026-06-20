/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate.compose

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Headset
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.android.settingslib.audiostate.AudioDevice
import com.android.settingslib.audiostate.AudioDeviceKind
import com.android.settingslib.audiostate.AudioUsage
import com.android.settingslib.audiostate.BitPerfectBlocker
import com.android.settingslib.audiostate.CaptureSource
import com.android.settingslib.audiostate.MicrophoneDirectionality
import com.android.settingslib.audiostate.MicrophoneLocation
import com.android.settingslib.audiostate.MixerKind
import com.android.settingslib.audiostate.OutputPath
import com.android.settingslib.audiostate.SampleEncoding

/** The resources of the current configuration, for the builders of the readout text. */
@Composable
@ReadOnlyComposable
internal fun resources(): Resources {
    LocalConfiguration.current
    return LocalContext.current.resources
}

@get:StringRes
internal val AudioDeviceKind?.labelRes: Int
    get() =
        when (this) {
            AudioDeviceKind.EARPIECE -> R.string.audio_state_device_earpiece
            AudioDeviceKind.SPEAKER -> R.string.audio_state_device_speaker
            AudioDeviceKind.SPEAKER_SAFE -> R.string.audio_state_device_speaker_safe
            AudioDeviceKind.BUILTIN_MIC -> R.string.audio_state_device_builtin_mic
            AudioDeviceKind.TELEPHONY -> R.string.audio_state_device_telephony
            AudioDeviceKind.REMOTE_SUBMIX -> R.string.audio_state_device_remote_submix
            AudioDeviceKind.FM_TUNER -> R.string.audio_state_device_fm_tuner
            AudioDeviceKind.TV_TUNER -> R.string.audio_state_device_tv_tuner
            AudioDeviceKind.ECHO_REFERENCE -> R.string.audio_state_device_echo_reference
            AudioDeviceKind.WIRED_HEADSET -> R.string.audio_state_device_wired_headset
            AudioDeviceKind.WIRED_HEADPHONES -> R.string.audio_state_device_wired_headphones
            AudioDeviceKind.LINE_ANALOG -> R.string.audio_state_device_line_analog
            AudioDeviceKind.LINE_DIGITAL -> R.string.audio_state_device_line_digital
            AudioDeviceKind.AUX_LINE -> R.string.audio_state_device_aux_line
            AudioDeviceKind.BLUETOOTH_SCO -> R.string.audio_state_device_bluetooth_sco
            AudioDeviceKind.BLUETOOTH_A2DP -> R.string.audio_state_device_bluetooth_a2dp
            AudioDeviceKind.BLE_HEADSET -> R.string.audio_state_device_ble_headset
            AudioDeviceKind.BLE_SPEAKER -> R.string.audio_state_device_ble_speaker
            AudioDeviceKind.BLE_BROADCAST -> R.string.audio_state_device_ble_broadcast
            AudioDeviceKind.HEARING_AID -> R.string.audio_state_device_hearing_aid
            AudioDeviceKind.USB_DEVICE -> R.string.audio_state_device_usb_device
            AudioDeviceKind.USB_ACCESSORY -> R.string.audio_state_device_usb_accessory
            AudioDeviceKind.USB_HEADSET -> R.string.audio_state_device_usb_headset
            AudioDeviceKind.HDMI -> R.string.audio_state_device_hdmi
            AudioDeviceKind.HDMI_ARC -> R.string.audio_state_device_hdmi_arc
            AudioDeviceKind.HDMI_EARC -> R.string.audio_state_device_hdmi_earc
            AudioDeviceKind.DOCK -> R.string.audio_state_device_dock
            AudioDeviceKind.DOCK_ANALOG -> R.string.audio_state_device_dock_analog
            AudioDeviceKind.FM -> R.string.audio_state_device_fm
            AudioDeviceKind.IP -> R.string.audio_state_device_ip
            AudioDeviceKind.BUS -> R.string.audio_state_device_bus
            AudioDeviceKind.MULTICHANNEL_GROUP -> R.string.audio_state_device_multichannel_group
            null -> R.string.audio_state_device_unknown
        }

internal val AudioDeviceKind?.icon: ImageVector
    get() =
        when (this) {
            AudioDeviceKind.USB_DEVICE,
            AudioDeviceKind.USB_ACCESSORY,
            AudioDeviceKind.USB_HEADSET -> Icons.Outlined.Usb
            AudioDeviceKind.BLUETOOTH_SCO,
            AudioDeviceKind.BLUETOOTH_A2DP,
            AudioDeviceKind.BLE_HEADSET,
            AudioDeviceKind.BLE_SPEAKER,
            AudioDeviceKind.BLE_BROADCAST,
            AudioDeviceKind.HEARING_AID -> Icons.Outlined.Bluetooth
            AudioDeviceKind.WIRED_HEADSET,
            AudioDeviceKind.WIRED_HEADPHONES -> Icons.Outlined.Headset
            AudioDeviceKind.BUILTIN_MIC,
            AudioDeviceKind.FM_TUNER,
            AudioDeviceKind.TV_TUNER,
            AudioDeviceKind.ECHO_REFERENCE -> Icons.Outlined.Mic
            AudioDeviceKind.EARPIECE,
            AudioDeviceKind.SPEAKER,
            AudioDeviceKind.SPEAKER_SAFE,
            AudioDeviceKind.TELEPHONY,
            AudioDeviceKind.REMOTE_SUBMIX,
            AudioDeviceKind.LINE_ANALOG,
            AudioDeviceKind.LINE_DIGITAL,
            AudioDeviceKind.AUX_LINE,
            AudioDeviceKind.HDMI,
            AudioDeviceKind.HDMI_ARC,
            AudioDeviceKind.HDMI_EARC,
            AudioDeviceKind.DOCK,
            AudioDeviceKind.DOCK_ANALOG,
            AudioDeviceKind.FM,
            AudioDeviceKind.IP,
            AudioDeviceKind.BUS,
            AudioDeviceKind.MULTICHANNEL_GROUP,
            null -> Icons.Outlined.Speaker
        }

@get:StringRes
internal val AudioUsage.labelRes: Int
    get() =
        when (this) {
            AudioUsage.MEDIA -> R.string.audio_state_usage_media
            AudioUsage.CALL -> R.string.audio_state_usage_call
            AudioUsage.ALARM -> R.string.audio_state_usage_alarm
            AudioUsage.NOTIFICATION -> R.string.audio_state_usage_notification
            AudioUsage.RINGTONE -> R.string.audio_state_usage_ringtone
            AudioUsage.GAME -> R.string.audio_state_usage_game
            AudioUsage.ASSISTANT -> R.string.audio_state_usage_assistant
            AudioUsage.OTHER -> R.string.audio_state_usage_other
        }

@get:StringRes
internal val CaptureSource.labelRes: Int
    get() =
        when (this) {
            CaptureSource.MIC -> R.string.audio_state_capture_mic
            CaptureSource.VOICE_RECOGNITION -> R.string.audio_state_capture_voice_recognition
            CaptureSource.VOICE_COMMUNICATION -> R.string.audio_state_capture_voice_communication
            CaptureSource.CAMCORDER -> R.string.audio_state_capture_camcorder
            CaptureSource.UNPROCESSED -> R.string.audio_state_capture_unprocessed
            CaptureSource.VOICE_UPLINK -> R.string.audio_state_capture_voice_uplink
            CaptureSource.VOICE_DOWNLINK -> R.string.audio_state_capture_voice_downlink
            CaptureSource.VOICE_CALL -> R.string.audio_state_capture_voice_call
            CaptureSource.VOICE_PERFORMANCE -> R.string.audio_state_capture_voice_performance
            CaptureSource.REMOTE_SUBMIX -> R.string.audio_state_capture_remote_submix
            CaptureSource.OTHER -> R.string.audio_state_capture_other
        }

@get:StringRes
internal val MicrophoneLocation.labelRes: Int
    get() =
        when (this) {
            MicrophoneLocation.MAIN_BODY -> R.string.audio_state_mic_main_body
            MicrophoneLocation.MAIN_BODY_MOVABLE -> R.string.audio_state_mic_main_body_movable
            MicrophoneLocation.PERIPHERAL -> R.string.audio_state_mic_peripheral
        }

@get:StringRes
internal val MicrophoneDirectionality.labelRes: Int
    get() =
        when (this) {
            MicrophoneDirectionality.OMNI -> R.string.audio_state_mic_omni
            MicrophoneDirectionality.BI_DIRECTIONAL -> R.string.audio_state_mic_bi_directional
            MicrophoneDirectionality.CARDIOID -> R.string.audio_state_mic_cardioid
            MicrophoneDirectionality.HYPER_CARDIOID -> R.string.audio_state_mic_hyper_cardioid
            MicrophoneDirectionality.SUPER_CARDIOID -> R.string.audio_state_mic_super_cardioid
        }

@get:StringRes
internal val OutputPath.labelRes: Int
    get() =
        when (this) {
            is OutputPath.Mixer ->
                when (kind) {
                    MixerKind.MIXER -> R.string.audio_state_path_mixer
                    MixerKind.DUPLICATING -> R.string.audio_state_path_duplicating
                    MixerKind.SPATIALIZER -> R.string.audio_state_path_spatializer
                    MixerKind.BIT_PERFECT -> R.string.audio_state_path_bit_perfect
                }
            OutputPath.Direct -> R.string.audio_state_path_direct
            OutputPath.Offload -> R.string.audio_state_path_offload
            OutputPath.Mmap -> R.string.audio_state_path_mmap
        }

internal fun Resources.label(blocker: BitPerfectBlocker): String =
    when (blocker) {
        BitPerfectBlocker.MixerStage -> getString(R.string.audio_state_reason_mixer_stage)
        is BitPerfectBlocker.MultipleTracks ->
            getString(R.string.audio_state_reason_multiple_tracks, blocker.audibleTracks)
        BitPerfectBlocker.Resampling -> getString(R.string.audio_state_reason_resampling)
    }

/** "This device" for the phone's own endpoints, else the product name or the kind. */
internal fun Resources.displayName(device: AudioDevice): String =
    when {
        device.isHostEndpoint -> getString(R.string.audio_state_this_device)
        else -> device.name ?: getString(device.kind.labelRes)
    }

/** Joins the labels of several values, e.g. "Media · Game". */
internal fun Resources.joinLabels(labels: List<String>, @StringRes joinRes: Int): String? =
    labels.reduceOrNull { joined, next -> getString(joinRes, joined, next) }

/** The encoding of a device, e.g. "PCM 16-bit", "PCM float", "AC3". */
internal fun Resources.label(encoding: SampleEncoding): String =
    when (encoding) {
        is SampleEncoding.Pcm ->
            if (encoding.isFloat) {
                getString(R.string.audio_state_pcm_float)
            } else {
                getString(R.string.audio_state_pcm_bits, encoding.bitsPerSample)
            }
        is SampleEncoding.Other -> encoding.name
    }

/** An exact rate, e.g. "44.1 kHz" or "11.025 kHz". */
internal fun Resources.rate(rateHz: Int): String {
    val kiloHertz = rateHz.toBigDecimal().movePointLeft(3).stripTrailingZeros().toPlainString()
    return getString(R.string.audio_state_rate_khz, kiloHertz)
}

/** Several rates, e.g. "44.1 kHz / 48 kHz". */
internal fun Resources.rates(ratesHz: List<Int>): String = ratesHz.joinToString(" / ") { rate(it) }

internal fun Resources.channels(count: Int): String =
    when (count) {
        1 -> getString(R.string.audio_state_mono)
        2 -> getString(R.string.audio_state_stereo)
        else -> getString(R.string.audio_state_channels, count)
    }
