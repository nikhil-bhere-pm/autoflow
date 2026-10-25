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
        ORDER BY is_deleted DESC, id DESC 
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
        WHERE record_type = 'EXPENSE' 
          AND is_deleted = 0 
          AND reference = :reference 
        ORDER BY date_time DESC
    """)
    suspend fun findExpensesByReference(reference: String): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE record_type = 'EXPENSE' 
          AND is_deleted = 0 
          AND merchant_name = :merchantName 
          AND date_time BETWEEN :minDateTime AND :maxDateTime 
        ORDER BY date_time DESC
    """)
    suspend fun findExpensesByMerchant(
        merchantName: String,
        minDateTime: Long,
        maxDateTime: Long
    ): List<TransactionEntity>

    @Query("""
        SELECT * FROM transactions 
        WHERE is_deleted = 0 
        ORDER BY date_time DESC
    """)
    fun getAllActiveTransactions(): Flow<List<TransactionEntity>>

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

    @Query("UPDATE transactions SET is_deleted = 0, updated_at = :updatedAt WHERE id = :id")
    suspend fun undelete(id: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE transactions SET category = :newCategory WHERE category = :oldCategory")
    suspend fun updateCategory(oldCategory: String, newCategory: String): Int = 0

    @Query("UPDATE transactions SET category = 'Others', categorySource = 'AUTO', updated_at = :now WHERE category = :category")
    suspend fun reassignCategoryToOthers(category: String, now: Long = System.currentTimeMillis()): Int = 0

    @Query("SELECT COUNT(*) FROM transactions WHERE is_deleted = 0 AND category = :category")
    suspend fun countActiveTransactionsInCategory(category: String): Int = 0

    companion object {
        const val QUERY_GET_ALL_AUTO = """
            SELECT * FROM transactions 
            WHERE is_deleted = 0 
              AND categorySource = 'AUTO'
        """

        const val QUERY_GET_IN_RANGE = """
            SELECT * FROM transactions 
            WHERE is_deleted = 0 
              AND date_time >= :startTime 
              AND date_time < :endTime 
            ORDER BY date_time DESC
        """

        const val SQL_UPDATE = """
            UPDATE OR ABORT `transactions` SET `id` = ?,`amount_paise` = ?,`record_type` = ?,`date_time` = ?,`merchant_name` = ?,`category` = ?,`categorySource` = ?,`payment_mode` = ?,`note` = ?,`account_number` = ?,`bank_name` = ?,`reference` = ?,`transaction_hash` = ?,`source` = ?,`review_status` = ?,`uncertainty_bits` = ?,`created_at` = ?,`updated_at` = ?,`is_deleted` = ? WHERE `id` = ?
        """
    }

    @Query(QUERY_GET_ALL_AUTO)
    suspend fun getAllAutoCategorizedTransactions(): List<TransactionEntity>

    @Query(QUERY_GET_IN_RANGE)
    fun getTransactionsInRange(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>
}
