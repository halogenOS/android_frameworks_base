/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.systemui.qs.tiles

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.repeatOnLifecycle
import com.android.bluetooth.audioquality.aidl.AudioQuality
import com.android.bluetooth.audioquality.aidl.AudioQualityLevel
import com.android.internal.logging.MetricsLogger
import com.android.settingslib.bluetooth.LocalBluetoothManager
import com.android.settingslib.bluetooth.audioquality.AudioQualityServiceClient
import com.android.settingslib.bluetooth.audioquality.currentLevelIndex
import com.android.settingslib.bluetooth.audioquality.formatSummary
import com.android.settingslib.bluetooth.audioquality.isAdjustable
import com.android.systemui.animation.Expandable
import com.android.systemui.bluetooth.bluetoothDeviceDetailsIntent
import com.android.systemui.broadcast.BroadcastDispatcher
import com.android.systemui.dagger.qualifiers.Background
import com.android.systemui.dagger.qualifiers.Main
import com.android.systemui.plugins.ActivityStarter
import com.android.systemui.plugins.FalsingManager
import com.android.systemui.plugins.qs.QSTile.State
import com.android.systemui.plugins.statusbar.StatusBarStateController
import com.android.systemui.qs.QSHost
import com.android.systemui.qs.QsEventLogger
import com.android.systemui.qs.logging.QSLogger
import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.res.R
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Shows the codec and the audio quality level on the active A2DP headset. A tap steps to the next
 * level, a long press opens the Settings page for the headset, or the Bluetooth settings while no
 * headset is active.
 */
class AudioQualityTile
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
    private val broadcastDispatcher: BroadcastDispatcher,
    private val localBluetoothManager: LocalBluetoothManager?,
    private val audioQualityClient: AudioQualityServiceClient,
    @Background private val backgroundDispatcher: CoroutineDispatcher,
) :
    QSTileImpl<State>(
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
        const val TILE_SPEC = "audio_quality"
    }

    /** The active A2DP headset with its quality, or null unless an A2DP headset is active. */
    @Volatile private var activeHeadset: ActiveHeadset? = null

    init {
        collectActiveHeadset()
    }

    private fun collectActiveHeadset() {
        lifecycle.coroutineScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                try {
                    broadcastDispatcher
                        .broadcastFlow(IntentFilter(BluetoothA2dp.ACTION_ACTIVE_DEVICE_CHANGED))
                        .onStart { emit(Unit) }
                        .map { localBluetoothManager?.profileManager?.a2dpProfile?.activeDevice }
                        .distinctUntilChanged()
                        .flatMapLatest { device ->
                            device?.let {
                                audioQualityClient.qualityFlow(it).map { quality ->
                                    ActiveHeadset(it, quality)
                                }
                            } ?: flowOf(null)
                        }
                        .flowOn(backgroundDispatcher)
                        .collect {
                            activeHeadset = it
                            refreshState()
                        }
                } finally {
                    // Opening the panel again shows "No headset" until the stack reports the active
                    // headset, never the headset shown before.
                    activeHeadset = null
                }
            }
        }
    }

    override fun isAvailable() = localBluetoothManager != null

    override fun newTileState() = State()

    override fun handleClick(expandable: Expandable?) {
        val headset = activeHeadset ?: return
        val level = headset.quality?.nextLevel ?: return
        audioQualityClient.setLevel(headset.device, level)
    }

    override fun getLongClickIntent(): Intent =
        activeHeadset?.let { bluetoothDeviceDetailsIntent(it.device.address) }
            ?: Intent(Settings.ACTION_BLUETOOTH_SETTINGS)

    override fun getTileLabel(): CharSequence =
        mContext.getString(com.android.settingslib.R.string.bluetooth_audio_quality_title)

    override fun getMetricsCategory() = MetricsLogger.VIEW_UNKNOWN

    override fun handleUpdateState(state: State, arg: Any?) {
        val quality = activeHeadset?.quality
        state.icon = maybeLoadResourceIcon(quality?.levelIconRes ?: R.drawable.ic_qs_audio_quality)
        state.label = tileLabel
        state.contentDescription = state.label
        state.state =
            if (quality?.isAdjustable == true) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        state.secondaryLabel =
            quality?.formatSummary(mContext)
                ?: mContext.getString(R.string.quick_settings_audio_quality_no_headset)
        state.stateDescription = state.secondaryLabel
    }
}

/** The active A2DP headset, with the quality reported for it once it has an A2DP codec status. */
private data class ActiveHeadset(val device: BluetoothDevice, val quality: AudioQuality?)

/**
 * The level after the current one, and the first level after the last one. While the setting of the
 * codec matches none of its levels, the first level. Null while the quality is fixed.
 */
private val AudioQuality.nextLevel: AudioQualityLevel?
    get() = if (isAdjustable) levels[currentLevelIndex?.plus(1)?.rem(levels.size) ?: 0] else null

/** Meter icons with one to four of their bars lit, from the lowest level to the highest. */
private val levelIcons =
    listOf(
        R.drawable.ic_qs_audio_quality_level_1,
        R.drawable.ic_qs_audio_quality_level_2,
        R.drawable.ic_qs_audio_quality_level_3,
        R.drawable.ic_qs_audio_quality_level_4,
    )

/**
 * The meter icon with as many bars lit as the share of levels at or below the current one, or null
 * while the setting of the codec matches none of its levels. The levels run from the highest fixed
 * bitrate to the lowest, then variable, then adaptive.
 */
private val AudioQuality.levelIconRes: Int?
    get() =
        currentLevelIndex?.let { position ->
            val rank = levels.size - position
            levelIcons[(rank * levelIcons.size).ceilDiv(levels.size) - 1]
        }

private fun Int.ceilDiv(divisor: Int) = (this + divisor - 1) / divisor
