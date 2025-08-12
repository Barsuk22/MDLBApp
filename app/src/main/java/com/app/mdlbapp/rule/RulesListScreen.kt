//Файл RulesListScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)

package com.app.mdlbapp.rule


import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.core.Constants
import com.app.mdlbapp.core.ui.AppHeightClass
import com.app.mdlbapp.core.ui.AppWidthClass
import com.app.mdlbapp.core.ui.rememberAppHeightClass
import com.app.mdlbapp.core.ui.rememberAppWidthClass
import com.app.mdlbapp.core.ui.rememberIsLandscape
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.app.mdlbapp.R
import com.google.firebase.firestore.FieldPath

// ——— Токены для экрана списка правил (адаптивные размерчики)
private data class RulesListUiTokens(
    val contentMaxWidth: Dp,
    val hPad: Dp,
    val vPad: Dp,
    val gap: Dp,
    val topBarPadV: Dp,
    val btnSize: Dp,
    val btnIcon: Dp,
    val titleSize: Float,
    val filterLabelSize: Float,
    val cardCorner: Dp,
    val listGap: Dp
)

@Composable
private fun rememberRulesListUiTokens(): RulesListUiTokens {
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()
    val phonePortrait  = !landscape && w == AppWidthClass.Compact
    val phoneLandscape =  landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded
    val tablet         = w == AppWidthClass.Medium || w == AppWidthClass.Expanded

    val contentMaxWidth = when (w) {
        AppWidthClass.Expanded -> 840.dp
        AppWidthClass.Medium  -> 720.dp
        else -> if (phoneLandscape) 560.dp else 520.dp
    }
    val hPad = if (phoneLandscape) 12.dp else 16.dp
    val vPad = if (phoneLandscape) 10.dp else 16.dp
    val gap  = if (phoneLandscape) 8.dp else 12.dp
    val topV = if (phonePortrait) 8.dp else 6.dp

    val btnSize  = if (phoneLandscape) 44.dp else 48.dp
    val btnIcon  = if (tablet) 26.dp else if (phoneLandscape) 22.dp else 24.dp
    val titleSp  = when {
        tablet -> 26f
        phoneLandscape -> 22f
        else -> 24f
    }
    val filterSp = if (phoneLandscape) 14f else 16f
    val corner   = 12.dp
    val listGap  = if (phoneLandscape) 10.dp else 12.dp

    return RulesListUiTokens(
        contentMaxWidth = contentMaxWidth,
        hPad = hPad,
        vPad = vPad,
        gap = gap,
        topBarPadV = topV,
        btnSize = btnSize,
        btnIcon = btnIcon,
        titleSize = titleSp,
        filterLabelSize = filterSp,
        cardCorner = corner,
        listGap = listGap
    )
}

@Composable
fun RulesListScreen(navController: NavController) {
    val t = rememberRulesListUiTokens()
    val rules = remember { mutableStateListOf<Rule>() }

    val selectedCategoryFilter = remember { mutableStateOf("Все") }
    val categories = listOf("Все", "Поведение", "Речь", "Контакт", "Физика", "Здоровье", "Дисциплина", "Прочее")


    // 1) Мамин uid
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

    // 2) Узнаём uid Малыша этой Мамочки
    var babyUid by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(mommyUid) {
        Firebase.firestore.collection("users").document(mommyUid).get()
            .addOnSuccessListener { doc -> babyUid = doc.getString("pairedWith") }
    }

    // 3) Подписываемся только когда babyUid получен
    DisposableEffect(mommyUid, babyUid) {
        if (babyUid == null) return@DisposableEffect onDispose {}

        val reg = Firebase.firestore.collection("rules")
            .whereEqualTo("createdBy", mommyUid)
            .whereEqualTo("targetUid", babyUid)          // ← именно МАЛЫШ
            .orderBy("createdAt")
            .orderBy(FieldPath.documentId())
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
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .widthIn(max = t.contentMaxWidth)
                .padding(horizontal = t.hPad, vertical = t.vPad)
        ) {
            // ——— ЕДИНАЯ ШАПКА: Меню | Заголовок по центру | Назад
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = t.topBarPadV),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // слева — бургер
                IconButton(
                    onClick = { /* TODO: меню */ },
                    modifier = Modifier.size(t.btnSize)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menu",
                        modifier = Modifier.size(t.btnIcon), tint = Color.Black)
                }

                // центр — заголовок, всегда одной строкой
                Text(
                    text = "Ваши правила",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = t.titleSize.sp,
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )

                // справа — назад (симметрия, чтобы заголовок был ИСТИННО по центру)
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(t.btnSize)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад",
                        modifier = Modifier.size(t.btnIcon), tint = Color.Black)
                }
            }

            Spacer(Modifier.height(t.gap))


