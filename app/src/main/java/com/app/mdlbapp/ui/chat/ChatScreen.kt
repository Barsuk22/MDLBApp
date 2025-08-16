@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class
)

package com.app.mdlbapp.ui.chat

// ——— imports (собраны без дублей и отсортированы по пакетам) ———
import android.graphics.BitmapFactory
import android.util.Base64
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.app.mdlbapp.data.chat.ChatMessage
import com.app.mdlbapp.data.chat.ChatRepository
import com.app.mdlbapp.data.chat.ReplyPayload
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import androidx.compose.ui.unit.IntSize
import com.app.mdlbapp.data.chat.ForwardPayload
import kotlin.math.roundToInt

// ——— моделька поиска ———
data class Hit(val msgIndex: Int, val range: IntRange)

sealed class ChatRow {
    data class Header(val date: java.time.LocalDate) : ChatRow()
    data class Msg(val msgIndex: Int) : ChatRow()
}

data class RectF(val left: Float, val top: Float, val right: Float, val bottom: Float)


// ┌───────────────────────────────────────────────────────────────────┐
// │                 ВХОДНЫЕ ЭКРАНЫ (мамочка/малыш)                    │
/* └───────────────────────────────────────────────────────────────────┘ */

@Composable
fun MommyChatScreen(nav: NavHostController) {
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var babyUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(mommyUid) {
        babyUid = Firebase.firestore.collection("users").document(mommyUid)
            .get().await().getString("pairedWith")
    }
    babyUid?.let { ChatScreen(nav, mommyUid, it) }
}

@Composable
fun BabyChatScreen(nav: NavHostController) {
    val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var mommyUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(babyUid) {
        mommyUid = Firebase.firestore.collection("users").document(babyUid)
            .get().await().getString("pairedWith")
    }
    mommyUid?.let { ChatScreen(nav, it, babyUid) }
}

// ┌───────────────────────────────────────────────────────────────────┐
// │                            ЭКРАН ЧАТА                             │
/* └───────────────────────────────────────────────────────────────────┘ */

@Composable
private fun ChatScreen(nav: NavHostController, mommyUid: String, babyUid: String) {
    // — ТЕКУЩИЙ ПОЛЬЗОВАТЕЛЬ
    val me = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val scope = rememberCoroutineScope()

    // ссылка на корень чата
    val chatRef = Firebase.firestore.collection("chats")
        .document("${mommyUid}_${babyUid}")

    val allMessages by ChatRepository.messagesFlow(mommyUid, babyUid)
        .collectAsState(initial = emptyList())
    // отметка "до какого момента я всё стёр" (персональная)

    var myClearedAtMs by remember { mutableStateOf<Long?>(null) }


    // показываем ТОЛЬКО те сообщения, что новее моей отметки очистки
    val messages = remember(allMessages, myClearedAtMs) {
        val cut = myClearedAtMs ?: Long.MIN_VALUE
        allMessages.filter { (it.at?.toDate()?.time ?: Long.MIN_VALUE) > cut }
    }

    // пины чата
    val pins by ChatRepository.pinsFlow(mommyUid, babyUid).collectAsState(initial = emptyList())
    val pinnedSet = remember(pins) { pins.map { it.mid }.toSet() }
    val visibleIds = remember(messages) { messages.map { it.id }.toSet() }
    // мапа: id сообщения -> его время (для сортировки пинов)
    val msgTimeById = remember(messages) {
        messages.associate { it.id to (it.at?.toDate()?.time ?: Long.MIN_VALUE) }
    }

    val msgOrderById = remember(messages) {
        messages.mapIndexed { idx, m -> m.id to idx }.toMap()
    }

    // пины, видимые мне, отсортированы по времени сообщений: от старых к новым
    // пины, видимые мне, отсортированы по времени сообщений: от НОВЫХ к СТАРЫМ
    val pinsForMe = remember(pins, visibleIds, msgTimeById, msgOrderById) {
        pins
            .filter { it.mid in visibleIds }
            .sortedWith(
                compareBy(
                    { msgTimeById[it.mid] ?: Long.MIN_VALUE }, // ← время СООБЩЕНИЯ
                    { msgOrderById[it.mid] ?: Int.MIN_VALUE },
                    { it.mid }
                )
            ) // индекс 0 — самый старый, lastIndex — самый новый
    }
    val pinnedSetForMe = remember(pinsForMe) { pinsForMe.map { it.mid }.toSet() }

    // — состояние плашечки
    var pinnedBarH by remember { mutableStateOf(0) }
    val chatId = "${mommyUid}_${babyUid}"


    // текущий шаг = индекс в pinsForMe: 0..n-1 (0 — самый старый, n-1 — самый новый)
    var pinIdx by rememberSaveable(chatId) { mutableStateOf<Int?>(null) }

    // Помогаем «удержаться» за тот же mid, если список пинов поменялся
    var prevPinMids by remember(chatId) { mutableStateOf(emptyList<String>()) }
    LaunchedEffect(pinsForMe) {
        val mids = pinsForMe.map { it.mid }
        pinIdx = pinIdx?.let { old ->
            val oldMid = prevPinMids.getOrNull(old)
            val newIndex = mids.indexOf(oldMid)
            if (newIndex >= 0) newIndex else null   // если пин исчез — уходим в «без номера»
        }
        prevPinMids = mids
    }

    // 💡 создать корень чата заранее (иначе правила не пускают читать несуществующий doc)
    LaunchedEffect(mommyUid, babyUid) {
        try { ensureChatRoot(chatRef, mommyUid, babyUid) } catch (_: Exception) {}
    }


    // — ДАННЫЕ ЧАТА


    val forward: ForwardPayload? = null
    var forwarding by remember { mutableStateOf<ForwardPayload?>(null) }

    var flashHighlightedId by remember { mutableStateOf<String?>(null) }
    var flashJob by remember { mutableStateOf<Job?>(null) }

    fun flashMessage(id: String) {
        flashJob?.cancel()
        flashHighlightedId = id
        flashJob = scope.launch {
            kotlinx.coroutines.delay(2200)      // «несколько секунд»
            if (flashHighlightedId == id) flashHighlightedId = null
        }
    }

    var selectedMsgId by remember { mutableStateOf<String?>(null) }
    var selectedRect by remember { mutableStateOf<RectF?>(null) }

    // самый новый закреп всегда первый после asReversed()
    val newestPinnedMid = pinsForMe.firstOrNull()?.mid

// сниппет для плашки — текст самого нового закреплённого сообщения
    val bannerSnippet = newestPinnedMid
        ?.let { id -> messages.find { it.id == id }?.text ?: "Сообщение удалено" }
        ?: ""

    // номер в заголовке — позиция сообщения в ленте (а не номер среди закрепов)
    fun bannerNumberLabel(): String =
        newestPinnedMid
            ?.let { mid -> msgOrderById[mid] }     // 0..N-1
            ?.let { "#${it + 1}" }                 // человекочитаемо: #1..#N
            ?: ""


// авточистка: если пин указывает на удалённое сообщение — снимем его тихонечко
    LaunchedEffect(pins, allMessages) {
        val allIds = allMessages.map { it.id }.toSet()
        pins.filter { it.mid !in allIds }.forEach {
            try { ChatRepository.removePin(mommyUid, babyUid, it.mid) } catch (_: Exception) {}
        }
    }

    // Какой mid сейчас показывать в плашке:
    // если pinIdx == null → всегда самый новый, иначе — выбранный
    val newestIdx = pinsForMe.lastIndex
    val shownIdx = pinIdx ?: newestIdx
    val shownMid = pinsForMe.getOrNull(shownIdx)?.mid

    val shownText = shownMid
        ?.let { id -> messages.find { it.id == id }?.text ?: "Сообщение удалено" }
        ?: ""

    // Номер показываем только когда мы «внутри» просмотра
    val titleText = pinIdx?.let { "#${it + 1}" } ?: ""

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)

    // — СОСТОЯНИЯ ПАРТНЁРА
    val peerUid = if (me == mommyUid) babyUid else mommyUid
    var peerName by remember { mutableStateOf("") }
    var peerPhoto by remember { mutableStateOf<String?>(null) }
    var peerOnline by remember { mutableStateOf(false) }
    var peerLastSeen by remember { mutableStateOf<com.google.firebase.Timestamp?>(null) }
    var peerTyping by remember { mutableStateOf(false) }
    var peerTypingAtMs by remember { mutableStateOf<Long?>(null) }

    // — МОИ ДАННЫЕ
    var meName by remember { mutableStateOf("Вы") }
    var mePhoto by remember { mutableStateOf<String?>(null) }

    // — UI СОСТОЯНИЯ
    var menuOpen by remember { mutableStateOf(false) }
    var bottomBarH by remember { mutableStateOf(0) }
    var draft by remember { mutableStateOf("") }
    var datePickerOpen by remember { mutableStateOf(false) }

    var clearDialogOpen by remember { mutableStateOf(false) }
    var alsoForPeer by remember { mutableStateOf(false) } // если true — удалим у обоих (жёстко)

    val ctx = androidx.compose.ui.platform.LocalContext.current

    var deleteConfirmForId by remember { mutableStateOf<String?>(null) } // спрашиваем, удалять ли

    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }

    // — ПОИСК
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf(listOf<Hit>()) }
    var curHit by remember { mutableStateOf(-1) }
    var curMsg by remember { mutableStateOf(0) }
    var showHitsSheet by remember { mutableStateOf(false) }
    var selectedMsgIndex by remember { mutableStateOf<Int?>(null) }
    var lastGoodMsgIndex by remember { mutableStateOf<Int?>(null) } // где сейчас «якорь»
    var lastQuery by remember { mutableStateOf("") }                 // прошлый запрос
    var perMessageHit by remember { mutableStateOf<Map<Int, IntRange>>(emptyMap()) }

    // — ТАЙПИНГ
    var lastTypingPingAt by remember { mutableStateOf(0L) }
    var idleClearJob by remember { mutableStateOf<Job?>(null) }

    // — ССЫЛКИ НА ДОКИ



    // — ЦВЕТОЧКИ
    val chatBg = Color(0xFFFFF3EE)


    // Ряды ленты с заголовками
    val rows = remember(messages) { buildRows(messages) }

    // Быстро получить rowIndex по msgIndex (для скролла/подсветки)
    fun rowIndexForMessage(msgIndex: Int): Int =
        rows.indexOfFirst { it is ChatRow.Msg && it.msgIndex == msgIndex }.takeIf { it >= 0 } ?: 0

