package io.legado.app.utils

import java.text.BreakIterator
import java.util.Locale

/** First displayed character for an initial icon, without splitting UTF-16 or emoji sequences. */
internal fun String.firstDisplayGrapheme(): String {
    if (isEmpty()) return ""
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(this@firstDisplayGrapheme) }
    var end = iterator.next().takeIf { it > 0 } ?: offsetByCodePoints(0, 1)

    // Older character iterators may split regional-indicator pairs and newer emoji sequences.
    val first = codePointAt(0)
    if (first in 0x1F1E6..0x1F1FF && end == Character.charCount(first) &&
        end < length && codePointAt(end) in 0x1F1E6..0x1F1FF
    ) {
        end = offsetByCodePoints(end, 1)
    }
    while (end < length) {
        val next = codePointAt(end)
        val type = Character.getType(next)
        when {
            type == Character.NON_SPACING_MARK.toInt() ||
                type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.ENCLOSING_MARK.toInt() ||
                next in 0x1F3FB..0x1F3FF || next in 0xE0020..0xE007F ->
                end = offsetByCodePoints(end, 1)
            next == 0x200D && end + 1 < length -> end = offsetByCodePoints(end + 1, 1)
            codePointBefore(end) == 0x200D -> end = offsetByCodePoints(end, 1)
            else -> break
        }
    }
    return substring(0, end)
}
