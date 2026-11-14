package io.legado.app.help.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class TextSelectionShowTextPreferenceTest {
    @Test
    fun defaultsToShownAndSurvivesReaderSettingsCaptureAndRestore() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        val existed = preferences.contains(PreferKey.textSelectionShowText)
        val original = preferences.getBoolean(PreferKey.textSelectionShowText, true)
        try {
            preferences.edit().remove(PreferKey.textSelectionShowText).commit()
            assertTrue(TextSelectionActionOrder.showText())
            assertTrue(ReadPresetPreferences.capture().get(PreferKey.textSelectionShowText).asBoolean)

            TextSelectionActionOrder.setShowText(false)
            val saved = ReadPresetPreferences.capture()
            assertFalse(saved.get(PreferKey.textSelectionShowText).asBoolean)
            ReadPresetPreferences.apply(JsonObject())
            assertFalse(TextSelectionActionOrder.showText())
            TextSelectionActionOrder.setShowText(true)
            ReadPresetPreferences.apply(JsonObject().apply {
                add(PreferKey.textSelectionShowText, saved.get(PreferKey.textSelectionShowText))
            })
            assertFalse(TextSelectionActionOrder.showText())
        } finally {
            val editor = preferences.edit()
            if (existed) editor.putBoolean(PreferKey.textSelectionShowText, original)
            else editor.remove(PreferKey.textSelectionShowText)
            editor.commit()
        }
    }
}
