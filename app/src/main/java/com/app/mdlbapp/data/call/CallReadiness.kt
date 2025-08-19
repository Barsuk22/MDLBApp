@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.data.call

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.app.mdlbapp.CALLS_CH_ID
import com.app.mdlbapp.ui.call.IncomingCallActivity

// ——— помощники проверки ———
private fun overlayAllowed(ctx: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        Settings.canDrawOverlays(ctx)
    else true

private fun notificationsEnabled(ctx: Context): Boolean =
    NotificationManagerCompat.from(ctx).areNotificationsEnabled()

private fun fullScreenAllowed(ctx: Context): Boolean {
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

private fun callChannelHigh(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 26) return true
    val nm = ctx.getSystemService(NotificationManager::class.java)
    val ch = nm.getNotificationChannel(CALLS_CH_ID) ?: return false
    return ch.importance >= NotificationManager.IMPORTANCE_HIGH
}

private fun batteryOptimOk(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
    val pm = ctx.getSystemService(PowerManager::class.java)
    return pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
}

private fun isMiui(): Boolean {
    val b = Build.BRAND ?: ""
    val m = Build.MANUFACTURER ?: ""
    return listOf("XIAOMI","REDMI","POCO").any {
        b.equals(it, true) || m.equals(it, true)
    }
}

// ——— глобальный хост — показываем окошки всегда, если что-то не включено ———
private fun isStepOk(ctx: Context, step: ReadinessStep): Boolean = when (step) {
    ReadinessStep.NOTIF   -> NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    ReadinessStep.FS      -> fullScreenAllowed(ctx)
    ReadinessStep.CHANNEL -> callChannelHigh(ctx)
    ReadinessStep.BATTERY -> batteryOptimOk(ctx)
    ReadinessStep.MIUI_TIPS -> false // ← показывать, пока юзер сам не отключит подсказки
}

@Composable
fun AutoCallReadinessWindows() {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var show by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(emptyList<ReadinessStep>()) }
    var idx by rememberSaveable { mutableStateOf(0) }

    fun refresh() {
        val q = buildQueue(ctx)
        queue = q
        // если очередь пустая — закрываемся; иначе показываем текущий шаг
        show = q.isNotEmpty()
        if (idx >= q.size) idx = 0
        // если текущий шаг уже починен — перепрыгнем к следующему
        while (show && idx < q.size && isStepOk(ctx, q[idx])) idx++
        if (idx >= q.size) { show = false; idx = 0 }
    }

    LaunchedEffect(Unit) { refresh() }
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    if (show && idx < queue.size) {
        val step = queue[idx]
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { show = false }, sheetState = sheetState) {
            StepByStepReadinessSheet(
                step = step,
                onDidAction = { refresh() },     // после кликов — перепроверим
                onNext = {
                    idx++
                    if (idx >= queue.size) refresh() else refresh()
                },
                onCloseNow = { show = false }
            )
        }
    }
}



// Обёртка: «перед звонком». Если всё ок — сразу run(); иначе — открываем окна.
@Composable
fun rememberCallReadinessLauncher(runWhenReady: () -> Unit): () -> Unit {
    val ctx = LocalContext.current
    var pending by remember { mutableStateOf(false) }

    // когда pending=true — проверяем и открываем нужный первый экран,
    // а глобальный лист покажет шаги по одному на onResume
    LaunchedEffect(pending) {
        if (!pending) return@LaunchedEffect
        pending = false

        val queue = buildQueue(ctx)
        if (queue.isEmpty()) {
            runWhenReady()
            return@LaunchedEffect
        }

        when (queue.first()) {
            ReadinessStep.NOTIF   -> openAppNotificationSettings(ctx)
            ReadinessStep.FS      -> openFsIntentSettings(ctx)
            ReadinessStep.CHANNEL -> openCallChannelSettings(ctx)
            ReadinessStep.BATTERY -> openBatteryOpt(ctx)
            ReadinessStep.MIUI_TIPS -> openMiuiPermEditor(ctx)
        }
    }

    // вернём функцию, которую можно повесить на onClick
    return { pending = true }
}


// ——— какие шаги бывают ———
private enum class ReadinessStep { NOTIF, FS, CHANNEL, BATTERY, MIUI_TIPS }

// соберём очередь только из «пропавших» шагов в нужном порядке
private fun buildQueue(ctx: Context): List<ReadinessStep> {
    val m = MissingChecks.build(ctx) // тут уже проставляется showMiuiTips по бренду
    val q = mutableListOf<ReadinessStep>()
    if (m.needNotif)   q += ReadinessStep.NOTIF
    if (m.needFs)      q += ReadinessStep.FS
    if (m.needChannel) q += ReadinessStep.CHANNEL
    if (m.needBattery) q += ReadinessStep.BATTERY

    val miuiDismissed = ctx.getSharedPreferences("ready", 0)
        .getBoolean("miui_tips_dismissed", false)
    if (m.showMiuiTips && !miuiDismissed) q += ReadinessStep.MIUI_TIPS

    return q
}


