package com.lingqi.app.meditation

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CuePlayerTest {
    @Test
    fun generatedCueHasExpectedDurationAndAudibleSignal() {
        val samples = generateCueSamples(
            startFrequency = 1046.0,
            endFrequency = 784.0,
            triangle = false,
            volume = 0.22,
            durationSeconds = 0.24
        )

        assertEquals(10_584, samples.size)
        assertTrue(samples.maxOf { abs(it.toInt()) } > 5_000)
        assertEquals(0, samples.first().toInt())
    }
}
