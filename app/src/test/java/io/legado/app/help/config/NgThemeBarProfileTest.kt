package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class NgThemeBarProfileTest {
    @Test
    fun `legacy reading background aliases still resolve`() {
        assertEquals("bg/暖色渐变.webp", resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_warm.png"))
        assertEquals("bg/暖色渐变.webp", resolveBundledBackgroundAssetPath("bg/暖色渐变.png"))
        assertEquals("bg/竹影之韵.webp", resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_bamboo.png"))
        assertEquals("bg/灰色雾霭.webp", resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_mist.png"))
    }

    @Test
    fun `legacy bar profile uses current settings as editor fallback`() {
        val current = NgThemeBarProfile(
            useFloatingBottomBar = true,
            floatingBottomBarBottomDistancePx = 40,
            floatingBottomBarTransparency = 40,
            bookshelfTopBarStyle = BookshelfTopBarStyle.GROUP_NAVIGATION.value,
            bookshelfFloatingDockTopDistancePx = 360,
            bookshelfFloatingDockTransparency = 40,
            bookshelfFloatingDockSearchPosition =
                BookshelfFloatingDockSearchPosition.RIGHT.value,
        )
        val legacy: NgThemeBarProfile? = null

        assertEquals(current, legacy.withFallback(current))
        assertEquals(
            current.copy(
                useFloatingBottomBar = false,
                bookshelfFloatingDockTransparency = 75,
                bookshelfFloatingDockSearchPosition =
                    BookshelfFloatingDockSearchPosition.LEFT.value,
            ),
            NgThemeBarProfile(
                useFloatingBottomBar = false,
                bookshelfFloatingDockTransparency = 75,
                bookshelfFloatingDockSearchPosition =
                    BookshelfFloatingDockSearchPosition.LEFT.value,
            ).withFallback(current),
        )
        assertEquals(
            BookshelfFloatingDockSearchPosition.LEFT.value,
            NgThemeBarProfile(
                bookshelfFloatingDockSearchPosition = Int.MAX_VALUE
            ).normalized().bookshelfFloatingDockSearchPosition,
        )
    }
}
