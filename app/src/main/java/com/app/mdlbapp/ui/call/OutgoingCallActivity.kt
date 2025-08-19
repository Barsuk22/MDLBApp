@file:OptIn(ExperimentalMaterial3Api::class)
package com.app.mdlbapp.ui.call

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationManagerCompat
import coil.compose.AsyncImage
import com.app.mdlbapp.rtc.RtcCallManager
import com.app.mdlbapp.data.call.CallRepository
import com.app.mdlbapp.data.call.CallSounds
import com.app.mdlbapp.data.call.SdpBlob
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.webrtc.MediaConstraints
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription

enum class OutPhase { Request, Waiting, Calling, ExchangingKeys, Connected }



class OutgoingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        super.onCreate(savedInstanceState)

        val tid     = intent.getStringExtra("tid") ?: return
        val peerUid = intent.getStringExtra("peerUid") ?: return
        val initialName   = intent.getStringExtra("peerName") ?: "Малыш"
        val initialAvatar = intent.getStringExtra("peerAvatar")


        setContent {
            MaterialTheme {
                var name by remember { mutableStateOf(initialName) }
                var avatar by remember { mutableStateOf(initialAvatar) }



                // состояния
                var phase by remember { mutableStateOf(OutPhase.Request) }
                var micOn by remember { mutableStateOf(true) }
                var camOn by remember { mutableStateOf(false) }   // 👈 стартуем без трансляции
                var spkOn by remember { mutableStateOf(true) }

                var rtc by remember { mutableStateOf<RtcCallManager?>(null) }
                var callId by remember { mutableStateOf<String?>(null) }
                val me = FirebaseAuth.getInstance().currentUser?.uid



                // отправляем ли видео собеседнику (по умолчанию — нет)
                var sendVideo by remember { mutableStateOf(false) }

                // открыт ли полноценный предпросмотр (прячет нижние контролы)
                var showCamPreview by remember { mutableStateOf(false) }

                // --- длительность ---
                var callStartAt by remember { mutableStateOf<Long?>(null) }
                var durationText by remember { mutableStateOf("00:00") }

                val remoteHas by remember(rtc) { rtc?.remoteHasVideo ?: MutableStateFlow(false) }
                    .collectAsState(initial = false)

                val showVideoLayer = rtc != null && !showCamPreview && (sendVideo || remoteHas)
                val showHeader     = !showVideoLayer

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

                // подгружаем профиль собеседника
                DisposableEffect(peerUid) {
                    val reg = Firebase.firestore.collection("users").document(peerUid)
                        .addSnapshotListener { snap, _ ->
                            name   = snap?.getString("displayName") ?: name
                            avatar = snap?.getString("photoDataUrl")
                                ?: snap?.getString("photoUrl")
                                        ?: avatar
                        }
                    onDispose { reg.remove() }
                }

                // при выходе — аккуратно завершаем
                DisposableEffect(Unit) {
                    onDispose { rtc?.endCall(); rtc = null }
                }


                // Привязки тумблеров
                LaunchedEffect(rtc, micOn) { rtc?.setMicEnabled(micOn) }
                LaunchedEffect(rtc, spkOn) { rtc?.setSpeakerphone(spkOn) }
                // ВНИМАНИЕ: видео включаем ТОЛЬКО после синей кнопки
//                LaunchedEffect(rtc) { rtc?.setVideoEnabled(camOn) }

                val act = this@OutgoingCallActivity
                val scope = rememberCoroutineScope()
                fun toast(s: String) = android.widget.Toast.makeText(act, s, android.widget.Toast.LENGTH_SHORT).show()

                // старт звонка
                LaunchedEffect(tid, peerUid) {
                    if (me == null) { toast("Нет пользователя"); finish(); return@LaunchedEffect }
                    try {
                        phase = OutPhase.Request
                        rtc = RtcCallManager(act, tid, me, peerUid)
                        rtc!!.startLocalVideo()
                        phase = OutPhase.Calling

                        // гудки пошли
                        CallSounds.startRingback()

                        rtc!!.makeOffer(sendVideo = false) { cid ->
                            callId = cid
                            phase = OutPhase.Waiting


                            // ждём answer / завершаем по ended
                            scope.launch {
                                val key = com.app.mdlbapp.data.call.CallRepository.getOrCreateRtcKeyB64(tid, me, peerUid)
                                CallRepository.watchCallDecrypted(tid, cid, key).collect { c ->
                                    when {
                                        c == null -> {}
                                        c.state == "ended" -> {
                                            CallSounds.playHangupBeep(this)
                                            rtc?.endCall(); finish()
                                        }
                                        c.answer != null -> {
                                            CallSounds.stopRingback()
                                            phase = OutPhase.ExchangingKeys
                                            rtc?.setRemoteAnswer(c.answer!!)
                                            if (c.state != "connected") CallRepository.setState(tid, cid, "connected")
                                            phase = OutPhase.Connected
                                            callStartAt = SystemClock.elapsedRealtime()
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        CallSounds.stopAll()
                        android.widget.Toast.makeText(this@OutgoingCallActivity, "Не получилось: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        finish()
                    }
                }

                // Завершение любым способом
                fun hangup() {
                    scope.launch {
                        callId?.let { runCatching { CallRepository.setState(tid, it, "ended") } }
                        CallSounds.playHangupBeep(this)
                        rtc?.endCall()
                        finish()
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    // 🟢 1) Логика показа слоёв
                    val showRemote = rtc != null && !showCamPreview && remoteHas
                    val showSelfPip = rtc != null && !showCamPreview && sendVideo

                    // 🟢 2) Большой удалённый слой — ТОЛЬКО когда пришёл первый кадр
                    if (showRemote) {
                        AndroidView(
                            factory = { rtc!!.remoteView },
                            modifier = Modifier.matchParentSize()
                        )
                    }
                    // 🟢 3) UI вызова: фон и заголовки показываем, если нет удалённого видео
                    OutgoingCallScreen(
                        name = name,
                        avatarUrl = avatar,
                        phase = phase,
                        micOn = micOn, camOn = camOn, spkOn = spkOn,
                        durationText = durationText,
                        onToggleMic = { micOn = !micOn },
                        onToggleCam = {
                            if (sendVideo) {
                                sendVideo = false
                                rtc?.setVideoSending(false)
                            } else {
                                showCamPreview = true
                            }
                        },
                        onToggleSpk = { spkOn = !spkOn },
                        onHangup = { hangup() },
                        showControls = !showCamPreview,
                        drawBg = !showRemote,     // << фон зелёный, пока нет удалённого видео
                        showHeader = !showRemote  // << заголовок виден, пока нет удалённого видео
                    )

                    // 🟢 4) Маленькое «пип»-окошко со СВОЕЙ камерой — отдельно
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

                    // 🟢 5) Фуллскрин-предпросмотр (как у тебя и было)
                    if (showCamPreview) {
                        rtc?.let { r ->
                            FullscreenCamPreview(
                                rtc = r,
                                onConfirm = {
                                    showCamPreview = false
                                    scope.launch {
                                        delay(16)
                                        r.setVideoSending(true)
                                        sendVideo = true
                                    }
                                },
                                onClose = { showCamPreview = false }
                            )
                        }
                    }
                }
            }
        }
    }


}

@Composable
private fun FullscreenCamPreview(
    rtc: RtcCallManager,
    onConfirm: () -> Unit,
    onClose: () -> Unit
) {
    val act = androidx.activity.compose.LocalActivity.current

    DisposableEffect(act) {
        if (act == null) return@DisposableEffect onDispose { }

        WindowCompat.setDecorFitsSystemWindows(act.window, false)
        val controller = WindowCompat.getInsetsController(act.window, act.window.decorView)
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            WindowCompat.setDecorFitsSystemWindows(act.window, true)
        }
    }

    // Сам слой — без инсет-паддингов, прямо под бары
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // сам превью — ровно один localView, на весь экран
        AndroidView(
            factory = { rtc.localPreviewView },
            modifier = Modifier.matchParentSize()
        )

        // Мягкая "подложка" под кнопкой, чтобы было видно текст на любом видео
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                    )
                )
        )

        // Большая кнопка
        Button(
            onClick = onConfirm,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .height(56.dp)
                .fillMaxWidth()
        ) { Text("Включить трансляцию") }

        // Крестик (по желанию)
        TextButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) { Text("Закрыть", color = Color.White) }
    }
}

