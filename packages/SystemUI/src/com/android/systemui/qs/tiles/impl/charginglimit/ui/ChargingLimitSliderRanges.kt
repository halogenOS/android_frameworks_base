/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.ui

import kotlin.math.roundToInt

/**
 * Limits offered by the tile slider and the dialog, a product choice. A stored limit outside them
 * is still valid, and a slider shows it at the nearest end.
 */
internal val OFFERED_LEVELS = 50..95

/** Variations offered by the dialog, a product choice. */
internal val OFFERED_VARIATIONS = 0..5

/** The value at [fraction] of the way from the first to the last value of this range. */
internal fun IntRange.valueAt(fraction: Float): Int =
    first + ((last - first) * fraction).roundToInt()

/** How far [value] lies from the first to the last value of this range, from 0 to 1. */
internal fun IntRange.fractionOf(value: Int): Float =
    (value.coerceIn(this) - first).toFloat() / (last - first)
