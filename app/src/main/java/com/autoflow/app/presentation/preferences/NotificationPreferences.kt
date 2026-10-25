package com.autoflow.app.presentation.preferences

import android.content.Context
import android.content.SharedPreferences

interface NotificationPreferences {
    fun isCapturedExpenseNotificationEnabled(): Boolean
    fun setCapturedExpenseNotificationEnabled(enabled: Boolean)
    fun hasRequestedNotificationPermission(): Boolean
    fun setHasRequestedNotificationPermission(requested: Boolean)

    fun isDailyRecapEnabled(): Boolean
    fun setDailyRecapEnabled(enabled: Boolean)
    fun getDailyRecapTimeHour(): Int
    fun getDailyRecapTimeMinute(): Int
    fun setDailyRecapTime(hour: Int, minute: Int)
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

    override fun isDailyRecapEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DAILY_RECAP_ENABLED, true)
    }

    override fun setDailyRecapEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DAILY_RECAP_ENABLED, enabled).apply()
    }

    override fun getDailyRecapTimeHour(): Int {
        return sharedPreferences.getInt(KEY_DAILY_RECAP_HOUR, 21)
    }

    override fun getDailyRecapTimeMinute(): Int {
        return sharedPreferences.getInt(KEY_DAILY_RECAP_MINUTE, 0)
    }

    override fun setDailyRecapTime(hour: Int, minute: Int) {
        sharedPreferences.edit()
            .putInt(KEY_DAILY_RECAP_HOUR, hour)
            .putInt(KEY_DAILY_RECAP_MINUTE, minute)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "autoflow_notification_prefs"
        const val KEY_NOTIFY_CAPTURED_EXPENSE = "notify_captured_expense"
        const val KEY_HAS_REQUESTED_PERMISSION = "has_requested_notification_permission"
        const val KEY_DAILY_RECAP_ENABLED = "daily_recap_enabled"
        const val KEY_DAILY_RECAP_HOUR = "daily_recap_hour"
        const val KEY_DAILY_RECAP_MINUTE = "daily_recap_minute"

        fun fromContext(context: Context): SharedPreferencesNotificationPreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SharedPreferencesNotificationPreferences(prefs)
        }
    }
}

class InMemoryNotificationPreferences(
    initialEnabled: Boolean = true,
    initialRequested: Boolean = false,
    initialDailyRecapEnabled: Boolean = true,
    initialDailyRecapHour: Int = 21,
    initialDailyRecapMinute: Int = 0
) : NotificationPreferences {
    private var enabled: Boolean = initialEnabled
    private var requested: Boolean = initialRequested
    private var dailyRecapEnabled: Boolean = initialDailyRecapEnabled
    private var dailyRecapHour: Int = initialDailyRecapHour
    private var dailyRecapMinute: Int = initialDailyRecapMinute

    override fun isCapturedExpenseNotificationEnabled(): Boolean = enabled

    override fun setCapturedExpenseNotificationEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun hasRequestedNotificationPermission(): Boolean = requested

    override fun setHasRequestedNotificationPermission(requested: Boolean) {
        this.requested = requested
    }

    override fun isDailyRecapEnabled(): Boolean = dailyRecapEnabled

    override fun setDailyRecapEnabled(enabled: Boolean) {
        this.dailyRecapEnabled = enabled
    }

    override fun getDailyRecapTimeHour(): Int = dailyRecapHour

    override fun getDailyRecapTimeMinute(): Int = dailyRecapMinute

    override fun setDailyRecapTime(hour: Int, minute: Int) {
        this.dailyRecapHour = hour
        this.dailyRecapMinute = minute
    }
}
