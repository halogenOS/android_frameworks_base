/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles.impl.charginglimit.ui.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.android.compose.PlatformButton
import com.android.compose.PlatformOutlinedButton
import com.android.compose.dialog.AlertDialogContent
import com.android.systemui.lifecycle.rememberViewModel
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.ChargingLimit
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.Percent
import com.android.systemui.qs.tiles.impl.charginglimit.ui.OFFERED_LEVELS
import com.android.systemui.qs.tiles.impl.charginglimit.ui.OFFERED_VARIATIONS
import com.android.systemui.qs.tiles.impl.charginglimit.ui.composable.BandDescription
import com.android.systemui.qs.tiles.impl.charginglimit.ui.composable.PercentSlider
import com.android.systemui.qs.tiles.impl.charginglimit.ui.composable.RangeEnds
import com.android.systemui.qs.tiles.impl.charginglimit.ui.viewmodel.ChargingLimitDialogViewModel
import com.android.systemui.res.R
import com.android.systemui.shade.domain.interactor.ShadeDialogContextInteractor
import com.android.systemui.statusbar.phone.SystemUIDialog
import com.android.systemui.statusbar.phone.SystemUIDialogFactory
import com.android.systemui.statusbar.phone.create
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject

/**
 * The charge limit and the variation above it, each set with a slider. A slider writes the value to
 * the setting when the user releases it.
 */
class ChargingLimitDialogDelegate
@AssistedInject
constructor(
    @Assisted private val initialLimit: ChargingLimit,
    private val sysuiDialogFactory: SystemUIDialogFactory,
    private val shadeDialogContextInteractor: ShadeDialogContextInteractor,
    private val viewModelFactory: ChargingLimitDialogViewModel.Factory,
) : SystemUIDialog.Delegate {

    override fun createDialog(): SystemUIDialog =
        sysuiDialogFactory.create(context = shadeDialogContextInteractor.context) { dialog ->
            ChargingLimitDialogContent(dialog)
        }

    @Composable
    private fun ChargingLimitDialogContent(dialog: SystemUIDialog) {
        val viewModel =
            rememberViewModel("ChargingLimitDialogViewModel") {
                viewModelFactory.create(initialLimit)
            }
        val limit = viewModel.limit

        AlertDialogContent(
            title = { Text(stringResource(R.string.quick_settings_charging_limit_label)) },
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PercentSlider(
                        label = stringResource(R.string.quick_settings_charging_limit_dialog_limit),
                        value =
                            when (limit) {
                                ChargingLimit.Off -> OFFERED_LEVELS.first
                                is ChargingLimit.On -> limit.level.value
                            },
                        valueText =
                            when (limit) {
                                ChargingLimit.Off ->
                                    stringResource(R.string.quick_settings_charging_limit_off)
                                is ChargingLimit.On ->
                                    stringResource(
                                        R.string.quick_settings_charging_limit_level,
                                        limit.level.value,
                                    )
                            },
                        range = OFFERED_LEVELS,
                        onValueChange = { viewModel.onChange(limit.withLevel(Percent(it))) },
                        onValueChangeFinished = viewModel::onRelease,
                    )
                    if (limit is ChargingLimit.On) {
                        val variations =
                            OFFERED_VARIATIONS.first..minOf(
                                    OFFERED_VARIATIONS.last,
                                    limit.maxVariation.value,
                                )
                        val stopsBetweenEnds = variations.last - variations.first - 1
                        Column {
                            PercentSlider(
                                label =
                                    stringResource(
                                        R.string.quick_settings_charging_limit_dialog_variation
                                    ),
                                value = limit.variation.value,
                                valueText =
                                    stringResource(
                                        R.string.quick_settings_charging_limit_level,
                                        limit.variation.value,
                                    ),
                                range = variations,
                                steps = stopsBetweenEnds,
                                onValueChange = {
                                    viewModel.onChange(limit.copy(variation = Percent(it)))
                                },
                                onValueChangeFinished = viewModel::onRelease,
                            )
                            RangeEnds(variations)
                        }
                        BandDescription(limit)
                    }
                }
            },
            neutralButton = {
                PlatformOutlinedButton(
                    onClick = {
                        viewModel.turnOff()
                        dialog.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.quick_settings_charging_limit_dialog_turn_off))
                }
            },
            positiveButton = {
                PlatformButton(onClick = { dialog.dismiss() }) {
                    Text(stringResource(R.string.quick_settings_done))
                }
            },
        )
    }

    @AssistedFactory
    interface Factory {
        fun create(initialLimit: ChargingLimit): ChargingLimitDialogDelegate
    }
}
