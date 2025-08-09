package com.yourname.mdlbapp

import BabyRulesScreen
import androidx.compose.material3.TextButton
import java.time.format.TextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yourname.mdlbapp.ui.theme.MDLBAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.rememberCoroutineScope
import com.yourname.mdlbapp.authorization.AuthScreen
import com.yourname.mdlbapp.authorization.PairCodeScreenBaby
import com.yourname.mdlbapp.authorization.PairCodeScreenMommy
import com.yourname.mdlbapp.authorization.RoleSelectionScreen
import com.yourname.mdlbapp.core.Constants
import com.yourname.mdlbapp.habits.BabyHabitCard
import com.yourname.mdlbapp.habits.background.HabitUpdateScheduler
import com.yourname.mdlbapp.habits.MommyHabitCard
import com.yourname.mdlbapp.mainScreens.BabyScreen
import com.yourname.mdlbapp.mainScreens.MommyScreen
import com.yourname.mdlbapp.reactions.ReactionImage
import com.yourname.mdlbapp.reactions.ReactionOverlay
import com.yourname.mdlbapp.reward.Reward
import com.yourname.mdlbapp.reward.changePoints
import com.yourname.mdlbapp.reward.changePointsAsync
import com.yourname.mdlbapp.rule.CreateRuleScreen
import com.yourname.mdlbapp.rule.Rule
import com.yourname.mdlbapp.rule.RuleCard
import com.yourname.mdlbapp.rule.RulesListScreen
import com.yourname.mdlbapp.rule.RulesScreen
import com.yourname.mdlbapp.rule.computeNextDueDateForHabit
import com.yourname.mdlbapp.rule.getNextDueDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        HabitUpdateScheduler.scheduleNext(this)

        enableEdgeToEdge()

        setContent {
            MDLBAppTheme {
                val navController = rememberNavController()

                val startDestination = remember { mutableStateOf("loading") }

                LaunchedEffect(Unit) {
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        Firebase.firestore.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                val role = doc.getString("role")
                                val pairedWith = doc.getString("pairedWith")
                                if (role != null && !pairedWith.isNullOrEmpty()) {
                                    startDestination.value = when (role) {
                                        "Mommy" -> Screen.Mommy.route
                                        "Baby" -> Screen.Baby.route
                                        else -> Screen.RoleSelection.route
                                    }
                                } else {
                                    startDestination.value = when (role) {
                                        "Mommy" -> "pair_mommy"
                                        "Baby" -> "pair_baby"
                                        else -> Screen.RoleSelection.route
                                    }
                                }
                            }.addOnFailureListener {
                                startDestination.value = Screen.RoleSelection.route
                            }
                    } else {
                        startDestination.value = Screen.RoleSelection.route
                    }
                }

                if (startDestination.value != "loading") {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination.value
                    ) {
                        composable(Screen.RoleSelection.route) {
                            RoleSelectionScreen(navController)
                        }
                        composable(Screen.Baby.route) {
                            BabyScreen(navController)
                        }
                        composable("auth_mommy") {
                            AuthScreen(navController = navController, preselectedRole = "Mommy")
                        }
                        composable("auth_baby") {
                            AuthScreen(navController = navController, preselectedRole = "Baby")
                        }
                        composable("pair_mommy") {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid != null) {
                                PairCodeScreenMommy(uid, navController)
                            } else {
                                Text("Ошибка UID")
                            }
                        }
                        composable("pair_baby") {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid != null) PairCodeScreenBaby(
                                uid,
                                navController
                            ) else Text("Ошибка UID")
                        }
                        composable(Screen.Mommy.route) {
                            MommyScreen(navController)
                        }
                        composable("rules_screen") {
                            val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            var babyUid by remember { mutableStateOf<String?>(null) }

                            LaunchedEffect(mommyUid) {
                                Firebase.firestore.collection("users")
                                    .document(mommyUid)
                                    .get()
                                    .addOnSuccessListener { doc ->
                                        babyUid = doc.getString("pairedWith")
                                    }
                            }

                            babyUid?.let {
                                RulesScreen(navController = navController, mommyUid = mommyUid, it)
                            } ?: run {
                                // Пока не загрузился UID малыша — показываем заглушку
                                Text("Загрузка UID малыша...")
                            }


                        }
                        composable("mommy_rules") {
                            RulesListScreen(navController)
                        }
                        composable("baby_rules") {
                            BabyRulesScreen(navController)
                        }
                        // Магазин наград для малыша
                        composable("baby_rewards") {
                            BabyRewardsScreen(navController)
                        }
                        composable("create_rule") {
                            val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            var babyUid by remember { mutableStateOf<String?>(null) }

                            LaunchedEffect(mommyUid) {
                                Firebase.firestore.collection("users")
                                    .document(mommyUid)
                                    .get()
                                    .addOnSuccessListener { doc ->
                                        babyUid = doc.getString("pairedWith")
                                    }
                            }

                            babyUid?.let {
                                CreateRuleScreen(navController, mommyUid, it)
                            } ?: run {
                                // Пока не загрузился UID малыша — показываем заглушку
                                Text("Загрузка UID малыша...")
                            }
                        }
                        composable("edit_rule/{ruleId}") { backStackEntry ->
                            val ruleId = backStackEntry.arguments?.getString("ruleId") ?: ""
                            EditRuleScreen(navController, ruleId)
                        }
                        composable("habits_screen") { HabitsScreen(navController) }
                        composable("create_habit") {
                            CreateHabitScreen(navController)
                        }
                        composable("baby_habits") {
                            BabyHabitsScreen(navController)
                        }
                        composable("edit_habit/{habitId}") { backStackEntry ->
                            val habitId = backStackEntry.arguments?.getString("habitId") ?: ""
                            EditHabitScreen(navController, habitId)
                        }
                        composable("mommy_rewards") {
                            RewardsListScreen(navController)
                        }
                        composable("create_reward") {
                            val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            var babyUid by remember { mutableStateOf<String?>(null) }

                            LaunchedEffect(mommyUid) {
                                Firebase.firestore.collection("users")
                                    .document(mommyUid)
                                    .get()
                                    .addOnSuccessListener { doc ->
                                        babyUid = doc.getString("pairedWith")
                                    }
                            }

                            babyUid?.let {
                                CreateRewardScreen(navController, mommyUid, it)
                            } ?: run {
                                // Пока не загрузился UID малыша — показываем заглушку
                                Text("Загрузка UID малыша...")
                            }
                        }
                        composable("edit_reward/{rewardId}") { backStackEntry ->
                            val rewardId = backStackEntry.arguments?.getString("rewardId") ?: ""
                            EditRewardScreen(navController, rewardId)
                        }
                    }
                }
            }
        }
    }
}

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
                                    changePoints(babyUid, -selectedReward.cost)
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

