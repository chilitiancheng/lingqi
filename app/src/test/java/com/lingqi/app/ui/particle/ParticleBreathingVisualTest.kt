package com.lingqi.app.ui.particle

import com.lingqi.app.meditation.BreathPhase
import com.lingqi.app.meditation.BreathState
import org.junit.Assert.assertEquals
import org.junit.Test

class ParticleBreathingVisualTest {
    @Test
    fun phaseBoundariesHaveContinuousSpread() {
        val inhaleEnd = particleSpread(BreathState(BreathPhase.INHALE, 1f), true)
        val holdStart = particleSpread(BreathState(BreathPhase.HOLD, 0f), true)
        val holdEnd = particleSpread(BreathState(BreathPhase.HOLD, 1f), true)
        val exhaleStart = particleSpread(BreathState(BreathPhase.EXHALE, 0f), true)
        val exhaleEnd = particleSpread(BreathState(BreathPhase.EXHALE, 1f), true)
        val nextInhale = particleSpread(BreathState(BreathPhase.INHALE, 0f), true)

        assertEquals(inhaleEnd, holdStart, 0.0001f)
        assertEquals(holdEnd, exhaleStart, 0.0001f)
        assertEquals(exhaleEnd, nextInhale, 0.0001f)
    }
}
