package com.lingqi.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BreathingCueSoundTest {
    @Test
    fun missingOrUnknownStorageValueDefaultsToPendulum() {
        assertEquals(BreathingCueSound.PENDULUM, BreathingCueSound.fromStorage(null))
        assertEquals(BreathingCueSound.PENDULUM, BreathingCueSound.fromStorage("future-value"))
    }

    @Test
    fun everyChoiceRoundTripsThroughStableStorageValue() {
        BreathingCueSound.entries.forEach { choice ->
            assertEquals(choice, BreathingCueSound.fromStorage(choice.storageValue))
        }
    }
}
