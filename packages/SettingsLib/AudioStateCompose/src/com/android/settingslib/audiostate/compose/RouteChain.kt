/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate.compose

import android.content.res.Resources
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.settingslib.audiostate.AudioDevice
import com.android.settingslib.audiostate.AudioRoute
import com.android.settingslib.audiostate.AudioSource
import com.android.settingslib.audiostate.BitPerfectVerdict
import com.android.settingslib.audiostate.OutputPath
import com.android.settingslib.audiostate.OutputThread
import com.android.settingslib.audiostate.SampleEncoding
import com.android.settingslib.audiostate.Sink

/** The glyph between a stage and the next: the signal arrow, or "+" between summed sources. */
internal enum class Connector {
    ARROW,
    PLUS,
}

/** One stage of a chain, built before rendering so the rail knows which stage is last. */
internal data class Stage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String?,
    val lines: List<ReadoutLine>,
    val batteryPercent: Int? = null,
    val connector: Connector = Connector.ARROW,
    /** One of two or more summed sources, drawn together inside one outline. */
    val grouped: Boolean = false,
    /** The stage belongs to a thread that plays nothing, drawn grayed. */
    val idle: Boolean = false,
    val statusChip: String? = null,
    /** The subtitle is the "This device" label, which keeps the accent color when idle. */
    val hostLabel: Boolean = false,
)

/** Sources → AudioFlinger → effects → output devices of one route. */
internal fun Resources.chainStages(route: AudioRoute): List<Stage> {
    val stages =
        sourceStages(route) +
            audioFlingerStage(route.thread) +
            effectStages(route.thread) +
            sinkStages(route)
    if (route.thread.isPlaying) return stages
    return stages.mapIndexed { index, stage ->
        stage.copy(
            idle = true,
            statusChip = if (index == 0) getString(R.string.audio_state_idle) else stage.statusChip,
        )
    }
}

/** The usages routed to a route, e.g. "Media · Game", or "Audio" when none is known. */
internal fun Resources.usageLabel(route: AudioRoute): String =
    joinLabels(route.usages.map { getString(it.labelRes) }, R.string.audio_state_usage_join)
        ?: getString(R.string.audio_state_usage_other)

/** One stage per source; two or more are summed, so "+" joins them inside one outline. */
private fun Resources.sourceStages(route: AudioRoute): List<Stage> {
    val sources = route.thread.sources
    val grouped = sources.size > 1
    return sources.mapIndexed { index, source ->
        Stage(
            icon = Icons.Outlined.MusicNote,
            title = getString(R.string.audio_state_stage_source),
            subtitle = usageLabel(route),
            lines = sourceLines(source),
            connector =
                if (grouped && index != sources.lastIndex) Connector.PLUS else Connector.ARROW,
            grouped = grouped,
        )
    }
}

private fun Resources.sourceLines(source: AudioSource): List<ReadoutLine> = buildList {
    formatLine(source.format)?.let { add(ReadoutLine.Mono(it)) }
    if (source.trackFlags.isNotEmpty()) {
        add(
            ReadoutLine.Mono(
                getString(R.string.audio_state_track_flags, source.trackFlags.joinToString(" "))
            )
        )
    }
}

private fun Resources.audioFlingerStage(thread: OutputThread): Stage =
    Stage(
        icon = Icons.Outlined.Tune,
        title = getString(R.string.audio_state_stage_audioflinger),
        subtitle = getString(thread.path.labelRes),
        lines =
            (thread.path as? OutputPath.Mixer)?.let { mixLines(thread, it) }.orEmpty() +
                threadLines(thread),
    )

private fun Resources.effectStages(thread: OutputThread): List<Stage> =
    thread.effects.map { effect ->
        Stage(
            icon = Icons.Outlined.GraphicEq,
            title = effect.name ?: effect.typeUuid,
            subtitle =
                getString(R.string.audio_state_effect_disabled).takeUnless { effect.enabled },
            lines = emptyList(),
        )
    }

