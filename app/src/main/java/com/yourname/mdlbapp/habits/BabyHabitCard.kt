package com.yourname.mdlbapp.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import androidx.compose.runtime.rememberCoroutineScope
import com.yourname.mdlbapp.R
import kotlinx.coroutines.launch
import com.yourname.mdlbapp.reward.changePoints
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun BabyHabitCard(habit: Map<String, Any>, onCompleted: () -> Unit) {
    val habitId = habit["id"] as? String ?: return
    val title = habit["title"] as? String ?: "Без названия"
    val reportType = habit["reportType"] as? String ?: "none"
    val dailyTarget = 1

    val completed   = habit["completedToday"] as? Boolean ?: false
    val streak = (habit["currentStreak"] as? Long ?: 0L).toInt()

    // Цвета и модификаторы…
    val CardBorderColor = Color(0xFFE0C2BD)
    val TextDarkBrown = Color(0xFF552216)

    // 1. Считаем дедлайн из данных привычки (строка в формате "HH:mm")
    val deadlineStr = habit["deadline"] as? String
    val deadlineTime = runCatching {
        LocalTime.parse(deadlineStr, DateTimeFormatter.ofPattern("HH:mm"))
    }.getOrNull()
    val nowTime = LocalTime.now()
    val beforeDeadline = deadlineTime?.let { nowTime.isBefore(it) } ?: true

    // 2. Выбираем цвет фона карточки:
    val activeBg = Color(0xFFF8E7DF)
    val doneBeforeDeadlineBg = Color(0xFFCCB2AB) // темнее
    val cardBg = if (completed && beforeDeadline) doneBeforeDeadlineBg else activeBg



    val scheduledStr = habit["nextDueDate"] as? String
    val scheduledDate = runCatching {
        LocalDate.parse(scheduledStr, DateTimeFormatter.ISO_DATE)
    }.getOrNull()
    val isToday = scheduledDate == LocalDate.now()

    // Считаем, можно ли выполнить
    val canComplete = !completed && beforeDeadline && isToday

    // Для начисления баллов используем coroutineScope
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(4.dp, CardBorderColor, RoundedCornerShape(12.dp))
            .background(cardBg, RoundedCornerShape(12.dp))  // <-- здесь используем cardBg
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Левый блок: название + серия
            Column(modifier = Modifier.weight(1f).offset(y = 20.dp, x = 20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_habit_name),
                        contentDescription = null,
                        tint = TextDarkBrown,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = TextDarkBrown
                    )
                }
                Spacer(Modifier.height(6.dp))
                // 🔥 Серия + мини-календарь
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥 $streak ${if (streak % 10 == 1 && streak != 11) "день" else "дня"}",
                        fontSize = 14.sp,
                        color = TextDarkBrown,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    repeat(7) { index ->
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .border(1.dp, TextDarkBrown, CircleShape)
                                .background(
                                    // если точка в пределах текущей серии — закрашиваем
                                    if (index < streak) TextDarkBrown else Color.White,
                                    CircleShape
                                )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }

            // Правый блок: меню, отчёт, счётчик + плюс
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.offset(y = 6.dp)
            ) {
                IconButton(
                    onClick = { /* TODO: меню */ },
                    modifier = Modifier.offset(y = (-4).dp)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Меню", tint = TextDarkBrown)
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = reportType,
                    fontSize = 14.sp,
                    color = TextDarkBrown,
                    modifier = Modifier.offset(y = (-20).dp),
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                )

                // Счётчик + плюс в один Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.offset(y = (-20).dp)
                ) {
                    Text(
                        text = "${if (completed) dailyTarget else 0}/$dailyTarget",
                        fontSize = 14.sp,
                        color = TextDarkBrown,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(Modifier.width(4.dp))

                    if (canComplete) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Выполнить",
                            tint = TextDarkBrown,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable {
                                    val updates = mutableMapOf<String, Any>(
                                        "completedToday" to true,
                                        "currentStreak"  to (streak + 1).toLong()
                                    )
                                    Firebase
                                        .firestore
                                        .collection("habits")
                                        .document(habitId)
                                        .update(updates)
                                        .addOnSuccessListener {
                                            // начисляем баллы малышу
                                            val babyUid  = habit["babyUid"] as? String
                                            val pointVal = (habit["points"] as? Long ?: 0L).toInt()
                                            if (babyUid != null && pointVal != 0) {
                                                scope.launch {
                                                    try {
                                                        changePoints(babyUid, pointVal)
                                                    } catch (_: Exception) {
                                                        // игнорируем ошибки начисления
                                                    }
                                                }
                                            }
                                            // вызываем коллбэк для реакции
                                            onCompleted()
                                        }
                                }
                        )
                    } else {
                        // неактивный плюс после дедлайна или если уже выполнено
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = TextDarkBrown.copy(alpha = 0.3f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}