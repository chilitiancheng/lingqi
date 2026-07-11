package com.lingqi.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingqi.app.LingqiApplication
import com.lingqi.app.data.SleepEpoch
import com.lingqi.app.data.SleepStage
import com.lingqi.app.ui.components.Metric
import com.lingqi.app.ui.components.ScreenHeader
import com.lingqi.app.ui.components.SectionTitle
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite
import com.lingqi.app.ui.theme.SleepAwake
import com.lingqi.app.ui.theme.SleepCalibrationBackground
import com.lingqi.app.ui.theme.SleepCalibrationText
import com.lingqi.app.ui.theme.SleepDeep
import com.lingqi.app.ui.theme.SleepLight
import com.lingqi.app.ui.theme.SleepRem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SleepReportScreen(sessionId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as LingqiApplication).container.repository
    val session = remember(sessionId) { repository.sleepSession(sessionId) }
    if (session == null) {
        Column(Modifier.fillMaxSize().padding(top = 28.dp)) {
            ScreenHeader("睡眠报告", "记录不存在或尚未完成", onBack)
        }
        return
    }
    val epochs = session.epochs
    val duration = (session.endedAt ?: session.startedAt) - session.startedAt
    val awakeEvents = countAwakeEvents(epochs)
    val averageNoise = epochs.map { it.noiseDb }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val confidence = epochs.map { it.confidence }.takeIf { it.isNotEmpty() }?.average() ?: 0.0
    val date = remember { SimpleDateFormat("M 月 d 日睡眠", Locale.CHINA).format(Date(session.startedAt)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 44.dp)
    ) {
        item { ScreenHeader(date, "睡眠阶段为手机传感器估算", onBack) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("${session.score ?: 0}", color = LingqiWhite, fontSize = 58.sp, fontWeight = FontWeight.ExtraLight)
                        Text("睡眠分", color = LingqiMuted, fontSize = 11.sp)
                    }
                    Column(Modifier.padding(top = 12.dp)) {
                        Text(formatSleepDuration(duration), color = LingqiWhite, fontSize = 18.sp)
                        Text("总时长", color = LingqiMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (session.calibrationNight <= 3) {
                    Text(
                        "校准第 ${session.calibrationNight}/3 晚 · 当前置信度上限 62%",
                        color = SleepCalibrationText,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(top = 20.dp)
                            .background(SleepCalibrationBackground, RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    )
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                SectionTitle("睡眠阶段", "置信度 ${(confidence * 100).toInt()}%")
                Spacer(Modifier.height(18.dp))
                SleepStageChart(epochs)
                StageLegend()
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric(awakeEvents.toString(), "清醒次数")
                Metric("${averageNoise.toInt()} dB", "环境声")
                Metric("${(session.coverage * 100).toInt()}%", "传感器覆盖")
            }
        }
        item {
            Text(
                "本报告根据床体活动和环境声音估算浅睡、深睡与 REM 趋势，不测量脑电或心率，不能用于诊断或治疗。",
                color = LingqiMuted,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)
            )
        }
    }
}

@Composable
private fun SleepStageChart(epochs: List<SleepEpoch>) {
    val segments = remember(epochs) { buildSleepStageSegments(epochs.map { it.stage }) }
    Canvas(Modifier.fillMaxWidth().height(170.dp)) {
        if (epochs.isEmpty()) return@Canvas
        val step = size.width / (epochs.size - 1).coerceAtLeast(1)
        fun y(level: Float): Float = size.height * (0.12f + level * 0.26f)
        repeat(4) { index ->
            val lineY = size.height * (0.12f + index * 0.26f)
            drawLine(Color.White.copy(alpha = 0.07f), Offset(0f, lineY), Offset(size.width, lineY), 1f)
        }
        segments.forEach { segment ->
            drawLine(
                color = sleepStageColor(segment.stage),
                start = Offset(segment.startPosition * step, y(segment.startLevel)),
                end = Offset(segment.endPosition * step, y(segment.endLevel)),
                strokeWidth = 3f,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun sleepStageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.AWAKE -> SleepAwake
    SleepStage.LIGHT -> SleepLight
    SleepStage.DEEP -> SleepDeep
    SleepStage.REM -> SleepRem
}

@Composable
private fun StageLegend() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(
            "清醒" to SleepAwake,
            "浅睡" to SleepLight,
            "深睡" to SleepDeep,
            "REM" to SleepRem
        ).forEach { (label, color) ->
            Row {
                Spacer(Modifier.size(8.dp).background(color, RoundedCornerShape(50)))
                Text(label, color = LingqiMuted, fontSize = 10.sp, modifier = Modifier.padding(start = 5.dp))
            }
        }
    }
}

private fun countAwakeEvents(epochs: List<SleepEpoch>): Int {
    var count = 0
    var awake = false
    epochs.forEach {
        if (it.stage == SleepStage.AWAKE && !awake) count += 1
        awake = it.stage == SleepStage.AWAKE
    }
    return count
}
