package com.app.mdlbapp.data.call

import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.mdlbapp.R
import com.app.mdlbapp.MainActivity // или твой экран звонка-роутер
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallOngoingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun canPostNotifications(): Boolean {
        // На 13+ нужно явное разрешение
        return Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(
                    this, android.Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_START = "call.ACTION_START_ONGOING"      // запустить/обновить
        const val ACTION_CONNECTED = "call.ACTION_CONNECTED"       // отметить, что соединение установлено (для таймера)
        const val ACTION_HANGUP = "call.ACTION_HANGUP"             // завершить
        const val EXTRA_TID = "tid"
        const val EXTRA_PEER_UID = "peerUid"
        const val EXTRA_PEER_NAME = "peerName"
        const val EXTRA_CALL_ID = "callId"

        const val EXTRA_AS_CALLER = "asCaller"
        const val NOTIF_ID = 2221
        const val CH_ID = "CALL_ONGOING_CH_ID"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val oldTid = CallRuntime.tid
                val oldCid = CallRuntime.callId

                intent.getStringExtra(EXTRA_TID)?.let { CallRuntime.tid = it }
                intent.getStringExtra(EXTRA_PEER_UID)?.let { CallRuntime.peerUid = it }
                intent.getStringExtra(EXTRA_PEER_NAME)?.let { CallRuntime.peerName = it }
                intent.getStringExtra(EXTRA_CALL_ID)?.let { CallRuntime.callId = it }
                CallRuntime.asCaller = intent.getBooleanExtra(EXTRA_AS_CALLER, CallRuntime.asCaller ?: false)

                // 🔁 Новый звонок? — сбросить таймер/флаги
                val isNewCall = (oldTid != CallRuntime.tid) || (oldCid != CallRuntime.callId)
                if (isNewCall) {
                    CallRuntime.callStartedAtUptimeMs = null
                    CallRuntime.connected.value = false
                }

                startForegroundCompat(buildNotification())
            }

            ACTION_CONNECTED -> {
                if (CallRuntime.callStartedAtUptimeMs == null) {
                    CallRuntime.callStartedAtUptimeMs = android.os.SystemClock.elapsedRealtime()
                }
                CallRuntime.connected.value = true
                updateNotification()
            }

            ACTION_HANGUP -> {
                serviceScope.launch {
                    runCatching {
                        val t = CallRuntime.tid
                        val c = CallRuntime.callId
                        if (!t.isNullOrBlank() && !c.isNullOrBlank()) {
                            CallRepository.setState(t, c, "ended")
                        }
                    }
                    withContext(Dispatchers.Main) {
                        runCatching { CallRuntime.rtc?.endCall() }
                        CallRuntime.rtc = null
                        CallRuntime.connected.value = false
                        // 🧹 Полный сброс
                        CallRuntime.callStartedAtUptimeMs = null
                        CallRuntime.tid = null
                        CallRuntime.callId = null
                        CallRuntime.peerUid = null
                        CallRuntime.peerName = null
                        CallRuntime.asCaller = null

                        stopForeground(true)
                        stopSelf()
                    }
                }
            }
        }
        // ⛔️ не даём сервису оживать с “прошлым состоянием”
        return START_NOT_STICKY
    }


    private fun startForegroundCompat(n: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                val fgsType =
                    if (Build.VERSION.SDK_INT >= 34)
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
                    else 0
                startForeground(NOTIF_ID, n, fgsType)
            } else {
                startForeground(NOTIF_ID, n)
            }
        } catch (_: SecurityException) {
            // если внезапно нет прав на уведомления — всё равно запускаем с простым уведомлением
            val fallback = NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                .setContentTitle("Идёт звонок")
                .setOngoing(true)
                .build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, fallback, 0)
            else startForeground(NOTIF_ID, fallback)
        }
    }

    private fun updateNotification() {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification())
        } catch (_: SecurityException) { /* тихо игнорим */ }
    }

    private fun buildNotification(): Notification {
        val title = CallRuntime.peerName?.let { "Звонок: $it" } ?: "Идёт звонок"
        val text  = if (CallRuntime.connected.value) "Соединение установлено" else "Соединяемся…"

        val targetIntent =
            if (CallRuntime.asCaller == true) {
                Intent(this, com.app.mdlbapp.ui.call.OutgoingCallActivity::class.java).apply {
                    putExtra("tid", CallRuntime.tid)
                    putExtra("peerUid", CallRuntime.peerUid)
                    putExtra("peerName", CallRuntime.peerName)
                    putExtra("resume", true)  // ← ВАЖНО
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            } else {
                Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                    putExtra("fromName", CallRuntime.peerName)
                    putExtra("callerUid", CallRuntime.peerUid)
                    putExtra("autoAccept", false)
                    putExtra("resume", true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
            }

        val contentIntent = PendingIntent.getActivity(
            this, 10, targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = PendingIntent.getService(
            this, 11,
            Intent(this, CallOngoingService::class.java).setAction(ACTION_HANGUP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)     // ← теперь ведёт на full-screen
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Завершить", hangupIntent)

        CallRuntime.callStartedAtUptimeMs?.let { startedElapsed ->
            val elapsedNow = android.os.SystemClock.elapsedRealtime()
            val wallNow    = System.currentTimeMillis()
            val whenWall   = wallNow - (elapsedNow - startedElapsed)
            builder.setUsesChronometer(true).setShowWhen(true).setWhen(whenWall)
        }
        return builder.build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (nm.getNotificationChannel(CH_ID) == null) {
                val ch = NotificationChannel(
                    CH_ID, "Идёт звонок", NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Постоянное уведомление во время звонка"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
                nm.createNotificationChannel(ch)
            }
        }
    }
}
