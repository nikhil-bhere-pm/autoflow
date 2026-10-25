package com.autoflow.app.presentation.ui.form

import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.presentation.util.CurrencyFormatter

object ExpenseFormLogic {
    const val MAX_AMOUNT_PAISE = 10_000_000_000L // ₹10,00,00,000 (10 crore)
    const val MAX_NOTE_LENGTH = 200

    fun parseAmount(input: String): Long? {
        val paise = CurrencyFormatter.parseRupeesToPaise(input) ?: return null
        return if (paise <= MAX_AMOUNT_PAISE) paise else null
    }

    fun isAmountValid(input: String): Boolean = parseAmount(input) != null

    fun isFutureDateTime(dateTimeMillis: Long, nowMillis: Long = System.currentTimeMillis()): Boolean {
        return dateTimeMillis > nowMillis
    }
}

data class AddExpenseFormState(
    val amountInput: String = "",
    val merchantInput: String = "",
    val selectedCategory: String = "Food",
    val selectedPaymentMode: String = "CASH",
    val isCustomDate: Boolean = false,
    val selectedTimestamp: Long = 0L,
    val now: Long = System.currentTimeMillis()
) {
    val parsedAmountPaise: Long? = ExpenseFormLogic.parseAmount(amountInput)
    val isAmountValid: Boolean = parsedAmountPaise != null
    val isFutureDate: Boolean = isCustomDate && ExpenseFormLogic.isFutureDateTime(selectedTimestamp, now)
    val isSaveEnabled: Boolean = isAmountValid && !isFutureDate
    val effectiveDateTime: Long = if (isCustomDate) selectedTimestamp else now
    val amountHint: String? = if (!isAmountValid) "Save turns on once the amount is valid" else null
}

data class EditTransactionFormState(
    val initialId: Long,
    val initialAmountPaise: Long,
    val initialSource: TransactionSource,
    val initialMerchant: String,
    val initialCategory: String,
    val initialDateTime: Long,
    val initialPaymentMode: String,
    val initialNote: String?,
    val initialReviewStatus: ReviewStatus,
    val amountInput: String,
    val merchantInput: String,
    val selectedCategory: String,
    val selectedDateTime: Long,
    val selectedPaymentMode: String,
    val noteInput: String,
    val now: Long = System.currentTimeMillis()
) {
    val isSms: Boolean = initialSource != TransactionSource.MANUAL_CASH
    val isAmountEditable: Boolean = !isSms
    val isExcluded: Boolean = initialReviewStatus == ReviewStatus.EXCLUDED

    val parsedAmountPaise: Long? = if (isAmountEditable) {
        ExpenseFormLogic.parseAmount(amountInput)
    } else {
        initialAmountPaise
    }

    val isAmountValid: Boolean = parsedAmountPaise != null
    val isFutureDate: Boolean = ExpenseFormLogic.isFutureDateTime(selectedDateTime, now)
    val isNoteValid: Boolean = noteInput.length <= ExpenseFormLogic.MAX_NOTE_LENGTH

    val isDirty: Boolean = run {
        val amountChanged = isAmountEditable && (parsedAmountPaise != initialAmountPaise)
        val merchantChanged = merchantInput.trim() != initialMerchant.trim()
        val categoryChanged = selectedCategory != initialCategory
        val dateChanged = selectedDateTime != initialDateTime
        val modeChanged = selectedPaymentMode != initialPaymentMode
        val noteChanged = noteInput.trim() != (initialNote ?: "").trim()

        amountChanged || merchantChanged || categoryChanged || dateChanged || modeChanged || noteChanged
    }

    val isSaveEnabled: Boolean = isDirty && isAmountValid && !isFutureDate && isNoteValid && selectedCategory.isNotBlank()
    val shouldShowDiscardDialog: Boolean = isDirty
    val noteCounterText: String = "${noteInput.length}/${ExpenseFormLogic.MAX_NOTE_LENGTH}"
    val secondaryButtonText: String = if (isExcluded) "Count this expense" else "Not an expense"

    val modeSelectedIndex: Int = when (selectedPaymentMode.uppercase()) {
        "CASH" -> 0
        "CREDIT" -> 1
        "BANK" -> 2
        "UPI" -> 3
        else -> -1
    }
}
