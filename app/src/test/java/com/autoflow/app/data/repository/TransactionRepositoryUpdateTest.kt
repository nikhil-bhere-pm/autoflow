package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.ingestion.FakeTransactionDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.YearMonth

class TransactionRepositoryUpdateTest {

    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var merchantCategoryDao: FakeMerchantCategoryDao
    private lateinit var repository: TransactionRepository

    private val now = 1700000000000L

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

    private class FakeMerchantCategoryDao : MerchantCategoryDao {
        val rules = mutableMapOf<String, MerchantCategoryEntity>()

        override suspend fun getByKey(key: String): MerchantCategoryEntity? = rules[key]
        override suspend fun getCategory(key: String): String? = rules[key]?.category
        override suspend fun upsert(entity: MerchantCategoryEntity) {
            rules[entity.merchantKey] = entity
        }
        override suspend fun getAll(): List<MerchantCategoryEntity> = rules.values.toList()
    }

    private fun createTx(
        id: Long = 0L,
        amountPaise: Long = 50000L,
        merchant: String = "Swiggy",
        category: String = "Food",
        categorySource: String = "AUTO",
        paymentMode: String = PaymentMode.UPI,
        note: String? = null,
        source: TransactionSource = TransactionSource.SMS_AUTO,
        hash: String = "hash_$id",
        dateTime: Long = now - 10000L,
        isDeleted: Int = 0
    ) = TransactionEntity(
        id = id,
        amountPaise = amountPaise,
        recordType = RecordType.EXPENSE,
        dateTime = dateTime,
        merchantName = merchant,
        category = category,
        categorySource = categorySource,
        paymentMode = paymentMode,
        note = note,
        transactionHash = hash,
        source = source,
        reviewStatus = ReviewStatus.CONFIRMED,
        uncertaintyBits = 0,
        createdAt = dateTime,
        updatedAt = dateTime,
        isDeleted = isDeleted
    )

    @Test
    fun testUpdateTransaction_futureDateRejected() = runBlocking {
        val id = transactionDao.insert(createTx(source = TransactionSource.MANUAL_CASH))

        val result = repository.updateTransaction(
            id = id,
            merchantName = "Chai Point",
            category = "Food",
            dateTime = now + 5000L, // in the future
            paymentMode = PaymentMode.CASH,
            note = "Evening tea",
            now = now
        )

        assertEquals(UpdateTransactionResult.FutureDateNotAllowed, result)
    }

    @Test
    fun testUpdateTransaction_noteLengthBoundary() = runBlocking {
        val id = transactionDao.insert(createTx(source = TransactionSource.MANUAL_CASH))

        // Note of exactly 200 chars: accepted
        val note200 = "a".repeat(200)
        val res200 = repository.updateTransaction(
            id = id,
            merchantName = "Chai Point",
            category = "Food",
            dateTime = now - 1000L,
            paymentMode = PaymentMode.CASH,
            note = note200,
            now = now
        )
        assertTrue(res200 is UpdateTransactionResult.Success)
        assertEquals(200, (res200 as UpdateTransactionResult.Success).transaction.note?.length)

        // Note of 201 chars: rejected
        val note201 = "a".repeat(201)
        val res201 = repository.updateTransaction(
            id = id,
            merchantName = "Chai Point",
            category = "Food",
            dateTime = now - 1000L,
            paymentMode = PaymentMode.CASH,
            note = note201,
            now = now
        )
        assertEquals(UpdateTransactionResult.NoteTooLong, res201)
    }

    @Test
    fun testUpdateTransaction_whitespaceOnlyNoteBecomesNull() = runBlocking {
        val id = transactionDao.insert(createTx(source = TransactionSource.MANUAL_CASH, note = "Old note"))

        val result = repository.updateTransaction(
            id = id,
            merchantName = "Chai Point",
            category = "Food",
            dateTime = now - 1000L,
            paymentMode = PaymentMode.CASH,
            note = "   \n\t   ",
            now = now
        )

        assertTrue(result is UpdateTransactionResult.Success)
        assertNull((result as UpdateTransactionResult.Success).transaction.note)
    }

