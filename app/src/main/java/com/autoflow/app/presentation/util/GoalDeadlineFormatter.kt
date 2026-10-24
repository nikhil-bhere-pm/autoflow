package com.autoflow.app.presentation.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object GoalDeadlineFormatter {

    private val dateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

    /**
     * Converts midnight UTC millis (returned by Android DatePicker) to
     * the start of that day in the phone's local time zone epoch millis.
     */
    fun utcDatePickerMillisToLocalStartOfDay(
        utcMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Converts a local date (year, month 1-12, day) to the start of that day in the local zone epoch millis.
     */
    fun localDateToStartOfDayMillis(
        year: Int,
        month: Int,
        dayOfMonth: Int,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Long {
        val date = LocalDate.of(year, month, dayOfMonth)
        return date.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    /**
     * Formats the deadline line for Goal Cards and Goal Detail.
     * Line under goal name:
     * - "Reached" if reached
     * - "No deadline" if deadlineAt == null
     * - "Deadline passed" if deadline date is before today
     * - "Due today" if deadline date is today
     * - "By 31 Dec · 1 day left" if deadline date is tomorrow (1 day left)
     * - "By 31 Dec · 67 days left" if deadline date is > 1 day in the future
     */
    fun formatDeadlineSubtitle(
        deadlineAt: Long?,
        isReached: Boolean,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (isReached) {
            return "Reached"
        }
        if (deadlineAt == null) {
            return "No deadline"
        }
        val today = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate()
        val deadlineDate = Instant.ofEpochMilli(deadlineAt).atZone(zoneId).toLocalDate()
        val days = ChronoUnit.DAYS.between(today, deadlineDate)

        return when {
            days < 0L -> "Deadline passed"
            days == 0L -> "Due today"
            days == 1L -> "By ${deadlineDate.format(dateFormatter)} · 1 day left"
            else -> "By ${deadlineDate.format(dateFormatter)} · $days days left"
        }
    }

    /**
     * Formats deadline for the picker display row in New/Edit goal: e.g. "31 Dec 2026"
     */
    fun formatPickerDate(
        deadlineAt: Long?,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (deadlineAt == null) return "No deadline"
        val date = Instant.ofEpochMilli(deadlineAt).atZone(zoneId).toLocalDate()
        return date.format(fullDateFormatter)
    }
}
