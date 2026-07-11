package com.lingqi.app.data

enum class MeditationKind(val id: String, val title: String, val subtitle: String) {
    BREATH_478("breath-478", "478 呼吸", "让呼吸带动星群收束与散开"),
    BODY_SCAN("body-scan", "身体扫描", "依次松开身体里的紧张"),
    SLEEP_RELEASE("sleep-release", "睡前放松", "把白天缓慢放回夜色"),
    FOCUS("focus", "专注", "把注意力稳稳放回此刻"),
    EMOTIONAL_EASE("emotional-ease", "情绪舒缓", "允许情绪被看见，也被放下"),
    MORNING_AWAKENING("morning-awakening", "晨间唤醒", "以清醒而柔和的节奏开始一天")
}

data class MeditationSession(
    val id: String,
    val practiceId: String,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val startedAt: Long,
    val endedAt: Long,
    val completionRate: Float,
    val soundEnabled: Boolean
)

enum class SleepStage(val label: String) {
    AWAKE("清醒"),
    LIGHT("浅睡估算"),
    DEEP("深睡估算"),
    REM("REM 估算")
}

data class SleepEpoch(
    val startedAt: Long,
    val movementRms: Double,
    val movementPeaks: Int,
    val noiseDb: Double,
    val noiseEvents: Int,
    val snoreProbability: Double,
    val coverage: Double,
    val stage: SleepStage = SleepStage.LIGHT,
    val confidence: Double = 0.0
)

data class SleepSession(
    val id: String,
    val startedAt: Long,
    val endedAt: Long?,
    val placement: String = "mattress_edge",
    val coverage: Double = 0.0,
    val score: Int? = null,
    val calibrationNight: Int = 1,
    val algorithmVersion: String = "lingqi-estimator-1",
    val epochs: List<SleepEpoch> = emptyList()
)

data class UserPreferences(
    val nickname: String = "旅人",
    val soundEnabled: Boolean = true,
    val meditationReminderEnabled: Boolean = false,
    val sleepReminderEnabled: Boolean = false,
    val meditationReminderHour: Int = 21,
    val meditationReminderMinute: Int = 0,
    val sleepReminderHour: Int = 23,
    val sleepReminderMinute: Int = 0,
    val linglianWellnessSharingEnabled: Boolean = false
)

data class UserStats(
    val meditationMinutes: Int = 0,
    val meditationSessions: Int = 0,
    val currentStreak: Int = 0,
    val sleepNights: Int = 0,
    val averageSleepScore: Int? = null
)
