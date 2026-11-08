/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.ui.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.android.systemui.res.R

/** The first and the last percent of [range], under the ends of a slider. */
@Composable
internal fun RangeEnds(range: IntRange) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (end in listOf(range.first, range.last)) {
            Text(
                text = stringResource(R.string.quick_settings_charging_limit_level, end),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
