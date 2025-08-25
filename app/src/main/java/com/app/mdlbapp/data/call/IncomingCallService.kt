// app/src/main/java/com/app/mdlbapp/data/call/IncomingCallService.kt
package com.app.mdlbapp.data.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.app.mdlbapp.CALLS_CH_ID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await
import android.content.pm.ServiceInfo


class IncomingCallService : Service() {

    companion object {
        const val ACTION_RING    = "com.app.mdlbapp.ACTION_RING"
        const val ACTION_ACCEPT  = "com.app.mdlbapp.ACTION_ACCEPT"
        const val ACTION_DECLINE = "com.app.mdlbapp.ACTION_DECLINE"
        const val ACTION_SILENCE = "com.app.mdlbapp.ACTION_SILENCE"
        const val ACTION_DISMISS = "com.app.mdlbapp.ACTION_DISMISS"

        // защёлка, чтобы не плодить несколько сервисов
        val running = java.util.concurrent.atomic.AtomicBoolean(false)

        @Volatile var globalPlayer: MediaPlayer? = null
    }

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private var audioManager: AudioManager? = null
    private val afListener = AudioManager.OnAudioFocusChangeListener { }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val act = intent?.action

        val fromName   = intent?.getStringExtra("fromName") ?: "Мамочка"
        val fromUid    = intent?.getStringExtra("fromUid")  ?: ""
        val fromAvatar = intent?.getStringExtra("fromAvatar")

        when (act) {

            // ПРИШЁЛ ВХОДЯЩИЙ — строим уведомление с fullScreenIntent и звоним
            ACTION_RING -> {
                if (!running.compareAndSet(false, true)) return START_NOT_STICKY
                ensureCallChannel()

                // Полноэкранка (просто открыть экран, без авто-принятия)
                val piFull = PendingIntent.getActivity(
                    this, 200,
                    Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                        putExtra("openCall", true)
                        putExtra("callerUid", fromUid)
                        putExtra("fromName", fromName)
                        putExtra("autoAccept", false)
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        )
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Кнопка принять — открывает экран с autoAccept=true
                val piAccept = PendingIntent.getActivity(
                    this, 201,
                    Intent(this, com.app.mdlbapp.ui.call.IncomingCallActivity::class.java).apply {
                        putExtra("openCall", true)
                        putExtra("callerUid", fromUid)
                        putExtra("fromName", fromName)
                        putExtra("autoAccept", true)
                        addFlags(
                            Intent.FLAG_ACTIVITY_NEW_TASK or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                        )
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Кнопка отклонить — вернётся сюда
                val piDecline = PendingIntent.getService(
                    this, 202,
                    Intent(this, javaClass).apply {
                        action = ACTION_DECLINE
                        putExtra("fromUid", fromUid)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val person = androidx.core.app.Person.Builder().setName(fromName).build()

                val notif = NotificationCompat.Builder(this, CALLS_CH_ID)
                    .setSmallIcon(android.R.drawable.stat_sys_phone_call)
                    .setStyle(
                        NotificationCompat.CallStyle.forIncomingCall(person, piDecline, piAccept)
                            .setAnswerButtonColorHint(0xFF2ECC71.toInt())
                            .setDeclineButtonColorHint(0xFFE74C3C.toInt())
                    )
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setFullScreenIntent(piFull, true)
                    .setOngoing(true)
                    .setAutoCancel(false)
                    .build()

                if (Build.VERSION.SDK_INT >= 29) {
                    startForeground(42, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL)
                } else {
                    startForeground(42, notif)
                }

                // Зазвеним
                startRingtoneLoop()

                // Смотрим Firestore: если звонок уже закончился/принят — гасим себя
                serviceScope.launch { watchUntilEndedOrAnswered(fromUid) }

                return START_NOT_STICKY
            }

            // ТИХО — просто перестать играть
            ACTION_SILENCE -> {
                stopRingtone()
                return START_NOT_STICKY
            }

            // УБРАТЬ УВЕДОМЛЕНИЕ И СЕРВИС
            ACTION_DISMISS -> {
                stopRingtone()
                runCatching { NotificationManagerCompat.from(this).cancel(42) }
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }

            // ОТКЛОНИТЬ
            ACTION_DECLINE -> {
                serviceScope.launch {
                    try { endLatestRingingForMe(fromUid) } catch (_: Throwable) {}
                    withContext(Dispatchers.Main) {
                        stopRingtone()
                        runCatching { NotificationManagerCompat.from(this@IncomingCallService).cancel(42) }
                        stopForeground(true)
                        stopSelf()
                    }
                }
                return START_NOT_STICKY
            }

            // Прочие случаи нам не нужны
            else -> return START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        running.set(false)
        serviceScope.cancel()
        stopRingtone()
        super.onDestroy()
    }

    // ————————— helpers —————————

    private suspend fun watchUntilEndedOrAnswered(callerUid: String) {
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = Firebase.firestore
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
                runCatching { NotificationManagerCompat.from(this).cancel(42) }
                stopForeground(true)
                stopSelf()
            }
        }
    }

    private suspend fun findTidWith(callerUid: String, me: String): String? {
        val db = Firebase.firestore
        val q1 = db.collection("chats").whereEqualTo("mommyUid", callerUid)
            .whereEqualTo("babyUid", me).limit(1).get().await()
        if (!q1.isEmpty) return q1.documents.first().id
        val q2 = db.collection("chats").whereEqualTo("mommyUid", me)
            .whereEqualTo("babyUid", callerUid).limit(1).get().await()
        return q2.documents.firstOrNull()?.id
    }

    private suspend fun endLatestRingingForMe(callerUid: String) {
        val me = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = Firebase.firestore
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
