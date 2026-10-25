package io.legado.app.ui.book.read.page

internal fun readHeaderImageHeight(width: Int, imageWidth: Int, imageHeight: Int, maxHeight: Int): Int {
    if (width <= 0 || imageWidth <= 0 || imageHeight <= 0 || maxHeight <= 0) return 0
    val naturalHeight = (width.toLong() * imageHeight + imageWidth / 2) / imageWidth
    return naturalHeight.coerceIn(1L, maxHeight.toLong()).toInt()
}

/** Both decorations share the height left after chrome and the minimum readable body. */
internal fun readDecorationImageHeights(header: Int, footer: Int, available: Int): Pair<Int, Int> {
    val headerHeight = header.coerceAtLeast(0)
    val footerHeight = footer.coerceAtLeast(0)
    val total = headerHeight.toLong() + footerHeight
    val budget = available.coerceAtLeast(0)
    if (total <= budget) return headerHeight to footerHeight
    if (total == 0L || budget == 0) return 0 to 0
    val limitedHeader = (headerHeight.toLong() * budget / total).toInt()
    return limitedHeader to budget - limitedHeader
}
