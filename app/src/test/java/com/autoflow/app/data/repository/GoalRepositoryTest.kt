package com.autoflow.app.data.repository

import com.autoflow.app.data.db.dao.GoalAggregateRaw
import com.autoflow.app.data.db.dao.GoalDao
import com.autoflow.app.data.db.entity.GoalEntity
import com.autoflow.app.data.db.entity.GoalEntryEntity
import com.autoflow.app.data.db.entity.TransactionEntity
import com.autoflow.app.data.model.PaymentMode
import com.autoflow.app.data.model.RecordType
import com.autoflow.app.data.model.ReviewStatus
import com.autoflow.app.data.model.TransactionSource
import com.autoflow.app.domain.goals.EntryType
import com.autoflow.app.domain.goals.GoalError
import com.autoflow.app.domain.goals.GoalKind
import com.autoflow.app.domain.goals.GoalResult
import com.autoflow.app.domain.goals.MAX_AMOUNT_PAISE
import com.autoflow.app.ingestion.FakeTransactionDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicLong

class GoalRepositoryTest {

    private lateinit var goalDao: FakeGoalDao
    private lateinit var transactionDao: FakeTransactionDao
    private lateinit var repository: DefaultGoalRepository

    private val now = 1700000000000L

    @Before
    fun setup() {
        goalDao = FakeGoalDao()
        transactionDao = FakeTransactionDao()
        repository = DefaultGoalRepository(
            goalDao = goalDao,
            transactionDao = transactionDao,
            runInTransaction = { block -> block() }
        )
    }

    private class FakeGoalDao : GoalDao {
        val goals = mutableMapOf<Long, GoalEntity>()
        val entries = mutableMapOf<Long, GoalEntryEntity>()
        private val nextGoalId = AtomicLong(1L)
        private val nextEntryId = AtomicLong(1L)

        private val _goalsFlow = MutableStateFlow<List<GoalEntity>>(emptyList())
        private val _entriesFlow = MutableStateFlow<List<GoalEntryEntity>>(emptyList())

        private fun notifyGoals() {
            _goalsFlow.value = goals.values.sortedBy { it.createdAt }
        }

        private fun notifyEntries() {
            _entriesFlow.value = entries.values.toList()
        }

        override suspend fun insertGoal(goal: GoalEntity): Long {
            val id = if (goal.id == 0L) nextGoalId.getAndIncrement() else goal.id
            val entity = goal.copy(id = id)
            goals[id] = entity
            notifyGoals()
            return id
        }

        override suspend fun updateGoal(goal: GoalEntity) {
            goals[goal.id] = goal
            notifyGoals()
        }

        override suspend fun getGoalById(id: Long): GoalEntity? = goals[id]

        override suspend fun getGoalByNameKey(nameKey: String): GoalEntity? =
            goals.values.firstOrNull { it.nameKey == nameKey }

        override fun observeAllGoals(): Flow<List<GoalEntity>> = _goalsFlow

        override suspend fun getAllGoals(): List<GoalEntity> = goals.values.sortedBy { it.createdAt }

        override fun observeGoalsRaw(): Flow<List<GoalAggregateRaw>> {
            return _entriesFlow.map { _ ->
                goals.values.sortedBy { it.createdAt }.map { g ->
                    val goalEntries = entries.values.filter { it.goalId == g.id && it.isDeleted == 0 }
                    val setAside = goalEntries.filter { it.type == "SET_ASIDE" }.sumOf { it.amountPaise }
                    val avoided = goalEntries.filter { it.type == "AVOIDED" }.sumOf { it.amountPaise }
                    val used = goalEntries.filter { it.type == "USED" }.sumOf { it.amountPaise }
                    GoalAggregateRaw(
                        id = g.id,
                        name = g.name,
                        name_key = g.nameKey,
                        kind = g.kind,
                        target_paise = g.targetPaise,
                        set_aside_paise = setAside,
                        avoided_paise = avoided,
                        used_paise = used
                    )
                }
            }
        }

        override suspend fun insertEntry(entry: GoalEntryEntity): Long {
            val id = if (entry.id == 0L) nextEntryId.getAndIncrement() else entry.id
            val entity = entry.copy(id = id)
            entries[id] = entity
            notifyEntries()
            return id
        }

