package com.rombamukiyam.vault.data

import com.google.common.truth.Truth.assertThat
import com.rombamukiyam.vault.data.repository.ReminderRepositoryImpl
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Test

class ExpiryParsingTest {
    private fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

    @Test
    fun `card style MM slash YY expires at end of month`() {
        assertThat(date(ReminderRepositoryImpl.parseExpiry("09/27")!!)).isEqualTo(LocalDate.of(2027, 9, 30))
        assertThat(date(ReminderRepositoryImpl.parseExpiry("2/2028")!!)).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `iso and indian date formats`() {
        assertThat(date(ReminderRepositoryImpl.parseExpiry("2030-01-15")!!)).isEqualTo(LocalDate.of(2030, 1, 15))
        assertThat(date(ReminderRepositoryImpl.parseExpiry("15/01/2030")!!)).isEqualTo(LocalDate.of(2030, 1, 15))
        assertThat(date(ReminderRepositoryImpl.parseExpiry("2030-06")!!)).isEqualTo(LocalDate.of(2030, 6, 30))
    }

    @Test
    fun `garbage is rejected`() {
        assertThat(ReminderRepositoryImpl.parseExpiry("")).isNull()
        assertThat(ReminderRepositoryImpl.parseExpiry("soon")).isNull()
        assertThat(ReminderRepositoryImpl.parseExpiry("13/27")).isNull()
    }
}
