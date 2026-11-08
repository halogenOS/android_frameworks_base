/*
 * SPDX-FileCopyrightText: The halogenOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.systemui.keyguard

import android.app.AlarmManager.AlarmClockInfo
import android.content.Context
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import com.android.systemui.res.R
import java.util.Locale
import java.util.concurrent.TimeUnit

/** The rules of the lockscreen row that shows the date and the next alarm. */
object KeyguardDateRow {
    /** Only show alarms that will ring within N hours. */
    const val ALARM_VISIBILITY_HOURS = 12

    /** Creates the format of the date in [locale]. */
    @JvmStatic
    fun createDateFormat(context: Context, locale: Locale): DateFormat =
        DateFormat.getInstanceForSkeleton(
                context.getString(R.string.system_ui_aod_date_pattern),
                locale,
            )
            .apply {
                // The use of CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE instead of
                // CAPITALIZATION_FOR_STANDALONE is to address
                // https://unicode-org.atlassian.net/browse/ICU-21631
                // TODO(b/229287642): Switch back to CAPITALIZATION_FOR_STANDALONE
                setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
            }

    /** Whether [alarm] rings soon enough at [now], in milliseconds since the epoch, to be shown. */
    @JvmStatic
    fun isAlarmShown(alarm: AlarmClockInfo?, now: Long): Boolean =
        alarm != null &&
            alarm.triggerTime <= now + TimeUnit.HOURS.toMillis(ALARM_VISIBILITY_HOURS.toLong())

    /** Formats the time at which an alarm rings. */
    @JvmStatic
    fun formatAlarmTime(triggerTime: Long, is24HourFormat: Boolean): String =
        android.text.format.DateFormat.format(if (is24HourFormat) "HH:mm" else "h:mm", triggerTime)
            .toString()
}
