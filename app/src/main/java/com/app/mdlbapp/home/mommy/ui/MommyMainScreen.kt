// MommyScreen.kt
package com.app.mdlbapp.home.mommy.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.SubcomposeAsyncImage
import com.app.mdlbapp.R
import com.app.mdlbapp.Screen
import com.app.mdlbapp.core.ui.AppHeightClass
import com.app.mdlbapp.core.ui.AppWidthClass
import com.app.mdlbapp.core.ui.rememberAppHeightClass
import com.app.mdlbapp.core.ui.rememberAppWidthClass
import com.app.mdlbapp.core.ui.rememberIsLandscape
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.*
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.SubcomposeAsyncImage
import com.google.firebase.firestore.ktx.firestore
import kotlinx.coroutines.launch
import android.graphics.BitmapFactory
import android.util.Base64

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

    val drawerState = rememberDrawerState(
        initialValue = DrawerValue.Closed
    )
    val scope = rememberCoroutineScope()

    var mommyName by remember { mutableStateOf<String?>("Мамочка") }
    var mommyPhoto by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(Unit) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val name = (doc.getString("displayName") ?: doc.getString("name") ?: "Мамочка").trim()
                mommyName = name
                val dataUrl = doc.getString("photoDataUrl")?.trim()
                val url     = doc.getString("photoUrl")?.trim()
                mommyPhoto = when {
                    !dataUrl.isNullOrBlank() -> dataUrl
                    !url.isNullOrBlank()     -> url
                    else                     -> null
                }
            }
    }

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
                navController.navigate("mommy_chat")
            },
            MommyAction(R.drawable.ic_control, "Контроль\nустройства") {
                //navController.navigate("device_control")
            },
            MommyAction(R.drawable.ic_journal, "Журнал\nПодчинения") {
                navController.navigate("journal")
            },
            MommyAction(R.drawable.ic_archive, "Архив\nдоказательств") {

            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimColor = Color(0x99000000),
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp),
                drawerContainerColor = Color(0xFFCCB2AB),
                drawerContentColor = Color(0xFF2A2A2A),
                modifier = Modifier.widthIn(min = 260.dp, max = 320.dp)
            ) {
                // ——— шапка с большой аватаркой и именем
                MommyDrawerHeader(displayName = mommyName, photoUrl = mommyPhoto)

                Divider(color = Color(0xFF2A2A2A).copy(alpha = 0.15f))

                val itemColors = NavigationDrawerItemDefaults.colors(
                    unselectedContainerColor = Color.Transparent,
                    unselectedIconColor = Color(0xFF2A2A2A),
                    unselectedTextColor = Color(0xFF2A2A2A),
                    selectedContainerColor = Color(0xFF2A2A2A).copy(alpha = 0.08f),
                    selectedIconColor = Color(0xFF2A2A2A),
                    selectedTextColor = Color(0xFF2A2A2A)
                )

                NavigationDrawerItem(
                    label = { Text("Аккаунт") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        // navController.navigate("mommy_account") // при необходимости
                    },
                    colors = itemColors,
                    icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Часовой пояс малыша") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings_baby_timezone")
                    },
                    colors = itemColors,
                    icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                NavigationDrawerItem(
                    label = { Text("Выйти и очистить сессию") },
                    selected = false,
                    onClick = {
                        scope.launch { drawerState.close() }
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate(Screen.RoleSelection.route) {
                            popUpTo(Screen.RoleSelection.route) { inclusive = true }
                        }
                    },
                    colors = itemColors,
                    icon = { Icon(Icons.Default.Menu, contentDescription = null) },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    ) {
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
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Меню",
                                    tint = Color.Black
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
                                val name = (mommyName ?: "Мамочка").trim()
                                val init = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                val pUrl = mommyPhoto
                                Box(
                                    modifier = Modifier
                                        .size(t.avatarSize)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .border(2.dp, t.tileBorder, RoundedCornerShape(percent = 50)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when {
                                        !pUrl.isNullOrBlank() && pUrl.startsWith("data:", true) -> {
                                            decodeDataUrlBitmap(pUrl)?.let { bmp ->
                                                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize().clip(CircleShape))
                                            } ?: Text(init, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2A2A))
                                        }
                                        !pUrl.isNullOrBlank() -> {
                                            SubcomposeAsyncImage(
                                                model = pUrl, contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                loading = { Text(init, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2A2A)) },
                                                error   = { Text(init, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2A2A)) }
                                            )
                                        }
                                        else -> {
                                            Text(init, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2A2A))
                                        }
                                    }
                                }
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
                            Text(
                                "Создать",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            )
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
                            Text(
                                "Режимы управления",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            )
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

@Composable
private fun MommyDrawerHeader(displayName: String?, photoUrl: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            val name = (displayName ?: "Мамочка").trim()
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val pUrl = photoUrl
            if (!pUrl.isNullOrBlank()) {
                if (pUrl.startsWith("data:", ignoreCase = true)) {
                    val bmp = remember(pUrl) { decodeDataUrlBitmap(pUrl) }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape)
                        )
                    } else {
                        InitialBadge(initial)
                    }
                } else {
                    SubcomposeAsyncImage(
                        model = pUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        loading = { InitialBadge(initial) },
                        error   = { InitialBadge(initial) }
                    )
                }
            } else {
                InitialBadge(initial)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = displayName ?: "Мамочка",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF2A2A2A)
        )
    }
}

@Composable
private fun InitialBadge(initial: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(initial, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2A2A))
    }
}

private fun decodeDataUrlBitmap(dataUrl: String): Bitmap? {
    return try {
        val comma = dataUrl.indexOf(',')
        if (comma <= 0) null
        else {
            val base64 = dataUrl.substring(comma + 1)
            val bytes = Base64.decode(base64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    } catch (_: Exception) { null }
}