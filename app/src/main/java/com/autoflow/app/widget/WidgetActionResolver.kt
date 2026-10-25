package com.autoflow.app.widget

import com.autoflow.app.domain.goals.Goal

sealed class WidgetDestination {
    data object QuickExpense : WidgetDestination()
    data class Saved(val selectedGoalId: Long, val goals: List<Goal>) : WidgetDestination()
    data object NewGoalWithHint : WidgetDestination()
}

object WidgetActionResolver {
    const val ACTION_EXPENSE = AutoFlowWidgetProvider.ACTION_EXPENSE
    const val ACTION_SAVED = AutoFlowWidgetProvider.ACTION_SAVED

    fun resolveAction(
        action: String?,
        goals: List<Goal>,
        lastUsedGoalId: Long?
    ): WidgetDestination? {
        return when (action) {
            ACTION_EXPENSE -> WidgetDestination.QuickExpense
            ACTION_SAVED -> resolveSavedAction(goals, lastUsedGoalId)
            else -> null // Unknown or malformed actions are strictly ignored
        }
    }

    fun resolveSavedAction(
        goals: List<Goal>,
        lastUsedGoalId: Long?
    ): WidgetDestination {
        val activeGoals = goals.filter { !it.isDeleted }
        if (activeGoals.isEmpty()) {
            return WidgetDestination.NewGoalWithHint
        }
        val selectedId = when {
            activeGoals.size == 1 -> activeGoals.first().id
            lastUsedGoalId != null && activeGoals.any { it.id == lastUsedGoalId } -> lastUsedGoalId
            else -> activeGoals.first().id
        }
        return WidgetDestination.Saved(
            selectedGoalId = selectedId,
            goals = activeGoals
        )
    }
}
