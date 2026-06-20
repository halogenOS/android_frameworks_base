/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.audioinfo.ui.dialog

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.compose.theme.PlatformTheme
import com.android.settingslib.audiostate.AudioStateRepository
import com.android.settingslib.audiostate.AudioStateSnapshot
import com.android.settingslib.audiostate.compose.AudioPathDialogContent
import com.android.systemui.dagger.qualifiers.Application
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.qs.shared.QSSettingsPackageRepository
import com.android.systemui.qs.tiles.impl.audioinfo.domain.audioInformationSettingsIntent
import com.android.systemui.res.R
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import javax.inject.Inject

/** The live audio paths in a dialog, updated in place while it is open. */
class AudioInfoDialogDelegate
@Inject
constructor(
    @Application private val context: Context,
    private val sysuiDialogFactory: SystemUIDialogFactory,
    private val settingsPackageRepository: QSSettingsPackageRepository,
    private val activityStarter: ActivityStarter,
    private val repository: AudioStateRepository,
) : SystemUIDialog.Delegate {

    override fun createDialog(): SystemUIDialog =
        sysuiDialogFactory.create(context = context) { dialog -> AudioInfoDialogContent(dialog) }

    @Composable
    private fun AudioInfoDialogContent(dialog: SystemUIDialog) {
        // Keep the theme the dialog opened with; a theme change while it shows leaves the
        // background and the content in different themes.
        val isCurrentlyInDarkTheme = isSystemInDarkTheme()
        val cachedDarkTheme = remember { isCurrentlyInDarkTheme }
        val snapshot: AudioStateSnapshot? by
            repository.audioState.collectAsStateWithLifecycle(initialValue = null)

        PlatformTheme(isDarkTheme = cachedDarkTheme) {
            AlertDialogContent(
                title = { Text(stringResource(R.string.quick_settings_audio_info_label)) },
                content = {
                    AudioPathDialogContent(
                        snapshot = snapshot,
                        modifier =
                            Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                    )
                },
                neutralButton = {
                    PlatformOutlinedButton(
                        onClick = {
                            dialog.dismiss()
                            activityStarter.postStartActivityDismissingKeyguard(
                                audioInformationSettingsIntent(
                                    settingsPackageRepository.getSettingsPackageName()
                                ),
                                /* delay= */ 0,
                            )
                        }
                    ) {
                        Text(stringResource(R.string.quick_settings_more))
                    }
                },
                positiveButton = {
                    PlatformButton(onClick = { dialog.dismiss() }) {
                        Text(stringResource(R.string.quick_settings_done))
                    }
                },
            )
        }
    }
}
