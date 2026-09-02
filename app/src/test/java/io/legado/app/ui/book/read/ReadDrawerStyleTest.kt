package io.legado.app.ui.book.read

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.putPrefString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReadDrawerStyleTest {

    @Test
    fun `soft gradient keeps the independent reading palette`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val originalEInkMode = AppConfig.isEInkMode

        try {
            AppConfig.isEInkMode = false
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
            val standardSnapshot = ReadDrawerStyle.themeSnapshot(context)

            context.putPrefString(PreferKey.ngThemePresentationMode, "soft_gradient")
            val softGradientSnapshot = ReadDrawerStyle.themeSnapshot(context)

            assertEquals(standardSnapshot, softGradientSnapshot)
            assertFalse(ThemeConfig.isReadingNgBackgroundTheme(context))
        } finally {
            AppConfig.isEInkMode = originalEInkMode
            context.putPrefString(PreferKey.ngThemePresentationMode, "standard")
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
