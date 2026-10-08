package io.legado.app.ui.main.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class HomeReadingTimeTest {
    @Test fun `elapsed duration changes units at one day without altering milliseconds`() {
        assertEquals(HomeReadingDuration("0.0", HomeReadingDurationUnit.HOURS),
            formatHomeReadingDuration(-1L, Locale.US))
        assertEquals(HomeReadingDuration("1.5", HomeReadingDurationUnit.HOURS),
            formatHomeReadingDuration(5_400_000L, Locale.US))
        assertEquals(HomeReadingDuration("23.9", HomeReadingDurationUnit.HOURS),
            formatHomeReadingDuration(86_040_000L, Locale.US))
        assertEquals(HomeReadingDuration("1.0", HomeReadingDurationUnit.DAYS),
            formatHomeReadingDuration(86_400_000L, Locale.US))
        assertEquals(HomeReadingDuration("156.9", HomeReadingDurationUnit.DAYS),
            formatHomeReadingDuration(13_553_280_000L, Locale.US))
    }

    @Test fun `elapsed duration uses the locale decimal separator without grouping`() {
        assertEquals(HomeReadingDuration("1,5", HomeReadingDurationUnit.HOURS),
            formatHomeReadingDuration(5_400_000L, Locale.GERMANY))
        assertEquals(HomeReadingDuration("1234.5", HomeReadingDurationUnit.DAYS),
            formatHomeReadingDuration(106_660_800_000L, Locale.US))
    }

    @Test fun `last read includes the full date and minutes in the display time zone`() {
        val timestamp = 1_609_437_600_000L // 2020-12-31 18:00 UTC
        assertEquals("2020-12-31 18:00", formatHomeReadingLastRead(timestamp,
            Locale.US, TimeZone.getTimeZone("UTC")))
        assertEquals("2021-01-01 02:00", formatHomeReadingLastRead(timestamp,
            Locale.CHINA, TimeZone.getTimeZone("Asia/Shanghai")))
    }

    @Test fun `unavailable last read does not display the epoch date`() {
        assertEquals("—", formatHomeReadingLastRead(0L))
        assertEquals("—", formatHomeReadingLastRead(-1L))
    }
}
