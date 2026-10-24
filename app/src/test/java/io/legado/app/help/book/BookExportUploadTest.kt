package io.legado.app.help.book

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class BookExportUploadTest {
    @Test
    fun sendsTheCompletedFileOnce() = runBlocking {
        var calls = 0
        val result = attemptBookExportUpload { calls++ }
        assertTrue(result.isSuccess)
        assertEquals(1, calls)
    }

    @Test
    fun uploadFailureRemainsSeparateFromTheCompletedLocalExport() = runBlocking {
        val failure = IOException("HTTP 403")
        assertSame(failure, attemptBookExportUpload { throw failure }.exceptionOrNull())
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotConvertedIntoAnUploadFailure() = runBlocking {
        attemptBookExportUpload { throw CancellationException("cancel export") }
        Unit
    }

    @Test
    fun specialAndUnicodeNamesStayInsideOnePathSegment() {
        for (format in listOf("txt", "epub")) {
            val fileName = "测试 #1?50%/完成.$format"
            val url = bookExportWebDavUrl("https://example.com/dav/legado/books/", fileName).toHttpUrl()
            assertEquals(listOf("dav", "legado", "books", fileName), url.pathSegments)
            assertEquals(null, url.query)
            assertEquals(null, url.fragment)
        }
    }
}
