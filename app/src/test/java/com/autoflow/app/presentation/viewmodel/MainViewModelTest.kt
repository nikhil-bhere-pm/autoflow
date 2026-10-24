package com.autoflow.app.presentation.viewmodel

import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var inMemoryDao: InMemoryTransactionDao
    private lateinit var repository: TransactionRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        inMemoryDao = InMemoryTransactionDao()
        repository = TransactionRepository(inMemoryDao)
        viewModel = MainViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testAddCashExpense_savesAsManualCashAndConfirmed() = runTest {
        val now = System.currentTimeMillis()
        var completed = false

        viewModel.addCashExpense(
            amountPaise = 13000L,
            merchantName = "Tea Corner",
            category = "Food",
            dateTime = now,
            onComplete = { completed = true }
        )
        advanceUntilIdle()

        assertTrue(completed)
        val transactions = inMemoryDao.items
        assertEquals(1, transactions.size)

        val saved = transactions[0]
        assertEquals(13000L, saved.amountPaise)
        assertEquals("Tea Corner", saved.merchantName)
        assertEquals("Food", saved.category)
        assertEquals(TransactionSource.MANUAL_CASH, saved.source)
        assertEquals(ReviewStatus.CONFIRMED, saved.reviewStatus)
        assertEquals(RecordType.EXPENSE, saved.recordType)
        assertEquals(0, saved.isDeleted)
        assertTrue(saved.transactionHash.startsWith("cash_"))
    }

    @Test
    fun testEditTransaction_updatesExistingRowAndSetsConfirmedWithoutNewRow() = runTest {
        // Pre-insert an expense needing review (e.g. from SMS)
        val initialId = inMemoryDao.insert(
            TransactionEntity(
                id = 1L,
                amountPaise = 25000L,
                recordType = RecordType.EXPENSE,
                dateTime = System.currentTimeMillis(),
                merchantName = "Unknown Merchant",
                category = "Others",
                transactionHash = "sms_hash_1",
                source = TransactionSource.SMS_AUTO,
                reviewStatus = ReviewStatus.NEEDS_REVIEW,
                uncertaintyBits = 1
            )
        )
        advanceUntilIdle()
        assertEquals(1, inMemoryDao.items.size)

        var completed = false
        viewModel.editTransaction(
            id = initialId,
            newMerchantName = "Swiggy Instamart",
            newCategory = "Groceries",
            onComplete = { completed = true }
        )
        advanceUntilIdle()

        assertTrue(completed)
        // Must update existing row, no new row created
        assertEquals(1, inMemoryDao.items.size)

        val updated = inMemoryDao.getById(initialId)
        assertNotNull(updated)
        assertEquals("Swiggy Instamart", updated?.merchantName)
        assertEquals("Groceries", updated?.category)
        assertEquals(ReviewStatus.CONFIRMED, updated?.reviewStatus)
        assertEquals(0, updated?.uncertaintyBits)
    }

    @Test
    fun testMarkNotAnExpense_setsStatusToExcluded() = runTest {
        val initialId = inMemoryDao.insert(
            TransactionEntity(
                id = 10L,
                amountPaise = 50000L,
                recordType = RecordType.EXPENSE,
                dateTime = System.currentTimeMillis(),
                merchantName = "ATM Transfer",
                category = "Others",
                transactionHash = "sms_hash_10",
                source = TransactionSource.SMS_AUTO,
                reviewStatus = ReviewStatus.NEEDS_REVIEW
            )
        )
        advanceUntilIdle()

        var completed = false
        viewModel.markNotAnExpense(
            id = initialId,
            onComplete = { completed = true }
        )
        advanceUntilIdle()

        assertTrue(completed)
        assertEquals(1, inMemoryDao.items.size)

        val updated = inMemoryDao.getById(initialId)
        assertEquals(ReviewStatus.EXCLUDED, updated?.reviewStatus)
    }

    @Test
    fun testDeleteTransaction_performsSoftDelete() = runTest {
        val initialId = inMemoryDao.insert(
            TransactionEntity(
                id = 20L,
                amountPaise = 7500L,
                recordType = RecordType.EXPENSE,
                dateTime = System.currentTimeMillis(),
                merchantName = "Accidental Tap",
                category = "Others",
                transactionHash = "cash_accidental",
                source = TransactionSource.MANUAL_CASH,
                reviewStatus = ReviewStatus.CONFIRMED,
                isDeleted = 0
            )
        )
        advanceUntilIdle()

        var completed = false
        viewModel.deleteTransaction(
            id = initialId,
            onComplete = { completed = true }
        )
        advanceUntilIdle()

        assertTrue(completed)
        val entity = inMemoryDao.items.find { it.id == initialId }
        assertNotNull(entity)
        assertEquals(1, entity?.isDeleted)

        // Active transactions flow filters out soft-deleted items
        val activeTransactions = inMemoryDao.getAllActiveTransactions().first()
        assertTrue(activeTransactions.none { it.id == initialId })
    }

    @Test
    fun testEditTransaction_blankMerchantPreservesUnknownMerchant() = runTest {
        val initialId = inMemoryDao.insert(
            TransactionEntity(
                id = 30L,
                amountPaise = 15000L,
                recordType = RecordType.EXPENSE,
                dateTime = System.currentTimeMillis(),
                merchantName = "Unknown Merchant",
                category = "Others",
                transactionHash = "sms_hash_unknown",
                source = TransactionSource.SMS_AUTO,
                reviewStatus = ReviewStatus.NEEDS_REVIEW,
                uncertaintyBits = 1
            )
        )
        advanceUntilIdle()

        var completed = false
        // User saves with the merchant field left empty
        viewModel.editTransaction(
            id = initialId,
            newMerchantName = "   ",
            newCategory = "Food",
            onComplete = { completed = true }
        )
        advanceUntilIdle()

        assertTrue(completed)
        val updated = inMemoryDao.getById(initialId)
        assertNotNull(updated)
        // Must preserve "Unknown Merchant", category updated to "Food", reviewStatus CONFIRMED
        assertEquals("Unknown Merchant", updated?.merchantName)
        assertEquals("Food", updated?.category)
        assertEquals(ReviewStatus.CONFIRMED, updated?.reviewStatus)
        assertEquals(0, updated?.uncertaintyBits)
    }

    @Test
    fun testExcludedTransaction_visibleInActiveTransactions_andCanBeRestored() = runTest {
        val excludedId = inMemoryDao.insert(
            TransactionEntity(
                id = 40L,
                amountPaise = 9900L,
                recordType = RecordType.EXPENSE,
                dateTime = System.currentTimeMillis() - 1000,
                merchantName = "Excluded Expense",
                category = "Others",
                transactionHash = "sms_hash_excluded",
                source = TransactionSource.SMS_AUTO,
                reviewStatus = ReviewStatus.EXCLUDED,
                isDeleted = 0
            )
        )
        val deletedId = inMemoryDao.insert(
            TransactionEntity(
                id = 41L,
                amountPaise = 5000L,
                recordType = RecordType.EXPENSE,
                dateTime = System.currentTimeMillis(),
                merchantName = "Deleted Expense",
                category = "Others",
                transactionHash = "sms_hash_deleted",
                source = TransactionSource.SMS_AUTO,
                reviewStatus = ReviewStatus.CONFIRMED,
                isDeleted = 1
            )
        )
        advanceUntilIdle()

        // 1. Check visibility in active transactions
        val activeTransactions = inMemoryDao.getAllActiveTransactions().first()
        assertTrue("EXCLUDED transaction must remain visible in active list", activeTransactions.any { it.id == excludedId })
        assertTrue("Soft-deleted transaction must remain hidden", activeTransactions.none { it.id == deletedId })

        // 2. Restore transaction back to CONFIRMED
        var restoreCompleted = false
        viewModel.restoreTransaction(
            id = excludedId,
            onComplete = { restoreCompleted = true }
        )
        advanceUntilIdle()

        assertTrue(restoreCompleted)
        val restored = inMemoryDao.getById(excludedId)
        assertEquals(ReviewStatus.CONFIRMED, restored?.reviewStatus)
        assertEquals(0, restored?.uncertaintyBits)
    }

    @Test
    fun testAddCashExpense_withCustomDateTime() = runTest {
        val pastTimestamp = 1690000000000L // Specific past date/time
        var completed = false

        viewModel.addCashExpense(
            amountPaise = 45000L,
            merchantName = "Grocery Mart",
            category = "Groceries",
            dateTime = pastTimestamp,
            onComplete = { completed = true }
        )
        advanceUntilIdle()

        assertTrue(completed)
        val entity = inMemoryDao.items.find { it.amountPaise == 45000L }
        assertNotNull(entity)
        assertEquals(pastTimestamp, entity?.dateTime)
    }

    private class InMemoryTransactionDao : TransactionDao {
        val items = mutableListOf<TransactionEntity>()
        private val stateFlow = MutableStateFlow<List<TransactionEntity>>(emptyList())
        private var nextId = 1L

        private fun emit() {
            stateFlow.value = items.toList()
        }

        override suspend fun insert(transaction: TransactionEntity): Long {
            val id = if (transaction.id == 0L) nextId++ else transaction.id
            val copy = transaction.copy(id = id)
            items.add(copy)
            emit()
            return id
        }

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> {
            return transactions.map { insert(it) }
        }

        override suspend fun update(transaction: TransactionEntity) {
            val idx = items.indexOfFirst { it.id == transaction.id }
            if (idx != -1) {
                items[idx] = transaction
                emit()
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
        ): TransactionEntity? = null

        override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> = emptyList()

        override suspend fun findExpensesByMerchant(
            merchantName: String,
            minDateTime: Long,
            maxDateTime: Long
        ): List<TransactionEntity> = emptyList()

        override fun getAllActiveTransactions(): Flow<List<TransactionEntity>> {
            return stateFlow.map { list ->
                list.filter { it.isDeleted == 0 }.sortedByDescending { it.dateTime }
            }
        }

        override fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> {
            return stateFlow.map { list ->
                list.filter { it.dateTime in startTime..endTime && it.isDeleted == 0 }
            }
        }

        override fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> {
            return stateFlow.map { list ->
                list.filter { it.reviewStatus == status && it.isDeleted == 0 }
            }
        }

        override fun getUnreviewedCount(): Flow<Int> {
            return stateFlow.map { list ->
                list.count { it.reviewStatus == ReviewStatus.NEEDS_REVIEW && it.isDeleted == 0 }
            }
        }

        override suspend fun softDelete(id: Long, updatedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx != -1) {
                items[idx] = items[idx].copy(isDeleted = 1, updatedAt = updatedAt)
                emit()
            }
        }
    }
}
