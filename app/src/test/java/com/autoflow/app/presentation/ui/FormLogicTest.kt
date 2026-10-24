package com.autoflow.app.presentation.ui

import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.presentation.ui.form.AddExpenseFormState
import com.autoflow.app.presentation.ui.form.EditTransactionFormState
import com.autoflow.app.presentation.ui.form.ExpenseFormLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormLogicTest {

    @Test
    fun testAddExpenseAmountParsing() {
        // Empty
        assertNull(ExpenseFormLogic.parseAmount(""))
        assertNull(ExpenseFormLogic.parseAmount("   "))

        // 0
        assertNull(ExpenseFormLogic.parseAmount("0"))
        assertNull(ExpenseFormLogic.parseAmount("0.0"))
        assertNull(ExpenseFormLogic.parseAmount("0.00"))

        // Whole rupees: 130 -> 13000 paise
        assertEquals(13000L, ExpenseFormLogic.parseAmount("130"))

        // 1 decimal: 130.5 -> 13050 paise
        assertEquals(13050L, ExpenseFormLogic.parseAmount("130.5"))

        // 2 decimals: 130.50 -> 13050 paise
        assertEquals(13050L, ExpenseFormLogic.parseAmount("130.50"))

        // More than 2 decimals: 130.555 -> null
        assertNull(ExpenseFormLogic.parseAmount("130.555"))

        // Letters / invalid: abc -> null
        assertNull(ExpenseFormLogic.parseAmount("abc"))
        assertNull(ExpenseFormLogic.parseAmount("12a"))

        // Maximum: 10 crore rupees = 10,000,000,000 paise
        assertEquals(10_000_000_000L, ExpenseFormLogic.parseAmount("100000000"))
        assertEquals(10_000_000_000L, ExpenseFormLogic.parseAmount("100000000.00"))

        // Above maximum: 10,000,000,001 paise -> null
        assertNull(ExpenseFormLogic.parseAmount("100000000.01"))
        assertNull(ExpenseFormLogic.parseAmount("100000001"))
    }

    @Test
    fun testAddExpenseSaveEnabledRules() {
        val now = 1000000L

        // Empty amount: Save disabled
        val stateEmpty = AddExpenseFormState(amountInput = "", now = now)
        assertFalse(stateEmpty.isSaveEnabled)
        assertEquals("Save turns on once the amount is valid", stateEmpty.amountHint)

        // Invalid amount (0): Save disabled
        val stateZero = AddExpenseFormState(amountInput = "0", now = now)
        assertFalse(stateZero.isSaveEnabled)
        assertEquals("Save turns on once the amount is valid", stateZero.amountHint)

        // Valid amount (130) + default "Now" date: Save enabled
        val stateValid = AddExpenseFormState(amountInput = "130", now = now)
        assertTrue(stateValid.isSaveEnabled)
        assertNull(stateValid.amountHint)

        // Valid amount + future date: Save disabled
        val stateFuture = AddExpenseFormState(
            amountInput = "130",
            isCustomDate = true,
            selectedTimestamp = now + 5000L,
            now = now
        )
        assertTrue(stateFuture.isFutureDate)
        assertFalse(stateFuture.isSaveEnabled)
    }

    @Test
    fun testAddExpenseFutureDateRejected() {
        val now = 2000000L

        // Past timestamp
        assertFalse(ExpenseFormLogic.isFutureDateTime(now - 10000L, now))

        // Exact current timestamp
        assertFalse(ExpenseFormLogic.isFutureDateTime(now, now))

        // Future timestamp
        assertTrue(ExpenseFormLogic.isFutureDateTime(now + 1000L, now))
    }

    @Test
    fun testAddExpenseDefaultModeCash() {
        val state = AddExpenseFormState()
        assertEquals("CASH", state.selectedPaymentMode)
        assertEquals("Food", state.selectedCategory)
    }

    @Test
    fun testEditTransactionDirtyTracking() {
        val now = 5000000L
        val baseState = EditTransactionFormState(
            initialId = 1L,
            initialAmountPaise = 13000L,
            initialSource = TransactionSource.MANUAL_CASH,
            initialMerchant = "Tea Stall",
            initialCategory = "Food",
            initialDateTime = now - 1000L,
            initialPaymentMode = "CASH",
            initialNote = "Morning tea",
            initialReviewStatus = ReviewStatus.CONFIRMED,
            amountInput = "130",
            merchantInput = "Tea Stall",
            selectedCategory = "Food",
            selectedDateTime = now - 1000L,
            selectedPaymentMode = "CASH",
            noteInput = "Morning tea",
            now = now
        )

        // Unmodified: not dirty
        assertFalse(baseState.isDirty)
        assertFalse(baseState.isSaveEnabled)

        // Merchant changed -> dirty
        assertTrue(baseState.copy(merchantInput = "Coffee Stall").isDirty)

        // Category changed -> dirty
        assertTrue(baseState.copy(selectedCategory = "Groceries").isDirty)

        // Date changed -> dirty
        assertTrue(baseState.copy(selectedDateTime = now - 2000L).isDirty)

        // Payment mode changed -> dirty
        assertTrue(baseState.copy(selectedPaymentMode = "UPI").isDirty)

        // Note changed -> dirty
        assertTrue(baseState.copy(noteInput = "Evening tea").isDirty)

        // Amount changed (manual cash) -> dirty
        assertTrue(baseState.copy(amountInput = "140").isDirty)
    }

    @Test
    fun testEditTransactionSmsAmountLockedAndManualEditable() {
        val now = 5000000L

        // SMS transaction
        val smsState = EditTransactionFormState(
            initialId = 1L,
            initialAmountPaise = 25000L,
            initialSource = TransactionSource.SMS_AUTO,
            initialMerchant = "Zomato",
            initialCategory = "Food",
            initialDateTime = now - 1000L,
            initialPaymentMode = "UPI",
            initialNote = null,
            initialReviewStatus = ReviewStatus.CONFIRMED,
            amountInput = "250",
            merchantInput = "Zomato",
            selectedCategory = "Food",
            selectedDateTime = now - 1000L,
            selectedPaymentMode = "UPI",
            noteInput = "",
            now = now
        )
        assertTrue(smsState.isSms)
        assertFalse(smsState.isAmountEditable)

        // Manual cash transaction
        val manualState = smsState.copy(
            initialSource = TransactionSource.MANUAL_CASH
        )
        assertFalse(manualState.isSms)
        assertTrue(manualState.isAmountEditable)
    }

    @Test
    fun testEditTransactionRestoreLabelOnExcludedRows() {
        val now = 5000000L
        val baseState = EditTransactionFormState(
            initialId = 1L,
            initialAmountPaise = 10000L,
            initialSource = TransactionSource.SMS_AUTO,
            initialMerchant = "Self Transfer",
            initialCategory = "Others",
            initialDateTime = now - 1000L,
            initialPaymentMode = "BANK",
            initialNote = null,
            initialReviewStatus = ReviewStatus.EXCLUDED,
            amountInput = "100",
            merchantInput = "Self Transfer",
            selectedCategory = "Others",
            selectedDateTime = now - 1000L,
            selectedPaymentMode = "BANK",
            noteInput = "",
            now = now
        )
        assertTrue(baseState.isExcluded)
        assertEquals("Restore", baseState.secondaryButtonText)

        val confirmedState = baseState.copy(
            initialReviewStatus = ReviewStatus.CONFIRMED
        )
        assertFalse(confirmedState.isExcluded)
        assertEquals("Not an expense", confirmedState.secondaryButtonText)
    }

    @Test
    fun testEditTransactionModePreselectionIncludingUnknown() {
        val now = 5000000L
        fun stateForMode(mode: String) = EditTransactionFormState(
            initialId = 1L,
            initialAmountPaise = 10000L,
            initialSource = TransactionSource.SMS_AUTO,
            initialMerchant = "Merchant",
            initialCategory = "Food",
            initialDateTime = now - 1000L,
            initialPaymentMode = mode,
            initialNote = null,
            initialReviewStatus = ReviewStatus.CONFIRMED,
            amountInput = "100",
            merchantInput = "Merchant",
            selectedCategory = "Food",
            selectedDateTime = now - 1000L,
            selectedPaymentMode = mode,
            noteInput = "",
            now = now
        )

        assertEquals(0, stateForMode("CASH").modeSelectedIndex)
        assertEquals(1, stateForMode("CREDIT").modeSelectedIndex)
        assertEquals(2, stateForMode("BANK").modeSelectedIndex)
        assertEquals(3, stateForMode("UPI").modeSelectedIndex)
        assertEquals(-1, stateForMode("UNKNOWN").modeSelectedIndex)
    }

    @Test
    fun testEditTransactionNoteCounterAnd200Limit() {
        val now = 5000000L
        val baseState = EditTransactionFormState(
            initialId = 1L,
            initialAmountPaise = 10000L,
            initialSource = TransactionSource.MANUAL_CASH,
            initialMerchant = "Merchant",
            initialCategory = "Food",
            initialDateTime = now - 1000L,
            initialPaymentMode = "CASH",
            initialNote = null,
            initialReviewStatus = ReviewStatus.CONFIRMED,
            amountInput = "100",
            merchantInput = "Merchant",
            selectedCategory = "Food",
            selectedDateTime = now - 1000L,
            selectedPaymentMode = "CASH",
            noteInput = "Short note",
            now = now
        )

        assertEquals("10/200", baseState.noteCounterText)
        assertTrue(baseState.isNoteValid)

        // 200 characters: valid
        val note200 = "a".repeat(200)
        val state200 = baseState.copy(noteInput = note200)
        assertEquals("200/200", state200.noteCounterText)
        assertTrue(state200.isNoteValid)
        assertTrue(state200.isSaveEnabled)

        // 201 characters: invalid
        val note201 = "a".repeat(201)
        val state201 = baseState.copy(noteInput = note201)
        assertEquals("201/200", state201.noteCounterText)
        assertFalse(state201.isNoteValid)
        assertFalse(state201.isSaveEnabled)
    }

    @Test
    fun testEditTransactionDiscardDialogRule() {
        val now = 5000000L
        val cleanState = EditTransactionFormState(
            initialId = 1L,
            initialAmountPaise = 10000L,
            initialSource = TransactionSource.MANUAL_CASH,
            initialMerchant = "Merchant",
            initialCategory = "Food",
            initialDateTime = now - 1000L,
            initialPaymentMode = "CASH",
            initialNote = null,
            initialReviewStatus = ReviewStatus.CONFIRMED,
            amountInput = "100",
            merchantInput = "Merchant",
            selectedCategory = "Food",
            selectedDateTime = now - 1000L,
            selectedPaymentMode = "CASH",
            noteInput = "",
            now = now
        )
        assertFalse(cleanState.shouldShowDiscardDialog)

        val dirtyState = cleanState.copy(merchantInput = "Modified Merchant")
        assertTrue(dirtyState.shouldShowDiscardDialog)
    }
}
