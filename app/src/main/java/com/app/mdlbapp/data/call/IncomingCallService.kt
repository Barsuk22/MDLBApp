package com.app.mdlbapp.data.call

import android.Manifest
import android.R.attr.data
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.view.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.ListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontVariation.Settings
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.mdlbapp.CALLS_CH_ID
import com.app.mdlbapp.MainActivity
import com.app.mdlbapp.R
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class IncomingCallService : Service() {
    private var afListener = AudioManager.OnAudioFocusChangeListener { }
    private var audioManager: AudioManager? = null

    companion object {
        private const val ACTION_ACCEPT  = "com.app.mdlbapp.ACTION_ACCEPT"
        private const val ACTION_DECLINE = "com.app.mdlbapp.ACTION_DECLINE"
        private const val ACTION_SILENCE = "com.app.mdlbapp.ACTION_SILENCE"
        private const val ACTION_DISMISS = "com.app.mdlbapp.ACTION_DISMISS"

        // Глобальные защёлки
        val running = java.util.concurrent.atomic.AtomicBoolean(false)
        @Volatile var globalPlayer: MediaPlayer? = null
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private var player: android.media.MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromName = intent?.getStringExtra("fromName") ?: "Мамочка"
        val fromUid  = intent?.getStringExtra("fromUid")  ?: ""

        serviceScope.launch {
            val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                ?: return@launch
            val db = com.google.firebase.ktx.Firebase.firestore
            // найдём tid нашей пары (у тебя уже есть findTidWith — можно использовать его)
            val tid = findTidWith(fromUid, me) ?: return@launch

            // берём самый свежий RINGING, адресованный нам
            val fresh = db.collection("chats").document(tid).collection("calls")
                .whereEqualTo("callerUid", fromUid)
                .whereEqualTo("calleeUid", me)
                .whereEqualTo("state", "ringing")
                .limit(1)
                .get().await()
                .documents.firstOrNull()

            // если ничего не нашли (звонящий уже успел завершить) — тихо выходим, ничего не показываем
            if (fresh == null) {
                stopSelf()
                return@launch
            }
        }

        // ACTIONS из уведомления
        when (intent?.action) {
            ACTION_SILENCE -> {
                stopRingtone()
                return START_NOT_STICKY
            }
            ACTION_DISMISS -> { // ← корректно убрать FGS и уведомление
                stopRingtone()
                NotificationManagerCompat.from(this).cancel(42)
                stopForeground(true); stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ACCEPT -> {
                stopRingtone()
                NotificationManagerCompat.from(this).cancel(42)
                stopForeground(true); stopSelf()

                val tap = Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                    putExtra("openCall", true)
                    putExtra("callerUid", fromUid)
                    putExtra("fromName", fromName)
                    putExtra("autoAccept", true)
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                    )
                }
                try {
                    startActivity(tap)
                } catch (_: Exception) { /* на всякий — но не должен падать, мы в FGS */ }

                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_DECLINE -> {
                serviceScope.launch {
                    try { endLatestRingingForMe(fromUid) } catch (_: Throwable) {}
                    withContext(Dispatchers.Main) {
                        stopRingtone()
                        NotificationManagerCompat.from(this@IncomingCallService).cancel(42)
                        stopForeground(true); stopSelf()
                    }
                }
                return START_NOT_STICKY
            }
        }

        // обычный вход (новый звонок): если сервис уже крутится — просто выходим,
        // чтобы не стартовать второй плеер/уведомление
        if (!running.compareAndSet(false, true)) return START_NOT_STICKY
        ensureCallChannel()

        // pending intent на полноэкранку (и для фуллскрина, и для accept)
        val person = androidx.core.app.Person.Builder()
            .setName(fromName)
            .build()

        // 1) Полноэкранный интент — только открыть экран, БЕЗ autoAccept
        val piFull = PendingIntent.getActivity(
            this, 200,
            Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                putExtra("openCall", true)
                putExtra("callerUid", fromUid)
                putExtra("fromName", fromName)
                putExtra("autoAccept", false) // <-- важно!
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 2) Кнопка «Принять» — открывает экран с autoAccept=true
        val piAccept = PendingIntent.getActivity(
            this, 201,
            Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                putExtra("openCall", true)
                putExtra("callerUid", fromUid)
                putExtra("fromName", fromName)
                putExtra("autoAccept", true) // <-- только тут!
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 3) Кнопка «Отклонить» — в сервис, он ставит state=ended
        val piDecline = PendingIntent.getService(
            this, 202,
            Intent(this, javaClass).apply {
                action = ACTION_DECLINE
                putExtra("fromUid", fromUid)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )


        val notif = NotificationCompat.Builder(this, CALLS_CH_ID)
            .setSmallIcon(android.R.drawable.stat_sys_phone_call)
            .setStyle(
                NotificationCompat.CallStyle.forIncomingCall(person, piDecline, piAccept)
                    .setAnswerButtonColorHint(0xFF2ECC71.toInt())
                    .setDeclineButtonColorHint(0xFFE74C3C.toInt())
            )
            .setCategory(Notification.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setFullScreenIntent(piFull, true)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(42, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
        } else startForeground(42, notif)

        startRingtoneLoop()
        serviceScope.launch { watchUntilEndedOrAnswered(fromUid) }
        return START_NOT_STICKY
    }



    private suspend fun watchUntilEndedOrAnswered(callerUid: String) {
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.ktx.Firebase.firestore
        val tid = findTidWith(callerUid, me) ?: return

        val qs = db.collection("chats").document(tid).collection("calls")
            .whereEqualTo("callerUid", callerUid)
            .whereEqualTo("calleeUid", me)
            .whereEqualTo("state", "ringing")
            .limit(1).get().await()
        val doc = qs.documents.firstOrNull() ?: return
        val ref = db.collection("chats").document(tid).collection("calls").document(doc.id)

        val latch = java.util.concurrent.atomic.AtomicBoolean(false)
        ref.addSnapshotListener { snap, _ ->
            if (latch.get()) return@addSnapshotListener
            val state = snap?.getString("state")
            val hasAnswer = snap?.get("answer") != null || snap?.getString("answerEnc") != null
            if (state == "ended" || hasAnswer) {
                latch.set(true)
                stopRingtone()
                NotificationManagerCompat.from(this).cancel(42)
                stopForeground(true); stopSelf()
            }
        }
    }

    private suspend fun findTidWith(callerUid: String, me: String): String? {
        val db = com.google.firebase.ktx.Firebase.firestore
        val q1 = db.collection("chats").whereEqualTo("mommyUid", callerUid)
            .whereEqualTo("babyUid", me).limit(1).get().await()
        if (!q1.isEmpty) return q1.documents.first().id
        val q2 = db.collection("chats").whereEqualTo("mommyUid", me)
            .whereEqualTo("babyUid", callerUid).limit(1).get().await()
        return q2.documents.firstOrNull()?.id
    }
    private suspend fun endLatestRingingForMe(callerUid: String) {
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.ktx.Firebase.firestore
        val tid = findTidWith(callerUid, me) ?: return
        val docs = db.collection("chats").document(tid).collection("calls")
            .whereEqualTo("callerUid", callerUid)
            .whereEqualTo("calleeUid", me)
            .whereEqualTo("state", "ringing")
            .get().await().documents
        val fresh = docs.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L } ?: return
        db.collection("chats").document(tid).collection("calls").document(fresh.id)
            .update("state", "ended").await()
    }

    private fun startRingtoneLoop() {
        try {
            if (globalPlayer != null) return
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager?.requestAudioFocus(
                afListener, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            )
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            globalPlayer = MediaPlayer().apply {
                setDataSource(this@IncomingCallService, uri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                setOnPreparedListener { it.start() }
                prepareAsync()
            }
        } catch (_: Throwable) {}
    }


    private fun stopRingtone() {
        runCatching { globalPlayer?.stop(); globalPlayer?.release() }
        globalPlayer = null
        runCatching { audioManager?.abandonAudioFocus(afListener) }
    }

    override fun onDestroy() {
        running.set(false)
        serviceScope.cancel()
        stopRingtone()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

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