//файл PairCodeScreenBaby.kt

package com.yourname.mdlbapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@Composable
fun PairCodeScreenBaby(uid: String, navController: NavHostController) {
    val db = Firebase.firestore
    var inputCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Введи код, полученный от Мамочки", fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = inputCode,
            onValueChange = { inputCode = it },
            label = { Text("Код от Мамочки") }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
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
        }) {
            Text("Подтвердить")
        }
    }
}