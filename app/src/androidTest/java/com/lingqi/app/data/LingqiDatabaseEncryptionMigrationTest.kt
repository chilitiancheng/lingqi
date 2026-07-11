package com.lingqi.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LingqiDatabaseEncryptionMigrationTest {
    private lateinit var context: Context
    private val databaseName = "encryption-migration-test.db"
    private val plaintextHeader = "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deleteTestDatabases()
    }

    @After
    fun tearDown() = deleteTestDatabases()

    @Test
    fun plaintextDatabaseIsEncryptedWithoutLosingRows() {
        val crypto = CryptoManager()
        createPlaintextFixture(crypto).close()
        assertTrue(hasPlaintextHeader(context.getDatabasePath(databaseName).readBytes()))

        val passphrase = ByteArray(32) { index -> (index + 1).toByte() }
        val encrypted = LingqiDatabaseMigration.openEncrypted(context, crypto, passphrase, databaseName)
        try {
            assertEquals(listOf("migration-meditation"), encrypted.meditationSessions().map { it.id })
            assertEquals(listOf("migration-sleep"), encrypted.sleepSessions().map { it.id })
            assertEquals(1, encrypted.epochs("migration-sleep").size)
            assertEquals("enabled", encrypted.getPreference("migration-check"))
            assertTableCount(encrypted, "meditation_sessions", 1)
            assertTableCount(encrypted, "sleep_sessions", 1)
            assertTableCount(encrypted, "sleep_epochs", 1)
            assertTableCount(encrypted, "preferences", 1)
        } finally {
            encrypted.close()
        }

        val bytes = context.getDatabasePath(databaseName).readBytes()
        assertFalse(hasPlaintextHeader(bytes))
        assertFalse(bytes.toString(StandardCharsets.ISO_8859_1).contains("migration-meditation"))
        assertNoMigrationArtifacts()
    }

    @Test
    fun rowsWrittenThroughARealWalAreMigrated() {
        val crypto = CryptoManager()
        val writer = createPlaintextFixture(crypto, useWal = true)
        val wal = context.getDatabasePath(databaseName).let { java.io.File(it.absolutePath + "-wal") }
        assertTrue(wal.exists() && wal.length() > 0L)

        val passphrase = ByteArray(32) { index -> (index + 11).toByte() }
        try {
            val encrypted = LingqiDatabaseMigration.openEncrypted(context, crypto, passphrase, databaseName)
            try {
                assertEquals("migration-meditation", encrypted.meditationSessions().single().id)
                assertEquals(1, encrypted.epochs("migration-sleep").size)
            } finally {
                encrypted.close()
            }
        } finally {
            writer.close()
        }

        assertNoMigrationArtifacts()
    }

    @Test
    fun missingSourceIsRestoredFromPlaintextBackupBeforeMigration() {
        val crypto = CryptoManager()
        createPlaintextFixture(crypto).close()
        val source = context.getDatabasePath(databaseName)
        val backup = context.getDatabasePath("$databaseName.plaintext-backup")
        assertTrue(source.renameTo(backup))
        java.io.File(context.getDatabasePath("$databaseName.encrypted-migration").absolutePath + "-wal")
            .writeBytes(byteArrayOf(1, 2, 3))

        val encrypted = LingqiDatabaseMigration.openEncrypted(
            context,
            crypto,
            ByteArray(32) { index -> (index + 21).toByte() },
            databaseName
        )
        try {
            assertEquals("migration-meditation", encrypted.meditationSessions().single().id)
            assertEquals(1, encrypted.epochs("migration-sleep").size)
        } finally {
            encrypted.close()
        }

        assertFalse(hasPlaintextHeader(source.readBytes()))
        assertNoMigrationArtifacts()
    }

    @Test
    fun backupWalIsCheckpointedAtBackupPathBeforeActivation() {
        val crypto = CryptoManager()
        val backupDatabaseName = "$databaseName.plaintext-backup"
        createPlaintextFixture(
            crypto = crypto,
            fixtureDatabaseName = backupDatabaseName
        ).close()
        val backup = context.getDatabasePath(backupDatabaseName)
        val writer = SQLiteDatabase.openDatabase(
            backup.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE
        )
        assertTrue(writer.enableWriteAheadLogging())
        writer.execSQL("PRAGMA wal_autocheckpoint=0")
        writer.insertOrThrow("meditation_sessions", null, ContentValues().apply {
            put("id", "backup-wal-only")
            put("practice_id", "focus")
            put("planned_seconds", 600)
            put("actual_seconds", 600)
            put("started_at", 500_000L)
            put("ended_at", 1_100_000L)
            put("completion_rate", 1.0)
            put("sound_enabled", 1)
        })
        val backupWal = java.io.File(backup.absolutePath + "-wal")
        assertTrue(backupWal.exists() && backupWal.length() > 0L)

        try {
            val encrypted = LingqiDatabaseMigration.openEncrypted(
                context,
                crypto,
                ByteArray(32) { index -> (index + 26).toByte() },
                databaseName
            )
            try {
                assertEquals(
                    setOf("migration-meditation", "backup-wal-only"),
                    encrypted.meditationSessions().map { it.id }.toSet()
                )
                assertEquals(1, encrypted.epochs("migration-sleep").size)
            } finally {
                encrypted.close()
            }
        } finally {
            writer.close()
        }

        assertNoMigrationArtifacts()
    }

    @Test
    fun verifiedEncryptedSourceRemovesResidualPlaintextBackup() {
        val crypto = CryptoManager()
        createPlaintextFixture(crypto).close()
        val source = context.getDatabasePath(databaseName)
        val savedPlaintext = java.io.File(context.cacheDir, "$databaseName.saved-plaintext")
        source.copyTo(savedPlaintext, overwrite = true)
        val passphrase = ByteArray(32) { index -> (index + 31).toByte() }
        try {
            LingqiDatabaseMigration.openEncrypted(context, crypto, passphrase, databaseName).close()
            val backup = context.getDatabasePath("$databaseName.plaintext-backup")
            savedPlaintext.copyTo(backup, overwrite = true)
            assertTrue(hasPlaintextHeader(backup.readBytes()))

            val reopened = LingqiDatabaseMigration.openEncrypted(
                context,
                crypto,
                passphrase,
                databaseName
            )
            try {
                assertEquals("migration-meditation", reopened.meditationSessions().single().id)
                assertEquals(1, reopened.epochs("migration-sleep").size)
            } finally {
                reopened.close()
            }

            assertFalse(backup.exists())
            assertNoMigrationArtifacts()
        } finally {
            savedPlaintext.delete()
        }
    }

    @Test
    fun encryptedSourceWithMismatchedResidualBackupIsBlockedAndBothArePreserved() {
        val crypto = CryptoManager()
        val backupDatabaseName = "$databaseName.plaintext-backup"
        createPlaintextFixture(
            crypto = crypto,
            fixtureDatabaseName = backupDatabaseName
        ).close()
        val backup = context.getDatabasePath(backupDatabaseName)
        val passphrase = ByteArray(32) { index -> (index + 47).toByte() }
        LingqiDatabase(context, crypto, databaseName, passphrase).use { encrypted ->
            encrypted.writableDatabase
        }
        val source = context.getDatabasePath(databaseName)

        val error = runCatching {
            LingqiDatabaseMigration.openEncrypted(context, crypto, passphrase, databaseName).close()
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("plaintext backup"))
        assertTrue(source.exists())
        assertTrue(backup.exists())
        assertFalse(hasPlaintextHeader(source.readBytes()))
        assertTrue(hasPlaintextHeader(backup.readBytes()))
    }

    private fun createPlaintextFixture(
        crypto: CryptoManager,
        useWal: Boolean = false,
        fixtureDatabaseName: String = databaseName
    ): SQLiteDatabase {
        val db = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(fixtureDatabaseName), null)
        try {
            if (useWal) assertTrue(db.enableWriteAheadLogging())
            db.execSQL(
                """CREATE TABLE meditation_sessions (
                    id TEXT PRIMARY KEY, practice_id TEXT NOT NULL, planned_seconds INTEGER NOT NULL,
                    actual_seconds INTEGER NOT NULL, started_at INTEGER NOT NULL, ended_at INTEGER NOT NULL,
                    completion_rate REAL NOT NULL, sound_enabled INTEGER NOT NULL)
                """.trimIndent()
            )
            db.execSQL(
                """CREATE TABLE sleep_sessions (
                    id TEXT PRIMARY KEY, started_at INTEGER NOT NULL, ended_at INTEGER,
                    placement TEXT NOT NULL, coverage REAL NOT NULL DEFAULT 0, score INTEGER,
                    calibration_night INTEGER NOT NULL DEFAULT 1, algorithm_version TEXT NOT NULL)
                """.trimIndent()
            )
            db.execSQL(
                """CREATE TABLE sleep_epochs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT, session_id TEXT NOT NULL,
                    started_at INTEGER NOT NULL, encrypted_payload TEXT NOT NULL)
                """.trimIndent()
            )
            db.execSQL("CREATE TABLE preferences (key TEXT PRIMARY KEY, encrypted_value TEXT NOT NULL)")
            db.insertOrThrow("meditation_sessions", null, ContentValues().apply {
                put("id", "migration-meditation")
                put("practice_id", "breath-478")
                put("planned_seconds", 300)
                put("actual_seconds", 240)
                put("started_at", 1_000L)
                put("ended_at", 241_000L)
                put("completion_rate", 0.8)
                put("sound_enabled", 1)
            })
            db.insertOrThrow("sleep_sessions", null, ContentValues().apply {
                put("id", "migration-sleep")
                put("started_at", 2_000L)
                put("ended_at", 62_000L)
                put("placement", "mattress_edge")
                put("coverage", 0.95)
                put("score", 86)
                put("calibration_night", 1)
                put("algorithm_version", "1.0")
            })
            db.insertOrThrow("sleep_epochs", null, ContentValues().apply {
                put("session_id", "migration-sleep")
                put("started_at", 2_000L)
                put(
                    "encrypted_payload",
                    crypto.encrypt(
                        """{"movementRms":0.1,"movementPeaks":1,"noiseDb":24.0,"noiseEvents":0,"snoreProbability":0.0,"coverage":0.98,"stage":"LIGHT","confidence":0.82}"""
                    )
                )
            })
            db.insertOrThrow("preferences", null, ContentValues().apply {
                put("key", "migration-check")
                put("encrypted_value", crypto.encrypt("enabled"))
            })
            return db
        } catch (error: Throwable) {
            db.close()
            throw error
        }
    }

    private fun assertTableCount(database: LingqiDatabase, table: String, expected: Int) {
        database.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private fun hasPlaintextHeader(bytes: ByteArray): Boolean =
        bytes.size >= plaintextHeader.size && bytes.copyOfRange(0, plaintextHeader.size).contentEquals(plaintextHeader)

    private fun deleteTestDatabases() {
        databaseNames().forEach { name ->
            context.deleteDatabase(name)
            val file = context.getDatabasePath(name)
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                java.io.File(file.absolutePath + suffix).delete()
            }
        }
    }

    private fun assertNoMigrationArtifacts() {
        val source = context.getDatabasePath(databaseName)
        listOf("-wal", "-shm", "-journal").forEach { suffix ->
            assertFalse(java.io.File(source.absolutePath + suffix).exists())
        }
        databaseNames().drop(1).forEach { name ->
            val file = context.getDatabasePath(name)
            assertFalse(file.exists())
            listOf("-wal", "-shm", "-journal").forEach { suffix ->
                assertFalse(java.io.File(file.absolutePath + suffix).exists())
            }
        }
    }

    private fun databaseNames() = listOf(
        databaseName,
        "$databaseName.encrypted-migration",
        "$databaseName.plaintext-backup"
    )
}
