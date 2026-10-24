package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.category.CategoryEngine
import com.autoflow.app.ingestion.DefaultIngestionPipeline
import com.autoflow.app.ingestion.FakeRefundMatchDao
import com.autoflow.app.ingestion.FakeTransactionDao
import com.autoflow.app.ingestion.IngestionResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TransactionRepositoryCategorySyncTest {

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var merchantCategoryDao: FakeMerchantCategoryDao
    private lateinit var repository: TransactionRepository

    private val baseTimestamp = 1700000000000L

    @Before
    fun setup() {
        transactionDao = FakeTransactionDao()
        merchantCategoryDao = FakeMerchantCategoryDao()
        repository = TransactionRepository(
            transactionDao = transactionDao,
            merchantCategoryDao = merchantCategoryDao,
            runInTransaction = { block -> block() }
        )
    }

    @Test
    fun testCategoryCorrection_syncsRuleAndRetroUpdatesOnlyAutoRows() = runBlocking {
        // Given transactions for Starbucks:
        // tx1: target row (AUTO, category Others)
        // tx2: other AUTO row with same normalized key (AUTO, category Others)
        // tx3: already USER-curated row (USER, category Transport)
        // tx4: other AUTO row with different normalized key (AUTO, category Others, tests NO prefix fallback)
        val tx1Id = transactionDao.insert(
            createTx(id = 1L, merchant = "Starbucks", category = "Others", categorySource = "AUTO", hash = "h1")
        )
        val tx2Id = transactionDao.insert(
            createTx(id = 2L, merchant = "STARBUCKS.", category = "Others", categorySource = "AUTO", hash = "h2")
        )
        val tx3Id = transactionDao.insert(
            createTx(id = 3L, merchant = "Starbucks", category = "Transport", categorySource = "USER", hash = "h3")
        )
        val tx4Id = transactionDao.insert(
            createTx(id = 4L, merchant = "STARBUCKS COFFEE", category = "Others", categorySource = "AUTO", hash = "h4")
        )

        // When user corrects tx1 category to "Food":
        repository.updateTransactionCorrection(tx1Id, "Starbucks", "Food")

        // Then:
        // 1. Target row is updated with category = Food, categorySource = USER
        val updatedTx1 = transactionDao.getById(tx1Id)
        assertNotNull(updatedTx1)
        assertEquals("Food", updatedTx1!!.category)
        assertEquals("USER", updatedTx1.categorySource)

        // 2. Rule is upserted for normalized key "starbucks"
        val rule = merchantCategoryDao.getByKey("starbucks")
        assertNotNull("Merchant rule must be stored for real merchant", rule)
        assertEquals("Food", rule!!.category)

        // 3. Other AUTO row with matching normalized key is retro-updated to Food (keeping categorySource = AUTO)
        val updatedTx2 = transactionDao.getById(tx2Id)
        assertNotNull(updatedTx2)
        assertEquals("Food", updatedTx2!!.category)
        assertEquals("AUTO", updatedTx2.categorySource)

        // 4. USER row is NEVER modified
        val updatedTx3 = transactionDao.getById(tx3Id)
        assertNotNull(updatedTx3)
        assertEquals("Transport", updatedTx3!!.category)
        assertEquals("USER", updatedTx3.categorySource)

        // 5. Row with different key ("STARBUCKS COFFEE") is NOT modified (NO prefix fallback)
        val updatedTx4 = transactionDao.getById(tx4Id)
        assertNotNull(updatedTx4)
        assertEquals("Others", updatedTx4!!.category)
        assertEquals("AUTO", updatedTx4.categorySource)
    }

    @Test
    fun testCategoryCorrection_skipsSoftDeletedRows() = runBlocking {
        val tx1Id = transactionDao.insert(
            createTx(id = 1L, merchant = "Swiggy", category = "Others", categorySource = "AUTO", hash = "h1")
        )
        val tx2Id = transactionDao.insert(
            createTx(id = 2L, merchant = "Swiggy", category = "Others", categorySource = "AUTO", hash = "h2", isDeleted = 1)
        )

        repository.updateTransactionCorrection(tx1Id, "Swiggy", "Food")

        val updatedTx1 = transactionDao.getById(tx1Id)
        assertEquals("Food", updatedTx1?.category)

        // Soft-deleted row must not be updated
        val tx2 = transactionDao.items.find { it.id == tx2Id }
        assertEquals("Others", tx2?.category)
    }

    @Test
    fun testCategoryCorrection_junkMerchantNeverStoresRuleAndOnlyChangesTargetRow() = runBlocking {
        val tx1Id = transactionDao.insert(
            createTx(id = 1L, merchant = "Unknown Merchant", category = "Others", categorySource = "AUTO", hash = "h1")
        )
        val tx2Id = transactionDao.insert(
            createTx(id = 2L, merchant = "Unknown Merchant", category = "Others", categorySource = "AUTO", hash = "h2")
        )

        // When user corrects category on "Unknown Merchant":
        repository.updateTransactionCorrection(tx1Id, "Unknown Merchant", "Food")

        // 1. Only tx1 is updated with categorySource = USER
        val updatedTx1 = transactionDao.getById(tx1Id)
        assertEquals("Food", updatedTx1?.category)
        assertEquals("USER", updatedTx1?.categorySource)

        // 2. No rule is stored for junk merchant
        assertEquals(0, merchantCategoryDao.rules.size)
        assertNull(merchantCategoryDao.getByKey(CategoryEngine.normalizeKey("Unknown Merchant")))

        // 3. Other Unknown Merchant row is untouched
        val updatedTx2 = transactionDao.getById(tx2Id)
        assertEquals("Others", updatedTx2?.category)
        assertEquals("AUTO", updatedTx2?.categorySource)
    }

    @Test
    fun testCategoryCorrection_withEditedMerchantName_usesNewMerchantKey() = runBlocking {
        val tx1Id = transactionDao.insert(
            createTx(id = 1L, merchant = "Unknown Merchant", category = "Others", categorySource = "AUTO", hash = "h1")
        )
        val tx2Id = transactionDao.insert(
            createTx(id = 2L, merchant = "Zomato", category = "Others", categorySource = "AUTO", hash = "h2")
        )

        // User edits merchant name to "Zomato" and category to "Food"
        repository.updateTransactionCorrection(tx1Id, "Zomato", "Food")

        val updatedTx1 = transactionDao.getById(tx1Id)
        assertEquals("Zomato", updatedTx1?.merchantName)
        assertEquals("Food", updatedTx1?.category)
        assertEquals("USER", updatedTx1?.categorySource)

        // Rule is stored under "zomato"
        val rule = merchantCategoryDao.getByKey("zomato")
        assertNotNull(rule)
        assertEquals("Food", rule?.category)

        // tx2 with matching normalized key is updated
        val updatedTx2 = transactionDao.getById(tx2Id)
        assertEquals("Food", updatedTx2?.category)
    }

    @Test
    fun testDefaultIngestionPipeline_appliesUserRuleDuringIngestion() = runBlocking {
        // Given a user rule for "Starbucks" -> "Food"
        merchantCategoryDao.upsert(
            MerchantCategoryEntity(merchantKey = "starbucks", category = "Food", updatedAt = System.currentTimeMillis())
        )

        val pipeline = DefaultIngestionPipeline(
            transactionDao = transactionDao,
            refundMatchDao = FakeRefundMatchDao(),
            merchantCategoryDao = merchantCategoryDao,
            runInTransaction = { block -> block() }
        )

        // An incoming automated transaction with matching normalized key "STARBUCKS"
        val tx = createTx(id = 0L, merchant = "STARBUCKS", category = "Others", categorySource = "AUTO", hash = "new_hash")
        val result = pipeline.ingest(tx)

        assertTrue(result is IngestionResult.Success)
        val inserted = transactionDao.getById((result as IngestionResult.Success).transactionId)
        assertNotNull(inserted)
        assertEquals("Food", inserted!!.category)
        assertEquals("AUTO", inserted.categorySource)

        // An incoming automated transaction with different key "Starbucks Coffee" stays "Others" (no prefix fallback)
        val txOther = createTx(id = 0L, merchant = "Starbucks Coffee", category = "Others", categorySource = "AUTO", hash = "new_hash_2")
        val resultOther = pipeline.ingest(txOther)
        assertTrue(resultOther is IngestionResult.Success)
        val insertedOther = transactionDao.getById((resultOther as IngestionResult.Success).transactionId)
        assertNotNull(insertedOther)
        assertEquals("Others", insertedOther!!.category)
    }

    private fun createTx(
        id: Long,
        merchant: String,
        category: String,
        categorySource: String,
        hash: String,
        isDeleted: Int = 0
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            amountPaise = 50000L,
            recordType = RecordType.EXPENSE,
            dateTime = baseTimestamp,
            merchantName = merchant,
            category = category,
            categorySource = categorySource,
            transactionHash = hash,
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyBits = 0,
            createdAt = baseTimestamp,
            updatedAt = baseTimestamp,
            isDeleted = isDeleted
        )
    }

    private class FakeMerchantCategoryDao : MerchantCategoryDao {
        val rules = mutableMapOf<String, MerchantCategoryEntity>()

        override suspend fun getByKey(key: String): MerchantCategoryEntity? = rules[key]

        override suspend fun getCategory(key: String): String? = rules[key]?.category

        override suspend fun upsert(entity: MerchantCategoryEntity) {
            rules[entity.merchantKey] = entity
        }

        override suspend fun getAll(): List<MerchantCategoryEntity> = rules.values.toList()
    }
}
