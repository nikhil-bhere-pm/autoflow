package com.autoflow.app.ingestion.sms

import com.autoflow.app.data.db.dao.RefundMatchDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.ingestion.DefaultIngestionPipeline
import com.autoflow.app.ingestion.FakeRefundMatchDao
import com.autoflow.app.ingestion.FakeTransactionDao
import com.autoflow.app.ingestion.IngestionPipeline
import com.autoflow.app.ingestion.IngestionResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmsMessageProcessorTest {

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var refundMatchDao: FakeRefundMatchDao
    private lateinit var pipeline: DefaultIngestionPipeline
    private lateinit var processor: SmsMessageProcessor

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        refundMatchDao = FakeRefundMatchDao()
        pipeline = DefaultIngestionPipeline(
            transactionDao = transactionDao,
            refundMatchDao = refundMatchDao,
            runInTransaction = { block -> block() }
        )
        processor = SmsMessageProcessor(ingestionPipeline = pipeline)
    }

    @Test
    fun testIrrelevantMessage_otp_ignoredWithoutError() = runTest {
        val result = processor.processMessage(
            sender = "VK-HDFCBK",
            body = "Your OTP for login to HDFC NetBanking is 543210. Do not share it with anyone.",
            timestamp = 1700000000000L
        )

        assertTrue(result is ProcessSmsResult.Ignored)
        assertEquals(0, transactionDao.items.size)
    }

    @Test
    fun testIrrelevantMessage_personalChat_ignoredWithoutError() = runTest {
        val result = processor.processMessage(
            sender = "+919876543210",
            body = "Hey, are you free this evening for dinner?",
            timestamp = 1700000000000L
        )

        assertTrue(result is ProcessSmsResult.Ignored)
        assertEquals(0, transactionDao.items.size)
    }

    @Test
    fun testIrrelevantMessage_salaryIncome_ignoredWithoutError() = runTest {
        // Salary/Income credits are not expenses and must not be ingested
        val result = processor.processMessage(
            sender = "AD-HDFCBK",
            body = "Salary of INR 85,000.00 credited to your A/C XX1234 on 01-Nov-2024. Total Bal: INR 1,20,000.00.",
            timestamp = 1700000000000L
        )

        assertTrue(result is ProcessSmsResult.Ignored)
        assertEquals(0, transactionDao.items.size)
    }

    @Test
    fun testIrrelevantMessage_balanceInquiry_ignoredWithoutError() = runTest {
        val result = processor.processMessage(
            sender = "VK-SBIBNK",
            body = "Available balance in A/C XX5678 is INR 14,250.75 as of 23-Oct-2026.",
            timestamp = 1700000000000L
        )

        assertTrue(result is ProcessSmsResult.Ignored)
        assertEquals(0, transactionDao.items.size)
    }

    @Test
    fun testValidExpenseSms_ingestedSuccessfully() = runTest {
        val result = processor.processMessage(
            sender = "VM-HDFCBK",
            body = "Sent Rs. 450.00 from HDFC Bank A/C *1234 to SWIGGY on 23-10-24 via UPI Ref 429712345678. Bal Rs 10550.",
            timestamp = 1700000000000L
        )

        assertTrue("Expected Success result for valid expense SMS", result is ProcessSmsResult.Success)
        assertEquals(1, transactionDao.items.size)

        val tx = transactionDao.items.first()
        assertEquals(45000L, tx.amountPaise) // ₹450.00
        assertEquals(RecordType.EXPENSE, tx.recordType)
        assertTrue(tx.merchantName.contains("SWIGGY", ignoreCase = true))
    }

    @Test
    fun testValidRefundSms_ingestedSuccessfully() = runTest {
        val result = processor.processMessage(
            sender = "VM-HDFCBK",
            body = "Refund of Rs. 350.00 credited to HDFC Bank A/C *1234 from ZOMATO on 23-10-24 via UPI Ref 429799998888. Bal Rs 10900.",
            timestamp = 1700000000000L
        )

        assertTrue("Expected Success result for refund SMS", result is ProcessSmsResult.Success)
        assertEquals(1, transactionDao.items.size)

        val tx = transactionDao.items.first()
        assertEquals(35000L, tx.amountPaise) // ₹350.00
        assertEquals(RecordType.REFUND, tx.recordType)
    }

    @Test
    fun testMultipartSms_assembledBody_ingestedSuccessfully() = runTest {
        // Simulating two parts of a multipart SMS concatenated by the receiver
        val part1 = "Sent Rs. 1,250.00 from HDFC Bank A/C *1234 to FLIPKART "
        val part2 = "on 23-10-24 via UPI Ref 987654321098. Bal Rs 15000."
        val assembledFullBody = part1 + part2

        val result = processor.processMessage(
            sender = "VM-HDFCBK",
            body = assembledFullBody,
            timestamp = 1700000000000L
        )

        assertTrue("Expected Success for assembled multipart SMS", result is ProcessSmsResult.Success)
        assertEquals(1, transactionDao.items.size)

        val tx = transactionDao.items.first()
        assertEquals(125000L, tx.amountPaise) // ₹1,250.00
        assertEquals(RecordType.EXPENSE, tx.recordType)
        assertEquals("987654321098", tx.reference)
    }

    @Test
    fun testDuplicateDelivery_sameSmsTwice_returnsDuplicate() = runTest {
        val sms = "Sent Rs. 200.00 from HDFC Bank A/C *1234 to UBER on 23-10-24 via UPI Ref 112233445566. Bal Rs 5000."

        val firstResult = processor.processMessage(
            sender = "VM-HDFCBK",
            body = sms,
            timestamp = 1700000000000L
        )
        assertTrue(firstResult is ProcessSmsResult.Success)
        assertEquals(1, transactionDao.items.size)

        // Carrier delivers identical SMS a second time
        val secondResult = processor.processMessage(
            sender = "VM-HDFCBK",
            body = sms,
            timestamp = 1700000000000L
        )
        assertTrue(secondResult is ProcessSmsResult.Duplicate)
        // No duplicate record inserted
        assertEquals(1, transactionDao.items.size)
    }

    @Test
    fun testPipelineFailure_handledGracefullyWithoutCrash() = runTest {
        val failingPipeline = object : IngestionPipeline {
            override suspend fun ingest(transaction: TransactionEntity): IngestionResult {
                return IngestionResult.Error("Simulated database disk full error")
            }
        }
        val processorWithFailingPipeline = SmsMessageProcessor(ingestionPipeline = failingPipeline)

        val result = processorWithFailingPipeline.processMessage(
            sender = "VM-HDFCBK",
            body = "Sent Rs. 100.00 from HDFC Bank A/C *1234 to CHAI on 23-10-24 via UPI Ref 778899001122. Bal Rs 1000.",
            timestamp = 1700000000000L
        )

        assertTrue(result is ProcessSmsResult.Error)
        assertEquals("Simulated database disk full error", (result as ProcessSmsResult.Error).message)
    }

    @Test
    fun testMalformedMessage_emptyOrWhitespace_ignoredWithoutCrash() = runTest {
        val emptyResult = processor.processMessage(
            sender = "VM-HDFCBK",
            body = "   ",
            timestamp = 1700000000000L
        )
        assertTrue(emptyResult is ProcessSmsResult.Ignored)

        val gibberishResult = processor.processMessage(
            sender = "UNKNOWN",
            body = "???###@@@!!!",
            timestamp = 1700000000000L
        )
        assertTrue(gibberishResult is ProcessSmsResult.Ignored)
        assertEquals(0, transactionDao.items.size)
    }

    @Test
    fun testNegativeGuards_allNonTransactionClasses_ignored() = runTest {
        val negativeCases = listOf(
            // 1. OTP mentioning an amount
            "VK-HDFCBK" to "123456 is the OTP for your transaction of Rs.1,500.00 at AMAZON. Do not share it with anyone.",
            // 2. Credit limit update
            "AX-AXISBK" to "Your credit limit on Axis Bank Card no. XX1234 is now Rs.2,50,000. Avail exciting benefits today.",
            // 3. Period spend summary
            "AX-AXISBK" to "Spent INR 15,000 on your Axis Card this month. Pay now to avoid late fees.",
            // 4. Failed or declined payment
            "AX-AXISBK" to "Your payment of Rs.350.00 to SWIGGY has failed. No amount has been debited from A/c XX1234.",
            // 5. Upcoming debit or mandate
            "VK-HDFCBK" to "Rs.499.00 will be debited from A/c XX1234 on 28-10-26 for NETFLIX subscription.",
            // 6. Promo or offer
            "VK-HDFCBK" to "Get flat Rs.200 off! Spend Rs.1,000 on your Card this weekend. T&C apply.",
            // 7. Card statement / bill generated
            "AX-AXISBK" to "Total Amount Due on your Axis Card XX1234 is Rs.8,450.50. Pay by 10-Nov-2026.",
            // 8. Payment received on card
            "AX-AXISBK" to "Payment of Rs.5,000.00 received towards your Axis Bank Card XX1234. Thank you."
        )

        for ((sender, message) in negativeCases) {
            val result = processor.processMessage(
                sender = sender,
                body = message,
                timestamp = 1700000000000L
            )
            assertTrue("Expected message to be ignored: $message, but got: $result", result is ProcessSmsResult.Ignored)
        }
        assertEquals("Zero transactions should be created from non-transaction messages", 0, transactionDao.items.size)
    }

    @Test
    fun testPositiveControls_atLeast10Scenarios_ingested() = runTest {
        val positiveCases = listOf(
            // 1. Swiggy credit-card multiline fixture
            "AX-AXISBK-S" to "Spent INR 130 \nusing Axis Bank Credit Card no. XX1234 on 24-10-26 09:59:42 IST at \nSWIGGY INST. \nAvl limit: INR 40000.4. Not you? SMS BLOCK 1234 to +919951860002.",
            // 2. Plain UPI debit
            "VM-HDFCBK" to "Sent Rs. 450.00 from HDFC Bank A/C *1234 to SWIGGY on 23-10-24 via UPI Ref 429712345678. Bal Rs 10550.",
            // 3. ATM withdrawal
            "AX-AXISBK" to "INR 2000.00 debited from A/c no. XX589034 on AXIS BANK L 04-11-2025 16:06:39 IST. Avl bal: INR 98919.81. Not you? SMS BLOCKCARD XX0192 to +919951860002 - Axis Bank",
            // 4. POS / Restaurant spend on debit card
            "AX-AXISBK" to "INR 1028.00 debited from A/c no. XX312225 on RESTAURANT XY 02-12-2025 20:38:23 IST. Avl bal: INR 10000.00. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank",
            // 5. Debit-card spend
            "AX-AXISBK" to "INR 209.00 debited from A/c no. XX312225 on BURGRILL 04-12-2025 13:13:27 IST. Avl bal: INR 12000.00. Not you? SMS BLOCKCARD XX0023 to +919951860002 - Axis Bank",
            // 6. Decimal card spend
            "AX-AXISBK" to "Spent INR 89.50 \nAxis Bank Card no. XX9876 \n24-10-26 08:30:00 IST \nSTARBUCKS \nAvl Limit: INR 50000.00 \nNot you? SMS BLOCK 9876 to 919951860002",
            // 7. Refund
            "VM-HDFCBK" to "Refund of Rs. 350.00 credited to HDFC Bank A/C *1234 from ZOMATO on 23-10-24 via UPI Ref 429799998888. Bal Rs 10900.",
            // 8. Debit with Avl Bal
            "AX-AXISBK" to "INR 130.00 debited from A/c no. XX1234 to BLINKIT. Bal INR 4870.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
            // 9. UPI debit to merchant
            "AX-AXISBK" to "INR 220.00 debited from A/c no. XX1234 to SWIGGY via UPI. Bal INR 4300.00. Not you? SMS BLOCK 1234 to 919951860002 - Axis Bank",
            // 10. Axis credit card single line
            "AX-AXISBK-S" to "Spent INR 500 using Axis Bank Credit Card no. XX1234 on 24-10-26 15:00:00 IST at BIGBASKET. Avl limit: INR 35000.0. Not you? SMS BLOCK 1234 to +919951860002."
        )

        var expectedCount = 0
        for ((sender, message) in positiveCases) {
            val result = processor.processMessage(
                sender = sender,
                body = message,
                timestamp = 1700000000000L + expectedCount * 1000L
            )
            assertTrue("Expected message to be successfully ingested: $message, but got: $result", result is ProcessSmsResult.Success)
            expectedCount++
            assertEquals(expectedCount, transactionDao.items.size)
        }
    }
}

