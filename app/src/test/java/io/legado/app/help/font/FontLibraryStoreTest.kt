package io.legado.app.help.font

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.resource.ResourceRepository
import io.legado.app.ui.design.theme.InterfaceFontTestApplication
import io.legado.app.utils.defaultSharedPreferences
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = InterfaceFontTestApplication::class)
class FontLibraryStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    private fun source(): File = File.createTempFile("library-test-", ".ttf", context.cacheDir).apply {
        writeBytes(context.assets.open("font/number.ttf").use { it.readBytes() } + UUID.randomUUID().toString().toByteArray())
    }

    @Test fun sameContentUsesOneManagedFileWithoutChangingTheExternalOriginal() {
        val external = source()
        val original = external.readBytes()
        var asset: LocalFontAsset? = null
        try {
            val first = FontLibraryStore.prepareDownload(external, "测试字体.ttf").commit().also { asset = it }
            val second = FontLibraryStore.prepareDownload(external, "另一个名字.otf").commit()
            assertEquals(first.id, second.id)
            assertEquals(first.path, second.path)
            assertEquals(1, second.paths.size)
            assertArrayEquals(original, external.readBytes())
            assertTrue(external.delete())
            assertArrayEquals(original, File(first.path).readBytes())
        } finally {
            asset?.let(FontLibraryStore::deleteFiles)
            external.delete()
        }
    }

    @Test fun interfaceSaveReferencesTheOriginalWithoutAddingAnAsset() = runBlocking {
        val external = source()
        val original = external.readBytes()
        val preferences = context.defaultSharedPreferences
        val keys = listOf("ngInterfaceFont.choice", "ngInterfaceFont.file", "ngInterfaceFont.name")
        val previous = keys.associateWith { preferences.getString(it, null) }
        val assetsBefore = FontLibraryStore.list().map { it.id }.toSet()
        try {
            val uri = external.toURI().toString()
            val preview = NgInterfaceFontStore.load(context, uri, reloadFromSource = true)
            NgInterfaceFontStore.save(context, uri, "源字体", preview)
            assertNull(preferences.getString("ngInterfaceFont.file", null))
            assertEquals(uri, preferences.getString("ngInterfaceFont.choice", null))
            assertEquals(external, preview.file)
            assertArrayEquals(original, external.readBytes())
            assertEquals(assetsBefore, FontLibraryStore.list().map { it.id }.toSet())
            external.delete()
            assertSame(preview.typeface, NgInterfaceFontStore.typeface(context))
            assertTrue(runCatching { NgInterfaceFontStore.load(context, uri, reloadFromSource = true) }.isFailure)
            NgInterfaceFontStore.save(context, "system", "系统字体", null)
            assertNull(preferences.getString("ngInterfaceFont.file", null))
        } finally {
            preferences.edit().apply { previous.forEach { (key, value) -> putString(key, value) } }.commit()
            external.delete()
        }
    }

    @Test fun changedSameSizeTimestampIsDetectedAndItsFileIsNotOverwrittenOrDeleted() {
        val external = source()
        val original = external.readBytes()
        var originalAsset: LocalFontAsset? = null
        var changedAsset: LocalFontAsset? = null
        var restoredAsset: LocalFontAsset? = null
        try {
            val imported = FontLibraryStore.prepareDownload(external, "原字体").commit().also { originalAsset = it }
            val file = File(imported.path)
            val stamp = file.lastModified()
            val changed = original.copyOf().also { it[it.lastIndex] = (it.last() + 1).toByte() }
            file.writeBytes(changed)
            assertTrue(file.setLastModified(stamp))
            val cached = FontLibraryStore.list().first { file.path in it.paths }
            assertEquals(imported.id, cached.id)
            val failure = assertThrows(IllegalStateException::class.java) {
                runBlocking { ResourceRepository.deleteFont(cached.copy(path = file.path, paths = listOf(file.path))) }
            }
            assertTrue(failure.message.orEmpty().contains("已变化"))
            assertArrayEquals(changed, file.readBytes())
            val changedEntry = FontLibraryStore.list(refresh = true).first { file.path in it.paths }.also { changedAsset = it }
            assertNotEquals(imported.id, changedEntry.id)
            assertThrows(IllegalStateException::class.java) { FontLibraryStore.deleteFiles(imported) }
            val restored = FontLibraryStore.prepareDownload(external, "重新下载").commit().also { restoredAsset = it }
            assertNotEquals(file.path, restored.path)
            assertArrayEquals(changed, file.readBytes())
            assertArrayEquals(original, File(restored.path).readBytes())
        } finally {
            changedAsset?.let(FontLibraryStore::deleteFiles)
            restoredAsset?.let(FontLibraryStore::deleteFiles)
            originalAsset?.takeIf { File(it.path).isFile }?.let { File(it.path).writeBytes(original); FontLibraryStore.deleteFiles(it) }
            external.delete()
        }
    }

    @Test fun invalidFontDoesNotCreateALibraryAsset() {
        val file = File.createTempFile("invalid-font-", ".ttf", context.cacheDir)
        try {
            val before = FontLibraryStore.list().map { it.id }.toSet()
            file.writeText("this is not a font")
            assertThrows(IllegalArgumentException::class.java) { FontLibraryStore.prepareDownload(file, "坏字体").commit() }
            assertEquals(before, FontLibraryStore.list().map { it.id }.toSet())
        } finally { file.delete() }
    }
}
