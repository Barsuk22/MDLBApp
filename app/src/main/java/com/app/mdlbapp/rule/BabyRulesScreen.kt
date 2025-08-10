package com.app.mdlbapp.rule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.rule.ui.rememberBabyRulesUiTokens
import com.app.mdlbapp.R

// ——— Адаптивные токены для экрана правил



@Composable
fun BabyRulesScreen(navController: NavHostController) {
    val t = rememberBabyRulesUiTokens()
    val rules = remember { mutableStateListOf<Rule>() }
    var mommyUid by remember { mutableStateOf<String?>(null) }

    // 1) Подгружаем UID Мамочки
    LaunchedEffect(Unit) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("users").document(babyUid).get()
            .addOnSuccessListener { doc -> mommyUid = doc.getString("pairedWith") }
    }

    // 2) Подписка на правила Мамочки
    DisposableEffect(mommyUid) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid
        if (babyUid == null || mommyUid == null) return@DisposableEffect onDispose {}
        val reg = Firebase.firestore.collection("rules")
            .whereEqualTo("targetUid", babyUid)
            .whereEqualTo("createdBy", mommyUid)
            .orderBy("createdAt")
            .addSnapshotListener { snaps, e ->
                if (e != null) return@addSnapshotListener
                rules.clear()
                snaps?.documents?.forEach { d ->
                    d.toObject(Rule::class.java)?.also { it.id = d.id; rules.add(it) }
                }
            }
        onDispose { reg.remove() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .widthIn(max = t.contentMaxWidth)
                .padding(horizontal = t.hPad, vertical = t.vPad)
        ) {
            // 🔝 Единая шапка: стрелка + центрированный заголовок
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // слева — только стрелочка (крупная зона тапа)
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(48.dp) // контейнер тапа
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_back),
                        contentDescription = "Назад",
                        tint = Color(0xFF552216),
                        modifier = Modifier.size(t.backIcon) // сам размер стрелки из токенов
                    )
                }

                // по центру — заголовок, всегда одной строкой
                Text(
                    text = "📜 Правила Мамочки",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontSize = t.titleSize.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF552216),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // справа — пустышка той же ширины, чтобы заголовок был ИСТИННО по центру
                Spacer(Modifier.size(48.dp))
            }

            Spacer(Modifier.height(t.gap))

            if (rules.isEmpty()) {
                Text(
                    text = "Пока нет правил...\nЖди указаний 🕊",
                    fontStyle = FontStyle.Italic,
                    fontSize = t.emptySize.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(t.listGap),
                    contentPadding = PaddingValues(bottom = t.bottomPad),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(rules) { index, rule ->
                        RuleCard(
                            number = index + 1,
                            rule = rule,
                            onDelete = {},   // Малыш не удаляет
                            onEdit = {}      // и не редактирует
                        )
                    }
                }
            }
        }
    }
}