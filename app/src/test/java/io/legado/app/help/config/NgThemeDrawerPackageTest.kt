package io.legado.app.help.config

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import io.legado.app.utils.GSON
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.Base64
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class NgThemeDrawerPackageTest {
    @Test
    fun `theme image source uses global package backgrounds without custom drawer copies`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = sampleImage(context)
        val archive = File(context.cacheDir, "${UUID.randomUUID()}.ngtheme")
        val theme = NgBuiltInThemes.storybook.copy(
            lightBackground = NgThemeBackground(image.path),
            darkBackground = NgThemeBackground(image.path),
            drawerProfile = NgThemeDrawerProfile(source = "theme_image"),
        )
        try {
            NgThemePackageManager.exportTheme(context, theme, Uri.fromFile(archive)).getOrThrow()
            ZipFile(archive).use { zip ->
                assertEquals(2, zip.size())
                val json = zip.getInputStream(zip.getEntry("manifest.json")).reader().use {
                    GSON.fromJson(it, JsonObject::class.java)
                }
                assertNull(json["lightDrawerImageAsset"])
                assertNull(json["darkDrawerImageAsset"])
                assertEquals("theme_image", json.getAsJsonObject("theme")
                    .getAsJsonObject("drawerProfile")["source"].asString)
            }
        } finally {
            image.delete()
            archive.delete()
        }
    }

    @Test
    fun `drawer images round trip through a portable package and survive source deletion`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = sampleImage(context)
        val theme = customTheme(image.absolutePath)
        val archive = File(context.cacheDir, "${UUID.randomUUID()}.ngtheme")
        NgThemePackageManager.exportTheme(context, theme, Uri.fromFile(archive)).getOrThrow()
        ZipFile(archive).use { zip ->
            val manifest = zip.getInputStream(zip.getEntry("manifest.json")).reader().use { it.readText() }
            assertFalse(manifest.contains(image.absolutePath))
            val json = GSON.fromJson(manifest, JsonObject::class.java)
            assertEquals(json["lightDrawerImageAsset"], json["darkDrawerImageAsset"])
            assertNotNull(zip.getEntry(json["lightDrawerImageAsset"].asString))
        }
        val imported = NgThemePackageManager.importTheme(context, Uri.fromFile(archive)).getOrThrow()
        try {
            val drawer = requireNotNull(imported.drawerProfile)
            assertEquals("custom_image", drawer.source)
            assertEquals(theme.drawerProfile?.dark?.backgroundColor, drawer.dark.backgroundColor)
            assertEquals(drawer.light.imagePath, drawer.dark.imagePath)
            assertArrayEquals(image.readBytes(), File(requireNotNull(drawer.light.imagePath)).readBytes())
            image.delete()
            assertTrue(File(requireNotNull(drawer.light.imagePath)).isFile)
        } finally {
            NgThemeLibraryStore.remove(context, imported.id)
            archive.delete()
            image.delete()
        }
    }

    @Test
    fun `import ignores embedded external image paths and rejects escaping asset entries`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val theme = customTheme("/private/another-app/secret.png")
        val manifest = JsonObject().apply {
            addProperty("format", "reading-ng-theme")
            addProperty("version", 1)
            add("theme", GSON.toJsonTree(theme))
        }
        val archive = packageFile(context, manifest)
        val imported = NgThemePackageManager.importTheme(context, Uri.fromFile(archive)).getOrThrow()
        try {
            assertNull(imported.drawerProfile?.light?.imagePath)
            assertNull(imported.drawerProfile?.dark?.imagePath)
        } finally {
            NgThemeLibraryStore.remove(context, imported.id)
            archive.delete()
        }
        manifest.addProperty("lightDrawerImageAsset", "../outside.png")
        val malicious = packageFile(context, manifest)
        try {
            assertTrue(NgThemePackageManager.importTheme(context, Uri.fromFile(malicious)).isFailure)
        } finally {
            malicious.delete()
        }
    }

    @Test
    fun `saved copies share managed images until the last reference is removed`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = sampleImage(context)
        val draftPath = NgThemeDrawerAssets.copyDraft(context, Uri.fromFile(image))
        val first = NgThemeLibraryStore.addOrReplace(context, customTheme(draftPath))
        val managed = File(requireNotNull(first.drawerProfile?.light?.imagePath))
        assertFalse(File(draftPath).exists())
        val second = NgThemeLibraryStore.addOrReplace(context, first.copy(
            id = "local.${UUID.randomUUID()}",
            name = "copy-${UUID.randomUUID()}",
        ))
        try {
            NgThemeDrawerAssets.discardDrafts(context, first.drawerProfile)
            assertTrue(managed.isFile)
            NgThemeLibraryStore.remove(context, first.id)
            assertTrue(managed.isFile)
            NgThemeLibraryStore.remove(context, second.id)
            assertFalse(managed.exists())
        } finally {
            NgThemeLibraryStore.remove(context, first.id)
            NgThemeLibraryStore.remove(context, second.id)
            image.delete()
        }
    }

    @Test
    fun `cancelling an image draft removes only the staged copy`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = sampleImage(context)
        val draftPath = NgThemeDrawerAssets.copyDraft(context, Uri.fromFile(image))
        NgThemeDrawerAssets.discardDraft(context, image.absolutePath)
        assertTrue(image.isFile)
        NgThemeDrawerAssets.discardDraft(context, draftPath)
        assertFalse(File(draftPath).exists())
        assertTrue(image.isFile)
        image.delete()
    }

    @Test
    fun `failed image materialization releases new files and preserves the draft`() = runBlocking<Unit> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val image = sampleImage(context)
        val draftPath = NgThemeDrawerAssets.copyDraft(context, Uri.fromFile(image))
        val invalid = File(context.cacheDir, "${UUID.randomUUID()}.png").apply {
            writeText("not an image")
        }
        val gallery = File(context.filesDir, "ng_drawer_images")
        val before = gallery.list().orEmpty().toSet()
        val theme = customTheme(draftPath).let {
            val profile = requireNotNull(it.drawerProfile)
            it.copy(drawerProfile = profile.updated(
                true,
                profile.dark.copy(imagePath = invalid.absolutePath),
            ))
        }
        try {
            assertTrue(runCatching { NgThemeLibraryStore.addOrReplace(context, theme) }.isFailure)
            assertEquals(before, gallery.list().orEmpty().toSet())
            assertTrue(File(draftPath).isFile)
            assertFalse(NgThemeLibraryStore.current(context).savedThemes.any { it.id == theme.id })
        } finally {
            NgThemeDrawerAssets.discardDraft(context, draftPath)
            image.delete()
            invalid.delete()
        }
    }

    private fun customTheme(path: String): NgManagedTheme = NgBuiltInThemes.storybook.copy(
        id = "local.${UUID.randomUUID()}",
        name = "drawer-${UUID.randomUUID()}",
        lightBackground = NgThemeBackground(),
        darkBackground = NgThemeBackground(),
        drawerProfile = requireNotNull(NgBuiltInThemes.storybook.drawerProfile).let { profile ->
            profile.copy(
                source = "custom_image",
                light = profile.light.copy(imagePath = path),
                dark = profile.dark.copy(imagePath = path),
            )
        },
    )

    private fun packageFile(context: Context, manifest: JsonObject): File =
        File(context.cacheDir, "${UUID.randomUUID()}.ngtheme").also { archive ->
            ZipOutputStream(archive.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(GSON.toJson(manifest).toByteArray())
                zip.closeEntry()
            }
        }

    private fun sampleImage(context: Context): File =
        File(context.cacheDir, "${UUID.randomUUID()}.png").also {
            it.writeBytes(Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            ))
        }
}
