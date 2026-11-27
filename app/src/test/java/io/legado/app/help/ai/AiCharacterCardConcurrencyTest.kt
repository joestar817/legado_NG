package io.legado.app.help.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class AiCharacterCardConcurrencyTest {
    @Test
    fun defaultsToTwoAndKeepsStoredAndEffectiveLimitsWithinRange() {
        val preferences = ApplicationProvider.getApplicationContext<Context>().defaultSharedPreferences
        val key = PreferKey.aiCharacterCardConcurrencyLimit
        try {
            preferences.edit().remove(key).commit()
            assertEquals(2, AiConfig.characterCardConcurrencyLimit)

            for (limit in 1..5) {
                AiConfig.characterCardConcurrencyLimit = limit
                assertEquals(limit, preferences.getInt(key, 0))
                assertEquals(limit, AiConfig.characterCardConcurrencyLimit)
            }

            AiConfig.characterCardConcurrencyLimit = 0
            assertEquals(1, preferences.getInt(key, 0))
            AiConfig.characterCardConcurrencyLimit = 6
            assertEquals(5, preferences.getInt(key, 0))

            preferences.edit().putInt(key, 0).commit()
            assertEquals(1, AiConfig.characterCardConcurrencyLimit)
            preferences.edit().putInt(key, 6).commit()
            assertEquals(5, AiConfig.characterCardConcurrencyLimit)
        } finally {
            preferences.edit().remove(key).commit()
        }
    }
}
