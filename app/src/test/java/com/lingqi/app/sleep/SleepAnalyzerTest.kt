package com.lingqi.app.sleep

import com.lingqi.app.data.SleepEpoch
import com.lingqi.app.data.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAnalyzerTest {
    private val analyzer = SleepAnalyzer()

    @Test
    fun producesBoundedWeightedScoreForEightHours() {
        val epochs = List(960) { index ->
            SleepEpoch(
                startedAt = index * 30_000L,
                movementRms = if (index % 180 == 0) 0.8 else 0.05,
                movementPeaks = if (index % 180 == 0) 10 else 0,
                noiseDb = 43.0,
                noiseEvents = 0,
                snoreProbability = 0.2,
                coverage = 0.95
            )
        }
        val result = analyzer.analyze(epochs, calibrationNight = 4, regularity = null)
        assertTrue(result.score in 70..100)
        assertEquals(960, result.epochs.size)
        assertTrue(result.coverage > 0.9)
    }

    @Test
    fun missingRegularityReweightsKnownSignalsWithoutInventingPoints() {
        val idealEpochs = List(960) { index ->
            SleepEpoch(
                startedAt = index * 30_000L,
                movementRms = 0.0,
                movementPeaks = 0,
                noiseDb = 42.0,
                noiseEvents = 0,
                snoreProbability = 0.0,
                coverage = 1.0
            )
        }

        val withoutRegularity = analyzer.analyze(idealEpochs, 4, regularity = null)
        val knownIrregular = analyzer.analyze(idealEpochs, 4, regularity = 0.0)

        assertEquals(100, withoutRegularity.score)
        assertEquals(85, knownIrregular.score)
    }

    @Test
    fun capsConfidenceDuringFirstThreeNights() {
        val epochs = List(20) { index ->
            SleepEpoch(index * 30_000L, 0.03, 0, 40.0, 0, 0.0, 1.0)
        }
        val calibration = analyzer.analyze(epochs, calibrationNight = 1, regularity = null)
        assertTrue(calibration.epochs.all { it.confidence <= 0.62 })
    }

    @Test
    fun removesSingleEpochStageSpike() {
        val epochs = listOf(
            SleepEpoch(0, 0.04, 0, 40.0, 0, 0.0, 1.0),
            SleepEpoch(30_000, 0.9, 12, 75.0, 20, 0.0, 1.0),
            SleepEpoch(60_000, 0.04, 0, 40.0, 0, 0.0, 1.0)
        )
        val result = analyzer.analyze(epochs, calibrationNight = 4, regularity = null)
        assertTrue(result.epochs[1].stage != SleepStage.AWAKE)
    }
}
