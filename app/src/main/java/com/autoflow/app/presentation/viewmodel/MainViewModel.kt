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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(
    private val transactionRepository: TransactionRepository
) : ViewModel() {

    val transactions: StateFlow<List<FinancialTransaction>> =
        transactionRepository.getAllActiveTransactions()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

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
            val existing = transactionRepository.getEntityById(id) ?: return@launch
            val updated = existing.copy(
                merchantName = newMerchantName.trim().ifBlank { existing.merchantName },
                category = newCategory.trim().ifBlank { existing.category },
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
