package com.lingqi.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LingqiBlack = Color(0xFF000000)
val LingqiWhite = Color(0xFFF4F4F2)
val LingqiMuted = Color(0xFF929692)
val LingqiLine = Color(0xFF262826)
val SleepLight = Color(0xFF8E8E8E)
val SleepDeep = Color(0xFF5C5C5C)
val SleepRem = Color(0xFFBEBEBE)
val SleepAwake = Color(0xFFE6E6E6)
val SleepCalibrationText = Color(0xFFB5B5B5)
val SleepCalibrationBackground = Color(0xFF151515)

private val colors = darkColorScheme(
    primary = LingqiWhite,
    onPrimary = LingqiBlack,
    background = LingqiBlack,
    onBackground = LingqiWhite,
    surface = Color(0xFF0A0B0A),
    onSurface = LingqiWhite,
    surfaceVariant = Color(0xFF151615),
    onSurfaceVariant = LingqiMuted,
    outline = LingqiLine
)

@Composable
fun LingqiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content
    )
}
