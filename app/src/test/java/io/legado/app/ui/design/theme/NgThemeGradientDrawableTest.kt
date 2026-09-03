package io.legado.app.ui.design.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.NgSoftGradientLightFieldPreset
import io.legado.app.help.config.NgSoftGradientTheme
import io.legado.app.help.config.NgThemeGradientMotion
import io.legado.app.utils.putPrefString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NgThemeGradientDrawableTest {

    @Test
    fun `soft gradient selected container is neutral white`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")

        try {
            assertEquals(
                NgSoftGradientTheme.selectedContainer,
                NgThemeResolver.resolve(context).colors.selectedContainer,
            )
        } finally {
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
        }
    }

    @Test
    fun `approved soft gradient colors keep stable storage values`() {
        assertEquals(
            listOf(
                "clear_blue",
                "dusk_violet",
                "young_bamboo",
                "forest_after_rain",
                "cherry_glow",
            ),
            NgSoftGradientColorPreset.entries.map { it.storageValue },
        )
    }

    @Test
    fun `dark soft gradient tones use readable backdrop navigation colors`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")

        try {
            listOf(
                NgSoftGradientColorPreset.DUSK_VIOLET,
                NgSoftGradientColorPreset.YOUNG_BAMBOO,
                NgSoftGradientColorPreset.FOREST_AFTER_RAIN,
                NgSoftGradientColorPreset.CHERRY_GLOW,
            ).forEach { colorPreset ->
                context.putPrefString(
                    PreferKey.ngSoftGradientColor,
                    colorPreset.storageValue,
                )
                val snapshot = NgThemeResolver.resolve(context)
                assertEquals(
                    0xFFFFFFFF.toInt(),
                    snapshot.backdropContent.topNavigationActive,
                )
                assertEquals(
                    0xB8FFFFFF.toInt(),
                    snapshot.backdropContent.topNavigationInactive,
                )
                assertFalse(snapshot.systemBars.darkStatusBarIcons)
            }
        } finally {
            context.putPrefString(
                PreferKey.ngSoftGradientColor,
                NgSoftGradientColorPreset.CLEAR_BLUE.storageValue,
            )
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
        }
    }

    @Test
    fun `soft gradient tones provide readable direct backdrop content`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")

        try {
            NgSoftGradientColorPreset.entries.forEach { colorPreset ->
                context.putPrefString(
                    PreferKey.ngSoftGradientColor,
                    colorPreset.storageValue,
                )
                val content = NgThemeResolver.resolve(context).backdropContent
                assertEquals(0xFFFFFFFF.toInt(), content.primaryContent)
                assertEquals(0xD9FFFFFF.toInt(), content.secondaryContent)
                assertEquals(0x52000000.toInt(), content.textShadow)
            }
        } finally {
            context.putPrefString(
                PreferKey.ngSoftGradientColor,
                NgSoftGradientColorPreset.CLEAR_BLUE.storageValue,
            )
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
        }
    }

    @Test
    fun `all soft gradient presets draw opaque non uniform portrait backgrounds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NgSoftGradientColorPreset.entries.forEach { colorPreset ->
            context.putPrefString(PreferKey.ngSoftGradientColor, colorPreset.storageValue)
            val baseColorProfiles = mutableSetOf<List<Int>>()
            val colors = NgSoftGradientTheme.colors(context)
            assertEquals(colors.manualLight, colors.manualDark)

            NgSoftGradientLightFieldPreset.entries.forEach { lightField ->
                context.putPrefString(
                    PreferKey.ngSoftGradientLightField,
                    lightField.storageValue,
                )
                val profile = NgSoftGradientTheme.gradient(context)
                if (lightField != NgSoftGradientLightFieldPreset.FLOW_SHADOW) {
                    baseColorProfiles += profile.colors
                }
                val drawable = NgThemeGradientDrawable(profile)
                assertEquals(
                    lightField == NgSoftGradientLightFieldPreset.FLOW_SHADOW,
                    drawable.supportsFlowShadow,
                )
                val bitmap = Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888)

                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))

                val top = bitmap.getPixel(bitmap.width / 2, 40)
                val center = bitmap.getPixel(bitmap.width / 2, bitmap.height / 2)
                val bottom = bitmap.getPixel(bitmap.width / 2, bitmap.height - 40)
                val samples = listOf(
                    top,
                    bitmap.getPixel(36, bitmap.height / 3),
                    center,
                    bitmap.getPixel(bitmap.width - 36, bitmap.height * 2 / 3),
                    bottom,
                )
                assertEquals(255, Color.alpha(top))
                assertEquals(255, Color.alpha(center))
                assertEquals(255, Color.alpha(bottom))
                assertNotEquals(top, center)
                assertNotEquals(center, bottom)
                assertTrue(samples.distinct().size >= 4)
                assertLuminanceRange(samples, minimumRange = 0.16)
            }
            assertEquals(
                NgSoftGradientLightFieldPreset.entries.size - 1,
                baseColorProfiles.size,
            )
        }
    }

    @Test
    fun `flow shadow reuses balanced scene with dedicated motion`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NgSoftGradientColorPreset.entries.forEach { colorPreset ->
            context.putPrefString(PreferKey.ngSoftGradientColor, colorPreset.storageValue)
            context.putPrefString(
                PreferKey.ngSoftGradientLightField,
                NgSoftGradientLightFieldPreset.BALANCED.storageValue,
            )
            val balanced = NgSoftGradientTheme.gradient(context)
            context.putPrefString(
                PreferKey.ngSoftGradientLightField,
                NgSoftGradientLightFieldPreset.FLOW_SHADOW.storageValue,
            )
            val flowShadow = NgSoftGradientTheme.gradient(context)

            assertEquals(NgThemeGradientMotion.NONE, balanced.motion)
            assertEquals(NgThemeGradientMotion.FLOW_SHADOW, flowShadow.motion)
            assertEquals(balanced.colors, flowShadow.colors)
            assertEquals(balanced.stops, flowShadow.stops)
            assertEquals(balanced.radialLayers, flowShadow.radialLayers)
        }
    }

    private fun assertLuminanceRange(colors: List<Int>, minimumRange: Double) {
        val luminances = colors.map(::luminance)
        val range = luminances.maxOrNull()!! - luminances.minOrNull()!!
        assertTrue(
            "Expected luminance range >= $minimumRange, actual=$range",
            range >= minimumRange,
        )
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) {
                normalized / 12.92
            } else {
                Math.pow((normalized + 0.055) / 1.055, 2.4)
            }
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
