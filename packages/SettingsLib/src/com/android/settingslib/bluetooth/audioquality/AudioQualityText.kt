/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.bluetooth.audioquality

import android.content.Context
import com.android.bluetooth.audioquality.aidl.AudioQuality
import com.android.bluetooth.audioquality.aidl.AudioQualityLevel
import com.android.settingslib.R
import kotlin.math.roundToInt

private const val BITS_PER_KILOBIT = 1000.0

/**
 * Formats text for the codec and its quality, such as "LDAC · 660 kbps" or "Fixed for aptX". It
 * shows the codec alone while the setting of the codec matches none of its levels.
 */
fun AudioQuality.formatSummary(context: Context): String =
    if (isAdjustable) {
        currentLevel?.let {
            context.getString(
                R.string.bluetooth_audio_quality_codec_and_level,
                codecType.codecName,
                it.formatLabel(context),
            )
        } ?: codecType.codecName
    } else {
        context.getString(R.string.bluetooth_audio_quality_fixed, codecType.codecName)
    }

/**
 * Formats the label of the level from its bitrate: a fixed bitrate rounded to the nearest kbps,
 * such as "660 kbps", or "Variable" or "Adaptive".
 */
fun AudioQualityLevel.formatLabel(context: Context): String =
    when (val bitrate = bitrate) {
        is AudioQualityLevel.Bitrate.Fixed ->
            context.getString(
                R.string.bluetooth_audio_quality_kbps,
                (bitrate.bitsPerSecond / BITS_PER_KILOBIT).roundToInt(),
            )
        AudioQualityLevel.Bitrate.Variable ->
            context.getString(R.string.bluetooth_audio_quality_variable)
        AudioQualityLevel.Bitrate.Adaptive ->
            context.getString(R.string.bluetooth_audio_quality_adaptive)
    }
