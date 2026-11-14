package io.legado.app.ui.book.read

import android.content.Context
import androidx.core.graphics.ColorUtils
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgColorConfigStore
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.ReadAppearanceMode
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgManualColorSet
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.defaultSharedPreferences
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ReadDrawerStyleTest {

    @Test
    fun `unconfigured independent multicolor night uses the original blue palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val preferences = context.defaultSharedPreferences
        val customNightPrimaryWasPresent = preferences.contains(PreferKey.ngColorDarkPrimary)
        val originalCustomNightPrimary = preferences.getInt(PreferKey.ngColorDarkPrimary, 0)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed
        val originalNightSeed = config.readFloatingSeedNight
        val originalNightFollow = config.readFloatingFollowAppNight

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = true
            ReadBookConfig.readFloatingFollowAppGlobally = false
            config.readFloatingSeed = 0xFFF8E4D2.toInt()
            config.readFloatingSeedNight = 0
            config.readFloatingFollowAppNight = null
            context.putPrefString(PreferKey.ngThemePresentationMode, "multicolor")

            assertFalse(ReadBookConfig.effectiveReadFloatingColor().followsApplication)
            val snapshot = ReadDrawerStyle.themeSnapshot(context)
            assertTrue(snapshot.isDark)
            assertEquals(0xFF12314D.toInt(), snapshot.colors.surface)
            assertEquals(0xFF5CCBFF.toInt(), snapshot.colors.surfaceTint)

            config.readFloatingSeedNight = 0xFF41566B.toInt()
            config.readFloatingFollowAppNight = false
            assertEquals(
                config.readFloatingSeedNight,
                ReadDrawerStyle.themeSnapshot(context).colors.surfaceTint,
            )

            config.readFloatingSeedNight = 0
            config.readFloatingFollowAppNight = null
            preferences.edit()
                .putInt(PreferKey.ngColorDarkPrimary, 0xFF2F8D5D.toInt())
                .apply()
            assertEquals(snapshot, ReadDrawerStyle.themeSnapshot(context))
        } finally {
            config.readFloatingSeed = originalSeed
            config.readFloatingSeedNight = originalNightSeed
            config.readFloatingFollowAppNight = originalNightFollow
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
            if (customNightPrimaryWasPresent) {
                preferences.edit().putInt(PreferKey.ngColorDarkPrimary, originalCustomNightPrimary).apply()
            } else {
                preferences.edit().remove(PreferKey.ngColorDarkPrimary).apply()
            }
        }
    }

    @Test
    fun `independent autumn colors do not inherit a saved green application theme`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.defaultSharedPreferences
        val originalColors = NgColorConfigStore.current(context)
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val customEditedWasPresent = preferences.contains(PreferKey.ngCustomAppearanceEdited)
        val originalCustomEdited = preferences.getBoolean(PreferKey.ngCustomAppearanceEdited, false)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed
        val originalNightSeed = config.readFloatingSeedNight
        val originalNightFollow = config.readFloatingFollowAppNight
        val green = NgManualColorSet(
            primary = 0xFF2F8D5D.toInt(),
            secondary = 0xFFF5FFF7.toInt(),
            primaryText = 0xFF08150E.toInt(),
            secondaryText = 0xFF3D5D4C.toInt(),
            background = 0xFFEDF8EF.toInt(),
            labelContainer = 0xFFE7F5EB.toInt(),
        )

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.readFloatingFollowAppGlobally = false
            config.readFloatingSeed = 0xFFF8E4D2.toInt()
            config.readFloatingSeedNight = 0
            config.readFloatingFollowAppNight = null
            context.putPrefBoolean(PreferKey.ngCustomAppearanceEdited, true)
            context.putPrefString(PreferKey.ngThemePresentationMode, "multicolor")
            NgColorConfigStore.update(context, originalColors.copy(
                mode = NgColorGenerationMode.MANUAL,
                manualLight = green,
                manualDark = green,
            ))

            ReadBookConfig.isNightTheme = false
            val day = ReadDrawerStyle.themeSnapshot(context)
            assertEquals(0xFFF8E4D2.toInt(), day.colors.surfaceTint)
            assertEquals(0xFFEEEEEE.toInt(), day.colors.surface)
            assertEquals(0xFF1D1B20.toInt(), day.colors.onSurfaceVariant)
            val nightPreview = ReadDrawerStyle.themeSnapshotForMode(context, config, ReadAppearanceMode.NIGHT)
            assertEquals(0xFF5CCBFF.toInt(), nightPreview.colors.surfaceTint)
            assertFalse(ReadBookConfig.isNightTheme)

            ReadBookConfig.isNightTheme = true
            val night = ReadDrawerStyle.themeSnapshot(context)
            assertEquals(0xFF12314D.toInt(), night.colors.surface)
            assertEquals(0xFF5CCBFF.toInt(), night.colors.surfaceTint)
            assertEquals(day, ReadDrawerStyle.themeSnapshotForMode(context, config, ReadAppearanceMode.DAY))
            assertTrue(ReadBookConfig.isNightTheme)
            listOf("soft_gradient", "standard").forEach { presentation ->
                context.putPrefString(PreferKey.ngThemePresentationMode, presentation)
                assertEquals(night, ReadDrawerStyle.themeSnapshot(context))
            }

            config.readFloatingSeedNight = 0xFF41566B.toInt()
            config.readFloatingFollowAppNight = false
            assertEquals(0xFF41566B.toInt(), ReadDrawerStyle.themeSnapshot(context).colors.surfaceTint)

            context.putPrefString(PreferKey.ngThemePresentationMode, "multicolor")
            config.readFloatingSeedNight = 0
            config.readFloatingFollowAppNight = true
            assertFalse(ReadBookConfig.effectiveReadFloatingColor().followsApplication)
            assertEquals(night, ReadDrawerStyle.themeSnapshot(context))
            ReadBookConfig.readFloatingFollowAppGlobally = true
            val followsApplication = ReadFloatingPalette.applySemanticRoles(
                snapshot = NgThemeResolver.resolve(context),
                primaryStrengthPercent = config.curReadFloatingPrimaryStrength(),
                colorStyle = ReadBookConfig.readFloatingGlobalColorStyle,
            )
            assertTrue(ReadBookConfig.effectiveReadFloatingColor().followsApplication)
            assertEquals(followsApplication, ReadDrawerStyle.themeSnapshot(context))

            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
            ReadBookConfig.isNightTheme = false
            val targetNight = ReadDrawerStyle.themeSnapshotForMode(context, config, ReadAppearanceMode.NIGHT)
            val expectedTargetNight = ReadFloatingPalette.applySemanticRoles(
                snapshot = NgThemeResolver.resolve(context, NgColorConfigStore.current(context), isDark = true),
                primaryStrengthPercent = config.curReadFloatingPrimaryStrength(),
                colorStyle = ReadBookConfig.readFloatingGlobalColorStyle,
            )
            assertEquals(expectedTargetNight, targetNight)
            assertTrue(targetNight.isDark)
            assertFalse(ReadBookConfig.isNightTheme)
        } finally {
            config.readFloatingSeed = originalSeed
            config.readFloatingSeedNight = originalNightSeed
            config.readFloatingFollowAppNight = originalNightFollow
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
            if (customEditedWasPresent) {
                context.putPrefBoolean(PreferKey.ngCustomAppearanceEdited, originalCustomEdited)
            } else {
                preferences.edit().remove(PreferKey.ngCustomAppearanceEdited).apply()
            }
            NgColorConfigStore.update(context, originalColors)
        }
    }

    @Test
    fun `every built in preset resolves both modes without changing the reader mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val asset = sequenceOf(
            File("src/main/assets/defaultData/readConfig.json"),
            File("app/src/main/assets/defaultData/readConfig.json"),
        ).first(File::isFile)
        val presets = GSON.fromJsonArray<ReadBookConfig.Config>(asset.readText()).getOrThrow()

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = false
            ReadBookConfig.readFloatingFollowAppGlobally = false
            context.putPrefString(PreferKey.ngThemePresentationMode, "multicolor")
            assertEquals(6, presets.size)
            presets.forEach { preset ->
                val originalPreset = GSON.toJson(preset)
                val day = ReadDrawerStyle.themeSnapshotForMode(context, preset, ReadAppearanceMode.DAY)
                val night = ReadDrawerStyle.themeSnapshotForMode(context, preset, ReadAppearanceMode.NIGHT)
                listOf(day, night).forEach { snapshot ->
                    val card = readingContentCardSnapshot(snapshot, preset.curReadFloatingPrimaryStrength())
                    if (!snapshot.isDark) assertEquals(0xFFFFFFFF.toInt(), card.colors.cardContainer)
                    assertTrue(ColorUtils.calculateContrast(card.colors.onSurface, card.colors.cardContainer) >= 4.5)
                    assertTrue(ColorUtils.calculateContrast(card.colors.onSurfaceVariant, card.colors.cardContainer) >= 4.5)
                }
                assertEquals(preset.readFloatingSeed.takeIf { it != 0 } ?: 0xFFE53935.toInt(), day.colors.surfaceTint)
                assertEquals(0xFF1D1B20.toInt(), day.colors.onSurfaceVariant)
                assertEquals(0xFF12314D.toInt(), night.colors.surface)
                assertEquals(0xFF5CCBFF.toInt(), night.colors.surfaceTint)
                assertEquals(originalPreset, GSON.toJson(preset))
                assertFalse(ReadBookConfig.isNightTheme)
            }
        } finally {
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
        }
    }

    @Test
    fun `multicolor follow app uses the active application palette in both reading modes`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed
        val originalNightSeed = config.readFloatingSeedNight
        val originalNightFollow = config.readFloatingFollowAppNight

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.readFloatingFollowAppGlobally = true
            config.readFloatingSeed = 0xFFF8E4D2.toInt()
            config.readFloatingSeedNight = 0xFF41566B.toInt()
            config.readFloatingFollowAppNight = false
            context.putPrefString(PreferKey.ngThemePresentationMode, "multicolor")

            for (night in listOf(false, true)) {
                ReadBookConfig.isNightTheme = night
                val expected = ReadFloatingPalette.applySemanticRoles(
                    snapshot = NgThemeResolver.resolve(context),
                    primaryStrengthPercent = config.curReadFloatingPrimaryStrength(),
                    colorStyle = ReadBookConfig.readFloatingGlobalColorStyle,
                )

                assertTrue(ReadBookConfig.effectiveReadFloatingColor().followsApplication)
                assertEquals(expected, ReadDrawerStyle.themeSnapshot(context))
            }
            ReadBookConfig.readFloatingFollowAppGlobally = false
            assertEquals(0xFFF8E4D2.toInt(), ReadDrawerStyle.themeSnapshotForMode(context, config, ReadAppearanceMode.DAY).colors.surfaceTint)
            assertEquals(0xFF41566B.toInt(), ReadDrawerStyle.themeSnapshotForMode(context, config, ReadAppearanceMode.NIGHT).colors.surfaceTint)
        } finally {
            config.readFloatingSeed = originalSeed
            config.readFloatingSeedNight = originalNightSeed
            config.readFloatingFollowAppNight = originalNightFollow
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
        }
    }

    @Test
    fun `multicolor background color keeps the independent reading palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = false
            ReadBookConfig.readFloatingFollowAppGlobally = false
            config.readFloatingSeed = 0xFF8A5D3B.toInt()
            context.putPrefString(PreferKey.ngThemePresentationMode, "multicolor")
            val snapshot = ReadDrawerStyle.themeSnapshot(context)

            assertFalse(ReadBookConfig.effectiveReadFloatingColor().followsApplication)
            assertEquals(config.readFloatingSeed, snapshot.colors.surfaceTint)
            assertEquals(0xFFEEEEEE.toInt(), snapshot.colors.surface)
            assertEquals(0xFF1D1B20.toInt(), snapshot.colors.onSurfaceVariant)
        } finally {
            config.readFloatingSeed = originalSeed
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
        }
    }

    @Test
    fun `soft gradient follow app uses the active soft gradient palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val originalSoftGradientColor = context.getPrefString(PreferKey.ngSoftGradientColor)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = false
            ReadBookConfig.readFloatingFollowAppGlobally = true
            config.readFloatingSeed = 0
            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            context.putPrefString(
                PreferKey.ngSoftGradientColor,
                NgSoftGradientColorPreset.DUSK_VIOLET.storageValue,
            )
            val expected = ReadFloatingPalette.applySemanticRoles(
                snapshot = NgThemeResolver.resolve(context),
                primaryStrengthPercent = config.curReadFloatingPrimaryStrength(),
                colorStyle = ReadBookConfig.readFloatingGlobalColorStyle,
            )

            assertEquals(expected, ReadDrawerStyle.themeSnapshot(context))
            assertFalse(ThemeConfig.isReadingNgBackgroundTheme(context))
        } finally {
            config.readFloatingSeed = originalSeed
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
            context.putPrefString(PreferKey.ngSoftGradientColor, originalSoftGradientColor)
        }
    }

    @Test
    fun `soft gradient legacy night without explicit follow keeps the independent palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val preferences = context.defaultSharedPreferences
        val customEditedWasPresent = preferences.contains(PreferKey.ngCustomAppearanceEdited)
        val originalCustomEdited = preferences.getBoolean(PreferKey.ngCustomAppearanceEdited, false)
        val originalSoftGradientColor = context.getPrefString(PreferKey.ngSoftGradientColor)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed
        val originalNightSeed = config.readFloatingSeedNight
        val originalNightFollow = config.readFloatingFollowAppNight

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = true
            ReadBookConfig.readFloatingFollowAppGlobally = false
            config.readFloatingSeed = 0xFFF8E4D2.toInt()
            config.readFloatingSeedNight = 0
            config.readFloatingFollowAppNight = null
            context.putPrefBoolean(PreferKey.ngCustomAppearanceEdited, true)
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
            val standardSnapshot = ReadDrawerStyle.themeSnapshot(context)

            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            context.putPrefString(
                PreferKey.ngSoftGradientColor,
                NgSoftGradientColorPreset.DUSK_VIOLET.storageValue,
            )

            assertFalse(ReadBookConfig.effectiveReadFloatingColor().followsApplication)
            assertEquals(standardSnapshot, ReadDrawerStyle.themeSnapshot(context))
            assertFalse(ThemeConfig.isReadingNgBackgroundTheme(context))
        } finally {
            config.readFloatingSeed = originalSeed
            config.readFloatingSeedNight = originalNightSeed
            config.readFloatingFollowAppNight = originalNightFollow
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            if (customEditedWasPresent) {
                context.putPrefBoolean(PreferKey.ngCustomAppearanceEdited, originalCustomEdited)
            } else {
                preferences.edit().remove(PreferKey.ngCustomAppearanceEdited).apply()
            }
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
            context.putPrefString(PreferKey.ngSoftGradientColor, originalSoftGradientColor)
        }
    }

    @Test
    fun `legacy night follow flag does not override an independent reading palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalGlobalFollow = ReadBookConfig.readFloatingFollowAppGlobally
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed
        val originalNightSeed = config.readFloatingSeedNight
        val originalNightFollow = config.readFloatingFollowAppNight

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = true
            ReadBookConfig.readFloatingFollowAppGlobally = false
            config.readFloatingSeed = 0xFFF8E4D2.toInt()
            config.readFloatingSeedNight = 0xFF41566B.toInt()
            config.readFloatingFollowAppNight = false
            config.clearCurReadFloatingSeed()
            config.readFloatingFollowAppNight = true
            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            val snapshot = ReadDrawerStyle.themeSnapshot(context)

            assertFalse(ReadBookConfig.effectiveReadFloatingColor().followsApplication)
            assertEquals(0xFF5CCBFF.toInt(), snapshot.colors.surfaceTint)
            assertEquals(0xFF12314D.toInt(), snapshot.colors.surface)
        } finally {
            config.readFloatingSeed = originalSeed
            config.readFloatingSeedNight = originalNightSeed
            config.readFloatingFollowAppNight = originalNightFollow
            ReadBookConfig.readFloatingFollowAppGlobally = originalGlobalFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
        }
    }

    @Test
    fun `soft gradient background color keeps the independent reading palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val originalSoftGradientColor = context.getPrefString(PreferKey.ngSoftGradientColor)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = false
            config.readFloatingSeed = 0xFF8A5D3B.toInt()
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
            val standardSnapshot = ReadDrawerStyle.themeSnapshot(context)

            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            context.putPrefString(
                PreferKey.ngSoftGradientColor,
                NgSoftGradientColorPreset.DUSK_VIOLET.storageValue,
            )

            assertEquals(standardSnapshot, ReadDrawerStyle.themeSnapshot(context))
            assertFalse(ThemeConfig.isReadingNgBackgroundTheme(context))
        } finally {
            config.readFloatingSeed = originalSeed
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
            context.putPrefString(PreferKey.ngSoftGradientColor, originalSoftGradientColor)
        }
    }

    @Test
    fun `eink still uses the forced monochrome snapshot`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode

        try {
            AppConfig.isEInkMode = true

            assertEquals(
                NgThemeResolver.resolve(context),
                ReadDrawerStyle.themeSnapshot(context),
            )
            assertFalse(ThemeConfig.isReadingNgBackgroundTheme(context))
        } finally {
            AppConfig.isEInkMode = originalEInkMode
        }
    }
}
