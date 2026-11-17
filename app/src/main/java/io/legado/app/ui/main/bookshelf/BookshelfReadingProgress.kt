package io.legado.app.ui.main.bookshelf

import kotlin.math.roundToInt

/** Chapter-level estimate; the bookshelf does not load chapter text to measure progress. */
internal fun bookshelfReadingProgress(chapterIndex: Int, chapterPos: Int, chapterCount: Int): Float {
    if (chapterCount <= 0 || chapterIndex < 0 || (chapterIndex == 0 && chapterPos <= 0)) {
        return 0f
    }
    return ((chapterIndex.toLong() + 1).toDouble() / chapterCount).toFloat().coerceIn(0f, 1f)
}

internal fun bookshelfReadingProgressPercent(progress: Float): Int =
    if (progress >= 1f) 100 else (progress * 100f).roundToInt().coerceAtMost(99)
