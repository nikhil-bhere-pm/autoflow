package com.autoflow.app.domain.goals

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GoalsDomainTest {

    private lateinit var repository: InMemoryGoalsRepository
    private lateinit var fixedClock: Clock
    private lateinit var service: GoalsService

    private val baseInstant = Instant.parse("2026-10-24T12:00:00Z")
    private val baseMillis = baseInstant.toEpochMilli()

    @Before
    fun setUp() {
        repository = InMemoryGoalsRepository()
        fixedClock = Clock.fixed(baseInstant, ZoneOffset.UTC)
        service = GoalsService(repository, fixedClock)
    }

    // -------------------------------------------------------------
    // RULE 1: Amounts and Targets Limits (Paise, > 0 and <= 10,00,00,000.00 rupees)
    // 10,00,00,000.00 rupees = 10,000,000,000 paise (10 billion paise)
    // -------------------------------------------------------------

    @Test
    fun rule1_createGoal_zeroTarget_returnsBadAmount() = runBlocking {
        val result = service.createGoal("Car Fund", GoalKind.SAVINGS, 0L)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.BadAmount(0L), (result as GoalResult.Error).error)
    }

    @Test
    fun rule1_createGoal_negativeTarget_returnsBadAmount() = runBlocking {
        val result = service.createGoal("Car Fund", GoalKind.SAVINGS, -5000L)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.BadAmount(-5000L), (result as GoalResult.Error).error)
    }

    @Test
    fun rule1_createGoal_overLimitTarget_returnsOverLimit() = runBlocking {
        // Exceeding 10,000,000,000 paise by 1 paise
        val overLimit = MAX_AMOUNT_PAISE + 1L
        val result = service.createGoal("Mega Fund", GoalKind.SAVINGS, overLimit)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.OverLimit(overLimit, MAX_AMOUNT_PAISE), (result as GoalResult.Error).error)
    }

    @Test
    fun rule1_createGoal_exactMaxLimitTarget_succeeds() = runBlocking {
        val result = service.createGoal("Max Fund", GoalKind.SAVINGS, MAX_AMOUNT_PAISE)
        assertTrue(result is GoalResult.Success)
        assertEquals(MAX_AMOUNT_PAISE, (result as GoalResult.Success).data.targetPaise)
    }

    @Test
    fun rule1_addEntry_zeroAmount_returnsBadAmount() = runBlocking {
        val goal = (service.createGoal("Savings", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, 0L)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.BadAmount(0L), (result as GoalResult.Error).error)
    }

    @Test
    fun rule1_addEntry_negativeAmount_returnsBadAmount() = runBlocking {
        val goal = (service.createGoal("Savings", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, -100L)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.BadAmount(-100L), (result as GoalResult.Error).error)
    }

    @Test
    fun rule1_addEntry_overLimitAmount_returnsOverLimit() = runBlocking {
        val goal = (service.createGoal("Savings", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val overLimit = MAX_AMOUNT_PAISE + 1L
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, overLimit)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.OverLimit(overLimit, MAX_AMOUNT_PAISE), (result as GoalResult.Error).error)
    }

    @Test
    fun rule1_addEntry_exactMaxLimitAmount_succeeds() = runBlocking {
        val goal = (service.createGoal("Savings", GoalKind.SAVINGS, MAX_AMOUNT_PAISE) as GoalResult.Success).data
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, MAX_AMOUNT_PAISE)
        assertTrue(result is GoalResult.Success)
        assertEquals(MAX_AMOUNT_PAISE, (result as GoalResult.Success).data.amountPaise)
    }

    // -------------------------------------------------------------
    // RULE 2: Goal name: trimmed, 1-40 characters, unique case-insensitively among non-archived goals
    // -------------------------------------------------------------

    @Test
    fun rule2_createGoal_blankName_returnsBlankName() = runBlocking {
        val emptyResult = service.createGoal("", GoalKind.SAVINGS, 50_000L)
        assertTrue(emptyResult is GoalResult.Error)
        assertEquals(GoalError.BlankName, (emptyResult as GoalResult.Error).error)

        val whitespaceResult = service.createGoal("   \t  ", GoalKind.SAVINGS, 50_000L)
        assertTrue(whitespaceResult is GoalResult.Error)
        assertEquals(GoalError.BlankName, (whitespaceResult as GoalResult.Error).error)
    }

    @Test
    fun rule2_createGoal_nameExceeds40Chars_returnsNameTooLong() = runBlocking {
        val name41 = "A".repeat(41)
        val result = service.createGoal(name41, GoalKind.SAVINGS, 50_000L)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.NameTooLong(41, 40), (result as GoalResult.Error).error)
    }

    @Test
    fun rule2_createGoal_nameBoundaries_1And40CharsAllowed() = runBlocking {
        val result1 = service.createGoal("A", GoalKind.SAVINGS, 50_000L)
        assertTrue(result1 is GoalResult.Success)
        assertEquals("A", (result1 as GoalResult.Success).data.name)

        val name40 = "B".repeat(40)
        val result40 = service.createGoal(name40, GoalKind.SAVINGS, 50_000L)
        assertTrue(result40 is GoalResult.Success)
        assertEquals(name40, (result40 as GoalResult.Success).data.name)
    }

    @Test
    fun rule2_createGoal_nameIsTrimmed() = runBlocking {
        val result = service.createGoal("   Trip to Ladakh   ", GoalKind.SAVINGS, 100_000L)
        assertTrue(result is GoalResult.Success)
        assertEquals("Trip to Ladakh", (result as GoalResult.Success).data.name)
    }

    @Test
    fun rule2_createGoal_duplicateNameExact_returnsDuplicateName() = runBlocking {
        service.createGoal("Laptop", GoalKind.SAVINGS, 100_000L)
        val duplicate = service.createGoal("Laptop", GoalKind.SAVINGS, 200_000L)
        assertTrue(duplicate is GoalResult.Error)
        assertEquals(GoalError.DuplicateName("Laptop"), (duplicate as GoalResult.Error).error)
    }

    @Test
    fun rule2_createGoal_duplicateNameCaseAndWhitespaceInsensitive_returnsDuplicateName() = runBlocking {
        service.createGoal("Emergency Fund", GoalKind.SAVINGS, 500_000L)
        val duplicate = service.createGoal("  emergency fund  ", GoalKind.SAVINGS, 500_000L)
        assertTrue(duplicate is GoalResult.Error)
        assertEquals(GoalError.DuplicateName("emergency fund"), (duplicate as GoalResult.Error).error)
    }

    @Test
    fun rule2_createGoal_duplicateNameAllowedIfPreviousIsArchived() = runBlocking {
        val original = (service.createGoal("Gadget", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        service.archiveGoal(original.id)

        // Same name now allowed because the existing goal is archived
        val newGoal = service.createGoal("gadget", GoalKind.SAVINGS, 80_000L)
        assertTrue(newGoal is GoalResult.Success)
        assertEquals("gadget", (newGoal as GoalResult.Success).data.name)
    }

    // -------------------------------------------------------------
    // RULE 3: Entry Type vs Goal Kind
    // SET_ASIDE only on SAVINGS. AVOIDED allowed on both and is ALWAYS an estimate.
    // -------------------------------------------------------------

    @Test
    fun rule3_setAsideAllowedOnSavingsGoal() = runBlocking {
        val goal = (service.createGoal("House", GoalKind.SAVINGS, 1_000_000L) as GoalResult.Success).data
        val entry = service.addEntry(goal.id, EntryType.SET_ASIDE, 20_000L)
        assertTrue(entry is GoalResult.Success)
        assertEquals(EntryType.SET_ASIDE, (entry as GoalResult.Success).data.type)
    }

    @Test
    fun rule3_avoidedAllowedOnSavingsGoal() = runBlocking {
        val goal = (service.createGoal("House", GoalKind.SAVINGS, 1_000_000L) as GoalResult.Success).data
        val entry = service.addEntry(goal.id, EntryType.AVOIDED, 15_000L, "Cooked dinner at home")
        assertTrue(entry is GoalResult.Success)
        assertEquals(EntryType.AVOIDED, (entry as GoalResult.Success).data.type)
    }

    @Test
    fun rule3_avoidedAllowedOnAvoidanceGoal() = runBlocking {
        val goal = (service.createGoal("Quit Smoking", GoalKind.AVOIDANCE, 500_000L) as GoalResult.Success).data
        val entry = service.addEntry(goal.id, EntryType.AVOIDED, 500L, "Resisted cigarette craving")
        assertTrue(entry is GoalResult.Success)
        assertEquals(EntryType.AVOIDED, (entry as GoalResult.Success).data.type)
    }

    @Test
    fun rule3_setAsideDisallowedOnAvoidanceGoal_returnsWrongEntryType() = runBlocking {
        val goal = (service.createGoal("No Alcohol", GoalKind.AVOIDANCE, 200_000L) as GoalResult.Success).data
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, 5_000L)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.WrongEntryType(GoalKind.AVOIDANCE, EntryType.SET_ASIDE), (result as GoalResult.Error).error)
    }

    @Test
    fun rule3_avoidedOnSavingsGoal_neverAddedToSetAsideTotalsOrPercent() = runBlocking {
        val goal = (service.createGoal("Bike", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 40_000L)
        service.addEntry(goal.id, EntryType.AVOIDED, 50_000L, "Sold unused gear")

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(40_000L, progress.setAsidePaise)
        assertEquals(50_000L, progress.avoidedPaise)
        // Percent should strictly use setAside (40_000 * 100 / 100_000 = 40%), not (40_000 + 50_000)
        assertEquals(40, progress.percent)
        assertFalse(progress.isReached)
    }


    // -------------------------------------------------------------
    // RULE 4: Progress calculation, floor rounding, over target, raw totals uncapped
    // -------------------------------------------------------------

    @Test
    fun rule4_goalWithNoEntries_hasZeroProgress() = runBlocking {
        val goal = (service.createGoal("Rainy Day", GoalKind.SAVINGS, 500_000L) as GoalResult.Success).data
        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data

        assertEquals(0L, progress.setAsidePaise)
        assertEquals(0L, progress.avoidedPaise)
        assertEquals(0, progress.percent)
        assertFalse(progress.isReached)
    }

    @Test
    fun rule4_floorRoundingOfPercent() = runBlocking {
        // Target = 10,000 paise (100 rupees)
        val goal = (service.createGoal("Gadget", GoalKind.SAVINGS, 10_000L) as GoalResult.Success).data
        // Set aside = 1,999 paise -> 19.99% -> floor rounding must be 19%
        service.addEntry(goal.id, EntryType.SET_ASIDE, 1_999L)

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(19, progress.percent)
        assertFalse(progress.isReached)
        assertEquals(1_999L, progress.setAsidePaise)
    }

    @Test
    fun rule4_exactlyAtTarget() = runBlocking {
        val goal = (service.createGoal("Watch", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 50_000L)

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(100, progress.percent)
        assertTrue(progress.isReached)
        assertEquals(50_000L, progress.setAsidePaise)
    }

    @Test
    fun rule4_overTarget_capsPercentAt100_preservesRawTotal() = runBlocking {
        // As specified in prompt: target 1,20,000.00 (12,000,000 paise) and set aside 1,25,000.00 (12,500,000 paise)
        val target = 12_000_000L
        val setAside = 12_500_000L
        val goal = (service.createGoal("Downpayment", GoalKind.SAVINGS, target) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, setAside)

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(100, progress.percent)
        assertTrue(progress.isReached)
        assertEquals(setAside, progress.setAsidePaise)
    }

    @Test
    fun rule4_avoidanceGoalProgress_usesAvoidedTotal() = runBlocking {
        val target = 100_000L
        val goal = (service.createGoal("Less Coffee", GoalKind.AVOIDANCE, target) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.AVOIDED, 35_000L, "Brewed coffee at home")

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(0L, progress.setAsidePaise)
        assertEquals(35_000L, progress.avoidedPaise)
        assertEquals(35, progress.percent)
        assertFalse(progress.isReached)

        // Reach target
        service.addEntry(goal.id, EntryType.AVOIDED, 65_000L, "Skipped coffee shop")
        val progressReached = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(100_000L, progressReached.avoidedPaise)
        assertEquals(100, progressReached.percent)
        assertTrue(progressReached.isReached)

        // Exceed target: raw total uncapped, percent capped at 100
        service.addEntry(goal.id, EntryType.AVOIDED, 20_000L, "Office free coffee")
        val progressExceeded = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(120_000L, progressExceeded.avoidedPaise)
        assertEquals(100, progressExceeded.percent)
        assertTrue(progressExceeded.isReached)
    }


    // -------------------------------------------------------------
    // RULE 5: Soft delete and restore change totals; editing an amount updates totals; archive hides a goal
    // -------------------------------------------------------------

    @Test
    fun rule5_softDeleteAndRestore_changeTotals() = runBlocking {
        val goal = (service.createGoal("Trip", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val entry1 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 30_000L) as GoalResult.Success).data
        val entry2 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 20_000L) as GoalResult.Success).data

        // Initial progress: 50,000 paise (50%)
        var progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(50_000L, progress.setAsidePaise)
        assertEquals(50, progress.percent)

        // Soft delete entry2: total drops to 30,000 paise (30%)
        val deleteResult = service.deleteEntry(entry2.id)
        assertTrue(deleteResult is GoalResult.Success)
        assertNotNull((deleteResult as GoalResult.Success).data.deletedAt)

        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(30_000L, progress.setAsidePaise)
        assertEquals(30, progress.percent)

        // Restore entry2: total restores to 50,000 paise (50%)
        val restoreResult = service.restoreEntry(entry2.id)
        assertTrue(restoreResult is GoalResult.Success)
        assertNull((restoreResult as GoalResult.Success).data.deletedAt)

        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(50_000L, progress.setAsidePaise)
        assertEquals(50, progress.percent)
    }

    @Test
    fun rule5_editingAmount_updatesTotals() = runBlocking {
        val goal = (service.createGoal("Gold", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val entry = (service.addEntry(goal.id, EntryType.SET_ASIDE, 25_000L) as GoalResult.Success).data

        var progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(25_000L, progress.setAsidePaise)
        assertEquals(25, progress.percent)

        // Edit amount from 25,000 to 75,000
        val editResult = service.editEntry(entry.id, 75_000L, "Revised investment", entry.occurredAt)
        assertTrue(editResult is GoalResult.Success)
        assertEquals(75_000L, (editResult as GoalResult.Success).data.amountPaise)
        assertEquals("Revised investment", editResult.data.note)

        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(75_000L, progress.setAsidePaise)
        assertEquals(75, progress.percent)
    }

    @Test
    fun rule5_archiveHidesGoalFromActiveList_keepsEntries() = runBlocking {
        val goal1 = (service.createGoal("Goal 1", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val goal2 = (service.createGoal("Goal 2", GoalKind.SAVINGS, 200_000L) as GoalResult.Success).data

        service.addEntry(goal1.id, EntryType.SET_ASIDE, 10_000L)
        service.addEntry(goal2.id, EntryType.SET_ASIDE, 20_000L)

        // Both active initially
        var activeGoals = service.listGoals()
        assertEquals(2, activeGoals.size)

        // Archive goal1
        val archiveResult = service.archiveGoal(goal1.id)
        assertTrue(archiveResult is GoalResult.Success)
        assertNotNull((archiveResult as GoalResult.Success).data.archivedAt)

        // Active list now hides goal1
        activeGoals = service.listGoals()
        assertEquals(1, activeGoals.size)
        assertEquals(goal2.id, activeGoals[0].goal.id)

        // But entries for goal1 are retained in history
        val historyGoal1 = service.history(goal1.id)
        assertTrue(historyGoal1 is GoalResult.Success)
        assertEquals(1, (historyGoal1 as GoalResult.Success).data.size)
        assertEquals(10_000L, historyGoal1.data[0].amountPaise)

        // includeArchived = true shows both
        val allGoals = service.listGoals(includeArchived = true)
        assertEquals(2, allGoals.size)
    }

    @Test
    fun rule5_operationsOnNonExistentEntities_returnTypedErrors() = runBlocking {
        val nonExistentGoalId = 99999L
        val nonExistentEntryId = 88888L

        // Goal not found
        val addResult = service.addEntry(nonExistentGoalId, EntryType.SET_ASIDE, 1000L)
        assertTrue(addResult is GoalResult.Error)
        assertEquals(GoalError.GoalNotFound(nonExistentGoalId), (addResult as GoalResult.Error).error)

        val archiveResult = service.archiveGoal(nonExistentGoalId)
        assertTrue(archiveResult is GoalResult.Error)
        assertEquals(GoalError.GoalNotFound(nonExistentGoalId), (archiveResult as GoalResult.Error).error)

        val historyResult = service.history(nonExistentGoalId)
        assertTrue(historyResult is GoalResult.Error)
        assertEquals(GoalError.GoalNotFound(nonExistentGoalId), (historyResult as GoalResult.Error).error)

        val progressResult = service.getGoalProgress(nonExistentGoalId)
        assertTrue(progressResult is GoalResult.Error)
        assertEquals(GoalError.GoalNotFound(nonExistentGoalId), (progressResult as GoalResult.Error).error)

        // Entry not found
        val editResult = service.editEntry(nonExistentEntryId, 5000L, "Note", baseMillis)
        assertTrue(editResult is GoalResult.Error)
        assertEquals(GoalError.EntryNotFound(nonExistentEntryId), (editResult as GoalResult.Error).error)

        val deleteResult = service.deleteEntry(nonExistentEntryId)
        assertTrue(deleteResult is GoalResult.Error)
        assertEquals(GoalError.EntryNotFound(nonExistentEntryId), (deleteResult as GoalResult.Error).error)

        val restoreResult = service.restoreEntry(nonExistentEntryId)
        assertTrue(restoreResult is GoalResult.Error)
        assertEquals(GoalError.EntryNotFound(nonExistentEntryId), (restoreResult as GoalResult.Error).error)
    }

    // -------------------------------------------------------------
    // RULE 7: Future-date rejection (reject if later than now + 5 minutes)
    // -------------------------------------------------------------

    @Test
    fun rule7_pastDate_allowed() = runBlocking {
        val goal = (service.createGoal("Vacation", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        val pastTime = baseMillis - (24 * 60 * 60 * 1000L) // 1 day ago
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, 5_000L, occurredAt = pastTime)
        assertTrue(result is GoalResult.Success)
        assertEquals(pastTime, (result as GoalResult.Success).data.occurredAt)
    }

    @Test
    fun rule7_exactlyAt5MinutesFuture_allowed() = runBlocking {
        val goal = (service.createGoal("Vacation", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        val exactly5Min = baseMillis + (5 * 60 * 1000L)
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, 5_000L, occurredAt = exactly5Min)
        assertTrue(result is GoalResult.Success)
        assertEquals(exactly5Min, (result as GoalResult.Success).data.occurredAt)
    }

    @Test
    fun rule7_beyond5MinutesFuture_rejected() = runBlocking {
        val goal = (service.createGoal("Vacation", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        val beyond5Min = baseMillis + (5 * 60 * 1000L) + 1L
        val result = service.addEntry(goal.id, EntryType.SET_ASIDE, 5_000L, occurredAt = beyond5Min)
        assertTrue(result is GoalResult.Error)
        val error = (result as GoalResult.Error).error
        assertTrue(error is GoalError.FutureDate)
        assertEquals(beyond5Min, (error as GoalError.FutureDate).occurredAt)
        assertEquals(baseMillis + (5 * 60 * 1000L), error.maxAllowedAt)
    }

    @Test
    fun rule7_editEntry_futureDate_rejected() = runBlocking {
        val goal = (service.createGoal("Vacation", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        val entry = (service.addEntry(goal.id, EntryType.SET_ASIDE, 5_000L, occurredAt = baseMillis) as GoalResult.Success).data

        val beyond5Min = baseMillis + (10 * 60 * 1000L)
        val editResult = service.editEntry(entry.id, 6_000L, "Future edit", beyond5Min)
        assertTrue(editResult is GoalResult.Error)
        assertTrue((editResult as GoalResult.Error).error is GoalError.FutureDate)
    }

    // -------------------------------------------------------------
    // RULE 8: History ordering (newest first by occurredAt, ties by id descending)
    // -------------------------------------------------------------

    @Test
    fun rule8_historyOrdering_newestFirstByOccurredAt() = runBlocking {
        val goal = (service.createGoal("History Goal", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data

        // Add entries with different occurredAt timestamps
        val e1 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 1000L, occurredAt = baseMillis - 3000L) as GoalResult.Success).data
        val e2 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 2000L, occurredAt = baseMillis - 1000L) as GoalResult.Success).data
        val e3 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 3000L, occurredAt = baseMillis - 2000L) as GoalResult.Success).data

        val historyResult = service.history(goal.id)
        assertTrue(historyResult is GoalResult.Success)
        val history = (historyResult as GoalResult.Success).data

        assertEquals(3, history.size)
        // Ordered newest first: e2 (-1000), e3 (-2000), e1 (-3000)
        assertEquals(e2.id, history[0].id)
        assertEquals(e3.id, history[1].id)
        assertEquals(e1.id, history[2].id)
    }

    @Test
    fun rule8_historyOrdering_tiesBrokenByIdDescending() = runBlocking {
        val goal = (service.createGoal("Ties Goal", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val sameTime = baseMillis - 5000L

        val e1 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 1000L, occurredAt = sameTime) as GoalResult.Success).data
        val e2 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 2000L, occurredAt = sameTime) as GoalResult.Success).data
        val e3 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 3000L, occurredAt = sameTime) as GoalResult.Success).data

        val historyResult = service.history(goal.id)
        assertTrue(historyResult is GoalResult.Success)
        val history = (historyResult as GoalResult.Success).data

        assertEquals(3, history.size)
        // Same occurredAt: tie broken by id descending (e3, e2, e1)
        assertEquals(e3.id, history[0].id)
        assertEquals(e2.id, history[1].id)
        assertEquals(e1.id, history[2].id)
    }

    @Test
    fun rule8_history_includeDeletedOption() = runBlocking {
        val goal = (service.createGoal("Delete Filter Goal", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val e1 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 1000L) as GoalResult.Success).data
        val e2 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 2000L) as GoalResult.Success).data

        service.deleteEntry(e1.id)

        // Default history includes deleted entry (with deletedAt set)
        val allHistory = (service.history(goal.id) as GoalResult.Success).data
        assertEquals(2, allHistory.size)
        val deletedInHistory = allHistory.first { it.id == e1.id }
        assertNotNull(deletedInHistory.deletedAt)

        // history(includeDeleted = false) excludes deleted entry
        val activeHistory = (service.history(goal.id, includeDeleted = false) as GoalResult.Success).data
        assertEquals(1, activeHistory.size)
        assertEquals(e2.id, activeHistory[0].id)
    }

    // -------------------------------------------------------------
    // OVERFLOW SAFETY: Summing many large entries cannot overflow silently
    // -------------------------------------------------------------

    @Test
    fun overflowSafety_summingLargeEntriesThrowsArithmeticException_notSilentNegativeWrap() {
        val goal = Goal(id = 1L, name = "Safe Sum", kind = GoalKind.SAVINGS, targetPaise = 100_000L, createdAt = baseMillis)
        val entries = listOf(
            GoalEntry(id = 1L, goalId = 1L, type = EntryType.SET_ASIDE, amountPaise = Long.MAX_VALUE - 10L, occurredAt = baseMillis),
            GoalEntry(id = 2L, goalId = 1L, type = EntryType.SET_ASIDE, amountPaise = 20L, occurredAt = baseMillis)
        )

        // Math.addExact throws ArithmeticException instead of silently overflowing into Long.MIN_VALUE
        assertThrows(ArithmeticException::class.java) {
            calculateProgress(goal, entries)
        }
    }

    @Test
    fun overflowSafety_percentCalculationDoesNotOverflowLong() {
        // Target = 10,000,000,000 paise (10 crore rupees)
        val goal = Goal(id = 1L, name = "Large Target", kind = GoalKind.SAVINGS, targetPaise = MAX_AMOUNT_PAISE, createdAt = baseMillis)
        // Large set aside within Long range
        val entries = listOf(
            GoalEntry(id = 1L, goalId = 1L, type = EntryType.SET_ASIDE, amountPaise = 5_000_000_000L, occurredAt = baseMillis)
        )

        // BigInteger calculation safely calculates 50% without Long multiplication overflow
        val progress = calculateProgress(goal, entries)
        assertEquals(50, progress.percent)
        assertFalse(progress.isReached)
    }

    // -------------------------------------------------------------
    // REPOSITORY FLOW: Flow observation emits updates
    // -------------------------------------------------------------

    @Test
    fun repositoryFlow_observeGoals_emitsUpdatedList() = runBlocking {
        val flow = repository.observeGoals()
        assertEquals(0, flow.first().size)

        service.createGoal("Flow Goal", GoalKind.SAVINGS, 50_000L)
        val updated = flow.first()
        assertEquals(1, updated.size)
        assertEquals("Flow Goal", updated[0].name)
    }

    @Test
    fun repositoryFlow_observeEntries_emitsForSpecificGoal() = runBlocking {
        val goal1 = (service.createGoal("Goal 1", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        val goal2 = (service.createGoal("Goal 2", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data

        val flowGoal1 = repository.observeEntries(goal1.id)
        val flowGoal2 = repository.observeEntries(goal2.id)

        service.addEntry(goal1.id, EntryType.SET_ASIDE, 5000L)
        service.addEntry(goal2.id, EntryType.SET_ASIDE, 8000L)

        assertEquals(1, flowGoal1.first().size)
        assertEquals(5000L, flowGoal1.first()[0].amountPaise)

        assertEquals(1, flowGoal2.first().size)
        assertEquals(8000L, flowGoal2.first()[0].amountPaise)
    }

    // -------------------------------------------------------------
    // G2 ROUND 2: USED ENTRIES + VALIDATION TESTS
    // -------------------------------------------------------------

    @Test
    fun g2_usedEntry_onAvoidanceGoal_rejected() = runBlocking {
        val goal = (service.createGoal("No Smoking", GoalKind.AVOIDANCE, 50_000L) as GoalResult.Success).data
        val result = service.addEntry(goal.id, EntryType.USED, 1_000L)
        assertTrue(result is GoalResult.Error)
        assertEquals(GoalError.WrongEntryType(GoalKind.AVOIDANCE, EntryType.USED), (result as GoalResult.Error).error)
    }

    @Test
    fun g2_usedEntry_boundary_balanceCheck() = runBlocking {
        val goal = (service.createGoal("Trip", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 50_000L)

        // 1 paise more than balance rejected
        val overResult = service.addEntry(goal.id, EntryType.USED, 50_001L)
        assertTrue(overResult is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 50_000L, requestedPaise = 50_001L), (overResult as GoalResult.Error).error)

        // Exact balance accepted
        val exactResult = service.addEntry(goal.id, EntryType.USED, 50_000L)
        assertTrue(exactResult is GoalResult.Success)
        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(0L, progress.savedPaise)
    }

    @Test
    fun g2_editingUsed_upPastBalance_rejected() = runBlocking {
        val goal = (service.createGoal("Trip", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 100_000L)
        val used = (service.addEntry(goal.id, EntryType.USED, 40_000L) as GoalResult.Success).data
        // Current balance is 60_000L

        // Editing up by 60_000L (to 100_000L) leaves balance 0L -> accepted
        val validEdit = service.editEntry(used.id, 100_000L, "Full budget used", used.occurredAt)
        assertTrue(validEdit is GoalResult.Success)

        // Editing up by 1 paise past balance (to 100_001L) -> rejected
        val overEdit = service.editEntry(used.id, 100_001L, "Too much", used.occurredAt)
        assertTrue(overEdit is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 0L, requestedPaise = 1L), (overEdit as GoalResult.Error).error)
    }

    @Test
    fun g2_deletingSetAside_strandingUsed_rejected() = runBlocking {
        val goal = (service.createGoal("Emergency", GoalKind.SAVINGS, 200_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 40_000L)
        val setAside2 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 60_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.USED, 50_000L)
        // Set aside total = 100_000L, used total = 50_000L, balance = 50_000L

        // Deleting setAside2 (60_000L) leaves set aside at 40_000L < used (50_000L) -> rejected
        val deleteResult = service.deleteEntry(setAside2.id)
        assertTrue(deleteResult is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 50_000L, requestedPaise = 60_000L), (deleteResult as GoalResult.Error).error)
    }

    @Test
    fun g2_reducingSetAside_strandingUsed_rejected() = runBlocking {
        val goal = (service.createGoal("Emergency", GoalKind.SAVINGS, 200_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 40_000L)
        val setAside2 = (service.addEntry(goal.id, EntryType.SET_ASIDE, 60_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.USED, 50_000L)
        // Balance = 50_000L

        // Reducing by 55_000L (from 60_000L to 5_000L) when balance is 50_000L -> rejected
        val overReduction = service.editEntry(setAside2.id, 5_000L, null, setAside2.occurredAt)
        assertTrue(overReduction is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 50_000L, requestedPaise = 55_000L), (overReduction as GoalResult.Error).error)

        // Reducing by 50_000L (from 60_000L to 10_000L) leaves balance 0L -> accepted
        val validReduction = service.editEntry(setAside2.id, 10_000L, null, setAside2.occurredAt)
        assertTrue(validReduction is GoalResult.Success)
    }

    @Test
    fun g2_linkExpense_amountEqualToExpenseAccepted_onePaiseMoreRejected() = runBlocking {
        val goal = (service.createGoal("Gadget", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 100_000L)

        // amount > expenseAmount rejected
        val overResult = service.linkExpense(goal.id, transactionId = 42L, expenseAmountPaise = 50_000L, amountPaise = 50_001L)
        assertTrue(overResult is GoalResult.Error)
        assertEquals(GoalError.AmountExceedsExpense(amountPaise = 50_001L, expenseAmountPaise = 50_000L), (overResult as GoalResult.Error).error)

        // amount <= 0 rejected
        val zeroResult = service.linkExpense(goal.id, transactionId = 42L, expenseAmountPaise = 50_000L, amountPaise = 0L)
        assertTrue(zeroResult is GoalResult.Error)
        assertEquals(GoalError.BadAmount(0L), (zeroResult as GoalResult.Error).error)

        // amount == expenseAmount accepted
        val successResult = service.linkExpense(goal.id, transactionId = 42L, expenseAmountPaise = 50_000L, amountPaise = 50_000L)
        assertTrue(successResult is GoalResult.Success)
        val entry = (successResult as GoalResult.Success).data
        assertEquals(EntryType.USED, entry.type)
        assertEquals(50_000L, entry.amountPaise)
        assertEquals(42L, entry.linkedTransactionId)

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(50_000L, progress.savedPaise)
    }

    @Test
    fun g2_linkExpense_relinkingSameTransactionReplacesNotDuplicates_alsoAcrossGoals() = runBlocking {
        val goal1 = (service.createGoal("Goal 1", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        val goal2 = (service.createGoal("Goal 2", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal1.id, EntryType.SET_ASIDE, 100_000L)
        service.addEntry(goal2.id, EntryType.SET_ASIDE, 100_000L)

        // Link tx 100 to Goal 1 with 30_000L
        service.linkExpense(goal1.id, transactionId = 100L, expenseAmountPaise = 50_000L, amountPaise = 30_000L)
        assertEquals(70_000L, (service.getGoalProgress(goal1.id) as GoalResult.Success).data.savedPaise)

        // Re-link tx 100 to Goal 1 with 40_000L (replaces earlier 30_000L, doesn't add to 70_000L)
        service.linkExpense(goal1.id, transactionId = 100L, expenseAmountPaise = 50_000L, amountPaise = 40_000L)
        assertEquals(60_000L, (service.getGoalProgress(goal1.id) as GoalResult.Success).data.savedPaise)

        // Re-link tx 100 to Goal 2 with 50_000L: Goal 1 balance restored to 100_000L, Goal 2 balance drops to 50_000L
        service.linkExpense(goal2.id, transactionId = 100L, expenseAmountPaise = 50_000L, amountPaise = 50_000L)
        assertEquals(100_000L, (service.getGoalProgress(goal1.id) as GoalResult.Success).data.savedPaise)
        assertEquals(50_000L, (service.getGoalProgress(goal2.id) as GoalResult.Success).data.savedPaise)
    }

    @Test
    fun g2_unlinkExpense_restoresBalance() = runBlocking {
        val goal = (service.createGoal("Dining", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 100_000L)
        service.linkExpense(goal.id, transactionId = 77L, expenseAmountPaise = 40_000L, amountPaise = 40_000L)
        assertEquals(60_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        val unlinkResult = service.unlinkExpense(77L)
        assertTrue(unlinkResult is GoalResult.Success)
        assertEquals(100_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Idempotent
        service.unlinkExpense(77L)
        assertEquals(100_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)
    }

    @Test
    fun g2_takeOut_reducesSaved_linkedTransactionIdIsNull() = runBlocking {
        val goal = (service.createGoal("Vacation", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 80_000L)

        val result = service.takeOut(goal.id, 30_000L, "ATM cash")
        assertTrue(result is GoalResult.Success)
        val entry = (result as GoalResult.Success).data
        assertEquals(EntryType.USED, entry.type)
        assertEquals(30_000L, entry.amountPaise)
        assertEquals("ATM cash", entry.note)
        assertNull(entry.linkedTransactionId)

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(50_000L, progress.savedPaise)
        assertEquals(30_000L, progress.usedPaise)
    }

    @Test
    fun g2_cancelAndRestoreUsageForTransaction_idempotent() = runBlocking {
        val goal = (service.createGoal("Bills", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 100_000L)
        service.linkExpense(goal.id, transactionId = 88L, expenseAmountPaise = 40_000L, amountPaise = 40_000L)
        assertEquals(60_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Cancel usage
        val cancelResult = service.cancelUsageForTransaction(88L)
        assertTrue(cancelResult is GoalResult.Success)
        assertEquals(100_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Calling cancel twice is idempotent
        service.cancelUsageForTransaction(88L)
        assertEquals(100_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Restore usage
        val restoreResult = service.restoreUsageForTransaction(88L)
        assertTrue(restoreResult is GoalResult.Success)
        assertEquals(60_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Calling restore twice is idempotent
        service.restoreUsageForTransaction(88L)
        assertEquals(60_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)
    }

    @Test
    fun g2_avoidedDescription_validation_onAddAndEdit() = runBlocking {
        val goal = (service.createGoal("Smoking", GoalKind.AVOIDANCE, 50_000L) as GoalResult.Success).data

        // Blank and whitespace
        val blankResult = service.addEntry(goal.id, EntryType.AVOIDED, 500L, "")
        assertTrue(blankResult is GoalResult.Error)
        assertEquals(GoalError.DescriptionRequired, (blankResult as GoalResult.Error).error)

        val whitespaceResult = service.addEntry(goal.id, EntryType.AVOIDED, 500L, "   \t  ")
        assertTrue(whitespaceResult is GoalResult.Error)
        assertEquals(GoalError.DescriptionRequired, (whitespaceResult as GoalResult.Error).error)

        val nullResult = service.addEntry(goal.id, EntryType.AVOIDED, 500L, null)
        assertTrue(nullResult is GoalResult.Error)
        assertEquals(GoalError.DescriptionRequired, (nullResult as GoalResult.Error).error)

        // 80 characters (accepted)
        val desc80 = "A".repeat(80)
        val validResult = service.addEntry(goal.id, EntryType.AVOIDED, 500L, desc80)
        assertTrue(validResult is GoalResult.Success)
        assertEquals(desc80, (validResult as GoalResult.Success).data.note)

        // 81 characters (rejected)
        val desc81 = "A".repeat(81)
        val overResult = service.addEntry(goal.id, EntryType.AVOIDED, 500L, desc81)
        assertTrue(overResult is GoalResult.Error)
        assertEquals(GoalError.DescriptionTooLong(81, 80), (overResult as GoalResult.Error).error)

        // Edit validation
        val entry = validResult.data
        val editBlank = service.editEntry(entry.id, 600L, "   ", entry.occurredAt)
        assertTrue(editBlank is GoalResult.Error)
        assertEquals(GoalError.DescriptionRequired, (editBlank as GoalResult.Error).error)

        val editOver = service.editEntry(entry.id, 600L, desc81, entry.occurredAt)
        assertTrue(editOver is GoalResult.Error)
        assertEquals(GoalError.DescriptionTooLong(81, 80), (editOver as GoalResult.Error).error)
    }

    @Test
    fun g2_noteLengthBoundaries_setAsideAndUsed() = runBlocking {
        val goal = (service.createGoal("Savings", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data

        val note120 = "N".repeat(120)
        val note121 = "N".repeat(121)

        // SET_ASIDE: 120 ok, 121 rejected
        val setAside120 = service.addEntry(goal.id, EntryType.SET_ASIDE, 10_000L, note120)
        assertTrue(setAside120 is GoalResult.Success)

        val setAside121 = service.addEntry(goal.id, EntryType.SET_ASIDE, 10_000L, note121)
        assertTrue(setAside121 is GoalResult.Error)
        assertEquals(GoalError.NoteTooLong(121, 120), (setAside121 as GoalResult.Error).error)

        // USED: 120 ok, 121 rejected
        val used120 = service.addEntry(goal.id, EntryType.USED, 1_000L, note120)
        assertTrue(used120 is GoalResult.Success)

        val used121 = service.addEntry(goal.id, EntryType.USED, 1_000L, note121)
        assertTrue(used121 is GoalResult.Error)
        assertEquals(GoalError.NoteTooLong(121, 120), (used121 as GoalResult.Error).error)

        // Edit SET_ASIDE note to 121 rejected
        val editSetAside121 = service.editEntry((setAside120 as GoalResult.Success).data.id, 10_000L, note121, baseMillis)
        assertTrue(editSetAside121 is GoalResult.Error)
        assertEquals(GoalError.NoteTooLong(121, 120), (editSetAside121 as GoalResult.Error).error)
    }

    @Test
    fun g2_progressNeverNegative_avoidedTotalUnaffectedByUsedOperations() = runBlocking {
        val goal = (service.createGoal("Dual Fund", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 50_000L)
        service.addEntry(goal.id, EntryType.AVOIDED, 25_000L, "Cooked at home")

        service.addEntry(goal.id, EntryType.USED, 50_000L)

        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(50_000L, progress.setAsidePaise)
        assertEquals(50_000L, progress.usedPaise)
        assertEquals(0L, progress.savedPaise)
        assertEquals(0, progress.percent)
        assertEquals(25_000L, progress.avoidedPaise)
        assertFalse(progress.isReached)
    }

    @Test
    fun g2_savingsGoal_isReached_dependsOnSavedAmount_notJustSetAside() = runBlocking {
        val target = 100_000L
        val goal = (service.createGoal("Laptop", GoalKind.SAVINGS, target) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 100_000L)
        // With 100_000 set aside and 0 used, reached is true
        var progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(100, progress.percent)
        assertTrue(progress.isReached)

        // Using 20_000 reduces saved to 80_000 -> isReached becomes false, percent 80
        service.takeOut(goal.id, 20_000L, "Component deposit")
        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(80_000L, progress.savedPaise)
        assertEquals(80, progress.percent)
        assertFalse(progress.isReached)

        // Adding 20_000 more set aside restores saved to 100_000 -> isReached becomes true again
        service.addEntry(goal.id, EntryType.SET_ASIDE, 20_000L)
        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(100_000L, progress.savedPaise)
        assertEquals(100, progress.percent)
        assertTrue(progress.isReached)
    }

    @Test
    fun g2_takeOut_boundaryAndValidation() = runBlocking {
        val goal = (service.createGoal("Car Maintenance", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 30_000L)

        // Over balance rejected
        val overResult = service.takeOut(goal.id, 30_001L)
        assertTrue(overResult is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 30_000L, requestedPaise = 30_001L), (overResult as GoalResult.Error).error)

        // Exact balance accepted
        val exactResult = service.takeOut(goal.id, 30_000L, "Full withdrawal")
        assertTrue(exactResult is GoalResult.Success)
        val progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(0L, progress.savedPaise)
        assertEquals(30_000L, progress.usedPaise)

        // Avoidance goal rejected
        val avoidGoal = (service.createGoal("No Junk Food", GoalKind.AVOIDANCE, 50_000L) as GoalResult.Success).data
        val avoidTakeOut = service.takeOut(avoidGoal.id, 1000L)
        assertTrue(avoidTakeOut is GoalResult.Error)
        assertEquals(GoalError.WrongEntryType(GoalKind.AVOIDANCE, EntryType.USED), (avoidTakeOut as GoalResult.Error).error)

        // Non-existent goal rejected
        val notFound = service.takeOut(999999L, 1000L)
        assertTrue(notFound is GoalResult.Error)
        assertEquals(GoalError.GoalNotFound(999999L), (notFound as GoalResult.Error).error)
    }

    @Test
    fun g2_restoreEntry_used_rejectedIfInsufficientBalance() = runBlocking {
        val goal = (service.createGoal("Emergency", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 50_000L)
        val used1 = (service.addEntry(goal.id, EntryType.USED, 30_000L) as GoalResult.Success).data
        // Balance = 20_000L

        // Delete used1 -> Balance becomes 50_000L
        service.deleteEntry(used1.id)
        assertEquals(50_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Add another used of 40_000L -> Balance becomes 10_000L
        service.addEntry(goal.id, EntryType.USED, 40_000L)
        assertEquals(10_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Now attempt to restore used1 (amount 30_000L) when only 10_000L is available -> rejected!
        val restoreResult = service.restoreEntry(used1.id)
        assertTrue(restoreResult is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 10_000L, requestedPaise = 30_000L), (restoreResult as GoalResult.Error).error)
    }

    @Test
    fun g2_linkExpense_avoidanceGoalAndInsufficientBalance() = runBlocking {
        val avoidGoal = (service.createGoal("No Gambling", GoalKind.AVOIDANCE, 50_000L) as GoalResult.Success).data
        val avoidLink = service.linkExpense(avoidGoal.id, transactionId = 12L, expenseAmountPaise = 10_000L, amountPaise = 5_000L)
        assertTrue(avoidLink is GoalResult.Error)
        assertEquals(GoalError.WrongEntryType(GoalKind.AVOIDANCE, EntryType.USED), (avoidLink as GoalResult.Error).error)

        val savingsGoal = (service.createGoal("Festive", GoalKind.SAVINGS, 50_000L) as GoalResult.Success).data
        service.addEntry(savingsGoal.id, EntryType.SET_ASIDE, 10_000L)
        // Expense is 20_000, link is 15_000, but goal only has 10_000 balance
        val overBalance = service.linkExpense(savingsGoal.id, transactionId = 13L, expenseAmountPaise = 20_000L, amountPaise = 15_000L)
        assertTrue(overBalance is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 10_000L, requestedPaise = 15_000L), (overBalance as GoalResult.Error).error)
    }

    @Test
    fun g2_restoreUsageForTransaction_insufficientBalance_rejected() = runBlocking {
        val goal = (service.createGoal("Electronics", GoalKind.SAVINGS, 100_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 50_000L)
        service.linkExpense(goal.id, transactionId = 44L, expenseAmountPaise = 40_000L, amountPaise = 40_000L)
        // Balance = 10_000L

        // Cancel usage -> Balance restores to 50_000L
        service.cancelUsageForTransaction(44L)
        assertEquals(50_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Spend 30_000 elsewhere -> Balance = 20_000L
        service.takeOut(goal.id, 30_000L, "Cash")
        assertEquals(20_000L, (service.getGoalProgress(goal.id) as GoalResult.Success).data.savedPaise)

        // Restoring tx 44 (needs 40_000L, only 20_000L available) -> rejected
        val restoreResult = service.restoreUsageForTransaction(44L)
        assertTrue(restoreResult is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 20_000L, requestedPaise = 40_000L), (restoreResult as GoalResult.Error).error)
    }

    // -------------------------------------------------------------
    // G2b: TWO RE-LINK EDGE CASES
    // -------------------------------------------------------------

    @Test
    fun g2b_test1_sameGoal_newAmount_balanceCountsOldAmountAsAvailable() = runBlocking {
        // Goal holds ₹1,000 saved (100_000 paise)
        val goal = (service.createGoal("Vacation Fund", GoalKind.SAVINGS, 200_000L) as GoalResult.Success).data
        service.addEntry(goal.id, EntryType.SET_ASIDE, 100_000L)
        val txId = 501L

        // Link expense T (expense amount ₹800 = 80_000 paise) with ₹600 (60_000 paise) -> saved becomes ₹400 (40_000 paise)
        val initialLink = service.linkExpense(goal.id, transactionId = txId, expenseAmountPaise = 80_000L, amountPaise = 60_000L)
        assertTrue(initialLink is GoalResult.Success)
        var progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(40_000L, progress.savedPaise)

        // Re-link T to the SAME goal with ₹900 (90_000 paise) is invalid (more than the ₹800 expense)
        val overExpenseResult = service.linkExpense(goal.id, transactionId = txId, expenseAmountPaise = 80_000L, amountPaise = 90_000L)
        assertTrue(overExpenseResult is GoalResult.Error)
        assertEquals(GoalError.AmountExceedsExpense(amountPaise = 90_000L, expenseAmountPaise = 80_000L), (overExpenseResult as GoalResult.Error).error)

        // Verify original ₹600 link is still active and saved amount unchanged
        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(40_000L, progress.savedPaise)
        var activeEntries = repository.getEntriesByTransaction(txId).filter { it.deletedAt == null }
        assertEquals(1, activeEntries.size)
        assertEquals(60_000L, activeEntries[0].amountPaise)

        // Re-link T to same goal with amount larger than (current saved + old amount):
        // current saved (40_000) + old amount (60_000) = 100_000 paise.
        // Trying 100_001 paise (with expense 150_000 paise) must be rejected with InsufficientBalance
        val overBalanceResult = service.linkExpense(goal.id, transactionId = txId, expenseAmountPaise = 150_000L, amountPaise = 100_001L)
        assertTrue(overBalanceResult is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 100_000L, requestedPaise = 100_001L), (overBalanceResult as GoalResult.Error).error)

        // Verify original ₹600 link is STILL active and saved amount unchanged
        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(40_000L, progress.savedPaise)
        activeEntries = repository.getEntriesByTransaction(txId).filter { it.deletedAt == null }
        assertEquals(1, activeEntries.size)
        assertEquals(60_000L, activeEntries[0].amountPaise)

        // Re-link with ₹800 (80_000 paise) must SUCCEED: old ₹600 counts as available again (40_000 + 60_000 = 100_000 >= 80_000)
        val validRelink = service.linkExpense(goal.id, transactionId = txId, expenseAmountPaise = 80_000L, amountPaise = 80_000L)
        assertTrue(validRelink is GoalResult.Success)

        // Final saved = ₹200 (20_000 paise) and exactly ONE active USED entry exists for T
        progress = (service.getGoalProgress(goal.id) as GoalResult.Success).data
        assertEquals(20_000L, progress.savedPaise)
        activeEntries = repository.getEntriesByTransaction(txId).filter { it.deletedAt == null }
        assertEquals(1, activeEntries.size)
        assertEquals(80_000L, activeEntries[0].amountPaise)
    }

    @Test
    fun g2b_test2_failedRelink_leavesEverythingAsItWas() = runBlocking {
        // Goal A holds ₹1,000 (100_000 paise), Goal B holds ₹100 (10_000 paise)
        val goalA = (service.createGoal("Goal A", GoalKind.SAVINGS, 200_000L) as GoalResult.Success).data
        val goalB = (service.createGoal("Goal B", GoalKind.SAVINGS, 200_000L) as GoalResult.Success).data
        service.addEntry(goalA.id, EntryType.SET_ASIDE, 100_000L)
        service.addEntry(goalB.id, EntryType.SET_ASIDE, 10_000L)
        val txId = 702L

        // Link T (expense ₹500 = 50_000 paise) to A with ₹300 (30_000 paise)
        val linkA = service.linkExpense(goalA.id, transactionId = txId, expenseAmountPaise = 50_000L, amountPaise = 30_000L)
        assertTrue(linkA is GoalResult.Success)
        assertEquals(70_000L, (service.getGoalProgress(goalA.id) as GoalResult.Success).data.savedPaise)
        assertEquals(10_000L, (service.getGoalProgress(goalB.id) as GoalResult.Success).data.savedPaise)

        // Check 1: Try to re-link T to B with ₹300 (30_000 paise) — B cannot afford it (holds only 10_000 paise)
        val failB = service.linkExpense(goalB.id, transactionId = txId, expenseAmountPaise = 50_000L, amountPaise = 30_000L)
        assertTrue(failB is GoalResult.Error)
        assertEquals(GoalError.InsufficientBalance(availablePaise = 10_000L, requestedPaise = 30_000L), (failB as GoalResult.Error).error)

        // Afterwards: A's link is still active, A's saved is unchanged (₹700), B is unchanged (₹100), exactly 1 active USED entry for T
        assertEquals(70_000L, (service.getGoalProgress(goalA.id) as GoalResult.Success).data.savedPaise)
        assertEquals(10_000L, (service.getGoalProgress(goalB.id) as GoalResult.Success).data.savedPaise)
        var activeEntries = repository.getEntriesByTransaction(txId).filter { it.deletedAt == null }
        assertEquals(1, activeEntries.size)
        assertEquals(goalA.id, activeEntries[0].goalId)
        assertEquals(30_000L, activeEntries[0].amountPaise)

        // Check 2: Same check when the failure is AmountExceedsExpense (trying ₹600 on ₹500 expense)
        val failExceeds = service.linkExpense(goalB.id, transactionId = txId, expenseAmountPaise = 50_000L, amountPaise = 60_000L)
        assertTrue(failExceeds is GoalResult.Error)
        assertEquals(GoalError.AmountExceedsExpense(amountPaise = 60_000L, expenseAmountPaise = 50_000L), (failExceeds as GoalResult.Error).error)

        // Afterwards: A's link is still active, A's saved is unchanged, B is unchanged, exactly 1 active USED entry for T
        assertEquals(70_000L, (service.getGoalProgress(goalA.id) as GoalResult.Success).data.savedPaise)
        assertEquals(10_000L, (service.getGoalProgress(goalB.id) as GoalResult.Success).data.savedPaise)
        activeEntries = repository.getEntriesByTransaction(txId).filter { it.deletedAt == null }
        assertEquals(1, activeEntries.size)
        assertEquals(goalA.id, activeEntries[0].goalId)
        assertEquals(30_000L, activeEntries[0].amountPaise)

        // Check 3: Same check when target goal is an AVOIDANCE goal
        val goalC = (service.createGoal("Goal C Avoidance", GoalKind.AVOIDANCE, 100_000L) as GoalResult.Success).data
        val failAvoidance = service.linkExpense(goalC.id, transactionId = txId, expenseAmountPaise = 50_000L, amountPaise = 20_000L)
        assertTrue(failAvoidance is GoalResult.Error)
        assertEquals(GoalError.WrongEntryType(GoalKind.AVOIDANCE, EntryType.USED), (failAvoidance as GoalResult.Error).error)

        // Afterwards: A's link is still active, A's saved is unchanged, Goal C has 0 entries, exactly 1 active USED entry for T
        assertEquals(70_000L, (service.getGoalProgress(goalA.id) as GoalResult.Success).data.savedPaise)
        assertEquals(0, repository.getEntries(goalC.id).size)
        activeEntries = repository.getEntriesByTransaction(txId).filter { it.deletedAt == null }
        assertEquals(1, activeEntries.size)
        assertEquals(goalA.id, activeEntries[0].goalId)
        assertEquals(30_000L, activeEntries[0].amountPaise)
    }
}



