package io.legado.app.help.config

import org.junit.Assert.*
import org.junit.Test

class ParagraphCommentBubbleLibraryTest {
    private val a = ParagraphCommentBubble("a", "a.svg", "<svg/>")
    private val b = ParagraphCommentBubble("b", "b.svg", "<svg/>")
    private val c = ParagraphCommentBubble("c", "c.svg", "<svg/>")
    private val library = ParagraphCommentBubbleLibrary(listOf(a, b, c), "b", true)

    @Test fun fileAndNetworkImportsReadUtf8AndUtf16XmlByteOrderMarks() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 54 39"><text>段评{n}</text></svg>"""
        for (encoding in listOf(Charsets.UTF_8, Charsets.UTF_16LE, Charsets.UTF_16BE)) {
            val bytes = ("\uFEFF" + svg).toByteArray(encoding)
            assertEquals(encoding.name(), svg, ParagraphCommentBubbleImport.read(bytes.inputStream()))
        }
        assertEquals(svg, ParagraphCommentBubbleImport.read(svg.byteInputStream()))
    }

    @Test fun deletingAnUnselectedBubbleKeepsSelection() {
        val result = library.remove("a")
        assertEquals("b", result.selectedId)
        assertTrue(result.enabled)
        assertEquals(listOf(b, c), result.bubbles)
    }

    @Test fun deletingTheSelectionChoosesTheNextOrPreviousNeighbour() {
        assertEquals("c", library.remove("b").selectedId)
        assertEquals("b", library.copy(selectedId = "c").remove("c").selectedId)
    }

    @Test fun deletingTheLastBubbleDisablesReplacement() {
        val result = ParagraphCommentBubbleLibrary(listOf(a), "a", true).remove("a")
        assertTrue(result.bubbles.isEmpty())
        assertNull(result.selectedId)
        assertFalse(result.enabled)
    }

    @Test fun editingDoesNotChangeIdentityOrderOrTheActiveBubble() {
        val edited = a.copy(svg = "<svg><circle/></svg>")
        val result = library.put(edited, select = false)
        assertEquals(listOf("a", "b", "c"), result.bubbles.map { it.id })
        assertEquals(edited, result.bubbles.first())
        assertEquals("b", result.selectedId)
    }

    @Test fun importingSelectsTheNewBubbleWithoutChangingTheSwitch() {
        val added = ParagraphCommentBubble("new", "a.svg", "<svg/>")
        for (enabled in listOf(false, true)) {
            val result = library.copy(enabled = enabled).put(added, select = true)
            assertEquals(added, result.bubbles.last())
            assertEquals("new", result.selectedId)
            assertEquals(enabled, result.enabled)
        }
        val empty = ParagraphCommentBubbleLibrary(emptyList(), null)
        assertFalse(empty.put(added, select = true).enabled)
    }

    @Test fun managingWhileOffKeepsTheSwitchOffAndTheChanges() {
        val edited = c.copy(svg = "<svg><circle/></svg>")
        val result = library.copy(enabled = false).select("c").put(edited, select = false).remove("b")
        assertEquals(listOf(a, edited), result.bubbles)
        assertEquals("c", result.selectedId)
        assertFalse(result.enabled)
    }

    @Test fun restoringReinstatesOriginalBuiltinsAndRemovesCustomBubblesById() {
        val defaults = listOf(a, b, c)
        val edited = b.copy(svg = "<svg><circle/></svg>")
        val sameName = a.copy(id = "imported")
        val appearance = ParagraphCommentIconConfig(size = 135, followTextColor = true, dayColor = 1, nightColor = 2)
        for (enabled in listOf(false, true)) {
            val before = ParagraphCommentBubbleLibrary(listOf(edited, sameName), sameName.id, enabled, appearance)
            val result = before.restore(defaults)
            assertEquals(defaults, result.bubbles)
            assertEquals(a.id, result.selectedId)
            assertEquals(enabled, result.enabled)
            assertEquals(appearance, result.appearance)
            assertEquals(result, result.restore(defaults))
            assertEquals(b.id, before.select(b.id).restore(defaults).selectedId)
        }
    }

    @Test fun restoringAnEmptyLibraryKeepsReplacementOff() {
        val result = ParagraphCommentBubbleLibrary(emptyList(), null).restore(listOf(a, b))
        assertEquals(listOf(a, b), result.bubbles)
        assertEquals(a.id, result.selectedId)
        assertFalse(result.enabled)
    }

    @Test fun refreshingTheReaderDependsOnTheVisibleBubble() {
        val off = library.copy(enabled = false)
        val appearance = library.appearance.copy(size = 120)
        assertFalse(off.select("c").copy(appearance = appearance).changesReadingFrom(off))
        assertFalse(off.remove("b").changesReadingFrom(off))
        assertFalse(library.remove("a").changesReadingFrom(library))
        assertFalse(library.select("c").changesReadingFrom(library)) // Same SVG under another name.
        assertTrue(off.changesReadingFrom(library))
        assertTrue(library.changesReadingFrom(off))
        assertTrue(library.copy(appearance = appearance).changesReadingFrom(library))
        assertTrue(library.put(b.copy(svg = "<svg><circle/></svg>"), select = false).changesReadingFrom(library))
    }
}
