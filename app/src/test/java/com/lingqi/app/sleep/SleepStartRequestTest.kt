package com.lingqi.app.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStartRequestTest {
    @Test
    fun pendingUiStatusUsesTheSameTimestampSentToTheService() {
        val request = SleepStartRequest(sessionId = "night", startedAt = 1_784_083_667_198L)

        val status = request.asTrackingStatus()

        assertTrue(status.active)
        assertEquals("night", status.sessionId)
        assertEquals(1_784_083_667_198L, status.startedAt)
    }
}
