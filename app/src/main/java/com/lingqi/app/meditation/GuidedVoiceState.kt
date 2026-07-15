package com.lingqi.app.meditation

enum class GuidedVoiceStatus {
    INITIALIZING,
    READY,
    UNAVAILABLE
}

class GuidedVoiceState {
    var status: GuidedVoiceStatus = GuidedVoiceStatus.INITIALIZING
        private set

    private var pendingCue: String? = null

    @Synchronized
    fun enqueueWhileInitializing(text: String) {
        if (status == GuidedVoiceStatus.INITIALIZING) pendingCue = text
    }

    @Synchronized
    fun markReady(): String? {
        if (status == GuidedVoiceStatus.UNAVAILABLE) return null
        status = GuidedVoiceStatus.READY
        return pendingCue.also { pendingCue = null }
    }

    @Synchronized
    fun markUnavailable() {
        status = GuidedVoiceStatus.UNAVAILABLE
        pendingCue = null
    }

    @Synchronized
    fun clearPending() {
        pendingCue = null
    }
}