        override suspend fun updateEntry(entry: GoalEntryEntity) {
            entries[entry.id] = entry
            notifyEntries()
        }

        override suspend fun getEntryById(id: Long): GoalEntryEntity? = entries[id]

        override fun observeEntriesForGoal(goalId: Long): Flow<List<GoalEntryEntity>> {
            return _entriesFlow.map { list ->
                list.filter { it.goalId == goalId }
                    .sortedWith(compareByDescending<GoalEntryEntity> { it.occurredAt }.thenByDescending { it.id })
            }
        }

        override suspend fun getEntriesForGoal(goalId: Long): List<GoalEntryEntity> {
            return entries.values.filter { it.goalId == goalId }
                .sortedWith(compareByDescending<GoalEntryEntity> { it.occurredAt }.thenByDescending { it.id })
        }

        override suspend fun getActiveEntriesForGoal(goalId: Long): List<GoalEntryEntity> {
            return entries.values.filter { it.goalId == goalId && it.isDeleted == 0 }
                .sortedWith(compareByDescending<GoalEntryEntity> { it.occurredAt }.thenByDescending { it.id })
        }

        override suspend fun getActiveEntriesByTransactionId(txId: Long): List<GoalEntryEntity> {
            return entries.values.filter { it.linkedTransactionId == txId && it.isDeleted == 0 }
        }

        override suspend fun getAllEntriesByTransactionId(txId: Long): List<GoalEntryEntity> {
            return entries.values.filter { it.linkedTransactionId == txId }
        }

        override suspend fun softDeleteEntry(id: Long, now: Long) {
            val entry = entries[id] ?: return
            entries[id] = entry.copy(isDeleted = 1, updatedAt = now)
            notifyEntries()
        }

        override suspend fun restoreEntry(id: Long, now: Long) {
            val entry = entries[id] ?: return
            entries[id] = entry.copy(isDeleted = 0, updatedAt = now)
            notifyEntries()
        }
    }

    private suspend fun insertExpense(
        id: Long = 10L,
        amountPaise: Long = 100000L, // ₹1,000.00
        merchantName: String = "FlightBooking",
        category: String = "Travel"
    ): Long {
        val tx = TransactionEntity(
            id = id,
            amountPaise = amountPaise,
            recordType = RecordType.EXPENSE,
            dateTime = now,
            merchantName = merchantName,
            category = category,
            categorySource = "USER",
            paymentMode = PaymentMode.UPI,
            note = null,
            accountNumber = "1234",
            bankName = "HDFC",
            reference = "REF$id",
            transactionHash = "HASH$id",
            source = TransactionSource.SMS_AUTO,
            reviewStatus = ReviewStatus.CONFIRMED,
            uncertaintyBits = 0,
            createdAt = now,
            updatedAt = now,
            isDeleted = 0
        )
        return transactionDao.insert(tx)
    }

    // -------------------------------------------------------------
    // 1. Goal Creation & Editing Rules
    // -------------------------------------------------------------

    @Test
    fun createGoal_valid_success() = runBlocking {
        val res = repository.createGoal("  New Laptop  ", GoalKind.SAVE, 8000000L, now)
        assertTrue(res is GoalResult.Success)
        val goal = (res as GoalResult.Success).data
        assertEquals("New Laptop", goal.name)
        assertEquals("new laptop", goal.nameKey)
        assertEquals(GoalKind.SAVE, goal.kind)
        assertEquals(8000000L, goal.targetPaise)
    }

    @Test
    fun createGoal_blankName_rejected() = runBlocking {
        val res = repository.createGoal("   ", GoalKind.SAVE, 50000L, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.BlankName, (res as GoalResult.Error).error)
    }

    @Test
    fun createGoal_nameExceeds30Chars_rejected() = runBlocking {
        val longName = "A".repeat(31)
        val res = repository.createGoal(longName, GoalKind.SAVE, 50000L, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.NameTooLong(31, 30), (res as GoalResult.Error).error)
    }

    @Test
    fun createGoal_duplicateName_caseInsensitive_rejected() = runBlocking {
        repository.createGoal("Emergency Fund", GoalKind.SAVE, 5000000L, now)
        val dup = repository.createGoal("  emergency fund  ", GoalKind.SAVE, 2000000L, now)
        assertTrue(dup is GoalResult.Error)
        assertEquals(GoalError.DuplicateName("emergency fund"), (dup as GoalResult.Error).error)
    }

