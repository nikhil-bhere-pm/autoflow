package com.autoflow.app.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.autoflow.app.presentation.viewmodel.MainViewModel

sealed class Screen {
    data object Home : Screen()
    data class Edit(val transactionId: Long) : Screen()
    data object AddCash : Screen()
}

@Composable
fun AutoFlowApp(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    val homeUiState by viewModel.homeUiState.collectAsState()

    BackHandler(enabled = currentScreen != Screen.Home) {
        currentScreen = Screen.Home
    }

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                uiState = homeUiState,
                onSelectPeriod = { period -> viewModel.selectPeriod(period) },
                onSelectMonth = { month -> viewModel.selectMonth(month) },
                onNavigateToEdit = { id -> currentScreen = Screen.Edit(id) },
                onNavigateToAddCash = { currentScreen = Screen.AddCash }
            )
        }

        is Screen.Edit -> {
            EditTransactionScreen(
                transactionId = screen.transactionId,
                loadTransaction = { id -> viewModel.getTransaction(id) },
                onSaveCorrection = { id, newMerchant, newCategory ->
                    viewModel.editTransaction(id, newMerchant, newCategory)
                },
                onMarkNotAnExpense = { id ->
                    viewModel.markNotAnExpense(id)
                },
                onDelete = { id ->
                    viewModel.deleteTransaction(id)
                },
                onNavigateBack = {
                    currentScreen = Screen.Home
                }
            )
        }

        is Screen.AddCash -> {
            AddCashExpenseScreen(
                onSave = { amountPaise, merchant, category, dateTime ->
                    viewModel.addCashExpense(amountPaise, merchant, category, dateTime)
                },
                onNavigateBack = {
                    currentScreen = Screen.Home
                }
            )
        }
    }
}
