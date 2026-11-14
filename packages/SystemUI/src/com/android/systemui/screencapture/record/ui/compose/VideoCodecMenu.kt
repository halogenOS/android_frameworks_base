/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.screencapture.record.ui.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.ui.compose.LoadingIcon
import com.android.systemui.screencapture.common.ui.compose.loadIcon
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screenrecord.shared.model.ScreenRecordVideoCodec

@Composable
fun VideoCodecMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    codecs: List<ScreenRecordVideoCodec>,
    selectedCodec: ScreenRecordVideoCodec,
    onCodecSelected: (ScreenRecordVideoCodec) -> Unit,
    viewModel: DrawableLoaderViewModel,
) {
    val checkIcon by
        loadIcon(
            viewModel = viewModel,
            resId = R.drawable.ic_check_expressive,
            contentDescription = null,
        )
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest) {
        codecs.forEach { codec ->
            val isSelected = codec == selectedCodec
            DropdownMenuItem(
                text = { Text(stringResource(codec.labelRes)) },
                onClick = {
                    onCodecSelected(codec)
                    onDismissRequest()
                },
                leadingIcon = {
                    LoadingIcon(
                        icon = checkIcon.takeIf { isSelected },
                        modifier = Modifier.size(24.dp),
                    )
                },
                modifier = Modifier.semantics { selected = isSelected },
            )
        }
    }
}

@get:StringRes
val ScreenRecordVideoCodec.labelRes: Int
    get() =
        when (this) {
            ScreenRecordVideoCodec.H264 -> R.string.screenrecord_video_codec_h264
            ScreenRecordVideoCodec.HEVC -> R.string.screenrecord_video_codec_hevc
            ScreenRecordVideoCodec.VP9 -> R.string.screenrecord_video_codec_vp9
            ScreenRecordVideoCodec.AV1 -> R.string.screenrecord_video_codec_av1
        }
