package io.legado.app.help.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CancellationException

class BookCacheSizeTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun countsNestedCacheAndPartialDownloadsWithoutIncludingSiblings() {
        val cache = temporary.newFolder("book")
        File(cache, "chapter.nb").writeBytes(ByteArray(100))
        File(cache, "images").mkdir()
        File(cache, "images/picture.jpg").writeBytes(ByteArray(200))
        File(cache, "pending.part").writeBytes(ByteArray(30))
        temporary.newFile("original.epub").writeBytes(ByteArray(900))

        assertEquals(330L, bookCacheDirectoryBytes(cache))
        assertEquals(900L, File(temporary.root, "original.epub").length())
        assertTrue(File(cache, "pending.part").exists())
    }

    @Test
    fun missingAndEmptyDirectoriesUseZeroBytes() {
        assertEquals(0L, bookCacheDirectoryBytes(temporary.newFolder()))
        assertEquals(0L, bookCacheDirectoryBytes(File(temporary.root, "missing")))
    }

    @Test(expected = IOException::class)
    fun aFileCannotBeMistakenForABookCacheDirectory() {
        bookCacheDirectoryBytes(temporary.newFile())
    }

    @Test(expected = CancellationException::class)
    fun leavingThePageCanCancelDirectoryTraversal() {
        val cache = temporary.newFolder("book")
        File(cache, "chapter.nb").writeBytes(ByteArray(10))
        bookCacheDirectoryBytes(cache) { throw CancellationException() }
    }

    @Test
    fun ignoresLinksOutsideTheBookAndDoesNotLoopBackIntoItself() {
        val cache = temporary.newFolder("book")
        val original = temporary.newFile("original.txt").apply { writeBytes(ByteArray(900)) }
        File(cache, "chapter.nb").writeBytes(ByteArray(10))
        try {
            Files.createSymbolicLink(File(cache, "external").toPath(), original.toPath())
            Files.createSymbolicLink(File(cache, "cycle").toPath(), cache.toPath())
        } catch (e: IOException) {
            assumeNoException("Host does not permit symbolic links", e)
        } catch (e: UnsupportedOperationException) {
            assumeNoException(e)
        }

        assertEquals(10L, bookCacheDirectoryBytes(cache))
        assertEquals(900L, original.length())
    }
}