/** The lines only a mixer has: rate and channels, the bit depth through the mix, resampling. */
private fun Resources.mixLines(thread: OutputThread, mixer: OutputPath.Mixer): List<ReadoutLine> =
    buildList {
        val conversion = rateConversion(thread, mixer)
        val rateAndChannels =
            listOfNotNull(
                conversion ?: mixer.output.sampleRateHz?.let { rate(it) },
                mixer.output.channelCount?.let { channels(it) },
            )
        if (rateAndChannels.isNotEmpty()) {
            add(ReadoutLine.Mono(rateAndChannels.joinToString(PART_SEPARATOR)))
        }
        bitDepthFlow(thread, mixer)?.let {
            add(ReadoutLine.Pair(getString(R.string.audio_state_label_bit_depth), it))
        }
        if (thread.sources.isNotEmpty()) {
            val resampling =
                when {
                    !thread.isResampling -> getString(R.string.audio_state_no)
                    conversion != null -> getString(R.string.audio_state_yes_with, conversion)
                    else -> getString(R.string.audio_state_yes)
                }
            add(ReadoutLine.Pair(getString(R.string.audio_state_label_resampling), resampling))
        }
    }

/** The source rates the mixer converts and its own rate, e.g. "44.1 kHz → 48 kHz". */
private fun Resources.rateConversion(thread: OutputThread, mixer: OutputPath.Mixer): String? {
    val mixRate = mixer.output.sampleRateHz ?: return null
    val converted = thread.sourceSampleRatesHz.filter { it != mixRate }
    if (converted.isEmpty()) return null
    return getString(R.string.audio_state_conversion, rates(converted), rate(mixRate))
}

/** The bit depth from the source through the mixer, e.g. "in 16 → mix 32 float → out 16". */
private fun Resources.bitDepthFlow(thread: OutputThread, mixer: OutputPath.Mixer): String? {
    val segments = buildList {
        thread.sources.singleOrNull()?.let { source ->
            (source.format.encoding as? SampleEncoding.Pcm)?.let { pcm ->
                val sourceDepth =
                    source.keptBits?.let {
                        getString(R.string.audio_state_depth_kept, pcm.bitsPerSample, it)
                    } ?: depth(pcm)
                add(getString(R.string.audio_state_depth_in, sourceDepth))
            }
        }
        (mixer.accumulation.encoding as? SampleEncoding.Pcm)?.let {
            add(getString(R.string.audio_state_depth_mix, depth(it)))
        }
        (mixer.output.encoding as? SampleEncoding.Pcm)?.let {
            add(getString(R.string.audio_state_depth_out, depth(it)))
        }
    }
    return segments.takeIf { it.size > 1 }?.joinToString(" → ")
}

/** The lines every thread has: the bit-perfect verdict with its reasons, flags, latency. */
private fun Resources.threadLines(thread: OutputThread): List<ReadoutLine> = buildList {
    val verdictLabel = getString(R.string.audio_state_label_bit_perfect)
    when (val verdict = thread.bitPerfect) {
        BitPerfectVerdict.BitPerfect ->
            add(ReadoutLine.Pair(verdictLabel, getString(R.string.audio_state_yes)))
        is BitPerfectVerdict.NotBitPerfect -> {
            add(ReadoutLine.Pair(verdictLabel, getString(R.string.audio_state_no)))
            verdict.blockers.forEach {
                add(ReadoutLine.Mono(getString(R.string.audio_state_reason_item, label(it))))
            }
        }
    }
    if (thread.flags.isNotEmpty()) {
        add(
            ReadoutLine.Pair(
                getString(R.string.audio_state_label_flags),
                thread.flags.joinToString(" "),
            )
        )
    }
    thread.latencyMillis?.let {
        add(
            ReadoutLine.Pair(
                getString(R.string.audio_state_label_latency),
                getString(R.string.audio_state_latency_ms, it),
            )
        )
    }
}

