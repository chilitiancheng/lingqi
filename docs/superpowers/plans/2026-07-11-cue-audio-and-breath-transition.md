# Cue Audio and Breath Transition Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 478 的“滴/嗒”改为清脆、易听的固定频率短铃，并消除呼气结束到下轮吸气开始时的粒子大小跳变。

**Architecture:** 将无 Android 依赖的 PCM 合成逻辑从 `CuePlayer` 拆到独立纯 Kotlin 文件，`CuePlayer` 只选择音色规格并管理 `AudioTrack`。将粒子基础大小抽为纯函数，保留现有聚散坐标计算，仅使呼气阶段的大小从 76 平滑回到 58。

**Tech Stack:** Kotlin, Android `AudioTrack`, Jetpack Compose/OpenGL ES, JUnit 4, Gradle Android plugin.

---

## File map and scope guard

- Create `app/src/main/java/com/lingqi/app/meditation/CueSynthesis.kt`: 固定提示音规格、包络、波形与 PCM 样本生成。
- Modify `app/src/main/java/com/lingqi/app/meditation/CuePlayer.kt`: 调用新合成函数，保留媒体通道、异常隔离与现有停止行为。
- Create `app/src/test/java/com/lingqi/app/meditation/CueSynthesisTest.kt`: 验证固定频率、18% 二次谐波、5 ms 起音、三次方衰减、峰值和首尾静音。
- Delete `app/src/test/java/com/lingqi/app/meditation/CuePlayerTest.kt`: 旧扫频签名的测试被新纯合成测试取代。
- Modify `app/src/main/java/com/lingqi/app/ui/particle/LingqiParticleView.kt`: 新增可测试的粒子基础大小函数并在渲染状态中调用。
- Modify `app/src/test/java/com/lingqi/app/ui/particle/ParticleBreathingVisualTest.kt`: 增加大小边界连续与单调性测试。
- Preserve, do not stage with this feature: `app/src/androidTest/java/com/lingqi/app/data/LingqiDatabaseEncryptionMigrationTest.kt`. 这是已验证的 Android 16 SQLCipher 测试兼容修正，后续另行提交。
- Do not touch `dist/`, `tmp-*.png`, 旧 Web 文件、呼吸 4/7/8 计时、粒子坐标、随机种子或聚散范围。

### Task 1: Replace sweep cues with deterministic clear-tone synthesis

**Files:**
- Create: `app/src/main/java/com/lingqi/app/meditation/CueSynthesis.kt`
- Modify: `app/src/main/java/com/lingqi/app/meditation/CuePlayer.kt`
- Create: `app/src/test/java/com/lingqi/app/meditation/CueSynthesisTest.kt`
- Delete: `app/src/test/java/com/lingqi/app/meditation/CuePlayerTest.kt`

- [ ] **Step 1: Write the failing synthesis tests**

Create `CueSynthesisTest.kt`:

```kotlin
package com.lingqi.app.meditation

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CueSynthesisTest {
    @Test
    fun cueSpecsMatchApprovedFrequenciesAndLevels() {
        assertEquals(1_400.0, DI_CUE.frequencyHz, 0.0)
        assertEquals(0.55, DI_CUE.volume, 0.0)
        assertEquals(880.0, TA_CUE.frequencyHz, 0.0)
        assertEquals(0.50, TA_CUE.volume, 0.0)
        listOf(DI_CUE, TA_CUE).forEach { spec ->
            assertEquals(0.18, spec.durationSeconds, 0.0)
            assertEquals(0.005, spec.attackSeconds, 0.0)
            assertEquals(0.18, spec.harmonicRatio, 0.0)
        }
    }

    @Test
    fun envelopeHasFiveMillisecondAttackAndCubicDecay() {
        assertEquals(0.0, cueEnvelope(0.0, 0.18, 0.005), 1e-9)
        assertEquals(0.5, cueEnvelope(0.0025, 0.18, 0.005), 1e-9)
        assertEquals(1.0, cueEnvelope(0.005, 0.18, 0.005), 1e-9)
        assertEquals(0.125, cueEnvelope(0.0925, 0.18, 0.005), 1e-9)
        assertEquals(0.0, cueEnvelope(0.18, 0.18, 0.005), 1e-9)
    }

    @Test
    fun waveUsesEighteenPercentSecondHarmonicWithoutSweep() {
        val phase = PI / 4.0
        val expected = (sin(phase) + 0.18 * sin(phase * 2.0)) / 1.18
        assertEquals(expected, cueWave(phase, 0.18), 1e-9)
        assertEquals(cueWave(phase, 0.18), cueWave(phase + 2.0 * PI, 0.18), 1e-9)
    }

    @Test
    fun generatedCuesHaveApprovedDurationSafePeakAndSilentEdges() {
        listOf(DI_CUE, TA_CUE).forEach { spec ->
            val samples = generateCueSamples(spec)
            val peak = samples.maxOf { abs(it.toInt()) }
            assertEquals(7_938, samples.size)
            assertEquals(0, samples.first().toInt())
            assertEquals(0, samples.last().toInt())
            assertTrue(peak > 8_000)
            assertTrue(peak <= (spec.volume * Short.MAX_VALUE).roundToInt())
        }
    }
}
```

