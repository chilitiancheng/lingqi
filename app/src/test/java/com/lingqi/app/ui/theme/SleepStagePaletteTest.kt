package com.lingqi.app.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepStagePaletteTest {
    @Test
    fun stageAndCalibrationColorsAreDistinctNeutralGrays() {
        val stageColors = listOf(SleepAwake, SleepLight, SleepDeep, SleepRem)
        assertEquals(4, stageColors.map { it.value }.toSet().size)
        (stageColors + SleepCalibrationText + SleepCalibrationBackground).forEach(::assertNeutralGray)
    }

    private fun assertNeutralGray(color: Color) {
        assertEquals(color.red, color.green, 0.0001f)
        assertEquals(color.green, color.blue, 0.0001f)
    }
}
