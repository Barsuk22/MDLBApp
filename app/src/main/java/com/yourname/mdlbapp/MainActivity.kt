package com.yourname.mdlbapp

import java.time.format.TextStyle
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.layout.FlowRow
import com.yourname.mdlbapp.RulesListScreen
import com.yourname.mdlbapp.Rule
import androidx.compose.runtime.*
import androidx.compose.foundation.BorderStroke
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.play.integrity.internal.e
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yourname.mdlbapp.ui.theme.MDLBAppTheme
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
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
                            val babyUid = "вставь UID малыша"
                            RulesScreen(navController = navController, mommyUid = mommyUid, babyUid = babyUid)
                        }
                        composable("mommy_rules") {
                            RulesListScreen(navController)
                        }
                        composable("baby_rules") {
                            BabyRulesScreen(navController)
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
                    }
                }
            }
        }
    }

    @Composable
    fun RoleSelectionScreen(navController: NavHostController) {
        val backgroundColor = Color(0xFFF8EDE6)
        val cardColor = Color(0xFFFFEBEE)
        val textColor = Color.Black

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = backgroundColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Выбери положение!",
                    style = MaterialTheme.typography.headlineLarge,
                    color = textColor
                )

                Spacer(modifier = Modifier.height(40.dp))

                RoleButton(
                    iconId = R.drawable.ic_mommy,
                    text = "Я мамочка!",
                    color = cardColor
                ) {
                    navController.navigate("auth_mommy")
                }

                Spacer(modifier = Modifier.height(24.dp))

                RoleButton(
                    iconId = R.drawable.ic_baby,
                    text = "Я малыш!!!!",
                    color = cardColor
                ) {
                    navController.navigate("auth_baby") // 🔄 и сюда
                }
            }
        }
    }

    //ВЫБОР РОЛИ------------------------------------------------
    @Composable
    fun RoleButton(
        iconId: Int,
        text: String,
        color: Color,
        onClick: () -> Unit
    ) {
        val screenWidth = LocalConfiguration.current.screenWidthDp
        val iconDpSize = (screenWidth * 0.15f).dp  // 🔹 Иконка занимает 15% ширины экрана

        Surface(
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 8.dp,
            color = color,
            modifier = Modifier
                .fillMaxWidth()
                .height((iconDpSize.value + 30).dp) // адаптивная высота
                .clickable { onClick() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                ) {
                    Image(
                        painter = painterResource(id = iconId),
                        contentDescription = null,
                        modifier = Modifier.size(iconDpSize * 0.99f) // сама иконка внутри круга
                    )
                }

                Spacer(modifier = Modifier.width(20.dp))

                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = (iconDpSize.value * 0.55).sp // текст тоже адаптивный
                    )
                )
            }
        }
    }

    //ГЛАВНОЕ ОКНО МАМОЧКИ------------------------------------------------
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun MommyScreen(navController: NavHostController) {

        val items = listOf(
            R.drawable.ic_habit to "Привычки",
            R.drawable.ic_rules to "Правила\nпослушания",
            R.drawable.ic_punishment to "Наказания",
            R.drawable.ic_rewards to "Магазин\nласки",
            R.drawable.ic_chat to "Чат с малышом",
            R.drawable.ic_control to "Контроль\nустройства",
            R.drawable.ic_journal to "Журнал\nПодчинения",
            R.drawable.ic_archive to "Архив\nдоказательств"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8EDE6))
        ) {
            CenterAlignedTopAppBar(
                modifier = Modifier.height(110.dp),
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Пульт Госпожи",
                            fontSize = 29.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Black,
                            fontStyle = FontStyle.Italic
                        )
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 8.dp), // чуть отступим от края
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Меню",
                                modifier = Modifier.size(32.dp),
                                tint = Color.Black
                            )
                        }
                    }
                },
                actions = {
                    // Уведомление
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Уведомления",
                                modifier = Modifier.size(50.dp),
                                tint = Color.Black
                            )
                        }
                    }

                    // Аватарка
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(
                            onClick = {},
                            modifier = Modifier.size(70.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.avatar),
                                contentDescription = "Аватар",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(percent = 50))
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFFF8EDE6)
                )
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp)
            ) {
                // Скроллируемая колонка
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 80.dp), // оставим место для кнопок
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items.chunked(2)) { rowItems ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            for ((icon, label: String) in rowItems) {
                                MommyTile(
                                    icon = painterResource(id = icon),
                                    label = label,
                                    navController = navController,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        when (label) {
                                            "Привычки" -> navController.navigate("habits_screen")
                                            "Правила\nпослушания" -> navController.navigate("rules_screen")
                                            // другие экраны — позже
                                        }
                                    }
                                )
                            }
                            if (rowItems.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }

                // Закреплённый блок кнопок
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 15.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            navController.navigate("create_rule")  // ← 🔥 Вот это действие
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF552216)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF552216)),
                        modifier = Modifier.width(130.dp)
                    ) {
                        Text(
                            text = "Создать",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                    }
                    OutlinedButton(
                        onClick = {},
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF552216)
                        ),
                        border = BorderStroke(1.dp, Color(0xFF552216))
                    ) {
                        Text(
                            text = "Режимы управления",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontStyle = FontStyle.Italic
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = {
            FirebaseAuth.getInstance().signOut()
            navController.navigate(Screen.RoleSelection.route) {
                popUpTo(Screen.RoleSelection.route) { inclusive = true }
            }
        }) {
            Text("Выйти и очистить сессию")
        }
    }

    @Composable
    fun MommyTile(
        icon: Painter,
        label: String,
        modifier: Modifier = Modifier,
        navController: NavHostController,
        onClick: () -> Unit = {}
    ) {
        Box(
            modifier = modifier
                .aspectRatio(460f / 470)
                .border(
                    width = 2.dp,
                    color = Color(0xFFE0C2BD),
                    shape = RoundedCornerShape(20.dp)
                )
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFF8E7DF))
                .clickable {
                    if (label.contains("Правила")) {
                        navController.navigate("mommy_rules")
                    }else {
                        onClick()  // ⬅️ Добавлено! Только для остальных
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Image(
                    painter = icon,
                    contentDescription = label,
                    modifier = Modifier.size(110.dp)
                )
                Spacer(modifier = Modifier.height(0.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color(0xFF552216),
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic
                    ),
                    textAlign = TextAlign.Center,
                    fontSize = 20.sp
                )
            }
        }
    }

    @Composable
    fun BabyScreen(navController: NavHostController) {
        val rules = remember { mutableStateListOf<Rule>() }

        // 🔄 Автосинхронизация правил Мамочки в реальном времени
        LaunchedEffect(Unit) {
            val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            Firebase.firestore.collection("rules")
                .whereEqualTo("targetUid", babyUid)
                .orderBy("createdAt")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
                    rules.clear()
                    snapshots?.documents?.forEach { doc ->
                        val rule = doc.toObject(Rule::class.java)
                        rule?.id = doc.id  // вот здесь вручную вставляем ID
                        if (rule != null) {
                            rules.add(rule)
                        }
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8EDE6))
                .padding(horizontal = 16.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 🔴 Кнопка выхода
            OutlinedButton(
                onClick = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate(Screen.RoleSelection.route) {
                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                    }
                },
                modifier = Modifier.align(Alignment.Start)
            ) {
                Text("Выйти и очистить сессию")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Приветствие
            Text(
                text = "Доброе утро,\nМалыш!",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                lineHeight = 40.sp,
                color = Color(0xFF552216),
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "🔵 Сегодня ты на уровне ожиданий",
                fontSize = 22.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF552216)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 🔷 Раздел: Правила Мамочки (автообновляемый)


            // 🔸 Список функциональных плиток
            val tiles = listOf(
                "🧸 Мои привычки",
                "🔖 Правила Мамочки",
                "📋 Поощрения",
                "⚠️ Наказания",
                "🗨️ Чат с Мамочкой",
                "🎞 Мои отчёты",
                "📅 Моя Лента"
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(tiles) { label ->
                    BabyTile(label, navController)
                }
            }
        }
    }


    @Composable
    fun BabyTile(label: String, navController: NavHostController) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, Color(0xFF552216), RoundedCornerShape(16.dp))
                .background(Color(0xFFFFF3EE))
                .clickable {
                    if (label.contains("Правила")) {
                        navController.navigate("baby_rules")
                    }else if (label.contains("привычки", ignoreCase = true)) {
                        navController.navigate("baby_habits")
                    }
                }
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 25.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF552216),
                textAlign = TextAlign.Center
            )
        }
    }

    @Composable
    fun RulesScreen(navController: NavController, mommyUid: String, babyUid: String) {
        val rules = remember { mutableStateListOf<Rule>() }
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }

        // Автозагрузка правил
        LaunchedEffect(Unit) {
            Firebase.firestore.collection("rules")
                .whereEqualTo("createdBy", mommyUid)
                .whereEqualTo("targetUid", babyUid)
                .orderBy("createdAt")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) return@addSnapshotListener
                    rules.clear()
                    snapshots?.documents?.forEach { doc ->
                        val rule = doc.toObject(Rule::class.java)
                        rule?.id = doc.id  // вот здесь вручную вставляем ID
                        if (rule != null) {
                            rules.add(rule)
                        }
                    }
                }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8EDE6))
                .padding(16.dp)
        ) {
            Text(
                text = "📘 Ваши правила",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = Color(0xFF552216)
            )

            Spacer(modifier = Modifier.height(16.dp))

            rules.forEachIndexed { index, rule ->
                RuleCard(
                    number = index + 1,
                    rule = rule,
                    onDelete = {
                        Firebase.firestore.collection("rules").document(rule.id!!).delete()
                    },
                    onEdit = {
                        navController.navigate("edit_rule/${rule.id}")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Форма добавления нового правила
        Text(
            text = "➕ Новое правило",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF552216)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Название правила") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Описание") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 5
        )

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = {
                if (title.isNotBlank() && description.isNotBlank()) {
                    val rule = Rule(
                        title = title,
                        description = description,
                        createdBy = mommyUid,
                        targetUid = babyUid,
                        timestamp = System.currentTimeMillis()
                    )
                    Firebase.firestore.collection("rules").add(rule)
                    title = ""
                    description = ""
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF552216)),
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.End)
        ) {
            Text("Сохранить", color = Color.White)
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Правила для любимого нижнего :)",
            fontStyle = FontStyle.Italic,
            fontSize = 50.sp,
            modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally),
            color = Color.DarkGray
        )
    }
}

