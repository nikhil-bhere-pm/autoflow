package com.autoflow.app.widget

import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.domain.stats.StatsPeriod
import com.autoflow.app.domain.stats.periodRange
import com.autoflow.app.presentation.util.CurrencyFormatter
import java.time.Instant
import java.time.ZoneId

data class SpendWidgetContent(
    val title: String = "This month",
    val amountFormatted: String
)

object SpendWidgetContentBuilder {

    fun build(
        transactions: List<FinancialTransaction>,
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
        isPrivacyMode: Boolean = false
    ): SpendWidgetContent {
        if (isPrivacyMode) {
            return SpendWidgetContent(
                title = "This month",
                amountFormatted = "₹••••"
            )
        }

        val range = periodRange(StatsPeriod.THIS_MONTH, now, zone)
        val monthlyPaise = transactions
            .filter {
                !it.isDeleted &&
                    it.recordType == RecordType.EXPENSE &&
                    it.reviewStatus != ReviewStatus.EXCLUDED &&
                    it.dateTime in range
            }
            .fold(0L) { acc, tx -> Math.addExact(acc, tx.amountPaise) }
            .coerceAtLeast(0L)

        val formatted = CurrencyFormatter.formatPaiseWithoutDecimalsIfZero(monthlyPaise)
        return SpendWidgetContent(
            title = "This month",
            amountFormatted = formatted
        )
    }
}
