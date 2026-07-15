package com.lingqi.app.sleep

import com.lingqi.app.data.SleepEpoch
import com.lingqi.app.data.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStageDistributionTest {
    @Test
    fun `awake and low coverage epochs are excluded`() {
        val epochs = listOf(
            epoch(0L, SleepStage.LIGHT),
            epoch(30_000L, SleepStage.DEEP),
            epoch(60_000L, SleepStage.REM),
            epoch(90_000L, SleepStage.AWAKE),
            epoch(120_000L, SleepStage.LIGHT, coverage = 0.19),
            epoch(150_000L, SleepStage.DEEP, coverage = Double.NaN)
        )

        val result = calculateSleepStageDistribution(epochs, endedAt = 180_000L)

        assertEquals(90_000L, result.totalSleepMillis)
        assertEquals(listOf(34, 33, 33), result.slices.map { it.percentage })
        assertEquals(100, result.slices.sumOf { it.percentage })
    }

    @Test
    fun `final partial epoch uses report end and each epoch is capped at thirty seconds`() {
        val epochs = listOf(
            epoch(0L, SleepStage.LIGHT),
            epoch(90_000L, SleepStage.REM)
        )

        val result = calculateSleepStageDistribution(epochs, endedAt = 100_000L)

        assertEquals(40_000L, result.totalSleepMillis)
        assertEquals(30_000L, result.slice(SleepStage.LIGHT).durationMillis)
        assertEquals(10_000L, result.slice(SleepStage.REM).durationMillis)
        assertEquals(75, result.slice(SleepStage.LIGHT).percentage)
        assertEquals(25, result.slice(SleepStage.REM).percentage)
    }

    @Test
    fun `empty and awake-only data has no chart`() {
        assertFalse(calculateSleepStageDistribution(emptyList(), null).hasData)
        assertFalse(
            calculateSleepStageDistribution(
                listOf(epoch(0L, SleepStage.AWAKE)),
                endedAt = 30_000L
            ).hasData
        )
    }

    @Test
    fun `single sleep stage is one hundred percent`() {
        val result = calculateSleepStageDistribution(
            listOf(epoch(0L, SleepStage.DEEP)),
            endedAt = 30_000L
        )

        assertTrue(result.hasData)
        assertEquals(listOf(0, 100, 0), result.slices.map { it.percentage })
    }

    @Test
    fun `duration formatter uses compact Chinese units`() {
        assertEquals("0分", formatSleepDuration(0L))
        assertEquals("45分", formatSleepDuration(45 * 60_000L))
        assertEquals("6时59分", formatSleepDuration((6 * 60 + 59) * 60_000L))
    }

    private fun epoch(
        startedAt: Long,
        stage: SleepStage,
        coverage: Double = 1.0
    ) = SleepEpoch(
        startedAt = startedAt,
        movementRms = 0.0,
        movementPeaks = 0,
        noiseDb = 0.0,
        noiseEvents = 0,
        snoreProbability = 0.0,
        coverage = coverage,
        stage = stage,
        confidence = 1.0
    )
}
