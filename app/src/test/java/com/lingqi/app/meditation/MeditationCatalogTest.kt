package com.lingqi.app.meditation

import org.junit.Assert.assertEquals
import org.junit.Test

class MeditationCatalogTest {
    @Test
    fun catalogContainsExactlyTheSixSupportedPractices() {
        assertEquals(
            setOf(
                "breath-478",
                "body-scan",
                "sleep-release",
                "focus",
                "emotional-ease",
                "morning-awakening"
            ),
            MeditationCatalog.practices.map { it.id }.toSet()
        )
        assertEquals(6, MeditationCatalog.practices.size)
    }

    @Test
    fun everyPracticeUsesTheSameSupportedDurations() {
        assertEquals(listOf(5, 10, 20), MeditationCatalog.durationMinutes)
        MeditationCatalog.practices.forEach {
            assertEquals(listOf(5, 10, 20), MeditationCatalog.durationMinutes)
        }
    }
}
