package com.lingqi.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.lingqi.app.data.MeditationKind
import com.lingqi.app.sleep.SleepTracker
import com.lingqi.app.ui.LingqiApp
import com.lingqi.app.ui.LingqiScreen
import com.lingqi.app.ui.theme.LingqiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LingqiTheme {
                val initialScreen = remember {
                    debugInitialScreen()
                        ?: if (SleepTracker.getStatus(this).active) LingqiScreen.Sleep else LingqiScreen.Home
                }
                var screen: LingqiScreen by remember { mutableStateOf(initialScreen) }
                LingqiApp(
                    screen = screen,
                    onNavigate = { screen = it },
                    onQuickStart = { screen = LingqiScreen.MeditationPlayer(MeditationKind.BREATH_478, 5) }
                )
            }
        }
    }

    private fun debugInitialScreen(): LingqiScreen? {
        if (!BuildConfig.DEBUG) return null
        return when (intent.getStringExtra(EXTRA_DEBUG_SCREEN)) {
            "breath" -> LingqiScreen.MeditationPlayer(MeditationKind.BREATH_478, 5)
            "meditation" -> LingqiScreen.MeditationLibrary
            "sleep" -> LingqiScreen.Sleep
            "profile" -> LingqiScreen.Profile
            else -> null
        }
    }

    companion object {
        private const val EXTRA_DEBUG_SCREEN = "com.lingqi.app.DEBUG_SCREEN"
    }
}
