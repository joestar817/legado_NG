package io.legado.app.ui.book.read.page.provider

/**
 * An immutable mapping for manual highlights in one published native layout.
 * Positions are UTF-16 boundaries, and ranges are half-open. Reading progress and TTS keep
 * using the layout's existing positions; this map only translates highlight endpoints.
 */
class NativeHighlightPositionMap private constructor(
    val canonicalText: String,
    val displayText: String,
    private val runs: List<Run>,
) {
    data class Range(val start: Int, val endExclusive: Int)

    private data class Run(
        val displayStart: Int,
        val displayEnd: Int,
        val canonicalStart: Int,
        val canonicalEnd: Int,
    ) {
        val isLinear: Boolean
            get() = displayEnd - displayStart == canonicalEnd - canonicalStart

        fun toCanonical(position: Int, end: Boolean): Int = when {
            position == displayStart -> canonicalStart
            position == displayEnd -> canonicalEnd
            isLinear -> canonicalStart + position - displayStart
            end -> canonicalEnd
            else -> canonicalStart
        }

        fun toDisplay(position: Int, end: Boolean): Int = when {
            position == canonicalStart -> displayStart
            position == canonicalEnd -> displayEnd
            isLinear -> displayStart + position - canonicalStart
            end -> displayEnd
            else -> displayStart
        }
    }

    /** At a shared display boundary, starts use the right run and ends use the left run. */
    fun toCanonical(displayPos: Int, end: Boolean = false): Int? {
        if (runs.isEmpty() || displayPos < 0 || displayPos > displayText.length) return null
        var low = 0
        var high = runs.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (runs[middle].displayStart <= displayPos) low = middle + 1
            else high = middle
        }
        var index = low - 1
        if (end && index > 0 && runs[index].displayStart == displayPos) index--
        return runs[index].toCanonical(displayPos, end)
    }

    /** Maps a visible canonical boundary, preferring the side owned by the requested endpoint. */
    fun toDisplayPosition(canonicalPos: Int, end: Boolean = false): Int? {
        if (canonicalPos < 0 || canonicalPos > canonicalText.length) return null
        var preferred: Int? = null
        var boundary: Int? = null
        for (run in runs) {
            if (run.canonicalStart == run.canonicalEnd ||
                canonicalPos < run.canonicalStart || canonicalPos > run.canonicalEnd
            ) continue
            val position = run.toDisplay(canonicalPos, end)
            val ownsPosition = if (end) canonicalPos > run.canonicalStart else canonicalPos < run.canonicalEnd
            if (ownsPosition) {
                preferred = preferred?.let { if (end) maxOf(it, position) else minOf(it, position) } ?: position
            } else if ((end && canonicalPos == 0) || (!end && canonicalPos == canonicalText.length)) {
                boundary = boundary?.let { if (end) maxOf(it, position) else minOf(it, position) } ?: position
            }
        }
        return preferred ?: boundary
    }

    /**
     * Finds the visible part of a canonical range in this snapshot. Hidden content returns null;
     * generated indentation or separators never extend its outer boundaries. Canonical runs may
     * be out of order, so all positive-length intersections contribute to the result.
     */
    fun toDisplayRange(canonicalStart: Int, canonicalEnd: Int): Range? {
        if (canonicalStart < 0 || canonicalEnd <= canonicalStart) return null
        val end = canonicalEnd.coerceAtMost(canonicalText.length)
        if (canonicalStart >= end) return null
        var displayStart = Int.MAX_VALUE
        var displayEnd = -1
        for (run in runs) {
            val from = maxOf(canonicalStart, run.canonicalStart)
            val to = minOf(end, run.canonicalEnd)
            if (from >= to) continue
            displayStart = minOf(displayStart, run.toDisplay(from, end = false))
            displayEnd = maxOf(displayEnd, run.toDisplay(to, end = true))
        }
        return if (displayStart < displayEnd) Range(displayStart, displayEnd) else null
    }

    class Builder(val canonicalText: String) {
        private val display = StringBuilder()
        private val runs = ArrayList<Run>()
        private var cached: NativeHighlightPositionMap? = null

        /**
         * Appends an actual layout fragment. Equal lengths map linearly; a zero-length canonical
         * range anchors generated characters. A non-linear replacement owns its entire canonical
         * range. Hidden fragments need no append, and empty display fragments have no effect.
         */
        fun append(text: String, canonicalStart: Int, canonicalEnd: Int): Builder {
            require(canonicalStart >= 0 && canonicalEnd >= canonicalStart && canonicalEnd <= canonicalText.length) {
                "Highlight run is outside the canonical text"
            }
            if (text.isEmpty()) return this
            val start = display.length
            display.append(text)
            val run = Run(start, display.length, canonicalStart, canonicalEnd)
            val previous = runs.lastOrNull()
            if (previous != null && previous.canonicalEnd == canonicalStart &&
                ((previous.isLinear && run.isLinear) ||
                    (previous.canonicalStart == canonicalStart && canonicalStart == canonicalEnd))
            ) {
                runs[runs.lastIndex] = previous.copy(displayEnd = run.displayEnd, canonicalEnd = canonicalEnd)
            } else {
                runs.add(run)
            }
            cached = null
            return this
        }

        fun snapshot(): NativeHighlightPositionMap = cached ?: NativeHighlightPositionMap(
            canonicalText,
            display.toString(),
            runs.toList(),
        ).also { cached = it }
    }
}
