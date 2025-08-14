package com.app.mdlbapp.habits.ui

import android.app.TimePickerDialog
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.R
import com.app.mdlbapp.habits.data.saveHabit
import com.app.mdlbapp.reactions.EmojiPickerGrid
import com.app.mdlbapp.reactions.ReactionImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.tasks.await
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateHabitScreen(navController: NavController) {
    val backgroundColor = Color(0xFFFDE9DD)
    val textColor = Color(0xFF53291E)

    var title by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("once") }
    var reportType by remember { mutableStateOf("none") }
    var category by remember { mutableStateOf("Дисциплина") }
    var points by remember { mutableStateOf(5) }
    var penalty by remember { mutableStateOf(-5) }
    var reaction by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("off") }

    val repeatOptions = listOf("Каждый день" to "daily", "По дням недели" to "weekly", "Один раз" to "once")
    val reportOptions = listOf("Нет отчета" to "none", "Текст" to "text", "Фото" to "photo", "Аудио" to "audio", "Видео" to "video")
    val categories = listOf("Дисциплина", "Забота", "Поведение", "Настроение")

    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var time by remember { mutableStateOf<Calendar?>(null) }

    val selectedDays = remember { mutableStateListOf<String>() }
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    var selectedSingleDay by remember { mutableStateOf<String?>(null) }

    var babyUid by remember { mutableStateOf<String?>(null) }
    var babyZone by remember { mutableStateOf<ZoneId?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInactiveDialog by remember { mutableStateOf(false) }


    var reactionImageRes by remember { mutableStateOf<Int?>(null) }

    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiRes: List<Int> = remember {
        R.drawable::class.java
            .declaredFields
            .filter { it.name.startsWith("emo_") }
            .mapNotNull {
                runCatching { it.getInt(null) }.getOrNull()
            }
    }


    LaunchedEffect(Unit) {
        val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val db = Firebase.firestore

        // 1) узнаём babyUid ПРАВИЛЬНО — в state, без 'val'
        babyUid = db.collection("users").document(mommyUid).get().await()
            .getString("pairedWith")

        val bUid = babyUid ?: return@LaunchedEffect

        // 2) читаем пояс малыша
        val babyDoc = db.collection("users").document(bUid).get().await()
        val zone = runCatching { ZoneId.of(babyDoc.getString("timezone") ?: "") }.getOrNull()
            ?: ZoneId.systemDefault()
        babyZone = zone

        Log.d("TZ", "habit-create: reschedule for zone=$zone")
        //HabitDeadlineScheduler.rescheduleAllForToday(context = context, zone = zone)
        //HabitUpdateScheduler.scheduleNext(context, zone)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // ⬅ Прокрутка
            .background(backgroundColor)
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Заголовок и назад
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDCD3 Создание привычки",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() },
                tint = textColor
            )
        }

        Text(
            text = "Придумайте то, что станет частью дисциплины вашего мальчика…",
            fontSize = 17.sp,
            color = textColor
        )

        Text(
            text = "📍 Название привычки",
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontSize = 16.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(12.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "\uD83D\uDCCD", // 📍
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 6.dp),
                color = textColor
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                if (title.isEmpty()) {
                    Text(
                        text = "Занятие английским", // 👈 placeholder
                        fontSize = 14.sp,
                        color = textColor.copy(alpha = 0.5f) // полупрозрачный
                    )
                }

                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        color = textColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Повторяемость
        Text(
            text = "\uD83D\uDD17 Повторяемость",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = textColor
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0C2BD), // цвет рамки
                    shape = RoundedCornerShape(20.dp) // скругление углов
                )
                .background(
                    color = Color(0xFFF9E3D6), // фон внутри рамки
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(12.dp) // внутренние отступы рамки
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeatOptions.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        RadioButton(
                            selected = repeat == value,
                            onClick = { repeat = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = textColor,
                                unselectedColor = textColor
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = label, fontSize = 15.sp, color = textColor)
                    }
                }
                if (repeat == "weekly") {
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        weekDays.forEach { day ->
                            val isSelected = selectedDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) textColor else Color.Transparent)
                                    .border(1.dp, textColor, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isSelected) selectedDays.remove(day)
                                        else selectedDays.add(day)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = day,
                                    color = if (isSelected) Color.White else textColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        val daysOfWeek = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        if (repeat == "once") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                daysOfWeek.forEach { day ->
                    val isSelected = selectedSingleDay == day
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) textColor else Color.Transparent)
                            .border(1.dp, textColor, RoundedCornerShape(12.dp))
                            .clickable {
                                selectedSingleDay = if (isSelected) null else day
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = day,
                            fontSize = 14.sp,
                            color = if (isSelected) Color.White else textColor
                        )
                    }
                }
            }
        }

        Text(
            text = if (time == null) "\uD83D\uDD52 Время не выбрано" else "\uD83D\uDD52 ${timeFormatter.format(time!!.time)}",
            color = textColor,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable {
                    val now = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour: Int, minute: Int ->
                            time = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                            }
                        },
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        true
                    ).show()
                }
                .padding(vertical = 8.dp)
        )


        // Тип отчета
        Text("\uD83D\uDCC3 Тип отчета", fontWeight = FontWeight.SemiBold, color = textColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0C2BD),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(
                    color = Color(0xFFF9E3D6),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                reportOptions.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        RadioButton(
                            selected = reportType == value,
                            onClick = { reportType = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = textColor,
                                unselectedColor = textColor
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = textColor
                        )
                    }
                }
            }
        }


        // Категория
        Text("\uD83D\uDCC2 Категория привычки (Вып список)", fontWeight = FontWeight.SemiBold, color = textColor)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        category = it
                        expanded = false
                    })
                }
            }
        }

        // Баллы за выполнение
        Text("\uD83D\uDCCA Баллы за выполнение", fontWeight = FontWeight.SemiBold, color = textColor)

        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .height(36.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { if (points > 0) points-- },
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = "+ $points",
                    fontSize = 16.sp,
                    color = textColor
                )
                Text(
                    text = "+",
                    modifier = Modifier.clickable { if (points < 100) points++ },
                    fontSize = 18.sp,
                    color = textColor
                )
            }
        }

        // Баллы за пропуск
        Text("\uD83D\uDCCA Баллы за пропуск", fontWeight = FontWeight.SemiBold, color = textColor)
        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .height(36.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { if (penalty > -100) penalty-- },
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = "$penalty",
                    fontSize = 16.sp,
                    color = textColor
                )
                Text(
                    text = "+",
                    modifier = Modifier.clickable { if (penalty < 0) penalty++ },
                    fontSize = 18.sp,
                    color = textColor
                )
            }
        }

        // Реакция Мамочки

        // 1) Заголовок
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp) // Немного отступов
        ) {
            Text("Выбор реакции", fontWeight = FontWeight.SemiBold, color = textColor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (reactionImageRes != null) {
                    ReactionImage(
                        resId = reactionImageRes!!,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterStart)
                    )
                } else {
                    Text(
                        text = "Не выбрано",
                        color = textColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { showEmojiPicker = true }
                )
            }
        }

        // 3) Диалог выбора эмодзи:
        if (showEmojiPicker) {
            AlertDialog(
                onDismissRequest = { showEmojiPicker = false },
                title = { Text("Выберите смайлик") },
                text = {
                    EmojiPickerGrid(
                        items = emojiRes
                    ) { selectedRes ->
                        reactionImageRes = selectedRes
                        showEmojiPicker  = false
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEmojiPicker = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Сообщение в реакции
        OutlinedTextField(
            value = reaction,
            onValueChange = { reaction = it },
            label = { Text("💬 Сообщение в реакции") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
        )

        // Статус
        Text("\uD83D\uDD11 Статус Привычки", fontWeight = FontWeight.SemiBold, color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = status == "on", onClick = { status = "on" })
                Text("Активно", color = textColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = status == "off", onClick = { status = "off" })
                Text("Временно отключено", color = textColor)
            }
        }

        // Показываем индикатор над всеми полями, если идёт сохранение
        if (isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Кнопка сохранить
        Button(
            onClick = {
                // валидация
                if (title.isBlank()) {
                    errorMessage = "Название привычки не может быть пустым"
                    return@Button
                }

                if (status == "off") {
                    showInactiveDialog = true
                    return@Button
                }

                if (status == "on") {
                    if (repeat == "weekly" && selectedDays.isEmpty()) {
                        errorMessage = "Выберите хотя бы один день недели"
                        return@Button
                    }

                    if (repeat == "once" && selectedSingleDay.isNullOrEmpty()) {
                        errorMessage = "Выберите день для однократной привычки"
                        return@Button
                    }
                }

                // начинаем сохранение
                isSaving = true
                errorMessage = null

                val zone0 = babyZone ?: run { // 👈 безопасно достали
                    isSaving = false
                    errorMessage = "Не успела загрузиться тайм-зонка малыша, подожди секунду…"
                    return@Button
                }

                saveHabit(
                    context = context,
                    navController = navController,
                    babyUid = babyUid,
                    title = title,
                    repeat = repeat,
                    selectedDays = selectedDays,
                    selectedSingleDay = selectedSingleDay,
                    time = time,
                    reportType = reportType,
                    category = category,
                    points = points,
                    penalty = penalty,
                    reaction = reaction,
                    reminder = reminder,
                    status = status,
                    zone = zone0,
                    reactionImageRes = reactionImageRes,
                ) { success, error ->
                    isSaving = false
                    if (success) {
                        navController.popBackStack()
                    } else {
                        errorMessage = error ?: "Ошибка при добавлении привычки"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && babyZone != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8C8BB))
        ) {
            if (isSaving) {
                Text("Сохранение…", color = textColor)
            } else {
                Text("Сохранить привычку", color = textColor)
            }
        }

        // Ошибка под кнопкой
        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = Color.Red,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Диалог для подтверждения отключенного статуса
        if (showInactiveDialog) {
            AlertDialog(
                onDismissRequest = { showInactiveDialog = false },
                title = {
                    Text(
                        text = "Вы уверены?",
                        fontSize = 20.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF53291E)
                    )
                },
                text = {
                    Text(
                        text = "Привычка останется в статусе «Временно отключено» и не будет отображаться у Малыша.",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF53291E)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showInactiveDialog = false
                            isSaving = true
                            errorMessage = null

                            if (status == "on") {
                                if (repeat == "weekly" && selectedDays.isEmpty()) {
                                    errorMessage = "Выберите хотя бы один день недели"
                                    return@Button
                                }

                                if (repeat == "once" && selectedSingleDay.isNullOrEmpty()) {
                                    errorMessage = "Выберите день для однократной привычки"
                                    return@Button
                                }
                            }

                            val zone0 = babyZone ?: run {
                                isSaving = false
                                errorMessage = "Не успела загрузиться тайм-зонка малыша"
                                return@Button
                            }
                            saveHabit(
                                context = context,
                                navController = navController,
                                babyUid = babyUid,
                                title = title,
                                repeat = repeat,
                                selectedDays = selectedDays,
                                selectedSingleDay = selectedSingleDay,
                                time = time,
                                reportType = reportType,
                                category = category,
                                points = points,
                                penalty = penalty,
                                reaction = reaction,
                                reminder = reminder,
                                status = status,
                                zone = zone0,
                                reactionImageRes = reactionImageRes
                            ) { success, error ->
                                isSaving = false
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    errorMessage = error ?: "Ошибка при добавлении привычки"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5D8CE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Да")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showInactiveDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF53291E)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF53291E))
                    ) {
                        Text("Нет", fontStyle = FontStyle.Italic)
                    }
                },
                containerColor = Color(0xFFFDE9DD),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}