package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.GoalDao
import com.autoflow.app.data.db.dao.TransactionDao
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.GoalEntryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.goals.EntryType
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.domain.goals.GoalEntry
import com.autoflow.app.domain.goals.GoalEntryWithDetails
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalKind
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.domain.goals.GoalWithEntries
import com.autoflow.app.domain.goals.GoalWithTotals
import com.autoflow.app.domain.goals.LinkedExpenseInfo
import com.autoflow.app.domain.goals.MAX_AMOUNT_PAISE
import com.autoflow.app.domain.goals.MAX_GOAL_NAME_LENGTH
import com.autoflow.app.domain.goals.MAX_NOTE_LENGTH
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

interface GoalRepository {
    suspend fun createGoal(name: String, kind: GoalKind, targetPaise: Long, now: Long = System.currentTimeMillis()): GoalResult<Goal>
    suspend fun updateGoal(id: Long, name: String, targetPaise: Long, now: Long = System.currentTimeMillis()): GoalResult<Goal>
    suspend fun getGoalById(id: Long): Goal?
    suspend fun getAllGoals(): List<Goal>
    fun observeGoals(): Flow<List<GoalWithTotals>>
    fun observeGoalWithEntries(goalId: Long): Flow<GoalWithEntries?>

    suspend fun addSetAside(
        goalId: Long,
        amountPaise: Long,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun addAvoided(
        goalId: Long,
        amountPaise: Long,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun useLinkExpense(
        goalId: Long,
        expenseId: Long,
        amountPaise: Long,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun useNewExpense(
        goalId: Long,
        amountPaise: Long,
        merchantName: String,
        category: String,
        paymentMode: String = "CASH",
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun useTakeOut(
        goalId: Long,
        amountPaise: Long,
        note: String? = null,
        occurredAt: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun editEntry(
        entryId: Long,
        newGoalId: Long? = null,
        newAmountPaise: Long? = null,
        newNote: String? = null,
        newOccurredAt: Long? = null,
        now: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun removeUsed(entryId: Long, now: Long = System.currentTimeMillis()): GoalResult<Unit>
    suspend fun deleteEntry(entryId: Long, now: Long = System.currentTimeMillis()): GoalResult<Unit>
    suspend fun undeleteEntry(entryId: Long, now: Long = System.currentTimeMillis()): GoalResult<Unit>
}

class DefaultGoalRepository(
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit = { block -> block() }
) : GoalRepository {

    override suspend fun createGoal(name: String, kind: GoalKind, targetPaise: Long, now: Long): GoalResult<Goal> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return GoalResult.Error(GoalError.BlankName)
        }
        if (trimmed.length > MAX_GOAL_NAME_LENGTH) {
            return GoalResult.Error(GoalError.NameTooLong(trimmed.length, MAX_GOAL_NAME_LENGTH))
        }
        if (targetPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(targetPaise))
        }
        if (targetPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(targetPaise, MAX_AMOUNT_PAISE))
        }

        val nameKey = trimmed.lowercase(Locale.ROOT)
        val existing = goalDao.getGoalByNameKey(nameKey)
        if (existing != null) {
            return GoalResult.Error(GoalError.DuplicateName(trimmed))
        }

        val entity = GoalEntity(
            id = 0L,
            name = trimmed,
            nameKey = nameKey,
            kind = kind.name,
            targetPaise = targetPaise,
            createdAt = now,
            updatedAt = now
        )
        val id = goalDao.insertGoal(entity)
        return GoalResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun updateGoal(id: Long, name: String, targetPaise: Long, now: Long): GoalResult<Goal> {
        val existing = goalDao.getGoalById(id) ?: return GoalResult.Error(GoalError.GoalNotFound(id))
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return GoalResult.Error(GoalError.BlankName)
        }
        if (trimmed.length > MAX_GOAL_NAME_LENGTH) {
            return GoalResult.Error(GoalError.NameTooLong(trimmed.length, MAX_GOAL_NAME_LENGTH))
        }
        if (targetPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(targetPaise))
        }
        if (targetPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(targetPaise, MAX_AMOUNT_PAISE))
        }

        val nameKey = trimmed.lowercase(Locale.ROOT)
        val existingWithKey = goalDao.getGoalByNameKey(nameKey)
        if (existingWithKey != null && existingWithKey.id != id) {
            return GoalResult.Error(GoalError.DuplicateName(trimmed))
        }

        val updated = existing.copy(
            name = trimmed,
            nameKey = nameKey,
            targetPaise = targetPaise,
            updatedAt = now
        )
        goalDao.updateGoal(updated)
        return GoalResult.Success(updated.toDomain())
    }

