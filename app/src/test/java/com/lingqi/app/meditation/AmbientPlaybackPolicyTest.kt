package com.lingqi.app.meditation

import com.lingqi.app.data.MeditationKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbientPlaybackPolicyTest {
    @Test
    fun onlyActiveSoundEnabledGuidedSessionsPlayAmbientAudio() {
        assertTrue(
            AmbientPlaybackPolicy.shouldPlay(
                kind = MeditationKind.BODY_SCAN,
                soundEnabled = true,
                playbackBlocked = false,
                completed = false
            )
        )
        assertFalse(
            AmbientPlaybackPolicy.shouldPlay(
                kind = MeditationKind.BREATH_478,
                soundEnabled = true,
                playbackBlocked = false,
                completed = false
            )
        )
        assertFalse(
            AmbientPlaybackPolicy.shouldPlay(
                kind = MeditationKind.FOCUS,
                soundEnabled = false,
                playbackBlocked = false,
                completed = false
            )
        )
        assertFalse(
            AmbientPlaybackPolicy.shouldPlay(
                kind = MeditationKind.SLEEP_RELEASE,
                soundEnabled = true,
                playbackBlocked = true,
                completed = false
            )
        )
        assertFalse(
            AmbientPlaybackPolicy.shouldPlay(
                kind = MeditationKind.MORNING_AWAKENING,
                soundEnabled = true,
                playbackBlocked = false,
                completed = true
            )
        )
    }
}
