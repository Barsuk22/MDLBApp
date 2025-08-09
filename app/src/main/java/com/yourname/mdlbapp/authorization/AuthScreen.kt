//файл AuthScreen.kt
@file:OptIn(ExperimentalMaterial3Api::class)
package com.yourname.mdlbapp.authorization


import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.yourname.mdlbapp.Screen
import java.time.ZoneId
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text as M3Text
import androidx.compose.ui.text.input.VisualTransformation
import com.yourname.mdlbapp.core.ui.AppWidthClass
import com.yourname.mdlbapp.core.ui.rememberAppWidthClass
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import com.yourname.mdlbapp.core.ui.AppHeightClass
import com.yourname.mdlbapp.core.ui.rememberAppHeightClass
import com.yourname.mdlbapp.core.ui.rememberIsLandscape
import androidx.compose.foundation.layout.BoxWithConstraints

private data class AuthUiTokens(
    val cardMaxWidth: Dp,
    val outerHPad: Dp,
    val vSpacing: Dp,
    val titleSize: TextUnit,
    val buttonHeight: Dp,
    val controlsCorner: Dp,
    val columnsGap: Dp,
    val actionsWidth: Dp,
    val cardWidthFraction: Float,
    val fieldCorner: Dp
)

@Composable
private fun rememberAuthUiTokens(): AuthUiTokens {
    val widthClass = rememberAppWidthClass()
    val heightClass = rememberAppHeightClass()
    val isLandscape = rememberIsLandscape()

    val corner = 14.dp

    val cfg = LocalConfiguration.current

    val columnsGap = when {
        heightClass == AppHeightClass.Compact -> 10.dp  // было 12.dp
        widthClass == AppWidthClass.Expanded  -> 24.dp
        else -> 16.dp
    }

    // Карточка в горизонтали — уже и с потолком; в портрете как раньше
    val cardMax = if (isLandscape) {
        when (widthClass) {
            AppWidthClass.Expanded -> 620.dp
            AppWidthClass.Medium   -> 600.dp
            else                   -> 520.dp
        }
    } else {
        when (widthClass) {
            AppWidthClass.Expanded -> 840.dp
            AppWidthClass.Medium   -> 720.dp
            else                   -> 560.dp
        }
    }

    // Доля ширины, которую займёт карточка (только для горизонтали)
    val cardWidthFraction = if (isLandscape) {
        when (widthClass) {
            AppWidthClass.Expanded -> 0.48f
            AppWidthClass.Medium   -> 0.56f
            else                   -> 0.85f   // телефон в landscape — шире, чтоб поля не были узкими
        }
    } else 1f



    val outerPad = when {
        isLandscape && widthClass == AppWidthClass.Compact -> 12.dp
        isLandscape -> 20.dp
        widthClass == AppWidthClass.Expanded -> 32.dp
        widthClass == AppWidthClass.Medium   -> 20.dp
        else -> 12.dp
    }
    val title = when {
        heightClass == AppHeightClass.Compact -> 22.sp
        widthClass == AppWidthClass.Expanded -> 28.sp
        else -> 24.sp
    }
    val btnH = when {
        heightClass == AppHeightClass.Compact -> 42.dp   // было 44.dp
        widthClass == AppWidthClass.Compact   -> 48.dp
        else -> 52.dp
    }
    // Узкий столбик кнопок справа, чтобы не «давил»
    val actionsWidth = if (isLandscape) {
        when (widthClass) {
            AppWidthClass.Expanded -> 260.dp
            AppWidthClass.Medium   -> 240.dp
            else                   -> 180.dp  // было 220.dp — ужимаем
        }
    } else {
        when (widthClass) {
            AppWidthClass.Expanded -> 360.dp
            AppWidthClass.Medium   -> 320.dp
            else -> 0.dp
        }
    }

    return AuthUiTokens(
        cardMaxWidth = cardMax,
        outerHPad = outerPad,
        vSpacing = if (heightClass == AppHeightClass.Compact) 12.dp else 16.dp,
        titleSize = title,
        buttonHeight = btnH,
        controlsCorner = corner,
        columnsGap = columnsGap,
        actionsWidth = actionsWidth,
        cardWidthFraction = cardWidthFraction,
        fieldCorner = corner
    )
}

// --------- Screen

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun AuthScreen(
    navController: NavHostController,
    preselectedRole: String
) {
    val tokens = rememberAuthUiTokens()
    val context = LocalContext.current
    var selectedRole by remember { mutableStateOf(preselectedRole) }

    var isRegistering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    // Палитра проекта (оставляем твою фирменную)
    val backgroundColor = Color(0xFFF8EDE6)
    val cardColor       = Color(0xFFFDE9DD)
    val accentColor     = Color(0xFF552216)
    val borderColor     = Color(0xFFE0C2BD)
    val googleColor     = Color(0xFFD5C1BC)

    // Google auth
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleSignInResult(context, result, selectedRole, navController)
    }

    val widthClass = rememberAppWidthClass()

    val isLandscape = rememberIsLandscape()

    val heightClass = rememberAppHeightClass()

