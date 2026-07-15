package com.lingqi.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.lingqi.app.LingqiApplication
import com.lingqi.app.data.SleepSession
import com.lingqi.app.sleep.SleepTracker
import com.lingqi.app.sleep.SleepTrackingStatus
import com.lingqi.app.ui.components.DividerLine
import com.lingqi.app.ui.components.Metric
import com.lingqi.app.ui.components.OutlineButton
import com.lingqi.app.ui.components.PrimaryButton
import com.lingqi.app.ui.components.ScreenHeader
import com.lingqi.app.ui.components.SectionTitle
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SleepScreen(onBack: () -> Unit, onOpenReport: (String) -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as LingqiApplication).container.repository
    var status by remember { mutableStateOf(SleepTracker.getStatus(context)) }
    var history by remember { mutableStateOf(emptyList<SleepSession>()) }
    var permissionMessage by remember { mutableStateOf<String?>(null) }
    val checklist = remember { mutableStateListOf(false, false, false) }
    var discardDialog by remember { mutableStateOf(false) }
    var wasActive by remember { mutableStateOf(status.active) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val microphoneGranted = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (microphoneGranted) {
            runCatching { SleepTracker.startSession(context) }
                .onSuccess { status = it.asTrackingStatus() }
                .onFailure { permissionMessage = "无法启动睡眠检测：${it.message ?: "系统限制"}" }
        } else {
            permissionMessage = "需要麦克风权限才能分析环境声。原始录音不会保存。"
        }
    }

    LaunchedEffect(Unit) {
        history = loadSleepHistory { repository.sleepHistory() }
        while (true) {
            delay(1000)
            val current = SleepTracker.getStatus(context)
            if (wasActive && !current.active) {
                history = loadSleepHistory { repository.sleepHistory() }
                history.firstOrNull()?.let { onOpenReport(it.id) }
            }
            wasActive = current.active
            status = current
        }
    }

    if (status.active) {
        SleepActiveContent(
            status = status,
            onStop = { SleepTracker.stopSession(context) },
            onDiscard = { discardDialog = true }
        )
    } else {
        SleepSetupContent(
            history = history,
            checklist = checklist,
            charging = isCharging(context),
            onBack = onBack,
            onStart = {
                val permissions = buildList {
                    add(Manifest.permission.RECORD_AUDIO)
                    if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                }
                permissionLauncher.launch(permissions.toTypedArray())
            },
            onOpenReport = onOpenReport
        )
    }

    permissionMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { permissionMessage = null },
            title = { Text("权限未完成") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { permissionMessage = null }) { Text("知道了") } }
        )
    }
    if (discardDialog) {
        AlertDialog(
            onDismissRequest = { discardDialog = false },
            title = { Text("放弃本次检测？") },
            text = { Text("本次尚未完成的数据会被删除。") },
            confirmButton = {
                TextButton(onClick = { discardDialog = false; SleepTracker.discardSession(context) }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { discardDialog = false }) { Text("继续守夜") } }
        )
    }
}

internal suspend fun loadSleepHistory(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    load: () -> List<SleepSession>
): List<SleepSession> = withContext(dispatcher) { load() }

@Composable
private fun SleepSetupContent(
    history: List<SleepSession>,
    checklist: MutableList<Boolean>,
    charging: Boolean,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onOpenReport: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 40.dp)
    ) {
        item { ScreenHeader("睡眠", "手机传感器估算睡眠趋势", onBack) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 22.dp)) {
                SectionTitle("睡前准备")
                Spacer(Modifier.height(12.dp))
                ChecklistRow("手机放在床垫边缘，屏幕朝上", checklist[0]) { checklist[0] = !checklist[0] }
                DividerLine()
                ChecklistRow(
                    if (charging) "已连接电源" else "建议连接电源后开始",
                    checklist[1],
                    accent = if (charging) LingqiWhite else Color(0xFFB49D86)
                ) { checklist[1] = !checklist[1] }
                DividerLine()
                ChecklistRow("确认不会把手机压在枕头下", checklist[2]) { checklist[2] = !checklist[2] }
                Spacer(Modifier.height(20.dp))
                PrimaryButton("开始守夜", enabled = checklist.all { it }, onClick = onStart)
                Text(
                    "麦克风只提取分贝与鼾声候选，原始音频不会保存。睡眠阶段属于估算趋势，不是医疗诊断。",
                    color = LingqiMuted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 14.dp)
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                SectionTitle("最近报告", if (history.isEmpty()) "尚无记录" else "${history.size} 晚")
            }
        }
        items(history.size) { index ->
            val session = history[index]
            SleepHistoryRow(session = session, onClick = { onOpenReport(session.id) })
        }
    }
}

@Composable
private fun ChecklistRow(title: String, checked: Boolean, accent: Color = LingqiWhite, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (checked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (checked) accent else LingqiMuted
        )
        Text(title, color = accent, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun SleepHistoryRow(session: SleepSession, onClick: () -> Unit) {
    val formatter = remember { SimpleDateFormat("M 月 d 日  HH:mm", Locale.CHINA) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(formatter.format(Date(session.startedAt)), color = LingqiWhite, fontSize = 14.sp)
            Text(
                "${formatSleepDuration((session.endedAt ?: session.startedAt) - session.startedAt)} · 覆盖率 ${(session.coverage * 100).toInt()}%",
                color = LingqiMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Text(session.score?.toString() ?: "--", color = LingqiWhite, fontSize = 22.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun SleepActiveContent(status: SleepTrackingStatus, onStop: () -> Unit, onDiscard: () -> Unit) {
    var elapsed by remember { mutableStateOf(System.currentTimeMillis() - status.startedAt) }
    LaunchedEffect(status.startedAt) {
        while (true) {
            elapsed = System.currentTimeMillis() - status.startedAt
            delay(1000)
        }
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 46.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("守夜中", color = LingqiWhite, fontSize = 13.sp, letterSpacing = 5.sp)
        Text(formatSleepDuration(elapsed), color = LingqiWhite, fontSize = 46.sp, fontWeight = FontWeight.ExtraLight, modifier = Modifier.padding(top = 24.dp))
        Text("锁屏后仍会继续 · 原始音频不会保存", color = LingqiMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(64.dp))
        PrimaryButton("结束并生成报告", onClick = onStop)
        Spacer(Modifier.height(12.dp))
        OutlineButton("放弃本次检测", onClick = onDiscard)
    }
}

private fun isCharging(context: Context): Boolean {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
}

internal fun formatSleepDuration(milliseconds: Long): String {
    val totalMinutes = (milliseconds.coerceAtLeast(0L) / 60_000L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} 小时 ${minutes} 分" else "${minutes} 分钟"
}
