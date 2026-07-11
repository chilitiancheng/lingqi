package com.lingqi.app.data

import android.content.Context
import com.lingqi.app.sleep.SleepAnalyzer

class AppContainer(context: Context) {
    val crypto = CryptoManager()
    private val databasePassphrase = crypto.databasePassphrase(context)
    val database = LingqiDatabaseMigration.openEncrypted(context, crypto, databasePassphrase)
    val repository = LingqiRepository(database)
    val sleepAnalyzer = SleepAnalyzer()
}
