package com.app.mdlbapp.permissions

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private fun canExact(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = context.getSystemService(AlarmManager::class.java)
        am?.canScheduleExactAlarms() == true
    } else true
}

private fun openExactAlarmSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ctx.startActivity(
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/**
 * Показываем НАШ диалог каждый раз после старта приложения,
 * если точные будильнички ещё не разрешены (Android 12+).
 * За один запуск — максимум один показ (не спамим).
 */
@Composable
fun ExactAlarmPrompt() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var allowed by remember { mutableStateOf(canExact(ctx)) }
    var shownThisSession by remember { mutableStateOf(false) }

    // Когда возвращаемся в приложение (после настроек) — перепроверяем
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                allowed = canExact(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val shouldShow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            !allowed &&
            !shownThisSession

    if (shouldShow) {
        AlertDialog(
            onDismissRequest = { shownThisSession = true },
            title = { Text("Точные будильнички") },
            text  = { Text("Разреши точные будильнички, чтобы дедлайны и штрафы срабатывали точно по времени, даже если приложение закрыто.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        shownThisSession = true
                        openExactAlarmSettings(ctx)
                    }
                ) { Text("Разрешить") }
            },
            dismissButton = {
                TextButton(onClick = { shownThisSession = true }) {
                    Text("Не сейчас")
                }
            }
        )
    }
}