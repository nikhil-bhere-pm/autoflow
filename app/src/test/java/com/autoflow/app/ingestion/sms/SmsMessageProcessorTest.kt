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
}
