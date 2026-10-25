package com.autoflow.app.widget

import com.autoflow.app.domain.goals.Goal
import com.autoflow.app.presentation.preferences.InMemoryGoalPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetIntentHandlingTest {

    private fun createGoal(id: Long, name: String, isDeleted: Boolean = false): Goal {
        return Goal(
            id = id,
            name = name,
            nameKey = name.lowercase(),
            targetPaise = 100000L,
            deadlineAt = null,
            isDeleted = isDeleted,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    @Test
    fun testActionExpense_resolvesToQuickExpense() {
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_EXPENSE,
            goals = emptyList(),
            lastUsedGoalId = null
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.QuickExpense)
    }

    @Test
    fun testActionSaved_withNoGoals_resolvesToNewGoalWithHint() {
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_SAVED,
            goals = emptyList(),
            lastUsedGoalId = null
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.NewGoalWithHint)
    }

    @Test
    fun testActionSaved_withOnlyDeletedGoals_resolvesToNewGoalWithHint() {
        val deletedGoal = createGoal(1L, "Old Trip", isDeleted = true)
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_SAVED,
            goals = listOf(deletedGoal),
            lastUsedGoalId = 1L
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.NewGoalWithHint)
    }

    @Test
    fun testActionSaved_withOneGoal_resolvesToSavedWithThatGoalPreselected() {
        val goal1 = createGoal(42L, "MacBook")
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_SAVED,
            goals = listOf(goal1),
            lastUsedGoalId = null
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.Saved)
        val saved = destination as WidgetDestination.Saved
        assertEquals(42L, saved.selectedGoalId)
        assertEquals(1, saved.goals.size)
        assertEquals("MacBook", saved.goals.first().name)
    }

    @Test
    fun testActionSaved_withManyGoals_andNoLastUsed_preselectsFirstGoal() {
        val g1 = createGoal(10L, "Goal 1")
        val g2 = createGoal(20L, "Goal 2")
        val g3 = createGoal(30L, "Goal 3")
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_SAVED,
            goals = listOf(g1, g2, g3),
            lastUsedGoalId = null
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.Saved)
        val saved = destination as WidgetDestination.Saved
        assertEquals(10L, saved.selectedGoalId)
        assertEquals(3, saved.goals.size)
    }

    @Test
    fun testActionSaved_withManyGoals_andLastUsedSet_preselectsLastUsedGoal() {
        val g1 = createGoal(10L, "Goal 1")
        val g2 = createGoal(20L, "Goal 2")
        val g3 = createGoal(30L, "Goal 3")
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_SAVED,
            goals = listOf(g1, g2, g3),
            lastUsedGoalId = 20L
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.Saved)
        val saved = destination as WidgetDestination.Saved
        assertEquals(20L, saved.selectedGoalId)
    }

    @Test
    fun testActionSaved_withManyGoals_andLastUsedNonExistent_fallbacksToFirst() {
        val g1 = createGoal(10L, "Goal 1")
        val g2 = createGoal(20L, "Goal 2")
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_SAVED,
            goals = listOf(g1, g2),
            lastUsedGoalId = 999L // Non-existent
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.Saved)
        val saved = destination as WidgetDestination.Saved
        assertEquals(10L, saved.selectedGoalId)
    }

    @Test
    fun testActionSaved_withManyGoals_andLastUsedIsDeleted_fallbacksToFirst() {
        val g1 = createGoal(10L, "Active 1")
        val g2 = createGoal(20L, "Deleted 2", isDeleted = true)
        val destination = WidgetActionResolver.resolveAction(
            action = WidgetActionResolver.ACTION_SAVED,
            goals = listOf(g1, g2),
            lastUsedGoalId = 20L // Last used is now deleted
        )
        assertNotNull(destination)
        assertTrue(destination is WidgetDestination.Saved)
        val saved = destination as WidgetDestination.Saved
        assertEquals(10L, saved.selectedGoalId)
        assertEquals(1, saved.goals.size)
    }

    @Test
    fun testUnknownAction_isIgnored() {
        val destination = WidgetActionResolver.resolveAction(
            action = "com.autoflow.app.action.SOME_UNKNOWN_ACTION",
            goals = listOf(createGoal(1L, "Goal")),
            lastUsedGoalId = 1L
        )
        assertNull(destination)
    }

    @Test
    fun testNullAction_isIgnored() {
        val destination = WidgetActionResolver.resolveAction(
            action = null,
            goals = listOf(createGoal(1L, "Goal")),
            lastUsedGoalId = 1L
        )
        assertNull(destination)
    }

    @Test
    fun testMalformedAction_isIgnored() {
        val destination = WidgetActionResolver.resolveAction(
            action = "android.intent.action.VIEW",
            goals = listOf(createGoal(1L, "Goal")),
            lastUsedGoalId = 1L
        )
        assertNull(destination)
    }

    @Test
    fun testGoalPreferences_storesAndRetrievesLastUsedGoal() {
        val prefs = InMemoryGoalPreferences()
        assertNull(prefs.getLastUsedGoalId())

        prefs.setLastUsedGoalId(42L)
        assertEquals(42L, prefs.getLastUsedGoalId())

        prefs.setLastUsedGoalId(88L)
        assertEquals(88L, prefs.getLastUsedGoalId())
    }

    @Test
    fun testSquareWidget_usesIdenticalActionsAsHorizontalWidget() {
        // Both horizontal and square widgets use AutoFlowWidgetProvider.ACTION_EXPENSE and ACTION_SAVED
        val expenseAction = AutoFlowWidgetProvider.ACTION_EXPENSE
        val savedAction = AutoFlowWidgetProvider.ACTION_SAVED

        val expenseDest = WidgetActionResolver.resolveAction(
            action = expenseAction,
            goals = emptyList(),
            lastUsedGoalId = null
        )
        assertTrue(expenseDest is WidgetDestination.QuickExpense)

        val savedDest = WidgetActionResolver.resolveAction(
            action = savedAction,
            goals = listOf(createGoal(1L, "Trip")),
            lastUsedGoalId = null
        )
        assertTrue(savedDest is WidgetDestination.Saved)
    }

    @Test
    fun testWidgetConstants() {
        assertEquals("com.autoflow.app.action.WIDGET_EXPENSE", AutoFlowWidgetProvider.ACTION_EXPENSE)
        assertEquals("com.autoflow.app.action.WIDGET_SAVED", AutoFlowWidgetProvider.ACTION_SAVED)
    }
}
