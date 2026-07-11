package com.lingqi.app.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EpochAccumulatorTest {
    @Test
    fun turnsMotionAndAudioIntoAggregateOnly() {
        val accumulator = EpochAccumulator(1_000L)
        repeat(750) { index ->
            val bump = if (index % 100 == 0) 2.0f else 0.02f
            accumulator.addMovement(0f, 0f, 9.80665f + bump)
        }
        val audio = ShortArray(3_200) { index -> if (index % 80 < 40) 2_000 else -2_000 }
        repeat(200) { accumulator.addAudio(audio, audio.size, 16_000) }
        val epoch = accumulator.flush(31_000L)
        assertEquals(1_000L, epoch.startedAt)
        assertTrue(epoch.movementRms > 0.0)
        assertTrue(epoch.movementPeaks > 0)
        assertTrue(epoch.noiseDb > 0.0)
        assertEquals(1.0, epoch.coverage, 0.0001)
    }
}
