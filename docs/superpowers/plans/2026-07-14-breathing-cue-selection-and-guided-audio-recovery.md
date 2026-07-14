# Breathing Cue Selection and Guided Audio Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a default recorded pendulum breathing cue with user-selectable bell/silent alternatives, and restore audible TTS plus ambient music in the five guided meditations.

**Architecture:** Extend the existing JSON preferences with a stable breathing-cue enum, isolate audio-routing decisions in pure Kotlin policies, and keep Android playback adapters responsible only for SoundPool, AudioTrack, MediaPlayer, and TextToSpeech lifecycles. The Profile screen persists the long-term choices, while the player combines them with a session-only master mute and playback-block state.

**Tech Stack:** Kotlin 2.2, Jetpack Compose Material 3, Android SoundPool/AudioTrack/MediaPlayer/TextToSpeech, JUnit 4, Gradle Android plugin, FFmpeg for offline asset derivation.

---

## File map

- Modify `app/src/main/java/com/lingqi/app/data/Models.kt`: define `BreathingCueSound` and add it to `UserPreferences`.
- Modify `app/src/main/java/com/lingqi/app/data/LingqiRepository.kt`: backward-compatible preference JSON read/write.
- Create `app/src/main/java/com/lingqi/app/meditation/MeditationAudioPolicy.kt`: pure routing and gating decisions.
- Modify `app/src/main/java/com/lingqi/app/meditation/CuePlayer.kt`: preload and play recorded tick/tock, retain synthesized bell fallback.
- Modify `app/src/main/java/com/lingqi/app/meditation/GuidedVoiceController.kt`: explicit TTS state, language validation, first-cue queue, utterance errors.
- Modify `app/src/main/java/com/lingqi/app/meditation/AmbientAudioPlayer.kt`: remove the 10% gain cap and expose creation/playback failure.
- Modify `app/src/main/java/com/lingqi/app/ui/screens/ProfileScreen.kt`: add the three-choice dialog and clarify the voice/ambient switch.
- Modify `app/src/main/java/com/lingqi/app/ui/screens/MeditationPlayerScreen.kt`: apply independent preferences, session master mute, and non-blocking TTS warning.
- Modify `app/src/main/AndroidManifest.xml`: declare TTS service visibility.
- Create `app/src/main/res/raw/pendulum_tick.wav` and `app/src/main/res/raw/pendulum_tock.wav`: short recorded cues derived from the approved official source.
- Modify `app/src/main/assets/audio/licenses.json` and `app/src/main/assets/audio/README.md`: record source, license, hashes, and processing.
- Create/modify unit and instrumentation tests named in the tasks below.

### Task 1: Preference model and backward compatibility

**Files:**
- Modify: `app/src/main/java/com/lingqi/app/data/Models.kt`
- Modify: `app/src/main/java/com/lingqi/app/data/LingqiRepository.kt`
- Create: `app/src/test/java/com/lingqi/app/data/BreathingCueSoundTest.kt`

- [ ] **Step 1: Write the failing enum compatibility test**

```kotlin
class BreathingCueSoundTest {
    @Test fun missingOrUnknownStorageValueDefaultsToPendulum() {
        assertEquals(BreathingCueSound.PENDULUM, BreathingCueSound.fromStorage(null))
        assertEquals(BreathingCueSound.PENDULUM, BreathingCueSound.fromStorage("future-value"))
    }

    @Test fun everyChoiceRoundTripsThroughStableStorageValue() {
        BreathingCueSound.entries.forEach { choice ->
            assertEquals(choice, BreathingCueSound.fromStorage(choice.storageValue))
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify it fails because the enum is absent**

Run: `./gradlew testDebugUnitTest --tests com.lingqi.app.data.BreathingCueSoundTest`

Expected: compilation failure naming `BreathingCueSound`.

- [ ] **Step 3: Add the enum and preference field**

```kotlin
enum class BreathingCueSound(val storageValue: String, val label: String) {
    PENDULUM("pendulum", "摆钟咔哒"),
    BELL("bell", "清脆短铃"),
    SILENT("silent", "静音");

    companion object {
        fun fromStorage(value: String?): BreathingCueSound =
            entries.firstOrNull { it.storageValue == value } ?: PENDULUM
    }
}

