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
    val target_paise: Long,
    val deadline_at: Long?,
    val is_deleted: Int,
    val created_at: Long,
    val updated_at: Long,
    val total_paise: Long
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

    @Query("SELECT * FROM goals WHERE is_deleted = 0 ORDER BY created_at ASC")
    fun observeAllGoals(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals WHERE is_deleted = 0 ORDER BY created_at ASC")
    suspend fun getAllGoals(): List<GoalEntity>

    @Query("""
        SELECT 
            g.id,
            g.name,
            g.name_key,
            g.target_paise,
            g.deadline_at,
            g.is_deleted,
            g.created_at,
            g.updated_at,
            COALESCE(SUM(CASE WHEN e.is_deleted = 0 THEN e.amount_paise ELSE 0 END), 0) AS total_paise
        FROM goals g
        LEFT JOIN goal_entries e ON g.id = e.goal_id
        WHERE g.is_deleted = 0
        GROUP BY g.id
        ORDER BY g.created_at ASC
    """)
    fun observeGoalsRaw(): Flow<List<GoalAggregateRaw>>

    @Query("UPDATE goals SET is_deleted = 1, name_key = :tombstoneKey, updated_at = :now WHERE id = :id")
    suspend fun softDeleteGoal(id: Long, tombstoneKey: String, now: Long)

    @Query("UPDATE goals SET is_deleted = 0, name_key = :restoredNameKey, updated_at = :now WHERE id = :id")
    suspend fun undeleteGoal(id: Long, restoredNameKey: String, now: Long)

    // Goal Entries
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertEntry(entry: GoalEntryEntity): Long

    @Update
    suspend fun updateEntry(entry: GoalEntryEntity)

    @Query("SELECT * FROM goal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryById(id: Long): GoalEntryEntity?

    @Query("SELECT * FROM goal_entries WHERE goal_id = :goalId AND is_deleted = 0 ORDER BY occurred_at DESC, id DESC")
    fun observeEntriesForGoal(goalId: Long): Flow<List<GoalEntryEntity>>

    @Query("SELECT * FROM goal_entries WHERE goal_id = :goalId ORDER BY occurred_at DESC, id DESC")
    suspend fun getAllEntriesForGoal(goalId: Long): List<GoalEntryEntity>

    @Query("SELECT * FROM goal_entries WHERE goal_id = :goalId AND is_deleted = 0 ORDER BY occurred_at DESC, id DESC")
    suspend fun getActiveEntriesForGoal(goalId: Long): List<GoalEntryEntity>

    @Query("UPDATE goal_entries SET is_deleted = 1, updated_at = :now WHERE id = :id")
    suspend fun softDeleteEntry(id: Long, now: Long)

    @Query("UPDATE goal_entries SET is_deleted = 0, updated_at = :now WHERE id = :id")
    suspend fun undeleteEntry(id: Long, now: Long)
}
