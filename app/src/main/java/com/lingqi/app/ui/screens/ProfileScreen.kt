package com.lingqi.app.ui.screens

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.lingqi.app.LingqiApplication
import com.lingqi.app.data.BreathingCueSound
import com.lingqi.app.data.LocalDataDeletionCoordinator
import com.lingqi.app.data.UserPreferences
import com.lingqi.app.data.UserStats
import com.lingqi.app.notifications.NotificationPermissionPolicy
import com.lingqi.app.notifications.ReminderToggleAction
import com.lingqi.app.reminder.ReminderScheduler
import com.lingqi.app.reminder.formatReminderTime
import com.lingqi.app.sleep.SleepTracker
import com.lingqi.app.ui.components.ActionRow
import com.lingqi.app.ui.components.DividerLine
import com.lingqi.app.ui.components.Metric
import com.lingqi.app.ui.components.ScreenHeader
import com.lingqi.app.ui.components.SectionTitle
import com.lingqi.app.ui.theme.LingqiMuted
import com.lingqi.app.ui.theme.LingqiWhite
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val repository = (context.applicationContext as LingqiApplication).container.repository
    var preferences by remember { mutableStateOf(UserPreferences()) }
    var stats by remember { mutableStateOf(UserStats()) }
    var nicknameDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var breathingCueDialog by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val loaded = loadProfileData {
            ProfileData(repository.preferences(), repository.stats())
        }
        preferences = loaded.preferences
        stats = loaded.stats
    }

    fun updatePreferences(next: UserPreferences) {
        preferences = next
        repository.savePreferences(next)
    }

    fun enableReminder(type: String) {
        val next = if (type == ReminderScheduler.TYPE_MEDITATION) {
            preferences.copy(meditationReminderEnabled = true)
        } else {
            preferences.copy(sleepReminderEnabled = true)
        }
        updatePreferences(next)
        if (type == ReminderScheduler.TYPE_MEDITATION) {
            ReminderScheduler.schedule(
                context,
                type,
                next.meditationReminderHour,
                next.meditationReminderMinute
            )
        } else {
            ReminderScheduler.schedule(
                context,
                type,
                next.sleepReminderHour,
                next.sleepReminderMinute
            )
        }
    }

    fun disableReminder(type: String) {
        val next = if (type == ReminderScheduler.TYPE_MEDITATION) {
            preferences.copy(meditationReminderEnabled = false)
        } else {
            preferences.copy(sleepReminderEnabled = false)
        }
        updatePreferences(next)
        ReminderScheduler.cancel(context, type)
    }

    fun showReminderTimePicker(type: String) {
        val meditation = type == ReminderScheduler.TYPE_MEDITATION
        val initialHour = if (meditation) preferences.meditationReminderHour else preferences.sleepReminderHour
        val initialMinute = if (meditation) preferences.meditationReminderMinute else preferences.sleepReminderMinute
        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                val next = if (meditation) {
                    preferences.copy(
                        meditationReminderHour = selectedHour,
                        meditationReminderMinute = selectedMinute
                    )
                } else {
                    preferences.copy(
                        sleepReminderHour = selectedHour,
                        sleepReminderMinute = selectedMinute
                    )
                }
                updatePreferences(next)
                val enabled = if (meditation) {
                    next.meditationReminderEnabled
                } else {
                    next.sleepReminderEnabled
                }
                if (enabled) {
                    ReminderScheduler.schedule(context, type, selectedHour, selectedMinute)
                }
            },
            initialHour,
            initialMinute,
            true
        ).show()
    }

    fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

    var notificationPermissionGranted by remember { mutableStateOf(hasNotificationPermission()) }

    fun reconcileNotificationPermission(): Boolean {
        val granted = hasNotificationPermission()
        notificationPermissionGranted = granted
        if (
            NotificationPermissionPolicy.mustDisableReminders(
                sdkInt = Build.VERSION.SDK_INT,
                permissionGranted = granted,
                meditationEnabled = preferences.meditationReminderEnabled,
                sleepEnabled = preferences.sleepReminderEnabled
            )
        ) {
            ReminderScheduler.cancelAll(context)
            updatePreferences(
                preferences.copy(
                    meditationReminderEnabled = false,
                    sleepReminderEnabled = false
                )
            )
        }
        return granted
    }

    var pendingReminderType by remember { mutableStateOf<String?>(null) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        val granted = reconcileNotificationPermission()
        val type = pendingReminderType
        pendingReminderType = null
        if (granted && type != null) enableReminder(type)
    }

    fun onReminderToggle(type: String, requestedEnabled: Boolean) {
        val permissionGranted = reconcileNotificationPermission()
        when (
            NotificationPermissionPolicy.reminderToggleAction(
                requestedEnabled = requestedEnabled,
                sdkInt = Build.VERSION.SDK_INT,
                permissionGranted = permissionGranted
            )
        ) {
            ReminderToggleAction.ENABLE -> enableReminder(type)
            ReminderToggleAction.DISABLE -> disableReminder(type)
            ReminderToggleAction.REQUEST_PERMISSION -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pendingReminderType = type
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enableReminder(type)
                }
            }
        }
    }

    val lifecycleOwner = context as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) reconcileNotificationPermission()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true) {
            reconcileNotificationPermission()
        }
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val deletionCoordinator = remember(context, repository) {
        LocalDataDeletionCoordinator(
            cancelAllReminders = { ReminderScheduler.cancelAll(context) },
            quiesceAndDiscardSleepTracking = {
                SleepTracker.quiesceAndDiscardForDeletion(context)
            },
            clearSleepTrackingState = { SleepTracker.clearPersistedState(context) },
            clearRepositoryData = repository::clearAll
        )
    }
    val exportJson = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(exportContent) } }
    }
    val exportCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use { writer -> writer.write(exportContent) } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(top = 28.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp)
    ) {
        item { ScreenHeader("我的", "数据只保存在这台设备", onBack) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(58.dp).background(Color(0xFF151615), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(preferences.nickname.firstOrNull()?.toString() ?: "灵", color = LingqiWhite, fontSize = 22.sp)
                }
                Column(Modifier.padding(start = 15.dp)) {
                    Text(preferences.nickname, color = LingqiWhite, fontSize = 19.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = { nicknameDialog = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("修改昵称", color = LingqiMuted, fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Metric("${stats.currentStreak} 天", "连续练习")
                Metric("${stats.meditationMinutes} 分", "冥想总时长")
                Metric(stats.averageSleepScore?.toString() ?: "--", "平均睡眠分")
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                SectionTitle("提醒与声音")
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    "呼吸提示音",
                    preferences.breathingCueSound.label,
                    onClick = { breathingCueDialog = true }
                )
                DividerLine()
                ActionRow("语音与环境声", "用于引导冥想", trailing = {
                    Switch(checked = preferences.soundEnabled, onCheckedChange = { updatePreferences(preferences.copy(soundEnabled = it)) })
                })
                DividerLine()
                ActionRow(
                    "每日冥想提醒",
                    formatReminderTime(
                        preferences.meditationReminderHour,
                        preferences.meditationReminderMinute
                    ),
                    trailing = {
                        Switch(
                            checked = preferences.meditationReminderEnabled && notificationPermissionGranted,
                            onCheckedChange = { enabled ->
                                onReminderToggle(ReminderScheduler.TYPE_MEDITATION, enabled)
                            }
                        )
                    },
                    onClick = { showReminderTimePicker(ReminderScheduler.TYPE_MEDITATION) }
                )
                DividerLine()
                ActionRow(
                    "睡眠提醒",
                    formatReminderTime(
                        preferences.sleepReminderHour,
                        preferences.sleepReminderMinute
                    ),
                    trailing = {
                        Switch(
                            checked = preferences.sleepReminderEnabled && notificationPermissionGranted,
                            onCheckedChange = { enabled ->
                                onReminderToggle(ReminderScheduler.TYPE_SLEEP, enabled)
                            }
                        )
                    },
                    onClick = { showReminderTimePicker(ReminderScheduler.TYPE_SLEEP) }
                )
            }
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                SectionTitle("隐私与数据")
                Spacer(Modifier.height(8.dp))
                val micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                ActionRow(
                    "传感器权限",
                    "麦克风 ${if (micGranted) "已允许" else "未允许"} · 通知 ${if (notificationPermissionGranted) "已允许" else "未允许，点击申请"}",
                    onClick = if (Build.VERSION.SDK_INT >= 33 && !notificationPermissionGranted) {
                        {
                            pendingReminderType = null
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        null
                    }
                )
                DividerLine()
                ActionRow(
                    "允许灵恋读取睡眠/冥想摘要",
                    "只读、仅已结束的摘要，可随时关闭",
                    trailing = {
                        Switch(
                            checked = preferences.linglianWellnessSharingEnabled,
                            onCheckedChange = { enabled ->
                                updatePreferences(preferences.copy(linglianWellnessSharingEnabled = enabled))
                            }
                        )
                    }
                )
                DividerLine()
                ActionRow("导出 JSON", "完整结构化记录", onClick = {
                    exportContent = repository.exportJson()
                    exportJson.launch("lingqi-data.json")
                })
                DividerLine()
                ActionRow("导出 CSV", "冥想与睡眠摘要", onClick = {
                    exportContent = repository.exportCsv()
                    exportCsv.launch("lingqi-summary.csv")
                })
                DividerLine()
                ActionRow("删除全部本地数据", "此操作不可撤销", onClick = { deleteDialog = true })
            }
        }
        item {
            Text(
                "音频策略：系统中文 TTS、离线环境声，以及可选的真实摆钟或程序生成短铃。素材来源、作者和授权记录随版本附带。\n\n睡眠阶段为健康趋势估算，不用于医疗诊断。",
                color = LingqiMuted,
                fontSize = 10.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
            )
        }
    }

    if (nicknameDialog) {
        var draft by remember(preferences.nickname) { mutableStateOf(preferences.nickname) }
        AlertDialog(
            onDismissRequest = { nicknameDialog = false },
            title = { Text("昵称") },
            text = { OutlinedTextField(value = draft, onValueChange = { draft = it.take(12) }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    updatePreferences(preferences.copy(nickname = draft.trim().ifBlank { "旅人" }))
                    nicknameDialog = false
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { nicknameDialog = false }) { Text("取消") } }
        )
    }
    if (breathingCueDialog) {
        BreathingCueSoundDialog(
            selected = preferences.breathingCueSound,
            onSelect = { sound ->
                updatePreferences(preferences.copy(breathingCueSound = sound))
                breathingCueDialog = false
            },
            onDismiss = { breathingCueDialog = false }
        )
    }
    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text("删除全部数据？") },
            text = { Text("冥想记录、睡眠报告和偏好都会从本机彻底删除。") },
            confirmButton = {
                TextButton(onClick = {
                    deletionCoordinator.deleteAll()
                    preferences = UserPreferences()
                    stats = UserStats()
                    deleteDialog = false
                }) { Text("彻底删除") }
            },
            dismissButton = { TextButton(onClick = { deleteDialog = false }) { Text("取消") } }
        )
    }
}

internal data class ProfileData(
    val preferences: UserPreferences,
    val stats: UserStats
)

internal suspend fun loadProfileData(
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    load: () -> ProfileData
): ProfileData = withContext(dispatcher) { load() }

@Composable
fun BreathingCueSoundDialog(
    selected: BreathingCueSound,
    onSelect: (BreathingCueSound) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("呼吸提示音") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                BreathingCueSound.entries.forEach { sound ->
                    TextButton(
                        onClick = { onSelect(sound) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (sound == selected) "●" else "○")
                            Spacer(Modifier.size(12.dp))
                            Text(sound.label)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
