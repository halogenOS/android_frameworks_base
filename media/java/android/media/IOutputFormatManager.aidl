/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package android.media;

import android.media.OutputFormatDevice;
import android.media.OutputFormatDeviceId;

/**
 * Lists the output devices that audio policy can switch to the format of a media track playing
 * alone on them, and sets for each device whether it switches. The audio service stores whether
 * each device switches and sends that setting to audio policy. A device seen for the first time
 * switches.
 *
 * Callers get it through IAudioService.getOutputFormatManager.
 *
 * @hide
 */
interface IOutputFormatManager {
    /** A setFormatSwitchingEnabled call failed to store the setting. */
    const int ERROR_NOT_STORED = 1;

    /**
     * Lists the connected devices and the devices connected before, in the order first seen by the
     * audio service.
     */
    @EnforcePermission("MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED)")
    List<OutputFormatDevice> getDevices();

    /**
     * Sets whether audio policy switches the device with this id, one listed by getDevices, to the
     * format of a lone media track. The setting applies at once to the device while it is
     * connected. Throws IllegalArgumentException for an id not listed by getDevices, and
     * ServiceSpecificException with ERROR_NOT_STORED when the setting could not be stored.
     */
    @EnforcePermission("MODIFY_AUDIO_SETTINGS_PRIVILEGED")
    @JavaPassthrough(annotation="@android.annotation.RequiresPermission(android.Manifest.permission.MODIFY_AUDIO_SETTINGS_PRIVILEGED)")
    void setFormatSwitchingEnabled(in OutputFormatDeviceId id, boolean enabled);
}
