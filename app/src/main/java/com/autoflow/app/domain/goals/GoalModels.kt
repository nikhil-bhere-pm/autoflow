package com.autoflow.app.domain.goals

import java.util.Locale

data class Goal(
    val id: Long,
    val name: String,
    val nameKey: String = normalizeGoalNameKey(name),
    val targetPaise: Long,
    val deadlineAt: Long? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long = createdAt
)

data class GoalEntry(
    val id: Long,
    val goalId: Long,
    val amountPaise: Long,
    val note: String,
    val occurredAt: Long,
    val isDeleted: Boolean = false,
    val createdAt: Long = occurredAt,
    val updatedAt: Long = occurredAt
)

data class GoalWithTotals(
    val goal: Goal,
    val totalPaise: Long,
    val reached: Boolean
) {
    val id: Long get() = goal.id
    val name: String get() = goal.name
    val targetPaise: Long get() = goal.targetPaise
    val deadlineAt: Long? get() = goal.deadlineAt
    val isDeleted: Boolean get() = goal.isDeleted
    val percent: Int get() = if (targetPaise > 0L) {
        ((totalPaise * 100L) / targetPaise).coerceIn(0L, 100L).toInt()
    } else 0
}

fun normalizeGoalNameKey(name: String): String {
    return name.trim().replace(Regex("\\s+"), " ").lowercase(Locale.ROOT)
}
