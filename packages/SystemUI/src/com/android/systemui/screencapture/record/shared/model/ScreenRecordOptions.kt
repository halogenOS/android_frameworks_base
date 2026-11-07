/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screencapture.record.shared.model

import com.android.systemui.screenrecord.ScreenRecordingAudioSource

/**
 * Screen recording options a user chose, saved for their next recording. The default values are
 * what a user who never changed an option gets.
 */
data class ScreenRecordOptions(
    val audioSource: ScreenRecordingAudioSource = ScreenRecordingAudioSource.NONE,
    /**
     * The audio source to record once audio is turned on. It is stored while [audioSource] is NONE
     * too.
     */
    val audioSourceWhenEnabled: ScreenRecordingAudioSource = ScreenRecordingAudioSource.INTERNAL,
    val shouldShowTaps: Boolean = false,
    val longerDuration: Boolean = false,
    val hevc: Boolean = false,
    val skipCountdown: Boolean = false,
)
