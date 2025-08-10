//Файл PairCodeScreenMommy.kt

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
import androidx.compose.material3.Text
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.app.mdlbapp.Screen

@Composable
fun PairCodeScreenMommy(uid: String, navController: NavHostController) {
    val db = Firebase.firestore
    var generatedCode by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Генерация кода — только один раз
    LaunchedEffect(Unit) {
        if (generatedCode == null) {
            val code = (100000..999999).random().toString()
            db.collection("pairCodes")
                .document(code)
                .set(mapOf(
                    "mommyUid" to uid,
                    "createdAt" to System.currentTimeMillis()
                ))
                .addOnSuccessListener { generatedCode = code }
                .addOnFailureListener {
                    Toast.makeText(context, "Ошибка генерации кода", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Реактивный слушатель пары
    DisposableEffect(uid) {
        val registration = db.collection("users")
            .document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val pairedWith = snapshot?.getString("pairedWith")
                if (!pairedWith.isNullOrEmpty()) {
                    navController.navigate(Screen.Mommy.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                }
            }
        onDispose { registration.remove() }
    }

    // Цвета для экрана генерации кода Мамочки
    val backgroundColor = Color(0xFFF8EDE6)
    val cardColor       = Color(0xFFFDE9DD)
    val accentColor     = Color(0xFF552216)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
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
                    text = "Дай этот код своему Малышу",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = generatedCode ?: "...",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Он должен ввести его у себя после входа.",
                    fontSize = 16.sp,
                    color = accentColor
                )
            }
        }
    }
}