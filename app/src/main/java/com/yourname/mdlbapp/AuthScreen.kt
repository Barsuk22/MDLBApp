//файл AuthScreen.kt

package com.yourname.mdlbapp

import android.app.Activity
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import com.google.firebase.firestore.SetOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.ZoneId

@Composable
fun AuthScreen(
    navController: NavHostController,
    preselectedRole: String
) {
    val context = LocalContext.current
    var selectedRole by remember { mutableStateOf(preselectedRole) }

    // Флаги для переключения между режимами: логин или регистрация
    var isRegistering by remember { mutableStateOf(false) }

    // Поля ввода
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    // Сообщения об ошибках / процессе
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Google auth launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSignInResult(context, result, selectedRole, navController)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8EDE6))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Заголовок: меняем текст в зависимости от режима
        Text(
            text = if (isRegistering) "Регистрация ($selectedRole)" else "Вход ($selectedRole)",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Email
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Пароль
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Пароль") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Кнопка входа/регистрации
        Button(
            onClick = {
                errorMessage = null
                if (email.isBlank() || password.isBlank()) {
                    errorMessage = if (isRegistering) "Заполните email и пароль" else "Введите email и пароль"
                    return@Button
                }
                isLoading = true
                val auth = FirebaseAuth.getInstance()
                if (isRegistering) {
                    // Регистрация по email
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                if (user != null) {
                                    // Сохраняем данные пользователя: роль и часовой пояс (merge сохраняет pairedWith)
                                    saveUserToFirestore(user.uid, selectedRole)
                                    checkPairingAndNavigate(user.uid, selectedRole, navController)
                                }
                            } else {
                                errorMessage = task.exception?.localizedMessage ?: "Ошибка регистрации"
                            }
                        }
                } else {
                    // Вход по email
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                val user = task.result?.user
                                if (user != null) {
                                    checkPairingAndNavigate(user.uid, selectedRole, navController)
                                }
                            } else {
                                errorMessage = task.exception?.localizedMessage ?: "Ошибка входа"
                            }
                        }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            Text(if (isRegistering) "Зарегистрироваться" else "Войти")
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Кнопка для Google входа только в режиме входа
        if (!isRegistering) {
            Button(
                onClick = {
                    errorMessage = null
                    val signInClient = getGoogleSignInClient(context)
                    signInClient.signOut().addOnCompleteListener {
                        val intent = signInClient.signInIntent
                        launcher.launch(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                Text("Войти через Google")
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Сообщение об ошибке
        errorMessage?.let { msg ->
            Text(msg, color = Color.Red, modifier = Modifier.padding(8.dp))
        }

        // Ссылка переключения между режимами
        Text(
            text = if (isRegistering) "Уже есть аккаунт? Войти" else "Зарегистрироваться",
            color = Color.Blue,
            modifier = Modifier
                .padding(top = 16.dp)
                .clickable { isRegistering = !isRegistering },
            fontSize = 16.sp
        )
    }
}

fun getGoogleSignInClient(context: Context): GoogleSignInClient {
    val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken("447458623377-1q3tl6v3mejtuspevb2pbjns376dpr3a.apps.googleusercontent.com")
        .requestEmail()
        .build()
    return GoogleSignIn.getClient(context, options)
}

fun handleSignInResult(
    context: Context,
    result: ActivityResult,
    selectedRole: String?,
    navController: NavHostController
) {
    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
    try {
        val account = task.getResult(ApiException::class.java)
        val credential = GoogleAuthProvider.getCredential(account.idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnCompleteListener { authResult ->
                if (authResult.isSuccessful && selectedRole != null) {
                    val user = authResult.result?.user
                    if (user != null) {
                        saveUserToFirestore(user.uid, selectedRole)
                        checkPairingAndNavigate(user.uid, selectedRole, navController)
                    }
                } else {
                    Toast.makeText(context, "Ошибка входа", Toast.LENGTH_SHORT).show()
                }
            }
    } catch (e: Exception) {
        Log.e("AuthError", "Google sign-in failed", e)
        Toast.makeText(context, "Ошибка входа через Google", Toast.LENGTH_SHORT).show()
    }
}

fun checkPairingAndNavigate(
    uid: String,
    role: String,
    navController: NavHostController
) {
    val db = Firebase.firestore
    db.collection("users").document(uid).get()
        .addOnSuccessListener { document ->
            val pairedWith = document.getString("pairedWith")
            if (pairedWith.isNullOrEmpty()) {
                if (role == "Mommy") {
                    navController.navigate("pair_mommy")
                } else {
                    navController.navigate("pair_baby")
                }
            } else {
                if (role == "Mommy") {
                    navController.navigate(Screen.Mommy.route)
                } else {
                    navController.navigate(Screen.Baby.route)
                }
            }
        }
}

fun saveUserToFirestore(uid: String, role: String) {
    // Сохраняем/обновляем только роль и часовой пояс, оставляя другие поля (например, pairedWith) без изменений.
    val data = mapOf(
        "role"     to role,
        "timezone" to ZoneId.systemDefault().id
    )
    Firebase.firestore.collection("users").document(uid).set(data, SetOptions.merge())
}