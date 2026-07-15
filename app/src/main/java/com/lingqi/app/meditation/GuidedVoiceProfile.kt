package com.lingqi.app.meditation

internal data class GuidedVoiceProfile(
    val speechRate: Float,
    val pitch: Float,
    val volume: Float
) {
    init {
        require(speechRate > 0f) { "Speech rate must be positive" }
        require(pitch > 0f) { "Pitch must be positive" }
        require(volume in 0f..1f) { "Volume must be between 0 and 1" }
    }
}

internal val DEFAULT_GUIDED_VOICE_PROFILE = GuidedVoiceProfile(
    speechRate = 0.72f,
    pitch = 0.92f,
    volume = 0.65f
)
