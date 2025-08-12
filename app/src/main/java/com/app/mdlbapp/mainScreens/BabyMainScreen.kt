@file:OptIn(ExperimentalMaterial3Api::class)

package com.app.mdlbapp.mainScreens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.Screen
import com.app.mdlbapp.core.ui.AppHeightClass
import com.app.mdlbapp.core.ui.AppWidthClass
import com.app.mdlbapp.core.ui.rememberAppHeightClass
import com.app.mdlbapp.core.ui.rememberAppWidthClass
import com.app.mdlbapp.core.ui.rememberIsLandscape
import com.app.mdlbapp.rule.Rule

// ── АДАПТИВНЫЕ ТОКЕНЫ
private data class BabyUiTokens(
    val contentMaxWidth: Dp,
    val hPad: Dp,
    val vPad: Dp,
    val gap: Dp,
    val helloSize: Float,
    val tileHeight: Dp,
    val tileCorner: Dp,
    val tileLabel: Float,
    val topBarHeight: Dp,
    val menuButtonSize: Dp,
    val menuIconSize: Dp
)

@Composable
private fun rememberBabyUiTokens(): BabyUiTokens {
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()
    val phoneLandscape = landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded
    val phonePortrait  = !landscape && w == AppWidthClass.Compact

    val contentMaxWidth = when (w) {
        AppWidthClass.Expanded -> 720.dp
        AppWidthClass.Medium  -> 600.dp
        else -> if (phoneLandscape) 560.dp else 480.dp
    }
    val hPad = if (phoneLandscape) 12.dp else 16.dp
    val vPad = if (phoneLandscape) 12.dp else 24.dp
    val gap  = if (phoneLandscape) 8.dp  else 12.dp

    val helloSize = when {
        phoneLandscape -> 28f
        phonePortrait  -> 24f
        w == AppWidthClass.Expanded -> 40f
        w == AppWidthClass.Medium   -> 36f
        else -> 34f
    }

    val tileHeight = when {
        phoneLandscape -> 64.dp
        w == AppWidthClass.Expanded -> 100.dp
        w == AppWidthClass.Medium   -> 88.dp
        else -> 84.dp
    }
    val tileCorner = if (phoneLandscape) 12.dp else 16.dp
    val tileLabel  = when {
        phoneLandscape -> 18f
        w == AppWidthClass.Expanded -> 22f
        w == AppWidthClass.Medium   -> 20f
        else -> 19f
    }

    // — бургер
    val menuButtonSize = if (phoneLandscape) 48.dp else 56.dp
    val menuIconSize   = if (phoneLandscape) 26.dp else 28.dp

    // — умная высота топбара: не меньше минимума и с учётом размера текста
    val density = LocalDensity.current
    val minBar = if (phoneLandscape) 48.dp else 56.dp
    val titleHeight = with(density) { helloSize.sp.toDp() }       // высота текста
    val topBarHeight = max(minBar, titleHeight + 20.dp)           // + вертикальные отступчики

    return BabyUiTokens(
        contentMaxWidth = contentMaxWidth,
        hPad = hPad,
        vPad = vPad,
        gap = gap,
        helloSize = helloSize,
        tileHeight = tileHeight,
        tileCorner = tileCorner,
        tileLabel = tileLabel,
        topBarHeight = topBarHeight,
        menuButtonSize = menuButtonSize,
        menuIconSize = menuIconSize
    )
}

@Composable
fun BabyScreen(navController: NavHostController) {
    val rules = remember { mutableStateListOf<Rule>() }
    val t = rememberBabyUiTokens()
    var menuOpen by remember { mutableStateOf(false) }


    // 🔄 Твоя синхронизация правил — без изменений
    LaunchedEffect(Unit) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("rules")
            .whereEqualTo("targetUid", babyUid)
            .orderBy("createdAt")
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

    Scaffold(
        containerColor = Color(0xFFF8EDE6),
        topBar = {
            BabyTopBar(t) {
                FirebaseAuth.getInstance().signOut()
                navController.navigate(Screen.RoleSelection.route) {
                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                }
            }
        }
    ) { inner ->
        // фон и центрированная узкая колонка
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8EDE6))
                .padding(inner)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .widthIn(max = t.contentMaxWidth)
                    .padding(horizontal = t.hPad, vertical = t.vPad),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val tiles = listOf(
                    "🧸 Мои привычки",
                    "🔖 Правила Мамочки",
                    "📋 Поощрения",
                    "⚠️ Наказания",
                    "🗨️ Чат с Мамочкой",
                    "🎞 Мои отчёты",
                    "📅 Моя Лента"
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(t.gap),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tiles) { label ->
                        BabyTile(
                            label = label,
                            navController = navController,
                            height = t.tileHeight,
                            corner = t.tileCorner,
                            labelSize = t.tileLabel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BabyTopBar(
    t: BabyUiTokens,
    onExit: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8EDE6))
            .windowInsetsPadding(WindowInsets.statusBars)
            .heightIn(min = 48.dp)
            .padding(horizontal = t.hPad, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // слева — бургер (якорь для меню)
        Box(modifier = Modifier.width(t.menuButtonSize), contentAlignment = Alignment.CenterStart) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(t.menuButtonSize)
            ) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Меню",
                    modifier = Modifier.size(t.menuIconSize),
                    tint = Color.Black
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Выйти и очистить сессию") },
                    onClick = {
                        menuOpen = false
                        onExit()
                    }
                )
            }
        }

        // по центру — заголовок, занимает всё оставшееся
        Text(
            text = "Доброе утро, Малыш!",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontSize = t.helloSize.sp,
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF552216),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )

        // справа — «пустышка» той же ширины, чтобы текст был ИСТИННО по центру
        Spacer(Modifier.width(t.menuButtonSize))
    }
}

@Composable
fun BabyTile(
    label: String,
    navController: NavHostController,
    height: Dp,
    corner: Dp,
    labelSize: Float
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(corner))
            .border(2.dp, Color(0xFF552216), RoundedCornerShape(corner))
            .background(Color(0xFFFFF3EE))
            .clickable {
                when {
                    label.contains("Правила") -> navController.navigate("baby_rules")
                    label.contains("привычки", ignoreCase = true) -> navController.navigate("baby_habits")
                    label.contains("Поощрения") -> navController.navigate("baby_rewards")
                    label.contains("Чат") -> navController.navigate("baby_chat")
                }
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = labelSize.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF552216),
            textAlign = TextAlign.Center
        )
    }
}
