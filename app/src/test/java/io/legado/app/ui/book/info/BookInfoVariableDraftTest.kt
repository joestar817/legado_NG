package io.legado.app.ui.book.info

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookInfoVariableDraftTest {

    private fun initial(book: String = "old book", source: String = "old source") = BookInfoVariableDraft(
        bookUrl = "book-url",
        sourceKey = "source-key",
        initialBookValue = book,
        initialSourceValue = source,
        bookComment = "book help",
        sourceComment = "source help",
    )

    @Test
    fun unchangedAndEmptyDefaultsDoNotWriteAnything() {
        listOf(initial(), initial("", "")).forEach { draft ->
            assertTrue(
                commitBookInfoVariableDraft(
                    draft, { true },
                    write = { _, _ -> error("Unchanged values must not be written") },
                    onCommitted = { error("No baseline change expected") },
                ),
            )
        }
    }

    @Test
    fun completingBothEditsKeepsTargetsDistinctEvenWhenTheirKeysMatch() {
        val draft = initial().copy(sourceKey = "book-url")
            .edit(BookInfoVariableKind.Book, "new book")
            .edit(BookInfoVariableKind.Source, "new source")
        val writes = mutableListOf<Pair<BookInfoVariableKind, String>>()
        var saved = draft
        assertTrue(commitBookInfoVariableDraft(draft, { true }, { kind, value -> writes += kind to value }) { saved = it })
        assertEquals(
            listOf(BookInfoVariableKind.Book to "new book", BookInfoVariableKind.Source to "new source"),
            writes,
        )
        assertEquals("new book", saved.initialBookValue)
        assertEquals("new source", saved.initialSourceValue)
    }

    @Test
    fun explicitlyClearingBookVariableDoesNotTouchSource() {
        val draft = initial().edit(BookInfoVariableKind.Book, "")
        val writes = mutableListOf<Pair<BookInfoVariableKind, String>>()
        assertTrue(commitBookInfoVariableDraft(draft, { true }, { kind, value -> writes += kind to value }) {})
        assertEquals(listOf(BookInfoVariableKind.Book to ""), writes)
    }

    @Test
    fun sourceOnlyEditDoesNotSaveUnchangedBook() {
        val draft = initial().edit(BookInfoVariableKind.Source, "changed")
        val writes = mutableListOf<Pair<BookInfoVariableKind, String>>()
        assertTrue(commitBookInfoVariableDraft(draft, { true }, { kind, value -> writes += kind to value }) {})
        assertEquals(listOf(BookInfoVariableKind.Source to "changed"), writes)
    }

    @Test
    fun changingTextBackToOriginalDoesNotWriteOnCompletion() {
        val draft = initial().edit(BookInfoVariableKind.Book, "temporary")
            .edit(BookInfoVariableKind.Book, "old book")
        assertTrue(commitBookInfoVariableDraft(draft, { true }, { _, _ -> error("Reverted change") }) {})
    }

    @Test
    fun configurationRestorePreservesBothDraftsAndTheirOriginalBaselines() {
        val edited = initial().edit(BookInfoVariableKind.Book, "书籍草稿\n第二行")
            .edit(BookInfoVariableKind.Source, "书源草稿")
        val restored = BookInfoVariableDraft.restore(edited.savedState())!!
        assertEquals(edited, restored)
        val writes = mutableListOf<Pair<BookInfoVariableKind, String>>()
        assertTrue(commitBookInfoVariableDraft(restored, { true }, { kind, value -> writes += kind to value }) {})
        assertEquals(
            listOf(BookInfoVariableKind.Book to "书籍草稿\n第二行", BookInfoVariableKind.Source to "书源草稿"),
            writes,
        )
    }

    @Test
    fun anotherBookOrSourceCannotReceiveTheDraft() {
        val draft = initial().edit(BookInfoVariableKind.Book, "changed")
        listOf("another-book" to "source-key", "book-url" to "another-source").forEach { (book, source) ->
            assertFalse(
                commitBookInfoVariableDraft(
                    draft,
                    { draft.matchesContext(book, source) },
                    { _, _ -> error("Stale draft must not write") },
                    {},
                ),
            )
        }
    }

    @Test
    fun contextChangeDuringCommitStopsRemainingWrites() {
        val draft = initial().edit(BookInfoVariableKind.Book, "new book")
            .edit(BookInfoVariableKind.Source, "new source")
        var current = true
        var saved = draft
        val writes = mutableListOf<BookInfoVariableKind>()
        assertFalse(
            commitBookInfoVariableDraft(
                draft, { current },
                { kind, _ ->
                    writes += kind
                    current = false
                },
                { saved = it },
            ),
        )
        assertEquals(listOf(BookInfoVariableKind.Book), writes)
        assertEquals(saved.bookValue, saved.initialBookValue)
        assertEquals("old source", saved.initialSourceValue)
    }

    @Test
    fun retryAfterSourceFailureDoesNotRepeatSuccessfulBookWrite() {
        var draft = initial().edit(BookInfoVariableKind.Book, "new book")
            .edit(BookInfoVariableKind.Source, "new source")
        val writes = mutableListOf<BookInfoVariableKind>()
        val failure = runCatching {
            commitBookInfoVariableDraft(
                draft, { true },
                { kind, _ ->
                    if (kind == BookInfoVariableKind.Source) error("storage failure")
                    writes += kind
                },
                { draft = it },
            )
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("new book", draft.initialBookValue)
        assertEquals("old source", draft.initialSourceValue)
        assertTrue(commitBookInfoVariableDraft(draft, { true }, { kind, _ -> writes += kind }) { draft = it })
        assertEquals(listOf(BookInfoVariableKind.Book, BookInfoVariableKind.Source), writes)
    }
}
