package com.yourname.mdlbapp.data

interface HabitRepo {
    fun updateNextDueDate(habitId: String, isoDate: String)
}