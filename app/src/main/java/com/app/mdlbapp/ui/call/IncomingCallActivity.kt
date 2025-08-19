package com.app.mdlbapp.ui.call

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.app.mdlbapp.MainActivity
import com.app.mdlbapp.R
import com.google.firebase.firestore.ktx.firestore


class IncomingCallActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        super.onCreate(savedInstanceState)

        val initialName   = intent.getStringExtra("fromName") ?: "Мамочка"
        val initialAvatar = intent.getStringExtra("fromAvatar")
        val callerUid     = intent.getStringExtra("callerUid")

        setContent {
            MaterialTheme {
                var name   by remember { mutableStateOf(initialName) }
                var avatar by remember { mutableStateOf(initialAvatar) }

                // Живём на данных профиля собеседника
                DisposableEffect(callerUid) {
                    var reg: com.google.firebase.firestore.ListenerRegistration? = null
                    if (!callerUid.isNullOrBlank()) {
                        reg = com.google.firebase.ktx.Firebase.firestore
                            .collection("users")
                            .document(callerUid)
                            .addSnapshotListener { snap, _ ->
                                if (snap != null && snap.exists()) {
                                    name   = snap.getString("displayName") ?: name
                                    avatar = snap.getString("photoDataUrl")
                                        ?: snap.getString("photoUrl")
                                                ?: avatar
                                }
                            }
                    }
                    onDispose { reg?.remove() }
                }

                IncomingCallScreen(
                    name = name,
                    avatarUrl = avatar,
                    onAccept = {
                        startActivity(
                            Intent(this, MainActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                .putExtra("openCall", true)
                                .putExtras(intent.extras ?: Bundle())
                        )
                        finish()
                    },
                    onDecline = { finish() }
                )
            }
        }
    }
}

@Composable
private fun IncomingCallScreen(
    name: String,
    avatarUrl: String?,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    // нежный «плюшевый» фон без картинок
    val bg = Brush.verticalGradient(
        listOf(Color(0xFF18122B), Color(0xFF33294D), Color(0xFF4C3F78))
    )
    Surface(color = Color.Transparent) {
        Box(
            Modifier.fillMaxSize().background(bg).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // большая кругленькая ава
                BigAvatar(avatarUrl)

                Text("Звонок MDLBApp", style = MaterialTheme.typography.titleMedium, color = Color(0xFFEDE7F6))
                Text(name, style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = Color.White)

                Spacer(Modifier.height(24.dp))

                // две круглые кнопочки напротив
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // отклонить — красная
                    LargeRoundButton(
                        label = "Отклонить",
                        container = Color(0xFFE74C3C),
                        icon = { Icon(Icons.Rounded.CallEnd, contentDescription = null, tint = Color.White) },
                        onClick = onDecline
                    )
                    // принять — зелёная
                    LargeRoundButton(
                        label = "Принять",
                        container = Color(0xFF2ECC71),
                        icon = { Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.White) },
                        onClick = onAccept
                    )
                }
            }
        }
    }
}

@Composable
private fun BigAvatar(photo: String?, size: Dp = 160.dp) {
    val fallback = painterResource(R.drawable.ic_rule_name)

    val dataBitmap = remember(photo) {
        if (!photo.isNullOrBlank() && photo.startsWith("data:image")) {
            try {
                val base64 = photo.substringAfter(",")
                val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?.asImageBitmap()
            } catch (_: Throwable) { null }
        } else null
    }

    if (dataBitmap != null) {
        Image(
            bitmap = dataBitmap,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else if (!photo.isNullOrBlank()) {
        AsyncImage(
            model = photo,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
            placeholder = fallback, error = fallback
        )
    } else {
        Image(
            painter = fallback,
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    }
}

@Composable
private fun LargeRoundButton(
    label: String,
    container: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = container),
            modifier = Modifier.size(88.dp)
        ) { icon() }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White)
    }
}