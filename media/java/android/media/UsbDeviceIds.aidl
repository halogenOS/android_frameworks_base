/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.media;

/**
 * The vendor ID and the product ID of a USB device.
 *
 * @hide
 */
@JavaDerive(equals = true, toString = true)
parcelable UsbDeviceIds {
    int vendorId;
    int productId;
}
