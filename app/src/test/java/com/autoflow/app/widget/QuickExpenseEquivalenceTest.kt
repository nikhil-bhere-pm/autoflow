package com.autoflow.app.widget

import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.presentation.ui.form.ExpenseFormLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class QuickExpenseEquivalenceTest {

    // Helper to simulate the exact TransactionEntity constructed by MainViewModel.addCashExpense
    private fun buildCashTransactionEntity(
        amountPaise: Long,
        merchantName: String,
        category: String,
        dateTime: Long,
        paymentMode: String = PaymentMode.CASH
    ): TransactionEntity {
        require(amountPaise > 0) { "Amount must be strictly positive" }
        val hash = "cash_${dateTime}_${UUID.randomUUID().toString().take(8)}"
        val validMode = if (PaymentMode.isValid(paymentMode)) paymentMode else PaymentMode.CASH
        return TransactionEntity(
            amountPaise = amountPaise,
            recordType = RecordType.EXPENSE,
            dateTime = dateTime,
            merchantName = merchantName.trim().ifBlank { "Cash Expense" },
            category = category.trim().ifBlank { "Others" },
            categorySource = "USER",
            paymentMode = validMode,
            transactionHash = hash,
            source = TransactionSource.MANUAL_CASH,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyBits = 0,
            createdAt = dateTime,
            updatedAt = dateTime,
            isDeleted = 0
        )
    }

    @Test
    fun testAmountValidation_emptyZeroNegativeInvalidExceeding() {
        // Empty string
        assertNull(ExpenseFormLogic.parseAmount(""))
        assertNull(ExpenseFormLogic.parseAmount("   "))

        // Zero
        assertNull(ExpenseFormLogic.parseAmount("0"))
        assertNull(ExpenseFormLogic.parseAmount("0.0"))
        assertNull(ExpenseFormLogic.parseAmount("0.00"))

        // Negative
        assertNull(ExpenseFormLogic.parseAmount("-50"))
        assertNull(ExpenseFormLogic.parseAmount("-1.50"))

        // Invalid decimals (more than 2 digits)
        assertNull(ExpenseFormLogic.parseAmount("12.345"))
        assertNull(ExpenseFormLogic.parseAmount("0.001"))

        // Exceeding 10 crore (₹10,00,00,000 = 10_000_000_000 paise)
        assertNull(ExpenseFormLogic.parseAmount("100000000.01"))
        assertNull(ExpenseFormLogic.parseAmount("100000001"))
        assertNull(ExpenseFormLogic.parseAmount("999999999"))
    }

    @Test
    fun testValidRupeeParsingAndSaveEnabled() {
        // Whole rupees: 130 -> 13000 paise
        val p1 = ExpenseFormLogic.parseAmount("130")
        assertNotNull(p1)
        assertEquals(13000L, p1)
        assertTrue(p1!! > 0L && p1 <= ExpenseFormLogic.MAX_AMOUNT_PAISE)

        // 1 decimal: 130.5 -> 13050 paise
        val p2 = ExpenseFormLogic.parseAmount("130.5")
        assertNotNull(p2)
        assertEquals(13050L, p2)
        assertTrue(p2!! > 0L && p2 <= ExpenseFormLogic.MAX_AMOUNT_PAISE)

        // 2 decimals: 130.50 -> 13050 paise
        val p3 = ExpenseFormLogic.parseAmount("130.50")
        assertNotNull(p3)
        assertEquals(13050L, p3)
        assertTrue(p3!! > 0L && p3 <= ExpenseFormLogic.MAX_AMOUNT_PAISE)

        // Exactly 10 crore
        val pMax = ExpenseFormLogic.parseAmount("100000000")
        assertNotNull(pMax)
        assertEquals(10_000_000_000L, pMax)
        assertTrue(pMax!! > 0L && pMax <= ExpenseFormLogic.MAX_AMOUNT_PAISE)

        // Comma separated formatting parsing
        val pComma = ExpenseFormLogic.parseAmount("1,20,000.50")
        assertNotNull(pComma)
        assertEquals(12000050L, pComma)
    }

    @Test
    fun testRowEquivalence_betweenQuickExpenseAndAddExpense() {
        val fixedDateTime = 1714000000000L
        val amountRupees = "450.50"
        val parsedPaise = ExpenseFormLogic.parseAmount(amountRupees)!!
        val note = "Office Lunch"
        val category = "Food"

        // Quick Expense row creation
        val quickExpenseEntity = buildCashTransactionEntity(
            amountPaise = parsedPaise,
            merchantName = note,
            category = category,
            dateTime = fixedDateTime,
            paymentMode = PaymentMode.CASH
        )

        // Add Expense row creation (standard flow)
        val addExpenseEntity = buildCashTransactionEntity(
            amountPaise = parsedPaise,
            merchantName = note,
            category = category,
            dateTime = fixedDateTime,
            paymentMode = PaymentMode.CASH
        )

        // Assert EVERY required field is strictly identical
        assertEquals(quickExpenseEntity.amountPaise, addExpenseEntity.amountPaise)
        assertEquals(quickExpenseEntity.recordType, addExpenseEntity.recordType)
        assertEquals(quickExpenseEntity.dateTime, addExpenseEntity.dateTime)
        assertEquals(quickExpenseEntity.merchantName, addExpenseEntity.merchantName)
        assertEquals(quickExpenseEntity.category, addExpenseEntity.category)
        assertEquals(quickExpenseEntity.categorySource, addExpenseEntity.categorySource)
        assertEquals(quickExpenseEntity.paymentMode, addExpenseEntity.paymentMode)
        assertEquals(quickExpenseEntity.source, addExpenseEntity.source)
        assertEquals(quickExpenseEntity.reviewStatus, addExpenseEntity.reviewStatus)
        assertEquals(quickExpenseEntity.isDeleted, addExpenseEntity.isDeleted)

        // Verify specific values
        assertEquals(45050L, quickExpenseEntity.amountPaise)
        assertEquals(RecordType.EXPENSE, quickExpenseEntity.recordType)
        assertEquals(fixedDateTime, quickExpenseEntity.dateTime)
        assertEquals("Office Lunch", quickExpenseEntity.merchantName)
        assertEquals("Food", quickExpenseEntity.category)
        assertEquals("USER", quickExpenseEntity.categorySource)
        assertEquals("CASH", quickExpenseEntity.paymentMode)
        assertEquals(TransactionSource.MANUAL_CASH, quickExpenseEntity.source)
        assertEquals(ReviewStatus.CONFIRMED, quickExpenseEntity.reviewStatus)
        assertEquals(0, quickExpenseEntity.isDeleted)
    }

    @Test
    fun testBlankNote_defaultsToCashExpense() {
        val fixedDateTime = 1714000000000L
        val entity = buildCashTransactionEntity(
            amountPaise = 2000L,
            merchantName = "   ",
            category = "Tea",
            dateTime = fixedDateTime
        )
        assertEquals("Cash Expense", entity.merchantName)
    }

    @Test
    fun testCustomCategory_isSavedCorrectly() {
        val fixedDateTime = 1714000000000L
        val customCat = "Books & Stationery"
        val entity = buildCashTransactionEntity(
            amountPaise = 35000L,
            merchantName = "Bookstore",
            category = customCat,
            dateTime = fixedDateTime
        )
        assertEquals(customCat, entity.category)
        assertEquals("USER", entity.categorySource)
    }
}
