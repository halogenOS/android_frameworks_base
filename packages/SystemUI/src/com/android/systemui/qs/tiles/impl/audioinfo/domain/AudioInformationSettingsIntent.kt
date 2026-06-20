/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.audioinfo.domain

import android.content.Intent

/** The action Settings declares for its "Audio information" page. */
private const val ACTION_AUDIO_INFORMATION_SETTINGS =
    "com.android.settings.AUDIO_INFORMATION_SETTINGS"

fun audioInformationSettingsIntent(settingsPackage: String): Intent =
    Intent(ACTION_AUDIO_INFORMATION_SETTINGS).setPackage(settingsPackage)
