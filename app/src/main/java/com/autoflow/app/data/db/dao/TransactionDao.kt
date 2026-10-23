package com.autoflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(transactions: List<TransactionEntity>): List<Long>

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE transaction_hash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE reference = :reference 
          AND record_type = :recordType 
          AND amount_paise = :amountPaise 
          AND date_time BETWEEN :minDateTime AND :maxDateTime 
          AND is_deleted = 0 
        LIMIT 1
    """)
    suspend fun findDuplicateCandidate(
        reference: String,
        recordType: RecordType,
        amountPaise: Long,
        minDateTime: Long,
        maxDateTime: Long
    ): TransactionEntity?

    @Query("""
        SELECT * FROM transactions 
        WHERE date_time BETWEEN :startTime AND :endTime 
          AND is_deleted = 0 
        ORDER BY date_time DESC
    """)
    fun getTransactionsForPeriod(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("""
        SELECT * FROM transactions 
        WHERE review_status = :status 
          AND is_deleted = 0 
        ORDER BY date_time DESC
    """)
    fun getTransactionsByReviewStatus(status: ReviewStatus): Flow<List<TransactionEntity>>

    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE review_status = 'NEEDS_REVIEW' 
          AND is_deleted = 0
    """)
    fun getUnreviewedCount(): Flow<Int>

    @Query("UPDATE transactions SET is_deleted = 1, updated_at = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, updatedAt: Long = System.currentTimeMillis())
}
