package com.autoflow.app.presentation.ui

import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.TransactionRepository
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewScreenTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("Asia/Kolkata")
    private val fixedNow = LocalDateTime.of(2026, 10, 24, 15, 30, 0)
        .atZone(zoneId)
        .toInstant()

    private lateinit var fakeDao: FakeReviewTransactionDao
    private lateinit var repository: TransactionRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeReviewTransactionDao()
        repository = TransactionRepository(fakeDao)
        viewModel = MainViewModel(
            transactionRepository = repository,
            timeProvider = { fixedNow },
            zoneId = zoneId
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createEntity(
        id: Long = 1L,
        amountPaise: Long = 25000L,
        merchantName: String = "Test Merchant",
        category: String = "Others",
        reviewStatus: ReviewStatus = ReviewStatus.NEEDS_REVIEW,
        dateTime: Long = fixedNow.toEpochMilli()
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            amountPaise = amountPaise,
            recordType = RecordType.EXPENSE,
            dateTime = dateTime,
            merchantName = merchantName,
            category = category,
            categorySource = "AUTO",
            paymentMode = PaymentMode.UNKNOWN,
            transactionHash = "hash_$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyBits = 1,
            createdAt = dateTime,
            updatedAt = dateTime,
            isDeleted = 0
        )
    }

    @Test
    fun testChoosingCategoryClearsFlagAndUpdatesCount() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.unreviewedCount.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.reviewTransactions.collect()
        }

        val txId = fakeDao.insert(createEntity(id = 1L, reviewStatus = ReviewStatus.NEEDS_REVIEW))
        advanceUntilIdle()

        assertEquals(1, viewModel.unreviewedCount.value)
        assertEquals(1, viewModel.reviewTransactions.value.size)

        // User chooses category "Food"
        viewModel.confirmCategory(txId, "Food")
        advanceUntilIdle()

        // Verify flag cleared and category updated in DB
        val updated = fakeDao.getById(txId)
        assertNotNull(updated)
        assertEquals(ReviewStatus.CONFIRMED, updated!!.reviewStatus)
        assertEquals(0, updated.uncertaintyBits)
        assertEquals("Food", updated.category)

        // Verify count and list updated
        assertEquals(0, viewModel.unreviewedCount.value)
        assertEquals(0, viewModel.reviewTransactions.value.size)
    }

    @Test
    fun testLaterKeepsItemAndCount() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.unreviewedCount.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.reviewTransactions.collect()
        }

        val txId = fakeDao.insert(createEntity(id = 2L, reviewStatus = ReviewStatus.NEEDS_REVIEW))
        advanceUntilIdle()

        assertEquals(1, viewModel.unreviewedCount.value)
        assertEquals(1, viewModel.reviewTransactions.value.size)

        // User taps "Later"
        viewModel.onLater(txId)
        advanceUntilIdle()

        // Verify item is NOT deleted and stays NEEDS_REVIEW
        val inDb = fakeDao.getById(txId)
        assertNotNull(inDb)
        assertEquals(ReviewStatus.NEEDS_REVIEW, inDb!!.reviewStatus)
        assertEquals(0, inDb.isDeleted)

        // Verify count is preserved
        assertEquals(1, viewModel.unreviewedCount.value)
        assertEquals(1, viewModel.reviewTransactions.value.size)
    }

    @Test
    fun testCountZeroBannerHidden() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.unreviewedCount.collect()
        }
        advanceUntilIdle()

        // 1. Initially 0 unreviewed -> banner is hidden
        assertEquals(0, viewModel.unreviewedCount.value)
        assertFalse(viewModel.homeUiState.value.showReviewBanner)

        // 2. Add unreviewed item -> banner is shown with count
        fakeDao.insert(createEntity(id = 3L, reviewStatus = ReviewStatus.NEEDS_REVIEW))
        advanceUntilIdle()

        assertEquals(1, viewModel.unreviewedCount.value)
        assertTrue(viewModel.homeUiState.value.showReviewBanner)
        assertEquals(1, viewModel.homeUiState.value.unreviewedCount)

        // 3. Confirm category -> count returns to 0 -> banner is hidden
        viewModel.confirmCategory(3L, "Groceries")
        advanceUntilIdle()

        assertEquals(0, viewModel.unreviewedCount.value)
        assertFalse(viewModel.homeUiState.value.showReviewBanner)
    }

    @Test
    fun testPendingItemsCountedInHomeTotals() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        advanceUntilIdle()

        // Insert pending review expense (15000 paise = ₹150.00)
        fakeDao.insert(
            createEntity(
                id = 4L,
                amountPaise = 15000L,
                reviewStatus = ReviewStatus.NEEDS_REVIEW,
                dateTime = fixedNow.toEpochMilli()
            )
        )
        advanceUntilIdle()

        val state = viewModel.homeUiState.value
        // Pending review transactions MUST be counted in home spending totals
        assertEquals(15000L, state.countedSpendPaise)
        assertEquals(1, state.countedTransactionCount)
    }

    private class FakeReviewTransactionDao : TransactionDao {
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
        override suspend fun findDuplicateCandidate(reference: String, recordType: RecordType, amountPaise: Long, minDateTime: Long, maxDateTime: Long): TransactionEntity? = null
        override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> = emptyList()
        override suspend fun findExpensesByMerchant(merchantName: String, minDateTime: Long, maxDateTime: Long): List<TransactionEntity> = emptyList()

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

        override suspend fun undelete(id: Long, updatedAt: Long) {
            val idx = items.indexOfFirst { it.id == id }
            if (idx != -1) {
                items[idx] = items[idx].copy(isDeleted = 0, updatedAt = updatedAt)
                emit()
            }
        }

        override suspend fun getAllAutoCategorizedTransactions(): List<TransactionEntity> {
            return items.filter { it.isDeleted == 0 && it.categorySource == "AUTO" }
        }

        override fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> {
            return stateFlow.map { list ->
                list.filter { it.isDeleted == 0 && it.dateTime >= startTime && it.dateTime < endTime }
                    .sortedByDescending { it.dateTime }
            }
        }
    }
}
