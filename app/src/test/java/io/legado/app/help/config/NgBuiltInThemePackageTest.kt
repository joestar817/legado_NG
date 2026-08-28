package io.legado.app.help.config

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.legado.app.ui.design.theme.NgThemeResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class NgBuiltInThemePackageTest {

    @Test
    fun `manual theme keeps primary and secondary surface text roles distinct`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dark = NgBuiltInThemes.autumn.colors.manualDark
        val snapshot = NgThemeResolver.resolve(
            context = context,
            colors = NgBuiltInThemes.autumn.colors,
            isDark = true,
        )

        assertEquals(dark.primaryText, snapshot.colors.onSurface)
        assertEquals(dark.secondaryText, snapshot.colors.onSurfaceVariant)
    }

    @Test
    fun `built in theme is materialized as an immutable installed package`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = NgThemePackageManager.installBuiltInThemes(
            context = context,
            definitions = listOf(NgBuiltInThemes.autumn),
        ).single()

        assertTrue(installed.isBuiltIn)
        assertEquals(NgBuiltInThemes.autumn.colors, installed.colors)
        assertNotNull(installed.packageRootPath)
        val root = File(requireNotNull(installed.packageRootPath)).canonicalFile
        val builtInRoot = File(
            File(context.filesDir, NgThemePackageManager.PACKAGE_DIR),
            "built-in",
        ).canonicalFile
        assertTrue(root.toPath().startsWith(builtInRoot.toPath()))
        assertTrue(File(root, "manifest.json").isFile)
        assertTrue(File(requireNotNull(installed.lightBackground.path)).isFile)
        assertTrue(File(requireNotNull(installed.darkBackground.path)).isFile)
    }

    @Test
    fun `dynamic theme package keeps its scene and poster`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val installed = NgThemePackageManager.installBuiltInThemes(
            context = context,
            definitions = listOf(NgBuiltInThemes.cats),
        ).single()

        assertEquals(ListeningCartoonType.CATS, installed.sceneProfile?.sceneType())
        assertEquals(
            NgThemeSceneProfile.DEFAULT_INTENSITY,
            installed.sceneProfile?.intensity,
        )
        assertTrue(File(requireNotNull(installed.lightBackground.path)).isFile)
        assertEquals(installed.lightBackground.path, installed.darkBackground.path)
    }
}
