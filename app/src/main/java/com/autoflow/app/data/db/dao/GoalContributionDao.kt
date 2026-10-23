package com.autoflow.app.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.autoflow.app.data.db.entity.GoalContributionEntity
import com.autoflow.app.data.model.SavingsType
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalContributionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(contribution: GoalContributionEntity): Long

    @Query("SELECT * FROM goal_contributions WHERE goal_id = :goalId ORDER BY date DESC")
    fun getContributionsForGoal(goalId: Long): Flow<List<GoalContributionEntity>>

    @Query("""
        SELECT COALESCE(SUM(amount_paise), 0) 
        FROM goal_contributions 
        WHERE goal_id = :goalId AND savings_type = :savingsType
    """)
    fun getSumForGoalAndType(goalId: Long, savingsType: SavingsType): Flow<Long>

    @Query("""
        SELECT COALESCE(SUM(amount_paise), 0) 
        FROM goal_contributions 
        WHERE goal_id = :goalId AND savings_type = 'ACTUAL_SAVED'
    """)
    suspend fun getActualSavedTotalForGoal(goalId: Long): Long

    @Query("""
        SELECT COALESCE(SUM(amount_paise), 0) 
        FROM goal_contributions 
        WHERE goal_id = :goalId AND savings_type = 'AVOIDED_PURCHASE'
    """)
    suspend fun getAvoidedSpendingTotalForGoal(goalId: Long): Long
}
