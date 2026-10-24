package com.autoflow.app.domain.goals

import java.util.Locale

enum class GoalKind {
    SAVE,
    AVOID;

    companion object {
        @JvmField val SAVINGS = SAVE
        @JvmField val AVOIDANCE = AVOID
    }
}

enum class EntryType {
    SET_ASIDE,
    AVOIDED,
    USED
}

data class Goal(
    val id: Long,
    val name: String,
    val nameKey: String = name.trim().lowercase(Locale.ROOT),
    val kind: GoalKind,
    val targetPaise: Long,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val archivedAt: Long? = null
)

data class GoalEntry(
    val id: Long,
    val goalId: Long,
    val type: EntryType,
    val amountPaise: Long,
    val note: String? = null,
    val occurredAt: Long,
    val linkedTransactionId: Long? = null,
    val isDeleted: Boolean = false,
    val createdAt: Long = occurredAt,
    val updatedAt: Long = occurredAt,
    val deletedAt: Long? = if (isDeleted) updatedAt else null
)

data class GoalWithTotals(
    val id: Long,
    val name: String,
    val kind: GoalKind,
    val targetPaise: Long,
    val savedPaise: Long,
    val avoidedPaise: Long,
    val usedPaise: Long,
    val availablePaise: Long,
    val percent: Int
)

data class LinkedExpenseInfo(
    val transactionId: Long,
    val merchantName: String,
    val dateTime: Long,
    val amountPaise: Long,
    val isDeleted: Boolean = false
)

data class GoalEntryWithDetails(
    val entry: GoalEntry,
    val linkedExpense: LinkedExpenseInfo? = null,
    val isLinkedExpenseRemoved: Boolean = false
)

data class GoalWithEntries(
    val goal: Goal,
    val totals: GoalWithTotals,
    val entries: List<GoalEntryWithDetails>
)

data class GoalProgress(
    val goalId: Long,
    val setAsidePaise: Long,
    val avoidedPaise: Long,
    val percent: Int,
    val isReached: Boolean,
    val usedPaise: Long = 0L,
    val savedPaise: Long = (setAsidePaise - usedPaise).coerceAtLeast(0L)
)

data class GoalWithProgress(
    val goal: Goal,
    val progress: GoalProgress
)
