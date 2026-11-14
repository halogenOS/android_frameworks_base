/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screenrecord.shared.model

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat
import android.media.MediaRecorder

enum class ScreenRecordVideoCodec(
    val codecMimeType: String,
    val mediaRecorderVideoEncoder: Int,
    val profile: Int,
    val fileFormat: ScreenRecordFileFormat,
) {
    H264(
        codecMimeType = MediaFormat.MIMETYPE_VIDEO_AVC,
        mediaRecorderVideoEncoder = MediaRecorder.VideoEncoder.H264,
        profile = CodecProfileLevel.AVCProfileHigh,
        fileFormat = ScreenRecordFileFormat.MP4,
    ),
    HEVC(
        codecMimeType = MediaFormat.MIMETYPE_VIDEO_HEVC,
        mediaRecorderVideoEncoder = MediaRecorder.VideoEncoder.HEVC,
        profile = CodecProfileLevel.HEVCProfileMain,
        fileFormat = ScreenRecordFileFormat.MP4,
    ),
    VP9(
        codecMimeType = MediaFormat.MIMETYPE_VIDEO_VP9,
        mediaRecorderVideoEncoder = MediaRecorder.VideoEncoder.VP9,
        profile = CodecProfileLevel.VP9Profile0,
        fileFormat = ScreenRecordFileFormat.WEBM,
    ),
    AV1(
        codecMimeType = MediaFormat.MIMETYPE_VIDEO_AV1,
        mediaRecorderVideoEncoder = MediaRecorder.VideoEncoder.AV1,
        profile = CodecProfileLevel.AV1ProfileMain8,
        fileFormat = ScreenRecordFileFormat.MP4,
    );

    companion object {
        /** The codec offered on every device, recorded until another one is picked. */
        @JvmField val DEFAULT = H264
    }
}