    @Test
    fun testUpdateTransaction_amountEdit_allowedForManual_rejectedForSms() = runBlocking {
        val manualId = transactionDao.insert(createTx(amountPaise = 50000L, source = TransactionSource.MANUAL_CASH))
        val smsId = transactionDao.insert(createTx(amountPaise = 50000L, source = TransactionSource.SMS_AUTO))

        // Manual row: amount edit allowed
        val manualRes = repository.updateTransaction(
            id = manualId,
            merchantName = "Cash Shop",
            category = "Food",
            dateTime = now - 1000L,
            paymentMode = PaymentMode.CASH,
            note = null,
            amountPaise = 75000L,
            now = now
        )
        assertTrue(manualRes is UpdateTransactionResult.Success)
        assertEquals(75000L, (manualRes as UpdateTransactionResult.Success).transaction.amountPaise)

        // SMS row: amount edit rejected
        val smsRes = repository.updateTransaction(
            id = smsId,
            merchantName = "Amazon",
            category = "Shopping",
            dateTime = now - 1000L,
            paymentMode = PaymentMode.CREDIT,
            note = null,
            amountPaise = 75000L,
            now = now
        )
        assertEquals(UpdateTransactionResult.CannotEditSmsAmount, smsRes)
    }

    @Test
    fun testUpdateTransaction_hashNeverChanged() = runBlocking {
        val initialHash = "original_immutable_hash_999"
        val id = transactionDao.insert(createTx(hash = initialHash, source = TransactionSource.MANUAL_CASH))

        val result = repository.updateTransaction(
            id = id,
            merchantName = "Updated Merchant",
            category = "Groceries",
            dateTime = now - 2000L,
            paymentMode = PaymentMode.CASH,
            note = "Updated note",
            amountPaise = 99900L,
            now = now
        )

        assertTrue(result is UpdateTransactionResult.Success)
        val updated = (result as UpdateTransactionResult.Success).transaction
        assertEquals(initialHash, updated.transactionHash)
        assertEquals(initialHash, transactionDao.getById(id)!!.transactionHash)
    }

    @Test
    fun testUpdateTransaction_atomicCategoryChange_retroUpdatesAutoRows_skipsUserAndDeleted() = runBlocking {
        val tx1Id = transactionDao.insert(createTx(merchant = "Starbucks", category = "Others", categorySource = "AUTO"))
        val tx2Id = transactionDao.insert(createTx(merchant = "STARBUCKS", category = "Others", categorySource = "AUTO"))
        val tx3Id = transactionDao.insert(createTx(merchant = "Starbucks", category = "Transport", categorySource = "USER"))
        val tx4Id = transactionDao.insert(createTx(merchant = "Starbucks", category = "Others", categorySource = "AUTO", isDeleted = 1))

        val res = repository.updateTransaction(
            id = tx1Id,
            merchantName = "Starbucks",
            category = "Food",
            dateTime = now - 1000L,
            paymentMode = PaymentMode.UPI,
            note = null,
            now = now
        )

        assertTrue(res is UpdateTransactionResult.Success)
        val tx1 = transactionDao.getById(tx1Id)!!
        assertEquals("Food", tx1.category)
        assertEquals("USER", tx1.categorySource)

        val tx2 = transactionDao.getById(tx2Id)!!
        assertEquals("Food", tx2.category)
        assertEquals("AUTO", tx2.categorySource)

        val tx3 = transactionDao.getById(tx3Id)!!
        assertEquals("Transport", tx3.category) // USER row untouched
        assertEquals("USER", tx3.categorySource)

        val tx4 = transactionDao.getById(tx4Id)!!
        assertEquals("Others", tx4.category) // deleted row untouched

        assertEquals("Food", merchantCategoryDao.getCategory("starbucks"))
    }

