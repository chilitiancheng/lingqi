package com.lingqi.app.sleep

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingqi.app.LingqiApplication
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledSleepDiscardProbeTest {
    @Test
    fun discardsTheCurrentNotificationProbeWithoutCreatingAReport() {
        val app = ApplicationProvider.getApplicationContext<LingqiApplication>()
        val activeSessionId = SleepTracker.getStatus(app).sessionId
        if (activeSessionId == null) return

        SleepTracker.discardSession(app)
        val deadline = System.currentTimeMillis() + 10_000L
        while (SleepTracker.getStatus(app).active && System.currentTimeMillis() < deadline) {
            Thread.sleep(50L)
        }

        assertFalse(SleepTracker.getStatus(app).active)
        assertNull(app.container.repository.sleepSessionIncludingOpen(activeSessionId))
    }
}
