package com.app.mdlbapp.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.core.time.formatDateLabel
import com.app.mdlbapp.reactions.ReactionOverlay
import kotlinx.coroutines.delay
import java.time.LocalDate
import kotlin.collections.plus

@Composable
fun BabyHabitsScreen(navController: NavController) {
    val habits = remember { mutableStateListOf<Map<String, Any>>() }
    val backgroundColor = Color(0xFFF5EAE3)
    val textColor = Color(0xFF53291E)
    val textColorMain = Color(0xFF000000)

    val context = LocalContext.current

    var showReaction      by remember { mutableStateOf(false) }
    var reactionImageRes  by remember { mutableStateOf<Int?>(null) }
    var reactionMessage   by remember { mutableStateOf("") }
    var earnedPoints      by remember { mutableStateOf(0) }


    // UID Мамочки для фильтрации привычек
    var mommyUid by remember { mutableStateOf<String?>(null) }

    // 1) Загрузка UID Мамочки из документа пользователя малыша
    LaunchedEffect(Unit) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("users").document(babyUid).get()
            .addOnSuccessListener { doc ->
                mommyUid = doc.getString("pairedWith")
            }
    }

    // 2) Подгружаем привычки, выданные текущей Мамочкой
    LaunchedEffect(mommyUid) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val mUid = mommyUid ?: return@LaunchedEffect
        Firebase.firestore.collection("habits")
            .whereEqualTo("babyUid", babyUid)
            .whereEqualTo("mommyUid", mUid)
            .whereEqualTo("status", "on")
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

    // 🔁 Группировка по дате nextDueDate
    val groupedHabits = habits
        .mapNotNull {
            val dateStr = it["nextDueDate"] as? String ?: return@mapNotNull null
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                null
            } ?: return@mapNotNull null
            date to it
        }
        .groupBy({ it.first }, { it.second }) // Группируем по LocalDate
        .toSortedMap() // Сортируем по возрастанию даты

    // 1) Авто-скрытие через 3 секунды
    LaunchedEffect(showReaction) {
        if (showReaction) {
            delay(3_000)            // ждём 3 секунды
            showReaction = false    // и скрываем оверлей
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "🧸 Твои привычки",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textColorMain,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (habits.isEmpty()) {
                Text(
                    text = "Нет заданных привычек.\nЖди указаний Мамочки...",
                    fontStyle = FontStyle.Italic,
                    color = Color.DarkGray,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupedHabits.forEach { (date, habitsForDate) ->
                        val dateLabel = formatDateLabel(date.toString()) // форматируем надпись

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
                            // 2) прокидываем onCompleted в карточку
                            BabyHabitCard(
                                habit = habit,
                                onCompleted = {
                                    // 1) Преобразуем res из Long (Firestore) в Int
                                    reactionImageRes =
                                        (habit["reactionImageRes"] as? Long ?: 0L).toInt()
                                    // 2) Текст реакции — в поле "reaction" (или "reactionMessage", смотрите, как назвали вы)
                                    reactionMessage = (habit["reaction"] as? String) ?: ""
                                    // 3) Баллы
                                    earnedPoints = (habit["points"] as? Long ?: 1L).toInt()
                                    // 4) Показываем оверлей
                                    showReaction = true
                                }
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showReaction && reactionImageRes != null,
            enter   = fadeIn(animationSpec = tween(durationMillis = 500)),
            exit    = fadeOut(animationSpec = tween(durationMillis = 500))
        ) {
            ReactionOverlay(
                resId = reactionImageRes!!,
                message = reactionMessage,
                points = earnedPoints
            ) {
                showReaction = false  // тоже можно тапом закрывать
            }
        }

        // 3) сам оверлей поверх всего
        if (showReaction && reactionImageRes != null) {
            ReactionOverlay(
                resId = reactionImageRes!!,
                message = reactionMessage,
                points = earnedPoints
            ) {
                showReaction = false
            }
        }
    }
}