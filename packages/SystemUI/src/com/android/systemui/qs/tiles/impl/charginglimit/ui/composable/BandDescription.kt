/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.ui.composable

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.ChargingLimit
import com.android.systemui.res.R

/** The levels the battery stays between under [limit]. */
@Composable
internal fun BandDescription(limit: ChargingLimit.On) {
    Text(
        text =
            if (limit.isExact) {
                stringResource(
                    R.string.quick_settings_charging_limit_dialog_stays_at,
                    limit.level.value,
                )
            } else {
                stringResource(
                    R.string.quick_settings_charging_limit_dialog_stays_between,
                    limit.level.value,
                    limit.top.value,
                )
            },
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
}