@Composable
fun BabyRulesScreen(navController: NavHostController) {
    val rules = remember { mutableStateListOf<Rule>() }

    // 🔄 Подписка на обновления правил от Мамочки
    LaunchedEffect(Unit) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("rules")
            .whereEqualTo("targetUid", babyUid)
            .orderBy("createdAt")
            .addSnapshotListener { snapshots, error ->
                if (error != null) return@addSnapshotListener
                rules.clear()
                snapshots?.documents?.forEach { doc ->
                    val rule = doc.toObject(Rule::class.java)
                    rule?.id = doc.id  // вот здесь вручную вставляем ID
                    if (rule != null) {
                        rules.add(rule)
                    }
                }
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .padding(16.dp)
    ) {
        // 🔙 Назад
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { navController.popBackStack() }
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_back),
                contentDescription = "Назад",
                tint = Color(0xFF552216),
                modifier = Modifier.size(35.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Назад",
                fontSize = 18.sp,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF552216)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "📜 Правила Мамочки",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF552216),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (rules.isEmpty()) {
            Text(
                text = "Пока нет правил...\nЖди указаний 🕊",
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                color = Color.DarkGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(rules) { index, rule ->
                    RuleCard(
                        number = index + 1,
                        rule = rule,
                        onDelete = {},   // Малыш не может удалять
                        onEdit = {}      // Малыш не может редактировать
                    )
                }
            }
        }
    }
}


