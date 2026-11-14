/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.media;

import android.media.OutputFormatDeviceId;

/**
 * An output device that is connected, or was connected before, with whether it switches format.
 *
 * @hide
 */
@JavaDerive(equals = true, toString = true)
parcelable OutputFormatDevice {
    OutputFormatDeviceId id;
    /**
     * The name reported by a Bluetooth or USB device when it was connected last, or null for a
     * built-in output and for a device that reported none.
     */
    @nullable String name;
    boolean connected;
    /**
     * Whether audio policy switches the device to the format of a media track that plays alone
     * on it.
     */
    boolean formatSwitchingEnabled;
}
