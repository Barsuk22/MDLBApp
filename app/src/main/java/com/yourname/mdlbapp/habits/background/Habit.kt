package com.yourname.mdlbapp.habits.background

data class Habit(
    var id: String = "",
    var title: String = "",
    var repeat: String = "",
    var daysOfWeek: List<String>? = null,
    var oneTimeDate: String? = null,
    var nextDueDate: String? = null,
    var deadline: String = "",
    var reportType: String = "",
    var category: String = "",
    var points: Int = 0,
    var penalty: Int = 0,
    var reaction: String = "",
    var reminder: String = "",
    var status: String = "on",
    var createdAt: Long = 0L,
    var currentStreak: Int = 0,
    var longestStreak: Int = 0,
    var completedToday: Boolean = false,
    var mommyUid: String = "",
    var babyUid: String = ""
)