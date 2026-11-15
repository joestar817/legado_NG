package io.legado.app.help.config

import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.appCtx

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class ThemeTopBarDistanceApplyTest {
    private fun apply(profile: NgThemeBarProfile?) {
        // Use real theme colors/profile, without loading background assets in this state regression.
        val theme = NgThemeTestFixtures.autumn.copy(
            barProfile = profile,
            lightBackground = NgThemeBackground(),
            darkBackground = NgThemeBackground(),
            drawerProfile = null,
            coverProfile = null,
        )
        assertTrue(ThemeConfig.applyManagedTheme(appCtx, theme))
    }

    @Test fun switchingFromAutumnToThemeWithoutBarProfileRestoresAppDefault() {
        apply(NgThemeTestFixtures.autumn.barProfile)
        assertEquals(360, AppConfig.bookshelfFloatingDockTopDistancePx)
        apply(null)
        assertEquals(BookshelfFloatingDockConfig.DEFAULT_TOP_DISTANCE_PX, AppConfig.bookshelfFloatingDockTopDistancePx)
        apply(null)
        assertEquals(BookshelfFloatingDockConfig.DEFAULT_TOP_DISTANCE_PX, AppConfig.bookshelfFloatingDockTopDistancePx)
        apply(NgThemeTestFixtures.autumn.barProfile)
        assertEquals(360, AppConfig.bookshelfFloatingDockTopDistancePx)
    }

    @Test fun partialBarProfileDoesNotInheritTopDistanceOrResetUnspecifiedOtherSettings() {
        apply(NgThemeTestFixtures.autumn.barProfile)
        AppConfig.floatingBottomBarBottomDistancePx = 25
        AppConfig.bookshelfFloatingDockSearchPosition = BookshelfFloatingDockSearchPosition.RIGHT
        apply(NgThemeBarProfile(bookshelfFloatingDockTransparency = 75))
        assertEquals(BookshelfFloatingDockConfig.DEFAULT_TOP_DISTANCE_PX, AppConfig.bookshelfFloatingDockTopDistancePx)
        assertEquals(75, AppConfig.bookshelfFloatingDockTransparency)
        assertEquals(25, AppConfig.floatingBottomBarBottomDistancePx)
        assertEquals(BookshelfFloatingDockSearchPosition.RIGHT, AppConfig.bookshelfFloatingDockSearchPosition)
    }

    @Test fun explicitZeroAndCustomDistancesArePreserved() {
        apply(NgThemeBarProfile(bookshelfFloatingDockTopDistancePx = 0))
        assertEquals(0, AppConfig.bookshelfFloatingDockTopDistancePx)
        apply(NgThemeBarProfile(bookshelfFloatingDockTopDistancePx = 135))
        assertEquals(135, AppConfig.bookshelfFloatingDockTopDistancePx)
    }
}
