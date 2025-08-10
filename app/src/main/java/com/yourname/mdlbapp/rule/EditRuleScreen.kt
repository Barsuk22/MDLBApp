package com.yourname.mdlbapp.rule

import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yourname.mdlbapp.R
import com.yourname.mdlbapp.rule.ui.CategoryDropdown
import com.yourname.mdlbapp.rule.ui.RuleInputField

@Composable
fun EditRuleScreen(
    navController: NavController,
    ruleId: String
) {
    val context = LocalContext.current
    var rule by remember { mutableStateOf<Rule?>(null) }
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Дисциплина") }
    var status by remember { mutableStateOf("Временно отключено") }

    // Новые состояния
    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(ruleId) {
        Firebase.firestore.collection("rules").document(ruleId).get()
            .addOnSuccessListener { doc ->
                val loadedRule = doc.toObject(Rule::class.java)
                loadedRule?.let {
                    rule = it
                    title = it.title
                    details = it.description
                    reminder = it.reminder ?: ""
                    category = it.category ?: "Дисциплина"
                    status = it.status ?: "Временно отключено"
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Ошибка загрузки", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
    }

    rule?.let {
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
                        text = "Редактирование правила",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF53291E)
                    )
                    Text(
                        text = "Измените то, что больше не служит вашему режиму...",
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

            RuleInputField("Название правила", title, {
                title = it
            }, R.drawable.ic_rule_name, "Не заходить в Telegram после 21:00")

            Spacer(modifier = Modifier.height(16.dp))

            RuleInputField("Подробности/суть", details, {
                details = it
            }, R.drawable.ic_rule_details, "Что-то типа дааа")

            Spacer(modifier = Modifier.height(16.dp))

            RuleInputField("Напоминание Малышу (видимое сообщение)", reminder, {
                reminder = it
            }, R.drawable.ic_reminder, "Это придет мне в уведомлениях")

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_category),
                    contentDescription = null,
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 6.dp),
                    tint = Color.Unspecified
                )
                Text(
                    text = "Категория правила (Вып список)",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF461E1B)
                )
            }

            CategoryDropdown(selectedCategory = category, onCategorySelected = { category = it })

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Статус правила",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF461E1B)
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = status == "Активно", onClick = { status = "Активно" })
                Text(text = "Активно")
                Spacer(modifier = Modifier.width(16.dp))
                RadioButton(
                    selected = status == "Временно отключено",
                    onClick = { status = "Временно отключено" }
                )
                Text(text = "Временно отключено")
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Индикатор во время сохранения
            if (isSaving) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = {
                    // валидация полей до isSaving
                    if (title.isBlank() || details.isBlank()) {
                        errorMessage = "Название и описание обязательны"
                        return@Button
                    }
                    isSaving = true
                    errorMessage = null

                    val updatedFields = mapOf(
                        "title" to title,
                        "description" to details,
                        "reminder" to reminder,
                        "category" to category,
                        "status" to status
                    )
                    Firebase.firestore.collection("rules").document(ruleId).update(updatedFields)
                        .addOnSuccessListener {
                            isSaving = false
                            navController.popBackStack()
                        }
                        .addOnFailureListener {
                            Toast.makeText(context, "Ошибка сохранения", Toast.LENGTH_SHORT).show()
                        }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5D8CE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Сохранить изменения",
                    fontSize = 18.sp,
                    color = Color(0xFF53291E),
                    fontWeight = FontWeight.SemiBold
                )
            }
            errorMessage?.let { msg ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = msg,
                    color = Color.Red,
                    fontStyle = FontStyle.Italic,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}