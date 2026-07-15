package com.lingqi.app.sleep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepNotificationSpecTest {
    @Test
    fun `stop action always has a real icon`() {
        assertTrue(sleepNotificationSpec(elapsedMinutes = 7).stopActionIconRes != 0)
    }

    @Test
    fun `content describes elapsed time without mojibake`() {
        assertEquals(
            "已记录 7 分钟 · 原始音频不会保存",
            sleepNotificationSpec(elapsedMinutes = 7).contentText
        )
    }
}
