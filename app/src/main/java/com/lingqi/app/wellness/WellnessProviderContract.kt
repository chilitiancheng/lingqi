package com.lingqi.app.wellness

object WellnessProviderContract {
    const val AUTHORITY = "com.lingqi.app.wellness"
    const val READ_PERMISSION = "com.lingqi.app.permission.READ_WELLNESS_SUMMARY"
    const val LINGLIAN_PACKAGE = "app.linglian.mobile"
    const val DEFAULT_LIMIT = 10
    const val MAX_LIMIT = 30

    val SLEEP_COLUMNS = listOf(
        "sessionId", "startedAt", "endedAt", "durationSeconds", "score", "coverage", "calibrationNight"
    )
    val MEDITATION_COLUMNS = listOf(
        "sessionId", "practiceId", "startedAt", "endedAt", "plannedSeconds", "actualSeconds", "completionRate"
    )
}

enum class WellnessSummaryKind { SLEEP, MEDITATION }

data class WellnessQuery(val kind: WellnessSummaryKind, val limit: Int)

data class SleepWellnessSummary(
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSeconds: Long,
    val score: Int?,
    val coverage: Double,
    val calibrationNight: Int
)

data class MeditationWellnessSummary(
    val sessionId: String,
    val practiceId: String,
    val startedAt: Long,
    val endedAt: Long,
    val plannedSeconds: Int,
    val actualSeconds: Int,
    val completionRate: Float
)

object WellnessQueryPolicy {
    fun parse(
        pathSegments: List<String>,
        queryParameters: Map<String, List<String>>,
        projection: Array<out String>? = null,
        selection: String? = null,
        selectionArgs: Array<out String>? = null,
        sortOrder: String? = null
    ): WellnessQuery {
        require(projection == null) { "Custom projection is not supported" }
        require(selection == null && selectionArgs == null) { "Custom selection is not supported" }
        require(sortOrder == null) { "Custom sort order is not supported" }
        require(queryParameters.keys.all { it == "limit" }) { "Unsupported query parameter" }

        val kind = when (pathSegments) {
            listOf("sleep", "recent") -> WellnessSummaryKind.SLEEP
            listOf("meditation", "recent") -> WellnessSummaryKind.MEDITATION
            else -> throw IllegalArgumentException("Unsupported wellness summary path")
        }
        val values = queryParameters["limit"]
        val limit = if (values == null) {
            WellnessProviderContract.DEFAULT_LIMIT
        } else {
            require(values.size == 1) { "Limit must be provided once" }
            values.single().toIntOrNull()
                ?.takeIf { it in 1..WellnessProviderContract.MAX_LIMIT }
                ?: throw IllegalArgumentException("Limit must be between 1 and ${WellnessProviderContract.MAX_LIMIT}")
        }
        return WellnessQuery(kind, limit)
    }
}

data class CallerIdentity(
    val uidPackages: Set<String>,
    val reportedCallingPackage: String?,
    val signaturesMatch: Boolean
)

object LinglianCallerAuthorization {
    fun isAllowed(identity: CallerIdentity): Boolean =
        identity.reportedCallingPackage == WellnessProviderContract.LINGLIAN_PACKAGE &&
            WellnessProviderContract.LINGLIAN_PACKAGE in identity.uidPackages &&
            identity.signaturesMatch
}
