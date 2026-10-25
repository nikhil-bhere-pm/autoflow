package com.autoflow.app.presentation.preferences

import android.content.Context
import android.content.SharedPreferences

interface GoalPreferences {
    fun getLastUsedGoalId(): Long?
    fun setLastUsedGoalId(goalId: Long)
}

class SharedPreferencesGoalPreferences(
    private val sharedPreferences: SharedPreferences
) : GoalPreferences {

    override fun getLastUsedGoalId(): Long? {
        val id = sharedPreferences.getLong(KEY_LAST_USED_GOAL_ID, -1L)
        return if (id != -1L) id else null
    }

    override fun setLastUsedGoalId(goalId: Long) {
        sharedPreferences.edit().putLong(KEY_LAST_USED_GOAL_ID, goalId).apply()
    }

    companion object {
        const val PREFS_NAME = "autoflow_goal_prefs"
        const val KEY_LAST_USED_GOAL_ID = "last_used_goal_id"

        fun fromContext(context: Context): SharedPreferencesGoalPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SharedPreferencesGoalPreferences(prefs)
        }
    }
}

class InMemoryGoalPreferences(
    initialGoalId: Long? = null
) : GoalPreferences {
    private var lastUsedGoalId: Long? = initialGoalId

    override fun getLastUsedGoalId(): Long? = lastUsedGoalId

    override fun setLastUsedGoalId(goalId: Long) {
        lastUsedGoalId = goalId
    }
}
