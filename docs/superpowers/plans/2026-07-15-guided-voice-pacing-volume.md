# Guided Voice Pacing and Volume Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将冥想中文引导人声调整为 0.72 倍速、65% 音量，同时不影响环境音和滴嗒提示音。

**Architecture:** 用独立的纯 Kotlin 配置对象承载 TTS 人声参数，控制器仅消费该配置。初始化阶段应用语速和音调，播报阶段用 Android TTS 的逐句音量参数应用 65% 音量。

**Tech Stack:** Kotlin, Android TextToSpeech, JUnit 4, Gradle

---

### Task 1: 锁定引导人声默认配置

**Files:**
- Create: `app/src/main/java/com/lingqi/app/meditation/GuidedVoiceProfile.kt`
- Create: `app/src/test/java/com/lingqi/app/meditation/GuidedVoiceProfileTest.kt`

- [ ] **Step 1: 写默认值失败测试**

```kotlin
@Test
fun defaultProfileUsesApprovedPacingAndVolume() {
    assertEquals(0.72f, DEFAULT_GUIDED_VOICE_PROFILE.speechRate)
    assertEquals(0.92f, DEFAULT_GUIDED_VOICE_PROFILE.pitch)
    assertEquals(0.65f, DEFAULT_GUIDED_VOICE_PROFILE.volume)
}
```

- [ ] **Step 2: 运行测试并确认因配置对象尚不存在而失败**

Run: `./gradlew testDebugUnitTest --tests com.lingqi.app.meditation.GuidedVoiceProfileTest`

Expected: `GuidedVoiceProfile` 或 `DEFAULT_GUIDED_VOICE_PROFILE` 未解析。

- [ ] **Step 3: 实现最小配置对象与边界检查**

```kotlin
internal data class GuidedVoiceProfile(
    val speechRate: Float,
    val pitch: Float,
    val volume: Float
) {
    init {
        require(speechRate > 0f)
        require(pitch > 0f)
        require(volume in 0f..1f)
    }
}

internal val DEFAULT_GUIDED_VOICE_PROFILE = GuidedVoiceProfile(
    speechRate = 0.72f,
    pitch = 0.92f,
    volume = 0.65f
)
```

- [ ] **Step 4: 增加非法语速、音调与音量测试并运行到通过**

Run: `./gradlew testDebugUnitTest --tests com.lingqi.app.meditation.GuidedVoiceProfileTest`

Expected: `BUILD SUCCESSFUL`。

### Task 2: 将配置应用到 Android TTS

**Files:**
- Modify: `app/src/main/java/com/lingqi/app/meditation/GuidedVoiceController.kt`

- [ ] **Step 1: 用默认配置替换控制器中的硬编码语速和音调**

```kotlin
tts.setSpeechRate(DEFAULT_GUIDED_VOICE_PROFILE.speechRate)
tts.setPitch(DEFAULT_GUIDED_VOICE_PROFILE.pitch)
```

- [ ] **Step 2: 为每次人声播报传入独立音量参数**

```kotlin
private val speechParams = Bundle().apply {
    putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, DEFAULT_GUIDED_VOICE_PROFILE.volume)
}

tts.speak(text, TextToSpeech.QUEUE_FLUSH, speechParams, "lingqi-guidance")
```

- [ ] **Step 3: 运行完整单元测试**

Run: `./gradlew testDebugUnitTest`

Expected: `BUILD SUCCESSFUL`，无失败测试。

- [ ] **Step 4: 构建 Debug APK 并检查改动范围**

Run: `./gradlew assembleDebug`

Expected: `BUILD SUCCESSFUL`，生成 `app-debug.apk`。

Run: `git diff --check`

Expected: 无输出，退出码为 0。
