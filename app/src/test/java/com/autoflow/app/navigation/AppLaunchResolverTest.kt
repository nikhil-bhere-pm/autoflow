package com.autoflow.app.navigation

import android.content.Intent
import com.autoflow.app.domain.goals.Goal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLaunchResolverTest {

    private class FakeIntent(
        private var actionString: String? = null,
        private val extrasMap: MutableMap<String, Any?> = mutableMapOf(),
        private var flagsInt: Int = 0
    ) : Intent() {
        override fun getAction(): String? = actionString
        override fun setAction(action: String?): Intent {
            actionString = action
            return this
        }
        override fun getFlags(): Int = flagsInt
        override fun setFlags(flags: Int): Intent {
            flagsInt = flags
            return this
        }
        override fun putExtra(name: String, value: Boolean): Intent {
            extrasMap[name] = value
            return this
        }
        override fun putExtra(name: String, value: Long): Intent {
            extrasMap[name] = value
            return this
        }
        override fun getBooleanExtra(name: String, defaultValue: Boolean): Boolean {
            return extrasMap[name] as? Boolean ?: defaultValue
        }
        override fun getLongExtra(name: String, defaultValue: Long): Long {
            return extrasMap[name] as? Long ?: defaultValue
        }
    }

    private fun createGoal(id: Long, name: String, isDeleted: Boolean = false): Goal {
        return Goal(
            id = id,
            name = name,
            nameKey = name.lowercase(),
            targetPaise = 50000L,
            deadlineAt = null,
            isDeleted = isDeleted,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    @Test
    fun testResolve_openHomeFlag_resolvesToHome() {
        val target = AppLaunchResolver.resolve(
            action = null,
            isOpenHome = true,
            transactionId = -1L
        )
        assertEquals(AppLaunchTarget.Home, target)
    }

    @Test
    fun testResolve_actionOpenHome_resolvesToHome() {
        val target = AppLaunchResolver.resolve(
            action = AppLaunchResolver.ACTION_OPEN_HOME,
            isOpenHome = false,
            transactionId = -1L
        )
        assertEquals(AppLaunchTarget.Home, target)
    }

    @Test
    fun testResolve_editTransaction_withValidId_resolvesToEditTransaction() {
        val target = AppLaunchResolver.resolve(
            action = AppLaunchResolver.ACTION_EDIT_TRANSACTION,
            isOpenHome = false,
            transactionId = 42L
        )
        assertEquals(AppLaunchTarget.EditTransaction(42L), target)
    }

    @Test
    fun testResolve_editTransaction_withInvalidId_resolvesToNone() {
        val target = AppLaunchResolver.resolve(
            action = AppLaunchResolver.ACTION_EDIT_TRANSACTION,
            isOpenHome = false,
            transactionId = -1L
        )
        assertEquals(AppLaunchTarget.None, target)
    }

    @Test
    fun testResolve_quickExpense_resolvesToQuickExpense() {
        val target = AppLaunchResolver.resolve(
            action = AppLaunchResolver.ACTION_EXPENSE,
            isOpenHome = false,
            transactionId = -1L
        )
        assertEquals(AppLaunchTarget.QuickExpense, target)
    }

    @Test
    fun testResolve_saved_withActiveGoals_resolvesToSavedWithSelectedGoal() {
        val goals = listOf(createGoal(101L, "Trip"), createGoal(102L, "MacBook"))
        val target = AppLaunchResolver.resolve(
            action = AppLaunchResolver.ACTION_SAVED,
            isOpenHome = false,
            transactionId = -1L,
            goals = goals,
            lastUsedGoalId = 102L
        )
        assertTrue(target is AppLaunchTarget.Saved)
        val saved = target as AppLaunchTarget.Saved
        assertEquals(102L, saved.selectedGoalId)
        assertEquals(2, saved.goals.size)
    }

    @Test
    fun testResolve_saved_withNoGoals_resolvesToNewGoalWithHint() {
        val target = AppLaunchResolver.resolve(
            action = AppLaunchResolver.ACTION_SAVED,
            isOpenHome = false,
            transactionId = -1L,
            goals = emptyList()
        )
        assertEquals(AppLaunchTarget.NewGoalWithHint, target)
    }

    @Test
    fun testResolve_unknownAction_resolvesToNone() {
        val target = AppLaunchResolver.resolve(
            action = "random.unknown.ACTION",
            isOpenHome = false,
            transactionId = -1L
        )
        assertEquals(AppLaunchTarget.None, target)
    }

    @Test
    fun testResolve_nullIntent_resolvesToNone() {
        val target = AppLaunchResolver.resolve(intent = null)
        assertEquals(AppLaunchTarget.None, target)
    }

    @Test
    fun testResolve_withIntent_openHomeExtra() {
        val fakeIntent = FakeIntent()
        fakeIntent.putExtra(AppLaunchResolver.EXTRA_OPEN_HOME, true)

        val target = AppLaunchResolver.resolve(fakeIntent)
        assertEquals(AppLaunchTarget.Home, target)
    }

    @Test
    fun testResolve_withIntent_editTransaction() {
        val fakeIntent = FakeIntent(actionString = AppLaunchResolver.ACTION_EDIT_TRANSACTION)
        fakeIntent.putExtra(AppLaunchResolver.EXTRA_TRANSACTION_ID, 99L)

        val target = AppLaunchResolver.resolve(fakeIntent)
        assertEquals(AppLaunchTarget.EditTransaction(99L), target)
    }

    @Test
    fun testBuildHomeIntent_setsExtraAndRequiredFlags() {
        val fakeIntent = FakeIntent()
        AppLaunchResolver.buildHomeIntent(fakeIntent)

        assertTrue(fakeIntent.getBooleanExtra(AppLaunchResolver.EXTRA_OPEN_HOME, false))

        val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP

        assertEquals(expectedFlags, fakeIntent.flags)
        assertTrue((fakeIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        assertTrue((fakeIntent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP) != 0)
        assertTrue((fakeIntent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP) != 0)
    }
}
