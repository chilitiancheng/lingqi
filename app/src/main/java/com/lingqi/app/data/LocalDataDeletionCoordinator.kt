package com.lingqi.app.data

class LocalDataDeletionCoordinator(
    private val cancelAllReminders: () -> Unit,
    private val quiesceAndDiscardSleepTracking: () -> Boolean,
    private val clearSleepTrackingState: () -> Unit,
    private val clearRepositoryData: () -> Unit
) {
    fun deleteAll() {
        cancelAllReminders()
        check(quiesceAndDiscardSleepTracking()) {
            "Sleep tracking must be quiescent before deleting local data"
        }
        clearSleepTrackingState()
        clearRepositoryData()
    }
}
