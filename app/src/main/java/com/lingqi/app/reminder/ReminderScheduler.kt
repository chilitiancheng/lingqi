package com.lingqi.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

object ReminderScheduler {
    const val TYPE_MEDITATION = "meditation"
    const val TYPE_SLEEP = "sleep"

    fun schedule(context: Context, type: String, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = pendingIntent(context, type)
        val trigger = nextDailyTriggerMillis(
            nowMillis = System.currentTimeMillis(),
            zoneId = ZoneId.systemDefault(),
            hour = hour,
            minute = minute
        )
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            trigger,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancel(context: Context, type: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, type))
    }

    fun cancelAll(context: Context) {
        cancel(context, TYPE_MEDITATION)
        cancel(context, TYPE_SLEEP)
    }

    private fun pendingIntent(context: Context, type: String): PendingIntent {
        return PendingIntent.getBroadcast(
            context,
            if (type == TYPE_MEDITATION) 2100 else 2300,
            Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_TYPE, type),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

internal fun nextDailyTriggerMillis(
    nowMillis: Long,
    zoneId: ZoneId,
    hour: Int,
    minute: Int
): Long {
    require(hour in 0..23) { "Reminder hour must be between 0 and 23" }
    require(minute in 0..59) { "Reminder minute must be between 0 and 59" }
    val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
    val today = now.toLocalDate().atTime(hour, minute).atZone(zoneId)
    return (if (today.toInstant().toEpochMilli() <= nowMillis) today.plusDays(1) else today)
        .toInstant()
        .toEpochMilli()
}

internal fun formatReminderTime(hour: Int, minute: Int): String {
    require(hour in 0..23) { "Reminder hour must be between 0 and 23" }
    require(minute in 0..59) { "Reminder minute must be between 0 and 59" }
    return String.format(Locale.ROOT, "%02d:%02d", hour, minute)
}
