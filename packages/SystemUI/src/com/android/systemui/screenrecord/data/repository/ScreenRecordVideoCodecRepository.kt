/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screenrecord.data.repository

import android.media.MediaCodecList
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.screenrecord.shared.model.ScreenRecordVideoCodec
import javax.inject.Inject

interface ScreenRecordVideoCodecRepository {
    /** The default codec, then every other codec the device has a hardware encoder for. */
    val offered: List<ScreenRecordVideoCodec>

    /** [codec] where the device offers it, the default codec otherwise. */
    fun offeredOrDefault(codec: ScreenRecordVideoCodec): ScreenRecordVideoCodec =
        if (codec in offered) codec else ScreenRecordVideoCodec.DEFAULT
}

@SysUISingleton
class ScreenRecordVideoCodecRepositoryImpl @Inject constructor() :
    ScreenRecordVideoCodecRepository {

    override val offered: List<ScreenRecordVideoCodec> by lazy {
        val hardwareEncodedTypes =
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filter { it.isEncoder && it.isHardwareAccelerated }
                .flatMap { it.supportedTypes.asList() }
        val hardwareEncoded =
            ScreenRecordVideoCodec.entries.filter { codec ->
                hardwareEncodedTypes.any { it.equals(codec.codecMimeType, ignoreCase = true) }
            }
        (listOf(ScreenRecordVideoCodec.DEFAULT) + hardwareEncoded).distinct()
    }
}
