    package com.app.mdlbapp

    import android.app.Notification
    import android.app.NotificationChannel
    import android.app.NotificationManager
    import android.content.Intent
    import android.content.pm.PackageManager
    import android.media.AudioAttributes
    import android.media.RingtoneManager
    import android.os.Build
    import android.os.Bundle
    import android.util.Log
    import android.view.WindowManager
    import androidx.activity.ComponentActivity
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.compose.setContent
    import androidx.activity.enableEdgeToEdge
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.foundation.layout.Box
    import androidx.compose.foundation.layout.fillMaxSize
    import androidx.compose.foundation.layout.fillMaxWidth
    import androidx.compose.foundation.layout.imePadding
    import androidx.compose.foundation.layout.navigationBarsPadding
    import androidx.compose.foundation.layout.padding
    import androidx.compose.material3.CircularProgressIndicator
    import androidx.compose.material3.Text
    import androidx.compose.runtime.Composable
    import androidx.compose.runtime.DisposableEffect
    import androidx.compose.runtime.LaunchedEffect
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.remember
    import androidx.compose.runtime.setValue
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.unit.dp
    import androidx.core.content.ContextCompat
    import androidx.lifecycle.lifecycleScope
    import androidx.navigation.NavHostController
    import androidx.navigation.compose.NavHost
    import androidx.navigation.compose.composable
    import androidx.navigation.compose.rememberNavController
    import com.google.firebase.FirebaseApp
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.firestore.ktx.firestore
    import com.google.firebase.ktx.Firebase
    import com.app.mdlbapp.authorization.AuthScreen
    import com.app.mdlbapp.authorization.BabyOnboardingFlow
    import com.app.mdlbapp.authorization.MommyOnboardingFlow
    import com.app.mdlbapp.authorization.PairCodeScreenBaby
    import com.app.mdlbapp.authorization.PairCodeScreenMommy
    import com.app.mdlbapp.authorization.RoleSelectionScreen
    import com.app.mdlbapp.data.call.AutoCallReadinessWindows
    import com.app.mdlbapp.data.call.CallPermissionsShortcuts
    import com.app.mdlbapp.data.call.CallRepository
    import com.app.mdlbapp.data.call.DeviceSetupFlow
    import com.app.mdlbapp.data.call.FullscreenIntentPrompt
    import com.app.mdlbapp.habits.ui.BabyHabitsScreen
    import com.app.mdlbapp.habits.ui.CreateHabitScreen
    import com.app.mdlbapp.habits.ui.EditHabitScreen
    import com.app.mdlbapp.habits.ui.HabitsScreen
    import com.app.mdlbapp.permissions.ui.BabyTimezonePickerScreen
    import com.app.mdlbapp.habits.background.workers.HabitDeadlineScheduler
    import com.app.mdlbapp.habits.background.workers.HabitUpdateScheduler
    import com.app.mdlbapp.journal.JournalScreen
    import com.app.mdlbapp.home.baby.ui.BabyScreen
    import com.app.mdlbapp.home.mommy.ui.MommyScreen
    import com.app.mdlbapp.permissions.ExactAlarmPrompt
    import com.app.mdlbapp.reward.BabyRewardsScreen
    import com.app.mdlbapp.reward.CreateRewardScreen
    import com.app.mdlbapp.reward.EditRewardScreen
    import com.app.mdlbapp.reward.RewardsListScreen
    import com.app.mdlbapp.rule.BabyRulesScreen
    import com.app.mdlbapp.rule.CreateRuleScreen
    import com.app.mdlbapp.rule.EditRuleScreen
    import com.app.mdlbapp.rule.RulesListScreen
    import com.app.mdlbapp.rule.RulesScreen
    import com.app.mdlbapp.ui.call.WatchIncomingCall
    import com.app.mdlbapp.ui.chat.BabyChatScreen
    import com.app.mdlbapp.ui.chat.MommyChatScreen
    import com.app.mdlbapp.ui.theme.MDLBAppTheme
    import com.google.firebase.firestore.FieldValue
    import com.google.firebase.messaging.FirebaseMessaging
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.tasks.await

    const val CALLS_CH_ID = "calls_incoming_v4"

    class MainActivity : ComponentActivity() {
        private fun registerFcmTokenForCurrentUser() {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            FirebaseMessaging.getInstance().token.addOnSuccessListener { t ->
                Log.d("FCM", "token=$t")
                Firebase.firestore.collection("users").document(uid)
                    .update("fcmTokens", FieldValue.arrayUnion(t))
                    .addOnSuccessListener { Log.d("FCM", "token saved") }
                    .addOnFailureListener { e -> Log.e("FCM", "save failed", e) }
            }
        }

        private val auth by lazy { FirebaseAuth.getInstance() }
        private val authListener = FirebaseAuth.AuthStateListener { fa ->
            if (fa.currentUser != null) registerFcmTokenForCurrentUser()
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            // если пришли из full-screen уведомления звонка:
            if (intent?.getBooleanExtra("openCall", false) == true) {
                if (Build.VERSION.SDK_INT >= 27) {
                    setShowWhenLocked(true)
                    setTurnScreenOn(true)
                } else {
                    @Suppress("DEPRECATION")
                    window.addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    )
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }

            FirebaseApp.initializeApp(this)

            ensureCallChannel()

            // 1) Слушатель, чтобы всегда сохранять токен после логина
            auth.addAuthStateListener(authListener)

            // 2) Если уже залогинен — сразу сохраним токен один раз
            FirebaseAuth.getInstance().currentUser?.let { registerFcmTokenForCurrentUser() }

            Log.d("TZ", "MainActivity onCreate started")

            enableEdgeToEdge()

            setContent {
                MDLBAppTheme {
                    com.app.mdlbapp.core.ui.theme.MDLBTheme {


                        // === Планировщики дедлайнов/обновлений (как у тебя было) ===
                        LaunchedEffect(Unit) {
                            val uid =
                                FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect

                            val db = Firebase.firestore

                            // слушаем только на телефоне Малыша
                            val myRole = try {
                                db.collection("users").document(uid).get().await().getString("role")
                            } catch (_: Exception) {
                                null
                            }
                            if (myRole != "Baby") return@LaunchedEffect

                            var zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
                            db.collection("users").document(uid)
                                .addSnapshotListener { snap, _ ->
                                    snap?.getString("timezone")
                                        ?.let { runCatching { java.time.ZoneId.of(it) }.getOrNull() }
                                        ?.let { zone = it }
                                }

                            db.collection("habits")
                                .whereEqualTo("babyUid", uid)
                                .whereEqualTo("status", "on")
                                .addSnapshotListener { _, _ ->
                                    HabitDeadlineScheduler.rescheduleAllForToday(this@MainActivity, zone)
                                }
                        }

                        LaunchedEffect(Unit) {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid
                            if (uid != null) {
                                val doc = Firebase.firestore.collection("users").document(uid)
                                    .get().await()
                                if (doc.getString("role") == "Baby") {
                                    val zone = runCatching {
                                        java.time.ZoneId.of(doc.getString("timezone") ?: "")
                                    }.getOrNull() ?: java.time.ZoneId.systemDefault()
                                    HabitUpdateScheduler.scheduleNext(this@MainActivity, zone)
                                }
                            }
                        }

                        // === Навигация с "загрузочным" стартом ===
                        val navController = rememberNavController()
                        val startDestination = remember { mutableStateOf("loading") }
                        val seeded = remember { mutableStateOf(false) }

                        // Решаем, куда идти при старте приложения
                        LaunchedEffect(Unit) {
                            val auth = FirebaseAuth.getInstance()
                            val user = auth.currentUser

                            startDestination.value = if (user == null) {
                                // Совсем новый малыш — сначала выбираем роль / авторизуемся
                                Screen.RoleSelection.route
                            } else {
                                try {
                                    val snap = Firebase.firestore.collection("users")
                                        .document(user.uid).get().await()

                                    val exists         = snap.exists()
                                    val role           = snap.getString("role")
                                    val paired         = snap.getString("pairedWith")
                                    val onboardingDone = snap.getBoolean("onboardingDone") == true

                                    when {
                                        // 1) Вход есть, но профиль не создан → отправляем на выбор роли (и можно разлогинить по желанию)
                                        !exists -> {
                                            FirebaseAuth.getInstance().signOut()
                                            Screen.RoleSelection.route
                                        }

                                        // 2) Профиль есть, но онбординг ещё не пройден → показываем онбординг по роли
                                        !onboardingDone -> when (role) {
                                            "Mommy" -> "onboarding_mommy"
                                            "Baby"  -> "onboarding_baby"
                                            else    -> {
                                                // Роль не задана? вернём на выбор роли, чтобы не гадать
                                                FirebaseAuth.getInstance().signOut()
                                                Screen.RoleSelection.route
                                            }
                                        }

                                        // 3) Профиль есть и онбординг пройден → либо дом, либо экран спаривания
                                        !paired.isNullOrEmpty() && role == "Mommy" -> Screen.Mommy.route
                                        !paired.isNullOrEmpty() && role == "Baby"  -> Screen.Baby.route
                                        role == "Mommy" -> "pair_mommy"
                                        role == "Baby"  -> "pair_baby"

                                        // 4) На всякий случай: что‑то странное в профиле → к выбору роли
                                        else -> {
                                            FirebaseAuth.getInstance().signOut()
                                            Screen.RoleSelection.route
                                        }
                                    }
                                } catch (e: Exception) {
                                    // если что‑то пошло не так — безопасно на RoleSelection
                                    Screen.RoleSelection.route
                                }
                            }
                        }


                        // Подписка на смену часового пояса (как было)
                        LaunchedEffect(Unit) {
                            val uid =
                                FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
                            val db = Firebase.firestore
                            var lastZoneId: String? = null

                            val myRole = try {
                                db.collection("users").document(uid).get().await().getString("role")
                            } catch (_: Exception) {
                                null
                            }
                            if (myRole != "Baby") return@LaunchedEffect

                            db.collection("users").document(uid)
                                .addSnapshotListener { snap, err ->
                                    if (err != null) {
                                        Log.e("TZ", "profile-snapshot error", err)
                                        return@addSnapshotListener
                                    }
                                    val tz = snap?.getString("timezone")
                                    if (!tz.isNullOrBlank() && tz != lastZoneId) {
                                        lastZoneId = tz
                                        val zone =
                                            runCatching { java.time.ZoneId.of(tz) }.getOrNull()
                                        if (zone != null) {
                                            Log.d("TZ", "timezone changed → $tz, reschedule…")
                                            HabitUpdateScheduler.scheduleNext(this@MainActivity, zone)
                                            this@MainActivity.lifecycleScope.launch {
                                                HabitDeadlineScheduler.rescheduleAllForToday(
                                                    context = this@MainActivity,
                                                    zone = zone
                                                )
                                            }
                                        }
                                    }
                                }
                        }

                        LaunchedEffect(startDestination) {
                            val me = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
                            val db = Firebase.firestore
                            runCatching {
                                // найдём tid чата пары (как в WatchIncomingCall)
                                val pair = db.collection("users").document(me).get().await().getString("pairedWith") ?: return@runCatching
                                val tid = listOf(
                                    db.collection("chats").whereEqualTo("mommyUid", me).whereEqualTo("babyUid", pair).limit(1).get().await(),
                                    db.collection("chats").whereEqualTo("mommyUid", pair).whereEqualTo("babyUid", me).limit(1).get().await()
                                ).firstOrNull { !it.isEmpty }?.documents?.first()?.id ?: return@runCatching

                                val ttlMs = 120_000L
                                db.collection("chats").document(tid).collection("calls")
                                    .whereEqualTo("state", "ringing").get().await()
                                    .documents.forEach { d ->
                                        val created = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                                        if (created == 0L || System.currentTimeMillis() - created > ttlMs) {
                                            CallRepository.setState(tid, d.id, "ended") // suspend есть; await внутри.
                                        }
                                    }
                            }
                        }

                        LaunchedEffect(startDestination.value) {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
                            // ищём наш tid (как в WatchIncomingCall) и подметаем залипшие звонки
                            runCatching {
                                val db = Firebase.firestore
                                val paired = db.collection("users").document(uid).get().await()
                                    .getString("pairedWith") ?: return@runCatching
                                val q1 = db.collection("chats")
                                    .whereEqualTo("mommyUid", uid).whereEqualTo("babyUid", paired)
                                    .limit(1).get().await()
                                val q2 = if (q1.isEmpty) db.collection("chats")
                                    .whereEqualTo("mommyUid", paired).whereEqualTo("babyUid", uid)
                                    .limit(1).get().await() else null
                                val tid = q1.documents.firstOrNull()?.id ?: q2?.documents?.firstOrNull()?.id ?: return@runCatching

                                val calls = db.collection("chats").document(tid).collection("calls")
                                    .whereEqualTo("state", "ringing")
                                    .get().await()

                                val ttlMs = 120_000L
                                calls.documents.forEach { d ->
                                    val callee = d.getString("calleeUid")
                                    val created = d.getTimestamp("createdAt")?.toDate()?.time ?: 0L
                                    if (callee == uid && (created == 0L || System.currentTimeMillis() - created > ttlMs)) {
                                        runCatching { CallRepository.setState(tid, d.id, "ended") }
                                    }
                                }
                            }
                        }

                        // Сеялка (DEBUG)
                        LaunchedEffect(startDestination.value) {
                            if (startDestination.value == "loading") return@LaunchedEffect
                            if (!com.app.mdlbapp.BuildConfig.DEBUG) return@LaunchedEffect
                            if (seeded.value) return@LaunchedEffect

                            val pair = fetchPairUids()
                            if (pair == null) {
                                Log.d("DevSeeder", "skip: not paired or no user")
                                return@LaunchedEffect
                            }

                            try {
                                val (mommyUid, babyUid) = pair
                                DevSeeder.seedIfEmpty(mommyUid, babyUid)
                                Log.d("DevSeeder", "seed OK for $mommyUid/$babyUid")
                                seeded.value = true
                            } catch (e: Exception) {
                                Log.e("DevSeeder", "seed error", e)
                            }
                        }

                        // Показ навигации (пока "loading" — крутилка)
                        if (startDestination.value == "loading") {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        } else {
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
                                        Text(text = stringResource(R.string.uid_error))
                                    }
                                }
                                composable("pair_baby") {
                                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                                    if (uid != null) {
                                        PairCodeScreenBaby(uid, navController)
                                    } else {
                                        Text(text = stringResource(R.string.uid_error))
                                    }
                                }
                                composable(Screen.Mommy.route) {
                                    MommyScreen(navController)
                                }
                                composable("rules_screen") {
                                    val mommyUid =
                                        FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                    var babyUid =
                                        remember { mutableStateOf<String?>(null) }

                                    LaunchedEffect(mommyUid) {
                                        babyUid.value = runCatching {
                                            Firebase.firestore.collection("users")
                                                .document(mommyUid)
                                                .get()
                                                .await()
                                                .getString("pairedWith")
                                        }.getOrNull()
                                    }

                                    babyUid.value?.let { bUid ->
                                        RulesScreen(
                                            navController = navController,
                                            mommyUid = mommyUid,
                                            babyUid = bUid
                                        )
                                    } ?: Text(text = stringResource(R.string.uid_loading))
                                }
                                composable("mommy_rules") {
                                    RulesListScreen(navController)
                                }
                                composable("baby_rules") {
                                    BabyRulesScreen(navController)
                                }
                                composable("baby_rewards") {
                                    BabyRewardsScreen(navController)
                                }
                                composable("create_rule") {
                                    val mommyUid =
                                        FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                    var babyUid =
                                        remember { mutableStateOf<String?>(null) }

                                    LaunchedEffect(mommyUid) {
                                        babyUid.value = runCatching {
                                            Firebase.firestore.collection("users")
                                                .document(mommyUid)
                                                .get()
                                                .await()
                                                .getString("pairedWith")
                                        }.getOrNull()
                                    }

                                    babyUid.value?.let {
                                        CreateRuleScreen(navController, mommyUid, it)
                                    } ?: Text(text = stringResource(R.string.uid_loading))
                                }
                                composable("edit_rule/{ruleId}") { backStackEntry ->
                                    val ruleId =
                                        backStackEntry.arguments?.getString("ruleId") ?: ""
                                    EditRuleScreen(navController, ruleId)
                                }
                                composable("habits_screen") { HabitsScreen(navController) }
                                composable("create_habit") { CreateHabitScreen(navController) }
                                composable("baby_habits") { BabyHabitsScreen(navController) }
                                composable("edit_habit/{habitId}") { backStackEntry ->
                                    val habitId =
                                        backStackEntry.arguments?.getString("habitId") ?: ""
                                    EditHabitScreen(navController, habitId)
                                }
                                composable("mommy_rewards") { RewardsListScreen(navController) }
                                composable("create_reward") {
                                    val mommyUid =
                                        FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                    var babyUid =
                                        remember { mutableStateOf<String?>(null) }

                                    LaunchedEffect(mommyUid) {
                                        babyUid.value = runCatching {
                                            Firebase.firestore.collection("users")
                                                .document(mommyUid)
                                                .get()
                                                .await()
                                                .getString("pairedWith")
                                        }.getOrNull()
                                    }

                                    babyUid.value?.let {
                                        CreateRewardScreen(navController, mommyUid, it)
                                    } ?: Text(text = stringResource(R.string.uid_loading))
                                }
                                composable("edit_reward/{rewardId}") { backStackEntry ->
                                    val rewardId =
                                        backStackEntry.arguments?.getString("rewardId") ?: ""
                                    EditRewardScreen(navController, rewardId)
                                }
                                composable("journal") {
                                    JournalScreen(onBack = { navController.popBackStack() })
                                }
                                composable("mommy_chat") { MommyChatScreen(navController) }
                                composable("baby_chat") { BabyChatScreen(navController) }
                                composable("settings_baby_timezone") {
                                    val mommyUid =
                                        FirebaseAuth.getInstance().currentUser?.uid
                                            ?: return@composable
                                    var babyUid =
                                        remember { mutableStateOf<String?>(null) }

                                    LaunchedEffect(Unit) {
                                        val my =
                                            FirebaseAuth.getInstance().currentUser?.uid
                                                ?: return@LaunchedEffect
                                        val doc = Firebase.firestore.collection("users")
                                            .document(my).get().await()
                                        babyUid.value = doc.getString("pairedWith")
                                    }

                                    babyUid.value?.let { bUid ->
                                        BabyTimezonePickerScreen(
                                            bUid,
                                            onDone = { navController.popBackStack() }
                                        )
                                    } ?: Text("Загружаю…")
                                }

                                // Онбординги
                                composable("onboarding_baby") {
                                    BabyOnboardingFlow(navController = navController)
                                }

                                // Онбординг Мамочки
                                composable("onboarding_mommy") {
                                    MommyOnboardingFlow(navController = navController)
                                }

                                composable("call/{tid}/{asCaller}?auto={auto}") { back ->
                                    val tid = back.arguments?.getString("tid")!!
                                    val asCaller = back.arguments?.getString("asCaller") == "1"
                                    val auto = back.arguments?.getString("auto") == "1"
                                    com.app.mdlbapp.ui.call.CallScreen(
                                        tid = tid,
                                        asCaller = asCaller,
                                        navBack = { navController.popBackStack() },
                                        autoJoin = auto                 // ← новый параметр
                                    )
                                }
//                                composable("device_setup") {
//                                    DeviceSetupFlow(navController) {
//                                        val uid = FirebaseAuth.getInstance().currentUser?.uid
//                                        if (uid == null) {
//                                            navController.navigate(Screen.RoleSelection.route) {
//                                                popUpTo("device_setup") { inclusive = true }
//                                            }
//                                        } else {
//                                            Firebase.firestore.collection("users").document(uid).get()
//                                                .addOnSuccessListener { doc ->
//                                                    val role = doc.getString("role") ?: "Baby"
//                                                    navController.navigate(
//                                                        if (role == "Mommy") Screen.Mommy.route else Screen.Baby.route
//                                                    ) {
//                                                        popUpTo("device_setup") { inclusive = true }
//                                                    }
//                                                }
//                                                .addOnFailureListener {
//                                                    navController.navigate(Screen.Baby.route) {
//                                                        popUpTo("device_setup") { inclusive = true }
//                                                    }
//                                                }
//                                        }
//                                    }
//                                }
                            }
                            LaunchedEffect(Unit) {
                                if (intent?.getBooleanExtra("openCall", false) == true) {
                                    val tid = intent.getStringExtra("tid")
                                    val asCaller = if (intent.getBooleanExtra("asCaller", false)) "1" else "0"
                                    val auto = if (intent.getBooleanExtra("autoJoin", false)) "1" else "0"
                                    if (!tid.isNullOrBlank()) {
                                        intent?.takeIf { it.getBooleanExtra("openCallFromNotif", false) }?.let {
                                            val tid = it.getStringExtra("tid") ?: return@let
                                            navController.navigate("call/$tid/$asCaller?auto=$auto")
                                        }
                                    }
                                }
                            }

                            var showWatcher by remember { mutableStateOf(false) }
                            LaunchedEffect(Unit) {
                                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
                                val paired = runCatching {
                                    Firebase.firestore.collection("users").document(uid).get().await()
                                        .getString("pairedWith")
                                }.getOrNull()
                                showWatcher = !paired.isNullOrBlank()
                            }
                            if (showWatcher) {
                                WatchIncomingCall(navController, preferSystemHeadsUp = true)
                            }
                        }
                        ExactAlarmPrompt()
                        FullscreenIntentPrompt(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        )

                        CallPermissionsShortcuts(
                            modifier = Modifier.fillMaxWidth()
                        )
                        AutoCallReadinessWindows()
                    }
                }
            }
        }

        private fun ensureCallChannel() {
            if (Build.VERSION.SDK_INT < 26) return
            val nm = getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CALLS_CH_ID) == null) {
                val ch = NotificationChannel(
                    CALLS_CH_ID, "Входящие звонки",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Показывает входящие видео/аудио-звонки"
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                    // опционально — звонок и вибрация по умолчанию:
                    setSound(
                        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                         AudioAttributes.Builder()
                             .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build())
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }

        override fun onNewIntent(intent: Intent) {
            super.onNewIntent(intent)
            setIntent(intent)
            if (intent.getBooleanExtra("openCall", false)) {
                if (Build.VERSION.SDK_INT >= 27) {
                    setShowWhenLocked(true); setTurnScreenOn(true)
                }
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        override fun onResume() {
            super.onResume()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            Firebase.firestore.collection("users").document(uid)
                .update(mapOf(
                    "isOnline" to true
                ))
        }


        override fun onPause() {
            super.onPause()
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            Firebase.firestore.collection("users").document(uid)
                .update(mapOf(
                    "isOnline" to false,
                    "lastSeenAt" to FieldValue.serverTimestamp()
                ))
        }
    }

    // Вспомогашка для DEBUG-сидера
    private suspend fun fetchPairUids(): Pair<String, String>? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val doc = Firebase.firestore.collection("users").document(uid).get().await()
        val role = doc.getString("role") ?: return null
        val paired = doc.getString("pairedWith") ?: return null
        return if (role == "Mommy") uid to paired else paired to uid
    }



