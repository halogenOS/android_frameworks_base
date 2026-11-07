/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.systemui.screenrecord

import android.app.Activity
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.projection.StopReason
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.os.UserHandle
import android.view.Display
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_UP
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import androidx.annotation.LayoutRes
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.mediaprojection.MediaProjectionCaptureTarget
import com.android.systemui.mediaprojection.MediaProjectionMetricsLogger
import com.android.systemui.mediaprojection.appselector.MediaProjectionAppSelectorActivity
import com.android.systemui.mediaprojection.permission.BaseMediaProjectionPermissionContentManager
import com.android.systemui.mediaprojection.permission.ENTIRE_SCREEN
import com.android.systemui.mediaprojection.permission.ENTIRE_SCREEN_EXTERNAL
import com.android.systemui.mediaprojection.permission.MediaProjectionPermissionUtils.getConnectedDisplays
import com.android.systemui.mediaprojection.permission.SINGLE_APP
import com.android.systemui.mediaprojection.permission.ScreenShareMode
import com.android.systemui.mediaprojection.permission.ScreenShareOption
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.res.R
import com.android.systemui.screencapture.record.data.repository.ScreenCaptureRecordParametersRepository
import com.android.systemui.screencapture.record.shared.model.ScreenRecordOptions
import com.android.systemui.screenrecord.data.repository.ScreenRecordingStartStopRepository
import com.android.systemui.screenrecord.shared.model.ScreenRecordingParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first