    @Test
    fun testUpdateTransaction_invalidPaymentMode_rejected() = runBlocking {
        val id = transactionDao.insert(createTx())
        val result = repository.updateTransaction(
            id = id,
            merchantName = "Store",
            category = "Food",
            dateTime = now - 1000L,
            paymentMode = "CRYPTO",
            note = null,
            now = now
        )
        assertEquals(UpdateTransactionResult.InvalidPaymentMode, result)
    }

    @Test
    fun testRangeQuery_halfOpenBounds() = runBlocking {
        val start = 1000L
        val end = 2000L

        transactionDao.insert(createTx(dateTime = start - 1, hash = "h_before"))
        val tStart = transactionDao.insert(createTx(dateTime = start, hash = "h_start"))
        val tMid = transactionDao.insert(createTx(dateTime = 1500L, hash = "h_mid"))
        val tEndMinus1 = transactionDao.insert(createTx(dateTime = end - 1, hash = "h_end_minus_1"))
        transactionDao.insert(createTx(dateTime = end, hash = "h_at_end")) // Must NOT be returned
        transactionDao.insert(createTx(dateTime = end + 1, hash = "h_after"))
        transactionDao.insert(createTx(dateTime = 1200L, isDeleted = 1, hash = "h_deleted")) // Must NOT be returned

        val results = repository.getTransactionsInRange(start, end).first()
        val ids = results.map { it.id }

        assertEquals(3, results.size)
        assertEquals(listOf(tEndMinus1, tMid, tStart), ids)
    }

    @Test
    fun testUpdateTransaction_atomicityRollbackOnStep2Failure() = runBlocking {
        var txSnapshot: TransactionEntity? = null
        val baseDao = FakeTransactionDao()
        val txDao = object : TransactionDao by baseDao {
            override suspend fun update(transaction: TransactionEntity) {
                if (txSnapshot == null) {
                    txSnapshot = baseDao.items.firstOrNull { it.id == transaction.id }?.copy()
                }
                baseDao.update(transaction)
            }
        }
        val failingRuleDao = object : MerchantCategoryDao {
            override suspend fun getByKey(key: String): MerchantCategoryEntity? = null
            override suspend fun getCategory(key: String): String? = null
            override suspend fun upsert(entity: MerchantCategoryEntity) {
                throw RuntimeException("Simulated SQLite constraint/write failure in Step 2")
            }
            override suspend fun getAll(): List<MerchantCategoryEntity> = emptyList()
        }
        val repo = TransactionRepository(
            transactionDao = txDao,
            merchantCategoryDao = failingRuleDao,
            runInTransaction = { block: suspend () -> Unit ->
                try {
                    block()
                } catch (e: Throwable) {
                    // Rollback Step 1 to pre-transaction snapshot
                    txSnapshot?.let { original ->
                        val idx = baseDao.items.indexOfFirst { it.id == original.id }
                        if (idx != -1) baseDao.items[idx] = original
                    }
                    throw e
                }
            }
        )

        val id = baseDao.insert(createTx(merchant = "Swiggy", category = "Food", categorySource = "AUTO"))

        try {
            repo.updateTransaction(
                id = id,
                merchantName = "Swiggy",
                category = "Groceries", // triggers Step 2
                dateTime = now - 1000L,
                paymentMode = PaymentMode.UPI,
                note = null,
                now = now
            )
            org.junit.Assert.fail("Expected exception from Step 2")
        } catch (e: RuntimeException) {
            assertEquals("Simulated SQLite constraint/write failure in Step 2", e.message)
        }

        // Verify Step 1 was rolled back completely
        val txAfterFailure = baseDao.getById(id)!!
        assertEquals("Food", txAfterFailure.category)
        assertEquals("AUTO", txAfterFailure.categorySource)
    }
}