// Текущая «видимая» дата для плавающего бейджа
    val topBadgeDate by remember(listState, messages, rows) {
        derivedStateOf<java.time.LocalDate?> {
            if (rows.isEmpty()) return@derivedStateOf null
            val i = listState.firstVisibleItemIndex.coerceIn(0, rows.lastIndex)
            var j = i
            var date: java.time.LocalDate? = null
            while (j >= 0 && date == null) {           // идём вверх от видимого
                when (val r = rows[j]) {
                    is ChatRow.Header -> date = r.date
                    is ChatRow.Msg -> {
                        val d = messages[r.msgIndex].at?.toLocalDate()
                        if (d != null) date = d
                    }
                }
                j--
            }
            date
        }
    }



    // Видна ли последняя строка (т.е. мы у самого низа)?
    val atBottom by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total == 0) true else {
                val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= total - 1
            }
        }
    }

    // идет ли прокрутка сейчас
    val isScrolling by remember(listState) { derivedStateOf { listState.isScrollInProgress } }

    // управляемая видимость бейджа
    var showDateBadge by remember { mutableStateOf(false) }

    LaunchedEffect(pinsForMe.isEmpty()) {
        if (pinsForMe.isEmpty()) pinnedBarH = 0
    }

    // показывать во время скролла, и скрывать через 1.5с после остановки
    LaunchedEffect(isScrolling, topBadgeDate) {
        if (topBadgeDate == null) {
            showDateBadge = false
            return@LaunchedEffect
        }
        if (isScrolling) {
            showDateBadge = true
        } else {
            val captured = topBadgeDate           // чтобы не мигать, если дата успела поменяться
            delay(1500)
            if (!listState.isScrollInProgress && topBadgeDate == captured) {
                showDateBadge = false
            }
        }
    }

    // ───────────────────── ЭФФЕКТЫ И ЛИСТЕНЕРЫ ──────────────────────

    // Подписка на профиль собеседника (имя/фото/онлайн)
    DisposableEffect(peerUid) {
        val reg = Firebase.firestore.collection("users").document(peerUid)
            .addSnapshotListener { snap, _ ->
                peerName = snap?.getString("displayName").orEmpty()
                peerPhoto = snap?.getString("photoDataUrl") ?: snap?.getString("photoUrl")
                peerOnline = snap?.getBoolean("isOnline") == true
                peerLastSeen = snap?.getTimestamp("lastSeenAt")
            }
        onDispose { reg.remove() }
    }

    // Подписка на мой профиль (имя/фото)
    DisposableEffect(me) {
        val reg = Firebase.firestore.collection("users").document(me)
            .addSnapshotListener { snap, _ ->
                meName = snap?.getString("displayName") ?: "Вы"
                mePhoto = snap?.getString("photoDataUrl")
                    ?: snap?.getString("photoUrl")
                            ?: FirebaseAuth.getInstance().currentUser?.photoUrl?.toString()
            }
        onDispose { reg.remove() }
    }

    // Создадим корневой doc чата (merge)
    var chatReady by remember { mutableStateOf(false) }

    LaunchedEffect(chatId) {
        try {
            chatRef.set(
                mapOf(
                    "mommyUid" to mommyUid,
                    "babyUid" to babyUid,
                    "createdAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()     // ⬅️ дождаться создания
            chatReady = true
        } catch (_: Exception) { /* покажите тост при желании */ }
    }

    // Тап-тап: статус «печатает» собеседника
    DisposableEffect(chatId, peerUid) {
        val reg = chatRef.addSnapshotListener { snap, _ ->
            val ts = snap?.getTimestamp("typing.$peerUid")
            val ms = ts?.toDate()?.time
            peerTypingAtMs = ms
            peerTyping = ms != null && (System.currentTimeMillis() - ms < 2_000L)

            myClearedAtMs = snap?.getTimestamp("clearedAt.$me")?.toDate()?.time
        }
        onDispose { reg.remove() }
    }
    LaunchedEffect(peerTypingAtMs) {
        val ts = peerTypingAtMs
        if (ts != null) {
            delay(2200L)
            if (peerTypingAtMs == ts) peerTyping = false
        } else peerTyping = false
    }

    // Помощник: мы «почти внизу»?
    fun LazyListState.isNearBottom(threshold: Int = 1): Boolean {
        val info = layoutInfo
        val total = info.totalItemsCount
        if (total == 0) return true
        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
        return lastVisible >= total - 1 - threshold
    }

// — флажки и прошлый размер
    var initialJumpDone by remember { mutableStateOf(false) }
    var prevRowsSize by remember { mutableStateOf(0) }

// 1) Первый показ — мгновенно в самый низ (без анимации)
    LaunchedEffect(rows.size) {
        if (!initialJumpDone && rows.isNotEmpty()) {
            listState.scrollToItem(rows.lastIndex) // без анимации, чтобы не мигало
            initialJumpDone = true
            prevRowsSize = rows.size
        }
    }

    // 2) Если пришло новое сообщение и пользователь был у низа — плавно докрутить
    LaunchedEffect(rows.size) {
        if (!initialJumpDone && rows.isNotEmpty()) {
            listState.scrollToItem(rows.lastIndex)
            initialJumpDone = true
            prevRowsSize = rows.size
            return@LaunchedEffect
        }

        // ⬇️ защита
        if (rows.isEmpty()) {
            prevRowsSize = 0
            return@LaunchedEffect
        }

        if (initialJumpDone && rows.size > prevRowsSize && listState.isNearBottom()) {
            listState.animateScrollToItem(rows.lastIndex)
        }
        if (initialJumpDone && listState.isNearBottom()) {
            listState.scrollToItem(rows.lastIndex)
        }
        prevRowsSize = rows.size
    }



// 3) Держим низ при смене IME
    LaunchedEffect(imeBottomPx) {
        if (initialJumpDone && rows.isNotEmpty() && listState.isNearBottom()) {
            listState.scrollToItem(rows.lastIndex)
        }
    }

    // Отметим входящие как увиденные
    LaunchedEffect(messages) {
        ChatRepository.markAllIncomingAsSeen(mommyUid, babyUid, me)
    }

    // Вход в поиск — курсор на самое новое
    LaunchedEffect(searchActive, messages.size) {
        if (searchActive && messages.isNotEmpty()) {
            val idx = messages.lastIndex
            lastGoodMsgIndex = idx
            curMsg = idx
            scope.launch { listState.animateScrollToItem(rowIndexForMessage(idx), -20) }
        }
    }





    // НОВАЯ findHits: идём от НОВЫХ к старым, и по сообщению берём ТОЛЬКО первое совпадение.
    // Подсвечиваем не «буквы», а целое слово.
    fun findHits(messages: List<ChatMessage>, q: String): List<Hit> {
        if (q.isBlank()) return emptyList()
        val out = mutableListOf<Hit>()

        // бежим от конца (самые новые сообщения)
        for (msgIndex in messages.indices.reversed()) {
            val text = messages[msgIndex].text
            // ищем ПЕРВОЕ совпадение в этом сообщении (от начала текста)
            val i = text.indexOf(q, ignoreCase = true)
            if (i >= 0) {
                val range = expandToWord(text, i, i + q.length)
                out += Hit(msgIndex, range)
                // важно: дальше по этому сообщению НЕ ищем — переходим к следующему
            }
        }
        return out
    }

    LaunchedEffect(searchActive) {
        if (!searchActive) return@LaunchedEffect
        snapshotFlow { searchQuery }
            .debounce(220)                  // чтобы не дёргать при каждом символе
            .distinctUntilChanged()
            .collect { q ->
                if (q.isBlank()) {
                    hits = emptyList()
                    curHit = -1
                    lastGoodMsgIndex = null
                    lastQuery = ""
                    return@collect
                }

                // Пересчитываем совпадения
                val newHits = findHits(messages, q)
                hits = newHits

                // Если пусто — сбрасываемся
                if (newHits.isEmpty()) {
                    curHit = -1
                    lastGoodMsgIndex = null
                    lastQuery = q
                    return@collect
                }

                // Пытаемся «удержать» текущий фокус, если он всё ещё подходит
                val currentMsgIdx = lastGoodMsgIndex
                val keepIndex = currentMsgIdx?.let { idx ->
                    val stillMatches = newHits.indexOfFirst { it.msgIndex == idx } // любой хит в этом же сообщении
                    if (stillMatches >= 0) stillMatches else null
                }

                curHit = when {
                    keepIndex != null -> keepIndex
                    lastQuery.isNotEmpty() && q.startsWith(lastQuery, ignoreCase = true) -> {
                        // продолжаем набирать тот же хвост — логично прыгать к ПЕРВОМУ совпадению
                        0
                    }
                    else -> 0
                }

                // Скроллим ТОЛЬКО если есть куда и если изменился curHit
                val msgIdx = hits[curHit].msgIndex
                if (lastGoodMsgIndex != msgIdx) {
                    lastGoodMsgIndex = msgIdx
                    scope.launch { listState.animateScrollToItem(rowIndexForMessage(msgIdx), -20) }
                }
                lastQuery = q
            }
    }

    // Выделение текущего элемента (внутри/вне поиска)
    LaunchedEffect(searchActive, searchQuery, curMsg, curHit, hits) {
        selectedMsgIndex = when {
            !searchActive -> null
            searchQuery.isNotBlank() -> hits.getOrNull(curHit)?.msgIndex
            else -> curMsg
        }
    }


    suspend fun jumpTo(hitIndex: Int) {
        if (hits.isEmpty()) return
        val n = hits.size
        val idx = ((hitIndex % n) + n) % n
        curHit = idx
        val msgIdx = hits[idx].msgIndex
        listState.animateScrollToItem(rowIndexForMessage(msgIdx), -20)
    }

    suspend fun nextOlder(i: Int, size: Int): Int =
        if (size == 0) -1 else (i + 1) % size
    suspend fun nextNewer(i: Int, size: Int): Int =
        if (size == 0) -1 else if (i == 0) size - 1 else i - 1

    // Пересчёт совпадений при наборе запроса
    LaunchedEffect(messages, searchQuery, searchActive) {
        if (searchActive && searchQuery.isNotBlank()) {
            hits = findHits(messages, searchQuery)
            // карта: msgIndex -> range (по одному на сообщение)
            perMessageHit = hits.associate { it.msgIndex to it.range }
            curHit = if (hits.isNotEmpty()) 0 else -1
            if (curHit >= 0) jumpTo(curHit)
        } else {
            hits = emptyList()
            perMessageHit = emptyMap()
            curHit = -1
        }
    }

    // ───────────────────── ХЕЛПЕРЫ (внутри экрана) ──────────────────
    suspend fun deleteMessageById(mid: String) {
        chatRef.collection("messages").document(mid).delete().await()
    }

    fun copyToClipboard(text: String) {
        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("message", text))
        Toast.makeText(ctx, "Скопировала в буфер ✂️", Toast.LENGTH_SHORT).show()
    }

    fun replyWithQuote(text: String) {
        // простая цитатка в поле ввода (без изменения схемы БД)
        val snippet = text.lines().first().take(140)
        draft = "↪ $snippet\n" + if (draft.isBlank()) "" else draft
    }

    suspend fun hardWipeChat(chatRef: DocumentReference, chunk: Int = 400) {
        // Удаляем сообщениями порциями (лимит Firestore: 500 операций в батче)
        while (true) {
            val snap = chatRef.collection("messages")
                .limit(chunk.toLong())
                .get()
                .await()
            if (snap.isEmpty) break
            val batch = Firebase.firestore.batch()
            for (doc in snap.documents) batch.delete(doc.reference)
            batch.commit().await()
        }
    }

    suspend fun jumpToMessage(i: Int) {
        val n = messages.size
        if (n == 0) return
        val msgIdx = ((i % n) + n) % n
        curMsg = msgIdx
        listState.animateScrollToItem(rowIndexForMessage(msgIdx), -20)
    }

    val onPrevClick: () -> Unit = { // ↑
        scope.launch {
            if (searchQuery.isBlank()) {
                // вне поиска: вверх = к более старому сообщению
                jumpToMessage(curMsg - 1)
                lastGoodMsgIndex = curMsg
            } else {
                val n = hits.size
                if (n > 0) {
                    curHit = nextOlder(curHit, n)       // ↑ идём к старшим по времени
                    val msgIdx = hits[curHit].msgIndex
                    lastGoodMsgIndex = msgIdx
                    listState.animateScrollToItem(rowIndexForMessage(msgIdx), -20)
                }
            }
        }
    }
    val onNextClick: () -> Unit = { // ↓
        scope.launch {
            if (searchQuery.isBlank()) {
                // вне поиска: вниз = к более новому
                jumpToMessage(curMsg + 1)
                lastGoodMsgIndex = curMsg
            } else {
                val n = hits.size
                if (n > 0) {
                    curHit = nextNewer(curHit, n)       // ↓ возвращаемся к более новым
                    val msgIdx = hits[curHit].msgIndex
                    lastGoodMsgIndex = msgIdx
                    listState.animateScrollToItem(rowIndexForMessage(msgIdx), -20)
                }
            }
        }
    }

    fun exitSearch() {
        // Сбрасываем всё, чтобы и подсветка пропала, и рамочка
        searchActive = false
        searchQuery = ""
        hits = emptyList()
        perMessageHit = emptyMap()
        curHit = -1
        selectedMsgIndex = null
        lastGoodMsgIndex = null
        lastQuery = ""
    }





    // Мапа id -> индекс в списке для быстрого скролла
    val midToIndex = remember(messages) {
        messages.mapIndexed { idx, m -> m.id to idx }.toMap()
    }

    // как пощёлкать пины: null -> (n-2) -> ... -> 0 -> null
    fun nextPinIndex(cur: Int?, n: Int): Int? = when {
        n <= 0      -> null
        cur == null -> if (n == 1) 0 else n - 2    // первый тык: сразу ко "второму с конца"
        cur > 0     -> cur - 1                      // дальше идём: #2 -> #1
        else        -> null                         // было #1 -> выходим в "без номера"
    }

    // Скролл и подсветка
    fun scrollToMid(mid: String?) {
        val msgIdx = mid?.let { midToIndex[it] } ?: return
        val rowIdx = rowIndexForMessage(msgIdx)
        val offsetPx = -20  // можно: -with(density){16.dp.roundToPx()} если хочется dp
        scope.launch {
            listState.animateScrollToItem(rowIdx, offsetPx)
            flashMessage(mid)
        }
    }

    // ─────────────────────────── UI ────────────────────────────────

    androidx.compose.material3.Scaffold(
        topBar = {
            if (!searchActive) {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFFD9E7F1),
                        titleContentColor = Color.Black,
                        navigationIconContentColor = Color.Black,
                        actionIconContentColor = Color.Black
                    ),
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
                                if (peerTyping) TypingSubtitle()
                                else Text(
                                    presenceText(peerOnline, peerLastSeen),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0x99000000)
                                )
                            }
                        }
                    },
                    actions = {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        IconButton(onClick = {
                            android.widget.Toast.makeText(ctx, "Звонки скоро появятся 🥰", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Filled.Call, contentDescription = "Звонок") }

                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Поиск") },
                                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                                    onClick = { menuOpen = false; searchActive = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("Профиль собеседника") },
                                    onClick = {
                                        menuOpen = false
                                        android.widget.Toast.makeText(ctx, "Профили скоро появятся 🥰", android.widget.Toast.LENGTH_SHORT).show()
                                    })
                                DropdownMenuItem(
                                    text = { Text("Очистить чат") },
                                    leadingIcon = { Icon(Icons.Filled.DeleteForever, contentDescription = null) },
                                    onClick = {
                                        menuOpen = false
                                        clearDialogOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Медиа и файлы") },
                                    onClick = {
                                        menuOpen = false
                                        android.widget.Toast.makeText(ctx, "Медиа скоро появятся 🥰", android.widget.Toast.LENGTH_SHORT).show()
                                    })
                                DropdownMenuItem(
                                    text = { Text("Настройки чата") },
                                    onClick = {
                                        menuOpen = false
                                        android.widget.Toast.makeText(ctx, "Настройки скоро появятся 🥰", android.widget.Toast.LENGTH_SHORT).show()
                                    })
                            }
                        }
                    }
                )
            } else {
                ChatSearchTopBarPlain(
                    query = searchQuery,
                    onQueryChange = { q ->
                        searchQuery = q
                    },
                    onBack = {
                        exitSearch()
                    },
                    onClear = {
                        exitSearch()
                    },
                    autoFocus = true
                )
            }
        },
        containerColor = chatBg,
        contentWindowInsets = WindowInsets(0)
    ) { inner ->
        Box(
            Modifier
                .fillMaxSize()
                .background(chatBg)
                .padding(top = inner.calculateTopPadding())
        ) {
            val bottomPad = with(density) { (bottomBarH + imeBottomPx).toDp() } + 8.dp

            val topExtraPad = with(density) { pinnedBarH.toDp() } + 8.dp

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp + topExtraPad, bottom = bottomPad)
            ) {
                items(
                    count = rows.size,
                    key = { i ->
                        when (val r = rows[i]) {
                            is ChatRow.Header -> "hdr_${r.date}"                 // стабильный ключ на дату
                            is ChatRow.Msg    -> "msg_${messages[r.msgIndex].id}"
                        }
                    }
                ) { i ->
                    when (val r = rows[i]) {
                        is ChatRow.Header -> {
                            // Центрированный заголовок даты
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    color = Color(0x1A000000), // ~10% чёрного — полупрозрачная «плашка»
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = prettyDateTitle(r.date),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFF1B1B1B)
                                    )
                                }
                            }
                        }
                        is ChatRow.Msg -> {
                            val index = r.msgIndex
                            val m = messages[index]
                            val mine = m.fromUid == me
                            val isLastInGroup = run {
                                val next = messages.getOrNull(index + 1)
                                next == null || next.fromUid != m.fromUid
                            }
                            val selected = searchActive && (selectedMsgIndex == index)

                            val density = LocalDensity.current
                            var myRect by remember { mutableStateOf<RectF?>(null) }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onGloballyPositioned { coords ->
                                        val pos = coords.positionInRoot()
                                        val sz = coords.size
                                        myRect = RectF(
                                            left = pos.x,
                                            top  = pos.y,
                                            right = pos.x + sz.width,
                                            bottom = pos.y + sz.height
                                        )
                                    }
                                    .animateItemPlacement() // мягко переставляемся
                            ) {

                                val isPinnedForMe = pinnedSetForMe.contains(m.id)
                                val isPinService = isPinServiceText(m.text)
                                if (isPinService) {
                                    // центрированный «чипчик» вместо пузырька
                                    CenterSystemChip(text = m.text)
                                } else {
                                    val isPinnedForMe = pinnedSetForMe.contains(m.id)
                                    ChatBubble(
                                        message = m,
                                        mine = mine,
                                        showAvatar = !mine && isLastInGroup,
                                        peerName = peerName,
                                        peerPhoto = peerPhoto,
                                        highlightRange = if (searchActive && searchQuery.isNotBlank()) perMessageHit[index] else null,
                                        selected = (searchActive && (selectedMsgIndex == index)) || (flashHighlightedId == m.id),
                                        onTap = {
                                            val r = myRect
                                            if (r != null) {
                                                selectedMsgId = m.id
                                                selectedRect = r
                                            } else {
                                                Toast.makeText(
                                                    ctx,
                                                    "Еще меряем, тапни ещё разик 🫶",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        meUid = me,
                                        meName = meName,
                                        onReplyAnchorClick = { anchorMid ->
                                            val targetIdx =
                                                messages.indexOfFirst { it.id == anchorMid }
                                            if (targetIdx >= 0) {
                                                scope.launch {
                                                    val row = rowIndexForMessage(targetIdx)
                                                    listState.animateScrollToItem(row, -20)
                                                    flashMessage(messages[targetIdx].id)                        // вспыхнули
                                                }
                                            } else {
                                                Toast.makeText(
                                                    ctx,
                                                    "Того сообщения уже нет здесь",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                        },
                                        isPinned = isPinnedForMe
                                    )
                                }
                            }

                        }
                    }
                }
            }

            // ВЕРХНЯЯ ПЛАНОЧКА
            if (pinsForMe.isNotEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp, start = 8.dp, end = 8.dp)
                        .onSizeChanged { pinnedBarH = it.height }
                ) {
                    PinnedBanner(
                        title   = titleText,
                        snippet = shownText,
                        onClick = {
                            val n = pinsForMe.size
                            if (n == 0) return@PinnedBanner

                            // 1) Куда скроллим СЕЙЧАС (по текущему состоянию pinIdx):
                            val scrollMid = when (pinIdx) {
                                null -> pinsForMe.last().mid            // были «без номера» → прыжок к самому новому
                                else -> pinsForMe[pinIdx!!].mid         // были в #k → прыжок к #k
                            }
                            scrollToMid(scrollMid)

                            // 2) Что ПОКАЖЕМ СЛЕДУЮЩИМ (обновляем «номерок» в плашке):
                            pinIdx = when {
                                n <= 1          -> null                 // один пин — всегда без номера
                                pinIdx == null  -> n - 2                // с «без номера» → сразу к #2
                                pinIdx!! > 0    -> pinIdx!! - 1         // #k → #(k-1)
                                else            -> null                 // было #1 → обратно в «без номера»
                            }
                        }
                    )
                }
            }



            // ─────────── ВСПЛЫВАЮЩЕЕ МЕНЮ (центрируем по пузырю, клампим по экрану, лёгкий скрим) ───────────
            val selectedMsg = remember(selectedMsgId, messages) { messages.find { it.id == selectedMsgId } }

            if (selectedMsgId != null && selectedRect != null && selectedMsg != null) {
                var rootSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
                var menuSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

                // Перекрывающий слой (ловит тап вне меню и даёт лёгкое затемнение)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10f)
                        .onGloballyPositioned { rootSize = it.size }
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {
                                selectedMsgId = null
                                selectedRect = null
                            })
                        }
                ) {
                    // Едва заметный общий скрим по всему экрану
                    Box(
                        Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.06f)) // «капелька» затемнения
                    )

                    val r = selectedRect!!

                    // Желанная позиция — центрируем меню относительно выбранного пузыря
                    val desiredX = (((r.left + r.right) / 2f) - menuSize.width / 2f).roundToInt()
                    val desiredY = (((r.top  + r.bottom) / 2f) - menuSize.height / 2f).roundToInt()

                    // Клампим в пределах экрана
                    val clampedX = desiredX.coerceIn(0, (rootSize.width  - menuSize.width ).coerceAtLeast(0))
                    val clampedY = desiredY.coerceIn(0, (rootSize.height - menuSize.height).coerceAtLeast(0))

                    // Само меню
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(clampedX, clampedY),
                        properties = PopupProperties(
                            focusable = true,
                            dismissOnClickOutside = true,
                            excludeFromSystemGesture = true
                        ),
                        onDismissRequest = {
                            selectedMsgId = null
                            selectedRect = null
                        }
                    ) {
                        // Сначала меряем — затем корректная центровка/кламп
                        Box(Modifier.onGloballyPositioned { menuSize = it.size }) {
                            val readLine: String? = selectedMsg.let { m ->
                                // показываем только для СВОИХ сообщений, которые реально прочитал собеседник
                                if (m.fromUid == me && m.seen && m.seenAt != null)
                                    "Прочитано в ${formatHmLocal(m.seenAt)}"
                                else null
                            }
                            val isPinned = pinnedSet.contains(selectedMsg.id)
                            MessageContextMenu(
                                headerText = readLine,
                                onReply = {
                                    replyingTo = selectedMsg
                                    selectedMsgId = null
                                    selectedRect = null
                                },
                                onCopy = {
                                    copyToClipboard(selectedMsg.text)
                                    selectedMsgId = null
                                    selectedRect = null
                                },
                                onForward = {
                                    forwarding = ForwardPayload(
                                        fromUid  = selectedMsg.fromUid,
                                        fromName = if (selectedMsg.fromUid == me) meName else peerName,
                                        fromPhoto= if (selectedMsg.fromUid == me) mePhoto else peerPhoto,
                                        text     = selectedMsg.text
                                    )
                                    replyingTo = null                      // одновременно и «ответ» и «переслать» не держим
                                    selectedMsgId = null; selectedRect = null
                                },
                                onPin = {
                                    scope.launch {
                                        ensureChatRoot(chatRef, mommyUid, babyUid)

                                        try {
                                            if (isPinned) {
                                                ChatRepository.removePin(mommyUid, babyUid, selectedMsg.id)
                                                ChatRepository.sendText(mommyUid, babyUid, fromUid = me, toUid = peerUid, text = "$meName открепил(а) сообщение")
                                            } else {
                                                ChatRepository.addPin(mommyUid, babyUid, selectedMsg.id, me)
                                                ChatRepository.sendText(mommyUid, babyUid, fromUid = me, toUid = peerUid, text = "$meName закрепил(а) сообщение 📌")
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(ctx, "Не получилось: ${e.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    selectedMsgId = null; selectedRect = null
                                },
                                pinTitle = if (isPinned) "Открепить" else "Закрепить",
                                onEdit = {
                                    Toast.makeText(ctx, "Редактировать нельзя (правила Firestore)", Toast.LENGTH_SHORT).show()
                                    selectedMsgId = null
                                    selectedRect = null
                                },
                                onDelete = {
                                    deleteConfirmForId = selectedMsg.id
                                    selectedMsgId = null
                                    selectedRect = null
                                }
                            )
                        }
                    }
                }
            }


            val bottomSafePad = with(density) { (bottomBarH + imeBottomPx).toDp() } + 12.dp
            AnimatedVisibility(
                visible = initialJumpDone && !atBottom,          // ⬅️ вот ключ
                enter = slideInVertically { it } + fadeIn(),     // всплытие снизу + fade
                exit  = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = bottomSafePad)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 4.dp,
                    modifier = Modifier.clickable {
                        scope.launch { listState.animateScrollToItem(rows.lastIndex) }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = "В конец",
                        modifier = Modifier.padding(10.dp),
                        tint = Color(0xFF1B1B1B)
                    )
                }
            }

            if (searchActive) {
                val bottomSafePad = with(density) { (bottomBarH + imeBottomPx).toDp() } + 12.dp
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = bottomSafePad),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircleNavButton(onClick = onPrevClick, icon = Icons.Filled.ArrowUpward, contentDesc = "Предыдущее")
                    Spacer(Modifier.height(10.dp))
                    CircleNavButton(onClick = onNextClick, icon = Icons.Filled.ArrowDownward, contentDesc = "Следующее")
                }
            }

            val bottomModifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .onSizeChanged { bottomBarH = it.height }

            if (!searchActive) {
                InputBarTelegramFullWidth(
                    draft = draft,
                    onDraft = { newText ->
                        draft = newText
                        val now = System.currentTimeMillis()

                        if (chatReady && now - lastTypingPingAt > 1_200L) {
                            lastTypingPingAt = now                      // ⬅️ не забыть
                            chatRef.update("typing.$me", FieldValue.serverTimestamp())
                                .addOnFailureListener { e ->
                                    Toast.makeText(ctx, "typing отказался: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }

                        idleClearJob?.cancel()
                        idleClearJob = scope.launch {
                            delay(3_000L)
                            if (draft == newText && chatReady) {
                                chatRef.update("typing.$me", FieldValue.delete())
                            }
                        }
                    },
                    onSend = {
                        val to = if (me == mommyUid) babyUid else mommyUid
                        scope.launch {
                            try {
                                ensureChatRoot(chatRef, mommyUid, babyUid)
                                if (draft.isNotBlank()) {
                                    ChatRepository.sendText(
                                        mommyUid, babyUid,
                                        fromUid = me, toUid = to,
                                        text = draft,
                                        reply = replyingTo?.let { ReplyPayload(it.id, it.fromUid, it.text.take(200)) }
                                    )
                                }
                                forwarding?.let { f ->
                                    ChatRepository.sendText(
                                        mommyUid, babyUid,
                                        fromUid = me, toUid = to,
                                        text = f.text,                     // сам текст пересылаемого сообщения
                                        forward = f                        // а это «шапочка переслано от»
                                    )
                                }
                                draft = ""; replyingTo = null; forwarding = null
                                chatRef.update("typing.$me", FieldValue.delete())
                            } catch (e: Exception) {
                                Toast.makeText(ctx, "Ой, не вышло: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    replyingTo   = replyingTo,
                    forwardFrom  = forwarding,
                    meUid        = me,
                    meName       = meName,
                    peerName     = peerName,
                    onCancelReply = { replyingTo = null },
                    onCancelForward = { forwarding = null },
                    modifier = bottomModifier
                )
            } else {
                SearchBottomDock(
                    curMsg = curMsg,
                    totalMsgs = messages.size,
                    curHit = curHit,
                    totalHits = hits.size,
                    onPrev = onPrevClick,
                    onNext = onNextClick,
                    onPickDate = { datePickerOpen = true },
                    onOpenList = { showHitsSheet = true },
                    modifier = bottomModifier,
                    isQueryEmpty = searchQuery.isBlank()
                )
            }

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = bottomPad),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = Color(0x88000000),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Здесь пока ничего нет...",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Пожалуйста, отправьте своё первое сообщение!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Плавающая дата вверху (как оверлей)
            AnimatedVisibility(
                visible = showDateBadge && topBadgeDate != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    // ниже плашки «Закреплённое сообщение»
                    .padding(top = with(density) { pinnedBarH.toDp() } + 15.dp)
            ) {
                topBadgeDate?.let { d ->
                    Surface(color = Color(0x33000000), shape = RoundedCornerShape(12.dp)) {
                        Text(
                            text = prettyDateTitle(d),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (showHitsSheet) {
            SearchResultsSheet(
                hits = hits,
                messages = messages,
                peerName = peerName,
                peerPhoto = peerPhoto,
                meUid = me,
                meName = meName,
                mePhoto = mePhoto,
                query = searchQuery,
                onPick = { i ->
                    showHitsSheet = false
                    scope.launch {
                        curHit = i
                        val msgIdx = hits[curHit].msgIndex
                        listState.animateScrollToItem(rowIndexForMessage(msgIdx), -20)
                    }
                },
                onClose = { showHitsSheet = false }
            )
        }
    }



    if (clearDialogOpen) {
        AlertDialog(
            onDismissRequest = { clearDialogOpen = false },
            title = { Text("Очистить чат?") },
            text = {
                Column {
                    Text(
                        "Сметём эту переписочку пушистым веничком ✨\n" +
                                "Без галочки — спрячем историю только у вас (у собеседника ничего не пропадёт).\n" +
                                "С галочкой — удалим всё у обоих навсегда. Это действие необратимо.",
                        color = Color(0xFF1B1B1B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = alsoForPeer, onCheckedChange = { alsoForPeer = it })
                        Spacer(Modifier.width(6.dp))
                        Text("И у собеседника (навсегда удалить у обоих)")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                ensureChatRoot(chatRef, mommyUid, babyUid)
                                if (alsoForPeer) {
                                    // 1) снести все messages (у тебя уже ок)
                                    hardWipeChat(chatRef)

                                    // 2) убрать СВОЮ "печатает" (а не весь map)
                                    chatRef.update("typing.$me", FieldValue.delete()).await()

                                    // 3) опционально убрать СВОЮ метку clearedAt (или просто оставить — не мешает)
                                    chatRef.update("clearedAt.$me", FieldValue.delete()).await()

                                    android.widget.Toast
                                        .makeText(ctx, "Готово. Переписка удалена у обоих 🌬️", android.widget.Toast.LENGTH_SHORT)
                                        .show()
                                } else {
                                    // МЯГКАЯ очистка: только спрятать у меня
                                    chatRef.update("clearedAt.${me}", FieldValue.serverTimestamp()).await()
                                    android.widget.Toast
                                        .makeText(ctx, "Спрятали историю только у вас 💧", android.widget.Toast.LENGTH_SHORT)
                                        .show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast
                                    .makeText(ctx, "Не получилось очистить: ${e.message}", android.widget.Toast.LENGTH_LONG)
                                    .show()
                            } finally {
                                clearDialogOpen = false
                                alsoForPeer = false
                            }
                        }
                    }
                ) { Text(if (alsoForPeer) "Удалить навсегда" else "Очистить") }
            },
            dismissButton = {
                TextButton(onClick = { clearDialogOpen = false }) { Text("Отмена") }
            }
        )
    }




    if (datePickerOpen) {
        val state = androidx.compose.material3.rememberDatePickerState()
        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    val sel = state.selectedDateMillis
                    if (sel != null) {
                        val selDate = java.time.Instant.ofEpochMilli(sel)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()

                        val idx = messages.indexOfLast { m ->
                            m.at != null && m.at!!.toLocalDate() == selDate
                        }
                        if (idx >= 0) {
                            datePickerOpen = false
                            scope.launch { jumpToMessage(idx) }
                        } else {
                            datePickerOpen = false
                        }
                    } else datePickerOpen = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton({ datePickerOpen = false }) { Text("Отмена") } }
        ) {
            androidx.compose.material3.DatePicker(state = state)
        }
    }

    if (deleteConfirmForId != null) {
        AlertDialog(
            onDismissRequest = { deleteConfirmForId = null },
            title = { Text("Удалить сообщение?") },
            text  = { Text("Уйдёт насовсем у обоих. Отменить нельзя.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            deleteMessageById(deleteConfirmForId!!)
                            Toast.makeText(ctx, "Удалено 🗑️", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Не вышло: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            deleteConfirmForId = null
                        }
                    }
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteConfirmForId = null }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun PinnedBanner(
    title: String,
    snippet: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFEFF6FF),
        shadowElevation = 2.dp,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PushPin, null, tint = Color(0xFF1E88E5))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Закреплённое сообщение" + if (title.isNotBlank()) " $title" else "",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color(0xFF1B1B1B)
                )
                Text(snippet, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF1B1B1B), maxLines = 1)
            }
            Icon(Icons.Filled.ArrowDownward, null, tint = Color(0xFF1E88E5), modifier = Modifier.size(18.dp))
        }
    }
}


//«умная» реакция на ввод с задержкой
// РАСШИРЕНИЕ: «это буква слова?» (учитывает цифры тоже)
private fun Char.isWordChar(): Boolean = this.isLetterOrDigit()




private suspend fun ensureChatRoot(
    chatRef: DocumentReference,
    mommyUid: String,
    babyUid: String
) {
    val snap = chatRef.get().await()
    if (!snap.exists()) {
        chatRef.set(
            mapOf(
                "mommyUid" to mommyUid,
                "babyUid"  to babyUid,
                "createdAt" to FieldValue.serverTimestamp()
            ),
            SetOptions.merge()
        ).await()
    }
}

// Расширяем найденный кусочек до целого слова (по границам не-букв)
private fun expandToWord(text: String, start: Int, end: Int): IntRange {
    var l = start
    var r = end
    while (l > 0 && text[l - 1].isWordChar()) l--
    while (r < text.length && text[r].isWordChar()) r++
    return l until r
}

// ┌───────────────────────────────────────────────────────────────────┐
// │                          КОМПОНЕНТЫ UI                           │
/* └───────────────────────────────────────────────────────────────────┘ */

@Composable
private fun CenterSystemChip(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = Color(0x1A000000),                 // ~10% чёрного — полупрозрачный фон
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 0.dp
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF1B1B1B)
            )
        }
    }
}

