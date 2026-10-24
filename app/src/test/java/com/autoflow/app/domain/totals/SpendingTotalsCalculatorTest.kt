package com.autoflow.app.domain.totals

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.model.FinancialTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class SpendingTotalsCalculatorTest {

    private val zoneId = ZoneId.of("Asia/Kolkata")
    // Reference instant: 24 Oct 2026, 12:00:00 PM IST
    private val refInstant = ZonedDateTime.of(2026, 10, 24, 12, 0, 0, 0, zoneId).toInstant()

    private fun createTx(
        id: Long = 1L,
        amountPaise: Long = 10000L, // ₹100
        dateTime: Long = refInstant.toEpochMilli(),
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
            category = "Food",
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

    @Test
    fun testTotals_reviewRowsIncluded() {
        val confirmedTx = createTx(id = 1L, amountPaise = 13000L, reviewStatus = ReviewStatus.CONFIRMED)
        val reviewTx = createTx(id = 2L, amountPaise = 25000L, reviewStatus = ReviewStatus.NEEDS_REVIEW)

        val totals = calculateSpendingTotals(listOf(confirmedTx, reviewTx), zoneId, refInstant)

        // Both CONFIRMED and NEEDS_REVIEW must be counted
        assertEquals(38000L, totals.todayPaise)
        assertEquals(38000L, totals.monthPaise)
    }

    @Test
    fun testTotals_excludedRowsNotCounted() {
        val confirmedTx = createTx(id = 1L, amountPaise = 10000L, reviewStatus = ReviewStatus.CONFIRMED)
        val excludedTx = createTx(id = 2L, amountPaise = 50000L, reviewStatus = ReviewStatus.EXCLUDED)

        val totals = calculateSpendingTotals(listOf(confirmedTx, excludedTx), zoneId, refInstant)

        assertEquals(10000L, totals.todayPaise)
        assertEquals(10000L, totals.monthPaise)
    }

    @Test
    fun testTotals_deletedRowsNotCounted() {
        val activeTx = createTx(id = 1L, amountPaise = 10000L, isDeleted = false)
        val deletedTx = createTx(id = 2L, amountPaise = 50000L, isDeleted = true)

        val totals = calculateSpendingTotals(listOf(activeTx, deletedTx), zoneId, refInstant)

        assertEquals(10000L, totals.todayPaise)
        assertEquals(10000L, totals.monthPaise)
    }

    @Test
    fun testTotals_refundsIgnored() {
        val expenseTx = createTx(id = 1L, amountPaise = 20000L, recordType = RecordType.EXPENSE)
        val refundTx = createTx(id = 2L, amountPaise = 10000L, recordType = RecordType.REFUND)

        val totals = calculateSpendingTotals(listOf(expenseTx, refundTx), zoneId, refInstant)

        // In this MVP, refunds do not reduce or change totals
        assertEquals(20000L, totals.todayPaise)
        assertEquals(20000L, totals.monthPaise)
    }

    @Test
    fun testTotals_dayBoundaries() {
        val startOfToday = ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val justBeforeToday = startOfToday - 1 // 23 Oct 23:59:59.999
        val endOfToday = ZonedDateTime.of(2026, 10, 24, 23, 59, 59, 999000000, zoneId).toInstant().toEpochMilli()
        val tomorrow = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val txYesterday = createTx(id = 1L, amountPaise = 1000L, dateTime = justBeforeToday)
        val txStartToday = createTx(id = 2L, amountPaise = 2000L, dateTime = startOfToday)
        val txEndToday = createTx(id = 3L, amountPaise = 4000L, dateTime = endOfToday)
        val txTomorrow = createTx(id = 4L, amountPaise = 8000L, dateTime = tomorrow)

        val totals = calculateSpendingTotals(
            listOf(txYesterday, txStartToday, txEndToday, txTomorrow),
            zoneId,
            refInstant
        )

        // Today: txStartToday (2000) + txEndToday (4000) = 6000
        assertEquals(6000L, totals.todayPaise)

        // This Month (Oct 2026): txYesterday, txStartToday, txEndToday, txTomorrow are all in Oct 2026 = 15000
        assertEquals(15000L, totals.monthPaise)
    }

    @Test
    fun testTotals_monthBoundaries() {
        val startOfOctober = ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, zoneId).toInstant().toEpochMilli()
        val justBeforeOctober = startOfOctober - 1 // 30 Sept 23:59:59.999
        val endOfOctober = ZonedDateTime.of(2026, 10, 31, 23, 59, 59, 999000000, zoneId).toInstant().toEpochMilli()
        val startOfNovember = ZonedDateTime.of(2026, 11, 1, 0, 0, 0, 0, zoneId).toInstant().toEpochMilli()

        val txSept = createTx(id = 1L, amountPaise = 10000L, dateTime = justBeforeOctober)
        val txOct1 = createTx(id = 2L, amountPaise = 20000L, dateTime = startOfOctober)
        val txOct31 = createTx(id = 3L, amountPaise = 30000L, dateTime = endOfOctober)
        val txNov = createTx(id = 4L, amountPaise = 40000L, dateTime = startOfNovember)

        val totals = calculateSpendingTotals(
            listOf(txSept, txOct1, txOct31, txNov),
            zoneId,
            refInstant
        )

        // Today is Oct 24, none of these 4 are on Oct 24
        assertEquals(0L, totals.todayPaise)

        // Month is October: txOct1 (20000) + txOct31 (30000) = 50000
        assertEquals(50000L, totals.monthPaise)
    }
}
