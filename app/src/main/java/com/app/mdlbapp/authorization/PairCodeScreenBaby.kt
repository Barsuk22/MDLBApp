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
                    onValueChange = { inputCode = it },
                    label = { Text("Код от Мамочки") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        val codeRef = db.collection("pairCodes").document(inputCode)
                        codeRef.get().addOnSuccessListener { doc ->
                            val mommyUid = doc.getString("mommyUid")
                            if (!mommyUid.isNullOrEmpty()) {
                                // Обновляем обоих
                                db.collection("users").document(uid).update("pairedWith", mommyUid)
                                db.collection("users").document(mommyUid).update("pairedWith", uid)
                                // Удаляем код
                                codeRef.delete()
                                // Навигация
                                navController.navigate(Screen.Baby.route) {
                                    popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                }
                            } else {
                                Toast.makeText(context, "Код не найден", Toast.LENGTH_SHORT).show()
                            }
                        }.addOnFailureListener {
                            Toast.makeText(context, "Ошибка при проверке кода", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE0C2BD),
                        contentColor = accentColor
                    )
                ) {
                    Text(
                        "Подтвердить",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}