package io.legado.app.help.ai

import android.app.Application
import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.provider.MediaStore
import android.util.AtomicFile
import kotlinx.coroutines.runBlocking
import okio.ByteString.Companion.decodeBase64
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import splitties.init.injectAsAppCtx
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

@RunWith(RobolectricTestRunner::class)
@Config(
    application = Application::class,
    manifest = Config.NONE,
    sdk = [35],
    shadows = [AiImageWorksTest.AtomicFileRenameShadow::class],
)
class AiImageWorksTest {
    @get:Rule val temp = TemporaryFolder()

    private val image = requireNotNull(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aV4sAAAAASUVORK5CYII="
            .decodeBase64()
    )

    @Before
    fun setUp() {
        RuntimeEnvironment.getApplication().injectAsAppCtx()
    }

    @Test
    fun batchRemovalAndRestoreKeepChatAndAttachmentFilesReadable() {
        val first = saveWork("first", 1_000L)
        val second = saveWork("second", 2_000L)
        val third = saveWork("third", 3_000L)
        val directory = requireNotNull(AiImageAssets.file(first)?.parentFile)
        val works = AiImageWorksStore(directory)
        val selected = setOf(first, second)

        assertEquals(listOf(third, second, first), works.read().visible)
        val removed = works.setRemoved(selected, true)
        assertEquals(listOf(third), removed.visible)
        assertEquals(listOf(second, first), removed.removed)
        assertEquals(removed, AiImageWorksStore(directory).read())
        assertEquals(removed, works.setRemoved(selected, true))
        assertArrayEquals(image.toByteArray(), codexImageFile("conversation", "first").readBytes())
        assertEquals(image.base64(), AiImageAssets.loadInputs(listOf(second)).single().base64)

        val restored = AiImageWorksStore(directory).setRemoved(selected, false)
        assertEquals(listOf(third, second, first), restored.visible)
        assertTrue(restored.removed.isEmpty())
        assertEquals(restored, works.read())
    }

    @Test
    fun hidingWorksOnlyRecordsExistingCreations() {
        val first = saveWork("first", 1_000L)
        val imported = requireNotNull(AiImageAssets.file(first.replaceFirst("g-", "i-")))
        imported.writeBytes(image.toByteArray())
        val missing = AiImageAssets.generatedId("conversation", "missing")
        val works = AiImageWorksStore(requireNotNull(imported.parentFile))

        val removed = works.setRemoved(setOf(first, imported.nameWithoutExtension, missing), true)
        assertEquals(listOf(first), removed.removed)
        assertArrayEquals(image.toByteArray(), imported.readBytes())
        assertEquals(missing, saveWork("missing", 2_000L))
        val current = works.read()
        assertEquals(listOf(missing), current.visible)
        assertEquals(listOf(first), current.removed)
    }

    @Test
    fun batchExportPublishesValidCopiesAndRemovesFailedPendingEntries() {
        val context = RuntimeEnvironment.getApplication()
        val album = TestAlbumProvider(temp.newFolder())
        album.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", album)
        val first = saveWork("first", 1_000L)
        val second = saveWork("second", 2_000L)
        val third = saveWork("third", 3_000L)

        val failed = runBlocking { saveAiImageWorksToAlbum(context, setOf(first, second, third)) }

        assertEquals(3, album.inserted)
        assertEquals(setOf(second), failed)
        assertEquals(2, album.images.size)
        album.images.values.forEach { saved ->
            assertEquals("Pictures/阅读 NG", saved.values.getAsString(MediaStore.MediaColumns.RELATIVE_PATH))
            assertEquals("image/png", saved.values.getAsString(MediaStore.MediaColumns.MIME_TYPE))
            assertEquals(0, saved.values.getAsInteger(MediaStore.MediaColumns.IS_PENDING))
            assertEquals("png", saved.file.extension)
            assertArrayEquals(image.toByteArray(), saved.file.readBytes())
        }
        assertArrayEquals(image.toByteArray(), codexImageFile("conversation", "second").readBytes())
    }

    private fun saveWork(receipt: String, modifiedAt: Long): String {
        val id = AiImageAssets.generatedId("conversation", receipt)
        AiImageAssets.saveGenerated("conversation", receipt, image)
        assertTrue(requireNotNull(AiImageAssets.file(id)).setLastModified(modifiedAt))
        return id
    }

    /** Use Android's rename-over-existing behavior on a Windows JVM. */
    @Implements(AtomicFile::class)
    class AtomicFileRenameShadow {
        companion object {
            @JvmStatic
            @Implementation
            fun rename(source: File, target: File) {
                Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING)
            }
        }
    }

    private class TestAlbumProvider(private val directory: File) : ContentProvider() {
        data class Image(val values: ContentValues, val file: File)

        var inserted = 0
            private set
        val images = linkedMapOf<Uri, Image>()

        override fun onCreate() = true

        override fun insert(uri: Uri, values: ContentValues?): Uri {
            val initial = ContentValues(requireNotNull(values))
            check(initial.getAsInteger(MediaStore.MediaColumns.IS_PENDING) == 1)
            val item = ContentUris.withAppendedId(uri, (++inserted).toLong())
            val file = File(directory, initial.getAsString(MediaStore.MediaColumns.DISPLAY_NAME))
            images[item] = Image(initial, file)
            shadowOf(requireNotNull(context).contentResolver).registerOutputStreamSupplier(item) {
                if (ContentUris.parseId(item) == 2L) throw FileNotFoundException("fixture write failed")
                file.outputStream()
            }
            return item
        }

        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            images.getValue(uri).values.putAll(requireNotNull(values))
            return 1
        }

        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            val removed = images.remove(uri) ?: return 0
            removed.file.delete()
            return 1
        }

        override fun getType(uri: Uri): String? = null

        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?,
        ): Cursor? = null
    }
}
