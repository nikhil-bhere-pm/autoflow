package com.autoflow.app.data.db

import com.autoflow.app.data.db.entity.GoalContributionEntity
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.MatchStatus
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.SavingsType
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.model.UncertaintyReason
import com.autoflow.app.domain.model.FinancialTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SchemaDefinitionTest {

    private val converters = AutoFlowTypeConverters()

    @Test
    fun testTransactionEntity_validData_succeeds() {
        val tx = TransactionEntity(
            id = 1L,
            amountPaise = 250000L, // ₹2500.00
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = "Swiggy",
            category = "Food",
            accountNumber = "1234",
            bankName = "HDFC",
            reference = "UPI123456789012",
            transactionHash = "hash123",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyBits = 0
        )
        assertEquals(250000L, tx.amountPaise)
        assertEquals("Swiggy", tx.merchantName)
        assertEquals(0, tx.isDeleted)
    }

    @Test
    fun testTransactionEntity_zeroOrNegativeAmount_throwsException() {
        try {
            TransactionEntity(
                amountPaise = 0L,
                recordType = RecordType.EXPENSE,
                dateTime = 1000L,
                merchantName = "Test",
                transactionHash = "hash0",
                source = TransactionSource.MANUAL_CASH,
                reviewStatus = ReviewStatus.CONFIRMED
            )
            fail("Expected IllegalArgumentException for 0 amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }

        try {
            TransactionEntity(
                amountPaise = -5000L,
                recordType = RecordType.EXPENSE,
                dateTime = 1000L,
                merchantName = "Test",
                transactionHash = "hashNeg",
                source = TransactionSource.MANUAL_CASH,
                reviewStatus = ReviewStatus.CONFIRMED
            )
            fail("Expected IllegalArgumentException for negative amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }
    }

    @Test
    fun testTransactionEntity_blankHash_throwsException() {
        try {
            TransactionEntity(
                amountPaise = 10000L,
                recordType = RecordType.EXPENSE,
                dateTime = 1000L,
                merchantName = "Test",
                transactionHash = "",
                source = TransactionSource.SMS_AUTO,
                reviewStatus = ReviewStatus.NEEDS_REVIEW
            )
            fail("Expected IllegalArgumentException for blank hash")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Transaction hash cannot be blank") == true)
        }
    }

    @Test
    fun testRefundMatchEntity_selfRefund_throwsException() {
        try {
            RefundMatchEntity(
                originalExpenseId = 42L,
                refundTransactionId = 42L,
                allocatedAmountPaise = 5000L,
                matchStatus = MatchStatus.CONFIRMED
            )
            fail("Expected IllegalArgumentException for self-refund")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("cannot be refunded against itself") == true)
        }
    }

    @Test
    fun testGoalEntity_invariants() {
        try {
            GoalEntity(
                name = "",
                targetAmountPaise = 100000L,
                targetDate = 2000000000000L
            )
            fail("Expected blank name failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Goal name cannot be blank") == true)
        }

        try {
            GoalEntity(
                name = "Car",
                targetAmountPaise = 0L,
                targetDate = 2000000000000L
            )
            fail("Expected target amount failure")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("strictly positive") == true)
        }
    }

    @Test
    fun testUncertaintyReasonBitmask_roundTrip() {
        val reasons = setOf(
            UncertaintyReason.UNKNOWN_MERCHANT,
            UncertaintyReason.AMBIGUOUS_AMOUNT
        )
        val bitmask = UncertaintyReason.toBitmask(reasons)
        assertEquals(3, bitmask) // 1 + 2

        val recovered = UncertaintyReason.fromBitmask(bitmask)
        assertEquals(reasons, recovered)
        assertTrue(recovered.contains(UncertaintyReason.UNKNOWN_MERCHANT))
        assertTrue(recovered.contains(UncertaintyReason.AMBIGUOUS_AMOUNT))
        assertFalse(recovered.contains(UncertaintyReason.AMBIGUOUS_REFUND))
    }

    @Test
    fun testTypeConverters_roundTrip() {
        assertEquals("EXPENSE", converters.fromRecordType(RecordType.EXPENSE))
        assertEquals(RecordType.EXPENSE, converters.toRecordType("EXPENSE"))

        assertEquals("REFUND", converters.fromRecordType(RecordType.REFUND))
        assertEquals(RecordType.REFUND, converters.toRecordType("REFUND"))

        assertEquals("SMS_AUTO", converters.fromTransactionSource(TransactionSource.SMS_AUTO))
        assertEquals(TransactionSource.SMS_AUTO, converters.toTransactionSource("SMS_AUTO"))

        assertEquals("MANUAL_CASH", converters.fromTransactionSource(TransactionSource.MANUAL_CASH))
        assertEquals(TransactionSource.MANUAL_CASH, converters.toTransactionSource("MANUAL_CASH"))

        assertEquals("NEEDS_REVIEW", converters.fromReviewStatus(ReviewStatus.NEEDS_REVIEW))
        assertEquals(ReviewStatus.NEEDS_REVIEW, converters.toReviewStatus("NEEDS_REVIEW"))

        assertEquals("ACTUAL_SAVED", converters.fromSavingsType(SavingsType.ACTUAL_SAVED))
        assertEquals(SavingsType.ACTUAL_SAVED, converters.toSavingsType("ACTUAL_SAVED"))

        assertEquals("AVOIDED_PURCHASE", converters.fromSavingsType(SavingsType.AVOIDED_PURCHASE))
        assertEquals(SavingsType.AVOIDED_PURCHASE, converters.toSavingsType("AVOIDED_PURCHASE"))
    }

    @Test
    fun testDomainFinancialTransaction_inrConversion() {
        val domainTx = FinancialTransaction(
            id = 1L,
            amountPaise = 157550L, // ₹1,575.50
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = "Amazon",
            category = "Shopping",
            accountNumber = "9876",
            bankName = "ICICI",
            reference = "UPI987654321012",
            transactionHash = "hashA",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyReasons = emptySet(),
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            isDeleted = false
        )

        assertEquals(1575.50, domainTx.amountInr, 0.001)
        assertTrue(domainTx.displayChips.isEmpty())
    }
}
