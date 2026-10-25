package com.autoflow.app.presentation.viewmodel

import com.autoflow.app.data.repository.GoalRepository
import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.domain.goals.GoalEntry
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.domain.goals.GoalWithTotals
import com.autoflow.app.domain.goals.normalizeGoalNameKey
import com.autoflow.app.presentation.util.GoalDeadlineFormatter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GoalsUiStateTest {

    private val kolkataZone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val fixedNow: Long = ZonedDateTime.of(2026, 10, 25, 12, 0, 0, 0, kolkataZone)
        .toInstant()
        .toEpochMilli()

    @Test
    fun testEmptyState() {
        val emptyUiState = GoalsUiState(goals = emptyList())
        assertTrue(emptyUiState.isEmpty)

        val sampleGoal = Goal(
            id = 1L,
            name = "Goa",
            targetPaise = 5000000L,
            createdAt = fixedNow
        )
        val sampleWithTotals = GoalWithTotals(
            goal = sampleGoal,
            totalPaise = 1000000L,
            reached = false
        )
        val populatedUiState = GoalsUiState(goals = listOf(sampleWithTotals))
        assertFalse(populatedUiState.isEmpty)
        assertEquals(1, populatedUiState.goals.size)
    }

    @Test
    fun testCardLineWordingEveryDeadlineCase() {
        // 1. Reached
        val reachedLine = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = null,
            isReached = true,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("Reached", reachedLine)

        // 2. No deadline
        val noDeadlineLine = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = null,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("No deadline", noDeadlineLine)

        // 3. Due today
        val todayDeadline = ZonedDateTime.of(2026, 10, 25, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val dueTodayLine = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = todayDeadline,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("Due today", dueTodayLine)

        // 4. Deadline passed
        val pastDeadline = ZonedDateTime.of(2026, 10, 24, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val passedLine = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = pastDeadline,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("Deadline passed", passedLine)

        // 5. 1 day left
        val tomorrow = ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val oneDayLeftLine = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = tomorrow,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("By 26 Oct · 1 day left", oneDayLeftLine)

        // 6. 67 days left (31 Dec 2026)
        val dec31 = ZonedDateTime.of(2026, 12, 31, 0, 0, 0, 0, kolkataZone).toInstant().toEpochMilli()
        val daysLeftLine = GoalDeadlineFormatter.formatDeadlineSubtitle(
            deadlineAt = dec31,
            isReached = false,
            now = fixedNow,
            zoneId = kolkataZone
        )
        assertEquals("By 31 Dec · 67 days left", daysLeftLine)
    }

    @Test
    fun testReachedState() {
        val goal = Goal(id = 1L, name = "New Phone", targetPaise = 5000000L, createdAt = fixedNow)

        val unreached = GoalWithTotals(goal = goal, totalPaise = 2500000L, reached = false)
        assertFalse(unreached.reached)
        assertEquals(50, unreached.percent)

        val reached = GoalWithTotals(goal = goal, totalPaise = 5000000L, reached = true)
        assertTrue(reached.reached)
        assertEquals(100, reached.percent)

        val overReached = GoalWithTotals(goal = goal, totalPaise = 6000000L, reached = true)
        assertTrue(overReached.reached)
        assertEquals(100, overReached.percent)
    }

    @Test
    fun testSavedDisabledUntilValid() {
        // Bad amount (<= 0)
        val zeroAmount = validateSavedEntry(amountPaise = 0L, note = "Skipped takeout")
        assertFalse(zeroAmount.isValid)
        assertEquals("Amount must be greater than zero", zeroAmount.amountError)

        // Blank note
        val blankNote = validateSavedEntry(amountPaise = 25000L, note = "   ")
        assertFalse(blankNote.isValid)
        assertEquals("Note can't be blank", blankNote.noteError)

        // Note too long (> 100)
        val tooLongNote = validateSavedEntry(amountPaise = 25000L, note = "a".repeat(101))
        assertFalse(tooLongNote.isValid)
        assertTrue(tooLongNote.noteError!!.contains("too long"))

        // Valid
        val valid = validateSavedEntry(amountPaise = 25000L, note = "Skipped ordering dessert")
        assertTrue(valid.isValid)
    }

    @Test
    fun testNewGoalValidation() {
        // Blank name
        val blankName = validateGoalForm(name = "  ", targetPaise = 500000L)
        assertFalse(blankName.isValid)
        assertEquals("Name can't be blank", blankName.nameError)

        // Name too long (> 30)
        val tooLongName = validateGoalForm(name = "a".repeat(31), targetPaise = 500000L)
        assertFalse(tooLongName.isValid)
        assertTrue(tooLongName.nameError!!.contains("too long"))

        // Target zero
        val zeroTarget = validateGoalForm(name = "Goa Trip", targetPaise = 0L)
        assertFalse(zeroTarget.isValid)
        assertEquals("Amount must be greater than zero", zeroTarget.targetError)

        // Valid
        val valid = validateGoalForm(name = "Goa Trip", targetPaise = 5000000L)
        assertTrue(valid.isValid)
    }

    @Test
    fun testEditGoalValidation() {
        val valid = validateGoalForm(name = "Updated Goa Trip", targetPaise = 6000000L)
        assertTrue(valid.isValid)

        val invalid = validateGoalForm(name = "", targetPaise = 6000000L)
        assertFalse(invalid.isValid)
    }

    @Test
    fun testDeleteGoalWithUndo() = runBlocking {
        val repo = InMemoryTestGoalRepository()
        val created = repo.createGoal(name = "Concert Ticket", targetPaise = 300000L, deadlineAt = null, now = fixedNow)
        assertTrue(created.isSuccess)
        val goalId = created.getOrThrow().id

        // Delete goal
        val deleteResult = repo.deleteGoal(goalId, now = fixedNow)
        assertTrue(deleteResult.isSuccess)
        assertEquals(0, repo.getAllGoals().size)

        // Undo delete
        val undoResult = repo.undeleteGoal(goalId, now = fixedNow)
        assertTrue(undoResult.isSuccess)
        assertEquals(1, repo.getAllGoals().size)
        assertEquals("Concert Ticket", repo.getAllGoals()[0].name)
    }

    @Test
    fun testUndoWithNameTakenCollision() = runBlocking {
        val repo = InMemoryTestGoalRepository()
        // 1. Create Goal A ("Goa")
        val goalA = repo.createGoal(name = "Goa", targetPaise = 5000000L, deadlineAt = null, now = fixedNow).getOrThrow()

        // 2. Delete Goal A
        repo.deleteGoal(goalA.id, now = fixedNow)

        // 3. Create Goal B also named "Goa"
        val goalB = repo.createGoal(name = "Goa", targetPaise = 2000000L, deadlineAt = null, now = fixedNow).getOrThrow()
        assertEquals(1, repo.getAllGoals().size)

        // 4. Try to undelete Goal A -> Must fail with NameTaken error
        val undoResult = repo.undeleteGoal(goalA.id, now = fixedNow)
        assertTrue(undoResult.isError)
        val err = (undoResult as GoalResult.Error).error
        assertTrue(err is GoalError.NameTaken)
        assertEquals("Goa", (err as GoalError.NameTaken).name)
    }

    @Test
    fun testEditEntryDeleteWithUndo() = runBlocking {
        val repo = InMemoryTestGoalRepository()
        val goal = repo.createGoal(name = "Camera", targetPaise = 5000000L, deadlineAt = null, now = fixedNow).getOrThrow()

        // Add saved entry
        val entry = repo.addSaved(goal.id, 50000L, "Cooked at home", fixedNow, fixedNow).getOrThrow()
        assertEquals(1, repo.getEntriesForGoal(goal.id).size)

        // Edit entry
        val edited = repo.editEntry(entry.id, 60000L, "Cooked meal at home", fixedNow, fixedNow)
        assertTrue(edited.isSuccess)
        assertEquals(60000L, repo.getEntryById(entry.id)!!.amountPaise)
        assertEquals("Cooked meal at home", repo.getEntryById(entry.id)!!.note)

        // Delete entry
        val deleteResult = repo.deleteEntry(entry.id, fixedNow)
        assertTrue(deleteResult.isSuccess)
        assertEquals(0, repo.getEntriesForGoal(goal.id).size)

        // Undo delete entry
        val undoResult = repo.undeleteEntry(entry.id, fixedNow)
        assertTrue(undoResult.isSuccess)
        assertEquals(1, repo.getEntriesForGoal(goal.id).size)
        assertEquals("Cooked meal at home", repo.getEntriesForGoal(goal.id)[0].note)
    }

    private class InMemoryTestGoalRepository : GoalRepository {
        private val goals = mutableListOf<Goal>()
        private val entries = mutableListOf<GoalEntry>()
        private var nextGoalId = 1L
        private var nextEntryId = 1L

        override suspend fun createGoal(
            name: String,
            targetPaise: Long,
            deadlineAt: Long?,
            now: Long
        ): GoalResult<Goal> {
            val trimmed = name.trim()
            if (trimmed.isEmpty()) return GoalResult.Error(GoalError.BlankName)
            if (targetPaise <= 0L) return GoalResult.Error(GoalError.BadAmount(targetPaise))
            val nameKey = normalizeGoalNameKey(trimmed)
            if (goals.any { !it.isDeleted && it.nameKey == nameKey }) {
                return GoalResult.Error(GoalError.NameTaken(trimmed))
            }
            val goal = Goal(
                id = nextGoalId++,
                name = trimmed,
                nameKey = nameKey,
                targetPaise = targetPaise,
                deadlineAt = deadlineAt,
                isDeleted = false,
                createdAt = now,
                updatedAt = now
            )
            goals.add(goal)
            return GoalResult.Success(goal)
        }

        override suspend fun updateGoal(
            id: Long,
            name: String,
            targetPaise: Long,
            deadlineAt: Long?,
            now: Long
        ): GoalResult<Goal> {
            val idx = goals.indexOfFirst { it.id == id && !it.isDeleted }
            if (idx == -1) return GoalResult.Error(GoalError.GoalNotFound(id))
            val trimmed = name.trim()
            val nameKey = normalizeGoalNameKey(trimmed)
            if (goals.any { it.id != id && !it.isDeleted && it.nameKey == nameKey }) {
                return GoalResult.Error(GoalError.NameTaken(trimmed))
            }
            val updated = goals[idx].copy(
                name = trimmed,
                nameKey = nameKey,
                targetPaise = targetPaise,
                deadlineAt = deadlineAt,
                updatedAt = now
            )
            goals[idx] = updated
            return GoalResult.Success(updated)
        }

        override suspend fun deleteGoal(id: Long, now: Long): GoalResult<Unit> {
            val idx = goals.indexOfFirst { it.id == id && !it.isDeleted }
            if (idx == -1) return GoalResult.Error(GoalError.GoalNotFound(id))
            goals[idx] = goals[idx].copy(isDeleted = true, updatedAt = now)
            return GoalResult.Success(Unit)
        }

        override suspend fun undeleteGoal(id: Long, now: Long): GoalResult<Unit> {
            val idx = goals.indexOfFirst { it.id == id && it.isDeleted }
            if (idx == -1) return GoalResult.Error(GoalError.GoalNotFound(id))
            val nameKey = goals[idx].nameKey
            if (goals.any { it.id != id && !it.isDeleted && it.nameKey == nameKey }) {
                return GoalResult.Error(GoalError.NameTaken(goals[idx].name))
            }
            goals[idx] = goals[idx].copy(isDeleted = false, updatedAt = now)
            return GoalResult.Success(Unit)
        }

        override suspend fun getGoalById(id: Long): Goal? = goals.find { it.id == id && !it.isDeleted }

        override suspend fun getAllGoals(): List<Goal> = goals.filter { !it.isDeleted }

        override fun observeGoalsWithTotals(): Flow<List<GoalWithTotals>> {
            val activeGoals = goals.filter { !it.isDeleted }
            val list = activeGoals.map { g ->
                val total = entries.filter { it.goalId == g.id && !it.isDeleted }.sumOf { it.amountPaise }
                GoalWithTotals(g, total, total >= g.targetPaise)
            }
            return flowOf(list)
        }

        override suspend fun addSaved(
            goalId: Long,
            amountPaise: Long,
            note: String,
            occurredAt: Long,
            now: Long
        ): GoalResult<GoalEntry> {
            val goal = getGoalById(goalId) ?: return GoalResult.Error(GoalError.GoalNotFound(goalId))
            val entry = GoalEntry(
                id = nextEntryId++,
                goalId = goalId,
                amountPaise = amountPaise,
                note = note,
                occurredAt = occurredAt,
                isDeleted = false,
                createdAt = now,
                updatedAt = now
            )
            entries.add(entry)
            return GoalResult.Success(entry)
        }

        override suspend fun editEntry(
            id: Long,
            amountPaise: Long,
            note: String,
            occurredAt: Long,
            now: Long
        ): GoalResult<GoalEntry> {
            val idx = entries.indexOfFirst { it.id == id && !it.isDeleted }
            if (idx == -1) return GoalResult.Error(GoalError.EntryNotFound(id))
            val updated = entries[idx].copy(amountPaise = amountPaise, note = note, updatedAt = now)
            entries[idx] = updated
            return GoalResult.Success(updated)
        }

        override suspend fun deleteEntry(id: Long, now: Long): GoalResult<Unit> {
            val idx = entries.indexOfFirst { it.id == id && !it.isDeleted }
            if (idx == -1) return GoalResult.Error(GoalError.EntryNotFound(id))
            entries[idx] = entries[idx].copy(isDeleted = true, updatedAt = now)
            return GoalResult.Success(Unit)
        }

        override suspend fun undeleteEntry(id: Long, now: Long): GoalResult<Unit> {
            val idx = entries.indexOfFirst { it.id == id && it.isDeleted }
            if (idx == -1) return GoalResult.Error(GoalError.EntryNotFound(id))
            entries[idx] = entries[idx].copy(isDeleted = false, updatedAt = now)
            return GoalResult.Success(Unit)
        }

        override suspend fun getEntryById(id: Long): GoalEntry? = entries.find { it.id == id && !it.isDeleted }

        fun getEntriesForGoal(goalId: Long): List<GoalEntry> = entries.filter { it.goalId == goalId && !it.isDeleted }

        override fun observeEntries(goalId: Long): Flow<List<GoalEntry>> {
            return flowOf(getEntriesForGoal(goalId))
        }
    }
}
