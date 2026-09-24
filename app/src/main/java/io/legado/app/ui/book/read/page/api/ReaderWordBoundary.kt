package io.legado.app.ui.book.read.page.api

import java.text.BreakIterator
import java.util.Locale

/** Existing word-selection policy; layouts supply the whole paragraph and its hit offset. */
internal fun readerWordBoundary(paragraph: String, offset: Int): IntRange? {
    if (offset !in paragraph.indices) return null
    val boundary = BreakIterator.getWordInstance(Locale.getDefault())
    boundary.setText(paragraph)
    var start = boundary.first()
    var end = boundary.next()
    while (end != BreakIterator.DONE) {
        if (offset in start until end) return start until end
        start = end
        end = boundary.next()
    }
    return null
}
