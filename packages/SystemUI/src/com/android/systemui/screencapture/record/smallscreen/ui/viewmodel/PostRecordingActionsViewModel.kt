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

package com.android.systemui.screencapture.record.smallscreen.ui.viewmodel

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.view.Display
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.internal.logging.UiEventLogger
import com.android.systemui.broadcast.BroadcastSender
import com.android.systemui.lifecycle.HydratedActivatable
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.screencapture.common.shared.model.ScreenCaptureUiParameters
import com.android.systemui.screencapture.common.ui.viewmodel.DrawableLoaderViewModel
import com.android.systemui.screencapture.domain.interactor.ScreenCaptureUiInteractor
import com.android.systemui.screencapture.record.data.repository.IntentResolutionRepository
import com.android.systemui.screencapture.record.largescreen.data.repository.ParentUriRepository
import com.android.systemui.screencapture.record.shared.model.ScreenRecordEvent
import com.android.systemui.screenrecord.service.ActivityStartingReceiver
import com.android.systemui.settings.UserTracker
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

private const val MIME_TYPE = "video/mp4"

private fun Intent.addNewTaskAndUriGrantFlags(): Intent =
    addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    )

class PostRecordingActionsViewModel
@AssistedInject
constructor(
    @Assisted private val videoUri: Uri,
    @Assisted private val displayId: Int,
    private val context: Context,
    private val broadcastSender: BroadcastSender,
    private val userTracker: UserTracker,
    private val drawableLoaderViewModel: DrawableLoaderViewModel,
    private val screenCaptureUiInteractor: ScreenCaptureUiInteractor,
    private val parentUriRepository: ParentUriRepository,
    private val uiEventLogger: UiEventLogger,
    private val activityStarter: ActivityStarter,
    private val intentResolutionRepository: IntentResolutionRepository,
) : HydratedActivatable(), DrawableLoaderViewModel by drawableLoaderViewModel {

    var parentUri: Uri? by mutableStateOf(null)
        private set

    /** Whether the current user has an activity for the intent sent by [edit]. */
    var hasVideoEditor: Boolean by mutableStateOf(false)
        private set

    override suspend fun onActivated() {
        parentUri = parentUriRepository.getParentDirectoryUri(videoUri)
        hasVideoEditor =
            intentResolutionRepository.hasActivityFor(createEditIntent(), userTracker.userId)
    }

    fun new() {
        uiEventLogger.log(ScreenRecordEvent.SCREEN_RECORD_POST_RECORDING_NEW)
        screenCaptureUiInteractor.show(ScreenCaptureUiParameters.Record())
    }

    fun edit() {
        uiEventLogger.log(ScreenRecordEvent.SCREEN_RECORD_POST_RECORDING_EDIT)
        startVideoActivity(createEditIntent())
    }

    fun view() {
        startVideoActivity(createVideoIntent(Intent.ACTION_VIEW))
    }

    fun openInFolder() {
        parentUri?.let {
            startVideoActivity(
                createVideoIntent(
                    action = Intent.ACTION_VIEW,
                    mimeType = DocumentsContract.Document.MIME_TYPE_DIR,
                    uri = it,
                )
            )
        }
    }

    fun share() {
        uiEventLogger.log(ScreenRecordEvent.SCREEN_RECORD_POST_RECORDING_SHARE)
        startVideoActivity(
            Intent.createChooser(
                    createVideoIntent(Intent.ACTION_SEND),
                    context.getString(R.string.screenrecord_share_label),
                )
                .addNewTaskAndUriGrantFlags()
        )
    }

    /** Executes a [runnable] after the keyguard is dismissed. */
    fun executeDismissingKeyguard(runnable: Runnable) {
        activityStarter.executeRunnableDismissingKeyguard(
            runnable,
            /* cancelAction= */ null,
            /* dismissShade= */ true,
            /* afterKeyguardGone= */ true,
            /* deferred= */ false,
        )
    }

    private fun createEditIntent(): Intent = createVideoIntent(Intent.ACTION_EDIT)

    private fun createVideoIntent(
        action: String,
        mimeType: String = MIME_TYPE,
        uri: Uri = videoUri,
    ): Intent =
        Intent(action)
            .setDataAndType(uri, mimeType)
            .addNewTaskAndUriGrantFlags()
            .putExtra(Intent.EXTRA_STREAM, videoUri)

    private fun startVideoActivity(intent: Intent) {
        executeDismissingKeyguard {
            broadcastSender.sendBroadcastAsUser(
                intent =
                    ActivityStartingReceiver.wrapIntent(
                        context,
                        intent,
                        displayId
                            .takeIf { it != Display.INVALID_DISPLAY }
                            ?.let { validDisplayId ->
                                ActivityOptions.makeBasic()
                                    .apply { launchDisplayId = validDisplayId }
                                    .toBundle()
                            },
                    ),
                userHandle = userTracker.userHandle,
            )
        }
    }

    @AssistedFactory
    interface Factory {

        fun create(videoUri: Uri, displayId: Int): PostRecordingActionsViewModel
    }
}
