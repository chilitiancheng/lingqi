package com.lingqi.app.sleep

import com.lingqi.app.R

internal data class SleepNotificationSpec(
    val contentText: String,
    val stopActionIconRes: Int
)

internal fun sleepNotificationSpec(elapsedMinutes: Long): SleepNotificationSpec =
    SleepNotificationSpec(
        contentText = "已记录 ${elapsedMinutes.coerceAtLeast(0L)} 分钟 · 原始音频不会保存",
        stopActionIconRes = R.drawable.ic_stop
    )