class ScreenRecordPermissionContentManager(
    private val hostUserHandle: UserHandle,
    private val hostUid: Int,
    mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
    @ScreenShareMode defaultSelectedMode: Int,
    displayManager: DisplayManager,
    private val controller: ScreenRecordUxController,
    private val activityStarter: ActivityStarter,
    private val onStartRecordingClicked: Runnable?,
    private val screenRecordingStartStopRepository: ScreenRecordingStartStopRepository,
    private val recordParametersRepository: ScreenCaptureRecordParametersRepository,
) :
    BaseMediaProjectionPermissionContentManager(
        createOptionList(displayManager),
        appName = null,
        hostUid = hostUid,
        mediaProjectionMetricsLogger,
        defaultSelectedMode,
    ) {
    @AssistedInject
    constructor(
        @Assisted hostUserHandle: UserHandle,
        @Assisted hostUid: Int,
        mediaProjectionMetricsLogger: MediaProjectionMetricsLogger,
        displayManager: DisplayManager,
        @Assisted controller: ScreenRecordUxController,
        activityStarter: ActivityStarter,
        @Assisted onStartRecordingClicked: Runnable?,
        screenRecordingStartStopRepository: ScreenRecordingStartStopRepository,
        recordParametersRepository: ScreenCaptureRecordParametersRepository,
    ) : this(
        hostUserHandle,
        hostUid,
        mediaProjectionMetricsLogger,
        defaultSelectedMode = SINGLE_APP,
        displayManager,
        controller,
        activityStarter,
        onStartRecordingClicked,
        screenRecordingStartStopRepository,
        recordParametersRepository,
    )

    @AssistedFactory
    interface Factory {
        fun create(
            hostUserHandle: UserHandle,
            hostUid: Int,
            controller: ScreenRecordUxController,
            onStartRecordingClicked: Runnable?,
        ): ScreenRecordPermissionContentManager
    }

    private lateinit var tapsSwitch: Switch
    private lateinit var audioSwitch: Switch
    private lateinit var longerDurationSwitch: Switch
    private lateinit var skipTimeSwitch: Switch
    private lateinit var hevcSwitch: Switch
    private lateinit var tapsView: View
    private lateinit var options: Spinner
    private var storedOptionsBinding: DisposableHandle? = null

    private val switches: List<Switch>
        get() = listOf(audioSwitch, tapsSwitch, longerDurationSwitch, skipTimeSwitch, hevcSwitch)

    /** The controls that are disabled until they show the stored options. */
    private val storedOptionsControls: List<View>
        get() = switches + options + startButton

    override fun bind(view: View) {
        super.bind(view)
        initRecordOptionsView()
        setStartButtonOnClickListener { startButtonOnClicked() }
    }

    override fun unbind() {
        storedOptionsBinding?.dispose()
        super.unbind()
    }

    fun startButtonOnClicked() {
        onStartRecordingClicked?.run()
        if (
            selectedScreenShareOption.mode == ENTIRE_SCREEN ||
                selectedScreenShareOption.mode == ENTIRE_SCREEN_EXTERNAL
        ) {
            requestScreenCapture(
                captureTarget = null,
                displayId = selectedScreenShareOption.displayId,
            )
        }
        if (selectedScreenShareOption.mode == SINGLE_APP) {
            val intent =
                Intent(containerView.context, MediaProjectionAppSelectorActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            // We can't start activity for result here so we use result receiver to get
            // the selected target to capture
            intent.putExtra(
                MediaProjectionAppSelectorActivity.EXTRA_CAPTURE_REGION_RESULT_RECEIVER,
                CaptureTargetResultReceiver(),
            )

            intent.putExtra(
                MediaProjectionAppSelectorActivity.EXTRA_HOST_APP_USER_HANDLE,
                hostUserHandle,
            )
            intent.putExtra(MediaProjectionAppSelectorActivity.EXTRA_HOST_APP_UID, hostUid)
            intent.putExtra(
                MediaProjectionAppSelectorActivity.EXTRA_SCREEN_SHARE_TYPE,
                MediaProjectionAppSelectorActivity.ScreenShareType.ScreenRecord.name,
            )
            activityStarter.startActivity(intent, /* dismissShade= */ true)
        }
    }

    private fun initRecordOptionsView() {
        audioSwitch = containerView.requireViewById(R.id.screenrecord_audio_switch)
        tapsSwitch = containerView.requireViewById(R.id.screenrecord_taps_switch)
        longerDurationSwitch =
            containerView.requireViewById(R.id.screenrecord_longer_timeout_switch)
        skipTimeSwitch = containerView.requireViewById(R.id.screenrecord_skip_time_switch)
        hevcSwitch = containerView.requireViewById(R.id.screenrecord_hevc_switch)

        tapsView = containerView.requireViewById(R.id.show_taps)
        updateTapsViewVisibility()

        switches.forEach { it.setOnTouchListener(toggleOnTap) }

        options = containerView.requireViewById(R.id.screen_recording_options)
        val a: ArrayAdapter<*> =
            ScreenRecordingAdapter(
                containerView.context,
                android.R.layout.simple_spinner_dropdown_item,
                MODES,
            )
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        options.adapter = a
        options.setOnItemClickListenerInt { _: AdapterView<*>?, _: View?, _: Int, _: Long ->
            audioSwitch.isChecked = true
        }

        // Disable HEVC when hardware accelerated codec is not available
        if (!hasHevcHwEncoder()) {
            containerView.requireViewById<View>(R.id.show_hevc).visibility = View.GONE
        }

        // disable redundant Touch & Hold accessibility action for Switch Access
        options.accessibilityDelegate =
            object : View.AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: AccessibilityNodeInfo,
                ) {
                    info.removeAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_LONG_CLICK)
                    super.onInitializeAccessibilityNodeInfo(host, info)
                }
            }
        options.isLongClickable = false

        storedOptionsControls.forEach { it.isEnabled = false }
        // Shows the stored options once per bind, so choices made since then survive a re-attach.
        storedOptionsBinding =
            containerView.repeatWhenAttached {
                showStoredOptions(recordParametersRepository.options.filterNotNull().first())
                storedOptionsBinding?.dispose()
            }
    }

    private fun showStoredOptions(stored: ScreenRecordOptions) {
        tapsSwitch.isChecked = stored.shouldShowTaps
        audioSwitch.isChecked = stored.audioSource != ScreenRecordingAudioSource.NONE
        listOf(stored.audioSource, stored.audioSourceWhenEnabled)
            .firstOrNull { it in MODES }
            ?.let { options.setSelection(MODES.indexOf(it)) }
        longerDurationSwitch.isChecked = stored.longerDuration
        skipTimeSwitch.isChecked = stored.skipCountdown
        hevcSwitch.isChecked = stored.hevc
        switches.forEach { it.jumpDrawablesToCurrentState() }
        storedOptionsControls.forEach { it.isEnabled = true }
    }

    override fun onItemSelected(adapterView: AdapterView<*>?, view: View, pos: Int, id: Long) {
        super.onItemSelected(adapterView, view, pos, id)
        updateTapsViewVisibility()
    }

    private fun updateTapsViewVisibility() {
        tapsView.visibility = if (selectedScreenShareOption.mode == SINGLE_APP) GONE else VISIBLE
    }

    @LayoutRes override fun getOptionsViewLayoutId(): Int = R.layout.screen_record_options

    /**
     * Starts screen capture after some countdown
     *
     * @param captureTarget target to capture (could be e.g. a task) or null to record the whole
     *   screen
     */
    private fun requestScreenCapture(
        captureTarget: MediaProjectionCaptureTarget?,
        displayId: Int = Display.DEFAULT_DISPLAY,
    ) {
        val showTaps = selectedScreenShareOption.mode != SINGLE_APP && tapsSwitch.isChecked
        val audioSourceWhenEnabled = options.selectedItem as ScreenRecordingAudioSource
        val audioMode =
            if (audioSwitch.isChecked) audioSourceWhenEnabled else ScreenRecordingAudioSource.NONE
        val longerDuration = longerDurationSwitch.isChecked
        val skipTime = skipTimeSwitch.isChecked
        val hevc = hevcSwitch.isChecked

        recordParametersRepository.update {
            it.copy(
                audioSource = audioMode,
                audioSourceWhenEnabled = audioSourceWhenEnabled,
                shouldShowTaps = tapsSwitch.isChecked,
                longerDuration = longerDuration,
                hevc = hevc,
                skipCountdown = skipTime,
            )
        }

        controller.startCountdown(
            if (skipTime) NO_DELAY else DELAY_MS,
            if (skipTime) NO_DELAY else INTERVAL_MS,
            {
                screenRecordingStartStopRepository.startRecording(
                    ScreenRecordingParameters(
                        captureTarget = captureTarget,
                        audioSource = audioMode,
                        displayId = displayId,
                        shouldShowTaps = showTaps,
                        longerDuration = longerDuration,
                        hevc = hevc,
                    )
                )
            },
            { screenRecordingStartStopRepository.stopRecording(StopReason.STOP_UNKNOWN) },
        )
    }

    private fun hasHevcHwEncoder(): Boolean {
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)

        for (codecInfo in mediaCodecList.getCodecInfos()) {
            if (!codecInfo.isEncoder() || !codecInfo.isHardwareAccelerated()) {
                continue
            }

            for (type in codecInfo.getSupportedTypes()) {
                if (type.equals(MediaFormat.MIMETYPE_VIDEO_HEVC, ignoreCase = true)) {
                    return true
                }
            }
        }

        return false
    }

    private inner class CaptureTargetResultReceiver :
        ResultReceiver(Handler(Looper.getMainLooper())) {
        override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
            if (resultCode == Activity.RESULT_OK) {
                val captureTarget =
                    resultData.getParcelable(
                        MediaProjectionAppSelectorActivity.KEY_CAPTURE_TARGET,
                        MediaProjectionCaptureTarget::class.java,
                    )

                // Start recording of the selected target
                requestScreenCapture(captureTarget)
            }
        }
    }

    companion object {
        private val MODES =
            listOf(
                ScreenRecordingAudioSource.INTERNAL,
                ScreenRecordingAudioSource.MIC,
                ScreenRecordingAudioSource.MIC_AND_INTERNAL,
            )

        private const val DELAY_MS: Long = 3000
        private const val NO_DELAY: Long = 100
        private const val INTERVAL_MS: Long = 1000

        /**
         * Toggles a switch with a tap and never with a drag of its thumb. At the end of the touch
         * the switch gets a cancel event in place of the up event, which ends its pressed state
         * without a second toggle.
         */
        private val toggleOnTap =
            View.OnTouchListener { view, event ->
                when (event.actionMasked) {
                    ACTION_MOVE -> true
                    ACTION_UP -> view.performClick() or view.cancelTouch(event)
                    else -> false
                }
            }

        /** Hands this view a cancel event at the position of [event], which ends its touch. */
        private fun View.cancelTouch(event: MotionEvent): Boolean {
            val cancel = MotionEvent.obtain(event).apply { action = ACTION_CANCEL }
            return onTouchEvent(cancel).also { cancel.recycle() }
        }

        fun createOptionList(displayManager: DisplayManager): List<ScreenShareOption> {
            val connectedDisplays = getConnectedDisplays(displayManager)

            val options =
                mutableListOf(
                    ScreenShareOption(
                        SINGLE_APP,
                        R.string.screenrecord_permission_dialog_option_text_single_app,
                        R.string.screenrecord_permission_dialog_warning_single_app,
                        startButtonText =
                            R.string
                                .media_projection_entry_generic_permission_dialog_continue_single_app,
                    ),
                    ScreenShareOption(
                        ENTIRE_SCREEN,
                        if (connectedDisplays.isEmpty()) {
                            R.string.screenrecord_permission_dialog_option_text_entire_screen
                        } else {
                            R.string
                                .screenrecord_permission_dialog_option_text_entire_screen_for_display
                        },
                        R.string.screenrecord_permission_dialog_warning_entire_screen,
                        startButtonText =
                            R.string.screenrecord_permission_dialog_continue_entire_screen,
                        displayId = Display.DEFAULT_DISPLAY,
                        displayName = Build.MODEL,
                    ),
                )

            if (connectedDisplays.isNotEmpty()) {
                options +=
                    connectedDisplays.map {
                        ScreenShareOption(
                            ENTIRE_SCREEN_EXTERNAL,
                            R.string
                                .screenrecord_permission_dialog_option_text_entire_screen_for_display,
                            warningText =
                                R.string
                                    .media_projection_entry_app_permission_dialog_warning_entire_screen,
                            startButtonText =
                                R.string
                                    .media_projection_entry_app_permission_dialog_continue_entire_screen,
                            displayId = it.displayId,
                            displayName = it.name,
                        )
                    }
            }
            return options.toList()
        }
    }
}
