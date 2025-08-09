package com.yourname.mdlbapp.reward

data class Reward(
    val title: String = "",
    val description: String = "",
    val cost: Int = 0,
    val type: String = "",
    val autoApprove: Boolean = false,
    val limit: String = "Без ограничений",
    val messageFromMommy: String = "",
    var id: String? = null,
    var createdAt: Long? = null,
    // Покупка, ожидающая подтверждения. Если true, Малыш запросил награду, но Мамочка ещё не подтвердила.
    var pending: Boolean = false,
    // UID пользователя, который запрашивает подтверждение награды (обычно это UID малыша). Null, если нет заявки.
    var pendingBy: String? = null
)