// MommyScreen.kt
package com.yourname.mdlbapp.mainScreens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.yourname.mdlbapp.Screen
import com.yourname.mdlbapp.core.ui.*
import com.yourname.mdlbapp.R
import androidx.compose.foundation.BorderStroke

// ————— Токены адаптивного UI для маминого пульта
private data class MommyUiTokens(
    val bg: Color,
    val tileBg: Color,
    val tileBorder: Color,
    val accent: Color,
    val compactLandscape: Boolean,   // 👈 добавили
    val topBarHeight: Dp,
    val avatarSize: Dp,
    val iconBase: Dp,
    val tileIconSize: Dp,            // 👈 добавили
    val titleSize: Float,
    val gridHPad: Dp,
    val gridVPad: Dp,
    val gridGap: Dp,
    val tileCorner: Dp,
    val tileAspect: Float,           // 👈 теперь меняем по режимам
    val labelSize: Float,
    val bottomBarHPad: Dp,
    val bottomBarVPad: Dp,
    val bottomBarHeight: Dp,
    val minTileSize: Dp
)

@Composable
private fun rememberMommyUiTokens(): MommyUiTokens {
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()

    val isPhoneLandscape = landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded

    val bg = Color(0xFFF8EDE6)
    val tileBg = Color(0xFFF8E7DF)
    val tileBorder = Color(0xFFE0C2BD)
    val accent = Color(0xFF552216)

    val topBarHeight = when {
        isPhoneLandscape -> 56.dp     // было 64–88
        w == AppWidthClass.Expanded -> 110.dp
        else -> 88.dp
    }
    val avatar = when {
        isPhoneLandscape -> 40.dp     // компактнее
        w == AppWidthClass.Expanded -> 64.dp
        w == AppWidthClass.Medium -> 56.dp
        else -> 48.dp
    }
    val iconBase = when {
        isPhoneLandscape -> 28.dp
        w == AppWidthClass.Expanded -> 48.dp
        w == AppWidthClass.Medium -> 44.dp
        else -> 40.dp
    }
    val title = when {
        isPhoneLandscape -> 22f
        w == AppWidthClass.Expanded -> 29f
        w == AppWidthClass.Medium -> 26f
        else -> 24f
    }
    val gridHPad = when {
        isPhoneLandscape -> 12.dp
        w == AppWidthClass.Expanded -> 24.dp
        w == AppWidthClass.Medium -> 16.dp
        else -> 10.dp
    }
    val gridVPad = 10.dp
    val gridGap = when {
        isPhoneLandscape -> 8.dp
        w == AppWidthClass.Expanded -> 16.dp
        else -> 12.dp
    }
    val label = when {
        isPhoneLandscape -> 14f
        w == AppWidthClass.Expanded -> 18f
        w == AppWidthClass.Medium -> 17f
        else -> 16f
    }

    // 🔽 ключ: делаем плитки ниже и уже на телефоне-горизонтали
    val minTile = when {
        isPhoneLandscape -> 140.dp    // было 180–200
        w == AppWidthClass.Expanded -> 260.dp
        w == AppWidthClass.Medium -> 220.dp
        else -> 180.dp
    }
    val tileAspect = when {
        isPhoneLandscape -> 2.1f      // было ~0.98 → стало «плоской» (шире, ниже)
        else -> 460f / 470f
    }
    val tileIconSize = when {
        isPhoneLandscape -> iconBase + 24.dp
        w == AppWidthClass.Expanded -> iconBase + 62.dp
        else -> iconBase + 48.dp
    }

    return MommyUiTokens(
        bg = bg,
        tileBg = tileBg,
        tileBorder = tileBorder,
        accent = accent,
        compactLandscape = isPhoneLandscape,
        topBarHeight = topBarHeight,
        avatarSize = avatar,
        iconBase = iconBase,
        tileIconSize = tileIconSize,
        titleSize = title,
        gridHPad = gridHPad,
        gridVPad = gridVPad,
        gridGap = gridGap,
        tileCorner = 20.dp,
        tileAspect = tileAspect,
        labelSize = label,
        bottomBarHPad = gridHPad,
        bottomBarVPad = if (isPhoneLandscape) 6.dp else 10.dp,
        bottomBarHeight = if (isPhoneLandscape) 44.dp else 56.dp,
        minTileSize = minTile
    )
}

