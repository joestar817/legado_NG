package io.legado.app.help.book

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.help.CacheManager
import io.legado.app.help.config.AppConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import splitties.init.injectAsAppCtx
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.UUID

/** Uses real cache files and production completion helpers, not the network/service queue. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = BookChapterCacheTestApplication::class, manifest = Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookChapterCacheTest {
    private val cacheDirectories = mutableListOf<File>()
    private val loginHeaderKeys = mutableListOf<String>()
    private var originalCountWords = false

    @Before
    fun setUp() {
        originalCountWords = AppConfig.tocCountWords
        // These fixtures test file persistence without starting the Room word-count path.
        AppConfig.tocCountWords = false
    }

    @After
    fun tearDown() {
        cacheDirectories.forEach { it.deleteRecursively() }
        loginHeaderKeys.forEach(CacheManager::deleteMemory)
        AppConfig.tocCountWords = originalCountWords
    }

    @Test
    fun ordinaryTextWithoutImagesRemainsACompleteChapter() = runBlocking {
        val fixture = fixture(BookType.text)
        val content = "普通文字正文，不包含图片。"
        BookHelp.saveText(fixture.book, fixture.chapter, content)

        assertTrue(hasUsableChapterCacheContent(fixture.book, content))
        assertTrue(hasCompleteChapterImages(fixture.book, fixture.chapter, content))
        fixture.complete(content)

        assertEquals(content, BookHelp.getContent(fixture.book, fixture.chapter))
        assertEquals(1, fixture.status().cached)
        assertFalse(File(fixture.directory, "images").exists())
    }

    @Test
    fun firstCompletionPersistsAllImagesBeforeReturningAndReusesThemLater() = runBlocking {
        val fixture = fixture()
        val png = png()
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" width="2" height="3" viewBox="0 0 2 3"><rect width="2" height="3" fill="red"/></svg>""".toByteArray()
        val pngUrl = dataUrl(png)
        val svgUrl = dataUrl(svg, "image/svg+xml")
        val content = "<img src=\"$pngUrl\"><img src=\"$svgUrl\">"
        BookHelp.saveText(fixture.book, fixture.chapter, content)
        val textBefore = fixture.contentFile.readBytes()

        assertFalse(hasCompleteChapterImages(fixture.book, fixture.chapter, content))
        assertEquals(0, fixture.status().cached)
        fixture.complete(content)

        val pngFile = BookHelp.getImage(fixture.book, pngUrl)
        val svgFile = BookHelp.getImage(fixture.book, svgUrl)
        assertArrayEquals(png, pngFile.readBytes())
        assertArrayEquals(svg, svgFile.readBytes())
        assertNotNull(getCachedBookImage(pngFile))
        assertNotNull(getCachedBookImage(svgFile))
        assertEquals(1, fixture.status().cached)
        assertArrayEquals(textBefore, fixture.contentFile.readBytes())
        val imageNames = File(fixture.directory, "images").list()!!.toSet()
        assertEquals(setOf(pngFile.name, svgFile.name), imageNames)

        // If a complete image is fetched/decrypted again, this rule makes it fail.
        fixture.source.ruleContent = ContentRule(imageDecode = "null")
        val pngModified = pngFile.lastModified()
        val svgModified = svgFile.lastModified()
        fixture.complete(content)
        assertEquals(pngModified, pngFile.lastModified())
        assertEquals(svgModified, svgFile.lastModified())
        assertArrayEquals(textBefore, fixture.contentFile.readBytes())
    }

    @Test
    fun emptyOrImagelessMangaContentCannotBeReportedComplete() = runBlocking {
        val fixture = fixture()
        assertFalse(hasUsableChapterCacheContent(fixture.book, null))
        assertFalse(hasUsableChapterCacheContent(fixture.book, ""))
        val content = "旧的普通文本正文"
        BookHelp.saveText(fixture.book, fixture.chapter, content)

        assertFalse(hasUsableChapterCacheContent(fixture.book, content))
        assertFalse(hasCompleteChapterImages(fixture.book, fixture.chapter, content))
        expectIOException { fixture.complete(content) }
        assertEquals(0, fixture.status().cached)
        assertEquals(content, fixture.contentFile.readText())
    }

    @Test
    fun completionRequiresTheActualSavedBodyToMatch() = runBlocking {
        val fixture = fixture(BookType.text)
        val content = "待缓存正文"
        expectIOException { fixture.complete(content) }
        fixture.contentFile.parentFile!!.mkdirs()
        fixture.contentFile.writeText("")
        expectIOException { fixture.complete(content) }
        fixture.contentFile.writeText("另一份正文")
        expectIOException { fixture.complete(content) }
        assertEquals("另一份正文", fixture.contentFile.readText())
    }

    @Test
    fun strictImagePersistenceRepairsEmptyAndCorruptExistingFiles() = runBlocking {
        val fixture = fixture()
        val png = png()
        val url = dataUrl(png)
        val image = BookHelp.getImage(fixture.book, url)
        val content = "<img src=\"$url\">"
        BookHelp.saveText(fixture.book, fixture.chapter, content)
        image.parentFile!!.mkdirs()

        for (broken in listOf(byteArrayOf(), "broken-image".toByteArray())) {
            image.writeBytes(broken)
            assertNull(getCachedBookImage(image))
            BookHelp.saveImageForCache(null, fixture.book, url, fixture.chapter)
            assertArrayEquals(png, image.readBytes())
            assertNotNull(getCachedBookImage(image))
            assertEquals(1, fixture.status().cached)
            assertEquals(setOf(image.name), image.parentFile!!.list()!!.toSet())
        }
    }

    @Test
    fun badImageBytesAndNullDecryptionFailWithoutReplacingExistingFiles() = runBlocking {
        for (nullDecode in listOf(false, true)) {
            val fixture = fixture()
            val payload = if (nullDecode) png() else "not-an-image".toByteArray()
            val url = dataUrl(payload)
            val content = "<img src=\"$url\">"
            BookHelp.saveText(fixture.book, fixture.chapter, content)
            if (nullDecode) fixture.source.ruleContent = ContentRule(imageDecode = "null")
            val image = BookHelp.getImage(fixture.book, url)

            expectIOException { fixture.complete(content) }
            assertFalse(image.exists())
            assertEquals(0, fixture.status().cached)
            assertTrue(image.parentFile?.list().isNullOrEmpty())

            val oldBytes = "existing-broken-file".toByteArray()
            image.parentFile!!.mkdirs()
            image.writeBytes(oldBytes)
            expectIOException { fixture.complete(content) }
            assertArrayEquals(oldBytes, image.readBytes())
            assertEquals(setOf(image.name), image.parentFile!!.list()!!.toSet())
            assertEquals(0, fixture.status().cached)
        }
    }

    @Test
    fun cancellationReachesTheCallerWithoutCompletingOrCreatingImages() = runBlocking {
        val fixture = fixture()
        val url = dataUrl(png())
        val content = "<img src=\"$url\">"
        BookHelp.saveText(fixture.book, fixture.chapter, content)
        val image = BookHelp.getImage(fixture.book, url)
        val operations: List<suspend () -> Unit> = listOf(
            { fixture.complete(content) },
            { BookHelp.saveImageForCache(null, fixture.book, url, fixture.chapter) },
        )

        for (operation in operations) {
            var entered = false
            var cancellation: CancellationException? = null
            var completed = false
            val task = launch(start = CoroutineStart.UNDISPATCHED) {
                coroutineContext.cancel(CancellationException("cache-test-cancel"))
                entered = true
                try {
                    operation()
                    completed = true
                } catch (error: CancellationException) {
                    cancellation = error
                    throw error
                }
            }
            task.join()
            assertTrue(entered)
            assertFalse(completed)
            assertNotNull(cancellation)
            assertFalse(image.exists())
            assertEquals(0, fixture.status().cached)
        }
    }

    private fun fixture(type: Int = BookType.image): Fixture {
        val id = UUID.randomUUID().toString()
        val source = BookSource(
            bookSourceUrl = "https://cache-fixture.invalid/source/$id",
            header = """{"User-Agent":"BookChapterCacheTest"}""",
            enabledCookieJar = false,
        )
        // AnalyzeUrl checks login headers even for data URIs; avoid unrelated Room access.
        val loginKey = "loginHeader_${source.bookSourceUrl}"
        CacheManager.putMemory(loginKey, "{}")
        loginHeaderKeys += loginKey
        val book = Book(
            bookUrl = "https://cache-fixture.invalid/book/$id",
            name = "cache-$id",
            origin = source.bookSourceUrl,
            type = type,
            totalChapterNum = 1,
        )
        val chapter = BookChapter(
            bookUrl = book.bookUrl,
            url = "${book.bookUrl}/chapter/1",
            title = "第一章",
            index = 0,
        )
        val directory = File(BookHelp.cachePath, book.getFolderName())
        cacheDirectories += directory
        return Fixture(source, book, chapter, directory)
    }

    private data class Fixture(
        val source: BookSource,
        val book: Book,
        val chapter: BookChapter,
        val directory: File,
    ) {
        val contentFile: File get() = File(directory, chapter.getFileName())
        fun status(): BookCacheStatus = loadBookCacheStatus(book, listOf(chapter))
        suspend fun complete(content: String) = completeChapterCache(source, book, chapter, content)
    }

    private suspend fun expectIOException(block: suspend () -> Unit) {
        try {
            block()
            fail("Incomplete cache must not return successfully")
        } catch (_: IOException) {
            // The caller must receive failure instead of a successful completion.
        }
    }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.RED)
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun dataUrl(bytes: ByteArray, mime: String = "image/png"): String =
        "data:$mime;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
}

class BookChapterCacheTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        injectAsAppCtx()
    }
}