@Composable
fun CreateRuleScreen(
    navController: NavController,
    mommyUid: String,
    babyUid: String
) {
    val ruleName = remember { mutableStateOf("") }
    val ruleDetails = remember { mutableStateOf("") }
    val ruleReminder = remember { mutableStateOf("") }
    val category = remember { mutableStateOf("Дисциплина") }
    val status = remember { mutableStateOf("Временно отключено") }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFDE9DD)) // Новый фон
            .padding(horizontal = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(30.dp))

        // Заголовок и стрелка
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "Создание нового правила",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF53291E)
                )
                Text(
                    text = "Придумайте то, что станет частью режима вашего солнышка...",
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

        // 🔑 Название правила
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_rule_name),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 6.dp),
                tint = Color.Unspecified
            )
            Text(
                text = "Название правила",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF461E1B)
            )
        }
        OutlinedTextField(
            value = ruleName.value,
            onValueChange = { ruleName.value = it },
            placeholder = {
                Text(
                    "Не заходить в Telegram после 21:00",
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF421B14)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 📄 Подробности/суть
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_rule_details),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 6.dp),
                tint = Color.Unspecified
            )
            Text(
                text = "Подробности/суть",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF461E1B)
            )
        }
        OutlinedTextField(
            value = ruleDetails.value,
            onValueChange = { ruleDetails.value = it },
            placeholder = {
                Text(
                    "Что-то типа дааа",
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF421B14)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 💬 Напоминание
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_reminder),
                contentDescription = null,
                modifier = Modifier
                    .size(20.dp)
                    .padding(end = 6.dp),
                tint = Color.Unspecified
            )
            Text(
                text = "Напоминание Малышу (видимое сообщение)",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = Color(0xFF461E1B)
            )
        }
        OutlinedTextField(
            value = ruleReminder.value,
            onValueChange = { ruleReminder.value = it },
            placeholder = {
                Text(
                    "Это придет мне в уведомлениях",
                    fontStyle = FontStyle.Italic,
                    color = Color(0xFF421B14)
                )
            },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 📂 Категория
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
        CategoryDropdown(
            selectedCategory = category.value,
            onCategorySelected = { category.value = it }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 📍 Статус правила
        Text(
            text = "Статус правила",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            fontStyle = FontStyle.Italic,
            color = Color(0xFF461E1B)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = status.value == "Активно",
                onClick = { status.value = "Активно" }
            )
            Text(text = "Активно")
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(
                selected = status.value == "Временно отключено",
                onClick = { status.value = "Временно отключено" }
            )
            Text(text = "Временно отключено")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Если идёт сохранение — показываем индикатор
        if (isSaving) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Кнопка
        Button(
            onClick = {
                // Начали сохранение
                isSaving = true
                errorMessage = null

                if (ruleName.value.isNotBlank() && ruleDetails.value.isNotBlank()) {
                    val newRule = hashMapOf(
                        "title" to ruleName.value,
                        "description" to ruleDetails.value,
                        "reminder" to ruleReminder.value,
                        "category" to category.value,
                        "status" to status.value,
                        "createdBy" to mommyUid,
                        "targetUid" to babyUid,
                        "createdAt" to System.currentTimeMillis()
                    )

                    Firebase.firestore.collection("rules")
                        .add(newRule)
                        .addOnSuccessListener {
                            isSaving = false
                            navController.popBackStack()
                        }
                        .addOnFailureListener { e ->
                            isSaving = false
                            errorMessage = e.localizedMessage ?: "Ошибка при сохранении"
                        }
                }else{
                    isSaving = false
                    errorMessage = "Название и описание обязательна вводить Мамотьке :)))"
                }
            },

            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF5D8CE)
            ),
            shape = RoundedCornerShape(12.dp),
            enabled = !isSaving  // блокируем кнопку во время сохранения
        ) {
            Text(
                text = if (isSaving) "Сохранение…" else "Сохранить правило",
                fontSize = 18.sp,
                color = Color(0xFF53291E),
                fontWeight = FontWeight.SemiBold
            )
        }
        // Если была ошибка — показываем сообщение
        errorMessage?.let { msg ->
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = msg,
                color = Color.Red,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
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
    val categories = RuleCategories

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
                        .weight(1f),
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

                    item {
                        Spacer(modifier = Modifier.height(100.dp))
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
    val reactions = listOf("Никакой", "Грусть Мамочки", "Лишение поощрения", "Стыдный комментарий")

    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    var time by remember { mutableStateOf<Calendar?>(null) }

    val selectedDays = remember { mutableStateListOf<String>() }
    val weekDays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    var selectedSingleDay by remember { mutableStateOf<String?>(null) }

    var selectedWeekDays by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedOnceDate by remember { mutableStateOf<String?>(null) }

    var babyUid by remember { mutableStateOf<String?>(null) }

    var isSaving by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showInactiveDialog by remember { mutableStateOf(false) }

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
        Text("\uD83D\uDCDD Выберите реакцию", fontWeight = FontWeight.SemiBold, color = textColor)
        var reactionExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = reactionExpanded, onExpandedChange = { reactionExpanded = !reactionExpanded }) {
            OutlinedTextField(
                value = reaction,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Не выбрано", color = textColor) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reactionExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
            )
            ExposedDropdownMenu(expanded = reactionExpanded, onDismissRequest = { reactionExpanded = false }) {
                reactions.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        reaction = it
                        reactionExpanded = false
                    })
                }
            }
        }

        // Сообщение в реакции
        OutlinedTextField(
            value = reminder,
            onValueChange = { reminder = it },
            label = { Text("\uD83D\uDCAC Сообщение в реакции") },
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
                    status = status
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
                                status = status
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
    selectedSingleDay: String?,
    time: Calendar?,
    reportType: String,
    category: String,
    points: Int,
    penalty: Int,
    reaction: String,
    reminder: String,
    status: String,

    onComplete: (success: Boolean, error: String?) -> Unit
) {
    val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val actualBabyUid = babyUid ?: return

    val deadlineString = time?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(it.time)
    } ?: "23:59"

    val today = LocalDate.now()
    val nowTime = LocalTime.now()
    val dayMapping = mapOf(
        "Пн" to 1, "Вт" to 2, "Ср" to 3,
        "Чт" to 4, "Пт" to 5, "Сб" to 6, "Вс" to 7
    )

    val deadlineTime = try {
        LocalTime.parse(deadlineString, DateTimeFormatter.ofPattern("HH:mm"))
    } catch (e: Exception) {
        LocalTime.MAX
    }

    val actualOneTimeDate = if (repeat == "once" && !selectedSingleDay.isNullOrBlank()) {
        val targetInt = dayMapping[selectedSingleDay]
        if (targetInt != null) {
            val todayInt = today.dayOfWeek.value
            val offset = if (targetInt == todayInt && nowTime.isAfter(deadlineTime)) {
                7
            } else if (targetInt >= todayInt) {
                targetInt - todayInt
            } else {
                7 - (todayInt - targetInt)
            }
            today.plusDays(offset.toLong()).toString()
        } else null
    } else null


    val nextDueDate = getNextDueDate(
        repeatMode = repeat,
        daysOfWeek = if (repeat == "weekly") selectedDays else null,
        oneTimeDate = actualOneTimeDate,
        deadline = deadlineString
    )


    val habit = hashMapOf(
        "title" to title,
        "repeat" to repeat,
        "daysOfWeek" to selectedDays,
        "oneTimeDate" to actualOneTimeDate,
        "deadline" to deadlineString,
        "reportType" to reportType,
        "category" to category,
        "points" to points,
        "penalty" to penalty,
        "reaction" to reaction,
        "reminder" to reminder,
        "status" to status,
        "mommyUid" to mommyUid,
        "babyUid" to actualBabyUid,
        "nextDueDate" to nextDueDate,
        "completedToday" to false,
        "currentStreak" to 0
    )

    Firebase.firestore.collection("habits")
        .add(habit)
        .addOnSuccessListener { onComplete(true, null) }
        .addOnFailureListener { e -> onComplete(false, e.localizedMessage) }
}



