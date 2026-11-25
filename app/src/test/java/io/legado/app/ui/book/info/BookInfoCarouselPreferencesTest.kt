package io.legado.app.ui.book.info

import android.app.Application
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class BookInfoCarouselPreferencesTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("bookInfoCarouselPreferencesTest", Context.MODE_PRIVATE)

    @Test
    fun defaultsToAutoPlayWithThreeSecondInterval() {
        assertEquals(
            BookInfoCarouselPreferences(autoPlay = true, intervalSeconds = 3),
            BookInfoCarouselPreferences.read(prefs),
        )
    }

    @Test
    fun savingAutoPlayOffKeepsTheChosenInterval() {
        val enabled = BookInfoCarouselPreferences(autoPlay = true, intervalSeconds = 9)
        enabled.save(prefs)
        assertEquals(enabled, BookInfoCarouselPreferences.read(prefs))

        val disabled = enabled.copy(autoPlay = false)
        disabled.save(prefs)
        assertEquals(disabled, BookInfoCarouselPreferences.read(prefs))
    }

    @Test
    fun outOfRangeIntervalsAreClampedOnReadAndWrite() {
        prefs.edit().putInt(BookInfoCarouselPreferences.KEY_INTERVAL, 1).apply()
        assertEquals(3, BookInfoCarouselPreferences.read(prefs).intervalSeconds)

        BookInfoCarouselPreferences(intervalSeconds = 100).save(prefs)
        assertEquals(15, prefs.getInt(BookInfoCarouselPreferences.KEY_INTERVAL, 0))
    }
}