// — аватар
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

// — подпись «печатает…»
@Composable
private fun TypingSubtitle() {
    var dots by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(350)
            dots = (dots + 1) % 4
        }
    }
    val dotsStr = ".".repeat(dots)
    Text(
        text = "печатает$dotsStr",
        style = MaterialTheme.typography.bodySmall,
        color = Color(0x99000000)
    )
}

// — кнопка кругленькая навигации
@Composable
private fun CircleNavButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDesc: String
) {
    Surface(
        shape = CircleShape,
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 3.dp,
        modifier = Modifier.size(56.dp)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            Icon(icon, contentDesc, modifier = Modifier.size(28.dp), tint = Color(0xFF1B1B1B))
        }
    }
}

// — панель ввода
@Composable
private fun InputBarTelegramFullWidth(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    replyingTo: ChatMessage? = null,
    forwardFrom: ForwardPayload? = null,
    onCancelForward: (() -> Unit)? = null,
    meUid: String = "",
    meName: String = "Вы",
    peerName: String = "",
    onCancelReply: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        tonalElevation = 0.dp,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = modifier
    ) {
        Column {
            if (forwardFrom != null) {
                ForwardPreviewBar(forwardFrom, onCancelForward) // ← НОВОЕ
                HorizontalDivider(color = Color(0x11000000))
            }
            // ─ верхняя полосочка «В ответ …» (если есть)
            if (replyingTo != null) {
                ReplyPreviewBar(
                    author = if (replyingTo.fromUid == meUid) meName else peerName,
                    snippet = replyingTo.text.take(140),
                    onClose = onCancelReply
                )
                HorizontalDivider(color = Color(0x11000000))
            }

            // ─ сам input как и был
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { /* TODO: emoji */ }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.Mood, null, tint = Color(0x99000000))
                }

                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF1B1B1B)),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF1B1B1B)),
                    minLines = 1,
                    maxLines = 6,
                    modifier = Modifier
                        .widthIn(min = 120.dp, max = 260.dp)
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .heightIn(min = 46.dp, max = 200.dp),
                    decorationBox = { inner ->
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            if (draft.isBlank()) Text("Сообщение", color = Color(0x99000000), style = MaterialTheme.typography.bodyLarge)
                            inner()
                        }
                    }
                )

                IconButton(onClick = { /* TODO: attach */ }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Outlined.AttachFile, null, tint = Color(0x99000000))
                }

                val canSend = draft.isNotBlank() || forwardFrom != null
                FilledIconButton(
                    onClick = onSend,
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (canSend) Color(0xFF3DA5F5) else Color(0xFFB0BEC5),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(40.dp)
                ) { Icon(Icons.Filled.Send, contentDescription = "Отправить") }
            }
        }
    }
}

