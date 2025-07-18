//Файл PairCodeScreenMommy.kt

package com.yourname.mdlbapp

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import kotlinx.coroutines.delay

@Composable
fun PairCodeScreenMommy(uid: String, navController: NavHostController) {
    val db = Firebase.firestore
    var generatedCode by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Генерация кода — только один раз
    LaunchedEffect(Unit) {
        if (generatedCode == null) {
            val code = (100000..999999).random().toString()
            val codeRef = db.collection("pairCodes").document(code)

            codeRef.set(mapOf(
                "mommyUid" to uid,
                "createdAt" to System.currentTimeMillis()
            )).addOnSuccessListener {
                generatedCode = code
            }.addOnFailureListener {
                Toast.makeText(context, "Ошибка генерации кода", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔄 Проверка — связался ли Малыш
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000) // каждые 3 секунды
            db.collection("users").document(uid).get()
                .addOnSuccessListener { doc ->
                    val pairedWith = doc.getString("pairedWith")
                    if (!pairedWith.isNullOrEmpty()) {
                        navController.navigate(Screen.Mommy.route) {
                            popUpTo(0) // очищаем стек навигации
                        }
                    }
                }
            // delay в цикле — цикл не блокирует UI
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Дай этот код своему Малышу", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = generatedCode ?: "...",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF552216)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Он должен ввести его у себя после входа.")
    }
}