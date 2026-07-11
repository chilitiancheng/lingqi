package com.lingqi.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase as PlainSQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabase as EncryptedSQLiteDatabase
import java.io.File
import java.nio.charset.StandardCharsets

object LingqiDatabaseMigration {
    private const val DATABASE_NAME = "lingqi.db"
    private val plaintextHeader = "SQLite format 3\u0000".toByteArray(StandardCharsets.US_ASCII)
    private val expectedTables = MIGRATION_TABLE_NAMES

    fun openEncrypted(
        context: Context,
        crypto: CryptoManager,
        passphrase: ByteArray,
        databaseName: String = DATABASE_NAME
    ): LingqiDatabase {
        val requiredPassphrase = requireDatabasePassphrase(passphrase)
        migratePlaintextIfNeeded(context, crypto, requiredPassphrase, databaseName)
        return LingqiDatabase(context, crypto, databaseName, requiredPassphrase)
    }

    private fun migratePlaintextIfNeeded(
        context: Context,
        crypto: CryptoManager,
        passphrase: ByteArray,
        databaseName: String
    ) {
        val tempDatabaseName = "$databaseName.encrypted-migration"
        val backupDatabaseName = "$databaseName.plaintext-backup"
        val sourceFile = context.getDatabasePath(databaseName)
        val plan = coordinateMigrationArtifacts(
            context = context,
            crypto = crypto,
            passphrase = passphrase,
            databaseName = databaseName,
            tempDatabaseName = tempDatabaseName,
            backupDatabaseName = backupDatabaseName
        )
        val action = plan.action
        if (action == MigrationStartupAction.CREATE_NEW ||
            action == MigrationStartupAction.OPEN_ENCRYPTED
        ) {
            val backupFile = context.getDatabasePath(backupDatabaseName)
            if (action == MigrationStartupAction.OPEN_ENCRYPTED &&
                classifyBackup(backupFile) == MigrationFileState.PLAINTEXT
            ) {
                checkResidualPlaintextBackupMatchesEncryptedSource(
                    context = context,
                    crypto = crypto,
                    passphrase = passphrase,
                    databaseName = databaseName,
                    backupFile = backupFile
                )
            }
            applyNonMigrationCleanup(
                plan = plan,
                sourceFile = sourceFile,
                tempFile = context.getDatabasePath(tempDatabaseName),
                backupFile = backupFile
            )
            return
        }
        if (action != MigrationStartupAction.MIGRATE_PLAINTEXT &&
            action != MigrationStartupAction.RESTORE_BACKUP_AND_MIGRATE
        ) {
            return
        }

        val plaintextInputFile = if (action == MigrationStartupAction.RESTORE_BACKUP_AND_MIGRATE) {
            context.getDatabasePath(backupDatabaseName)
        } else {
            sourceFile
        }
        val expectedCounts = linkedMapOf<String, Int>()
        runPlaintextMigrationBeforeFileSwitch(
            plaintextInputFile = plaintextInputFile,
            prepareEncryptedDatabase = { inputFile ->
                val source = PlainSQLiteDatabase.openDatabase(
                    inputFile.absolutePath,
                    null,
                    PlainSQLiteDatabase.OPEN_READWRITE
                )
                try {
                    checkpointPlaintextWal(source)
                    if (plan.cleanTempArtifacts) {
                        checkedDeleteDatabaseArtifacts(
                            context.getDatabasePath(tempDatabaseName),
                            "remove stale migration temp artifacts"
                        )
                    }
                    if (action == MigrationStartupAction.MIGRATE_PLAINTEXT &&
                        plan.cleanBackupArtifacts
                    ) {
                        checkedDeleteDatabaseArtifacts(
                            context.getDatabasePath(backupDatabaseName),
                            "remove stale plaintext backup artifacts"
                        )
                    }
                    val targetHelper = LingqiDatabase(context, crypto, tempDatabaseName, passphrase)
                    try {
                        val target = targetHelper.writableDatabase
                        target.beginTransaction()
                        try {
                            expectedCounts["meditation_sessions"] = copyTable(
                                source,
                                target,
                                "meditation_sessions",
                                arrayOf("id", "practice_id", "planned_seconds", "actual_seconds", "started_at", "ended_at", "completion_rate", "sound_enabled")
                            )
                            expectedCounts["sleep_sessions"] = copyTable(
                                source,
                                target,
                                "sleep_sessions",
                                arrayOf("id", "started_at", "ended_at", "placement", "coverage", "score", "calibration_night", "algorithm_version")
                            )
                            expectedCounts["sleep_epochs"] = copyTable(
                                source,
                                target,
                                "sleep_epochs",
                                arrayOf("id", "session_id", "started_at", "encrypted_payload")
                            )
                            expectedCounts["preferences"] = copyTable(
                                source,
                                target,
                                "preferences",
                                arrayOf("key", "encrypted_value")
                            )
                            target.setTransactionSuccessful()
                        } finally {
                            target.endTransaction()
                        }
                        checkpointEncryptedWal(target)
                    } finally {
                        targetHelper.close()
                    }
                } finally {
                    source.close()
                }
            },
            switchFiles = {
                replacePlaintextDatabase(
                    context = context,
                    crypto = crypto,
                    passphrase = passphrase,
                    databaseName = databaseName,
                    tempDatabaseName = tempDatabaseName,
                    backupDatabaseName = backupDatabaseName,
                    sourceFile = sourceFile,
                    expectedCounts = expectedCounts,
                    action = action
                )
            }
        )
    }

