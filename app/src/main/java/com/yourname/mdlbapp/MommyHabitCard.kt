package com.yourname.mdlbapp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun MommyHabitCard(habit: Map<String, Any>, navController: NavController) {
    val title = habit["title"] as? String ?: "Без названия"
    val reportType = habit["reportType"] as? String ?: "none"
    val completedToday = habit["completedToday"] as? Boolean ?: false
    val currentStreak = habit["currentStreak"] as? Long ?: 0L
    val dailyTarget = 1

    val CardBackground = Color(0xFFF8E7DF)
    val CardBorderColor = Color(0xFFE0C2BD)
    val TextDarkBrown = Color(0xFF552216)

    var expanded by remember { mutableStateOf(false) }
    var showDeactivateDialog by remember { mutableStateOf(false) }

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
                Spacer(modifier = Modifier.height(4.dp))

                val status = habit["status"] as? String ?: "off"

                Text(
                    text = if (status == "on") "Статус: Активна" else "Статус: Отключена",
                    fontSize = 14.sp,
                    color = if (status == "on") Color(0xFF006400) else Color.Gray,
                    fontStyle = FontStyle.Italic
                )
            }


            // Правая часть: иконка меню + отчёт + выполнено
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Top,
                modifier = Modifier.offset(y = 6.dp) // ⬅ приопускаем всё
            ) {
                IconButton(
                    onClick = { expanded = true },
                    modifier = Modifier.offset(y = (-4).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Меню",
                        tint = TextDarkBrown
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(CardBackground)
                        .border(1.dp, CardBorderColor, RoundedCornerShape(12.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("✏️ Редактировать", fontStyle = FontStyle.Italic, color = TextDarkBrown) },
                        onClick = {
                            expanded = false
                            val id = habit["id"] as? String ?: return@DropdownMenuItem
                            navController.navigate("edit_habit/$id")
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            val isActive = habit["status"] == "on"
                            Text(
                                text = if (isActive) "⏸️ Отключить" else "▶️ Активировать",
                                fontStyle = FontStyle.Italic,
                                color = TextDarkBrown
                            )
                        },
                        onClick = {
                            expanded = false
                            val habitId = habit["id"] as? String ?: return@DropdownMenuItem
                            val isActive = habit["status"] == "on"
                            if (isActive) {
                                showDeactivateDialog = true
                            } else {
                                Firebase.firestore.collection("habits").document(habitId)
                                    .update("status", "on")
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("🗑️ Удалить", fontStyle = FontStyle.Italic, color = Color.Red) },
                        onClick = {
                            expanded = false
                            val habitId = habit["id"] as? String ?: return@DropdownMenuItem
                            Firebase.firestore.collection("habits").document(habitId).delete()
                        }
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
    if (showDeactivateDialog) {
        AlertDialog(
            onDismissRequest = { showDeactivateDialog = false },
            title = {
                Text(
                    text = "Вы уверены?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    color = TextDarkBrown
                )
            },
            text = {
                Text(
                    text = "Привычка останется в статусе «Временно отключено» и не будет отображаться у Малыша.",
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic,
                    color = TextDarkBrown
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeactivateDialog = false
                        val habitId = habit["id"] as? String ?: return@Button
                        Firebase.firestore.collection("habits").document(habitId)
                            .update("status", "off")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5D8CE)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Да", color = TextDarkBrown)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeactivateDialog = false },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, TextDarkBrown),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextDarkBrown)
                ) {
                    Text("Нет", fontStyle = FontStyle.Italic)
                }
            },
            containerColor = Color(0xFFFDE9DD),
            shape = RoundedCornerShape(16.dp)
        )
    }
}