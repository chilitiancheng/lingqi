package com.lingqi.app.sleep

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepDeletionSafetyTest {
    @Test
    fun epochPersistenceStopsAsSoonAsTrackingIsQuiesced() {
        assertTrue(shouldPersistSleepEpoch(trackingActive = true))
        assertFalse(shouldPersistSleepEpoch(trackingActive = false))
    }
}
