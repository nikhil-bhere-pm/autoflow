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

    @Test
    fun testPromptM_promiseMessages_ignored() {
        // 1. "Refund has been initiated for your cancelled order of Rs 799.00."
        val msg1 = "Refund has been initiated for your cancelled order of Rs 799.00."
        assertTrue("Expected Promise 1 to be non-transaction", AutoFlowParserAdapter.isNonTransactionMessage(msg1))
        assertNull("Expected Promise 1 to return null", AutoFlowParserAdapter.parseAndAdapt(msg1, "VM-HDFCBK", baseTimestamp))

        // 2. "Rs 500.00 will get reversed to your account if debited."
        val msg2 = "Rs 500.00 will get reversed to your account if debited."
        assertTrue("Expected Promise 2 to be non-transaction", AutoFlowParserAdapter.isNonTransactionMessage(msg2))
        assertNull("Expected Promise 2 to return null", AutoFlowParserAdapter.parseAndAdapt(msg2, "VM-HDFCBK", baseTimestamp))

        // 3. "Rs 799.00 will be credited back to your card XX1234."
        val msg3 = "Rs 799.00 will be credited back to your card XX1234."
        assertTrue("Expected Promise 3 to be non-transaction", AutoFlowParserAdapter.isNonTransactionMessage(msg3))
        assertNull("Expected Promise 3 to return null", AutoFlowParserAdapter.parseAndAdapt(msg3, "VM-HDFCBK", baseTimestamp))
    }

    @Test
    fun testPromptM_completedRefunds_ingested() {
        // 4 & 7. "Rs 799.00 refunded to your card XX1234 for order at MYNTRA."
        val msgE = "Rs 799.00 refunded to your card XX1234 for order at MYNTRA."
        org.junit.Assert.assertFalse("Expected msgE to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msgE))
        val entityE = AutoFlowParserAdapter.parseAndAdapt(msgE, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entityE to be parsed", entityE)
        assertEquals(RecordType.REFUND, entityE!!.recordType)
        assertEquals(79900L, entityE.amountPaise)
        assertEquals("MYNTRA", entityE.merchantName)

        // 5 & 8. "Rs 250.00 reversed to A/c XX1234 for failed UPI txn at SWIGGY. Ref 123456789."
        val msgF = "Rs 250.00 reversed to A/c XX1234 for failed UPI txn at SWIGGY. Ref 123456789."
        org.junit.Assert.assertFalse("Expected msgF to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msgF))
        val entityF = AutoFlowParserAdapter.parseAndAdapt(msgF, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entityF to be parsed", entityF)
        assertEquals(RecordType.REFUND, entityF!!.recordType)
        assertEquals(25000L, entityF.amountPaise)
        assertEquals("SWIGGY", entityF.merchantName)

        // 6. "Rs 799.00 refunded to your card XX1234 and will reflect within 3 days."
        val msg6 = "Rs 799.00 refunded to your card XX1234 and will reflect within 3 days."
        org.junit.Assert.assertFalse("Expected msg6 to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msg6))
        val entity6 = AutoFlowParserAdapter.parseAndAdapt(msg6, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity6 to be parsed", entity6)
        assertEquals(RecordType.REFUND, entity6!!.recordType)
        assertEquals(79900L, entity6.amountPaise)
    }

    @Test
    fun testPromiseGuard_doesNotDropDebitWithCashbackPromiseFooter() {
        val msg = "Rs 450.00 debited from A/c XX1234 at ZOMATO on 24-10-26. Cashback of Rs 20 will be credited within 7 days."
        org.junit.Assert.assertFalse("Expected msg 1 to not be blocked by promise guard", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity 1 to be parsed", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(45000L, entity.amountPaise)
        assertEquals("ZOMATO", entity.merchantName)
    }

    @Test
    fun testPromiseGuard_doesNotDropSpentWithRewardPointsPromiseFooter() {
        val msg = "Spent INR 130 using Axis Bank Credit Card no. XX7997 on 24-10-26 09:59:42 IST at SWIGGY INST. Reward points will be credited within 3 days. Avl limit: INR 44673.4."
        org.junit.Assert.assertFalse("Expected msg 2 to not be blocked by promise guard", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "AX-AXISBK-S", baseTimestamp)
        assertNotNull("Expected entity 2 to be parsed", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(13000L, entity.amountPaise)
        assertEquals("SWIGGY INST", entity.merchantName)
    }

    @Test
    fun testPromiseGuard_doesNotDropDebitWithRefundPromiseFooter() {
        val msg = "Rs 1,200.00 debited at AMAZON on 24-10-26. Refund, if applicable, will be credited within 5-7 days."
        org.junit.Assert.assertFalse("Expected msg 3 to not be blocked by promise guard", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity 3 to be parsed", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(120000L, entity.amountPaise)
        assertEquals("AMAZON", entity.merchantName)
    }

    @Test
    fun testPromptM2_rowE_refundWithPaidMention_ingestedAsRefund() {
        val msg = "Rs 799.00 refunded to your card XX1234 for the order paid on 12-Oct at MYNTRA."
        org.junit.Assert.assertFalse("Expected msg e to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity e to be parsed", entity)
        assertEquals(RecordType.REFUND, entity!!.recordType)
        assertEquals(79900L, entity.amountPaise)
        assertEquals("MYNTRA", entity.merchantName)
    }

    @Test
    fun testPromptM2_rowF_refundWithDebitedEarlier_ingestedAsRefund() {
        val msg = "Refund of Rs. 350.00 credited to HDFC Bank A/C *1234 from ZOMATO on 23-10-24 for the amount debited earlier."
        org.junit.Assert.assertFalse("Expected msg f to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity f to be parsed", entity)
        assertEquals(RecordType.REFUND, entity!!.recordType)
        assertEquals(35000L, entity.amountPaise)
        assertEquals("ZOMATO", entity.merchantName)
    }

    @Test
    fun testPromptM2_rowG_reversalWithDebitedEarlier_ingestedAsRefund() {
        val msg = "Refund credited: Rs 250.00 for your cancelled ride with SWIGGY. Amount debited earlier is reversed."
        org.junit.Assert.assertFalse("Expected msg g to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity g to be parsed", entity)
        assertEquals(RecordType.REFUND, entity!!.recordType)
        assertEquals(25000L, entity.amountPaise)
        assertEquals("SWIGGY", entity.merchantName)
    }

    @Test
    fun testPromptM3_message4_debitAlertTransferredTo_ingestedAsExpense() {
        val msg = "Debit alert: Rs 1,000.00 transferred to RAMESH KUMAR; amount credited successfully. Ref 123."
        org.junit.Assert.assertFalse("Expected msg 4 to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity 4 to be parsed", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(100000L, entity.amountPaise)
    }

    @Test
    fun testPromptM3_message5_debitWithCashbackCredited_ingestedAsExpense() {
        val msg = "Rs 450.00 debited at ZOMATO. Cashback of Rs 20 credited to your wallet."
        org.junit.Assert.assertFalse("Expected msg 5 to not be blocked", AutoFlowParserAdapter.isNonTransactionMessage(msg))
        val entity = AutoFlowParserAdapter.parseAndAdapt(msg, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity 5 to be parsed", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(45000L, entity.amountPaise)
        assertEquals("ZOMATO", entity.merchantName)
    }

    @Test
    fun testPromptM4_messageA_transferredToYourAccountAsRefund_ingestedAsRefund() {
        val msg = "Rs 799.00 has been transferred to your account XX1234 as refund for order at MYNTRA."
        val senders = listOf("VM-HDFCBK", "ICICIB", "SBIINB")
        for (sender in senders) {
            val entity = AutoFlowParserAdapter.parseAndAdapt(msg, sender, baseTimestamp)
            assertNotNull("Expected message a to be parsed for $sender", entity)
            assertEquals("Expected REFUND for $sender", RecordType.REFUND, entity!!.recordType)
            assertEquals(79900L, entity.amountPaise)
            assertEquals("MYNTRA", entity.merchantName)
        }
    }

    @Test
    fun testPromptM4_messagesBCD_incomingOrNonSpend_notIngestedAsExpense() {
        val messages = mapOf(
            "b" to "Your payout of Rs 5,000.00 has been transferred to your bank account XX1234.",
            "c" to "Rs 2,500.00 credited to A/c XX1234 on 24-10-26. Amount transferred to your account by RAMESH via UPI.",
            "d" to "Credit Alert: Rs 10,000.00 transferred to your A/c XX1234 from SBI. Ref 123."
        )
        val senders = listOf("VM-HDFCBK", "ICICIB", "SBIINB")
        for ((row, msg) in messages) {
            for (sender in senders) {
                val entity = AutoFlowParserAdapter.parseAndAdapt(msg, sender, baseTimestamp)
                if (entity != null) {
                    org.junit.Assert.assertNotEquals(
                        "Row $row on sender $sender must NOT be ingested as EXPENSE",
                        RecordType.EXPENSE,
                        entity.recordType
                    )
                }
            }
        }
    }

    @Test
    fun testPromptM4_merchantExtraction_upiVpaAndMessySms() {
        // row e: VPA handle extraction
        val msgE = "Sent Rs.250.00 from HDFC Bank A/c XX1234 to ZEPTO@upi on 24/10/26. UPI Ref 123456789012."
        val entityE = AutoFlowParserAdapter.parseAndAdapt(msgE, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity E to be parsed", entityE)
        assertEquals(RecordType.EXPENSE, entityE!!.recordType)
        assertEquals(25000L, entityE.amountPaise)
        assertEquals("ZEPTO", entityE.merchantName)

        // VPA handle swiggy@upi -> SWIGGY
        val msgVpa = "Rs.500.00 debited from A/c XX1234 on 24-10-26 and credited to VPA swiggy@upi (UPI Ref No 123456789012)."
        val entityVpa = AutoFlowParserAdapter.parseAndAdapt(msgVpa, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity VPA to be parsed", entityVpa)
        assertEquals(RecordType.EXPENSE, entityVpa!!.recordType)
        assertEquals("SWIGGY", entityVpa.merchantName)

        // Phone number VPA -> stays Unknown Merchant
        val msgPhone = "Sent Rs.500.00 from A/c XX1234 to 9876543210@ybl on 24-10-26. UPI Ref 123."
        val entityPhone = AutoFlowParserAdapter.parseAndAdapt(msgPhone, "VM-HDFCBK", baseTimestamp)
        if (entityPhone != null) {
            assertEquals("Unknown Merchant", entityPhone.merchantName)
        }

        // row g: strip trailing "via UPI"
        val msgG = "Rs 640.00 debited from A/c XX1234 on 24-10-26 at SWIGGY via UPI."
        val entityG = AutoFlowParserAdapter.parseAndAdapt(msgG, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Expected entity G to be parsed", entityG)
        assertEquals(RecordType.EXPENSE, entityG!!.recordType)
        assertEquals(64000L, entityG.amountPaise)
        assertEquals("SWIGGY", entityG.merchantName)
    }

    @Test
    fun testPromptM5_expenseTypedMessage_neverDroppedByOutboundWordCheck() {
        val parsed = createParsed(
            amount = BigDecimal("1200.00"),
            type = TransactionType.EXPENSE,
            merchant = "AMAZON"
        )
        val snippet = "Txn of Rs 1,200.00 done on HDFC Bank Credit Card XX1234 at AMAZON on 24-10-26. Avl limit Rs 50,000."
        val entity = AutoFlowParserAdapter.adapt(parsed, snippet, baseTimestamp)
        assertNotNull("Expense-typed message must never be dropped by outbound-word check", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(120000L, entity.amountPaise)
    }

    @Test
    fun testPromptM5_widenOutboundWords_incomeWithNewWords_adaptedAsExpense() {
        val snippet = "A/c XX1234 Dr. Rs 640.00 on 24-10-26 UPI/SWIGGY/123456."
        val parsed = createParsed(
            amount = BigDecimal("640.00"),
            type = TransactionType.INCOME,
            merchant = "SWIGGY"
        )
        val entity = AutoFlowParserAdapter.adapt(parsed, snippet, baseTimestamp)
        assertNotNull("INCOME message with widened word 'Dr.' must be adapted as EXPENSE", entity)
        assertEquals(RecordType.EXPENSE, entity!!.recordType)
        assertEquals(64000L, entity.amountPaise)
    }

    @Test
    fun testPromptM6_inboundMoneyMustNotBecomeExpense() {
        val senders = listOf("VM-HDFCBK", "ICICIB", "SBIINB")

        // Row a: Paid by RAMESH via UPI -> null
        val msgA = "Rs 500.00 credited to A/c XX1234 on 24-10-26. Paid by RAMESH via UPI."
        for (sender in senders) {
            val resultA = AutoFlowParserAdapter.parseAndAdapt(msgA, sender, baseTimestamp)
            assertNull("Row a with sender $sender must not be ingested as EXPENSE", resultA)
        }

        // Row b: Salary credited, Paid by ACME CORP LTD -> null
        val msgB = "Salary of Rs 52,000.00 credited to A/c XX1234 on 24-10-26. Paid by ACME CORP LTD."
        for (sender in senders) {
            val resultB = AutoFlowParserAdapter.parseAndAdapt(msgB, sender, baseTimestamp)
            assertNull("Row b with sender $sender must not be ingested as EXPENSE", resultB)
        }

        // Row c: Cashback credited to wallet -> REFUND (amount 5000 paise)
        val msgC = "Cashback of Rs 50.00 credited to your wallet. Use it on your next purchase."
        for (sender in senders) {
            val resultC = AutoFlowParserAdapter.parseAndAdapt(msgC, sender, baseTimestamp)
            assertNotNull("Row c with sender $sender should be ingested as REFUND", resultC)
            assertEquals(RecordType.REFUND, resultC!!.recordType)
            assertEquals(5000L, resultC.amountPaise)
        }

        // Row d: used for bill payments -> null
        val msgD = "Rs 200.00 credited to your account. Amount can be used for bill payments."
        for (sender in senders) {
            val resultD = AutoFlowParserAdapter.parseAndAdapt(msgD, sender, baseTimestamp)
            assertNull("Row d with sender $sender must not be ingested as EXPENSE", resultD)
        }

        // Row e: Sent by ACME LTD -> null
        val msgE = "Rs 10,000.00 credited to A/c XX1234 on 24-10-26 by NEFT. Sent by ACME LTD."
        for (sender in senders) {
            val resultE = AutoFlowParserAdapter.parseAndAdapt(msgE, sender, baseTimestamp)
            assertNull("Row e with sender $sender must not be ingested as EXPENSE", resultE)
        }

        // Controls c1, c2, c3 must stay EXPENSE
        val msgC1 = "Debit alert: Rs 1,000.00 transferred to RAMESH KUMAR; amount credited successfully. Ref 123."
        val resultC1 = AutoFlowParserAdapter.parseAndAdapt(msgC1, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Control c1 must be ingested as EXPENSE", resultC1)
        assertEquals(RecordType.EXPENSE, resultC1!!.recordType)
        assertEquals(100000L, resultC1.amountPaise)

        val msgC2 = "Rs 640.00 debited from A/c XX1234 on 24-10-26 at SWIGGY via UPI."
        val resultC2 = AutoFlowParserAdapter.parseAndAdapt(msgC2, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Control c2 must be ingested as EXPENSE", resultC2)
        assertEquals(RecordType.EXPENSE, resultC2!!.recordType)
        assertEquals(64000L, resultC2.amountPaise)

        val msgC3 = "Your a/c XX1234 is debited by INR 90.00 on 24-10-26."
        val resultC3 = AutoFlowParserAdapter.parseAndAdapt(msgC3, "VM-HDFCBK", baseTimestamp)
        assertNotNull("Control c3 must be ingested as EXPENSE", resultC3)
        assertEquals(RecordType.EXPENSE, resultC3!!.recordType)
        assertEquals(9000L, resultC3.amountPaise)
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
