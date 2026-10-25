package com.autoflow.app.presentation.preferences

import android.content.Context
import android.content.SharedPreferences

interface NotificationPreferences {
    fun isCapturedExpenseNotificationEnabled(): Boolean
    fun setCapturedExpenseNotificationEnabled(enabled: Boolean)
    fun hasRequestedNotificationPermission(): Boolean
    fun setHasRequestedNotificationPermission(requested: Boolean)
}

class SharedPreferencesNotificationPreferences(
    private val sharedPreferences: SharedPreferences
) : NotificationPreferences {

    override fun isCapturedExpenseNotificationEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_NOTIFY_CAPTURED_EXPENSE, true)
    }

    override fun setCapturedExpenseNotificationEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_NOTIFY_CAPTURED_EXPENSE, enabled).apply()
    }

    override fun hasRequestedNotificationPermission(): Boolean {
        return sharedPreferences.getBoolean(KEY_HAS_REQUESTED_PERMISSION, false)
    }

    override fun setHasRequestedNotificationPermission(requested: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_HAS_REQUESTED_PERMISSION, requested).apply()
    }

    companion object {
        const val PREFS_NAME = "autoflow_notification_prefs"
        const val KEY_NOTIFY_CAPTURED_EXPENSE = "notify_captured_expense"
        const val KEY_HAS_REQUESTED_PERMISSION = "has_requested_notification_permission"

        fun fromContext(context: Context): SharedPreferencesNotificationPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SharedPreferencesNotificationPreferences(prefs)
        }
    }
}

class InMemoryNotificationPreferences(
    initialEnabled: Boolean = true,
    initialRequested: Boolean = false
) : NotificationPreferences {
    private var enabled: Boolean = initialEnabled
    private var requested: Boolean = initialRequested

    override fun isCapturedExpenseNotificationEnabled(): Boolean = enabled

    override fun setCapturedExpenseNotificationEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun hasRequestedNotificationPermission(): Boolean = requested

    override fun setHasRequestedNotificationPermission(requested: Boolean) {
        this.requested = requested
    }
}
