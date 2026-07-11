package com.lingqi.app.ui.screens

import com.lingqi.app.data.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStageSegmentsTest {
    @Test
    fun transitionsAreSplitBetweenTheirActualStages() {
        val segments = buildSleepStageSegments(
            listOf(
                SleepStage.AWAKE,
                SleepStage.LIGHT,
                SleepStage.DEEP,
                SleepStage.REM
            )
        )

        assertEquals(
            listOf(
                SleepStage.AWAKE,
                SleepStage.LIGHT,
                SleepStage.LIGHT,
                SleepStage.DEEP,
                SleepStage.DEEP,
                SleepStage.REM
            ),
            segments.map { it.stage }
        )
        assertEquals(0f, segments.first().startPosition, 0.0001f)
        assertEquals(3f, segments.last().endPosition, 0.0001f)
        segments.zipWithNext().forEach { (first, second) ->
            assertEquals(first.endPosition, second.startPosition, 0.0001f)
            assertEquals(first.endLevel, second.startLevel, 0.0001f)
        }
        assertTrue(segments.map { it.stage }.toSet().containsAll(SleepStage.entries))
    }

    @Test
    fun fewerThanTwoStagesProduceNoLineSegments() {
        assertTrue(buildSleepStageSegments(emptyList()).isEmpty())
        assertTrue(buildSleepStageSegments(listOf(SleepStage.LIGHT)).isEmpty())
    }
}
