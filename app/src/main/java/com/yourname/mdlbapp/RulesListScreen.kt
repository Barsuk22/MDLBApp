//Файл RulesListScreen.kt

package com.yourname.mdlbapp


import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import android.widget.Toast
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yourname.mdlbapp.Constants
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun RulesListScreen(navController: NavController) {
    val context = LocalContext.current
    val rules = remember { mutableStateListOf<Rule>() }
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

    val selectedCategoryFilter = remember { mutableStateOf("Все") }
    val categories = listOf("Все", "Поведение", "Речь", "Физика", "Контакт", "Прочее")

    // Подписка на обновления правил
    LaunchedEffect(Unit) {
        Firebase.firestore.collection("rules")
            .whereEqualTo("createdBy", mommyUid)
            .orderBy("createdAt")
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener

                val updatedRules = snapshots?.documents?.mapNotNull { doc ->
                    doc.toObject(Rule::class.java)?.apply { id = doc.id }
                } ?: emptyList()

                rules.clear()
                rules.addAll(updatedRules)
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .padding(12.dp)
    ) {
        // Верхняя панель
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* TODO: меню */ }) {
                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.Black)
            }
            Text(
                text = "Ваши правила",
                fontSize = 26.sp,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                color = Color.Black
            )
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Назад", tint = Color.Black)
            }
        }

        var expanded by remember { mutableStateOf(false) }

        @OptIn(ExperimentalMaterial3Api::class)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {

            OutlinedTextField(
                value = selectedCategoryFilter.value,
                onValueChange = {},
                readOnly = true,
                label = { Text("Фильтр по категории") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category) },
                        onClick = {
                            selectedCategoryFilter.value = category
                            expanded = false
                        }
                    )
                }
            }
        }


        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            val filteredRules = if (selectedCategoryFilter.value == "Все") {
                rules
            } else {
                rules.filter { it.category == selectedCategoryFilter.value }
            }

            itemsIndexed(filteredRules) { index, rule ->
                RuleCard(
                    number = index + 1,
                    rule = rule,
                    onDelete = {
                        rules.remove(rule) // мгновенно исчезает из списка
                        Firebase.firestore.collection("rules").document(rule.id!!).delete()
                    },
                    onEdit = {
                        navController.navigate("edit_rule/${rule.id}")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Кнопка "Новое правило"
        OutlinedButton(
            onClick = {
                navController.navigate("create_rule")
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            border = ButtonDefaults.outlinedButtonBorder,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF552216))
        ) {
            Text("＋ Новое правило", fontSize = 20.sp, fontStyle = FontStyle.Italic)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Правила для любимого нижнего :)",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF552216)
        )
    }
}

@Composable
fun RuleCard(
    number: Int,
    rule: Rule,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val isDisabled = rule.status == "Временно отключено"
    val backgroundColor = if (isDisabled) Color(0xFFFFF0F0) else Color(0xFFF8EDE6)
    val borderColor = if (isDisabled) Color(0xFFCC8888) else Color(0xFFDEBEB5)
    val titleColor = if (isDisabled) Color(0xFF993333) else Color(0xFF552216)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(backgroundColor, RoundedCornerShape(12.dp))
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
                            .size(45.dp)
                            .padding(end = 6.dp)
                    )
                    Column {
                        Text(
                            text = "Правило $number: ${rule.title}",
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic,
                            fontSize = 25.sp,
                            color = titleColor
                        )
                        if (isDisabled) {
                            Text(
                                text = "⛔ Временно отключено",
                                color = Color(0xFF993333),
                                fontStyle = FontStyle.Italic,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Удалить правило",
                        tint = Color(0xFF552216),
                        modifier = Modifier.size(25.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = rule.description,
                fontSize = 18.sp,
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

            // Кнопка "Изменить"
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

            // Преобразуем русские аббревиатуры в DayOfWeek
            val wantedDows = daysOfWeek.mapNotNull { Constants.ruToDayOfWeek[it] }
            if (wantedDows.isEmpty()) return null

            // Создаём последовательность пар (offset, date)
            val (_, bestDate) = (0L..6L).asSequence()
                .map { offset -> offset to today.plusDays(offset) }
                .first { (offset, date) ->
                    // 1) дата по дню недели входит в wantedDows
                    date.dayOfWeek in wantedDows
                            // 2) если это сегодня (offset == 0) и есть дедлайн — ещё проверяем время
                            && !(offset == 0L && deadlineTime != null && nowTime.isAfter(deadlineTime))
                }

            bestDate.toString()
        }

        "once" -> {
            // Если oneTimeDate не в формате YYYY-MM-DD, возвращаем null
            oneTimeDate
                ?.takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) }
        }
        else   -> null
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