package com.lingqi.app.data

import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class LingqiRepository(private val database: LingqiDatabase) {
    fun saveMeditation(session: MeditationSession) = database.insertMeditation(session)
    fun meditationHistory(): List<MeditationSession> = database.meditationSessions()
    fun beginSleep(session: SleepSession) = database.startSleepSession(session)
    fun appendSleepEpoch(sessionId: String, epoch: SleepEpoch) = database.appendEpoch(sessionId, epoch)
    fun finalizeSleep(session: SleepSession) {
        database.replaceEpochs(session.id, session.epochs)
        database.finishSleepSession(session)
    }
    fun sleepHistory(): List<SleepSession> = database.sleepSessions()
    fun sleepSession(id: String): SleepSession? = database.sleepSession(id)
    fun sleepSessionIncludingOpen(id: String): SleepSession? = database.sleepSessionIncludingOpen(id)
    fun rawEpochs(sessionId: String): List<SleepEpoch> = database.epochs(sessionId)
    fun discardSleep(sessionId: String) = database.deleteSleepSession(sessionId)
    fun sleepNightCount(): Int = database.sleepSessions(1000).size

    fun preferences(): UserPreferences {
        val raw = database.getPreference("user_preferences") ?: return UserPreferences()
        val json = JSONObject(raw)
        return UserPreferences(
            nickname = json.optString("nickname", "旅人"),
            soundEnabled = json.optBoolean("soundEnabled", true),
            meditationReminderEnabled = json.optBoolean("meditationReminderEnabled", false),
            sleepReminderEnabled = json.optBoolean("sleepReminderEnabled", false),
            meditationReminderHour = json.optInt("meditationReminderHour", 21),
            meditationReminderMinute = json.optInt("meditationReminderMinute", 0),
            sleepReminderHour = json.optInt("sleepReminderHour", 23),
            sleepReminderMinute = json.optInt("sleepReminderMinute", 0),
            linglianWellnessSharingEnabled = json.optBoolean("linglianWellnessSharingEnabled", false)
        )
    }

    fun savePreferences(value: UserPreferences) {
        database.putPreference(
            "user_preferences",
            JSONObject().apply {
                put("nickname", value.nickname)
                put("soundEnabled", value.soundEnabled)
                put("meditationReminderEnabled", value.meditationReminderEnabled)
                put("sleepReminderEnabled", value.sleepReminderEnabled)
                put("meditationReminderHour", value.meditationReminderHour)
                put("meditationReminderMinute", value.meditationReminderMinute)
                put("sleepReminderHour", value.sleepReminderHour)
                put("sleepReminderMinute", value.sleepReminderMinute)
                put("linglianWellnessSharingEnabled", value.linglianWellnessSharingEnabled)
            }.toString()
        )
    }

    fun stats(): UserStats {
        val meditations = meditationHistory()
        val sleep = sleepHistory()
        val completedDays = meditations
            .filter { it.completionRate >= 0.8f }
            .map { Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() }
            .distinct()
            .sortedDescending()
        var streak = 0
        var day = LocalDate.now()
        if (completedDays.firstOrNull() != day) day = day.minusDays(1)
        while (completedDays.contains(day)) {
            streak += 1
            day = day.minusDays(1)
        }
        return UserStats(
            meditationMinutes = meditations.sumOf { it.actualSeconds } / 60,
            meditationSessions = meditations.size,
            currentStreak = streak,
            sleepNights = sleep.size,
            averageSleepScore = sleep.mapNotNull { it.score }.takeIf { it.isNotEmpty() }?.average()?.toInt()
        )
    }

    fun exportJson(): String = database.exportJson()
    fun exportCsv(): String {
        val rows = mutableListOf("type,id,started_at,ended_at,duration_seconds,score,completion_rate")
        meditationHistory().forEach { session ->
            rows += listOf(
                "meditation",
                session.id,
                session.startedAt,
                session.endedAt,
                session.actualSeconds,
                "",
                session.completionRate
            ).joinToString(",")
        }
        sleepHistory().forEach { session ->
            rows += listOf(
                "sleep",
                session.id,
                session.startedAt,
                session.endedAt ?: "",
                ((session.endedAt ?: session.startedAt) - session.startedAt) / 1000L,
                session.score ?: "",
                ""
            ).joinToString(",")
        }
        return rows.joinToString("\n")
    }
    fun clearAll() = database.clearAll()
}
