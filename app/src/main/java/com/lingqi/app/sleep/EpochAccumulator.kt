package com.lingqi.app.sleep

import com.lingqi.app.data.SleepEpoch
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class EpochAccumulator(
    private var epochStartedAt: Long = System.currentTimeMillis()
) {
    private var movementSquaredSum = 0.0
    private var movementSamples = 0
    private var movementPeaks = 0
    private var noiseDbSum = 0.0
    private var audioBlocks = 0
    private var noiseEvents = 0
    private var snoreProbabilitySum = 0.0

    @Synchronized
    fun addMovement(x: Float, y: Float, z: Float) {
        val magnitude = sqrt((x * x + y * y + z * z).toDouble())
        val movement = abs(magnitude - 9.80665)
        movementSquaredSum += movement * movement
        movementSamples += 1
        if (movement > 0.85) movementPeaks += 1
    }

    @Synchronized
    fun addAudio(samples: ShortArray, count: Int, sampleRate: Int) {
        if (count <= 0) return
        var squareSum = 0.0
        var zeroCrossings = 0
        var previous = samples[0].toInt()
        for (index in 0 until count) {
            val sample = samples[index].toInt()
            val normalized = sample / 32768.0
            squareSum += normalized * normalized
            if ((sample >= 0) != (previous >= 0)) zeroCrossings += 1
            previous = sample
        }
        val rms = sqrt(squareSum / count)
        val db = (20.0 * ln(max(rms, 0.000001)) / ln(10.0) + 90.0).coerceIn(0.0, 90.0)
        val durationSeconds = count.toDouble() / sampleRate
        val dominantEstimate = if (durationSeconds > 0) zeroCrossings / (2.0 * durationSeconds) else 0.0
        val snore = when {
            db < 40.0 -> 0.0
            dominantEstimate in 70.0..350.0 -> ((db - 40.0) / 35.0).coerceIn(0.0, 1.0)
            else -> 0.05
        }
        noiseDbSum += db
        snoreProbabilitySum += snore
        audioBlocks += 1
        if (db > 68.0) noiseEvents += 1
    }

    @Synchronized
    fun flush(now: Long = System.currentTimeMillis()): SleepEpoch {
        val movementRms = if (movementSamples == 0) 0.0 else sqrt(movementSquaredSum / movementSamples)
        val noiseDb = if (audioBlocks == 0) 0.0 else noiseDbSum / audioBlocks
        val snore = if (audioBlocks == 0) 0.0 else snoreProbabilitySum / audioBlocks
        val motionCoverage = min(1.0, movementSamples / 750.0)
        val audioCoverage = min(1.0, audioBlocks / 200.0)
        val coverage = if (audioBlocks == 0) motionCoverage * 0.6 else (motionCoverage + audioCoverage) / 2.0
        val epoch = SleepEpoch(
            startedAt = epochStartedAt,
            movementRms = movementRms,
            movementPeaks = movementPeaks,
            noiseDb = noiseDb,
            noiseEvents = noiseEvents,
            snoreProbability = snore,
            coverage = coverage
        )
        epochStartedAt = now
        movementSquaredSum = 0.0
        movementSamples = 0
        movementPeaks = 0
        noiseDbSum = 0.0
        audioBlocks = 0
        noiseEvents = 0
        snoreProbabilitySum = 0.0
        return epoch
    }
}
