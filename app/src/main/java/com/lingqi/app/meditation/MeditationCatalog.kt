package com.lingqi.app.meditation

import com.lingqi.app.data.MeditationKind

object MeditationCatalog {
    val practices: List<MeditationKind> = MeditationKind.entries
    val durationMinutes: List<Int> = listOf(5, 10, 20)
}