    @Test
    fun createGoal_zeroOrNegativeTarget_rejected() = runBlocking {
        val zeroRes = repository.createGoal("Vacation", GoalKind.SAVE, 0L, now)
        assertTrue(zeroRes is GoalResult.Error)
        assertEquals(GoalError.BadAmount(0L), (zeroRes as GoalResult.Error).error)

        val negRes = repository.createGoal("Vacation", GoalKind.SAVE, -100L, now)
        assertTrue(negRes is GoalResult.Error)
        assertEquals(GoalError.BadAmount(-100L), (negRes as GoalResult.Error).error)
    }

    @Test
    fun updateGoal_valid_success() = runBlocking {
        val created = (repository.createGoal("Old Name", GoalKind.SAVE, 100000L, now) as GoalResult.Success).data
        val updatedRes = repository.updateGoal(created.id, "Renamed Goal", 200000L, now)
        assertTrue(updatedRes is GoalResult.Success)
        val updated = (updatedRes as GoalResult.Success).data
        assertEquals("Renamed Goal", updated.name)
        assertEquals("renamed goal", updated.nameKey)
        assertEquals(200000L, updated.targetPaise)
    }

    @Test
    fun updateGoal_duplicateName_rejected() = runBlocking {
        repository.createGoal("Goal A", GoalKind.SAVE, 100000L, now)
        val b = (repository.createGoal("Goal B", GoalKind.SAVE, 200000L, now) as GoalResult.Success).data
        val res = repository.updateGoal(b.id, "goal a", 300000L, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.DuplicateName("goal a"), (res as GoalResult.Error).error)
    }

    // -------------------------------------------------------------
    // 2. Goal Kinds & Entry Types (SAVE vs AVOID)
    // -------------------------------------------------------------

    @Test
    fun addSetAside_onSaveGoal_success() = runBlocking {
        val goal = (repository.createGoal("House", GoalKind.SAVE, 10000000L, now) as GoalResult.Success).data
        val entryRes = repository.addSetAside(goal.id, 500000L, "Bonus", now)
        assertTrue(entryRes is GoalResult.Success)
        val entry = (entryRes as GoalResult.Success).data
        assertEquals(EntryType.SET_ASIDE, entry.type)
        assertEquals(500000L, entry.amountPaise)
        assertEquals("Bonus", entry.note)
    }

    @Test
    fun addSetAside_onAvoidGoal_rejected() = runBlocking {
        val goal = (repository.createGoal("No Smoking", GoalKind.AVOID, 50000L, now) as GoalResult.Success).data
        val entryRes = repository.addSetAside(goal.id, 500L, "Saved", now)
        assertTrue(entryRes is GoalResult.Error)
        assertTrue((entryRes as GoalResult.Error).error is GoalError.WrongGoalKind)
    }

