package com.app.mdlbapp.rule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.ktx.Firebase
import com.google.firebase.firestore.ktx.firestore
import com.app.mdlbapp.core.ui.AppHeightClass
import com.app.mdlbapp.core.ui.AppWidthClass
import com.app.mdlbapp.core.ui.rememberAppHeightClass
import com.app.mdlbapp.core.ui.rememberAppWidthClass
import com.app.mdlbapp.core.ui.rememberIsLandscape
import com.app.mdlbapp.R
import com.app.mdlbapp.core.ui.theme.Tokens
import uiToDbStatus

// ——— Адаптивные токены для экрана создания правила
private data class CreateRuleUiTokens(
    val contentMaxWidth: Dp,
    val hPad: Dp,
    val vPad: Dp,
    val gap: Dp,
    val titleSize: Float,
    val subtitleSize: Float,
    val labelSize: Float,
    val backTap: Dp,
    val backIcon: Dp,
    val fieldMinHeight: Dp,
    val buttonHeight: Dp
)

@Composable
private fun rememberCreateRuleUiTokens(): CreateRuleUiTokens {
    val w = rememberAppWidthClass()
    val h = rememberAppHeightClass()
    val landscape = rememberIsLandscape()

    val phonePortrait  = !landscape && w == AppWidthClass.Compact
    val phoneLandscape =  landscape && h == AppHeightClass.Compact && w != AppWidthClass.Expanded
    val tablet         = w == AppWidthClass.Medium || w == AppWidthClass.Expanded

    val contentMaxWidth = when (w) {
        AppWidthClass.Expanded -> 840.dp
        AppWidthClass.Medium  -> 720.dp
        else -> if (phoneLandscape) 580.dp else 520.dp
    }
    val hPad = when {
        phoneLandscape -> 16.dp
        phonePortrait  -> 20.dp
        else -> 24.dp
    }
    val vPad = when {
        phoneLandscape -> 12.dp
        phonePortrait  -> 18.dp
        else -> 24.dp
    }
    val gap = when {
        phoneLandscape -> 10.dp
        phonePortrait  -> 12.dp
        else -> 16.dp
    }

    val titleSize = when {
        tablet -> 26f
        phoneLandscape -> 22f
        phonePortrait  -> 24f
        else -> 24f
    }
    val subtitleSize = when {
        tablet -> 18f
        phoneLandscape -> 15f
        phonePortrait  -> 16f
        else -> 16f
    }
    val labelSize = when {
        tablet -> 18f
        phoneLandscape -> 16f
        else -> 17f
    }

    val backTap   = if (phoneLandscape) 44.dp else 48.dp
    val backIcon  = if (tablet) 28.dp else if (phoneLandscape) 22.dp else 24.dp
    val fieldMin  = if (phoneLandscape) 42.dp else 48.dp
    val buttonH   = if (phoneLandscape) 48.dp else 52.dp

    return CreateRuleUiTokens(
        contentMaxWidth = contentMaxWidth,
        hPad = hPad,
        vPad = vPad,
        gap = gap,
        titleSize = titleSize,
        subtitleSize = subtitleSize,
        labelSize = labelSize,
        backTap = backTap,
        backIcon = backIcon,
        fieldMinHeight = fieldMin,
        buttonHeight = buttonH
    )
}

