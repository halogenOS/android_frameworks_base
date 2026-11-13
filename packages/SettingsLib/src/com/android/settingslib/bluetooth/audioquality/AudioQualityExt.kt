/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.settingslib.bluetooth.audioquality

import android.bluetooth.audioquality.AudioQuality
import android.bluetooth.audioquality.AudioQualityLevel

/** True when the codec offers levels to choose from, false when its quality is fixed. */
val AudioQuality.isAdjustable: Boolean
    get() = levels.isNotEmpty()

/**
 * Position of the level used by the codec in [AudioQuality.getLevels], or null while the setting of
 * the codec matches none of its levels.
 */
val AudioQuality.currentLevelIndex: Int?
    get() = levels.indexOfFirst { it.id == currentLevelId }.takeIf { it >= 0 }

/**
 * The level used by the codec, or null while the setting of the codec matches none of its levels.
 */
val AudioQuality.currentLevel: AudioQualityLevel?
    get() = currentLevelIndex?.let(levels::get)
