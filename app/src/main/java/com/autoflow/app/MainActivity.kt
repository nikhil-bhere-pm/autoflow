package com.autoflow.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.autoflow.app.presentation.theme.AutoFlowTheme
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
                    categoryRepository = app.categoryRepository
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AutoFlowTheme {
                AutoFlowApp(viewModel = viewModel)
            }
        }
    }
}
