package com.lingqi.app.sleep

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.io.File
import java.util.UUID

data class SleepTrackingStatus(
    val active: Boolean,
    val sessionId: String? = null,
    val startedAt: Long = 0L
)

data class SleepStartRequest(
    val sessionId: String,
    val startedAt: Long
) {
    fun asTrackingStatus(): SleepTrackingStatus = SleepTrackingStatus(
        active = true,
        sessionId = sessionId,
        startedAt = startedAt
    )
}

object SleepTracker {
    internal const val PREFS = "sleep_tracker_state"
    internal const val KEY_ACTIVE = "active"
    internal const val KEY_SESSION_ID = "session_id"
    internal const val KEY_STARTED_AT = "started_at"

    fun startSession(context: Context): SleepStartRequest {
        val existing = getStatus(context)
        if (existing.active && existing.sessionId != null) {
            return SleepStartRequest(
                existing.sessionId,
                existing.startedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
            )
        }
        val sessionId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val intent = Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_START
            putExtra(SleepTrackingService.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepTrackingService.EXTRA_STARTED_AT, startedAt)
        }
        ContextCompat.startForegroundService(context, intent)
        return SleepStartRequest(sessionId, startedAt)
    }

    fun stopSession(context: Context) {
        context.startService(Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_STOP
        })
    }

    fun discardSession(context: Context) {
        context.startService(Intent(context, SleepTrackingService::class.java).apply {
            action = SleepTrackingService.ACTION_DISCARD
        })
    }

    fun quiesceAndDiscardForDeletion(context: Context): Boolean =
        SleepTrackingService.quiesceAndDiscardForDeletion(context)

    fun getStatus(context: Context): SleepTrackingStatus {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return SleepTrackingStatus(
            active = prefs.getBoolean(KEY_ACTIVE, false),
            sessionId = prefs.getString(KEY_SESSION_ID, null),
            startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        )
    }

    fun clearPersistedState(context: Context) {
        val preferencesCleared = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        check(preferencesCleared) { "Unable to clear persisted sleep tracking state" }

        val checkpointDirectory = File(context.filesDir, CHECKPOINT_DIRECTORY)
        check(!checkpointDirectory.exists() || checkpointDirectory.deleteRecursively()) {
            "Unable to clear persisted sleep checkpoints"
        }
    }

    private const val CHECKPOINT_DIRECTORY = "sleep-checkpoints"
}
