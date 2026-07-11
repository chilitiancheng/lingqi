package com.lingqi.app.sleep

import com.lingqi.app.data.SleepEpoch
import com.lingqi.app.data.SleepStage
import kotlin.math.max
import kotlin.math.min

data class SleepAnalysisResult(
    val epochs: List<SleepEpoch>,
    val score: Int,
    val coverage: Double,
    val awakeEvents: Int,
    val averageNoiseDb: Double,
    val confidence: Double
)

class SleepAnalyzer {
    fun analyze(
        rawEpochs: List<SleepEpoch>,
        calibrationNight: Int,
        regularity: Double?
    ): SleepAnalysisResult {
        if (rawEpochs.isEmpty()) {
            return SleepAnalysisResult(emptyList(), 0, 0.0, 0, 0.0, 0.0)
        }

        val classified = rawEpochs.mapIndexed { index, epoch -> classify(epoch, index, calibrationNight) }
        val smoothed = smoothStages(classified)
        val coverage = smoothed.map { it.coverage }.average().coerceIn(0.0, 1.0)
        val awakeEvents = countAwakeEvents(smoothed)
        val averageNoise = smoothed.map { it.noiseDb }.average()
        val confidence = smoothed.map { it.confidence }.average() * coverage
        val score = score(smoothed, regularity, coverage)
        return SleepAnalysisResult(smoothed, score, coverage, awakeEvents, averageNoise, confidence)
    }

    private fun classify(epoch: SleepEpoch, index: Int, calibrationNight: Int): SleepEpoch {
        val minutes = index * 0.5
        val cyclePosition = minutes % 90.0
        val stage = when {
            epoch.coverage < 0.2 -> SleepStage.LIGHT
            epoch.movementRms > 0.5 || epoch.movementPeaks >= 8 || epoch.noiseEvents >= 12 -> SleepStage.AWAKE
            epoch.movementRms < 0.07 && epoch.noiseDb < 54.0 && cyclePosition in 18.0..58.0 -> SleepStage.DEEP
            epoch.movementRms < 0.12 && cyclePosition >= 62.0 && epoch.snoreProbability < 0.65 -> SleepStage.REM
            else -> SleepStage.LIGHT
        }
        val signalClarity = when (stage) {
            SleepStage.AWAKE -> max(epoch.movementRms / 0.8, epoch.movementPeaks / 12.0)
            SleepStage.DEEP -> 1.0 - min(1.0, epoch.movementRms / 0.12)
            SleepStage.REM -> 0.55 + (1.0 - min(1.0, epoch.movementRms / 0.2)) * 0.2
            SleepStage.LIGHT -> 0.58
        }.coerceIn(0.2, 0.95)
        val calibrationCap = if (calibrationNight <= 3) 0.62 else 0.82
        val confidence = min(calibrationCap, signalClarity * epoch.coverage)
        return epoch.copy(stage = stage, confidence = confidence)
    }

    private fun smoothStages(epochs: List<SleepEpoch>): List<SleepEpoch> {
        if (epochs.size < 3) return epochs
        val majoritySmoothed = epochs.mapIndexed { index, epoch ->
            if (index == 0 || index == epochs.lastIndex) return@mapIndexed epoch
            val window = listOf(epochs[index - 1].stage, epoch.stage, epochs[index + 1].stage)
            val majority = window.groupingBy { it }.eachCount().maxBy { it.value }.key
            epoch.copy(stage = majority)
        }.toMutableList()

        for (index in 1 until majoritySmoothed.lastIndex) {
            val previous = majoritySmoothed[index - 1].stage
            val current = majoritySmoothed[index].stage
            val next = majoritySmoothed[index + 1].stage
            if (current != previous && previous == next) {
                majoritySmoothed[index] = majoritySmoothed[index].copy(stage = previous)
            }
        }
        return majoritySmoothed
    }

    private fun countAwakeEvents(epochs: List<SleepEpoch>): Int {
        var events = 0
        var wasAwake = false
        epochs.forEach { epoch ->
            val awake = epoch.stage == SleepStage.AWAKE
            if (awake && !wasAwake) events += 1
            wasAwake = awake
        }
        return events
    }

    private fun score(epochs: List<SleepEpoch>, regularity: Double?, coverage: Double): Int {
        val hours = epochs.size * 0.5 / 60.0
        val durationScore = when {
            hours in 7.0..9.0 -> 1.0
            hours < 7.0 -> (hours / 7.0).coerceIn(0.0, 1.0)
            else -> (1.0 - (hours - 9.0) / 4.0).coerceIn(0.45, 1.0)
        }
        val awakeRatio = epochs.count { it.stage == SleepStage.AWAKE }.toDouble() / epochs.size
        val continuityScore = (1.0 - awakeRatio * 2.4).coerceIn(0.0, 1.0)
        val averageMovement = epochs.map { it.movementRms }.average()
        val activityScore = (1.0 - averageMovement / 0.55).coerceIn(0.0, 1.0)
        val averageNoise = epochs.map { it.noiseDb }.average()
        val noiseScore = (1.0 - max(0.0, averageNoise - 42.0) / 38.0).coerceIn(0.0, 1.0)

        val knownWeighted = durationScore * 35.0 +
            continuityScore * 30.0 +
            activityScore * 10.0 +
            noiseScore * 10.0
        val weighted = if (regularity == null) {
            knownWeighted / 85.0 * 100.0
        } else {
            knownWeighted + regularity.coerceIn(0.0, 1.0) * 15.0
        }
        return (weighted * (0.72 + coverage * 0.28)).toInt().coerceIn(0, 100)
    }
}
