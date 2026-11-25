package io.legado.app.ui.book.info

import android.app.Application
import android.content.Context
import io.legado.app.constant.PreferKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = Application::class)
class BookInfoOtherWorksPreferencesTest {
    private val prefs = RuntimeEnvironment.getApplication()
        .getSharedPreferences("otherWorksPreferencesTest", Context.MODE_PRIVATE)

    @Test
    fun autoLoadIsEnabledByDefaultForVisibleSection() {
        val preferences = BookInfoOtherWorksPreferences.read(prefs)
        assertTrue(preferences.autoLoad)
        assertTrue(preferences.allowsSearch(background = true))
    }

    @Test
    fun explicitOffBlocksOnlyAutomaticSearchWhenSectionIsVisible() {
        prefs.edit().putBoolean(PreferKey.autoLoadBookOtherWorks, false).apply()

        val preferences = BookInfoOtherWorksPreferences.read(prefs)
        assertFalse(preferences.autoLoad)
        assertFalse(preferences.allowsSearch(background = true))
        assertTrue(preferences.allowsSearch(background = false))
    }

    @Test
    fun savingVisibilityControlsRequestsAndPreviewDoesNot() {
        val hidden = BookInfoLayout().setVisible(BookInfoSection.OTHER_WORKS, false)
        assertTrue(BookInfoOtherWorksPreferences.read(prefs).allowsSearch(background = true))

        BookInfoLayoutStore.save(prefs, hidden)
        val preferences = BookInfoOtherWorksPreferences.read(prefs)
        assertTrue(preferences.autoLoad)
        assertFalse(preferences.allowsSearch(background = true))
        assertFalse(preferences.allowsSearch(background = false))

        BookInfoLayoutStore.save(prefs, hidden.setVisible(BookInfoSection.OTHER_WORKS, true))
        assertTrue(BookInfoOtherWorksPreferences.read(prefs).allowsSearch(background = true))
    }
}