// когда делаем ДВЕ колонки (поле ↔ кнопка)
    val useTwoColumns = when {
        widthClass == AppWidthClass.Expanded -> true
        widthClass == AppWidthClass.Medium && heightClass != AppHeightClass.Compact -> true
        else -> false
    }

    // 2 колонки только если реально есть место
    val useTwoColumnsBase = when {
        widthClass == AppWidthClass.Expanded -> true
        widthClass == AppWidthClass.Medium && heightClass != AppHeightClass.Compact -> true
        else -> false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .windowInsetsPadding(WindowInsets.systemBars),
        contentAlignment = Alignment.Center
    ) {
        // Карточка фиксированной max-ширины + вертикальная прокрутка (для маленьких экранов/клавы)
        Card(
            modifier = Modifier
                .fillMaxWidth(tokens.cardWidthFraction)
                .widthIn(max = tokens.cardMaxWidth)
                .padding(horizontal = tokens.outerHPad)
                .imePadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardColor),
            border = BorderStroke(2.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            // Заголовок всегда сверху, по центру
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                M3Text(
                    text = if (isRegistering) "Регистрация ($selectedRole)" else "Вход ($selectedRole)",
                    fontSize = tokens.titleSize,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(16.dp))
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp)
            ) {
                // мамина линейка: реальная доступная ширина контента
                val minField = 340.dp
                val canFitTwoColumns = maxWidth >= (tokens.actionsWidth + tokens.columnsGap + minField)

                val useTwoColumns = useTwoColumnsBase && canFitTwoColumns

                if (!useTwoColumns) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(tokens.vSpacing)
                    ) {
                        AuthFields(
                            email = email, onEmailChange = { email = it },
                            password = password, onPasswordChange = { password = it },
                            showPassword = showPassword, onToggleShowPassword = { showPassword = !showPassword },
                            tokens = tokens, accentColor = accentColor, borderColor = borderColor
                        )
                        AuthActions(
                            isRegistering = isRegistering, isLoading = isLoading,
                            googleVisible = !isRegistering,
                            onSubmit = {
                                submitAuth(isRegistering, email, password,
                                    onError = { errorMessage = it },
                                    onLoading = { isLoading = it },
                                    onSuccess = { uid -> checkPairingAndNavigate(uid, selectedRole, navController) })
                            },
                            onGoogleClick = {
                                errorMessage = null
                                val client = getGoogleSignInClient(context)
                                client.signOut().addOnCompleteListener { launcher.launch(client.signInIntent) }
                            },
                            onToggleMode = { isRegistering = !isRegistering },
                            errorMessage = errorMessage,
                            accentColor = accentColor, borderColor = borderColor, googleColor = googleColor, tokens = tokens
                        )
                    }
                } else {
                    // твоя двухстрочная раскладка «поле ↔ кнопка»
                    Column(verticalArrangement = Arrangement.spacedBy(tokens.vSpacing)) {
                        FieldWithActionRow(
                            actionsWidth = tokens.actionsWidth,
                            gap = tokens.columnsGap,
                            left = { EmailField(email, { email = it }, tokens, accentColor, borderColor) },
                            right = {
                                Button(
                                    onClick = {
                                        submitAuth(isRegistering, email, password,
                                            onError = { errorMessage = it },
                                            onLoading = { isLoading = it },
                                            onSuccess = { uid -> checkPairingAndNavigate(uid, selectedRole, navController) })
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(tokens.buttonHeight),
                                    enabled = !isLoading,
                                    shape = RoundedCornerShape(tokens.controlsCorner),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = borderColor.copy(alpha = 0.65f),
                                        contentColor = accentColor
                                    )
                                ) { M3Text("Войти", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                            }
                        )

                        FieldWithActionRow(
                            actionsWidth = tokens.actionsWidth,
                            gap = tokens.columnsGap,
                            left = {
                                PasswordField(
                                    password, { password = it }, showPassword, { showPassword = !showPassword },
                                    tokens, accentColor, borderColor
                                )
                            },
                            right = {
                                if (!isRegistering) {
                                    Button(
                                        onClick = {
                                            errorMessage = null
                                            val client = getGoogleSignInClient(context)
                                            client.signOut().addOnCompleteListener { launcher.launch(client.signInIntent) }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(tokens.buttonHeight),
                                        enabled = !isLoading,
                                        shape = RoundedCornerShape(tokens.controlsCorner),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = googleColor,
                                            contentColor = accentColor
                                        )
                                    ) { M3Text("Войти через Google", fontSize = 16.sp, fontWeight = FontWeight.Medium) }
                                }
                            }
                        )

                        errorMessage?.let { msg ->
                            M3Text(msg, color = Color.Red, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
            // общий низ карточки — работает для обеих раскладок
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = { isRegistering = !isRegistering }, contentPadding = PaddingValues(0.dp)) {
                    M3Text(
                        text = if (isRegistering) "Уже есть аккаунт? Войти" else "Зарегистрироваться",
                        color = Color(0xFF0066CC),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Оверлей загрузки
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentColor)
            }
        }
    }
}

@Composable
private fun FieldWithActionRow(
    actionsWidth: Dp,
    gap: Dp,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) { left() }           // поле растёт
        Box(modifier = Modifier.width(actionsWidth)) { right() }  // кнопка фикс. ширины, по центру бокса
    }
}

