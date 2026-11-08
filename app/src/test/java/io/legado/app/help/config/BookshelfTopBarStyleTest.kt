package io.legado.app.help.config

import org.junit.Assert.assertEquals
import org.junit.Test

class BookshelfTopBarStyleTest {

    @Test
    fun regularBookshelfDefaultsToGroupNavigation() {
        assertEquals(
            BookshelfTopBarStyle.GROUP_NAVIGATION,
            BookshelfTopBarStyle.resolveForLayout(
                configuredStyle = BookshelfTopBarStyle.DEFAULT,
                groupGridMode = false,
            )
        )
    }

    @Test
    fun restoresSupportedStyles() {
        BookshelfTopBarStyle.entries.forEach { style ->
            assertEquals(style, BookshelfTopBarStyle.fromValue(style.value))
        }
    }

    @Test
    fun defaultsUnknownStylesToCompactToolbar() {
        assertEquals(
            BookshelfTopBarStyle.COMPACT_TOOLBAR,
            BookshelfTopBarStyle.fromValue(Int.MIN_VALUE)
        )
    }

    @Test
    fun groupGridAlwaysUsesCompactToolbar() {
        assertEquals(
            BookshelfTopBarStyle.COMPACT_TOOLBAR,
            BookshelfTopBarStyle.resolveForLayout(
                configuredStyle = BookshelfTopBarStyle.GROUP_NAVIGATION,
                groupGridMode = true,
            )
        )
        assertEquals(
            BookshelfTopBarStyle.COMPACT_TOOLBAR,
            BookshelfTopBarStyle.resolveForLayout(
                configuredStyle = BookshelfTopBarStyle.COMPACT_TOOLBAR,
                groupGridMode = true,
            )
        )
    }

    @Test
    fun regularBookshelfKeepsConfiguredStyle() {
        BookshelfTopBarStyle.entries.forEach { style ->
            assertEquals(
                style,
                BookshelfTopBarStyle.resolveForLayout(
                    configuredStyle = style,
                    groupGridMode = false,
                )
            )
        }
    }

    @Test
    fun resolvesAutomaticDockGapFromLegacyDynamicLayout() {
        assertEquals(
            295,
            BookshelfFloatingDockConfig.resolveTopDistancePx(
                storedDistancePx = BookshelfFloatingDockConfig.AUTOMATIC_TOP_DISTANCE_PX,
                screenWidthPx = 744,
                density = 2f,
                statusBarHeightPx = 48
            )
        )
        assertEquals(
            445,
            BookshelfFloatingDockConfig.resolveTopDistancePx(
                storedDistancePx = BookshelfFloatingDockConfig.AUTOMATIC_TOP_DISTANCE_PX,
                screenWidthPx = 1116,
                density = 3f,
                statusBarHeightPx = 72
            )
        )
    }

    @Test
    fun defaultDockGapIsFortyPhysicalPixelsBelowStatusBar() {
        val gapPx = BookshelfFloatingDockConfig.resolveTopDistancePx(
            storedDistancePx = BookshelfFloatingDockConfig.DEFAULT_TOP_DISTANCE_PX,
            screenWidthPx = 1116,
            density = 3f,
            statusBarHeightPx = 72
        )

        assertEquals(40, gapPx)
        assertEquals(112, BookshelfFloatingDockConfig.screenTopDistancePx(gapPx, 72))
    }

    @Test
    fun clampsDockDistanceAndTransparencyToSupportedRanges() {
        assertEquals(
            0,
            BookshelfFloatingDockConfig.resolveTopDistancePx(
                storedDistancePx = 0,
                screenWidthPx = 744,
                density = 2f,
                statusBarHeightPx = 48
            )
        )
        assertEquals(
            0,
            BookshelfFloatingDockConfig.resolveTopDistancePx(
                storedDistancePx = 1,
                screenWidthPx = 744,
                density = 2f,
                statusBarHeightPx = 48
            )
        )
        assertEquals(400, BookshelfFloatingDockConfig.normalizeTopDistancePx(399))
        assertEquals(400, BookshelfFloatingDockConfig.normalizeTopDistancePx(401))
        assertEquals(405, BookshelfFloatingDockConfig.normalizeTopDistancePx(403))
        assertEquals(
            500,
            BookshelfFloatingDockConfig.resolveTopDistancePx(
                storedDistancePx = 999,
                screenWidthPx = 744,
                density = 2f,
                statusBarHeightPx = 48
            )
        )
        assertEquals(
            142,
            BookshelfFloatingDockConfig.screenTopDistancePx(
                topGapPx = 72,
                statusBarHeightPx = 72
            )
        )
        assertEquals(
            572,
            BookshelfFloatingDockConfig.screenTopDistancePx(
                topGapPx = 500,
                statusBarHeightPx = 72
            )
        )
        assertEquals(1f, BookshelfFloatingDockConfig.surfaceAlpha(-1), 0f)
        assertEquals(0f, BookshelfFloatingDockConfig.surfaceAlpha(101), 0f)
        assertEquals(0.6f, BookshelfFloatingDockConfig.surfaceAlpha(40), 0f)
    }
}
