package io.legado.app.ui.book.read.page.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LatinRunScaleTest {
    @Test
    fun identityScaleReturnsTheSameArray() {
        val styles = arrayOfNulls<ReadCharStyle?>(2)
        assertSame(styles, LatinRunScale.apply("Ab", styles, 1f))
        assertNull(LatinRunScale.apply("Ab", null, 1f))
    }

    @Test
    fun latinRunsScaleAndCjkRunsDoNot() {
        val result = LatinRunScale.apply("你A", null, 1.1f)!!
        assertEquals(1f, result[0]?.latinScale ?: 1f)
        assertEquals(1.1f, result[1]?.latinScale)
    }

    @Test
    fun neutralAfterLatinInheritsTheScale() {
        val result = LatinRunScale.apply("A,B", null, 1.05f)!!
        assertEquals(1.05f, result[0]?.latinScale)
        assertEquals(1.05f, result[1]?.latinScale)
        assertEquals(1.05f, result[2]?.latinScale)
    }

    @Test
    fun highlightStyleKeepsItsFont() {
        val styles = arrayOf<ReadCharStyle?>(ReadCharStyle(fontPath = "/fonts/highlight.ttf"))
        val result = LatinRunScale.apply("A", styles, 1.1f)!!
        assertEquals("/fonts/highlight.ttf", result[0]?.fontPath)
        assertEquals(1.1f, result[0]?.latinScale)
    }
}
