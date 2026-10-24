package com.autoflow.app.domain.stats

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.model.FinancialTransaction
import java.math.BigInteger
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class StatsPeriod {
    TODAY,
    THIS_WEEK,
    THIS_MONTH
}

data class PeriodRange(
    val start: Instant,
    val end: Instant
) {
    val startEpochMillis: Long get() = start.toEpochMilli()
    val endEpochMillis: Long get() = end.toEpochMilli()

    operator fun contains(instant: Instant): Boolean = instant >= start && instant < end
    operator fun contains(epochMillis: Long): Boolean = epochMillis in startEpochMillis until endEpochMillis
}

data class MonthPoint(
    val month: YearMonth,
    val totalPaise: Long,
    val isFuture: Boolean
)

data class CategorySpendItem(
    val category: String,
    val totalPaise: Long,
    val count: Int,
    val percent: Int
) {
    val percentInt: Int get() = percent
}

data class MonthlySpendPoint(
    val label: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val totalExpensePaise: Long,
    val totalRefundPaise: Long,
    val netSpendPaise: Long
)

data class CategoryBreakdown(
    val totalPaise: Long,
    val items: List<CategorySpendItem>
)

data class DayTotal(
    val date: LocalDate,
    val totalPaise: Long
)

/**
 * Predicate matching domain/totals/SpendingTotals.kt:
 * - Must not be soft-deleted
 * - Must be an EXPENSE (refunds are not counted in spending MVP)
 * - Must not be EXCLUDED (NEEDS_REVIEW and CONFIRMED are both counted)
 */
fun FinancialTransaction.isCountedAsSpending(): Boolean {
    return !isDeleted && recordType == RecordType.EXPENSE && reviewStatus != ReviewStatus.EXCLUDED
}

/**
 * Computes a half-open [start, end) time interval for the given period in the specified time zone.
 * Weeks start on Monday.
 */
fun periodRange(period: StatsPeriod, now: Instant, zone: ZoneId): PeriodRange {
    val zonedDateTime = now.atZone(zone)
    val localDate = zonedDateTime.toLocalDate()
    return when (period) {
        StatsPeriod.TODAY -> {
            val start = localDate.atStartOfDay(zone).toInstant()
            val end = localDate.plusDays(1).atStartOfDay(zone).toInstant()
            PeriodRange(start, end)
        }
        StatsPeriod.THIS_WEEK -> {
            val monday = localDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val start = monday.atStartOfDay(zone).toInstant()
            val end = monday.plusWeeks(1).atStartOfDay(zone).toInstant()
            PeriodRange(start, end)
        }
        StatsPeriod.THIS_MONTH -> {
            val firstDay = localDate.withDayOfMonth(1)
            val start = firstDay.atStartOfDay(zone).toInstant()
            val end = firstDay.plusMonths(1).atStartOfDay(zone).toInstant()
            PeriodRange(start, end)
        }
    }
}

/**
 * Returns a 5-month series: selectedMonth - 2 .. selectedMonth + 2.
 * Future months relative to currentMonth have isFuture = true and total 0.
 * Past months relative to currentMonth have their real calculated spending total.
 */
fun monthlySeries(
    records: List<FinancialTransaction>,
    selectedMonth: YearMonth,
    currentMonth: YearMonth,
    zone: ZoneId
): List<MonthPoint> {
    val months = (-2..2).map { selectedMonth.plusMonths(it.toLong()) }
    val validRecords = records.filter { it.isCountedAsSpending() }

    return months.map { month ->
        val isFuture = month.isAfter(currentMonth)
        if (isFuture) {
            MonthPoint(month = month, totalPaise = 0L, isFuture = true)
        } else {
            val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            var total = 0L
            for (tx in validRecords) {
                if (tx.dateTime in start until end) {
                    total = Math.addExact(total, tx.amountPaise)
                }
            }
            MonthPoint(month = month, totalPaise = total.coerceAtLeast(0L), isFuture = false)
        }
    }
}

/**
 * Calculates category breakdown for eligible spending records within the given period range.
 * Percents are whole numbers that sum to exactly 100 via largest-remainder rounding.
 * Sorted by total descending then category name. Categories with zero spend are omitted.
 */
fun categoryBreakdown(
    records: List<FinancialTransaction>,
    range: PeriodRange
): CategoryBreakdown {
    val startMillis = range.startEpochMillis
    val endMillis = range.endEpochMillis
    val eligible = records.filter { tx -> tx.dateTime in startMillis until endMillis }
    val items = categoryBreakdown(eligible)
    val totalPaise = items.fold(0L) { acc, item -> Math.addExact(acc, item.totalPaise) }
    return CategoryBreakdown(totalPaise = totalPaise, items = items)
}

/**
 * Calculates total spending for a specific calendar day in the given time zone.
 * Total is never negative.
 */
fun dayTotal(
    records: List<FinancialTransaction>,
    date: LocalDate,
    zone: ZoneId
): Long {
    val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    var total = 0L
    for (tx in records) {
        if (!tx.isCountedAsSpending()) continue
        if (tx.dateTime in start until end) {
            total = Math.addExact(total, tx.amountPaise)
        }
    }
    return total.coerceAtLeast(0L)
}