@Composable
fun BabyRewardCard(
    reward: Reward,
    totalPoints: Int,
    babyUid: String,
    onBuy: (Reward) -> Unit
) {
    val canAfford = totalPoints >= reward.cost
    val isPending = reward.pending && reward.pendingBy == babyUid
    // Box с общей стилистикой, совпадающей с картой Мамочки
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFDEBEB5), RoundedCornerShape(12.dp))
            .background(Color(0xFFFDF2EC), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reward.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF552216)
                    )
                    Text(
                        text = "${reward.cost} баллов • ${reward.type}",
                        fontSize = 16.sp,
                        color = Color(0xFF552216),
                        fontStyle = FontStyle.Italic
                    )
                }

                // Показываем кнопку или статус ожидания
                if (isPending) {
                    Text(
                        text = "Ожидает подтверждения",
                        color = Color.Red,
                        fontStyle = FontStyle.Italic,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                } else {
                    Button(
                        onClick = {
                            // Покупка: если авто-подтверждение, просто списываем баллы. Иначе выставляем pending.
                            onBuy(reward)
                            if (!reward.autoApprove) {
                                reward.id?.let { rid ->
                                    val updates = mapOf(
                                        "pending" to true,
                                        "pendingBy" to babyUid
                                    )
                                    Firebase.firestore.collection("rewards").document(rid).update(updates)
                                }
                            }
                        },
                        enabled = canAfford,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) Color(0xFFF5D8CE) else Color(0xFFDFDFDF)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (canAfford) "Купить" else "Нет баллов",
                            color = Color(0xFF552216),
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = reward.description,
                fontSize = 18.sp,
                color = Color(0xFF4A3C36),
                fontStyle = FontStyle.Italic
            )

            if (reward.messageFromMommy.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Сообщение: ${'$'}{reward.messageFromMommy}",
                    fontSize = 14.sp,
                    color = Color(0xFF795548),
                    fontStyle = FontStyle.Italic
                )
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryDropdown(
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val categories = Constants.RuleCategories

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedCategory,
            onValueChange = {},
            readOnly = true,
            label = { Text("Категория правила") },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFFFF4F0),
                unfocusedContainerColor = Color(0xFFFFF4F0),
                focusedBorderColor = Color(0xFF552216),
                unfocusedBorderColor = Color(0xFF552216),
                focusedTextColor = Color(0xFF552216),
                unfocusedTextColor = Color(0xFF552216),
                focusedLabelColor = Color(0xFF552216),
                unfocusedLabelColor = Color(0xFF552216)
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category) },
                    onClick = {
                        onCategorySelected(category)
                        expanded = false
                    }
                )
            }
        }
    }
}




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

@Composable
fun RuleInputField(label: String, value: String, onValueChange: (String) -> Unit, iconId: Int, placeholder: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(id = iconId),
            contentDescription = null,
            modifier = Modifier
                .size(20.dp)
                .padding(end = 6.dp),
            tint = Color.Unspecified
        )
        Text(
            text = label,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF461E1B)
        )
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                placeholder,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF421B14)
            )
        },
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun HabitsScreen(navController: NavController) {
    val backgroundColor = Color(0xFFF5EAE3)
    val textColor = Color(0xFF53291E)
    val textColorMain = Color(0xFF000000)

    val habits = remember { mutableStateListOf<Map<String, Any>>() }

    var showInactive by remember { mutableStateOf(false) }

    var showFinished by remember { mutableStateOf(false) }

    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("habits")
            .whereEqualTo("mommyUid", mommyUid)
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                habits.clear()
                snapshots?.documents?.forEach { doc ->
                    val data = doc.data
                    if (data != null) {
                        habits.add(data + ("id" to doc.id))
                    }
                }
            }
    }

    // ▶ 1) Отбираем разовые с дедлайном в прошлом
    val today = LocalDate.now()
    val finishedOnceHabits = habits.filter { habit ->
        val repeat = habit["repeat"] as? String ?: "daily"
        if (repeat == "once") {
            val ds = (habit["nextDueDate"] as? String).orEmpty()
            val d = runCatching { LocalDate.parse(ds) }.getOrNull()
            d != null && d.isBefore(today)
        } else false
    }

    // ▶ 2) Остальные привычки (повторяющиеся + разовые c дедлайном ≥ today)
    val otherHabits = habits - finishedOnceHabits

    // ▶ 3) Из остальных: активные и отключённые
    val (activeHabits, inactiveHabits) = otherHabits.partition {
        val status = it["status"] as? String ?: "off"
        status == "on"
    }

    // ▶ 4) Группировка активных (они все due, потому что просроченные once уже вынесены)
    val groupedHabits = activeHabits
        .mapNotNull { habit ->
            val ds = habit["nextDueDate"] as? String ?: return@mapNotNull null
            val d = runCatching { LocalDate.parse(ds) }.getOrNull() ?: return@mapNotNull null
            d to habit
        }
        .groupBy({ it.first }, { it.second })
        .toSortedMap()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 🔺 Верхняя строка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_filter),
                contentDescription = "Фильтр",
                tint = textColorMain,
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = "Привычки вашего мальчика",
                fontSize = 20.sp,
                color = textColorMain,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = "Назад",
                tint = textColorMain,
                modifier = Modifier
                    .size(55.dp)
                    .clickable { navController.popBackStack() }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 120.dp), // ⬅ пространство под кнопку
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groupedHabits.forEach { (date, habitsForDate) ->
                        val dateLabel = formatDateLabel(date.toString())

                        item {
                            Text(
                                text = dateLabel,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(habitsForDate) { habit ->
                            MommyHabitCard(habit, navController)
                        }
                    }

                    // === 2) Завершённые одноразовые ===
                    if (finishedOnceHabits.isNotEmpty()) {
                        // заголовок сворачиваемого списка
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showFinished = !showFinished }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✅ Завершённые привычки",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showFinished)
                                        Icons.Default.KeyboardArrowUp
                                    else
                                        Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }
                        // сам список в AnimatedVisibility
                        item {
                            AnimatedVisibility(
                                visible = showFinished,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    finishedOnceHabits.forEach { habit ->
                                        MommyHabitCard(habit, navController)
                                    }
                                }
                            }
                        }
                    }


                    if (inactiveHabits.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showInactive = !showInactive }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⛔️ Отключённые привычки",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (showInactive) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color.Gray
                                )
                            }
                        }

                        item {
                            AnimatedVisibility(
                                visible = showInactive,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    inactiveHabits.forEach { habit ->
                                        MommyHabitCard(habit, navController)
                                    }
                                }
                            }
                        }
                    }
                }
            }

