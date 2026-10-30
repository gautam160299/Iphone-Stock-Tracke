package com.example.applestockpulse

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Daily plan for background checks, in the phone's local time:
 * paused from [pauseStartHour] to [pauseEndHour], fast checks every [fastIntervalSec] seconds
 * from [pauseEndHour] to [fastEndHour], and the normal interval for the rest of the day.
 * An hour range wraps past midnight when its start is after its end; equal hours mean "never".
 */
data class CheckSchedule(
    val enabled: Boolean = true,
    val pauseStartHour: Int = 23,
    val pauseEndHour: Int = 6,
    val fastEndHour: Int = 12,
    val fastIntervalSec: Int = FAST_INTERVAL_SEC
) {
    enum class Mode { PAUSED, FAST, NORMAL }

    companion object {
        const val FAST_INTERVAL_SEC = 30

        fun hourLabel(hour: Int): String {
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, 0)
            }
            return SimpleDateFormat("h a", Locale.getDefault()).format(calendar.time)
        }

        private fun inRange(hour: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }

    fun modeAtHour(hour: Int): Mode = when {
        !enabled -> Mode.NORMAL
        inRange(hour, pauseStartHour, pauseEndHour) -> Mode.PAUSED
        inRange(hour, pauseEndHour, fastEndHour) -> Mode.FAST
        else -> Mode.NORMAL
    }

    fun modeAt(timeMs: Long): Mode = modeAtHour(calendarAt(timeMs).get(Calendar.HOUR_OF_DAY))

    /** The first time after [timeMs] at which the pause ends. */
    fun pauseEndsAfter(timeMs: Long): Long {
        val end = calendarAt(timeMs).apply {
            set(Calendar.HOUR_OF_DAY, pauseEndHour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (end.timeInMillis <= timeMs) end.add(Calendar.DAY_OF_YEAR, 1)
        return end.timeInMillis
    }

    /** How much of [start]..[end] falls in pause hours, to tell a planned pause from a lost check. */
    fun pausedMsBetween(start: Long, end: Long): Long {
        if (!enabled || end <= start) return 0L
        val step = 60_000L
        var paused = 0L
        var t = start
        while (t < end) {
            if (modeAt(t) == Mode.PAUSED) paused += minOf(step, end - t)
            t += step
        }
        return paused
    }

    private fun calendarAt(timeMs: Long) = Calendar.getInstance().apply { timeInMillis = timeMs }
}
