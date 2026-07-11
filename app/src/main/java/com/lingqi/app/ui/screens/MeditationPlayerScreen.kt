package com.lingqi.app.ui.screens

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lingqi.app.LingqiApplication
import com.lingqi.app.data.MeditationKind
import com.lingqi.app.data.MeditationSession
import com.lingqi.app.meditation.BreathPhase
import com.lingqi.app.meditation.BreathState
import com.lingqi.app.meditation.BreathingCycle
import com.lingqi.app.meditation.AmbientAudioPlayer
import com.lingqi.app.meditation.AmbientPlaybackPolicy
import com.lingqi.app.meditation.CuePlayer
import com.lingqi.app.meditation.GuidedVoiceController
import com.lingqi.app.meditation.advanceMeditationPlaybackClock
import com.lingqi.app.meditation.guidedCues
import com.lingqi.app.meditation.isMeditationPlaybackBlocked
import com.lingqi.app.ui.particle.LingqiParticleView
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.max

@Composable
fun MeditationPlayerScreen(kind: MeditationKind, minutes: Int, onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = (context.applicationContext as LingqiApplication).container.repository
    val plannedMillis = minutes * 60_000L
    val sessionStartedAt = remember { System.currentTimeMillis() }
    val cuePlayer = remember { CuePlayer() }
    val voice = remember { GuidedVoiceController(context) }
    val ambientAudio = remember { AmbientAudioPlayer(context) }
    val cues = remember(kind) { guidedCues(kind) }
    var elapsedMillis by remember { mutableLongStateOf(0L) }
    var lastTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var paused by remember { mutableStateOf(false) }
    var soundEnabled by remember { mutableStateOf(repository.preferences().soundEnabled) }
    var controlsVisible by remember { mutableStateOf(true) }
    var exitDialog by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var completed by remember { mutableStateOf(false) }
    var lastPhase by remember { mutableStateOf<BreathPhase?>(null) }
    var nextCueIndex by remember { mutableIntStateOf(0) }
    val playbackBlocked = isMeditationPlaybackBlocked(
        manuallyPaused = paused,
        exitDialogVisible = exitDialog,
        completed = completed
    )
    val breathState: BreathState? = if (kind == MeditationKind.BREATH_478) {
        BreathingCycle.stateAt(elapsedMillis / 1000.0)
    } else null

    fun saveSession() {
        if (saved) return
        saved = true
        val actual = (elapsedMillis / 1000L).toInt()
        repository.saveMeditation(
            MeditationSession(
                id = UUID.randomUUID().toString(),
                practiceId = kind.id,
                plannedSeconds = minutes * 60,
                actualSeconds = actual,
                startedAt = sessionStartedAt,
                endedAt = System.currentTimeMillis(),
                completionRate = (elapsedMillis.toFloat() / plannedMillis).coerceIn(0f, 1f),
                soundEnabled = soundEnabled
            )
        )
    }

    BackHandler { exitDialog = true }

    DisposableEffect(Unit) {
        onDispose {
            cuePlayer.stopAll()
            voice.shutdown()
            ambientAudio.release()
        }
    }

    LaunchedEffect(soundEnabled, playbackBlocked) {
        voice.setEnabled(soundEnabled && !playbackBlocked)
        if (!soundEnabled || playbackBlocked) cuePlayer.stopAll()
    }

    LaunchedEffect(kind, soundEnabled, playbackBlocked, completed) {
        ambientAudio.setPlaying(
            AmbientPlaybackPolicy.shouldPlay(
                kind = kind,
                soundEnabled = soundEnabled,
                playbackBlocked = playbackBlocked,
                completed = completed
            )
        )
    }

    LaunchedEffect(playbackBlocked) {
        val resetClock = advanceMeditationPlaybackClock(
            elapsedMillis = elapsedMillis,
            lastTickMillis = lastTick,
            nowMillis = SystemClock.elapsedRealtime(),
            playbackBlocked = true,
            plannedMillis = plannedMillis
        )
        elapsedMillis = resetClock.elapsedMillis
        lastTick = resetClock.lastTickMillis
        while (!playbackBlocked) {
            withFrameNanos { }
            val now = SystemClock.elapsedRealtime()
            val clock = advanceMeditationPlaybackClock(
                elapsedMillis = elapsedMillis,
                lastTickMillis = lastTick,
                nowMillis = now,
                playbackBlocked = false,
                plannedMillis = plannedMillis
            )
            elapsedMillis = clock.elapsedMillis
            lastTick = clock.lastTickMillis
            if (elapsedMillis >= plannedMillis) {
                completed = true
                saveSession()
            }
        }
    }

    LaunchedEffect(breathState?.phase, soundEnabled, playbackBlocked) {
        val phase = breathState?.phase ?: return@LaunchedEffect
        if (phase != lastPhase && soundEnabled && !playbackBlocked) {
            when (phase) {
                BreathPhase.INHALE -> cuePlayer.playDi()
                BreathPhase.EXHALE -> cuePlayer.playTa()
                BreathPhase.HOLD -> Unit
            }
        }
        lastPhase = phase
    }

    LaunchedEffect(elapsedMillis, playbackBlocked, soundEnabled) {
        if (kind == MeditationKind.BREATH_478 || playbackBlocked || !soundEnabled || nextCueIndex >= cues.size) return@LaunchedEffect
        val progress = elapsedMillis.toFloat() / plannedMillis
        if (progress >= cues[nextCueIndex].fraction) {
            voice.speak(cues[nextCueIndex].text)
            nextCueIndex += 1
        }
    }

    LaunchedEffect(controlsVisible, playbackBlocked) {
        if (controlsVisible && !playbackBlocked) {
            delay(4000)
            controlsVisible = false
        }
    }

    Box(Modifier.fillMaxSize().clickable { controlsVisible = true }) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { LingqiParticleView(it).apply { setActive(true) } },
            update = {
                it.setActive(true)
                it.setPaused(playbackBlocked)
                it.setBreathState(breathState)
            }
        )
        Column(
            modifier = Modifier.align(Alignment.Center).padding(bottom = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                breathState?.phase?.title ?: kind.title,
                color = LingqiWhite.copy(alpha = 0.84f),
                fontSize = 20.sp,
                letterSpacing = 5.sp
            )
            Text(
                breathState?.phase?.hint ?: "让注意力停留在声音里",
                color = LingqiMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                formatRemaining(plannedMillis - elapsedMillis),
                color = LingqiMuted.copy(alpha = 0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 22.dp)
            )
        }
        AnimatedVisibility(
            visible = controlsVisible || playbackBlocked,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { exitDialog = true }) {
                        Icon(Icons.Outlined.Close, "结束练习", tint = LingqiWhite)
                    }
                    IconButton(onClick = {
                        soundEnabled = !soundEnabled
                        if (!soundEnabled) voice.pause()
                    }) {
                        Icon(
                            if (soundEnabled) Icons.AutoMirrored.Outlined.VolumeUp else Icons.AutoMirrored.Outlined.VolumeOff,
                            "声音",
                            tint = LingqiWhite
                        )
                    }
                }
                if (!completed) {
                    IconButton(
                        onClick = {
                            paused = !paused
                            controlsVisible = true
                            if (paused) voice.pause()
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(bottom = 18.dp)
                            .size(58.dp)
                    ) {
                        Icon(
                            if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause,
                            if (paused) "继续" else "暂停",
                            tint = LingqiWhite,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
        if (completed) {
            Column(
                modifier = Modifier.align(Alignment.Center).padding(horizontal = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("练习完成", color = LingqiWhite, fontSize = 24.sp, fontWeight = FontWeight.Light)
                Text("这段安静已经记录下来", color = LingqiMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                Text(
                    "返回灵栖",
                    color = Color.Black,
                    modifier = Modifier
                        .padding(top = 28.dp)
                        .background(LingqiWhite, RoundedCornerShape(4.dp))
                        .clickable(onClick = onExit)
                        .padding(horizontal = 26.dp, vertical = 13.dp)
                )
            }
        }
    }

    if (exitDialog) {
        AlertDialog(
            onDismissRequest = { exitDialog = false },
            title = { Text("结束这次练习？") },
            text = { Text("已进行的时间仍会保存。") },
            confirmButton = {
                TextButton(onClick = { saveSession(); onExit() }) { Text("结束") }
            },
            dismissButton = { TextButton(onClick = { exitDialog = false }) { Text("继续练习") } }
        )
    }
}

private fun formatRemaining(value: Long): String {
    val seconds = max(0L, value / 1000L)
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
