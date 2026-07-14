package com.lingqi.app.meditation

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.SoundPool
import android.media.AudioTrack
import com.lingqi.app.R
import com.lingqi.app.data.BreathingCueSound
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class CuePlayer(context: Context) {
    private val activeTracks = ConcurrentHashMap.newKeySet<AudioTrack>()
    private val activeStreams = ConcurrentHashMap.newKeySet<Int>()
    private val loadedSounds = ConcurrentHashMap.newKeySet<Int>()
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val tickSoundId: Int
    private val tockSoundId: Int

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            if (status == 0) loadedSounds.add(sampleId)
        }
        tickSoundId = soundPool.load(context.applicationContext, R.raw.pendulum_tick, 1)
        tockSoundId = soundPool.load(context.applicationContext, R.raw.pendulum_tock, 1)
    }

    fun playDi() = playTone(DI_CUE)
    fun playTa() = playTone(TA_CUE)

    fun play(sound: BreathingCueSound, phase: BreathPhase) {
        when (cueAction(sound, phase, recordedReady = recordedCuesReady())) {
            CueAction.RECORDED_TICK -> playRecorded(tickSoundId)
            CueAction.RECORDED_TOCK -> playRecorded(tockSoundId)
            CueAction.SYNTHESIZED_DI -> playDi()
            CueAction.SYNTHESIZED_TA -> playTa()
            CueAction.NONE -> Unit
        }
    }

    fun stopAll() {
        activeStreams.toList().forEach { streamId ->
            runCatching { soundPool.stop(streamId) }
            activeStreams.remove(streamId)
        }
        activeTracks.toList().forEach { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
            activeTracks.remove(track)
        }
    }

    fun release() {
        stopAll()
        runCatching { soundPool.release() }
    }

    private fun recordedCuesReady(): Boolean =
        tickSoundId in loadedSounds && tockSoundId in loadedSounds

    private fun playRecorded(soundId: Int) {
        val streamId = runCatching {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }.getOrDefault(0)
        if (streamId != 0) activeStreams.add(streamId)
    }

    private fun playTone(spec: CueToneSpec) {
        thread(name = "lingqi-cue", isDaemon = true) {
            val sampleRate = 44_100
            val samples = generateCueSamples(spec, sampleRate)
            var track: AudioTrack? = null
            try {
                track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sampleRate)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(samples.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                activeTracks.add(track)
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep((spec.durationSeconds * 1000).toLong() + 50)
            } catch (_: Exception) {
                // A cue must never interrupt the meditation session.
            } finally {
                track?.let {
                    activeTracks.remove(it)
                    runCatching { it.release() }
                }
            }
        }
    }
}
