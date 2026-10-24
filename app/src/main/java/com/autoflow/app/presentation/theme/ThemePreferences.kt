package com.autoflow.app.presentation.theme

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

interface ThemePreferences {
    val themeMode: StateFlow<AppThemeMode>
    fun setThemeMode(mode: AppThemeMode)
}

class SharedPreferencesThemePreferences(
    private val sharedPreferences: SharedPreferences
) : ThemePreferences {

    private val _themeMode = MutableStateFlow(readTheme())
    override val themeMode: StateFlow<AppThemeMode> = _themeMode.asStateFlow()

    private fun readTheme(): AppThemeMode {
        val raw = sharedPreferences.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(raw ?: AppThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    override fun setThemeMode(mode: AppThemeMode) {
        sharedPreferences.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    companion object {
        const val PREFS_NAME = "autoflow_settings"
        private const val KEY_THEME_MODE = "app_theme_mode"

        fun fromContext(context: Context): SharedPreferencesThemePreferences {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return SharedPreferencesThemePreferences(prefs)
        }
    }
}
