package com.lingqi.app.meditation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueSynthesisTest {
    private val sampleRate = 44_100

    @Test
    fun cueSpecsAreExact() {
        assertEquals(CueToneSpec(1400.0, 0.80), DI_CUE)
        assertEquals(CueToneSpec(880.0, 0.75), TA_CUE)
        assertEquals(0.18, DI_CUE.durationSeconds, 0.0)
        assertEquals(0.005, DI_CUE.attackSeconds, 0.0)
        assertEquals(0.18, DI_CUE.harmonicRatio, 0.0)
    }

    @Test
    fun envelopeUsesFiveMillisecondLinearAttackThenCubicDecay() {
        assertEquals(0.0, cueEnvelope(0.0, DI_CUE), 1e-12)
        assertEquals(0.5, cueEnvelope(0.0025, DI_CUE), 1e-12)
        assertEquals(1.0, cueEnvelope(0.005, DI_CUE), 1e-12)

        val elapsed = 0.0925
        val u = (elapsed - 0.005) / (0.18 - 0.005)
        assertEquals((1.0 - u) * (1.0 - u) * (1.0 - u), cueEnvelope(elapsed, DI_CUE), 1e-12)
        assertEquals(0.0, cueEnvelope(0.18, DI_CUE), 0.0)
    }

    @Test
    fun waveAddsEighteenPercentSecondHarmonicAndNormalizes() {
        val phase = PI / 4.0
        val expected = (sin(phase) + 0.18 * sin(2.0 * phase)) / 1.18
        assertEquals(expected, cueWave(phase, 0.18), 1e-12)
    }

    @Test
    fun waveIsStableAcrossWholePeriods() {
        val phase = 0.731
        assertEquals(cueWave(phase, 0.18), cueWave(phase + 2.0 * PI, 0.18), 1e-12)
    }

    @Test
    fun generatedCuesKeepTheirApprovedFrequencyWithoutSweeping() {
        listOf(DI_CUE, TA_CUE).forEach { spec ->
            val samples = generateCueSamples(spec, sampleRate)
            val earlyFrequency = estimateFrequencyFromPositiveZeroCrossings(samples, 0.010, 0.060)
            val lateFrequency = estimateFrequencyFromPositiveZeroCrossings(samples, 0.100, 0.150)

            assertTrue(abs(earlyFrequency - spec.frequencyHz) / spec.frequencyHz <= 0.03)
            assertTrue(abs(lateFrequency - spec.frequencyHz) / spec.frequencyHz <= 0.03)
            assertTrue(abs(earlyFrequency - lateFrequency) / spec.frequencyHz <= 0.01)
        }
    }

    @Test
    fun generatedCuesHaveApprovedDurationSafePeakAndSilentEdges() {
        listOf(DI_CUE, TA_CUE).forEach { spec ->
            val samples = generateCueSamples(spec)
            val peak = samples.maxOf { abs(it.toInt()) }

            assertEquals(7_938, samples.size)
            assertEquals(0, samples.first().toInt())
            assertEquals(0, samples.last().toInt())
            assertTrue(peak > 8_000)
            assertTrue(peak <= spec.volume * Short.MAX_VALUE)
        }
    }

    private fun estimateFrequencyFromPositiveZeroCrossings(
        samples: ShortArray,
        startSeconds: Double,
        endSeconds: Double
    ): Double {
        val startIndex = (startSeconds * sampleRate).toInt().coerceAtLeast(1)
        val endIndex = (endSeconds * sampleRate).toInt().coerceAtMost(samples.lastIndex)
        val crossings = (startIndex..endIndex).mapNotNull { index ->
            val previous = samples[index - 1].toDouble()
            val current = samples[index].toDouble()
            if (previous <= 0.0 && current > 0.0) {
                index - 1 + (-previous / (current - previous))
            } else {
                null
            }
        }
        assertTrue("Expected at least two positive zero crossings", crossings.size >= 2)
        return (crossings.size - 1) * sampleRate / (crossings.last() - crossings.first())
    }
}
