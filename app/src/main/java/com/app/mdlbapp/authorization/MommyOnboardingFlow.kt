@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.app.mdlbapp.authorization

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.app.mdlbapp.Screen
import java.io.ByteArrayOutputStream
import kotlin.math.max

@Composable
fun MommyOnboardingFlow(navController: NavHostController) {
    val bg = Color(0xFFF8EDE6)
    val card = Color(0xFFFDE9DD)
    val accent = Color(0xFF552216)
    val border = Color(0xFFE0C2BD)

    val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
    var step by remember { mutableStateOf(0) } // 0 привет, 1 роль, 2 имя+аватар, 3 тема
    var displayName by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf("soft") }
    var localAvatar by remember { mutableStateOf<Uri?>(null) }
    var saving by remember { mutableStateOf(false) }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) localAvatar = uri }

    val context = LocalContext.current

    fun prepareAvatarDataUrlIfNeeded(onDone: (String?) -> Unit, onError: (Exception) -> Unit) {
        val uri = localAvatar ?: return onDone(null)
        try {
            val dataUrl = makeDataUrlFromUri(context, uri, maxSize = 384, quality = 85)
            onDone(dataUrl)
        } catch (e: Exception) {
            onError(e)
        }
    }

    fun finish() {
        saving = true
        prepareAvatarDataUrlIfNeeded(
            onDone = { dataUrl ->
                val data = mutableMapOf<String, Any>(
                    "displayName" to (displayName.ifBlank { "Мамочка" }),
                    "theme" to theme,
                    "onboardingDone" to true
                )
                if (!dataUrl.isNullOrBlank()) data["photoDataUrl"] = dataUrl

                Firebase.firestore.collection("users").document(uid)
                    .set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        saving = false
                        Firebase.firestore.collection("users").document(uid).get()
                            .addOnSuccessListener { me ->
                                val pairedWith = me.getString("pairedWith")
                                if (pairedWith.isNullOrEmpty()) {
                                    navController.navigate("pair_mommy") {
                                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                    }
                                } else {
                                    navController.navigate(Screen.Mommy.route) {
                                        popUpTo(Screen.RoleSelection.route) { inclusive = true }
                                    }
                                }
                            }
                    }
                    .addOnFailureListener {
                        saving = false
                        Toast.makeText(navController.context, "Не удалось сохранить", Toast.LENGTH_SHORT).show()
                    }
            },
            onError = { e ->
                saving = false
                Toast.makeText(navController.context, "Не удалось подготовить фото: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Scaffold(containerColor = bg, bottomBar = {
        Surface(color = bg) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 0) {
                    TextButton(onClick = { step-- }, enabled = !saving) { Text("Назад", color = accent) }
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { if (step < 3) step++ else finish() },
                    enabled = if (step == 2) displayName.isNotBlank() && !saving else !saving,
                    colors = ButtonDefaults.buttonColors(containerColor = border.copy(alpha = .65f), contentColor = accent)
                ) { Text(if (step < 3) "Далее" else "Готово") }
            }
        }
    }) { pad ->
        Card(
            modifier = Modifier.fillMaxSize().padding(pad).padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = card),
            border = BorderStroke(2.dp, border),
            shape = RoundedCornerShape(20.dp)
        ) {
            when (step) {
                0 -> MommyIntro(accent)
                1 -> MommyRoleStep(accent)
                2 -> NameAvatarStep(
                    accent = accent,
                    name = displayName,
                    onNameChange = { displayName = it },
                    avatarUri = localAvatar,
                    onPickAvatar = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                )
                else -> ThemeStep(accent, theme) { theme = it }
            }
        }
    }
}

@Composable
private fun MommyIntro(accent: Color) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Добро пожаловать!", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(8.dp))
        Text("Сейчас мы познакомим тебя с приложением и подготовим самое нужное. Без звука и команд.", fontSize = 16.sp, color = accent)
    }
}

@Composable
private fun MommyRoleStep(accent: Color) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Роль Мамочки", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(8.dp))
        Text("Ты задаёшь правила, привычки и режимы. Малыш видит только разрешённое и присылает отчёты. Всё — вручную и с любовью.", fontSize = 16.sp, color = accent, lineHeight = 20.sp)
    }
}

@Composable
private fun NameAvatarStep(
    accent: Color,
    name: String,
    onNameChange: (String) -> Unit,
    avatarUri: Uri?,
    onPickAvatar: () -> Unit
) {
    Column(Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(Color(0x33FFFFFF)), contentAlignment = Alignment.Center) {
            if (avatarUri != null) {
                AsyncImage(model = avatarUri, contentDescription = null,
                    modifier = Modifier.fillMaxSize().clip(CircleShape))
            } else {
                Text("M", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2A2A2A))
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = name, onValueChange = onNameChange,
            label = { Text("Имя Мамочки") }, singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onPickAvatar) { Text("Выбрать фото (по желанию)", color = accent) }
    }
}

@Composable
private fun ThemeStep(accent: Color, selected: String, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Выбери тему приложения", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeChip("Мягкая", selected == "soft") { onSelect("soft") }
            ThemeChip("Строгая", selected == "strict") { onSelect("strict") }
            ThemeChip("Нейтральная", selected == "neutral") { onSelect("neutral") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Тему можно поменять позже в настройках.", color = accent)
    }
}

@Composable
private fun ThemeChip(text: String, sel: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick,
        tonalElevation = if (sel) 2.dp else 0.dp,
        shadowElevation = if (sel) 1.dp else 0.dp,
        color = if (sel) Color(0xFFF5D8CE) else Color.White,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = Color(0xFF552216), fontWeight = FontWeight.Medium)
    }
}

private fun makeDataUrlFromUri(
    context: Context,
    uri: Uri,
    maxSize: Int = 384,
    quality: Int = 85
): String {
    context.contentResolver.openInputStream(uri).use { input ->
        requireNotNull(input) { "InputStream is null" }
        val original = BitmapFactory.decodeStream(input)
            ?: throw IllegalArgumentException("Failed to decode bitmap")

        val (w, h) = original.width to original.height
        val scale = if (max(w, h) > maxSize) maxSize.toFloat() / max(w, h) else 1f
        val targetW = max(1, (w * scale).toInt())
        val targetH = max(1, (h * scale).toInt())

        val scaled: Bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        } else original

        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
        if (scaled !== original) original.recycle()
        return "data:image/jpeg;base64,$base64"
    }
}