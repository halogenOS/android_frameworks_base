/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import kotlin.math.roundToInt

/**
 * A named slider over whole percents, with [valueText] beside the name. A [value] outside [range]
 * puts the thumb at the nearest end.
 */
@Composable
internal fun PercentSlider(
    label: String,
    value: Int,
    valueText: String,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    steps: Int = 0,
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = label, color = MaterialTheme.colorScheme.onSurface)
            Text(text = valueText)
        }
        Slider(
            value = value.coerceIn(range).toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = steps,
            modifier =
                Modifier.semantics {
                    contentDescription = label
                    stateDescription = valueText
                },
        )
    }
}
