package io.legado.app.ui.main.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

class HomeUpdatesCheckTimeTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private fun timestamp(instant: String): Long = Instant.parse(instant).toEpochMilli()

    @Test fun `a successful check on the same local day shows padded hours and minutes`() {
        assertEquals("07:04", formatHomeUpdatesCheckTime(timestamp("2026-10-05T23:04:35Z"),
            LocalDate.of(2026, 10, 6), shanghai, Locale.US))
    }

    @Test fun `a previous day in the same year includes its month and day`() {
        assertEquals("10-05 23:55", formatHomeUpdatesCheckTime(timestamp("2026-10-05T15:55:00Z"),
            LocalDate.of(2026, 10, 6), shanghai, Locale.SIMPLIFIED_CHINESE))
    }

    @Test fun `a check from the previous year retains its year`() {
        assertEquals("2025-12-31 23:05", formatHomeUpdatesCheckTime(timestamp("2025-12-31T15:05:00Z"),
            LocalDate.of(2026, 1, 1), shanghai, Locale.US))
    }

    @Test fun `day qualification follows the supplied time zone rather than the UTC date`() {
        val checkedAt = timestamp("2026-10-05T16:05:00Z")
        val today = LocalDate.of(2026, 10, 6)
        assertEquals("00:05", formatHomeUpdatesCheckTime(checkedAt, today, shanghai, Locale.US))
        assertEquals("10-05 16:05", formatHomeUpdatesCheckTime(checkedAt, today, ZoneId.of("UTC"), Locale.US))
    }

    @Test fun `a future-dated timestamp is not displayed as if it belongs to today`() {
        assertEquals("10-07 00:05", formatHomeUpdatesCheckTime(timestamp("2026-10-06T16:05:00Z"),
            LocalDate.of(2026, 10, 6), shanghai, Locale.US))
    }

    @Test fun `supported interface locales preserve an unambiguous numeric cross-year timestamp`() {
        val checkedAt = timestamp("2025-12-31T15:05:00Z")
        listOf(Locale.US, Locale.SIMPLIFIED_CHINESE).forEach { locale ->
            assertEquals("2025-12-31 23:05", formatHomeUpdatesCheckTime(checkedAt,
                LocalDate.of(2026, 1, 1), shanghai, locale))
        }
    }
}
