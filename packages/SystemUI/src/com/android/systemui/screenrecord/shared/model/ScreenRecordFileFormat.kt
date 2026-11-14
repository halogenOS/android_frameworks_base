/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screenrecord.shared.model

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder

enum class ScreenRecordFileFormat(
    val mediaRecorderOutputFormat: Int,
    val mediaMuxerFormat: Int,
    val fileExtension: String,
    val containerMimeType: String,
    val mediaRecorderAudioEncoder: Int,
    val audioMimeType: String,
    /** The AAC profile of the audio, null where the audio is not AAC. */
    val aacProfile: Int?,
    val audioSampleRate: Int,
) {
    MP4(
        mediaRecorderOutputFormat = MediaRecorder.OutputFormat.MPEG_4,
        mediaMuxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
        fileExtension = "mp4",
        containerMimeType = "video/mp4",
        mediaRecorderAudioEncoder = MediaRecorder.AudioEncoder.HE_AAC,
        audioMimeType = MediaFormat.MIMETYPE_AUDIO_AAC,
        aacProfile = CodecProfileLevel.AACObjectLC,
        audioSampleRate = 44100,
    ),
    WEBM(
        mediaRecorderOutputFormat = MediaRecorder.OutputFormat.WEBM,
        mediaMuxerFormat = MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM,
        fileExtension = "webm",
        containerMimeType = "video/webm",
        mediaRecorderAudioEncoder = MediaRecorder.AudioEncoder.OPUS,
        audioMimeType = MediaFormat.MIMETYPE_AUDIO_OPUS,
        aacProfile = null,
        // Opus accepts only 8, 12, 16, 24 and 48 kHz. The 44.1 kHz used for AAC does not work here:
        // https://www.rfc-editor.org/rfc/rfc6716#section-2 (checked 2026-11-14)
        audioSampleRate = 48000,
    ),
}