@Composable
private fun OutgoingCallScreen(
    name: String,
    avatarUrl: String?,
    phase: OutPhase,
    durationText: String,
    micOn: Boolean, camOn: Boolean, spkOn: Boolean,
    showControls: Boolean = true,
    onToggleMic: () -> Unit,
    onToggleCam: () -> Unit,
    onToggleSpk: () -> Unit,
    onHangup: () -> Unit,
    drawBg: Boolean,
    showHeader: Boolean = true
) {
    val bgDisconnected = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color(0xFF18122B), Color(0xFF33294D), Color(0xFF4C3F78))
    )
    val bgConnected = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color(0xFF0B3D39), Color(0xFF116B63), Color(0xFF1AA68F))
    )
    val bg = if (phase == OutPhase.Connected) bgConnected else bgDisconnected

    Surface(color = Color.Transparent) {
        val base = Modifier.fillMaxSize()
        val layered = if (drawBg) base.background(bg) else base
        Box(layered) {

            if (showHeader) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 48.dp, start = 24.dp, end = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    BigAvatar(avatarUrl)
                    Text(
                        when (phase) {
                            OutPhase.Request       -> "Готовим запрос…"
                            OutPhase.Waiting       -> "Ждём ответа…"
                            OutPhase.Calling       -> "Звоним…"
                            OutPhase.ExchangingKeys-> "Обмен ключиками…"
                            OutPhase.Connected      -> durationText
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFFEDE7F6)
                    )
                    Text(name, style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
            }

            // для звонящего — четыре кнопочки ВСЕГДА видны
            if (showControls) {
                CallBottomControls(
                    spkOn = spkOn, camOn = camOn, micOn = micOn,
                    onToggleSpk = onToggleSpk,
                    onToggleCam = onToggleCam,
                    onToggleMic = onToggleMic,
                    onHangup = onHangup,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}


@Composable
fun CallSurfaces(
    rtc: RtcCallManager,
    selfVisible: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier) {
        // 👀 собеседник — во весь экран
        AndroidView(
            factory = { rtc.remoteView },
            modifier = Modifier.matchParentSize()
        )
        // 🪞 мы — маленьким окошком
        if (selfVisible) {
            AndroidView(
                factory = { rtc.localPipView },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(120.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}
