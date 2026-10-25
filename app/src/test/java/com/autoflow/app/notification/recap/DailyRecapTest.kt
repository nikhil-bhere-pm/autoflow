package com.autoflow.app.notification.recap

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.preferences.InMemoryNotificationPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class DailyRecapTest {

    private fun createTx(
        id: Long,
        amountPaise: Long,
        merchant: String,
        category: String,
        reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
        recordType: RecordType = RecordType.EXPENSE,
        isDeleted: Boolean = false
    ): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            amountPaise = amountPaise,
            recordType = recordType,
            dateTime = System.currentTimeMillis(),
            merchantName = merchant,
            category = category,
            paymentMode = "UPI",
            accountNumber = null,
            bankName = null,
            reference = null,
            transactionHash = "hash_$id",
            source = com.autoflow.app.data.model.TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyReasons = emptySet(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            isDeleted = isDeleted
        )
    }

    @Test
    fun testRecapContent_withExpensesAndNoReview() {
        val txs = listOf(
            createTx(1, 20000L, "Swiggy", "Food"),
            createTx(2, 15000L, "Zomato", "Food"),
            createTx(3, 10000L, "Uber", "Transport")
        )

        val content = DailyRecapContentBuilder.buildContent(txs)
        assertNotNull(content)
        assertEquals("Today: ₹450.00 spent", content!!.title)
        assertEquals("3 expenses · Top: Food ₹350.00", content.text)
        assertEquals("AutoFlow", content.publicTitle)
        assertEquals("Your daily recap is ready", content.publicText)
    }

    @Test
    fun testRecapContent_withExpensesAndReviewItems() {
        val txs = listOf(
            createTx(1, 20000L, "Swiggy", "Food"),
            createTx(2, 15000L, "Zomato", "Food"),
            createTx(3, 10000L, "Uber", "Transport"),
            createTx(4, 5000L, "Unknown Shop 1", "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW),
            createTx(5, 7500L, "Unknown Shop 2", "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW)
        )

        val content = DailyRecapContentBuilder.buildContent(txs)
        assertNotNull(content)
        assertEquals("Today: ₹575.00 spent", content!!.title)
        assertEquals("5 expenses · Top: Food ₹350.00 · 2 need a category", content.text)
    }

    @Test
    fun testRecapContent_withSingleReviewItem() {
        val txs = listOf(
            createTx(1, 30000L, "Cafe", "Food"),
            createTx(2, 12000L, "Corner Store", "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW)
        )

        val content = DailyRecapContentBuilder.buildContent(txs)
        assertNotNull(content)
        assertEquals("Today: ₹420.00 spent", content!!.title)
        assertEquals("2 expenses · Top: Food ₹300.00 · 1 needs a category", content.text)
    }

    @Test
    fun testRecapContent_excludedAndDeletedRowsAreIgnored() {
        val txs = listOf(
            createTx(1, 25000L, "Swiggy", "Food"),
            createTx(2, 50000L, "Not Counted Shop", "Others", reviewStatus = ReviewStatus.EXCLUDED),
            createTx(3, 100000L, "Deleted Shop", "Others", isDeleted = true)
        )

        val content = DailyRecapContentBuilder.buildContent(txs)
        assertNotNull(content)
        assertEquals("Today: ₹250.00 spent", content!!.title)
        assertEquals("1 expense · Top: Food ₹250.00", content.text)
    }

    @Test
    fun testRecapContent_zeroCountedExpensesAndZeroReview_returnsNull() {
        val txs = listOf(
            createTx(1, 50000L, "Excluded", "Others", reviewStatus = ReviewStatus.EXCLUDED),
            createTx(2, 100000L, "Deleted", "Others", isDeleted = true)
        )

        val content = DailyRecapContentBuilder.buildContent(txs)
        assertNull(content)
    }

    @Test
    fun testRecapContent_zeroCountedExpensesWithReviewItems() {
        val txs = listOf(
            createTx(1, 50000L, "Excluded But Needs Review", "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW, recordType = RecordType.REFUND)
        )

        val content = DailyRecapContentBuilder.buildContent(txs)
        assertNotNull(content)
        assertEquals("Today: ₹0.00 spent", content!!.title)
        assertEquals("1 needs a category", content.text)
    }

    @Test
    fun testDelay_beforeTargetTime_sameDay() {
        val now = ZonedDateTime.of(2026, 10, 25, 14, 0, 0, 0, ZoneId.of("Asia/Kolkata"))
        val delay = DailyRecapScheduler.calculateDelayMillis(21, 0, now)
        assertEquals(Duration.ofHours(7).toMillis(), delay)
    }

    @Test
    fun testDelay_afterTargetTime_nextDay() {
        val now = ZonedDateTime.of(2026, 10, 25, 21, 30, 0, 0, ZoneId.of("Asia/Kolkata"))
        val delay = DailyRecapScheduler.calculateDelayMillis(21, 0, now)
        val expected = Duration.ofHours(23).plusMinutes(30).toMillis()
        assertEquals(expected, delay)
    }

    @Test
    fun testDelay_acrossMidnight() {
        val now = ZonedDateTime.of(2026, 10, 25, 23, 59, 0, 0, ZoneId.of("Asia/Kolkata"))
        val delay = DailyRecapScheduler.calculateDelayMillis(1, 0, now)
        val expected = Duration.ofHours(1).plusMinutes(1).toMillis()
        assertEquals(expected, delay)
    }

    @Test
    fun testDelay_otherTimezone_newYork() {
        val now = ZonedDateTime.of(2026, 10, 25, 8, 30, 0, 0, ZoneId.of("America/New_York"))
        val delay = DailyRecapScheduler.calculateDelayMillis(21, 0, now)
        val expected = Duration.ofHours(12).plusMinutes(30).toMillis()
        assertEquals(expected, delay)
    }

    @Test
    fun testNotificationPreferences_dailyRecapSettings() {
        val prefs = InMemoryNotificationPreferences()
        assertEquals(true, prefs.isDailyRecapEnabled())
        assertEquals(21, prefs.getDailyRecapTimeHour())
        assertEquals(0, prefs.getDailyRecapTimeMinute())

        prefs.setDailyRecapEnabled(false)
        assertEquals(false, prefs.isDailyRecapEnabled())

        prefs.setDailyRecapTime(20, 45)
        assertEquals(20, prefs.getDailyRecapTimeHour())
        assertEquals(45, prefs.getDailyRecapTimeMinute())
    }
}
