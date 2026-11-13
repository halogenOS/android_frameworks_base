/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.bluetooth.audioquality

import android.bluetooth.audioquality.AudioQuality
import android.bluetooth.audioquality.AudioQualityLevel
import android.content.Context
import com.android.settingslib.R
import kotlin.math.roundToInt

private const val BITS_PER_KILOBIT = 1000.0

/**
 * Formats text for the codec and its quality, such as "LDAC · Balanced" or "Fixed for aptX". It
 * shows the codec alone while the setting of the codec matches none of its levels.
 */
fun AudioQuality.formatSummary(context: Context): String =
    if (isAdjustable) {
        formatCodecAndLevel(context, currentLevel?.name)
    } else {
        context.getString(R.string.bluetooth_audio_quality_fixed, codecType.codecName)
    }

/** Formats the codec and the level with its bitrate, such as "LDAC · Balanced (660 kbps)". */
fun AudioQuality.formatSummaryWithBitrate(context: Context): String =
    formatCodecAndLevel(context, currentLevel?.formatNameWithBitrate(context))

private fun AudioQuality.formatCodecAndLevel(context: Context, levelText: String?): String =
    levelText?.let {
        context.getString(R.string.bluetooth_audio_quality_codec_and_level, codecType.codecName, it)
    } ?: codecType.codecName

/**
 * Formats the name of the level together with the bitrate set by the level, such as "Balanced (660
 * kbps)".
 */
fun AudioQualityLevel.formatNameWithBitrate(context: Context): String =
    bitrate?.let {
        context.getString(
            R.string.bluetooth_audio_quality_level_with_bitrate,
            name,
            it.format(context),
        )
    } ?: name

/** Formats the bitrate rounded to the nearest kbps, such as "660 kbps", or as "adaptive". */
private fun AudioQualityLevel.Bitrate.format(context: Context): String =
    when (this) {
        is AudioQualityLevel.Bitrate.Fixed ->
            context.getString(
                R.string.bluetooth_audio_quality_kbps,
                (bitsPerSecond / BITS_PER_KILOBIT).roundToInt(),
            )
        is AudioQualityLevel.Bitrate.Adaptive ->
            context.getString(R.string.bluetooth_audio_quality_adaptive)
        else -> error("Unknown bitrate $this")
    }
