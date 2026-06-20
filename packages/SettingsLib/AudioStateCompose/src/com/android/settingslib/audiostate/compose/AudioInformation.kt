/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.audiostate.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.android.settingslib.audiostate.AudioRoute
import com.android.settingslib.audiostate.AudioStateSnapshot
import com.android.settingslib.audiostate.OutputPathFailure
import com.android.settingslib.audiostate.OutputPaths

/** The routes that play now, for the Quick Settings dialog. A null snapshot is still loading. */
@Composable
fun AudioPathDialogContent(snapshot: AudioStateSnapshot?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        when (val paths = snapshot?.outputPaths) {
            null -> Loading()
            is OutputPaths.Unavailable -> PathsUnavailable(paths.failure)
            is OutputPaths.Read -> {
                val playing = paths.routes.filter { it.thread.isPlaying }
                if (playing.isEmpty()) NothingPlaying()
                playing.forEachIndexed { index, route ->
                    key(route.thread.ioHandle) {
                        if (index != 0) ChainDivider()
                        FullChain(route)
                    }
                }
            }
        }
    }
}

/**
 * Every route, the recordings, devices and microphones, for the Settings page. A null snapshot is
 * still loading. The page brings its own horizontal padding; idle route cards reach closer to the
 * screen edge than the text.
 */
@Composable
fun AudioInformationPage(snapshot: AudioStateSnapshot?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        val text = Modifier.padding(horizontal = PAGE_PADDING)
        if (snapshot == null) {
            Box(text) { Loading() }
            return@Column
        }
        when (val paths = snapshot.outputPaths) {
            is OutputPaths.Unavailable -> Box(text) { PathsUnavailable(paths.failure) }
            is OutputPaths.Read -> Routes(paths.routes, textModifier = text)
        }
        Column(text) {
            InputRoutesSection(snapshot.inputRoutes)
            DeviceSection(
                stringResource(R.string.audio_state_section_output_devices),
                snapshot.outputDevices,
            )
            DeviceSection(
                stringResource(R.string.audio_state_section_input_devices),
                snapshot.inputDevices,
            )
            MicrophoneSection(snapshot.microphones)
        }
    }
}

/** The playing routes as chains, then the idle ones as cards. */
@Composable
private fun Routes(routes: List<AudioRoute>, textModifier: Modifier) {
    val (playing, idle) = routes.partition { it.thread.isPlaying }
    Column(textModifier) {
        playing.forEachIndexed { index, route ->
            key(route.thread.ioHandle) {
                if (index != 0) ChainDivider()
                FullChain(route)
            }
        }
    }
    idle.forEachIndexed { index, route ->
        key(route.thread.ioHandle) {
            if (index != 0 || playing.isNotEmpty()) Spacer(Modifier.height(8.dp))
            IdleRouteCard(route, Modifier.padding(horizontal = CARD_EDGE_INSET))
        }
    }
}

@Composable
private fun Loading() {
    val description = stringResource(R.string.audio_state_loading)
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.semantics { contentDescription = description }
        )
    }
}

@Composable
private fun NothingPlaying() {
    Notice(
        icon = Icons.Outlined.GraphicEq,
        title = R.string.audio_state_no_active_streams,
        line = R.string.audio_state_nothing_playing,
    )
}

@Composable
private fun PathsUnavailable(failure: OutputPathFailure) {
    Notice(
        icon = Icons.Outlined.ErrorOutline,
        title = R.string.audio_state_paths_unavailable,
        line =
            when (failure) {
                OutputPathFailure.SERVICE_MISSING -> R.string.audio_state_paths_service_missing
                OutputPathFailure.PERMISSION_DENIED -> R.string.audio_state_paths_permission_denied
                OutputPathFailure.QUERY_FAILED -> R.string.audio_state_paths_query_failed
            },
    )
}

@Composable
private fun Notice(icon: ImageVector, @StringRes title: Int, @StringRes line: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(line),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The horizontal padding of the page text. */
private val PAGE_PADDING = 24.dp

/** How far an idle card sits from the screen edge. */
private val CARD_EDGE_INSET = 8.dp
