package com.lingqi.app.sleep

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingqi.app.MainActivity
import com.lingqi.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SleepNotificationRenderingTest {
    @Test
    fun collapsedNotificationContainsVisibleStopControlAndFallbackAction() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val contentIntent = PendingIntent.getActivity(
            context,
            80,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            context,
            81,
            Intent(context, SleepTrackingService::class.java)
                .setAction(SleepTrackingService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = buildSleepTrackingNotification(
            context = context,
            elapsedMinutes = 7,
            contentIntent = contentIntent,
            stopIntent = stopIntent
        )

        assertNotNull(notification.contentView)
        assertNotNull(notification.bigContentView)
        assertEquals(1, notification.actions.size)
        assertEquals("结束检测", notification.actions.single().title.toString())

        val collapsed = notification.contentView.apply(context, FrameLayout(context))
        val stopControl = collapsed.findViewById<TextView>(R.id.sleep_notification_stop)
        assertEquals("结束检测", stopControl.text.toString())
        assertTrue(stopControl.hasOnClickListeners())
    }
}
