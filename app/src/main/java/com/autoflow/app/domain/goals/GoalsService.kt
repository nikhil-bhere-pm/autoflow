package com.autoflow.app.domain.goals

import java.time.Clock

/**
 * Domain service for managing financial goals and their entries.
 *
 * Rules:
 * - All monetary values are integer paise (Long) > 0 and <= MAX_AMOUNT_PAISE (10,00,00,000.00 rupees = 10,000,000,000 paise).
 * - Goal names are trimmed, 1-40 characters, case-insensitively unique among non-archived goals.
 * - SET_ASIDE entries are allowed only on SAVINGS goals.
 * - AVOIDED entries are allowed on any goal and are always an estimate (never added to set-aside totals or savings percent).
 * - Soft-deleted entries are excluded from progress totals.
 * - Entries can be edited (amount, note, date), deleted, and restored.
 * - Archiving a goal hides it from active listGoals and preserves its entries.
 * - Entry occurredAt may be in the past but not > now + 5 minutes.
 * - History is ordered newest first by occurredAt, ties broken by id descending.
 * - Validation returns typed GoalResult.Error rather than throwing exceptions.
 */
class GoalsService(
    private val repository: GoalsRepository,
    private val clock: Clock = Clock.systemUTC()
) {

    /**
     * Creates a new goal after validating name uniqueness, length, and target limit.
     */
    suspend fun createGoal(
        name: String,
        kind: GoalKind,
        targetPaise: Long
    ): GoalResult<Goal> {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            return GoalResult.Error(GoalError.BlankName)
        }
        if (trimmedName.length > MAX_GOAL_NAME_LENGTH) {
            return GoalResult.Error(GoalError.NameTooLong(trimmedName.length, MAX_GOAL_NAME_LENGTH))
        }

        if (targetPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(targetPaise))
        }
        if (targetPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(targetPaise, MAX_AMOUNT_PAISE))
        }

        val existingGoals = repository.getAllGoals()
        val isDuplicate = existingGoals.any { existing ->
            existing.archivedAt == null && existing.name.trim().equals(trimmedName, ignoreCase = true)
        }
        if (isDuplicate) {
            return GoalResult.Error(GoalError.DuplicateName(trimmedName))
        }

        val goal = Goal(
            id = 0L,
            name = trimmedName,
            kind = kind,
            targetPaise = targetPaise,
            createdAt = clock.millis(),
            archivedAt = null
        )
        val saved = repository.saveGoal(goal)
        return GoalResult.Success(saved)
    }

    /**
     * Adds an entry to a goal after validating existence, amount limits, entry type, and date.
     */
    suspend fun addEntry(
        goalId: Long,
        type: EntryType,
        amountPaise: Long,
        note: String? = null,
        occurredAt: Long = clock.millis()
    ): GoalResult<GoalEntry> {
        val goal = repository.getGoal(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))

        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        if (goal.kind == GoalKind.AVOIDANCE && type == EntryType.SET_ASIDE) {
            return GoalResult.Error(GoalError.WrongEntryType(goal.kind, type))
        }

        val now = clock.millis()
        val maxAllowedAt = now + FUTURE_TOLERANCE_MILLIS
        if (occurredAt > maxAllowedAt) {
            return GoalResult.Error(GoalError.FutureDate(occurredAt, maxAllowedAt))
        }

        val entry = GoalEntry(
            id = 0L,
            goalId = goalId,
            type = type,
            amountPaise = amountPaise,
            note = note?.trim(),
            occurredAt = occurredAt,
            deletedAt = null
        )
        val saved = repository.saveEntry(entry)
        return GoalResult.Success(saved)
    }

    /**
     * Edits an existing entry's amount, note, and occurredAt timestamp.
     */
    suspend fun editEntry(
        entryId: Long,
        amountPaise: Long,
        note: String?,
        occurredAt: Long
    ): GoalResult<GoalEntry> {
        val existing = repository.getEntry(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))

        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val now = clock.millis()
        val maxAllowedAt = now + FUTURE_TOLERANCE_MILLIS
        if (occurredAt > maxAllowedAt) {
            return GoalResult.Error(GoalError.FutureDate(occurredAt, maxAllowedAt))
        }

        val updated = existing.copy(
            amountPaise = amountPaise,
            note = note?.trim(),
            occurredAt = occurredAt
        )
        val saved = repository.saveEntry(updated)
        return GoalResult.Success(saved)
    }

    /**
     * Soft deletes an entry so it is excluded from totals but kept in history/storage.
     */
    suspend fun deleteEntry(entryId: Long): GoalResult<GoalEntry> {
        val existing = repository.getEntry(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))
        val updated = existing.copy(
            deletedAt = existing.deletedAt ?: clock.millis()
        )
        val saved = repository.saveEntry(updated)
        return GoalResult.Success(saved)
    }

    /**
     * Restores a soft-deleted entry back into totals.
     */
    suspend fun restoreEntry(entryId: Long): GoalResult<GoalEntry> {
        val existing = repository.getEntry(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))
        val updated = existing.copy(deletedAt = null)
        val saved = repository.saveEntry(updated)
        return GoalResult.Success(saved)
    }

    /**
     * Archives a goal, hiding it from the active goals list while preserving its entries.
     */
    suspend fun archiveGoal(goalId: Long): GoalResult<Goal> {
        val goal = repository.getGoal(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        val updated = goal.copy(
            archivedAt = goal.archivedAt ?: clock.millis()
        )
        val saved = repository.saveGoal(updated)
        return GoalResult.Success(saved)
    }

    /**
     * Lists active non-archived goals along with their current progress.
     */
    suspend fun listGoals(includeArchived: Boolean = false): List<GoalWithProgress> {
        val all = repository.getAllGoals()
        val filtered = if (includeArchived) all else all.filter { it.archivedAt == null }
        return filtered.map { goal ->
            val entries = repository.getEntries(goal.id)
            val progress = calculateProgress(goal, entries)
            GoalWithProgress(goal = goal, progress = progress)
        }
    }

    /**
     * Retrieves history for a goal ordered newest first by occurredAt, ties by id descending.
     */
    suspend fun history(
        goalId: Long,
        includeDeleted: Boolean = true
    ): GoalResult<List<GoalEntry>> {
        val goal = repository.getGoal(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        val entries = repository.getEntries(goal.id)
        val filtered = if (includeDeleted) entries else entries.filter { it.deletedAt == null }
        val sorted = filtered.sortedWith(GOAL_HISTORY_COMPARATOR)
        return GoalResult.Success(sorted)
    }

    /**
     * Computes the current progress for a specific goal.
     */
    suspend fun getGoalProgress(goalId: Long): GoalResult<GoalProgress> {
        val goal = repository.getGoal(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        val entries = repository.getEntries(goal.id)
        return GoalResult.Success(calculateProgress(goal, entries))
    }
}
