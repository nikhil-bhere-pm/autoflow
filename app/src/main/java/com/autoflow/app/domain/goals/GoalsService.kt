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
        if (trimmedName.length > 40) {
            return GoalResult.Error(GoalError.NameTooLong(trimmedName.length, 40))
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
     * Adds an entry to a goal after validating existence, amount limits, entry type, note/description, and date.
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

        if (goal.kind == GoalKind.AVOIDANCE && (type == EntryType.SET_ASIDE || type == EntryType.USED)) {
            return GoalResult.Error(GoalError.WrongEntryType(goal.kind, type))
        }

        val noteResult = validateNoteAndDescription(type, note)
        val validatedNote = when (noteResult) {
            is GoalResult.Success -> noteResult.data
            is GoalResult.Error -> return noteResult
        }

        val now = clock.millis()
        val maxAllowedAt = now + FUTURE_TOLERANCE_MILLIS
        if (occurredAt > maxAllowedAt) {
            return GoalResult.Error(GoalError.FutureDate(occurredAt, maxAllowedAt))
        }

        if (type == EntryType.USED) {
            val currentBalance = getSavedBalance(goalId)
            if (amountPaise > currentBalance) {
                return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = currentBalance, requestedPaise = amountPaise))
            }
        }

        val entry = GoalEntry(
            id = 0L,
            goalId = goalId,
            type = type,
            amountPaise = amountPaise,
            note = validatedNote,
            occurredAt = occurredAt,
            deletedAt = null,
            linkedTransactionId = null
        )
        val saved = repository.saveEntry(entry)
        return GoalResult.Success(saved)
    }

    /**
     * Edits an existing entry's amount, note, and occurredAt timestamp with balance and length checks.
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

        val noteResult = validateNoteAndDescription(existing.type, note)
        val validatedNote = when (noteResult) {
            is GoalResult.Success -> noteResult.data
            is GoalResult.Error -> return noteResult
        }

        val now = clock.millis()
        val maxAllowedAt = now + FUTURE_TOLERANCE_MILLIS
        if (occurredAt > maxAllowedAt) {
            return GoalResult.Error(GoalError.FutureDate(occurredAt, maxAllowedAt))
        }

        if (existing.deletedAt == null) {
            if (existing.type == EntryType.USED && amountPaise > existing.amountPaise) {
                val increase = amountPaise - existing.amountPaise
                val currentBalance = getSavedBalance(existing.goalId)
                if (increase > currentBalance) {
                    return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = currentBalance, requestedPaise = increase))
                }
            } else if (existing.type == EntryType.SET_ASIDE && amountPaise < existing.amountPaise) {
                val reduction = existing.amountPaise - amountPaise
                val currentBalance = getSavedBalance(existing.goalId)
                if (reduction > currentBalance) {
                    return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = currentBalance, requestedPaise = reduction))
                }
            }
        }

        val updated = existing.copy(
            amountPaise = amountPaise,
            note = validatedNote,
            occurredAt = occurredAt
        )
        val saved = repository.saveEntry(updated)
        return GoalResult.Success(saved)
    }

    /**
     * Soft deletes an entry so it is excluded from totals but kept in history/storage.
     * Rejects deleting a SET_ASIDE entry if that would leave balance negative.
     */
    suspend fun deleteEntry(entryId: Long): GoalResult<GoalEntry> {
        val existing = repository.getEntry(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))
        if (existing.deletedAt != null) {
            return GoalResult.Success(existing)
        }

        if (existing.type == EntryType.SET_ASIDE) {
            val currentBalance = getSavedBalance(existing.goalId)
            if (existing.amountPaise > currentBalance) {
                return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = currentBalance, requestedPaise = existing.amountPaise))
            }
        }

        val updated = existing.copy(
            deletedAt = clock.millis()
        )
        val saved = repository.saveEntry(updated)
        return GoalResult.Success(saved)
    }

    /**
     * Restores a soft-deleted entry back into totals.
     * Rejects restoring a USED entry if that would leave balance negative.
     */
    suspend fun restoreEntry(entryId: Long): GoalResult<GoalEntry> {
        val existing = repository.getEntry(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))
        if (existing.deletedAt == null) {
            return GoalResult.Success(existing)
        }

        if (existing.type == EntryType.USED) {
            val currentBalance = getSavedBalance(existing.goalId)
            if (existing.amountPaise > currentBalance) {
                return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = currentBalance, requestedPaise = existing.amountPaise))
            }
        }

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

    /**
     * Takes out money from a SAVINGS goal without spending on an expense (linkedTransactionId is null).
     */
    suspend fun takeOut(
        goalId: Long,
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

        if (goal.kind == GoalKind.AVOIDANCE) {
            return GoalResult.Error(GoalError.WrongEntryType(goal.kind, EntryType.USED))
        }

        val noteResult = validateNoteAndDescription(EntryType.USED, note)
        val validatedNote = when (noteResult) {
            is GoalResult.Success -> noteResult.data
            is GoalResult.Error -> return noteResult
        }

        val now = clock.millis()
        val maxAllowedAt = now + FUTURE_TOLERANCE_MILLIS
        if (occurredAt > maxAllowedAt) {
            return GoalResult.Error(GoalError.FutureDate(occurredAt, maxAllowedAt))
        }

        val currentBalance = getSavedBalance(goalId)
        if (amountPaise > currentBalance) {
            return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = currentBalance, requestedPaise = amountPaise))
        }

        val entry = GoalEntry(
            id = 0L,
            goalId = goalId,
            type = EntryType.USED,
            amountPaise = amountPaise,
            note = validatedNote,
            occurredAt = occurredAt,
            deletedAt = null,
            linkedTransactionId = null
        )
        val saved = repository.saveEntry(entry)
        return GoalResult.Success(saved)
    }

    /**
     * Links an expense transaction to a SAVINGS goal. Replaces any earlier link for the same transaction.
     */
    suspend fun linkExpense(
        goalId: Long,
        transactionId: Long,
        expenseAmountPaise: Long,
        amountPaise: Long,
        occurredAt: Long = clock.millis()
    ): GoalResult<GoalEntry> {
        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > expenseAmountPaise) {
            return GoalResult.Error(GoalError.AmountExceedsExpense(amountPaise, expenseAmountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val goal = repository.getGoal(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        if (goal.kind == GoalKind.AVOIDANCE) {
            return GoalResult.Error(GoalError.WrongEntryType(goal.kind, EntryType.USED))
        }

        val now = clock.millis()
        val maxAllowedAt = now + FUTURE_TOLERANCE_MILLIS
        if (occurredAt > maxAllowedAt) {
            return GoalResult.Error(GoalError.FutureDate(occurredAt, maxAllowedAt))
        }

        val existingLinked = repository.getEntriesByTransaction(transactionId).filter { it.deletedAt == null }

        val goalEntries = repository.getEntries(goalId).filter { it.deletedAt == null }
        val setAsideTotal = goalEntries.filter { it.type == EntryType.SET_ASIDE }.sumOf { it.amountPaise }
        val otherUsedTotal = goalEntries.filter { it.type == EntryType.USED && it.linkedTransactionId != transactionId }.sumOf { it.amountPaise }
        val available = setAsideTotal - otherUsedTotal

        if (amountPaise > available) {
            return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = available, requestedPaise = amountPaise))
        }

        for (oldEntry in existingLinked) {
            repository.saveEntry(oldEntry.copy(deletedAt = now))
        }

        val newEntry = GoalEntry(
            id = 0L,
            goalId = goalId,
            type = EntryType.USED,
            amountPaise = amountPaise,
            note = null,
            occurredAt = occurredAt,
            deletedAt = null,
            linkedTransactionId = transactionId
        )
        val saved = repository.saveEntry(newEntry)
        return GoalResult.Success(saved)
    }

    /**
     * Unlinks an expense transaction, restoring the goal's balance. Idempotent.
     */
    suspend fun unlinkExpense(transactionId: Long): GoalResult<Unit> {
        val existingLinked = repository.getEntriesByTransaction(transactionId).filter { it.deletedAt == null }
        val now = clock.millis()
        for (entry in existingLinked) {
            repository.saveEntry(entry.copy(deletedAt = now))
        }
        return GoalResult.Success(Unit)
    }

    /**
     * Soft-deletes the USED entries linked to a transaction. Idempotent.
     */
    suspend fun cancelUsageForTransaction(transactionId: Long): GoalResult<Unit> {
        val existingLinked = repository.getEntriesByTransaction(transactionId).filter { it.deletedAt == null }
        val now = clock.millis()
        for (entry in existingLinked) {
            repository.saveEntry(entry.copy(deletedAt = now))
        }
        return GoalResult.Success(Unit)
    }

    /**
     * Undoes the cancellation of usage for a transaction, restoring the USED entry. Idempotent.
     */
    suspend fun restoreUsageForTransaction(transactionId: Long): GoalResult<Unit> {
        val deletedLinked = repository.getEntriesByTransaction(transactionId).filter { it.deletedAt != null }
        val toRestore = deletedLinked.maxByOrNull { it.deletedAt ?: 0L } ?: return GoalResult.Success(Unit)

        val currentBalance = getSavedBalance(toRestore.goalId)
        if (toRestore.amountPaise > currentBalance) {
            return GoalResult.Error(GoalError.InsufficientBalance(availablePaise = currentBalance, requestedPaise = toRestore.amountPaise))
        }

        repository.saveEntry(toRestore.copy(deletedAt = null))
        return GoalResult.Success(Unit)
    }

    private fun validateNoteAndDescription(type: EntryType, note: String?): GoalResult<String?> {
        return when (type) {
            EntryType.AVOIDED -> {
                val trimmed = note?.trim() ?: ""
                if (trimmed.isEmpty()) {
                    GoalResult.Error(GoalError.DescriptionRequired)
                } else if (trimmed.length > MAX_AVOIDED_DESCRIPTION_LENGTH) {
                    GoalResult.Error(GoalError.DescriptionTooLong(trimmed.length, MAX_AVOIDED_DESCRIPTION_LENGTH))
                } else {
                    GoalResult.Success(trimmed)
                }
            }
            EntryType.SET_ASIDE, EntryType.USED -> {
                val trimmed = note?.trim()
                if (trimmed != null && trimmed.length > MAX_NOTE_LENGTH) {
                    GoalResult.Error(GoalError.NoteTooLong(trimmed.length, MAX_NOTE_LENGTH))
                } else {
                    GoalResult.Success(if (trimmed.isNullOrEmpty()) null else trimmed)
                }
            }
        }
    }

    private suspend fun getSavedBalance(goalId: Long): Long {
        val entries = repository.getEntries(goalId)
        var setAside = 0L
        var used = 0L
        for (e in entries) {
            if (e.deletedAt != null) continue
            when (e.type) {
                EntryType.SET_ASIDE -> setAside += e.amountPaise
                EntryType.USED -> used += e.amountPaise
                EntryType.AVOIDED -> {}
            }
        }
        return setAside - used
    }

}