Delete `CuePlayerTest.kt` so no test retains the obsolete sweep API.

- [ ] **Step 2: Run the focused test and verify it fails**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.lingqi.app.meditation.CueSynthesisTest
```

Expected: `FAILED` with unresolved references such as `DI_CUE`, `cueEnvelope`, or `cueWave`.

- [ ] **Step 3: Add the minimal pure Kotlin synthesis implementation**

Create `CueSynthesis.kt`:

```kotlin
package com.lingqi.app.meditation

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

internal data class CueToneSpec(
    val frequencyHz: Double,
    val volume: Double,
    val durationSeconds: Double = 0.18,
    val attackSeconds: Double = 0.005,
    val harmonicRatio: Double = 0.18
)

internal val DI_CUE = CueToneSpec(frequencyHz = 1_400.0, volume = 0.55)
internal val TA_CUE = CueToneSpec(frequencyHz = 880.0, volume = 0.50)

internal fun cueEnvelope(elapsedSeconds: Double, durationSeconds: Double, attackSeconds: Double): Double {
    if (elapsedSeconds <= 0.0 || elapsedSeconds >= durationSeconds) return 0.0
    if (elapsedSeconds < attackSeconds) return elapsedSeconds / attackSeconds
    val u = (elapsedSeconds - attackSeconds) / (durationSeconds - attackSeconds)
    return (1.0 - u).coerceIn(0.0, 1.0).pow(3)
}

internal fun cueWave(phase: Double, harmonicRatio: Double): Double =
    (sin(phase) + harmonicRatio * sin(phase * 2.0)) / (1.0 + harmonicRatio)

