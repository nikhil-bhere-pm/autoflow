package com.autoflow.app.domain.model

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.model.UncertaintyReason

/**
 * Clean domain representation of a financial transaction.
 * Completely decoupled from Room database annotations.
 */
data class FinancialTransaction(
    val id: Long,
    val amountPaise: Long,
    val recordType: RecordType,
    val dateTime: Long,
    val merchantName: String,
    val category: String,
    val accountNumber: String?,
    val bankName: String?,
    val reference: String?,
    val transactionHash: String,
    val source: TransactionSource,
    val reviewStatus: ReviewStatus,
    val uncertaintyReasons: Set<UncertaintyReason>,
    val createdAt: Long,
    val updatedAt: Long,
    val isDeleted: Boolean
) {
    /**
     * Amount in INR formatted for presentation (e.g. 150000 paise -> ₹1500.00).
     */
    val amountInr: Double
        get() = amountPaise / 100.0

    /**
     * Explainable uncertainty chips to display in the UI.
     */
    val displayChips: List<String>
        get() = uncertaintyReasons.map { it.name }
}
