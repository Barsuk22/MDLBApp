package com.app.mdlbapp.reward

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardsListScreen(navController: NavController) {
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val rewards = remember { mutableStateListOf<Reward>() }

    // 1) Состояния для babyUid и totalPoints
    var babyUid by remember { mutableStateOf<String?>(null) }
    var totalPoints by remember { mutableStateOf(0) }

    val scope = rememberCoroutineScope()

    // Локальный дельтик. Каждый раз, когда пришёл новый totalPoints из снапшота,
    // мы сбрасываем localDelta в 0, чтобы не "уезжать".
    var localDelta by remember(totalPoints) { androidx.compose.runtime.mutableIntStateOf(0) }
    val shownPoints = (totalPoints + localDelta).coerceAtLeast(0)

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
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val bid = babyUid!! // тут уже не null

                    // МИНУС — мгновенно уменьшаем в UI и отправляем транзакцию
                    IconButton(onClick = {
                        if (shownPoints == 0) return@IconButton
                        localDelta -= 1
                        scope.launch {
                            try {
                                changePoints(bid, -1) // транзакция с await, не даёт уйти < 0
                            } catch (e: Exception) {
                                // откатим локально и можно показать тост
                                localDelta += 1
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Remove, contentDescription = "Минус 1")
                    }

                    Text(
                        text = "Всего баллов: $shownPoints 🪙",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF552216)
                    )

                    // ПЛЮС — мгновенно увеличиваем в UI и отправляем транзакцию
                    IconButton(onClick = {
                        localDelta += 1
                        scope.launch {
                            try {
                                changePoints(bid, +1)
                            } catch (e: Exception) {
                                localDelta -= 1
                            }
                        }
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "Плюс 1")
                    }
                }
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

                                Firebase.firestore.collection("rewards").document(rid)
                                    .collection("rewardLogs")
                                    .add(
                                        mapOf(
                                            "status" to "approved",
                                            "pointsDelta" to 0,                 // очки не меняем при approve
                                            "source" to "mom-approve",
                                            "at" to FieldValue.serverTimestamp(),
                                            "rewardId" to rid,
                                            "rewardTitle" to reward.title,
                                            "mommyUid" to reward.createdBy,     // теперь есть в Reward
                                            "babyUid" to reward.targetUid
                                        )
                                    )
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

                                Firebase.firestore.collection("rewards").document(rid)
                                    .collection("rewardLogs")
                                    .add(
                                        mapOf(
                                            "status" to "rejected",
                                            "pointsDelta" to +reward.cost,
                                            "source" to "mom-reject",
                                            "at" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                                            "rewardId" to rid,
                                            "rewardTitle" to reward.title,
                                            "mommyUid" to reward.createdBy,
                                            "babyUid" to (reward.targetUid.takeIf { !it.isNullOrEmpty() } ?: (reward.pendingBy ?: ""))
                                        )
                                    )
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