package com.autoflow.app.presentation.ui

import com.autoflow.app.presentation.preferences.FirstRunPreferences
import com.autoflow.app.presentation.preferences.InMemoryFirstRunPreferences
import com.autoflow.app.presentation.ui.firstrun.PermissionPromptResult
import com.autoflow.app.presentation.ui.firstrun.resolveNextScreenOnPermissionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunTest {

    @Test
    fun testFirstRunFlagInitiallyFalse() {
        val prefs: FirstRunPreferences = InMemoryFirstRunPreferences(initialCompleted = false)
        assertFalse(prefs.isFirstRunCompleted)
    }

    @Test
    fun testFirstRunFlagNeverShownAgainAfterNotNow() {
        val prefs: FirstRunPreferences = InMemoryFirstRunPreferences(initialCompleted = false)
        assertFalse(prefs.isFirstRunCompleted)

        prefs.setFirstRunCompleted(true)
        assertTrue(prefs.isFirstRunCompleted)
    }

    @Test
    fun testFirstRunFlagNeverShownAgainAfterPermissionChoice() {
        val prefs: FirstRunPreferences = InMemoryFirstRunPreferences(initialCompleted = false)
        assertFalse(prefs.isFirstRunCompleted)

        prefs.setFirstRunCompleted(true)
        assertTrue(prefs.isFirstRunCompleted)
    }

    @Test
    fun testPermissionResultMappingGrantedGoesToHome() {
        val nextScreen = resolveNextScreenOnPermissionResult(
            result = PermissionPromptResult.Granted,
            fromSettings = false
        )
        assertEquals(Screen.Home, nextScreen)
    }

    @Test
    fun testPermissionResultMappingDeniedGoesToHome() {
        // Denied -> go to Home (cash still works), no nagging
        val nextScreen = resolveNextScreenOnPermissionResult(
            result = PermissionPromptResult.Denied,
            fromSettings = false
        )
        assertEquals(Screen.Home, nextScreen)
    }

    @Test
    fun testPermissionResultMappingBlockedOrRestrictedGoesToHelp() {
        // If Android refuses to show the prompt because the app is sideloaded/restricted, open SCREEN 2
        val nextScreen = resolveNextScreenOnPermissionResult(
            result = PermissionPromptResult.BlockedOrRestricted,
            fromSettings = false
        )
        assertEquals(Screen.PermissionHelp(fromSettings = false), nextScreen)
    }

    @Test
    fun testPermissionResultMappingFromSettingsWhenGrantedGoesToSettings() {
        val nextScreen = resolveNextScreenOnPermissionResult(
            result = PermissionPromptResult.Granted,
            fromSettings = true
        )
        assertEquals(Screen.Settings, nextScreen)
    }
}
