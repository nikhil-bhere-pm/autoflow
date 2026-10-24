package com.autoflow.app.ingestion

import com.autoflow.app.data.db.dao.RefundMatchDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.MatchStatus
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.model.UncertaintyReason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DefaultIngestionPipelineTest {

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var refundMatchDao: FakeRefundMatchDao
    private lateinit var pipeline: DefaultIngestionPipeline

    @Before
    fun setUp() {
        transactionDao = FakeTransactionDao()
        refundMatchDao = FakeRefundMatchDao()
        pipeline = DefaultIngestionPipeline(
            transactionDao = transactionDao,
            refundMatchDao = refundMatchDao,
            runInTransaction = { block -> block() }
        )
    }

    @Test
    fun testRepeatedSms_sameHash_ignoredAsDuplicate() = runTest {
        val tx = createExpense(
            hash = "HASH_COFFEE_1",
            amountPaise = 25000L, // ₹250.00
            merchant = "Starbucks",
            reference = "UPI111"
        )

        val firstResult = pipeline.ingest(tx)
        assertTrue(firstResult is IngestionResult.Success)
        assertEquals(1, transactionDao.items.size)

        // Carrier re-delivers the exact same SMS
        val secondResult = pipeline.ingest(tx)
        assertTrue(secondResult is IngestionResult.DuplicateIgnored)
        assertEquals("HASH_COFFEE_1", (secondResult as IngestionResult.DuplicateIgnored).existingHash)
        assertEquals(1, transactionDao.items.size)
    }

    @Test
    fun testLegitimateIdenticalPayments_distinctHashesNoMatchingRef_bothInserted() = runTest {
        // User pays ₹150 for coffee, then 5 minutes later pays another ₹150 at the same cafe.
        // SMS timestamps/balances differ, producing different hashes.
        val tx1 = createExpense(
            hash = "HASH_CAFE_A",
            amountPaise = 15000L,
            merchant = "Blue Tokai",
            reference = null,
            dateTime = 1700000000000L
        )
        val tx2 = createExpense(
            hash = "HASH_CAFE_B",
            amountPaise = 15000L,
            merchant = "Blue Tokai",
            reference = null,
            dateTime = 1700000300000L // 5 mins later
        )

        val res1 = pipeline.ingest(tx1)
        val res2 = pipeline.ingest(tx2)

        assertTrue(res1 is IngestionResult.Success)
        assertTrue(res2 is IngestionResult.Success)
        // INVARIANT: AutoFlow NEVER merges solely on (merchant, amount, approximate time)
        assertEquals(2, transactionDao.items.size)
    }

    @Test
    fun testReferenceBasedDuplicate_differentHashSameReference_ignoredAsDuplicate() = runTest {
        val tx1 = createExpense(
            hash = "HASH_ORIGINAL",
            amountPaise = 50000L, // ₹500.00
            merchant = "Amazon",
            reference = "UPI987654321012",
            dateTime = 1700000000000L
        )
        val res1 = pipeline.ingest(tx1)
        assertTrue(res1 is IngestionResult.Success)

        // Follow-up SMS with slight phrasing variation (different hash), but identical bank reference & amount
        val tx2 = createExpense(
            hash = "HASH_FOLLOWUP_VARIANT",
            amountPaise = 50000L,
            merchant = "Amazon India",
            reference = "UPI987654321012",
            dateTime = 1700000010000L
        )
        val res2 = pipeline.ingest(tx2)
        assertTrue(res2 is IngestionResult.DuplicateIgnored)
        assertEquals("HASH_ORIGINAL", (res2 as IngestionResult.DuplicateIgnored).existingHash)
        assertEquals(1, transactionDao.items.size)
    }

    @Test
    fun testReferenceMatch_differentRecordType_notTreatedAsDuplicate() = runTest {
        // A refund quoting the original expense reference must NOT be treated as a duplicate expense!
        val expense = createExpense(
            hash = "HASH_EXPENSE",
            amountPaise = 50000L,
            merchant = "Flipkart",
            reference = "FLIP123"
        )
        pipeline.ingest(expense)

        val refund = createRefund(
            hash = "HASH_REFUND",
            amountPaise = 50000L,
            merchant = "Flipkart",
            reference = "FLIP123"
        )
        val refundResult = pipeline.ingest(refund)

        assertTrue(refundResult is IngestionResult.Success)
        assertEquals(2, transactionDao.items.size)
    }

    @Test
    fun testSoftDeletedTransaction_byHash_cannotBeReimported() = runTest {
        val tx = createExpense(
            hash = "HASH_TO_DELETE",
            amountPaise = 30000L,
            merchant = "Zomato",
            reference = "ZOM1"
        )
        val res = pipeline.ingest(tx)
        val txId = (res as IngestionResult.Success).transactionId

        // User deletes the transaction
        transactionDao.softDelete(txId)
        val deleted = transactionDao.getById(txId)
        assertNotNull(deleted)
        assertEquals(1, deleted!!.isDeleted)

        // Rescan attempts to re-import the deleted transaction
        val rescanResult = pipeline.ingest(tx)
        assertTrue(rescanResult is IngestionResult.SoftDeletedIgnored)
        assertEquals("HASH_TO_DELETE", (rescanResult as IngestionResult.SoftDeletedIgnored).existingHash)
        // Ensure it was NOT resurrected
        assertEquals(1, transactionDao.getById(txId)!!.isDeleted)
    }

    @Test
    fun testSoftDeletedTransaction_byReference_cannotBeReimported() = runTest {
        val tx1 = createExpense(
            hash = "HASH_REF_DEL_1",
            amountPaise = 45000L,
            merchant = "Swiggy",
            reference = "SWIGGY_RRN_999"
        )
        val res = pipeline.ingest(tx1)
        val txId = (res as IngestionResult.Success).transactionId

        // User soft-deletes this transaction
        transactionDao.softDelete(txId)

        // Later rescan arrives with slightly different hash, but identical bank reference
        val tx2 = createExpense(
            hash = "HASH_REF_DEL_2",
            amountPaise = 45000L,
            merchant = "Swiggy Delivery",
            reference = "SWIGGY_RRN_999"
        )
        val rescanResult = pipeline.ingest(tx2)

        assertTrue(
            "Soft-deleted transaction must not be re-imported via matching bank reference",
            rescanResult is IngestionResult.SoftDeletedIgnored
        )
        assertEquals(1, transactionDao.items.size)
        assertEquals(1, transactionDao.items.first().isDeleted)
    }

    @Test
    fun testPreserveUserEditsAndExclusions_duringRescan() = runTest {
        val tx = createExpense(
            hash = "HASH_USER_EDIT",
            amountPaise = 10000L,
            merchant = "POS Store",
            reference = null
        ).copy(reviewStatus = ReviewStatus.NEEDS_REVIEW, category = "Others")

        pipeline.ingest(tx)
        val original = transactionDao.getByHash("HASH_USER_EDIT")!!

        // User edits the transaction: categorizes to "Groceries" and confirms it
        val edited = original.copy(
            category = "Groceries",
            merchantName = "Local Kirana",
            reviewStatus = ReviewStatus.CONFIRMED
        )
        transactionDao.update(edited)

        // Rescan arrives with original parser data
        val rescanResult = pipeline.ingest(tx)
        assertTrue(rescanResult is IngestionResult.DuplicateIgnored)

        val preserved = transactionDao.getByHash("HASH_USER_EDIT")!!
        assertEquals("Groceries", preserved.category)
        assertEquals("Local Kirana", preserved.merchantName)
        assertEquals(ReviewStatus.CONFIRMED, preserved.reviewStatus)

        // Test exclusion preservation:
        val excludedTx = createExpense(
            hash = "HASH_EXCLUDED",
            amountPaise = 5000L,
            merchant = "ATM"
        )
        pipeline.ingest(excludedTx)
        val loadedExcluded = transactionDao.getByHash("HASH_EXCLUDED")!!
        transactionDao.update(loadedExcluded.copy(reviewStatus = ReviewStatus.EXCLUDED))

        // Rescan arrives:
        val rescanExcluded = pipeline.ingest(excludedTx)
        assertTrue(rescanExcluded is IngestionResult.DuplicateIgnored)
        assertEquals(ReviewStatus.EXCLUDED, transactionDao.getByHash("HASH_EXCLUDED")!!.reviewStatus)
    }

    @Test
    fun testRefundAllocationCap_singleRefundExceedsExpense_cappedAtRemaining() = runTest {
        val expense = createExpense(
            hash = "HASH_EXP_500",
            amountPaise = 50000L, // ₹500.00
            merchant = "Myntra",
            reference = "MYN_REF_1"
        )
        pipeline.ingest(expense)

        // Refund arrives for ₹700.00 (70,000 paise) citing the same reference
        val refund = createRefund(
            hash = "HASH_REF_700",
            amountPaise = 70000L, // ₹700.00
            merchant = "Myntra",
            reference = "MYN_REF_1"
        )
        val result = pipeline.ingest(refund)
        assertTrue(result is IngestionResult.Success)

        val expenseId = transactionDao.getByHash("HASH_EXP_500")!!.id
        val totalAllocated = refundMatchDao.getTotalAllocatedPaiseForExpense(expenseId)

        // INVARIANT: Allocated refund MUST NOT exceed original expense amount
        assertEquals(50000L, totalAllocated)

        val refundTxId = (result as IngestionResult.Success).transactionId
        val refundMatches = refundMatchDao.getMatchesListForRefund(refundTxId)
        assertEquals(1, refundMatches.size)
        assertEquals(50000L, refundMatches.first().allocatedAmountPaise)

        // The refund is not fully allocated, so it should retain NEEDS_REVIEW
        val persistedRefund = transactionDao.getById(refundTxId)!!
        assertEquals(ReviewStatus.NEEDS_REVIEW, persistedRefund.reviewStatus)
    }

    @Test
    fun testRefundAllocationCap_multiplePartialRefunds_cappedAtTotal() = runTest {
        val expense = createExpense(
            hash = "HASH_EXP_1000",
            amountPaise = 100000L, // ₹1,000.00
            merchant = "MakeMyTrip",
            reference = "MMT_BOOKING_1"
        )
        pipeline.ingest(expense)
        val expenseId = transactionDao.getByHash("HASH_EXP_1000")!!.id

        // Refund 1: ₹600.00
        val refund1 = createRefund(
            hash = "HASH_REF_P1",
            amountPaise = 60000L,
            merchant = "MakeMyTrip",
            reference = "MMT_BOOKING_1"
        )
        pipeline.ingest(refund1)
        assertEquals(60000L, refundMatchDao.getTotalAllocatedPaiseForExpense(expenseId))

        // Refund 2: ₹400.00 (Completes the ₹1,000.00)
        val refund2 = createRefund(
            hash = "HASH_REF_P2",
            amountPaise = 40000L,
            merchant = "MakeMyTrip",
            reference = "MMT_BOOKING_1"
        )
        pipeline.ingest(refund2)
        assertEquals(100000L, refundMatchDao.getTotalAllocatedPaiseForExpense(expenseId))

        // Refund 3: ₹200.00 (Attempted excess refund)
        val refund3 = createRefund(
            hash = "HASH_REF_P3",
            amountPaise = 20000L,
            merchant = "MakeMyTrip",
            reference = "MMT_BOOKING_1"
        )
        val res3 = pipeline.ingest(refund3)
        assertTrue(res3 is IngestionResult.Success)

        // Total allocated to expense remains strictly capped at 100,000 paise (₹1,000.00)
        val finalAllocated = refundMatchDao.getTotalAllocatedPaiseForExpense(expenseId)
        assertEquals(100000L, finalAllocated)

        // Third refund has 0 allocated
        val ref3Id = (res3 as IngestionResult.Success).transactionId
        val ref3Matches = refundMatchDao.getMatchesListForRefund(ref3Id)
        assertEquals(0, ref3Matches.size)
    }

    @Test
    fun testPendingReviewExpenses_countImmediatelyInLedger() = runTest {
        // Unknown merchant creates uncertainty bit and NEEDS_REVIEW status
        val ambiguousExpense = createExpense(
            hash = "HASH_AMBIGUOUS",
            amountPaise = 42000L, // ₹420.00
            merchant = "Unknown Merchant",
            reference = null
        ).copy(
            reviewStatus = ReviewStatus.NEEDS_REVIEW,
            uncertaintyBits = UncertaintyReason.UNKNOWN_MERCHANT.bit
        )

        val result = pipeline.ingest(ambiguousExpense)
        assertTrue(result is IngestionResult.Success)

        // INVARIANT: Needs-review expense is immediately in the active ledger
        val periodTransactions = transactionDao.getTransactionsForPeriod(0L, System.currentTimeMillis() + 10000L).first()
        assertEquals(1, periodTransactions.size)
        assertEquals(42000L, periodTransactions.first().amountPaise)
        assertEquals(ReviewStatus.NEEDS_REVIEW, periodTransactions.first().reviewStatus)
        assertEquals(1, transactionDao.getUnreviewedCount().first())
    }

    @Test
    fun testRefund_neverAutoMatchesSolelyOnMerchantIfExpenseIsAmbiguous() = runTest {
        // Expense is ambiguous (NEEDS_REVIEW with uncertainty flag), has NO bank reference
        val ambiguousExpense = createExpense(
            hash = "HASH_AMBIGUOUS_EXP",
            amountPaise = 20000L,
            merchant = "Ambiguous Store",
            reference = null
        ).copy(
            reviewStatus = ReviewStatus.NEEDS_REVIEW,
            uncertaintyBits = UncertaintyReason.UNKNOWN_MERCHANT.bit
        )
        pipeline.ingest(ambiguousExpense)
        val expenseId = transactionDao.getByHash("HASH_AMBIGUOUS_EXP")!!.id

        // Refund arrives matching only the merchant name, without matching reference
        val refund = createRefund(
            hash = "HASH_AMBIGUOUS_REF",
            amountPaise = 20000L,
            merchant = "Ambiguous Store",
            reference = null
        )
        val result = pipeline.ingest(refund)
        assertTrue(result is IngestionResult.Success)

        // FINANCIAL CORRECTNESS: Never auto-match a refund solely on merchant name when original expense is ambiguous
        val allocated = refundMatchDao.getTotalAllocatedPaiseForExpense(expenseId)
        assertEquals("Ambiguous expense must not be auto-matched solely by merchant name", 0L, allocated)
    }

    private fun createExpense(
        hash: String,
        amountPaise: Long,
        merchant: String,
        reference: String? = null,
        dateTime: Long = 1700000000000L
    ) = TransactionEntity(
        amountPaise = amountPaise,
        recordType = RecordType.EXPENSE,
        dateTime = dateTime,
        merchantName = merchant,
        category = "Food",
        reference = reference,
        transactionHash = hash,
        source = TransactionSource.SMS_AUTO,
        reviewStatus = ReviewStatus.CONFIRMED
    )

    private fun createRefund(
        hash: String,
        amountPaise: Long,
        merchant: String,
        reference: String? = null,
        dateTime: Long = 1700000050000L
    ) = TransactionEntity(
        amountPaise = amountPaise,
        recordType = RecordType.REFUND,
        dateTime = dateTime,
        merchantName = merchant,
        category = "Others",
        reference = reference,
        transactionHash = hash,
        source = TransactionSource.SMS_AUTO,
        reviewStatus = ReviewStatus.NEEDS_REVIEW,
        uncertaintyBits = UncertaintyReason.UNMATCHED_REFUND.bit
    )
}

