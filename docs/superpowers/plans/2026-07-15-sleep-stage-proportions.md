# Sleep Stage Proportions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Lingqi-styled sleep-structure donut chart that shows light, deep, and REM proportions, durations, reference ranges, and valid-sleep total time.

**Architecture:** A pure Kotlin calculator converts stored `SleepEpoch` values into a stable three-slice distribution. A focused Compose file renders the donut, leader labels, and breakdown card, while `SleepReportScreen` only calculates the distribution and places the component between summary metrics and the existing trend chart.

**Tech Stack:** Kotlin, Jetpack Compose Canvas, AndroidX Compose UI tests, JUnit 4, existing Lingqi sleep-stage palette.

---

## File map

- Create `app/src/main/java/com/lingqi/app/sleep/SleepStageDistribution.kt`: pure stage-duration, percentage allocation, reference-range, and duration-formatting logic.
- Create `app/src/test/java/com/lingqi/app/sleep/SleepStageDistributionTest.kt`: unit coverage for filtering, durations, rounding, empty data, and single-stage data.
- Create `app/src/main/java/com/lingqi/app/ui/screens/SleepStageDistributionCard.kt`: donut Canvas, leader labels, details rows, and empty state.
- Create `app/src/androidTest/java/com/lingqi/app/ui/SleepStageDistributionCardTest.kt`: Compose semantics and text regression coverage.
- Modify `app/src/main/java/com/lingqi/app/ui/screens/SleepReportScreen.kt`: calculate and insert the new section without removing the existing trend chart.

### Task 1: Build the pure sleep-stage distribution calculator

**Files:**
- Create: `app/src/test/java/com/lingqi/app/sleep/SleepStageDistributionTest.kt`
- Create: `app/src/main/java/com/lingqi/app/sleep/SleepStageDistribution.kt`

- [ ] **Step 1: Write the failing calculator tests**

Create test helpers that construct 30-second epochs and assert all confirmed rules:

```kotlin
package com.lingqi.app.sleep

import com.lingqi.app.data.SleepEpoch
import com.lingqi.app.data.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepStageDistributionTest {
    @Test
    fun `awake and low coverage epochs are excluded`() {
        val epochs = listOf(
            epoch(0L, SleepStage.LIGHT),
            epoch(30_000L, SleepStage.DEEP),
            epoch(60_000L, SleepStage.REM),
            epoch(90_000L, SleepStage.AWAKE),
            epoch(120_000L, SleepStage.LIGHT, coverage = 0.19)
        )

        val result = calculateSleepStageDistribution(epochs, endedAt = 150_000L)

        assertEquals(90_000L, result.totalSleepMillis)
        assertEquals(listOf(34, 33, 33), result.slices.map { it.percentage })
        assertEquals(100, result.slices.sumOf { it.percentage })
    }

    @Test
    fun `final partial epoch uses report end and each epoch is capped at thirty seconds`() {
        val epochs = listOf(
            epoch(0L, SleepStage.LIGHT),
            epoch(90_000L, SleepStage.REM)
        )

        val result = calculateSleepStageDistribution(epochs, endedAt = 100_000L)

        assertEquals(40_000L, result.totalSleepMillis)
        assertEquals(30_000L, result.slice(SleepStage.LIGHT).durationMillis)
        assertEquals(10_000L, result.slice(SleepStage.REM).durationMillis)
    }

    @Test
    fun `empty and awake-only data has no chart`() {
        assertFalse(calculateSleepStageDistribution(emptyList(), null).hasData)
        assertFalse(
            calculateSleepStageDistribution(
                listOf(epoch(0L, SleepStage.AWAKE)),
                endedAt = 30_000L
            ).hasData
        )
    }

    @Test
    fun `single sleep stage is one hundred percent`() {
        val result = calculateSleepStageDistribution(
            listOf(epoch(0L, SleepStage.DEEP)),
            endedAt = 30_000L
        )

        assertTrue(result.hasData)
        assertEquals(listOf(0, 100, 0), result.slices.map { it.percentage })
    }

    @Test
    fun `duration formatter uses compact Chinese units`() {
        assertEquals("0分", formatSleepDuration(0L))
        assertEquals("45分", formatSleepDuration(45 * 60_000L))
        assertEquals("6时59分", formatSleepDuration((6 * 60 + 59) * 60_000L))
    }

    private fun epoch(
        startedAt: Long,
        stage: SleepStage,
        coverage: Double = 1.0
    ) = SleepEpoch(
        startedAt = startedAt,
        movementRms = 0.0,
        movementPeaks = 0,
        noiseDb = 0.0,
        noiseEvents = 0,
        snoreProbability = 0.0,
        coverage = coverage,
        stage = stage,
        confidence = 1.0
    )
}
```

