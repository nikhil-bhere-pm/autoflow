package com.autoflow.app.presentation.viewmodel

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.PeriodRange
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.domain.stats.periodRange
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

data class CategorySpend(
    val category: String,
    val totalPaise: Long,
    val percent: Int,
    val transactionCount: Int
)

data class CategoriesUiState(
    val periodFilter: PeriodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
    val pillLabel: String = "This month",
    val totalSpendPaise: Long = 0L,
    val categories: List<CategorySpend> = emptyList(),
    val topCategory: CategorySpend? = null,
    val selectedCategory: String? = null,
    val categoryTransactions: List<FinancialTransaction> = emptyList(),
    val isEmpty: Boolean = true
)

fun computeCategoriesUiState(
    transactions: List<FinancialTransaction>,
    periodFilter: PeriodFilter,
    selectedCategory: String? = null,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): CategoriesUiState {
    val range: PeriodRange = when (periodFilter) {
        is PeriodFilter.Predefined -> periodRange(periodFilter.period, now, zone)
        is PeriodFilter.SpecificMonth -> {
            val start = periodFilter.month.atDay(1).atStartOfDay(zone).toInstant()
            val end = periodFilter.month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant()
            PeriodRange(start, end)
        }
    }

    val pillLabel = when (periodFilter) {
        is PeriodFilter.Predefined -> when (periodFilter.period) {
            StatsPeriod.TODAY -> "Today"
            StatsPeriod.THIS_WEEK -> "This week"
            StatsPeriod.THIS_MONTH -> "This month"
        }
        is PeriodFilter.SpecificMonth -> {
            val mName = periodFilter.month.month.name.take(3).lowercase(Locale.ROOT)
                .replaceFirstChar { it.uppercase(Locale.ROOT) }
            "$mName ${periodFilter.month.year}"
        }
    }

    // Active transactions in range that are not excluded from spending
    val periodTransactions = transactions.filter {
        !it.isDeleted && it.dateTime in range && it.reviewStatus != ReviewStatus.EXCLUDED
    }

    // Group by category to compute net spend (expenses minus refunds, coerced to >= 0)
    val grouped = periodTransactions.groupBy { it.category.trim() }
    val categorySpends = mutableListOf<CategorySpend>()

    for ((categoryName, catTxs) in grouped) {
        if (categoryName.isBlank()) continue

        var expensePaise = 0L
        var refundPaise = 0L
        var expenseCount = 0

        for (tx in catTxs) {
            when (tx.recordType) {
                RecordType.EXPENSE -> {
                    expensePaise = Math.addExact(expensePaise, tx.amountPaise)
                    expenseCount++
                }
                RecordType.REFUND -> {
                    refundPaise = Math.addExact(refundPaise, tx.amountPaise)
                }
                else -> {
                    // Ignore other record types
                }
            }
        }

        val netPaise = (expensePaise - refundPaise).coerceAtLeast(0L)
        if (netPaise > 0L) {
            categorySpends.add(
                CategorySpend(
                    category = categoryName,
                    totalPaise = netPaise,
                    percent = 0, // calculated below against totalSpendPaise
                    transactionCount = expenseCount
                )
            )
        }
    }

    val totalSpendPaise = categorySpends.fold(0L) { acc, item -> Math.addExact(acc, item.totalPaise) }

    val finalizedCategories = if (totalSpendPaise > 0L) {
        categorySpends.map { item ->
            val pct = ((item.totalPaise.toDouble() * 100.0) / totalSpendPaise.toDouble()).roundToInt()
            item.copy(percent = pct)
        }.sortedWith(
            compareByDescending<CategorySpend> { it.totalPaise }
                .thenBy { it.category.lowercase(Locale.ROOT) }
        )
    } else {
        emptyList()
    }

    val topCategory = finalizedCategories.firstOrNull()

    val categoryTxList = if (selectedCategory != null) {
        periodTransactions
            .filter { it.category.trim().equals(selectedCategory.trim(), ignoreCase = true) }
            .sortedByDescending { it.dateTime }
    } else {
        emptyList()
    }

    return CategoriesUiState(
        periodFilter = periodFilter,
        pillLabel = pillLabel,
        totalSpendPaise = totalSpendPaise,
        categories = finalizedCategories,
        topCategory = topCategory,
        selectedCategory = selectedCategory,
        categoryTransactions = categoryTxList,
        isEmpty = finalizedCategories.isEmpty()
    )
}
