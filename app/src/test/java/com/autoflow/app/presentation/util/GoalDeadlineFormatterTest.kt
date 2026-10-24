package com.autoflow.app.presentation.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class GoalDeadlineFormatterTest {

    private val kolkataZone: ZoneId = ZoneId.of("Asia/Kolkata")
    // Fixed now: Sunday, 25 October 2026 15:30:00 IST
    private val fixedNow: Long = ZonedDateTime.of(2026, 10, 25, 15, 30, 0, 0, kolkataZone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun `formatDeadlineSubtitle returns Reached when reached is true`() {
        val deadline = ZonedDateTime.of(2026, 10, 20, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val result = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = deadline,
            isReached = true,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("Reached", result)
    }

    @Test
    fun `formatDeadlineSubtitle returns No deadline when deadline is null`() {
        val result = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = null,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("No deadline", result)
    }

    @Test
    fun `formatDeadlineSubtitle returns Due today when deadline date matches today in local zone`() {
        // Start of today in Kolkata
        val todayStart = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val result = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = todayStart,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("Due today", result)
    }

    @Test
    fun `formatDeadlineSubtitle returns Deadline passed when deadline is in the past`() {
        val pastDate = ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val result = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = pastDate,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("Deadline passed", result)
    }

    @Test
    fun `formatDeadlineSubtitle returns 1 day left when deadline is tomorrow`() {
        val tomorrow = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val result = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = tomorrow,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("By 26 Oct · 1 day left", result)
    }

    @Test
    fun `formatDeadlineSubtitle returns 67 days left when deadline is 31 Dec 2026`() {
        val targetDate = ZonedDateTime.of(2026, 12, 31, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val result = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = targetDate,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("By 31 Dec · 67 days left", result)
    }

    @Test
    fun `utcDatePickerMillis converted to local start of day resolves to Due today when today is picked`() {
        // Android DatePicker returns midnight UTC for 2026-10-25
        val utcPickerMidnight = LocalDate.of(2026, 10, 25)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli()

        val localStartOfDay = GoalDeadlineFormatter.utcDatePickerMillisToLocalStartOfDay(
            utcMillis = utcPickerMidnight,
            zoneId = kolkataZone
        )

        val result = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = localStartOfDay,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("Due today", result)
    }
}
