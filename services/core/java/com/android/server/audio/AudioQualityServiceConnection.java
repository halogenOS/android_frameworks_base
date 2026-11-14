/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.server.audio;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.media.AudioFormat;

import com.android.bluetooth.audioquality.aidl.AudioQuality;
import com.android.bluetooth.audioquality.aidl.AudioQualityCall;
import com.android.bluetooth.audioquality.aidl.AudioQualityCalls;
import com.android.bluetooth.audioquality.aidl.AudioQualityServiceBinding;
import com.android.bluetooth.audioquality.aidl.IAudioQuality;

/**
 * Connection of the audio service to the audio quality service of the Bluetooth app, bound as the
 * current user, the user the Bluetooth stack runs in. The calls return what the service returns
 * while the binding is connected.
 */
final class AudioQualityServiceConnection {
    private final @NonNull Context mContext;
    private final @NonNull AudioQualityServiceBinding mBinding;

    // The service while the binding is connected. The main thread sets it, the broker thread reads
    // it.
    private volatile @Nullable IAudioQuality mService;

    /** {@code onConnected} runs on the main thread each time the binding connects. */
    AudioQualityServiceConnection(@NonNull Context context, @NonNull Runnable onConnected) {
        mContext = context;
        mBinding = new AudioQualityServiceBinding(context, service -> {
            mService = service;
            if (service != null) {
                onConnected.run();
            }
        });
    }

    /** Binds the service. The binding logs when the system cannot bind it. */
    void bind() {
        boolean unusedBound = mBinding.bind();
    }

    void unbind() {
        mBinding.unbind();
        mService = null;
    }

    /** Gets the audio quality of the A2DP codec used by {@code device}. */
    @NonNull AudioQualityCall<AudioQuality> getQuality(@NonNull BluetoothDevice device) {
        return AudioQualityCalls.callService(mService,
                service -> service.getQuality(device, mContext.getAttributionSource()));
    }

    /**
     * Requests the sample rate and the encoding of {@code format} on the A2DP codec used by
     * {@code device}. A returned call has the BluetoothStatusCodes value returned by the service.
     */
    @NonNull AudioQualityCall<Integer> requestPcmFormat(
            @NonNull BluetoothDevice device, @NonNull AudioFormat format) {
        return AudioQualityCalls.callService(mService,
                service -> service.requestPcmFormat(device, format,
                        mContext.getAttributionSource()));
    }
}
