package com.lingqi.app.wellness

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lingqi.app.sleep.SleepTrackingService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@Suppress("DEPRECATION")
class WellnessManifestContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun providerUsesSignatureReadPermission() {
        val provider = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getProviderInfo(
                ComponentName(context, WellnessSummaryProvider::class.java),
                PackageManager.ComponentInfoFlags.of(0)
            )
        } else {
            context.packageManager.getProviderInfo(ComponentName(context, WellnessSummaryProvider::class.java), 0)
        }
        val permission = context.packageManager.getPermissionInfo(WellnessProviderContract.READ_PERMISSION, 0)
        assertTrue(provider.exported)
        assertEquals(WellnessProviderContract.AUTHORITY, provider.authority)
        assertEquals(WellnessProviderContract.READ_PERMISSION, provider.readPermission)
        assertEquals(WellnessProviderContract.READ_PERMISSION, provider.writePermission)
        assertFalse(provider.grantUriPermissions)
        assertEquals(
            android.content.pm.PermissionInfo.PROTECTION_SIGNATURE,
            permission.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_MASK_BASE
        )
    }

    @Test
    fun sleepServiceRemainsPrivate() {
        val service = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getServiceInfo(
                ComponentName(context, SleepTrackingService::class.java),
                PackageManager.ComponentInfoFlags.of(0)
            )
        } else {
            context.packageManager.getServiceInfo(ComponentName(context, SleepTrackingService::class.java), 0)
        }
        assertFalse(service.exported)
    }

    @Test
    fun textToSpeechServicesAreVisibleToTheApp() {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val services = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong())
            )
        } else {
            context.packageManager.queryIntentServices(intent, PackageManager.MATCH_ALL)
        }
        assertTrue("No visible TTS service; verify the manifest queries declaration", services.isNotEmpty())
    }
}
