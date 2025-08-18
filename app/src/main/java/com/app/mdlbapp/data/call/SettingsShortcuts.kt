package com.app.mdlbapp.data.call

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun CallPermissionsShortcuts(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Button(onClick = {
            // Откроет канал "Входящие звонки"
            val i = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, com.app.mdlbapp.CALLS_CH_ID)
            ctx.startActivity(i)
        }) { Text("Открыть настройки канала «Входящие звонки»") }

        Button(onClick = {
            // Общие уведомления для приложения
            val i = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            ctx.startActivity(i)
        }) { Text("Открыть уведомления приложения") }

        Button(onClick = {
            // MIUI: редактор разрешений (всплывающие окна / запуск в фоне)
            val intents = listOf(
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setClassName("com.miui.securitycenter",
                        "com.miui.permcenter.permissions.PermissionsEditorActivity")
                    .putExtra("extra_pkgname", ctx.packageName),
                Intent("miui.intent.action.APP_PERM_EDITOR")
                    .setPackage("com.miui.securitycenter")
                    .putExtra("extra_pkgname", ctx.packageName)
            )
            var ok = false
            for (it in intents) try { ctx.startActivity(it); ok = true; break } catch (_: Throwable) {}
            if (!ok) {
                // fallback: откроем страницу приложения в настройках
                val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${ctx.packageName}"))
                ctx.startActivity(i)
            }
        }) { Text("MIUI: Всплывающие окна/Запуск в фоне") }

        Button(onClick = {
            // Батарея → Без ограничений (обычно пользователь дальше сам ставит)
            try {
                val i = Intent().apply {
                    component = ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"
                    )
                    putExtra("package_name", ctx.packageName)
                    putExtra("package_label", "MDLBApp")
                }
                ctx.startActivity(i)
            } catch (_: ActivityNotFoundException) {
                val i = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                ctx.startActivity(i)
            }
        }) { Text("MIUI: Без ограничений по батарее") }
    }
}