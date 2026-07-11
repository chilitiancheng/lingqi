package com.lingqi.app.wellness

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingqi.app.data.CryptoManager
import com.lingqi.app.data.LingqiDatabase
import com.lingqi.app.data.LingqiRepository
import com.lingqi.app.data.MeditationSession
import com.lingqi.app.data.SleepSession
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LingqiDatabaseWellnessSummaryTest {
    private lateinit var context: Context
    private lateinit var database: LingqiDatabase
    private lateinit var repository: LingqiRepository
    private val databaseName = "wellness-provider-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        database = LingqiDatabase(
            context,
            CryptoManager(),
            databaseName,
            ByteArray(32) { index -> (index + 1).toByte() }
        )
        repository = LingqiRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun sleepSummariesExcludeOpenSessions() {
        database.startSleepSession(SleepSession(id = "open", startedAt = 2_000L, endedAt = null))
        database.startSleepSession(SleepSession(id = "closed", startedAt = 1_000L, endedAt = null))
        database.finishSleepSession(
            SleepSession(
                id = "closed",
                startedAt = 1_000L,
                endedAt = 61_000L,
                score = 88,
                coverage = 0.92,
                calibrationNight = 2
            )
        )

        val summaries = database.sleepSummaries(30)
        assertEquals(1, summaries.size)
        assertEquals("closed", summaries.single().sessionId)
        assertEquals(60L, summaries.single().durationSeconds)
    }

    @Test
    fun meditationSummariesAreRecentFirstAndLimited() {
        database.insertMeditation(meditation("older", 1_000L))
        database.insertMeditation(meditation("newer", 2_000L))
        assertEquals(listOf("newer"), database.meditationSummaries(1).map { it.sessionId })
    }

    @Test
    fun sharingIsOffByDefaultAndRevocable() {
        assertFalse(repository.preferences().linglianWellnessSharingEnabled)
        repository.savePreferences(repository.preferences().copy(linglianWellnessSharingEnabled = true))
        assertTrue(repository.preferences().linglianWellnessSharingEnabled)
        repository.savePreferences(repository.preferences().copy(linglianWellnessSharingEnabled = false))
        assertFalse(repository.preferences().linglianWellnessSharingEnabled)
    }

    private fun meditation(id: String, startedAt: Long) = MeditationSession(
        id = id,
        practiceId = "breath-478",
        plannedSeconds = 300,
        actualSeconds = 240,
        startedAt = startedAt,
        endedAt = startedAt + 240_000L,
        completionRate = 0.8f,
        soundEnabled = true
    )
}
