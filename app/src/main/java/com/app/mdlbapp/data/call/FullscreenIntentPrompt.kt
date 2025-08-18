package com.app.mdlbapp.data.call

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// На некоторых SDK константы нет — берём строковый опкод.
private const val OPSTR_USE_FULL_SCREEN_INTENT = "android:use_full_screen_intent"

private fun isFsAllowed(ctx: Context): Boolean {
    if (Build.VERSION.SDK_INT < 34) return true
    val appOps = ctx.getSystemService(AppOpsManager::class.java)
    val mode = try {
        appOps.unsafeCheckOpNoThrow(
            OPSTR_USE_FULL_SCREEN_INTENT,
            android.os.Process.myUid(),
            ctx.packageName
        )
    } catch (_: Throwable) {
        AppOpsManager.MODE_ALLOWED // если что-то пошло не так—не мучаем малыша
    }
    return mode == AppOpsManager.MODE_ALLOWED
}

@Composable
fun FullscreenIntentPrompt(
    modifier: Modifier = Modifier // можно подвинуть «пониже» из места вызова
) {
    if (Build.VERSION.SDK_INT < 34) return

    val ctx = LocalContext.current
    var needs by remember { mutableStateOf(!isFsAllowed(ctx)) }

    // Перепроверяем, когда возвращаемся из настроек (на RESUME)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, ev ->
            if (ev == Lifecycle.Event.ON_RESUME) {
                needs = !isFsAllowed(ctx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    AnimatedVisibility(visible = needs) {
        Surface(
            tonalElevation = 2.dp,
            modifier = modifier
        ) {
            ListItem(
                headlineContent = { Text("Разреши полноэкранные уведомления для звонков") },
                supportingContent = {
                    Text("Иначе на заблокированном экране будет только тихая шторка.")
                },
                trailingContent = {
                    TextButton(onClick = {
                        val i = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(i)
                    }) { Text("Открыть") }
                }
            )
        }
    }
}