    override suspend fun getGoalById(id: Long): Goal? {
        return goalDao.getGoalById(id)?.toDomain()
    }

    override suspend fun getAllGoals(): List<Goal> {
        return goalDao.getAllGoals().map { it.toDomain() }
    }

    private suspend fun calculateAvailable(goalId: Long): Long {
        val activeEntries = goalDao.getActiveEntriesForGoal(goalId)
        val setAside = activeEntries.filter { it.type == "SET_ASIDE" }.sumOf { it.amountPaise }
        val used = activeEntries.filter { it.type == "USED" }.sumOf { it.amountPaise }
        return (setAside - used).coerceAtLeast(0L)
    }

    override fun observeGoals(): Flow<List<GoalWithTotals>> {
        return goalDao.observeGoalsRaw().map { rawList ->
            rawList.map { raw ->
                val kind = GoalKind.valueOf(raw.kind)
                val savedPaise = if (kind == GoalKind.SAVE) {
                    (raw.set_aside_paise - raw.used_paise).coerceAtLeast(0L)
                } else 0L
                val availablePaise = savedPaise
                val avoidedPaise = raw.avoided_paise
                val usedPaise = raw.used_paise

                val percent = when (kind) {
                    GoalKind.SAVE -> if (raw.target_paise > 0L) {
                        ((savedPaise * 100L) / raw.target_paise).coerceIn(0L, 100L).toInt()
                    } else 0
                    GoalKind.AVOID -> if (raw.target_paise > 0L) {
                        ((avoidedPaise * 100L) / raw.target_paise).coerceIn(0L, 100L).toInt()
                    } else 0
                }

                GoalWithTotals(
                    id = raw.id,
                    name = raw.name,
                    kind = kind,
                    targetPaise = raw.target_paise,
                    savedPaise = savedPaise,
                    avoidedPaise = avoidedPaise,
                    usedPaise = usedPaise,
                    availablePaise = availablePaise,
                    percent = percent
                )
            }
        }
    }

    override fun observeGoalWithEntries(goalId: Long): Flow<GoalWithEntries?> {
        return goalDao.observeAllGoals().combine(goalDao.observeEntriesForGoal(goalId)) { goals: List<GoalEntity>, entries: List<GoalEntryEntity> ->
            val goal = goals.firstOrNull { it.id == goalId } ?: return@combine null

            val details = entries.map { entry: GoalEntryEntity ->
                val tx = entry.linkedTransactionId?.let { txId -> transactionDao.getById(txId) }
                val isRemoved = entry.linkedTransactionId != null && (tx == null || tx.isDeleted != 0)
                val expenseInfo = tx?.let {
                    LinkedExpenseInfo(
                        transactionId = it.id,
                        merchantName = it.merchantName,
                        dateTime = it.dateTime,
                        amountPaise = it.amountPaise,
                        isDeleted = it.isDeleted != 0
                    )
                }

                GoalEntryWithDetails(
                    entry = entry.toDomain(),
                    linkedExpense = expenseInfo,
                    isLinkedExpenseRemoved = isRemoved
                )
            }

            val activeEntries = entries.filter { it.isDeleted == 0 }
            val setAside = activeEntries.filter { it.type == "SET_ASIDE" }.sumOf { it.amountPaise }
            val avoided = activeEntries.filter { it.type == "AVOIDED" }.sumOf { it.amountPaise }
            val used = activeEntries.filter { it.type == "USED" }.sumOf { it.amountPaise }
            val saved = (setAside - used).coerceAtLeast(0L)
            val kind = GoalKind.valueOf(goal.kind)

            val percent = when (kind) {
                GoalKind.SAVE -> if (goal.targetPaise > 0L) {
                    ((saved * 100L) / goal.targetPaise).coerceIn(0L, 100L).toInt()
                } else 0
                GoalKind.AVOID -> if (goal.targetPaise > 0L) {
                    ((avoided * 100L) / goal.targetPaise).coerceIn(0L, 100L).toInt()
                } else 0
            }

            val totals = GoalWithTotals(
                id = goal.id,
                name = goal.name,
                kind = kind,
                targetPaise = goal.targetPaise,
                savedPaise = saved,
                avoidedPaise = avoided,
                usedPaise = used,
                availablePaise = saved,
                percent = percent
            )

            GoalWithEntries(
                goal = goal.toDomain(),
                totals = totals,
                entries = details
            )
        }
    }

