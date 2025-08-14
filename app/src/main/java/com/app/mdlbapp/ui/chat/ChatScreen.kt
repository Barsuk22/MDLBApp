@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import com.app.mdlbapp.data.chat.ChatMessage
import com.app.mdlbapp.data.chat.ChatRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import coil.compose.AsyncImage
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.draw.clip
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage

@Composable
private fun PeerAvatar(photo: String?, name: String, sizeDp: Dp) {
    val letter = (name.trim().firstOrNull() ?: '•').uppercaseChar().toString()

    val dataBitmap = remember(photo) {
        if (!photo.isNullOrBlank() && photo.startsWith("data:image")) {
            try {
                val base64Part = photo.substringAfter(",")
                val bytes = Base64.decode(base64Part, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (_: Exception) { null }
        } else null
    }

    Surface(shape = CircleShape, shadowElevation = 0.dp) {
        when {
            dataBitmap != null -> {
                Image(
                    bitmap = dataBitmap,
                    contentDescription = "Аватар",
                    modifier = Modifier.size(sizeDp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            !photo.isNullOrBlank() -> {
                AsyncImage(
                    model = photo,
                    contentDescription = "Аватар",
                    modifier = Modifier.size(sizeDp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Box(
                    Modifier.size(sizeDp).clip(CircleShape).background(Color(0xFFE6E6E6)),
                    contentAlignment = Alignment.Center
                ) { Text(letter, color = Color(0xFF333333)) }
            }
        }
    }
}
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

    val peerUid = if (me == mommyUid) babyUid else mommyUid

    var peerName by remember { mutableStateOf<String>("") }
    var peerPhoto by remember { mutableStateOf<String?>(null) }

    // (по желанию) lastSeen — если заведёшь поле; пока не используем
    // var lastSeen by remember { mutableStateOf<Timestamp?>(null) }

    LaunchedEffect(peerUid) {
        Firebase.firestore.collection("users").document(peerUid)
            .addSnapshotListener { snap, _ ->
                peerName = snap?.getString("displayName").orEmpty()
                // у нас фото хранится как dataURL в поле photoDataUrl на онбординге
                peerPhoto = snap?.getString("photoDataUrl")
                    ?: snap?.getString("photoUrl") // вдруг старое поле встретится
            }
    }

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
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PeerAvatar(peerPhoto, peerName, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(peerName.ifBlank { "Без имени" }, style = MaterialTheme.typography.titleMedium)
                            Text("в сети", style = MaterialTheme.typography.bodySmall, color = Color(0x99000000))
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { /* TODO: поиск в чате */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Поиск")
                    }
                    IconButton(onClick = { /* TODO: меню */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Ещё")
                    }
                }
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
        // сообщения
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)              // <-- вместо fillMaxSize()
                .fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)
        ) {
            itemsIndexed(messages, key = { _, m -> m.id }) { index, m ->
                val mine = m.fromUid == me
                val isLastInGroup = index == messages.lastIndex || messages[index + 1].fromUid != m.fromUid
                ChatBubble(
                    message = m,
                    mine = mine,
                    showAvatar = !mine && isLastInGroup,
                    peerName = peerName,
                    peerPhoto = peerPhoto
                )
            }
        }

// тоненькая полосочка, как разделитель
        Divider()

// поле ввода (прибито к низу)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .navigationBarsPadding(),  // чтоб не пряталось за системную панель
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
private fun ChatBubble(
    message: ChatMessage,
    mine: Boolean,
    showAvatar: Boolean,
    peerName: String,
    peerPhoto: String?
) {
    val bubbleColor = if (mine) Color(0xFFDCF7C5) else Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            if (showAvatar) {
                PeerAvatar(photo = peerPhoto, name = peerName, sizeDp = 28.dp)
            } else {
                // чтобы выравнивание не прыгало — ставим пустышку размера авы
                Spacer(Modifier.size(28.dp))
            }
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp, topEnd = 18.dp,
                bottomEnd = if (mine) 4.dp else 18.dp,
                bottomStart = if (mine) 18.dp else 4.dp
            ),
            tonalElevation = 1.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(message.text, color = Color(0xFF1B1B1B))
                // по желанию: маленькое время
                // Text(formatTime(message.createdAt), style = MaterialTheme.typography.labelSmall, color = Color(0x99000000))
            }
        }
    }
}