package com.yourname.mdlbapp.reward

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun CreateRewardScreen(
    navController: NavController,
    mommyUid: String,
    babyUid: String
) {
    val rewardName = remember { mutableStateOf("") }
    val rewardDetails = remember { mutableStateOf("") }
    val rewardCost = remember { mutableStateOf("") }
    val rewardType = remember { mutableStateOf("Контентная") }
    val autoApprove = remember { mutableStateOf(false) }
    val limit = remember { mutableStateOf("Без ограничений") }
    val messageFromMommy = remember { mutableStateOf("") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDE9DD))
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Создание новой награды",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF53291E)
                )
                Text(
                    text = "Придумайте награду вашего солнышка...",
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF290E0C)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                modifier = Modifier
                    .size(35.dp)
                    .clickable { navController.popBackStack() },
                tint = Color(0xFF53291E)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = rewardName.value,
            onValueChange = { rewardName.value = it },
            label = { Text("Название награды") },
            placeholder = { Text("Лечь спать позже") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = rewardDetails.value,
            onValueChange = { rewardDetails.value = it },
            label = { Text("Описание награды") },
            placeholder = { Text("Разрешение лечь спать на час позже") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = rewardCost.value,
            onValueChange = { rewardCost.value = it },
            label = { Text("Стоимость (баллы)") },
            placeholder = { Text("10") },
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text("Тип награды", fontWeight = FontWeight.Medium, color = Color(0xFF53291E))
        RewardTypeDropdown(
            selectedOption = rewardType.value,
            onOptionSelected = { rewardType.value = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = autoApprove.value,
                onCheckedChange = { autoApprove.value = it }
            )
            Text("Автоматическое подтверждение")
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = messageFromMommy.value,
            onValueChange = { messageFromMommy.value = it },
            label = { Text("Сообщение от Мамочки") },
            placeholder = { Text("Ты заслужил это!") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        Button(
            onClick = {
                isSaving = true
                errorMessage = null

                val costValue = rewardCost.value.toIntOrNull()

                if (rewardName.value.isNotBlank() && rewardDetails.value.isNotBlank() && costValue != null) {
                    val newReward = hashMapOf(
                        "title" to rewardName.value,
                        "description" to rewardDetails.value,
                        "cost" to costValue,
                        "type" to rewardType.value,
                        "autoApprove" to autoApprove.value,
                        "limit" to limit.value,
                        "messageFromMommy" to messageFromMommy.value,
                        "createdBy" to mommyUid,
                        "targetUid" to babyUid,
                        "createdAt" to System.currentTimeMillis()
                    )

                    Firebase.firestore.collection("rewards")
                        .add(newReward)
                        .addOnSuccessListener {
                            isSaving = false
                            navController.popBackStack()
                        }
                        .addOnFailureListener { e ->
                            isSaving = false
                            errorMessage = e.localizedMessage ?: "Ошибка при сохранении"
                        }
                } else {
                    isSaving = false
                    errorMessage = "Заполните все обязательные поля корректно"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5D8CE)),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSaving
        ) {
            Text(
                if (isSaving) "Сохранение…" else "Сохранить награду",
                fontSize = 18.sp,
                color = Color(0xFF53291E),
                fontWeight = FontWeight.SemiBold
            )
        }

        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(msg, color = Color.Red, modifier = Modifier.padding(horizontal = 8.dp))
        }
    }
}