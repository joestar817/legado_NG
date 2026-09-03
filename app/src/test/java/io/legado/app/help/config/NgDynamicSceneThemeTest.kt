package io.legado.app.help.config

import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NgDynamicSceneThemeTest {

    @Test
    fun `scene color overrides are isolated by preset`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clearScenePreferences(context)
        try {
            val customizedSakura = NgBuiltInThemes.sakura.colors.copy(
                manualLight = NgBuiltInThemes.sakura.colors.manualLight.copy(
                    primary = 0xFF123456.toInt(),
                ),
            )
            NgDynamicSceneTheme.migrateLegacyColorsIfCustomized(
                context,
                ListeningCartoonType.SAKURA,
                customizedSakura,
            )

            NgDynamicSceneTheme.select(context, ListeningCartoonType.SAKURA)
            assertEquals(customizedSakura.normalized(), NgDynamicSceneTheme.colors(context))

            NgDynamicSceneTheme.select(context, ListeningCartoonType.CATS)
            assertEquals(NgBuiltInThemes.cats.colors, NgDynamicSceneTheme.colors(context))
        } finally {
            clearScenePreferences(context)
        }
    }

    @Test
    fun `legacy untouched defaults adopt new scene primary colors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        clearScenePreferences(context)
        try {
            val legacySakura = NgBuiltInThemes.sakura.colors.copy(
                lightSeed = 0xFFFFA3D1.toInt(),
                darkSeed = 0xFFFFA3D1.toInt(),
                manualLight = NgBuiltInThemes.sakura.colors.manualLight.copy(
                    primary = 0xFFFFA3D1.toInt(),
                ),
                manualDark = NgBuiltInThemes.sakura.colors.manualDark.copy(
                    primary = 0xFFFFA3D1.toInt(),
                ),
            )
            NgDynamicSceneTheme.migrateLegacyColorsIfCustomized(
                context,
                ListeningCartoonType.SAKURA,
                legacySakura,
            )
            NgDynamicSceneTheme.select(context, ListeningCartoonType.SAKURA)

            assertEquals(NgBuiltInThemes.sakura.colors, NgDynamicSceneTheme.colors(context))
        } finally {
            clearScenePreferences(context)
        }
    }

    private fun clearScenePreferences(context: Context) {
        context.defaultSharedPreferences.edit(commit = true) {
            remove(PreferKey.ngDynamicScenePreset)
            remove(PreferKey.ngDynamicSceneSakuraColors)
            remove(PreferKey.ngDynamicSceneCatsColors)
            putString(PreferKey.ngThemePresentationMode, "standard")
        }
    }
}