// In-Memory Test Fakes for Room DAOs

class FakeTransactionDao : TransactionDao {
    val items = mutableListOf<TransactionEntity>()
    private var nextId = 1L

    override suspend fun insert(transaction: TransactionEntity): Long {
        val id = if (transaction.id == 0L) nextId++ else transaction.id
        val copy = transaction.copy(id = id)
        items.add(copy)
        return id
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> {
        return transactions.map { insert(it) }
    }

    override suspend fun update(transaction: TransactionEntity) {
        val idx = items.indexOfFirst { it.id == transaction.id }
        if (idx != -1) {
            items[idx] = transaction
        }
    }

    override suspend fun getById(id: Long): TransactionEntity? = items.find { it.id == id }

    override suspend fun getByHash(hash: String): TransactionEntity? = items.find { it.transactionHash == hash }

    override suspend fun findDuplicateCandidate(
        reference: String,
        recordType: RecordType,
        amountPaise: Long,
        minDateTime: Long,
        maxDateTime: Long
    ): TransactionEntity? {
        return items
            .filter {
                it.reference == reference &&
                it.recordType == recordType &&
                it.amountPaise == amountPaise &&
                it.dateTime in minDateTime..maxDateTime
            }
            .sortedWith(compareByDescending<TransactionEntity> { it.isDeleted }.thenByDescending { it.id })
            .firstOrNull()
    }

    override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> {
        return items
            .filter { it.recordType == RecordType.EXPENSE && it.isDeleted == 0 && it.reference == reference }
            .sortedByDescending { it.dateTime }
    }

    override suspend fun findExpensesByMerchant(
        merchantName: String,
        minDateTime: Long,
        maxDateTime: Long
    ): List<TransactionEntity> {
        return items
            .filter {
                it.recordType == RecordType.EXPENSE &&
                it.isDeleted == 0 &&
                it.merchantName.equals(merchantName, ignoreCase = true) &&
                it.dateTime in minDateTime..maxDateTime
            }
            .sortedByDescending { it.dateTime }
    }

    override fun getAllActiveTransactions(): Flow<List<TransactionEntity>> {
        return flowOf(items.filter { it.isDeleted == 0 }.sortedByDescending { it.dateTime })
    }

    override fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> {
        return flowOf(items.filter { it.dateTime in startTime..endTime && it.isDeleted == 0 })
    }

    override fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> {
        return flowOf(items.filter { it.reviewStatus == status && it.isDeleted == 0 })
    }

    override fun getUnreviewedCount(): Flow<Int> {
        return flowOf(items.count { it.reviewStatus == ReviewStatus.NEEDS_REVIEW && it.isDeleted == 0 })
    }

    override suspend fun softDelete(id: Long, updatedAt: Long) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx != -1) {
            items[idx] = items[idx].copy(isDeleted = 1, updatedAt = updatedAt)
        }
    }
}

