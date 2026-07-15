package com.lingqi.app.meditation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GuidedVoiceProfileTest {
    @Test
    fun defaultProfileUsesApprovedPacingPitchAndVolume() {
        assertEquals(0.72f, DEFAULT_GUIDED_VOICE_PROFILE.speechRate, 0f)
        assertEquals(0.92f, DEFAULT_GUIDED_VOICE_PROFILE.pitch, 0f)
        assertEquals(0.65f, DEFAULT_GUIDED_VOICE_PROFILE.volume, 0f)
    }

    @Test
    fun speechRateMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            GuidedVoiceProfile(speechRate = 0f, pitch = 0.92f, volume = 0.65f)
        }
    }

    @Test
    fun pitchMustBePositive() {
        assertThrows(IllegalArgumentException::class.java) {
            GuidedVoiceProfile(speechRate = 0.72f, pitch = 0f, volume = 0.65f)
        }
    }

    @Test
    fun volumeMustStayWithinMediaVolumeRange() {
        assertThrows(IllegalArgumentException::class.java) {
            GuidedVoiceProfile(speechRate = 0.72f, pitch = 0.92f, volume = 1.01f)
        }
    }
}
