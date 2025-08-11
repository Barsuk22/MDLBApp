package com.app.mdlbapp.reward.logic

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RewardLogicTest {

    @Test fun buy_auto_approve_ok() {
        val d = RewardLogic.decide(
            PurchaseInput(balance = 10, cost = 7, needMommyApprove = false, babyUid = "b1")
        )
        assertThat(d.canBuy).isTrue()
        assertThat(d.pointsDelta).isEqualTo(-7)
        assertThat(d.setPending).isFalse()
        assertThat(d.pendingBy).isNull()
    }

    @Test fun buy_needs_approve_ok() {
        val d = RewardLogic.decide(
            PurchaseInput(balance = 15, cost = 5, needMommyApprove = true, babyUid = "baby")
        )
        assertThat(d.canBuy).isTrue()
        assertThat(d.pointsDelta).isEqualTo(-5)
        assertThat(d.setPending).isTrue()
        assertThat(d.pendingBy).isEqualTo("baby")
    }

    @Test fun buy_not_enough_points() {
        val d = RewardLogic.decide(
            PurchaseInput(balance = 3, cost = 5, needMommyApprove = false, babyUid = "b")
        )
        assertThat(d.canBuy).isFalse()
        assertThat(d.pointsDelta).isEqualTo(0)
        assertThat(d.setPending).isFalse()
    }

    @Test fun buy_zero_cost_rejected() {
        val d = RewardLogic.decide(
            PurchaseInput(balance = 100, cost = 0, needMommyApprove = false, babyUid = "b")
        )
        assertThat(d.canBuy).isFalse()
    }
}