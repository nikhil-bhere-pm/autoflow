package com.autoflow.app.widget

import android.content.ContextWrapper
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.TransactionRepository
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.ingestion.FakeTransactionDao
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SpendWidgetTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 10, 25).atTime(15, 0).atZone(zone).toInstant()

    private fun createTx(
        id: Long = 1L,
        amountPaise: Long = 50000L,
        recordType: RecordType = RecordType.EXPENSE,
        reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
        isDeleted: Boolean = false,
        dateTimeMillis: Long = now.toEpochMilli(),
        merchant: String = "Swiggy",
        category: String = "Food"
    ): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            amountPaise = amountPaise,
            recordType = recordType,
            dateTime = dateTimeMillis,
            merchantName = merchant,
            category = category,
            categorySource = "AUTO",
            paymentMode = PaymentMode.UPI,
            note = null,
            accountNumber = "1234",
            bankName = "HDFC",
            reference = "REF$id",
            transactionHash = "HASH$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyReasons = emptySet(),
            createdAt = dateTimeMillis,
            updatedAt = dateTimeMillis,
            isDeleted = isDeleted
        )
    }

    @Test
    fun testSpendWidgetContent_zeroSpend() {
        val emptyResult = SpendWidgetContentBuilder.build(
            transactions = emptyList(),
            now = now,
            zone = zone
        )
        assertEquals("This month", emptyResult.title)
        assertEquals("₹0", emptyResult.amountFormatted)

        val zeroTxResult = SpendWidgetContentBuilder.build(
            transactions = listOf(createTx(amountPaise = 0L)),
            now = now,
            zone = zone
        )
        assertEquals("This month", zeroTxResult.title)
        assertEquals("₹0", zeroTxResult.amountFormatted)
    }

    @Test
    fun testSpendWidgetContent_largeAmount() {
        // ₹10,00,000 (100,000,000 paise)
        val largeTx = createTx(amountPaise = 100_000_000L)
        val result1 = SpendWidgetContentBuilder.build(
            transactions = listOf(largeTx),
            now = now,
            zone = zone
        )
        assertEquals("This month", result1.title)
        assertEquals("₹10,00,000", result1.amountFormatted)

        // ₹1,23,456 (12,345,600 paise)
        val tx2 = createTx(amountPaise = 12_345_600L)
        val result2 = SpendWidgetContentBuilder.build(
            transactions = listOf(tx2),
            now = now,
            zone = zone
        )
        assertEquals("₹1,23,456", result2.amountFormatted)
    }

    @Test
    fun testSpendWidgetContent_excludedRows() {
        val lastMonth = LocalDate.of(2026, 9, 20).atTime(12, 0).atZone(zone).toInstant()

        val transactions = listOf(
            // Valid expense 1: ₹500 (50,000 paise)
            createTx(id = 1L, amountPaise = 50_000L, reviewStatus = ReviewStatus.CONFIRMED),
            // Valid expense 2: ₹300 (30,000 paise, NEEDS_REVIEW is counted in spending)
            createTx(id = 2L, amountPaise = 30_000L, reviewStatus = ReviewStatus.NEEDS_REVIEW),
            // Excluded: markNotAnExpense (reviewStatus = EXCLUDED) -> ₹400
            createTx(id = 3L, amountPaise = 40_000L, reviewStatus = ReviewStatus.EXCLUDED),
            // Excluded: soft deleted -> ₹600
            createTx(id = 4L, amountPaise = 60_000L, isDeleted = true),
            // Excluded: REFUND -> ₹200
            createTx(id = 5L, amountPaise = 20_000L, recordType = RecordType.REFUND),
            // Excluded: Outside current month (September) -> ₹1,500
            createTx(id = 6L, amountPaise = 150_000L, dateTimeMillis = lastMonth.toEpochMilli())
        )

        val result = SpendWidgetContentBuilder.build(
            transactions = transactions,
            now = now,
            zone = zone
        )

        // Total should only be 500 + 300 = ₹800
        assertEquals("This month", result.title)
        assertEquals("₹800", result.amountFormatted)
    }

    @Test
    fun testSpendWidgetContent_privacyMode() {
        val tx = createTx(amountPaise = 842_000L)
        val result = SpendWidgetContentBuilder.build(
            transactions = listOf(tx),
            now = now,
            zone = zone,
            isPrivacyMode = true
        )
        assertEquals("This month", result.title)
        assertEquals("₹••••", result.amountFormatted)
    }

    @Test
    fun testTransactionRepository_updateHookTriggersOnWriteOperations() = runBlocking {
        val fakeDao = FakeTransactionDao()
        var hookCount = 0

        val repository = TransactionRepository(
            transactionDao = fakeDao,
            runInTransaction = { block -> block() },
            onTransactionWritten = { hookCount++ }
        )

        // 1. Insert
        val entity = TransactionEntity(
            id = 0L,
            amountPaise = 25000L,
            recordType = RecordType.EXPENSE,
            dateTime = now.toEpochMilli(),
            merchantName = "Chai Point",
            category = "Food",
            categorySource = "AUTO",
            paymentMode = PaymentMode.UPI,
            note = null,
            accountNumber = "1234",
            bankName = "HDFC",
            reference = "REF1",
            transactionHash = "HASH1",
            source = TransactionSource.MANUAL_CASH,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyBits = 0,
            createdAt = now.toEpochMilli(),
            updatedAt = now.toEpochMilli(),
            isDeleted = 0
        )
        val insertedId = repository.insertTransaction(entity)
        assertEquals(1, hookCount)

        // 2. Update via entity
        repository.updateTransaction(entity.copy(id = insertedId, amountPaise = 30000L))
        assertEquals(2, hookCount)

        // 3. Update via fields
        repository.updateTransaction(
            id = insertedId,
            merchantName = "Chai Point",
            category = "Food",
            dateTime = now.toEpochMilli(),
            paymentMode = PaymentMode.CASH,
            note = "Evening tea",
            amountPaise = 35000L,
            now = now.toEpochMilli()
        )
        assertEquals(3, hookCount)

        // 4. Soft delete
        repository.softDelete(insertedId)
        assertEquals(4, hookCount)

        // 5. Restore (undelete)
        repository.undelete(insertedId, now.toEpochMilli())
        assertEquals(5, hookCount)

        // 6. Mark not an expense
        val markSnapshot = repository.markNotAnExpense(insertedId, now.toEpochMilli())
        assertEquals(6, hookCount)

        // 7. Undo mark not an expense
        if (markSnapshot != null) {
            repository.undoMarkNotAnExpense(markSnapshot, now.toEpochMilli())
            assertEquals(7, hookCount)
        }
    }

    @Test
    fun testSpendWidgetUpdater_notifyDataChangedTriggersUpdateHook() {
        var hookInvoked = false
        val previousHook = SpendWidgetUpdater.updateHook

        try {
            SpendWidgetUpdater.updateHook = {
                hookInvoked = true
            }

            val mockContext = object : ContextWrapper(null) {}
            SpendWidgetUpdater.notifyDataChanged(mockContext)
            assertTrue(hookInvoked)
        } finally {
            SpendWidgetUpdater.updateHook = previousHook
        }
    }
}