// 🔒 Зафиксированная кнопка
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color(0xFFF5EAE3), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column {
                    OutlinedButton(
                        onClick = { navController.navigate("create_habit") },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFDF2EC)),
                        border = BorderStroke(1.dp, Color(0xFFE0C2BD)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            "＋ Новая привычка",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = textColor
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Привычки для любимого нижнего :)",
                        fontSize = 16.sp,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

fun getLastKnownDate(context: Context): LocalDate? {
    val prefs = context.getSharedPreferences("habit_prefs", Context.MODE_PRIVATE)
    return prefs.getString("last_date", null)?.let {
        try {
            LocalDate.parse(it)
        } catch (e: Exception) {
            null
        }
    }
}

fun updateHabitsNextDueDate(
    habits: List<Map<String, Any?>>,
    fromDate: LocalDate = LocalDate.now(),
) {
    val db       = Firebase.firestore
    val isoFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val nowTime  = LocalTime.now()

    habits.forEach { habit ->
        val habitId     = habit["id"] as? String ?: return@forEach
        val repeat      = habit["repeat"] as? String ?: "daily"
        val daysOfWeek  = habit["daysOfWeek"] as? List<String>
        val oneTimeDate = habit["oneTimeDate"] as? String
        val deadlineStr = habit["deadline"] as? String
        val completed   = habit["completedToday"] as? Boolean ?: false

        if (repeat == "once") {
            val parsed = runCatching { LocalDate.parse(oneTimeDate) }.getOrNull()
            val updates = mutableMapOf<String, Any>()
            if (parsed != null) {
                if (parsed.isBefore(LocalDate.now())) {
                    // Дата прошла — отключаем привычку, сбрасываем daily-флаг и «горячие дни»
                    updates["status"]         = "off"
                    updates["completedToday"] = false
                    updates["currentStreak"]  = 0L      // ← сброс серии
                } else {
                    // Если дата ещё не наступила — просто запланировать nextDueDate и сбросить completedToday
                    updates["nextDueDate"]    = parsed.format(isoFmt)
                    updates["completedToday"] = false
                }
            }
            if (updates.isNotEmpty()) {
                db.collection("habits")
                    .document(habitId)
                    .update(updates)
            }
            return@forEach
        }

        val newDueDate = computeNextDueDateForHabit(
            repeatMode = repeat,
            daysOfWeek = daysOfWeek,
            oneTimeDate = oneTimeDate,
            deadline = deadlineStr,
            fromDate = fromDate,
            nowTime = nowTime
        ) ?: return@forEach

        val oldDateStr = habit["nextDueDate"] as? String
        val oldDate    = oldDateStr?.let { LocalDate.parse(it, isoFmt) }

        val updates = mutableMapOf<String, Any>()

        // если дата сменилась — обновим её и сбросим completedToday
        if (oldDate != newDueDate) {
            updates["nextDueDate"] = newDueDate.format(isoFmt)
            updates["completedToday"] = false
        }

        // если предыдущий due-дата уже в прошлом и не была выполнена — сбрасываем серию
        // и применяем штраф к общему счёту малыша
        if (oldDate != null && oldDate.isBefore(fromDate) && !completed) {
            updates["currentStreak"] = 0L
            val babyUid   = habit["babyUid"] as? String
            val penaltyVal = (habit["penalty"] as? Long ?: 0L).toInt()
            if (babyUid != null && penaltyVal != 0) {
                try {
                    changePointsAsync(babyUid, penaltyVal)
                } catch (_: Exception) {
                    // ошибки игнорируем
                }
            }
        }

        // если есть что обновлять — шлём в Firebase
        if (updates.isNotEmpty()) {
            db.collection("habits")
                .document(habitId)
                .update(updates)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EmojiPickerGrid(
    items: List<Int>,
    onSelect: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement   = Arrangement.spacedBy(8.dp),
        modifier              = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
    ) {
        items(items) { resId ->
            ReactionImage(
                resId = resId,
                modifier = Modifier
                    .size(48.dp)
                    .clickable { onSelect(resId) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateHabitScreen(navController: NavController) {
    val backgroundColor = Color(0xFFFDE9DD)
    val textColor = Color(0xFF53291E)

    var title by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("once") }
    var reportType by remember { mutableStateOf("none") }
    var category by remember { mutableStateOf("Дисциплина") }
    var points by remember { mutableStateOf(5) }
    var penalty by remember { mutableStateOf(-5) }
    var reaction by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("off") }

    val repeatOptions = listOf("Каждый день" to "daily", "По дням недели" to "weekly", "Один раз" to "once")
    val reportOptions = listOf("Нет отчета" to "none", "Текст" to "text", "Фото" to "photo", "Аудио" to "audio", "Видео" to "video")
    val categories = listOf("Дисциплина", "Забота", "Поведение", "Настроение")

    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var time by remember { mutableStateOf<Calendar?>(null) }

    val selectedDays = remember { mutableStateListOf<String>() }
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    var selectedSingleDay by remember { mutableStateOf<String?>(null) }

    var babyUid by remember { mutableStateOf<String?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInactiveDialog by remember { mutableStateOf(false) }


    var reactionImageRes by remember { mutableStateOf<Int?>(null) }

    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiRes: List<Int> = remember {
        R.drawable::class.java
            .declaredFields
            .filter { it.name.startsWith("emo_") }
            .mapNotNull {
                runCatching { it.getInt(null) }.getOrNull()
            }
    }

    LaunchedEffect(Unit) {
        val mommyUid = FirebaseAuth.getInstance().currentUser?.uid
        if (mommyUid != null) {
            Firebase.firestore.collection("users").document(mommyUid)
                .get()
                .addOnSuccessListener { doc ->
                    babyUid = doc.getString("pairedWith")
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState) // ⬅ Прокрутка
            .background(backgroundColor)
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        // Заголовок и назад
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDCD3 Создание привычки",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() },
                tint = textColor
            )
        }

        Text(
            text = "Придумайте то, что станет частью дисциплины вашего мальчика…",
            fontSize = 17.sp,
            color = textColor
        )

        Text(
            text = "📍 Название привычки",
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            fontSize = 16.sp
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(12.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = "\uD83D\uDCCD", // 📍
                fontSize = 14.sp,
                modifier = Modifier.padding(end = 6.dp),
                color = textColor
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                if (title.isEmpty()) {
                    Text(
                        text = "Занятие английским", // 👈 placeholder
                        fontSize = 14.sp,
                        color = textColor.copy(alpha = 0.5f) // полупрозрачный
                    )
                }

                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 14.sp,
                        color = textColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Повторяемость
        Text(
            text = "\uD83D\uDD17 Повторяемость",
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = textColor
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0C2BD), // цвет рамки
                    shape = RoundedCornerShape(20.dp) // скругление углов
                )
                .background(
                    color = Color(0xFFF9E3D6), // фон внутри рамки
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(12.dp) // внутренние отступы рамки
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                repeatOptions.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        RadioButton(
                            selected = repeat == value,
                            onClick = { repeat = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = textColor,
                                unselectedColor = textColor
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = label, fontSize = 15.sp, color = textColor)
                    }
                }
                if (repeat == "weekly") {
                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        weekDays.forEach { day ->
                            val isSelected = selectedDays.contains(day)
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) textColor else Color.Transparent)
                                    .border(1.dp, textColor, RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (isSelected) selectedDays.remove(day)
                                        else selectedDays.add(day)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = day,
                                    color = if (isSelected) Color.White else textColor,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        val daysOfWeek = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

        if (repeat == "once") {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                daysOfWeek.forEach { day ->
                    val isSelected = selectedSingleDay == day
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) textColor else Color.Transparent)
                            .border(1.dp, textColor, RoundedCornerShape(12.dp))
                            .clickable {
                                selectedSingleDay = if (isSelected) null else day
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = day,
                            fontSize = 14.sp,
                            color = if (isSelected) Color.White else textColor
                        )
                    }
                }
            }
        }

        Text(
            text = if (time == null) "\uD83D\uDD52 Время не выбрано" else "\uD83D\uDD52 ${timeFormatter.format(time!!.time)}",
            color = textColor,
            fontSize = 16.sp,
            modifier = Modifier
                .clickable {
                    val now = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour: Int, minute: Int ->
                            time = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                            }
                        },
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        true
                    ).show()
                }
                .padding(vertical = 8.dp)
        )


        // Тип отчета
        Text("\uD83D\uDCC3 Тип отчета", fontWeight = FontWeight.SemiBold, color = textColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 1.dp)
                .border(
                    width = 1.dp,
                    color = Color(0xFFE0C2BD),
                    shape = RoundedCornerShape(20.dp)
                )
                .background(
                    color = Color(0xFFF9E3D6),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                reportOptions.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        RadioButton(
                            selected = reportType == value,
                            onClick = { reportType = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(
                                selectedColor = textColor,
                                unselectedColor = textColor
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = label,
                            fontSize = 15.sp,
                            color = textColor
                        )
                    }
                }
            }
        }






        // Категория
        Text("\uD83D\uDCC2 Категория привычки (Вып список)", fontWeight = FontWeight.SemiBold, color = textColor)
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        category = it
                        expanded = false
                    })
                }
            }
        }

        // Баллы за выполнение
        Text("\uD83D\uDCCA Баллы за выполнение", fontWeight = FontWeight.SemiBold, color = textColor)

        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .height(36.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { if (points > 0) points-- },
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = "+ $points",
                    fontSize = 16.sp,
                    color = textColor
                )
                Text(
                    text = "+",
                    modifier = Modifier.clickable { if (points < 100) points++ },
                    fontSize = 18.sp,
                    color = textColor
                )
            }
        }

        // Баллы за пропуск
        Text("\uD83D\uDCCA Баллы за пропуск", fontWeight = FontWeight.SemiBold, color = textColor)
        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .height(36.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { if (penalty > -100) penalty-- },
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = "$penalty",
                    fontSize = 16.sp,
                    color = textColor
                )
                Text(
                    text = "+",
                    modifier = Modifier.clickable { if (penalty < 0) penalty++ },
                    fontSize = 18.sp,
                    color = textColor
                )
            }
        }

        // Реакция Мамочки

        // 1) Заголовок
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp) // Немного отступов
        ) {
            Text("Выбор реакции", fontWeight = FontWeight.SemiBold, color = textColor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (reactionImageRes != null) {
                    ReactionImage(
                        resId = reactionImageRes!!,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterStart)
                    )
                } else {
                    Text(
                        text = "Не выбрано",
                        color = textColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { showEmojiPicker = true }
                )
            }
        }

        // 3) Диалог выбора эмодзи:
        if (showEmojiPicker) {
            AlertDialog(
                onDismissRequest = { showEmojiPicker = false },
                title = { Text("Выберите смайлик") },
                text = {
                    EmojiPickerGrid(
                        items = emojiRes
                    ) { selectedRes ->
                        reactionImageRes = selectedRes
                        showEmojiPicker  = false
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEmojiPicker = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        // Сообщение в реакции
        OutlinedTextField(
            value = reaction,
            onValueChange = { reaction = it },
            label = { Text("💬 Сообщение в реакции") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
        )

        // Статус
        Text("\uD83D\uDD11 Статус Привычки", fontWeight = FontWeight.SemiBold, color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = status == "on", onClick = { status = "on" })
                Text("Активно", color = textColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = status == "off", onClick = { status = "off" })
                Text("Временно отключено", color = textColor)
            }
        }

        // Показываем индикатор над всеми полями, если идёт сохранение
        if (isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Кнопка сохранить
        Button(
            onClick = {
                // валидация
                if (title.isBlank()) {
                    errorMessage = "Название привычки не может быть пустым"
                    return@Button
                }

                if (status == "off") {
                    showInactiveDialog = true
                    return@Button
                }

                if (status == "on") {
                    if (repeat == "weekly" && selectedDays.isEmpty()) {
                        errorMessage = "Выберите хотя бы один день недели"
                        return@Button
                    }

                    if (repeat == "once" && selectedSingleDay.isNullOrEmpty()) {
                        errorMessage = "Выберите день для однократной привычки"
                        return@Button
                    }
                }

                // начинаем сохранение
                isSaving = true
                errorMessage = null
                saveHabit(
                    context = context,
                    navController = navController,
                    babyUid = babyUid,
                    title = title,
                    repeat = repeat,
                    selectedDays = selectedDays,
                    selectedSingleDay = selectedSingleDay,
                    time = time,
                    reportType = reportType,
                    category = category,
                    points = points,
                    penalty = penalty,
                    reaction = reaction,
                    reminder = reminder,
                    status = status,
                    reactionImageRes = reactionImageRes,
                ) { success, error ->
                    isSaving = false
                    if (success) {
                        navController.popBackStack()
                    } else {
                        errorMessage = error ?: "Ошибка при добавлении привычки"
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8C8BB))
        ) {
            if (isSaving) {
                Text("Сохранение…", color = textColor)
            } else {
                Text("Сохранить привычку", color = textColor)
            }
        }

        // Ошибка под кнопкой
        errorMessage?.let { msg ->
            Text(
                text = msg,
                color = Color.Red,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        // Диалог для подтверждения отключенного статуса
        if (showInactiveDialog) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showInactiveDialog = false },
                title = {
                    Text(
                        text = "Вы уверены?",
                        fontSize = 20.sp,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF53291E)
                    )
                },
                text = {
                    Text(
                        text = "Привычка останется в статусе «Временно отключено» и не будет отображаться у Малыша.",
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                        color = Color(0xFF53291E)
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showInactiveDialog = false
                            isSaving = true
                            errorMessage = null

                            if (status == "on") {
                                if (repeat == "weekly" && selectedDays.isEmpty()) {
                                    errorMessage = "Выберите хотя бы один день недели"
                                    return@Button
                                }

                                if (repeat == "once" && selectedSingleDay.isNullOrEmpty()) {
                                    errorMessage = "Выберите день для однократной привычки"
                                    return@Button
                                }
                            }

                            saveHabit(
                                context = context,
                                navController = navController,
                                babyUid = babyUid,
                                title = title,
                                repeat = repeat,
                                selectedDays = selectedDays,
                                selectedSingleDay = selectedSingleDay,
                                time = time,
                                reportType = reportType,
                                category = category,
                                points = points,
                                penalty = penalty,
                                reaction = reaction,
                                reminder = reminder,
                                status = status,
                                reactionImageRes = reactionImageRes
                            ) { success, error ->
                                isSaving = false
                                if (success) {
                                    navController.popBackStack()
                                } else {
                                    errorMessage = error ?: "Ошибка при добавлении привычки"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF5D8CE)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Да")
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showInactiveDialog = false },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF53291E)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF53291E))
                    ) {
                        Text("Нет", fontStyle = FontStyle.Italic)
                    }
                },
                containerColor = Color(0xFFFDE9DD),
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}


fun saveHabit(
    context: Context,
    navController: NavController,
    babyUid: String?,
    title: String,
    repeat: String,
    selectedDays: List<String>,
    selectedSingleDay: String?,    // для once передаём сюда дату (например "2025-07-24")
    time: Calendar?,
    reportType: String,
    category: String,
    points: Int,
    penalty: Int,
    reaction: String,
    reminder: String,
    status: String,
    reactionImageRes: Int?,

    onComplete: (success: Boolean, error: String?) -> Unit
) {
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val actualBabyUid = babyUid ?: return
    val today      = LocalDate.now()

    // 1) Для once-режима: конвертим аббревиатуру в ближайшую дату
    val oneTimeIso: String? = if (repeat == "once" && !selectedSingleDay.isNullOrBlank()) {
        // получаем DayOfWeek из нашей мапы
        val targetDow = Constants.ruToDayOfWeek[selectedSingleDay]
        if (targetDow == null) {
            onComplete(false, "Неправильный день для одноразовой привычки")
            return
        }
        // ищем ближайший день (0..6)
        val date = (0L..6L).asSequence()
            .map { today.plusDays(it) }
            .first { it.dayOfWeek == targetDow }
        date.toString()   // ISO: "2025-07-24"
    } else null

    // Формируем строку-дедлайн (HH:mm), default = "23:59"
    val deadlineString = time
        ?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.time) }
        ?: "23:59"

    // Вычисляем nextDueDate через единую функцию
    val nextDueDate = if (status == "on") {
        getNextDueDate(
            repeatMode = repeat,
            daysOfWeek = if (repeat == "weekly") selectedDays else null,
            oneTimeDate = oneTimeIso,
            deadline = deadlineString
        ) ?: run {
            onComplete(false, "Не удалось определить дату следующего выполнения.")
            return
        }
    } else null

    val habit = hashMapOf(
        "title"         to title,
        "repeat"        to repeat,
        "daysOfWeek"    to selectedDays,
        "oneTimeDate"   to oneTimeIso,
        "deadline"      to deadlineString,
        "reportType"    to reportType,
        "category"      to category,
        "points"        to points,
        "penalty"       to penalty,
        "reaction"      to reaction,
        "reminder"      to reminder,
        "status"        to status,
        "mommyUid"      to mommyUid,
        "babyUid"       to actualBabyUid,
        "nextDueDate"   to nextDueDate,
        "completedToday" to false,
        "currentStreak"  to 0,
        "reactionImageRes" to reactionImageRes,
    )

    Firebase.firestore.collection("habits")
        .add(habit)
        .addOnSuccessListener { onComplete(true, null) }
        .addOnFailureListener { e -> onComplete(false, e.localizedMessage) }
}