@Composable
private fun ForwardPreviewBar(payload: ForwardPayload, onClose: (() -> Unit)?) {
    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Send, null, tint = Color(0xFF3DA5F5), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Переслать 1 сообщение", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.weight(1f))
            if (onClose != null) IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, null, tint = Color(0x99000000))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PeerAvatar(payload.fromPhoto, payload.fromName, 22.dp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(payload.fromName, color = Color(0xFF1976D2), style = MaterialTheme.typography.labelMedium)
                Text(payload.text.take(140), color = Color(0x99000000), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ReplyPreviewBar(
    author: String,
    snippet: String,
    onClose: (() -> Unit)? = null
) {
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // стрелочка как в Telegram
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Reply,
            contentDescription = null,
            tint = Color(0xFF3DA5F5),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(author, color = Color(0xFF1976D2), style = MaterialTheme.typography.labelLarge)
            Text(snippet, color = Color(0x99000000), maxLines = 1)
        }
        if (onClose != null) {
            IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Filled.Close, null, tint = Color(0x99000000))
            }
        }
    }
}


// — топбар поиска
@Composable
fun ChatSearchTopBarPlain(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    autoFocus: Boolean = false,
) {
    TopAppBar(
        modifier = Modifier.statusBarsPadding(),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color(0xFFD9E7F1),
            titleContentColor = Color.Black,
            navigationIconContentColor = Color.Black,
            actionIconContentColor = Color.Black
        ),
        windowInsets = WindowInsets(0),
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
        },
        title = {
            val textStyle = MaterialTheme.typography.titleMedium.copy(color = Color.Black)

            val focusRequester = remember { FocusRequester() }
            val keyboard = LocalSoftwareKeyboardController.current

            // как только бар появился — фокус и показать клавиатуру
            LaunchedEffect(autoFocus) {
                if (autoFocus) {
                    focusRequester.requestFocus()
                    keyboard?.show()
                }
            }

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Black),
                textStyle = textStyle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .focusRequester(focusRequester),
                decorationBox = { inner ->
                    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (query.isEmpty()) Text("Поиск", style = textStyle.copy(color = Color.Black.copy(alpha = .7f)))
                        Box(Modifier.weight(1f)) { inner() }
                        if (query.isNotEmpty()) IconButton(onClick = onClear) {
                            Icon(Icons.Filled.Close, null, tint = Color.Black)
                        }
                    }
                }
            )
        }
    )
}

