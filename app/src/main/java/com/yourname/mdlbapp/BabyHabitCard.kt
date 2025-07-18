package com.yourname.mdlbapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButtonDefaults.Icon
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

@Composable
fun BabyHabitCard(habit: Map<String, Any>) {
    val title = habit["title"] as? String ?: "Без названия"
    val reportType = habit["reportType"] as? String ?: "none"
    val completedToday = habit["completedToday"] as? Boolean ?: false
    val currentStreak = habit["currentStreak"] as? Long ?: 0L
    val dailyTarget = 1

    val CardBackground = Color(0xFFF8E7DF)
    val CardBorderColor = Color(0xFFE0C2BD)
    val TextDarkBrown = Color(0xFF552216)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(4.dp, CardBorderColor, RoundedCornerShape(12.dp))
            .background(CardBackground, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Левая часть: заголовок + серия
            Column(modifier = Modifier.weight(1f) .offset(y = 20.dp, x = 20.dp)) {
                // 🔖 Название
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_habit_name),
                        contentDescription = null,
                        tint = TextDarkBrown,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = TextDarkBrown
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 🔥 Серия
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥 $currentStreak день",
                        fontSize = 14.sp,
                        color = TextDarkBrown,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    repeat(7) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .border(1.dp, TextDarkBrown, CircleShape)
                                .background(Color.White, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }


            // Правая часть: иконка меню + отчёт + выполнено
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.offset(y = 6.dp) // ⬅ приопускаем всё
            ) {
                IconButton(
                    onClick = { /* TODO */ },
                    modifier = Modifier.offset(y = (-4).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню",
                        tint = TextDarkBrown
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "$reportType",
                    fontSize = 14.sp,
                    color = TextDarkBrown,
                    modifier = Modifier.offset(y = (-20).dp),
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                )

                Text(
                    text = "${if (completedToday) "1" else "0"}/$dailyTarget",
                    fontSize = 14.sp,
                    color = TextDarkBrown,
                    modifier = Modifier.offset(y = (-20).dp),
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}