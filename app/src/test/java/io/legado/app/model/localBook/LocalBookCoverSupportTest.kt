package io.legado.app.model.localBook

import io.legado.app.data.entities.Book
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalBookCoverSupportTest {

    @Test
    fun extractableFormats() {
        assertTrue(LocalBook.canExtractCover(Book(originName = "a.epub", type = 0)))
        assertTrue(LocalBook.canExtractCover(Book(originName = "a.mobi", type = 0)))
        assertTrue(LocalBook.canExtractCover(Book(originName = "a.azw3", type = 0)))
        assertTrue(LocalBook.canExtractCover(Book(originName = "a.umd", type = 0)))
        assertTrue(LocalBook.canExtractCover(Book(originName = "a.pdf", type = 0)))
    }

    @Test
    fun txtIsNotExtractable() {
        assertFalse(LocalBook.canExtractCover(Book(originName = "a.txt", type = 0)))
    }
}
