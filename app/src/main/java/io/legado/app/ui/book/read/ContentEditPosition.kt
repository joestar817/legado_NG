package io.legado.app.ui.book.read

/** 编辑正文与排版共用 ContentProcessor 的非空段落，换行才是段落边界。 */
internal fun contentEditParagraphOffset(content: String, paragraphIndex: Int): Int {
    var offset = 0
    repeat(paragraphIndex.coerceAtLeast(0)) {
        val newline = content.indexOf('\n', offset)
        if (newline == -1) return content.length
        offset = newline + 1
    }
    return offset
}

/** 在起始段内按字符位置校准选区，避免跳到全文中第一次出现的同名文字。 */
internal fun contentEditSelectionOffset(
    content: String,
    paragraphIndex: Int,
    paragraphOffset: Int,
    selectedTextPrefix: String,
): Int {
    val start = contentEditParagraphOffset(content, paragraphIndex)
    val end = content.indexOf('\n', start).takeIf { it >= 0 } ?: content.length
    val expected = start + paragraphOffset.coerceIn(0, end - start)
    val prefix = selectedTextPrefix.substringBefore('\n').take(128)
    if (prefix.isEmpty()) return expected
    var match = content.indexOf(prefix, start)
    var nearest = -1
    while (match >= start && match + prefix.length <= end) {
        if (nearest == -1 || kotlin.math.abs(match - expected) < kotlin.math.abs(nearest - expected)) {
            nearest = match
        }
        if (match == expected) break
        match = content.indexOf(prefix, match + 1)
    }
    return nearest.takeIf { it >= 0 } ?: expected
}