// — нижняя панель поиска (док)
@Composable
private fun SearchBottomDock(
    isQueryEmpty: Boolean,
    curMsg: Int,
    totalMsgs: Int,
    curHit: Int,
    totalHits: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickDate: () -> Unit,
    onOpenList: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.White,
        shadowElevation = 6.dp,
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
        modifier = modifier
    ) {
        SearchBottomBar(
            isQueryEmpty = isQueryEmpty,
            curMsg = curMsg,
            totalMsgs = totalMsgs,
            curHit = curHit,
            totalHits = totalHits,
            onPickDate = onPickDate,
            onOpenList = onOpenList
        )
    }
}

@Composable
private fun SearchBottomBar(
    isQueryEmpty: Boolean,
    curMsg: Int,
    totalMsgs: Int,
    curHit: Int,
    totalHits: Int,
    onPickDate: () -> Unit,
    onOpenList: () -> Unit
) {
    val (n, m) = if (isQueryEmpty) {
        val positionFromNewest = if (totalMsgs == 0) 0 else (totalMsgs - curMsg)
        positionFromNewest to totalMsgs
    } else {
        (if (curHit < 0) 0 else curHit + 1) to totalHits
    }

    Surface(tonalElevation = 2.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPickDate) { Icon(Icons.Filled.AccessTime, contentDescription = "По дате") }
            Spacer(Modifier.width(6.dp))
            Text("$n из $m", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.weight(1f))
            TextButton(enabled = m > 0, onClick = onOpenList) { Text("Списком") }
        }
    }
}

