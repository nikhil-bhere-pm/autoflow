package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.UncertaintyReason
import com.autoflow.app.domain.category.CategoryEngine
import com.autoflow.app.domain.model.FinancialTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val merchantCategoryDao: MerchantCategoryDao? = null,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() }
) {
    suspend fun insertTransaction(transaction: TransactionEntity): Long {
        return transactionDao.insert(transaction)
    }

    suspend fun getTransactionById(id: Long): FinancialTransaction? {
        return transactionDao.getById(id)?.toDomain()
    }

    suspend fun getTransactionByHash(hash: String): FinancialTransaction? {
        return transactionDao.getByHash(hash)?.toDomain()
    }

    fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<FinancialTransaction>> {
        return transactionDao.getTransactionsForPeriod(startTime, endTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<FinancialTransaction>> {
        return transactionDao.getTransactionsByReviewStatus(status).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getUnreviewedCount(): Flow<Int> {
        return transactionDao.getUnreviewedCount()
    }

    fun getAllActiveTransactions(): Flow<List<FinancialTransaction>> {
        return transactionDao.getAllActiveTransactions().map { list ->
            list.map { it.toDomain() }
        }
    }

    suspend fun getEntityById(id: Long): TransactionEntity? {
        return transactionDao.getById(id)
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        transactionDao.update(transaction)
    }

    suspend fun updateTransactionCorrection(
        id: Long,
        newMerchantName: String,
        newCategory: String,
        now: Long = System.currentTimeMillis()
    ) {
        val existing = transactionDao.getById(id) ?: return
        val trimmedMerchant = newMerchantName.trim()
        val finalMerchant = if (trimmedMerchant.isBlank()) {
            if (existing.merchantName == "Unknown Merchant") "Unknown Merchant" else existing.merchantName
        } else {
            trimmedMerchant
        }
        val categoryTrimmed = newCategory.trim().ifBlank { existing.category }
        val categoryChanged = categoryTrimmed != existing.category
        val isRealMerchant = CategoryEngine.shouldStoreRule(finalMerchant)

        runInTransaction {
            // (a) update that row, categorySource=USER
            val updated = existing.copy(
                merchantName = finalMerchant,
                category = categoryTrimmed,
                categorySource = "USER",
                reviewStatus = ReviewStatus.CONFIRMED,
                uncertaintyBits = 0,
                updatedAt = now
            )
            transactionDao.update(updated)

            // When user changes category on a row with a real merchant name:
            if (categoryChanged && isRealMerchant && merchantCategoryDao != null) {
                val normalizedKey = CategoryEngine.normalizeKey(finalMerchant)

                // (b) upsert the merchant rule
                merchantCategoryDao.upsert(
                    MerchantCategoryEntity(
                        merchantKey = normalizedKey,
                        category = categoryTrimmed,
                        updatedAt = now
                    )
                )

                // (c) update all OTHER non-deleted rows with the same normalized merchant key whose categorySource='AUTO'
                val autoRows = transactionDao.getAllAutoCategorizedTransactions()
                for (other in autoRows) {
                    if (other.id != id && CategoryEngine.normalizeKey(other.merchantName) == normalizedKey) {
                        transactionDao.update(
                            other.copy(
                                category = categoryTrimmed,
                                updatedAt = now
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun softDelete(id: Long) {
        transactionDao.softDelete(id)
    }

    private fun TransactionEntity.toDomain(): FinancialTransaction {
        return FinancialTransaction(
            id = id,
            amountPaise = amountPaise,
            recordType = recordType,
            dateTime = dateTime,
            merchantName = merchantName,
            category = category,
            categorySource = categorySource,
            accountNumber = accountNumber,
            bankName = bankName,
            reference = reference,
            transactionHash = transactionHash,
            source = source,
            reviewStatus = reviewStatus,
            uncertaintyReasons = UncertaintyReason.fromBitmask(uncertaintyBits),
            createdAt = createdAt,
            updatedAt = updatedAt,
            isDeleted = isDeleted != 0
        )
    }
}
