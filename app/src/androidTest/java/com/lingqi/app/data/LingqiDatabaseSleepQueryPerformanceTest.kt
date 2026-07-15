package com.lingqi.app.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LingqiDatabaseSleepQueryPerformanceTest {
    private lateinit var context: Context
    private lateinit var database: LingqiDatabase
    private lateinit var crypto: CryptoManager
    private val databaseName = "sleep-query-performance-test.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(databaseName)
        crypto = CryptoManager()
        database = LingqiDatabase(
            context,
            crypto,
            databaseName,
            ByteArray(32) { index -> (index + 31).toByte() }
        )
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun historySummariesDoNotDecryptEpochPayloads() {
        completeSession("night", 1_000L)
        insertCorruptEpoch("night", 1_000L)

        val summaries = database.sleepSessionHeaders(30)

        assertEquals(listOf("night"), summaries.map { it.id })
        assertTrue(summaries.single().epochs.isEmpty())
    }

    @Test
    fun completedNightCountDoesNotDecryptEpochPayloads() {
        completeSession("night", 1_000L)
        insertCorruptEpoch("night", 1_000L)

        assertEquals(1, database.completedSleepSessionCount())
    }

    @Test
    fun singleReportDoesNotDecryptUnrelatedSessions() {
        completeSession("target", 1_000L)
        database.appendEpoch(
            "target",
            SleepEpoch(
                startedAt = 1_000L,
                movementRms = 0.2,
                movementPeaks = 1,
                noiseDb = 30.0,
                noiseEvents = 0,
                snoreProbability = 0.0,
                coverage = 1.0
            )
        )
        completeSession("unrelated-newer", 2_000L)
        insertCorruptEpoch("unrelated-newer", 2_000L)

        val report = database.sleepSession("target")

        assertEquals("target", report?.id)
        assertEquals(1, report?.epochs?.size)
    }

    @Test
    fun legacyEpochIsRewrittenAfterTheMetadataCursorIsClosed() {
        completeSession("legacy", 1_000L)
        val json = """
            {
              "movementRms": 0.2,
              "movementPeaks": 1,
              "noiseDb": 30.0,
              "noiseEvents": 0,
              "snoreProbability": 0.0,
              "coverage": 1.0,
              "stage": "LIGHT",
              "confidence": 0.5
            }
        """.trimIndent()
        insertEpochPayload("legacy", 1_000L, crypto.encrypt(json))

        assertEquals(1, database.sleepSession("legacy")?.epochs?.size)

        val stored = database.readableDatabase.query(
            "sleep_epochs",
            arrayOf("encrypted_payload"),
            "session_id = ?",
            arrayOf("legacy"),
            null,
            null,
            null
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getString(0)
        }
        assertTrue(stored.startsWith("sqlcipher-json-v1:"))
    }

    private fun completeSession(id: String, startedAt: Long) {
        database.startSleepSession(SleepSession(id = id, startedAt = startedAt, endedAt = null))
        database.finishSleepSession(
            SleepSession(
                id = id,
                startedAt = startedAt,
                endedAt = startedAt + 60_000L,
                score = 80,
                coverage = 0.9
            )
        )
    }

    private fun insertCorruptEpoch(sessionId: String, startedAt: Long) {
        insertEpochPayload(sessionId, startedAt, "not-valid-encrypted-data")
    }

    private fun insertEpochPayload(sessionId: String, startedAt: Long, payload: String) {
        database.writableDatabase.insert(
            "sleep_epochs",
            null,
            ContentValues().apply {
                put("session_id", sessionId)
                put("started_at", startedAt)
                put("encrypted_payload", payload)
            }
        )
    }
}
