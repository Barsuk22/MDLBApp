package com.app.mdlbapp.data.call

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.navigation.NavHostController
import com.app.mdlbapp.ui.call.IncomingCallActivity
import com.app.mdlbapp.CALLS_CH_ID

@Composable
fun DeviceSetupFlow(nav: NavHostController, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var step by remember { mutableStateOf(0) }

    fun next() { step++ }

    Surface {
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            Text("Давай настроим звонки, котёнок 🍼", style = MaterialTheme.typography.titleLarge)

            when (step) {
                0 -> StepNotificationsApp(ctx) { next() }
                1 -> StepCallsChannel(ctx) { next() }
                2 -> StepFullScreenTest(ctx) { next() }
                3 -> StepMiuiSpecial(ctx) { next() }
                4 -> StepBattery(ctx) {
                    saveSetupDone(ctx)
                    onDone()
                }
            }

            Spacer(Modifier.weight(1f))
            Text("Шажочек ${step+1} из 5", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun StepNotificationsApp(ctx: Context, onNext: () -> Unit) {
    Text("Шаг 1 — включим уведомления для приложения")
    Text("Открою системную страничку, там дай разрешить, ладно?")

    val open = {
        val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        }
        ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = open) { Text("Открыть уведомления") }
        OutlinedButton(onClick = { if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) onNext() else open() }) {
            Text("Готово")
        }
    }
}

@Composable
private fun StepCallsChannel(ctx: Context, onNext: () -> Unit) {
    Text("Шаг 2 — сделаем канал «Входящие звонки» самым важным")

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(
                CALLS_CH_ID, "Входящие звонки",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { setBypassDnd(true); enableVibration(true) }
            nm.createNotificationChannel(ch)
        }
    }

    val openChannel = {
        if (Build.VERSION.SDK_INT >= 26) {
            val uri: Uri = Uri.parse("package:${ctx.packageName}")
            val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                putExtra(Settings.EXTRA_CHANNEL_ID, CALLS_CH_ID)
                data = uri
            }
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = openChannel) { Text("Открыть канал") }
        OutlinedButton(onClick = onNext) { Text("Дальше") }
    }
}

@Composable
private fun StepFullScreenTest(ctx: Context, onNext: () -> Unit) {
    Text("Шаг 3 — проверим полноэкранный показ на звонок")
    Text("Нажми «Проверить». Должно распахнуться окошко звонка поверх замка.")

    val test: () -> Unit = {
        val full = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ВАЖНО: используем единый канал твоего приложения
        val n = NotificationCompat.Builder(ctx, com.app.mdlbapp.CALLS_CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Проверка звонка")
            .setContentText("Это тест полноэкранного входящего")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setFullScreenIntent(full, true)
            .build()

        // На Android 13+ без разрешения может упасть — ловим SecurityException
        try {
            NotificationManagerCompat.from(ctx).notify(1001, n)
        } catch (_: SecurityException) {
            // Нет разрешения на уведомления — просто откроем страницу уведомлений приложения
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = test) { Text("Проверить") }
        OutlinedButton(onClick = onNext) { Text("Всё ок") }
    }
}

@Composable
private fun StepMiuiSpecial(ctx: Context, onNext: () -> Unit) {
    Text("Шаг 4 — для телефонов Xiaomi (MIUI) включим спец-настройки")
    Text("Если у тебя не Xiaomi — просто жми «Пропустить».")

    fun tryOpen(cn: ComponentName): Boolean = runCatching {
        ctx.startActivity(Intent().setComponent(cn).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrNull() == true

    val openAutostart = {
        val tried = tryOpen(ComponentName("com.miui.securitycenter","com.miui.permcenter.autostart.AutoStartManagementActivity"))
        if (!tried) openAppPermEditor(ctx)
    }
    val openPopups = {
        openAppPermEditor(ctx) // там есть всплывающие/на экране блокировки
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = openAutostart) { Text("Автозапуск") }
        Button(onClick = openPopups)    { Text("Всплывающие/Блокировка") }
        OutlinedButton(onClick = onNext) { Text("Пропустить") }
    }
}

private fun openAppPermEditor(ctx: Context) {
    val i = Intent("miui.intent.action.APP_PERM_EDITOR")
        .putExtra("extra_pkgname", ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }
}

@Composable
private fun StepBattery(ctx: Context, onNext: () -> Unit) {
    Text("Шаг 5 — снимем ограничение батарейки")
    Text("Это нужно, чтобы звонки доходили даже когда малыш спит 💤")

    val openBattery: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val p = ctx.packageName
            runCatching {
                val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$p"))
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } else {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        Unit // <- чтобы лямбда точно вернула Unit
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = openBattery) { Text("Открыть настройки") }
        OutlinedButton(onClick = onNext) { Text("Готово") }
    }
}

private fun saveSetupDone(ctx: Context) {
    ctx.getSharedPreferences("device_setup", Context.MODE_PRIVATE)
        .edit().putBoolean("done", true).apply()
}
fun isSetupDone(ctx: Context) =
    ctx.getSharedPreferences("device_setup", Context.MODE_PRIVATE)
        .getBoolean("done", false)