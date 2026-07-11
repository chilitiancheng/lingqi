package com.lingqi.app.data

import android.content.ContentValues
import android.content.Context
import com.lingqi.app.wellness.MeditationWellnessSummary
import com.lingqi.app.wellness.SleepWellnessSummary
import com.lingqi.app.wellness.WellnessProviderContract
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class LingqiDatabase(
    context: Context,
    private val crypto: CryptoManager,
    databaseName: String = "lingqi.db",
    passphrase: ByteArray
) : SQLiteOpenHelper(
    context,
    databaseName,
    requireDatabasePassphrase(passphrase),
    null,
    1,
    0,
    null,
    null,
    false
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE meditation_sessions (
                id TEXT PRIMARY KEY,
                practice_id TEXT NOT NULL,
                planned_seconds INTEGER NOT NULL,
                actual_seconds INTEGER NOT NULL,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                completion_rate REAL NOT NULL,
                sound_enabled INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE sleep_sessions (
                id TEXT PRIMARY KEY,
                started_at INTEGER NOT NULL,
                ended_at INTEGER,
                placement TEXT NOT NULL,
                coverage REAL NOT NULL DEFAULT 0,
                score INTEGER,
                calibration_night INTEGER NOT NULL DEFAULT 1,
                algorithm_version TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE sleep_epochs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                encrypted_payload TEXT NOT NULL,
                FOREIGN KEY(session_id) REFERENCES sleep_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX sleep_epochs_session_idx ON sleep_epochs(session_id, started_at)")
        db.execSQL(
            """
            CREATE TABLE preferences (
                key TEXT PRIMARY KEY,
                encrypted_value TEXT NOT NULL
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insertMeditation(session: MeditationSession) {
        writableDatabase.insertWithOnConflict(
            "meditation_sessions",
            null,
            ContentValues().apply {
                put("id", session.id)
                put("practice_id", session.practiceId)
                put("planned_seconds", session.plannedSeconds)
                put("actual_seconds", session.actualSeconds)
                put("started_at", session.startedAt)
                put("ended_at", session.endedAt)
                put("completion_rate", session.completionRate)
                put("sound_enabled", if (session.soundEnabled) 1 else 0)
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun meditationSessions(): List<MeditationSession> {
        return readableDatabase.query(
            "meditation_sessions",
            null,
            null,
            null,
            null,
            null,
            "started_at DESC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MeditationSession(
                            id = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            practiceId = cursor.getString(cursor.getColumnIndexOrThrow("practice_id")),
                            plannedSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("planned_seconds")),
                            actualSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("actual_seconds")),
                            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                            endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at")),
                            completionRate = cursor.getFloat(cursor.getColumnIndexOrThrow("completion_rate")),
                            soundEnabled = cursor.getInt(cursor.getColumnIndexOrThrow("sound_enabled")) == 1
                        )
                    )
                }
            }
        }
    }

    fun meditationSummaries(limit: Int): List<MeditationWellnessSummary> {
        require(limit in 1..WellnessProviderContract.MAX_LIMIT)
        return readableDatabase.query(
            "meditation_sessions",
            arrayOf("id", "practice_id", "planned_seconds", "actual_seconds", "started_at", "ended_at", "completion_rate"),
            null,
            null,
            null,
            null,
            "started_at DESC",
            limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        MeditationWellnessSummary(
                            sessionId = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            practiceId = cursor.getString(cursor.getColumnIndexOrThrow("practice_id")),
                            plannedSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("planned_seconds")),
                            actualSeconds = cursor.getInt(cursor.getColumnIndexOrThrow("actual_seconds")),
                            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                            endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at")),
                            completionRate = cursor.getFloat(cursor.getColumnIndexOrThrow("completion_rate"))
                        )
                    )
                }
            }
        }
    }

    fun startSleepSession(session: SleepSession) {
        writableDatabase.insertWithOnConflict(
            "sleep_sessions",
            null,
            ContentValues().apply {
                put("id", session.id)
                put("started_at", session.startedAt)
                put("placement", session.placement)
                put("coverage", session.coverage)
                put("calibration_night", session.calibrationNight)
                put("algorithm_version", session.algorithmVersion)
            },
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun appendEpoch(sessionId: String, epoch: SleepEpoch) {
        val payload = JSONObject().apply {
            put("movementRms", epoch.movementRms)
            put("movementPeaks", epoch.movementPeaks)
            put("noiseDb", epoch.noiseDb)
            put("noiseEvents", epoch.noiseEvents)
            put("snoreProbability", epoch.snoreProbability)
            put("coverage", epoch.coverage)
            put("stage", epoch.stage.name)
            put("confidence", epoch.confidence)
        }.toString()

        writableDatabase.insert(
            "sleep_epochs",
            null,
            ContentValues().apply {
                put("session_id", sessionId)
                put("started_at", epoch.startedAt)
                put("encrypted_payload", crypto.encrypt(payload))
            }
        )
    }

    fun replaceEpochs(sessionId: String, epochs: List<SleepEpoch>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("sleep_epochs", "session_id = ?", arrayOf(sessionId))
            epochs.forEach { appendEpoch(sessionId, it) }
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun finishSleepSession(session: SleepSession) {
        writableDatabase.update(
            "sleep_sessions",
            ContentValues().apply {
                put("ended_at", session.endedAt)
                put("coverage", session.coverage)
                put("score", session.score)
                put("calibration_night", session.calibrationNight)
            },
            "id = ?",
            arrayOf(session.id)
        )
    }

    fun sleepSessions(limit: Int = 30): List<SleepSession> {
        return readableDatabase.query(
            "sleep_sessions",
            null,
            "ended_at IS NOT NULL",
            null,
            null,
            null,
            "started_at DESC",
            limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(cursor.getColumnIndexOrThrow("id"))
                    add(
                        SleepSession(
                            id = id,
                            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                            endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at")),
                            placement = cursor.getString(cursor.getColumnIndexOrThrow("placement")),
                            coverage = cursor.getDouble(cursor.getColumnIndexOrThrow("coverage")),
                            score = cursor.getInt(cursor.getColumnIndexOrThrow("score")),
                            calibrationNight = cursor.getInt(cursor.getColumnIndexOrThrow("calibration_night")),
                            algorithmVersion = cursor.getString(cursor.getColumnIndexOrThrow("algorithm_version")),
                            epochs = epochs(id)
                        )
                    )
                }
            }
        }
    }

    fun sleepSession(id: String): SleepSession? = sleepSessions(200).firstOrNull { it.id == id }

    fun sleepSummaries(limit: Int): List<SleepWellnessSummary> {
        require(limit in 1..WellnessProviderContract.MAX_LIMIT)
        return readableDatabase.query(
            "sleep_sessions",
            arrayOf("id", "started_at", "ended_at", "score", "coverage", "calibration_night"),
            "ended_at IS NOT NULL",
            null,
            null,
            null,
            "started_at DESC",
            limit.toString()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at"))
                    val endedAt = cursor.getLong(cursor.getColumnIndexOrThrow("ended_at"))
                    val scoreIndex = cursor.getColumnIndexOrThrow("score")
                    add(
                        SleepWellnessSummary(
                            sessionId = cursor.getString(cursor.getColumnIndexOrThrow("id")),
                            startedAt = startedAt,
                            endedAt = endedAt,
                            durationSeconds = (endedAt - startedAt).coerceAtLeast(0L) / 1_000L,
                            score = if (cursor.isNull(scoreIndex)) null else cursor.getInt(scoreIndex),
                            coverage = cursor.getDouble(cursor.getColumnIndexOrThrow("coverage")),
                            calibrationNight = cursor.getInt(cursor.getColumnIndexOrThrow("calibration_night"))
                        )
                    )
                }
            }
        }
    }

    fun sleepSessionIncludingOpen(id: String): SleepSession? {
        return readableDatabase.query(
            "sleep_sessions",
            null,
            "id = ?",
            arrayOf(id),
            null,
            null,
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val endedIndex = cursor.getColumnIndexOrThrow("ended_at")
            val scoreIndex = cursor.getColumnIndexOrThrow("score")
            SleepSession(
                id = id,
                startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                endedAt = if (cursor.isNull(endedIndex)) null else cursor.getLong(endedIndex),
                placement = cursor.getString(cursor.getColumnIndexOrThrow("placement")),
                coverage = cursor.getDouble(cursor.getColumnIndexOrThrow("coverage")),
                score = if (cursor.isNull(scoreIndex)) null else cursor.getInt(scoreIndex),
                calibrationNight = cursor.getInt(cursor.getColumnIndexOrThrow("calibration_night")),
                algorithmVersion = cursor.getString(cursor.getColumnIndexOrThrow("algorithm_version")),
                epochs = epochs(id)
            )
        }
    }

    fun deleteSleepSession(id: String) {
        writableDatabase.delete("sleep_epochs", "session_id = ?", arrayOf(id))
        writableDatabase.delete("sleep_sessions", "id = ?", arrayOf(id))
    }

    fun epochs(sessionId: String): List<SleepEpoch> {
        return readableDatabase.query(
            "sleep_epochs",
            null,
            "session_id = ?",
            arrayOf(sessionId),
            null,
            null,
            "started_at ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val json = JSONObject(crypto.decrypt(cursor.getString(cursor.getColumnIndexOrThrow("encrypted_payload"))))
                    add(
                        SleepEpoch(
                            startedAt = cursor.getLong(cursor.getColumnIndexOrThrow("started_at")),
                            movementRms = json.getDouble("movementRms"),
                            movementPeaks = json.getInt("movementPeaks"),
                            noiseDb = json.getDouble("noiseDb"),
                            noiseEvents = json.getInt("noiseEvents"),
                            snoreProbability = json.getDouble("snoreProbability"),
                            coverage = json.getDouble("coverage"),
                            stage = SleepStage.valueOf(json.getString("stage")),
                            confidence = json.getDouble("confidence")
                        )
                    )
                }
            }
        }
    }

    fun putPreference(key: String, value: String) {
        writableDatabase.insertWithOnConflict(
            "preferences",
            null,
            ContentValues().apply {
                put("key", key)
                put("encrypted_value", crypto.encrypt(value))
            },
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun getPreference(key: String): String? {
        return readableDatabase.query(
            "preferences",
            arrayOf("encrypted_value"),
            "key = ?",
            arrayOf(key),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) crypto.decrypt(cursor.getString(0)) else null
        }
    }

    fun exportJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("meditationSessions", JSONArray(meditationSessions().map { session ->
            JSONObject().apply {
                put("id", session.id)
                put("practiceId", session.practiceId)
                put("plannedSeconds", session.plannedSeconds)
                put("actualSeconds", session.actualSeconds)
                put("startedAt", session.startedAt)
                put("endedAt", session.endedAt)
                put("completionRate", session.completionRate)
            }
        }))
        root.put("sleepSessions", JSONArray(sleepSessions(365).map { session ->
            JSONObject().apply {
                put("id", session.id)
                put("startedAt", session.startedAt)
                put("endedAt", session.endedAt)
                put("score", session.score)
                put("coverage", session.coverage)
                put("calibrationNight", session.calibrationNight)
            }
        }))
        return root.toString(2)
    }

    fun clearAll() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("sleep_epochs", null, null)
            writableDatabase.delete("sleep_sessions", null, null)
            writableDatabase.delete("meditation_sessions", null, null)
            writableDatabase.delete("preferences", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }
}
