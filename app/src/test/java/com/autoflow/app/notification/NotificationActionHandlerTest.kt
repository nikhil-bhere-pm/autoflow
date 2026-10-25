package com.autoflow.app.notification

import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NotificationActionHandlerTest {

    private lateinit var fakeTxDao: FakeTxDao
    private lateinit var fakeMerchantDao: FakeMerchantDao
    private lateinit var repository: TransactionRepository

    @Before
    fun setUp() {
        fakeTxDao = FakeTxDao()
        fakeMerchantDao = FakeMerchantDao()
        repository = TransactionRepository(
            transactionDao = fakeTxDao,
            merchantCategoryDao = fakeMerchantDao
        )
    }

    private fun insertSampleTx(
        id: Long = 1L,
        merchant: String = "Swiggy",
        category: String = "Others",
        categorySource: String = "AUTO",
        reviewStatus: ReviewStatus = ReviewStatus.NEEDS_REVIEW,
        uncertaintyBits: Int = 1,
        isDeleted: Int = 0
    ): TransactionEntity {
        val entity = TransactionEntity(
            id = id,
            amountPaise = 32000L,
            recordType = RecordType.EXPENSE,
            dateTime = 1000L,
            merchantName = merchant,
            category = category,
            categorySource = categorySource,
            paymentMode = "UPI",
            note = null,
            accountNumber = "1234",
            bankName = "HDFC",
            reference = "REF_$id",
            transactionHash = "hash_$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = reviewStatus,
            uncertaintyBits = uncertaintyBits,
            createdAt = 1000L,
            updatedAt = 1000L,
            isDeleted = isDeleted
        )
        fakeTxDao.insertSync(entity)
        return entity
    }

    @Test
    fun testAssignCategory_updatesStateAndStoresRuleAndRetroUpdates() = runBlocking {
        // Target tx
        insertSampleTx(id = 1L, merchant = "Swiggy", category = "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW)
        // Another AUTO row with same merchant
        insertSampleTx(id = 2L, merchant = "Swiggy", category = "Others", categorySource = "AUTO", reviewStatus = ReviewStatus.NEEDS_REVIEW)
        // USER row with same merchant should NOT be retro-updated
        insertSampleTx(id = 3L, merchant = "Swiggy", category = "Transport", categorySource = "USER", reviewStatus = ReviewStatus.CONFIRMED)

        val snapshot = repository.assignCategory(id = 1L, category = "Food")
        assertNotNull(snapshot)
        assertEquals("Others", snapshot!!.previousCategory)
        assertEquals(ReviewStatus.NEEDS_REVIEW, snapshot.previousReviewStatus)
        assertNull(snapshot.previousMerchantRuleCategory) // newly remembered

        // Verify target tx
        val tx1 = fakeTxDao.getById(1L)!!
        assertEquals("Food", tx1.category)
        assertEquals("USER", tx1.categorySource)
        assertEquals(ReviewStatus.CONFIRMED, tx1.reviewStatus)
        assertEquals(0, tx1.uncertaintyBits)

        // Verify rule stored
        val rule = fakeMerchantDao.getByKey("swiggy")
        assertNotNull(rule)
        assertEquals("Food", rule!!.category)

        // Verify auto row 2 was retro-updated
        val tx2 = fakeTxDao.getById(2L)!!
        assertEquals("Food", tx2.category)

        // Verify user row 3 was untouched
        val tx3 = fakeTxDao.getById(3L)!!
        assertEquals("Transport", tx3.category)
    }

    @Test
    fun testUndoAssignCategory_whenMerchantNewlyRemembered() = runBlocking {
        insertSampleTx(id = 10L, merchant = "Starbucks", category = "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW)
        insertSampleTx(id = 11L, merchant = "Starbucks", category = "Others", categorySource = "AUTO", reviewStatus = ReviewStatus.NEEDS_REVIEW)

        val snapshot = repository.assignCategory(id = 10L, category = "Food")!!
        assertNotNull(fakeMerchantDao.getByKey("starbucks"))

        // Now undo
        repository.undoAssignCategory(snapshot)

        // Verify rule deleted
        assertNull(fakeMerchantDao.getByKey("starbucks"))

        // Verify tx 10 restored
        val tx10 = fakeTxDao.getById(10L)!!
        assertEquals("Others", tx10.category)
        assertEquals("AUTO", tx10.categorySource)
        assertEquals(ReviewStatus.NEEDS_REVIEW, tx10.reviewStatus)
        assertEquals(1, tx10.uncertaintyBits)

        // Verify auto row 11 reverted
        val tx11 = fakeTxDao.getById(11L)!!
        assertEquals("Others", tx11.category)
    }

    @Test
    fun testUndoAssignCategory_whenMerchantPreviouslyRemembered() = runBlocking {
        // Merchant was previously remembered as Shopping
        fakeMerchantDao.upsert(MerchantCategoryEntity(merchantKey = "zara", category = "Shopping", updatedAt = 500L))

        insertSampleTx(id = 20L, merchant = "Zara", category = "Shopping", categorySource = "AUTO", reviewStatus = ReviewStatus.CONFIRMED)
        insertSampleTx(id = 21L, merchant = "Zara", category = "Shopping", categorySource = "AUTO", reviewStatus = ReviewStatus.CONFIRMED)

        val snapshot = repository.assignCategory(id = 20L, category = "Entertainment")!!
        assertEquals("Shopping", snapshot.previousMerchantRuleCategory)
        assertEquals("Entertainment", fakeMerchantDao.getByKey("zara")!!.category)

        // Undo
        repository.undoAssignCategory(snapshot)

        // Rule should be restored to Shopping
        val rule = fakeMerchantDao.getByKey("zara")
        assertNotNull(rule)
        assertEquals("Shopping", rule!!.category)

        // Row 20 restored to Shopping
        val tx20 = fakeTxDao.getById(20L)!!
        assertEquals("Shopping", tx20.category)

        // Row 21 restored to Shopping
        val tx21 = fakeTxDao.getById(21L)!!
        assertEquals("Shopping", tx21.category)
    }

    @Test
    fun testMarkNotAnExpense_andUndo() = runBlocking {
        insertSampleTx(id = 30L, merchant = "ATM", category = "Others", reviewStatus = ReviewStatus.CONFIRMED)

        val snapshot = repository.markNotAnExpense(id = 30L)
        assertNotNull(snapshot)
        assertEquals(ReviewStatus.CONFIRMED, snapshot!!.previousReviewStatus)

        val txAfter = fakeTxDao.getById(30L)!!
        assertEquals(ReviewStatus.EXCLUDED, txAfter.reviewStatus)

        // Undo
        repository.undoMarkNotAnExpense(snapshot)
        val txRestored = fakeTxDao.getById(30L)!!
        assertEquals(ReviewStatus.CONFIRMED, txRestored.reviewStatus)
    }

    @Test
    fun testInvalidOrUnknownTransactionId_isIgnored() = runBlocking {
        val snapshotAssign = repository.assignCategory(id = 9999L, category = "Food")
        assertNull(snapshotAssign)

        val snapshotNotExpense = repository.markNotAnExpense(id = 9999L)
        assertNull(snapshotNotExpense)
    }

    @Test
    fun testSoftDeletedTransaction_isIgnored() = runBlocking {
        insertSampleTx(id = 50L, isDeleted = 1)
        val snapshot = repository.assignCategory(id = 50L, category = "Food")
        assertNull(snapshot)
    }

    @Test
    fun testUndoMerchantRule_subsequentSpendNeedsCategory() = runBlocking {
        // First spend at new merchant "Chai Stall"
        insertSampleTx(id = 60L, merchant = "Chai Stall", category = "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW)

        // Initially no rule exists
        assertFalse(repository.hasMerchantRule("Chai Stall"))

        // Assign category from notification -> creates rule
        val snapshot = repository.assignCategory(id = 60L, category = "Food")
        assertNotNull(snapshot)
        assertTrue(repository.hasMerchantRule("Chai Stall"))

        // Tap Undo on the new rule
        repository.undoAssignCategory(snapshot!!)

        // Rule is removed
        assertFalse(repository.hasMerchantRule("Chai Stall"))

        // Subsequent spend at Chai Stall
        val nextTxEntity = insertSampleTx(id = 61L, merchant = "Chai Stall", category = "Others", reviewStatus = ReviewStatus.NEEDS_REVIEW)
        val hasRule = repository.hasMerchantRule(nextTxEntity.merchantName)
        assertFalse(hasRule)

        val nextTx = repository.getTransactionById(nextTxEntity.id)!!
        val nextContent = CapturedExpenseNotificationBuilder.buildInitialContent(
            nextTx,
            com.autoflow.app.domain.category.BUILT_IN_CATEGORIES,
            hasSavedRule = hasRule
        )

        // Falls back to "needs category" (shows 3 buttons: 2 likeliest + "More…")
        assertEquals(3, nextContent.actions.size)
        assertEquals("More…", nextContent.actions[2].title)
        assertEquals("Pick a category so it is counted correctly", nextContent.text)
    }

    // Fakes
    private class FakeTxDao : TransactionDao {
        val list = mutableListOf<TransactionEntity>()

        fun insertSync(entity: TransactionEntity) {
            list.removeIf { it.id == entity.id }
            list.add(entity)
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
            }
        }

        override suspend fun getAllAutoCategorizedTransactions(): List<TransactionEntity> =
            list.filter { it.categorySource == "AUTO" && it.isDeleted == 0 }

        override fun getAllActiveTransactions(): Flow<List<TransactionEntity>> =
            MutableStateFlow(list.filter { it.isDeleted == 0 })

        override suspend fun findExpensesByReference(reference: String): List<TransactionEntity> = emptyList()
        override suspend fun findExpensesByMerchant(merchantName: String, minDateTime: Long, maxDateTime: Long): List<TransactionEntity> = emptyList()
        override fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>> = flowOf(emptyList())
        override fun getUnreviewedCount(): Flow<Int> = flowOf(0)
        override suspend fun softDelete(id: Long, updatedAt: Long) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx != -1) list[idx] = list[idx].copy(isDeleted = 1, updatedAt = updatedAt)
        }
        override suspend fun undelete(id: Long, updatedAt: Long) {
            val idx = list.indexOfFirst { it.id == id }
            if (idx != -1) list[idx] = list[idx].copy(isDeleted = 0, updatedAt = updatedAt)
        }
        override fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> = flowOf(emptyList())
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
        override suspend fun deleteByKey(key: String): Int {
            return if (rules.remove(key) != null) 1 else 0
        }
    }
}
