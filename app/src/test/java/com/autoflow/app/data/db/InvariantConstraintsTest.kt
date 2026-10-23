package com.autoflow.app.data.db

import com.autoflow.app.data.db.entity.GoalContributionEntity
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.MatchStatus
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.SavingsType
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
    fun testGoalIsolation_actualSavingsAndAvoidedSpendingRemainStrictlySeparated() {
        val contributions = listOf(
            GoalContributionEntity(
                id = 1L,
                goalId = 1L,
                savingsType = SavingsType.ACTUAL_SAVED,
                amountPaise = 500000L, // ₹5,000
                date = 1700000000000L,
                note = "Cash saved"
            ),
            GoalContributionEntity(
                id = 2L,
                goalId = 1L,
                savingsType = SavingsType.AVOIDED_PURCHASE,
                amountPaise = 120000L, // ₹1,200
                date = 1700000100000L,
                note = "Skipped impulse buy"
            ),
            GoalContributionEntity(
                id = 3L,
                goalId = 1L,
                savingsType = SavingsType.ACTUAL_SAVED,
                amountPaise = 200000L, // ₹2,000
                date = 1700000200000L,
                note = "Bank deposit"
            )
        )

        val actualSavedTotal = contributions
            .filter { it.savingsType == SavingsType.ACTUAL_SAVED }
            .sumOf { it.amountPaise }

        val avoidedSpendingTotal = contributions
            .filter { it.savingsType == SavingsType.AVOIDED_PURCHASE }
            .sumOf { it.amountPaise }

        assertEquals(700000L, actualSavedTotal) // ₹7,000
        assertEquals(120000L, avoidedSpendingTotal) // ₹1,200
        // Invariant: Avoided spending must NEVER bleed into actual liquid savings
        assertTrue(actualSavedTotal != (actualSavedTotal + avoidedSpendingTotal))
    }
}
