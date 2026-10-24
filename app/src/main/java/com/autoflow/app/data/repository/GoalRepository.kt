package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.GoalDao
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.GoalEntryEntity
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.domain.goals.GoalEntry
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.domain.goals.GoalWithTotals
import com.autoflow.app.domain.goals.MAX_AMOUNT_PAISE
import com.autoflow.app.domain.goals.MAX_GOAL_NAME_LENGTH
import com.autoflow.app.domain.goals.MAX_NOTE_LENGTH
import com.autoflow.app.domain.goals.normalizeGoalNameKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

interface GoalRepository {
    suspend fun createGoal(
        name: String,
        targetPaise: Long,
        deadlineAt: Long? = null,
        now: Long = System.currentTimeMillis()
    ): GoalResult<Goal>

    suspend fun updateGoal(
        id: Long,
        name: String,
        targetPaise: Long,
        deadlineAt: Long? = null,
        now: Long = System.currentTimeMillis()
    ): GoalResult<Goal>

    suspend fun deleteGoal(
        id: Long,
        now: Long = System.currentTimeMillis()
    ): GoalResult<Unit>

    suspend fun undeleteGoal(
        id: Long,
        now: Long = System.currentTimeMillis()
    ): GoalResult<Unit>

    suspend fun getGoalById(id: Long): Goal?

    suspend fun getAllGoals(): List<Goal>

    fun observeGoalsWithTotals(): Flow<List<GoalWithTotals>>