- [ ] **Step 2: Run the new test and verify the red state**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.lingqi.app.sleep.SleepStageDistributionTest
```

Expected: compilation fails because `calculateSleepStageDistribution`, `SleepStageDistribution`, and `formatSleepDuration` do not exist.

- [ ] **Step 3: Implement the minimal pure calculator**

Create these public-to-module types and functions:

```kotlin
package com.lingqi.app.sleep

import com.lingqi.app.data.SleepEpoch
import com.lingqi.app.data.SleepStage
import kotlin.math.floor

internal data class SleepStageSlice(
    val stage: SleepStage,
    val durationMillis: Long,
    val percentage: Int,
    val referenceRange: String
)

internal data class SleepStageDistribution(
    val slices: List<SleepStageSlice>,
    val totalSleepMillis: Long
) {
    val hasData: Boolean get() = totalSleepMillis > 0L
    fun slice(stage: SleepStage): SleepStageSlice = slices.first { it.stage == stage }
}

internal fun calculateSleepStageDistribution(
    epochs: List<SleepEpoch>,
    endedAt: Long?
): SleepStageDistribution {
    val ordered = epochs.sortedBy { it.startedAt }
    val durations = linkedMapOf(
        SleepStage.LIGHT to 0L,
        SleepStage.DEEP to 0L,
        SleepStage.REM to 0L
    )
    ordered.forEachIndexed { index, epoch ->
        if (epoch.stage == SleepStage.AWAKE || epoch.coverage < MIN_STAGE_COVERAGE) return@forEachIndexed
        val nextStart = ordered.getOrNull(index + 1)?.startedAt ?: endedAt ?: (epoch.startedAt + EPOCH_MILLIS)
        val duration = (nextStart - epoch.startedAt).coerceIn(0L, EPOCH_MILLIS)
        durations.computeIfPresent(epoch.stage) { _, current -> current + duration }
    }
    val total = durations.values.sum()
    val percentages = allocatePercentages(durations.values.toList(), total)
    val ranges = mapOf(
        SleepStage.LIGHT to "45%-60%",
        SleepStage.DEEP to "20%-35%",
        SleepStage.REM to "15%-25%"
    )
    return SleepStageDistribution(
        slices = durations.entries.mapIndexed { index, (stage, duration) ->
            SleepStageSlice(stage, duration, percentages[index], ranges.getValue(stage))
        },
        totalSleepMillis = total
    )
}

private fun allocatePercentages(durations: List<Long>, total: Long): List<Int> {
    if (total <= 0L) return List(durations.size) { 0 }
    val exact = durations.map { it * 100.0 / total }
    val result = exact.map { floor(it).toInt() }.toMutableList()
    repeat(100 - result.sum()) {
        val index = exact.indices.maxBy { exact[it] - result[it] }
        result[index] += 1
    }
    return result
}

internal fun formatSleepDuration(durationMillis: Long): String {
    val totalMinutes = (durationMillis.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}时${minutes}分" else "${minutes}分"
}

