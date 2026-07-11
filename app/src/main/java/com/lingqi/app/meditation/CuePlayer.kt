package com.lingqi.app.meditation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

class CuePlayer {
    private val activeTracks = ConcurrentHashMap.newKeySet<AudioTrack>()

    fun playDi() = playTone(DI_CUE)
    fun playTa() = playTone(TA_CUE)

    fun stopAll() {
        activeTracks.toList().forEach { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
            activeTracks.remove(track)
        }
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
