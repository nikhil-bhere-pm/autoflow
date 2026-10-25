package com.autoflow.app.notification.recap

import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.util.CurrencyFormatter

data class DailyRecapContent(
    val title: String,
    val text: String,
    val publicTitle: String = "AutoFlow",
    val publicText: String = "Your daily recap is ready",
    val channelId: String = CHANNEL_ID,
    val notificationId: Int = NOTIFICATION_ID
) {
    companion object {
        const val CHANNEL_ID = "daily_recap"
        const val NOTIFICATION_ID = 4001
    }
}

object DailyRecapContentBuilder {

    fun buildContent(todayTransactions: List<FinancialTransaction>): DailyRecapContent? {
        val activeTransactions = todayTransactions.filter { !it.isDeleted }
        val countedExpenses = activeTransactions.filter {
            it.recordType == RecordType.EXPENSE && it.reviewStatus != ReviewStatus.EXCLUDED
        }
        val reviewCount = activeTransactions.count {
            it.reviewStatus == ReviewStatus.NEEDS_REVIEW
        }

        if (countedExpenses.isEmpty() && reviewCount == 0) {
            return null
        }

        val totalSpentPaise = countedExpenses.sumOf { it.amountPaise }
        val title = "Today: ${CurrencyFormatter.formatPaiseToInr(totalSpentPaise)} spent"

        val text = if (countedExpenses.isNotEmpty()) {
            val count = countedExpenses.size
            val countText = "$count ${if (count == 1) "expense" else "expenses"}"

            val topCategoryEntry = countedExpenses
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amountPaise } }
                .maxByOrNull { it.value }

            val topCatName = topCategoryEntry?.key ?: "Others"
            val topCatAmount = topCategoryEntry?.value ?: 0L
            val topPart = "Top: $topCatName ${CurrencyFormatter.formatPaiseToInr(topCatAmount)}"
            val base = "$countText · $topPart"

            if (reviewCount > 0) {
                val reviewPart = if (reviewCount == 1) " · 1 needs a category" else " · $reviewCount need a category"
                base + reviewPart
            } else {
                base
            }
        } else {
            if (reviewCount == 1) "1 needs a category" else "$reviewCount need a category"
        }

        return DailyRecapContent(
            title = title,
            text = text
        )
    }
}
