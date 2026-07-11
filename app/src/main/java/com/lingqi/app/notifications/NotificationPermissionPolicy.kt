package com.lingqi.app.notifications

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

enum class ReminderToggleAction {
    ENABLE,
    REQUEST_PERMISSION,
    DISABLE
}

object NotificationPermissionPolicy {
    fun canPost(sdkInt: Int, permissionGranted: Boolean): Boolean =
        sdkInt < Build.VERSION_CODES.TIRAMISU || permissionGranted

    fun reminderToggleAction(
        requestedEnabled: Boolean,
        sdkInt: Int,
        permissionGranted: Boolean
    ): ReminderToggleAction = when {
        !requestedEnabled -> ReminderToggleAction.DISABLE
        canPost(sdkInt, permissionGranted) -> ReminderToggleAction.ENABLE
        else -> ReminderToggleAction.REQUEST_PERMISSION
    }

    fun mustDisableReminders(
        sdkInt: Int,
        permissionGranted: Boolean,
        meditationEnabled: Boolean,
        sleepEnabled: Boolean
    ): Boolean = !canPost(sdkInt, permissionGranted) && (meditationEnabled || sleepEnabled)
}

fun Context.canPostNotifications(): Boolean = NotificationPermissionPolicy.canPost(
    sdkInt = Build.VERSION.SDK_INT,
    permissionGranted = ContextCompat.checkSelfPermission(
        this,
        POST_NOTIFICATIONS_PERMISSION
    ) == PackageManager.PERMISSION_GRANTED
)

private const val POST_NOTIFICATIONS_PERMISSION = "android.permission.POST_NOTIFICATIONS"
