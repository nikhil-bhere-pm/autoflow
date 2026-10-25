package com.autoflow.app.presentation.ui

import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.TransactionRepository
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.presentation.viewmodel.HomeBadgeType
import com.autoflow.app.presentation.viewmodel.MainViewModel
import com.autoflow.app.presentation.viewmodel.PeriodFilter
import com.autoflow.app.presentation.viewmodel.getHomeBadgeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class HomeScreenTest {

    private val testDispatcher = StandardTestDispatcher()
    private val zoneId = ZoneId.of("Asia/Kolkata")
    // Reference instant: 2026-10-24 15:30:00 IST
    private val fixedNow = LocalDateTime.of(2026, 10, 24, 15, 30, 0)
        .atZone(zoneId)
        .toInstant()

    private lateinit var fakeDao: FakeTransactionDao
    private lateinit var repository: TransactionRepository
    private lateinit var viewModel: MainViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeDao = FakeTransactionDao()
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

    private fun timeAt(year: Int, month: Int, day: Int, hour: Int = 12, min: Int = 0): Long {
        return LocalDateTime.of(year, month, day, hour, min).atZone(zoneId).toInstant().toEpochMilli()
    }

    @Test
    fun testPeriodSwitching() = runTest {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        advanceUntilIdle()
        // Default is THIS_MONTH
        assertEquals(PeriodFilter.Predefined(StatsPeriod.THIS_MONTH), viewModel.periodFilter.value)
        assertEquals("This month", viewModel.homeUiState.value.pillLabel)

        // Switch to TODAY
        viewModel.selectPeriod(StatsPeriod.TODAY)
        advanceUntilIdle()
        assertEquals(PeriodFilter.Predefined(StatsPeriod.TODAY), viewModel.periodFilter.value)
        assertEquals("Today", viewModel.homeUiState.value.pillLabel)

        // Switch to THIS_WEEK
        viewModel.selectPeriod(StatsPeriod.THIS_WEEK)
        advanceUntilIdle()
        assertEquals(PeriodFilter.Predefined(StatsPeriod.THIS_WEEK), viewModel.periodFilter.value)
        assertEquals("This week", viewModel.homeUiState.value.pillLabel)

        // Switch back to THIS_MONTH
        viewModel.selectPeriod(StatsPeriod.THIS_MONTH)
        advanceUntilIdle()
        assertEquals(PeriodFilter.Predefined(StatsPeriod.THIS_MONTH), viewModel.periodFilter.value)
        assertEquals("This month", viewModel.homeUiState.value.pillLabel)
    }

    @Test
    fun testPastMonthSelection() = runTest {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        // Insert transaction in Sep 2026 (past) and Oct 2026 (current)
        fakeDao.insert(
            createEntity(id = 1L, amountPaise = 50000L, dateTime = timeAt(2026, 9, 15))
        )
        fakeDao.insert(
            createEntity(id = 2L, amountPaise = 120000L, dateTime = timeAt(2026, 10, 10))
        )
        advanceUntilIdle()

        // Select September 2026
        val sep2026 = YearMonth.of(2026, 9)
        viewModel.selectMonth(sep2026)
        advanceUntilIdle()

        assertEquals(PeriodFilter.SpecificMonth(sep2026), viewModel.periodFilter.value)
        val state = viewModel.homeUiState.value
        assertEquals("Sep 2026", state.pillLabel)
        assertEquals(50000L, state.countedSpendPaise)
        assertEquals(1, state.countedTransactionCount)
        assertEquals(1, state.groupedTransactions.size)
        assertEquals(1, state.groupedTransactions[0].transactions.size)
        assertEquals(1L, state.groupedTransactions[0].transactions[0].id)
    }

    @Test
    fun testFutureMonthNonSelectable() = runTest {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        advanceUntilIdle()
        val initialFilter = viewModel.periodFilter.value
        assertEquals(PeriodFilter.Predefined(StatsPeriod.THIS_MONTH), initialFilter)

        // Attempt to select November 2026 (future relative to fixedNow: October 2026)
        val nov2026 = YearMonth.of(2026, 11)
        viewModel.selectMonth(nov2026)
        advanceUntilIdle()

        // Should NOT have changed to Nov 2026
        assertEquals(initialFilter, viewModel.periodFilter.value)
    }

    @Test
    fun testReturnToCurrentPeriod() = runTest {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        // First select past month Sep 2026
        val sep2026 = YearMonth.of(2026, 9)
        viewModel.selectMonth(sep2026)
        advanceUntilIdle()
        assertEquals(PeriodFilter.SpecificMonth(sep2026), viewModel.periodFilter.value)

        // Now select THIS_MONTH
        viewModel.selectPeriod(StatsPeriod.THIS_MONTH)
        advanceUntilIdle()
        assertEquals(PeriodFilter.Predefined(StatsPeriod.THIS_MONTH), viewModel.periodFilter.value)
        assertEquals("This month", viewModel.homeUiState.value.pillLabel)
    }

    @Test
    fun testEmptyState() = runTest {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        advanceUntilIdle()
        val state = viewModel.homeUiState.value
        assertTrue("Expected empty state when no transactions exist", state.isEmpty)
        assertEquals(0L, state.countedSpendPaise)
        assertEquals(0, state.countedTransactionCount)
        assertTrue(state.groupedTransactions.isEmpty())

        // Insert a transaction in this month
        fakeDao.insert(
            createEntity(id = 10L, amountPaise = 25000L, dateTime = timeAt(2026, 10, 20))
        )
        advanceUntilIdle()
        val updated = viewModel.homeUiState.value
        assertFalse("Expected non-empty state after transaction inserted", updated.isEmpty)
        assertEquals(25000L, updated.countedSpendPaise)
        assertEquals(1, updated.countedTransactionCount)
        assertEquals(1, updated.groupedTransactions.size)
    }

    @Test
    fun testBadgeRules() {
        val base = FinancialTransaction(
            id = 1L,
            amountPaise = 10000L,
            recordType = RecordType.EXPENSE,
            dateTime = fixedNow.toEpochMilli(),
            merchantName = "Test",
            category = "Food",
            categorySource = "AUTO",
            paymentMode = "UPI",
            note = null,
            accountNumber = null,
            bankName = null,
            reference = null,
            transactionHash = "h1",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyReasons = emptySet(),
            createdAt = fixedNow.toEpochMilli(),
            updatedAt = fixedNow.toEpochMilli(),
            isDeleted = false
        )

        // 1. REFUND
        val refundTx = base.copy(recordType = RecordType.REFUND)
        assertEquals(HomeBadgeType.REFUND, refundTx.getHomeBadgeType())

        // 2. EXCLUDED (Not counted)
        val excludedTx = base.copy(reviewStatus = ReviewStatus.EXCLUDED)
        assertEquals(HomeBadgeType.NOT_COUNTED, excludedTx.getHomeBadgeType())

        // 3. NEEDS_REVIEW (Check)
        val reviewTx = base.copy(reviewStatus = ReviewStatus.NEEDS_REVIEW)
        assertEquals(HomeBadgeType.CHECK, reviewTx.getHomeBadgeType())

        // 4. Manual Cash entry -> null (no mode badge)
        val cashTx1 = base.copy(source = TransactionSource.MANUAL_CASH, paymentMode = "CASH")
        assertNull(cashTx1.getHomeBadgeType())

        val cashTx2 = base.copy(source = TransactionSource.SMS_AUTO, paymentMode = "CASH")
        assertNull(cashTx2.getHomeBadgeType())

        // 5. Manual UPI entry -> null (no mode badge)
        val manualUpiTx = base.copy(source = TransactionSource.MANUAL_CASH, paymentMode = "UPI")
        assertNull(manualUpiTx.getHomeBadgeType())

        // 6. Standard Confirmed Bank/UPI Expense -> null (no badge)
        val normalTx = base.copy(
            recordType = RecordType.EXPENSE,
            reviewStatus = ReviewStatus.CONFIRMED,
            source = TransactionSource.SMS_AUTO,
            paymentMode = "UPI"
        )
        assertNull(normalTx.getHomeBadgeType())
    }

    @Test
    fun testDayGrouping() = runTest {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        // Day 1: 2026-10-24 with 2 transactions
        val tx1 = createEntity(id = 1L, amountPaise = 10000L, dateTime = timeAt(2026, 10, 24, 10, 0))
        val tx2 = createEntity(id = 2L, amountPaise = 20000L, dateTime = timeAt(2026, 10, 24, 14, 0))

        // Day 2: 2026-10-23 with 1 transaction
        val tx3 = createEntity(id = 3L, amountPaise = 30000L, dateTime = timeAt(2026, 10, 23, 9, 0))

        fakeDao.insertAll(listOf(tx1, tx2, tx3))
        advanceUntilIdle()

        val state = viewModel.homeUiState.value
        assertEquals(2, state.groupedTransactions.size)

        // Sorted by date descending
        assertEquals(LocalDate.of(2026, 10, 24), state.groupedTransactions[0].date)
        assertEquals(LocalDate.of(2026, 10, 23), state.groupedTransactions[1].date)

        // Transactions inside Day 1 sorted by dateTime descending
        val day1Txs = state.groupedTransactions[0].transactions
        assertEquals(2, day1Txs.size)
        assertEquals(2L, day1Txs[0].id) // 14:00
        assertEquals(1L, day1Txs[1].id) // 10:00
    }

    @Test
    fun testDayTotals() = runTest {
        backgroundScope.launch(kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)) {
            viewModel.homeUiState.collect()
        }
        // Day: 2026-10-24
        // Expense 1: 45000 paise (counted)
        // Expense 2: 32000 paise (counted)
        // Refund: 15000 paise (not counted as spending)
        // Excluded: 20000 paise (not counted as spending)
        val tx1 = createEntity(id = 1L, amountPaise = 45000L, dateTime = timeAt(2026, 10, 24, 10, 0))
        val tx2 = createEntity(id = 2L, amountPaise = 32000L, dateTime = timeAt(2026, 10, 24, 11, 0))
        val tx3 = createEntity(id = 3L, amountPaise = 15000L, dateTime = timeAt(2026, 10, 24, 12, 0), recordType = RecordType.REFUND)
        val tx4 = createEntity(id = 4L, amountPaise = 20000L, dateTime = timeAt(2026, 10, 24, 13, 0), reviewStatus = ReviewStatus.EXCLUDED)

        fakeDao.insertAll(listOf(tx1, tx2, tx3, tx4))
        advanceUntilIdle()

        val state = viewModel.homeUiState.value
        assertEquals(1, state.groupedTransactions.size)
        // Total counted for the day is 45000 + 32000 = 77000 paise
        assertEquals(77000L, state.groupedTransactions[0].dayTotalPaise)
        assertEquals(77000L, state.countedSpendPaise)
        assertEquals(2, state.countedTransactionCount) // 2 counted transactions
        assertEquals(4, state.groupedTransactions[0].transactions.size) // all 4 transactions shown in list
    }

    private fun createEntity(
        id: Long,
        amountPaise: Long,
        dateTime: Long,
        recordType: RecordType = RecordType.EXPENSE,
        reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
        source: TransactionSource = TransactionSource.SMS_AUTO,
        paymentMode: String = "UPI"
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            amountPaise = amountPaise,
            recordType = recordType,
            dateTime = dateTime,
            merchantName = "Merchant $id",
            category = "Food",
            categorySource = "AUTO",
            paymentMode = paymentMode,
            transactionHash = "hash_$id",
            source = source,
            reviewStatus = reviewStatus,
            uncertaintyBits = 0,
            createdAt = dateTime,
            updatedAt = dateTime,
            isDeleted = 0
        )
    }

    private class FakeTransactionDao : TransactionDao {
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

        override fun searchTransactions(
            textPattern: String,
            exactPaise: Long?,
            digitPattern: String?,
            limit: Int
        ): Flow<List<TransactionEntity>> = kotlinx.coroutines.flow.flowOf(emptyList())
    }
}
