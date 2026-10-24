package io.legado.app.model.remote

import com.google.gson.Gson
import io.legado.app.data.entities.Book
import io.legado.app.constant.BookType
import org.junit.Assert.*
import org.junit.Test

class WebDavBookSyncTest {
    @Test fun equalFilesResolveEvenWithoutCheckpoint() {
        assertEquals(WebDavBookChange.SAME, webDavBookChange(null, "new", "new"))
        assertEquals(WebDavBookChange.SAME, webDavBookChange("old", "new", "new"))
    }

    @Test fun missingFileIsRecoverableRatherThanAConflict() {
        assertEquals(WebDavBookChange.LOCAL_MISSING, webDavBookChange("old", null, "new"))
    }

    @Test fun unknownBaselineCannotAuthorizeReplacement() {
        assertEquals(WebDavBookChange.UNTRACKED, webDavBookChange(null, "local", "cloud"))
    }

    @Test fun distinguishesTheTwoChangeDirections() {
        assertEquals(WebDavBookChange.LOCAL_CHANGED, webDavBookChange("old", "new", "old"))
        assertEquals(WebDavBookChange.REMOTE_CHANGED, webDavBookChange("old", "old", "new"))
        assertEquals(WebDavBookChange.CONFLICT, webDavBookChange("old", "local", "cloud"))
    }

    @Test fun sameFilenameInDifferentDirectoriesOrAccountsIsNotTheSameFile() {
        val path = "https://example.com/books/a/book.epub"
        assertNotEquals(webDavBookKey(null, path), webDavBookKey(1, path))
        assertNotEquals(webDavBookKey(1, path), webDavBookKey(2, path))
        assertNotEquals(webDavBookKey(1, path), webDavBookKey(1, path.replace("/a/", "/b/")))
    }

    @Test fun reservedCharactersStayPartOfTheFileIdentity() {
        val path = "https://example.com/books/a%23b%25.epub"
        assertNotEquals(webDavBookKey(null, path), webDavBookKey(null, "https://example.com/books/a"))
        assertEquals("https://example.com/books/", WebDavBookLocation(null, path).directory)
    }

    @Test fun checkpointHasStableSerializedFieldNamesAndRoundTrips() {
        val record = WebDavBookRecord(2, "https://example.com/books/a.epub", "/books/a.epub",
            "sha", 100, 20, 101, 20, "etag")
        val gson = Gson()
        val json = gson.toJson(record)
        assertTrue(json.contains("\"serverId\":2"))
        assertTrue(json.contains("\"localUri\":\"/books/a.epub\""))
        assertEquals(record, gson.fromJson(json, WebDavBookRecord::class.java))
    }

    @Test fun equivalentPathEscapesHaveOneIdentity() {
        assertEquals(webDavBookKey(null, "https://example.com/books/A%23%E4%B8%AD.epub"),
            webDavBookKey(null, "https://example.com/books/%41%23%e4%b8%ad.epub"))
        assertEquals("https://example.com/books/", normalizedWebDavBookUrl("https://example.com/books/"))
    }

    @Test fun numericServerAttributeKeepsItsAccount() {
        val book = Book(origin = BookType.webDavTag + "https://example.com/books/a.epub,{\"serverID\":\"12\"}")
        assertEquals(12L, WebDavBookLocation.fromBook(book)!!.serverId)
    }

    @Test fun invalidServerAttributeDoesNotFallBackToDefaultAccount() {
        val book = Book(origin = BookType.webDavTag + "https://example.com/books/a.epub,{\"serverID\":\"invalid\"}")
        assertThrows(IllegalStateException::class.java) { WebDavBookLocation.fromBook(book) }
    }
}
