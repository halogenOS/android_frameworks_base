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

package com.android.systemui.screencapture.record.data.repository

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.MainThread
import androidx.core.content.edit
import com.android.systemui.dagger.SysUISingleton
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.domain.interactor.SharedPreferencesInteractor
import com.android.systemui.screencapture.record.shared.model.ScreenRecordOptions
import com.android.systemui.screenrecord.ScreenRecordingAudioSource
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FILE_NAME = "screen_record_options"
private const val KEY_AUDIO_SOURCE = "audio_source"
private const val KEY_AUDIO_SOURCE_WHEN_ENABLED = "audio_source_when_enabled"
private const val KEY_SHOW_TAPS = "show_taps"
private const val KEY_LONGER_DURATION = "longer_duration"
private const val KEY_HEVC = "hevc"
private const val KEY_SKIP_COUNTDOWN = "skip_countdown"

private val DEFAULT_OPTIONS = ScreenRecordOptions()

/** Stores the screen recording options of the selected user. */
@SysUISingleton
class ScreenCaptureRecordParametersRepository
@Inject
constructor(
    sharedPreferencesInteractor: SharedPreferencesInteractor,
    @Background backgroundContext: CoroutineContext,
    @Application applicationScope: CoroutineScope,
) {

    private val selectedUserPreferences: Flow<SharedPreferences> =
        sharedPreferencesInteractor.sharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private val _options = MutableStateFlow<ScreenRecordOptions?>(null)

    /** The options of the selected user, null until they are read from the file of that user. */
    val options: StateFlow<ScreenRecordOptions?> = _options.asStateFlow()

    /** The file [options] were read from. Only the main thread reads and sets it. */
    private var optionsFile: SharedPreferences? = null

    private val pendingWrites = Channel<PendingWrite>(Channel.UNLIMITED)

    init {
        applicationScope.launch {
            selectedUserPreferences.collectLatest { file ->
                _options.value = null
                val stored = withContext(backgroundContext) { file.readOptions() }
                optionsFile = file
                _options.value = stored
            }
        }
        applicationScope.launch(backgroundContext) {
            for (write in pendingWrites) {
                write.file.edit { putOptions(write.options) }
            }
        }
    }

    /**
     * Changes the options of the selected user at once and writes them to the file of that user in
     * the order the calls were made. Does nothing until the options are read, so the defaults never
     * replace the stored options.
     */
    @MainThread
    fun update(transform: (ScreenRecordOptions) -> ScreenRecordOptions) {
        val file = optionsFile ?: return
        val changed = transform(_options.value ?: return)
        _options.value = changed
        pendingWrites.trySend(PendingWrite(file, changed)).getOrThrow()
    }

    private class PendingWrite(val file: SharedPreferences, val options: ScreenRecordOptions)
}

private fun SharedPreferences.readOptions() =
    ScreenRecordOptions(
        audioSource = getAudioSource(KEY_AUDIO_SOURCE, DEFAULT_OPTIONS.audioSource),
        audioSourceWhenEnabled =
            getAudioSource(KEY_AUDIO_SOURCE_WHEN_ENABLED, DEFAULT_OPTIONS.audioSourceWhenEnabled),
        shouldShowTaps = getBoolean(KEY_SHOW_TAPS, DEFAULT_OPTIONS.shouldShowTaps),
        longerDuration = getBoolean(KEY_LONGER_DURATION, DEFAULT_OPTIONS.longerDuration),
        hevc = getBoolean(KEY_HEVC, DEFAULT_OPTIONS.hevc),
        skipCountdown = getBoolean(KEY_SKIP_COUNTDOWN, DEFAULT_OPTIONS.skipCountdown),
    )

private fun SharedPreferences.Editor.putOptions(options: ScreenRecordOptions) {
    putString(KEY_AUDIO_SOURCE, options.audioSource.name)
    putString(KEY_AUDIO_SOURCE_WHEN_ENABLED, options.audioSourceWhenEnabled.name)
    putBoolean(KEY_SHOW_TAPS, options.shouldShowTaps)
    putBoolean(KEY_LONGER_DURATION, options.longerDuration)
    putBoolean(KEY_HEVC, options.hevc)
    putBoolean(KEY_SKIP_COUNTDOWN, options.skipCountdown)
}

/** The audio source stored by name under [key], or [default] when no audio source has that name. */
private fun SharedPreferences.getAudioSource(
    key: String,
    default: ScreenRecordingAudioSource,
): ScreenRecordingAudioSource {
    val name = getString(key, null)
    return ScreenRecordingAudioSource.entries.firstOrNull { it.name == name } ?: default
}
