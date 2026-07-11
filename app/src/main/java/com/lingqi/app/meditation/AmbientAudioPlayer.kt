package com.lingqi.app.meditation

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.lingqi.app.data.MeditationKind

internal object AmbientPlaybackPolicy {
    const val ASSET_PATH = "audio/meditation-relax-sleep-music-346733.mp3"

    fun shouldPlay(
        kind: MeditationKind,
        soundEnabled: Boolean,
        playbackBlocked: Boolean,
        completed: Boolean
    ): Boolean = kind != MeditationKind.BREATH_478 &&
        soundEnabled &&
        !playbackBlocked &&
        !completed
}

internal class AmbientAudioPlayer(context: Context) {
    private val applicationContext = context.applicationContext
    private var player: MediaPlayer? = null

    fun setPlaying(shouldPlay: Boolean) {
        if (!shouldPlay) {
            player?.let { current ->
                runCatching { if (current.isPlaying) current.pause() }
            }
            return
        }

        val current = player ?: createPlayer()?.also { player = it } ?: return
        runCatching { if (!current.isPlaying) current.start() }
    }

    fun release() {
        player?.let { current ->
            runCatching { current.stop() }
            runCatching { current.release() }
        }
        player = null
    }

    private fun createPlayer(): MediaPlayer? {
        val created = MediaPlayer()
        return try {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            applicationContext.assets.openFd(AmbientPlaybackPolicy.ASSET_PATH).use { descriptor ->
                created.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }
            created.isLooping = true
            created.setVolume(AMBIENT_VOLUME, AMBIENT_VOLUME)
            created.prepare()
            created
        } catch (_: Throwable) {
            runCatching { created.release() }
            null
        }
    }

    private companion object {
        const val AMBIENT_VOLUME = 0.10f
    }
}
