package com.autoflow.app.data.adapter

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.model.UncertaintyReason
import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class AutoFlowParserAdapterTest {

    private val baseTimestamp = 1700000000000L

    @Test
    fun testPaiseConversion_exactPrecision_noFloatingPointDrift() {
        // ₹125.50 -> 12550 paise
        val parsed1 = createParsed(amount = BigDecimal("125.50"), type = TransactionType.EXPENSE, merchant = "Swiggy")
        val entity1 = AutoFlowParserAdapter.adapt(parsed1, "debited Rs 125.50", baseTimestamp)
        assertNotNull(entity1)
        assertEquals(12550L, entity1!!.amountPaise)

        // ₹0.28 -> 28 paise
        val parsed2 = createParsed(amount = BigDecimal("0.28"), type = TransactionType.EXPENSE, merchant = "Test")
        val entity2 = AutoFlowParserAdapter.adapt(parsed2, "debited Rs 0.28", baseTimestamp)
        assertNotNull(entity2)
        assertEquals(28L, entity2!!.amountPaise)

        // ₹12,345.67 -> 1234567 paise
        val parsed3 = createParsed(amount = BigDecimal("12345.67"), type = TransactionType.EXPENSE, merchant = "Croma")
        val entity3 = AutoFlowParserAdapter.adapt(parsed3, "debited Rs 12,345.67", baseTimestamp)
        assertNotNull(entity3)
        assertEquals(1234567L, entity3!!.amountPaise)

        // Zero amount -> returns null (strictly positive invariant)
        val parsedZero = createParsed(amount = BigDecimal.ZERO, type = TransactionType.EXPENSE)
        assertNull(AutoFlowParserAdapter.adapt(parsedZero, "debited Rs 0", baseTimestamp))
    }

    @Test
    fun testExpenseClassification_standardDebit() {
        val parsed = createParsed(
            amount = BigDecimal("450.00"),
            type = TransactionType.EXPENSE,
            merchant = "Starbucks",
            reference = "UPI123456789012"
        )
        val entity = AutoFlowParserAdapter.adapt(parsed, "Rs 450 debited at Starbucks", baseTimestamp)
        assertNotNull(entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(45000L, entity.amountPaise)
        assertEquals("Starbucks", entity.merchantName)
        assertEquals("UPI123456789012", entity.reference)
        assertEquals(ReviewStatus.CONFIRMED, entity.reviewStatus)
        assertEquals(0, entity.uncertaintyBits)
        assertEquals(TransactionSource.SMS_AUTO, entity.source)
    }

    @Test
    fun testRefundPrecedence_takesPrecedenceOverIncome() {
        // A refund message that parser-core classified as INCOME
        val parsedRefund = createParsed(
            amount = BigDecimal("350.00"),
            type = TransactionType.INCOME,
            merchant = "Swiggy"
        )
        val smsText = "Rs. 350.00 refunded to your account for Swiggy order"
        val entity = AutoFlowParserAdapter.adapt(parsedRefund, smsText, baseTimestamp)

        assertNotNull(entity)
        assertEquals(RecordType.REFUND, entity!!.recordType)
        assertEquals(35000L, entity.amountPaise)
        assertEquals(ReviewStatus.NEEDS_REVIEW, entity.reviewStatus)

        // Uncertainty must include UNMATCHED_REFUND
        val reasons = UncertaintyReason.fromBitmask(entity.uncertaintyBits)
        assertTrue(reasons.contains(UncertaintyReason.UNMATCHED_REFUND))
    }

    @Test
    fun testReversalAndCashback_classifiedAsRefund() {
        val parsed = createParsed(amount = BigDecimal("50.00"), type = TransactionType.INCOME, merchant = "GPay")
        val cashbackEntity = AutoFlowParserAdapter.adapt(parsed, "You received cashback of Rs 50", baseTimestamp)
        assertNotNull(cashbackEntity)
        assertEquals(RecordType.REFUND, cashbackEntity!!.recordType)

        val reversalEntity = AutoFlowParserAdapter.adapt(parsed, "Transaction reversal of Rs 50 processed", baseTimestamp)
        assertNotNull(reversalEntity)
        assertEquals(RecordType.REFUND, reversalEntity!!.recordType)
    }

    @Test
    fun testOrdinarySalaryAndIncome_ignored() {
        // Salary credit without refund keywords must be ignored by AutoFlow (expense-only app)
        val parsedSalary = createParsed(
            amount = BigDecimal("75000.00"),
            type = TransactionType.INCOME,
            merchant = "Acme Corp"
        )
        val smsText = "Your Salary of Rs 75,000.00 has been credited to your account"
        val entity = AutoFlowParserAdapter.adapt(parsedSalary, smsText, baseTimestamp)
        assertNull("Ordinary salary/income must be ignored", entity)

        val parsedDeposit = createParsed(
            amount = BigDecimal("5000.00"),
            type = TransactionType.INCOME,
            merchant = "Friend"
        )
        val depositText = "Account credited with Rs 5,000.00 from John"
        assertNull("Peer deposit without refund keyword must be ignored",
            AutoFlowParserAdapter.adapt(parsedDeposit, depositText, baseTimestamp))
    }

    @Test
    fun testBalanceInquiries_dropped() {
        val parsedBalance = createParsed(
            amount = BigDecimal("10000.00"),
            type = TransactionType.BALANCE_UPDATE,
            merchant = null
        )
        val result = AutoFlowParserAdapter.adapt(parsedBalance, "Avail bal Rs 10,000", baseTimestamp)
        assertNull("Balance updates must be dropped", result)
    }

    @Test
    fun testInvestments_dropped() {
        val parsedInvestment = createParsed(
            amount = BigDecimal("2000.00"),
            type = TransactionType.INVESTMENT,
            merchant = "Zerodha"
        )
        val result = AutoFlowParserAdapter.adapt(parsedInvestment, "SIP Rs 2,000 executed for Mutual Fund", baseTimestamp)
        assertNull("Investment transactions must be dropped", result)
    }

    @Test
    fun testInternalTransfers_ignored() {
        val parsedInternalTransfer = createParsed(
            amount = BigDecimal("5000.00"),
            type = TransactionType.TRANSFER,
            fromAccount = "1234",
            toAccount = "1234"
        )
        val result = AutoFlowParserAdapter.adapt(parsedInternalTransfer, "Transferred between own accounts", baseTimestamp)
        assertNull("Self account transfers must be ignored", result)
    }

    @Test
    fun testUncertaintyReasons_unknownMerchant() {
        // Missing merchant
        val parsed = createParsed(amount = BigDecimal("200.00"), type = TransactionType.EXPENSE, merchant = null)
        val entity = AutoFlowParserAdapter.adapt(parsed, "Rs 200 debited via UPI", baseTimestamp)
        assertNotNull(entity)
        assertEquals(ReviewStatus.NEEDS_REVIEW, entity!!.reviewStatus)
        val reasons = UncertaintyReason.fromBitmask(entity.uncertaintyBits)
        assertTrue(reasons.contains(UncertaintyReason.UNKNOWN_MERCHANT))

        // Generic placeholder merchant
        val parsedGeneric = createParsed(amount = BigDecimal("200.00"), type = TransactionType.EXPENSE, merchant = "UPI Payment")
        val entityGeneric = AutoFlowParserAdapter.adapt(parsedGeneric, "Rs 200 debited", baseTimestamp)
        assertNotNull(entityGeneric)
        val reasonsGeneric = UncertaintyReason.fromBitmask(entityGeneric!!.uncertaintyBits)
        assertTrue(reasonsGeneric.contains(UncertaintyReason.UNKNOWN_MERCHANT))
    }

    @Test
    fun testTransactionHash_stableAndNonBlank() {
        val parsed = createParsed(
            amount = BigDecimal("999.00"),
            type = TransactionType.EXPENSE,
            merchant = "Netflix",
            transactionHash = "precomputedHash123"
        )
        val entity = AutoFlowParserAdapter.adapt(parsed, "Rs 999 debited for Netflix", baseTimestamp)
        assertNotNull(entity)
        assertEquals("precomputedHash123", entity!!.transactionHash)
        assertTrue(entity.transactionHash.isNotBlank())
    }

    @Test
    fun testLiveBankParserFactory_hdfcExpense() {
        val hdfcSms = "Sent Rs.350.00 From HDFC Bank A/C *1234 To ZOMATO PVT LTD On 23/10/26 Ref 328912345678 Not You? Call 18005556789"
        val entity = AutoFlowParserAdapter.parseAndAdapt(hdfcSms, "HDFCBK", baseTimestamp)

        assertNotNull(entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(35000L, entity.amountPaise)
        assertEquals("1234", entity.accountNumber)
        assertEquals("HDFC Bank", entity.bankName)
        assertTrue(entity.merchantName.contains("ZOMATO", ignoreCase = true))
        assertTrue(entity.transactionHash.isNotBlank())
    }

    @Test
    fun testLiveBankParserFactory_hdfcRefund() {
        val hdfcRefundSms = "Dear Customer, INR 250.00 has been refunded to your account **1234 on 23-OCT-26 for SWIGGY order. Info: REF123456."
        val entity = AutoFlowParserAdapter.parseAndAdapt(hdfcRefundSms, "HDFCBK", baseTimestamp)

        assertNotNull(entity)
        assertEquals(RecordType.REFUND, entity!!.recordType)
        assertEquals(25000L, entity.amountPaise)
        assertEquals(ReviewStatus.NEEDS_REVIEW, entity.reviewStatus)
        val reasons = UncertaintyReason.fromBitmask(entity.uncertaintyBits)
        assertTrue(reasons.contains(UncertaintyReason.UNMATCHED_REFUND))
    }

    @Test
    fun testIsNonTransactionMessage_identifiesAllNonFinancialAlerts() {
        val nonTransactions = listOf(
            "123456 is the OTP for your transaction of Rs.1,500.00 at AMAZON. Do not share it with anyone.",
            "Your credit limit on Axis Bank Card no. XX1234 is now Rs.2,50,000. Avail exciting benefits today.",
            "Spent INR 15,000 on your Axis Card this month. Pay now to avoid late fees.",
            "Your payment of Rs.350.00 to SWIGGY has failed. No amount has been debited from A/c XX1234.",
            "Rs.499.00 will be debited from A/c XX1234 on 28-10-26 for NETFLIX subscription.",
            "Get flat Rs.200 off! Spend Rs.1,000 on your Card this weekend. T&C apply.",
            "Total Amount Due on your Axis Card XX1234 is Rs.8,450.50. Pay by 10-Nov-2026.",
            "Payment of Rs.5,000.00 received towards your Axis Bank Card XX1234. Thank you."
        )

        for (msg in nonTransactions) {
            assertTrue("Expected isNonTransactionMessage to be true for: $msg", AutoFlowParserAdapter.isNonTransactionMessage(msg))
            assertNull("Expected parseAndAdapt to return null for: $msg", AutoFlowParserAdapter.parseAndAdapt(msg, "AXISBK", baseTimestamp))
        }
    }

    @Test
    fun testIsNonTransactionMessage_doesNotFlagLegitimateTransactions() {
        val legitimateTransactions = listOf(
            "Spent INR 130 \nusing Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at \nSWIGGY INST. \nAvl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
            "Sent Rs. 450.00 from HDFC Bank A/C *1234 to SWIGGY on 23-10-24 via UPI Ref 429712345678. Bal Rs 10550.",
            "INR 2000.00 debited from A/c no. XX589034 on AXIS BANK L 04-11-2025 16:06:39 IST. Avl bal: INR 98919.81. Not you? SMS BLOCKCARD XX0192 to +919951860002 - Axis Bank",
            "INR 1028.00 debited from A/c no. XX312225 on RESTAURANT XY 02-12-2025 20:38:23 IST. Avl bal: INR 10000.00. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank",
            "INR 209.00 debited from A/c no. XX312225 on BURGRILL 04-12-2025 13:13:27 IST. Avl bal: INR 12000.00. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank",
            "Spent INR 89.50 \nAxis Bank Card no. XX9876 \n24-10-26 08:30:00 IST \nSTARBUCKS \nAvl Limit: INR 50000.00 \nNot you? SMS BLOCK 9876 to 919951860002",
            "Refund of Rs. 350.00 credited to HDFC Bank A/C *1234 from ZOMATO on 23-10-24 via UPI Ref 429799998888. Bal Rs 10900.",
            "INR 130.00 debited from A/c no. XX1234 to BLINKIT. Bal INR 4870.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
            "INR 220.00 debited from A/c no. XX1234 to SWIGGY via UPI. Bal INR 4300.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
            "Spent INR 500 using Axis Bank Credit Card no. XX1234 on 24-10-26 15:00:00 IST at BIGBASKET. Avl limit: INR 35000.0. Not you? SMS BLOCK 1234 to +919951860002.",
            // Narrowed guard positive controls
            "INR 500.00 debited from A/c no. XX1234 on 24-10-26 to CAFE. Bal INR 4500.00. Never share OTP, CVV or PIN with anyone.",
            "INR 250.00 debited from A/c no. XX1234 to SWIGGY. Bal INR 4250.00. Do not share your OTP with anyone.",
            "INR 1,450.00 has been spent on your card XX1234 at RELIANCE RETAIL. Available credit limit INR 45,000.00.",
            "Refund of Rs 350.00 for your cancelled order at SWIGGY credited to A/c XX1234.",
            "Reversal of Rs 500.00 for failed UPI transaction credited to A/c XX1234.",
            "INR 350.00 debited from A/c no. XX1234 to SWIGGY on 24-10-26 via UPI. Avl Bal INR 12500.00. Not you? Call 18004195959",
            "Spent INR 450 using Axis Bank Credit Card no. XX1234 at STARBUCKS. Transaction successful. Avl limit: INR 40000.0.",
            "Spent INR 650 using Axis Bank Credit Card no. XX1234 on 24-10-26 14:00:00 IST at AMAZON. Avl limit: INR 38000.0. Pay now to clear dues.",
            "INR 120.00 debited from A/c no. XX1234 to CHAI POINT on 24-10-26. Bal INR 5000.00. Reminder: Keep your card details safe.",
            "INR 2000.00 debited from A/c no. XX1234 for ATM cash withdrawal on 24-10-26. Avl bal INR 8000.00. Beware of fraud! Never reveal sensitive data to strangers."
        )

        for (msg in legitimateTransactions) {
            org.junit.Assert.assertFalse("Expected isNonTransactionMessage to be false for: $msg", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        }
    }

    @Test
    fun testRow1_failedTransactionWithPromisedReversal_ignored() {
        val msg = "Your UPI txn of Rs 500.00 to ABC has failed. If debited, the amount will be reversed within 3-5 working days."
        assertTrue("Expected Row 1 to be identified as non-transaction", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        assertNull("Expected parseAndAdapt to return null for promised reversal", AutoFlowParserAdapter.parseAndAdapt(msg, "VK-HDFCBK", baseTimestamp))
    }

    @Test
    fun testRow2_cancelledOrderWithPromisedRefund_ignored() {
        val msg = "Order cancelled. Refund of Rs 799.00 will be initiated to your original payment mode within 5-7 days."
        assertTrue("Expected Row 2 to be identified as non-transaction", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        assertNull("Expected parseAndAdapt to return null for promised refund", AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp))
    }

    @Test
    fun testRow4_debitAlertWithOneTimePasswordWarning_ingestedAsExpense() {
        val msg = "Dear Customer, never disclose your One Time Password to anyone. Rs 450.00 debited from A/c XX1234 at ZOMATO on 24-10-26."
        org.junit.Assert.assertFalse("Expected Row 4 to NOT be blocked by OTP guard", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-UNIONB-S", baseTimestamp)
        assertNotNull("Expected parseAndAdapt to parse valid expense with OTP warning footer", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(45000L, entity.amountPaise)
        assertTrue("Expected merchant to contain ZOMATO", entity.merchantName.contains("ZOMATO", ignoreCase = true))
    }



    private fun createParsed(
        amount: BigDecimal,
        type: TransactionType,
        merchant: String? = "Test Merchant",
        reference: String? = "REF123",
        accountLast4: String? = "1234",
        balance: BigDecimal? = null,
        creditLimit: BigDecimal? = null,
        fromAccount: String? = null,
        toAccount: String? = null,
        transactionHash: String? = null
    ): ParsedTransaction {
        return ParsedTransaction(
            amount = amount,
            type = type,
            merchant = merchant,
            reference = reference,
            accountLast4 = accountLast4,
            balance = balance,
            creditLimit = creditLimit,
            smsBody = "in-memory-snippet",
            sender = "TESTBK",
            timestamp = baseTimestamp,
            bankName = "Test Bank",
            transactionHash = transactionHash,
            isFromCard = false,
            currency = "INR",
            fromAccount = fromAccount,
            toAccount = toAccount
        )
    }
}
