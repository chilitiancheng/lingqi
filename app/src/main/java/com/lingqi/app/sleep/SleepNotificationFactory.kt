package com.lingqi.app.sleep

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.lingqi.app.R

internal const val SLEEP_NOTIFICATION_CHANNEL_ID = "lingqi_sleep_tracking"

internal fun buildSleepTrackingNotification(
    context: Context,
    elapsedMinutes: Long,
    contentIntent: PendingIntent,
    stopIntent: PendingIntent
): Notification {
    val spec = sleepNotificationSpec(elapsedMinutes)
    val collapsedView = sleepNotificationView(context, spec, contentIntent, stopIntent)
    val expandedView = sleepNotificationView(context, spec, contentIntent, stopIntent)
    val stopAction = NotificationCompat.Action.Builder(
        spec.stopActionIconRes,
        context.getString(R.string.sleep_notification_stop),
        stopIntent
    )
        .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_DELETE)
        .setShowsUserInterface(false)
        .build()

    return NotificationCompat.Builder(context, SLEEP_NOTIFICATION_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(context.getString(R.string.sleep_notification_title))
        .setContentText(spec.contentText)
        .setContentIntent(contentIntent)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        .setCustomContentView(collapsedView)
        .setCustomBigContentView(expandedView)
        .addAction(stopAction)
        .build()
}

private fun sleepNotificationView(
    context: Context,
    spec: SleepNotificationSpec,
    contentIntent: PendingIntent,
    stopIntent: PendingIntent
): RemoteViews = RemoteViews(context.packageName, R.layout.notification_sleep_tracking).apply {
    setTextViewText(R.id.sleep_notification_title, context.getString(R.string.sleep_notification_title))
    setTextViewText(R.id.sleep_notification_elapsed, spec.contentText)
    setTextViewText(R.id.sleep_notification_stop, context.getString(R.string.sleep_notification_stop))
    setOnClickPendingIntent(R.id.sleep_notification_content, contentIntent)
    setOnClickPendingIntent(R.id.sleep_notification_stop, stopIntent)
}
