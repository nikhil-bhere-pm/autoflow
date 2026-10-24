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
    data object Review : Screen()
    data object Categories : Screen()
    data class Edit(
        val transactionId: Long,
        val fromReview: Boolean = false,
        val fromCategories: Boolean = false
    ) : Screen()
    data object AddCash : Screen()
    data object Settings : Screen()
    data object MyCategories : Screen()
    data object AddCategory : Screen()
    data class EditCategory(val categoryName: String) : Screen()
}

@Composable
fun AutoFlowApp(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }

    val homeUiState by viewModel.homeUiState.collectAsState()
    val categoriesUiState by viewModel.categoriesUiState.collectAsState()
    val reviewTransactions by viewModel.reviewTransactions.collectAsState()
    val unreviewedCount by viewModel.unreviewedCount.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allCategoryItems by viewModel.allCategoryItems.collectAsState()
    val customCategoriesWithCounts by viewModel.customCategoriesWithCounts.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    BackHandler(enabled = currentScreen != Screen.Home) {
        currentScreen = when (val s = currentScreen) {
            is Screen.Edit -> {
                when {
                    s.fromReview -> Screen.Review
                    s.fromCategories -> Screen.Categories
                    else -> Screen.Home
                }
            }
            is Screen.AddCash -> Screen.Home
            is Screen.Review -> Screen.Home
            is Screen.Categories -> Screen.Home
            is Screen.Settings -> Screen.Home
            is Screen.MyCategories -> Screen.Settings
            is Screen.AddCategory -> Screen.MyCategories
            is Screen.EditCategory -> Screen.MyCategories
            Screen.Home -> Screen.Home
        }
    }

    when (val screen = currentScreen) {
        is Screen.Home -> {
            HomeScreen(
                uiState = homeUiState,
                onSelectPeriod = { period -> viewModel.selectPeriod(period) },
                onSelectMonth = { month -> viewModel.selectMonth(month) },
                onNavigateToEdit = { id -> currentScreen = Screen.Edit(id, fromReview = false) },
                onNavigateToAddCash = { currentScreen = Screen.AddCash },
                onNavigateToReview = { currentScreen = Screen.Review },
                onNavigateToCategories = { currentScreen = Screen.Categories },
                onNavigateToSettings = { currentScreen = Screen.Settings }
            )
        }

        is Screen.Review -> {
            ReviewScreen(
                transactions = reviewTransactions,
                categories = categories,
                onNavigateToHome = { currentScreen = Screen.Home },
                onNavigateToCategories = { currentScreen = Screen.Categories },
                onNavigateToAddCash = { currentScreen = Screen.AddCash },
                onNavigateToEdit = { id -> currentScreen = Screen.Edit(id, fromReview = true) },
                onConfirmCategory = { id, category -> viewModel.confirmCategory(id, category) },
                onLater = { id -> viewModel.onLater(id) }
            )
        }

        is Screen.Categories -> {
            CategoriesScreen(
                uiState = categoriesUiState,
                unreviewedCount = unreviewedCount,
                onSelectPeriod = { period -> viewModel.selectPeriod(period) },
                onSelectMonth = { month -> viewModel.selectMonth(month) },
                onSelectCategory = { cat -> viewModel.selectCategoryDetail(cat) },
                onNavigateToHome = { currentScreen = Screen.Home },
                onNavigateToReview = { currentScreen = Screen.Review },
                onNavigateToAddCash = { currentScreen = Screen.AddCash },
                onNavigateToEdit = { id -> currentScreen = Screen.Edit(id, fromCategories = true) }
            )
        }

        is Screen.Edit -> {
            EditTransactionScreen(
                transactionId = screen.transactionId,
                loadTransaction = { id -> viewModel.getTransaction(id) },
                onSave = { id, merchant, category, dateTime, paymentMode, note, amountPaise, onResult ->
                    viewModel.editTransactionFull(id, merchant, category, dateTime, paymentMode, note, amountPaise, onResult)
                },
                onMarkNotAnExpense = { id ->
                    viewModel.markNotAnExpense(id)
                },
                onRestore = { id ->
                    viewModel.restoreTransaction(id)
                },
                onDelete = { id ->
                    viewModel.deleteTransaction(id)
                },
                onNavigateBack = {
                    currentScreen = when {
                        screen.fromReview -> Screen.Review
                        screen.fromCategories -> Screen.Categories
                        else -> Screen.Home
                    }
                },
                categories = categories
            )
        }

        is Screen.AddCash -> {
            AddCashExpenseScreen(
                onSave = { amountPaise, merchant, category, dateTime, paymentMode ->
                    viewModel.addCashExpense(amountPaise, merchant, category, dateTime, paymentMode)
                },
                onNavigateBack = {
                    currentScreen = Screen.Home
                },
                categories = categories
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                currentThemeMode = themeMode,
                onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
                onNavigateToMyCategories = { currentScreen = Screen.MyCategories },
                onNavigateBack = { currentScreen = Screen.Home }
            )
        }

        is Screen.MyCategories -> {
            MyCategoriesScreen(
                customCategories = customCategoriesWithCounts,
                onAddCategory = { currentScreen = Screen.AddCategory },
                onEditCategory = { name -> currentScreen = Screen.EditCategory(name) },
                onNavigateBack = { currentScreen = Screen.Settings }
            )
        }

        is Screen.AddCategory -> {
            AddEditCategoryScreen(
                initialCategory = null,
                onSave = { name, iconKey, colorKey ->
                    viewModel.addCategory(name, iconKey, colorKey)
                },
                onDelete = null,
                onNavigateBack = { currentScreen = Screen.MyCategories }
            )
        }

        is Screen.EditCategory -> {
            val targetCategory = allCategoryItems.find { it.name.equals(screen.categoryName, ignoreCase = true) }
            AddEditCategoryScreen(
                initialCategory = targetCategory,
                onSave = { newName, iconKey, colorKey ->
                    viewModel.updateCategory(
                        currentName = screen.categoryName,
                        newName = newName,
                        iconKey = iconKey,
                        colorKey = colorKey
                    )
                },
                onDelete = { name ->
                    viewModel.deleteCategory(name)
                },
                onNavigateBack = { currentScreen = Screen.MyCategories }
            )
        }
    }
}
