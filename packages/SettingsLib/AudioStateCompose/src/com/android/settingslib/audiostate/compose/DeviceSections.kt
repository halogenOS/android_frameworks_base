/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate.compose

import android.content.res.Resources
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.settingslib.audiostate.AudioDevice
import com.android.settingslib.audiostate.AudioInputRoute
import com.android.settingslib.audiostate.MicrophoneInfoSummary

/** The recordings in progress, each as source → input device. */
@Composable
internal fun InputRoutesSection(inputRoutes: List<AudioInputRoute>) {
    if (inputRoutes.isEmpty()) return
    SectionHeader(stringResource(R.string.audio_state_section_capture))
    val resources = resources()
    inputRoutes.forEach { input ->
        val stages = resources.inputStages(input)
        stages.forEachIndexed { index, stage ->
            ChainStage(stage = stage, isLast = index == stages.lastIndex)
        }
    }
}

private fun Resources.inputStages(input: AudioInputRoute): List<Stage> {
    val device = input.inputDevice
    return listOf(
        Stage(
            icon = Icons.Outlined.Mic,
            title = getString(R.string.audio_state_stage_source),
            subtitle = getString(input.source.labelRes),
            lines =
                listOf(
                    ReadoutLine.Mono(
                        input.captureFormat?.let { formatLine(it) }
                            ?: getString(R.string.audio_state_format_unavailable)
                    )
                ),
        ),
        Stage(
            icon = Icons.Outlined.Mic,
            title = getString(R.string.audio_state_stage_input_device),
            subtitle =
                device?.let { displayName(it) }
                    ?: getString(R.string.audio_state_unknown_input_device),
            hostLabel = device?.isHostEndpoint == true,
            lines =
                listOfNotNull(
                    device?.let {
                        ReadoutLine.Pair(
                            getString(R.string.audio_state_label_interface),
                            getString(it.kind.labelRes),
                        )
                    }
                ),
        ),
    )
}

@Composable
internal fun DeviceSection(title: String, devices: List<AudioDevice>) {
    if (devices.isEmpty()) return
    SectionHeader(title)
    devices.forEachIndexed { index, device ->
        DeviceCard(device)
        if (index != devices.lastIndex) Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun DeviceCard(device: AudioDevice) {
    val resources = resources()
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Icon(
            imageVector = device.kind.icon,
            contentDescription = null,
            tint =
                if (device.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = resources.displayName(device),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (device.isHostEndpoint) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (device.isActive) {
                    Spacer(Modifier.width(8.dp))
                    ActiveBadge()
                }
                device.batteryPercent?.let {
                    Spacer(Modifier.width(8.dp))
                    BatteryChip(it)
                }
            }
            Spacer(Modifier.height(2.dp))
            resources.deviceLines(device).forEach { ReadoutRow(it) }
        }
    }
}

private fun Resources.deviceLines(device: AudioDevice): List<ReadoutLine> = buildList {
    add(
        ReadoutLine.Pair(
            getString(R.string.audio_state_label_interface),
            getString(device.kind.labelRes),
        )
    )
    device.address?.let { add(ReadoutLine.Pair(getString(R.string.audio_state_label_address), it)) }
    // An empty capability list means the device adapts to what it is fed.
    val dynamic = getString(R.string.audio_state_dynamic)
    add(
        ReadoutLine.Pair(
            getString(R.string.audio_state_label_sample_rates),
            device.sampleRates.takeIf { it.isNotEmpty() }?.joinToString(", ") { rate(it) }
                ?: dynamic,
        )
    )
    add(
        ReadoutLine.Pair(
            getString(R.string.audio_state_label_channels),
            device.channelCounts.takeIf { it.isNotEmpty() }?.joinToString(", ") { channels(it) }
                ?: dynamic,
        )
    )
    add(
        ReadoutLine.Pair(
            getString(R.string.audio_state_label_formats),
            device.encodings.takeIf { it.isNotEmpty() }?.joinToString(", ") { label(it) } ?: dynamic,
        )
    )
}

/** The microphones, or a line saying they could not be read. */
@Composable
internal fun MicrophoneSection(microphones: List<MicrophoneInfoSummary>?) {
    if (microphones?.isEmpty() == true) return
    SectionHeader(stringResource(R.string.audio_state_section_microphones))
    if (microphones == null) {
        ReadoutRow(ReadoutLine.Mono(stringResource(R.string.audio_state_microphones_unavailable)))
        return
    }
    microphones.forEachIndexed { index, mic ->
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Icon(
                Icons.Outlined.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = mic.description,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(2.dp))
                listOfNotNull(
                        mic.location?.let {
                            ReadoutLine.Pair(
                                stringResource(R.string.audio_state_label_location),
                                stringResource(it.labelRes),
                            )
                        },
                        mic.directionality?.let {
                            ReadoutLine.Pair(
                                stringResource(R.string.audio_state_label_directionality),
                                stringResource(it.labelRes),
                            )
                        },
                        mic.address?.let {
                            ReadoutLine.Pair(stringResource(R.string.audio_state_label_address), it)
                        },
                    )
                    .forEach { ReadoutRow(it) }
            }
        }
        if (index != microphones.lastIndex) Spacer(Modifier.height(6.dp))
    }
}
