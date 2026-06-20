/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate.compose

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.settingslib.audiostate.AudioRoute
import com.android.settingslib.audiostate.OutputPath
import com.android.settingslib.audiostate.SampleEncoding
import com.android.settingslib.audiostate.Sink

/** An idle route on the page: a card with a summary row that opens to the full chain. */
@Composable
internal fun IdleRouteCard(route: AudioRoute, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val state =
        stringResource(
            if (expanded) R.string.audio_state_expanded else R.string.audio_state_collapsed
        )
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                // clickable after clip, so the press highlight takes the rounded card shape.
                .clip(shape)
                .clickable(
                    onClickLabel =
                        stringResource(
                            if (expanded) R.string.audio_state_collapse
                            else R.string.audio_state_expand
                        ),
                    role = Role.Button,
                ) {
                    expanded = !expanded
                }
                .semantics { stateDescription = state }
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = shape,
                )
    ) {
        Column {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                IdleRouteHeader(route = route, expanded = expanded)
            }
            if (expanded) {
                // A darker recess under the header, from edge to edge of the card.
                Column(
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f))
                            .padding(12.dp)
                ) {
                    FullChain(route)
                }
            }
        }
    }
}

@Composable
private fun IdleRouteHeader(route: AudioRoute, expanded: Boolean) {
    val resources = resources()
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = (route.sink as? Sink.Devices)?.devices?.singleOrNull()?.kind.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = resources.idleSummary(route),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        val pills = resources.outputPills(route)
        if (pills.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            // Stacked, so a title that wraps keeps its width.
            Column(
                modifier = Modifier.wrapContentWidth(Alignment.End),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                pills.forEach {
                    StatusChip(it, modifier = Modifier.wrapContentWidth(Alignment.End))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        StatusChip(stringResource(R.string.audio_state_idle))
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The kinds of the devices of the route, e.g. "Speaker", or what holds when there are none. */
private fun Resources.idleSummary(route: AudioRoute): String =
    when (val sink = route.sink) {
        is Sink.Devices ->
            joinLabels(
                sink.devices.map { getString(it.kind.labelRes) },
                R.string.audio_state_device_join,
            ) ?: getString(R.string.audio_state_device_unknown)
        Sink.Duplicated -> getString(R.string.audio_state_idle_duplicated)
        Sink.NotPatched ->
            if (route.thread.path is OutputPath.Mixer) {
                getString(R.string.audio_state_idle_disconnected_mixer)
            } else {
                getString(R.string.audio_state_sink_not_patched)
            }
        Sink.Unidentified -> getString(R.string.audio_state_idle_unidentified)
    }

/** The rate and bit depth the mixer writes, e.g. "48 kHz" and "16 bit". */
private fun Resources.outputPills(route: AudioRoute): List<String> {
    val output = (route.thread.path as? OutputPath.Mixer)?.output ?: return emptyList()
    return listOfNotNull(
        output.sampleRateHz?.let { rate(it) },
        (output.encoding as? SampleEncoding.Pcm)?.let {
            if (it.isFloat) {
                getString(R.string.audio_state_bits_float_short, it.bitsPerSample)
            } else {
                getString(R.string.audio_state_bits, it.bitsPerSample)
            }
        },
    )
}