private const val MIN_STAGE_COVERAGE = 0.2
private const val EPOCH_MILLIS = 30_000L
```

- [ ] **Step 4: Run the calculator tests and verify green**

Run the same targeted Gradle command. Expected: `BUILD SUCCESSFUL` and all five tests pass.

- [ ] **Step 5: Commit the calculator**

```powershell
git add -- app/src/main/java/com/lingqi/app/sleep/SleepStageDistribution.kt app/src/test/java/com/lingqi/app/sleep/SleepStageDistributionTest.kt
git commit -m "Calculate sleep stage proportions"
```

### Task 2: Render the Lingqi sleep-structure component

**Files:**
- Create: `app/src/main/java/com/lingqi/app/ui/screens/SleepStageDistributionCard.kt`
- Create: `app/src/androidTest/java/com/lingqi/app/ui/SleepStageDistributionCardTest.kt`

- [ ] **Step 1: Write the failing Compose test**

Render a fixed `SleepStageDistribution` and assert the component exposes the confirmed labels and values:

```kotlin
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
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun cardShowsThreeStagesPercentagesRangesAndDurations() {
        composeRule.setContent {
            SleepStageDistributionCard(
                SleepStageDistribution(
                    totalSleepMillis = (6 * 60 + 59) * 60_000L,
                    slices = listOf(
                        SleepStageSlice(SleepStage.LIGHT, 218 * 60_000L, 52, "45%-60%"),
                        SleepStageSlice(SleepStage.DEEP, 117 * 60_000L, 28, "20%-35%"),
                        SleepStageSlice(SleepStage.REM, 84 * 60_000L, 20, "15%-25%")
                    )
                )
            )
        }
        listOf("睡眠结构", "6时59分", "浅睡眠", "52%", "深睡眠", "28%", "眼动期", "20%")
            .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
    }
}
```

- [ ] **Step 2: Verify the Compose test does not compile**

Run:

```powershell
.\gradlew.bat assembleDebugAndroidTest
```

Expected: compilation fails because `SleepStageDistributionCard` does not exist.

- [ ] **Step 3: Implement the component**

Create `SleepStageDistributionCard.kt` with this complete structure. Keep the draw order `REM → LIGHT → DEEP` so the fixed leader-label positions match the approved reference image, while the details rows stay `LIGHT → DEEP → REM`.

```kotlin
package com.lingqi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingqi.app.data.SleepStage
import com.lingqi.app.sleep.SleepStageDistribution
import com.lingqi.app.sleep.SleepStageSlice
import com.lingqi.app.sleep.formatSleepDuration
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite
import com.lingqi.app.ui.theme.SleepDeep
import com.lingqi.app.ui.theme.SleepLight
import com.lingqi.app.ui.theme.SleepRem
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun SleepStageDistributionCard(distribution: SleepStageDistribution) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("睡眠结构", color = LingqiWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "睡眠结构说明",
                tint = LingqiMuted,
                modifier = Modifier.padding(start = 6.dp).size(16.dp)
            )
        }
        if (!distribution.hasData) {
            Text("暂无足够睡眠数据", color = LingqiMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 24.dp))
            return@Column
        }
        SleepStageDonutChart(distribution, Modifier.fillMaxWidth().height(300.dp))
        Surface(
            color = Color(0xFF101010),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color(0xFF242424)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                distribution.slices.forEachIndexed { index, slice ->
                    SleepStageBreakdownRow(slice)
                    if (index < distribution.slices.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepStageDonutChart(distribution: SleepStageDistribution, modifier: Modifier = Modifier) {
    val drawOrder = listOf(SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val diameter = min(size.width * 0.62f, size.height * 0.62f)
            val strokeWidth = 34.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f
            var startAngle = -90f
            drawOrder.forEach { stage ->
                val slice = distribution.slice(stage)
                val sweep = slice.percentage * 3.6f
                drawArc(
                    color = stageColor(stage),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                )
                if (sweep > 0f) {
                    val radians = Math.toRadians(startAngle.toDouble())
                    val inner = Offset(
                        center.x + cos(radians).toFloat() * (radius - strokeWidth / 2f),
                        center.y + sin(radians).toFloat() * (radius - strokeWidth / 2f)
                    )
                    val outer = Offset(
                        center.x + cos(radians).toFloat() * (radius + strokeWidth / 2f),
                        center.y + sin(radians).toFloat() * (radius + strokeWidth / 2f)
                    )
                    drawLine(Color(0xFF050505), inner, outer, 2.dp.toPx())
                }
                val middleRadians = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val anchor = Offset(
                    center.x + cos(middleRadians).toFloat() * (radius + strokeWidth / 2f),
                    center.y + sin(middleRadians).toFloat() * (radius + strokeWidth / 2f)
                )
                val direction = if (anchor.x < center.x) -1f else 1f
                val elbow = Offset(anchor.x + direction * 22.dp.toPx(), anchor.y + 22.dp.toPx())
                val end = Offset(elbow.x + direction * 50.dp.toPx(), elbow.y)
                drawLine(Color(0xFF777777), anchor, elbow, 1.dp.toPx())
                drawLine(Color(0xFF777777), elbow, end, 1.dp.toPx())
                startAngle += sweep
            }
        }
        Text(
            formatSleepDuration(distribution.totalSleepMillis),
            color = LingqiWhite,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
        Text("深睡眠", color = LingqiWhite, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopStart).padding(top = 56.dp))
        Text("眼动期", color = LingqiWhite, fontSize = 13.sp, modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp))
        Text("浅睡眠", color = LingqiWhite, fontSize = 13.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 52.dp))
    }
}

@Composable
private fun SleepStageBreakdownRow(slice: SleepStageSlice) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(4.dp).height(24.dp).background(stageColor(slice.stage), RoundedCornerShape(50)))
            Text(slice.stage.displayName(), color = LingqiWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 10.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${slice.percentage}%", color = LingqiWhite, fontSize = 30.sp, fontWeight = FontWeight.Light)
                Text("参考：${slice.referenceRange}", color = LingqiMuted, fontSize = 12.sp, modifier = Modifier.padding(start = 10.dp, bottom = 4.dp))
            }
            Text(formatSleepDuration(slice.durationMillis), color = LingqiWhite, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
    }
}

private fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.LIGHT -> SleepLight
    SleepStage.DEEP -> SleepDeep
    SleepStage.REM -> SleepRem
    SleepStage.AWAKE -> Color.Transparent
}

private fun SleepStage.displayName(): String = when (this) {
    SleepStage.LIGHT -> "浅睡眠"
    SleepStage.DEEP -> "深睡眠"
    SleepStage.REM -> "眼动期"
    SleepStage.AWAKE -> "清醒"
}
```

- [ ] **Step 4: Compile the Android test APK**

Run `assembleDebugAndroidTest`. Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit the UI component**

```powershell
git add -- app/src/main/java/com/lingqi/app/ui/screens/SleepStageDistributionCard.kt app/src/androidTest/java/com/lingqi/app/ui/SleepStageDistributionCardTest.kt
git commit -m "Render sleep stage proportion chart"
```

### Task 3: Insert the component into the report

**Files:**
- Modify: `app/src/main/java/com/lingqi/app/ui/screens/SleepReportScreen.kt`
- Test: `app/src/test/java/com/lingqi/app/sleep/SleepStageDistributionTest.kt`

- [ ] **Step 1: Add a regression assertion for the report end time**

Extend the calculator test so a 10-second final REM epoch produces a 25% REM share against one 30-second light epoch. Run it before integration and confirm the calculator remains green.

- [ ] **Step 2: Calculate once per loaded report**

Immediately after `val epochs = loadedSession.epochs`, add:

```kotlin
val stageDistribution = remember(epochs, loadedSession.endedAt) {
    calculateSleepStageDistribution(epochs, loadedSession.endedAt)
}
```

Import `calculateSleepStageDistribution` from `com.lingqi.app.sleep`.

- [ ] **Step 3: Place the new section before the trend chart**

After the existing three-metric summary item and before the item containing `SleepStageChart`, add:

```kotlin
item {
    SleepStageDistributionCard(stageDistribution)
}
```

The chart area stays directly on the report's black background, matching the approved mockup; only the lower breakdown list uses its own rounded surface.

Do not remove `SleepStageChart`, `StageLegend`, awake-event metrics, or the non-medical disclaimer.

- [ ] **Step 4: Run focused and full desktop verification**

Run:

```powershell
.\gradlew.bat testDebugUnitTest --tests com.lingqi.app.sleep.SleepStageDistributionTest
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
git diff --check
```

Expected: all commands exit 0; no unit failures, build failures, lint errors, or whitespace errors.

- [ ] **Step 5: Commit report integration**

```powershell
git add -- app/src/main/java/com/lingqi/app/ui/screens/SleepReportScreen.kt app/src/test/java/com/lingqi/app/sleep/SleepStageDistributionTest.kt
git commit -m "Add sleep structure to reports"
```

### Task 4: Final device and release verification

**Files:** No production file changes expected.

- [ ] **Step 1: Build signed release and inspect signatures**

```powershell
.\gradlew.bat assembleRelease
& "$env:LOCALAPPDATA\Android\Sdk\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs C:\Users\Administrator\.lingqi-build\app\outputs\apk\release\app-release.apk
```

Expected: Release build succeeds, APK verifies with v2 signing, and certificate SHA-256 remains `14b8fd2ab09813830c7aca377cc5edd4f85cd38849e5fe1345a94b5fb4065bfd`.

- [ ] **Step 2: If the shared phone is available and unlocked, install Debug without clearing data**

Use `adb install -r` for the main Debug APK and `adb install -r -t` for the test APK. Never uninstall or clear app data.

- [ ] **Step 3: Run the targeted Compose and installed-data tests**

Run `SleepStageDistributionCardTest`, `InstalledSleepPerformanceProbeTest`, and `InstalledSleepDataSafetyProbeTest`. Expected: chart text is visible, three existing reports remain, and report load stays below 100ms.

- [ ] **Step 4: Manually inspect one existing report**

Confirm the donut matches the approved reference layout, percentages sum to 100%, total stage duration is plausible, the existing trend chart remains visible, and scrolling stays responsive.

- [ ] **Step 5: Clean device tooling and publish**

Remove ADB forwards/reverses, stop the ADB server, push `main`, and report the final Release APK path and SHA-256. Do not stage or modify `hatch-pet-nightwatch/`, `交付/`, or old Web files.
