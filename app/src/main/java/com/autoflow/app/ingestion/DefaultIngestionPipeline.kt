package com.autoflow.app.ingestion

import androidx.room.withTransaction
import com.autoflow.app.data.db.AutoFlowDatabase
import com.autoflow.app.data.db.dao.RefundMatchDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.RefundMatchEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.MatchStatus
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.UncertaintyReason

/**
 * Core ingestion engine responsible for:
 * 1. Tier-1 deduplication (exact immutable transaction_hash).
 * 2. Tier-2 deduplication (bank reference match + amount + record type within time window).
 * 3. Soft-deletion protection: deleted transactions are NEVER resurrected during rescans.
 * 4. User-curated edit preservation: confirmed or edited transactions are NEVER overwritten.
 * 5. Strict refusal to deduplicate solely on (merchant, amount, time) without matching hash or reference.
 * 6. Transactionally capped refund allocations across single, partial, and multiple refunds.
 * 7. Zero raw SMS persistence or sensitive data leakage.
 */
class DefaultIngestionPipeline(
    private val transactionDao: TransactionDao,
    private val refundMatchDao: RefundMatchDao,
    private val merchantCategoryDao: com.autoflow.app.data.db.dao.MerchantCategoryDao? = null,
    private val runInTransaction: suspend (suspend () -> IngestionResult) -> IngestionResult = { block -> block() }
) : IngestionPipeline {

    constructor(
        database: AutoFlowDatabase,
        transactionDao: TransactionDao = database.transactionDao(),
        refundMatchDao: RefundMatchDao = database.refundMatchDao(),
        merchantCategoryDao: com.autoflow.app.data.db.dao.MerchantCategoryDao = database.merchantCategoryDao()
    ) : this(
        transactionDao = transactionDao,
        refundMatchDao = refundMatchDao,
        merchantCategoryDao = merchantCategoryDao,
        runInTransaction = { block -> database.withTransaction { block() } }
    )

    override suspend fun ingest(transaction: TransactionEntity): IngestionResult {
        // Basic entity validation
        if (transaction.amountPaise <= 0L) {
            return IngestionResult.Error("Transaction amount in paise must be strictly positive: ${transaction.amountPaise}")
        }
        if (transaction.transactionHash.isBlank()) {
            return IngestionResult.Error("Transaction hash cannot be blank")
        }

        // Tier 1 Deduplication: Exact transaction hash
        val existingByHash = transactionDao.getByHash(transaction.transactionHash)
        if (existingByHash != null) {
            if (existingByHash.isDeleted != 0) {
                return IngestionResult.SoftDeletedIgnored(existingByHash.transactionHash)
            }
            // Existing active transaction: preserve user edits, categories, or confirmed status
            return IngestionResult.DuplicateIgnored(
                existingHash = existingByHash.transactionHash,
                reason = "Tier-1 exact hash duplicate (status=${existingByHash.reviewStatus})"
            )
        }

        // Tier 2 Deduplication: Bank reference match (RRN, UTR, etc.)
        // Strictly only applied if reference is non-blank.
        if (!transaction.reference.isNullOrBlank()) {
            val windowMs = 48 * 3600 * 1000L // 48-hour tolerance window for SMS delivery delays
            val existingByRef = transactionDao.findDuplicateCandidate(
                reference = transaction.reference,
                recordType = transaction.recordType,
                amountPaise = transaction.amountPaise,
                minDateTime = transaction.dateTime - windowMs,
                maxDateTime = transaction.dateTime + windowMs
            )
            if (existingByRef != null) {
                if (existingByRef.isDeleted != 0) {
                    return IngestionResult.SoftDeletedIgnored(existingByRef.transactionHash)
                }
                return IngestionResult.DuplicateIgnored(
                    existingHash = existingByRef.transactionHash,
                    reason = "Tier-2 reference duplicate on ref='${transaction.reference}'"
                )
            }
        }

        // Invariant: Never deduplicate solely on (merchant, amount, approximate time)
        // If hash is distinct and reference is null or different, it proceeds to insertion.

        var txToInsert = transaction
        if (txToInsert.categorySource == "AUTO" && merchantCategoryDao != null) {
            val userRule = merchantCategoryDao.getCategory(
                com.autoflow.app.domain.category.CategoryEngine.normalizeKey(txToInsert.merchantName)
            )
            if (!userRule.isNullOrBlank()) {
                txToInsert = txToInsert.copy(category = userRule)
            }
        }

        return runInTransaction {
            val insertedId = transactionDao.insert(txToInsert)
            val persistedTx = txToInsert.copy(id = insertedId)

            if (persistedTx.recordType == RecordType.REFUND) {
                matchRefundInternal(persistedTx)
            }

            IngestionResult.Success(insertedId)
        }
    }

    private suspend fun matchRefundInternal(refundTx: TransactionEntity) {
        val totalAllocatedSoFar = refundMatchDao.getTotalAllocatedPaiseForRefund(refundTx.id)
        var unallocatedPaise = refundTx.amountPaise - totalAllocatedSoFar
        if (unallocatedPaise <= 0L) return

        // 1. Look for matching candidates by explicit reference first
        val candidatesByRef = if (!refundTx.reference.isNullOrBlank()) {
            transactionDao.findExpensesByReference(refundTx.reference)
        } else {
            emptyList()
        }

        // 2. If no candidate found by reference, fall back to matching by merchant name (last 90 days)
        val candidateExpenses = if (candidatesByRef.isNotEmpty()) {
            candidatesByRef
        } else {
            val lookbackMs = 90L * 24 * 3600 * 1000L
            transactionDao.findExpensesByMerchant(
                merchantName = refundTx.merchantName,
                minDateTime = refundTx.dateTime - lookbackMs,
                maxDateTime = refundTx.dateTime
            )
        }

        for (expense in candidateExpenses) {
            if (unallocatedPaise <= 0L) break

            val isExactRefMatch = !refundTx.reference.isNullOrBlank() && refundTx.reference == expense.reference

            // Financial correctness safeguard:
            // Never auto-match a refund solely on merchant name or amount when the original expense is ambiguous
            if (!isExactRefMatch && (expense.uncertaintyBits != 0 || expense.reviewStatus == ReviewStatus.NEEDS_REVIEW)) {
                continue
            }

            val currentAllocatedForExpense = refundMatchDao.getTotalAllocatedPaiseForExpense(expense.id)
            val remainingRefundable = expense.amountPaise - currentAllocatedForExpense
            if (remainingRefundable <= 0L) {
                // Expense is already fully refunded; cannot allocate any more
                continue
            }

            // Cap allocation at the expense's remaining refundable amount
            val allocateAmount = minOf(unallocatedPaise, remainingRefundable)

            val matchStatus = if (isExactRefMatch) MatchStatus.CONFIRMED else MatchStatus.AMBIGUOUS_PROPOSED

            refundMatchDao.insert(
                RefundMatchEntity(
                    originalExpenseId = expense.id,
                    refundTransactionId = refundTx.id,
                    allocatedAmountPaise = allocateAmount,
                    matchStatus = matchStatus
                )
            )

            unallocatedPaise -= allocateAmount
        }

        // If fully matched via confirmed match, clear UNMATCHED_REFUND flag
        if (unallocatedPaise == 0L) {
            val matches = refundMatchDao.getMatchesListForRefund(refundTx.id)
            if (matches.isNotEmpty() && matches.all { it.matchStatus == MatchStatus.CONFIRMED }) {
                val updatedBits = refundTx.uncertaintyBits and UncertaintyReason.UNMATCHED_REFUND.bit.inv()
                val newReviewStatus = if (updatedBits == 0) ReviewStatus.CONFIRMED else ReviewStatus.NEEDS_REVIEW
                transactionDao.update(
                    refundTx.copy(
                        reviewStatus = newReviewStatus,
                        uncertaintyBits = updatedBits,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
