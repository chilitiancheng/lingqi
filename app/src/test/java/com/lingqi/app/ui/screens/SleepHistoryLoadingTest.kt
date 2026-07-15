package com.lingqi.app.ui.screens

import com.lingqi.app.data.SleepSession
import com.lingqi.app.data.UserPreferences
import com.lingqi.app.data.UserStats
import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepHistoryLoadingTest {
    @Test
    fun reportLoadingLabelsAreValidChinese() {
        assertEquals("睡眠报告", SLEEP_REPORT_LOADING_TITLE)
        assertEquals("正在解析本地记录", SLEEP_REPORT_LOADING_SUBTITLE)
    }

    @Test
    fun historyDecryptionRunsOnTheProvidedBackgroundDispatcher() {
        val callerThread = Thread.currentThread().name
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "sleep-history-io") }
        val dispatcher = executor.asCoroutineDispatcher()
        var loadingThread = ""
        val expected = listOf(SleepSession(id = "night", startedAt = 1L, endedAt = 2L))

        try {
            val actual = runBlocking {
                loadSleepHistory(dispatcher) {
                    loadingThread = Thread.currentThread().name
                    expected
                }
            }

            assertEquals(expected, actual)
            assertTrue(loadingThread.startsWith("sleep-history-io"))
            assertNotEquals(callerThread, loadingThread)
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun reportDecryptionRunsOnTheProvidedBackgroundDispatcher() {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "sleep-report-io") }
        val dispatcher = executor.asCoroutineDispatcher()
        var loadingThread = ""
        val expected = SleepSession(id = "night", startedAt = 1L, endedAt = 2L)

        try {
            val actual = runBlocking {
                loadSleepReport(dispatcher) {
                    loadingThread = Thread.currentThread().name
                    expected
                }
            }

            assertEquals(expected, actual)
            assertTrue(loadingThread.startsWith("sleep-report-io"))
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun profileStatisticsRunOnTheProvidedBackgroundDispatcher() {
        val executor = Executors.newSingleThreadExecutor { task -> Thread(task, "profile-data-io") }
        val dispatcher = executor.asCoroutineDispatcher()
        var loadingThread = ""

        try {
            val actual = runBlocking {
                loadProfileData(dispatcher) {
                    loadingThread = Thread.currentThread().name
                    ProfileData(UserPreferences(nickname = "守夜人"), UserStats(sleepNights = 1))
                }
            }

            assertEquals("守夜人", actual.preferences.nickname)
            assertEquals(1, actual.stats.sleepNights)
            assertTrue(loadingThread.startsWith("profile-data-io"))
        } finally {
            dispatcher.close()
        }
    }
}
