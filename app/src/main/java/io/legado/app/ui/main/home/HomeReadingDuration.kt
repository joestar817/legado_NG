package io.legado.app.ui.main.home

import java.text.NumberFormat
import java.util.Locale

internal enum class HomeReadingDurationUnit { HOURS, DAYS }

/** Display-only value; reading records continue to store elapsed milliseconds. */
internal data class HomeReadingDuration(val value: String, val unit: HomeReadingDurationUnit)

internal fun formatHomeReadingDuration(
    readTime: Long,
    locale: Locale = Locale.getDefault(),
): HomeReadingDuration {
    val duration = readTime.coerceAtLeast(0L)
    val days = duration >= 86_400_000L
    val value = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
        isGroupingUsed = false
    }.format(duration.toDouble() / if (days) 86_400_000.0 else 3_600_000.0)
    return HomeReadingDuration(value,
        if (days) HomeReadingDurationUnit.DAYS else HomeReadingDurationUnit.HOURS)
}
