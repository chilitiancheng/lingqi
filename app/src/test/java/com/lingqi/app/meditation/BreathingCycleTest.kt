package com.lingqi.app.meditation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathingCycleTest {
    @Test
    fun mapsExact478Boundaries() {
        assertEquals(BreathPhase.INHALE, BreathingCycle.stateAt(0.0).phase)
        assertEquals(BreathPhase.HOLD, BreathingCycle.stateAt(4.0).phase)
        assertEquals(BreathPhase.EXHALE, BreathingCycle.stateAt(11.0).phase)
        assertEquals(BreathPhase.INHALE, BreathingCycle.stateAt(19.0).phase)
    }

    @Test
    fun progressIsBoundedAndContinuousInsidePhases() {
        assertEquals(0.5f, BreathingCycle.stateAt(2.0).progress, 0.0001f)
        assertEquals(0.5f, BreathingCycle.stateAt(7.5).progress, 0.0001f)
        assertEquals(0.5f, BreathingCycle.stateAt(15.0).progress, 0.0001f)
        assertTrue(BreathingCycle.stateAt(-0.1).progress in 0f..1f)
    }
}
