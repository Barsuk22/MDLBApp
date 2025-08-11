package com.app.mdlbapp.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate


class DateLabelTest {
    // понедельник
    private val today = LocalDate.of(2025, 8, 11)

    @Test fun today_label() {
        assertThat(formatDateLabel("2025-08-11", today)).isEqualTo("Сегодня")
    }

    @Test fun tomorrow_label() {
        assertThat(formatDateLabel("2025-08-12", today)).isEqualTo("Завтра")
    }

    @Test fun same_week_returns_weekday() {
        // пятница той же недели
        assertThat(formatDateLabel("2025-08-15", today)).isEqualTo("Пятница")
    }

    @Test fun next_week_returns_d_MMMM() {
        // следующая неделя
        assertThat(formatDateLabel("2025-08-20", today)).isEqualTo("20 августа")
    }
}