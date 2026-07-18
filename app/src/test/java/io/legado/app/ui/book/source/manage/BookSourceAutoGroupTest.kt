package io.legado.app.ui.book.source.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BookSourceAutoGroupTest {

    @Test
    fun groupsSourcesThatShareBaseUrlBeforeFragment() {
        val groups = BookSourceAutoGroup.sharedBaseUrlGroups(
            listOf(
                "http://192.168.11.10:8080#lm",
                "http://192.168.11.10:8080#icon"
            )
        )

        assertEquals("http://192.168.11.10:8080", groups["http://192.168.11.10:8080#lm"])
        assertEquals("http://192.168.11.10:8080", groups["http://192.168.11.10:8080#icon"])
    }

    @Test
    fun usesBuiltInBaseUrlForFragmentsPathsAndQueries() {
        val groups = BookSourceAutoGroup.sharedBaseUrlGroups(
            listOf(
                "https://example.com/source",
                "https://example.com/other?channel=backup",
                "https://example.com#icon"
            )
        )

        assertEquals("https://example.com", groups["https://example.com/source"])
        assertEquals("https://example.com", groups["https://example.com/other?channel=backup"])
        assertEquals("https://example.com", groups["https://example.com#icon"])
    }

    @Test
    fun doesNotGroupDifferentHostsPortsOrSchemes() {
        val groups = BookSourceAutoGroup.sharedBaseUrlGroups(
            listOf(
                "https://example.com#one",
                "http://example.com#two",
                "https://example.com:8443#three",
                "https://other.example.com#four"
            )
        )

        assertEquals(emptyMap<String, String>(), groups)
    }

    @Test
    fun duplicateInputAndNonHttpUrlDoNotCreateGroups() {
        val groups = BookSourceAutoGroup.sharedBaseUrlGroups(
            listOf(
                "https://example.com/source#one",
                "https://example.com/source#one",
                "custom-source#one",
                "custom-source#two"
            )
        )

        assertFalse(groups.containsKey("https://example.com/source#one"))
        assertFalse(groups.containsKey("custom-source#one"))
        assertFalse(groups.containsKey("custom-source#two"))
    }
}
