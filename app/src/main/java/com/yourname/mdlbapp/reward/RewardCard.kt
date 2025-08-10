package com.yourname.mdlbapp.reward

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yourname.mdlbapp.R

@Composable
fun RewardCard(
    reward: Reward,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFDEBEB5), RoundedCornerShape(12.dp))
            .background(Color(0xFFFDF2EC), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            // Заголовок и стоимость
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
                // Кнопка удаления доступна только если нет ожидающей заявки
                if (!reward.pending) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Удалить",
                            tint = Color(0xFF552216)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Описание
            Text(
                text = reward.description,
                fontSize = 18.sp,
                color = Color(0xFF4A3C36),
                fontStyle = FontStyle.Italic
            )

            // Если есть сообщение от Мамочки – показываем его
            if (reward.messageFromMommy.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Сообщение: ${'$'}{reward.messageFromMommy}",
                    fontSize = 14.sp,
                    color = Color(0xFF795548),
                    fontStyle = FontStyle.Italic
                )
            }

            // Если награда ожидает подтверждения
            if (reward.pending && reward.pendingBy != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Запрос от малыша",
                    fontSize = 14.sp,
                    color = Color.Red,
                    fontStyle = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Кнопки подтверждения и отказа
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (onApprove != null) {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0C3B1)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Подтвердить", color = Color(0xFF552216))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (onReject != null) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0C3B1))
                        ) {
                            Text(
                                text = "Отказать",
                                color = Color(0xFF552216),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            } else {
                // Если нет заявки – кнопка редактирования
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
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
}