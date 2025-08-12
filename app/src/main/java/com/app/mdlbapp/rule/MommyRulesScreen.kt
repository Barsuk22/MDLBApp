package com.app.mdlbapp.rule

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.core.ui.AppHeightClass
import com.app.mdlbapp.core.ui.AppWidthClass
import com.app.mdlbapp.core.ui.rememberAppHeightClass
import com.app.mdlbapp.core.ui.rememberAppWidthClass
import com.app.mdlbapp.core.ui.rememberIsLandscape

@Composable
fun rememberRulesUiTokens(): RulesUiTokens {
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()

    val phonePortrait  = !landscape && w == AppWidthClass.Compact
    val phoneLandscape =  landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded
    val tablet         = w == AppWidthClass.Medium || w == AppWidthClass.Expanded

    val contentMaxWidth = when (w) {
        AppWidthClass.Expanded -> 800.dp
        AppWidthClass.Medium  -> 700.dp
        else -> if (phoneLandscape) 560.dp else 520.dp
    }
    val hPad = when {
        phoneLandscape -> 12.dp
        phonePortrait  -> 16.dp
        else -> 20.dp
    }
    val vPad = when {
        phoneLandscape -> 12.dp
        phonePortrait  -> 20.dp
        else -> 24.dp
    }
    val gap = when {
        phoneLandscape -> 8.dp
        phonePortrait  -> 10.dp
        else -> 14.dp
    }

    val titleSize = when {
        phoneLandscape -> 20f
        phonePortrait  -> 22f
        tablet         -> 26f
        else -> 22f
    }
    val sectionSize = when {
        phoneLandscape -> 18f
        phonePortrait  -> 20f
        tablet         -> 22f
        else -> 20f
    }
    val buttonHeight = if (phoneLandscape) 44.dp else 48.dp
    val footerSize = when {
        phoneLandscape -> 16f
        phonePortrait  -> 18f
        tablet         -> 20f
        else -> 18f
    }

    return RulesUiTokens(
        contentMaxWidth, hPad, vPad, gap,
        titleSize, sectionSize, buttonHeight, footerSize
    )
}


data class RulesUiTokens(
    val contentMaxWidth: Dp,
    val hPad: Dp,
    val vPad: Dp,
    val gap: Dp,
    val titleSize: Float,
    val sectionSize: Float,
    val buttonHeight: Dp,
    val footerSize: Float
)

@Composable
fun RulesScreen(navController: NavController, mommyUid: String, babyUid: String) {
    val t = rememberRulesUiTokens()

    val rules = remember { mutableStateListOf<Rule>() }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    // 🔄 Автозагрузка правил
    LaunchedEffect(Unit) {
        Firebase.firestore.collection("rules")
            .whereEqualTo("createdBy", mommyUid)
            .whereEqualTo("targetUid", babyUid)
            .orderBy("createdAt") // ← читаем по createdAt
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                rules.clear()
                snapshots?.documents?.forEach { doc ->
                    val rule = doc.toObject(Rule::class.java)
                    rule?.id = doc.id
                    if (rule != null) rules.add(rule)
                }
            }
    }

    // Общий фон
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
    ) {
        // Центрированная «узкая» колонка + прокрутка (чтобы на телефоне ничего не резалось)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .widthIn(max = t.contentMaxWidth)
                .padding(horizontal = t.hPad, vertical = t.vPad)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Заголовок
            Text(
                text = "📘 Ваши правила",
                fontSize = t.titleSize.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF552216),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(t.gap))

            // Список правил (как у тебя)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(t.gap)
            ) {
                rules.forEachIndexed { index, rule ->
                    RuleCard(
                        number = index + 1,
                        rule = rule,
                        onDelete = {
                            rule.id?.let { Firebase.firestore.collection("rules").document(it).delete() }
                        },
                        onEdit = {
                            navController.navigate("edit_rule/${rule.id}")
                        }
                    )
                }
            }

            Spacer(Modifier.height(t.gap * 2))

            // Заголовок формы
            Text(
                text = "➕ Новое правило",
                fontSize = t.sectionSize.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF552216),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(t.gap))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название правила") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(t.gap / 2))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5
            )

            Spacer(Modifier.height(t.gap))

            // Кнопка сохранения
            Button(
                onClick = {
                    if (title.isNotBlank() && description.isNotBlank()) {
                        val rule = Rule(
                            title = title,
                            description = description,
                            createdBy = mommyUid,
                            targetUid = babyUid,
                            createdAt = System.currentTimeMillis() // ← сохраняем то, по чему сортируем
                        )
                        Firebase.firestore.collection("rules").add(rule)
                        title = ""; description = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF552216)),
                modifier = Modifier
                    .align(Alignment.End) // ← аккуратно прижимаем вправо
                    .height(t.buttonHeight)
            ) {
                Text("Сохранить", color = Color.White)
            }

            Spacer(Modifier.height(t.gap * 1.5f))

            Text(
                text = "Правила для любимого нижнего :)",
                fontStyle = FontStyle.Italic,
                fontSize = t.footerSize.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(t.gap))
        }
    }
}