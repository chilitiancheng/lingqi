package com.lingqi.app.reminder

import com.lingqi.app.data.UserPreferences
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderSchedulerTest {
    private val zone = ZoneId.of("Asia/Shanghai")

    @Test
    fun reminderMinutesDefaultToZeroForOlderPreferences() {
        val preferences = UserPreferences()

        assertEquals(0, preferences.meditationReminderMinute)
        assertEquals(0, preferences.sleepReminderMinute)
    }

    @Test
    fun nextDailyTriggerUsesTodayWhenTimeIsStillAhead() {
        val now = ZonedDateTime.of(2026, 7, 11, 20, 30, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 7, 11, 21, 15, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(expected, nextDailyTriggerMillis(now, zone, hour = 21, minute = 15))
    }

    @Test
    fun nextDailyTriggerRollsToTomorrowAtExactScheduledTime() {
        val now = ZonedDateTime.of(2026, 7, 11, 21, 15, 0, 0, zone).toInstant().toEpochMilli()
        val expected = ZonedDateTime.of(2026, 7, 12, 21, 15, 0, 0, zone).toInstant().toEpochMilli()

        assertEquals(expected, nextDailyTriggerMillis(now, zone, hour = 21, minute = 15))
    }

    @Test
    fun reminderTimeIsAlwaysZeroPadded() {
        assertEquals("07:05", formatReminderTime(hour = 7, minute = 5))
        assertEquals("23:00", formatReminderTime(hour = 23, minute = 0))
    }
}
