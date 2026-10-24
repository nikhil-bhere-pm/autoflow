package com.autoflow.app.presentation.viewmodel

import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.domain.stats.MonthPoint
import com.autoflow.app.domain.stats.PeriodRange
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.domain.stats.dayTotal
import com.autoflow.app.domain.stats.isCountedAsSpending
import com.autoflow.app.domain.stats.monthlySeries
import com.autoflow.app.domain.stats.periodRange
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

sealed class PeriodFilter {
    data class Predefined(val period: StatsPeriod) : PeriodFilter()
    data class SpecificMonth(val month: YearMonth) : PeriodFilter()
}

enum class HomeBadgeType {
    CHECK,
    NOT_COUNTED,
    REFUND
}

fun FinancialTransaction.getHomeBadgeType(): HomeBadgeType? {
    return when {
        recordType == RecordType.REFUND -> HomeBadgeType.REFUND
        reviewStatus == ReviewStatus.EXCLUDED -> HomeBadgeType.NOT_COUNTED
        reviewStatus == ReviewStatus.NEEDS_REVIEW -> HomeBadgeType.CHECK
        else -> null
    }
}

data class DayGroup(
    val date: LocalDate,
    val dayTotalPaise: Long,
    val transactions: List<FinancialTransaction>
)

data class HomeUiState(
    val periodFilter: PeriodFilter = PeriodFilter.Predefined(StatsPeriod.THIS_MONTH),
    val pillLabel: String = "This month",
    val countedSpendPaise: Long = 0L,
    val countedTransactionCount: Int = 0,
    val monthlySeries: List<MonthPoint> = emptyList(),
    val chartAccessibilityDescription: String = "",
    val groupedTransactions: List<DayGroup> = emptyList(),
    val isEmpty: Boolean = true
)

fun computeHomeUiState(
    transactions: List<FinancialTransaction>,
    periodFilter: PeriodFilter,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault()
): HomeUiState {
    val currentMonth = YearMonth.from(now.atZone(zone))
    val viewedMonth = when (periodFilter) {
        is PeriodFilter.SpecificMonth -> periodFilter.month
        is PeriodFilter.Predefined -> currentMonth
    }

    val series = monthlySeries(
        records = transactions,
        selectedMonth = viewedMonth,
        currentMonth = currentMonth,
        zone = zone
    )

    val startMonthName = series.first().month.month.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
    val endMonthName = series.last().month.month.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
    val selectedMonthName = viewedMonth.month.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
    val accessibilityDesc = "Spending by month, $startMonthName to $endMonthName, $selectedMonthName selected"

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
            val mName = periodFilter.month.month.name.take(3).lowercase(Locale.ROOT).replaceFirstChar { it.uppercase(Locale.ROOT) }
            "$mName ${periodFilter.month.year}"
        }
    }

    val periodTransactions = transactions.filter { !it.isDeleted && it.dateTime in range }
    val countedSpendPaise = periodTransactions
        .filter { it.isCountedAsSpending() }
        .fold(0L) { acc, tx -> Math.addExact(acc, tx.amountPaise) }
        .coerceAtLeast(0L)

    val countedTransactionCount = periodTransactions.count { it.isCountedAsSpending() }

    val groupedMap = periodTransactions.groupBy {
        Instant.ofEpochMilli(it.dateTime).atZone(zone).toLocalDate()
    }

    val sortedDates = groupedMap.keys.sortedDescending()
    val dayGroups = sortedDates.map { date ->
        val dayTx = groupedMap[date]!!.sortedByDescending { it.dateTime }
        val dayTotal = dayTotal(transactions, date, zone)
        DayGroup(
            date = date,
            dayTotalPaise = dayTotal,
            transactions = dayTx
        )
    }

    return HomeUiState(
        periodFilter = periodFilter,
        pillLabel = pillLabel,
        countedSpendPaise = countedSpendPaise,
        countedTransactionCount = countedTransactionCount,
        monthlySeries = series,
        chartAccessibilityDescription = accessibilityDesc,
        groupedTransactions = dayGroups,
        isEmpty = periodTransactions.isEmpty()
    )
}