    suspend fun addSaved(
        goalId: Long,
        amountPaise: Long,
        note: String,
        occurredAt: Long = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun editEntry(
        id: Long,
        amountPaise: Long,
        note: String,
        occurredAt: Long = System.currentTimeMillis(),
        now: Long = System.currentTimeMillis()
    ): GoalResult<GoalEntry>

    suspend fun deleteEntry(
        id: Long,
        now: Long = System.currentTimeMillis()
    ): GoalResult<Unit>

    suspend fun undeleteEntry(
        id: Long,
        now: Long = System.currentTimeMillis()
    ): GoalResult<Unit>

    suspend fun getEntryById(id: Long): GoalEntry?

    fun observeEntries(goalId: Long): Flow<List<GoalEntry>>
}

fun startOfTodayEpochMillis(
    now: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault()
): Long {
    return Instant.ofEpochMilli(now)
        .atZone(zoneId)
        .toLocalDate()
        .atStartOfDay(zoneId)
        .toInstant()
        .toEpochMilli()
}

class DefaultGoalRepository(
    private val goalDao: GoalDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : GoalRepository {

    override suspend fun createGoal(
        name: String,
        targetPaise: Long,
        deadlineAt: Long?,
        now: Long
    ): GoalResult<Goal> {
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

        if (deadlineAt != null) {
            val startOfToday = startOfTodayEpochMillis(now, zoneId)
            if (deadlineAt < startOfToday) {
                return GoalResult.Error(GoalError.PastDeadline(deadlineAt, startOfToday))
            }
        }

        val nameKey = normalizeGoalNameKey(trimmed)
        val existing = goalDao.getGoalByNameKey(nameKey)
        if (existing != null) {
            return GoalResult.Error(GoalError.NameTaken(trimmed))
        }

        val entity = GoalEntity(
            id = 0L,
            name = trimmed,
            nameKey = nameKey,
            targetPaise = targetPaise,
            deadlineAt = deadlineAt,
            isDeleted = 0,
            createdAt = now,
            updatedAt = now
        )
        val id = goalDao.insertGoal(entity)
        return GoalResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun updateGoal(
        id: Long,
        name: String,
        targetPaise: Long,
        deadlineAt: Long?,
        now: Long
    ): GoalResult<Goal> {
        val existing = goalDao.getGoalById(id) ?: return GoalResult.Error(GoalError.GoalNotFound(id))
        if (existing.isDeleted != 0) {
            return GoalResult.Error(GoalError.GoalDeleted(id))
        }

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

        // Deadline check: only validate if set or changed; unchanged old deadline is allowed
        if (deadlineAt != existing.deadlineAt && deadlineAt != null) {
            val startOfToday = startOfTodayEpochMillis(now, zoneId)
            if (deadlineAt < startOfToday) {
                return GoalResult.Error(GoalError.PastDeadline(deadlineAt, startOfToday))
            }
        }

        val nameKey = normalizeGoalNameKey(trimmed)
        val existingWithKey = goalDao.getGoalByNameKey(nameKey)
        if (existingWithKey != null && existingWithKey.id != id) {
            return GoalResult.Error(GoalError.NameTaken(trimmed))
        }

        val updated = existing.copy(
            name = trimmed,
            nameKey = nameKey,
            targetPaise = targetPaise,
            deadlineAt = deadlineAt,
            updatedAt = now
        )
        goalDao.updateGoal(updated)
        return GoalResult.Success(updated.toDomain())
    }

    override suspend fun deleteGoal(id: Long, now: Long): GoalResult<Unit> {
        val existing = goalDao.getGoalById(id) ?: return GoalResult.Error(GoalError.GoalNotFound(id))
        if (existing.isDeleted != 0) {
            return GoalResult.Success(Unit)
        }

        val tombstoneKey = "deleted_${id}_${UUID.randomUUID()}"
        goalDao.softDeleteGoal(id, tombstoneKey, now)
        return GoalResult.Success(Unit)
    }

    override suspend fun undeleteGoal(id: Long, now: Long): GoalResult<Unit> {
        val existing = goalDao.getGoalById(id) ?: return GoalResult.Error(GoalError.GoalNotFound(id))
        if (existing.isDeleted == 0) {
            return GoalResult.Success(Unit)
        }

        val restoredKey = normalizeGoalNameKey(existing.name)
        val conflicting = goalDao.getGoalByNameKey(restoredKey)
        if (conflicting != null && conflicting.id != id) {
            return GoalResult.Error(GoalError.NameTaken(existing.name))
        }

        goalDao.undeleteGoal(id, restoredKey, now)
        return GoalResult.Success(Unit)
    }

    override suspend fun getGoalById(id: Long): Goal? {
        return goalDao.getGoalById(id)?.toDomain()
    }

    override suspend fun getAllGoals(): List<Goal> {
        return goalDao.getAllGoals().map { it.toDomain() }
    }

    override fun observeGoalsWithTotals(): Flow<List<GoalWithTotals>> {
        return goalDao.observeGoalsRaw().map { rawList ->
            rawList.map { raw ->
                val goal = Goal(
                    id = raw.id,
                    name = raw.name,
                    nameKey = raw.name_key,
                    targetPaise = raw.target_paise,
                    deadlineAt = raw.deadline_at,
                    isDeleted = raw.is_deleted != 0,
                    createdAt = raw.created_at,
                    updatedAt = raw.updated_at
                )
                GoalWithTotals(
                    goal = goal,
                    totalPaise = raw.total_paise,
                    reached = raw.total_paise >= raw.target_paise
                )
            }
        }
    }

    override suspend fun addSaved(
        goalId: Long,
        amountPaise: Long,
        note: String,
        occurredAt: Long,
        now: Long
    ): GoalResult<GoalEntry> {
        val goal = goalDao.getGoalById(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
        if (goal.isDeleted != 0) {
            return GoalResult.Error(GoalError.GoalDeleted(goalId))
        }

        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val trimmedNote = note.trim()
        if (trimmedNote.isEmpty()) {
            return GoalResult.Error(GoalError.BlankNote)
        }
        if (trimmedNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(trimmedNote.length, MAX_NOTE_LENGTH))
        }

        val entity = GoalEntryEntity(
            id = 0L,
            goalId = goalId,
            amountPaise = amountPaise,
            note = trimmedNote,
            occurredAt = occurredAt,
            isDeleted = 0,
            createdAt = now,
            updatedAt = now
        )
        val id = goalDao.insertEntry(entity)
        return GoalResult.Success(entity.copy(id = id).toDomain())
    }

    override suspend fun editEntry(
        id: Long,
        amountPaise: Long,
        note: String,
        occurredAt: Long,
        now: Long
    ): GoalResult<GoalEntry> {
        val existing = goalDao.getEntryById(id) ?: return GoalResult.Error(GoalError.EntryNotFound(id))

        if (amountPaise <= 0L) {
            return GoalResult.Error(GoalError.BadAmount(amountPaise))
        }
        if (amountPaise > MAX_AMOUNT_PAISE) {
            return GoalResult.Error(GoalError.OverLimit(amountPaise, MAX_AMOUNT_PAISE))
        }

        val trimmedNote = note.trim()
        if (trimmedNote.isEmpty()) {
            return GoalResult.Error(GoalError.BlankNote)
        }
        if (trimmedNote.length > MAX_NOTE_LENGTH) {
            return GoalResult.Error(GoalError.NoteTooLong(trimmedNote.length, MAX_NOTE_LENGTH))
        }

        val updated = existing.copy(
            amountPaise = amountPaise,
            note = trimmedNote,
            occurredAt = occurredAt,
            updatedAt = now
        )
        goalDao.updateEntry(updated)
        return GoalResult.Success(updated.toDomain())
    }

    override suspend fun deleteEntry(id: Long, now: Long): GoalResult<Unit> {
        val existing = goalDao.getEntryById(id) ?: return GoalResult.Error(GoalError.EntryNotFound(id))
        if (existing.isDeleted != 0) {
            return GoalResult.Success(Unit)
        }
        goalDao.softDeleteEntry(id, now)
        return GoalResult.Success(Unit)
    }

    override suspend fun undeleteEntry(id: Long, now: Long): GoalResult<Unit> {
        val existing = goalDao.getEntryById(id) ?: return GoalResult.Error(GoalError.EntryNotFound(id))
        if (existing.isDeleted == 0) {
            return GoalResult.Success(Unit)
        }
        goalDao.undeleteEntry(id, now)
        return GoalResult.Success(Unit)
    }

    override suspend fun getEntryById(id: Long): GoalEntry? {
        return goalDao.getEntryById(id)?.toDomain()
    }

    override fun observeEntries(goalId: Long): Flow<List<GoalEntry>> {
        return goalDao.observeEntriesForGoal(goalId).map { entries ->
            entries.map { it.toDomain() }
        }
    }

    private fun GoalEntity.toDomain(): Goal {
        return Goal(
            id = id,
            name = name,
            nameKey = nameKey,
            targetPaise = targetPaise,
            deadlineAt = deadlineAt,
            isDeleted = isDeleted != 0,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

    private fun GoalEntryEntity.toDomain(): GoalEntry {
        return GoalEntry(
            id = id,
            goalId = goalId,
            amountPaise = amountPaise,
            note = note,
            occurredAt = occurredAt,
            isDeleted = isDeleted != 0,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }
}
