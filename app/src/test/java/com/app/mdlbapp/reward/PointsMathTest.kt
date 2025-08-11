package com.app.mdlbapp.reward


import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PointsMathTest {
    @Test fun positive_delta() = assertThat(clampAfterDelta(10, 5)).isEqualTo(15)
    @Test fun clamp_below_zero() = assertThat(clampAfterDelta(3, -10)).isEqualTo(0)
}