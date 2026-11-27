package io.legado.app.ui.book.read.config

internal data class AiPurifyDiffSpan(val original: String, val cleaned: String) {
    val changed: Boolean get() = original != cleaned
}

/** Builds the preview only. Rule generation and automatic application use the original result. */
internal fun aiPurifyTextDiff(original: String, cleaned: String): List<AiPurifyDiffSpan> {
    if (original == cleaned) return listOf(AiPurifyDiffSpan(original, cleaned))
    val prefix = original.commonPrefixWith(cleaned)
    val originalTail = original.substring(prefix.length)
    val cleanedTail = cleaned.substring(prefix.length)
    val suffix = originalTail.commonSuffixWith(cleanedTail)
    val old = originalTail.dropLast(suffix.length)
    val new = cleanedTail.dropLast(suffix.length)
    return buildList {
        if (prefix.isNotEmpty()) add(AiPurifyDiffSpan(prefix, prefix))
        addAll(alignAiPurifyChanges(old, new))
        if (suffix.isNotEmpty()) add(AiPurifyDiffSpan(suffix, suffix))
    }
}

private fun alignAiPurifyChanges(original: String, cleaned: String): List<AiPurifyDiffSpan> {
    val widthBound = cleaned.length + 1L
    if (original.isEmpty() || cleaned.isEmpty() || (original.length + 1L) * widthBound > 4_000_000L) {
        return listOf(AiPurifyDiffSpan(original, cleaned))
    }
    val old = original.toPurifyCodePoints()
    val new = cleaned.toPurifyCodePoints()
    val width = new.size + 1
    val cost = IntArray((old.size + 1) * width)
    for (j in 0..new.size) cost[j] = j
    for (i in 1..old.size) {
        val row = i * width
        val previous = row - width
        cost[row] = i
        for (j in 1..new.size) {
            cost[row + j] = minOf(
                cost[previous + j] + 1,
                cost[row + j - 1] + 1,
                cost[previous + j - 1] + if (old[i - 1] == new[j - 1]) 0 else 1,
            )
        }
    }

    val spans = ArrayList<AiPurifyDiffSpan>()
    var i = old.size
    var j = new.size
    var endI = i
    var endJ = j
    var previousChanged: Boolean? = null
    while (i > 0 || j > 0) {
        val current = cost[i * width + j]
        val matched = i > 0 && j > 0 && old[i - 1] == new[j - 1]
        val diagonal = i > 0 && j > 0 && current == cost[(i - 1) * width + j - 1] +
            if (matched) 0 else 1
        val changed = !matched || !diagonal
        if (previousChanged != null && previousChanged != changed) {
            spans.add(AiPurifyDiffSpan(old.toPurifyText(i, endI), new.toPurifyText(j, endJ)))
            endI = i
            endJ = j
        }
        previousChanged = changed
        when {
            diagonal -> {
                i--
                j--
            }
            i > 0 && current == cost[(i - 1) * width + j] + 1 -> i--
            else -> j--
        }
    }
    spans.add(AiPurifyDiffSpan(old.toPurifyText(0, endI), new.toPurifyText(0, endJ)))
    spans.reverse()
    return spans
}

private fun String.toPurifyCodePoints(): IntArray {
    var offset = 0
    return IntArray(codePointCount(0, length)) {
        val codePoint = codePointAt(offset)
        offset += Character.charCount(codePoint)
        codePoint
    }
}

private fun IntArray.toPurifyText(start: Int, end: Int): String = buildString {
    for (index in start until end) appendCodePoint(this@toPurifyText[index])
}
