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
import java.util.UUID

class MainViewModel(
    private val transactionRepository: TransactionRepository,
    private val timeProvider: () -> Instant = { Instant.now() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : ViewModel() {

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
        onComplete: () -> Unit = {}
    ) {
        require(amountPaise > 0) { "Amount must be strictly positive" }
        viewModelScope.launch {
            val hash = "cash_${dateTime}_${UUID.randomUUID().toString().take(8)}"
            val entity = TransactionEntity(
                amountPaise = amountPaise,
                recordType = RecordType.EXPENSE,
                dateTime = dateTime,
                merchantName = merchantName.trim().ifBlank { "Cash Expense" },
                category = category.trim().ifBlank { "Others" },
                categorySource = "USER",
                paymentMode = com.autoflow.app.data.model.PaymentMode.CASH,
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
}
