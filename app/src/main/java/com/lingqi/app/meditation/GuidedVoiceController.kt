package com.lingqi.app.meditation

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

@Suppress("DEPRECATION")
class GuidedVoiceController(
    context: Context,
    private val onUnavailable: () -> Unit = {}
) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)
    private val state = GuidedVoiceState()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var enabled = true

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            val languageStatus = tts.setLanguage(Locale.SIMPLIFIED_CHINESE)
            if (languageStatus < TextToSpeech.LANG_AVAILABLE) {
                reportUnavailable()
                return
            }
            tts.setSpeechRate(0.82f)
            tts.setPitch(0.92f)
            tts.setOnUtteranceProgressListener(
                object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) = Unit
                    override fun onError(utteranceId: String?) = reportUnavailable()
                    override fun onError(utteranceId: String?, errorCode: Int) = reportUnavailable()
                }
            )
            state.markReady()?.takeIf { enabled }?.let(::speakNow)
        } else {
            reportUnavailable()
        }
    }

    fun speak(text: String) {
        if (!enabled) return
        when (state.status) {
            GuidedVoiceStatus.INITIALIZING -> state.enqueueWhileInitializing(text)
            GuidedVoiceStatus.READY -> speakNow(text)
            GuidedVoiceStatus.UNAVAILABLE -> Unit
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) pause()
    }

    fun pause() {
        state.clearPending()
        tts.stop()
    }

    fun shutdown() = tts.shutdown()

    private fun speakNow(text: String) {
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lingqi-guidance") != TextToSpeech.SUCCESS) {
            reportUnavailable()
        }
    }

    private fun reportUnavailable() {
        val firstReport = state.status != GuidedVoiceStatus.UNAVAILABLE
        state.markUnavailable()
        if (firstReport) mainHandler.post(onUnavailable)
    }
}

data class GuidedCue(val fraction: Float, val text: String)

fun guidedCues(kind: com.lingqi.app.data.MeditationKind): List<GuidedCue> = when (kind) {
    com.lingqi.app.data.MeditationKind.BODY_SCAN -> listOf(
        GuidedCue(0f, "轻轻闭上眼睛。让呼吸回到自然的节奏。"),
        GuidedCue(0.1f, "把注意力放到额头，松开眉心和眼周。"),
        GuidedCue(0.24f, "感受肩颈的重量，让肩膀缓慢下沉。"),
        GuidedCue(0.4f, "注意胸口和腹部，不改变呼吸，只是感受起伏。"),
        GuidedCue(0.58f, "让双手、手指和手心都变得柔软。"),
        GuidedCue(0.72f, "把注意力移到双腿和脚底，感受身体被稳稳承托。"),
        GuidedCue(0.9f, "重新感受整个身体。带着这份松弛，慢慢睁开眼睛。")
    )
    com.lingqi.app.data.MeditationKind.SLEEP_RELEASE -> listOf(
        GuidedCue(0f, "夜晚已经到来。现在不需要解决任何事情。"),
        GuidedCue(0.12f, "吸气时感受身体，呼气时把白天放下。"),
        GuidedCue(0.3f, "让下巴、肩膀和腹部依次松开。"),
        GuidedCue(0.48f, "如果念头出现，只需要看见它，然后让它离开。"),
        GuidedCue(0.66f, "感受床面托住身体，你可以放心休息。"),
        GuidedCue(0.82f, "呼吸越来越轻，身体越来越安静。"),
        GuidedCue(0.94f, "接下来无需继续聆听。让自己自然进入睡眠。")
    )
    com.lingqi.app.data.MeditationKind.FOCUS -> listOf(
        GuidedCue(0f, "选择一个自然的坐姿。让视线落在一点，或轻轻闭上眼睛。"),
        GuidedCue(0.12f, "把注意力放在鼻尖，感受每一次呼吸经过。"),
        GuidedCue(0.28f, "当念头带走注意力，不必责备，只把它带回呼吸。"),
        GuidedCue(0.45f, "现在缩小关注范围，只感受下一次吸气和呼气。"),
        GuidedCue(0.62f, "觉察身体仍然稳定，注意力也可以稳定。"),
        GuidedCue(0.8f, "让周围的声音进入觉知，但不需要跟随。"),
        GuidedCue(0.94f, "带着这份清晰，慢慢回到眼前的事情。")
    )
    com.lingqi.app.data.MeditationKind.EMOTIONAL_EASE -> listOf(
        GuidedCue(0f, "先不用改变感受。给此刻的情绪留出一点空间。"),
        GuidedCue(0.14f, "注意它在身体里的位置，是紧、热、重，还是其他感觉。"),
        GuidedCue(0.3f, "轻轻告诉自己：我看见了，也允许它暂时存在。"),
        GuidedCue(0.46f, "每一次呼气，都让身体少用一点力。"),
        GuidedCue(0.62f, "情绪不是全部的你，它正在经过，而你在这里。"),
        GuidedCue(0.8f, "把手掌放松，让呼吸自然流动。"),
        GuidedCue(0.94f, "带着更宽一点的空间，慢慢睁开眼睛。")
    )
    com.lingqi.app.data.MeditationKind.MORNING_AWAKENING -> listOf(
        GuidedCue(0f, "感受新的一天已经开始。让呼吸比刚才深一点。"),
        GuidedCue(0.12f, "活动手指和脚趾，唤醒身体的边缘。"),
        GuidedCue(0.28f, "吸气时舒展脊柱，呼气时放松肩膀。"),
        GuidedCue(0.44f, "感受胸口变得开阔，视线也逐渐清晰。"),
        GuidedCue(0.62f, "想起今天最重要的一件事，只保留一个方向。"),
        GuidedCue(0.8f, "再做一次完整呼吸，带回稳定的力量。"),
        GuidedCue(0.94f, "睁开眼睛，从第一件小事开始。")
    )
    com.lingqi.app.data.MeditationKind.BREATH_478 -> emptyList()
}
