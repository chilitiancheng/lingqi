package com.lingqi.app.data

import java.io.File

internal enum class MigrationFileState {
    MISSING,
    EMPTY,
    PLAINTEXT,
    ENCRYPTED,
    UNKNOWN
}

internal data class MigrationArtifactStates(
    val source: MigrationFileState,
    val temp: MigrationFileState,
    val backup: MigrationFileState,
    val sourceHasNonEmptySidecars: Boolean = false,
    val tempHasNonEmptySidecars: Boolean = false,
    val backupHasNonEmptySidecars: Boolean = false
)

internal enum class MigrationStartupAction {
    CREATE_NEW,
    MIGRATE_PLAINTEXT,
    RESTORE_BACKUP_AND_MIGRATE,
    OPEN_ENCRYPTED
}

internal data class MigrationStartupPlan(
    val action: MigrationStartupAction,
    val cleanSourceArtifacts: Boolean = false,
    val cleanTempArtifacts: Boolean = false,
    val cleanBackupArtifacts: Boolean = false
)

internal val MIGRATION_TABLE_NAMES = setOf(
    "meditation_sessions",
    "sleep_sessions",
    "sleep_epochs",
    "preferences"
)

internal fun encryptedSourceMatchesPlaintextBackup(
    encryptedCounts: Map<String, Int>,
    plaintextCounts: Map<String, Int>
): Boolean = encryptedCounts.keys == MIGRATION_TABLE_NAMES &&
    plaintextCounts.keys == MIGRATION_TABLE_NAMES &&
    encryptedCounts == plaintextCounts

internal data class WalCheckpointResult(
    val busy: Int,
    val logFrames: Int,
    val checkpointedFrames: Int
)

internal fun isEncryptedDatabaseProbeUsable(
    quickCheckRows: List<String>,
    readableTables: Set<String>,
    requiredTables: Set<String>
): Boolean = quickCheckRows == listOf("ok") && readableTables.containsAll(requiredTables)

internal fun validateWalCheckpoint(result: WalCheckpointResult) {
    check(result.busy == 0) {
        "Lingqi database WAL checkpoint is busy: busy=${result.busy}, " +
            "log=${result.logFrames}, checkpointed=${result.checkpointedFrames}"
    }
    check(result.checkpointedFrames >= result.logFrames) {
        "Lingqi database WAL checkpoint is incomplete: busy=${result.busy}, " +
            "log=${result.logFrames}, checkpointed=${result.checkpointedFrames}"
    }
}

internal fun runPlaintextMigrationBeforeFileSwitch(
    plaintextInputFile: File,
    prepareEncryptedDatabase: (File) -> Unit,
    switchFiles: () -> Unit
) {
    prepareEncryptedDatabase(plaintextInputFile)
    switchFiles()
}

internal fun checkedRename(
    source: File,
    destination: File,
    operation: String,
    rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
) {
    check(source.exists()) { "Unable to $operation: source does not exist at ${source.absolutePath}" }
    check(!destination.exists()) {
        "Unable to $operation: destination already exists at ${destination.absolutePath}"
    }
    check(rename(source, destination)) {
        "Unable to $operation: ${source.absolutePath} -> ${destination.absolutePath}"
    }
}

internal fun checkedDelete(
    file: File,
    operation: String,
    delete: (File) -> Boolean = File::delete
) {
    if (file.exists()) {
        check(delete(file)) { "Unable to $operation: ${file.absolutePath}" }
    }
}

internal fun checkedDeleteDatabaseArtifacts(databaseFile: File, operation: String) {
    databaseArtifacts(databaseFile).forEach { checkedDelete(it, operation) }
}

internal fun checkedDeleteDatabaseSidecars(
    databaseFile: File,
    operation: String,
    delete: (File) -> Boolean = File::delete
) {
    databaseSidecars(databaseFile).forEach { checkedDelete(it, operation, delete) }
}

internal fun databaseSidecars(databaseFile: File): List<File> =
    listOf("-wal", "-shm", "-journal").map { suffix -> File(databaseFile.absolutePath + suffix) }

private fun databaseArtifacts(databaseFile: File): List<File> =
    listOf(databaseFile) + databaseSidecars(databaseFile)

internal fun restoreBackupFilesAfterActivationFailure(
    sourceFile: File,
    backupFile: File,
    delete: (File) -> Boolean = File::delete,
    rename: (File, File) -> Boolean = { from, to -> from.renameTo(to) }
) {
    try {
        checkedDeleteDatabaseSidecars(
            sourceFile,
            "remove failed encrypted source sidecars",
            delete
        )
        checkedDelete(sourceFile, "remove the failed encrypted source database", delete)
        checkedRename(
            backupFile,
            sourceFile,
            "restore plaintext backup after activation failure",
            rename
        )
    } catch (error: Throwable) {
        throw IllegalStateException(
            "Unable to restore the plaintext backup; recovery data remains at ${backupFile.absolutePath}",
            error
        )
    }
}

