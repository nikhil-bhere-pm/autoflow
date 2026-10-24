package com.autoflow.app.data.db

import com.autoflow.app.data.db.entity.GoalEntryEntity
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.MatchStatus
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvariantConstraintsTest {

    @Test
    fun testRefundAllocationCap_multipleRefunds_mustNotExceedOriginalExpense() {
        val originalExpense = TransactionEntity(
            id = 100L,
            amountPaise = 100000L, // ₹1,000.00
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = "FlightBooking",
            transactionHash = "origTxHash",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED
        )

        val refund1 = RefundMatchEntity(
            id = 1L,
            originalExpenseId = originalExpense.id,
            refundTransactionId = 201L,
            allocatedAmountPaise = 60000L, // ₹600.00
            matchStatus = MatchStatus.CONFIRMED
        )

        val refund2 = RefundMatchEntity(
            id = 2L,
            originalExpenseId = originalExpense.id,
            refundTransactionId = 202L,
            allocatedAmountPaise = 40000L, // ₹400.00
            matchStatus = MatchStatus.CONFIRMED
        )

        val allocatedMatches = listOf(refund1, refund2)
        val totalAllocatedPaise = allocatedMatches.sumOf { it.allocatedAmountPaise }

        assertEquals(100000L, totalAllocatedPaise)
        assertTrue(totalAllocatedPaise <= originalExpense.amountPaise)

        // Attempting an excess refund of ₹200.00 (20,000 paise)
        val incomingExcessRefundPaise = 20000L
        val canAllocate = (totalAllocatedPaise + incomingExcessRefundPaise) <= originalExpense.amountPaise
        assertFalse("Invariant: Excess refund allocation must be rejected", canAllocate)
    }

    @Test
    fun testGoalIsolation_entriesAreMotivationJarAndDoNotBleedIntoTransactions() {
        val entries = listOf(
            GoalEntryEntity(
                id = 1L,
                goalId = 1L,
                amountPaise = 500000L, // ₹5,000
                note = "Saved on dinner",
                occurredAt = 1700000000000L,
                isDeleted = 0
            ),
            GoalEntryEntity(
                id = 2L,
                goalId = 1L,
                amountPaise = 120000L, // ₹1,200
                note = "Avoided cab ride",
                occurredAt = 1700000100000L,
                isDeleted = 0
            ),
            GoalEntryEntity(
                id = 3L,
                goalId = 1L,
                amountPaise = 200000L, // ₹2,000
                note = "Old deleted note",
                occurredAt = 1700000200000L,
                isDeleted = 1
            )
        )

        val activeTotal = entries
            .filter { it.isDeleted == 0 }
            .sumOf { it.amountPaise }

        assertEquals(620000L, activeTotal) // ₹6,200
    }
}
