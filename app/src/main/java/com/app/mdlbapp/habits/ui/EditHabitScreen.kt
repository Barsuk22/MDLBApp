package com.app.mdlbapp.habits.ui

import android.app.TimePickerDialog
import android.widget.Toast
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.R
import com.app.mdlbapp.habits.background.workers.HabitDeadlineScheduler
import com.app.mdlbapp.reactions.EmojiPickerGrid
import com.app.mdlbapp.reactions.ReactionImage
import com.app.mdlbapp.rule.computeNextDueDateForHabit
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditHabitScreen(navController: NavController, habitId: String) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val dateFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeFormatter = dateFormatter

    val backgroundColor = Color(0xFFFDE9DD)
    val textColor = Color(0xFF53291E)

    val repeatOptions = listOf("Каждый день" to "daily", "По дням недели" to "weekly", "Один раз" to "once")
    val reportOptions = listOf("Нет отчета" to "none", "Текст" to "text", "Фото" to "photo", "Аудио" to "audio", "Видео" to "video")
    val categories = listOf("Дисциплина", "Забота", "Поведение", "Настроение")
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")




    var title by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("once") }
    val selectedDays = remember { mutableStateListOf<String>() }
    var selectedSingleDay by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf<Calendar?>(null) }
    var reportType by remember { mutableStateOf("none") }
    var category by remember { mutableStateOf("Дисциплина") }
    var points by remember { mutableStateOf(5) }
    var penalty by remember { mutableStateOf(-5) }
    var reaction by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("off") }

    var categoryExpanded by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

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

    var habitBabyUid by remember { mutableStateOf<String?>(null) }
    var babyZone     by remember { mutableStateOf<ZoneId?>(null) }

    LaunchedEffect(habitId) {
        Firebase.firestore.collection("habits").document(habitId).get()
            .addOnSuccessListener { doc ->
                val data = doc.data
                if (data != null) {                // 👈 проверочка
                    habitBabyUid = data["babyUid"] as? String

                    // грузим пояск малыша
                    habitBabyUid?.let { bUid ->
                        Firebase.firestore.collection("users").document(bUid).get()
                            .addOnSuccessListener { uDoc ->
                                babyZone = runCatching {
                                    ZoneId.of(uDoc.getString("timezone") ?: "")
                                }.getOrNull() ?: ZoneId.systemDefault()
                            }
                    }

                    // дальше — остальные поля привычки
                    title   = data["title"]         as? String ?: ""
                    repeat  = data["repeat"]        as? String ?: "once"
                    selectedDays.clear()
                    selectedDays.addAll((data["daysOfWeek"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                    selectedSingleDay = data["oneTimeDate"] as? String
                    data["deadline"]?.let {
                        val cal = Calendar.getInstance()
                        cal.time = dateFormatter.parse(it as String) ?: Date()
                        time = cal
                    }
                    reportType       = data["reportType"]       as? String ?: "none"
                    category         = data["category"]         as? String ?: "Дисциплина"
                    points           = (data["points"]          as? Long)?.toInt() ?: 5
                    penalty          = (data["penalty"]         as? Long)?.toInt() ?: -5
                    reaction         = data["reaction"]         as? String ?: ""
                    reminder         = data["reminder"]         as? String ?: ""
                    status           = data["status"]           as? String ?: "off"
                    reactionImageRes = (data["reactionImageRes"] as? Long)?.toInt()
                } else {
                    Toast.makeText(context, "Пустая карточка привычки", Toast.LENGTH_SHORT).show()
                    navController.popBackStack()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Ошибка загрузки привычки", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(backgroundColor)
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDCD3 Редактирование привычки",
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

        // Название привычки
        Text("📍 Название привычки", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textColor)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(12.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp)
        ) {
            Text("📍", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp), color = textColor)
            Box(modifier = Modifier.fillMaxWidth()) {
                if (title.isEmpty()) {
                    Text("Занятие английским", fontSize = 14.sp, color = textColor.copy(alpha = 0.5f))
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = textColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Повторяемость
        Text("🔗 Повторяемость", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeatOptions.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        RadioButton(
                            selected = repeat == value,
                            onClick = { repeat = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(selectedColor = textColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, fontSize = 15.sp, color = textColor)
                    }
                }
            }

            if (repeat == "weekly") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
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
                            Text(day, fontSize = 14.sp, color = if (isSelected) Color.White else textColor)
                        }
                    }
                }
            }

            if (repeat == "once") {
                OutlinedTextField(
                    value = selectedSingleDay ?: "",
                    onValueChange = { selectedSingleDay = it },
                    label = { Text("Дата выполнения (dd-MM-yyyy)", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Время
        Text(
            text = if (time == null) "\uD83D\uDD52 Время не выбрано" else "\uD83D\uDD52 ${timeFormatter.format(time!!.time)}",
            fontSize = 16.sp,
            color = textColor,
            modifier = Modifier
                .clickable {
                    val now = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
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
        Text("📝 Тип отчёта", fontWeight = FontWeight.SemiBold, color = textColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reportOptions.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = reportType == value,
                            onClick = { reportType = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(selectedColor = textColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, fontSize = 15.sp, color = textColor)
                    }
                }
            }
        }

        // Категория
        Text("📂 Категория", fontWeight = FontWeight.SemiBold, color = textColor)
        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        category = it
                        categoryExpanded = false
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

        OutlinedTextField(
            value = reaction,
            onValueChange = { reaction = it },
            label = { Text("💬 Сообщение в реакции") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
        )

        // Статус
        Text("🔓 Статус привычки", fontWeight = FontWeight.SemiBold, color = textColor)
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

// Показываем индикатор над кнопками, если идёт сохранение
        if (isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }

// Кнопки
        Button(
            onClick = {
                if (title.isBlank()) {
                    errorMessage = "Название не может быть пустым"
                    return@Button
                }

                // ✅ Валидация режимов
                if (status == "on") {
                    if (repeat == "weekly" && selectedDays.isEmpty()) {
                        errorMessage = "Выберите хотя бы один день недели"
                        return@Button
                    }
                    if (repeat == "once" && toIsoOrNull(selectedSingleDay) == null) {
                        errorMessage = "Укажите дату в формате dd-MM-yyyy или yyyy-MM-dd"
                        return@Button
                    }
                }

                isSaving = true
                errorMessage = null

                val zone = babyZone ?: run {
                    isSaving = false
                    Toast.makeText(context, "Ещё загружаю пояск малыша…", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                val deadline = time?.let { dateFormatter.format(it.time) } ?: "23:59"
                val isoFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd")

                // ✅ Нормализация по режимам
                val oneTimeISO = if (repeat == "once") toIsoOrNull(selectedSingleDay) else null
                val daysForStore = if (repeat == "weekly") selectedDays.toList() else emptyList()

                // ✅ Штраф — отрицательный
                val penaltySanitized = if (penalty > 0) -penalty else penalty

                // ✅ nextDueDate считаем только когда статус on
                val newNext = if (status == "on") {
                    computeNextDueDateForHabit(
                        repeatMode  = repeat,
                        daysOfWeek  = daysForStore,
                        oneTimeDate = oneTimeISO,
                        deadline    = deadline,
                        fromDate    = LocalDate.now(zone),
                        nowTime     = LocalTime.now(zone)
                    )?.format(isoFmt)
                } else null

                if (status == "off") {
                    HabitDeadlineScheduler
                        .cancelForHabit(context, habitId)
                }

                val updated = mapOf(
                    "title"            to title,
                    "repeat"           to repeat,
                    "daysOfWeek"       to daysForStore,
                    "oneTimeDate"      to oneTimeISO,
                    "deadline"         to deadline,
                    "reportType"       to reportType,
                    "category"         to category,
                    "points"           to points,
                    "penalty"          to penaltySanitized,
                    "reminder"         to reminder,
                    "status"           to status,
                    "reaction"         to reaction,
                    "reactionImageRes" to reactionImageRes,
                    "nextDueDate"      to newNext
                )

                Firebase.firestore.collection("habits").document(habitId)
                    .update(updated)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Изменения сохранены", Toast.LENGTH_SHORT).show()

//                        // пересадка дедлайнов на сегодня по пояску малыша
//                        com.app.mdlbapp.habits.background.workers.HabitDeadlineScheduler
//                            .rescheduleAllForToday(context, zone)
//                        com.app.mdlbapp.habits.background.workers.HabitUpdateScheduler
//                            .scheduleNext(context, zone)

                        navController.popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Ошибка при сохранении", Toast.LENGTH_SHORT).show()
                        isSaving = false
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving && babyZone != null,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8C8BB))
        ) {
            Text(
                if (isSaving) "Сохранение…" else "Сохранить",
                color = textColor
            )
        }

        OutlinedButton(
            onClick = {
                Firebase.firestore.collection("habits").document(habitId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Не удалось удалить", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.Red),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Text("Удалить привычку")
        }
    }
}

private fun toIsoOrNull(input: String?): String? {
    if (input.isNullOrBlank()) return null
    return try {
        // сначала пробуем dd-MM-yyyy
        val dmy = LocalDate.parse(input, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
        dmy.format(DateTimeFormatter.ISO_DATE)
    } catch (_: Exception) {
        // потом пробуем уже ISO
        try {
            val iso = LocalDate.parse(input, DateTimeFormatter.ISO_DATE)
            iso.format(DateTimeFormatter.ISO_DATE)
        } catch (_: Exception) {
            null
        }
    }
}