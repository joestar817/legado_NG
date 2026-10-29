package io.legado.app.help.book

import android.app.Application
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.legado.app.constant.BookType
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.exoplayer.AudioDownloadCache
import io.legado.app.utils.GSON
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import splitties.init.injectAsAppCtx
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/** Real cache files and production reorder helpers; no service, player, or UI is started. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = ChapterCacheReorderTestApplication::class, manifest = Config.NONE)
class ChapterCacheReorderTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bookDirectories = mutableListOf<File>()

    @After
    fun cleanBookFixtures() {
        bookDirectories.forEach { it.deleteRecursively() }
    }

    @Test
    fun sameTitleThreeWayCyclePublishesTheRightBodiesAndRoundTripsExactly() {
        val directory = temporary.newFolder("cycle")
        val old = (0..2).map { BookChapter(url = "chapter-$it", title = "同名章节", index = it) }
        val updated = old.map { it.copy(index = (it.index + 1) % old.size) }
        old.forEach { write(File(directory, it.getFileName()), "body:${it.url}") }
        val before = snapshot(directory)
        val times = old.associate { it.url to File(directory, it.getFileName()).lastModified() }
        val moves = old.zip(updated).map { (source, target) ->
            ChapterCacheFileMove(File(directory, source.getFileName()), File(directory, target.getFileName()))
        }

        val result = withReorderedCacheFiles(directory, moves) {
            updated.forEach { chapter ->
                val target = File(directory, chapter.getFileName())
                assertEquals("body:${chapter.url}", target.readText())
                assertEquals(times.getValue(chapter.url), target.lastModified())
            }
            "committed"
        }
        assertEquals("committed", result)
        withReorderedCacheFiles(directory, moves.map { ChapterCacheFileMove(it.target, it.source) }) {}
        assertEquals(before, snapshot(directory))
        assertNoStaging(directory)
    }

    @Test
    fun chapterWrapperKeepsMangaImagesMissingChaptersAndCompanionFilesAcrossTwoReversals() {
        val book = book(BookType.image)
        val directory = directory(book)
        val old = (0..4).map { index ->
            BookChapter(bookUrl = book.bookUrl, url = "${book.bookUrl}/$index",
                title = "同名章节", index = index, isVolume = index == 1)
        }
        val updated = old.reversed().mapIndexed { index, chapter -> chapter.copy(index = index) }
        val imageUrl = "https://cache-fixture.invalid/page.svg"
        val image = BookHelp.getImage(book, imageUrl)
        write(image, """<svg xmlns="http://www.w3.org/2000/svg" width="2" height="3"><rect width="2" height="3"/></svg>""")
        val cached = old.filter { !it.isVolume && it.index != 3 }
        val bodies = cached.associate { it.url to "${it.url}<img src=\"$imageUrl\">" }
        cached.forEach { write(File(directory, it.getFileName()), bodies.getValue(it.url)) }
        val companionSuffixes = listOf(".epub-positions.gz", ".epub-positions.gz.bak", ".epub-positions.gz.new")
        companionSuffixes.forEach { suffix ->
            write(File(directory, old.first().getFileName() + suffix), "companion:$suffix")
        }
        val before = snapshot(directory)
        val imageBytes = image.readBytes()
        val imageTime = image.lastModified()
        val cachedUrls = bodies.keys
        assertEquals(cachedUrls, loadBookCacheStatus(book, old).chapterUrls)

        withReorderedChapterCache(book, old, updated) {}

        assertEquals(cachedUrls, loadBookCacheStatus(book, updated).chapterUrls)
        assertEquals(4, loadBookCacheStatus(book, updated).total)
        updated.forEach { chapter ->
            val file = File(directory, chapter.getFileName())
            if (chapter.url in bodies) assertEquals(bodies.getValue(chapter.url), file.readText())
            else assertFalse(file.exists())
        }
        val movedFirst = updated.single { it.url == old.first().url }
        companionSuffixes.forEach { suffix ->
            assertEquals("companion:$suffix", File(directory, movedFirst.getFileName() + suffix).readText())
        }
        assertArrayEquals(imageBytes, image.readBytes())
        assertEquals(imageTime, image.lastModified())
        withReorderedChapterCache(book, updated, old) {}
        assertEquals(before, snapshot(directory))
        assertEquals(cachedUrls, loadBookCacheStatus(book, old).chapterUrls)
        assertNoStaging(directory)
    }

    @Test
    fun missingSourceRemovesStaleTargetInsteadOfInventingACacheHit() {
        val directory = temporary.newFolder("missing")
        val source = File(directory, "missing.nb")
        val target = write(File(directory, "stale.nb"), "unrelated old body")
        val keep = write(File(directory, "images/keep.svg"), "unrelated image")

        withReorderedCacheFiles(directory, listOf(ChapterCacheFileMove(source, target))) {
            assertFalse(source.exists())
            assertFalse(target.exists())
        }

        assertEquals(setOf("images/keep.svg"), snapshot(directory).keys)
        assertEquals("unrelated image", keep.readText())
        assertNoStaging(directory)
    }

    @Test
    fun commitFailureOrCancellationRestoresOriginalsStaleTargetsAndTimestamps() {
        listOf(IOException("database rejected commit"), CancellationException("canceled"))
            .forEachIndexed { index, failure ->
                val directory = temporary.newFolder("rollback-$index")
                val first = write(File(directory, "first.nb"), "first")
                val second = write(File(directory, "second.nb"), "second")
                val stale = write(File(directory, "stale.nb"), "stale")
                val replacementTarget = write(File(directory, "replacement.nb"), "previous target")
                val missing = File(directory, "missing.nb")
                val created = File(directory, "created.nb")
                write(File(directory, "images/keep"), "keep")
                val before = snapshot(directory)
                val times = directory.walkTopDown().filter(File::isFile)
                    .associate { it.relativeTo(directory).invariantSeparatorsPath to it.lastModified() }
                val moves = listOf(
                    ChapterCacheFileMove(first, replacementTarget, "replacement".toByteArray()),
                    ChapterCacheFileMove(second, created),
                    ChapterCacheFileMove(missing, stale),
                )

                try {
                    withReorderedCacheFiles(directory, moves) {
                        assertEquals("replacement", replacementTarget.readText())
                        assertEquals("second", created.readText())
                        assertFalse(stale.exists())
                        throw failure
                    }
                    fail("Failed commit must propagate")
                } catch (actual: Throwable) {
                    assertSame(failure, actual)
                }

                assertEquals(before, snapshot(directory))
                times.forEach { (path, time) -> assertEquals(time, File(directory, path).lastModified()) }
                assertFalse(created.exists())
                assertNoStaging(directory)
            }
    }

    @Test
    fun replacementChangesOnlyPublishedBytesAndLeavesUnrelatedFilesAlone() {
        val directory = temporary.newFolder("replacement")
        val source = write(File(directory, "old.json"), "original")
        val target = File(directory, "new.json")
        write(File(directory, "other-book.nb"), "keep")
        val bytes = "new metadata".toByteArray()

        withReorderedCacheFiles(directory, listOf(ChapterCacheFileMove(source, target, bytes))) {}

        assertFalse(source.exists())
        assertArrayEquals(bytes, target.readBytes())
        assertEquals("keep", File(directory, "other-book.nb").readText())
        assertEquals(setOf("new.json", "other-book.nb"), snapshot(directory).keys)
        assertNoStaging(directory)
    }

    @Test
    fun escapingPathsAreRejectedBeforeAnyFileOrCommitChanges() {
        val directory = temporary.newFolder("inside")
        val inside = write(File(directory, "chapter.nb"), "inside")
        val outside = write(temporary.newFile("outside.nb"), "outside")
        listOf(
            ChapterCacheFileMove(inside, File(directory, "../outside.nb")),
            ChapterCacheFileMove(outside, inside),
        ).forEach { move ->
            var committed = false
            try {
                withReorderedCacheFiles(directory, listOf(move)) { committed = true }
                fail("Escaping path must be rejected")
            } catch (_: IllegalArgumentException) {
                assertFalse(committed)
            }
            assertEquals("inside", inside.readText())
            assertEquals("outside", outside.readText())
            assertNoStaging(directory)
        }
    }

    @Test
    fun audioManifestsFollowNewIndicesWhileKeepingMediaKeysAndRollbackBytes() {
        val book = book(BookType.audio)
        val directory = directory(book)
        val old = (0..1).map { index ->
            BookChapter(bookUrl = book.bookUrl, url = "${book.bookUrl}/$index",
                title = "同名音频", index = index)
        }
        val updated = old.reversed().mapIndexed { index, chapter -> chapter.copy(index = index) }
        // Obtain paths from the real planner, without duplicating its filename/hash algorithm.
        val locations = AudioDownloadCache.chapterReorderMoves(book, old, updated)
        val manifests = old.map { chapter -> audioManifest(book, chapter) }
        locations.zip(manifests).forEach { (move, manifest) -> write(move.source, GSON.toJson(manifest)) }
        val before = snapshot(directory)
        val failure = IOException("database commit failed")
        try {
            withReorderedChapterCache(book, old, updated) { throw failure }
            fail("Failed commit must propagate")
        } catch (actual: IOException) {
            assertSame(failure, actual)
        }
        assertEquals(before, snapshot(directory))

        withReorderedChapterCache(book, old, updated) {}

        locations.forEachIndexed { index, move ->
            assertFalse(move.source.exists())
            val actual = GSON.fromJson(move.target.readText(), JsonObject::class.java)
            val chapter = updated.single { it.url == old[index].url }
            val expected = manifests[index].deepCopy().apply {
                addProperty("chapter_index", chapter.index)
                addProperty("chapter_file_name", chapter.getFileName())
            }
            assertEquals(expected, actual)
            assertEquals(manifests[index].getAsJsonArray("parts"), actual.getAsJsonArray("parts"))
        }
        withReorderedChapterCache(book, updated, old) {}
        locations.forEachIndexed { index, move ->
            assertEquals(manifests[index], GSON.fromJson(move.source.readText(), JsonObject::class.java))
            assertFalse(move.target.exists())
        }
        assertNoStaging(directory)
    }

    private fun book(type: Int): Book {
        val id = UUID.randomUUID().toString()
        return Book(bookUrl = "https://cache-fixture.invalid/$id", origin = "https://source.invalid",
            name = "reorder-$id", author = "fixture", type = type, totalChapterNum = 5)
    }

    private fun directory(book: Book): File = File(BookHelp.cachePath, book.getFolderName()).also {
        bookDirectories += it
        check(it.mkdirs())
    }

    private fun audioManifest(book: Book, chapter: BookChapter) = JsonObject().apply {
        addProperty("version", 1)
        addProperty("source_url", book.origin)
        addProperty("book_url", book.bookUrl)
        addProperty("chapter_url", chapter.url)
        addProperty("chapter_index", chapter.index)
        addProperty("chapter_file_name", chapter.getFileName())
        addProperty("content", "https://audio.invalid/${chapter.index}.mp3")
        add("parts", JsonArray().apply {
            add(JsonObject().apply {
                addProperty("cache_key", "existing-media-key-${chapter.url}")
                addProperty("length", 1234L + chapter.index)
            })
        })
    }

    private fun write(file: File, content: String): File = file.apply {
        parentFile?.mkdirs()
        writeText(content)
    }

    private fun snapshot(directory: File): Map<String, String> = directory.walkTopDown()
        .filter(File::isFile).associate { file ->
            file.relativeTo(directory).invariantSeparatorsPath to
                MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }

    private fun assertNoStaging(directory: File) {
        assertTrue(directory.listFiles().orEmpty().none { it.name.startsWith(".chapter-cache-reorder-") })
    }
}

class ChapterCacheReorderTestApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        injectAsAppCtx()
    }
}
