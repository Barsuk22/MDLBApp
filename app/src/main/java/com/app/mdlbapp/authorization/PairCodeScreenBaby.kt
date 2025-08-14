//файл PairCodeScreenBaby.kt

package com.app.mdlbapp.authorization

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.Screen

@Composable
fun PairCodeScreenBaby(uid: String, navController: NavHostController) {
    val db = Firebase.firestore
    var inputCode by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

// валидность: ровно 6 цифрок
    val code = inputCode.trim()
    val codeValid = code.length == 6 && code.all { it.isDigit() }
    val context = LocalContext.current

    // Единый набор цветов для экранов спаривания
    val backgroundColor = Color(0xFFF8EDE6)
    val cardColor       = Color(0xFFFDE9DD)
    val accentColor     = Color(0xFF552216)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        // Карточка с кодом. Она приподнята и отделена от фона границей.
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = BorderStroke(2.dp, Color(0xFFE0C2BD))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Введи код, полученный от Мамочки",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = inputCode,
                    onValueChange = {
                        inputCode = it.filter { ch -> ch.isDigit() }.take(6)
                        if (errorText != null) errorText = null
                    },
                    isError = errorText != null,
                    label = { Text("Код от Мамочки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorText!!, color = Color.Red)
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    enabled = !isSaving && codeValid,
                    onClick = {
                        if (!codeValid) {              // двойная защита
                            errorText = "Нужен 6-значный кодик"
                            return@Button
                        }
                        isSaving = true
                        errorText = null

                        val db = Firebase.firestore
                        val codeRef = db.collection("pairCodes").document(code) // тут уже не пусто

                        codeRef.get()
                            .addOnSuccessListener { doc ->
                                if (!doc.exists()) {
                                    isSaving = false
                                    errorText = "Такого кодика нет"
                                    return@addOnSuccessListener
                                }
                                val mommyUid = doc.getString("mommyUid")
                                if (mommyUid.isNullOrEmpty()) {
                                    isSaving = false
                                    errorText = "Кодик испорчен"
                                    return@addOnSuccessListener
                                }

                                // делаем всё атомно: себе пару, маме пару, удалить код
                                val presetTz = doc.getString("defaultBabyTimezone")

                                db.runBatch { b ->
                                    val babyRef  = db.collection("users").document(uid)
                                    val mommyRef = db.collection("users").document(mommyUid)

                                    b.update(babyRef,  "pairedWith", mommyUid)
                                    b.update(mommyRef, "pairedWith", uid)

                                    // если в коде пояска нет — просто ничего не делаем
                                    if (!presetTz.isNullOrEmpty()) {
                                        b.update(babyRef, "timezone", presetTz)
                                    }

                                    b.delete(codeRef)

                                }.addOnSuccessListener {
                                    isSaving = false
                                    navController.navigate(Screen.Baby.route) {
                                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                    }
                                }.addOnFailureListener { e ->
                                    isSaving = false
                                    errorText = e.localizedMessage ?: "Не вышло спариться"
                                }
                            }
                            .addOnFailureListener { e ->
                                isSaving = false
                                errorText = e.localizedMessage ?: "Не получилось проверить код"
                            }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5D8CE))
                ) {
                    Text(if (isSaving) "Ждём…" else "Продолжить")
                }
            }
        }
    }
}