class FakeRefundMatchDao : RefundMatchDao {
    val matches = mutableListOf<RefundMatchEntity>()
    private var nextId = 1L

    override suspend fun insert(refundMatch: RefundMatchEntity): Long {
        val id = if (refundMatch.id == 0L) nextId++ else refundMatch.id
        val copy = refundMatch.copy(id = id)
        matches.add(copy)
        return id
    }

    override fun getMatchesForExpense(expenseId: Long): Flow<List<RefundMatchEntity>> {
        return flowOf(matches.filter { it.originalExpenseId == expenseId })
    }

    override fun getMatchesForRefund(refundTxId: Long): Flow<List<RefundMatchEntity>> {
        return flowOf(matches.filter { it.refundTransactionId == refundTxId })
    }

    override suspend fun getMatchesListForRefund(refundTxId: Long): List<RefundMatchEntity> {
        return matches.filter { it.refundTransactionId == refundTxId }
    }

    override suspend fun getTotalAllocatedPaiseForExpense(expenseId: Long): Long {
        return matches.filter { it.originalExpenseId == expenseId }.sumOf { it.allocatedAmountPaise }
    }

    override suspend fun getTotalAllocatedPaiseForRefund(refundTxId: Long): Long {
        return matches.filter { it.refundTransactionId == refundTxId }.sumOf { it.allocatedAmountPaise }
    }

    override suspend fun deleteById(id: Long) {
        matches.removeAll { it.id == id }
    }
}
