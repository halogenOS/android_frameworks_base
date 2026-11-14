/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.media;

import android.media.UsbDeviceIds;

/**
 * The identity under which the audio service stores whether an output device switches format.
 *
 * @hide
 */
@JavaDerive(equals = true, toString = true)
union OutputFormatDeviceId {
    /** An output built into the phone, of which the phone has one. */
    @Backing(type="byte")
    enum BuiltInOutput {
        SPEAKER = 0,
        /** The headphone jack, or a USB-C analog adapter, with or without a microphone. */
        WIRED_HEADPHONES = 1,
    }

    /** The Bluetooth address of a Bluetooth A2DP device. */
    String bluetoothAddress;
    /** The IDs of a USB audio device. */
    UsbDeviceIds usbIds;
    BuiltInOutput builtInOutput;
}
