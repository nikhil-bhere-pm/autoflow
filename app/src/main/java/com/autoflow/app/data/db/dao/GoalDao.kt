package com.autoflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.GoalEntryEntity
import kotlinx.coroutines.flow.Flow

data class GoalAggregateRaw(
    val id: Long,
    val name: String,
    val name_key: String,
    val kind: String,
    val target_paise: Long,
    val set_aside_paise: Long,
    val avoided_paise: Long,
    val used_paise: Long
)

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertGoal(goal: GoalEntity): Long

    @Update
    suspend fun updateGoal(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getGoalById(id: Long): GoalEntity?

    @Query("SELECT * FROM goals WHERE name_key = :nameKey LIMIT 1")
    suspend fun getGoalByNameKey(nameKey: String): GoalEntity?

    @Query("SELECT * FROM goals ORDER BY created_at ASC")
    fun observeAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY created_at ASC")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("""
        SELECT 
            g.id,
            g.name,
            g.name_key,
            g.kind,
            g.target_paise,
            COALESCE(SUM(CASE WHEN e.type = 'SET_ASIDE' AND e.is_deleted = 0 THEN e.amount_paise ELSE 0 END), 0) AS set_aside_paise,
            COALESCE(SUM(CASE WHEN e.type = 'AVOIDED' AND e.is_deleted = 0 THEN e.amount_paise ELSE 0 END), 0) AS avoided_paise,
            COALESCE(SUM(CASE WHEN e.type = 'USED' AND e.is_deleted = 0 THEN e.amount_paise ELSE 0 END), 0) AS used_paise
        FROM goals g
        LEFT JOIN goal_entries e ON g.id = e.goal_id
        GROUP BY g.id
        ORDER BY g.created_at ASC
    """)
    fun observeGoalsRaw(): Flow<List<GoalAggregateRaw>>

    // Goal Entries
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: GoalEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: GoalEntryEntity)

    @Query("SELECT * FROM goal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): GoalEntryEntity?

    @Query("SELECT * FROM goal_entries WHERE goal_id = :goalId ORDER BY occurred_at DESC, id DESC")
    fun observeEntriesForGoal(goalId: Long): Flow<List<GoalEntryEntity>>

    @Query("SELECT * FROM goal_entries WHERE goal_id = :goalId ORDER BY occurred_at DESC, id DESC")
    suspend fun getEntriesForGoal(goalId: Long): List<GoalEntryEntity>

    @Query("SELECT * FROM goal_entries WHERE goal_id = :goalId AND is_deleted = 0 ORDER BY occurred_at DESC, id DESC")
    suspend fun getActiveEntriesForGoal(goalId: Long): List<GoalEntryEntity>

    @Query("SELECT * FROM goal_entries WHERE linked_transaction_id = :txId AND is_deleted = 0")
    suspend fun getActiveEntriesByTransactionId(txId: Long): List<GoalEntryEntity>

    @Query("SELECT * FROM goal_entries WHERE linked_transaction_id = :txId")
    suspend fun getAllEntriesByTransactionId(txId: Long): List<GoalEntryEntity>

    @Query("UPDATE goal_entries SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDeleteEntry(id: Long, now: Long)

    @Query("UPDATE goal_entries SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun restoreEntry(id: Long, now: Long)
}