    override suspend fun addSetAside(goalId: Long, amountPaise: Long, note: String?, occurredAt: Long): GoalResult<GoalEntry> {
        val goal = goalDao.getGoalById(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        if (goal.kind != GoalKind.SAVE.name) {
            return GoalResult.Error(GoalError.WrongGoalKind(GoalKind.valueOf(goal.kind), "SET_ASIDE only allowed on SAVE goals"))
        }
        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val cleanNote = note?.trim()?.ifBlank { null }
        if (cleanNote != null && cleanNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(cleanNote.length, MAX_NOTE_LENGTH))
        }

        val entity = GoalEntryEntity(
            id = 0L,
            goalId = goalId,
            type = "SET_ASIDE",
            amountPaise = amountPaise,
            note = cleanNote,
            occurredAt = occurredAt,
            linkedTransactionId = null,
            isDeleted = 0,
            createdAt = occurredAt,
            updatedAt = occurredAt
        )
        val id = goalDao.insertEntry(entity)
        return GoalResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun addAvoided(goalId: Long, amountPaise: Long, note: String?, occurredAt: Long): GoalResult<GoalEntry> {
        val goal = goalDao.getGoalById(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val cleanNote = note?.trim()?.ifBlank { null }
        if (cleanNote != null && cleanNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(cleanNote.length, MAX_NOTE_LENGTH))
        }

        val entity = GoalEntryEntity(
            id = 0L,
            goalId = goalId,
            type = "AVOIDED",
            amountPaise = amountPaise,
            note = cleanNote,
            occurredAt = occurredAt,
            linkedTransactionId = null,
            isDeleted = 0,
            createdAt = occurredAt,
            updatedAt = occurredAt
        )
        val id = goalDao.insertEntry(entity)
        return GoalResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun useLinkExpense(goalId: Long, expenseId: Long, amountPaise: Long, note: String?, occurredAt: Long): GoalResult<GoalEntry> {
        val goal = goalDao.getGoalById(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        if (goal.kind != GoalKind.SAVE.name) {
            return GoalResult.Error(GoalError.WrongGoalKind(GoalKind.valueOf(goal.kind), "USED entries only allowed on SAVE goals"))
        }

        val tx = transactionDao.getById(expenseId)
        if (tx == null || tx.isDeleted != 0 || tx.recordType != RecordType.EXPENSE) {
            return GoalResult.Error(GoalError.ExpenseNotFound(expenseId))
        }

        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val otherLinked = goalDao.getActiveEntriesByTransactionId(expenseId)
        val otherLinkedTotal = otherLinked.sumOf { it.amountPaise }
        val remainingExpensePaise = (tx.amountPaise - otherLinkedTotal).coerceAtLeast(0L)

        if (amountPaise > remainingExpensePaise) {
            return GoalResult.Error(GoalError.AmountExceedsExpense(amountPaise, tx.amountPaise, remainingExpensePaise))
        }

        val available = calculateAvailable(goalId)
        if (amountPaise > available) {
            return GoalResult.Error(GoalError.InsufficientBalance(available, amountPaise))
        }

        val cleanNote = note?.trim()?.ifBlank { null }
        if (cleanNote != null && cleanNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(cleanNote.length, MAX_NOTE_LENGTH))
        }

        val entity = GoalEntryEntity(
            id = 0L,
            goalId = goalId,
            type = "USED",
            amountPaise = amountPaise,
            note = cleanNote,
            occurredAt = occurredAt,
            linkedTransactionId = expenseId,
            isDeleted = 0,
            createdAt = occurredAt,
            updatedAt = occurredAt
        )
        val id = goalDao.insertEntry(entity)
        return GoalResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun useNewExpense(
        goalId: Long,
        amountPaise: Long,
        merchantName: String,
        category: String,
        paymentMode: String,
        note: String?,
        occurredAt: Long
    ): GoalResult<GoalEntry> {
        val goal = goalDao.getGoalById(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        if (goal.kind != GoalKind.SAVE.name) {
            return GoalResult.Error(GoalError.WrongGoalKind(GoalKind.valueOf(goal.kind), "USED entries only allowed on SAVE goals"))
        }

        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val available = calculateAvailable(goalId)
        if (amountPaise > available) {
            return GoalResult.Error(GoalError.InsufficientBalance(available, amountPaise))
        }

        val cleanNote = note?.trim()?.ifBlank { null }
        if (cleanNote != null && cleanNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(cleanNote.length, MAX_NOTE_LENGTH))
        }

        var createdEntry: GoalEntryEntity? = null

        runInTransaction {
            val tx = TransactionEntity(
                id = 0L,
                amountPaise = amountPaise,
                recordType = RecordType.EXPENSE,
                dateTime = occurredAt,
                merchantName = merchantName.trim().ifBlank { "Expense" },
                category = category.trim().ifBlank { "Others" },
                categorySource = "USER",
                paymentMode = paymentMode,
                note = cleanNote,
                accountNumber = null,
                bankName = null,
                reference = null,
                transactionHash = "manual_${occurredAt}_${UUID.randomUUID().toString().take(8)}",
                source = TransactionSource.MANUAL_CASH,
                reviewStatus = ReviewStatus.CONFIRMED,
                uncertaintyBits = 0,
                createdAt = occurredAt,
                updatedAt = occurredAt,
                isDeleted = 0
            )
            val txId = transactionDao.insert(tx)

            val entry = GoalEntryEntity(
                id = 0L,
                goalId = goalId,
                type = "USED",
                amountPaise = amountPaise,
                note = cleanNote,
                occurredAt = occurredAt,
                linkedTransactionId = txId,
                isDeleted = 0,
                createdAt = occurredAt,
                updatedAt = occurredAt
            )
            val entryId = goalDao.insertEntry(entry)
            createdEntry = entry.copy(id = entryId)
        }

        return GoalResult.Success(createdEntry!!.toDomain())
    }

    override suspend fun useTakeOut(goalId: Long, amountPaise: Long, note: String?, occurredAt: Long): GoalResult<GoalEntry> {
        val goal = goalDao.getGoalById(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        if (goal.kind != GoalKind.SAVE.name) {
            return GoalResult.Error(GoalError.WrongGoalKind(GoalKind.valueOf(goal.kind), "USED entries only allowed on SAVE goals"))
        }

        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val available = calculateAvailable(goalId)
        if (amountPaise > available) {
            return GoalResult.Error(GoalError.InsufficientBalance(available, amountPaise))
        }

        val cleanNote = note?.trim()?.ifBlank { null }
        if (cleanNote != null && cleanNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(cleanNote.length, MAX_NOTE_LENGTH))
        }

        val entity = GoalEntryEntity(
            id = 0L,
            goalId = goalId,
            type = "USED",
            amountPaise = amountPaise,
            note = cleanNote,
            occurredAt = occurredAt,
            linkedTransactionId = null,
            isDeleted = 0,
            createdAt = occurredAt,
            updatedAt = occurredAt
        )
        val id = goalDao.insertEntry(entity)
        return GoalResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun editEntry(
        entryId: Long,
        newGoalId: Long?,
        newAmountPaise: Long?,
        newNote: String?,
        newOccurredAt: Long?,
        now: Long
    ): GoalResult<GoalEntry> {
        val existing = goalDao.getEntryById(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))

        val targetGoalId = newGoalId ?: existing.goalId
        val targetAmount = newAmountPaise ?: existing.amountPaise
        val targetNote = if (newNote !== null) newNote.trim().ifBlank { null } else existing.note
        val targetOccurredAt = newOccurredAt ?: existing.occurredAt

        if (targetAmount <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(targetAmount))
        }
        if (targetAmount > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(targetAmount, MAX_AMOUNT_PAISE))
        }
        if (targetNote != null && targetNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(targetNote.length, MAX_NOTE_LENGTH))
        }

        if (targetGoalId != existing.goalId) {
            // Validating move across goals
            if (existing.type == "SET_ASIDE" && existing.isDeleted == 0) {
                val sourceAvail = calculateAvailable(existing.goalId)
                if (existing.amountPaise > sourceAvail) {
                    return GoalResult.Error(GoalError.InsufficientBalance(sourceAvail, existing.amountPaise))
                }
            }

            val destGoal = goalDao.getGoalById(targetGoalId) ?: return GoalResult.Error(GoalError.GoalNotFound(targetGoalId))
            if ((existing.type == "SET_ASIDE" || existing.type == "USED") && destGoal.kind != GoalKind.SAVE.name) {
                return GoalResult.Error(GoalError.WrongGoalKind(GoalKind.valueOf(destGoal.kind)))
            }

            if (existing.type == "USED" && existing.isDeleted == 0) {
                val destAvail = calculateAvailable(targetGoalId)
                if (targetAmount > destAvail) {
                    return GoalResult.Error(GoalError.InsufficientBalance(destAvail, targetAmount))
                }
            }
        } else {
            // Same goal
            if (existing.type == "SET_ASIDE" && existing.isDeleted == 0 && targetAmount < existing.amountPaise) {
                val reduction = existing.amountPaise - targetAmount
                val currentAvail = calculateAvailable(existing.goalId)
                if (reduction > currentAvail) {
                    return GoalResult.Error(GoalError.InsufficientBalance(currentAvail, reduction))
                }
            }

            if (existing.type == "USED" && existing.isDeleted == 0) {
                if (existing.linkedTransactionId != null) {
                    val tx = transactionDao.getById(existing.linkedTransactionId)
                    if (tx != null) {
                        val otherLinks = goalDao.getActiveEntriesByTransactionId(existing.linkedTransactionId)
                            .filter { it.id != existing.id }
                            .sumOf { it.amountPaise }
                        val remainingExpense = (tx.amountPaise - otherLinks).coerceAtLeast(0L)
                        if (targetAmount > remainingExpense) {
                            return GoalResult.Error(GoalError.AmountExceedsExpense(targetAmount, tx.amountPaise, remainingExpense))
                        }
                    }
                }

                val currentAvail = calculateAvailable(existing.goalId)
                val maxAllowed = currentAvail + existing.amountPaise
                if (targetAmount > maxAllowed) {
                    return GoalResult.Error(GoalError.InsufficientBalance(currentAvail, targetAmount - existing.amountPaise))
                }
            }
        }

        val updated = existing.copy(
            goalId = targetGoalId,
            amountPaise = targetAmount,
            note = targetNote,
            occurredAt = targetOccurredAt,
            updatedAt = now
        )
        goalDao.updateEntry(updated)
        return GoalResult.Success(updated.toDomain())
    }