@Composable
private fun EmailField(
    email: String,
    onEmailChange: (String) -> Unit,
    tokens: AuthUiTokens,
    accentColor: Color,
    borderColor: Color
) {
    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { M3Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        shape = RoundedCornerShape(tokens.controlsCorner),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor.copy(alpha = 0.7f),
            focusedLabelColor = accentColor,
            cursorColor = accentColor
        )
    )
}

@Composable
private fun PasswordField(
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    tokens: AuthUiTokens,
    accentColor: Color,
    borderColor: Color
) {
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { M3Text("Пароль") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = onToggleShowPassword, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                M3Text(if (showPassword) "Скрыть" else "Показать", fontSize = 12.sp, color = accentColor)
            }
        },
        shape = RoundedCornerShape(tokens.controlsCorner),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor.copy(alpha = 0.7f),
            focusedLabelColor = accentColor,
            cursorColor = accentColor
        )
    )
}

@Composable
private fun AuthFields(
    email: String,
    onEmailChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onToggleShowPassword: () -> Unit,
    tokens: AuthUiTokens,
    accentColor: Color,
    borderColor: Color
) {
    val fieldShape = RoundedCornerShape(tokens.controlsCorner)

    OutlinedTextField(
        value = email,
        onValueChange = onEmailChange,
        label = { M3Text("Email") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        shape = fieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor.copy(alpha = 0.7f),
            focusedLabelColor = accentColor,
            cursorColor = accentColor
        )
    )

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        label = { M3Text("Пароль") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            // маленькая текст-кнопочка вместо «кирпичика»
            TextButton(onClick = onToggleShowPassword, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                M3Text(if (showPassword) "Скрыть" else "Показать", fontSize = 12.sp, color = accentColor)
            }
        },
        shape = fieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = borderColor,
            unfocusedBorderColor = borderColor.copy(alpha = 0.7f),
            focusedLabelColor = accentColor,
            cursorColor = accentColor
        )
    )
}

@Composable
private fun ColumnScope.AuthActions(
    isRegistering: Boolean,
    isLoading: Boolean,
    googleVisible: Boolean,
    onSubmit: () -> Unit,
    onGoogleClick: () -> Unit,
    onToggleMode: () -> Unit,
    errorMessage: String?,
    accentColor: Color,
    borderColor: Color,
    googleColor: Color,
    tokens: AuthUiTokens
) {
    val btnShape = RoundedCornerShape(tokens.controlsCorner)

    // Главная — спокойная тональная
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth().height(tokens.buttonHeight),
        enabled = !isLoading,
        shape = btnShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = borderColor.copy(alpha = 0.65f),
            contentColor = accentColor
        )
    ) {
        M3Text(if (isRegistering) "Зарегистрироваться" else "Войти", fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }

    if (googleVisible) {
        Button(
            onClick = onGoogleClick,
            modifier = Modifier.fillMaxWidth().height(tokens.buttonHeight),
            enabled = !isLoading,
            shape = btnShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = googleColor,
                contentColor = accentColor
            )
        ) {
            M3Text("Войти через Google", fontSize = 16.sp, fontWeight = FontWeight.Medium)
        }
    }

    errorMessage?.let { msg ->
        M3Text(msg, color = Color.Red, fontSize = 13.sp, modifier = Modifier.fillMaxWidth())
    }
}

// Вспомогалочка: единая отправка (логин/регистрация) с обработкой loading/error
private fun submitAuth(
    isRegistering: Boolean,
    email: String,
    password: String,
    onError: (String) -> Unit,
    onLoading: (Boolean) -> Unit,
    onSuccess: (uid: String) -> Unit
) {
    if (email.isBlank() || password.isBlank()) {
        onError(if (isRegistering) "Заполните email и пароль" else "Введите email и пароль")
        return
    }
    onLoading(true)
    val auth = FirebaseAuth.getInstance()
    if (isRegistering) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                onLoading(false)
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        saveUserToFirestore(user.uid, role = "Unknown") // роль подставим при навигации
                        onSuccess(user.uid)
                    } else onError("Ошибка регистрации")
                } else {
                    onError(task.exception?.localizedMessage ?: "Ошибка регистрации")
                }
            }
    } else {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                onLoading(false)
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) onSuccess(user.uid)
                    else onError("Ошибка входа")
                } else {
                    onError(task.exception?.localizedMessage ?: "Ошибка входа")
                }
            }
    }
}

// --------- Остальные функции из твоего файла (оставляем как были)

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
                if (role == "Mommy") navController.navigate("pair_mommy")
                else navController.navigate("pair_baby")
            } else {
                if (role == "Mommy") navController.navigate(Screen.Mommy.route)
                else navController.navigate(Screen.Baby.route)
            }
        }
}

fun saveUserToFirestore(uid: String, role: String) {
    val data = mapOf(
        "role" to role,
        "timezone" to ZoneId.systemDefault().id
    )
    Firebase.firestore.collection("users").document(uid).set(data, SetOptions.merge())
}