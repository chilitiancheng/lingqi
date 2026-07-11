package com.lingqi.app

import android.app.Application
import com.lingqi.app.data.AppContainer

class LingqiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        System.loadLibrary("sqlcipher")
        container = AppContainer(this)
    }
}
