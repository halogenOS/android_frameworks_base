/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.screencapture.record.domain.interactor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.systemui.screencapture.common.ScreenCapture
import com.android.systemui.screencapture.common.ScreenCaptureScope
import com.android.systemui.screencapture.record.data.repository.ScreenCaptureRecordParametersRepository
import com.android.systemui.screencapture.record.shared.model.ScreenRecordOptions
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import com.android.systemui.screenrecord.domain.interactor.ScreenRecordingServiceInteractor
import com.android.systemui.screenrecord.shared.model.ScreenRecordingStatus
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@ScreenCaptureScope
class ScreenCaptureRecordParametersInteractor
@Inject
constructor(
    @ScreenCapture coroutineScope: CoroutineScope,
    private val serviceInteractor: ScreenRecordingServiceInteractor,
    private val repository: ScreenCaptureRecordParametersRepository,
) {

    /** The stored options of the selected user, null until they are read. */
    val options: StateFlow<ScreenRecordOptions?> = repository.options

    private var _shouldShowFrontCamera: Boolean by mutableStateOf(false)

    var shouldShowFrontCamera: Boolean
        set(value) {
            if (value) {
                updateAudioSource { it.withEnabledMic() }
            }
            _shouldShowFrontCamera = value
        }
        get() = _shouldShowFrontCamera

    val canChangeAudioSource: StateFlow<Boolean> =
        serviceInteractor.status
            .map { it.canChangeAudioSource() }
            .stateIn(
                coroutineScope,
                SharingStarted.Eagerly,
                serviceInteractor.status.value.canChangeAudioSource(),
            )

    fun setAudioSource(audioSource: ScreenRecordingAudioSource) = updateAudioSource { audioSource }

    fun setShouldShowTaps(shouldShowTaps: Boolean) {
        serviceInteractor.updateShouldShowTaps(shouldShowTaps)
        repository.update { it.copy(shouldShowTaps = shouldShowTaps) }
    }

    fun setLongerDuration(longerDuration: Boolean) =
        repository.update { it.copy(longerDuration = longerDuration) }

    fun setHevc(hevc: Boolean) = repository.update { it.copy(hevc = hevc) }

    private fun updateAudioSource(
        transform: (ScreenRecordingAudioSource) -> ScreenRecordingAudioSource
    ) {
        if (canChangeAudioSource.value) {
            repository.update { it.copy(audioSource = transform(it.audioSource)) }
        }
    }
}

private fun ScreenRecordingAudioSource.withEnabledMic(): ScreenRecordingAudioSource =
    when (this) {
        ScreenRecordingAudioSource.MIC -> this
        ScreenRecordingAudioSource.MIC_AND_INTERNAL -> this
        ScreenRecordingAudioSource.NONE -> ScreenRecordingAudioSource.MIC
        ScreenRecordingAudioSource.INTERNAL -> ScreenRecordingAudioSource.MIC_AND_INTERNAL
    }

private fun ScreenRecordingStatus.canChangeAudioSource(): Boolean =
    this is ScreenRecordingStatus.Stopped
