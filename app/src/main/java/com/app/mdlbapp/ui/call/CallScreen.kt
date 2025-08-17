@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.ui.call

import android.content.pm.PackageManager
import android.widget.Toast
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

@Composable
fun CallScreen(tid: String, asCaller: Boolean, navBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val me = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // получаем участников чата
    var peerUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(tid) {
        val d = Firebase.firestore.collection("chats").document(tid).get().await()
        val mommy = d.getString("mommyUid"); val baby = d.getString("babyUid")
        peerUid = if (me == mommy) baby else mommy
    }

    if (peerUid == null) { Text("Загружаем…"); return }

    val rtc = remember { RtcCallManager(ctx, tid, me, peerUid!!) }
    var callId by remember { mutableStateOf<String?>(null) }
    var callDoc by remember { mutableStateOf<CallDoc?>(null) }
    var permsOk by remember { mutableStateOf(false) }

    // ① Разрешения
    EnsureCallPermissions(
        onGranted = { permsOk = true; rtc.startLocalVideo() },
        onDenied = {
            Toast.makeText(ctx, "Нужны камера и микрофон", Toast.LENGTH_SHORT).show()
            navBack()
        }
    )



    DisposableEffect(permsOk, asCaller, peerUid) {
        if (!permsOk || peerUid == null || asCaller) {
            onDispose { }
        } else {
            // слушаем появление "ringing" на меня
            val reg = Firebase.firestore.collection("chats").document(tid)
                .collection("calls")
                .whereEqualTo("calleeUid", me)
                .whereEqualTo("state", "ringing")
                .limit(1)
                .addSnapshotListener { qs, err ->
                    if (err != null) return@addSnapshotListener
                    val doc = qs?.documents?.firstOrNull() ?: return@addSnapshotListener

                    // нашли входящий
                    callId = doc.id
                    callDoc = doc.toObject(CallDoc::class.java)

                    // ещё и сам call слушаем дальше (answer/ended)
                    scope.launch {
                        CallRepository.watchCall(tid, doc.id).collect { c ->
                            callDoc = c
                            if (c?.state == "ended") {
                                rtc.endCall()
                                navBack()
                            }
                        }
                    }
                }
            onDispose { reg.remove() }
        }
    }

    fun accept() {
        val cid = callId ?: return
        val offer = callDoc?.offer ?: return
        rtc.acceptOffer(cid, offer, sendVideo = true)
        scope.launch { CallRepository.setState(tid, cid, "connected") }
    }
    fun hangup() {
        val cid = callId
        if (cid != null) scope.launch { CallRepository.setState(tid, cid, "ended") }
        rtc.endCall(); navBack()
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Звонок") }) },
        bottomBar = {
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(
                    onClick = { hangup() }, // <-- обязательно в лямбду!
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Сбросить") }

                if (!asCaller && callDoc?.answer == null) {
                    Button(onClick = { accept() }) { Text("Ответить") } // <-- тоже лямбда
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(12.dp)) {
            Text(when {
                asCaller && callDoc?.answer == null -> "Дзынь-дзынь… ждём"
                !asCaller && callDoc?.answer == null -> "Тебе звонят!"
                else -> "Связь установлена"
            })
            Spacer(Modifier.height(12.dp))
            Text("Ты:");
            AndroidView(
                factory = { _: android.content.Context -> rtc.localView },
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text("Мамочка:");
            AndroidView(
                factory = { _: android.content.Context -> rtc.remoteView },
                modifier = Modifier.fillMaxWidth().height(200.dp)
            )
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
    val me = FirebaseAuth.getInstance().currentUser?.uid
    if (me == null) return

    // чтобы не навигироваться по 100 раз
    var lastCid by remember { mutableStateOf<String?>(null) }

    // один раз на старте найдём tid нашего чата
    var tid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(me) {
        val db = Firebase.firestore
        val paired = db.collection("users").document(me).get().await()
            .getString("pairedWith") ?: return@LaunchedEffect

        // чат может быть записан в обоих направлениях
        val q1 = db.collection("chats")
            .whereEqualTo("mommyUid", me)
            .whereEqualTo("babyUid", paired)
            .limit(1).get().await()
        val q2 = if (q1.isEmpty) db.collection("chats")
            .whereEqualTo("mommyUid", paired)
            .whereEqualTo("babyUid", me)
            .limit(1).get().await() else null

        tid = q1.documents.firstOrNull()?.id ?: q2?.documents?.firstOrNull()?.id
    }

    // как только знаем tid — слушаем входящие
    DisposableEffect(tid) {
        if (tid == null) return@DisposableEffect onDispose {}
        val reg = Firebase.firestore.collection("chats").document(tid!!)
            .collection("calls")
            .whereEqualTo("calleeUid", me)
            .whereEqualTo("state", "ringing")
            .limit(1)
            .addSnapshotListener { qs, _ ->
                val doc = qs?.documents?.firstOrNull() ?: return@addSnapshotListener
                val cid = doc.id
                if (cid != lastCid) {
                    lastCid = cid
                    // 0 = asCaller=false
                    navController.navigate("call/$tid/0")
                }
            }
        onDispose { reg.remove() }
    }
}