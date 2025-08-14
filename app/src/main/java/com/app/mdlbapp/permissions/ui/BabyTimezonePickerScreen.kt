package com.app.mdlbapp.permissions.ui

import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.mdlbapp.habits.background.workers.HabitUpdateScheduler
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BabyTimezonePickerScreen(
    babyUid: String,
    onDone: () -> Unit
) {
    val ctx = LocalContext.current
    var saving by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }

    val zones = remember {
        ZoneId.getAvailableZoneIds().sorted().toList()
    }
    val filtered = remember(query, zones) {
        if (query.isBlank()) zones else zones.filter { it.contains(query.trim(), true) }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Поиск пояска") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth()
        ) {
            items(filtered) { tz ->
                val isSel = selected == tz
                TextButton(
                    onClick = { selected = tz },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tz, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isSel) Text("✓")
                    }
                }
            }
        }

        Button(
            onClick = {
                val tz = selected ?: return@Button
                saving = true
                val zone = ZoneId.of(tz)
                Firebase.firestore.collection("users").document(babyUid)
                    .update(mapOf("timezone" to tz))   // ← сохраняем в профиль Малыша
                    .addOnSuccessListener {
                        Log.d("TZ","picker: saved timezone=$tz; rescheduling…")
                        HabitUpdateScheduler.scheduleNext(ctx, zone)
                        saving = false
                        onDone()
                    }
                    .addOnFailureListener { e ->
                        saving = false
                        Log.e("TZ","picker: save failed", e)
                        Toast.makeText(ctx, "Не могу сохранить: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    }
            },
            enabled = !saving && selected != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (saving) "Сохраняю…" else "Сохранить")
        }
    }
}