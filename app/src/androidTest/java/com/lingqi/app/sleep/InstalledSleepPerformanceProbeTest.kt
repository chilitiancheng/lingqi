package com.lingqi.app.sleep

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingqi.app.LingqiApplication
import java.io.File
import kotlin.system.measureTimeMillis
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InstalledSleepPerformanceProbeTest {
    @Test
    fun measuresLatestInstalledReportLoad() {
        val app = ApplicationProvider.getApplicationContext<LingqiApplication>()
        val sessionIds = app.container.database.sleepSummaries(30).map { it.sessionId }
        val measurements = JSONArray()
        sessionIds.forEach { sessionId ->
            var firstReport: Any? = null
            var secondReport: Any? = null
            val firstLoadMs = measureTimeMillis {
                firstReport = app.container.repository.sleepSession(sessionId)
            }
            val secondLoadMs = measureTimeMillis {
                secondReport = app.container.repository.sleepSession(sessionId)
            }
            assertEquals(firstReport, secondReport)
            measurements.put(
                JSONObject()
                    .put("sessionId", sessionId)
                    .put("firstLoadMs", firstLoadMs)
                    .put("secondLoadMs", secondLoadMs)
            )
        }

        File(app.cacheDir, "sleep-performance-probe.json").writeText(
            JSONObject()
                .put("reports", measurements)
                .toString(2)
        )
        assertEquals(sessionIds.size, measurements.length())
    }
}
