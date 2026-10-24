package com.autoflow.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.autoflow.app.presentation.preferences.SharedPreferencesFirstRunPreferences
import com.autoflow.app.presentation.theme.AppThemeMode
import com.autoflow.app.presentation.theme.AutoFlowTheme
import com.autoflow.app.presentation.theme.SharedPreferencesThemePreferences
import com.autoflow.app.presentation.ui.AutoFlowApp
import com.autoflow.app.presentation.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val app = application as AutoFlowApplication
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(
                    transactionRepository = app.transactionRepository,
                    categoryRepository = app.categoryRepository,
                    themePreferences = SharedPreferencesThemePreferences.fromContext(app),
                    firstRunPreferences = SharedPreferencesFirstRunPreferences.fromContext(app)
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleDebugIntent(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val isDark = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            AutoFlowTheme(darkTheme = isDark) {
                AutoFlowApp(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        val isDebuggable = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (isDebuggable && intent != null) {
            if (intent.getBooleanExtra("reset_first_run", false)) {
                viewModel.setFirstRunCompleted(false)
            }
        }
    }
}
