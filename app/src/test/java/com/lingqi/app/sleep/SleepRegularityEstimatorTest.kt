package com.lingqi.app.sleep

import com.lingqi.app.data.SleepSession
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepRegularityEstimatorTest {
    private val estimator = SleepRegularityEstimator(ZoneOffset.UTC)

    @Test
    fun nearbyTimesAcrossMidnightHaveHighRegularity() {
        val sessions = listOf(
            session("one", "2026-07-01T23:55:00Z", "2026-07-02T07:55:00Z"),
            session("two", "2026-07-03T00:05:00Z", "2026-07-03T08:05:00Z"),
            session("three", "2026-07-03T23:58:00Z", "2026-07-04T08:00:00Z")
        )

        assertTrue(requireNotNull(estimator.estimate(sessions)) > 0.9)
    }

    @Test
    fun widelyDispersedTimesHaveLowRegularity() {
        val sessions = listOf(
            session("one", "2026-07-01T21:00:00Z", "2026-07-02T05:00:00Z"),
            session("two", "2026-07-03T00:00:00Z", "2026-07-03T08:00:00Z"),
            session("three", "2026-07-04T03:00:00Z", "2026-07-04T11:00:00Z")
        )

        assertTrue(requireNotNull(estimator.estimate(sessions)) < 0.25)
    }

    @Test
    fun fewerThanThreeEndedNightsHaveNoRegularityScore() {
        val sessions = listOf(
            session("closed", "2026-07-01T23:00:00Z", "2026-07-02T07:00:00Z"),
            SleepSession(
                id = "open",
                startedAt = Instant.parse("2026-07-02T23:00:00Z").toEpochMilli(),
                endedAt = null
            )
        )

        assertNull(estimator.estimate(sessions))
    }

    @Test
    fun onlySevenMostRecentEndedNightsAreUsed() {
        val oldestOutlier = session(
            "oldest",
            "2026-07-01T12:00:00Z",
            "2026-07-01T13:00:00Z"
        )
        val recentStable = (2..8).map { day ->
            session(
                "recent-$day",
                "2026-07-%02dT23:55:00Z".format(day),
                "2026-07-%02dT07:55:00Z".format(day + 1)
            )
        }

        assertTrue(requireNotNull(estimator.estimate(recentStable + oldestOutlier)) > 0.99)
    }

    @Test
    fun currentCompletedNightIsIncludedAtTheThreeNightBoundary() {
        val history = listOf(
            session("one", "2026-07-01T23:55:00Z", "2026-07-02T07:55:00Z"),
            session("two", "2026-07-02T23:58:00Z", "2026-07-03T08:00:00Z")
        )
        val current = session(
            "current",
            "2026-07-03T23:57:00Z",
            "2026-07-04T07:58:00Z"
        )

        val input = regularityHistoryIncludingCurrent(history, current)

        assertNotNull(estimator.estimate(input))
    }

    private fun session(id: String, startedAt: String, endedAt: String) = SleepSession(
        id = id,
        startedAt = Instant.parse(startedAt).toEpochMilli(),
        endedAt = Instant.parse(endedAt).toEpochMilli()
    )
}
