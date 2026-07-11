package com.lingqi.app.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPermissionPolicyTest {
    @Test
    fun reminderToggleRequestsPermissionBeforeEnablingOnAndroid13() {
        assertEquals(
            ReminderToggleAction.REQUEST_PERMISSION,
            NotificationPermissionPolicy.reminderToggleAction(
                requestedEnabled = true,
                sdkInt = 33,
                permissionGranted = false
            )
        )
    }

    @Test
    fun reminderToggleOnlyEnablesWithPermissionAndDisablesDirectly() {
        assertEquals(
            ReminderToggleAction.ENABLE,
            NotificationPermissionPolicy.reminderToggleAction(true, 33, true)
        )
        assertEquals(
            ReminderToggleAction.ENABLE,
            NotificationPermissionPolicy.reminderToggleAction(true, 32, false)
        )
        assertEquals(
            ReminderToggleAction.DISABLE,
            NotificationPermissionPolicy.reminderToggleAction(false, 36, false)
        )
    }

    @Test
    fun enabledRemindersMustBeTurnedOffAfterPermissionIsRevoked() {
        assertTrue(
            NotificationPermissionPolicy.mustDisableReminders(
                sdkInt = 33,
                permissionGranted = false,
                meditationEnabled = true,
                sleepEnabled = false
            )
        )
        assertFalse(NotificationPermissionPolicy.mustDisableReminders(33, false, false, false))
        assertFalse(NotificationPermissionPolicy.mustDisableReminders(33, true, true, true))
        assertFalse(NotificationPermissionPolicy.mustDisableReminders(32, false, true, true))
    }

    @Test
    fun preAndroid13DoesNotRequireRuntimeGrant() {
        assertTrue(NotificationPermissionPolicy.canPost(32, false))
    }

    @Test
    fun android13AndLaterRequireRuntimeGrant() {
        assertFalse(NotificationPermissionPolicy.canPost(33, false))
        assertTrue(NotificationPermissionPolicy.canPost(33, true))
        assertFalse(NotificationPermissionPolicy.canPost(36, false))
    }
}
