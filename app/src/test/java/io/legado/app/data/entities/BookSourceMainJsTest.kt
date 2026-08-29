package io.legado.app.data.entities

import io.legado.app.model.jsSource.isJsSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookSourceMainJsTest {

    @Test
    fun jsSourceUsesMainScriptForLoginAndEquality() {
        val source = BookSource(
            bookSourceUrl = "https://example.com",
            bookSourceName = "示例",
            loginUrl = "https://example.com/login",
        )
        assertFalse(source.isJsSource())
        assertEquals("https://example.com/login", source.getLoginJs())

        source.mainJs = "var config = {}; function login() {}"

        assertTrue(source.isJsSource())
        assertEquals(source.mainJs, source.getLoginJs())
        assertFalse(source.equal(source.copy(mainJs = "changed")))
    }
}
