@file:OptIn(ExperimentalMaterial3Api::class)

package com.app.mdlbapp.data.call

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.app.mdlbapp.CALLS_CH_ID
import com.app.mdlbapp.data.call.FullscreenIntentPrompt   // твой компоузик
import com.app.mdlbapp.data.call.CallPermissionsShortcuts // твои ярлычки MIUI

@Composable
fun CallSetupWizard(
    onAllGood: () -> Unit
) {
    val ctx = LocalContext.current

    // Детектор бренда для «умных» подсказок
    val isXiaomi = remember {
        listOf("XIAOMI","REDMI","POCO").any { Build.BRAND.equals(it, true) || Build.MANUFACTURER.equals(it, true) }
    }

    // Шаг 1 — Разрешение на уведомления (Android 13+)
    val needsNotifPerm = remember {
        Build.VERSION.SDK_INT >= 33 &&
                !NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    // Шаг 2 — Полноэкранный интент (Android 14+): проверка через AppOps (у тебя есть готовый UI)
    fun isFsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val appOps = ctx.getSystemService(AppOpsManager::class.java)
        val mode = try {
            appOps.unsafeCheckOpNoThrow("android:use_full_screen_intent",
                android.os.Process.myUid(), ctx.packageName)
        } catch (_: Throwable) {
            AppOpsManager.MODE_ALLOWED
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
    var needsFs by remember { mutableStateOf(!isFsAllowed()) }

    // Шаг 3 — Канал звонков: есть ли и важность HIGH
    fun isCallChannelReady(): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = nm.getNotificationChannel(CALLS_CH_ID) ?: return false
        return ch.importance >= NotificationManager.IMPORTANCE_HIGH
    }
    var needsChannelFix by remember { mutableStateOf(!isCallChannelReady()) }

    // Шаг 4 — Вендорские ярлыки (MIUI): показываем только на соответствующих устройствах
    val needsMiuiHelp = isXiaomi

    // Кнопочки действий
    fun openAppNotifications() {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        ctx.startActivity(i)
    }
    fun openCallChannelSettings() {
        val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, CALLS_CH_ID)
        ctx.startActivity(i)
    }
    fun requestIgnoreBattery() {
        val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        ctx.startActivity(i)
    }

    // UI
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Проверка звонков", style = MaterialTheme.typography.headlineSmall)
        Text("Малыш, давай всё включим, чтобы звонки всплывали большими окошками даже на замке экрана 😊")

        // Шаг 1: Уведомления
        if (needsNotifPerm) StepCard(
            title = "Разреши уведомления",
            desc  = "Иначе звонок может спрятаться.",
            actionLabel = "Открыть",
            onAction = { openAppNotifications() },
            onRecheck = {
                // простая перепроверка
                if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
                    // обновляем локальное состояние
                }
            }
        )

        // Шаг 2: Полноэкранные уведомления (Android 14+)
        if (Build.VERSION.SDK_INT >= 34) StepCard(
            title = "Полноэкранный звонок",
            desc  = "Разреши показывать экран звонка поверх замка экрана.",
            actionLabel = "Открыть",
            onAction = {
                val i = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                }
                ctx.startActivity(i)
            },
            onRecheck = { needsFs = !isFsAllowed() }
        )

        // Шаг 3: Канал звонков (важность HIGH, звук)
        if (needsChannelFix) StepCard(
            title = "Канал «Входящие звонки»",
            desc  = "Проверь, что важность — ВЫСОКАЯ и включён звук.",
            actionLabel = "Открыть",
            onAction = { openCallChannelSettings() },
            onRecheck = { needsChannelFix = !isCallChannelReady() }
        )

        // Шаг 4: MIUI-помощь (покажем твои кнопки-ярлыки)
        if (needsMiuiHelp) {
            Divider()
            Text("Подсказки для MIUI", style = MaterialTheme.typography.titleMedium)
            CallPermissionsShortcuts() // твои готовые кнопки-ярлыки. :contentReference[oaicite:5]{index=5}
        }

        // Шаг 5: (опц.) Игнор оптимизаций батареи
        StepCard(
            title = "Не душить приложение батарейкой",
            desc  = "Дай приложению работать в фоне, чтобы звонок не опоздал.",
            actionLabel = "Открыть",
            onAction = { requestIgnoreBattery() }
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = {
                // итоговая проверка — всё ли готово
                val ok = NotificationManagerCompat.from(ctx).areNotificationsEnabled() &&
                        (Build.VERSION.SDK_INT < 34 || isFsAllowed()) &&
                        isCallChannelReady()
                if (ok) onAllGood() else Unit
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Готово!") }
    }
}

@Composable
private fun StepCard(
    title: String,
    desc: String,
    actionLabel: String,
    onAction: () -> Unit,
    onRecheck: (() -> Unit)? = null
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onAction) { Text(actionLabel) }
                if (onRecheck != null) TextButton(onClick = onRecheck) { Text("Проверить") }
            }
        }
    }
}
