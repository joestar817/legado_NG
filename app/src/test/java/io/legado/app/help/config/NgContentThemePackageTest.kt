package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import io.legado.app.constant.PreferKey
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.utils.GSON
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class NgContentThemePackageTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var previousPreferences: Map<String, *>

    @Before
    fun rememberPreferences() {
        previousPreferences = context.defaultSharedPreferences.all.toMap()
    }

    @After
    @Suppress("UNCHECKED_CAST")
    fun restorePreferences() {
        val editor = context.defaultSharedPreferences.edit().clear()
        previousPreferences.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        editor.commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
    }

    @Test
    fun `content themes install from self contained ZIPs without bundled assets`() {
        listOf("summer-childhood", "autumn-mountains", "storybook-house").forEach { slug ->
            val source = File(NgThemeTestFixtures.contentRoot, slug)
            val temp = Files.createTempDirectory("content-theme").toFile()
            try {
                val archive = File(temp, "theme.zip")
                ZipOutputStream(archive.outputStream()).use { zip ->
                    source.walkTopDown().filter { it.isFile }.forEach { file ->
                        zip.putNextEntry(ZipEntry(file.relativeTo(source).invariantSeparatorsPath))
                        zip.write(file.readBytes()); zip.closeEntry()
                    }
                }
                val manifest = NgThemeArchive.inspect(archive)
                assertFalse(manifest.theme.isBuiltIn)
                assertNull(manifest.theme.effect)
                assertNull(manifest.theme.centerCropBackground)
                val root = File(temp, "installed")
                NgThemeArchive.extract(archive, root)
                val installed = NgThemePackageManager.materializeTheme(root, manifest, "local.test.$slug", manifest.theme.name)
                assertEquals(manifest.theme.colors, installed.colors)
                assertEquals(manifest.theme.barProfile, installed.barProfile)
                assertEquals(manifest.theme.drawerProfile?.source, installed.drawerProfile?.source)
                listOf(installed.lightBackground.path to manifest.lightBackgroundAsset,
                    installed.darkBackground.path to manifest.darkBackgroundAsset).forEach { (path, asset) ->
                    assertArrayEquals(File(source, requireNotNull(asset)).readBytes(), File(requireNotNull(path)).readBytes())
                }
                assertTrue(installed.lightBackground.path!!.startsWith(root.absolutePath))
                assertTrue(installed.darkBackground.path!!.startsWith(root.absolutePath))
            } finally { temp.deleteRecursively() }
        }
    }

    @Test
    fun `ordinary packages can omit effect parameters`() {
        val manifest = GSON.toJsonTree(NgThemePackageManifest(
            format = NgEffectPackageKind.THEME.format,
            version = 1,
            theme = NgThemeTestFixtures.autumn,
        )).asJsonObject
        manifest.getAsJsonObject("theme").remove("effectValues")
        val archive = File.createTempFile("ordinary-theme", ".zip", context.cacheDir)
        try {
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            assertTrue(NgThemeArchive.inspect(archive).theme.effectValues.isEmpty())
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `inspecting cancelling and installing an ordinary theme preserve the current appearance`() = runBlocking<Unit> {
        val incoming = NgThemeTestFixtures.autumn.copy(centerCropBackground = true)
        val selected = incoming.copy(id = "local.current")
        context.defaultSharedPreferences.edit().clear()
            .putString("ngManagedThemes.v1", GSON.toJson(listOf(selected)))
            .putString("ngActiveManagedThemeId.v1", selected.id)
            .putString(PreferKey.ngThemePresentationMode, "standard")
            .commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
        val before = NgThemeLibraryStore.current(context)
        val archive = File.createTempFile("ordinary-theme", ".zip", context.cacheDir)
        try {
            archive.outputStream().use { NgThemePackageManager.writeTheme(context, incoming, it) }
            val uri = Uri.fromFile(archive)
            val pending = NgThemePackageManager.inspectTheme(context, uri).getOrThrow()
            try {
                assertEquals(NgThemeImportAction.INSTALL, pending.action)
                assertEquals(before, NgThemeLibraryStore.current(context))
            } finally {
                pending.discard()
            }
            assertEquals(before, NgThemeLibraryStore.current(context))

            val confirmed = NgThemePackageManager.inspectTheme(context, uri).getOrThrow()
            try {
                val installed = confirmed.commit(context)
                try {
                    val after = NgThemeLibraryStore.current(context)
                    assertEquals(incoming.id, installed.id)
                    assertEquals(true, installed.centerCropBackground)
                    assertEquals(before.activeThemeId, after.activeThemeId)
                    assertEquals(before.savedThemes.toSet() + installed, after.savedThemes.toSet())
                    assertEquals(NgThemePresentationMode.STANDARD, NgThemeModeStore.current(context))

                    val repeat = NgThemePackageManager.inspectTheme(context, uri).getOrThrow()
                    try {
                        assertEquals(installed, repeat.previous)
                        assertEquals(installed.publication?.sha256, repeat.sha256)
                        assertEquals(NgThemeImportAction.REPLACE, repeat.action)
                        assertTrue(runCatching { repeat.commit(context) }.isFailure)
                        assertEquals(after, NgThemeLibraryStore.current(context))
                        val replaced = repeat.commit(context, allowReplace = true)
                        assertEquals(installed.id, replaced.id)
                        assertEquals(before.savedThemes.toSet() + replaced, NgThemeLibraryStore.current(context).savedThemes.toSet())
                        assertEquals(before.activeThemeId, NgThemeLibraryStore.current(context).activeThemeId)
                    } finally {
                        repeat.discard()
                    }
                } finally {
                    NgThemeLibraryStore.remove(context, installed.id, restoreFallback = false)
                }
            } finally {
                confirmed.discard()
            }
        } finally {
            archive.delete()
        }
    }

    @Test
    fun `anonymous themes import separately and export without an update ID`() = runBlocking<Unit> {
        context.defaultSharedPreferences.edit().clear().commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
        val incoming = NgThemeTestFixtures.autumn.copy(id = "")
        val archive = File.createTempFile("anonymous-theme", ".zip", context.cacheDir)
        val exported = File.createTempFile("anonymous-export", ".zip", context.cacheDir)
        val installedIds = mutableListOf<String>()
        try {
            archive.outputStream().use { NgThemePackageManager.writeTheme(context, incoming, it) }
            val uri = Uri.fromFile(archive)
            val first = NgThemePackageManager.importTheme(context, uri).getOrThrow()
            installedIds.add(first.id)
            val second = NgThemePackageManager.importTheme(context, uri).getOrThrow()
            installedIds.add(second.id)

            assertEquals(incoming.name, first.name)
            assertEquals("${incoming.name} 2", second.name)
            assertNotEquals(first.id, second.id)
            assertNull(NgThemeLibraryStore.current(context).activeThemeId)
            exported.outputStream().use { NgThemePackageManager.writeTheme(context, first, it) }
            val candidate = NgThemePackageManager.inspectTheme(context, Uri.fromFile(exported)).getOrThrow()
            try {
                assertEquals("", candidate.theme.id)
                assertNull(candidate.previous)
                assertEquals(NgThemeImportAction.INSTALL, candidate.action)
            } finally {
                candidate.discard()
            }
        } finally {
            installedIds.forEach { NgThemeLibraryStore.remove(context, it, restoreFallback = false) }
            exported.delete()
            archive.delete()
        }
    }

    @Test
    fun `manual theme preserves distinct primary and secondary text roles`() {
        val colors = NgThemeTestFixtures.autumn.colors
        val snapshot = NgThemeResolver.resolve(context, colors, true)
        assertEquals(colors.manualDark.primaryText, snapshot.colors.onSurface)
        assertEquals(colors.manualDark.secondaryText, snapshot.colors.onSurfaceVariant)
    }

    @Test
    fun `empty library never installs content themes and has a multicolor base`() {
        context.defaultSharedPreferences.edit().clear().commit()
        AppConfig.isEInkMode = false
        NgThemeLibraryStore.reloadAfterRestore(context)
        assertTrue(NgThemeLibraryStore.allThemes(context).isEmpty())
        assertEquals(NgThemePresentationMode.MULTICOLOR, NgThemeModeStore.current(context))
        assertEquals(NgThemePresentationMode.MULTICOLOR, NgThemeModeStore.lastInternalMode(context))
    }

    @Test
    fun `default initialization preserves an explicit custom or internal appearance`() {
        listOf("standard", "effect_theme", "soft_gradient", "multicolor").forEach { mode ->
            context.defaultSharedPreferences.edit().clear()
                .putString(PreferKey.ngThemePresentationMode, mode)
                .putString("ngActiveManagedThemeId.v1", "local.test.selected")
                .putString("ngManagedThemes.v1", GSON.toJson(listOf(NgThemeTestFixtures.autumn.copy(id = "local.test.selected"))))
                .commit()
            NgThemeLibraryStore.reloadAfterRestore(context)
            NgThemeLibraryStore.applyDefaultThemeIfNeeded(context)
            assertEquals(mode, context.defaultSharedPreferences.getString(PreferKey.ngThemePresentationMode, null))
            assertEquals("local.test.selected", NgThemeLibraryStore.current(context).activeThemeId)
        }
    }
    @Test
    fun `deleting remembered theme clears owned backgrounds without changing internal mode or manual images`() {
        val root = Files.createTempDirectory("applied-theme").toFile()
        try {
            val day = File(root, "day.webp").path
            val night = File(root, "night.webp").path
            val manualNight = File(root.parentFile, "manual-night.webp").path
            val theme = NgManagedTheme(
                id = "local.remembered", name = "Remembered", colors = NgThemeTestFixtures.autumn.colors,
                packageRootPath = root.path,
                lightBackground = NgThemeBackground(day), darkBackground = NgThemeBackground(night),
            )
            context.defaultSharedPreferences.edit().clear()
                .putString("ngManagedThemes.v1", GSON.toJson(listOf(theme)))
                .putString("ngActiveManagedThemeId.v1", theme.id)
                .putString(PreferKey.ngThemePresentationMode, "soft_gradient")
                .putString(PreferKey.bgImage, day).putInt(PreferKey.bgImageBlurring, 8)
                .putString(PreferKey.bgImageN, manualNight).putInt(PreferKey.bgImageNBlurring, 3)
                .putString(PreferKey.dThemeName, theme.name).putString(PreferKey.dNThemeName, theme.name)
                .commit()
            NgThemeLibraryStore.reloadAfterRestore(context)
            assertNotNull(NgThemeLibraryStore.remove(context, theme.id))
            val prefs = context.defaultSharedPreferences
            assertNull(prefs.getString(PreferKey.bgImage, null))
            assertFalse(prefs.contains(PreferKey.bgImageBlurring))
            assertEquals(manualNight, prefs.getString(PreferKey.bgImageN, null))
            assertEquals(3, prefs.getInt(PreferKey.bgImageNBlurring, 0))
            assertNull(prefs.getString(PreferKey.dThemeName, null))
            assertNull(prefs.getString(PreferKey.dNThemeName, null))
            assertEquals("soft_gradient", prefs.getString(PreferKey.ngThemePresentationMode, null))
        } finally { root.deleteRecursively() }
    }

    @Test
    fun `background ownership respects package boundaries and explicit image references`() {
        val root = File("build/owned-theme").absoluteFile
        val theme = NgThemeTestFixtures.autumn.copy(
            packageRootPath = root.path,
            lightBackground = NgThemeBackground("asset://test/background.webp"),
        )
        assertTrue(theme.ownsBackground("asset://test/background.webp"))
        assertTrue(theme.ownsBackground(File(root, "assets/night.webp").path))
        assertFalse(theme.ownsBackground(File(root.parentFile, "owned-theme-other/background.webp").path))
        assertFalse(theme.ownsBackground(File(root, "../manual.webp").path))
        assertFalse(theme.ownsBackground(null))
    }

    @Test
    fun `unselected custom groups render multicolor while keeping their controls and display memory`() {
        listOf("standard", "effect_theme").forEach { mode ->
            context.defaultSharedPreferences.edit().clear()
                .putString(PreferKey.ngThemePresentationMode, mode)
                .putString(PreferKey.ngStandardThemeMode, "2")
                .putString(PreferKey.themeMode, "1").commit()
            NgThemeLibraryStore.reloadAfterRestore(context)
            assertEquals(NgThemeModeGroup.STANDARD, NgThemeModeStore.currentGroup(context))
            assertEquals(NgThemePresentationMode.MULTICOLOR, NgThemeModeStore.current(context))
            assertTrue(NgThemeModeStore.isCustomFoundation(context))
            NgThemeModeStore.captureCustomMode(context)
            assertEquals("2", NgThemeModeStore.standardThemeMode(context))
        }
    }

    @Test
    fun `a selected ordinary theme or explicit manual appearance enables custom rendering`() {
        val selected = NgThemeTestFixtures.autumn.copy(id = "local.selected")
        context.defaultSharedPreferences.edit().clear()
            .putString(PreferKey.ngThemePresentationMode, "standard")
            .putString("ngManagedThemes.v1", GSON.toJson(listOf(selected)))
            .putString("ngActiveManagedThemeId.v1", selected.id).commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
        assertEquals(NgThemePresentationMode.STANDARD, NgThemeModeStore.current(context))
        context.defaultSharedPreferences.edit().remove("ngActiveManagedThemeId.v1")
            .putBoolean(PreferKey.ngCustomAppearanceEdited, true).commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
        assertEquals(NgThemePresentationMode.STANDARD, NgThemeModeStore.current(context))
        context.defaultSharedPreferences.edit().putBoolean(PreferKey.ngCustomAppearanceEdited, false).commit()
        assertEquals(NgThemePresentationMode.MULTICOLOR, NgThemeModeStore.current(context))
    }

    @Test
    fun `deleting current theme returns to multicolor instead of selecting another package`() {
        val first = NgThemeTestFixtures.autumn.copy(id = "local.current")
        val other = NgThemeTestFixtures.storybook.copy(id = "local.other")
        context.defaultSharedPreferences.edit().clear()
            .putString(PreferKey.ngThemePresentationMode, "standard")
            .putString("ngManagedThemes.v1", GSON.toJson(listOf(first, other)))
            .putString("ngActiveManagedThemeId.v1", first.id).commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
        assertNotNull(NgThemeLibraryStore.remove(context, first.id, restoreFallback = false))
        assertEquals(listOf(other.id), NgThemeLibraryStore.current(context).savedThemes.map { it.id })
        assertEquals(NgThemeModeGroup.STANDARD, NgThemeModeStore.currentGroup(context))
        assertEquals(NgThemePresentationMode.MULTICOLOR, NgThemeModeStore.current(context))
    }

    @Test
    fun `deleting the final unselected package resets manual fallback within the custom group`() {
        val theme = NgThemeTestFixtures.autumn.copy(id = "local.last")
        context.defaultSharedPreferences.edit().clear()
            .putString(PreferKey.ngThemePresentationMode, "standard")
            .putBoolean(PreferKey.ngCustomAppearanceEdited, true)
            .putString("ngManagedThemes.v1", GSON.toJson(listOf(theme))).commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
        assertNotNull(NgThemeLibraryStore.remove(context, theme.id, restoreFallback = false))
        assertTrue(NgThemeLibraryStore.current(context).savedThemes.isEmpty())
        assertEquals(NgThemeModeGroup.STANDARD, NgThemeModeStore.currentGroup(context))
        assertEquals(NgThemePresentationMode.MULTICOLOR, NgThemeModeStore.current(context))
    }

    @Test
    fun `clearing the library under a built in mode preserves that mode but forgets custom activation`() {
        val theme = NgThemeTestFixtures.autumn.copy(id = "local.last.internal")
        context.defaultSharedPreferences.edit().clear()
            .putString(PreferKey.ngThemePresentationMode, "soft_gradient")
            .putBoolean(PreferKey.ngCustomAppearanceEdited, true)
            .putString("ngManagedThemes.v1", GSON.toJson(listOf(theme))).commit()
        NgThemeLibraryStore.reloadAfterRestore(context)
        assertNotNull(NgThemeLibraryStore.remove(context, theme.id))
        assertEquals(NgThemePresentationMode.SOFT_GRADIENT, NgThemeModeStore.current(context))
        assertFalse(context.defaultSharedPreferences.getBoolean(PreferKey.ngCustomAppearanceEdited, true))
        context.defaultSharedPreferences.edit().putString(PreferKey.ngThemePresentationMode, "standard").commit()
        assertEquals(NgThemePresentationMode.MULTICOLOR, NgThemeModeStore.current(context))
    }

}
