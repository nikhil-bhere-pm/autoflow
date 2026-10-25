package com.autoflow.app.notification

import com.autoflow.app.data.db.dao.CustomCategoryDao
import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.CustomCategoryEntity
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.CategoryRepository
import com.autoflow.app.data.repository.TransactionRepository
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.isCountedAsSpending
import com.autoflow.app.domain.totals.calculateSpendingTotals
import com.autoflow.app.presentation.ui.form.EditTransactionFormState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class CategoryPopupAndNotCountedRowsTest {

    private lateinit var fakeTxDao: FakeTxDao
    private lateinit var fakeMerchantDao: FakeMerchantDao
    private lateinit var fakeCategoryDao: FakeCustomCategoryDao
    private lateinit var txRepository: TransactionRepository
    private lateinit var catRepository: CategoryRepository

    @Before
    fun setUp() {
        fakeTxDao = FakeTxDao()
        fakeMerchantDao = FakeMerchantDao()
        fakeCategoryDao = FakeCustomCategoryDao()
        txRepository = TransactionRepository(
            transactionDao = fakeTxDao,
            merchantCategoryDao = fakeMerchantDao
        )
        catRepository = CategoryRepository(
            customCategoryDao = fakeCategoryDao,
            transactionDao = fakeTxDao,
            merchantCategoryDao = fakeMerchantDao
        )
    }

    private fun insertTx(
        id: Long = 101L,
        amountPaise: Long = 25000L,
        merchant: String = "Test Store",
        category: String = "Others",
        reviewStatus: ReviewStatus = ReviewStatus.CONFIRMED,
        isDeleted: Int = 0
    ): TransactionEntity {
        val entity = TransactionEntity(
            id = id,
            amountPaise = amountPaise,
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = merchant,
            category = category,
            categorySource = "AUTO",
            paymentMode = "UPI",
            note = null,
            accountNumber = "1234",
            bankName = "Bank",
            reference = "REF_$id",
            transactionHash = "hash_$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyBits = 0,
            createdAt = 1700000000000L,
            updatedAt = 1700000000000L,
            isDeleted = isDeleted
        )
        fakeTxDao.insertSync(entity)
        return entity
    }

    // --- POPUP TESTS ---

    @Test
    fun testPopupCategories_includesBuiltInsAndCustomCategoriesInOrder() = runBlocking {
        fakeCategoryDao.insert(CustomCategoryEntity(nameKey = "hobbies", name = "Hobbies", iconKey = "TOOL", colorKey = "PURPLE", createdAt = 100L))
        fakeCategoryDao.insert(CustomCategoryEntity(nameKey = "gaming", name = "Gaming", iconKey = "ENTERTAINMENT", colorKey = "INDIGO", createdAt = 200L))

        val allCategories = catRepository.getAllCategories().first().map { it.name }

        // Must start with built-ins in order, followed by custom categories
        assertEquals(BUILT_IN_CATEGORIES.size + 2, allCategories.size)
        BUILT_IN_CATEGORIES.forEachIndexed { index, builtIn ->
            assertEquals(builtIn, allCategories[index])
        }
        assertEquals("Hobbies", allCategories[BUILT_IN_CATEGORIES.size])
        assertEquals("Gaming", allCategories[BUILT_IN_CATEGORIES.size + 1])
    }

    @Test
    fun testPopupCategorySelect_callsSameRepositoryPathAsN1NotificationButton() = runBlocking {
        insertTx(id = 201L, merchant = "Blue Tokai", category = "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW)

        // Select category "Food"
        val snapshot = txRepository.assignCategory(201L, "Food")
        assertNotNull(snapshot)
        assertEquals("Others", snapshot!!.previousCategory)
        assertEquals(ReviewStatus.NEEDS_REVIEW, snapshot.previousReviewStatus)

        // Verifying transaction updated
        val updated = fakeTxDao.getById(201L)!!
        assertEquals("Food", updated.category)
        assertEquals(ReviewStatus.CONFIRMED, updated.reviewStatus)

        // Verifying merchant rule remembered
        val rule = fakeMerchantDao.getByKey("blue tokai")
        assertNotNull(rule)
        assertEquals("Food", rule!!.category)

        // Verifying notification content matches N1 Saved notification with Undo
        val savedContent = CapturedExpenseNotificationBuilder.buildCategorySavedContent(
            txId = 201L,
            amountPaise = 25000L,
            category = "Food"
        )
        assertEquals("Saved: ₹250.00 · Food", savedContent.title)
        assertEquals(1, savedContent.actions.size)
        assertEquals("Undo", savedContent.actions[0].title)
        assertEquals(CapturedExpenseNotificationBuilder.ACTION_UNDO_CATEGORY, savedContent.actions[0].actionType)
    }

    @Test
    fun testPopupNotAnExpense_callsSameRepositoryPathAsN1NotificationButton() = runBlocking {
        insertTx(id = 202L, merchant = "Rent Transfer", category = "Bills", reviewStatus = ReviewStatus.CONFIRMED)

        val snapshot = txRepository.markNotAnExpense(202L)
        assertNotNull(snapshot)
        assertEquals(ReviewStatus.CONFIRMED, snapshot!!.previousReviewStatus)

        val updated = fakeTxDao.getById(202L)!!
        assertEquals(ReviewStatus.EXCLUDED, updated.reviewStatus)

        // Notification content matches N1 Not An Expense notification with Undo
        val notExpenseContent = CapturedExpenseNotificationBuilder.buildNotExpenseContent(
            txId = 202L,
            amountPaise = 25000L,
            merchantName = "Rent Transfer"
        )
        assertEquals("Marked as not an expense", notExpenseContent.title)
        assertEquals("₹250.00 at Rent Transfer not counted", notExpenseContent.text)
        assertEquals(1, notExpenseContent.actions.size)
        assertEquals("Undo", notExpenseContent.actions[0].title)
    }

    @Test
    fun testPopupInvalidOrMissingOrDeletedTransaction_handledGracefully() = runBlocking {
        // Invalid non-positive ID
        assertNull(txRepository.assignCategory(0L, "Food"))
        assertNull(txRepository.assignCategory(-5L, "Food"))
        assertNull(txRepository.markNotAnExpense(-1L))

        // Non-existent ID
        assertNull(txRepository.assignCategory(99999L, "Food"))
        assertNull(txRepository.markNotAnExpense(99999L))

        // Soft-deleted transaction
        insertTx(id = 203L, isDeleted = 1)
        assertNull(txRepository.assignCategory(203L, "Food"))
        assertNull(txRepository.markNotAnExpense(203L))
    }

    // --- NOT-COUNTED ROWS TESTS ---

    @Test
    fun testTotalsCalculation_excludesNotCountedRows() {
        val fixedInstant = Instant.ofEpochMilli(1700000000000L)
        val zoneId = ZoneId.of("Asia/Kolkata")

        val txCounted = FinancialTransaction(
            id = 1L,
            amountPaise = 50000L,
            recordType = RecordType.EXPENSE,
            dateTime = 1700000000000L,
            merchantName = "Store 1",
            category = "Food",
            categorySource = "AUTO",
            paymentMode = "UPI",
            note = null,
            accountNumber = null,
            bankName = null,
            reference = null,
            transactionHash = "hash1",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyReasons = emptySet(),
            createdAt = 1000L,
            updatedAt = 1000L,
            isDeleted = false
        )

        val txNotCounted = txCounted.copy(
            id = 2L,
            amountPaise = 75000L,
            reviewStatus = ReviewStatus.EXCLUDED,
            transactionHash = "hash2"
        )

        val txDeleted = txCounted.copy(
            id = 3L,
            amountPaise = 100000L,
            isDeleted = true,
            transactionHash = "hash3"
        )

        val list = listOf(txCounted, txNotCounted, txDeleted)

        // Spending totals must only include txCounted (50000 paise)
        val totals = calculateSpendingTotals(list, zoneId = zoneId, referenceInstant = fixedInstant)
        assertEquals(50000L, totals.todayPaise)
        assertEquals(50000L, totals.monthPaise)

        // Domain helper predicate
        assertTrue(txCounted.isCountedAsSpending())
        assertFalse(txNotCounted.isCountedAsSpending())
        assertFalse(txDeleted.isCountedAsSpending())
    }

    @Test
    fun testFormLogic_forNotCountedRow_showsCountThisExpenseButton() {
        val formStateNotCounted = EditTransactionFormState(
            initialId = 1L,
            initialAmountPaise = 30000L,
            initialSource = TransactionSource.SMS_AUTO,
            initialMerchant = "Merchant",
            initialCategory = "Food",
            initialDateTime = 1000L,
            initialPaymentMode = "UPI",
            initialNote = null,
            initialReviewStatus = ReviewStatus.EXCLUDED,
            amountInput = "300.00",
            merchantInput = "Merchant",
            selectedCategory = "Food",
            selectedDateTime = 1000L,
            selectedPaymentMode = "UPI",
            noteInput = ""
        )

        assertTrue(formStateNotCounted.isExcluded)
        assertEquals("Count this expense", formStateNotCounted.secondaryButtonText)

        val formStateCounted = formStateNotCounted.copy(
            initialReviewStatus = ReviewStatus.CONFIRMED
        )
        assertFalse(formStateCounted.isExcluded)
        assertEquals("Not an expense", formStateCounted.secondaryButtonText)
    }

    @Test
    fun testUndeleteTransaction_restoresSoftDeletedRow() = runBlocking {
        insertTx(id = 301L, isDeleted = 0)
        assertEquals(1, fakeTxDao.getAllActiveTransactions().first().size)

        // Delete
        txRepository.softDelete(301L)
        val deleted = fakeTxDao.getById(301L)!!
        assertEquals(1, deleted.isDeleted)
        assertEquals(0, fakeTxDao.getAllActiveTransactions().first().size)

        // Undelete
        txRepository.undelete(301L)
        val restored = fakeTxDao.getById(301L)!!
        assertEquals(0, restored.isDeleted)
        assertEquals(1, fakeTxDao.getAllActiveTransactions().first().size)
    }

    // --- FAKES ---

    private class FakeTxDao : TransactionDao {
        val list = mutableListOf<TransactionEntity>()
        private val flow = MutableStateFlow<List<TransactionEntity>>(emptyList())

        fun insertSync(entity: TransactionEntity) {
            list.removeIf { it.id == entity.id }
            list.add(entity)
            flow.value = list.filter { it.isDeleted == 0 }
        }

        override suspend fun insert(transaction: TransactionEntity): Long {
            insertSync(transaction)
            return transaction.id
        }

        override suspend fun insertAll(transactions: List<TransactionEntity>): List<Long> {
            transactions.forEach { insertSync(it) }
            return transactions.map { it.id }
        }

        override suspend fun getById(id: Long): TransactionEntity? =
            list.firstOrNull { it.id == id }

        override suspend fun getByHash(hash: String): TransactionEntity? =
            list.firstOrNull { it.transactionHash == hash }

        override suspend fun findDuplicateCandidate(
            reference: String,
            recordType: RecordType,
            amountPaise: Long,
            minDateTime: Long,
            maxDateTime: Long
        ): TransactionEntity? = null

        override suspend fun update(transaction: TransactionEntity) {
            val idx = list.indexOfFirst { it.id == transaction.id }
            if (idx != -1) {
                list[idx] = transaction
                flow.value = list.filter { it.isDeleted == 0 }
            }
        }

        override suspend fun getAllAutoCategorizedTransactions(): List<TransactionEntity> =
            list.filter { it.categorySource == "AUTO" && it.isDeleted == 0 }

        override fun getAllActiveTransactions(): Flow<List<TransactionEntity>> = flow

        override suspend fun softDelete(id: Long, updatedAt: Long) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx != -1) {
                list[idx] = list[idx].copy(isDeleted = 1, updatedAt = updatedAt)
                flow.value = list.filter { it.isDeleted == 0 }
            }
        }

        override suspend fun undelete(id: Long, updatedAt: Long) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx != -1) {
                list[idx] = list[idx].copy(isDeleted = 0, updatedAt = updatedAt)
                flow.value = list.filter { it.isDeleted == 0 }
            }
        }

        override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> = emptyList()
        override suspend fun findExpensesByMerchant(merchantName: String, minDateTime: Long, maxDateTime: Long): List<TransactionEntity> = emptyList()
        override fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getUnreviewedCount(): Flow<Int> = flowOf(0)
        override fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun searchTransactions(
            textPattern: String,
            exactPaise: Long?,
            digitPattern: String?,
            limit: Int
        ): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override suspend fun updateCategory(oldCategory: String, newCategory: String): Int = 0
        override suspend fun reassignCategoryToOthers(category: String, now: Long): Int = 0
        override suspend fun countActiveTransactionsInCategory(category: String): Int = 0
    }

    private class FakeMerchantDao : MerchantCategoryDao {
        val rules = mutableMapOf<String, MerchantCategoryEntity>()

        override suspend fun getByKey(key: String): MerchantCategoryEntity? = rules[key]
        override suspend fun getCategory(key: String): String? = rules[key]?.category
        override suspend fun upsert(entity: MerchantCategoryEntity) {
            rules[entity.merchantKey] = entity
        }
        override suspend fun getAll(): List<MerchantCategoryEntity> = rules.values.toList()
        override suspend fun updateCategory(oldCategory: String, newCategory: String): Int = 0
        override suspend fun deleteByCategory(category: String): Int = 0
        override suspend fun deleteByKey(key: String): Int = if (rules.remove(key) != null) 1 else 0
    }

    private class FakeCustomCategoryDao : CustomCategoryDao {
        val list = mutableListOf<CustomCategoryEntity>()
        private val flow = MutableStateFlow<List<CustomCategoryEntity>>(emptyList())

        override fun getAll(): Flow<List<CustomCategoryEntity>> = flow
        override suspend fun getAllSync(): List<CustomCategoryEntity> = list.toList()

        override suspend fun getByNameKey(nameKey: String): CustomCategoryEntity? =
            list.firstOrNull { it.nameKey == nameKey }

        override suspend fun insert(entity: CustomCategoryEntity) {
            list.add(entity)
            flow.value = list.toList()
        }

        override suspend fun update(entity: CustomCategoryEntity) {
            val idx = list.indexOfFirst { it.nameKey == entity.nameKey }
            if (idx != -1) {
                list[idx] = entity
                flow.value = list.toList()
            }
        }

        override suspend fun updateCategoryValues(
            oldKey: String,
            newKey: String,
            newName: String,
            newIcon: String,
            newColor: String
        ): Int {
            val idx = list.indexOfFirst { it.nameKey == oldKey }
            if (idx != -1) {
                list[idx] = list[idx].copy(nameKey = newKey, name = newName, iconKey = newIcon, colorKey = newColor)
                flow.value = list.toList()
                return 1
            }
            return 0
        }

        override suspend fun deleteByKey(nameKey: String): Int {
            val removed = list.removeIf { it.nameKey == nameKey }
            if (removed) {
                flow.value = list.toList()
                return 1
            }
            return 0
        }

        override suspend fun count(): Int = list.size
    }
}
