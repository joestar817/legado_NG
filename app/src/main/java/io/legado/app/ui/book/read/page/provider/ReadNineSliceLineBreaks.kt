package io.legado.app.ui.book.read.page.provider

/** 保持完整字形簇，宽度预算包含每行起止边框；单个超宽簇仍保证前进。 */
internal fun nineSliceLineStarts(
    words: List<String>,
    widths: List<Float>,
    maxWidth: Float,
    decorationWidth: (Int, Int) -> Float,
    canBreakAt: (Int) -> Boolean,
): IntArray {
    val offsets = IntArray(words.size + 1)
    val advances = FloatArray(words.size + 1)
    words.indices.forEach { index ->
        offsets[index + 1] = offsets[index] + words[index].length
        advances[index + 1] = advances[index] + widths[index]
    }
    val starts = arrayListOf(0)
    var start = 0
    while (start < words.size) {
        var end = start + 1
        var lastBoundary = -1
        while (end < words.size && advances[end + 1] - advances[start] +
            decorationWidth(offsets[start], offsets[end + 1]) <= maxWidth) {
            if (canBreakAt(offsets[end])) lastBoundary = end
            end++
        }
        if (end < words.size && lastBoundary > start && !canBreakAt(offsets[end])) end = lastBoundary
        starts.add(offsets[end])
        start = end
    }
    return starts.toIntArray()
}
