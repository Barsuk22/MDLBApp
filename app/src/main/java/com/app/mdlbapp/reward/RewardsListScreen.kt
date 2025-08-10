package com.app.mdlbapp.reward

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsListScreen(navController: NavController) {
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val rewards = remember { mutableStateListOf<Reward>() }

    // 1) Состояния для babyUid и totalPoints
    var babyUid by remember { mutableStateOf<String?>(null) }
    var totalPoints by remember { mutableStateOf(0) }

    // 2) Подгружаем список наград как раньше
    LaunchedEffect(mommyUid) {
        // Получаем список наград, созданных Мамочкой. Отказались от сортировки по createdAt,
        // так как комбинация whereEqualTo + orderBy требует индекса в Firestore, которого может не быть.
        Firebase.firestore
            .collection("rewards")
            .whereEqualTo("createdBy", mommyUid)
            .addSnapshotListener { snap, _ ->
                rewards.clear()
                snap?.documents
                    ?.mapNotNull { it.toObject(Reward::class.java)?.apply { id = it.id } }
                    ?.let { rewards.addAll(it) }
            }
    }

    // 3) Один эффект для получения babyUid
    LaunchedEffect(mommyUid) {
        Firebase.firestore
            .collection("users")
            .document(mommyUid)
            .get()
            .addOnSuccessListener { doc ->
                babyUid = doc.getString("pairedWith")
            }
    }

    // 4) Как только babyUid загрузился — подписываемся на points
    LaunchedEffect(babyUid) {
        val bid = babyUid ?: return@LaunchedEffect
        Firebase.firestore
            .collection("points")
            .document(bid)
            .addSnapshotListener { snap, _ ->
                totalPoints = snap?.getLong("totalPoints")?.toInt() ?: 0
            }
    }

    // 5) В самом UI уже только чтение состояний
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
            actions = {
                IconButton(onClick = { navController.navigate("create_reward") }) {
                    Icon(Icons.Default.Add, contentDescription = "Добавить")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFFF8EDE6))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Показываем либо счётчик, либо индикатор загрузки babyUid
        if (babyUid != null) {
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
        } else {
            // пока babyUid ещё не подгрузился
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Загрузка баллов…", fontStyle = FontStyle.Italic, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(rewards) { reward ->
                RewardCard(
                    reward = reward,
                    onEdit = { navController.navigate("edit_reward/${reward.id}") },
                    onDelete = {
                        reward.id?.let { Firebase.firestore.collection("rewards").document(it).delete() }
                    },
                    onApprove = if (reward.pending) {
                        {
                            reward.id?.let { rid ->
                                val updates = mapOf(
                                    "pending" to false,
                                    "pendingBy" to null
                                )
                                Firebase.firestore.collection("rewards").document(rid).update(updates)
                            }
                        }
                    } else null,
                    onReject = if (reward.pending) {
                        {
                            // Возврат баллов малышу
                            val bUid = reward.pendingBy
                            if (bUid != null) {
                                changePointsAsync(bUid, reward.cost)
                            }
                            reward.id?.let { rid ->
                                val updates = mapOf(
                                    "pending" to false,
                                    "pendingBy" to null
                                )
                                Firebase.firestore.collection("rewards").document(rid).update(updates)
                            }
                        }
                    } else null
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { navController.navigate("create_reward") },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("＋ Новая награда", fontSize = 20.sp, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold, color = Color(0xFF552216))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Поощрения для любимого нижнего :)",
            modifier = Modifier.align(Alignment.CenterHorizontally),
            fontSize = 16.sp,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF552216)
        )
    }
}