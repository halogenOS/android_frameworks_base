/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.audio;

import static android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED;

import android.annotation.EnforcePermission;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.media.AudioDeviceAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.media.AudioSystem;
import android.media.IOutputFormatManager;
import android.media.OutputFormatDevice;
import android.media.OutputFormatDeviceId;
import android.media.UsbDeviceIds;
import android.media.audio.common.AidlConversion;
import android.media.audio.common.AudioDeviceDescription;
import android.os.Binder;
import android.os.PermissionEnforcer;
import android.os.ServiceSpecificException;

import com.android.internal.annotations.GuardedBy;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Remembers each output device that audio policy can switch to the format of a lone media track,
 * and tells audio policy whether the device switches format before the device connects, and again
 * when that changes. Each time audio policy starts, it remembers the attached outputs, such as the
 * built-in speaker, and tells audio policy again about every device told before.
 */
final class OutputFormatSwitching extends IOutputFormatManager.Stub
        implements AudioDeviceInventory.OutputConnectingListener {
    private final @NonNull AudioPolicyFacade mAudioPolicy;

    private final Object mLock = new Object();

    /**
     * Makes one call to audio policy at a time. Each call sends the setting stored when the call
     * runs, so the last call for a device sends the latest setting. Taken before {@link #mLock}.
     */
    private final Object mAudioPolicyCallLock = new Object();

    @GuardedBy("mLock")
    private final @NonNull OutputFormatChoices mChoices;

    /** The output device types switched by audio policy, read each time audio policy starts. */
    @GuardedBy("mLock")
    private @NonNull Set<AudioDeviceDescription> mSwitchingDeviceTypes = Set.of();

    /** The IDs of each connected USB audio device, by its type and address. */
    @GuardedBy("mLock")
    private final HashMap<AudioDeviceAttributes, UsbDeviceIds> mUsbIdsByDevice = new HashMap<>();

    /** The devices, by type and address, that audio policy was told about. */
    @GuardedBy("mLock")
    private final Set<AudioDeviceAttributes> mDevicesToldToAudioPolicy = new HashSet<>();

    OutputFormatSwitching(@NonNull OutputFormatChoiceStore store,
            @NonNull AudioPolicyFacade audioPolicy, @NonNull PermissionEnforcer enforcer) {
        super(enforcer);
        mAudioPolicy = audioPolicy;
        mChoices = new OutputFormatChoices(store);
        audioPolicy.registerOnStartTask(this::onAudioPolicyStarted);
    }

    /** Remembers {@code device}, and tells audio policy whether it switches format. */
    @Override
    public void onOutputConnecting(@NonNull AudioDeviceAttributes device) {
        remember(device);
        tellAudioPolicy(List.of(typeAndAddressOf(device)));
    }

    /**
     * Sets {@code ids} as the IDs of the USB audio device {@code device} while it is connected.
     * Called before the device connects and before it disconnects.
     */
    void setUsbDeviceConnectionState(@NonNull AudioDeviceAttributes device,
            @NonNull UsbDeviceIds ids, boolean connected) {
        AudioDeviceAttributes typeAndAddress = typeAndAddressOf(device);
        boolean told;
        synchronized (mLock) {
            if (connected) {
                mUsbIdsByDevice.put(typeAndAddress, ids);
                return;
            }
            mUsbIdsByDevice.remove(typeAndAddress);
            told = mDevicesToldToAudioPolicy.remove(typeAndAddress);
        }
        if (told) {
            // Audio policy switches a device that was never set, so another USB device at the same
            // ALSA card address starts from there.
            synchronized (mAudioPolicyCallLock) {
                mAudioPolicy.setFormatSwitchingEnabled(typeAndAddress, true);
            }
        }
    }

    @Override
    @EnforcePermission(MODIFY_AUDIO_SETTINGS_PRIVILEGED)
    public @NonNull List<OutputFormatDevice> getDevices() {
        super.getDevices_enforcePermission();
        List<AudioDeviceAttributes> connectedOutputs = listConnectedOutputs();
        synchronized (mLock) {
            Set<OutputFormatDeviceId> connected = connectedOutputs.stream()
                    .map(this::idOf)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return mChoices.getDevices().stream()
                    .map(remembered -> {
                        OutputFormatDevice device = new OutputFormatDevice();
                        device.id = remembered.id();
                        device.name = remembered.name();
                        device.connected = connected.contains(remembered.id());
                        device.formatSwitchingEnabled = remembered.formatSwitchingEnabled();
                        return device;
                    })
                    .toList();
        }
    }

    @Override
    @EnforcePermission(MODIFY_AUDIO_SETTINGS_PRIVILEGED)
    public void setFormatSwitchingEnabled(@NonNull OutputFormatDeviceId id, boolean enabled) {
        super.setFormatSwitchingEnabled_enforcePermission();
        List<AudioDeviceAttributes> devices;
        synchronized (mLock) {
            if (!mChoices.setFormatSwitchingEnabled(id, enabled)) {
                throw new ServiceSpecificException(ERROR_NOT_STORED,
                        "Could not store whether " + id + " switches format");
            }
            devices = mDevicesToldToAudioPolicy.stream()
                    .filter(device -> id.equals(idOf(device)))
                    .toList();
        }
        Binder.withCleanCallingIdentity(() -> tellAudioPolicy(devices));
    }

    private void onAudioPolicyStarted() {
        List<AudioDeviceDescription> switchingDeviceTypes =
                mAudioPolicy.getFormatSwitchingDeviceTypes();
        synchronized (mLock) {
            mSwitchingDeviceTypes = Set.copyOf(switchingDeviceTypes);
        }
        listConnectedOutputs().forEach(this::remember);
        List<AudioDeviceAttributes> devices;
        synchronized (mLock) {
            devices = List.copyOf(mDevicesToldToAudioPolicy);
        }
        tellAudioPolicy(devices);
    }

    /** Remembers {@code device} under its ID, as a device to tell audio policy about. */
    private void remember(@NonNull AudioDeviceAttributes device) {
        synchronized (mLock) {
            OutputFormatDeviceId id = idOf(device);
            if (id == null) return;
            mChoices.remember(id, reportedNameOf(id, device));
            mDevicesToldToAudioPolicy.add(typeAndAddressOf(device));
        }
    }

    /**
     * Tells audio policy whether each of {@code devices} switches format, skipping a device that
     * audio policy does not switch.
     */
    private void tellAudioPolicy(@NonNull Collection<AudioDeviceAttributes> devices) {
        synchronized (mAudioPolicyCallLock) {
            for (AudioDeviceAttributes device : devices) {
                findFormatSwitchingEnabled(device).ifPresent(
                        enabled -> mAudioPolicy.setFormatSwitchingEnabled(device, enabled));
            }
        }
    }

    private @NonNull Optional<Boolean> findFormatSwitchingEnabled(
            @NonNull AudioDeviceAttributes device) {
        synchronized (mLock) {
            return Optional.ofNullable(idOf(device)).map(mChoices::isFormatSwitchingEnabled);
        }
    }

    /**
     * The ID under which {@code device} is remembered, or null for a device that audio policy does
     * not switch, and for a USB device without IDs.
     */
    @GuardedBy("mLock")
    private @Nullable OutputFormatDeviceId idOf(@NonNull AudioDeviceAttributes device) {
        int type = device.getInternalType();
        if (!mSwitchingDeviceTypes.contains(
                AidlConversion.api2aidl_NativeType_AudioDeviceDescription(type))) {
            return null;
        }
        if (AudioSystem.DEVICE_OUT_ALL_A2DP_SET.contains(type)) {
            return OutputFormatDeviceId.bluetoothAddress(device.getAddress());
        }
        if (AudioSystem.DEVICE_OUT_ALL_USB_SET.contains(type)) {
            UsbDeviceIds ids = mUsbIdsByDevice.get(typeAndAddressOf(device));
            return ids == null ? null : OutputFormatDeviceId.usbIds(ids);
        }
        return switch (type) {
            case AudioSystem.DEVICE_OUT_SPEAKER -> OutputFormatDeviceId.builtInOutput(
                    OutputFormatDeviceId.BuiltInOutput.SPEAKER);
            case AudioSystem.DEVICE_OUT_WIRED_HEADSET, AudioSystem.DEVICE_OUT_WIRED_HEADPHONE ->
                    OutputFormatDeviceId.builtInOutput(
                            OutputFormatDeviceId.BuiltInOutput.WIRED_HEADPHONES);
            default -> null;
        };
    }

    /**
     * The name reported by a Bluetooth or USB {@code device}, or null for one that reported none
     * and for a built-in output, whose drivers report names such as "h2w".
     */
    private static @Nullable String reportedNameOf(@NonNull OutputFormatDeviceId id,
            @NonNull AudioDeviceAttributes device) {
        return id.getTag() == OutputFormatDeviceId.builtInOutput || device.getName().isEmpty()
                ? null
                : device.getName();
    }

    /** {@code device} without its name and profiles, as audio policy identifies it. */
    private static @NonNull AudioDeviceAttributes typeAndAddressOf(
            @NonNull AudioDeviceAttributes device) {
        return new AudioDeviceAttributes(device.getInternalType(), device.getAddress());
    }

    private static @NonNull List<AudioDeviceAttributes> listConnectedOutputs() {
        AudioDeviceInfo[] outputs = AudioManager.getDevicesStatic(AudioManager.GET_DEVICES_OUTPUTS);
        return Arrays.stream(outputs).map(AudioDeviceAttributes::new).toList();
    }
}
