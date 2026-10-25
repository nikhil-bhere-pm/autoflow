package com.autoflow.app.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.presentation.ui.firstrun.PermissionPromptResult
import com.autoflow.app.presentation.ui.firstrun.resolveNextScreenOnPermissionResult
import com.autoflow.app.presentation.ui.goals.AddSavedScreen
import com.autoflow.app.presentation.ui.goals.EditEntryScreen
import com.autoflow.app.presentation.ui.goals.EditGoalScreen
import com.autoflow.app.presentation.ui.goals.GoalDetailScreen
import com.autoflow.app.presentation.ui.goals.GoalsTabScreen
import com.autoflow.app.presentation.ui.goals.NewGoalScreen
import com.autoflow.app.presentation.viewmodel.MainViewModel
import kotlinx.coroutines.launch

sealed class Screen {
    data object FirstRun : Screen()
    data class PermissionHelp(val fromSettings: Boolean = false) : Screen()
    data object Home : Screen()
    data object Goals : Screen()
    data class GoalDetail(val goalId: Long) : Screen()
    data class AddSaved(
        val goalId: Long,
        val fromWidget: Boolean = false,
        val availableGoals: List<com.autoflow.app.domain.goals.Goal> = emptyList(),
        val showGoalChips: Boolean = false
    ) : Screen()
    data class NewGoal(
        val fromWidget: Boolean = false,
        val hintMessage: String? = null
    ) : Screen()
    data class EditGoal(val goalId: Long) : Screen()
    data class EditGoalEntry(val goalId: Long, val entryId: Long) : Screen()
    data object Review : Screen()
    data object Categories : Screen()
    data class Edit(
        val transactionId: Long,
        val fromReview: Boolean = false,
        val fromCategories: Boolean = false,
        val fromSearch: Boolean = false
    ) : Screen()
    data object Search : Screen()
    data object AddCash : Screen()
    data class QuickExpense(val fromWidget: Boolean = false) : Screen()
    data object Settings : Screen()
    data object MyCategories : Screen()
    data object AddCategory : Screen()
    data class EditCategory(val categoryName: String) : Screen()
    data object Autopay : Screen()
}

