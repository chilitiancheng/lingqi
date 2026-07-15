package com.lingqi.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.lingqi.app.data.SleepStage
import com.lingqi.app.sleep.SleepStageDistribution
import com.lingqi.app.sleep.SleepStageSlice
import com.lingqi.app.ui.screens.SleepStageDistributionCard
import org.junit.Rule
import org.junit.Test

class SleepStageDistributionCardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun cardShowsThreeStagesPercentagesRangesAndDurations() {
        composeRule.setContent {
            SleepStageDistributionCard(
                SleepStageDistribution(
                    totalSleepMillis = 25_140_000L,
                    slices = listOf(
                        SleepStageSlice(SleepStage.LIGHT, 13_080_000L, 52, "45%-60%"),
                        SleepStageSlice(SleepStage.DEEP, 7_020_000L, 28, "20%-35%"),
                        SleepStageSlice(SleepStage.REM, 5_040_000L, 20, "15%-25%")
                    )
                )
            )
        }

        listOf(
            "睡眠结构",
            "6时59分",
            "浅睡眠",
            "52%",
            "参考：45%-60%",
            "3时38分",
            "深睡眠",
            "28%",
            "参考：20%-35%",
            "1时57分",
            "眼动期",
            "20%",
            "参考：15%-25%",
            "1时24分"
        ).forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun emptyDistributionShowsNoDataMessage() {
        composeRule.setContent {
            SleepStageDistributionCard(
                SleepStageDistribution(
                    totalSleepMillis = 0L,
                    slices = listOf(
                        SleepStageSlice(SleepStage.LIGHT, 0L, 0, "45%-60%"),
                        SleepStageSlice(SleepStage.DEEP, 0L, 0, "20%-35%"),
                        SleepStageSlice(SleepStage.REM, 0L, 0, "15%-25%")
                    )
                )
            )
        }

        composeRule.onNodeWithText("暂无足够睡眠数据").assertIsDisplayed()
    }
}
