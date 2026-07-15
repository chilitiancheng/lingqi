package com.lingqi.app.sleep

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.lingqi.app.LingqiApplication
import java.io.File
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
class InstalledSleepDataSafetyProbeTest {
    @Test
    fun preservesInstalledSleepDataAndCapturesLatestAnrTraceWhenAvailable() {
        val app = ApplicationProvider.getApplicationContext<LingqiApplication>()
        val completed = app.container.repository.sleepHistory()
        val status = SleepTracker.getStatus(app)
        val activityManager = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val latestAnr = activityManager
            .getHistoricalProcessExitReasons(app.packageName, 0, 20)
            .firstOrNull { it.reason == ApplicationExitInfo.REASON_ANR }
        val outputDirectory = File(app.cacheDir, "sleep-safety-probe").apply { mkdirs() }

        File(outputDirectory, "summary.json").writeText(
            JSONObject().apply {
                put("completedSleepSessions", completed.size)
                put("latestCompletedSessionId", completed.firstOrNull()?.id)
                put("latestCompletedStartedAt", completed.firstOrNull()?.startedAt)
                put("latestCompletedEndedAt", completed.firstOrNull()?.endedAt)
                put("trackingActive", status.active)
                put("latestExitReason", latestAnr?.reason)
                put("latestExitDescription", latestAnr?.description)
                put("latestExitTimestamp", latestAnr?.timestamp)
            }.toString(2)
        )
        latestAnr?.traceInputStream?.use { input ->
            File(outputDirectory, "latest-anr-trace.txt").outputStream().use(input::copyTo)
        }

        assertTrue(app.getDatabasePath("lingqi.db").isFile)
    }
}