    override suspend fun removeUsed(entryId: Long, now: Long): GoalResult<Unit> {
        val existing = goalDao.getEntryById(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))
        if (existing.type != "USED") {
            return GoalResult.Error(GoalError.WrongGoalKind(GoalKind.SAVE, "Only USED entries can be removed with give money back"))
        }
        if (existing.isDeleted != 0) {
            return GoalResult.Success(Unit)
        }
        goalDao.softDeleteEntry(entryId, now)
        return GoalResult.Success(Unit)
    }

    override suspend fun deleteEntry(entryId: Long, now: Long): GoalResult<Unit> {
        val existing = goalDao.getEntryById(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))
        if (existing.isDeleted != 0) {
            return GoalResult.Success(Unit)
        }

        if (existing.type == "SET_ASIDE") {
            val available = calculateAvailable(existing.goalId)
            if (existing.amountPaise > available) {
                return GoalResult.Error(GoalError.InsufficientBalance(available, existing.amountPaise))
            }
        }

        goalDao.softDeleteEntry(entryId, now)
        return GoalResult.Success(Unit)
    }

    override suspend fun undeleteEntry(entryId: Long, now: Long): GoalResult<Unit> {
        val existing = goalDao.getEntryById(entryId) ?: return GoalResult.Error(GoalError.EntryNotFound(entryId))
        if (existing.isDeleted == 0) {
            return GoalResult.Success(Unit)
        }

        if (existing.type == "USED") {
            val available = calculateAvailable(existing.goalId)
            if (existing.amountPaise > available) {
                return GoalResult.Error(GoalError.InsufficientBalance(available, existing.amountPaise))
            }
        }

        goalDao.restoreEntry(entryId, now)
        return GoalResult.Success(Unit)
    }

    private fun GoalEntity.toDomain(): Goal {
        return Goal(
            id = id,
            name = name,
            nameKey = nameKey,
            kind = GoalKind.valueOf(kind),
            targetPaise = targetPaise,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun GoalEntryEntity.toDomain(): GoalEntry {
        return GoalEntry(
            id = id,
            goalId = goalId,
            type = EntryType.valueOf(type),
            amountPaise = amountPaise,
            note = note,
            occurredAt = occurredAt,
            linkedTransactionId = linkedTransactionId,
            isDeleted = isDeleted != 0,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
