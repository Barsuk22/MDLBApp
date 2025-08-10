package com.app.mdlbapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.authorization.AuthScreen
import com.app.mdlbapp.authorization.PairCodeScreenBaby
import com.app.mdlbapp.authorization.PairCodeScreenMommy
import com.app.mdlbapp.authorization.RoleSelectionScreen
import com.app.mdlbapp.habits.BabyHabitsScreen
import com.app.mdlbapp.habits.CreateHabitScreen
import com.app.mdlbapp.habits.EditHabitScreen
import com.app.mdlbapp.habits.HabitsScreen
import com.app.mdlbapp.habits.background.HabitUpdateScheduler
import com.app.mdlbapp.mainScreens.BabyScreen
import com.app.mdlbapp.mainScreens.MommyScreen
import com.app.mdlbapp.reward.BabyRewardsScreen
import com.app.mdlbapp.reward.CreateRewardScreen
import com.app.mdlbapp.reward.EditRewardScreen
import com.app.mdlbapp.reward.RewardsListScreen
import com.app.mdlbapp.rule.BabyRulesScreen
import com.app.mdlbapp.rule.CreateRuleScreen
import com.app.mdlbapp.rule.EditRuleScreen
import com.app.mdlbapp.rule.RulesListScreen
import com.app.mdlbapp.rule.RulesScreen
import com.app.mdlbapp.ui.theme.MDLBAppTheme
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        FirebaseApp.initializeApp(this)

        HabitUpdateScheduler.scheduleNext(this)

        enableEdgeToEdge()

        setContent {
            MDLBAppTheme {
                com.app.mdlbapp.core.ui.theme.MDLBTheme {
                    val navController = rememberNavController()

                    val startDestination = remember { mutableStateOf("loading") }

                    LaunchedEffect(Unit) {
                        val auth = FirebaseAuth.getInstance()
                        startDestination.value = runCatching {
                            val user =
                                auth.currentUser ?: return@runCatching Screen.RoleSelection.route
                            val doc =
                                Firebase.firestore.collection("users").document(user.uid).get()
                                    .await()
                            val role = doc.getString("role")
                            val paired = doc.getString("pairedWith")
                            when {
                                role == "Mommy" && !paired.isNullOrEmpty() -> Screen.Mommy.route
                                role == "Baby" && !paired.isNullOrEmpty() -> Screen.Baby.route
                                role == "Mommy" -> "pair_mommy"
                                role == "Baby" -> "pair_baby"
                                else -> Screen.RoleSelection.route
                            }
                        }.getOrElse { Screen.RoleSelection.route }
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
                                    Text(text = stringResource(R.string.uid_error))
                                }
                            }
                            composable("pair_baby") {
                                val uid = FirebaseAuth.getInstance().currentUser?.uid
                                if (uid != null) PairCodeScreenBaby(
                                    uid,
                                    navController
                                ) else Text(text = stringResource(R.string.uid_error))
                            }
                            composable(Screen.Mommy.route) {
                                MommyScreen(navController)
                            }
                            composable("rules_screen") {
                                val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                var babyUid by remember { mutableStateOf<String?>(null) }

                                LaunchedEffect(mommyUid) {
                                    babyUid = runCatching {
                                        Firebase.firestore.collection("users")
                                            .document(mommyUid)
                                            .get()
                                            .await()
                                            .getString("pairedWith")
                                    }.getOrNull()
                                }

                                babyUid?.let { bUid ->
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
                            // Магазин наград для малыша
                            composable("baby_rewards") {
                                BabyRewardsScreen(navController)
                            }
                            composable("create_rule") {
                                val mommyUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                                var babyUid by remember { mutableStateOf<String?>(null) }

                                LaunchedEffect(mommyUid) {
                                    babyUid = runCatching {
                                        Firebase.firestore.collection("users")
                                            .document(mommyUid)
                                            .get()
                                            .await()
                                            .getString("pairedWith")
                                    }.getOrNull()
                                }

                                babyUid?.let {
                                    CreateRuleScreen(navController, mommyUid, it)
                                } ?: run {
                                    // Пока не загрузился UID малыша — показываем заглушку
                                    Text(text = stringResource(R.string.uid_loading))
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
                                    babyUid = runCatching {
                                        Firebase.firestore.collection("users")
                                            .document(mommyUid)
                                            .get()
                                            .await()
                                            .getString("pairedWith")
                                    }.getOrNull()
                                }

                                babyUid?.let {
                                    CreateRewardScreen(navController, mommyUid, it)
                                } ?: run {
                                    // Пока не загрузился UID малыша — показываем заглушку
                                    Text(text = stringResource(R.string.uid_loading))
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
}











