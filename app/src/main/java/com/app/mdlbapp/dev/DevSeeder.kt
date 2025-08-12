import android.util.Log
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object DevSeeder {

    /**
     * Посеять только то, чего НЕ хватает у пары.
     * Если удалил только привычки — досеет привычки; если удалил награды — досеет награды.
     */
    suspend fun seedIfEmpty(mommyUid: String, babyUid: String) {
        val db = Firebase.firestore

        val hasHabits = db.collection("habits")
            .whereEqualTo("mommyUid", mommyUid)
            .whereEqualTo("babyUid",  babyUid)
            .limit(1)
            .get().await()
            .isEmpty.not()

        val hasRewards = db.collection("rewards")
            .whereEqualTo("createdBy", mommyUid)
            .whereEqualTo("targetUid", babyUid)
            .limit(1)
            .get().await()
            .isEmpty.not()

        if (!hasHabits) seedHabits(mommyUid, babyUid)
        if (!hasRewards) seedRewards(mommyUid, babyUid)

        Log.d("DevSeeder","seedIfEmpty done: habits=$hasHabits, rewards=$hasRewards")
    }

    // --- отдельная подсевка привычек ---
    private suspend fun seedHabits(mommyUid: String, babyUid: String) {
        val db = Firebase.firestore
        val today = LocalDate.now()
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE

        val habits = listOf(
            mapOf(
                "title" to "Фото завтрака",
                "repeat" to "daily",
                "deadline" to "16:00",
                "nextDueDate" to today.format(fmt),
                "reportType" to "photo",
                "points" to 5, "penalty" to -10,
                "completedToday" to false,
                "currentStreak" to 0, "longestStreak" to 0,
                "status" to "on", "mommyUid" to mommyUid, "babyUid" to babyUid
            ),
            mapOf(
                "title" to "Фото обеда",
                "repeat" to "daily",
                "deadline" to "17:00",
                "nextDueDate" to today.format(fmt),
                "reportType" to "photo",
                "points" to 5, "penalty" to -10,
                "completedToday" to false,
                "currentStreak" to 0, "longestStreak" to 0,
                "status" to "on", "mommyUid" to mommyUid, "babyUid" to babyUid
            ),
            mapOf(
                "title" to "Фото ужина",
                "repeat" to "daily",
                "deadline" to "18:00",
                "nextDueDate" to today.format(fmt),
                "reportType" to "photo",
                "points" to 10, "penalty" to -20,
                "completedToday" to false,
                "currentStreak" to 0, "longestStreak" to 0,
                "status" to "on", "mommyUid" to mommyUid, "babyUid" to babyUid
            ),
            mapOf(
                "title" to "Учёба 20 минут",
                "repeat" to "weekly",
                "daysOfWeek" to listOf("Пн","Ср","Пт"),
                "deadline" to "18:00",
                "nextDueDate" to today.format(fmt),
                "reportType" to "none",
                "points" to 10, "penalty" to -15,
                "completedToday" to false,
                "currentStreak" to 0, "longestStreak" to 0,
                "status" to "on", "mommyUid" to mommyUid, "babyUid" to babyUid
            ),
            mapOf(
                "title" to "Разовое поручение",
                "repeat" to "once",
                "oneTimeDate" to today.plusDays(2).format(fmt),
                "deadline" to "23:59",
                "nextDueDate" to today.plusDays(2).format(fmt),
                "reportType" to "text",
                "points" to 7, "penalty" to -5,
                "completedToday" to false,
                "currentStreak" to 0, "longestStreak" to 0,
                "status" to "on", "mommyUid" to mommyUid, "babyUid" to babyUid
            ),
            mapOf(
                "title" to "Еженедельная порка",
                "repeat" to "weekly",
                "daysOfWeek" to listOf("Вс"),
                "deadline" to "19:00",
                "nextDueDate" to today.format(fmt),
                "reportType" to "none",
                "points" to 10, "penalty" to -15,
                "completedToday" to false,
                "currentStreak" to 0, "longestStreak" to 0,
                "status" to "on", "mommyUid" to mommyUid, "babyUid" to babyUid
            )
        )

        val batch = db.batch()
        habits.forEach { batch.set(db.collection("habits").document(), it) }
        batch.commit().await()
    }

    // --- отдельная подсевка наград ---
    private suspend fun seedRewards(mommyUid: String, babyUid: String) {
        val db = Firebase.firestore
        val now = System.currentTimeMillis()

        val rewards = listOf(
            mapOf(
                "title" to "Обнимашки",
                "description" to "Тёплые обнимашки от Мамочки",
                "cost" to 3,
                "type" to "Эмоциональная",
                "autoApprove" to true,
                "limit" to "Без ограничений",
                "messageFromMommy" to "Иди к мамочке 💗",
                "createdBy" to mommyUid,
                "targetUid" to babyUid,
                "createdAt" to now,
                "pending" to false,
                "pendingBy" to null
            ),
            mapOf(
                "title" to "Мультик 15 мин",
                "description" to "Небольшая радость для Малыша",
                "cost" to 5,
                "type" to "Поведенческая",
                "autoApprove" to false,
                "limit" to "1 в день",
                "messageFromMommy" to "Сначала обязанности — потом мультик 😉",
                "createdBy" to mommyUid,
                "targetUid" to babyUid,
                "createdAt" to now,
                "pending" to false,
                "pendingBy" to null
            )
        )

        val batch = db.batch()
        rewards.forEach { batch.set(db.collection("rewards").document(), it) }
        batch.commit().await()
    }
}