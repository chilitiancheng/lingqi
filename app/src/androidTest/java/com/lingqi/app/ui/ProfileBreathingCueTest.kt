package com.lingqi.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.lingqi.app.data.BreathingCueSound
import com.lingqi.app.ui.screens.BreathingCueSoundDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProfileBreathingCueTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dialogShowsAllChoicesAndReturnsSelectedSound() {
        var selected: BreathingCueSound? = null
        composeRule.setContent {
            BreathingCueSoundDialog(
                selected = BreathingCueSound.PENDULUM,
                onSelect = { selected = it },
                onDismiss = {}
            )
        }

        composeRule.onNodeWithText("摆钟咔哒").assertIsDisplayed()
        composeRule.onNodeWithText("清脆短铃").assertIsDisplayed()
        composeRule.onNodeWithText("静音").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(BreathingCueSound.SILENT, selected)
        }
    }
}
