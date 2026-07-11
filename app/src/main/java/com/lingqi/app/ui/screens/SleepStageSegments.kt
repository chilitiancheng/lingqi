package com.lingqi.app.ui.screens

import com.lingqi.app.data.SleepStage

internal data class SleepStageLineSegment(
    val startPosition: Float,
    val startLevel: Float,
    val endPosition: Float,
    val endLevel: Float,
    val stage: SleepStage
)

internal fun buildSleepStageSegments(stages: List<SleepStage>): List<SleepStageLineSegment> {
    if (stages.size < 2) return emptyList()
    return buildList {
        stages.zipWithNext().forEachIndexed { index, (start, end) ->
            val startPosition = index.toFloat()
            val endPosition = index + 1f
            val middlePosition = (startPosition + endPosition) / 2f
            val startLevel = start.chartLevel()
            val endLevel = end.chartLevel()
            val middleLevel = (startLevel + endLevel) / 2f
            add(
                SleepStageLineSegment(
                    startPosition,
                    startLevel,
                    middlePosition,
                    middleLevel,
                    start
                )
            )
            add(
                SleepStageLineSegment(
                    middlePosition,
                    middleLevel,
                    endPosition,
                    endLevel,
                    end
                )
            )
        }
    }
}

private fun SleepStage.chartLevel(): Float = when (this) {
    SleepStage.AWAKE -> 0f
    SleepStage.REM -> 1f
    SleepStage.LIGHT -> 2f
    SleepStage.DEEP -> 3f
}
