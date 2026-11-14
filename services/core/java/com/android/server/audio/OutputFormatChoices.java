/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.audio;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.OutputFormatDeviceId;

import com.android.server.audio.OutputFormatChoiceStore.RememberedDevice;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Remembers each output device seen, with whether it switches format, in the order the devices
 * were first seen. Callers synchronize the calls.
 */
final class OutputFormatChoices {
    /** Product decision: a device seen for the first time switches its format. */
    private static final boolean FORMAT_SWITCHING_OF_NEW_DEVICE = true;

    private final @NonNull OutputFormatChoiceStore mStore;
    /** The stored devices. Replaced only once a change is stored. */
    private @NonNull LinkedHashMap<OutputFormatDeviceId, RememberedDevice> mDevices =
            new LinkedHashMap<>();

    OutputFormatChoices(@NonNull OutputFormatChoiceStore store) {
        mStore = store;
        store.read().forEach(device -> mDevices.put(device.id(), device));
    }

    /**
     * Remembers the device with {@code id} and {@code name}. A device that could not be stored
     * stays as it was.
     */
    void remember(@NonNull OutputFormatDeviceId id, @Nullable String name) {
        boolean unusedStored = store(new RememberedDevice(id, name, isFormatSwitchingEnabled(id)));
    }

    /** Whether the device with {@code id} switches format, also when it was never seen. */
    boolean isFormatSwitchingEnabled(@NonNull OutputFormatDeviceId id) {
        RememberedDevice known = mDevices.get(id);
        return known == null ? FORMAT_SWITCHING_OF_NEW_DEVICE : known.formatSwitchingEnabled();
    }

    /**
     * Sets whether the device with {@code id} switches format, and returns whether that is stored.
     *
     * @throws IllegalArgumentException for a device never seen
     */
    boolean setFormatSwitchingEnabled(@NonNull OutputFormatDeviceId id, boolean enabled) {
        RememberedDevice known = mDevices.get(id);
        if (known == null) {
            throw new IllegalArgumentException("Unknown output device " + id);
        }
        return store(new RememberedDevice(id, known.name(), enabled));
    }

    @NonNull List<RememberedDevice> getDevices() {
        return List.copyOf(mDevices.values());
    }

    private boolean store(@NonNull RememberedDevice device) {
        if (device.equals(mDevices.get(device.id()))) return true;
        LinkedHashMap<OutputFormatDeviceId, RememberedDevice> devices =
                new LinkedHashMap<>(mDevices);
        devices.put(device.id(), device);
        if (!mStore.write(devices.values())) return false;
        mDevices = devices;
        return true;
    }
}
