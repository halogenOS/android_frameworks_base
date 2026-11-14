/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.audio;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ContentResolver;
import android.media.OutputFormatDeviceId;
import android.media.UsbDeviceIds;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Reads and writes the remembered output devices in Settings.Secure for the system user, as a JSON
 * array with one object per device.
 */
final class OutputFormatChoiceStore {
    private static final String TAG = "AS.OutputFormatChoiceStore";

    private static final String KEY_KIND = "kind";
    private static final String KEY_BLUETOOTH_ADDRESS = "bluetoothAddress";
    private static final String KEY_USB_VENDOR_ID = "usbVendorId";
    private static final String KEY_USB_PRODUCT_ID = "usbProductId";
    private static final String KEY_BUILT_IN_OUTPUT = "builtInOutput";
    private static final String KEY_NAME = "name";
    private static final String KEY_FORMAT_SWITCHING_ENABLED = "formatSwitchingEnabled";

    /** An output device seen before, with the name reported when it was connected last. */
    record RememberedDevice(@NonNull OutputFormatDeviceId id, @Nullable String name,
            boolean formatSwitchingEnabled) {}

    /** The kind of a stored device ID, stored as {@link #mStoredName}. */
    private enum IdKind {
        BLUETOOTH_ADDRESS("bluetoothAddress", OutputFormatDeviceId.bluetoothAddress),
        USB_IDS("usbIds", OutputFormatDeviceId.usbIds),
        BUILT_IN_OUTPUT("builtInOutput", OutputFormatDeviceId.builtInOutput);

        private final @NonNull String mStoredName;
        private final int mTag;

        IdKind(@NonNull String storedName, int tag) {
            mStoredName = storedName;
            mTag = tag;
        }

        static @NonNull IdKind ofId(@NonNull OutputFormatDeviceId id) {
            return Arrays.stream(values()).filter(kind -> kind.mTag == id.getTag()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown device ID " + id));
        }

        static @NonNull IdKind ofStoredName(@NonNull String storedName) throws JSONException {
            return Arrays.stream(values()).filter(kind -> kind.mStoredName.equals(storedName))
                    .findFirst()
                    .orElseThrow(() -> new JSONException("Unknown device ID kind " + storedName));
        }
    }

    private final @NonNull ContentResolver mResolver;
    private final @NonNull SettingsAdapter mSettings;

    OutputFormatChoiceStore(@NonNull ContentResolver resolver, @NonNull SettingsAdapter settings) {
        mResolver = resolver;
        mSettings = settings;
    }

    /** Reads the stored devices. A malformed device is left out. */
    @NonNull List<RememberedDevice> read() {
        String stored = mSettings.getSecureStringForUser(mResolver,
                Settings.Secure.AUDIO_DEVICE_FORMAT_SWITCHING, UserHandle.USER_SYSTEM);
        List<RememberedDevice> devices = new ArrayList<>();
        if (stored == null || stored.isEmpty()) return devices;
        JSONArray array;
        try {
            array = new JSONArray(stored);
        } catch (JSONException e) {
            Log.e(TAG, "Malformed remembered output devices", e);
            return devices;
        }
        for (int i = 0; i < array.length(); i++) {
            try {
                devices.add(fromJson(array.getJSONObject(i)));
            } catch (JSONException e) {
                Log.w(TAG, "Malformed remembered output device " + i, e);
            }
        }
        return devices;
    }

    /** Writes {@code devices} in place of the stored ones, and returns whether that succeeded. */
    boolean write(@NonNull Collection<RememberedDevice> devices) {
        JSONArray array = new JSONArray();
        devices.forEach(device -> array.put(toJson(device)));
        boolean written = mSettings.putSecureStringForUser(mResolver,
                Settings.Secure.AUDIO_DEVICE_FORMAT_SWITCHING, array.toString(),
                UserHandle.USER_SYSTEM);
        if (!written) {
            Log.e(TAG, "Could not store the remembered output devices");
        }
        return written;
    }

    private static @NonNull RememberedDevice fromJson(@NonNull JSONObject json)
            throws JSONException {
        return new RememberedDevice(idFromJson(json),
                json.isNull(KEY_NAME) ? null : json.getString(KEY_NAME),
                json.getBoolean(KEY_FORMAT_SWITCHING_ENABLED));
    }

    private static @NonNull OutputFormatDeviceId idFromJson(@NonNull JSONObject json)
            throws JSONException {
        return switch (IdKind.ofStoredName(json.getString(KEY_KIND))) {
            case BLUETOOTH_ADDRESS ->
                    OutputFormatDeviceId.bluetoothAddress(json.getString(KEY_BLUETOOTH_ADDRESS));
            case USB_IDS -> {
                UsbDeviceIds ids = new UsbDeviceIds();
                ids.vendorId = json.getInt(KEY_USB_VENDOR_ID);
                ids.productId = json.getInt(KEY_USB_PRODUCT_ID);
                yield OutputFormatDeviceId.usbIds(ids);
            }
            case BUILT_IN_OUTPUT -> OutputFormatDeviceId.builtInOutput(
                    builtInOutputFromJson(json.getInt(KEY_BUILT_IN_OUTPUT)));
        };
    }

    private static byte builtInOutputFromJson(int stored) throws JSONException {
        return switch (stored) {
            case OutputFormatDeviceId.BuiltInOutput.SPEAKER ->
                    OutputFormatDeviceId.BuiltInOutput.SPEAKER;
            case OutputFormatDeviceId.BuiltInOutput.WIRED_HEADPHONES ->
                    OutputFormatDeviceId.BuiltInOutput.WIRED_HEADPHONES;
            default -> throw new JSONException("Unknown built-in output " + stored);
        };
    }

    private static @NonNull JSONObject toJson(@NonNull RememberedDevice device) {
        OutputFormatDeviceId id = device.id();
        IdKind kind = IdKind.ofId(id);
        try {
            JSONObject json = new JSONObject()
                    .put(KEY_KIND, kind.mStoredName)
                    .putOpt(KEY_NAME, device.name())
                    .put(KEY_FORMAT_SWITCHING_ENABLED, device.formatSwitchingEnabled());
            return switch (kind) {
                case BLUETOOTH_ADDRESS -> json.put(KEY_BLUETOOTH_ADDRESS, id.getBluetoothAddress());
                case USB_IDS -> json
                        .put(KEY_USB_VENDOR_ID, id.getUsbIds().vendorId)
                        .put(KEY_USB_PRODUCT_ID, id.getUsbIds().productId);
                case BUILT_IN_OUTPUT -> json.put(KEY_BUILT_IN_OUTPUT, id.getBuiltInOutput());
            };
        } catch (JSONException e) {
            // JSONObject.put fails only for a number that is not finite, and every value here is
            // a string, an int or a boolean.
            throw new AssertionError(e);
        }
    }
}
