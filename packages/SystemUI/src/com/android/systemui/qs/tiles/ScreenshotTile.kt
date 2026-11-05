/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.view.WindowManager.ScreenshotSource.SCREENSHOT_OTHER
import androidx.lifecycle.lifecycleScope
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.internal.logging.MetricsLogger
import com.android.internal.util.ScreenshotHelper
import com.android.systemui.animation.Expandable
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.pipeline.domain.interactor.PanelInteractor
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.base.shared.model.QSTileConfigProvider
import com.android.systemui.shade.domain.interactor.ShadeBackActionInteractor
import com.android.systemui.shade.domain.interactor.ShadeInteractor
import com.android.systemui.statusbar.StatusBarState
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/** Takes a screenshot of the screen behind the shade. */
class ScreenshotTile
@Inject
constructor(
    host: QSHost,
    uiEventLogger: QsEventLogger,
    @Background backgroundLooper: Looper,
    @Main mainHandler: Handler,
    falsingManager: FalsingManager,
    metricsLogger: MetricsLogger,
    private val statusBarStateController: StatusBarStateController,
    activityStarter: ActivityStarter,
    qsLogger: QSLogger,
    qsTileConfigProvider: QSTileConfigProvider,
    private val panelInteractor: PanelInteractor,
    private val shadeBackActionInteractor: ShadeBackActionInteractor,
    private val shadeInteractor: ShadeInteractor,
    private val screenshotHelper: ScreenshotHelper,
) :
    QSTileImpl<QSTile.State>(
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

    private enum class CollapseOutcome {
        Collapsed,
        Interrupted,
    }

    private val config = qsTileConfigProvider.getConfig(TILE_SPEC)

    /** Written and read only by [handleClick], which runs on the tile's background looper. */
    private var pendingScreenshot: Job? = null

    override fun newTileState(): QSTile.State =
        QSTile.State().apply {
            state = Tile.STATE_INACTIVE
            handlesLongClick = false
        }

    override fun handleClick(expandable: Expandable?) {
        if (pendingScreenshot?.isActive == true) return
        pendingScreenshot =
            lifecycleScope.launch {
                collapseShade()
                if (awaitCollapse() == CollapseOutcome.Collapsed) {
                    screenshotHelper.takeScreenshot(
                        SCREENSHOT_OTHER,
                        mUiHandler,
                        /* completionConsumer= */ null,
                    )
                }
            }
    }

    private fun collapseShade() {
        if (statusBarStateController.state == StatusBarState.KEYGUARD) {
            // The lock screen keeps the shade expanded and ignores a shade collapse, so only
            // Quick Settings closes.
            shadeBackActionInteractor.animateCollapseQs(fullyCollapse = false)
        } else {
            panelInteractor.collapsePanels()
        }
    }

    /**
     * Waits until neither the shade nor Quick Settings covers the screen, or until the user grabs
     * the shade again. A drag still settling at the tap is not taken as a new grab.
     */
    private suspend fun awaitCollapse(): CollapseOutcome =
        merge(
                shadeInteractor.anyExpansion
                    .filter { expansion -> expansion == 0f }
                    .map { CollapseOutcome.Collapsed },
                shadeInteractor.isUserInteracting
                    .dropWhile { interacting -> interacting }
                    .filter { interacting -> interacting }
                    .map { CollapseOutcome.Interrupted },
            )
            .first()

    override fun getLongClickIntent(): Intent? = null

    override fun getTileLabel(): CharSequence = mContext.getString(config.uiConfig.labelRes)

    override fun handleUpdateState(state: QSTile.State, arg: Any?) {
        state.label = tileLabel
        state.icon = ResourceIcon.get(config.uiConfig.iconRes)
    }

    companion object {
        const val TILE_SPEC = "screenshot"
    }
}
