@file:OptIn(ExperimentalMaterial3Api::class)
package com.app.mdlbapp.ui.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.app.mdlbapp.MainActivity
import com.app.mdlbapp.R
import com.google.firebase.firestore.ktx.firestore
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.app.mdlbapp.data.call.CallRepository
import com.app.mdlbapp.data.call.CallRuntime
import com.app.mdlbapp.data.call.CallSounds
import com.app.mdlbapp.rtc.RtcCallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

enum class CallPhase { Ringing, ExchangingKeys, Connecting, Connected }

class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        super.onCreate(savedInstanceState)


        val initialName   = intent.getStringExtra("fromName") ?: "Мамочка"
        val initialAvatar = intent.getStringExtra("fromAvatar")
        val callerUid     = intent.getStringExtra("callerUid")
        val autoAccept = intent.getBooleanExtra("autoAccept", false)



        setContent {
            MaterialTheme {
                var name   by remember { mutableStateOf(initialName) }
                var avatar by remember { mutableStateOf(initialAvatar) }

                var phase by remember { mutableStateOf(CallPhase.Ringing) }
                var micOn by remember { mutableStateOf(true) }
                var camOn by remember { mutableStateOf(false) }
                var spkOn by remember { mutableStateOf(true) }
                var sendVideo by remember { mutableStateOf(false) }

                var callStartAt by remember { mutableStateOf<Long?>(null) }
                var durationText by remember { mutableStateOf("00:00") }
                // --- предпросмотр камеры ---
                var showCamPreview by remember { mutableStateOf(false) }

                val scope = rememberCoroutineScope()



                // когда реально подключились
                LaunchedEffect(phase) {
                    if (phase == CallPhase.Connected && callStartAt == null) {
                        callStartAt = com.app.mdlbapp.data.call.CallRuntime.callStartedAtUptimeMs
                            ?: android.os.SystemClock.elapsedRealtime()
                    }
                }

                var rtc by remember { mutableStateOf<com.app.mdlbapp.rtc.RtcCallManager?>(null) }
                var callId by remember { mutableStateOf<String?>(null) }
                var currentTid by remember { mutableStateOf<String?>(null) }
                var currentCallId by remember { mutableStateOf<String?>(null) }

                val remoteHas by remember(rtc) {
                    rtc?.remoteHasVideo ?: MutableStateFlow(false)
                }.collectAsState(initial = false)
                val showVideoLayer = rtc != null && !showCamPreview && remoteHas
                val showHeader     = !showVideoLayer


                // --- длительность ---
                LaunchedEffect(callStartAt) {
                    if (callStartAt != null) {
                        while (true) {
                            val sec = ((SystemClock.elapsedRealtime() - callStartAt!!) / 1000).toInt()
                            val mm = (sec / 60); val ss = sec % 60
                            durationText = "%02d:%02d".format(mm, ss)
                            delay(1000)
                        }
                    }
                }

                // Привязки тумблеров
                LaunchedEffect(rtc, micOn) { rtc?.setMicEnabled(micOn) }
                LaunchedEffect(rtc, spkOn) { rtc?.setSpeakerphone(spkOn) }
                // ВНИМАНИЕ: видео включаем ТОЛЬКО после синей кнопки
//                LaunchedEffect(rtc) { rtc?.setVideoEnabled(camOn) }

                val act = this@IncomingCallActivity
                fun toast(msg: String) = android.widget.Toast.makeText(act, msg, android.widget.Toast.LENGTH_SHORT).show()


                // Живём на данных профиля собеседника
                DisposableEffect(callerUid) {
                    var reg: com.google.firebase.firestore.ListenerRegistration? = null
                    if (!callerUid.isNullOrBlank()) {
                        reg = com.google.firebase.ktx.Firebase.firestore
                            .collection("users")
                            .document(callerUid)
                            .addSnapshotListener { snap, _ ->
                                if (snap != null && snap.exists()) {
                                    name   = snap.getString("displayName") ?: name
                                    avatar = snap.getString("photoDataUrl")
                                        ?: snap.getString("photoUrl")
                                                ?: avatar
                                }
                            }
                    }
                    onDispose { reg?.remove() }
                }

                val resumeFromNotif = intent.getBooleanExtra("resume", false)
                LaunchedEffect(resumeFromNotif) {
                    if (resumeFromNotif && com.app.mdlbapp.data.call.CallRuntime.rtc != null) {
                        rtc = com.app.mdlbapp.data.call.CallRuntime.rtc
                        phase = CallPhase.Connected
                        callStartAt = com.app.mdlbapp.data.call.CallRuntime.callStartedAtUptimeMs
                    }
                }

                    // 1) Лямбда принятия – ВНЕ экрана, чтобы была видна и LaunchedEffect, и экрану:
                    val acceptCall: () -> Unit = {
                        callStartAt = null
                        CallRuntime.callStartedAtUptimeMs = null

                        val act = this@IncomingCallActivity
                        act.lifecycleScope.launch {
                            val me = FirebaseAuth.getInstance().currentUser?.uid
                            val from = callerUid

                            // попросим сервис мгновенно замолчать (если он крутит рингтон)
                            act.startService(
                                Intent(act, com.app.mdlbapp.data.call.IncomingCallService::class.java)
                                    .setAction("com.app.mdlbapp.ACTION_DISMISS")
                            )

                            if (me == null) { toast("Нет пользователя"); return@launch }
                            if (from.isNullOrBlank()) { toast("Нет UID звонящего"); return@launch }

                            // 1) ищем чат и свежий звонок
                            val tid = findTidWith(from, me)
                            if (tid == null) { toast("Чат не найден"); return@launch }
                            val callId = findLatestRingingCallId(tid, from, me)
                            if (callId == null) { toast("Нет активного звонка"); return@launch }

                            // 2) ключики
                            phase = CallPhase.ExchangingKeys
                            toast("Получаем ключики…")
                            val rtcKeyB64 = CallRepository.getOrCreateRtcKeyB64(tid, me, from)

                            // 3) создаём RTC (в state, не локально!)
                            currentTid = tid
                            currentCallId = callId
                            rtc = RtcCallManager(applicationContext, tid, me, from).also { mgr ->
                                mgr.onFatalDisconnect = {
                                    lifecycleScope.launch {
                                        // пометим звонок завершённым (если ещё не)
                                        runCatching { CallRepository.setState(tid, callId, "ended") }
                                        CallSounds.playHangupBeep(this)
                                        mgr.endCall()
                                        finish()  // или finishAndRemoveTask() на 21+
                                    }
                                }
                            }

                            val rtcNow = rtc!!


                            withContext(kotlinx.coroutines.Dispatchers.Main) {
                                runCatching { rtcNow.startLocalVideo() }
                            }

                            // 4) слушаем состояние звонка
                            var watchJob: kotlinx.coroutines.Job? = null
                            watchJob = launch {
                                CallRepository.watchCallDecrypted(tid, callId, rtcKeyB64).collect { c ->
                                    if (c?.answer != null && c.state != "connected") {
                                        CallRepository.setState(tid, callId, "connected")
                                    }
                                    if (c?.state == "connected") phase = CallPhase.Connected
                                    if (c?.state == "ended") {
                                        CallSounds.playHangupBeep(this)
                                        watchJob?.cancel()
                                        rtc?.endCall(); rtc = null
                                        startService(
                                            Intent(this@IncomingCallActivity, com.app.mdlbapp.data.call.CallOngoingService::class.java)
                                                .setAction(com.app.mdlbapp.data.call.CallOngoingService.ACTION_HANGUP)
                                        )
                                        if (Build.VERSION.SDK_INT >= 21) act.finishAndRemoveTask() else act.finish()
                                    }
                                }
                            }

                            // 5) ждём оффер → отвечаем
                            phase = CallPhase.Connecting
                            toast("Соединяемся…")
                            val cdoc  = CallRepository.watchCallDecryptedOnce(tid, callId, rtcKeyB64)
                            val offer = cdoc?.offer ?: CallRepository.waitForDecryptedOffer(tid, callId, rtcKeyB64)
                            if (offer == null) { toast("Не дождались оффера"); return@launch }

                            try {
                                rtcNow.acceptOffer(callId, offer, sendVideo = false)
                            } catch (e: Exception) {
                                toast("Ошибка ответа: ${e.message}")
                                CallRepository.setState(tid, callId, "ended")

                                watchJob?.cancel()
                                return@launch
                            }

                            // 6) тумблеры
                            launch { rtcNow.setMicEnabled(micOn) }
//                            launch { rtcNow.setVideoEnabled(camOn) }
                            launch { rtcNow.setSpeakerphone(spkOn) }

                            startService(
                                Intent(this@IncomingCallActivity, com.app.mdlbapp.data.call.CallOngoingService::class.java)
                                    .setAction(com.app.mdlbapp.data.call.CallOngoingService.ACTION_START)
                                    .putExtra(com.app.mdlbapp.data.call.CallOngoingService.EXTRA_TID, tid)
                                    .putExtra(com.app.mdlbapp.data.call.CallOngoingService.EXTRA_PEER_UID, from)
                                    .putExtra(com.app.mdlbapp.data.call.CallOngoingService.EXTRA_PEER_NAME, name)
                                    .putExtra(com.app.mdlbapp.data.call.CallOngoingService.EXTRA_CALL_ID, callId)
                                    .putExtra(com.app.mdlbapp.data.call.CallOngoingService.EXTRA_AS_CALLER, false)
                            )
                            startService(
                                Intent(this@IncomingCallActivity, com.app.mdlbapp.data.call.CallOngoingService::class.java)
                                    .setAction(com.app.mdlbapp.data.call.CallOngoingService.ACTION_CONNECTED)
                            )
                            CallRuntime.rtc = rtc
                            CallRuntime.tid = tid
                            CallRuntime.callId = callId
                            CallRuntime.peerUid = from
                            CallRuntime.peerName = name
                            CallRuntime.asCaller = false

                            CallRuntime.callIdFlow.value    = callId
                            CallRuntime.asCallerFlow.value  = false
                            CallRuntime.sessionActive.value = true
                        }
                        phase = if (CallRuntime.connected.value) CallPhase.Connected else CallPhase.Connecting
                    }

                LaunchedEffect(Unit) {
                    if (callStartAt == null && CallRuntime.connected.value) {
                        callStartAt = CallRuntime.callStartedAtUptimeMs ?: SystemClock.elapsedRealtime()
                    }
                }

                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                Box(Modifier.fillMaxSize()) {
                    val showRemote = rtc != null && !showCamPreview && remoteHas
                    val showSelfPip = rtc != null && !showCamPreview && sendVideo

                    // Большой удалённый — только когда есть кадры
                    if (showRemote) {
                        AndroidView(
                            factory = { rtc!!.remoteView },
                            modifier = Modifier.matchParentSize()
                        )
                    }

                    // Экран входящего вызова с правильным фоном
                    IncomingCallScreen(
                        name = name,
                        avatarUrl = avatar,
                        phase = phase,
                        durationText = durationText,
                        micOn = micOn, camOn = camOn, spkOn = spkOn,
                        onToggleMic = { micOn = !micOn },
                        onToggleCam = {
                            if (sendVideo) {
                                rtc?.setVideoSending(false)  // ❗ выключаем отправку
                                sendVideo = false
                                camOn = false
                            } else if (rtc != null) {
                                showCamPreview = true        // ❗ сначала предпросмотр
                            } else {
                                toast("Пока нельзя — идёт соединение")
                            }
                        },
                        onToggleSpk = { spkOn = !spkOn },
                        onAccept = acceptCall,
                        onDecline = {
                            val act = this@IncomingCallActivity
                            act.lifecycleScope.launch {
                                // На всякий: заглушим входящий сервис/звук и погасим fallback-увед.
                                runCatching {
                                    act.startService(
                                        Intent(act, com.app.mdlbapp.data.call.IncomingCallService::class.java)
                                            .setAction("com.app.mdlbapp.ACTION_DISMISS")
                                    )
                                    androidx.core.app.NotificationManagerCompat.from(act).cancel(42)
                                }

                                if (phase != CallPhase.Connected) {
                                    // 📵 СБРОС ДО СОЕДИНЕНИЯ
                                    runCatching { endLatestRingingForMe(callerUid) }    // ← готовая утилита в этом файле
                                    if (android.os.Build.VERSION.SDK_INT >= 21) act.finishAndRemoveTask() else act.finish()
                                    return@launch
                                }

                                // ⛔ ЗАВЕРШЕНИЕ В СОЕДИНЕНИИ
                                // Отдадим это фон-сервису «идёт звонок», он красиво всё выключит и поставит state=ended.
                                act.startService(
                                    Intent(act, com.app.mdlbapp.data.call.CallOngoingService::class.java)
                                        .setAction(com.app.mdlbapp.data.call.CallOngoingService.ACTION_HANGUP)
                                )

                                // Локально тоже прибираемся (мягкая подстраховка)
                                runCatching { rtc?.endCall() }
                                rtc = null
                                if (android.os.Build.VERSION.SDK_INT >= 21) act.finishAndRemoveTask() else act.finish()
                            }
                        },
                        drawBg = !showRemote,     // зелёненький фон до появления кадра
                        showHeader = !showRemote,
                        showControls = !showCamPreview
                    )

                    // Маленькое окно со своей камерой — отдельно
                    if (showSelfPip) {
                        AndroidView(
                            factory = { rtc!!.localPipView },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(120.dp)
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }

                    // Лист/модалка предпросмотра — как у тебя
                    if (showCamPreview) {
                        rtc?.let { r ->
                            FullscreenCamPreview(
                                rtc = r,
                                onConfirm = {
                                    camOn = true
                                    scope.launch {
                                        delay(16)
                                        r.setVideoSending(true)
                                        sendVideo = true
                                        showCamPreview = false
                                    }
                                },
                                onClose = { showCamPreview = false }
                            )
                        }
                    }
                }
                LaunchedEffect(autoAccept, callerUid) {
                    if (autoAccept && !callerUid.isNullOrBlank()) {
                        act.startService(
                            Intent(this@IncomingCallActivity, com.app.mdlbapp.data.call.IncomingCallService::class.java)
                                .setAction("com.app.mdlbapp.ACTION_DISMISS")
                        )
                        acceptCall()
                    }
                }
            }
        }
    }


    private suspend fun findTidWith(callerUid: String, me: String): String? {
        val db = com.google.firebase.ktx.Firebase.firestore
        val q1 = db.collection("chats")
            .whereEqualTo("mommyUid", callerUid).whereEqualTo("babyUid", me)
            .limit(1).get().await()
        if (!q1.isEmpty) return q1.documents.first().id
        val q2 = db.collection("chats")
            .whereEqualTo("mommyUid", me).whereEqualTo("babyUid", callerUid)
            .limit(1).get().await()
        return q2.documents.firstOrNull()?.id
    }

    private suspend fun findLatestRingingCallId(
        tid: String, callerUid: String, me: String
    ): String? {
        val db = com.google.firebase.ktx.Firebase.firestore
        val qs = db.collection("chats").document(tid).collection("calls")
            .whereEqualTo("callerUid", callerUid)
            .whereEqualTo("calleeUid", me)
            .whereEqualTo("state", "ringing")
            .get().await()
        return qs.documents.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }?.id
    }

    private suspend fun endLatestRingingForMe(callerUid: String?) {
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = com.google.firebase.ktx.Firebase.firestore
        val caller = callerUid ?: return

        val q1 = db.collection("chats")
            .whereEqualTo("mommyUid", caller).whereEqualTo("babyUid", me)
            .limit(1).get().await()
        val q2 = if (q1.isEmpty) db.collection("chats")
            .whereEqualTo("mommyUid", me).whereEqualTo("babyUid", caller)
            .limit(1).get().await() else null
        val tid = q1.documents.firstOrNull()?.id ?: q2?.documents?.firstOrNull()?.id ?: return

        val docs = db.collection("chats").document(tid).collection("calls")
            .whereEqualTo("callerUid", caller)
            .whereEqualTo("calleeUid", me)
            .whereEqualTo("state", "ringing")
            .get().await().documents

        val fresh = docs.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L } ?: return

        // ВАЖНО: ждём обновление состояния!
        db.collection("chats").document(tid)
            .collection("calls").document(fresh.id)
            .update("state", "ended")
            .await()
    }
}

