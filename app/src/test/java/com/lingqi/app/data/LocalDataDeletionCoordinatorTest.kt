package com.lingqi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalDataDeletionCoordinatorTest {
    @Test
    fun deleteAllCoordinatesExternalStateBeforeClearingRepository() {
        val events = mutableListOf<String>()
        val coordinator = LocalDataDeletionCoordinator(
            cancelAllReminders = { events += "cancel-reminders" },
            quiesceAndDiscardSleepTracking = {
                events += "discard-sleep"
                true
            },
            clearSleepTrackingState = { events += "clear-sleep-state" },
            clearRepositoryData = { events += "clear-repository" }
        )

        coordinator.deleteAll()

        assertEquals(
            listOf(
                "cancel-reminders",
                "discard-sleep",
                "clear-sleep-state",
                "clear-repository"
            ),
            events
        )
    }

    @Test
    fun repositoryIsNotClearedUntilSleepTrackingConfirmsItIsQuiescent() {
        var sleepStateCleared = false
        var repositoryCleared = false
        val coordinator = LocalDataDeletionCoordinator(
            cancelAllReminders = {},
            quiesceAndDiscardSleepTracking = { false },
            clearSleepTrackingState = { sleepStateCleared = true },
            clearRepositoryData = { repositoryCleared = true }
        )

        val error = runCatching { coordinator.deleteAll() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(false, sleepStateCleared)
        assertEquals(false, repositoryCleared)
    }
}
