package com.app.mdlbapp.habits.data

import android.content.Context
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.app.mdlbapp.core.Constants
import com.app.mdlbapp.reward.changePointsAsync
import com.app.mdlbapp.rule.computeNextDueDateForHabit
import com.app.mdlbapp.rule.getNextDueDate
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

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
        "currentStreak" to 0L,
        "reactionImageRes" to reactionImageRes,
    )

    Firebase.firestore.collection("habits")
        .add(habit)
        .addOnSuccessListener { onComplete(true, null) }
        .addOnFailureListener { e -> onComplete(false, e.localizedMessage) }
}

fun updateHabitsNextDueDate(
    habits: List<Map<String, Any?>>,
    fromDate: LocalDate = LocalDate.now(),
    rollTime: LocalTime = LocalTime.MIDNIGHT
) {
    val db       = Firebase.firestore
    val isoFmt   = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    val nowTime = rollTime

    habits.forEach { habit ->
        val status = habit["status"] as? String ?: "on"
        if (status != "on") return@forEach

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
                if (parsed.isBefore(fromDate)) {
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

        val lastPenaltyDateStr = habit["lastPenaltyDate"] as? String
        val lastPenaltyDate = lastPenaltyDateStr?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

        // если предыдущий due-дата уже в прошлом и не была выполнена — сбрасываем серию
        // и применяем штраф к общему счёту малыша
        if (oldDate != null && oldDate.isBefore(fromDate) && !completed) {
            // штрафуем ТОЛЬКО если ещё не штрафовали вчера в дедлайн
            if (lastPenaltyDate == null || lastPenaltyDate != oldDate) {
                updates["currentStreak"] = 0L
                val babyUid   = habit["babyUid"] as? String
                val penaltyVal = (habit["penalty"] as? Long ?: 0L).toInt()
                if (babyUid != null && penaltyVal != 0) {
                    try { changePointsAsync(babyUid, penaltyVal) } catch (_: Exception) {}
                }
                // помечаем, что штраф за вчера уже списан
                updates["lastPenaltyDate"] = oldDate.toString()
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






