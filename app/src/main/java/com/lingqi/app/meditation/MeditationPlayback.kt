package com.lingqi.app.meditation

internal data class MeditationPlaybackClock(
    val elapsedMillis: Long,
    val lastTickMillis: Long
)

internal fun isMeditationPlaybackBlocked(
    manuallyPaused: Boolean,
    exitDialogVisible: Boolean,
    completed: Boolean
): Boolean = manuallyPaused || exitDialogVisible || completed

internal fun advanceMeditationPlaybackClock(
    elapsedMillis: Long,
    lastTickMillis: Long,
    nowMillis: Long,
    playbackBlocked: Boolean,
    plannedMillis: Long
): MeditationPlaybackClock {
    val elapsed = if (playbackBlocked) {
        elapsedMillis
    } else {
        elapsedMillis + (nowMillis - lastTickMillis).coerceAtLeast(0L)
    }
    return MeditationPlaybackClock(
        elapsedMillis = elapsed.coerceAtMost(plannedMillis),
        lastTickMillis = nowMillis
    )
}
