package com.app.mdlbapp.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.R
import com.app.mdlbapp.core.time.formatDateLabel
import java.time.LocalDate
import kotlin.collections.plus

@Composable
fun HabitsScreen(navController: NavController) {
    val backgroundColor = Color(0xFFF5EAE3)
    val textColor = Color(0xFF53291E)
    val textColorMain = Color(0xFF000000)

    val habits = remember { mutableStateListOf<Map<String, Any>>() }

    var showInactive by remember { mutableStateOf(false) }

    var showFinished by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("habits")
            .whereEqualTo("mommyUid", mommyUid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                habits.clear()
                snapshots?.documents?.forEach { doc ->
                    val data = doc.data
                    if (data != null) {
                        habits.add(data + ("id" to doc.id))
                    }
                }
            }
    }

    // ▶ 1) Отбираем разовые с дедлайном в прошлом
    val today = LocalDate.now()
    val finishedOnceHabits = habits.filter { habit ->
        val repeat = habit["repeat"] as? String ?: "daily"
        if (repeat == "once") {
            val ds = (habit["nextDueDate"] as? String).orEmpty()
            val d = runCatching { LocalDate.parse(ds) }.getOrNull()
            d != null && d.isBefore(today)
        } else false
    }

    // ▶ 2) Остальные привычки (повторяющиеся + разовые c дедлайном ≥ today)
    val otherHabits = habits - finishedOnceHabits

    // ▶ 3) Из остальных: активные и отключённые
    val (activeHabits, inactiveHabits) = otherHabits.partition {
        val status = it["status"] as? String ?: "off"
        status == "on"
    }

    // ▶ 4) Группировка активных (они все due, потому что просроченные once уже вынесены)
    val groupedHabits = activeHabits
        .mapNotNull { habit ->
            val ds = habit["nextDueDate"] as? String ?: return@mapNotNull null
            val d = runCatching { LocalDate.parse(ds) }.getOrNull() ?: return@mapNotNull null
            d to habit
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 🔺 Верхняя строка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_filter),
                contentDescription = "Фильтр",
                tint = textColorMain,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Привычки вашего мальчика",
                fontSize = 20.sp,
                color = textColorMain,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = "Назад",
                tint = textColorMain,
                modifier = Modifier
                    .size(55.dp)
                    .clickable { navController.popBackStack() }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 120.dp), // ⬅ пространство под кнопку
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groupedHabits.forEach { (date, habitsForDate) ->
                        val dateLabel = formatDateLabel(date.toString())

                        item {
                            Text(
                                text = dateLabel,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(habitsForDate) { habit ->
                            MommyHabitCard(habit, navController)
                        }
                    }

                    // === 2) Завершённые одноразовые ===
                    if (finishedOnceHabits.isNotEmpty()) {
                        // заголовок сворачиваемого списка
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFinished = !showFinished }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✅ Завершённые привычки",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showFinished)
                                        Icons.Default.KeyboardArrowUp
                                    else
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }
                        // сам список в AnimatedVisibility
                        item {
                            AnimatedVisibility(
                                visible = showFinished,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    finishedOnceHabits.forEach { habit ->
                                        MommyHabitCard(habit, navController)
                                    }
                                }
                            }
                        }
                    }


                    if (inactiveHabits.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showInactive = !showInactive }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⛔️ Отключённые привычки",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showInactive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = showInactive,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    inactiveHabits.forEach { habit ->
                                        MommyHabitCard(habit, navController)
                                    }
                                }
                            }
                        }
                    }
                }
            }

// 🔒 Зафиксированная кнопка
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xFFF5EAE3), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    OutlinedButton(
                        onClick = { navController.navigate("create_habit") },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFDF2EC)),
                        border = BorderStroke(1.dp, Color(0xFFE0C2BD)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            "＋ Новая привычка",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Привычки для любимого нижнего :)",
                        fontSize = 16.sp,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}