package com.lingqi.app.sleep

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lingqi.app.LingqiApplication
import com.lingqi.app.MainActivity
import com.lingqi.app.R
import com.lingqi.app.data.SleepSession
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SleepTrackingService : Service(), SensorEventListener {
    private val repository by lazy { (application as LingqiApplication).container.repository }
    private val analyzer by lazy { (application as LingqiApplication).container.sleepAnalyzer }
    private val regularityEstimator = SleepRegularityEstimator()
    private val sensorManager by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
    private val notificationManager by lazy { NotificationManagerCompat.from(this) }
    private val tracking = AtomicBoolean(false)
    private var sessionId: String? = null
    private var startedAt: Long = 0L
    private var accumulator = EpochAccumulator()
    private var scheduler: ScheduledExecutorService? = null
    private var audioExecutor = Executors.newSingleThreadExecutor()
    private val finishExecutor = Executors.newSingleThreadExecutor()
    private val finishDispatcher = SleepFinishDispatcher(finishExecutor)
    private var audioRecord: AudioRecord? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> requestFinish(discard = false)
            ACTION_DISCARD -> requestFinish(discard = true)
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_SESSION_ID) ?: return START_NOT_STICKY
                val start = intent.getLongExtra(EXTRA_STARTED_AT, System.currentTimeMillis())
                startTracking(id, start)
            }
            else -> {
                val status = SleepTracker.getStatus(this)
                if (status.active && status.sessionId != null) startTracking(status.sessionId, status.startedAt)
            }
        }
        return START_STICKY
    }

    private fun startTracking(id: String, start: Long) {
        if (!tracking.compareAndSet(false, true)) return
        sessionId = id
        startedAt = start
        accumulator = EpochAccumulator(System.currentTimeMillis())
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            }
        )
        setPersistedStatus(true, id, start)
        val calibrationNight = (repository.sleepNightCount() + 1).coerceAtMost(4)
        repository.beginSleep(
            SleepSession(
                id = id,
                startedAt = start,
                endedAt = null,
                calibrationNight = calibrationNight
            )
        )
        acquireWakeLock()
        startSensors()
        startAudio()
        scheduler = Executors.newSingleThreadScheduledExecutor().apply {
            scheduleWithFixedDelay({ flushEpoch() }, 30, 30, TimeUnit.SECONDS)
        }
    }

    private fun startSensors() {
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let { sensor ->
            sensorManager.registerListener(this, sensor, 40_000)
        }
    }

    private fun startAudio() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val sampleRate = 16_000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val recorder = createAudioRecord(sampleRate, minBuffer) ?: return
        audioRecord = recorder
        audioExecutor.execute {
            val buffer = ShortArray(minBuffer)
            try {
                recorder.startRecording()
                while (tracking.get()) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0) accumulator.addAudio(buffer, read, sampleRate)
                }
            } catch (_: SecurityException) {
                // Motion-only tracking continues if microphone access disappears.
            } catch (_: IllegalStateException) {
                // A device without a usable recorder falls back to motion-only tracking.
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createAudioRecord(sampleRate: Int, minBuffer: Int): AudioRecord? {
        val sources = intArrayOf(MediaRecorder.AudioSource.UNPROCESSED, MediaRecorder.AudioSource.MIC)
        sources.forEach { source ->
            val recorder = runCatching {
                AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuffer * 2
                )
            }.getOrNull() ?: return@forEach
            if (recorder.state == AudioRecord.STATE_INITIALIZED) return recorder
            runCatching { recorder.release() }
        }
        return null
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) {
            accumulator.addMovement(event.values[0], event.values[1], event.values[2])
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Synchronized
    private fun flushEpoch() {
        if (!shouldPersistSleepEpoch(tracking.get())) return
        val id = sessionId ?: return
        val epoch = accumulator.flush()
        repository.appendSleepEpoch(id, epoch)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(NOTIFICATION_ID, buildNotification())
        }
    }

    @Synchronized
    private fun finishSession(discard: Boolean) {
        val wasTracking = tracking.getAndSet(false)
        if (!wasTracking) {
            val persisted = SleepTracker.getStatus(this)
            if (!persisted.active || persisted.sessionId == null) {
                return
            }
            sessionId = persisted.sessionId
            startedAt = persisted.startedAt
        }
        scheduler?.shutdownNow()
        scheduler = null
        sensorManager.unregisterListener(this)
        audioRecord?.let { recorder ->
            runCatching { recorder.stop() }
            recorder.release()
        }
        audioRecord = null
        audioExecutor.shutdownNow()
        val id = sessionId
        if (id != null) {
            if (discard) {
                repository.discardSleep(id)
            } else {
                if (wasTracking) repository.appendSleepEpoch(id, accumulator.flush())
                val openSession = repository.sleepSessionIncludingOpen(id)
                val rawEpochs = repository.rawEpochs(id)
                val calibrationNight = openSession?.calibrationNight ?: (repository.sleepNightCount() + 1)
                val completedAt = System.currentTimeMillis()
                val completedSession = SleepSession(
                    id = id,
                    startedAt = openSession?.startedAt ?: startedAt,
                    endedAt = completedAt,
                    placement = "mattress_edge",
                    calibrationNight = calibrationNight
                )
                val regularity = regularityEstimator.estimate(
                    regularityHistoryIncludingCurrent(
                        history = repository.sleepHistory(),
                        current = completedSession
                    )
                )
                val result = analyzer.analyze(rawEpochs, calibrationNight, regularity)
                repository.finalizeSleep(
                    completedSession.copy(
                        coverage = result.coverage,
                        score = result.score,
                        epochs = result.epochs
                    )
                )
            }
        }
        setPersistedStatus(false, null, 0L)
    }

    private fun requestFinish(discard: Boolean) {
        finishDispatcher.dispatch {
            try {
                finishSession(discard)
            } finally {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
                stopSelf()
            }
        }
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Lingqi::SleepTracking").apply {
            setReferenceCounted(false)
            acquire(10 * 60 * 60 * 1000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val elapsedMinutes = ((System.currentTimeMillis() - startedAt).coerceAtLeast(0L) / 60_000L)
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, SleepTrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return buildSleepTrackingNotification(
            context = this,
            elapsedMinutes = elapsedMinutes,
            contentIntent = contentIntent,
            stopIntent = stopIntent
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            SLEEP_NOTIFICATION_CHANNEL_ID,
            getString(R.string.sleep_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.sleep_channel_description)
            setShowBadge(false)
        }
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    private fun setPersistedStatus(active: Boolean, id: String?, start: Long) {
        getSharedPreferences(SleepTracker.PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(SleepTracker.KEY_ACTIVE, active)
            .putString(SleepTracker.KEY_SESSION_ID, id)
            .putLong(SleepTracker.KEY_STARTED_AT, start)
            .apply()
        sendBroadcast(Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    override fun onDestroy() {
        scheduler?.shutdownNow()
        sensorManager.unregisterListener(this)
        if (tracking.get()) {
            runCatching { audioRecord?.stop() }
            audioRecord?.release()
            audioRecord = null
        }
        audioExecutor.shutdownNow()
        finishExecutor.shutdownNow()
        releaseWakeLock()
        if (activeInstance === this) activeInstance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.lingqi.app.sleep.START"
        const val ACTION_STOP = "com.lingqi.app.sleep.STOP"
        const val ACTION_DISCARD = "com.lingqi.app.sleep.DISCARD"
        const val ACTION_STATE_CHANGED = "com.lingqi.app.sleep.STATE_CHANGED"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_STARTED_AT = "started_at"
        private const val NOTIFICATION_ID = 478

        @Volatile
        private var activeInstance: SleepTrackingService? = null

        internal fun quiesceAndDiscardForDeletion(context: Context): Boolean {
            val service = activeInstance
            return if (service != null) {
                service.finishSession(discard = true)
                true
            } else {
                context.stopService(Intent(context, SleepTrackingService::class.java))
                true
            }
        }
    }
}

internal fun shouldPersistSleepEpoch(trackingActive: Boolean): Boolean = trackingActive
