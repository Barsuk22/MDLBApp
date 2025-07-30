package com.yourname.mdlbapp

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class HabitsViewModel : ViewModel() {

    // публичный Flow, за который будет цепляться UI
    private val _habitsFlow = MutableStateFlow<List<Habit>>(emptyList())
    val habitsFlow: StateFlow<List<Habit>> = _habitsFlow

    init {
        // получаем uid и, только если он != null, регистрируем listener
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            Firebase.firestore
                .collection("habits")
                .whereEqualTo("mommyUid", uid)
                .addSnapshotListener { snap, err ->
                    if (err == null && snap != null) {
                        val list = snap.documents.mapNotNull { doc ->
                            doc.toObject(Habit::class.java)
                                ?.apply { id = doc.id }
                        }
                        _habitsFlow.value = list
                    }
                }
        }
    }
}