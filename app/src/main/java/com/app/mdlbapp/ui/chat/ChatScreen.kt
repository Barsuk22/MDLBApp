@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation.NavHostController
import com.app.mdlbapp.habits.data.chat.ChatMessage
import com.app.mdlbapp.habits.data.chat.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

@Composable
fun MommyChatScreen(nav: NavHostController) {
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var babyUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        babyUid = Firebase.firestore.collection("users").document(mommyUid)
            .get().await().getString("pairedWith")
    }
    babyUid?.let {
        LaunchedEffect(it) {
            ChatRepository.ensureChat(mommyUid, it)
            ChatRepository.ensurePresence(mommyUid, it, mommyUid)
        }
        ChatScreen(nav, mommyUid, it)
    }
}

@Composable
fun BabyChatScreen(nav: NavHostController) {
    val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var mommyUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        mommyUid = Firebase.firestore.collection("users").document(babyUid)
            .get().await().getString("pairedWith")
    }
    mommyUid?.let {
        LaunchedEffect(it) {
            ChatRepository.ensureChat(it, babyUid)
            ChatRepository.ensurePresence(it, babyUid, babyUid)
        }
        ChatScreen(nav, it, babyUid)
    }
}

/** mommyUid/babyUid — пара. Текущий пользователь берётся из FirebaseAuth. */
@Composable
private fun ChatScreen(nav: NavHostController, mommyUid: String, babyUid: String) {
    val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val other = if (me == mommyUid) babyUid else mommyUid
    val scope = rememberCoroutineScope()
    var typingJob by remember { mutableStateOf<Job?>(null) }

    val density = LocalDensity.current
    var inputBarHeightPx by remember { mutableStateOf(0) }
    val inputBarHeightDp = with(density) { inputBarHeightPx.toDp() }

    fun bumpTyping() {
        typingJob?.cancel()
        typingJob = scope.launch {
            ChatRepository.setTyping(mommyUid, babyUid, me, true)
            delay(1500)  // 1.5 секунды тишины
            ChatRepository.setTyping(mommyUid, babyUid, me, false)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            typingJob?.cancel()
            scope.launch { ChatRepository.setTyping(mommyUid, babyUid, me, false) }
        }
    }

    val messages by ChatRepository.messagesFlow(mommyUid, babyUid)
        .collectAsState(initial = emptyList())

    // presence
    val otherPresence by ChatRepository.presenceFlow(mommyUid, babyUid, other)
        .collectAsState(initial = ChatRepository.ChatPresence())

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // автоскролл и отметки доставлено/прочитано
    LaunchedEffect(messages.size, otherPresence.typing) {
        if (messages.isNotEmpty()) {
            // если пользователь был у низа — держим у низа
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val atBottom = lastVisible >= messages.lastIndex
            if (atBottom) {
                val extra = if (otherPresence.typing) 1 else 0
                listState.scrollToItem(messages.lastIndex + extra)      // жёстко
                listState.animateScrollToItem(messages.lastIndex + extra) // мягко
            }
        }
    }

    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Чат") }) }) { inner ->
        Column(
            modifier = Modifier.fillMaxSize()
                .background(Color(0xFFFFF3EE))
                .padding(inner)
                .imePadding()
        ) {

            val listState = rememberLazyListState()

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 12.dp, top = 12.dp, end = 12.dp,
                    bottom = 12.dp + inputBarHeightDp    // ⬅️ место под поле ввода
                )
            ) {
                items(messages) { m -> Bubble(m, me, otherPresence) }

                if (otherPresence.typing) {
                    item(key = "typing-indicator") { TypingIndicator() }
                }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { inputBarHeightPx = it.height }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = {
                        draft = it
                        bumpTyping()   // ← вот тут вызываем, а не отдельным параметром
                    },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Напиши сообщение…") },
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val to = if (me == mommyUid) babyUid else mommyUid
                        scope.launch {
                            runCatching {
                                ChatRepository.sendText(mommyUid, babyUid, me, to, draft)
                            }.onSuccess {
                                draft = ""
                                ChatRepository.setTyping(mommyUid, babyUid, me, false)
                            }
                        }
                    }
                ) { Icon(Icons.Default.Send, contentDescription = "Отправить") }
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color(0x66FFFFFF),
            shape = MaterialTheme.shapes.small,
            shadowElevation = 2.dp
        ) {
            Text(
                "печатает…",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color.DarkGray
            )
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, me: String, otherPresence: ChatRepository.ChatPresence) {
    val mine = m.fromUid == me
    val bg = if (mine) Color(0xFFDCF7C5) else Color.White
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart

    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val maxBubble = min(screenW * 0.75f, 360.dp)

    Box(Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(Modifier.widthIn(max = maxBubble)) {
            Surface(color = bg, shape = MaterialTheme.shapes.medium) {
                Text(
                    text = m.text,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    softWrap = true,
                    color = Color(0xFF1B1B1B)
                )
            }
            if (mine) {
                // ✓ sent — всегда; ✓✓ delivered; ✓✓ синие — read
                // стало — безопасно по seconds
                val atSec = m.at?.seconds
                val delivered = atSec != null &&
                        (otherPresence.lastDeliveredAt?.seconds ?: -1) >= atSec
                val read = atSec != null &&
                        (otherPresence.lastReadAt?.seconds ?: -1) >= atSec

                Text(
                    text = when {
                        read -> "✓✓"
                        delivered -> "✓✓"
                        else -> "✓"
                    },
                    color = if (read) Color(0xFF2196F3) else Color.Gray,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp).align(Alignment.End)
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
}