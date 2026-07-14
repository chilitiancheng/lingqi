package com.lingqi.app.meditation

import com.lingqi.app.data.BreathingCueSound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeditationAudioPolicyTest {
    @Test
    fun routesAllThreeBreathingChoices() {
        assertEquals(CuePlayback.RECORDED, cuePlayback(BreathingCueSound.PENDULUM))
        assertEquals(CuePlayback.SYNTHESIZED, cuePlayback(BreathingCueSound.BELL))
        assertEquals(CuePlayback.NONE, cuePlayback(BreathingCueSound.SILENT))
    }

    @Test
    fun profileVoiceSwitchDoesNotMuteBreathingCue() {
        assertTrue(
            shouldPlayBreathingCue(
                playbackBlocked = false,
                completed = false,
                sessionMuted = false,
                sound = BreathingCueSound.PENDULUM
            )
        )
    }

    @Test
    fun sessionMuteAndBlockedStateStopEveryAudioPath() {
        assertFalse(
            shouldPlayBreathingCue(
                playbackBlocked = true,
                completed = false,
                sessionMuted = false,
                sound = BreathingCueSound.PENDULUM
            )
        )
        assertFalse(
            shouldPlayBreathingCue(
                playbackBlocked = false,
                completed = false,
                sessionMuted = true,
                sound = BreathingCueSound.PENDULUM
            )
        )
        assertFalse(
            shouldPlayGuidedAudio(
                soundEnabled = true,
                playbackBlocked = false,
                sessionMuted = true
            )
        )
        assertFalse(
            shouldPlayGuidedAudio(
                soundEnabled = true,
                playbackBlocked = true,
                sessionMuted = false
            )
        )
    }
}
