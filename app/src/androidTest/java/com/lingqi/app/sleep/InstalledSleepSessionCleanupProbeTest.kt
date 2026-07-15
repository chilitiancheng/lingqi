package com.lingqi.app.sleep

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.lingqi.app.LingqiApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledSleepSessionCleanupProbeTest {
    @Test
    fun removesOnlyTheExplicitlyNamedTestSession() {
        val arguments = InstrumentationRegistry.getArguments()
        val sessionId = arguments.getString("session_id")
        val expectedStartedAt = arguments.getString("expected_started_at")?.toLongOrNull()
        assumeTrue(!sessionId.isNullOrBlank() && expectedStartedAt != null)

        val app = ApplicationProvider.getApplicationContext<LingqiApplication>()
        val session = app.container.repository.sleepSessionIncludingOpen(checkNotNull(sessionId))
        assertEquals(expectedStartedAt, session?.startedAt)

        app.container.repository.discardSleep(sessionId)

        assertNull(app.container.repository.sleepSessionIncludingOpen(sessionId))
    }
}