    @Test
    fun addAvoided_onSaveGoal_doesNotAffectSavedOrAvailable() = runBlocking {
        val goal = (repository.createGoal("Car", GoalKind.SAVE, 1000000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 200000L, "Deposit", now)
        repository.addAvoided(goal.id, 50000L, "Skipped dinner", now)

        val summary = repository.observeGoals().first().first { it.id == goal.id }
        assertEquals(200000L, summary.savedPaise)
        assertEquals(200000L, summary.availablePaise)
        assertEquals(50000L, summary.avoidedPaise)
        assertEquals(20, summary.percent) // 200,000 / 1,000,000 = 20%
    }

    @Test
    fun addAvoided_onAvoidGoal_progressIsAvoidedTotal() = runBlocking {
        val goal = (repository.createGoal("No Uber", GoalKind.AVOID, 100000L, now) as GoalResult.Success).data
        repository.addAvoided(goal.id, 40000L, "Took metro", now)

        val summary = repository.observeGoals().first().first { it.id == goal.id }
        assertEquals(40000L, summary.avoidedPaise)
        assertEquals(0L, summary.savedPaise)
        assertEquals(0L, summary.availablePaise)
        assertEquals(40, summary.percent) // 40,000 / 100,000 = 40%
    }

    // -------------------------------------------------------------
    // 3. Available Balance & Three Ways to Use Money
    // -------------------------------------------------------------

    @Test
    fun availableBalance_cannotDropBelowZero() = runBlocking {
        val goal = (repository.createGoal("Phone", GoalKind.SAVE, 5000000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 100000L, "Part 1", now)

        // Attempt to take out 150000 (> 100000)
        val res = repository.useTakeOut(goal.id, 150000L, "Cash", now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(100000L, 150000L), (res as GoalResult.Error).error)
    }

    @Test
    fun useLinkExpense_valid_reducesAvailable_andDoesNotModifyExpense() = runBlocking {
        val goal = (repository.createGoal("Vacation", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 300000L, "Funds", now)

        val txId = insertExpense(id = 1L, amountPaise = 200000L, merchantName = "AirIndia")

        val res = repository.useLinkExpense(goal.id, txId, 150000L, "Flight ticket", now)
        assertTrue(res is GoalResult.Success)
        val entry = (res as GoalResult.Success).data
        assertEquals(txId, entry.linkedTransactionId)
        assertEquals(150000L, entry.amountPaise)

        // Goal available is now 300000 - 150000 = 150000
        val summary = repository.observeGoals().first().first { it.id == goal.id }
        assertEquals(150000L, summary.availablePaise)
        assertEquals(150000L, summary.usedPaise)

        // Expense itself in transaction repository remains intact
        val txAfter = transactionDao.getById(txId)
        assertNotNull(txAfter)
        assertEquals(200000L, txAfter!!.amountPaise)
        assertEquals(0, txAfter.isDeleted)
    }

    @Test
    fun useLinkExpense_multipleLinksOnSameExpense_upToExpenseAmount() = runBlocking {
        val goal = (repository.createGoal("Gadgets", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 500000L, "Savings", now)

        val txId = insertExpense(id = 2L, amountPaise = 100000L, merchantName = "Apple Store") // ₹1,000

        // First link ₹600 (60,000 paise)
        val link1 = repository.useLinkExpense(goal.id, txId, 60000L, "Link 1", now)
        assertTrue(link1 is GoalResult.Success)

        // Second link ₹400 (40,000 paise) -> Exactly reaches ₹1,000
        val link2 = repository.useLinkExpense(goal.id, txId, 40000L, "Link 2", now)
        assertTrue(link2 is GoalResult.Success)

        // Third link ₹10 (1,000 paise) -> Rejects: exceeds remaining expense amount (0)
        val link3 = repository.useLinkExpense(goal.id, txId, 1000L, "Link 3", now)
        assertTrue(link3 is GoalResult.Error)
        assertEquals(GoalError.AmountExceedsExpense(1000L, 100000L, 0L), (link3 as GoalResult.Error).error)
    }

    @Test
    fun useNewExpense_atomicTransaction_bothCreatedOnSuccess() = runBlocking {
        val goal = (repository.createGoal("Shopping", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 200000L, "Deposit", now)

        val res = repository.useNewExpense(
            goalId = goal.id,
            amountPaise = 80000L,
            merchantName = "Zara",
            category = "Shopping",
            paymentMode = PaymentMode.CASH,
            note = "Jacket",
            occurredAt = now
        )
        assertTrue(res is GoalResult.Success)
        val entry = (res as GoalResult.Success).data
        assertNotNull(entry.linkedTransactionId)

        // Verify transaction exists in transactions table
        val tx = transactionDao.getById(entry.linkedTransactionId!!)
        assertNotNull(tx)
        assertEquals("Zara", tx!!.merchantName)
        assertEquals(80000L, tx.amountPaise)
        assertEquals("Shopping", tx.category)
        assertEquals(0, tx.isDeleted)

        // Verify goal available reduced
        val summary = repository.observeGoals().first().first { it.id == goal.id }
        assertEquals(120000L, summary.availablePaise)
        assertEquals(80000L, summary.usedPaise)
    }

    @Test
    fun useTakeOut_valid_reducesAvailable_noLinkedExpense() = runBlocking {
        val goal = (repository.createGoal("Reserve", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 200000L, "Deposit", now)

        val res = repository.useTakeOut(goal.id, 50000L, "ATM cash withdrawal", now)
        assertTrue(res is GoalResult.Success)
        val entry = (res as GoalResult.Success).data
        assertNull(entry.linkedTransactionId)
        assertEquals(50000L, entry.amountPaise)

        val summary = repository.observeGoals().first().first { it.id == goal.id }
        assertEquals(150000L, summary.availablePaise)
    }

    // -------------------------------------------------------------
    // 4. Editing, Removing & Moving Entries
    // -------------------------------------------------------------

    @Test
    fun editEntry_usedLinkedExpense_amountCappedByExpenseAndAvailable() = runBlocking {
        val goal = (repository.createGoal("Tech", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 300000L, "Deposit", now)
        val txId = insertExpense(id = 3L, amountPaise = 200000L, merchantName = "Broma")
        val entry = (repository.useLinkExpense(goal.id, txId, 100000L, "Initial", now) as GoalResult.Success).data

        // Edit amount to 180000: <= expense (200000) and <= available (300000 total set aside)
        val editRes = repository.editEntry(entryId = entry.id, newAmountPaise = 180000L, newNote = "Updated")
        assertTrue(editRes is GoalResult.Success)
        val updated = (editRes as GoalResult.Success).data
        assertEquals(180000L, updated.amountPaise)
        assertEquals("Updated", updated.note)

        // Edit amount to 250000: exceeds expense (200000) -> rejected
        val excessRes = repository.editEntry(entryId = entry.id, newAmountPaise = 250000L)
        assertTrue(excessRes is GoalResult.Error)
        assertTrue((excessRes as GoalResult.Error).error is GoalError.AmountExceedsExpense)
    }

    @Test
    fun editEntry_setAside_cannotReduceBelowUsed() = runBlocking {
        val goal = (repository.createGoal("Car", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        val setAside = (repository.addSetAside(goal.id, 300000L, "Deposit", now) as GoalResult.Success).data
        repository.useTakeOut(goal.id, 250000L, "Spent", now)

        // Available is now 50000. Reducing set aside from 300000 to 200000 would require reducing available by 100000 -> drops negative!
        val res = repository.editEntry(entryId = setAside.id, newAmountPaise = 200000L)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(50000L, 100000L), (res as GoalResult.Error).error)
    }

    @Test
    fun editEntry_moveAcrossGoals_validatesBothGoals() = runBlocking {
        val saveGoalA = (repository.createGoal("Goal A", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        val saveGoalB = (repository.createGoal("Goal B", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        val avoidGoal = (repository.createGoal("Avoid Goal", GoalKind.AVOID, 500000L, now) as GoalResult.Success).data

        val setAsideA = (repository.addSetAside(saveGoalA.id, 200000L, "Deposit", now) as GoalResult.Success).data

        // Moving SET_ASIDE from Goal A to AVOID goal must be rejected
        val wrongKindRes = repository.editEntry(entryId = setAsideA.id, newGoalId = avoidGoal.id)
        assertTrue(wrongKindRes is GoalResult.Error)
        assertTrue((wrongKindRes as GoalResult.Error).error is GoalError.WrongGoalKind)

        // Moving SET_ASIDE from Goal A to Goal B succeeds
        val validMoveRes = repository.editEntry(entryId = setAsideA.id, newGoalId = saveGoalB.id)
        assertTrue(validMoveRes is GoalResult.Success)

        // Check Goal A has 0, Goal B has 200000
        val summaryA = repository.observeGoals().first().first { it.id == saveGoalA.id }
        val summaryB = repository.observeGoals().first().first { it.id == saveGoalB.id }
        assertEquals(0L, summaryA.availablePaise)
        assertEquals(200000L, summaryB.availablePaise)
    }

    @Test
    fun removeUsed_givesMoneyBack_expenseUntouched() = runBlocking {
        val goal = (repository.createGoal("Holiday", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 300000L, "Deposit", now)
        val txId = insertExpense(id = 4L, amountPaise = 100000L, merchantName = "Hotel")
        val entry = (repository.useLinkExpense(goal.id, txId, 100000L, "Hotel stay", now) as GoalResult.Success).data

        assertEquals(200000L, repository.observeGoals().first().first { it.id == goal.id }.availablePaise)

        // Call removeUsed ("Remove, give money back")
        val res = repository.removeUsed(entry.id, now)
        assertTrue(res is GoalResult.Success)

        // Available money returned to goal (300000)
        val summary = repository.observeGoals().first().first { it.id == goal.id }
        assertEquals(300000L, summary.availablePaise)
        assertEquals(0L, summary.usedPaise)

        // Linked expense in transactions table is untouched!
        val tx = transactionDao.getById(txId)
        assertNotNull(tx)
        assertEquals(0, tx!!.isDeleted)
    }

    @Test
    fun deleteEntry_setAside_rejectedIfAvailableDropsNegative() = runBlocking {
        val goal = (repository.createGoal("Trip", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        val setAside = (repository.addSetAside(goal.id, 200000L, "Part 1", now) as GoalResult.Success).data
        repository.useTakeOut(goal.id, 150000L, "Cash", now)

        // Available is 50000. Deleting setAside (200000) would make available drop negative!
        val res = repository.deleteEntry(setAside.id, now)
        assertTrue(res is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(50000L, 200000L), (res as GoalResult.Error).error)
    }

    @Test
    fun undeleteEntry_restoresEntry() = runBlocking {
        val goal = (repository.createGoal("Trip", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        val setAside = (repository.addSetAside(goal.id, 200000L, "Deposit", now) as GoalResult.Success).data
        val used = (repository.useTakeOut(goal.id, 100000L, "Cash", now) as GoalResult.Success).data

        // Soft delete used
        repository.deleteEntry(used.id, now)
        assertEquals(200000L, repository.observeGoals().first().first { it.id == goal.id }.availablePaise)

        // Undelete used
        val undelRes = repository.undeleteEntry(used.id, now)
        assertTrue(undelRes is GoalResult.Success)
        assertEquals(100000L, repository.observeGoals().first().first { it.id == goal.id }.availablePaise)
    }

    @Test
    fun observeGoalWithEntries_whenLinkedTransactionSoftDeleted_exposesLinkedExpenseRemoved() = runBlocking {
        val goal = (repository.createGoal("Camera", GoalKind.SAVE, 500000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 300000L, "Deposit", now)
        val txId = insertExpense(id = 5L, amountPaise = 150000L, merchantName = "Nikon Store")
        repository.useLinkExpense(goal.id, txId, 150000L, "Lens", now)

        // Initial check: not removed
        var details = repository.observeGoalWithEntries(goal.id).first()!!
        assertEquals(2, details.entries.size)
        val usedEntryBefore = details.entries.first { it.entry.type == EntryType.USED }
        assertFalse(usedEntryBefore.isLinkedExpenseRemoved)
        assertEquals("Nikon Store", usedEntryBefore.linkedExpense?.merchantName)

        // Soft delete the transaction in transaction repository
        transactionDao.softDelete(txId)

        // Exposes linked expense was removed
        details = repository.observeGoalWithEntries(goal.id).first()!!
        assertEquals(2, details.entries.size)
        val usedEntryAfter = details.entries.first { it.entry.type == EntryType.USED }
        assertTrue(usedEntryAfter.isLinkedExpenseRemoved)
        assertEquals(150000L, usedEntryAfter.entry.amountPaise) // amount kept
    }

    @Test
    fun observeGoals_returnsIntegerPercentages_andTotals() = runBlocking {
        val goal = (repository.createGoal("Bike", GoalKind.SAVE, 1000000L, now) as GoalResult.Success).data // ₹10,000
        repository.addSetAside(goal.id, 333333L, "Deposit", now) // 33.33% -> integer 33%

        val summary = repository.observeGoals().first().first { it.id == goal.id }
        assertEquals(33, summary.percent)
        assertEquals(333333L, summary.savedPaise)
        assertEquals(333333L, summary.availablePaise)
        assertEquals(0L, summary.avoidedPaise)
        assertEquals(0L, summary.usedPaise)
    }

    @Test
    fun goalMoney_neverChangesTransactionTotals() = runBlocking {
        val initialActive = transactionDao.getAllActiveTransactions().first().size
        val goal = (repository.createGoal("Separation", GoalKind.SAVE, 1000000L, now) as GoalResult.Success).data
        repository.addSetAside(goal.id, 500000L, "Money aside", now)
        repository.addAvoided(goal.id, 200000L, "Skipped dinner", now)
        repository.useTakeOut(goal.id, 100000L, "Cash to pocket", now)

        // Transaction table active count is COMPLETELY UNCHANGED
        val afterActive = transactionDao.getAllActiveTransactions().first().size
        assertEquals("Goal operations must never alter transaction spending or records", initialActive, afterActive)
    }
}
