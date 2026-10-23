package com.autoflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.autoflow.app.data.db.entity.GoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(goal: GoalEntity): Long

    @Update
    suspend fun update(goal: GoalEntity)

    @Query("SELECT * FROM goals WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): GoalEntity?

    @Query("SELECT * FROM goals WHERE is_archived = 0 ORDER BY target_date ASC")
    fun getActiveGoals(): Flow<List<GoalEntity>>

    @Query("UPDATE goals SET is_archived = 1 WHERE id = :id")
    suspend fun archiveGoal(id: Long)
}