data class UserPreferences(
    // existing fields remain unchanged
    val breathingCueSound: BreathingCueSound = BreathingCueSound.PENDULUM
)
```

Update repository loading with `BreathingCueSound.fromStorage(json.optString("breathingCueSound", null))` and saving with `put("breathingCueSound", value.breathingCueSound.storageValue)`.

- [ ] **Step 4: Run the focused test and the existing unit suite**

Run: `./gradlew testDebugUnitTest --tests com.lingqi.app.data.BreathingCueSoundTest`

Expected: PASS.

Run: `./gradlew testDebugUnitTest`

Expected: all tests pass.

- [ ] **Step 5: Commit only the preference change**

```powershell
git add app/src/main/java/com/lingqi/app/data/Models.kt app/src/main/java/com/lingqi/app/data/LingqiRepository.kt app/src/test/java/com/lingqi/app/data/BreathingCueSoundTest.kt
git commit -m "Add breathing cue sound preference"
```

### Task 2: Pure audio routing policy

**Files:**
- Create: `app/src/main/java/com/lingqi/app/meditation/MeditationAudioPolicy.kt`
- Create: `app/src/test/java/com/lingqi/app/meditation/MeditationAudioPolicyTest.kt`

- [ ] **Step 1: Write failing routing and gating tests**

```kotlin
class MeditationAudioPolicyTest {
    @Test fun routesAllThreeBreathingChoices() {
        assertEquals(CuePlayback.RECORDED, cuePlayback(BreathingCueSound.PENDULUM))
        assertEquals(CuePlayback.SYNTHESIZED, cuePlayback(BreathingCueSound.BELL))
        assertEquals(CuePlayback.NONE, cuePlayback(BreathingCueSound.SILENT))
    }

    @Test fun profileVoiceSwitchDoesNotMuteBreathingCue() {
        assertTrue(shouldPlayBreathingCue(false, false, false, BreathingCueSound.PENDULUM))
    }