// — шторка результатов поиска
@Composable
private fun SearchResultsSheet(
    hits: List<Hit>,
    messages: List<ChatMessage>,
    peerName: String,
    peerPhoto: String?,
    meName: String,
    mePhoto: String?,
    meUid: String,
    query: String,
    onPick: (indexInHits: Int) -> Unit,
    onClose: () -> Unit
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheet,
        containerColor = Color.White
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${hits.size} результат${if (hits.size % 10 == 1 && hits.size % 100 != 11) "" else "а"}",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("В чате") }
        }

        HorizontalDivider()

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(hits) { i, h ->
                val m = messages[h.msgIndex]
                SearchResultRow(
                    mine = m.fromUid == meUid,
                    meName = meName,
                    mePhoto = mePhoto,
                    peerName = peerName,
                    peerPhoto = peerPhoto,
                    text = m.text,
                    time = formatHmLocal(m.at),
                    query = query,
                    onClick = { onPick(i) }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    mine: Boolean,
    meName: String,
    mePhoto: String?,
    peerName: String,
    peerPhoto: String?,
    text: String,
    time: String,
    query: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val title = if (mine) "Вы" else (peerName.ifBlank { "Без имени" })
        val photoUrl = if (mine) mePhoto else peerPhoto

        PeerAvatar(photo = photoUrl, name = title, sizeDp = 40.dp)
        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = Color(0xFF1B1B1B))
                Spacer(Modifier.weight(1f))
                Text(time, style = MaterialTheme.typography.labelSmall, color = Color(0x99000000))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildHighlightedSnippet(text, query, radius = 28),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF1B1B1B),
                maxLines = 2
            )
        }
    }
}

