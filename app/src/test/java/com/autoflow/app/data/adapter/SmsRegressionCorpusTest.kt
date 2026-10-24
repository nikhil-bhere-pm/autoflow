package com.autoflow.app.data.adapter

import com.autoflow.app.data.model.RecordType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression corpus test for SMS classification and extraction.
 *
 * Checks:
 * - expectedResult: IGNORE (must be identified as non-transaction or parse to null)
 *                   or INGEST (must parse to an active transaction entity)
 * - expectedType: EXPENSE, REFUND, or null
 * - expectedAmountPaise: amount in paise or null
 * - expectedMerchant: substring check (case-insensitive) against merchantName, or null to skip
 */
class SmsRegressionCorpusTest {

    enum class ExpectedResult {
        IGNORE,
        INGEST
    }

    data class TestCase(
        val id: String,
        val message: String,
        val sender: String = "VM-HDFCBK",
        val expectedResult: ExpectedResult,
        val expectedType: RecordType? = null,
        val expectedAmountPaise: Long? = null,
        val expectedMerchant: String? = null // Substring check (case-insensitive)
    )

    private val baseTimestamp = 1700000000000L

    private val corpus = listOf(
        // ==========================================
        // Prompt M2 Part 1: Rows a..i
        // ==========================================
        TestCase(
            id = "M2-a",
            message = "Your UPI transaction of Rs 500.00 to ABC has failed. Amount debited, if any, will be reversed within 3-5 working days.",
            expectedResult = ExpectedResult.IGNORE
        ),
        TestCase(
            id = "M2-b",
            message = "Txn of Rs 1,200.00 at AMAZON failed. Debited amount will be refunded in 5-7 days.",
            expectedResult = ExpectedResult.IGNORE
        ),
        TestCase(
            id = "M2-c",
            message = "Payment of Rs 300.00 failed. Amount debited from your account will be reversed within 7 days.",
            expectedResult = ExpectedResult.IGNORE
        ),
        TestCase(
            id = "M2-e",
            message = "Rs 799.00 refunded to your card XX1234 for the order paid on 12-Oct at MYNTRA.",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 79900L,
            expectedMerchant = "MYNTRA"
        ),
        TestCase(
            id = "M2-f",
            message = "Refund of Rs. 350.00 credited to HDFC Bank A/C *1234 from ZOMATO on 23-10-24 for the amount debited earlier.",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 35000L,
            expectedMerchant = "ZOMATO"
        ),
        TestCase(
            id = "M2-g",
            message = "Refund credited: Rs 250.00 for your cancelled ride with SWIGGY. Amount debited earlier is reversed.",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 25000L,
            expectedMerchant = "SWIGGY"
        ),
        TestCase(
            id = "M2-h",
            message = "Rs 450.00 debited from A/c XX1234 at ZOMATO on 24-10-26. Cashback of Rs 20 will be credited within 7 days.",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 45000L,
            expectedMerchant = "ZOMATO"
        ),
        TestCase(
            id = "M2-i",
            message = "Spent INR 130 using Axis Bank Credit Card no. XX7997 on 24-10-26 09:59:42 IST at SWIGGY INST. Reward points will be credited within 3 days. Avl limit: INR 44673.4.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 13000L,
            expectedMerchant = "SWIGGY INST"
        ),

        // ==========================================
        // Prompt M: 3 Promises + 3 Completed Refunds
        // ==========================================
        TestCase(
            id = "M-Promise-1",
            message = "Refund has been initiated for your cancelled order of Rs 799.00.",
            expectedResult = ExpectedResult.IGNORE
        ),
        TestCase(
            id = "M-Promise-2",
            message = "Rs 500.00 will get reversed to your account if debited.",
            expectedResult = ExpectedResult.IGNORE
        ),
        TestCase(
            id = "M-Promise-3",
            message = "Rs 799.00 will be credited back to your card XX1234.",
            expectedResult = ExpectedResult.IGNORE
        ),
        TestCase(
            id = "M-Completed-4",
            message = "Rs 799.00 refunded to your card XX1234 for order at MYNTRA.",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 79900L,
            expectedMerchant = "MYNTRA"
        ),
        TestCase(
            id = "M-Completed-5",
            message = "Rs 250.00 reversed to A/c XX1234 for failed UPI txn at SWIGGY. Ref 123456789.",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 25000L,
            expectedMerchant = "SWIGGY"
        ),
        TestCase(
            id = "M-Completed-6",
            message = "Rs 799.00 refunded to your card XX1234 and will reflect within 3 days.",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 79900L,
            expectedMerchant = null // Merchant absent in message
        ),

        // ==========================================
        // Prompt J: 10 Positive Controls (Narrowed Guards)
        // ==========================================
        TestCase(
            id = "J-Positive-1",
            message = "INR 500.00 debited from A/c no. XX1234 on 24-10-26 to CAFE. Bal INR 4500.00. Never share OTP, CVV or PIN with anyone.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 50000L,
            expectedMerchant = "CAFE"
        ),
        TestCase(
            id = "J-Positive-2",
            message = "INR 250.00 debited from A/c no. XX1234 to SWIGGY. Bal INR 4250.00. Do not share your OTP with anyone.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 25000L,
            expectedMerchant = "SWIGGY"
        ),
        TestCase(
            id = "J-Positive-3",
            message = "INR 1,450.00 has been spent on your card XX1234 at RELIANCE RETAIL. Available credit limit INR 45,000.00.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 145000L,
            expectedMerchant = "RELIANCE RETAIL"
        ),
        TestCase(
            id = "J-Positive-4",
            message = "Refund of Rs 350.00 for your cancelled order at SWIGGY credited to A/c XX1234.",
            sender = "HDFCBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 35000L,
            expectedMerchant = "SWIGGY"
        ),
        TestCase(
            id = "J-Positive-5",
            message = "Reversal of Rs 500.00 for failed UPI transaction credited to A/c XX1234.",
            sender = "HDFCBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.REFUND,
            expectedAmountPaise = 50000L,
            expectedMerchant = null // generic fallback merchant
        ),
        TestCase(
            id = "J-Positive-6",
            message = "INR 350.00 debited from A/c no. XX1234 to SWIGGY on 24-10-26 via UPI. Avl Bal INR 12500.00. Not you? Call 18004195959",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 35000L,
            expectedMerchant = "SWIGGY"
        ),
        TestCase(
            id = "J-Positive-7",
            message = "Spent INR 450 using Axis Bank Credit Card no. XX1234 at STARBUCKS. Transaction successful. Avl limit: INR 40000.0.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 45000L,
            expectedMerchant = "STARBUCKS"
        ),
        TestCase(
            id = "J-Positive-8",
            message = "Spent INR 650 using Axis Bank Credit Card no. XX1234 on 24-10-26 14:00:00 IST at AMAZON. Avl limit: INR 38000.0. Pay now to clear dues.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 65000L,
            expectedMerchant = "AMAZON"
        ),
        TestCase(
            id = "J-Positive-9",
            message = "INR 120.00 debited from A/c no. XX1234 to CHAI POINT on 24-10-26. Bal INR 5000.00. Reminder: Keep your card details safe.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 12000L,
            expectedMerchant = "CHAI POINT"
        ),
        TestCase(
            id = "J-Positive-10",
            message = "INR 2000.00 debited from A/c no. XX1234 for ATM cash withdrawal on 24-10-26. Avl bal INR 8000.00. Beware of fraud! Never reveal sensitive data to strangers.",
            sender = "AXISBK",
            expectedResult = ExpectedResult.INGEST,
            expectedType = RecordType.EXPENSE,
            expectedAmountPaise = 200000L,
            expectedMerchant = null // ATM cash withdrawal
        )
    )

