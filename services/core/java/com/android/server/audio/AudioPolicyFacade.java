/*
 * Copyright 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.audio;

import android.media.AudioDeviceAttributes;
import android.media.audio.common.AudioDeviceDescription;

import com.android.media.permission.INativePermissionController;

import java.util.List;

/**
 * Facade to IAudioPolicyService which fulfills AudioService dependencies.
 * See @link{IAudioPolicyService.aidl}
 */
public interface AudioPolicyFacade {
    public boolean isHotwordStreamSupported(boolean lookbackAudio);
    public INativePermissionController getPermissionController();
    public void registerOnStartTask(Runnable r);
    public void setHardeningOverride(byte hardeningOverride);
    /**
     * Sets whether audio policy switches device to the format of a media track that plays alone
     * on it. Does nothing while audio policy is not running, and logs an output that fails to open
     * again at its default format.
     */
    public void setFormatSwitchingEnabled(AudioDeviceAttributes device, boolean enabled);
    /**
     * The output device types that audio policy switches to the format of a lone media track, or
     * none when audio policy stops before it returns them.
     */
    public List<AudioDeviceDescription> getFormatSwitchingDeviceTypes();
    /**
     * Tells audio policy that the format request it sent for the track with the port ID
     * {@code trackPortId} is done, so that the track starts where it waits for the request. Does
     * nothing while audio policy is not running.
     */
    public void onDeviceFormatRequestDone(int trackPortId);
    public boolean isServiceAvailable();
}