// ————— Модель действий
private data class MommyAction(
    val iconRes: Int,
    val label: String,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MommyScreen(navController: NavHostController) {
    val t = rememberMommyUiTokens()
    val gridState = rememberLazyGridState()
    var overflowOpen by remember { mutableStateOf(false) }

    val items = remember(navController) {
        listOf(
            MommyAction(R.drawable.ic_habit, "Привычки") {
                navController.navigate("habits_screen")
            },

            MommyAction(R.drawable.ic_rules, "Правила\nпослушания") {
                navController.navigate("mommy_rules")
            },

            MommyAction(R.drawable.ic_punishment, "Наказания") {
                // подставь свой маршрут
                //navController.navigate("punishments_screen")
            },

            MommyAction(R.drawable.ic_rewards, "Магазин\nласки") {
                navController.navigate("mommy_rewards")
            },

            MommyAction(R.drawable.ic_chat, "Чат с малышом") {
                //navController.navigate("mommy_chat")
            },
            MommyAction(R.drawable.ic_control, "Контроль\nустройства") {
                //navController.navigate("device_control")
            },
            MommyAction(R.drawable.ic_journal, "Журнал\nПодчинения") {
                //navController.navigate("submission_journal")
            },
            MommyAction(R.drawable.ic_archive, "Архив\nдоказательств") {
                //navController.navigate("evidence_archive")
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(t.bg),
        containerColor = t.bg,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.height(t.topBarHeight),
                title = {
                    Text(
                        text = "Пульт Госпожи",
                        fontSize = t.titleSize.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        fontStyle = FontStyle.Italic,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Меню",
                                tint = Color.Black
                            )
                        }
                        DropdownMenu(
                            expanded = overflowOpen,
                            onDismissRequest = { overflowOpen = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Выйти и очистить сессию") },
                                onClick = {
                                    overflowOpen = false
                                    FirebaseAuth.getInstance().signOut()
                                    navController.navigate(Screen.RoleSelection.route) {
                                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Уведомления
                        IconButton(onClick = { /*navController.navigate("mommy_notifications")*/ }) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Уведомления",
                                modifier = Modifier.size(t.iconBase + 8.dp),
                                tint = Color.Black
                            )
                        }
                        // Аватар
                        IconButton(onClick = { navController.navigate("mommy_profile") }) {
                            Image(
                                painter = painterResource(R.drawable.avatar),
                                contentDescription = "Профиль Мамочки",
                                modifier = Modifier
                                    .size(t.avatarSize)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .border(2.dp, t.tileBorder, RoundedCornerShape(percent = 50)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = t.bg
                )
            )
        },
        bottomBar = {
            // Прикреплённая панель действий
            Surface(
                color = t.bg,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = t.bottomBarHPad, vertical = t.bottomBarVPad),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { navController.navigate("create_rule") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = t.accent),
                        border = BorderStroke(1.dp, t.accent),
                        modifier = Modifier
                            .height(t.bottomBarHeight)
                            .weight(1f)
                    ) {
                        Text("Создать", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    }

                    OutlinedButton(
                        onClick = { navController.navigate("management_modes") },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = t.accent),
                        border = BorderStroke(1.dp, t.accent),
                        modifier = Modifier
                            .height(t.bottomBarHeight)
                            .weight(1.4f)
                    ) {
                        Text("Режимы управления", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                    }
                }
            }
        }
    ) { innerPadding ->
        // Контент: адаптивная сетка
        val gridCells = GridCells.Adaptive(minSize = t.minTileSize)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = t.minTileSize),
            contentPadding = PaddingValues(
                start = t.gridHPad,
                end = t.gridHPad,
                top = t.gridVPad,
                bottom = t.gridVPad + (t.bottomBarHeight + t.bottomBarVPad * 2)
            ),
            horizontalArrangement = Arrangement.spacedBy(t.gridGap),
            verticalArrangement = Arrangement.spacedBy(t.gridGap),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(t.bg)
        ) {
            items(items) { item ->
                MommyTile(
                    iconRes = item.iconRes,
                    label = item.label,
                    tokens = t,
                    onClick = item.onClick
                )
            }
        }
    }
}

@Composable
private fun MommyTile(
    iconRes: Int,
    label: String,
    tokens: MommyUiTokens,
    onClick: () -> Unit
) {
    Surface(
        color = tokens.tileBg,
        shape = RoundedCornerShape(tokens.tileCorner),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = Modifier
            .aspectRatio(tokens.tileAspect) // 👈 стало «плоской» в альбомчике телефона
            .clip(RoundedCornerShape(tokens.tileCorner))
            .border(2.dp, tokens.tileBorder, RoundedCornerShape(tokens.tileCorner))
            .clickable(onClick = onClick)
    ) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = label,
                modifier = Modifier.size(tokens.tileIconSize) // 👈 меньше иконка
            )
            Spacer(Modifier.height(if (tokens.compactLandscape) 2.dp else 6.dp))
            Text(
                text = label,
                color = tokens.accent,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                fontSize = tokens.labelSize.sp,
                textAlign = TextAlign.Center,
                lineHeight = (tokens.labelSize + if (tokens.compactLandscape) 0f else 2f).sp
            )
        }
    }
}