package io.legado.app.ui.book.read.page.provider

import io.legado.app.data.entities.Bookmark

/** Resolves stored highlights without changing their persisted coordinates or quoted text. */
internal object NativeTextHighlightResolver {

    fun resolve(
        bookmark: Bookmark,
        chapterIndex: Int,
        map: NativeHighlightPositionMap,
    ): NativeHighlightPositionMap.Range? {
        val anchor = findAnchor(bookmark, chapterIndex, map) ?: return null
        return if (anchor.canonical) map.toDisplayRange(anchor.start, anchor.endExclusive)
        else clippedRange(anchor.start, anchor.endExclusive, map.displayText.length)
    }

    /** A note belongs to the real end, never to the clipped end of a partially built chapter. */
    fun resolveEnd(bookmark: Bookmark, chapterIndex: Int, map: NativeHighlightPositionMap): Int? {
        if (chapterIndex != bookmark.endChapterIndex) return null
        val anchor = findAnchor(bookmark, chapterIndex, map) ?: return null
        return if (anchor.canonical) map.toDisplayPosition(anchor.endExclusive, end = true)
        else anchor.endExclusive.takeIf { it > 0 && it <= map.displayText.length }
    }

    private data class Anchor(val start: Int, val endExclusive: Int, val canonical: Boolean)

    private fun findAnchor(
        bookmark: Bookmark,
        chapterIndex: Int,
        map: NativeHighlightPositionMap,
    ): Anchor? {
        if (!bookmark.coversChapter(chapterIndex)) return null
        val isFirst = chapterIndex == bookmark.chapterIndex
        val isLast = chapterIndex == bookmark.endChapterIndex
        val start = if (isFirst) bookmark.chapterPos else 0
        val end = if (isLast) bookmark.endChapterPos else Int.MAX_VALUE
        if (bookmark.bookmarkType == Bookmark.TYPE_TEXT_HIGHLIGHT_CANONICAL) {
            return Anchor(start, end, canonical = true)
        }

        val fallback = Anchor(start, end, canonical = false)
        val original = clippedRange(start, end, map.displayText.length)
        if (!isFirst && !isLast) return fallback

        // Old bookmarks contain display offsets and may have an editable quote. Prefer a
        // verified original range; otherwise recover only an unambiguous textual anchor.
        // Ambiguous or edited quotes keep the old clipped range, never move to a guessed hit.
        val canVerifyOriginal = !isFirst || isLast ||
                map.toCanonical(map.displayText.length, end = true) == map.canonicalText.length
        if (original != null && canVerifyOriginal) {
            for (keepLineBreaks in listOf(true, false)) {
                val quote = normalize(bookmark.bookText, keepLineBreaks).text
                val selected = normalize(
                    map.displayText.substring(original.start, original.endExclusive),
                    keepLineBreaks,
                ).text
                if (matchesQuote(selected, quote, isFirst, isLast)) return fallback
            }
        }
        for (keepLineBreaks in listOf(true, false)) {
            val quote = normalize(bookmark.bookText, keepLineBreaks)
            if (quote.text.isEmpty()) return fallback
            val chapter = normalize(map.canonicalText, keepLineBreaks)
            val matches = findMatches(chapter.text, quote.text, isFirst, isLast)
            if (matches.size > 1) return fallback
            if (matches.size == 1) {
                val match = matches.single()
                val from = if (isFirst) chapter.starts[match.first] else 0
                val to = if (isLast) chapter.ends[match.last] else map.canonicalText.length
                // A valid anchor outside the currently published layout stays invisible;
                // do not draw its obsolete display range on an unrelated earlier page.
                return Anchor(from, to, canonical = true)
            }
        }
        return fallback
    }

    private fun clippedRange(start: Int, end: Int, length: Int): NativeHighlightPositionMap.Range? {
        val from = start.coerceIn(0, length)
        val to = end.coerceIn(0, length)
        return if (from < to) NativeHighlightPositionMap.Range(from, to) else null
    }

    private fun matchesQuote(selected: String, quote: String, first: Boolean, last: Boolean): Boolean {
        if (selected.isEmpty()) return false
        return when {
            first && last -> selected == quote
            first -> quote.startsWith(selected)
            else -> quote.endsWith(selected)
        }
    }

    private fun findMatches(chapter: String, quote: String, first: Boolean, last: Boolean): List<IntRange> {
        if (chapter.isEmpty()) return emptyList()
        if (first && last) {
            val start = chapter.indexOf(quote)
            if (start < 0) return emptyList()
            val next = chapter.indexOf(quote, start + 1)
            return if (next < 0) listOf(start until start + quote.length)
            else listOf(start until start + quote.length, next until next + quote.length)
        }
        return if (first) {
            suffixPrefixLengths(chapter, quote).map { chapter.length - it until chapter.length }
        } else {
            suffixPrefixLengths(quote, chapter).map { 0 until it }
        }
    }

    /** All overlaps of the complete source suffix and pattern prefix, not just one line. */
    private fun suffixPrefixLengths(source: String, pattern: String): List<Int> {
        if (source.isEmpty() || pattern.isEmpty()) return emptyList()
        val prefix = IntArray(pattern.length)
        for (i in 1 until pattern.length) {
            var length = prefix[i - 1]
            while (length > 0 && pattern[i] != pattern[length]) length = prefix[length - 1]
            if (pattern[i] == pattern[length]) length++
            prefix[i] = length
        }
        var length = 0
        for (char in source) {
            while (length > 0 && (length == pattern.length || char != pattern[length])) {
                length = prefix[length - 1]
            }
            if (char == pattern[length]) length++
        }
        val matches = ArrayList<Int>(2)
        while (length > 0 && matches.size < 2) {
            matches.add(length)
            length = prefix[length - 1]
        }
        return matches
    }

    private data class NormalizedText(val text: String, val starts: IntArray, val ends: IntArray)

    /**
     * Preserves UTF-16 boundaries and spaces inside a line ("a b" must not match "ab").
     * Old selections omit image/comment columns and can omit the paragraph break following
     * those columns; the second pass tolerates only this missing-break distinction.
     */
    private fun normalize(text: String, keepLineBreaks: Boolean): NormalizedText {
        val value = StringBuilder(text.length)
        val starts = ArrayList<Int>(text.length)
        val ends = ArrayList<Int>(text.length)
        var lineStart = true
        var index = 0
        while (index < text.length) {
            val start = index
            val char = text[index++]
            when {
                char == '\r' || char == '\n' -> {
                    if (char == '\r' && index < text.length && text[index] == '\n') index++
                    if (keepLineBreaks && value.isNotEmpty()) {
                        value.append('\n')
                        starts.add(start)
                        ends.add(index)
                    }
                    lineStart = true
                }
                char == '\uFFFC' || char == '\uA9C1' || char == '\u88AE' -> Unit
                lineStart && (char == ' ' || char == '\t' || char == '\u3000') -> Unit
                else -> {
                    value.append(char)
                    starts.add(start)
                    ends.add(index)
                    lineStart = false
                }
            }
        }
        while (value.isNotEmpty() && value.last() == '\n') {
            value.setLength(value.length - 1)
            starts.removeAt(starts.lastIndex)
            ends.removeAt(ends.lastIndex)
        }
        return NormalizedText(value.toString(), starts.toIntArray(), ends.toIntArray())
    }
}
