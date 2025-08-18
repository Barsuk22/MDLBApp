@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.ui.call

import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.data.call.CallRepository
import com.app.mdlbapp.data.call.CallDoc
import com.app.mdlbapp.rtc.RtcCallManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.app.mdlbapp.data.call.CallRepository.getOrCreateRtcKeyB64
import java.util.jar.Manifest

@Composable
fun CallScreen(tid: String, asCaller: Boolean, navBack: () -> Unit) {
    val ctx   = LocalContext.current
    val scope = rememberCoroutineScope()
    val me    = FirebaseAuth.getInstance().currentUser?.uid ?: return




    var rtcKeyB64 by remember { mutableStateOf<String?>(null) }
    // 1) узнаём собеседника
    var peerUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tid) {
        val d = Firebase.firestore.collection("chats").document(tid).get().await()
        val mommy = d.getString("mommyUid"); val baby = d.getString("babyUid")
        peerUid = if (me == mommy) baby else mommy
    }
    if (peerUid == null) { Text("Загружаем…"); return }

    // 2) менеджер звонка
    var rtc by remember { mutableStateOf<RtcCallManager?>(null) }
    LaunchedEffect(peerUid) {
        if (peerUid != null) {
            rtc = runCatching { RtcCallManager(ctx, tid, me, peerUid!!) }
                .onFailure { e ->
                    Toast.makeText(ctx, "Ой, звонок не создался: ${e.message}", Toast.LENGTH_LONG).show()
                    navBack()
                }
                .getOrNull()
        }
    }
    LaunchedEffect(peerUid) {
        val p = peerUid ?: return@LaunchedEffect
        val me = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        rtcKeyB64 = getOrCreateRtcKeyB64(tid, me, p)
    }

    if (rtc == null) { Text("Готовим звонок…"); return }

    var callId  by remember { mutableStateOf<String?>(null) }
    var callDoc by remember { mutableStateOf<CallDoc?>(null) }
    var permsOk by remember { mutableStateOf(false) }

    var accepting by remember { mutableStateOf(false) }

    // ---- ЕДИНАЯ «метла» завершения ----
    var hungUp by remember { mutableStateOf(false) }
    fun reallyHangup() {
        if (hungUp) return
        hungUp = true
        callId?.let { cid ->
            scope.launch { runCatching { CallRepository.setState(tid, cid, "ended") } }
        }
        rtc?.endCall()   // было rtc!! — делаем мягко
        navBack()
    }

    // Разрешения
    EnsureCallPermissions(
        onGranted = { permsOk = true }, // ничего не трогаем rtc тут!
        onDenied  = {
            Toast.makeText(ctx, "Нужны камера и микрофон", Toast.LENGTH_SHORT).show()
            navBack()
        }
    )

    LaunchedEffect(permsOk, rtc) {
        if (permsOk && rtc != null) {
            runCatching { rtc!!.startLocalVideo() }.onFailure { e ->
                Toast.makeText(ctx, "Камера не стартанула: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Входящий: ждём "ringing" и сам документ звонка
    val ttlMs = 120_000L

    DisposableEffect(permsOk, asCaller, peerUid, rtcKeyB64) {
        if (!permsOk || peerUid == null || asCaller || rtcKeyB64 == null) onDispose { }
        else {
            val reg = Firebase.firestore.collection("chats").document(tid)
                .collection("calls")
                .whereEqualTo("calleeUid", me)
                .whereEqualTo("state", "ringing")
                .addSnapshotListener { qs, err ->
                    if (err != null) return@addSnapshotListener
                    val docs = qs?.documents.orEmpty()
                    if (docs.isEmpty()) return@addSnapshotListener

                    val fresh = docs.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }!!
                    val created = fresh.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                    if (created == 0L || System.currentTimeMillis() - created > ttlMs) {
                        scope.launch { runCatching { CallRepository.setState(tid, fresh.id, "ended") } }
                        return@addSnapshotListener
                    }

                    if (callId == null) {
                        callId = fresh.id
                        // ⚠️ не берём сырое fresh.toObject(...) — ждём расшифровку
                        scope.launch {
                            CallRepository.watchCallDecrypted(tid, fresh.id, rtcKeyB64!!)
                                .collect { c ->
                                    callDoc = c
                                    if (c?.answer != null && c.state != "connected") {
                                        runCatching { CallRepository.setState(tid, fresh.id, "connected") }
                                    }
                                    if (c?.state == "ended") reallyHangup()
                                }
                        }
                    }
                }
            onDispose { reg.remove() }
        }
    }

    // Исходящий: создаём offer и ждём answer
    LaunchedEffect(permsOk, asCaller, peerUid) {
        if (!permsOk || !asCaller || peerUid == null) return@LaunchedEffect

        val db = Firebase.firestore
        // 1) пытаемся найти самый свежий незавершённый вызов, где я — caller
        val existing = runCatching {
            db.collection("chats").document(tid)
                .collection("calls")
                .whereEqualTo("callerUid", me)
                .whereEqualTo("state", "ringing")
                .limit(1)
                .get().await()
                .documents
                .firstOrNull()
        }.getOrNull()

        if (existing != null) {
            // Подцепляемся к уже начатому разговору
            callId = existing.id
            callDoc = existing.toObject(CallDoc::class.java)
            // если партнёр уже ответил — ставим remote answer
            callDoc?.answer?.let { ans -> rtc!!.setRemoteAnswer(ans) }
            // и всё равно слушаем любые изменения
            scope.launch {
                val key = rtcKeyB64 ?: return@launch
                CallRepository.watchCallDecrypted(tid, existing.id, key).collect { c ->
                    callDoc = c
                    c?.answer?.let { ans ->
                        rtc!!.setRemoteAnswer(ans)
                        if (c.state != "connected") CallRepository.setState(tid, existing.id, "connected")
                    }
                    if (c?.state == "ended") reallyHangup()
                }
            }
        } else {
            // 2) если не нашли — создаём новый как раньше
            rtc!!.makeOffer(sendVideo = true) { cid ->
                callId = cid
                scope.launch {
                    val key = rtcKeyB64 ?: return@launch
                    CallRepository.watchCallDecrypted(tid, cid, key).collect { c ->
                        callDoc = c
                        c?.answer?.let { ans ->
                            rtc!!.setRemoteAnswer(ans)
                            if (c.state != "connected") CallRepository.setState(tid, cid, "connected")
                        }
                        if (c?.state == "ended") reallyHangup()
                    }
                }
            }
        }
    }

    // ОДИН BackHandler на весь экран
    BackHandler { reallyHangup() }

    // И ОДИН onDispose на случай уничтожения экрана/процесса
    DisposableEffect(Unit) { onDispose { reallyHangup() } }

    LaunchedEffect(callId) {
        val cid = callId ?: return@LaunchedEffect
        val snap = Firebase.firestore.collection("chats").document(tid)
            .collection("calls").document(cid).get().await()
        val callee = snap.getString("calleeUid")
        android.util.Log.d("CALL", "me=$me  calleeUid=$callee  (должны совпасть)")
    }

    // Принять/сбросить
    fun accept() {
        if (accepting) return
        accepting = true

        val cid = callId ?: run { accepting = false; return }
        val offer = callDoc?.offer ?: run { accepting = false; return }


        try {
            rtc!!.acceptOffer(cid, offer, sendVideo = true)
            // state=connected ставим не мгновенно, а уже по факту — см. watcher ниже
        } catch (e: Exception) {
            Toast.makeText(ctx, "Не удалось ответить: ${e.message}", Toast.LENGTH_LONG).show()
            accepting = false
            return
        }
    }

    LaunchedEffect(callDoc?.answer, callDoc?.state) {
        if (callDoc?.answer != null || callDoc?.state == "connected") accepting = false
    }
    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Звонок") }) },
        bottomBar = {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Сброс
                Button(
                    onClick = { reallyHangup() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Сбросить") }

                // Для входящего до ответа показываем «Ответить/Отклонить»
                if (!asCaller && callDoc?.answer == null) {
                    Button(
                        enabled = permsOk && !accepting && callId != null && callDoc?.offer != null,
                        onClick = { accept() }
                    ) { Text(if (accepting) "Подключаем…" else "Ответить") }
                    OutlinedButton(onClick = { reallyHangup() }) { Text("Отклонить") }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            Text(
                when {
                    callDoc?.state == "connected" -> "Связь установлена"
                    asCaller && callDoc?.answer == null -> "Дзынь-дзынь… ждём"
                    !asCaller && callDoc?.answer == null -> "Тебе звонят!"
                    else -> "Связь устанавливается…"
                }
            )
            Spacer(Modifier.height(12.dp))

            Text("Ты:")
            AndroidView({ rtc!!.localView }, Modifier.fillMaxWidth().height(200.dp))
            Spacer(Modifier.height(8.dp))
            Text("Мамочка:")
            AndroidView({ rtc!!.remoteView }, Modifier.fillMaxWidth().height(200.dp))

            Spacer(Modifier.height(12.dp))
            var micOn by remember { mutableStateOf(true) }
            var camOn by remember { mutableStateOf(true) }
            var spkOn by remember { mutableStateOf(true) }
            LaunchedEffect(micOn) { rtc!!.setMicEnabled(micOn) }
            LaunchedEffect(camOn) { rtc!!.setVideoEnabled(camOn) }
            LaunchedEffect(spkOn) { rtc!!.setSpeakerphone(spkOn) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                FilledTonalButton(onClick = { micOn = !micOn }) { Text(if (micOn) "Микрофон Вкл" else "Микрофон Выкл") }
                FilledTonalButton(onClick = { camOn = !camOn }) { Text(if (camOn) "Камера Вкл" else "Камера Выкл") }
                FilledTonalButton(onClick = { spkOn = !spkOn }) { Text(if (spkOn) "Громкая" else "Динамик") }
                OutlinedButton(onClick = { rtc!!.switchCamera() }) { Text("Повернуть") }
            }
        }
    }
}


@Composable
fun EnsureCallPermissions(
    onGranted: () -> Unit,
    onDenied: () -> Unit
) {
    val ctx = LocalContext.current
    val hasCamera = ctx.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    // какие разрешения нам реально нужны на этом устройстве
    val perms = remember(hasCamera) {
        buildList {
            add(android.Manifest.permission.RECORD_AUDIO)
            if (hasCamera) add(android.Manifest.permission.CAMERA)
        }.toTypedArray()
    }

    // проверяем, все ли уже даны
    fun allGranted(): Boolean = perms.all {
        ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (perms.all { result[it] == true }) onGranted() else onDenied()
    }

    LaunchedEffect(perms) {
        if (allGranted()) onGranted() else launcher.launch(perms)
    }
}

@Composable
fun WatchIncomingCall(navController: NavHostController) {
    val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var tid by remember { mutableStateOf<String?>(null) }
    var lastCid by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val ttlMs = 120_000L

    // находим tid пары (без падений)
    LaunchedEffect(me) {
        runCatching {
            val db = Firebase.firestore
            val pair = db.collection("users").document(me).get().await()
                .getString("pairedWith") ?: return@LaunchedEffect
            val q1 = db.collection("chats").whereEqualTo("mommyUid", me)
                .whereEqualTo("babyUid", pair).limit(1).get().await()
            val q2 = if (q1.isEmpty) db.collection("chats")
                .whereEqualTo("mommyUid", pair).whereEqualTo("babyUid", me)
                .limit(1).get().await() else null
            tid = q1.documents.firstOrNull()?.id ?: q2?.documents?.firstOrNull()?.id
        }.onFailure { e -> android.util.Log.e("WatchIncomingCall", "init fail", e) }
    }

    DisposableEffect(tid) {
        if (tid == null) return@DisposableEffect onDispose {}
        val reg = Firebase.firestore.collection("chats").document(tid!!)
            .collection("calls")
            .whereEqualTo("calleeUid", me)
            .whereEqualTo("state", "ringing")
            .addSnapshotListener { qs, err ->
                if (err != null) return@addSnapshotListener
                val docs = qs?.documents.orEmpty()
                if (docs.isEmpty()) return@addSnapshotListener

                // берём самый свежий
                val fresh = docs.maxByOrNull { it.getTimestamp("createdAt")?.toDate()?.time ?: 0L }!!
                val created = fresh.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                if (created == 0L || System.currentTimeMillis() - created > ttlMs) {
                    scope.launch { runCatching { CallRepository.setState(tid!!, fresh.id, "ended") } }
                    return@addSnapshotListener
                }
                // если уже есть answer — не тащим экран
                if (fresh.getString("answerEnc") != null || (fresh.get("answer") as? Map<*, *>) != null)
                    return@addSnapshotListener

                val isCallScreen = navController.currentBackStackEntry
                    ?.destination?.route?.startsWith("call") == true

                if (!isCallScreen && lastCid != fresh.id) {
                    lastCid = fresh.id
                    navController.navigate("call/${tid!!}/0")
                }
            }
        onDispose { reg.remove() }
    }
}
