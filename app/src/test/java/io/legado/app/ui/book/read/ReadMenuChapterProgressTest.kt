package io.legado.app.ui.book.read

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ViewReadMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadPresetPreferences
import io.legado.app.help.storage.BackupModule
import io.legado.app.help.storage.BackupModules
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "en-rUS-w411dp-h891dp-xxhdpi", application = InterfaceFontTestApplication::class)
class ReadMenuChapterProgressTest {
    @Test
    fun preferenceDefaultsToVisibleAndIsStoredAsGlobalReaderSetting() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        val key = PreferKey.readMenuShowChapterProgress
        val existed = preferences.contains(key)
        val original = preferences.getBoolean(key, true)
        try {
            preferences.edit().remove(key).commit()
            assertTrue(AppConfig.readMenuShowChapterProgress)
            preferences.edit().putBoolean(key, false).commit()
            assertFalse(AppConfig.readMenuShowChapterProgress)
            assertFalse(ReadPresetPreferences.capture().has(key))
            assertEquals(BackupModule.READER, BackupModules.preferenceModule(key))
            assertTrue(BackupModules.includesPreference(key, setOf(BackupModule.READER.id)))
        } finally {
            val editor = preferences.edit()
            if (existed) editor.putBoolean(key, original) else editor.remove(key)
            editor.commit()
        }
    }

    @Test
    fun hidingChapterControlsShrinksTheBottomGlassAndKeepsActions() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(), R.style.AppTheme_Light,
        )
        val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context))
        fun measureBottom(): Int {
            binding.bottomGlassContainer.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            return binding.bottomGlassContainer.measuredHeight
        }

        binding.setChapterProgressVisible(true)
        val expandedHeight = measureBottom()
        assertTrue(binding.llChapterProgress.measuredHeight > 0)
        binding.setChapterProgressVisible(false)
        val compactHeight = measureBottom()
        assertEquals(View.GONE, binding.llChapterProgress.visibility)
        assertEquals(View.VISIBLE, binding.llBottomActionsPrimary.visibility)
        assertEquals(View.VISIBLE, binding.llCatalog.visibility)
        assertTrue(compactHeight < expandedHeight)
        binding.setChapterProgressVisible(true)
        assertEquals(View.VISIBLE, binding.llChapterProgress.visibility)
        assertEquals(expandedHeight, measureBottom())
    }
}
