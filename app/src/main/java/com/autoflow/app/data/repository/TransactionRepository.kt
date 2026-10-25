package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.CustomCategoryDao
import com.autoflow.app.data.db.dao.MerchantCategoryDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.MerchantCategoryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.model.UncertaintyReason
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.category.CategoryEngine
import com.autoflow.app.domain.category.normalizeCategoryKey
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
    object CategoryNotFound : UpdateTransactionResult
}

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val merchantCategoryDao: MerchantCategoryDao? = null,
    private val customCategoryDao: CustomCategoryDao? = null,
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

    fun searchTransactions(query: String, limit: Int = 200): Flow<List<FinancialTransaction>> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        val textPattern = com.autoflow.app.domain.search.TransactionSearchHelper.buildTextPattern(trimmed)
        val exactPaise = com.autoflow.app.domain.search.TransactionSearchHelper.parseRupeesToPaise(trimmed)
        val digitPattern = com.autoflow.app.domain.search.TransactionSearchHelper.extractDigitsPattern(trimmed)
        return transactionDao.searchTransactions(
            textPattern = textPattern,
            exactPaise = exactPaise,
            digitPattern = digitPattern,
            limit = limit
        ).map { list -> list.map { it.toDomain() } }
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
        val isBuiltIn = BUILT_IN_CATEGORIES.any { it.equals(categoryTrimmed, ignoreCase = true) }
        val isCustom = if (!isBuiltIn && customCategoryDao != null) {
            customCategoryDao.getByNameKey(normalizeCategoryKey(categoryTrimmed)) != null
        } else false

        if (!isBuiltIn && !isCustom) {
            return UpdateTransactionResult.CategoryNotFound
        }

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

    suspend fun undelete(id: Long, now: Long = System.currentTimeMillis()) {
        transactionDao.undelete(id, now)
    }

    suspend fun hasMerchantRule(merchantName: String): Boolean {
        if (!CategoryEngine.shouldStoreRule(merchantName) || merchantCategoryDao == null) return false
        val normalizedKey = CategoryEngine.normalizeKey(merchantName)
        return merchantCategoryDao.getByKey(normalizedKey) != null
    }

    suspend fun assignCategory(
        id: Long,
        category: String,
        now: Long = System.currentTimeMillis()
    ): CategoryUndoSnapshot? {
        val existing = transactionDao.getById(id) ?: return null
        if (existing.isDeleted != 0) return null
        val normalizedKey = CategoryEngine.normalizeKey(existing.merchantName)
        val isRealMerchant = CategoryEngine.shouldStoreRule(existing.merchantName)
        val previousRule = if (isRealMerchant && merchantCategoryDao != null) {
            merchantCategoryDao.getByKey(normalizedKey)?.category
        } else {
            null
        }
        val snapshot = CategoryUndoSnapshot(
            transactionId = id,
            previousCategory = existing.category,
            previousCategorySource = existing.categorySource,
            previousReviewStatus = existing.reviewStatus,
            previousUncertaintyBits = existing.uncertaintyBits,
            previousMerchantRuleCategory = previousRule,
            assignedCategory = category.trim(),
            normalizedMerchantKey = normalizedKey
        )

        updateTransaction(
            id = id,
            merchantName = existing.merchantName,
            category = category,
            dateTime = existing.dateTime,
            paymentMode = existing.paymentMode,
            note = existing.note,
            now = now
        )

        return snapshot
    }

    suspend fun undoAssignCategory(
        snapshot: CategoryUndoSnapshot,
        now: Long = System.currentTimeMillis()
    ) {
        val existing = transactionDao.getById(snapshot.transactionId) ?: return
        runInTransaction {
            val reverted = existing.copy(
                category = snapshot.previousCategory,
                categorySource = snapshot.previousCategorySource,
                reviewStatus = snapshot.previousReviewStatus,
                uncertaintyBits = snapshot.previousUncertaintyBits,
                updatedAt = now
            )
            transactionDao.update(reverted)

            if (merchantCategoryDao != null && snapshot.normalizedMerchantKey.isNotBlank()) {
                if (snapshot.previousMerchantRuleCategory == null) {
                    merchantCategoryDao.deleteByKey(snapshot.normalizedMerchantKey)
                    val autoRows = transactionDao.getAllAutoCategorizedTransactions()
                    for (other in autoRows) {
                        if (other.id != snapshot.transactionId &&
                            CategoryEngine.normalizeKey(other.merchantName) == snapshot.normalizedMerchantKey &&
                            other.category == snapshot.assignedCategory
                        ) {
                            transactionDao.update(
                                other.copy(
                                    category = snapshot.previousCategory,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                } else {
                    merchantCategoryDao.upsert(
                        MerchantCategoryEntity(
                            merchantKey = snapshot.normalizedMerchantKey,
                            category = snapshot.previousMerchantRuleCategory,
                            updatedAt = now
                        )
                    )
                    val autoRows = transactionDao.getAllAutoCategorizedTransactions()
                    for (other in autoRows) {
                        if (other.id != snapshot.transactionId &&
                            CategoryEngine.normalizeKey(other.merchantName) == snapshot.normalizedMerchantKey
                        ) {
                            transactionDao.update(
                                other.copy(
                                    category = snapshot.previousMerchantRuleCategory,
                                    updatedAt = now
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun markNotAnExpense(
        id: Long,
        now: Long = System.currentTimeMillis()
    ): NotExpenseUndoSnapshot? {
        val existing = transactionDao.getById(id) ?: return null
        if (existing.isDeleted != 0) return null
        val snapshot = NotExpenseUndoSnapshot(
            transactionId = id,
            previousReviewStatus = existing.reviewStatus
        )
        transactionDao.update(
            existing.copy(
                reviewStatus = ReviewStatus.EXCLUDED,
                updatedAt = now
            )
        )
        return snapshot
    }

    suspend fun undoMarkNotAnExpense(
        snapshot: NotExpenseUndoSnapshot,
        now: Long = System.currentTimeMillis()
    ) {
        val existing = transactionDao.getById(snapshot.transactionId) ?: return
        transactionDao.update(
            existing.copy(
                reviewStatus = snapshot.previousReviewStatus,
                updatedAt = now
            )
        )
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

data class CategoryUndoSnapshot(
    val transactionId: Long,
    val previousCategory: String,
    val previousCategorySource: String,
    val previousReviewStatus: ReviewStatus,
    val previousUncertaintyBits: Int,
    val previousMerchantRuleCategory: String?,
    val assignedCategory: String,
    val normalizedMerchantKey: String
)

data class NotExpenseUndoSnapshot(
    val transactionId: Long,
    val previousReviewStatus: ReviewStatus
)

