package io.legado.app.help.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.JsonObject
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ReadFixedDefaultsTest {
    @Test
    fun removedSettingsUseDefaultsDespiteSavedAndImportedValues() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        val oldValues = linkedMapOf(
            PreferKey.showReadTitleAddition to false,
            PreferKey.readBarStyleFollowPage to true,
            PreferKey.mouseWheelPage to false,
            PreferKey.useZhLayout to true,
            PreferKey.textFullJustify to false,
            PreferKey.textBottomJustify to false,
            PreferKey.adaptSpecialStyle to false,
            PreferKey.textSelectAble to false,
            PreferKey.optimizeRender to true,
        )
        val originalValues = (oldValues.keys + PreferKey.volumeKeyPage).associateWith { key ->
            if (preferences.contains(key)) preferences.getBoolean(key, false) else null
        }
        try {
            preferences.edit().apply {
                oldValues.forEach { (key, value) -> putBoolean(key, value) }
            }.commit()

            val imported = JsonObject().apply {
                oldValues.forEach { (key, value) -> addProperty(key, !value) }
                addProperty(PreferKey.volumeKeyPage, true)
            }
            ReadPresetPreferences.apply(imported)

            assertTrue(AppConfig.showReadTitleBarAddition)
            assertTrue(AppConfig.mouseWheelPage)
            assertTrue(AppConfig.adaptSpecialStyle)
            assertTrue(AppConfig.textSelectAble)
            assertFalse(AppConfig.optimizeRender)
            assertFalse(ReadBookConfig.useZhLayout)
            assertTrue(ReadBookConfig.textFullJustify)
            assertTrue(ReadBookConfig.textBottomJustify)
            val captured = ReadPresetPreferences.capture()
            oldValues.forEach { (key, value) ->
                assertEquals(value, preferences.getBoolean(key, !value))
                assertFalse(key in ReadPresetPreferences.preferenceKeys)
                assertFalse(captured.has(key))
            }
            assertTrue(AppConfig.volumeKeyPage)
            assertTrue(captured.get(PreferKey.volumeKeyPage).asBoolean)
        } finally {
            preferences.edit().apply {
                originalValues.forEach { (key, value) ->
                    if (value == null) remove(key) else putBoolean(key, value)
                }
            }.commit()
        }
    }
}
