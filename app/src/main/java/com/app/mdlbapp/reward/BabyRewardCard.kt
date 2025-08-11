package com.app.mdlbapp.reward

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.delay
import java.time.Instant.now
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun BabyRewardCard(
    reward: Reward,
    totalPoints: Int,
    babyUid: String,
    onBuy: (Reward) -> Unit
) {
    // мягкий тик, чтобы в полночь кнопка сама оживала
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() } }

    val canAfford = totalPoints >= reward.cost
    val isPending = reward.pending && reward.pendingBy == babyUid
    val isDisabled = (reward.disabledUntil ?: 0L) > now

    val buttonEnabled = canAfford && !isPending && !isDisabled
    val bg = if (isDisabled) Color(0xFFEDEDED) else Color(0xFFFDF2EC)

    // Box с общей стилистикой, совпадающей с картой Мамочки
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFDEBEB5), RoundedCornerShape(12.dp))
            .background(bg, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reward.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF552216)
                    )
                    Text(
                        text = "${reward.cost} баллов • ${reward.type}",
                        fontSize = 16.sp,
                        color = Color(0xFF552216),
                        fontStyle = FontStyle.Italic
                    )
                }

                val endOfDayMillis = remember {
                    val zone = java.time.ZoneId.systemDefault()
                    java.time.LocalDate.now(zone).plusDays(1)
                        .atStartOfDay(zone).toInstant().toEpochMilli()
                }

                // Показываем кнопку или статус ожидания
                if (isPending) {
                    Text("Ожидает подтверждения", color = Color.Red, fontStyle = FontStyle.Italic)
                } else {
                    Button(
                        onClick = {
                            onBuy(reward) // списание поинтов делаем в экране

                            // Всегда ставим блокировку до конца дня
                            reward.id?.let { rid ->
                                // ВЫЧИСЛЯЕМ тут, без remember
                                val zone = ZoneId.systemDefault()
                                val endOfToday = LocalDate.now(zone)
                                    .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                                val updates = mutableMapOf<String, Any>(
                                    "disabledUntil" to endOfToday
                                )
                                if (!reward.autoApprove) {
                                    updates += mapOf("pending" to true, "pendingBy" to babyUid)
                                }
                                Firebase.firestore.collection("rewards").document(rid).update(updates)
                            }
                        },
                        enabled = buttonEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when {
                                isDisabled -> Color(0xFFDFDFDF)
                                canAfford  -> Color(0xFFF5D8CE)
                                else       -> Color(0xFFDFDFDF)
                            }
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when {
                                isDisabled -> "Доступно завтра"
                                canAfford  -> "Купить"
                                else       -> "Нет баллов"
                            },
                            color = Color(0xFF552216),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = reward.description,
                fontSize = 18.sp,
                color = Color(0xFF4A3C36),
                fontStyle = FontStyle.Italic
            )

            if (reward.messageFromMommy.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Сообщение: ${reward.messageFromMommy}",
                    fontSize = 14.sp,
                    color = Color(0xFF795548),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}