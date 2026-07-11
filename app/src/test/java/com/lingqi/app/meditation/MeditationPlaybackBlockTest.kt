package com.lingqi.app.meditation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeditationPlaybackBlockTest {
    @Test
    fun exitDialogAndCompletionBlockPlaybackWithoutChangingManualPauseIntent() {
        assertTrue(isMeditationPlaybackBlocked(manuallyPaused = false, exitDialogVisible = true, completed = false))
        assertFalse(isMeditationPlaybackBlocked(manuallyPaused = false, exitDialogVisible = false, completed = false))
        assertTrue(isMeditationPlaybackBlocked(manuallyPaused = true, exitDialogVisible = false, completed = false))
        assertTrue(isMeditationPlaybackBlocked(manuallyPaused = false, exitDialogVisible = false, completed = true))
    }

    @Test
    fun blockedTickFreezesElapsedAndResetsClockOrigin() {
        val blocked = advanceMeditationPlaybackClock(
            elapsedMillis = 1_000L,
            lastTickMillis = 2_000L,
            nowMillis = 7_000L,
            playbackBlocked = true,
            plannedMillis = 60_000L
        )

        assertEquals(1_000L, blocked.elapsedMillis)
        assertEquals(7_000L, blocked.lastTickMillis)

        val resumed = advanceMeditationPlaybackClock(
            elapsedMillis = blocked.elapsedMillis,
            lastTickMillis = blocked.lastTickMillis,
            nowMillis = 7_100L,
            playbackBlocked = false,
            plannedMillis = 60_000L
        )
        assertEquals(1_100L, resumed.elapsedMillis)
        assertEquals(7_100L, resumed.lastTickMillis)
    }
}
