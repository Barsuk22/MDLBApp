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

        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("openCall", true)
            putExtra("callerUid", callerUid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CALLS_CH_ID)   // 👈 единый канал!
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Входящий звонок")
            .setContentText("$callerName звонит тебе 📞")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(pendingIntent, true)
            .setOngoing(true)
            .build()

        NotificationManagerCompat.from(this).notify(1001, notif)
    }

    private fun ensureCallChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)!!
            if (nm.getNotificationChannel(CALLS_CH_ID) == null) {
                val ch = NotificationChannel(
                    CALLS_CH_ID, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Показывает экран входящего звонка"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                    // Делаем канал «звонковым»
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                    val attrs = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(uri, attrs)
                    try { setBypassDnd(true) } catch (_: Throwable) {}
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
