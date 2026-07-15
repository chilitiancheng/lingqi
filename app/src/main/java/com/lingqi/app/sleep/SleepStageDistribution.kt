package com.lingqi.app.sleep

import com.lingqi.app.data.SleepEpoch
import com.lingqi.app.data.SleepStage
import kotlin.math.floor

internal data class SleepStageSlice(
    val stage: SleepStage,
    val durationMillis: Long,
    val percentage: Int,
    val referenceRange: String
)

internal data class SleepStageDistribution(
    val slices: List<SleepStageSlice>,
    val totalSleepMillis: Long
) {
    val hasData: Boolean get() = totalSleepMillis > 0L

    fun slice(stage: SleepStage): SleepStageSlice = slices.first { it.stage == stage }
}

internal fun calculateSleepStageDistribution(
    epochs: List<SleepEpoch>,
    endedAt: Long?
): SleepStageDistribution {
    val ordered = epochs.sortedBy { it.startedAt }
    val durations = linkedMapOf(
        SleepStage.LIGHT to 0L,
        SleepStage.DEEP to 0L,
        SleepStage.REM to 0L
    )
    ordered.forEachIndexed { index, epoch ->
        if (epoch.stage == SleepStage.AWAKE || !epoch.coverage.isFinite() || epoch.coverage < MIN_STAGE_COVERAGE) {
            return@forEachIndexed
        }
        val nextStart = ordered.getOrNull(index + 1)?.startedAt
            ?: endedAt
            ?: (epoch.startedAt + EPOCH_MILLIS)
        val duration = (nextStart - epoch.startedAt).coerceIn(0L, EPOCH_MILLIS)
        durations.computeIfPresent(epoch.stage) { _, current -> current + duration }
    }

    val total = durations.values.sum()
    val percentages = allocatePercentages(durations.values.toList(), total)
    val ranges = mapOf(
        SleepStage.LIGHT to "45%-60%",
        SleepStage.DEEP to "20%-35%",
        SleepStage.REM to "15%-25%"
    )
    return SleepStageDistribution(
        slices = durations.entries.mapIndexed { index, (stage, duration) ->
            SleepStageSlice(
                stage = stage,
                durationMillis = duration,
                percentage = percentages[index],
                referenceRange = ranges.getValue(stage)
            )
        },
        totalSleepMillis = total
    )
}

private fun allocatePercentages(durations: List<Long>, total: Long): List<Int> {
    if (total <= 0L) return List(durations.size) { 0 }
    val exact = durations.map { it * 100.0 / total }
    val result = exact.map { floor(it).toInt() }.toMutableList()
    repeat(100 - result.sum()) {
        val index = exact.indices.maxByOrNull { exact[it] - result[it] } ?: return@repeat
        result[index] += 1
    }
    return result
}

internal fun formatSleepDuration(durationMillis: Long): String {
    val totalMinutes = durationMillis.coerceAtLeast(0L) / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}时${minutes}分" else "${minutes}分"
}

private const val MIN_STAGE_COVERAGE = 0.2
private const val EPOCH_MILLIS = 30_000L
