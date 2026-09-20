package io.legado.app.ui.rss.source.edit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RssSourceEditorHistoryTest {
    @Test
    fun insertReplacesReversedSelectionAndReturnsCursor() {
        assertEquals(
            RssEditorSnapshot("abURLef", 5, 5),
            RssEditorSnapshot("abcdef", 4, 2).insert("URL"),
        )
        assertEquals(
            RssEditorSnapshot("x", 1, 1),
            RssEditorSnapshot("", 9, -4).insert("x"),
        )
    }

    @Test
    fun undoAndRedoStayWithinTheTargetFieldAndRestoreSelection() {
        val history = RssSourceEditorHistory()
        val before = RssEditorSnapshot("tag.h2@text", 4, 6)
        history.record("ruleTitle", before, "tag.h3@text")
        history.record("ruleLink", RssEditorSnapshot("a", 1, 1), "tag.a@href")
        val current = RssEditorSnapshot("tag.h3@text", 6, 6)
        assertEquals(before, history.undo("ruleTitle", current))
        assertEquals(current, history.redo("ruleTitle", before))
        assertEquals(
            RssEditorSnapshot("a", 1, 1),
            history.undo("ruleLink", RssEditorSnapshot("tag.a@href", 10, 10)),
        )
    }

    @Test
    fun selectionOnlyChangesKeepRedoButNewTextInvalidatesIt() {
        val history = RssSourceEditorHistory()
        val before = RssEditorSnapshot("a", 0, 1)
        history.record("title", before, "b")
        history.undo("title", RssEditorSnapshot("b", 1, 1))
        history.record("title", before, "a")
        assertEquals("b", history.redo("title", before)?.text)
        history.undo("title", RssEditorSnapshot("b", 1, 1))
        history.record("title", before, "c")
        assertNull(history.redo("title", RssEditorSnapshot("c", 1, 1)))
        history.clear()
        assertNull(history.undo("title", before))
    }

    @Test
    fun historyIsBoundedAcrossFieldsAndLongScripts() {
        val history = RssSourceEditorHistory()
        repeat(101) { history.record("field$it", RssEditorSnapshot("x", 1, 1), "y") }
        assertNull(history.undo("field0", RssEditorSnapshot("y", 1, 1)))
        history.clear()
        repeat(3) {
            history.record("script$it", RssEditorSnapshot("x".repeat(1024 * 1024), 0, 0), "y")
        }
        assertNull(history.undo("script0", RssEditorSnapshot("y", 1, 1)))
    }
}
