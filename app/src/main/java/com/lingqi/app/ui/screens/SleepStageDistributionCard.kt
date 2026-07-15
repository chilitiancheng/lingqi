package com.lingqi.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lingqi.app.data.SleepStage
import com.lingqi.app.sleep.SleepStageDistribution
import com.lingqi.app.sleep.SleepStageSlice
import com.lingqi.app.sleep.formatSleepDuration
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite
import com.lingqi.app.ui.theme.SleepDeep
import com.lingqi.app.ui.theme.SleepLight
import com.lingqi.app.ui.theme.SleepRem
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
internal fun SleepStageDistributionCard(distribution: SleepStageDistribution) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("睡眠结构", color = LingqiWhite, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "睡眠结构说明",
                tint = LingqiMuted,
                modifier = Modifier.padding(start = 6.dp).size(16.dp)
            )
        }
        if (!distribution.hasData) {
            Text(
                "暂无足够睡眠数据",
                color = LingqiMuted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 24.dp)
            )
            return@Column
        }
        SleepStageDonutChart(distribution, Modifier.fillMaxWidth().height(300.dp))
        Surface(
            color = Color(0xFF101010),
            shape = RoundedCornerShape(22.dp),
            border = BorderStroke(1.dp, Color(0xFF242424)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
                distribution.slices.forEachIndexed { index, slice ->
                    SleepStageBreakdownRow(slice)
                    if (index < distribution.slices.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SleepStageDonutChart(
    distribution: SleepStageDistribution,
    modifier: Modifier = Modifier
) {
    val drawOrder = listOf(SleepStage.REM, SleepStage.LIGHT, SleepStage.DEEP)
    Box(modifier) {
        Canvas(Modifier.matchParentSize()) {
            val diameter = min(size.width * 0.62f, size.height * 0.62f)
            val strokeWidth = 34.dp.toPx()
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = diameter / 2f
            var startAngle = -90f
            drawOrder.forEach { stage ->
                val slice = distribution.slice(stage)
                val sweep = slice.percentage * 3.6f
                drawArc(
                    color = stageColor(stage),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(strokeWidth, cap = StrokeCap.Butt)
                )
                if (sweep > 0f) {
                    val radians = Math.toRadians(startAngle.toDouble())
                    val inner = Offset(
                        center.x + cos(radians).toFloat() * (radius - strokeWidth / 2f),
                        center.y + sin(radians).toFloat() * (radius - strokeWidth / 2f)
                    )
                    val outer = Offset(
                        center.x + cos(radians).toFloat() * (radius + strokeWidth / 2f),
                        center.y + sin(radians).toFloat() * (radius + strokeWidth / 2f)
                    )
                    drawLine(Color(0xFF050505), inner, outer, 2.dp.toPx())
                }

                val middleRadians = Math.toRadians((startAngle + sweep / 2f).toDouble())
                val anchor = Offset(
                    center.x + cos(middleRadians).toFloat() * (radius + strokeWidth / 2f),
                    center.y + sin(middleRadians).toFloat() * (radius + strokeWidth / 2f)
                )
                val direction = if (anchor.x < center.x) -1f else 1f
                val elbow = Offset(anchor.x + direction * 22.dp.toPx(), anchor.y + 22.dp.toPx())
                val end = Offset(elbow.x + direction * 50.dp.toPx(), elbow.y)
                drawLine(Color(0xFF777777), anchor, elbow, 1.dp.toPx())
                drawLine(Color(0xFF777777), elbow, end, 1.dp.toPx())
                startAngle += sweep
            }
        }
        Text(
            formatSleepDuration(distribution.totalSleepMillis),
            color = LingqiWhite,
            fontSize = 30.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.align(Alignment.Center)
        )
        Text(
            "深睡眠",
            color = LingqiWhite,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 56.dp)
        )
        Text(
            "眼动期",
            color = LingqiWhite,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 56.dp)
        )
        Text(
            "浅睡眠",
            color = LingqiWhite,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 52.dp)
        )
    }
}

@Composable
private fun SleepStageBreakdownRow(slice: SleepStageSlice) {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(
                Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(stageColor(slice.stage), RoundedCornerShape(50))
            )
            Text(
                slice.stage.displayName(),
                color = LingqiWhite,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${slice.percentage}%",
                    color = LingqiWhite,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Light
                )
                Text(
                    "参考：${slice.referenceRange}",
                    color = LingqiMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp)
                )
            }
            Text(
                formatSleepDuration(slice.durationMillis),
                color = LingqiWhite,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun stageColor(stage: SleepStage): Color = when (stage) {
    SleepStage.LIGHT -> SleepLight
    SleepStage.DEEP -> SleepDeep
    SleepStage.REM -> SleepRem
    SleepStage.AWAKE -> Color.Transparent
}

private fun SleepStage.displayName(): String = when (this) {
    SleepStage.LIGHT -> "浅睡眠"
    SleepStage.DEEP -> "深睡眠"
    SleepStage.REM -> "眼动期"
    SleepStage.AWAKE -> "清醒"
}
