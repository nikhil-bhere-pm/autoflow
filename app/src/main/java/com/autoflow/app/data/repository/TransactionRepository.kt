package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.UncertaintyReason
import com.autoflow.app.domain.model.FinancialTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val transactionDao: TransactionDao
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
