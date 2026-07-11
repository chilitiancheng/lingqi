package com.lingqi.app.meditation

import kotlin.math.PI
import kotlin.math.sin

internal data class CueToneSpec(
    val frequencyHz: Double,
    val volume: Double,
    val durationSeconds: Double = 0.18,
    val attackSeconds: Double = 0.005,
    val harmonicRatio: Double = 0.18
)

internal val DI_CUE = CueToneSpec(frequencyHz = 1400.0, volume = 0.55)
internal val TA_CUE = CueToneSpec(frequencyHz = 880.0, volume = 0.50)

internal fun cueEnvelope(elapsedSeconds: Double, spec: CueToneSpec): Double {
    if (elapsedSeconds >= spec.durationSeconds) return 0.0
    if (elapsedSeconds <= spec.attackSeconds) {
        return (elapsedSeconds / spec.attackSeconds).coerceIn(0.0, 1.0)
    }
    val u = (elapsedSeconds - spec.attackSeconds) / (spec.durationSeconds - spec.attackSeconds)
    val remaining = (1.0 - u).coerceAtLeast(0.0)
    return remaining * remaining * remaining
}

internal fun cueWave(phase: Double, harmonicRatio: Double): Double =
    (sin(phase) + harmonicRatio * sin(2.0 * phase)) / (1.0 + harmonicRatio)

internal fun generateCueSamples(
    spec: CueToneSpec,
    sampleRate: Int = 44_100
): ShortArray {
    val count = (sampleRate * spec.durationSeconds).toInt()
    val samples = ShortArray(count)
    for (index in 0 until count) {
        val elapsedSeconds = index.toDouble() / sampleRate
        val phase = 2.0 * PI * spec.frequencyHz * elapsedSeconds
        val scaled = cueWave(phase, spec.harmonicRatio) *
            cueEnvelope(elapsedSeconds, spec) * spec.volume * Short.MAX_VALUE
        samples[index] = scaled.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
    if (samples.isNotEmpty()) samples[samples.lastIndex] = 0
    return samples
}
