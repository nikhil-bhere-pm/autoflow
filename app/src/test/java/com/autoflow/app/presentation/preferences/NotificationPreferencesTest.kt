package com.autoflow.app.presentation.preferences

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPreferencesTest {

    @Test
    fun inMemoryPreferences_defaultValuesMatchRequirement() {
        val prefs = InMemoryNotificationPreferences()
        assertTrue("Notifications should be enabled by default", prefs.isCapturedExpenseNotificationEnabled())
        assertFalse("Permission request should be false by default", prefs.hasRequestedNotificationPermission())
    }

    @Test
    fun inMemoryPreferences_togglePersistsCorrectly() {
        val prefs = InMemoryNotificationPreferences()

        prefs.setCapturedExpenseNotificationEnabled(false)
        assertFalse(prefs.isCapturedExpenseNotificationEnabled())

        prefs.setCapturedExpenseNotificationEnabled(true)
        assertTrue(prefs.isCapturedExpenseNotificationEnabled())

        prefs.setHasRequestedNotificationPermission(true)
        assertTrue(prefs.hasRequestedNotificationPermission())

        prefs.setHasRequestedNotificationPermission(false)
        assertFalse(prefs.hasRequestedNotificationPermission())
    }

    @Test
    fun inMemoryPreferences_customInitialValues() {
        val prefs = InMemoryNotificationPreferences(initialEnabled = false, initialRequested = true)
        assertFalse(prefs.isCapturedExpenseNotificationEnabled())
        assertTrue(prefs.hasRequestedNotificationPermission())
    }
}