internal fun planMigrationStartup(states: MigrationArtifactStates): MigrationStartupPlan {
    val hasAuthoritativeDatabase = states.source in setOf(
        MigrationFileState.PLAINTEXT,
        MigrationFileState.ENCRYPTED
    ) || states.backup == MigrationFileState.PLAINTEXT
    if (!hasAuthoritativeDatabase) {
        when {
            states.sourceHasNonEmptySidecars -> migrationConflict(
                "Lingqi database migration found an orphaned source sidecar without an " +
                    "authoritative database; all artifacts were preserved"
            )
            states.backupHasNonEmptySidecars -> migrationConflict(
                "Lingqi database migration found an orphaned backup sidecar without an " +
                    "authoritative database; all artifacts were preserved"
            )
            states.tempHasNonEmptySidecars -> migrationConflict(
                "Lingqi database migration found an orphaned temp sidecar without an " +
                    "authoritative database; all artifacts were preserved"
            )
        }
    }

    return when (states.source) {
        MigrationFileState.MISSING,
        MigrationFileState.EMPTY -> planWithoutUsableSource(states)

        MigrationFileState.PLAINTEXT -> planForPlaintextSource(states)
        MigrationFileState.ENCRYPTED -> planForEncryptedSource(states)
        MigrationFileState.UNKNOWN -> migrationConflict(
            "Lingqi database migration cannot identify the source database; " +
                "source, temp, and backup were preserved"
        )
    }
}

private fun planWithoutUsableSource(states: MigrationArtifactStates): MigrationStartupPlan {
    val cleanSource = states.source == MigrationFileState.EMPTY
    return when (states.backup) {
        MigrationFileState.PLAINTEXT -> {
            val tempIsUnrecognized = states.temp in setOf(
                MigrationFileState.PLAINTEXT,
                MigrationFileState.UNKNOWN
            )
            if (tempIsUnrecognized) {
                migrationConflict(
                    "Lingqi database migration found an unrecognized temp database before recovery; " +
                        "source, temp, and plaintext backup were preserved"
                )
            }
            MigrationStartupPlan(
                action = MigrationStartupAction.RESTORE_BACKUP_AND_MIGRATE,
                cleanSourceArtifacts = cleanSource,
                cleanTempArtifacts = true
            )
        }

        MigrationFileState.MISSING,
        MigrationFileState.EMPTY -> {
            if (states.temp !in setOf(MigrationFileState.MISSING, MigrationFileState.EMPTY)) {
                migrationConflict(
                    "Lingqi database migration found an orphaned temp database without a " +
                        "recoverable source or backup; temp was preserved"
                )
            }
            MigrationStartupPlan(
                action = MigrationStartupAction.CREATE_NEW,
                cleanSourceArtifacts = cleanSource,
                cleanTempArtifacts = true,
                cleanBackupArtifacts = true
            )
        }

        MigrationFileState.ENCRYPTED,
        MigrationFileState.UNKNOWN -> migrationConflict(
            "Lingqi database migration cannot use the non-plaintext backup while source is " +
                "missing; backup was preserved"
        )
    }
}

private fun planForPlaintextSource(states: MigrationArtifactStates): MigrationStartupPlan {
    if (states.backup !in setOf(MigrationFileState.MISSING, MigrationFileState.EMPTY)) {
        migrationConflict(
            "Lingqi database migration found both a plaintext source and a non-empty backup; " +
                "backup was preserved"
        )
    }
    requireDisposableTemp(states.temp, "restart plaintext migration")
    return MigrationStartupPlan(
        action = MigrationStartupAction.MIGRATE_PLAINTEXT,
        cleanTempArtifacts = true,
        cleanBackupArtifacts = true
    )
}

private fun planForEncryptedSource(states: MigrationArtifactStates): MigrationStartupPlan {
    if (states.backup !in setOf(
            MigrationFileState.MISSING,
            MigrationFileState.EMPTY,
            MigrationFileState.PLAINTEXT
        )
    ) {
        migrationConflict(
            "Lingqi database migration found an encrypted source with an unrecognized backup; " +
                "backup was preserved"
        )
    }
    requireDisposableTemp(states.temp, "open the verified encrypted source")
    return MigrationStartupPlan(
        action = MigrationStartupAction.OPEN_ENCRYPTED,
        cleanTempArtifacts = true,
        cleanBackupArtifacts = true
    )
}

private fun requireDisposableTemp(state: MigrationFileState, operation: String) {
    if (state == MigrationFileState.PLAINTEXT || state == MigrationFileState.UNKNOWN) {
        migrationConflict(
            "Lingqi database migration cannot $operation because temp is unrecognized; " +
                "temp was preserved"
        )
    }
}

private fun migrationConflict(message: String): Nothing = throw IllegalStateException(message)
