package com.app.mdlbapp.core.time

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate

class DateLabelTest {
    private val today = LocalDate.of(2025, 8, 11)

    @Test fun today_label()    = assertThat(formatDateLabel("2025-08-11", today)).isEqualTo("Сегодня")
    @Test fun tomorrow_label() = assertThat(formatDateLabel("2025-08-12", today)).isEqualTo("Завтра")
    @Test fun other_date_iso() = assertThat(formatDateLabel("2025-08-15", today)).isEqualTo("2025-08-15")
}