// ——— UI лист окошек ———

@Composable
private fun StepByStepReadinessSheet(
    step: ReadinessStep,
    onDidAction: () -> Unit,
    onNext: () -> Unit,
    onCloseNow: () -> Unit
) {
    val ctx = LocalContext.current

    // launcher для POST_NOTIFICATIONS (Android 13+)
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onDidAction() }

    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Давай всё включим, малыш, шажочком 🐾", style = MaterialTheme.typography.titleMedium)

        when (step) {
            ReadinessStep.NOTIF -> StepCard(
                title = "Разрешить уведомления",
                desc  = "Иначе звонок может спрятаться.",
                primary = if (Build.VERSION.SDK_INT >= 33) "Разрешить" else "Открыть настройки",
                onPrimary = {
                    if (Build.VERSION.SDK_INT >= 33) {
                        notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else openAppNotificationSettings(ctx)
                },
                secondary = "Настройки приложеньки",
                onSecondary = { openAppNotificationSettings(ctx) },
                onDone = onDidAction
            )

            ReadinessStep.FS -> StepCard(
                title = "Полноэкранный звонок поверх замка",
                desc  = "Разреши показывать входящий звонок во весь экран.",
                primary = "Открыть разрешение",
                onPrimary = { openFsIntentSettings(ctx) },
                onDone = onDidAction
            )

            ReadinessStep.CHANNEL -> StepCard(
                title = "Канал «Входящие звонки»",
                desc  = "Сделай важность ВЫСОКАЯ и включи звук.",
                primary = "Открыть канал",
                onPrimary = { openCallChannelSettings(ctx) },
                onDone = onDidAction
            )

            ReadinessStep.BATTERY -> StepCard(
                title = "Не душить батарейкой",
                desc  = "Разреши работать в фоне, чтобы звонки не опаздывали.",
                primary = "Разрешить",
                onPrimary = { requestBatteryDialogIfPossible(ctx) }, // ← системное окно, если доступно
                onDone = onDidAction
            )

            ReadinessStep.MIUI_TIPS -> Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1) "Другие разрешения" MIUI — всплывашки, экран блокировки, ярлыки
                StepCard(
                    title = "MIUI: Другие разрешения",
                    desc  = "Включи «Всплывающие окна», «На экране блокировки», «Создавать ярлыки».",
                    primary = "Открыть MIUI-разрешения",
                    onPrimary = { openMiuiPermEditor(ctx); onDidAction() },
                    onDone = onDidAction
                )

                // 2) Автозапуск и Батарея (твои готовые ярлыки)
                StepButtonsRow(
                    "MIUI: Автозапуск" to { openMiuiAutostart(ctx); onDidAction() },
                    "MIUI: Батарея"    to { openMiuiBattery(ctx);  onDidAction() }
                )

                // 3) Поверх других приложений + Уведомления/Экран блокировки
                StepButtonsRow(
                    "Поверх других приложений"      to { openOverlaySettings(ctx);            onDidAction() },
                    "Уведомления / Экран блокировки" to { openAppNotificationSettings(ctx);    onDidAction() }
                )

                // 4) Системное окно «Закрепить ярлык»
                StepButtonsRow(
                    "Закрепить ярлык" to { requestPinShortcutSample(ctx); onDidAction() }
                )

                // 5) Можно скрыть MIUI-подсказки на этом устройстве
                TextButton(onClick = {
                    ctx.getSharedPreferences("ready", 0)
                        .edit().putBoolean("miui_tips_dismissed", true).apply()
                    onDidAction()
                }) { Text("Не напоминать для MIUI на этом устройстве") }

                // 5а) «Готово» — закрыть MIUI-подсказки
                TextButton(onClick = {
                    ctx.getSharedPreferences("ready", 0)
                        .edit().putBoolean("miui_tips_dismissed", true).apply()
                    onDidAction()
                }) { Text("Готово — я всё включил") }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCloseNow) { Text("Потом") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

// ——— Overlay (поверх других приложений) — системная страница ———
private fun openOverlaySettings(ctx: Context) {
    // сначала пробуем MIUI "другие разрешения" для конкретного пакета
    if (isMiui()) {
        openMiuiPermEditor(ctx)
        return
    }
    // далее — стандартная системная страница overlay
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    } else {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

// ——— Системное окно «Закрепить ярлык» (Android 8+) ———
private fun requestPinShortcutSample(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val sm = ctx.getSystemService(android.content.pm.ShortcutManager::class.java)
        if (sm?.isRequestPinShortcutSupported == true) {
            val shortcut = android.content.pm.ShortcutInfo.Builder(ctx, "mdlb_chat")
                .setShortLabel("Чат с Мамочкой")
                .setIntent(
                    Intent(ctx, com.app.mdlbapp.MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .putExtra("openChat", true)
                )
                .build()
            sm.requestPinShortcut(shortcut, null) // ← покажет системное окно закрепления
        } else {
            // На старых MIUI может требоваться тумблер «Создавать ярлыки» в «Других разрешениях»
            openMiuiPermEditor(ctx)
        }
    } else {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}

private fun requestBatteryDialogIfPossible(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val pm = ctx.getSystemService(PowerManager::class.java)
        val pkg = ctx.packageName
        if (!pm.isIgnoringBatteryOptimizations(pkg)) {
            runCatching {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                        .setData(Uri.parse("package:$pkg"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.onFailure {
                // запасной вариант — открываем список в настройках
                ctx.startActivity(
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

// ——— Full-screen intent (Android 14+) ———
private fun openFsIntentSettings(ctx: Context) {
    if (Build.VERSION.SDK_INT >= 34) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { openAppNotificationSettings(ctx) }
    } else openAppNotificationSettings(ctx)
}

@Composable
private fun StepCard(
    title: String,
    desc: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    onDone: () -> Unit
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onPrimary(); onDone() }) { Text(primary) }
                if (secondary != null && onSecondary != null) {
                    OutlinedButton(onClick = { onSecondary(); onDone() }) { Text(secondary) }
                }
            }
        }
    }
}

@Composable
private fun StepButtonsRow(vararg pairs: Pair<String, () -> Unit>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        pairs.forEach { (t, a) ->
            OutlinedButton(onClick = a, modifier = Modifier.fillMaxWidth()) { Text(t) }
        }
    }
}

// ——— модель «чего не хватает» ———
data class MissingChecks(
    val needNotif: Boolean = false,
    val needFs: Boolean = false,
    val needChannel: Boolean = false,
    val needBattery: Boolean = false,
    val showMiuiTips: Boolean = false
) {
    val allOk: Boolean get() = !needNotif && !needFs && !needChannel
    val hasAny: Boolean get() = !allOk

    companion object {
        fun build(ctx: Context): MissingChecks {
            val notif   = !notificationsEnabled(ctx)
            val fs      = !fullScreenAllowed(ctx)
            val channel = !callChannelHigh(ctx)
            val battery = !batteryOptimOk(ctx)
            val miui    = isMiui()

            val miuiDismissed = ctx.getSharedPreferences("ready", 0)
                .getBoolean("miui_tips_dismissed", false)

            // ПОДСТАВЬ ЭТО:
            val showMiui = miui && !miuiDismissed && !overlayAllowed(ctx)

            return MissingChecks(
                needNotif = notif,
                needFs = fs,
                needChannel = channel,
                needBattery = battery,
                showMiuiTips = showMiui
            )
        }
    }
}

// ——— открывашки системных экранов ———
private fun openAppNotificationSettings(ctx: Context) {
    val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }
}

private fun openCallChannelSettings(ctx: Context) {
    val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, CALLS_CH_ID)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { ctx.startActivity(i) }
}

private fun openBatteryOpt(ctx: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val p = ctx.packageName
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData("package:$p".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    } else {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun openMiuiPermEditor(ctx: Context) {
    val pkg = ctx.packageName
    val intents = listOf(
        // 1) стандартный action
        Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", pkg),

        // 2) самые частые активити на MIUI/HyperOS
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.AppPermissionsEditorActivity"
        ).putExtra("extra_pkgname", pkg),

        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity"
        ).putExtra("extra_pkgname", pkg),

        // ВНИМАНИЕ: здесь экранируем $
        Intent().setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity\$AppDetailActivity"
        ).putExtra("extra_pkgname", pkg)
    )

    for (i in intents) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ctx.startActivity(i)
            return
        } catch (_: Throwable) { /* пробуем следующий */ }
    }

    // фолбэк — карточка приложения в системных настройках
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun openMiuiAutostart(ctx: Context) {
    val intents = listOf(
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        ),
        Intent("miui.intent.action.APP_PERM_EDITOR")
            .putExtra("extra_pkgname", ctx.packageName)
    )
    for (i in intents) {
        try {
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Throwable) {}
    }
    openMiuiPermEditor(ctx)
}

private fun openMiuiBattery(ctx: Context) {
    runCatching {
        val i = Intent().apply {
            component = ComponentName("com.miui.powerkeeper","com.miui.powerkeeper.ui.HiddenAppsConfigActivity")
            putExtra("package_name", ctx.packageName)
            putExtra("package_label", "MDLBApp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(i)
    }.onFailure {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