// — пузырёк + измерялка
@Composable
private fun ChatBubble(
    message: ChatMessage,
    mine: Boolean,
    showAvatar: Boolean,
    peerName: String,
    peerPhoto: String?,
    highlightRange: IntRange?,
    selected: Boolean = false,
    onTap: (() -> Unit)? = null,
    meUid: String,
    meName: String,
    onReplyAnchorClick: ((String) -> Unit)? = null,
    isPinned: Boolean = false
) {
    val baseColor = if (mine) Color(0xFFDCF7C5) else Color.White
    val selectedColor = if (mine) Color(0xFFE9FFD6) else Color(0xFFFFFDE7)

    val bg by animateColorAsState(
        targetValue = if (selected) selectedColor else baseColor,
        label = "bubbleBg"
    )

    val shape = RoundedCornerShape(
        topStart = 18.dp, topEnd = 18.dp,
        bottomEnd = if (mine) 4.dp else 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        if (!mine) {
            if (showAvatar) PeerAvatar(peerPhoto, peerName, 28.dp) else Spacer(Modifier.size(28.dp))
            Spacer(Modifier.width(6.dp))
        }

        Surface(
            color = bg,
            shape = shape,
            tonalElevation = 1.dp,
            shadowElevation = if (selected) 2.dp else 0.dp,
            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF3DA5F5)) else null,
            modifier = Modifier
                .widthIn(max = if (mine) 360.dp else 320.dp)
                .clickable(enabled = onTap != null) { onTap?.invoke() }
        ) {
            Column {
                if (isPinned) {             // ← маленькая скрепочка сверху
                    Row(Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PushPin, null, modifier = Modifier.size(14.dp), tint = Color(0xFF1E88E5))
                        Spacer(Modifier.width(4.dp))
                    }
                }
                message.forward?.let { f ->
                    ForwardHeader(name = f.fromName, photo = f.fromPhoto)
                    Spacer(Modifier.height(4.dp))
                }

                // ─ если это ответ — рисуем голубую шапочку
                message.reply?.let { r ->
                    ReplyStub(
                        author = if (r.fromUid == meUid) meName else peerName,
                        text = r.text,
                        onClick = { onReplyAnchorClick?.invoke(r.mid) }
                    )
                    Spacer(Modifier.height(4.dp))
                }
                BubbleMeasured(
                    text = message.text, mine = mine, at = message.at, seen = message.seen, highlightRange = highlightRange
                )
            }
        }
    }
}

@Composable
private fun ForwardHeader(name: String, photo: String?) {
    Row(
        Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Переслано от", style = MaterialTheme.typography.labelSmall, color = Color(0x99000000))
        Spacer(Modifier.width(6.dp))
        PeerAvatar(photo, name, 18.dp)
        Spacer(Modifier.width(6.dp))
        Text(name, color = Color(0xFF1976D2), style = MaterialTheme.typography.labelLarge)
    }
}


@Composable
private fun ReplyStub(
    author: String,
    text: String,
    onClick: (() -> Unit)? = null
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Surface(
        color = Color(0xFFE6F2FF),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .padding(start = 8.dp, top = 8.dp, end = 8.dp)
            .then(clickMod)
    ) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Box(Modifier.width(3.dp).heightIn(min = 24.dp).background(Color(0xFF3DA5F5)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(author, color = Color(0xFF1976D2), style = MaterialTheme.typography.labelLarge)
                Text(text, color = Color(0xFF1B1B1B), maxLines = 1)
            }
        }
    }
}