/**
 * Returns daily spending totals for all days falling within the given range in the specified time zone.
 */
fun dailyTotals(
    records: List<FinancialTransaction>,
    range: PeriodRange,
    zone: ZoneId
): List<DayTotal> {
    val startLocalDate = range.start.atZone(zone).toLocalDate()
    val endLocalDate = range.end.atZone(zone).toLocalDate()

    val result = mutableListOf<DayTotal>()
    var current = startLocalDate
    while (current.isBefore(endLocalDate)) {
        val total = dayTotal(records, current, zone)
        result.add(DayTotal(date = current, totalPaise = total))
        current = current.plusDays(1)
    }
    return result
}

/**
 * Returns a 5-month spending series: selectedMonth + 4 prior months (5 points total).
 * For each month: label (e.g. "Oct 2026"), start timestamp, end timestamp,
 * total expense paise, total refund paise, and net spend paise (floored at 0).
 */
fun fiveMonthSpendingSeries(
    records: List<FinancialTransaction>,
    selectedMonth: YearMonth,
    zone: ZoneId = ZoneId.systemDefault()
): List<MonthlySpendPoint> {
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", java.util.Locale.US)
    val months = (4 downTo 0).map { selectedMonth.minusMonths(it.toLong()) }
    return months.map { month ->
        val start = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        var expTotal = 0L
        var refTotal = 0L
        for (tx in records) {
            if (tx.isDeleted || tx.reviewStatus == ReviewStatus.EXCLUDED) continue
            if (tx.dateTime in start until end) {
                if (tx.recordType == RecordType.EXPENSE) {
                    expTotal = Math.addExact(expTotal, tx.amountPaise)
                } else if (tx.recordType == RecordType.REFUND) {
                    refTotal = Math.addExact(refTotal, tx.amountPaise)
                }
            }
        }
        val net = (expTotal - refTotal).coerceAtLeast(0L)
        MonthlySpendPoint(
            label = month.format(formatter),
            startTimestamp = start,
            endTimestamp = end,
            totalExpensePaise = expTotal,
            totalRefundPaise = refTotal,
            netSpendPaise = net
        )
    }
}

/**
 * Calculates category breakdown for a pre-filtered list of transactions.
 * Returns a list of CategorySpendItem sorted descending by totalPaise.
 */
fun categoryBreakdown(
    records: List<FinancialTransaction>
): List<CategorySpendItem> {
    val eligibleRecords = records.filter { it.isCountedAsSpending() }
    if (eligibleRecords.isEmpty()) {
        return emptyList()
    }

    data class MutableCat(val category: String, var totalPaise: Long, var count: Int)

    val map = LinkedHashMap<String, MutableCat>()
    for (tx in eligibleRecords) {
        val catName = tx.category
        val existing = map.getOrPut(catName) { MutableCat(catName, 0L, 0) }
        existing.totalPaise = Math.addExact(existing.totalPaise, tx.amountPaise)
        existing.count++
    }

    val nonZeroCategories = map.values.filter { it.totalPaise > 0L }
    if (nonZeroCategories.isEmpty()) {
        return emptyList()
    }

    var grandTotal = 0L
    for (c in nonZeroCategories) {
        grandTotal = Math.addExact(grandTotal, c.totalPaise)
    }

    if (grandTotal <= 0L) {
        return emptyList()
    }

    data class TempItem(
        val category: String,
        val totalPaise: Long,
        val count: Int,
        var percent: Int,
        val remainder: BigInteger
    )

    val bigGrandTotal = BigInteger.valueOf(grandTotal)
    val big100 = BigInteger.valueOf(100)

    val tempItems = nonZeroCategories.map { c ->
        val bigAmt = BigInteger.valueOf(c.totalPaise).multiply(big100)
        val quotient = bigAmt.divide(bigGrandTotal).toInt()
        val remainder = bigAmt.remainder(bigGrandTotal)
        TempItem(
            category = c.category,
            totalPaise = c.totalPaise,
            count = c.count,
            percent = quotient,
            remainder = remainder
        )
    }

    val sumBase = tempItems.sumOf { it.percent }
    val remainingPoints = 100 - sumBase

    if (remainingPoints > 0) {
        val priorityComparator = Comparator<TempItem> { a, b ->
            val cmpRem = b.remainder.compareTo(a.remainder)
            if (cmpRem != 0) return@Comparator cmpRem
            val cmpTotal = b.totalPaise.compareTo(a.totalPaise)
            if (cmpTotal != 0) return@Comparator cmpTotal
            a.category.compareTo(b.category)
        }

        val sortedForAllocation = tempItems.sortedWith(priorityComparator)
        for (i in 0 until remainingPoints.coerceAtMost(sortedForAllocation.size)) {
            sortedForAllocation[i].percent += 1
        }
    }

    val finalComparator = compareByDescending<CategorySpendItem> { it.totalPaise }
        .thenBy { it.category }

    return tempItems.map {
        CategorySpendItem(
            category = it.category,
            totalPaise = it.totalPaise,
            count = it.count,
            percent = it.percent
        )
    }.sortedWith(finalComparator)
}

