package com.autoflow.app.presentation.ui

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.presentation.viewmodel.CategoriesUiState
import com.autoflow.app.presentation.viewmodel.PeriodFilter
import com.autoflow.app.presentation.viewmodel.computeCategoriesUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class CategoriesUiTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    // Reference date: Wednesday 2026-10-24 14:00:00 IST
    private val refInstant = ZonedDateTime.of(2026, 10, 24, 14, 0, 0, 0, zone).toInstant()

    private fun timeAt(hour: Int, minute: Int, dayOffset: Long = 0): Long {
        return ZonedDateTime.of(2026, 10, 24, hour, minute, 0, 0, zone)
            .plusDays(dayOffset)
            .toInstant()
            .toEpochMilli()
    }

    private fun createTx(
        id: Long,
        amountPaise: Long,
        category: String,
        recordType: RecordType = RecordType.EXPENSE,
        reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
        dateTime: Long = timeAt(12, 0),
        merchantName: String = "Test Merchant",
        isDeleted: Boolean = false
    ): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            amountPaise = amountPaise,
            recordType = recordType,
            dateTime = dateTime,
            merchantName = merchantName,
            category = category,
            paymentMode = "UPI",
            accountNumber = null,
            bankName = null,
            reference = null,
            transactionHash = "hash_$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyReasons = emptySet(),
            createdAt = dateTime,
            updatedAt = dateTime,
            isDeleted = isDeleted
        )
    }

    @Test
    fun totalsPerCategoryAndPercentages_noRoundingDriftBeyondDisplay() {
        val txs = listOf(
            createTx(id = 1, amountPaise = 25000L, category = "Food"),      // ₹250.00
            createTx(id = 2, amountPaise = 50000L, category = "Shopping"),  // ₹500.00
            createTx(id = 3, amountPaise = 25000L, category = "Bills")      // ₹250.00
        )

        val state = computeCategoriesUiState(
            transactions = txs,
            periodFilter = PeriodFilter.Predefined(StatsPeriod.TODAY),
            now = refInstant,
            zone = zone
        )

        assertFalse("State should not be empty", state.isEmpty)
        assertEquals("Total spend must equal exact sum of paise", 100000L, state.totalSpendPaise)
        assertEquals("Must have 3 categories", 3, state.categories.size)

        // Sorted descending by amount: Shopping (50000), then Food (25000), then Bills (25000)
        val shopping = state.categories.first { it.category == "Shopping" }
        val food = state.categories.first { it.category == "Food" }
        val bills = state.categories.first { it.category == "Bills" }

        assertEquals(50000L, shopping.totalPaise)
        assertEquals(50, shopping.percent)

        assertEquals(25000L, food.totalPaise)
        assertEquals(25, food.percent)

        assertEquals(25000L, bills.totalPaise)
        assertEquals(25, bills.percent)

        val sumPaise = state.categories.sumOf { it.totalPaise }
        assertEquals(state.totalSpendPaise, sumPaise)

        // Top category should be Shopping
        assertNotNull(state.topCategory)
        assertEquals("Shopping", state.topCategory?.category)
        assertEquals(50000L, state.topCategory?.totalPaise)
        assertEquals(50, state.topCategory?.percent)
    }

    @Test
    fun refundsReduceRightCategory_asPerRule() {
        val txs = listOf(
            createTx(id = 1, amountPaise = 50000L, category = "Food", recordType = RecordType.EXPENSE),     // ₹500
            createTx(id = 2, amountPaise = 100000L, category = "Shopping", recordType = RecordType.EXPENSE), // ₹1000
            createTx(id = 3, amountPaise = 20000L, category = "Food", recordType = RecordType.REFUND)       // ₹200 refund for Food
        )

        val state = computeCategoriesUiState(
            transactions = txs,
            periodFilter = PeriodFilter.Predefined(StatsPeriod.TODAY),
            now = refInstant,
            zone = zone
        )

        // Food net spend: 50000 - 20000 = 30000 (₹300)
        // Shopping net spend: 100000 (₹1000)
        // Total spend: 130000 (₹1300)
        assertEquals(130000L, state.totalSpendPaise)
        assertEquals(2, state.categories.size)

        val food = state.categories.first { it.category == "Food" }
        val shopping = state.categories.first { it.category == "Shopping" }

        assertEquals("Shopping should be unaffected by Food refund", 100000L, shopping.totalPaise)
        assertEquals("Food should be reduced by refund amount", 30000L, food.totalPaise)

        // Percentages: Shopping = 100000/130000 = 76.92% -> 77%
        // Food = 30000/130000 = 23.08% -> 23%
        assertEquals(77, shopping.percent)
        assertEquals(23, food.percent)
    }

    @Test
    fun periodChangeUpdatesRingAndList() {
        val todayTx = createTx(id = 1, amountPaise = 40000L, category = "Food", dateTime = timeAt(10, 0))
        val yesterdayTx = createTx(id = 2, amountPaise = 60000L, category = "Transport", dateTime = timeAt(10, 0, dayOffset = -1))

        val allTxs = listOf(todayTx, yesterdayTx)

        // 1. TODAY period: only todayTx should be present
        val todayState = computeCategoriesUiState(
            transactions = allTxs,
            periodFilter = PeriodFilter.Predefined(StatsPeriod.TODAY),
            now = refInstant,
            zone = zone
        )
        assertEquals(40000L, todayState.totalSpendPaise)
        assertEquals(1, todayState.categories.size)
        assertEquals("Food", todayState.categories[0].category)

        // 2. THIS_WEEK period: both todayTx and yesterdayTx are in the current week
        val weekState = computeCategoriesUiState(
            transactions = allTxs,
            periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_WEEK),
            now = refInstant,
            zone = zone
        )
        assertEquals(100000L, weekState.totalSpendPaise)
        assertEquals(2, weekState.categories.size)
    }

    @Test
    fun emptyPeriod_returnsEmptyState() {
        val state = computeCategoriesUiState(
            transactions = emptyList(),
            periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
            now = refInstant,
            zone = zone
        )

        assertTrue(state.isEmpty)
        assertEquals(0L, state.totalSpendPaise)
        assertTrue(state.categories.isEmpty())
        assertNull(state.topCategory)
    }

    @Test
    fun customCategoriesAppearWithSpend() {
        val customTx = createTx(id = 1, amountPaise = 85000L, category = "Pets")
        val state = computeCategoriesUiState(
            transactions = listOf(customTx),
            periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
            now = refInstant,
            zone = zone
        )

        assertFalse(state.isEmpty)
        assertEquals(1, state.categories.size)
        assertEquals("Pets", state.categories[0].category)
        assertEquals(85000L, state.categories[0].totalPaise)
        assertEquals(100, state.categories[0].percent)
    }
}
