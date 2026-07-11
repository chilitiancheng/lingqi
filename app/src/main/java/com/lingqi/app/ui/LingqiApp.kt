package com.lingqi.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lingqi.app.data.MeditationKind
import com.lingqi.app.ui.screens.HomeScreen
import com.lingqi.app.ui.screens.MeditationLibraryScreen
import com.lingqi.app.ui.screens.MeditationPlayerScreen
import com.lingqi.app.ui.screens.ProfileScreen
import com.lingqi.app.ui.screens.SleepReportScreen
import com.lingqi.app.ui.screens.SleepScreen
import com.lingqi.app.ui.theme.LingqiBlack

sealed interface LingqiScreen {
    data object Home : LingqiScreen
    data object MeditationLibrary : LingqiScreen
    data class MeditationPlayer(val kind: MeditationKind, val minutes: Int) : LingqiScreen
    data object Sleep : LingqiScreen
    data class SleepReport(val sessionId: String) : LingqiScreen
    data object Profile : LingqiScreen
}

@Composable
fun LingqiApp(
    screen: LingqiScreen,
    onNavigate: (LingqiScreen) -> Unit,
    onQuickStart: () -> Unit
) {
    BackHandler(
        enabled = screen !is LingqiScreen.Home && screen !is LingqiScreen.MeditationPlayer
    ) { onNavigate(LingqiScreen.Home) }
    Box(Modifier.fillMaxSize().background(LingqiBlack)) {
        when (screen) {
            LingqiScreen.Home -> HomeScreen(onNavigate = onNavigate, onQuickStart = onQuickStart)
            LingqiScreen.MeditationLibrary -> MeditationLibraryScreen(
                onBack = { onNavigate(LingqiScreen.Home) },
                onStart = { kind, minutes -> onNavigate(LingqiScreen.MeditationPlayer(kind, minutes)) }
            )
            is LingqiScreen.MeditationPlayer -> MeditationPlayerScreen(
                kind = screen.kind,
                minutes = screen.minutes,
                onExit = { onNavigate(LingqiScreen.Home) }
            )
            LingqiScreen.Sleep -> SleepScreen(
                onBack = { onNavigate(LingqiScreen.Home) },
                onOpenReport = { onNavigate(LingqiScreen.SleepReport(it)) }
            )
            is LingqiScreen.SleepReport -> SleepReportScreen(
                sessionId = screen.sessionId,
                onBack = { onNavigate(LingqiScreen.Sleep) }
            )
            LingqiScreen.Profile -> ProfileScreen(onBack = { onNavigate(LingqiScreen.Home) })
        }
    }
}
