/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate.compose

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.settingslib.audiostate.AudioFormatSummary
import com.android.settingslib.audiostate.SampleEncoding

/** A line in a readout: a value on its own, or a label with its value. */
internal sealed interface ReadoutLine {
    data class Mono(val value: String) : ReadoutLine

    data class Pair(val label: String, val value: String) : ReadoutLine
}

@Composable
internal fun ReadoutRow(line: ReadoutLine) {
    when (line) {
        is ReadoutLine.Mono ->
            Text(
                text = line.value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 1.dp),
            )
        is ReadoutLine.Pair ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    text = line.value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End,
                )
            }
    }
}

@Composable
internal fun SectionHeader(text: String) {
    Spacer(Modifier.height(20.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.5.sp,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
internal fun ActiveBadge() {
    Box(
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(R.string.audio_state_active),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp,
        )
    }
}

/** A muted chip naming a state, e.g. "Idle", so the state does not rest on color alone. */
@Composable
internal fun StatusChip(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(50))
                // Opaque, so the card and its recess do not show through and mute the chip.
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.5.sp,
        )
    }
}

@Composable
internal fun BatteryChip(percent: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = batteryIconFor(percent),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.audio_state_battery_percent, percent),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun batteryIconFor(percent: Int): ImageVector =
    when {
        percent >= 90 -> Icons.Outlined.BatteryFull
        percent >= 20 -> Icons.Outlined.BatteryStd
        else -> Icons.Outlined.BatteryAlert
    }

/** A format on one line, e.g. "44.1 kHz · 16 bit · Stereo · PCM"; null when nothing is known. */
internal fun Resources.formatLine(format: AudioFormatSummary): String? {
    val encoding = format.encoding
    val parts = buildList {
        format.sampleRateHz?.let { add(rate(it)) }
        if (encoding is SampleEncoding.Pcm) add(sampleBits(encoding))
        format.channelCount?.let { add(channels(it)) }
        when (encoding) {
            is SampleEncoding.Pcm -> add(getString(R.string.audio_state_pcm))
            is SampleEncoding.Other -> add(encoding.name)
            null -> {}
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(PART_SEPARATOR)
}

/** The bit depth of a sample, e.g. "16 bit" or "32 bit float". */
internal fun Resources.sampleBits(pcm: SampleEncoding.Pcm): String =
    if (pcm.isFloat) {
        getString(R.string.audio_state_bits_float, pcm.bitsPerSample)
    } else {
        getString(R.string.audio_state_bits, pcm.bitsPerSample)
    }

/** The bit depth inside the mixer row, e.g. "16" or "32 float". */
internal fun Resources.depth(pcm: SampleEncoding.Pcm): String =
    if (pcm.isFloat) {
        getString(R.string.audio_state_depth_float, pcm.bitsPerSample)
    } else {
        getString(R.string.audio_state_depth, pcm.bitsPerSample)
    }

internal const val PART_SEPARATOR = "  ·  "
