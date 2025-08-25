// app/src/main/java/com/app/mdlbapp/data/call/AppFirebaseMessagingService.kt
package com.app.mdlbapp.data.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.mdlbapp.CALLS_CH_ID
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {

        val type = message.data["type"]

        when (type) {
            // ВХОДЯЩИЙ ЗВОНОК
            "call" -> {
                val callerUid   = message.data["fromUid"]   ?: return
                val callerName  = message.data["fromName"]  ?: "Мамочка"
                val callerAvatar= message.data["fromAvatar"] // может быть null

                // Запускаем наш входящий сервис (FGS), он уже поднимет full-screen уведомление
                val i = Intent(this, IncomingCallService::class.java).apply {
                    setAction(IncomingCallService.ACTION_RING)   // ⬅️ главное!
                    putExtra("fromUid",  message.data["fromUid"] ?: "")
                    putExtra("fromName", message.data["fromName"] ?: "Мамочка")
                    putExtra("fromAvatar", message.data["fromAvatar"])
                }
                try {
                    ContextCompat.startForegroundService(this, i)
                } catch (e: Exception) {
                    Log.e("FCM", "startForegroundService failed: ${e.message}")
                    // Фолбэк — хотя бы обычное heads-up
                    showFallbackIncomingNotification(callerUid, callerName)
                }
            }

            // ОТМЕНА ЗВОНКА
            "call_cancel" -> {
                // Скажем сервису аккуратно закрыться (если он крутится)
                try {
                    val i = android.content.Intent(this, IncomingCallService::class.java)
                        .setAction(IncomingCallService.ACTION_DISMISS)
                    startService(i)
                } catch (_: Exception) {}
                // На всякий случай снимем heads-up (фолбэк)
                runCatching { NotificationManagerCompat.from(this).cancel(42) }
            }
        }
    }

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            Firebase.firestore.collection("users").document(uid)
                .update("fcmTokens", FieldValue.arrayUnion(token))
        }
    }

    // Тихий канал + heads-up на случай, если FGS не стартовал (редко)
    private fun showFallbackIncomingNotification(callerUid: String, callerName: String) {
        ensureCallChannel()

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val fullPi = PendingIntent.getActivity(
            this, 300,
            android.content.Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                putExtra("openCall", true)
                putExtra("callerUid", callerUid)
                putExtra("fromName", callerName)
                putExtra("autoAccept", false)
                addFlags(
                    android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val person = androidx.core.app.Person.Builder().setName(callerName).build()

        val notif = NotificationCompat.Builder(this, CALLS_CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle("Входящий звонок")
            .setContentText("$callerName звонит…")
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, null, null))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullPi, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(this).notify(42, notif)
    }

    private fun ensureCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm.getNotificationChannel(CALLS_CH_ID)
            if (existing == null || existing.sound != null) {
                if (existing != null) nm.deleteNotificationChannel(CALLS_CH_ID)
                val ch = NotificationChannel(
                    CALLS_CH_ID, "Входящие звонки",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Полноэкранные вызовы"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null) // звук даёт сервис
                    enableVibration(true)
                    enableLights(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
