package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.model.UncertaintyReason
import com.autoflow.app.domain.category.CategoryEngine
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.CategorySpendItem
import com.autoflow.app.domain.stats.MonthlySpendPoint
import com.autoflow.app.domain.stats.categoryBreakdown
import com.autoflow.app.domain.stats.fiveMonthSpendingSeries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.YearMonth
import java.time.ZoneId

sealed interface UpdateTransactionResult {
    data class Success(val transaction: FinancialTransaction) : UpdateTransactionResult
    object NotFound : UpdateTransactionResult
    object FutureDateNotAllowed : UpdateTransactionResult
    object NoteTooLong : UpdateTransactionResult
    object InvalidAmount : UpdateTransactionResult
    object CannotEditSmsAmount : UpdateTransactionResult
    object InvalidPaymentMode : UpdateTransactionResult
}

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

    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<FinancialTransaction>> {
        return transactionDao.getTransactionsInRange(startTime, endTime).map { list ->
            list.map { it.toDomain() }
        }
    }

    fun getFiveMonthSpendingSeries(
        records: List<FinancialTransaction>,
        selectedMonth: YearMonth,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<MonthlySpendPoint> {
        return fiveMonthSpendingSeries(records, selectedMonth, zone)
    }

    fun getCategoryBreakdown(
        records: List<FinancialTransaction>
    ): List<CategorySpendItem> {
        return categoryBreakdown(records)
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

    suspend fun updateTransaction(
        id: Long,
        merchantName: String,
        category: String,
        dateTime: Long,
        paymentMode: String,
        note: String?,
        amountPaise: Long? = null,
        now: Long = System.currentTimeMillis()
    ): UpdateTransactionResult {
        val existing = transactionDao.getById(id) ?: return UpdateTransactionResult.NotFound

        if (!PaymentMode.isValid(paymentMode)) {
            return UpdateTransactionResult.InvalidPaymentMode
        }

        if (dateTime > now) {
            return UpdateTransactionResult.FutureDateNotAllowed
        }

        val cleanedNote = note?.trim()?.ifBlank { null }
        if (cleanedNote != null && cleanedNote.length > 200) {
            return UpdateTransactionResult.NoteTooLong
        }

        val finalAmountPaise = if (amountPaise != null) {
            if (existing.source != TransactionSource.MANUAL_CASH) {
                return UpdateTransactionResult.CannotEditSmsAmount
            }
            if (amountPaise <= 0L || amountPaise > 10_000_000_000L) {
                return UpdateTransactionResult.InvalidAmount
            }
            amountPaise
        } else {
            existing.amountPaise
        }

        val trimmedMerchant = merchantName.trim()
        val finalMerchant = if (trimmedMerchant.isBlank()) {
            if (existing.merchantName == "Unknown Merchant") "Unknown Merchant" else existing.merchantName
        } else {
            trimmedMerchant
        }

        val categoryTrimmed = category.trim().ifBlank { existing.category }
        val categoryChanged = categoryTrimmed != existing.category
        val isRealMerchant = CategoryEngine.shouldStoreRule(finalMerchant)

        var resultEntity: TransactionEntity = existing

        runInTransaction {
            val updated = existing.copy(
                amountPaise = finalAmountPaise,
                merchantName = finalMerchant,
                category = categoryTrimmed,
                categorySource = "USER",
                dateTime = dateTime,
                paymentMode = paymentMode,
                note = cleanedNote,
                reviewStatus = ReviewStatus.CONFIRMED,
                uncertaintyBits = 0,
                updatedAt = now
            )
            transactionDao.update(updated)
            resultEntity = updated

            if (categoryChanged && isRealMerchant && merchantCategoryDao != null) {
                val normalizedKey = CategoryEngine.normalizeKey(finalMerchant)
                merchantCategoryDao.upsert(
                    MerchantCategoryEntity(
                        merchantKey = normalizedKey,
                        category = categoryTrimmed,
                        updatedAt = now
                    )
                )

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

        return UpdateTransactionResult.Success(resultEntity.toDomain())
    }

    suspend fun updateTransactionCorrection(
        id: Long,
        newMerchantName: String,
        newCategory: String,
        now: Long = System.currentTimeMillis()
    ) {
        val existing = transactionDao.getById(id) ?: return
        updateTransaction(
            id = id,
            merchantName = newMerchantName,
            category = newCategory,
            dateTime = existing.dateTime,
            paymentMode = existing.paymentMode,
            note = existing.note,
            amountPaise = null,
            now = now
        )
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
            paymentMode = paymentMode,
            note = note,
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
