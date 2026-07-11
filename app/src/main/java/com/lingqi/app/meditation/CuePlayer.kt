package com.lingqi.app.meditation

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

class CuePlayer {
    private val activeTracks = ConcurrentHashMap.newKeySet<AudioTrack>()

    fun playDi() = playSweep(1046.0, 784.0, triangle = false, volume = 0.22)
    fun playTa() = playSweep(392.0, 262.0, triangle = true, volume = 0.18)

    fun stopAll() {
        activeTracks.toList().forEach { track ->
            runCatching { track.stop() }
            runCatching { track.release() }
            activeTracks.remove(track)
        }
    }

    private fun playSweep(startFrequency: Double, endFrequency: Double, triangle: Boolean, volume: Double) {
        thread(name = "lingqi-cue", isDaemon = true) {
            val sampleRate = 44_100
            val duration = 0.24
            val samples = generateCueSamples(startFrequency, endFrequency, triangle, volume, duration, sampleRate)
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
                Thread.sleep((duration * 1000).toLong() + 50)
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

internal fun generateCueSamples(
    startFrequency: Double,
    endFrequency: Double,
    triangle: Boolean,
    volume: Double,
    durationSeconds: Double,
    sampleRate: Int = 44_100
): ShortArray {
    val count = (sampleRate * durationSeconds).toInt()
    val samples = ShortArray(count)
    var phase = 0.0
    for (index in 0 until count) {
        val t = index.toDouble() / count
        val frequency = startFrequency + (endFrequency - startFrequency) * t
        phase += 2.0 * PI * frequency / sampleRate
        val wave = if (triangle) 2.0 / PI * kotlin.math.asin(sin(phase)) else sin(phase)
        val envelope = if (t < 0.08) t / 0.08 else (1.0 - t).coerceAtLeast(0.0)
        samples[index] = (wave * envelope * volume * Short.MAX_VALUE).toInt().toShort()
    }
    return samples
}
