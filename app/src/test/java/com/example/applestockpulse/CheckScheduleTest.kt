package com.example.applestockpulse

import com.example.applestockpulse.CheckSchedule.Mode
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class CheckScheduleTest {

    private val schedule = CheckSchedule(pauseStartHour = 23, pauseEndHour = 6, fastEndHour = 12)

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test
    fun `the day is paused at night, fast in the morning and normal after`() {
        val modes = (0..23).map { schedule.modeAtHour(it) }
        val expected = List(6) { Mode.PAUSED } + List(6) { Mode.FAST } + List(11) { Mode.NORMAL } + Mode.PAUSED
        assertEquals(expected, modes)
    }

    @Test
    fun `an off schedule is normal all day`() {
        val off = schedule.copy(enabled = false)
        assertEquals(List(24) { Mode.NORMAL }, (0..23).map { off.modeAtHour(it) })
    }

    @Test
    fun `equal start and end hours mean no pause`() {
        val noPause = schedule.copy(pauseStartHour = 6, pauseEndHour = 6)
        assertEquals(Mode.FAST, noPause.modeAtHour(7))
        assertEquals(Mode.NORMAL, noPause.modeAtHour(2))
    }

    @Test
    fun `the pause ends at the next start hour`() {
        assertEquals(at(31, 6), schedule.pauseEndsAfter(at(30, 23, 30)))
        assertEquals(at(30, 6), schedule.pauseEndsAfter(at(30, 2, 15)))
    }

    @Test
    fun `paused time is counted only inside pause hours`() {
        val minute = 60_000L
        assertEquals(7 * 60 * minute, schedule.pausedMsBetween(at(30, 23), at(31, 6)))
        assertEquals(60 * minute, schedule.pausedMsBetween(at(30, 22), at(31, 0)))
        assertEquals(0L, schedule.pausedMsBetween(at(30, 13), at(30, 14)))
    }
}