    private fun applyNonMigrationCleanup(
        plan: MigrationStartupPlan,
        sourceFile: File,
        tempFile: File,
        backupFile: File
    ) {
        if (plan.cleanSourceArtifacts) {
            checkedDeleteDatabaseArtifacts(sourceFile, "remove the unusable source database")
        }
        if (plan.cleanTempArtifacts) {
            checkedDeleteDatabaseArtifacts(tempFile, "remove stale migration temp artifacts")
        }
        if (plan.cleanBackupArtifacts) {
            checkedDeleteDatabaseArtifacts(backupFile, "remove stale plaintext backup artifacts")
        }
    }

    private fun coordinateMigrationArtifacts(
        context: Context,
        crypto: CryptoManager,
        passphrase: ByteArray,
        databaseName: String,
        tempDatabaseName: String,
        backupDatabaseName: String
    ): MigrationStartupPlan {
        val sourceFile = context.getDatabasePath(databaseName)
        val tempFile = context.getDatabasePath(tempDatabaseName)
        val backupFile = context.getDatabasePath(backupDatabaseName)
        return planMigrationStartup(
            MigrationArtifactStates(
                source = classifyActiveDatabase(context, crypto, passphrase, databaseName, sourceFile),
                temp = classifyActiveDatabase(context, crypto, passphrase, tempDatabaseName, tempFile),
                backup = classifyBackup(backupFile),
                sourceHasNonEmptySidecars = databaseSidecars(sourceFile).any {
                    it.exists() && it.length() > 0L
                },
                tempHasNonEmptySidecars = databaseSidecars(tempFile).any {
                    it.exists() && it.length() > 0L
                },
                backupHasNonEmptySidecars = databaseSidecars(backupFile).any {
                    it.exists() && it.length() > 0L
                }
            )
        )

    }

    private fun classifyActiveDatabase(
        context: Context,
        crypto: CryptoManager,
        passphrase: ByteArray,
        databaseName: String,
        file: File
    ): MigrationFileState {
        if (!file.exists()) return MigrationFileState.MISSING
        if (file.length() == 0L) return MigrationFileState.EMPTY
        if (hasPlaintextHeader(file)) return MigrationFileState.PLAINTEXT
        return if (isUsableEncryptedDatabase(context, crypto, passphrase, databaseName)) {
            MigrationFileState.ENCRYPTED
        } else {
            MigrationFileState.UNKNOWN
        }
    }

    private fun classifyBackup(file: File): MigrationFileState = when {
        !file.exists() -> MigrationFileState.MISSING
        file.length() == 0L -> MigrationFileState.EMPTY
        hasPlaintextHeader(file) -> MigrationFileState.PLAINTEXT
        else -> MigrationFileState.UNKNOWN
    }

