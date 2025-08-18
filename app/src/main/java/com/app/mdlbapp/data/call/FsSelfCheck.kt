package com.app.mdlbapp.data.call


import android.app.AppOpsManager
import android.app.NotificationManager
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun FsSelfCheck() {
    val ctx = LocalContext.current
    val nm = ctx.getSystemService(NotificationManager::class.java)

    val ch = if (Build.VERSION.SDK_INT >= 26) nm.getNotificationChannel(com.app.mdlbapp.CALLS_CH_ID) else null
    val chOk = ch?.importance ?: NotificationManager.IMPORTANCE_HIGH >= NotificationManager.IMPORTANCE_HIGH
    val soundOk = ch?.sound != null
    val notifEnabled = nm.areNotificationsEnabled()

    val fsAllowed = if (Build.VERSION.SDK_INT >= 34) {
        val mode = ctx.getSystemService(AppOpsManager::class.java)
            .unsafeCheckOpNoThrow("android:use_full_screen_intent",
                android.os.Process.myUid(), ctx.packageName)
        mode == AppOpsManager.MODE_ALLOWED
    } else true

    ElevatedCard {
        ListItem(
            headlineContent = { Text("Full-screen статус") },
            supportingContent = {
                Text(
                    "Канал важный: $chOk  |  Звук есть: $soundOk  |  Уведомления включены: $notifEnabled  |  Full-screen разрешён: $fsAllowed"
                )
            }
        )
    }
}