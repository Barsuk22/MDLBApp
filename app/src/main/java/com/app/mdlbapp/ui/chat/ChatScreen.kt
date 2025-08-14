@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation.NavHostController
import com.app.mdlbapp.data.chat.ChatMessage
import com.app.mdlbapp.data.chat.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

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

    val messages by ChatRepository.messagesFlow(mommyUid, babyUid)
        .collectAsState(initial = emptyList())

    // presence
    val otherPresence by ChatRepository.presenceFlow(mommyUid, babyUid, other)
        .collectAsState(initial = ChatRepository.ChatPresence())

    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // высота панели ввода, чтобы список имел нижний паддинг и ничто не перекрывалось
    val density = LocalDensity.current
    var inputBarHeightPx by remember { mutableStateOf(0) }
    val inputBarHeightDp = with(density) { inputBarHeightPx.toDp() }

    val cfg = LocalConfiguration.current



// «печатает…» — таймер (вкл немедленно, выкл через 1.5с тишины)
    var typingJob by remember { mutableStateOf<Job?>(null) }
    fun bumpTyping() {
        typingJob?.cancel()
        typingJob = scope.launch {
            ChatRepository.setTyping(mommyUid, babyUid, me, true)
            delay(1500)
            ChatRepository.setTyping(mommyUid, babyUid, me, false)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            typingJob?.cancel()
            scope.launch { ChatRepository.setTyping(mommyUid, babyUid, me, false) }
        }
    }

    // Удобная функция «прокрутить в самый низ» (с учётом индикатора печати)
    fun scrollToBottom(typing: Boolean) {
        val extra = if (typing) 1 else 0
        val lastIndex = (messages.lastIndex + extra).coerceAtLeast(0)
        scope.launch {
            // жёстко + мягко (надёжно на разных девайсах)
            listState.scrollToItem(lastIndex)
            listState.animateScrollToItem(lastIndex)
        }
    }

    var firstScrollDone by remember { mutableStateOf(false) }

// находимся ли мы сейчас у низа (чтоб не мешать ручной прокрутке вверх)
    val isAtBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            val lastDataIndex = messages.lastIndex + (if (otherPresence.typing) 1 else 0)
            lastVisible >= lastDataIndex
        }
    }

    // автоскролл и отметки доставлено/прочитано
    LaunchedEffect(messages.size, otherPresence.typing) {
        if (messages.isNotEmpty()) {
            when {
                !firstScrollDone -> {                 // первый заход/первая загрузка
                    scrollToBottom(otherPresence.typing)
                    firstScrollDone = true
                }
                isAtBottom -> {                        // держим внизу только если юзер уже внизу
                    scrollToBottom(otherPresence.typing)
                }
            }
            runCatching {
                ChatRepository.markDelivered(mommyUid, babyUid, me)
                ChatRepository.markRead(mommyUid, babyUid, me)
            }
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("Чат") }) },
        contentWindowInsets = WindowInsets(0)
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF3EE))
                .padding(inner)
                .consumeWindowInsets(inner)
        ) {
            // 1) Лента сообщений занимает весь экран
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 12.dp,
                    end = 12.dp,
                    bottom = 12.dp +
                            inputBarHeightDp +
                            with(density) { WindowInsets.ime.getBottom(this).toDp() } // ← добавляем высоту клавы
                )
            ) {
                items(messages, key = { it.id }) { m -> Bubble(m, me, otherPresence) }

                if (otherPresence.typing) {
                    item(key = "typing-indicator") {
                        TypingIndicator()
                    }
                }
            }

            // 2) Панель ввода — поверх ленты, приклеена к низу и поднимается над клавой
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()   // низ навбара
                    .imePadding()              // подъём ровно на клаву (если она выше)
            ) {
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
                            bumpTyping()
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
                                    scrollToBottom(otherPresence.typing)
                                }
                            }
                        }
                    ) { Icon(Icons.Filled.Send, contentDescription = "Отправить") }
                }
            }
        }
    }
}

//@Composable
//fun InputBar(modifier: navigationBarsPadding) {
//    TODO("Not yet implemented")
//}

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