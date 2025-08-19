// app/src/main/java/com/app/mdlbapp/data/call/AppFirebaseMessagingService.kt
package com.app.mdlbapp.data.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.mdlbapp.CALLS_CH_ID        // 👈 импортим общий ID канала
import com.app.mdlbapp.MainActivity
import com.app.mdlbapp.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class AppFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] == "call") {
            val name = message.data["fromName"] ?: "Мамочка"
            val uid  = message.data["fromUid"]  ?: ""
            val i = Intent(this, IncomingCallService::class.java)
                .putExtra("fromName", name)
                .putExtra("fromUid",  uid)

            try {
                ContextCompat.startForegroundService(this, i)
            } catch (e: Exception) {
                Log.e("FCM","startForegroundService failed: ${e::class.java.simpleName}: ${e.message}")
                showIncomingCallNotification(name, uid) // фолбэк в тот же канал
            }
            Log.d("FCM","got ${message.data}")
        }
    }

    override fun onNewToken(token: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        com.google.firebase.ktx.Firebase.firestore.collection("users").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
    }

    private fun showIncomingCallNotification(callerName: String, callerUid: String) {
        ensureCallChannel()

        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) return

        val person = androidx.core.app.Person.Builder()
            .setName(callerName)
            .build()

        val fullScreen = PendingIntent.getActivity(
            this, 300,
            Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                putExtra("openCall", true)
                putExtra("callerUid", callerUid)
                putExtra("fromName", callerName)
                putExtra("autoAccept", false) // <-- только показать
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val acceptAct = PendingIntent.getActivity(
            this, 301,
            Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                putExtra("openCall", true)
                putExtra("callerUid", callerUid)
                putExtra("fromName", callerName)
                putExtra("autoAccept", true) // <-- тут принимаем
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val declineSvc = PendingIntent.getService(
            this, 302,
            Intent(this, com.app.mdlbapp.data.call.IncomingCallService::class.java).apply {
                action = "com.app.mdlbapp.ACTION_DECLINE"
                putExtra("fromUid", callerUid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CALLS_CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(person, declineSvc, acceptAct)
                    .setAnswerButtonColorHint(0xFF2ECC71.toInt())
                    .setDeclineButtonColorHint(0xFFE74C3C.toInt())
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(fullScreen, true) // только открывает экран
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        NotificationManagerCompat.from(this).notify(42, notif)
    }

    private fun ensureCallChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val existing = nm.getNotificationChannel(CALLS_CH_ID)

            // если канал уже есть и в нём включён звук — удалим и создадим заново тихий
            if (existing == null || existing.sound != null) {
                if (existing != null) nm.deleteNotificationChannel(CALLS_CH_ID)
                val ch = NotificationChannel(
                    CALLS_CH_ID, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Полноэкранные вызовы"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setSound(null, null) // 🔇 никаких системных звуков — звуком рулит сервис
                    enableVibration(true)
                    enableLights(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
