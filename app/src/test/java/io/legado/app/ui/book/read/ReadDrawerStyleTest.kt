package io.legado.app.ui.book.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadDrawerStyleTest {

    @Test
    fun `soft gradient follow app uses the active soft gradient palette`() {
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
            config.readFloatingSeed = 0
            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            context.putPrefString(
                PreferKey.ngSoftGradientColor,
                NgSoftGradientColorPreset.DUSK_VIOLET.storageValue,
            )
            val expected = ReadFloatingPalette.applySemanticRoles(
                snapshot = NgThemeResolver.resolve(context),
                primaryStrengthPercent = config.curReadFloatingPrimaryStrength(),
                colorStyle = config.curReadFloatingColorStyle(),
            )

            assertEquals(expected, ReadDrawerStyle.themeSnapshot(context))
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
    fun `soft gradient legacy night without explicit follow keeps the independent palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val originalSoftGradientColor = context.getPrefString(PreferKey.ngSoftGradientColor)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed
        val originalNightSeed = config.readFloatingSeedNight
        val originalNightFollow = config.readFloatingFollowAppNight

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = true
            config.readFloatingSeed = 0xFFF8E4D2.toInt()
            config.readFloatingSeedNight = 0
            config.readFloatingFollowAppNight = null
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
            val standardSnapshot = ReadDrawerStyle.themeSnapshot(context)

            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            context.putPrefString(
                PreferKey.ngSoftGradientColor,
                NgSoftGradientColorPreset.DUSK_VIOLET.storageValue,
            )

            assertFalse(config.curReadFloatingFollowsApplication())
            assertEquals(standardSnapshot, ReadDrawerStyle.themeSnapshot(context))
            assertFalse(ThemeConfig.isReadingNgBackgroundTheme(context))
        } finally {
            config.readFloatingSeed = originalSeed
            config.readFloatingSeedNight = originalNightSeed
            config.readFloatingFollowAppNight = originalNightFollow
            ReadBookConfig.isNightTheme = originalNightTheme
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, originalMode)
            context.putPrefString(PreferKey.ngSoftGradientColor, originalSoftGradientColor)
        }
    }

    @Test
    fun `soft gradient explicit night follow uses the active application palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode
        val originalNightTheme = ReadBookConfig.isNightTheme
        val originalMode = context.getPrefString(PreferKey.ngThemePresentationMode)
        val config = ReadBookConfig.durConfig
        val originalSeed = config.readFloatingSeed
        val originalNightSeed = config.readFloatingSeedNight
        val originalNightFollow = config.readFloatingFollowAppNight

        try {
            AppConfig.isEInkMode = false
            ReadBookConfig.isNightTheme = true
            config.readFloatingSeed = 0xFFF8E4D2.toInt()
            config.readFloatingSeedNight = 0xFF41566B.toInt()
            config.readFloatingFollowAppNight = false
            config.clearCurReadFloatingSeed()
            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            val expected = ReadFloatingPalette.applySemanticRoles(
                snapshot = NgThemeResolver.resolve(context),
                primaryStrengthPercent = config.curReadFloatingPrimaryStrength(),
                colorStyle = config.curReadFloatingColorStyle(),
            )

            assertTrue(config.curReadFloatingFollowsApplication())
            assertEquals(expected, ReadDrawerStyle.themeSnapshot(context))
        } finally {
            config.readFloatingSeed = originalSeed
            config.readFloatingSeedNight = originalNightSeed
            config.readFloatingFollowAppNight = originalNightFollow
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
