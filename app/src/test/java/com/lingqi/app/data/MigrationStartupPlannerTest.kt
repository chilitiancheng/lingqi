package com.lingqi.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationStartupPlannerTest {
    @Test
    fun sourceWalWithoutAuthoritativeDatabaseIsPreservedAndRejected() {
        val error = runCatching {
            planMigrationStartup(
                MigrationArtifactStates(
                    source = MigrationFileState.MISSING,
                    temp = MigrationFileState.MISSING,
                    backup = MigrationFileState.MISSING,
                    sourceHasNonEmptySidecars = true
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("source sidecar"))
    }

    @Test
    fun backupWalWithoutAuthoritativeDatabaseIsPreservedAndRejected() {
        val error = runCatching {
            planMigrationStartup(
                MigrationArtifactStates(
                    source = MigrationFileState.MISSING,
                    temp = MigrationFileState.MISSING,
                    backup = MigrationFileState.MISSING,
                    backupHasNonEmptySidecars = true
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("backup sidecar"))
    }

    @Test
    fun tempWalWithoutAuthoritativeDatabaseIsPreservedAndRejected() {
        val error = runCatching {
            planMigrationStartup(
                MigrationArtifactStates(
                    source = MigrationFileState.MISSING,
                    temp = MigrationFileState.MISSING,
                    backup = MigrationFileState.MISSING,
                    tempHasNonEmptySidecars = true
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("temp sidecar"))
    }

    @Test
    fun plaintextBackupMakesOrphanedTempWalDisposableAfterRecovery() {
        val plan = planMigrationStartup(
            MigrationArtifactStates(
                source = MigrationFileState.MISSING,
                temp = MigrationFileState.MISSING,
                backup = MigrationFileState.PLAINTEXT,
                tempHasNonEmptySidecars = true
            )
        )

        assertEquals(MigrationStartupAction.RESTORE_BACKUP_AND_MIGRATE, plan.action)
        assertTrue(plan.cleanTempArtifacts)
    }

    @Test
    fun missingSourceWithPlaintextBackupRestoresBeforeOpening() {
        val plan = planMigrationStartup(
            MigrationArtifactStates(
                source = MigrationFileState.MISSING,
                temp = MigrationFileState.EMPTY,
                backup = MigrationFileState.PLAINTEXT
            )
        )

        assertEquals(MigrationStartupAction.RESTORE_BACKUP_AND_MIGRATE, plan.action)
        assertTrue(plan.cleanTempArtifacts)
    }

    @Test
    fun unrecognizedTempBlocksRecoveryAndPreservesEveryCandidate() {
        val error = runCatching {
            planMigrationStartup(
                MigrationArtifactStates(
                    source = MigrationFileState.MISSING,
                    temp = MigrationFileState.UNKNOWN,
                    backup = MigrationFileState.PLAINTEXT
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("before recovery"))
        assertTrue(error?.message.orEmpty().contains("preserved"))
    }

    @Test
    fun verifiedEncryptedSourceRemovesResidualPlaintextBackup() {
        val plan = planMigrationStartup(
            MigrationArtifactStates(
                source = MigrationFileState.ENCRYPTED,
                temp = MigrationFileState.ENCRYPTED,
                backup = MigrationFileState.PLAINTEXT
            )
        )

        assertEquals(MigrationStartupAction.OPEN_ENCRYPTED, plan.action)
        assertTrue(plan.cleanTempArtifacts)
        assertTrue(plan.cleanBackupArtifacts)
    }

    @Test
    fun residualPlaintextBackupCanOnlyBeRemovedAfterAllTableCountsMatch() {
        val sourceCounts = linkedMapOf(
            "meditation_sessions" to 3,
            "sleep_sessions" to 2,
            "sleep_epochs" to 120,
            "preferences" to 1
        )

        assertTrue(encryptedSourceMatchesPlaintextBackup(sourceCounts, sourceCounts.toMap()))
        assertTrue(
            !encryptedSourceMatchesPlaintextBackup(
                sourceCounts,
                sourceCounts.toMutableMap().apply { put("sleep_epochs", 119) }
            )
        )
        assertTrue(
            !encryptedSourceMatchesPlaintextBackup(
                sourceCounts,
                sourceCounts - "preferences"
            )
        )
    }

    @Test
    fun unknownSourceConflictIsRejectedWithoutCleanup() {
        val error = runCatching {
            planMigrationStartup(
                MigrationArtifactStates(
                    source = MigrationFileState.UNKNOWN,
                    temp = MigrationFileState.MISSING,
                    backup = MigrationFileState.PLAINTEXT
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("source"))
    }

    @Test
    fun twoPlaintextCandidatesAreRejectedAsAmbiguous() {
        val error = runCatching {
            planMigrationStartup(
                MigrationArtifactStates(
                    source = MigrationFileState.PLAINTEXT,
                    temp = MigrationFileState.MISSING,
                    backup = MigrationFileState.PLAINTEXT
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("backup"))
    }

    @Test
    fun orphanedEncryptedTempIsNotDiscardedToCreateEmptyDatabase() {
        val error = runCatching {
            planMigrationStartup(
                MigrationArtifactStates(
                    source = MigrationFileState.MISSING,
                    temp = MigrationFileState.ENCRYPTED,
                    backup = MigrationFileState.MISSING
                )
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("temp"))
    }
}
