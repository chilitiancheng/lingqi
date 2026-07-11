package com.lingqi.app.meditation

enum class BreathPhase(val title: String, val hint: String, val durationSeconds: Double) {
    INHALE("吸气 4 秒", "星群收束", 4.0),
    HOLD("停留 7 秒", "让光停在身体里", 7.0),
    EXHALE("呼气 8 秒", "星群散开", 8.0)
}

data class BreathState(
    val phase: BreathPhase,
    val progress: Float
)

object BreathingCycle {
    const val CYCLE_SECONDS = 19.0

    fun stateAt(elapsedSeconds: Double): BreathState {
        var cursor = ((elapsedSeconds % CYCLE_SECONDS) + CYCLE_SECONDS) % CYCLE_SECONDS
        BreathPhase.entries.forEach { phase ->
            if (cursor < phase.durationSeconds) {
                return BreathState(phase, (cursor / phase.durationSeconds).toFloat().coerceIn(0f, 1f))
            }
            cursor -= phase.durationSeconds
        }
        return BreathState(BreathPhase.INHALE, 0f)
    }
}
