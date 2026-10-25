package com.autoflow.app.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.autoflow.app.AutoFlowApplication
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.repository.CategoryUndoSnapshot
import com.autoflow.app.data.repository.NotExpenseUndoSnapshot
import com.autoflow.app.domain.category.BUILT_IN_CATEGORIES
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return

        val txId = intent.getLongExtra(CapturedExpenseNotificationBuilder.EXTRA_TRANSACTION_ID, -1L)
        if (txId <= 0) return

        val app = context.applicationContext as? AutoFlowApplication ?: return
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                handleAction(context, app, action, txId, intent)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleAction(
        context: Context,
        app: AutoFlowApplication,
        action: String,
        txId: Long,
        intent: Intent
    ) {
        val tx = app.transactionRepository.getTransactionById(txId) ?: return
        if (tx.isDeleted) return

        when (action) {
            CapturedExpenseNotificationBuilder.ACTION_ASSIGN_CATEGORY -> {
                val category = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_CATEGORY) ?: return
                val snapshot = app.transactionRepository.assignCategory(txId, category) ?: return

                val content = CapturedExpenseNotificationBuilder.buildCategorySavedContent(
                    txId = txId,
                    amountPaise = tx.amountPaise,
                    category = category
                )
                val undoExtras = mutableMapOf<String, Any?>()
                undoExtras[CapturedExpenseNotificationBuilder.EXTRA_PREV_CATEGORY] = snapshot.previousCategory
                undoExtras[CapturedExpenseNotificationBuilder.EXTRA_PREV_CATEGORY_SOURCE] = snapshot.previousCategorySource
                undoExtras[CapturedExpenseNotificationBuilder.EXTRA_PREV_REVIEW_STATUS] = snapshot.previousReviewStatus.name
                undoExtras[CapturedExpenseNotificationBuilder.EXTRA_PREV_UNCERTAINTY_BITS] = snapshot.previousUncertaintyBits
                if (snapshot.previousMerchantRuleCategory != null) {
                    undoExtras[CapturedExpenseNotificationBuilder.EXTRA_PREV_MERCHANT_RULE] = snapshot.previousMerchantRuleCategory
                }
                undoExtras[CapturedExpenseNotificationBuilder.EXTRA_ASSIGNED_CATEGORY] = snapshot.assignedCategory
                undoExtras[CapturedExpenseNotificationBuilder.EXTRA_NORMALIZED_MERCHANT_KEY] = snapshot.normalizedMerchantKey

                CapturedExpenseNotificationHelper.postNotification(context, content, txId, undoExtras)
            }

            CapturedExpenseNotificationBuilder.ACTION_UNDO_CATEGORY -> {
                val prevCategory = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_PREV_CATEGORY) ?: return
                val prevCategorySource = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_PREV_CATEGORY_SOURCE) ?: "AUTO"
                val prevReviewStatusStr = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_PREV_REVIEW_STATUS) ?: return
                val prevReviewStatus = try {
                    ReviewStatus.valueOf(prevReviewStatusStr)
                } catch (e: Exception) {
                    return
                }
                val prevUncertaintyBits = intent.getIntExtra(CapturedExpenseNotificationBuilder.EXTRA_PREV_UNCERTAINTY_BITS, 0)
                val prevMerchantRule = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_PREV_MERCHANT_RULE)
                val assignedCategory = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_ASSIGNED_CATEGORY) ?: ""
                val normalizedKey = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_NORMALIZED_MERCHANT_KEY) ?: ""

                val snapshot = CategoryUndoSnapshot(
                    transactionId = txId,
                    previousCategory = prevCategory,
                    previousCategorySource = prevCategorySource,
                    previousReviewStatus = prevReviewStatus,
                    previousUncertaintyBits = prevUncertaintyBits,
                    previousMerchantRuleCategory = prevMerchantRule,
                    assignedCategory = assignedCategory,
                    normalizedMerchantKey = normalizedKey
                )
                app.transactionRepository.undoAssignCategory(snapshot)

                val restoredTx = app.transactionRepository.getTransactionById(txId) ?: return
                val categories = try {
                    app.categoryRepository.getAllCategories().first().map { it.name }
                } catch (e: Exception) {
                    BUILT_IN_CATEGORIES
                }
                val content = CapturedExpenseNotificationBuilder.buildNeedsCategoryContent(restoredTx, categories)
                CapturedExpenseNotificationHelper.postNotification(context, content, txId)
            }

            CapturedExpenseNotificationBuilder.ACTION_MARK_NOT_EXPENSE -> {
                val snapshot = app.transactionRepository.markNotAnExpense(txId) ?: return

                val content = CapturedExpenseNotificationBuilder.buildNotExpenseContent(
                    txId = txId,
                    amountPaise = tx.amountPaise,
                    merchantName = tx.merchantName
                )
                val undoExtras = mapOf<String, Any?>(
                    CapturedExpenseNotificationBuilder.EXTRA_PREV_REVIEW_STATUS to snapshot.previousReviewStatus.name
                )
                CapturedExpenseNotificationHelper.postNotification(context, content, txId, undoExtras)
            }

            CapturedExpenseNotificationBuilder.ACTION_UNDO_NOT_EXPENSE -> {
                val prevReviewStatusStr = intent.getStringExtra(CapturedExpenseNotificationBuilder.EXTRA_PREV_REVIEW_STATUS) ?: return
                val prevReviewStatus = try {
                    ReviewStatus.valueOf(prevReviewStatusStr)
                } catch (e: Exception) {
                    return
                }
                val snapshot = NotExpenseUndoSnapshot(
                    transactionId = txId,
                    previousReviewStatus = prevReviewStatus
                )
                app.transactionRepository.undoMarkNotAnExpense(snapshot)

                val restoredTx = app.transactionRepository.getTransactionById(txId) ?: return
                val content = CapturedExpenseNotificationBuilder.buildKnownMerchantContent(restoredTx)
                CapturedExpenseNotificationHelper.postNotification(context, content, txId)
            }

            else -> {
                // Ignore unknown or malformed action
            }
        }
    }
}
