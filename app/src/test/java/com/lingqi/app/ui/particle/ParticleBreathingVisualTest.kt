package com.lingqi.app.ui.particle

import com.lingqi.app.meditation.BreathPhase
import com.lingqi.app.meditation.BreathState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun phaseBoundariesHaveContinuousBaseSize() {
        val inhaleEnd = particleBaseSize(BreathState(BreathPhase.INHALE, 1f), true)
        val holdStart = particleBaseSize(BreathState(BreathPhase.HOLD, 0f), true)
        val holdEnd = particleBaseSize(BreathState(BreathPhase.HOLD, 1f), true)
        val exhaleStart = particleBaseSize(BreathState(BreathPhase.EXHALE, 0f), true)
        val exhaleEnd = particleBaseSize(BreathState(BreathPhase.EXHALE, 1f), true)
        val nextInhale = particleBaseSize(BreathState(BreathPhase.INHALE, 0f), true)

        assertEquals(76f, inhaleEnd, 0.0001f)
        assertEquals(inhaleEnd, holdStart, 0.0001f)
        assertEquals(holdEnd, exhaleStart, 0.0001f)
        assertEquals(58f, exhaleEnd, 0.0001f)
        assertEquals(exhaleEnd, nextInhale, 0.0001f)
    }

    @Test
    fun baseSizeChangesMonotonicallyWithinMovingPhases() {
        val inhaleSizes = (0..100).map { progress ->
            particleBaseSize(BreathState(BreathPhase.INHALE, progress / 100f), true)
        }
        val exhaleSizes = (0..100).map { progress ->
            particleBaseSize(BreathState(BreathPhase.EXHALE, progress / 100f), true)
        }

        assertTrue(inhaleSizes.zipWithNext().all { (before, after) -> after >= before })
        assertTrue(exhaleSizes.zipWithNext().all { (before, after) -> after <= before })
        assertEquals(58f, particleBaseSize(null, true), 0.0001f)
        assertEquals(
            58f,
            particleBaseSize(BreathState(BreathPhase.HOLD, 0.5f), false),
            0.0001f,
        )
    }
}
