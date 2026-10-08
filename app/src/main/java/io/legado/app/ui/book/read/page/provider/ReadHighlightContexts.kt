package io.legado.app.ui.book.read.page.provider

/** A paragraph's UTF-16 offset in a shared run of plain chapter text. Never persisted. */
internal data class ReadHighlightContextSlice(
    val context: ReadHighlightContext,
    val offset: Int,
)

/** Null paragraphs are media/HTML boundaries; their neighbours must not share a match. */
internal fun prepareReadHighlightContexts(
    paragraphs: List<String?>,
): List<ReadHighlightContextSlice?> {
    val result = MutableList<ReadHighlightContextSlice?>(paragraphs.size) { null }
    var index = 0
    while (index < paragraphs.size) {
        if (paragraphs[index] == null) {
            index++
            continue
        }
        val first = index
        val text = StringBuilder()
        val offsets = ArrayList<Int>()
        while (index < paragraphs.size) {
            val paragraph = paragraphs[index] ?: break
            if (index > first) text.append('\n')
            offsets.add(text.length)
            text.append(paragraph)
            index++
        }
        val context = ReadHighlightContext(text.toString())
        offsets.forEachIndexed { paragraphIndex, offset ->
            result[first + paragraphIndex] = ReadHighlightContextSlice(context, offset)
        }
    }
    return result
}
