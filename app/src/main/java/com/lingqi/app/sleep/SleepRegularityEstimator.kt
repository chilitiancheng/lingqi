package com.lingqi.app.sleep

import com.lingqi.app.data.SleepSession
import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.min

internal fun regularityHistoryIncludingCurrent(
    history: List<SleepSession>,
    current: SleepSession
): List<SleepSession> {
    require(current.endedAt != null) { "Current sleep session must be completed for regularity" }
    return history.filter { it.endedAt != null && it.id != current.id } + current
}

class SleepRegularityEstimator(
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun estimate(sessions: List<SleepSession>): Double? {
        val recent = sessions
            .filter { it.endedAt != null }
            .sortedByDescending { it.startedAt }
            .take(MAX_NIGHTS)
        if (recent.size < MIN_NIGHTS) return null

        val bedtimeDifference = meanPairwiseDifference(recent.map { minuteOfDay(it.startedAt) })
        val wakeDifference = meanPairwiseDifference(recent.map { minuteOfDay(requireNotNull(it.endedAt)) })
        val averageDifference = (bedtimeDifference + wakeDifference) / 2.0
        return (1.0 - averageDifference / LOW_REGULARITY_MINUTES).coerceIn(0.0, 1.0)
    }

    private fun minuteOfDay(timestamp: Long): Double {
        val time = Instant.ofEpochMilli(timestamp).atZone(zoneId).toLocalTime()
        return time.hour * 60.0 + time.minute + time.second / 60.0
    }

    private fun meanPairwiseDifference(values: List<Double>): Double {
        var total = 0.0
        var pairs = 0
        for (first in values.indices) {
            for (second in first + 1 until values.size) {
                val direct = abs(values[first] - values[second])
                total += min(direct, MINUTES_PER_DAY - direct)
                pairs += 1
            }
        }
        return total / pairs
    }

    private companion object {
        const val MIN_NIGHTS = 3
        const val MAX_NIGHTS = 7
        const val MINUTES_PER_DAY = 24.0 * 60.0
        const val LOW_REGULARITY_MINUTES = 4.0 * 60.0
    }
}