fun getNextDueDate(
    repeatMode: String,
    daysOfWeek: List<String>? = null,
    oneTimeDate: String? = null,
    deadline: String? = null // формат HH:mm
): String? {
    val today = LocalDate.now()
    val nowTime = LocalTime.now()

    // Парсим дедлайн
    val deadlineTime = try {
        deadline?.let {
            LocalTime.parse(it, DateTimeFormatter.ofPattern("HH:mm"))
        }
    } catch (e: Exception) {
        null
    }

    return when (repeatMode) {
        "daily" -> {
            val isTooLate = deadlineTime != null && nowTime.isAfter(deadlineTime)
            if (isTooLate) today.plusDays(1).toString() else today.toString()
        }

        "weekly" -> {
            if (daysOfWeek.isNullOrEmpty()) return null

            val dayMapping = mapOf(
                "Пн" to 1, "Вт" to 2, "Ср" to 3,
                "Чт" to 4, "Пт" to 5, "Сб" to 6, "Вс" to 7
            )

            val todayInt = today.dayOfWeek.value
            val weekInts = daysOfWeek.mapNotNull { dayMapping[it] }.sorted()

            val nextValid = weekInts.firstOrNull {
                if (it == todayInt) {
                    deadlineTime == null || nowTime.isBefore(deadlineTime)
                } else it > todayInt
            } ?: weekInts.first()

            val offset = if (nextValid > todayInt) {
                nextValid - todayInt
            } else if (nextValid == todayInt) {
                if (deadlineTime != null && nowTime.isAfter(deadlineTime)) 7 else 0
            } else {
                7 - (todayInt - nextValid)
            }

            today.plusDays(offset.toLong()).toString()
        }

        "once" -> oneTimeDate

        else -> null
    }
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

    // 🔄 Подгружаем привычки, выданные Мамочкой
    LaunchedEffect(Unit) {
        val babyUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        Firebase.firestore.collection("habits")
            .whereEqualTo("babyUid", babyUid)
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
                        BabyHabitCard(habit)
                    }
                }
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
    val reactions = listOf("Никакой", "Грусть Мамочки", "Лишение поощрения", "Стыдный комментарий")
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

        Text("📢 Реакция Мамочки", fontWeight = FontWeight.SemiBold, color = textColor)
        ExposedDropdownMenuBox(expanded = reactionExpanded, onExpandedChange = { reactionExpanded = !reactionExpanded }) {
            OutlinedTextField(
                value = reaction,
                onValueChange = {},
                readOnly = true,
                placeholder = { Text("Не выбрано", color = textColor) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = reactionExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = textColor)
            )
            ExposedDropdownMenu(expanded = reactionExpanded, onDismissRequest = { reactionExpanded = false }) {
                reactions.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = {
                        reaction = it
                        reactionExpanded = false
                    })
                }
            }
        }

        OutlinedTextField(
            value = reminder,
            onValueChange = { reminder = it },
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

        // Кнопки
        Button(
            onClick = {
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
                    "reaction" to reaction,
                    "reminder" to reminder,
                    "status" to status
                )
                Firebase.firestore.collection("habits").document(habitId)
                    .update(updated)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Изменения сохранены", Toast.LENGTH_SHORT).show()
                        navController.popBackStack()
                    }
                    .addOnFailureListener {
                        Toast.makeText(context, "Ошибка при сохранении", Toast.LENGTH_SHORT).show()
                    }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE8C8BB))
        ) {
            Text("Сохранить", color = textColor)
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