// 🔶 Фильтр в фирменном стиле (капсула + своё меню)
            val accent = Color(0xFF552216)
            val fieldBg = Color(0xFFFFF3EE)
            val fieldBorder = Color(0xFFE0C2BD)

            var expanded by remember { mutableStateOf(false) }
            var anchorWidthPx by remember { mutableStateOf(0) }
            val density = LocalDensity.current

            Column(Modifier.fillMaxWidth()) {
                // Лейбл над полем, чтоб не было «чипа»-ярлыка
                Text(
                    text = "Фильтр по категории",
                    fontSize = t.filterLabelSize.sp,
                    fontStyle = FontStyle.Italic,
                    color = accent,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { anchorWidthPx = it.size.width }
                ) {
                    // Капсула-поле
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = fieldBg,
                        border = BorderStroke(1.dp, fieldBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)            // фиксируем высоту (можно 48–56.dp)
                            .clickable { expanded = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()       // ширину, не высоту
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = selectedCategoryFilter.value,
                                fontSize = t.filterLabelSize.sp,
                                fontStyle = FontStyle.Italic,
                                color = accent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.rotate(if (expanded) 180f else 0f)
                            )
                        }
                    }

// Меню той же ширины и с ограничением по высоте
                    val menuShape = RoundedCornerShape(12.dp)

                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier
                            .width(with(density) { anchorWidthPx.toDp() })
                            .heightIn(max = 320.dp)                 // не выше экрана
                            .shadow(8.dp, menuShape, clip = true)   // мягкая тень
                            .background(fieldBg, menuShape)         // кремовый фон
                            .border(1.dp, fieldBorder, menuShape)   // тонкий бордер
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            categories.forEach { category ->
                                val selected = category == selectedCategoryFilter.value

                                DropdownMenuItem(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent),
                                    text = {
                                        Text(
                                            text = category,
                                            color = accent,
                                            fontStyle = FontStyle.Italic,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = {
                                        if (selected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = accent
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCategoryFilter.value = category
                                        expanded = false
                                    },
                                    colors = MenuDefaults.itemColors(
                                        textColor = accent,
                                        leadingIconColor = accent,
                                        trailingIconColor = accent
                                    )
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(t.gap))

            val filteredRules = remember(rules, selectedCategoryFilter.value) {
                if (selectedCategoryFilter.value == "Все") rules
                else rules.filter { it.category == selectedCategoryFilter.value }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(t.listGap),
                contentPadding = PaddingValues(bottom = t.vPad)
            ) {
                itemsIndexed(filteredRules) { index, rule ->
                    RuleCard(
                        number = index + 1,
                        rule = rule,
                        onDelete = {
                            rules.remove(rule)
                            Firebase.firestore.collection("rules").document(rule.id!!).delete()
                        },
                        onEdit = { navController.navigate("edit_rule/${rule.id}") }
                    )
                }
            }

            Spacer(Modifier.height(t.gap))

            // Кнопка "Новое правило" — бордер обычный, без deprecated
            OutlinedButton(
                onClick = { navController.navigate("create_rule") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF552216)),
                border = BorderStroke(1.dp, Color(0xFF552216))
            ) {
                Text("＋ Новое правило", fontSize = 20.sp, fontStyle = FontStyle.Italic)
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Правила для любимого нижнего :)",
                modifier = Modifier.align(Alignment.CenterHorizontally),
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF552216)
            )
        }
    }
}

