package com.rombamukiyam.vault.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.rombamukiyam.vault.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit

/** Dates are persisted as epoch millis / ISO strings; formatting happens only here, per locale. */
object Dates {
    private val medium: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    fun toLocalDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate()

    fun format(epochMillis: Long?): String? = epochMillis?.let { toLocalDate(it).format(medium) }

    fun format(date: LocalDate?): String? = date?.format(medium)

    fun daysBetween(fromMillis: Long, toMillis: Long): Long = ChronoUnit.DAYS.between(toLocalDate(fromMillis), toLocalDate(toMillis))

    fun parseIso(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

    fun startOfDayMillis(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

@Composable
fun relativeDays(targetMillis: Long, nowMillis: Long): String {
    val days = Dates.daysBetween(nowMillis, targetMillis).toInt()
    return when {
        days == 0 -> stringResource(R.string.detail_today)
        days > 0 -> stringResource(R.string.detail_in_days, days)
        else -> stringResource(R.string.detail_overdue_days, -days)
    }
}

@Composable
fun daysAgo(pastMillis: Long?, nowMillis: Long): String {
    if (pastMillis == null) return stringResource(R.string.detail_never)
    val days = Dates.daysBetween(pastMillis, nowMillis).toInt()
    return if (days <= 0) stringResource(R.string.detail_today) else stringResource(R.string.detail_days_ago, days)
}
