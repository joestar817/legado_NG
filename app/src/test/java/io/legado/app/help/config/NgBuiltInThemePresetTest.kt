package io.legado.app.help.config

import io.legado.app.ui.design.theme.NgColorMath
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.ui.design.theme.NgTopBarTextMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NgBuiltInThemePresetTest {

    @Test
    fun `legacy bundled background paths resolve to reading background assets`() {
        assertEquals(
            "bg/暖色渐变.webp",
            resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_warm.png"),
        )
        assertEquals(
            "bg/暖色渐变.webp",
            resolveBundledBackgroundAssetPath("bg/暖色渐变.png"),
        )
        assertEquals(
            "bg/竹影之韵.webp",
            resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_bamboo.png"),
        )
        assertEquals(
            "bg/灰色雾霭.webp",
            resolveBundledBackgroundAssetPath("defaultData/theme/reading_ng_mist.png"),
        )
        assertEquals(
            "defaultData/theme/reading_ng_autumn_mountains.webp",
            resolveBundledBackgroundAssetPath(
                "defaultData/theme/reading_ng_autumn_mountains.png"
            ),
        )
        assertEquals(
            "defaultData/theme/reading_ng_autumn_mountains_dark.webp",
            resolveBundledBackgroundAssetPath(
                "defaultData/theme/reading_ng_autumn_mountains_dark.png"
            ),
        )
    }

    @Test
    fun `missing installed background is repaired after built in package update`() {
        val packageRoot = File("build/theme-package").absoluteFile
        val oldPath = File(packageRoot, "assets/background-light.png").path
        val installedPath = File(packageRoot, "assets/background-light.webp").path

        assertEquals(
            installedPath,
            resolveReinstalledThemeBackgroundPath(
                currentPath = oldPath,
                installedPath = installedPath,
                packageRootPath = packageRoot.path,
                isFile = { it == installedPath },
            ),
        )
    }

    @Test
    fun `existing and user managed background paths are not repaired`() {
        val packageRoot = File("build/theme-package").absoluteFile
        val existingPath = File(packageRoot, "assets/background-light.png").path
        val installedPath = File(packageRoot, "assets/background-light.webp").path
        val userPath = File(packageRoot.parentFile, "user/background.png").path

        assertEquals(
            existingPath,
            resolveReinstalledThemeBackgroundPath(
                currentPath = existingPath,
                installedPath = installedPath,
                packageRootPath = packageRoot.path,
                isFile = { it == existingPath || it == installedPath },
            ),
        )
        assertEquals(
            userPath,
            resolveReinstalledThemeBackgroundPath(
                currentPath = userPath,
                installedPath = installedPath,
                packageRootPath = packageRoot.path,
                isFile = { it == installedPath },
            ),
        )
    }

    @Test
    fun `managed theme library exposes completed seasons in calendar order`() {
        val expected = NgThemeBarProfile(
            useFloatingBottomBar = true,
            floatingBottomBarBottomDistancePx = 40,
            floatingBottomBarTransparency = 40,
            bookshelfTopBarStyle = BookshelfTopBarStyle.GROUP_NAVIGATION.value,
            bookshelfFloatingDockTopDistancePx = 50,
            bookshelfFloatingDockTransparency = 40,
            bookshelfFloatingDockSearchPosition =
                BookshelfFloatingDockSearchPosition.LEFT.value,
        )

        assertEquals(
            listOf(
                "builtin.ng.summer_childhood",
                "builtin.ng.autumn_mountains",
            ),
            NgBuiltInThemes.all.map { it.id },
        )
        assertEquals(
            listOf(expected, expected),
            listOf(
                NgBuiltInThemes.sakura,
                NgBuiltInThemes.cats,
            ).map { it.barProfile },
        )
    }

    @Test
    fun `summer preset provides paired day and night artwork`() {
        val summer = NgBuiltInThemes.summer
        val dark = summer.colors.manualDark

        assertEquals("夏日童趣", summer.name)
        assertTrue(summer.isBuiltIn)
        assertEquals(0xFF008B71.toInt(), summer.colors.manualLight.primary)
        assertEquals(0xFF5CCBFF.toInt(), dark.primary)
        assertEquals(0xFF153A5B.toInt(), dark.secondary)
        assertEquals(0xFFF2F7FF.toInt(), dark.primaryText)
        assertEquals(0xFFB8D4E8.toInt(), dark.secondaryText)
        assertEquals(0xFF06182D.toInt(), dark.background)
        assertEquals(0xFF12314D.toInt(), dark.labelContainer)
        assertEquals(NgTopBarTextMode.DARK, summer.colors.lightTopBarTextMode)
        assertEquals(NgTopBarTextMode.LIGHT, summer.colors.darkTopBarTextMode)
        assertEquals(
            "asset://defaultData/theme/reading_ng_summer_childhood.webp",
            summer.lightBackground.path,
        )
        assertEquals(
            "asset://defaultData/theme/reading_ng_summer_childhood_dark.webp",
            summer.darkBackground.path,
        )
        assertEquals(null, summer.sceneProfile)
        assertTrue(summer in NgBuiltInThemes.all)
    }

    @Test
    fun `cartoon scenes are exposed as two stable internal presets`() {
        val expected = listOf(
            Triple(
                NgBuiltInThemes.sakura,
                ListeningCartoonType.SAKURA,
                "asset://listening_motion/cartoon/sakura/background.webp",
            ),
            Triple(
                NgBuiltInThemes.cats,
                ListeningCartoonType.CATS,
                "asset://listening_motion/cartoon/cats/poster.webp",
            ),
        )

        expected.forEach { (theme, type, background) ->
            assertTrue(theme.isBuiltIn)
            assertEquals(type, theme.sceneProfile?.sceneType())
            assertEquals(NgThemeSceneProfile.DEFAULT_INTENSITY, theme.sceneProfile?.intensity)
            assertEquals(background, theme.lightBackground.path)
            assertEquals(background, theme.darkBackground.path)
            assertEquals(NgTopBarTextMode.LIGHT, theme.colors.lightTopBarTextMode)
            assertEquals(NgTopBarTextMode.LIGHT, theme.colors.darkTopBarTextMode)
            assertEquals(NgColorGenerationMode.MANUAL, theme.colors.mode)
        }
        assertEquals("湖畔樱花", NgBuiltInThemes.sakura.name)
        assertEquals(0xFFFF61FF.toInt(), NgBuiltInThemes.sakura.colors.manualLight.primary)
        assertEquals(0xFFFF61FF.toInt(), NgBuiltInThemes.sakura.colors.manualDark.primary)
        assertEquals("好奇猫咪", NgBuiltInThemes.cats.name)
        assertEquals(0xFF4E900C.toInt(), NgBuiltInThemes.cats.colors.manualLight.primary)
        assertEquals(0xFF4E900C.toInt(), NgBuiltInThemes.cats.colors.manualDark.primary)
        assertEquals(
            listOf(ListeningCartoonType.SAKURA, ListeningCartoonType.CATS),
            NgDynamicSceneTheme.presets,
        )
        assertEquals(
            ListeningCartoonType.SAKURA,
            NgDynamicSceneTheme.fromLegacyThemeId("builtin.ng.sakura"),
        )
        assertEquals(
            ListeningCartoonType.CATS,
            NgDynamicSceneTheme.fromLegacyThemeId("builtin.ng.cats"),
        )
        assertEquals(null, NgDynamicSceneTheme.fromLegacyThemeId("builtin.ng.autumn_mountains"))
    }

    @Test
    fun `dynamic scene profile clamps percentages and drops unknown ids`() {
        assertEquals(
            NgThemeSceneProfile(
                sceneId = ListeningCartoonType.CATS.storageValue,
                intensity = NgThemeSceneProfile.MAX_INTENSITY,
            ),
            NgThemeSceneProfile(
                sceneId = ListeningCartoonType.CATS.storageValue,
                intensity = Int.MAX_VALUE,
            ).normalized(),
        )
        assertEquals(
            null,
            NgManagedTheme(
                id = "local.scene-test",
                name = "scene-test",
                colors = NgBuiltInThemes.autumn.colors,
                sceneProfile = NgThemeSceneProfile(sceneId = "future_scene"),
            ).normalized().sceneProfile,
        )
    }

    @Test
    fun `dynamic scene profile keeps stable json field names`() {
        val json = GSON.toJson(NgBuiltInThemes.cats)

        assertTrue(json.contains("\"sceneProfile\""))
        assertTrue(json.contains("\"sceneId\":\"cats\""))
        assertTrue(json.contains("\"intensity\":100"))
    }

    @Test
    fun `autumn preset provides a paired night theme and configures both floating docks`() {
        val autumn = NgBuiltInThemes.autumn
        val dark = autumn.colors.manualDark

        assertEquals("秋山书意", autumn.name)
        assertTrue(autumn.isBuiltIn)
        assertEquals(0xFFF78E66.toInt(), autumn.colors.manualLight.primary)
        assertEquals(0xFF758DB4.toInt(), autumn.colors.darkSeed)
        assertEquals(0xFF758DB4.toInt(), dark.primary)
        assertEquals(0xFF2F3B4B.toInt(), dark.secondary)
        assertEquals(0xFFF2F5F8.toInt(), dark.primaryText)
        assertEquals(0xFFB8C2CC.toInt(), dark.secondaryText)
        assertEquals(0xFF192633.toInt(), dark.background)
        assertEquals(0xFF263440.toInt(), dark.labelContainer)
        val settingsIconContainer = NgColorMath.blend(dark.background, dark.primary, 0.34f)
        assertTrue(
            NgColorMath.contrastRatio(settingsIconContainer, dark.primaryText) >= 4.5
        )
        assertEquals(NgTopBarTextMode.LIGHT, autumn.colors.darkTopBarTextMode)
        assertEquals(
            "asset://defaultData/theme/reading_ng_autumn_mountains.webp",
            autumn.lightBackground.path,
        )
        assertEquals(
            "asset://defaultData/theme/reading_ng_autumn_mountains_dark.webp",
            autumn.darkBackground.path,
        )
        assertEquals(
            NgThemeBarProfile(
                useFloatingBottomBar = true,
                floatingBottomBarBottomDistancePx = 40,
                floatingBottomBarTransparency = 40,
                bookshelfTopBarStyle = BookshelfTopBarStyle.GROUP_NAVIGATION.value,
                bookshelfFloatingDockTopDistancePx = 360,
                bookshelfFloatingDockTransparency = 40,
                bookshelfFloatingDockSearchPosition =
                    BookshelfFloatingDockSearchPosition.LEFT.value,
            ),
            autumn.barProfile,
        )
        assertTrue(autumn in NgBuiltInThemes.all)
        assertEquals(autumn, NgBuiltInThemes.defaultTheme)
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
