package com.app.mdlbapp.reward

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.app.mdlbapp.reward.data.Reward
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

// ===================== BABY REWARDS SCREEN =============================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyRewardsScreen(navController: NavHostController) {
    val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val rewards = remember { mutableStateListOf<Reward>() }
    var totalPoints by remember { mutableStateOf(0) }

    // Состояние для UID Мамочки. Он понадобится для фильтрации наград, чтобы
    // малыш видел только награды, созданные текущей Мамочкой.
    var mommyUid by remember { mutableStateOf<String?>(null) }

    // Загружаем UID Мамочки из документа пользователя малыша
    LaunchedEffect(Unit) {
        Firebase.firestore
            .collection("users")
            .document(babyUid)
            .get()
            .addOnSuccessListener { doc ->
                mommyUid = doc.getString("pairedWith")
            }
    }

    // Подписываемся на награды только после получения mommyUid
    LaunchedEffect(mommyUid) {
        val mUid = mommyUid ?: return@LaunchedEffect
        Firebase.firestore
            .collection("rewards")
            .whereEqualTo("targetUid", babyUid)
            .whereEqualTo("createdBy", mUid)
            .addSnapshotListener { snap, _ ->
                rewards.clear()
                snap?.documents
                    ?.mapNotNull { it.toObject(Reward::class.java)?.apply { id = it.id } }
                    ?.let { rewards.addAll(it) }
            }
    }

    // Подписка на баланс очков малыша
    LaunchedEffect(babyUid) {
        Firebase.firestore
            .collection("points")
            .document(babyUid)
            .addSnapshotListener { snap, _ ->
                totalPoints = snap?.getLong("totalPoints")?.toInt() ?: 0
            }
    }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .padding(12.dp)
    ) {
        TopAppBar(
            title = { Text("Магазин Ласки", fontSize = 26.sp, fontStyle = FontStyle.Italic) },
            navigationIcon = {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                }
            },
            actions = {},
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8EDE6))
        )

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF4E8), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFD9B99B), RoundedCornerShape(12.dp))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Всего баллов: $totalPoints 🪙",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF552216)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rewards) { reward ->
                BabyRewardCard(
                    reward = reward,
                    totalPoints = totalPoints,
                    babyUid = babyUid,
                    onBuy = { selectedReward ->
                        if (totalPoints >= selectedReward.cost) {
                            scope.launch {
                                try {
                                    // 1) списали очки (как было)
                                    changePoints(babyUid, -selectedReward.cost)

                                    // 2) записали лог
                                    val rid = selectedReward.id ?: return@launch
                                    val mUid = mommyUid ?: return@launch
                                    Firebase.firestore
                                        .collection("rewards").document(rid)
                                        .collection("rewardLogs")
                                        .add(
                                            mapOf(
                                                "status" to if (selectedReward.autoApprove) "bought" else "pending",
                                                "pointsDelta" to -selectedReward.cost,
                                                "source" to "buy",
                                                "at" to FieldValue.serverTimestamp(),
                                                "rewardId" to rid,
                                                "rewardTitle" to selectedReward.title,
                                                "mommyUid" to mUid,
                                                "babyUid" to babyUid
                                            )
                                        )
                                } catch (_: Exception) {
                                    Toast.makeText(context, "Не удалось списать баллы", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            Toast.makeText(context, "Недостаточно баллов для покупки", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Выбери награду, когда будет достаточно баллов",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF552216)
        )
    }
}