    @Test fun sessionMuteAndBlockedStateStopEveryAudioPath() {
        assertFalse(shouldPlayBreathingCue(true, false, true, BreathingCueSound.PENDULUM))
        assertFalse(shouldPlayGuidedAudio(true, true, false))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the policy symbols are missing**

Run: `./gradlew testDebugUnitTest --tests com.lingqi.app.meditation.MeditationAudioPolicyTest`

Expected: compilation failure for `CuePlayback` and policy functions.

- [ ] **Step 3: Implement the minimal pure policy**

```kotlin
enum class CuePlayback { RECORDED, SYNTHESIZED, NONE }

fun cuePlayback(sound: BreathingCueSound): CuePlayback = when (sound) {
    BreathingCueSound.PENDULUM -> CuePlayback.RECORDED
    BreathingCueSound.BELL -> CuePlayback.SYNTHESIZED
    BreathingCueSound.SILENT -> CuePlayback.NONE
}

fun shouldPlayBreathingCue(
    playbackBlocked: Boolean,
    completed: Boolean,
    sessionMuted: Boolean,
    sound: BreathingCueSound
): Boolean = !playbackBlocked && !completed && !sessionMuted && sound != BreathingCueSound.SILENT

fun shouldPlayGuidedAudio(
    soundEnabled: Boolean,
    playbackBlocked: Boolean,
    sessionMuted: Boolean
): Boolean = soundEnabled && !playbackBlocked && !sessionMuted
```

- [ ] **Step 4: Run focused and full unit tests**

Expected: all policy tests and the complete unit suite pass.

- [ ] **Step 5: Commit the policy**

```powershell
git add app/src/main/java/com/lingqi/app/meditation/MeditationAudioPolicy.kt app/src/test/java/com/lingqi/app/meditation/MeditationAudioPolicyTest.kt
git commit -m "Define meditation audio routing policy"
```

### Task 3: Approved pendulum asset and license evidence

**Files:**
- Create: `app/src/main/res/raw/pendulum_tick.wav`
- Create: `app/src/main/res/raw/pendulum_tock.wav`
- Modify: `app/src/main/assets/audio/licenses.json`
- Modify: `app/src/main/assets/audio/README.md`
- Create: `app/src/test/java/com/lingqi/app/meditation/PendulumAssetContractTest.kt`

- [ ] **Step 1: Obtain the original only from the approved official page**

Open `https://pixabay.com/sound-effects/film-special-effects-old-clock-ticking-352288/`, use the official download control, and save the response outside the repository under `.codex/tmp/lingqi-audio/old-clock-ticking-original.*`. Record its SHA-256 before processing. Do not use mirrors, search-result downloads, or third-party copies.

- [ ] **Step 2: Inspect waveform/transients and derive two short WAV resources**

Use the bundled FFmpeg/ffprobe waveform output to locate a natural neighboring tick and tock. Store the measured decimal-second positions in `$tickStart` and `$tockStart`, then run:

```powershell
ffmpeg -ss $tickStart -t 0.30 -i old-clock-ticking-original.* -ac 1 -ar 44100 -af "highpass=f=35,adeclick,afade=t=in:st=0:d=0.003,areverse,afade=t=in:st=0:d=0.008,areverse,alimiter=limit=0.891" pendulum_tick.wav
ffmpeg -ss $tockStart -t 0.30 -i old-clock-ticking-original.* -ac 1 -ar 44100 -af "highpass=f=35,adeclick,afade=t=in:st=0:d=0.003,areverse,afade=t=in:st=0:d=0.008,areverse,alimiter=limit=0.891" pendulum_tock.wav
```

Write the measured `$tickStart` and `$tockStart` values into the license record; they are properties of the downloaded waveform and must be measured before derivation.

- [ ] **Step 3: Write the asset contract test before copying the resources**

The test opens each repository WAV, validates RIFF/WAVE headers, mono channel count, 44,100 Hz sample rate, duration at most 300 ms, non-zero audio, and a peak below full-scale. It also verifies that `licenses.json` contains the official URL, author `Universfield`, original hash, derived hashes, and trim timestamps.

- [ ] **Step 4: Copy only the two derived resources into `res/raw` and update license metadata**

The long original remains outside Git and APK. Update the README to state that 478 defaults to recorded pendulum cues and that the existing synthesized bell remains selectable.

- [ ] **Step 5: Run the asset contract test and commit**

Run: `./gradlew testDebugUnitTest --tests com.lingqi.app.meditation.PendulumAssetContractTest`

Expected: PASS with both resource hashes matching the JSON record.

```powershell
git add app/src/main/res/raw/pendulum_tick.wav app/src/main/res/raw/pendulum_tock.wav app/src/main/assets/audio/licenses.json app/src/main/assets/audio/README.md app/src/test/java/com/lingqi/app/meditation/PendulumAssetContractTest.kt
git commit -m "Bundle licensed pendulum breathing cues"
```

### Task 4: Recorded cue playback with synthesized fallback

**Files:**
- Modify: `app/src/main/java/com/lingqi/app/meditation/CuePlayer.kt`
- Modify: `app/src/main/java/com/lingqi/app/ui/screens/MeditationPlayerScreen.kt`
- Modify: `app/src/test/java/com/lingqi/app/meditation/MeditationAudioPolicyTest.kt`

- [ ] **Step 1: Add failing tests for recorded-load fallback and phase mapping**

Add pure assertions that inhale maps to recorded tick, exhale maps to recorded tock, hold maps to none, and an unloaded recorded resource maps to the corresponding synthesized bell fallback.

- [ ] **Step 2: Run the focused test and verify the new mapping is absent**

Expected: compilation failure for `RecordedCue.TICK`, `RecordedCue.TOCK`, or the fallback decision.

- [ ] **Step 3: Extend `CuePlayer` with application context and SoundPool**

Construct `CuePlayer(context)` once per meditation page. Preload both raw resources with `USAGE_MEDIA`; track each load result with `setOnLoadCompleteListener`. Route `PENDULUM` to SoundPool when loaded, otherwise call the existing `playTone(DI_CUE/TA_CUE)`; route `BELL` directly to synthesis and `SILENT` to no action. `stopAll()` stops active SoundPool streams and AudioTracks; a new `release()` also releases SoundPool on page disposal.

- [ ] **Step 4: Wire independent breathing preference into the player**

Read `val initialPreferences = repository.preferences()` once, keep `breathingCueSound` separate from the session master mute, and trigger cues only through `shouldPlayBreathingCue`. Do not let profile `soundEnabled` suppress 478 cues.

- [ ] **Step 5: Run focused and full unit tests, then commit**

```powershell
git add app/src/main/java/com/lingqi/app/meditation/CuePlayer.kt app/src/main/java/com/lingqi/app/ui/screens/MeditationPlayerScreen.kt app/src/test/java/com/lingqi/app/meditation/MeditationAudioPolicyTest.kt
git commit -m "Play selectable recorded breathing cues"
```

### Task 5: Profile sound choice UI

**Files:**
- Modify: `app/src/main/java/com/lingqi/app/ui/screens/ProfileScreen.kt`
- Create: `app/src/androidTest/java/com/lingqi/app/ui/ProfileBreathingCueTest.kt`

- [ ] **Step 1: Write a failing Compose test for visible choices and immediate persistence behavior**

The test renders the extracted selection dialog with `PENDULUM`, opens the row, asserts all three Chinese labels, taps “静音”, and verifies the callback receives `SILENT` once.

- [ ] **Step 2: Run the instrumentation compile and verify the dialog composable is absent**

Run: `./gradlew assembleDebugAndroidTest`

Expected: test compilation failure for `BreathingCueSoundDialog`.

- [ ] **Step 3: Implement the row and minimal black/white single-choice dialog**

Add local `breathingCueDialog` state. The row title is “呼吸提示音”, its subtitle is `preferences.breathingCueSound.label`, and selecting a value calls `updatePreferences(preferences.copy(breathingCueSound = value))` before closing. Rename the existing row to “语音与环境声” with subtitle “用于引导冥想”; its switch still updates only `soundEnabled`.

- [ ] **Step 4: Build instrumentation tests and commit**

Run: `./gradlew assembleDebugAndroidTest`

Expected: BUILD SUCCESSFUL.

```powershell
git add app/src/main/java/com/lingqi/app/ui/screens/ProfileScreen.kt app/src/androidTest/java/com/lingqi/app/ui/ProfileBreathingCueTest.kt
git commit -m "Add breathing cue selector to profile"
```

### Task 6: TTS visibility, state, and first-cue reliability

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/lingqi/app/meditation/GuidedVoiceController.kt`
- Modify: `app/src/main/java/com/lingqi/app/ui/screens/MeditationPlayerScreen.kt`
- Create: `app/src/main/java/com/lingqi/app/meditation/GuidedVoiceState.kt`
- Create: `app/src/test/java/com/lingqi/app/meditation/GuidedVoiceStateTest.kt`
- Modify: `app/src/androidTest/java/com/lingqi/app/wellness/WellnessManifestContractTest.kt`

- [ ] **Step 1: Write failing pure state tests**

```kotlin
class GuidedVoiceStateTest {
    @Test fun firstCueWaitsDuringInitializationAndPlaysOnceWhenReady() {
        val state = GuidedVoiceState()
        state.enqueueWhileInitializing("第一句")
        assertEquals("第一句", state.markReady())
        assertNull(state.markReady())
    }

    @Test fun unavailableClearsPendingCue() {
        val state = GuidedVoiceState()
        state.enqueueWhileInitializing("第一句")
        state.markUnavailable()
        assertNull(state.markReady())
    }
}
```

Extend the manifest contract test to require `android.intent.action.TTS_SERVICE` inside `<queries>` while preserving `app.linglian.mobile` and the wellness Provider contract.

- [ ] **Step 2: Run focused unit and AndroidTest builds to observe failures**

Expected: missing `GuidedVoiceState` and missing TTS manifest query.

- [ ] **Step 3: Implement explicit TTS state and callbacks**

Add `INITIALIZING`, `READY`, and `UNAVAILABLE` state. On successful engine initialization, call `setLanguage(Locale.SIMPLIFIED_CHINESE)` and accept only non-negative Android language availability codes. Register an `UtteranceProgressListener`; treat `speak()` results other than `TextToSpeech.SUCCESS` and utterance errors as unavailable callbacks. Keep only the latest pending initialization cue and play it once on readiness.

- [ ] **Step 4: Add the manifest query and non-blocking UI warning**

Inside the existing `<queries>`, add:

```xml
<intent>
    <action android:name="android.intent.action.TTS_SERVICE" />
</intent>
```

Pass an unavailable callback to the player and show “中文语音暂不可用，请检查系统文字转语音设置” as a small non-modal text notice without stopping the timer.

- [ ] **Step 5: Run tests and commit**

Run focused unit tests, `testDebugUnitTest`, and `assembleDebugAndroidTest`; all must pass.

```powershell
git add app/src/main/AndroidManifest.xml app/src/main/java/com/lingqi/app/meditation/GuidedVoiceController.kt app/src/main/java/com/lingqi/app/meditation/GuidedVoiceState.kt app/src/main/java/com/lingqi/app/ui/screens/MeditationPlayerScreen.kt app/src/test/java/com/lingqi/app/meditation/GuidedVoiceStateTest.kt app/src/androidTest/java/com/lingqi/app/wellness/WellnessManifestContractTest.kt
git commit -m "Restore reliable Chinese meditation guidance"
```

### Task 7: Ambient playback audibility and independent failure handling

**Files:**
- Modify: `app/src/main/java/com/lingqi/app/meditation/AmbientAudioPlayer.kt`
- Modify: `app/src/main/java/com/lingqi/app/ui/screens/MeditationPlayerScreen.kt`
- Modify: `app/src/test/java/com/lingqi/app/meditation/AmbientPlaybackPolicyTest.kt`

- [ ] **Step 1: Add a failing contract for full player gain**

Extract `AMBIENT_PLAYER_GAIN = 1.0f` as an internal constant and assert it equals `1.0f`; retain existing tests that 478 never plays ambient audio and guided sessions obey playback state.

- [ ] **Step 2: Run the focused test and verify the inaccessible or incorrect 0.10 gain fails**

Run: `./gradlew testDebugUnitTest --tests com.lingqi.app.meditation.AmbientPlaybackPolicyTest`

Expected: failure because current gain is private and `0.10f`.

- [ ] **Step 3: Remove fixed attenuation and report failures without coupling TTS**

Set both MediaPlayer channels to `AMBIENT_PLAYER_GAIN`. Change `setPlaying` to return a result or invoke an ambient-only error callback when asset creation/start fails. Do not call `voice.setEnabled(false)` from ambient failures.

- [ ] **Step 4: Run focused and full unit tests, then commit**

```powershell
git add app/src/main/java/com/lingqi/app/meditation/AmbientAudioPlayer.kt app/src/main/java/com/lingqi/app/ui/screens/MeditationPlayerScreen.kt app/src/test/java/com/lingqi/app/meditation/AmbientPlaybackPolicyTest.kt
git commit -m "Restore guided meditation background audio"
```

### Task 8: Desktop verification and deferred device checklist

**Files:**
- Modify only if verification exposes a defect in files already in this plan.

- [ ] **Step 1: Run the complete desktop verification set**

```powershell
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug assembleRelease
git diff --check
```

Expected: every Gradle task reports BUILD SUCCESSFUL, signed Release output exists, and `git diff --check` prints no errors.

- [ ] **Step 2: Inspect packaged artifacts**

Verify Debug and Release APKs contain both pendulum WAV files, the ambient MP3, updated license JSON, and the TTS manifest query. Verify the Release certificate matches the configured Ling-family signing certificate without printing passwords or private-key material.

- [ ] **Step 3: Review Git scope**

Confirm only Android/Gradle/docs/required audio resources are tracked. Preserve `hatch-pet-nightwatch/`, `交付/`, old Web files, and unrelated user changes.

- [ ] **Step 4: Defer all phone work until 2026-07-15**

Do not start ADB on 2026-07-14. Tomorrow, after a visible “现在开始占用手机” message, install the latest Debug main and `-t` APKs, run instrumentation, test all three cue choices, sample all five guided meditations, use hardware media-volume controls, and verify pause/mute/resume. End with a visible “手机已释放” message and stop ADB.

- [ ] **Step 5: Final desktop commit if verification required corrections**

Stage only the corrected in-scope files, rerun the full verification set, and commit with a message naming the verified correction. Do not create an empty commit.
