package io.legado.app.ui.main.home

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class HomeReadingTimeTest {
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
