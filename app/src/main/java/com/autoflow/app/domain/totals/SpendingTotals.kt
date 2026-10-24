package com.autoflow.app.domain.totals

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.model.FinancialTransaction
import java.time.Instant
import java.time.ZoneId

data class SpendingTotals(
    val todayPaise: Long,
    val monthPaise: Long
)

/**
 * Calculates spending totals for today and this month in device time zone.
 *
 * Rules:
 * - Count EXPENSE rows that are not deleted and not EXCLUDED.
 * - NEEDS_REVIEW rows ARE counted in totals.
 * - Refunds do NOT change totals in this MVP.
 * - Today and this month use the device time zone.
 */
fun calculateSpendingTotals(
    transactions: List<FinancialTransaction>,
    zoneId: ZoneId = ZoneId.systemDefault(),
    referenceInstant: Instant = Instant.now()
): SpendingTotals {
    val zonedDateTime = referenceInstant.atZone(zoneId)
    val todayDate = zonedDateTime.toLocalDate()

    val startOfTodayMillis = todayDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
    val startOfTomorrowMillis = todayDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    val startOfMonthMillis = todayDate.withDayOfMonth(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
    val startOfNextMonthMillis = todayDate.withDayOfMonth(1).plusMonths(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

    var todayPaise = 0L
    var monthPaise = 0L

    for (tx in transactions) {
        // Exclude soft-deleted rows
        if (tx.isDeleted) continue

        // Exclude anything that is not an EXPENSE (refunds do NOT change totals in this MVP)
        if (tx.recordType != RecordType.EXPENSE) continue

        // Exclude EXCLUDED rows (NEEDS_REVIEW and CONFIRMED are both counted)
        if (tx.reviewStatus == ReviewStatus.EXCLUDED) continue

        val txTime = tx.dateTime

        // Check if within today: [startOfTodayMillis, startOfTomorrowMillis)
        if (txTime in startOfTodayMillis until startOfTomorrowMillis) {
            todayPaise += tx.amountPaise
        }

        // Check if within this month: [startOfMonthMillis, startOfNextMonthMillis)
        if (txTime in startOfMonthMillis until startOfNextMonthMillis) {
            monthPaise += tx.amountPaise
        }
    }

    return SpendingTotals(
        todayPaise = todayPaise,
        monthPaise = monthPaise
    )
}
