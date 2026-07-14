package com.lingqi.app.meditation

import com.lingqi.app.data.BreathingCueSound

enum class CuePlayback {
    RECORDED,
    SYNTHESIZED,
    NONE
}

enum class CueAction {
    RECORDED_TICK,
    RECORDED_TOCK,
    SYNTHESIZED_DI,
    SYNTHESIZED_TA,
    NONE
}

fun cuePlayback(sound: BreathingCueSound): CuePlayback = when (sound) {
    BreathingCueSound.PENDULUM -> CuePlayback.RECORDED
    BreathingCueSound.BELL -> CuePlayback.SYNTHESIZED
    BreathingCueSound.SILENT -> CuePlayback.NONE
}

fun cueAction(
    sound: BreathingCueSound,
    phase: BreathPhase,
    recordedReady: Boolean
): CueAction = when {
    sound == BreathingCueSound.SILENT || phase == BreathPhase.HOLD -> CueAction.NONE
    sound == BreathingCueSound.PENDULUM && recordedReady && phase == BreathPhase.INHALE ->
        CueAction.RECORDED_TICK
    sound == BreathingCueSound.PENDULUM && recordedReady && phase == BreathPhase.EXHALE ->
        CueAction.RECORDED_TOCK
    phase == BreathPhase.INHALE -> CueAction.SYNTHESIZED_DI
    else -> CueAction.SYNTHESIZED_TA
}

fun shouldPlayBreathingCue(
    playbackBlocked: Boolean,
    completed: Boolean,
    sessionMuted: Boolean,
    sound: BreathingCueSound
): Boolean = !playbackBlocked &&
    !completed &&
    !sessionMuted &&
    sound != BreathingCueSound.SILENT

fun shouldPlayGuidedAudio(
    soundEnabled: Boolean,
    playbackBlocked: Boolean,
    sessionMuted: Boolean
): Boolean = soundEnabled && !playbackBlocked && !sessionMuted
