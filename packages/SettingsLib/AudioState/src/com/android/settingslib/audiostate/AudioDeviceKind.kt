/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate

/** The kind of an audio device, one per AudioDeviceInfo type. */
enum class AudioDeviceKind(
    /** The device is part of the phone or tablet itself, not an accessory. */
    val isHostEndpoint: Boolean = false,
    /** The device is a Bluetooth accessory that reports its battery level. */
    val reportsAccessoryBattery: Boolean = false,
) {
    EARPIECE(isHostEndpoint = true),
    SPEAKER(isHostEndpoint = true),
    SPEAKER_SAFE(isHostEndpoint = true),
    BUILTIN_MIC(isHostEndpoint = true),
    TELEPHONY(isHostEndpoint = true),
    REMOTE_SUBMIX(isHostEndpoint = true),
    FM_TUNER(isHostEndpoint = true),
    TV_TUNER(isHostEndpoint = true),
    ECHO_REFERENCE(isHostEndpoint = true),
    WIRED_HEADSET,
    WIRED_HEADPHONES,
    LINE_ANALOG,
    LINE_DIGITAL,
    AUX_LINE,
    BLUETOOTH_SCO(reportsAccessoryBattery = true),
    BLUETOOTH_A2DP(reportsAccessoryBattery = true),
    BLE_HEADSET(reportsAccessoryBattery = true),
    BLE_SPEAKER(reportsAccessoryBattery = true),
    BLE_BROADCAST,
    HEARING_AID(reportsAccessoryBattery = true),
    USB_DEVICE,
    USB_ACCESSORY,
    USB_HEADSET,
    HDMI,
    HDMI_ARC,
    HDMI_EARC,
    DOCK,
    DOCK_ANALOG,
    FM,
    IP,
    BUS,
    MULTICHANNEL_GROUP,
}