@Composable
private fun BubbleMeasured(
    text: String,
    mine: Boolean,
    at: com.google.firebase.Timestamp?,
    seen: Boolean,
    highlightRange: IntRange?
) {
    val metaColor = Color(0x99000000)
    val density = LocalDensity.current

    val padStart = 12.dp
    val padTop = 6.dp
    val padBottomSingle = 6.dp
    val padBottomMulti  = 3.dp
    val padRightExtra   = 2.dp
    val gapTextMeta     = 4.dp
    val inlineGapSmall  = 6.dp

    val touchGap       = 2.dp
    val pushDownMin    = 3.dp
    val pushDownMax    = 15.dp

    SubcomposeLayout { constraints ->
        val metaPlaceables = subcompose("meta") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatHmLocal(at),
                    style = MaterialTheme.typography.labelSmall,
                    color = metaColor
                )
                if (mine) {
                    Spacer(Modifier.width(gapTextMeta))
                    val (icon, tint) = when {
                        at == null -> Icons.Filled.AccessTime to metaColor
                        seen       -> Icons.Filled.DoneAll   to metaColor
                        else       -> Icons.Filled.Done      to metaColor
                    }
                    Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
                }
            }
        }.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val metaW = metaPlaceables.maxOfOrNull { it.width } ?: 0
        val metaH = metaPlaceables.maxOfOrNull { it.height } ?: 0

        val rightReservePx = metaW + with(density) { padRightExtra.toPx() }.toInt()

        var probeLines = 1
        subcompose("probe") {
            Box(
                Modifier.padding(
                    start = padStart, top = padTop,
                    end   = with(density) { rightReservePx.toDp() }
                )
            ) { Text(text, onTextLayout = { probeLines = it.lineCount }) }
        }.forEach { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val isMulti = probeLines > 1

        val endPadDp: Dp =
            if (isMulti) 6.dp
            else with(density) { rightReservePx.toDp() } + inlineGapSmall
        val padBottomDp: Dp   = if (isMulti) padBottomMulti else padBottomSingle
        val bottomLiftDp: Dp  = if (isMulti) 1.dp else 2.dp

        val endPadPx       = with(density) { endPadDp.toPx() }.toInt()
        val padStartPx     = with(density) { padStart.toPx() }.toInt()
        val padTopPx       = with(density) { padTop.toPx() }.toInt()
        val padBottomPx    = with(density) { padBottomDp.toPx() }.toInt()
        val bottomLiftPx   = with(density) { bottomLiftDp.toPx() }.toInt()
        val touchGapPx     = with(density) { touchGap.toPx() }.toInt()
        val pushDownMinPx  = with(density) { pushDownMin.toPx() }.toInt()
        val pushDownMaxPx  = with(density) { pushDownMax.toPx() }.toInt()

        var lastLineRightLocal = 0f
        val bodyPlaceables = subcompose("body") {
            Box(
                Modifier.padding(
                    start = padStart, top = padTop, bottom = padBottomDp, end = endPadDp
                )
            ) {
                Text(
                    text = buildHighlighted(text, highlightRange),   // ⬅️ см. функцию ниже
                    color = Color(0xFF1B1B1B),
                    onTextLayout = { lr -> lastLineRightLocal = lr.getLineRight(lr.lineCount - 1) }
                )
            }
        }.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }

        val bodyW = bodyPlaceables.maxOfOrNull { it.width } ?: 0
        val bodyH = bodyPlaceables.maxOfOrNull { it.height } ?: 0

        var width  = maxOf(bodyW, padStartPx + endPadPx + 1)
        var height = maxOf(bodyH, padTopPx + metaH + padBottomPx)

        val metaBaseX = width - metaW - with(density) { padRightExtra.toPx() }.toInt()
        var metaY     = height - metaH - bottomLiftPx

        if (isMulti) {
            val lastLineRightAbs = padStartPx + lastLineRightLocal.toInt()
            val overlapPx = lastLineRightAbs - (metaBaseX - touchGapPx)
            if (overlapPx > 0) {
                val k = (overlapPx.toFloat() / (metaW.toFloat())).coerceIn(0f, 1f)
                val extraPushPx = (pushDownMinPx + k * (pushDownMaxPx - pushDownMinPx)).toInt()
                val needMore = (extraPushPx - bottomLiftPx).coerceAtLeast(0)
                height = maxOf(height, bodyH + needMore)

                metaY = minOf(height - metaH, (height - metaH - bottomLiftPx) + extraPushPx)
                width = maxOf(width, padStartPx + metaW + with(density){ padRightExtra.toPx() }.toInt())
            }
        }

        layout(width, height) {
            bodyPlaceables.forEach { it.place(0, 0) }
            val metaX = width - metaW - with(density) { padRightExtra.toPx() }.toInt()
            metaPlaceables.forEach { it.place(metaX, metaY) }
        }
    }
}

// ┌───────────────────────────────────────────────────────────────────┐
// │                             УТИЛИТЫ                               │
/* └───────────────────────────────────────────────────────────────────┘ */
private fun isPinServiceText(t: String): Boolean =
    t.contains("закрепил(а) сообщение") || t.contains("открепил(а) сообщение")

private fun presenceText(
    isOnline: Boolean,
    lastSeen: com.google.firebase.Timestamp?
): String {
    if (isOnline) return "в сети"
    val rawMs = lastSeen?.toDate()?.time ?: return "был(а) недавно"
    val now = System.currentTimeMillis()
    val ms = minOf(rawMs, now)
    val zdt = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return "был(а) в %02d:%02d".format(zdt.hour, zdt.minute)
}

// Локальный формат заголовков ("Сегодня", "Вчера", 15 августа 2025)
private fun prettyDateTitle(d: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    val yesterday = today.minusDays(1)
    return when (d) {
        today -> "Сегодня"
        yesterday -> "Вчера"
        else -> d.format(
            java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy")
        )
    }
}

private fun formatHmLocal(ts: com.google.firebase.Timestamp?): String {
    val rawMs = ts?.toDate()?.time ?: return "···"
    val nowMs = System.currentTimeMillis()
    val ms = kotlin.math.min(rawMs, nowMs)
    val zdt = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalTime()
    return "%02d:%02d".format(zdt.hour, zdt.minute)
}

private fun com.google.firebase.Timestamp.toLocalDate(): LocalDate =
    java.time.Instant.ofEpochMilli(this.toDate().time)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDate()

private fun buildHighlighted(
    text: String,
    range: IntRange?
): androidx.compose.ui.text.AnnotatedString {
    if (range == null || range.first !in text.indices) {
        return androidx.compose.ui.text.buildAnnotatedString { append(text) }
    }
    val start = range.first.coerceIn(0, text.length)
    val end   = range.last.plus(1).coerceIn(start, text.length)

    return androidx.compose.ui.text.buildAnnotatedString {
        append(text.substring(0, start))
        pushStyle(androidx.compose.ui.text.SpanStyle(background = Color(0xFFFFEB3B)))
        append(text.substring(start, end))
        pop()
        append(text.substring(end))
    }
}

private fun buildHighlightedSnippet(
    full: String,
    query: String,
    radius: Int = 28
): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank()) return androidx.compose.ui.text.buildAnnotatedString { append(full) }
    val lower = full.lowercase()
    val q = query.lowercase()
    val i = lower.indexOf(q)
    val text =
        if (i < 0) full
        else {
            val start = (i - radius).coerceAtLeast(0)
            val end = (i + q.length + radius).coerceAtMost(full.length)
            (if (start > 0) "…" else "") + full.substring(start, end) + (if (end < full.length) "…" else "")
        }
    return buildHighlighted(text, query)
}

private fun buildHighlighted(text: String, query: String?): androidx.compose.ui.text.AnnotatedString {
    if (query.isNullOrBlank()) return androidx.compose.ui.text.buildAnnotatedString { append(text) }
    val q = query.lowercase()
    return androidx.compose.ui.text.buildAnnotatedString {
        var i = 0
        val lower = text.lowercase()
        while (i < text.length) {
            val hit = lower.indexOf(q, startIndex = i)
            if (hit < 0) { append(text.substring(i)); break }
            append(text.substring(i, hit))
            pushStyle(androidx.compose.ui.text.SpanStyle(background = Color(0xFFFFEB3B)))
            append(text.substring(hit, hit + q.length))
            pop()
            i = hit + q.length
        }
    }
}


// Строим список строк со вставками заголовков (из СТАРЫХ к НОВЫМ)
private fun buildRows(messages: List<ChatMessage>): List<ChatRow> {
    if (messages.isEmpty()) return emptyList()
    val out = mutableListOf<ChatRow>()
    var lastDate: java.time.LocalDate? = null
    messages.forEachIndexed { idx, m ->
        val d = m.at?.toLocalDate() ?: lastDate // если нет ts — прилипнет к предыдущей дате
        if (d != null && d != lastDate) {
            out += ChatRow.Header(d)
            lastDate = d
        }
        out += ChatRow.Msg(idx)
    }
    return out
}


@Composable
private fun ReactionBar(onPick: (String) -> Unit) {
    val emojis = listOf("❤️","😁","🥰","🤯","😭","👍")
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 4.dp,
        modifier = Modifier
            .padding(horizontal = 8.dp)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            emojis.forEach { e ->
                Text(
                    e,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .clickable { onPick(e) }
                )
            }
        }
    }
}

@Composable
private fun MessageContextMenu(
    headerText: String? = null,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onForward: () -> Unit,
    onPin: () -> Unit,
    pinTitle: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 0.dp,
        shadowElevation = 10.dp,
        border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0x14000000)),
        modifier = Modifier
            .widthIn(min = 170.dp, max = 210.dp) // уже и компактнее
    ) {
        Column {
            // ───────── шапочка «Прочитано в …» (ТОЛЬКО если есть текст) ─────────
            if (headerText != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F5F5), RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.DoneAll, null, tint = Color(0xFF43A047), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(headerText, color = Color(0xFF1B1B1B), style = MaterialTheme.typography.labelMedium)
                }
                HorizontalDivider(color = Color(0x11000000))
            }

            // ───────── дальше — обычные кнопочки меню ─────────
            Column(Modifier.padding(vertical = 4.dp)) {
                MenuItemRow("Ответить", Icons.AutoMirrored.Filled.Reply, onReply)
                MenuItemRow("Копировать", Icons.Filled.ContentCopy, onCopy)
                MenuItemRow("Переслать", Icons.Filled.Send, onForward)
                MenuItemRow(pinTitle, Icons.Filled.PushPin, onPin)
                MenuItemRow("Изменить", Icons.Filled.Edit, onEdit)
                HorizontalDivider(color = Color(0x11000000))
                MenuItemRowDanger("Удалить", Icons.Filled.DeleteForever, onDelete)
            }
        }
    }
}

@Composable
private fun MenuItemRow(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFF424242), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color(0xFF212121), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MenuItemRowDanger(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(title, color = Color(0xFFD32F2F), style = MaterialTheme.typography.bodyMedium)
    }
}


