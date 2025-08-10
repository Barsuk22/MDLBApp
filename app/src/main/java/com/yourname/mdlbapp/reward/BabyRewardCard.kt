package com.yourname.mdlbapp.reward

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun BabyRewardCard(
    reward: Reward,
    totalPoints: Int,
    babyUid: String,
    onBuy: (Reward) -> Unit
) {
    val canAfford = totalPoints >= reward.cost
    val isPending = reward.pending && reward.pendingBy == babyUid
    // Box с общей стилистикой, совпадающей с картой Мамочки
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFDEBEB5), RoundedCornerShape(12.dp))
            .background(Color(0xFFFDF2EC), RoundedCornerShape(12.dp))
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

                // Показываем кнопку или статус ожидания
                if (isPending) {
                    Text(
                        text = "Ожидает подтверждения",
                        color = Color.Red,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    Button(
                        onClick = {
                            // Покупка: если авто-подтверждение, просто списываем баллы. Иначе выставляем pending.
                            onBuy(reward)
                            if (!reward.autoApprove) {
                                reward.id?.let { rid ->
                                    val updates = mapOf(
                                        "pending" to true,
                                        "pendingBy" to babyUid
                                    )
                                    Firebase.firestore.collection("rewards").document(rid).update(updates)
                                }
                            }
                        },
                        enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) Color(0xFFF5D8CE) else Color(0xFFDFDFDF)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (canAfford) "Купить" else "Нет баллов",
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
                    text = "Сообщение: ${'$'}{reward.messageFromMommy}",
                    fontSize = 14.sp,
                    color = Color(0xFF795548),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}