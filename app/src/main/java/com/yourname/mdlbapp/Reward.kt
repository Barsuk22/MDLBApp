package com.yourname.mdlbapp

data class Reward(
    val title: String = "",
    val description: String = "",
    val cost: Int = 0,
    val type: String = "",
    val autoApprove: Boolean = false,
    val limit: String = "Без ограничений",
    val messageFromMommy: String = "",
    var id: String? = null,
    var createdAt: Long? = null
)