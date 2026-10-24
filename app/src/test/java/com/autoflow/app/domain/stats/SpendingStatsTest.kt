package com.autoflow.app.domain.stats

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.model.FinancialTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class SpendingStatsTest {

    private val kolkataZone = ZoneId.of("Asia/Kolkata")
    private val utcZone = ZoneId.of("UTC")

    private fun createTx(
        id: Long = 1L,
        amountPaise: Long = 10_000L,
        dateTime: Long,
        category: String = "Food",
        recordType: RecordType = RecordType.EXPENSE,
        reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
        isDeleted: Boolean = false
    ): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            amountPaise = amountPaise,
            recordType = recordType,
            dateTime = dateTime,
            merchantName = "Test Merchant",
            category = category,
            accountNumber = "1234",
            bankName = "Axis Bank",
            reference = "REF$id",
            transactionHash = "hash$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyReasons = emptySet(),
            createdAt = dateTime,
            updatedAt = dateTime,
            isDeleted = isDeleted
        )
    }

    // -------------------------------------------------------------------------
    // 1. Period Range & Boundaries (Day, Week, Month, Leap Year, Time Zone)
    // -------------------------------------------------------------------------

    @Test
    fun periodRange_today_dayBoundary_inAsiaKolkata() {
        // 24 Oct 2026, 15:30:00 IST
        val now = ZonedDateTime.of(2026, 10, 24, 15, 30, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.TODAY, now, kolkataZone)

        val expectedStart = ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, kolkataZone).toInstant()
        val expectedEnd = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, kolkataZone).toInstant()

        assertEquals(expectedStart, range.start)
        assertEquals(expectedEnd, range.end)

        // 23:59:59.999 is in today
        val endOfToday = ZonedDateTime.of(2026, 10, 24, 23, 59, 59, 999_000_000, kolkataZone).toInstant()
        assertTrue(range.contains(endOfToday))

        // 00:00:00.000 next day is outside
        assertFalse(range.contains(expectedEnd))

        // Specific prompt requirement: A record at 2026-10-31T18:45:00Z is 1 Nov 00:15 IST and must count as NOVEMBER
        val utcRecordTime = Instant.parse("2026-10-31T18:45:00Z")
        val octRange = periodRange(StatsPeriod.THIS_MONTH, ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant(), kolkataZone)
        val novRange = periodRange(StatsPeriod.THIS_MONTH, ZonedDateTime.of(2026, 11, 15, 12, 0, 0, 0, kolkataZone).toInstant(), kolkataZone)

        assertFalse(octRange.contains(utcRecordTime))
        assertTrue(novRange.contains(utcRecordTime))
    }

    @Test
    fun periodRange_thisWeek_weekBoundaries_mondayStart() {
        // Wednesday 21 Oct 2026, 12:00:00 IST
        val wednesday = ZonedDateTime.of(2026, 10, 21, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_WEEK, wednesday, kolkataZone)

        val mondayStart = ZonedDateTime.of(2026, 10, 19, 0, 0, 0, 0, kolkataZone).toInstant()
        val nextMondayStart = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, kolkataZone).toInstant()

        assertEquals(mondayStart, range.start)
        assertEquals(nextMondayStart, range.end)

        // Sunday 23:59:59.999 is inside this week
        val sundayNight = ZonedDateTime.of(2026, 10, 25, 23, 59, 59, 999_000_000, kolkataZone).toInstant()
        assertTrue(range.contains(sundayNight))

        // Monday 00:00:00.000 is next week (outside)
        assertFalse(range.contains(nextMondayStart))

        // Same check when "now" is Sunday
        val sundayNow = ZonedDateTime.of(2026, 10, 25, 18, 0, 0, 0, kolkataZone).toInstant()
        val sundayRange = periodRange(StatsPeriod.THIS_WEEK, sundayNow, kolkataZone)
        assertEquals(mondayStart, sundayRange.start)
        assertEquals(nextMondayStart, sundayRange.end)

        // Same check when "now" is Monday
        val mondayNow = ZonedDateTime.of(2026, 10, 19, 9, 0, 0, 0, kolkataZone).toInstant()
        val mondayRange = periodRange(StatsPeriod.THIS_WEEK, mondayNow, kolkataZone)
        assertEquals(mondayStart, mondayRange.start)
        assertEquals(nextMondayStart, mondayRange.end)
    }

    @Test
    fun periodRange_thisMonth_leapYearAndMonthLengths() {
        // Leap year 2028: February has 29 days
        val feb2028 = ZonedDateTime.of(2028, 2, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val febRange = periodRange(StatsPeriod.THIS_MONTH, feb2028, kolkataZone)

        val feb1 = ZonedDateTime.of(2028, 2, 1, 0, 0, 0, 0, kolkataZone).toInstant()
        val mar1 = ZonedDateTime.of(2028, 3, 1, 0, 0, 0, 0, kolkataZone).toInstant()

        assertEquals(feb1, febRange.start)
        assertEquals(mar1, febRange.end)

        val feb29Night = ZonedDateTime.of(2028, 2, 29, 23, 59, 59, 999_000_000, kolkataZone).toInstant()
        assertTrue(febRange.contains(feb29Night))
        assertFalse(febRange.contains(mar1))

        // 30-day month: April 2026
        val apr2026 = ZonedDateTime.of(2026, 4, 10, 12, 0, 0, 0, kolkataZone).toInstant()
        val aprRange = periodRange(StatsPeriod.THIS_MONTH, apr2026, kolkataZone)
        assertEquals(ZonedDateTime.of(2026, 4, 1, 0, 0, 0, 0, kolkataZone).toInstant(), aprRange.start)
        assertEquals(ZonedDateTime.of(2026, 5, 1, 0, 0, 0, 0, kolkataZone).toInstant(), aprRange.end)
    }

    // -------------------------------------------------------------------------
    // 2. Monthly Series (5 months, year boundary, future points)
    // -------------------------------------------------------------------------

    @Test
    fun monthlySeries_acrossYearBoundary() {
        // selected = Jan 2027, current = Dec 2026
        // 5 points: Nov 2026, Dec 2026, Jan 2027, Feb 2027, Mar 2027
        val selected = YearMonth.of(2027, 1)
        val current = YearMonth.of(2026, 12)

        val novTx = createTx(id = 1L, amountPaise = 15_000L, dateTime = ZonedDateTime.of(2026, 11, 10, 12, 0, 0, 0, utcZone).toInstant().toEpochMilli())
        val decTx = createTx(id = 2L, amountPaise = 25_000L, dateTime = ZonedDateTime.of(2026, 12, 10, 12, 0, 0, 0, utcZone).toInstant().toEpochMilli())
        val janTx = createTx(id = 3L, amountPaise = 35_000L, dateTime = ZonedDateTime.of(2027, 1, 10, 12, 0, 0, 0, utcZone).toInstant().toEpochMilli())

        val series = monthlySeries(listOf(novTx, decTx, janTx), selected, current, utcZone)

        assertEquals(5, series.size)

        // Point 0: Nov 2026 (past)
        assertEquals(YearMonth.of(2026, 11), series[0].month)
        assertEquals(15_000L, series[0].totalPaise)
        assertFalse(series[0].isFuture)

        // Point 1: Dec 2026 (current month -> not future)
        assertEquals(YearMonth.of(2026, 12), series[1].month)
        assertEquals(25_000L, series[1].totalPaise)
        assertFalse(series[1].isFuture)

        // Point 2: Jan 2027 (future relative to Dec 2026 -> total 0)
        assertEquals(YearMonth.of(2027, 1), series[2].month)
        assertEquals(0L, series[2].totalPaise)
        assertTrue(series[2].isFuture)

        // Point 3: Feb 2027 (future -> total 0)
        assertEquals(YearMonth.of(2027, 2), series[3].month)
        assertEquals(0L, series[3].totalPaise)
        assertTrue(series[3].isFuture)

        // Point 4: Mar 2027 (future -> total 0)
        assertEquals(YearMonth.of(2027, 3), series[4].month)
        assertEquals(0L, series[4].totalPaise)
        assertTrue(series[4].isFuture)
    }

    @Test
    fun monthlySeries_selectedEqualsCurrentMonth_hasTwoFuturePoints() {
        val current = YearMonth.of(2026, 10)
        val selected = YearMonth.of(2026, 10)

        val augTx = createTx(id = 1L, amountPaise = 10_000L, dateTime = ZonedDateTime.of(2026, 8, 5, 12, 0, 0, 0, kolkataZone).toInstant().toEpochMilli())
        val sepTx = createTx(id = 2L, amountPaise = 20_000L, dateTime = ZonedDateTime.of(2026, 9, 5, 12, 0, 0, 0, kolkataZone).toInstant().toEpochMilli())
        val octTx = createTx(id = 3L, amountPaise = 30_000L, dateTime = ZonedDateTime.of(2026, 10, 5, 12, 0, 0, 0, kolkataZone).toInstant().toEpochMilli())

        val series = monthlySeries(listOf(augTx, sepTx, octTx), selected, current, kolkataZone)

        assertEquals(5, series.size)
        // Aug, Sep, Oct (past & current)
        assertEquals(YearMonth.of(2026, 8), series[0].month)
        assertEquals(10_000L, series[0].totalPaise)
        assertFalse(series[0].isFuture)

        assertEquals(YearMonth.of(2026, 9), series[1].month)
        assertEquals(20_000L, series[1].totalPaise)
        assertFalse(series[1].isFuture)

        assertEquals(YearMonth.of(2026, 10), series[2].month)
        assertEquals(30_000L, series[2].totalPaise)
        assertFalse(series[2].isFuture)

        // Nov, Dec (2 future points with total 0)
        assertEquals(YearMonth.of(2026, 11), series[3].month)
        assertEquals(0L, series[3].totalPaise)
        assertTrue(series[3].isFuture)

        assertEquals(YearMonth.of(2026, 12), series[4].month)
        assertEquals(0L, series[4].totalPaise)
        assertTrue(series[4].isFuture)
    }

    @Test
    fun monthlySeries_selectedPastMonth_noFuturePointsWhenCurrentIsFarAhead() {
        val selected = YearMonth.of(2026, 6) // Jun 2026
        val current = YearMonth.of(2026, 10) // Oct 2026 (current >= selected + 2)

        val aprTx = createTx(id = 1L, amountPaise = 10_000L, dateTime = ZonedDateTime.of(2026, 4, 5, 12, 0, 0, 0, kolkataZone).toInstant().toEpochMilli())
        val julTx = createTx(id = 2L, amountPaise = 40_000L, dateTime = ZonedDateTime.of(2026, 7, 5, 12, 0, 0, 0, kolkataZone).toInstant().toEpochMilli())
        val augTx = createTx(id = 3L, amountPaise = 50_000L, dateTime = ZonedDateTime.of(2026, 8, 5, 12, 0, 0, 0, kolkataZone).toInstant().toEpochMilli())

        val series = monthlySeries(listOf(aprTx, julTx, augTx), selected, current, kolkataZone)

        assertEquals(5, series.size)
        // Months: Apr, May, Jun, Jul, Aug. None should be future!
        assertEquals(listOf(
            YearMonth.of(2026, 4),
            YearMonth.of(2026, 5),
            YearMonth.of(2026, 6),
            YearMonth.of(2026, 7),
            YearMonth.of(2026, 8)
        ), series.map { it.month })

        assertTrue(series.none { it.isFuture })
        assertEquals(10_000L, series[0].totalPaise) // Apr
        assertEquals(0L, series[1].totalPaise)      // May
        assertEquals(0L, series[2].totalPaise)      // Jun
        assertEquals(40_000L, series[3].totalPaise) // Jul (after selected, but in past relative to current)
        assertEquals(50_000L, series[4].totalPaise) // Aug
    }

    // -------------------------------------------------------------------------
    // 3. Category Breakdown & Largest-Remainder Percentage Rounding
    // -------------------------------------------------------------------------

    @Test
    fun categoryBreakdown_threeEqualThirds_roundsTo34And33And33Deterministically() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        // Three categories: "Groceries", "Shopping", "Bills", each 100 paise
        val tx1 = createTx(id = 1L, amountPaise = 100L, category = "Shopping", dateTime = baseTime.toEpochMilli())
        val tx2 = createTx(id = 2L, amountPaise = 100L, category = "Bills", dateTime = baseTime.toEpochMilli())
        val tx3 = createTx(id = 3L, amountPaise = 100L, category = "Groceries", dateTime = baseTime.toEpochMilli())

        val breakdown = categoryBreakdown(listOf(tx1, tx2, tx3), range)

        assertEquals(300L, breakdown.totalPaise)
        assertEquals(3, breakdown.items.size)

        // Sorted by total descending (all equal), then category name ascending:
        // "Bills", "Groceries", "Shopping"
        assertEquals("Bills", breakdown.items[0].category)
        assertEquals("Groceries", breakdown.items[1].category)
        assertEquals("Shopping", breakdown.items[2].category)

        // Ties in remainder broken by larger total, then category name ascending ("Bills" gets the extra remainder)
        assertEquals(34, breakdown.items[0].percent)
        assertEquals(33, breakdown.items[1].percent)
        assertEquals(33, breakdown.items[2].percent)

        // Sum of percents must be EXACTLY 100
        assertEquals(100, breakdown.items.sumOf { it.percent })
    }

    @Test
    fun categoryBreakdown_singleCategory_returns100Percent() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        val tx = createTx(id = 1L, amountPaise = 50_000L, category = "Food", dateTime = baseTime.toEpochMilli())
        val breakdown = categoryBreakdown(listOf(tx), range)

        assertEquals(50_000L, breakdown.totalPaise)
        assertEquals(1, breakdown.items.size)
        assertEquals("Food", breakdown.items[0].category)
        assertEquals(100, breakdown.items[0].percent)
        assertEquals(1, breakdown.items[0].count)
    }

    @Test
    fun categoryBreakdown_roundingAlwaysSumsTo100() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        // 6 categories with amounts that generate fractions: 10, 20, 30, 40, 50, 60
        // Total = 210
        val amounts = listOf(10L, 20L, 30L, 40L, 50L, 60L)
        val txs = amounts.mapIndexed { idx, amt ->
            createTx(id = idx.toLong() + 1, amountPaise = amt, category = "Cat$idx", dateTime = baseTime.toEpochMilli())
        }

        val breakdown = categoryBreakdown(txs, range)
        assertEquals(210L, breakdown.totalPaise)
        assertEquals(6, breakdown.items.size)
        assertEquals(100, breakdown.items.sumOf { it.percent })
    }

    @Test
    fun categoryBreakdown_tinyShare_doesNotRoundToNegativeOrBreakSum() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        val txBig = createTx(id = 1L, amountPaise = 99_999L, category = "Main", dateTime = baseTime.toEpochMilli())
        val txTiny = createTx(id = 2L, amountPaise = 1L, category = "Tiny", dateTime = baseTime.toEpochMilli())

        val breakdown = categoryBreakdown(listOf(txBig, txTiny), range)
        assertEquals(100_000L, breakdown.totalPaise)
        assertEquals(2, breakdown.items.size)

        val mainItem = breakdown.items.first { it.category == "Main" }
        val tinyItem = breakdown.items.first { it.category == "Tiny" }

        assertEquals(100, mainItem.percent)
        assertEquals(0, tinyItem.percent)
        assertTrue(tinyItem.percent >= 0)
        assertEquals(100, breakdown.items.sumOf { it.percent })
    }

    @Test
    fun categoryBreakdown_emptyInput_returnsZeroTotalAndNoDivisionError() {
        val range = periodRange(StatsPeriod.TODAY, Instant.now(), kolkataZone)
        val breakdown = categoryBreakdown(emptyList(), range)

        assertEquals(0L, breakdown.totalPaise)
        assertTrue(breakdown.items.isEmpty())
    }

    @Test
    fun categoryBreakdown_zeroSpendCategoriesAreOmitted() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        val txPos = createTx(id = 1L, amountPaise = 5000L, category = "Valid", dateTime = baseTime.toEpochMilli())
        val txZero = createTx(id = 2L, amountPaise = 0L, category = "ZeroCat", dateTime = baseTime.toEpochMilli())

        val breakdown = categoryBreakdown(listOf(txPos, txZero), range)
        assertEquals(1, breakdown.items.size)
        assertEquals("Valid", breakdown.items[0].category)
    }

    @Test
    fun categoryBreakdown_sorting_totalDescendingThenNameAscending() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        val tx1 = createTx(id = 1L, amountPaise = 2000L, category = "Beta", dateTime = baseTime.toEpochMilli())
        val tx2 = createTx(id = 2L, amountPaise = 5000L, category = "Charlie", dateTime = baseTime.toEpochMilli())
        val tx3 = createTx(id = 3L, amountPaise = 2000L, category = "Alpha", dateTime = baseTime.toEpochMilli())

        val breakdown = categoryBreakdown(listOf(tx1, tx2, tx3), range)

        // Sorted: Charlie (5000), Alpha (2000), Beta (2000)
        assertEquals(3, breakdown.items.size)
        assertEquals("Charlie", breakdown.items[0].category)
        assertEquals("Alpha", breakdown.items[1].category)
        assertEquals("Beta", breakdown.items[2].category)
    }

    // -------------------------------------------------------------------------
    // 4. Filtering Rules & Overflow Safety
    // -------------------------------------------------------------------------

    @Test
    fun stats_filteringRules_excludedDeletedRefundAndPendingReview() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        val validExpense = createTx(id = 1L, amountPaise = 10_000L, reviewStatus = ReviewStatus.CONFIRMED, dateTime = baseTime.toEpochMilli())
        val pendingReview = createTx(id = 2L, amountPaise = 5_000L, reviewStatus = ReviewStatus.NEEDS_REVIEW, dateTime = baseTime.toEpochMilli())
        val excludedTx = createTx(id = 3L, amountPaise = 50_000L, reviewStatus = ReviewStatus.EXCLUDED, dateTime = baseTime.toEpochMilli())
        val deletedTx = createTx(id = 4L, amountPaise = 20_000L, isDeleted = true, dateTime = baseTime.toEpochMilli())
        val refundTx = createTx(id = 5L, amountPaise = 10_000L, recordType = RecordType.REFUND, dateTime = baseTime.toEpochMilli())

        val all = listOf(validExpense, pendingReview, excludedTx, deletedTx, refundTx)
        val breakdown = categoryBreakdown(all, range)

        // Only validExpense (10000) + pendingReview (5000) = 15000
        assertEquals(15_000L, breakdown.totalPaise)
    }

    @Test
    fun stats_largeAmountsDoNotOverflow() {
        val baseTime = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, kolkataZone).toInstant()
        val range = periodRange(StatsPeriod.THIS_MONTH, baseTime, kolkataZone)

        // 5 records each near MAX_AMOUNT_PAISE (10,000,000,000 paise = 10 billion paise)
        val largeAmount = 9_500_000_000L
        val txs = (1..5).map { i ->
            createTx(id = i.toLong(), amountPaise = largeAmount, category = "Asset$i", dateTime = baseTime.toEpochMilli())
        }

        val breakdown = categoryBreakdown(txs, range)
        val expectedTotal = largeAmount * 5L
        assertEquals(expectedTotal, breakdown.totalPaise)
        assertEquals(100, breakdown.items.sumOf { it.percent })

        // Check monthlySeries with large amounts
        val currentMonth = YearMonth.of(2026, 10)
        val series = monthlySeries(txs, currentMonth, currentMonth, kolkataZone)
        assertEquals(expectedTotal, series[2].totalPaise)
    }

    @Test
    fun dayTotal_neverNegative_and_correctForDate() {
        val date = LocalDate.of(2026, 10, 24)
        val dayStart = date.atStartOfDay(kolkataZone).toInstant().toEpochMilli()

        val tx1 = createTx(id = 1L, amountPaise = 12_000L, dateTime = dayStart + 3600_000L) // 1 AM
        val tx2 = createTx(id = 2L, amountPaise = 8_000L, dateTime = dayStart + 7200_000L)  // 2 AM
        val txOtherDay = createTx(id = 3L, amountPaise = 50_000L, dateTime = dayStart - 1000L) // Previous day

        val total = dayTotal(listOf(tx1, tx2, txOtherDay), date, kolkataZone)
        assertEquals(20_000L, total)
        assertTrue(total >= 0L)
    }
}
