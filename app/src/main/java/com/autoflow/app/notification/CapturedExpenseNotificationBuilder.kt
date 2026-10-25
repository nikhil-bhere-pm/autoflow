package com.autoflow.app.notification

import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import com.autoflow.app.domain.model.FinancialTransaction
import com.autoflow.app.presentation.ui.getTopLikelyCategories
import com.autoflow.app.presentation.util.CurrencyFormatter

data class NotificationActionContent(
    val title: String,
    val actionType: String,
    val isActivity: Boolean,
    val category: String? = null
)

data class NotificationContent(
    val notificationId: Int,
    val title: String,
    val text: String,
    val channelId: String,
    val actions: List<NotificationActionContent>,
    val publicTitle: String,
    val publicText: String,
    val timeoutMs: Long?
)

object CapturedExpenseNotificationBuilder {
    const val CHANNEL_ID = "captured_expenses"
    const val ACTION_EDIT_TRANSACTION = "com.autoflow.app.ACTION_EDIT_TRANSACTION"
    const val ACTION_ASSIGN_CATEGORY = "com.autoflow.app.notification.ACTION_ASSIGN_CATEGORY"
    const val ACTION_MARK_NOT_EXPENSE = "com.autoflow.app.notification.ACTION_MARK_NOT_EXPENSE"
    const val ACTION_UNDO_CATEGORY = "com.autoflow.app.notification.ACTION_UNDO_CATEGORY"
    const val ACTION_UNDO_NOT_EXPENSE = "com.autoflow.app.notification.ACTION_UNDO_NOT_EXPENSE"

    const val EXTRA_TRANSACTION_ID = "extra_transaction_id"
    const val EXTRA_CATEGORY = "extra_category"
    const val EXTRA_PREV_CATEGORY = "extra_prev_category"
    const val EXTRA_PREV_CATEGORY_SOURCE = "extra_prev_category_source"
    const val EXTRA_PREV_REVIEW_STATUS = "extra_prev_review_status"
    const val EXTRA_PREV_UNCERTAINTY_BITS = "extra_prev_uncertainty_bits"
    const val EXTRA_PREV_MERCHANT_RULE = "extra_prev_merchant_rule"
    const val EXTRA_ASSIGNED_CATEGORY = "extra_assigned_category"
    const val EXTRA_NORMALIZED_MERCHANT_KEY = "extra_normalized_merchant_key"
    const val EXTRA_AMOUNT_PAISE = "extra_amount_paise"
    const val EXTRA_MERCHANT_NAME = "extra_merchant_name"

    fun getNotificationId(txId: Long): Int = ((txId % 100000L).toInt() + 10000)

    fun needsCategory(tx: FinancialTransaction): Boolean {
        return tx.reviewStatus == com.autoflow.app.data.model.ReviewStatus.NEEDS_REVIEW ||
                tx.merchantName.isBlank() ||
                tx.merchantName.equals("Unknown Merchant", ignoreCase = true)
    }

    fun buildInitialContent(
        tx: FinancialTransaction,
        allCategories: List<String> = BUILT_IN_CATEGORIES
    ): NotificationContent {
        return if (needsCategory(tx)) {
            buildNeedsCategoryContent(tx, allCategories)
        } else {
            buildKnownMerchantContent(tx)
        }
    }

    fun buildNeedsCategoryContent(
        tx: FinancialTransaction,
        allCategories: List<String> = BUILT_IN_CATEGORIES
    ): NotificationContent {
        val displayMerchant = if (tx.merchantName.isBlank()) "Unknown Merchant" else tx.merchantName
        val formattedAmount = CurrencyFormatter.formatPaiseToInr(tx.amountPaise)
        val likely = getTopLikelyCategories(tx, allCategories)
        val cat1 = likely.firstOrNull() ?: "Food"
        val cat2 = likely.getOrNull(1) ?: "Groceries"

        return NotificationContent(
            notificationId = getNotificationId(tx.id),
            title = "$formattedAmount spent at $displayMerchant",
            text = "Pick a category so it is counted correctly",
            channelId = CHANNEL_ID,
            actions = listOf(
                NotificationActionContent(title = cat1, actionType = ACTION_ASSIGN_CATEGORY, isActivity = false, category = cat1),
                NotificationActionContent(title = cat2, actionType = ACTION_ASSIGN_CATEGORY, isActivity = false, category = cat2),
                NotificationActionContent(title = "More…", actionType = ACTION_EDIT_TRANSACTION, isActivity = true)
            ),
            publicTitle = "AutoFlow",
            publicText = "New expense captured",
            timeoutMs = null
        )
    }

    fun buildKnownMerchantContent(tx: FinancialTransaction): NotificationContent {
        val displayMerchant = if (tx.merchantName.isBlank()) "Unknown Merchant" else tx.merchantName
        val formattedAmount = CurrencyFormatter.formatPaiseToInr(tx.amountPaise)

        return NotificationContent(
            notificationId = getNotificationId(tx.id),
            title = "$formattedAmount spent at $displayMerchant",
            text = "${tx.category} · counted in today's total",
            channelId = CHANNEL_ID,
            actions = listOf(
                NotificationActionContent(title = "Change", actionType = ACTION_EDIT_TRANSACTION, isActivity = true),
                NotificationActionContent(title = "Not an expense", actionType = ACTION_MARK_NOT_EXPENSE, isActivity = false)
            ),
            publicTitle = "AutoFlow",
            publicText = "New expense captured",
            timeoutMs = null
        )
    }

    fun buildCategorySavedContent(
        txId: Long,
        amountPaise: Long,
        category: String
    ): NotificationContent {
        val formattedAmount = CurrencyFormatter.formatPaiseToInr(amountPaise)
        return NotificationContent(
            notificationId = getNotificationId(txId),
            title = "Saved: $formattedAmount · $category",
            text = "Future spends at this merchant will use $category",
            channelId = CHANNEL_ID,
            actions = listOf(
                NotificationActionContent(title = "Undo", actionType = ACTION_UNDO_CATEGORY, isActivity = false)
            ),
            publicTitle = "AutoFlow",
            publicText = "New expense captured",
            timeoutMs = 15000L
        )
    }

    fun buildNotExpenseContent(
        txId: Long,
        amountPaise: Long,
        merchantName: String
    ): NotificationContent {
        val displayMerchant = if (merchantName.isBlank()) "Unknown Merchant" else merchantName
        val formattedAmount = CurrencyFormatter.formatPaiseToInr(amountPaise)
        return NotificationContent(
            notificationId = getNotificationId(txId),
            title = "Marked as not an expense",
            text = "$formattedAmount at $displayMerchant not counted",
            channelId = CHANNEL_ID,
            actions = listOf(
                NotificationActionContent(title = "Undo", actionType = ACTION_UNDO_NOT_EXPENSE, isActivity = false)
            ),
            publicTitle = "AutoFlow",
            publicText = "New expense captured",
            timeoutMs = 15000L
        )
    }
}
