package com.lingqi.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptedDatabaseProbeTest {
    private val tables = setOf(
        "meditation_sessions",
        "sleep_sessions",
        "sleep_epochs",
        "preferences"
    )

    @Test
    fun quickCheckMustReturnOnlyOk() {
        assertFalse(isEncryptedDatabaseProbeUsable(listOf("corrupt page"), tables, tables))
        assertFalse(isEncryptedDatabaseProbeUsable(listOf("ok", "extra"), tables, tables))
    }

    @Test
    fun everyRequiredTableMustBeReadable() {
        assertFalse(
            isEncryptedDatabaseProbeUsable(
                quickCheckRows = listOf("ok"),
                readableTables = tables - "sleep_epochs",
                requiredTables = tables
            )
        )
    }

    @Test
    fun healthyDatabaseWithAllReadableTablesIsUsable() {
        assertTrue(isEncryptedDatabaseProbeUsable(listOf("ok"), tables, tables))
    }
}
