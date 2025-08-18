package com.app.mdlbapp.data.call

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.mdlbapp.CALLS_CH_ID
import com.app.mdlbapp.MainActivity
import com.app.mdlbapp.R

class IncomingCallService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromName = intent?.getStringExtra("fromName") ?: "Мамочка"
        val fromUid  = intent?.getStringExtra("fromUid")  ?: ""

        ensureCallChannel()

        val tap = Intent(this, MainActivity::class.java).apply {
            putExtra("openCall", true)
            putExtra("callerUid", fromUid)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            this, 0, tap,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CALLS_CH_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Входящий звонок")
            .setContentText("$fromName звонит тебе 📞")
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setFullScreenIntent(pi, true)
            .setOngoing(true)
            .build()

        try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    42, notif,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                )
            } else {
                startForeground(42, notif)
            }
            android.os.Handler(mainLooper).postDelayed({ stopSelf() }, 120_000)
            return START_NOT_STICKY
        } catch (t: Throwable) {
            android.util.Log.e("IncomingCallService", "FGS failed", t)

            // 🔐 Линтер просит — проверяем разрешение на Android 13+
            if (android.os.Build.VERSION.SDK_INT >= 33) {
                val granted = ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    stopSelf()
                    return START_NOT_STICKY
                }
            }

            // фолбэк: покажем хотя бы уведомление
            NotificationManagerCompat.from(this).notify(421, notif)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureCallChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CALLS_CH_ID) == null) {
                val ch = NotificationChannel(
                    CALLS_CH_ID, "Входящие звонки",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Полноэкранные вызовы"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    enableVibration(true); enableLights(true)
                    val uri = android.media.RingtoneManager.getDefaultUri(
                        android.media.RingtoneManager.TYPE_RINGTONE
                    )
                    val attrs = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                    setSound(uri, attrs)
                    try { setBypassDnd(true) } catch (_: Throwable) {}
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
