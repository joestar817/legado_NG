package io.legado.app.help.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
class VolumeKeyPageDefaultTest {
    @Test
    fun unsetPreferenceDefaultsOffAndExplicitValuesArePreserved() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        val existed = preferences.contains(PreferKey.volumeKeyPage)
        val original = preferences.getBoolean(PreferKey.volumeKeyPage, false)
        try {
            preferences.edit().remove(PreferKey.volumeKeyPage).commit()
            assertFalse(AppConfig.volumeKeyPage)
            assertFalse(ReadPresetPreferences.capture().get(PreferKey.volumeKeyPage).asBoolean)
            preferences.edit().putBoolean(PreferKey.volumeKeyPage, true).commit()
            assertTrue(AppConfig.volumeKeyPage)
            assertTrue(ReadPresetPreferences.capture().get(PreferKey.volumeKeyPage).asBoolean)
            preferences.edit().putBoolean(PreferKey.volumeKeyPage, false).commit()
            assertFalse(AppConfig.volumeKeyPage)
        } finally {
            val editor = preferences.edit()
            if (existed) editor.putBoolean(PreferKey.volumeKeyPage, original)
            else editor.remove(PreferKey.volumeKeyPage)
            editor.commit()
        }
    }
}
