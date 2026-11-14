package io.legado.app.ui.book.read.aloud

import io.legado.app.help.tts.ReadAloudPageText

/** 按阅读排版中的页边界定位；无相邻页时由调用方处理跨章。 */
internal fun ReadAloudPageText.adjacentPagePosition(position: Int, next: Boolean): Int? {
    if (size == 0) return null
    val target = indexAt(position) + if (next) 1 else -1
    return if (target in 0 until size) startAt(target) else null
}