@Composable
fun AutoFlowApp(
    viewModel: MainViewModel,
    onFinishActivity: () -> Unit = {}
) {
    val isFirstRunCompleted by viewModel.isFirstRunCompleted.collectAsState()

    var currentScreen by remember(isFirstRunCompleted) {
        mutableStateOf<Screen>(
            if (!isFirstRunCompleted) Screen.FirstRun else Screen.Home
        )
    }

    val homeUiState by viewModel.homeUiState.collectAsState()
    val categoriesUiState by viewModel.categoriesUiState.collectAsState()
    val reviewTransactions by viewModel.reviewTransactions.collectAsState()
    val unreviewedCount by viewModel.unreviewedCount.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val allCategoryItems by viewModel.allCategoryItems.collectAsState()
    val customCategoriesWithCounts by viewModel.customCategoriesWithCounts.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val goalsUiState by viewModel.goalsUiState.collectAsState()
    val goalsWithTotals by viewModel.goalsWithTotals.collectAsState()
    val widgetDestination by viewModel.widgetDestination.collectAsState()
    val pendingEditTxId by viewModel.pendingEditTransactionId.collectAsState()
    val resetToHomeEvent by viewModel.resetToHomeEvent.collectAsState()

    androidx.compose.runtime.LaunchedEffect(resetToHomeEvent) {
        if (resetToHomeEvent > 0 && isFirstRunCompleted) {
            currentScreen = Screen.Home
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingEditTxId) {
        val id = pendingEditTxId
        if (id != null) {
            currentScreen = Screen.Edit(id, fromReview = false)
            viewModel.clearPendingEditTransaction()
        }
    }

    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val context = androidx.compose.ui.platform.LocalContext.current
        val notificationPrefs = androidx.compose.runtime.remember {
            com.autoflow.app.presentation.preferences.SharedPreferencesNotificationPreferences.fromContext(context)
        }
        val postNotifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
            contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { _ -> }

        androidx.compose.runtime.LaunchedEffect(currentScreen) {
            if (currentScreen is Screen.Home && !notificationPrefs.hasRequestedNotificationPermission()) {
                notificationPrefs.setHasRequestedNotificationPermission(true)
                if (androidx.core.content.ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    postNotifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(widgetDestination) {
        when (val dest = widgetDestination) {
            is com.autoflow.app.widget.WidgetDestination.QuickExpense -> {
                currentScreen = Screen.QuickExpense(fromWidget = true)
                viewModel.clearWidgetDestination()
            }
            is com.autoflow.app.widget.WidgetDestination.Saved -> {
                currentScreen = Screen.AddSaved(
                    goalId = dest.selectedGoalId,
                    fromWidget = true,
                    availableGoals = dest.goals,
                    showGoalChips = dest.goals.size > 1
                )
                viewModel.clearWidgetDestination()
            }
            is com.autoflow.app.widget.WidgetDestination.NewGoalWithHint -> {
                currentScreen = Screen.NewGoal(
                    fromWidget = true,
                    hintMessage = "Create a goal first, then tap Saved"
                )
                viewModel.clearWidgetDestination()
            }
            null -> {}
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val goalsSnackbarHostState = remember { SnackbarHostState() }
    val detailSnackbarHostState = remember { SnackbarHostState() }
    val appSnackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = currentScreen != Screen.Home && currentScreen != Screen.FirstRun) {
        currentScreen = when (val s = currentScreen) {
            is Screen.FirstRun -> Screen.FirstRun
            is Screen.PermissionHelp -> {
                if (s.fromSettings) Screen.Settings else Screen.Home
            }
            is Screen.Edit -> {
                when {
                    s.fromReview -> Screen.Review
                    s.fromCategories -> Screen.Categories
                    s.fromSearch -> Screen.Search
                    else -> Screen.Home
                }
            }
            is Screen.Search -> Screen.Home
            is Screen.AddCash -> Screen.Home
            is Screen.QuickExpense -> {
                if (s.fromWidget) {
                    onFinishActivity()
                    Screen.Home
                } else {
                    Screen.Home
                }
            }
            is Screen.Goals -> Screen.Home
            is Screen.GoalDetail -> Screen.Goals
            is Screen.AddSaved -> {
                if (s.fromWidget) {
                    onFinishActivity()
                    Screen.Home
                } else {
                    Screen.GoalDetail(s.goalId)
                }
            }
            is Screen.NewGoal -> {
                if (s.fromWidget) {
                    onFinishActivity()
                    Screen.Home
                } else {
                    Screen.Goals
                }
            }
            is Screen.EditGoal -> Screen.GoalDetail(s.goalId)
            is Screen.EditGoalEntry -> Screen.GoalDetail(s.goalId)
            is Screen.Review -> Screen.Home
            is Screen.Categories -> Screen.Home
            is Screen.Settings -> Screen.Home
            is Screen.MyCategories -> Screen.Settings
            is Screen.AddCategory -> Screen.MyCategories
            is Screen.EditCategory -> Screen.MyCategories
            is Screen.Autopay -> Screen.Categories
            Screen.Home -> Screen.Home
        }
    }

    when (val screen = currentScreen) {
        is Screen.FirstRun -> {
            FirstRunScreen(
                onPermissionResult = { result ->
                    val next = resolveNextScreenOnPermissionResult(result, fromSettings = false)
                    if (result == PermissionPromptResult.Granted || result == PermissionPromptResult.Denied) {
                        viewModel.setFirstRunCompleted(true)
                    }
                    currentScreen = next
                },
                onNotNow = {
                    viewModel.setFirstRunCompleted(true)
                    currentScreen = Screen.Home
                }
            )
        }

        is Screen.PermissionHelp -> {
            PermissionHelpScreen(
                fromSettings = screen.fromSettings,
                onPermissionGranted = {
                    if (!screen.fromSettings) {
                        viewModel.setFirstRunCompleted(true)
                        currentScreen = Screen.Home
                    } else {
                        currentScreen = Screen.Settings
                    }
                },
                onNavigateBack = {
                    if (!screen.fromSettings) {
                        viewModel.setFirstRunCompleted(true)
                        currentScreen = Screen.Home
                    } else {
                        currentScreen = Screen.Settings
                    }
                }
            )
        }

        is Screen.Home -> {
            HomeScreen(
                uiState = homeUiState,
                onSelectPeriod = { period -> viewModel.selectPeriod(period) },
                onSelectMonth = { month -> viewModel.selectMonth(month) },
                onNavigateToEdit = { id -> currentScreen = Screen.Edit(id, fromReview = false) },
                onNavigateToAddCash = { currentScreen = Screen.AddCash },
                onNavigateToGoals = { currentScreen = Screen.Goals },
                onNavigateToReview = { currentScreen = Screen.Review },
                onNavigateToCategories = { currentScreen = Screen.Categories },
                onNavigateToSettings = { currentScreen = Screen.Settings },
                onNavigateToSearch = { currentScreen = Screen.Search },
                snackbarHostState = appSnackbarHostState
            )
        }

        is Screen.Goals -> {
            GoalsTabScreen(
                uiState = goalsUiState,
                unreviewedCount = unreviewedCount,
                snackbarHostState = goalsSnackbarHostState,
                onNavigateToNewGoal = { currentScreen = Screen.NewGoal() },
                onNavigateToGoalDetail = { id -> currentScreen = Screen.GoalDetail(id) },
                onNavigateToHome = { currentScreen = Screen.Home },
                onNavigateToAddCash = { currentScreen = Screen.AddCash },
                onNavigateToReview = { currentScreen = Screen.Review },
                onNavigateToCategories = { currentScreen = Screen.Categories }
            )
        }

        is Screen.GoalDetail -> {
            val goal = goalsWithTotals.find { it.id == screen.goalId }
            val entries by viewModel.observeEntries(screen.goalId).collectAsState(initial = emptyList())
            GoalDetailScreen(
                goalWithTotals = goal,
                entries = entries,
                snackbarHostState = detailSnackbarHostState,
                onNavigateBack = { currentScreen = Screen.Goals },
                onNavigateToEditGoal = { id -> currentScreen = Screen.EditGoal(id) },
                onDeleteGoal = { id ->
                    val goalName = goal?.name ?: "Goal"
                    coroutineScope.launch {
                        viewModel.deleteGoal(id)
                        currentScreen = Screen.Goals
                        val result = goalsSnackbarHostState.showSnackbar(
                            message = "$goalName deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            val undeleteResult = viewModel.undeleteGoal(id)
                            if (undeleteResult is GoalResult.Error && undeleteResult.error is GoalError.NameTaken) {
                                goalsSnackbarHostState.showSnackbar(
                                    message = "Can't undo: that name is now used by another goal",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                },
                onNavigateToAddSaved = { id -> currentScreen = Screen.AddSaved(id) },
                onNavigateToEditEntry = { gId, eId -> currentScreen = Screen.EditGoalEntry(gId, eId) }
            )
        }

        is Screen.AddSaved -> {
            AddSavedScreen(
                goalId = screen.goalId,
                onSave = { amountPaise, note ->
                    coroutineScope.launch {
                        viewModel.addSaved(screen.goalId, amountPaise, note)
                        if (screen.fromWidget) {
                            onFinishActivity()
                        } else {
                            currentScreen = Screen.GoalDetail(screen.goalId)
                        }
                    }
                },
                onSaveWithGoal = { targetGoalId, amountPaise, note ->
                    coroutineScope.launch {
                        viewModel.addSaved(targetGoalId, amountPaise, note)
                        if (screen.fromWidget) {
                            onFinishActivity()
                        } else {
                            currentScreen = Screen.GoalDetail(targetGoalId)
                        }
                    }
                },
                onNavigateBack = {
                    if (screen.fromWidget) {
                        onFinishActivity()
                    } else {
                        currentScreen = Screen.GoalDetail(screen.goalId)
                    }
                },
                availableGoals = screen.availableGoals,
                showGoalChips = screen.showGoalChips
            )
        }

        is Screen.NewGoal -> {
            NewGoalScreen(
                onCreateGoal = { name, targetPaise, deadlineAt ->
                    viewModel.createGoal(name, targetPaise, deadlineAt)
                },
                onGoalCreated = {
                    if (!screen.fromWidget) {
                        currentScreen = Screen.Goals
                    }
                },
                onGoalCreatedWithResult = { newGoal ->
                    if (screen.fromWidget) {
                        currentScreen = Screen.AddSaved(
                            goalId = newGoal.id,
                            fromWidget = true,
                            availableGoals = listOf(newGoal),
                            showGoalChips = true
                        )
                    }
                },
                onNavigateBack = {
                    if (screen.fromWidget) {
                        onFinishActivity()
                    } else {
                        currentScreen = Screen.Goals
                    }
                },
                hintMessage = screen.hintMessage
            )
        }

        is Screen.EditGoal -> {
            val targetGoal = goalsWithTotals.find { it.id == screen.goalId }?.goal
            EditGoalScreen(
                goal = targetGoal,
                onSaveGoal = { id, name, targetPaise, deadlineAt ->
                    viewModel.updateGoal(id, name, targetPaise, deadlineAt)
                },
                onGoalSaved = {
                    currentScreen = Screen.GoalDetail(screen.goalId)
                },
                onDeleteGoal = { id ->
                    val goalName = targetGoal?.name ?: "Goal"
                    coroutineScope.launch {
                        viewModel.deleteGoal(id)
                        currentScreen = Screen.Goals
                        val result = goalsSnackbarHostState.showSnackbar(
                            message = "$goalName deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            val undeleteResult = viewModel.undeleteGoal(id)
                            if (undeleteResult is GoalResult.Error && undeleteResult.error is GoalError.NameTaken) {
                                goalsSnackbarHostState.showSnackbar(
                                    message = "Can't undo: that name is now used by another goal",
                                    duration = SnackbarDuration.Short
                                )
                            }
                        }
                    }
                },
                onNavigateBack = { currentScreen = Screen.GoalDetail(screen.goalId) }
            )
        }

        is Screen.EditGoalEntry -> {
            val entries by viewModel.observeEntries(screen.goalId).collectAsState(initial = emptyList())
            val targetEntry = entries.find { it.id == screen.entryId }
            EditEntryScreen(
                entry = targetEntry,
                onSaveEntry = { id, amountPaise, note ->
                    viewModel.editEntry(id, amountPaise, note)
                },
                onEntrySaved = {
                    currentScreen = Screen.GoalDetail(screen.goalId)
                },
                onDeleteEntry = { id ->
                    coroutineScope.launch {
                        viewModel.deleteEntry(id)
                        currentScreen = Screen.GoalDetail(screen.goalId)
                        val result = detailSnackbarHostState.showSnackbar(
                            message = "Entry deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undeleteEntry(id)
                        }
                    }
                },
                onNavigateBack = { currentScreen = Screen.GoalDetail(screen.goalId) }
            )
        }

        is Screen.Review -> {
            ReviewScreen(
                transactions = reviewTransactions,
                categories = categories,
                onNavigateToHome = { currentScreen = Screen.Home },
                onNavigateToGoals = { currentScreen = Screen.Goals },
                onNavigateToCategories = { currentScreen = Screen.Categories },
                onNavigateToAddCash = { currentScreen = Screen.AddCash },
                onNavigateToEdit = { id -> currentScreen = Screen.Edit(id, fromReview = true) },
                onConfirmCategory = { id, category -> viewModel.confirmCategory(id, category) },
                onLater = { id -> viewModel.onLater(id) },
                snackbarHostState = appSnackbarHostState
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
                onNavigateToGoals = { currentScreen = Screen.Goals },
                onNavigateToReview = { currentScreen = Screen.Review },
                onNavigateToAddCash = { currentScreen = Screen.AddCash },
                onNavigateToEdit = { id -> currentScreen = Screen.Edit(id, fromCategories = true) },
                onNavigateToAutopay = { currentScreen = Screen.Autopay },
                snackbarHostState = appSnackbarHostState
            )
        }

        is Screen.Autopay -> {
            AutopayScreen(
                onNavigateBack = { currentScreen = Screen.Categories }
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
                    coroutineScope.launch {
                        viewModel.deleteTransaction(id)
                        val result = appSnackbarHostState.showSnackbar(
                            message = "Expense deleted",
                            actionLabel = "Undo",
                            duration = SnackbarDuration.Short
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            viewModel.undeleteTransaction(id)
                        }
                    }
                },
                onNavigateBack = {
                    currentScreen = when {
                        screen.fromReview -> Screen.Review
                        screen.fromCategories -> Screen.Categories
                        screen.fromSearch -> Screen.Search
                        else -> Screen.Home
                    }
                },
                categories = categories
            )
        }

        is Screen.Search -> {
            val searchQuery by viewModel.searchQuery.collectAsState()
            val searchResults by viewModel.searchResults.collectAsState()
            SearchScreen(
                query = searchQuery,
                results = searchResults,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onNavigateBack = { currentScreen = Screen.Home },
                onNavigateToEdit = { id ->
                    currentScreen = Screen.Edit(id, fromSearch = true)
                }
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

        is Screen.QuickExpense -> {
            QuickExpenseScreen(
                onSave = { amountPaise, merchant, category ->
                    viewModel.addCashExpense(amountPaise, merchant, category)
                    if (screen.fromWidget) {
                        onFinishActivity()
                    } else {
                        currentScreen = Screen.Home
                    }
                },
                onNavigateBack = {
                    if (screen.fromWidget) {
                        onFinishActivity()
                    } else {
                        currentScreen = Screen.Home
                    }
                },
                categories = categories
            )
        }

        is Screen.Settings -> {
            SettingsScreen(
                currentThemeMode = themeMode,
                onSelectThemeMode = { mode -> viewModel.setThemeMode(mode) },
                onNavigateToMyCategories = { currentScreen = Screen.MyCategories },
                onNavigateToPermissionHelp = {
                    currentScreen = Screen.PermissionHelp(fromSettings = true)
                },
                onNavigateBack = { currentScreen = Screen.Home },
                onResetFirstRun = {
                    viewModel.setFirstRunCompleted(false)
                }
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