// ——— Карточка правила: те же краски, только адаптивные размеры
@Composable
fun RuleCard(
    number: Int,
    rule: Rule,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    // локальные токены карточки
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()
    val phoneLandscape = landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded
    val tablet = w == AppWidthClass.Medium || w == AppWidthClass.Expanded

    val isDisabled = rule.status == RuleStatus.DISABLED
    val backgroundColor = if (isDisabled) Color(0xFFFFF0F0) else Color(0xFFF8EDE6)
    val borderColor = if (isDisabled) Color(0xFFCC8888) else Color(0xFFDEBEB5)
    val titleColor = if (isDisabled) Color(0xFF993333) else Color(0xFF552216)

    val iconSize   = when { tablet -> 48.dp; phoneLandscape -> 34.dp; else -> 45.dp }
    val titleSp    = when { tablet -> 24.sp; phoneLandscape -> 18.sp; else -> 22.sp }
    val bodySp     = when { tablet -> 18.sp; phoneLandscape -> 15.sp; else -> 17.sp }
    val badgeSp    = when { tablet -> 15.sp; else -> 14.sp }
    val closeSize  = when { tablet -> 26.dp; phoneLandscape -> 22.dp; else -> 25.dp }
    val corner     = 12.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(corner))
            .background(backgroundColor, RoundedCornerShape(corner))
            .padding(12.dp)
    ) {
        Column {
            // Заголовок с иконкой и крестиком
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_rule_name),
                        contentDescription = "Иконка правила",
                        modifier = Modifier
                            .size(iconSize)
                            .padding(end = 6.dp)
                    )
                    Column {
                        Text(
                            text = "Правило $number: ${rule.title}",
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = titleSp,
                            color = titleColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isDisabled) {
                            Text(
                                text = "⛔ Временно отключено",
                                color = Color(0xFF993333),
                                fontStyle = FontStyle.Italic,
                                fontSize = badgeSp
                            )
                        }
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Удалить правило",
                        tint = Color(0xFF552216),
                        modifier = Modifier.size(closeSize)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = rule.description,
                fontSize = bodySp,
                color = Color(0xFF4A3C36),
                fontStyle = FontStyle.Italic
            )

            if (!rule.category.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Категория: ${rule.category}",
                    fontSize = 14.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF886C66)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) {
                    Text(
                        text = "Изменить",
                        color = Color(0xFF552216),
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

fun computeNextDueDateForHabit(
    repeatMode: String,
    daysOfWeek: List<String>?,
    oneTimeDate: String?,
    deadline: String?,
    fromDate: LocalDate,
    nowTime: LocalTime
): LocalDate? {
    // Для ежедневных и weekly мы уже умеем считать строки дат,
    // так что делаем обратное: парсим строку → LocalDate
    return when (repeatMode) {
        "daily" -> {
            // просто берём строку и парсим её
            getNextDueDate("daily", null, null, deadline, fromDate, nowTime)
                ?.let { LocalDate.parse(it) }
        }
        "weekly" -> {
            getNextDueDate("weekly", daysOfWeek, null, deadline, fromDate, nowTime)
                ?.let { LocalDate.parse(it) }
        }
        "once" -> {
            // если oneTimeDate есть и валидна, возвращаем её как LocalDate
            oneTimeDate
                ?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
                ?.let { LocalDate.parse(it) }
        }
        else -> null
    }
}

fun getNextDueDate(
    repeatMode: String,
    daysOfWeek: List<String>? = null,
    oneTimeDate: String? = null,
    deadline: String? = null,
    today: LocalDate,
    nowTime: LocalTime
): String? {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")

    // Парсим дедлайн
    val deadlineTime = deadline
        ?.takeIf { it.matches(Regex("\\d{2}:\\d{2}")) }
        ?.let { LocalTime.parse(it, formatter) }

    return when (repeatMode) {
        "daily" -> {
            // если сегодня уже поздно — завтра, иначе сегодня
            if (deadlineTime != null && nowTime.isAfter(deadlineTime))
                today.plusDays(1).toString()
            else
                today.toString()
        }

        "weekly" -> {
            if (daysOfWeek.isNullOrEmpty()) return null
            val wanted = daysOfWeek.mapNotNull { Constants.ruToDayOfWeek[it] }
            if (wanted.isEmpty()) return null

            val startOffset = if (deadlineTime != null && nowTime.isAfter(deadlineTime)) 1L else 0L
            val best = (startOffset..6L).asSequence()
                .map { offset -> today.plusDays(offset) }
                .first { date -> date.dayOfWeek in wanted }

            best.toString()
        }

        "once" -> {
            // Если oneTimeDate не в формате YYYY-MM-DD, возвращаем null
            oneTimeDate
                ?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        }
        else   -> null
    }
}

fun migrateRuleStatuses(mommyUid: String) {
    val db = Firebase.firestore
    db.collection("rules").whereEqualTo("createdBy", mommyUid).get()
        .addOnSuccessListener { snaps ->
            val batch = db.batch()
            snaps.documents.forEach { d ->
                when (d.getString("status")) {
                    "Активно" -> batch.update(d.reference, "status", RuleStatus.ACTIVE)
                    "Временно отключено" -> batch.update(d.reference, "status", RuleStatus.DISABLED)
                }
            }
            batch.commit()
        }
}

// "Продакшн"-оболочка, для обычного вызова без параметров:
fun getNextDueDate(
    repeatMode: String,
    daysOfWeek: List<String>? = null,
    oneTimeDate: String? = null,
    deadline: String? = null
): String? = getNextDueDate(
    repeatMode,
    daysOfWeek,
    oneTimeDate,
    deadline,
    LocalDate.now(),
    LocalTime.now()
)