package com.autoflow.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.data.repository.TransactionRepository
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.totals.SpendingTotals
import com.autoflow.app.domain.totals.calculateSpendingTotals
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import com.autoflow.app.domain.stats.StatsPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import com.autoflow.app.data.repository.GoalRepository
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.domain.goals.GoalEntry
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.domain.goals.GoalWithTotals
import com.autoflow.app.widget.WidgetActionResolver
import com.autoflow.app.widget.WidgetDestination
import java.util.UUID

class MainViewModel(
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: com.autoflow.app.data.repository.CategoryRepository? = null,
    private val goalRepository: GoalRepository? = null,
    private val themePreferences: com.autoflow.app.presentation.theme.ThemePreferences = InMemoryThemePreferences(),
    private val firstRunPreferences: com.autoflow.app.presentation.preferences.FirstRunPreferences = com.autoflow.app.presentation.preferences.InMemoryFirstRunPreferences(initialCompleted = true),
    private val goalPreferences: com.autoflow.app.presentation.preferences.GoalPreferences = com.autoflow.app.presentation.preferences.InMemoryGoalPreferences(),
    private val timeProvider: () -> Instant = { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

    private val _widgetDestination = MutableStateFlow<WidgetDestination?>(null)
    val widgetDestination: StateFlow<WidgetDestination?> = _widgetDestination.asStateFlow()

    fun handleWidgetIntent(action: String?) {
        if (action == null) return
        viewModelScope.launch {
            val goals = goalRepository?.getAllGoals()?.filter { !it.isDeleted } ?: emptyList()
            val lastUsedGoalId = goalPreferences.getLastUsedGoalId()
            val destination = WidgetActionResolver.resolveAction(action, goals, lastUsedGoalId)
            if (destination != null) {
                _widgetDestination.value = destination
            }
        }
    }

    fun clearWidgetDestination() {
        _widgetDestination.value = null
    }

    private val _pendingEditTransactionId = MutableStateFlow<Long?>(null)
    val pendingEditTransactionId: StateFlow<Long?> = _pendingEditTransactionId.asStateFlow()

    fun openEditTransaction(txId: Long) {
        if (txId > 0) {
            _pendingEditTransactionId.value = txId
        }
    }

    fun clearPendingEditTransaction() {
        _pendingEditTransactionId.value = null
    }

    fun getLastUsedGoalId(): Long? = goalPreferences.getLastUsedGoalId()

    val themeMode: StateFlow<com.autoflow.app.presentation.theme.AppThemeMode> = themePreferences.themeMode

    fun setThemeMode(mode: com.autoflow.app.presentation.theme.AppThemeMode) {
        themePreferences.setThemeMode(mode)
    }

    private val _isFirstRunCompleted = MutableStateFlow(firstRunPreferences.isFirstRunCompleted)
    val isFirstRunCompleted: StateFlow<Boolean> = _isFirstRunCompleted.asStateFlow()

    fun setFirstRunCompleted(completed: Boolean) {
        firstRunPreferences.setFirstRunCompleted(completed)
        _isFirstRunCompleted.value = completed
    }

    private val _periodFilter = MutableStateFlow<PeriodFilter>(PeriodFilter.Predefined(StatsPeriod.THIS_MONTH))
    val periodFilter: StateFlow<PeriodFilter> = _periodFilter.asStateFlow()

    val transactions: StateFlow<List<FinancialTransaction>> =
        transactionRepository.getAllActiveTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    val homeUiState: StateFlow<HomeUiState> =
        combine(transactions, _periodFilter) { list, filter ->
            computeHomeUiState(
                transactions = list,
                periodFilter = filter,
                now = timeProvider(),
                zone = zoneId
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = computeHomeUiState(
                transactions = emptyList(),
                periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
                now = timeProvider(),
                zone = zoneId
            )
        )

    val allCategoryItems: StateFlow<List<com.autoflow.app.domain.category.CategoryItem>> =
        if (categoryRepository != null) {
            categoryRepository.getAllCategories()
                .map { list ->
                    com.autoflow.app.presentation.ui.components.CategoryThemeRegistry.updateCategories(list)
                    list
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = com.autoflow.app.domain.category.BUILT_IN_CATEGORIES.map {
                        com.autoflow.app.domain.category.CategoryItem(name = it, isBuiltIn = true)
                    }
                )
        } else {
            MutableStateFlow(com.autoflow.app.domain.category.BUILT_IN_CATEGORIES.map {
                com.autoflow.app.domain.category.CategoryItem(name = it, isBuiltIn = true)
            }).asStateFlow()
        }

    val categories: StateFlow<List<String>> =
        allCategoryItems.map { list -> list.map { it.name } }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
            )

    val customCategoriesWithCounts: StateFlow<List<CustomCategoryRowUiModel>> =
        combine(allCategoryItems, transactions) { items, txs ->
            items.filter { it.isCustom }.map { item ->
                val count = txs.count { it.category.equals(item.name, ignoreCase = true) }
                CustomCategoryRowUiModel(
                    category = item,
                    transactionCount = count
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    suspend fun addCategory(
        name: String,
        iconKey: String,
        colorKey: String
    ): com.autoflow.app.domain.category.CategoryOperationResult {
        val repo = categoryRepository ?: return com.autoflow.app.domain.category.CategoryOperationResult.CategoryNotFound
        return repo.addCategory(name = name, iconKey = iconKey, colorKey = colorKey)
    }

    suspend fun updateCategory(
        currentName: String,
        newName: String,
        iconKey: String,
        colorKey: String
    ): com.autoflow.app.domain.category.CategoryOperationResult {
        val repo = categoryRepository ?: return com.autoflow.app.domain.category.CategoryOperationResult.CategoryNotFound
        return repo.updateCategory(
            currentName = currentName,
            newName = newName,
            iconKey = iconKey,
            colorKey = colorKey
        )
    }

    suspend fun deleteCategory(name: String): com.autoflow.app.domain.category.CategoryOperationResult {
        val repo = categoryRepository ?: return com.autoflow.app.domain.category.CategoryOperationResult.CategoryNotFound
        return repo.deleteCategory(name = name)
    }

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    fun selectCategoryDetail(category: String?) {
        _selectedCategory.value = category
    }

    val categoriesUiState: StateFlow<CategoriesUiState> =
        combine(transactions, _periodFilter, _selectedCategory) { list, filter, selCat ->
            computeCategoriesUiState(
                transactions = list,
                periodFilter = filter,
                selectedCategory = selCat,
                now = timeProvider(),
                zone = zoneId
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = computeCategoriesUiState(
                transactions = emptyList(),
                periodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
                selectedCategory = null,
                now = timeProvider(),
                zone = zoneId
            )
        )

    fun selectPeriod(period: StatsPeriod) {
        _periodFilter.value = PeriodFilter.Predefined(period)
    }

    fun selectMonth(month: YearMonth) {
        val currentMonth = YearMonth.from(timeProvider().atZone(zoneId))
        if (month.isAfter(currentMonth)) {
            return // future month is non-selectable
        }
        _periodFilter.value = PeriodFilter.SpecificMonth(month)
    }

    val spendingTotals: StateFlow<SpendingTotals> =
        transactions.map { list ->
            calculateSpendingTotals(list)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SpendingTotals(0L, 0L)
        )

    suspend fun getTransaction(id: Long): FinancialTransaction? {
        return transactionRepository.getTransactionById(id)
    }

    fun editTransaction(
        id: Long,
        newMerchantName: String,
        newCategory: String,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            transactionRepository.updateTransactionCorrection(id, newMerchantName, newCategory)
            onComplete()
        }
    }

    fun restoreTransaction(
        id: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = transactionRepository.getEntityById(id) ?: return@launch
            val updated = existing.copy(
                reviewStatus = ReviewStatus.CONFIRMED,
                uncertaintyBits = 0,
                updatedAt = System.currentTimeMillis()
            )
            transactionRepository.updateTransaction(updated)
            onComplete()
        }
    }

    fun markNotAnExpense(
        id: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            val existing = transactionRepository.getEntityById(id) ?: return@launch
            val updated = existing.copy(
                reviewStatus = ReviewStatus.EXCLUDED,
                updatedAt = System.currentTimeMillis()
            )
            transactionRepository.updateTransaction(updated)
            onComplete()
        }
    }

    fun deleteTransaction(
        id: Long,
        onComplete: () -> Unit = {}
    ) {
        viewModelScope.launch {
            transactionRepository.softDelete(id)
            onComplete()
        }
    }

    fun addCashExpense(
        amountPaise: Long,
        merchantName: String,
        category: String,
        dateTime: Long = System.currentTimeMillis(),
        paymentMode: String = com.autoflow.app.data.model.PaymentMode.CASH,
        onComplete: () -> Unit = {}
    ) {
        require(amountPaise > 0) { "Amount must be strictly positive" }
        viewModelScope.launch {
            val hash = "cash_${dateTime}_${UUID.randomUUID().toString().take(8)}"
            val validMode = if (com.autoflow.app.data.model.PaymentMode.isValid(paymentMode)) {
                paymentMode
            } else {
                com.autoflow.app.data.model.PaymentMode.CASH
            }
            val entity = TransactionEntity(
                amountPaise = amountPaise,
                recordType = RecordType.EXPENSE,
                dateTime = dateTime,
                merchantName = merchantName.trim().ifBlank { "Cash Expense" },
                category = category.trim().ifBlank { "Others" },
                categorySource = "USER",
                paymentMode = validMode,
                transactionHash = hash,
                source = TransactionSource.MANUAL_CASH,
                reviewStatus = ReviewStatus.CONFIRMED,
                uncertaintyBits = 0,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                isDeleted = 0
            )
            transactionRepository.insertTransaction(entity)
            onComplete()
        }
    }

    fun editTransactionFull(
        id: Long,
        merchantName: String,
        category: String,
        dateTime: Long,
        paymentMode: String,
        note: String?,
        amountPaise: Long? = null,
        onResult: (com.autoflow.app.data.repository.UpdateTransactionResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val result = transactionRepository.updateTransaction(
                id = id,
                merchantName = merchantName,
                category = category,
                dateTime = dateTime,
                paymentMode = paymentMode,
                note = note,
                amountPaise = amountPaise
            )
            onResult(result)
        }
    }

    val unreviewedCount: StateFlow<Int> =
        transactionRepository.getUnreviewedCount()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = 0
            )

    val reviewTransactions: StateFlow<List<FinancialTransaction>> =
        transactionRepository.getTransactionsByReviewStatus(ReviewStatus.NEEDS_REVIEW)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

    fun confirmCategory(id: Long, category: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val existing = transactionRepository.getEntityById(id) ?: return@launch
            transactionRepository.updateTransaction(
                id = id,
                merchantName = existing.merchantName,
                category = category,
                dateTime = existing.dateTime,
                paymentMode = existing.paymentMode,
                note = existing.note
            )
            onComplete()
        }
    }

    fun onLater(id: Long) {
        // Leaves it pending and moves on. Do not delete it.
    }

    val goalsWithTotals: StateFlow<List<GoalWithTotals>> =
        goalRepository?.observeGoalsWithTotals()
            ?.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            ) ?: MutableStateFlow(emptyList())

    val goalsUiState: StateFlow<GoalsUiState> =
        goalsWithTotals.map { list ->
            GoalsUiState(goals = list, isLoading = false)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = GoalsUiState()
        )

    fun observeEntries(goalId: Long): kotlinx.coroutines.flow.Flow<List<GoalEntry>> {
        return goalRepository?.observeEntries(goalId) ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }

    suspend fun getGoalById(id: Long): Goal? {
        return goalRepository?.getGoalById(id)
    }

    suspend fun createGoal(
        name: String,
        targetPaise: Long,
        deadlineAt: Long?
    ): GoalResult<Goal> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.BlankName)
        return repo.createGoal(name = name, targetPaise = targetPaise, deadlineAt = deadlineAt)
    }

    suspend fun updateGoal(
        id: Long,
        name: String,
        targetPaise: Long,
        deadlineAt: Long?
    ): GoalResult<Goal> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.GoalNotFound(id))
        return repo.updateGoal(id = id, name = name, targetPaise = targetPaise, deadlineAt = deadlineAt)
    }

    suspend fun deleteGoal(id: Long): GoalResult<Unit> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.GoalNotFound(id))
        return repo.deleteGoal(id)
    }

    suspend fun undeleteGoal(id: Long): GoalResult<Unit> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.GoalNotFound(id))
        return repo.undeleteGoal(id)
    }

    suspend fun addSaved(
        goalId: Long,
        amountPaise: Long,
        note: String
    ): GoalResult<GoalEntry> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        val result = repo.addSaved(goalId = goalId, amountPaise = amountPaise, note = note)
        if (result is GoalResult.Success) {
            goalPreferences.setLastUsedGoalId(goalId)
        }
        return result
    }

    suspend fun editEntry(
        id: Long,
        amountPaise: Long,
        note: String
    ): GoalResult<GoalEntry> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.EntryNotFound(id))
        return repo.editEntry(id = id, amountPaise = amountPaise, note = note)
    }

    suspend fun deleteEntry(id: Long): GoalResult<Unit> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.EntryNotFound(id))
        return repo.deleteEntry(id)
    }

    suspend fun undeleteEntry(id: Long): GoalResult<Unit> {
        val repo = goalRepository ?: return GoalResult.Error(GoalError.EntryNotFound(id))
        return repo.undeleteEntry(id)
    }

    suspend fun getEntryById(id: Long): GoalEntry? {
        return goalRepository?.getEntryById(id)
    }
}
