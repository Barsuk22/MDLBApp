package com.app.mdlbapp.reward.logic

data class PurchaseInput(
    val balance: Int,
    val cost: Int,
    val needMommyApprove: Boolean,
    val babyUid: String
)

data class PurchaseDecision(
    val canBuy: Boolean,
    val pointsDelta: Int,      // сколько менять очков (обычно отрицательное)
    val setPending: Boolean,   // ставить статус "ожидает"?
    val pendingBy: String?     // кто поставил в ожидание
)

object RewardLogic {
    fun decide(input: PurchaseInput): PurchaseDecision {
        if (input.cost <= 0) return PurchaseDecision(false, 0, false, null)
        if (input.balance < input.cost) return PurchaseDecision(false, 0, false, null)

        val delta = -input.cost
        val pend = input.needMommyApprove
        return PurchaseDecision(
            canBuy = true,
            pointsDelta = delta,
            setPending = pend,
            pendingBy = if (pend) input.babyUid else null
        )
    }
}