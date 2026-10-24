package com.autoflow.app.domain.goals

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Repository interface for Goals domain storage.
 * Supports suspend functions and Flow observation.
 */
interface GoalsRepository {
    suspend fun getGoal(goalId: Long): Goal?
    suspend fun getAllGoals(): List<Goal>
    fun observeGoals(): Flow<List<Goal>>
    suspend fun saveGoal(goal: Goal): Goal

    suspend fun getEntry(entryId: Long): GoalEntry?
    suspend fun getEntries(goalId: Long): List<GoalEntry>
    fun observeEntries(goalId: Long): Flow<List<GoalEntry>>
    suspend fun saveEntry(entry: GoalEntry): GoalEntry
    suspend fun getEntriesByTransaction(transactionId: Long): List<GoalEntry>
}

/**
 * In-memory repository implementation for testing and previews.
 * Thread-safe via Mutex and state flows.
 */
class InMemoryGoalsRepository : GoalsRepository {
    private val mutex = Mutex()
    private val goals = LinkedHashMap<Long, Goal>()
    private val entries = LinkedHashMap<Long, GoalEntry>()

    private val _goalsFlow = MutableStateFlow<List<Goal>>(emptyList())
    private val _entriesFlow = MutableStateFlow<List<GoalEntry>>(emptyList())

    private var nextGoalId: Long = 1L
    private var nextEntryId: Long = 1L

    override suspend fun getGoal(goalId: Long): Goal? = mutex.withLock {
        goals[goalId]
    }

    override suspend fun getAllGoals(): List<Goal> = mutex.withLock {
        goals.values.toList()
    }

    override fun observeGoals(): Flow<List<Goal>> = _goalsFlow.asStateFlow()

    override suspend fun saveGoal(goal: Goal): Goal = mutex.withLock {
        val assignedGoal = if (goal.id == 0L) {
            val id = nextGoalId++
            goal.copy(id = id)
        } else {
            goal
        }
        goals[assignedGoal.id] = assignedGoal
        _goalsFlow.value = goals.values.toList()
        assignedGoal
    }

    override suspend fun getEntry(entryId: Long): GoalEntry? = mutex.withLock {
        entries[entryId]
    }

    override suspend fun getEntries(goalId: Long): List<GoalEntry> = mutex.withLock {
        entries.values.filter { it.goalId == goalId }
    }

    override fun observeEntries(goalId: Long): Flow<List<GoalEntry>> {
        return _entriesFlow.map { allEntries ->
            allEntries.filter { it.goalId == goalId }
        }
    }

    override suspend fun saveEntry(entry: GoalEntry): GoalEntry = mutex.withLock {
        val assignedEntry = if (entry.id == 0L) {
            val id = nextEntryId++
            entry.copy(id = id)
        } else {
            entry
        }
        entries[assignedEntry.id] = assignedEntry
        _entriesFlow.value = entries.values.toList()
        assignedEntry
    }

    override suspend fun getEntriesByTransaction(transactionId: Long): List<GoalEntry> = mutex.withLock {
        entries.values.filter { it.linkedTransactionId == transactionId }
    }
}