    private fun isUsableEncryptedDatabase(
        context: Context,
        crypto: CryptoManager,
        passphrase: ByteArray,
        databaseName: String
    ): Boolean {
        val helper = LingqiDatabase(context, crypto, databaseName, passphrase)
        return try {
            val database = helper.readableDatabase
            val quickCheckRows = database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            val readableTables = buildSet {
                expectedTables.forEach { table ->
                    database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                        if (cursor.moveToFirst() && cursor.getLong(0) >= 0L) add(table)
                    }
                }
            }
            isEncryptedDatabaseProbeUsable(quickCheckRows, readableTables, expectedTables)
        } catch (_: Throwable) {
            false
        } finally {
            helper.close()
        }
    }

    private fun checkpointPlaintextWal(database: PlainSQLiteDatabase) {
        database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
            validateWalCheckpoint(readCheckpointResult(cursor))
        }
    }

    private fun checkpointEncryptedWal(database: EncryptedSQLiteDatabase) {
        database.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { cursor ->
            validateWalCheckpoint(readCheckpointResult(cursor))
        }
    }

    private fun readCheckpointResult(cursor: Cursor): WalCheckpointResult {
        check(cursor.moveToFirst() && cursor.columnCount >= 3) {
            "Lingqi database WAL checkpoint did not return busy/log/checkpointed"
        }
        return WalCheckpointResult(
            busy = cursor.getInt(0),
            logFrames = cursor.getInt(1),
            checkpointedFrames = cursor.getInt(2)
        )
    }

    private fun checkResidualPlaintextBackupMatchesEncryptedSource(
        context: Context,
        crypto: CryptoManager,
        passphrase: ByteArray,
        databaseName: String,
        backupFile: File
    ) {
        val backup = PlainSQLiteDatabase.openDatabase(
            backupFile.absolutePath,
            null,
            PlainSQLiteDatabase.OPEN_READWRITE
        )
        val plaintextCounts = try {
            checkpointPlaintextWal(backup)
            plaintextTableCounts(backup)
        } finally {
            backup.close()
        }
        val encrypted = LingqiDatabase(context, crypto, databaseName, passphrase)
        val encryptedCounts = try {
            encryptedTableCounts(encrypted.readableDatabase)
        } finally {
            encrypted.close()
        }
        check(encryptedSourceMatchesPlaintextBackup(encryptedCounts, plaintextCounts)) {
            "Encrypted Lingqi source does not match its residual plaintext backup; both were preserved"
        }
    }

    private fun plaintextTableCounts(database: PlainSQLiteDatabase): Map<String, Int> =
        expectedTables.associateWith { table ->
            database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                check(cursor.moveToFirst()) { "Unable to count plaintext table $table" }
                cursor.getInt(0)
            }
        }

    private fun encryptedTableCounts(database: EncryptedSQLiteDatabase): Map<String, Int> =
        expectedTables.associateWith { table ->
            database.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                check(cursor.moveToFirst()) { "Unable to count encrypted table $table" }
                cursor.getInt(0)
            }
        }

    private fun copyTable(
        source: PlainSQLiteDatabase,
        target: EncryptedSQLiteDatabase,
        table: String,
        columns: Array<String>
    ): Int {
        var count = 0
        source.query(table, columns, null, null, null, null, null).use { cursor ->
            while (cursor.moveToNext()) {
                val values = ContentValues(columns.size)
                columns.forEachIndexed { index, column -> values.putCursorValue(column, cursor, index) }
                target.insertWithOnConflict(table, null, values, EncryptedSQLiteDatabase.CONFLICT_REPLACE)
                count += 1
            }
        }
        return count
    }

    private fun replacePlaintextDatabase(
        context: Context,
        crypto: CryptoManager,
        passphrase: ByteArray,
        databaseName: String,
        tempDatabaseName: String,
        backupDatabaseName: String,
        sourceFile: File,
        expectedCounts: Map<String, Int>,
        action: MigrationStartupAction
    ) {
        val encryptedFile = context.getDatabasePath(tempDatabaseName)
        val backupFile = context.getDatabasePath(backupDatabaseName)
        when (action) {
            MigrationStartupAction.MIGRATE_PLAINTEXT -> {
                checkedRename(sourceFile, backupFile, "secure the existing plaintext Lingqi database")
                checkedDeleteDatabaseSidecars(
                    sourceFile,
                    "remove checkpointed plaintext source sidecars before activation"
                )
            }

            MigrationStartupAction.RESTORE_BACKUP_AND_MIGRATE -> {
                checkedDeleteDatabaseSidecars(
                    backupFile,
                    "remove checkpointed plaintext backup sidecars before activation"
                )
                checkedDeleteDatabaseArtifacts(
                    sourceFile,
                    "remove unusable source artifacts before restored backup activation"
                )
            }

            else -> error("Unexpected Lingqi migration activation action: $action")
        }
        try {
            checkedRename(encryptedFile, sourceFile, "activate the encrypted Lingqi database")
        } catch (activationError: Throwable) {
            restoreBackupAfterActivationFailure(
                sourceFile = sourceFile,
                backupFile = backupFile,
                activationError = activationError
            )
        }

        try {
            val verifier = LingqiDatabase(context, crypto, databaseName, passphrase)
            try {
                expectedCounts.forEach { (table, expected) ->
                    verifier.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
                        check(cursor.moveToFirst() && cursor.getInt(0) == expected) {
                            "Encrypted Lingqi database verification failed for $table"
                        }
                    }
                }
            } finally {
                verifier.close()
            }
        } catch (verificationError: Throwable) {
            rollbackVerifiedActivation(
                sourceFile = sourceFile,
                backupFile = backupFile,
                verificationError = verificationError
            )
        }

        checkedDeleteDatabaseSidecars(
            context.getDatabasePath(tempDatabaseName),
            "remove encrypted migration temp sidecars"
        )
        checkedDeleteDatabaseSidecars(sourceFile, "remove encrypted source sidecars after verification")
        checkedDeleteDatabaseArtifacts(backupFile, "remove verified plaintext backup artifacts")
    }

    private fun restoreBackupAfterActivationFailure(
        sourceFile: File,
        backupFile: File,
        activationError: Throwable
    ): Nothing {
        try {
            restoreBackupFilesAfterActivationFailure(sourceFile, backupFile)
        } catch (restoreError: Throwable) {
            activationError.addSuppressed(restoreError)
            throw IllegalStateException(
                "Unable to activate the encrypted Lingqi database and restore its plaintext backup; " +
                    "recovery data remains at ${backupFile.absolutePath}",
                activationError
            )
        }
        throw activationError
    }

    private fun rollbackVerifiedActivation(
        sourceFile: File,
        backupFile: File,
        verificationError: Throwable
    ): Nothing {
        try {
            checkedDeleteDatabaseSidecars(sourceFile, "remove failed encrypted source sidecars")
            checkedDelete(sourceFile, "remove the failed encrypted source database")
            checkedRename(backupFile, sourceFile, "restore plaintext backup after verification failure")
        } catch (restoreError: Throwable) {
            verificationError.addSuppressed(restoreError)
            throw IllegalStateException(
                "Encrypted Lingqi database verification failed and the plaintext backup could not " +
                    "be restored; recovery data remains at ${backupFile.absolutePath}",
                verificationError
            )
        }
        throw verificationError
    }

    private fun ContentValues.putCursorValue(column: String, cursor: Cursor, index: Int) {
        when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> putNull(column)
            Cursor.FIELD_TYPE_INTEGER -> put(column, cursor.getLong(index))
            Cursor.FIELD_TYPE_FLOAT -> put(column, cursor.getDouble(index))
            Cursor.FIELD_TYPE_BLOB -> put(column, cursor.getBlob(index))
            else -> put(column, cursor.getString(index))
        }
    }

    private fun hasPlaintextHeader(file: File): Boolean {
        val header = ByteArray(plaintextHeader.size)
        file.inputStream().use { stream ->
            if (stream.read(header) != header.size) return false
        }
        return header.contentEquals(plaintextHeader)
    }

}
