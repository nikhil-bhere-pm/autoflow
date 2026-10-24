package com.autoflow.app.domain.goals

import java.math.BigInteger

/**
 * Calculates progress for a goal based on its entries.
 *
 * Rules:
 * - Excludes soft-deleted entries (deletedAt != null).
 * - SET_ASIDE entries sum into setAsidePaise.
 * - AVOIDED entries sum into avoidedPaise.
 * - Summing large entries uses Math.addExact to prevent silent overflow.
 * - For SAVINGS goals:
 *     percent = floor(setAsidePaise * 100 / targetPaise), capped at 100 for display;
 *     isReached when setAsidePaise >= targetPaise;
 *     AVOIDED entries are an estimate and do NOT affect setAsidePaise or percent.
 * - For AVOIDANCE goals:
 *     percent = floor(avoidedPaise * 100 / targetPaise), capped at 100 for display;
 *     isReached when avoidedPaise >= targetPaise.
 * - Raw totals (setAsidePaise, avoidedPaise) are NOT capped.
 */
fun calculateProgress(goal: Goal, entries: List<GoalEntry>): GoalProgress {
    var setAsidePaise = 0L
    var avoidedPaise = 0L
    var usedPaise = 0L

    for (entry in entries) {
        if (entry.deletedAt != null) continue
        when (entry.type) {
            EntryType.SET_ASIDE -> setAsidePaise = Math.addExact(setAsidePaise, entry.amountPaise)
            EntryType.AVOIDED -> avoidedPaise = Math.addExact(avoidedPaise, entry.amountPaise)
            EntryType.USED -> usedPaise = Math.addExact(usedPaise, entry.amountPaise)
        }
    }

    val savedPaise = (setAsidePaise - usedPaise).coerceAtLeast(0L)

    val target = goal.targetPaise
    val relevantPaise = when (goal.kind) {
        GoalKind.SAVINGS -> savedPaise
        GoalKind.AVOIDANCE -> avoidedPaise
    }

    val percent = if (target <= 0L) {
        0
    } else {
        val calc = BigInteger.valueOf(relevantPaise)
            .multiply(BigInteger.valueOf(100))
            .divide(BigInteger.valueOf(target))
        calc.coerceIn(BigInteger.ZERO, BigInteger.valueOf(100)).toInt()
    }

    val isReached = target > 0L && relevantPaise >= target

    return GoalProgress(
        goalId = goal.id,
        setAsidePaise = setAsidePaise,
        avoidedPaise = avoidedPaise,
        percent = percent,
        isReached = isReached,
        usedPaise = usedPaise,
        savedPaise = savedPaise
    )
}

/**
 * Comparator for goal history: newest first by occurredAt, ties broken by id descending.
 */
val GOAL_HISTORY_COMPARATOR: Comparator<GoalEntry> =
    compareByDescending<GoalEntry> { it.occurredAt }.thenByDescending { it.id }
