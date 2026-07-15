package com.lingqi.app.meditation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GuidedVoiceStateTest {
    @Test
    fun firstCueWaitsDuringInitializationAndPlaysOnceWhenReady() {
        val state = GuidedVoiceState()
        state.enqueueWhileInitializing("第一句")

        assertEquals("第一句", state.markReady())
        assertNull(state.markReady())
    }

    @Test
    fun unavailableClearsPendingCue() {
        val state = GuidedVoiceState()
        state.enqueueWhileInitializing("第一句")

        state.markUnavailable()

        assertNull(state.markReady())
        assertEquals(GuidedVoiceStatus.UNAVAILABLE, state.status)
    }

    @Test
    fun onlyLatestInitializationCueIsRetained() {
        val state = GuidedVoiceState()
        state.enqueueWhileInitializing("旧句")
        state.enqueueWhileInitializing("新句")

        assertEquals("新句", state.markReady())
    }

    @Test
    fun clearingPendingCuePreventsPlaybackAfterInitialization() {
        val state = GuidedVoiceState()
        state.enqueueWhileInitializing("不会补播")

        state.clearPending()

        assertNull(state.markReady())
    }
}
