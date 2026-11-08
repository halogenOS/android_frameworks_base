/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.custom.smartspace.date

import android.app.AlarmManager.AlarmClockInfo
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.icu.text.DateFormat
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.android.app.tracing.coroutines.launchTraced as launch
import com.android.systemui.clock.domain.interactor.ClockInteractor
import com.android.systemui.custom.smartspace.SmartspaceSlotView
import com.android.systemui.custom.smartspace.SmartspaceSlotViewFactory
import com.android.systemui.keyguard.KeyguardDateRow
import com.android.systemui.keyguard.domain.interactor.KeyguardClockInteractor
import com.android.systemui.keyguard.shared.model.ClockSize
import com.android.systemui.lifecycle.repeatWhenAttached
import com.android.systemui.plugins.keyguard.ui.clocks.ClockController
import com.android.systemui.plugins.keyguard.ui.clocks.ClockStyleSlot
import com.android.systemui.res.R
import com.android.systemui.statusbar.policy.domain.interactor.NextAlarmInteractor
import com.android.systemui.util.time.DateFormatUtil
import dagger.Lazy
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

/** Creates the views of the date slot, following the lockscreen clock. */
internal class DateSlotViewFactory
@Inject
constructor(
    // Lazy: KeyguardClockInteractor reaches the glanceable hub smartspace, which this factory serves.
    private val keyguardClockInteractor: Lazy<KeyguardClockInteractor>,
    private val clockInteractor: ClockInteractor,
    private val nextAlarmInteractor: NextAlarmInteractor,
    private val dateFormatUtil: DateFormatUtil,
) : SmartspaceSlotViewFactory {
    override fun create(context: Context, clockSize: ClockSize): DateSlotView =
        DateSlotView(
            context,
            clockSize = clockSize,
            lockscreenClock = keyguardClockInteractor.get().currentClock,
            now = clockInteractor.currentTime,
            dateFormats =
                clockInteractor.onTimeFormatChange.map {
                    KeyguardDateRow.createDateFormat(context, Locale.getDefault())
                },
            nextAlarm = nextAlarmInteractor.nextAlarm,
            dateFormatUtil = dateFormatUtil,
        )
}

/**
 * Shows the [ClockStyleSlot] view of a clock face or, when the face has none, the date and the next
 * alarm that rings soon. The clock is the lockscreen clock until [followClock] names another one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class DateSlotView(
    context: Context,
    private val clockSize: ClockSize,
    lockscreenClock: Flow<ClockController?>,
    now: Flow<Date>,
    dateFormats: Flow<DateFormat>,
    nextAlarm: Flow<AlarmClockInfo?>,
    private val dateFormatUtil: DateFormatUtil,
) : SmartspaceSlotView(context) {
    private val dateText = createRowText()
    private val alarmText =
        createRowText().apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                R.drawable.ic_access_alarms_big,
                0,
                0,
                0,
            )
            compoundDrawablePadding = resources.getDimensionPixelSize(R.dimen.widget_icon_padding)
        }
    // Holds the date or the view of the clock face. It is the first child, because SystemUI adds
    // the weather view after it.
    private val content = FrameLayout(context).apply { addView(dateText) }
    private val clocks = MutableStateFlow(lockscreenClock)
    private var clockOfContent: ClockController? = null
    private var faceView: View? = null
    private var isAlarmShown = false
    private var primaryTextColor = dateText.currentTextColor
    private var dozeAmount = 0f

    init {
        gravity = Gravity.CENTER_VERTICAL
        showDividers = SHOW_DIVIDER_MIDDLE
        dividerDrawable =
            GradientDrawable().apply {
                val gap = resources.getDimensionPixelSize(R.dimen.widget_horizontal_padding)
                setSize(gap, gap)
            }
        addView(content)
        addView(alarmText)

        repeatWhenAttached {
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                launch("$TAG#clocks") { clocks.flatMapLatest { it }.collect(::showFaceOf) }
                launch("$TAG#dateAndAlarm") {
                    combine(now, dateFormats, nextAlarm, ::Triple).collect {
                        (now, dateFormat, alarm) ->
                        showDateAndAlarm(now, dateFormat, alarm)
                    }
                }
            }
        }
    }

    /** Shows the face of the clock in [clock] from now on, for a host that shows another clock. */
    fun followClock(clock: Flow<ClockController?>) {
        clocks.value = clock
    }

    override fun setPrimaryTextColor(color: Int) {
        primaryTextColor = color
        showTextColor()
    }

    override fun setDozeAmount(amount: Float) {
        dozeAmount = amount
        showTextColor()
    }

    private fun createRowText() =
        TextView(context, null, 0, R.style.TextAppearance_Keyguard_Secondary).apply {
            ellipsize = TextUtils.TruncateAt.END
        }

    // The view of a face is created once each time this slot starts to show that clock, also when
    // the slot is attached again.
    private fun showFaceOf(clock: ClockController?) {
        if (clock === clockOfContent) return
        clockOfContent = clock
        faceView = createFaceView(clock)
        content.removeAllViews()
        content.addView(faceView ?: dateText)
        showAlarmVisibility()
    }

    private fun createFaceView(clock: ClockController?): View? {
        val view = clock?.dateSlot(clockSize)?.createView(context) ?: return null
        if (view.parent == null) return view
        Log.wtf(TAG, "$clock returned a date slot view that is already in another parent")
        return null
    }

    private fun showDateAndAlarm(now: Date, dateFormat: DateFormat, nextAlarm: AlarmClockInfo?) {
        dateText.text = dateFormat.format(now)
        val alarm = nextAlarm?.takeIf { KeyguardDateRow.isAlarmShown(it, now.time) }
        isAlarmShown = alarm != null
        if (alarm != null) {
            alarmText.text =
                KeyguardDateRow.formatAlarmTime(
                    alarm.triggerTime,
                    is24HourFormat = dateFormatUtil.is24HourFormat(),
                )
            alarmText.contentDescription =
                context.getString(R.string.accessibility_quick_settings_alarm, alarmText.text)
        }
        showAlarmVisibility()
    }

    // The view of a clock face replaces both the date and the alarm.
    private fun showAlarmVisibility() {
        alarmText.isVisible = isAlarmShown && faceView == null
    }

    // Text turns white towards always-on display, as in the stock slice view.
    private fun showTextColor() {
        val color = ColorUtils.blendARGB(primaryTextColor, Color.WHITE, dozeAmount)
        dateText.setTextColor(color)
        alarmText.setTextColor(color)
        alarmText.compoundDrawableTintList = ColorStateList.valueOf(color)
    }

    private companion object {
        const val TAG = "DateSlotView"
    }
}