@Composable
private fun IncomingCallScreen(
    name: String,
    avatarUrl: String?,
    phase: CallPhase,
    durationText: String,
    micOn: Boolean, camOn: Boolean, spkOn: Boolean,
    onToggleMic: () -> Unit,
    onToggleCam: () -> Unit,
    onToggleSpk: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    drawBg: Boolean,
    showHeader: Boolean = true,
    showControls: Boolean = true
) {
    val bgDisconnected = Brush.verticalGradient(
        listOf(Color(0xFF18122B), Color(0xFF33294D), Color(0xFF4C3F78))
    )
    val bgConnected = Brush.verticalGradient(
        listOf(Color(0xFF0B3D39), Color(0xFF116B63), Color(0xFF1AA68F))
    )
    val bg = if (phase == CallPhase.Connected) bgConnected else bgDisconnected

    Surface(color = Color.Transparent) {
        val base = Modifier.fillMaxSize()
        val layered = if (drawBg) base.background(bg) else base
        Box(layered) {

            if (showHeader) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 48.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BigAvatar(avatarUrl)
                    Text(
                        when (phase) {
                            CallPhase.Ringing -> "Звонок MDLBApp"
                            CallPhase.ExchangingKeys -> "Обмен ключиками шифрования…"
                            CallPhase.Connecting -> "Соединяемся…"
                            CallPhase.Connected -> durationText
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFEDE7F6)
                    )
                    Text(
                        name,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }

            // — нижняя панель — всегда у самого низа —
            if (phase == CallPhase.Connected && showControls) {
                CallBottomControls(
                    spkOn = spkOn, camOn = camOn, micOn = micOn,
                    onToggleSpk = onToggleSpk,
                    onToggleCam = onToggleCam,
                    onToggleMic = onToggleMic,
                    onHangup = onDecline,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            } else if (phase != CallPhase.Connected) {
                // до соединения — «Принять/Отклонить» тоже у низа
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 14.dp)
                        .windowInsetsPadding(WindowInsets.navigationBars),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactActionButton(
                        label = "Отклонить",
                        container = Color(0xFFE74C3C),
                        icon = { Icon(Icons.Rounded.CallEnd, null, tint = Color.White) },
                        onClick = onDecline
                    )
                    Spacer(Modifier.width(24.dp))
                    CompactActionButton(
                        label = "Принять",
                        container = Color(0xFF2ECC71),
                        icon = { Icon(Icons.Rounded.Call, null, tint = Color.White) },
                        onClick = onAccept
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactActionButton(
    label: String,
    container: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    size: Dp = 56.dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container),
            modifier = Modifier.size(size)
        ) { icon() }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun CallBottomControls(
    spkOn: Boolean,
    camOn: Boolean,
    micOn: Boolean,
    onToggleSpk: () -> Unit,
    onToggleCam: () -> Unit,
    onToggleMic: () -> Unit,
    onHangup: () -> Unit,
    modifier: Modifier = Modifier
) {
    // полупрозрачная «дорожка» у самого низа
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 12.dp)
            .windowInsetsPadding(WindowInsets.navigationBars),
        color = Color.Black.copy(alpha = 0.12f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1) Динамик (метка одна и та же, кружок светится при активном)
            RoundToggleCompact(
                active = spkOn,
                labelOn = "Динамик",
                labelOff = "Динамик",
                onClick = onToggleSpk,
                iconOn = { Icon(Icons.Rounded.VolumeUp, null) },
                iconOff = { Icon(Icons.Rounded.VolumeUp, null) },
            )
            // 2) Видео
            RoundToggleCompact(
                active = camOn,
                labelOn = "Выкл. видео",
                labelOff = "Вкл. видео",
                onClick = onToggleCam,
                iconOn = { Icon(Icons.Rounded.VideocamOff, null) },
                iconOff = { Icon(Icons.Rounded.Videocam, null) },
            )
            // 3) Микрофон
            RoundToggleCompact(
                active = micOn,
                labelOn = "Выкл. звук",
                labelOff = "Вкл. звук",
                onClick = onToggleMic,
                iconOn = { Icon(Icons.Rounded.MicOff, null) },
                iconOff = { Icon(Icons.Rounded.Mic, null) },
            )
            // 4) Завершить — красный
            RoundActionRedCompact(
                label = "Завершить",
                onClick = onHangup,
                icon = { Icon(Icons.Rounded.CallEnd, null, tint = Color.White) }
            )
        }
    }
}

@Composable
internal fun RoundToggleCompact(
    active: Boolean,
    labelOn: String,
    labelOff: String,
    onClick: () -> Unit,
    iconOn: @Composable () -> Unit,
    iconOff: @Composable () -> Unit,
    size: Dp = 56.dp      // компактнее
) {
    val container = if (active) Color.White else Color.White.copy(alpha = 0.18f)
    val content   = if (active) Color.Black else Color.White

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = container,
                contentColor   = content
            ),
            modifier = Modifier.size(size)
        ) {
            if (active) iconOn() else iconOff()
        }
        Spacer(Modifier.height(4.dp))
        Text(
            if (active) labelOn else labelOff,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
internal fun RoundActionRedCompact(
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    size: Dp = 56.dp
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFFE74C3C),
                contentColor   = Color.White
            ),
            modifier = Modifier.size(size)
        ) { icon() }
        Spacer(Modifier.height(4.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun BigAvatar(photo: String?, size: Dp = 160.dp) {
    val fallback = painterResource(R.drawable.ic_rule_name)

    val dataBitmap = remember(photo) {
        if (!photo.isNullOrBlank() && photo.startsWith("data:image")) {
            try {
                val base64 = photo.substringAfter(",")
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.asImageBitmap()
            } catch (_: Throwable) { null }
        } else null
    }

    if (dataBitmap != null) {
        Image(
            bitmap = dataBitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else if (!photo.isNullOrBlank()) {
        AsyncImage(
            model = photo,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = fallback, error = fallback
        )
    } else {
        Image(
            painter = fallback,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun LargeRoundButton(
    label: String,
    container: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container),
            modifier = Modifier.size(88.dp)
        ) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White)
    }
}