private fun Resources.sinkStages(route: AudioRoute): List<Stage> {
    val title = getString(R.string.audio_state_stage_output_device)
    val (subtitle, line) =
        when (val sink = route.sink) {
            is Sink.Devices ->
                return sink.devices.map { device ->
                    Stage(
                        icon = device.kind.icon,
                        title = title,
                        subtitle = displayName(device),
                        hostLabel = device.isHostEndpoint,
                        lines = outputDeviceLines(device, route.thread),
                        batteryPercent = device.batteryPercent,
                    )
                }
            Sink.Duplicated ->
                R.string.audio_state_sink_duplicated to R.string.audio_state_sink_duplicated_line
            Sink.NotPatched ->
                R.string.audio_state_sink_not_patched to R.string.audio_state_sink_not_patched_line
            Sink.Unidentified ->
                R.string.audio_state_sink_unidentified to
                    R.string.audio_state_sink_unidentified_line
        }
    return listOf(
        Stage(
            icon = Icons.Outlined.Speaker,
            title = title,
            subtitle = getString(subtitle),
            lines = listOf(ReadoutLine.Mono(getString(line))),
        )
    )
}

private fun Resources.outputDeviceLines(
    device: AudioDevice,
    thread: OutputThread,
): List<ReadoutLine> = buildList {
    add(
        ReadoutLine.Pair(
            getString(R.string.audio_state_label_interface),
            getString(device.kind.labelRes),
        )
    )
    device.address?.let { add(ReadoutLine.Pair(getString(R.string.audio_state_label_address), it)) }
    // The format the device receives from the thread.
    val format = thread.hardwareFormat
    format.sampleRateHz?.let {
        add(ReadoutLine.Pair(getString(R.string.audio_state_label_sample_rate), rate(it)))
    }
    format.encoding?.let {
        add(ReadoutLine.Pair(getString(R.string.audio_state_label_format), label(it)))
    }
}

/** A playing route, or an opened idle one: the stages top to bottom. */
@Composable
internal fun FullChain(route: AudioRoute) {
    val (group, stages) = resources().chainStages(route).partition { it.grouped }
    if (group.isNotEmpty()) {
        SourceGroup { group.forEach { ChainStage(stage = it, isLast = false) } }
        Spacer(Modifier.height(8.dp))
    }
    stages.forEachIndexed { index, stage ->
        ChainStage(stage = stage, isLast = index == stages.lastIndex)
    }
}

/** One stage: an icon rail whose line runs on to the next stage, and the stage's readout. */
@Composable
internal fun ChainStage(stage: Stage, isLast: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.width(RAIL_WIDTH).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = stage.icon,
                contentDescription = null,
                tint =
                    if (stage.idle) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            if (!isLast) {
                // The glyph sits in a gap between two line segments, so it breaks the line.
                ConnectorSegment(Modifier.weight(1f))
                Spacer(Modifier.height(2.dp))
                Icon(
                    when (stage.connector) {
                        Connector.ARROW -> Icons.Outlined.ArrowDownward
                        Connector.PLUS -> Icons.Outlined.Add
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.height(2.dp))
                ConnectorSegment(Modifier.weight(1f))
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.fillMaxWidth().padding(bottom = if (isLast) 0.dp else 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stage.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color =
                        if (stage.idle) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                stage.statusChip?.let {
                    Spacer(Modifier.width(8.dp))
                    StatusChip(it)
                }
            }
            stage.subtitle?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color =
                            if (stage.idle && !stage.hostLabel)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    stage.batteryPercent?.let { percent ->
                        Spacer(Modifier.width(8.dp))
                        BatteryChip(percent)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            stage.lines.forEach { ReadoutRow(it) }
        }
    }
}

/** The rule between two chains, so they read as separate paths. */
@Composable
internal fun ChainDivider() {
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier.fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
    Spacer(Modifier.height(16.dp))
}

/** The outline around summed sources and their arrow into the mixer. */
@Composable
private fun SourceGroup(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(10.dp)
    ) {
        content()
    }
}

@Composable
private fun ConnectorSegment(modifier: Modifier) {
    Box(
        modifier
            .width(2.dp)
            .clip(RoundedCornerShape(1.dp))
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

private val RAIL_WIDTH = 24.dp
