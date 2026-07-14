package com.lingqi.app.meditation

import com.lingqi.app.data.BreathingCueSound

enum class CuePlayback {
    RECORDED,
    SYNTHESIZED,
    NONE
}

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
): Boolean = !playbackBlocked &&
    !completed &&
    !sessionMuted &&
    sound != BreathingCueSound.SILENT

fun shouldPlayGuidedAudio(
    soundEnabled: Boolean,
    playbackBlocked: Boolean,
    sessionMuted: Boolean
): Boolean = soundEnabled && !playbackBlocked && !sessionMuted