    @Test
    fun testCorpusSize() {
        assertEquals("Corpus must have exactly 24 test cases", 24, corpus.size)
    }

    @Test
    fun testAllCorpusEntries() {
        for (case in corpus) {
            val isNonTx = AutoFlowParserAdapter.isNonTransactionMessage(case.message)
            val entity = AutoFlowParserAdapter.parseAndAdapt(case.message, case.sender, baseTimestamp)

            when (case.expectedResult) {
                ExpectedResult.IGNORE -> {
                    val ignored = isNonTx || entity == null
                    assertTrue(
                        "Case ${case.id} should be ignored (isNonTx=$isNonTx, entity=$entity). Msg: ${case.message}",
                        ignored
                    )
                }
                ExpectedResult.INGEST -> {
                    assertFalse(
                        "Case ${case.id} should not be flagged as non-transaction. Msg: ${case.message}",
                        isNonTx
                    )
                    assertNotNull(
                        "Case ${case.id} must parse into an active transaction entity. Msg: ${case.message}",
                        entity
                    )
                    if (case.expectedType != null) {
                        assertEquals(
                            "Case ${case.id} recordType mismatch",
                            case.expectedType,
                            entity!!.recordType
                        )
                    }
                    if (case.expectedAmountPaise != null) {
                        assertEquals(
                            "Case ${case.id} amountPaise mismatch",
                            case.expectedAmountPaise,
                            entity!!.amountPaise
                        )
                    }
                    if (case.expectedMerchant != null) {
                        assertTrue(
                            "Case ${case.id} merchantName '${entity!!.merchantName}' should contain '${case.expectedMerchant}'",
                            entity.merchantName.contains(case.expectedMerchant, ignoreCase = true)
                        )
                    }
                }
            }
        }
    }
}
