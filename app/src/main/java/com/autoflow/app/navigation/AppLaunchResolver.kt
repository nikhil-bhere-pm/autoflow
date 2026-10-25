package com.autoflow.app.navigation

import android.content.Intent
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.notification.CapturedExpenseNotificationBuilder
import com.autoflow.app.widget.AutoFlowWidgetProvider
import com.autoflow.app.widget.WidgetActionResolver
import com.autoflow.app.widget.WidgetDestination

sealed class AppLaunchTarget {
    data object Home : AppLaunchTarget()
    data class EditTransaction(val transactionId: Long) : AppLaunchTarget()
    data class Saved(val selectedGoalId: Long, val goals: List<Goal>) : AppLaunchTarget()
    data object QuickExpense : AppLaunchTarget()
    data object NewGoalWithHint : AppLaunchTarget()
    data object None : AppLaunchTarget()
}

object AppLaunchResolver {
    const val EXTRA_OPEN_HOME = "com.autoflow.app.extra.OPEN_HOME"
    const val ACTION_OPEN_HOME = "com.autoflow.app.action.OPEN_HOME"
    const val ACTION_EDIT_TRANSACTION = CapturedExpenseNotificationBuilder.ACTION_EDIT_TRANSACTION
    const val EXTRA_TRANSACTION_ID = CapturedExpenseNotificationBuilder.EXTRA_TRANSACTION_ID
    const val ACTION_EXPENSE = AutoFlowWidgetProvider.ACTION_EXPENSE
    const val ACTION_SAVED = AutoFlowWidgetProvider.ACTION_SAVED

    const val HOME_INTENT_FLAGS = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP

    fun buildHomeIntent(baseIntent: Intent = Intent()): Intent {
        return baseIntent.apply {
            flags = HOME_INTENT_FLAGS
            putExtra(EXTRA_OPEN_HOME, true)
        }
    }

    fun resolve(
        action: String?,
        isOpenHome: Boolean,
        transactionId: Long,
        goals: List<Goal> = emptyList(),
        lastUsedGoalId: Long? = null
    ): AppLaunchTarget {
        return when {
            isOpenHome || action == ACTION_OPEN_HOME -> AppLaunchTarget.Home
            action == ACTION_EDIT_TRANSACTION -> {
                if (transactionId > 0) {
                    AppLaunchTarget.EditTransaction(transactionId)
                } else {
                    AppLaunchTarget.None
                }
            }
            action == ACTION_EXPENSE -> AppLaunchTarget.QuickExpense
            action == ACTION_SAVED -> {
                when (val dest = WidgetActionResolver.resolveSavedAction(goals, lastUsedGoalId)) {
                    is WidgetDestination.Saved -> AppLaunchTarget.Saved(dest.selectedGoalId, dest.goals)
                    is WidgetDestination.NewGoalWithHint -> AppLaunchTarget.NewGoalWithHint
                    else -> AppLaunchTarget.None
                }
            }
            else -> AppLaunchTarget.None
        }
    }

    fun resolve(
        intent: Intent?,
        goals: List<Goal> = emptyList(),
        lastUsedGoalId: Long? = null
    ): AppLaunchTarget {
        if (intent == null) return AppLaunchTarget.None
        val isOpenHome = intent.getBooleanExtra(EXTRA_OPEN_HOME, false)
        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        return resolve(
            action = intent.action,
            isOpenHome = isOpenHome,
            transactionId = transactionId,
            goals = goals,
            lastUsedGoalId = lastUsedGoalId
        )
    }
}
