package com.autoflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autoflow.app.data.db.entity.RefundMatchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RefundMatchDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(refundMatch: RefundMatchEntity): Long

    @Query("SELECT * FROM refund_matches WHERE original_expense_id = :expenseId")
    fun getMatchesForExpense(expenseId: Long): Flow<List<RefundMatchEntity>>

    @Query("SELECT * FROM refund_matches WHERE refund_transaction_id = :refundTxId")
    fun getMatchesForRefund(refundTxId: Long): Flow<List<RefundMatchEntity>>

    @Query("""
        SELECT COALESCE(SUM(allocated_amount_paise), 0) 
        FROM refund_matches 
        WHERE original_expense_id = :expenseId
    """)
    suspend fun getTotalAllocatedPaiseForExpense(expenseId: Long): Long

    @Query("DELETE FROM refund_matches WHERE id = :id")
    suspend fun deleteById(id: Long)
}
