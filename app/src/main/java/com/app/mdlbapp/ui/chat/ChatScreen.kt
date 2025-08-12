@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.navigation.NavHostController
import com.app.mdlbapp.habits.data.chat.ChatMessage
import com.app.mdlbapp.habits.data.chat.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
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
    babyUid?.let { ChatScreen(nav, mommyUid, it) }
}

@Composable
fun BabyChatScreen(nav: NavHostController) {
    val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var mommyUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        mommyUid = Firebase.firestore.collection("users").document(babyUid)
            .get().await().getString("pairedWith")
    }
    mommyUid?.let { ChatScreen(nav, it, babyUid) }
}

/** mommyUid/babyUid — пара. Текущий пользователь берётся из FirebaseAuth. */
@Composable
private fun ChatScreen(nav: NavHostController, mommyUid: String, babyUid: String) {
    val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val scope = rememberCoroutineScope()

    val messages by ChatRepository.messagesFlow(mommyUid, babyUid)
        .collectAsState(initial = emptyList())

    var draft by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val density = LocalDensity.current

    val imeBottomPx = WindowInsets.ime.getBottom(density)

    LaunchedEffect(messages.size, imeBottomPx) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Чат") }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFFFF3EE))
                .padding(inner)
                .imePadding()
        ) {
            // сообщения
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(12.dp)
            ) {
                items(messages) { m -> Bubble(m, me) }
            }

            // поле ввода
            Row(
                Modifier.fillMaxWidth().padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Напиши сообщение…") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    enabled = draft.isNotBlank(),
                    onClick = {
                        val to = if (me == mommyUid) babyUid else mommyUid
                        scope.launch {
                            ChatRepository.sendText(mommyUid, babyUid, me, to, draft)
                            draft = ""
                        }
                    }
                ) { Icon(Icons.Default.Send, contentDescription = "Отправить") }
            }
        }
    }
}

@Composable
private fun Bubble(m: ChatMessage, me: String) {
    val mine = m.fromUid == me
    val bg = if (mine) Color(0xFFDCF7C5) else Color.White
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart

    val screenW = LocalConfiguration.current.screenWidthDp.dp
    val maxBubble = min(screenW * 0.75f, 360.dp)

    Box(
        Modifier.fillMaxWidth(),
        contentAlignment = align
    ) {
        Surface(
            color = bg,
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.widthIn(max = maxBubble)
        ) {
            Text(
                text = m.text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                textAlign = if (mine) TextAlign.Start else TextAlign.Start,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                color = Color(0xFF1B1B1B)
            )
        }
    }
    Spacer(Modifier.height(8.dp))
}