@Composable
private fun AppDropdownField(
    value: String,
    onSelect: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp
) {
    val accent = Color(0xFF552216)
    val fieldBg = Color(0xFFFFF3EE)
    val fieldBorder = Color(0xFFE0C2BD)
    val shape = RoundedCornerShape(12.dp)

    var expanded by remember { mutableStateOf(false) }
    var anchorWidthPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .onGloballyPositioned { anchorWidthPx = it.size.width }
    ) {
        Surface(
            shape = shape,
            color = fieldBg,
            border = BorderStroke(1.dp, fieldBorder),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = value,
                    color = accent,
                    fontStyle = FontStyle.Italic,
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

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(with(density) { anchorWidthPx.toDp() })
                .heightIn(max = 320.dp)
                .shadow(8.dp, shape, clip = true)
                .background(fieldBg, shape)
                .border(1.dp, fieldBorder, shape)
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                options.forEach { opt ->
                    val selected = opt == value
                    DropdownMenuItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) accent.copy(alpha = 0.10f) else Color.Transparent),
                        text = {
                            Text(
                                text = opt,
                                color = accent,
                                fontStyle = FontStyle.Italic,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        onClick = {
                            onSelect(opt)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

private val categoryOptions = listOf(
    "Поведение",
    "Речь",
    "Контакт",
    "Физика",
    "Здоровье",
    "Дисциплина",
    "Прочее"
)

@Composable
fun CreateRuleScreen(
    navController: NavController,
    mommyUid: String,
    babyUid: String
) {
    val t = rememberCreateRuleUiTokens()

    val ruleName = remember { mutableStateOf("") }
    val ruleDetails = remember { mutableStateOf("") }
    val ruleReminder = remember { mutableStateOf("") }
    val category = remember { mutableStateOf("Дисциплина") }
    var isActive by remember { mutableStateOf(true) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDE9DD))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .widthIn(max = t.contentMaxWidth)
                .padding(horizontal = t.hPad)
                .padding(top = t.vPad + 12.dp, bottom = maxOf(t.vPad, 12.dp))
                .verticalScroll(rememberScrollState())
                .imePadding(), // чтобы кнопка не пряталась за клавиатурой
            horizontalAlignment = Alignment.Start
        ) {
            // ——— Заголовок + стрелка (как у тебя: текст слева, стрелка справа)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Создание нового правила",
                        fontSize = t.titleSize.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF53291E)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Придумайте то, что станет частью режима вашего солнышка...",
                        fontSize = t.subtitleSize.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF290E0C)
                    )
                }
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.size(t.backTap)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Назад",
                        tint = Color(0xFF53291E),
                        modifier = Modifier.size(t.backIcon)
                    )
                }
            }

            Spacer(Modifier.height(t.gap))

            // ——— Название правила
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rule_name),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 6.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = "Название правила",
                    fontSize = t.labelSize.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF461E1B)
                )
            }
            OutlinedTextField(
                value = ruleName.value,
                onValueChange = { ruleName.value = it },
                placeholder = {
                    Text(
                        "Не заходить в Telegram после 21:00",
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF421B14)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = t.fieldMinHeight)
            )

            Spacer(Modifier.height(t.gap))

            // ——— Подробности/суть
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rule_details),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 6.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = "Подробности/суть",
                    fontSize = t.labelSize.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF461E1B)
                )
            }
            OutlinedTextField(
                value = ruleDetails.value,
                onValueChange = { ruleDetails.value = it },
                placeholder = {
                    Text(
                        "Что-то типа дааа",
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF421B14)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = t.fieldMinHeight)
            )

            Spacer(Modifier.height(t.gap))

            // ——— Напоминание
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_reminder),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 6.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = "Напоминание Малышу (видимое сообщение)",
                    fontSize = t.labelSize.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF461E1B)
                )
            }
            OutlinedTextField(
                value = ruleReminder.value,
                onValueChange = { ruleReminder.value = it },
                placeholder = {
                    Text(
                        "Это придет мне в уведомлениях",
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF421B14)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = t.fieldMinHeight)
            )

            Spacer(Modifier.height(t.gap))

            // ——— Категория
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_category),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 6.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = "Категория правила (Вып список)",
                    fontSize = t.labelSize.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF461E1B)
                )
            }
            AppDropdownField(
                value = category.value,
                onSelect = { category.value = it },
                options = categoryOptions,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(t.gap))

            // ——— Статус
            Text(
                text = "Статус правила",
                fontSize = t.labelSize.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF461E1B)
            )
            Row {
                RadioButton(selected = isActive, onClick = { isActive = true });  Text("Активно")
                Spacer(Modifier.width(16.dp))
                RadioButton(selected = !isActive, onClick = { isActive = false }); Text("Временно отключено")
            }

            Spacer(Modifier.height(t.gap * 2))

            // ——— Индикатор сохранения
            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
            }

            // ——— Кнопка сохранения
            Button(
                onClick = {
                    isSaving = true
                    errorMessage = null

                    if (ruleName.value.isNotBlank() && ruleDetails.value.isNotBlank()) {
                        val newRule = hashMapOf(
                            "title" to ruleName.value,
                            "description" to ruleDetails.value,
                            "reminder" to ruleReminder.value,
                            "category" to category.value,
                            "status" to uiToDbStatus(isActive),
                            "createdBy" to mommyUid,
                            "targetUid" to babyUid,
                            "createdAt" to System.currentTimeMillis()
                        )
                        Firebase.firestore.collection("rules")
                            .add(newRule)
                            .addOnSuccessListener {
                                isSaving = false
                                navController.popBackStack()
                            }
                            .addOnFailureListener { e ->
                                isSaving = false
                                errorMessage = e.localizedMessage ?: "Ошибка при сохранении"
                            }
                    } else {
                        isSaving = false
                        errorMessage = "Название и описание обязатильна вводить мамотьке!!! ☺️"
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(t.buttonHeight),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5D8CE)),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving
            ) {
                Text(
                    text = if (isSaving) "Сохранение…" else "Сохранить правило",
                    fontSize = 18.sp,
                    color = Color(0xFF53291E),
                    fontWeight = FontWeight.SemiBold
                )
            }

            // ——— Ошибка при сохранении
            errorMessage?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Text(
                    text = msg,
                    color = Color.Red,
                    fontStyle = FontStyle.Italic
                )
            }

            Spacer(Modifier.height(t.vPad))
        }
    }
}