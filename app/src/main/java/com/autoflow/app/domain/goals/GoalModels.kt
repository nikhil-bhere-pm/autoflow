package com.autoflow.app.domain.goals

enum class GoalKind {
    SAVINGS,
    AVOIDANCE
}

enum class EntryType {
    SET_ASIDE,
    AVOIDED
}

data class Goal(
    val id: Long,
    val name: String,
    val kind: GoalKind,
    val targetPaise: Long,
    val createdAt: Long,
    val archivedAt: Long? = null
)

data class GoalEntry(
    val id: Long,
    val goalId: Long,
    val type: EntryType,
    val amountPaise: Long,
    val note: String? = null,
    val occurredAt: Long,
    val deletedAt: Long? = null
)

data class GoalProgress(
    val goalId: Long,
    val setAsidePaise: Long,
    val avoidedPaise: Long,
    val percent: Int,
    val isReached: Boolean
)

data class GoalWithProgress(
    val goal: Goal,
    val progress: GoalProgress
)
