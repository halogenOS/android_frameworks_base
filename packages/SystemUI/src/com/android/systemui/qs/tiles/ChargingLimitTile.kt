/*
 * Copyright (C) 2026 The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.internal.jank.InteractionJankMonitor
import com.android.internal.logging.MetricsLogger
import com.android.systemui.animation.DialogCuj
import com.android.systemui.animation.DialogTransitionAnimator
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.BooleanState
import com.android.systemui.plugins.qs.QSTile.Icon
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.impl.charginglimit.data.repository.ChargingLimitRepository
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.ChargingLimit
import com.android.systemui.qs.tiles.impl.charginglimit.shared.model.Percent
import com.android.systemui.qs.tiles.impl.charginglimit.ui.OFFERED_LEVELS
import com.android.systemui.qs.tiles.impl.charginglimit.ui.dialog.ChargingLimitDialogDelegate
import com.android.systemui.qs.tiles.impl.charginglimit.ui.fractionOf
import com.android.systemui.qs.tiles.impl.charginglimit.ui.valueAt
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.launch

class ChargingLimitTile
@Inject
constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    private val repository: ChargingLimitRepository,
    private val dialogDelegateFactory: ChargingLimitDialogDelegate.Factory,
    private val dialogTransitionAnimator: DialogTransitionAnimator,
) :
    QSTileImpl<BooleanState>(
        host,
        uiEventLogger,
        backgroundLooper,
        mainHandler,
        falsingManager,
        metricsLogger,
        statusBarStateController,
        activityStarter,
        qsLogger,
    ) {
    companion object {
        const val TILE_SPEC = "charging_limit"
        private const val INTERACTION_JANK_TAG = "charging_limit"

        /** Levels stepped through by a tap, where null is no limit. */
        private val PRESETS = listOf(null, Percent(80), Percent(85), Percent(90))
    }

    private var icon: Icon? = null

    init {
        lifecycle.coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                repository.changes.collect { refreshState() }
            }
        }
    }

    override fun isAvailable() = repository.isSupported()

    override fun newTileState() = BooleanState()

    override fun handleClick(expandable: Expandable?) {
        val limit = repository.read()
        val level =
            when (limit) {
                ChargingLimit.Off -> null
                is ChargingLimit.On -> limit.level
            }
        val next = PRESETS[(PRESETS.indexOf(level).coerceAtLeast(0) + 1) % PRESETS.size]
        repository.set(next?.let(limit::withLevel) ?: ChargingLimit.Off)
    }

    override fun handleSliderChanged(value: Float) {
        repository.set(repository.read().withLevel(Percent(OFFERED_LEVELS.valueAt(value))))
    }

    override fun handleLongClick(expandable: Expandable?) {
        val dialogDelegate = dialogDelegateFactory.create(repository.read())
        mUiHandler.post {
            dialogTransitionAnimator.show(
                dialogDelegate.createDialog(),
                { cuj -> expandable?.dialogTransitionController(cuj) },
                DialogCuj(InteractionJankMonitor.CUJ_SHADE_DIALOG_OPEN, INTERACTION_JANK_TAG),
            )
        }
    }

    override fun getLongClickIntent(): Intent? = null

    override fun getTileLabel(): CharSequence =
        mContext.getString(R.string.quick_settings_charging_limit_label)

    override fun getMetricsCategory() = com.android.internal.logging.MetricsLogger.VIEW_UNKNOWN

    override fun handleUpdateState(state: BooleanState, arg: Any?) {
        val limit = repository.read()
        state.value = limit is ChargingLimit.On
        state.sliderEnabled = true
        if (icon == null) {
            icon = maybeLoadResourceIcon(R.drawable.ic_qs_charging_limit)
        }
        state.icon = icon
        state.label = mContext.getString(R.string.quick_settings_charging_limit_label)
        state.state = Tile.STATE_INACTIVE
        when (limit) {
            ChargingLimit.Off -> {
                state.sliderShortLabel = null
                state.secondaryLabel =
                    mContext.getString(R.string.quick_settings_charging_limit_off)
                state.sliderValue = 0f
            }
            is ChargingLimit.On -> {
                val bandLabel = formatBand(limit)
                state.sliderShortLabel = bandLabel
                state.secondaryLabel = bandLabel
                state.sliderValue = OFFERED_LEVELS.fractionOf(limit.level.value)
            }
        }
    }

    private fun formatBand(limit: ChargingLimit.On): String =
        if (limit.isExact) {
            mContext.getString(R.string.quick_settings_charging_limit_level, limit.level.value)
        } else {
            mContext.getString(
                R.string.quick_settings_charging_limit_band,
                limit.level.value,
                limit.top.value,
            )
        }
}