internal fun generateCueSamples(spec: CueToneSpec, sampleRate: Int = 44_100): ShortArray {
    val count = (sampleRate * spec.durationSeconds).roundToInt()
    return ShortArray(count) { index ->
        if (index == count - 1) return@ShortArray 0
        val elapsed = index.toDouble() / sampleRate
        val phase = 2.0 * PI * spec.frequencyHz * elapsed
        val value = cueWave(phase, spec.harmonicRatio) *
            cueEnvelope(elapsed, spec.durationSeconds, spec.attackSeconds) *
            spec.volume * Short.MAX_VALUE
        value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
```

Modify `CuePlayer.kt` to remove the old sweep generator and use the fixed specs:

```kotlin
fun playDi() = playTone(DI_CUE)
fun playTa() = playTone(TA_CUE)

private fun playTone(spec: CueToneSpec) {
    thread(name = "lingqi-cue", isDaemon = true) {
        val sampleRate = 44_100
        val samples = generateCueSamples(spec, sampleRate)
        var track: AudioTrack? = null
        try {
            track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(samples.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            activeTracks.add(track)
            track.write(samples, 0, samples.size)
            track.play()
            Thread.sleep((spec.durationSeconds * 1_000).toLong() + 50)
        } catch (_: Exception) {
            // A cue must never interrupt the meditation session.
        } finally {
            track?.let {
                activeTracks.remove(it)
                runCatching { it.release() }
            }
        }
    }
}
```

Keep `stopAll()`, `USAGE_MEDIA`, mono PCM and exception handling unchanged. Remove `playSweep(...)` and the old top-level sweep generator.

- [ ] **Step 4: Run the focused meditation tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests "com.lingqi.app.meditation.*"
```

Expected: `BUILD SUCCESSFUL`; all meditation unit tests pass.

- [ ] **Step 5: Commit only the cue synthesis change**

```powershell
git add -- app/src/main/java/com/lingqi/app/meditation/CuePlayer.kt app/src/main/java/com/lingqi/app/meditation/CueSynthesis.kt app/src/test/java/com/lingqi/app/meditation/CuePlayerTest.kt app/src/test/java/com/lingqi/app/meditation/CueSynthesisTest.kt
git diff --cached --check
git commit -m "Improve meditation cue clarity"
```

Expected: only the four cue-related paths are committed; the SQLCipher androidTest file remains unstaged.

### Task 2: Make particle base size continuous across the full 478 cycle

**Files:**
- Modify: `app/src/main/java/com/lingqi/app/ui/particle/LingqiParticleView.kt`
- Modify: `app/src/test/java/com/lingqi/app/ui/particle/ParticleBreathingVisualTest.kt`

- [ ] **Step 1: Add failing boundary and monotonicity tests**

Append to `ParticleBreathingVisualTest.kt` and add `import org.junit.Assert.assertTrue`:

```kotlin
@Test
fun phaseBoundariesHaveContinuousBaseSize() {
    val inhaleEnd = particleBaseSize(BreathState(BreathPhase.INHALE, 1f), true)
    val holdStart = particleBaseSize(BreathState(BreathPhase.HOLD, 0f), true)
    val holdEnd = particleBaseSize(BreathState(BreathPhase.HOLD, 1f), true)
    val exhaleStart = particleBaseSize(BreathState(BreathPhase.EXHALE, 0f), true)
    val exhaleEnd = particleBaseSize(BreathState(BreathPhase.EXHALE, 1f), true)
    val nextInhale = particleBaseSize(BreathState(BreathPhase.INHALE, 0f), true)

    assertEquals(76f, inhaleEnd, 0.0001f)
    assertEquals(inhaleEnd, holdStart, 0.0001f)
    assertEquals(holdEnd, exhaleStart, 0.0001f)
    assertEquals(58f, exhaleEnd, 0.0001f)
    assertEquals(exhaleEnd, nextInhale, 0.0001f)
}

@Test
fun baseSizeChangesMonotonicallyWithinMovingPhases() {
    val inhale = (0..100).map { step ->
        particleBaseSize(BreathState(BreathPhase.INHALE, step / 100f), true)
    }
    val exhale = (0..100).map { step ->
        particleBaseSize(BreathState(BreathPhase.EXHALE, step / 100f), true)
    }

    assertTrue(inhale.zipWithNext().all { (left, right) -> right >= left })
    assertTrue(exhale.zipWithNext().all { (left, right) -> right <= left })
    assertEquals(58f, particleBaseSize(null, true), 0.0001f)
    assertEquals(58f, particleBaseSize(BreathState(BreathPhase.HOLD, 0f), false), 0.0001f)
}
```

- [ ] **Step 2: Run the focused visual test and verify it fails**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.lingqi.app.ui.particle.ParticleBreathingVisualTest
```

Expected: `FAILED` because `particleBaseSize` does not exist.

- [ ] **Step 3: Implement the minimal continuous size function**

Add beside `particleSpread` in `LingqiParticleView.kt`:

```kotlin
internal fun particleBaseSize(state: BreathState?, active: Boolean): Float {
    if (!active || state == null) return 58f
    return when (state.phase) {
        BreathPhase.INHALE -> {
            val progress = state.progress
            val smooth = progress * progress * (3f - 2f * progress)
            58f + smooth * 18f
        }
        BreathPhase.HOLD -> 76f
        BreathPhase.EXHALE -> {
            val release = (0.5 - cos(PI * state.progress) / 2.0).toFloat()
            76f - release * 18f
        }
    }
}
```

Replace `visualState(...)` with:

```kotlin
private fun visualState(state: BreathState?): VisualState {
    val motionEnabled = !active || state == null || state.phase == BreathPhase.INHALE
    return VisualState(
        spread = particleSpread(state, active),
        size = particleBaseSize(state, active),
        motionEnabled = motionEnabled
    )
}
```

Do not change `particleSpread`, shaders, particle coordinates, particle count, random seed, timing, rotation or camera values.

- [ ] **Step 4: Run focused visual tests**

```powershell
.\gradlew.bat testDebugUnitTest --tests com.lingqi.app.ui.particle.ParticleBreathingVisualTest
```

Expected: `BUILD SUCCESSFUL`; spread and base-size boundary tests pass.

- [ ] **Step 5: Commit only the visual continuity change**

```powershell
git add -- app/src/main/java/com/lingqi/app/ui/particle/LingqiParticleView.kt app/src/test/java/com/lingqi/app/ui/particle/ParticleBreathingVisualTest.kt
git diff --cached --check
git commit -m "Smooth 478 particle size transition"
```

Expected: only the two particle files are committed; the SQLCipher androidTest file remains unstaged.

### Task 3: Run desktop regression and package checks

**Files:**
- Verify only; no planned production edits.

- [ ] **Step 1: Run all JVM tests**

```powershell
.\gradlew.bat testDebugUnitTest
```

Expected: `BUILD SUCCESSFUL`; all JVM tests pass.

- [ ] **Step 2: Build Debug, instrumentation APK and signed Release APK**

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest assembleRelease
```

Expected: `BUILD SUCCESSFUL`; APKs exist under `C:\Users\Administrator\.lingqi-build\app\outputs\apk\`.

- [ ] **Step 3: Run Android lint**

```powershell
.\gradlew.bat lintDebug
```

Expected: `BUILD SUCCESSFUL` with no blocking lint errors.

- [ ] **Step 4: Verify repository boundaries**

```powershell
git diff --check
git status --short
```

Expected: no whitespace errors; no `dist/`, `tmp-*.png`, capture or old Web files are staged. The only unrelated tracked modification is the SQLCipher androidTest compatibility fix.

### Task 4: Install, listen and verify one complete cycle on the released phone

**Files:**
- Verify artifact: `C:\Users\Administrator\.lingqi-build\app\outputs\apk\debug\app-debug.apk`
- Capture locally, ignored by Git: `captures/native/lingqi-478-clear-cue.mp4`

- [ ] **Step 1: Announce the manual action before using the phone**

Send this exact visible message:

```text
需要你操作：我将重新安装灵栖测试包。安装后请打开“冥想 → 478 呼吸 → 开始”，保持本次复现时的媒体音量，听一轮“滴/嗒”。
```

- [ ] **Step 2: Reinstall without deleting app data**

```powershell
adb start-server
adb devices -l
adb install -r "C:\Users\Administrator\.lingqi-build\app\outputs\apk\debug\app-debug.apk"
```

Expected: the known phone is `device` and install returns `Success`. Do not uninstall, clear data, inject input, or request Xiaomi dangerous USB security settings.

- [ ] **Step 3: Perform user listening acceptance**

Ask the user to confirm:

- “滴”在每轮收缩开始时只响一次，清脆且足够清晰。
- “嗒”在每轮散开开始时只响一次，不闷、不破音、无奇怪尾音。
- 提示时点仍对应 4 秒收缩、7 秒停留、8 秒散开。

If the user requests tonal parameter changes, stop and obtain explicit confirmation of the new frequency, amplitude, harmonic or envelope values before editing because the approved spec fixes these values.

- [ ] **Step 4: Record and inspect a full cycle**

After the user starts 478, run:

```powershell
adb shell screenrecord --time-limit 24 /sdcard/lingqi-478-clear-cue.mp4
adb pull /sdcard/lingqi-478-clear-cue.mp4 captures/native/lingqi-478-clear-cue.mp4
```

Expected: recording covers at least one 19-second cycle. Inspect the exhale-to-next-inhale boundary frame by frame and confirm no point-size jump; particle coordinates, silhouette and spread path otherwise match the current appearance.

- [ ] **Step 5: Release the phone and report it visibly**

```powershell
adb kill-server
```

Send: `手机已释放。`

- [ ] **Step 6: Push only after acceptance**

```powershell
git status --short --branch
git push origin main
```

Expected: `main` is synchronized with `origin/main`; old Web outputs and captures remain untracked or ignored and uncommitted.
