package com.lingqi.app.data

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckedDatabaseFileOperationsTest {
    @Test
    fun activationRollbackKeepsBackupWhenSourceWalCannotBeDeleted() {
        val directory = Files.createTempDirectory("lingqi-activation-rollback").toFile()
        val source = File(directory, "lingqi.db")
        val sourceWal = File(directory, "lingqi.db-wal").apply { createNewFile() }
        val backup = File(directory, "lingqi.db.plaintext-backup").apply { createNewFile() }
        try {
            val error = runCatching {
                restoreBackupFilesAfterActivationFailure(
                    sourceFile = source,
                    backupFile = backup,
                    delete = { file -> if (file == sourceWal) false else file.delete() }
                )
            }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(error?.message.orEmpty().contains(backup.absolutePath))
            assertTrue(backup.exists())
            assertFalse(source.exists())
            assertTrue(sourceWal.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedRenameLeavesRecoverySourceAndThrowsNamedError() {
        val directory = Files.createTempDirectory("lingqi-rename-test").toFile()
        val source = File(directory, "lingqi.db.plaintext-backup").apply { createNewFile() }
        val destination = File(directory, "lingqi.db")
        try {
            val error = runCatching {
                checkedRename(source, destination, "restore plaintext backup") { _, _ -> false }
            }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(error?.message.orEmpty().contains("restore plaintext backup"))
            assertTrue(source.exists())
            assertFalse(destination.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedDeleteIsNeverReportedAsSuccessful() {
        val directory = Files.createTempDirectory("lingqi-delete-test").toFile()
        val file = File(directory, "lingqi.db-wal").apply { createNewFile() }
        try {
            val error = runCatching {
                checkedDelete(file, "remove encrypted source sidecar") { false }
            }.exceptionOrNull()

            assertTrue(error is IllegalStateException)
            assertTrue(error?.message.orEmpty().contains("remove encrypted source sidecar"))
            assertTrue(file.exists())
        } finally {
            directory.deleteRecursively()
        }
    }
}
