package com.autoflow.app.presentation.preferences

import android.content.Context
import android.content.SharedPreferences

interface FirstRunPreferences {
    val isFirstRunCompleted: Boolean
    fun setFirstRunCompleted(completed: Boolean)
}

class SharedPreferencesFirstRunPreferences(
    private val sharedPreferences: SharedPreferences
) : FirstRunPreferences {

    override val isFirstRunCompleted: Boolean
        get() = sharedPreferences.getBoolean(KEY_FIRST_RUN_COMPLETED, false)

    override fun setFirstRunCompleted(completed: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_FIRST_RUN_COMPLETED, completed).apply()
    }

    companion object {
        const val PREFS_NAME = "autoflow_settings"
        const val KEY_FIRST_RUN_COMPLETED = "has_completed_first_run"

        fun fromContext(context: Context): SharedPreferencesFirstRunPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SharedPreferencesFirstRunPreferences(prefs)
        }
    }
}

class InMemoryFirstRunPreferences(
    initialCompleted: Boolean = false
) : FirstRunPreferences {
    private var completed: Boolean = initialCompleted

    override val isFirstRunCompleted: Boolean
        get() = completed

    override fun setFirstRunCompleted(completed: Boolean) {
        this.completed = completed
    }
}
