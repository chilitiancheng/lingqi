package com.lingqi.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertTrue
import org.junit.Test

class WalCheckpointValidatorTest {
    @Test
    fun restoreCheckpointFailureLeavesBackupAtOriginalPath() {
        val directory = Files.createTempDirectory("lingqi-restore-checkpoint").toFile()
        val source = File(directory, "lingqi.db")
        val backup = File(directory, "lingqi.db.plaintext-backup").apply { createNewFile() }
        var preparedInput: File? = null
        try {
            val error = runCatching {
                runPlaintextMigrationBeforeFileSwitch(
                    plaintextInputFile = backup,
                    prepareEncryptedDatabase = { input ->
                        preparedInput = input
                        validateWalCheckpoint(
                            WalCheckpointResult(busy = 1, logFrames = 4, checkpointedFrames = 4)
                        )
                    },
                    switchFiles = {
                        checkedRename(backup, source, "restore plaintext backup")
                    }
                )
            }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(preparedInput == backup)
            assertTrue(backup.exists())
            assertTrue(!source.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun busyCheckpointStopsMigration() {
        val error = runCatching {
            validateWalCheckpoint(WalCheckpointResult(busy = 1, logFrames = 8, checkpointedFrames = 8))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("busy=1"))
    }

    @Test
    fun partiallyCheckpointedWalStopsMigration() {
        val error = runCatching {
            validateWalCheckpoint(WalCheckpointResult(busy = 0, logFrames = 8, checkpointedFrames = 7))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message.orEmpty().contains("checkpointed=7"))
    }

    @Test
    fun completeCheckpointAllowsMigration() {
        validateWalCheckpoint(WalCheckpointResult(busy = 0, logFrames = 8, checkpointedFrames = 8))
    }
}
