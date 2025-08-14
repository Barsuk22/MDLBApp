package com.app.mdlbapp.journal

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.mdlbapp.journal.data.HabitJournalItem
import com.app.mdlbapp.journal.data.HabitLogsRepository
import com.app.mdlbapp.journal.data.RewardJournalItem
import com.app.mdlbapp.journal.data.RewardLogsRepository
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.tasks.await
import kotlin.collections.emptyList

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalScreen(onBack: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val tabs = listOf("Привычки", "Награды")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Журнал") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padd ->
        Column(Modifier.fillMaxSize().padding(padd)) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> HabitJournalTabPairFeed()
                1 -> RewardJournalTabPairFeed()
            }
        }
    }
}

@Composable
private fun RewardJournalTabPairFeed() {
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    var mommyUid by remember { mutableStateOf<String?>(null) }
    var babyUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        val users = com.google.firebase.ktx.Firebase.firestore.collection("users")
        val doc = users.document(uid).get().await()
        val role = doc.getString("role")
        val paired = doc.getString("pairedWith")
        if (!paired.isNullOrEmpty()) {
            if (role == "Mommy") { mommyUid = uid; babyUid = paired }
            else { mommyUid = paired; babyUid = uid }
        }
    }

    val flowReady = mommyUid != null && babyUid != null
    val logs by remember(mommyUid, babyUid, flowReady) {
        if (flowReady) RewardLogsRepository
            .allRewardLogsFlow(mommyUid!!, babyUid!!)
        else kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs) { item ->
            RewardJournalRow(item)
        }
    }
}

@Composable
private fun RewardJournalRow(item: RewardJournalItem) {
    val color = when (item.status) {
        "bought", "approved" -> MaterialTheme.colorScheme.primary
        "rejected"           -> MaterialTheme.colorScheme.error
        "pending"            -> MaterialTheme.colorScheme.onSurface
        else                 -> MaterialTheme.colorScheme.onSurface
    }
    val dateStr = item.at?.toDate()?.let {
        java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(it)
    } ?: ""

    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.rewardTitle, fontWeight = FontWeight.SemiBold)
                Text(
                    when (item.status) {
                        "bought"   -> "Обмененно! (${item.source ?: "?"})"
                        "pending"  -> "Заявка на обмен баллов"
                        "approved" -> "Подтверждено Мамочкой"
                        "rejected" -> "Отказ и возврат"
                        else       -> item.status
                    },
                    color = color
                )
            }
            // 👉 слева дата, справа баллы — как ты просил
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dateStr, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = if (item.pointsDelta >= 0) "+${item.pointsDelta}" else "${item.pointsDelta}",
                    color = color,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
private fun HabitJournalTabPlaceholder() {
    SimplePlaceholder("Здесь скоро появится история привычек (общая лента)")
}

@Composable
private fun SimplePlaceholder(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun HabitJournalTabPairFeed() {
    // узнаём пару (кто и с кем)
    val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
    var mommyUid by remember { mutableStateOf<String?>(null) }
    var babyUid by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (uid == null) return@LaunchedEffect
        val users = com.google.firebase.ktx.Firebase.firestore.collection("users")
        val doc = users.document(uid).get().await()
        val role = doc.getString("role")
        val paired = doc.getString("pairedWith")
        if (!paired.isNullOrEmpty()) {
            if (role == "Mommy") { mommyUid = uid; babyUid = paired }
            else { mommyUid = paired; babyUid = uid }
        }
    }

    val flowReady = mommyUid != null && babyUid != null
    val logs by remember(mommyUid, babyUid, flowReady) {
        if (flowReady) HabitLogsRepository.allHabitLogsFlow(mommyUid!!, babyUid!!) else
            kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    androidx.compose.foundation.lazy.LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(logs) { item ->
            HabitJournalRow(item)
        }
    }
}

@Composable
private fun HabitJournalRow(item: HabitJournalItem) {
    val color = when (item.status) {
        "done" -> MaterialTheme.colorScheme.primary
        "missed" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }

    // форматируем id документа "yyyy-MM-dd" -> "dd.MM.yyyy"
    val prettyDate = remember(item.docId) {
        runCatching {
            java.time.LocalDate.parse(item.docId)
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        }.getOrElse { item.docId } // если вдруг не распарсилось
    }

    Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.habitTitle, fontWeight = FontWeight.SemiBold)
                Text(
                    when (item.status) {
                        "done" -> "Выполнено (${item.source ?: "?"})"
                        "missed" -> "Пропуск (${item.source ?: "?"})"
                        else -> item.status
                    },
                    color = color
                )
            }

            // 👉 справа: дата и баллы
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    prettyDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (item.pointsDelta >= 0) "+${item.pointsDelta}" else "${item.pointsDelta}",
                    color = color,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}