fun formatDateLabel(dateStr: String): String {
    val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val today = LocalDate.now()
    val tomorrow = today.plusDays(1)
    val date = try {
        LocalDate.parse(dateStr, inputFormatter)
    } catch (e: Exception) {
        return dateStr
    }

    return when {
        date == today -> "Сегодня"
        date == tomorrow -> "Завтра"
        date.isAfter(today.plusDays(1)) && date <= today.with(DayOfWeek.SUNDAY) -> {
            // Только если внутри этой недели
            val dayOfWeek = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale("ru"))
            dayOfWeek.replaceFirstChar { it.uppercaseChar() }
        }
        else -> {
            val formatter = DateTimeFormatter.ofPattern("d MMMM", Locale("ru"))
            date.format(formatter).replaceFirstChar { it.uppercaseChar() }
        }
    }
}







@Composable
fun BabyHabitsScreen(navController: NavController) {
    val habits = remember { mutableStateListOf<Map<String, Any>>() }
    val backgroundColor = Color(0xFFF5EAE3)
    val textColor = Color(0xFF53291E)
    val textColorMain = Color(0xFF000000)

    val context = LocalContext.current

    var showReaction      by remember { mutableStateOf(false) }
    var reactionImageRes  by remember { mutableStateOf<Int?>(null) }
    var reactionMessage   by remember { mutableStateOf("") }
    var earnedPoints      by remember { mutableStateOf(0) }


    // UID Мамочки для фильтрации привычек
    var mommyUid by remember { mutableStateOf<String?>(null) }

    // 1) Загрузка UID Мамочки из документа пользователя малыша
    LaunchedEffect(Unit) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("users").document(babyUid).get()
            .addOnSuccessListener { doc ->
                mommyUid = doc.getString("pairedWith")
            }
    }

    // 2) Подгружаем привычки, выданные текущей Мамочкой
    LaunchedEffect(mommyUid) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        val mUid = mommyUid ?: return@LaunchedEffect
        Firebase.firestore.collection("habits")
            .whereEqualTo("babyUid", babyUid)
            .whereEqualTo("mommyUid", mUid)
            .whereEqualTo("status", "on")
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                habits.clear()
                snapshots?.documents?.forEach { doc ->
                    val data = doc.data
                    if (data != null) {
                        habits.add(data + ("id" to doc.id))
                    }
                }
            }
    }

    // 🔁 Группировка по дате nextDueDate
    val groupedHabits = habits
        .mapNotNull {
            val dateStr = it["nextDueDate"] as? String ?: return@mapNotNull null
            val date = try {
                LocalDate.parse(dateStr)
            } catch (e: Exception) {
                null
            } ?: return@mapNotNull null
            date to it
        }
        .groupBy({ it.first }, { it.second }) // Группируем по LocalDate
        .toSortedMap() // Сортируем по возрастанию даты

    // 1) Авто-скрытие через 3 секунды
    LaunchedEffect(showReaction) {
        if (showReaction) {
            delay(3_000)            // ждём 3 секунды
            showReaction = false    // и скрываем оверлей
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "🧸 Твои привычки",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = textColorMain,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (habits.isEmpty()) {
                Text(
                    text = "Нет заданных привычек.\nЖди указаний Мамочки...",
                    fontStyle = FontStyle.Italic,
                    color = Color.DarkGray,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    groupedHabits.forEach { (date, habitsForDate) ->
                        val dateLabel = formatDateLabel(date.toString()) // форматируем надпись

                        item {
                            Text(
                                text = dateLabel,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }

                        items(habitsForDate) { habit ->
                            // 2) прокидываем onCompleted в карточку
                            BabyHabitCard(
                                habit = habit,
                                onCompleted = {
                                    // 1) Преобразуем res из Long (Firestore) в Int
                                    reactionImageRes =
                                        (habit["reactionImageRes"] as? Long ?: 0L).toInt()
                                    // 2) Текст реакции — в поле "reaction" (или "reactionMessage", смотрите, как назвали вы)
                                    reactionMessage = (habit["reaction"] as? String) ?: ""
                                    // 3) Баллы
                                    earnedPoints = (habit["points"] as? Long ?: 1L).toInt()
                                    // 4) Показываем оверлей
                                    showReaction = true
                                }
                            )
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showReaction && reactionImageRes != null,
            enter   = fadeIn(animationSpec = tween(durationMillis = 500)),
            exit    = fadeOut(animationSpec = tween(durationMillis = 500))
        ) {
            ReactionOverlay(
                resId = reactionImageRes!!,
                message = reactionMessage,
                points = earnedPoints
            ) {
                showReaction = false  // тоже можно тапом закрывать
            }
        }

        // 3) сам оверлей поверх всего
        if (showReaction && reactionImageRes != null) {
            ReactionOverlay(
                resId = reactionImageRes!!,
                message = reactionMessage,
                points = earnedPoints
            ) {
                showReaction = false
            }
        }
    }
}





@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditHabitScreen(navController: NavController, habitId: String) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val dateFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeFormatter = dateFormatter

    val backgroundColor = Color(0xFFFDE9DD)
    val textColor = Color(0xFF53291E)

    val repeatOptions = listOf("Каждый день" to "daily", "По дням недели" to "weekly", "Один раз" to "once")
    val reportOptions = listOf("Нет отчета" to "none", "Текст" to "text", "Фото" to "photo", "Аудио" to "audio", "Видео" to "video")
    val categories = listOf("Дисциплина", "Забота", "Поведение", "Настроение")
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")




    var title by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("once") }
    val selectedDays = remember { mutableStateListOf<String>() }
    var selectedSingleDay by remember { mutableStateOf<String?>(null) }
    var time by remember { mutableStateOf<Calendar?>(null) }
    var reportType by remember { mutableStateOf("none") }
    var category by remember { mutableStateOf("Дисциплина") }
    var points by remember { mutableStateOf(5) }
    var penalty by remember { mutableStateOf(-5) }
    var reaction by remember { mutableStateOf("") }
    var reminder by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("off") }

    var categoryExpanded by remember { mutableStateOf(false) }
    var reactionExpanded by remember { mutableStateOf(false) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var reactionImageRes by remember { mutableStateOf<Int?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    val emojiRes: List<Int> = remember {
        R.drawable::class.java
            .declaredFields
            .filter { it.name.startsWith("emo_") }
            .mapNotNull {
                runCatching { it.getInt(null) }.getOrNull()
            }
    }

    LaunchedEffect(habitId) {
        Firebase.firestore.collection("habits").document(habitId).get()
            .addOnSuccessListener { doc ->
                val data = doc.data
                if (data != null) {
                    title = data["title"] as? String ?: ""
                    repeat = data["repeat"] as? String ?: "once"
                    selectedDays.clear()
                    selectedDays.addAll((data["daysOfWeek"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList())
                    selectedSingleDay = data["oneTimeDate"] as? String
                    data["deadline"]?.let {
                        val cal = Calendar.getInstance()
                        cal.time = dateFormatter.parse(it as String) ?: Date()
                        time = cal
                    }
                    reportType = data["reportType"] as? String ?: "none"
                    category = data["category"] as? String ?: "Дисциплина"
                    points = (data["points"] as? Long)?.toInt() ?: 5
                    penalty = (data["penalty"] as? Long)?.toInt() ?: -5
                    reaction = data["reaction"] as? String ?: ""
                    reminder = data["reminder"] as? String ?: ""
                    status = data["status"] as? String ?: "off"
                    reactionImageRes = (data["reactionImageRes"] as? Long)?.toInt()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Ошибка загрузки привычки", Toast.LENGTH_SHORT).show()
                navController.popBackStack()
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .background(backgroundColor)
            .padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "\uD83D\uDCD3 Редактирование привычки",
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Назад",
                modifier = Modifier
                    .size(32.dp)
                    .clickable { navController.popBackStack() },
                tint = textColor
            )
        }

        // Название привычки
        Text("📍 Название привычки", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textColor)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(12.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp)
        ) {
            Text("📍", fontSize = 14.sp, modifier = Modifier.padding(end = 6.dp), color = textColor)
            Box(modifier = Modifier.fillMaxWidth()) {
                if (title.isEmpty()) {
                    Text("Занятие английским", fontSize = 14.sp, color = textColor.copy(alpha = 0.5f))
                }
                BasicTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp, color = textColor),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Повторяемость
        Text("🔗 Повторяемость", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = textColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeatOptions.forEach { (label, value) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        RadioButton(
                            selected = repeat == value,
                            onClick = { repeat = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(selectedColor = textColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, fontSize = 15.sp, color = textColor)
                    }
                }
            }

            if (repeat == "weekly") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    weekDays.forEach { day ->
                        val isSelected = selectedDays.contains(day)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) textColor else Color.Transparent)
                                .border(1.dp, textColor, RoundedCornerShape(12.dp))
                                .clickable {
                                    if (isSelected) selectedDays.remove(day)
                                    else selectedDays.add(day)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(day, fontSize = 14.sp, color = if (isSelected) Color.White else textColor)
                        }
                    }
                }
            }

            if (repeat == "once") {
                OutlinedTextField(
                    value = selectedSingleDay ?: "",
                    onValueChange = { selectedSingleDay = it },
                    label = { Text("Дата выполнения (dd-MM-yyyy)", color = textColor) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Время
        Text(
            text = if (time == null) "\uD83D\uDD52 Время не выбрано" else "\uD83D\uDD52 ${timeFormatter.format(time!!.time)}",
            fontSize = 16.sp,
            color = textColor,
            modifier = Modifier
                .clickable {
                    val now = Calendar.getInstance()
                    TimePickerDialog(
                        context,
                        { _, hour, minute ->
                            time = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, hour)
                                set(Calendar.MINUTE, minute)
                            }
                        },
                        now.get(Calendar.HOUR_OF_DAY),
                        now.get(Calendar.MINUTE),
                        true
                    ).show()
                }
                .padding(vertical = 8.dp)
        )

        // Тип отчета
        Text("📝 Тип отчёта", fontWeight = FontWeight.SemiBold, color = textColor)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp))
                .padding(12.dp)
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                reportOptions.forEach { (label, value) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = reportType == value,
                            onClick = { reportType = value },
                            modifier = Modifier.size(16.dp),
                            colors = RadioButtonDefaults.colors(selectedColor = textColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(label, fontSize = 15.sp, color = textColor)
                    }
                }
            }
        }

        // Категория
        Text("📂 Категория", fontWeight = FontWeight.SemiBold, color = textColor)
        ExposedDropdownMenuBox(expanded = categoryExpanded, onExpandedChange = { categoryExpanded = !categoryExpanded }) {
            OutlinedTextField(
                value = category,
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
            )
            ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                categories.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        category = it
                        categoryExpanded = false
                    })
                }
            }
        }

        // Баллы за выполнение
        Text("\uD83D\uDCCA Баллы за выполнение", fontWeight = FontWeight.SemiBold, color = textColor)

        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .height(36.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { if (points > 0) points-- },
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = "+ $points",
                    fontSize = 16.sp,
                    color = textColor
                )
                Text(
                    text = "+",
                    modifier = Modifier.clickable { if (points < 100) points++ },
                    fontSize = 18.sp,
                    color = textColor
                )
            }
        }

        // Баллы за пропуск
        Text("\uD83D\uDCCA Баллы за пропуск", fontWeight = FontWeight.SemiBold, color = textColor)
        Box(
            modifier = Modifier
                .widthIn(max = 200.dp)
                .height(36.dp)
                .border(1.dp, Color(0xFFE0C2BD), RoundedCornerShape(20.dp))
                .background(Color(0xFFF9E3D6), RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            ) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { if (penalty > -100) penalty-- },
                    fontSize = 18.sp,
                    color = textColor
                )
                Text(
                    text = "$penalty",
                    fontSize = 16.sp,
                    color = textColor
                )
                Text(
                    text = "+",
                    modifier = Modifier.clickable { if (penalty < 0) penalty++ },
                    fontSize = 18.sp,
                    color = textColor
                )
            }
        }

        // Реакция Мамочки

        // 1) Заголовок
        Column(
            horizontalAlignment = Alignment.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp) // Немного отступов
        ) {
            Text("Выбор реакции", fontWeight = FontWeight.SemiBold, color = textColor)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (reactionImageRes != null) {
                    ReactionImage(
                        resId = reactionImageRes!!,
                        modifier = Modifier
                            .size(48.dp)
                            .align(Alignment.CenterStart)
                    )
                } else {
                    Text(
                        text = "Не выбрано",
                        color = textColor.copy(alpha = 0.5f),
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { showEmojiPicker = true }
                )
            }
        }

        // 3) Диалог выбора эмодзи:
        if (showEmojiPicker) {
            AlertDialog(
                onDismissRequest = { showEmojiPicker = false },
                title = { Text("Выберите смайлик") },
                text = {
                    EmojiPickerGrid(
                        items = emojiRes
                    ) { selectedRes ->
                        reactionImageRes = selectedRes
                        showEmojiPicker  = false
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEmojiPicker = false }) {
                        Text("Отмена")
                    }
                }
            )
        }

        OutlinedTextField(
            value = reaction,
            onValueChange = { reaction = it },
            label = { Text("💬 Сообщение в реакции") },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
        )

        // Статус
        Text("🔓 Статус привычки", fontWeight = FontWeight.SemiBold, color = textColor)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = status == "on", onClick = { status = "on" })
                Text("Активно", color = textColor)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = status == "off", onClick = { status = "off" })
                Text("Временно отключено", color = textColor)
            }
        }

        var isSaving by remember { mutableStateOf(false) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

// Показываем индикатор над кнопками, если идёт сохранение
        if (isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
        }

// Кнопки
        Button(
            onClick = {
                if (title.isBlank()) {
                    errorMessage = "Название не может быть пустым"
                    return@Button
                }

                isSaving = true
                errorMessage = null

                val deadline = time?.let { dateFormatter.format(it.time) } ?: "23:59"
                val updated = mapOf(
                    "title" to title,
                    "repeat" to repeat,
                    "daysOfWeek" to selectedDays.toList(),
                    "oneTimeDate" to selectedSingleDay,
                    "deadline" to deadline,
                    "reportType" to reportType,
                    "category" to category,
                    "points" to points,
                    "penalty" to penalty,
                    "reminder" to reminder,
                    "status" to status,
                    "reaction" to reaction,
                    "reactionImageRes" to reactionImageRes
                )
                Firebase.firestore.collection("habits").document(habitId)
                    .update(updated)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Изменения сохранены", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Ошибка при сохранении", Toast.LENGTH_SHORT).show()
                        isSaving = false
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8C8BB))
        ) {
            Text(
                if (isSaving) "Сохранение…" else "Сохранить",
                color = textColor
            )
        }

        OutlinedButton(
            onClick = {
                Firebase.firestore.collection("habits").document(habitId).delete()
                    .addOnSuccessListener {
                        Toast.makeText(context, "Удалено", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Не удалось удалить", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, Color.Red),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Text("Удалить привычку")
        }
    }
}

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

@Composable
fun RewardCard(
    reward: Reward,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFDEBEB5), RoundedCornerShape(12.dp))
            .background(Color(0xFFFDF2EC), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Column {
            // Заголовок и стоимость
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = reward.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = Color(0xFF552216)
                    )
                    Text(
                        text = "${reward.cost} баллов • ${reward.type}",
                        fontSize = 16.sp,
                        color = Color(0xFF552216),
                        fontStyle = FontStyle.Italic
                    )
                }
                // Кнопка удаления доступна только если нет ожидающей заявки
                if (!reward.pending) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Удалить",
                            tint = Color(0xFF552216)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Описание
            Text(
                text = reward.description,
                fontSize = 18.sp,
                color = Color(0xFF4A3C36),
                fontStyle = FontStyle.Italic
            )

            // Если есть сообщение от Мамочки – показываем его
            if (reward.messageFromMommy.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Сообщение: ${'$'}{reward.messageFromMommy}",
                    fontSize = 14.sp,
                    color = Color(0xFF795548),
                    fontStyle = FontStyle.Italic
                )
            }

            // Если награда ожидает подтверждения
            if (reward.pending && reward.pendingBy != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Запрос от малыша",
                    fontSize = 14.sp,
                    color = Color.Red,
                    fontStyle = FontStyle.Italic
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Кнопки подтверждения и отказа
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (onApprove != null) {
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0C3B1)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Подтвердить", color = Color(0xFF552216))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (onReject != null) {
                        OutlinedButton(
                            onClick = onReject,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0C3B1))
                        ) {
                            Text(
                                text = "Отказать",
                                color = Color(0xFF552216),
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
            } else {
                // Если нет заявки – кнопка редактирования
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onEdit) {
                        Text(
                            text = "Изменить",
                            color = Color(0xFF552216),
                            fontSize = 16.sp,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
    }
}

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



@Composable
fun EditRewardScreen(navController: NavController, rewardId: String) {
    // TODO: реализовать позже
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardTypeDropdown(selectedOption: String, onOptionSelected: (String) -> Unit) {
    val options = listOf("Контентная", "Поведенческая", "Эмоциональная", "Привилегия")
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption,
            onValueChange = {},
            readOnly = true,
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}