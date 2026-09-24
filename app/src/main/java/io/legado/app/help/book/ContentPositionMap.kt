package io.legado.app.help.book

/** Edits from an actual text operation. UTF-16 ranges are half-open, as in String/DOM Range. */
data class ContentEdit(val start: Int, val end: Int, val replacement: String, val nested: ContentPositionMap? = null)

/**
 * Transient provenance attached to one prepared chapter. It executes no rules and stores no book
 * progress. A replacement owns its entire input range; invented characters are never assigned a
 * guessed proportional position in the source.
 */
class ContentPositionMap(val source: String) {
    enum class Affinity { BEFORE, AFTER }
    data class Range(val start: Int, val end: Int)
    data class Slice(val start: Int, val end: Int, val prefix: String = "", val suffix: String = "")
    data class Step(val inputLength: Int, val outputLength: Int, val edits: List<ContentEdit>, val display: Boolean) {
        val outputStarts = IntArray(edits.size)
        val outputEnds = IntArray(edits.size)
        init {
            var delta = 0
            edits.forEachIndexed { index, edit ->
                outputStarts[index] = edit.start + delta
                outputEnds[index] = outputStarts[index] + edit.replacement.length
                delta = outputEnds[index] - edit.end
            }
        }
    }

    private val changes = ArrayList<Step>()
    val steps: List<Step> get() = changes
    var text: String = source
        private set
    var removedTitle: Range? = null
        internal set

    /** Share immutable completed steps; the returned recorder has its own mutable list. */
    fun followedBy(next: ContentPositionMap): ContentPositionMap {
        require(text == next.source) { "Content provenance chain has a gap" }
        return ContentPositionMap(source).also { result ->
            result.changes.addAll(changes)
            result.changes.addAll(next.changes)
            result.text = next.text
            result.removedTitle = removedTitle ?: next.removedTitle?.let { sourceRange(it.start, it.end) }
        }
    }

    fun copy(): ContentPositionMap = followedBy(ContentPositionMap(text))

    fun record(input: String, output: String, edits: List<ContentEdit>, display: Boolean = true) {
        require(input == text) { "Content provenance belongs to another input" }
        var cursor = 0
        val expected = StringBuilder()
        edits.forEach { edit ->
            require(edit.start >= cursor && edit.end in edit.start..input.length)
            edit.nested?.let { require(it.source == input.substring(edit.start, edit.end) && it.text == edit.replacement) }
            expected.append(input, cursor, edit.start).append(edit.replacement)
            cursor = edit.end
        }
        expected.append(input, cursor, input.length)
        require(expected.toString() == output) { "Content provenance does not describe the actual result" }
        if (input != output) changes.add(Step(input.length, output.length,
            edits.filter { input.substring(it.start, it.end) != it.replacement }, display))
        text = output
    }

    fun sourceRange(start: Int, end: Int): Range {
        require(start in 0..end && end <= text.length)
        var left = start
        var right = end
        changes.asReversed().forEach {
            left = map(it, left, Affinity.BEFORE, reverse = true)
            right = map(it, right, Affinity.AFTER, reverse = true)
        }
        return Range(left, right)
    }

    fun sourcePosition(position: Int, affinity: Affinity): Int {
        require(position in 0..text.length)
        return changes.asReversed().fold(position) { offset, step -> map(step, offset, affinity, reverse = true) }
    }

    fun outputPosition(position: Int, affinity: Affinity = Affinity.BEFORE): Int {
        require(position in 0..source.length)
        return changes.fold(position) { offset, step -> map(step, offset, affinity, reverse = false) }
    }

    private fun map(step: Step, offset: Int, affinity: Affinity, reverse: Boolean): Int {
        fun start(index: Int) = if (reverse) step.outputStarts[index] else step.edits[index].start
        fun end(index: Int) = if (reverse) step.outputEnds[index] else step.edits[index].end
        fun targetStart(index: Int) = if (reverse) step.edits[index].start else step.outputStarts[index]
        fun targetEnd(index: Int) = if (reverse) step.edits[index].end else step.outputEnds[index]
        var low = 0
        var high = step.edits.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (if (affinity == Affinity.BEFORE) end(middle) < offset else start(middle) <= offset) low = middle + 1
            else high = middle
        }
        val candidate = if (affinity == Affinity.BEFORE) low else low - 1
        if (candidate in step.edits.indices && offset in start(candidate)..end(candidate)) {
            step.edits[candidate].nested?.let {
                val relative = offset - start(candidate)
                return targetStart(candidate) + if (reverse) it.sourcePosition(relative, affinity) else it.outputPosition(relative, affinity)
            }
            if (start(candidate) == end(candidate)) return if (affinity == Affinity.BEFORE) targetStart(candidate) else targetEnd(candidate)
            if (offset == start(candidate)) return targetStart(candidate)
            if (offset == end(candidate)) return targetEnd(candidate)
            return if (affinity == Affinity.BEFORE) targetStart(candidate) else targetEnd(candidate)
        }
        val previous = if (affinity == Affinity.BEFORE) low - 1 else candidate
        return if (previous >= 0) offset + targetEnd(previous) - end(previous) else offset
    }

    fun literal(input: String, old: String, replacement: String, display: Boolean = true): String {
        val edits = ArrayList<ContentEdit>()
        var cursor = 0
        if (old.isEmpty()) {
            for (index in 0..input.length) edits.add(ContentEdit(index, index, replacement))
        } else while (cursor <= input.length) {
            val index = input.indexOf(old, cursor)
            if (index < 0) break
            edits.add(ContentEdit(index, index + old.length, replacement))
            cursor = index + old.length
        }
        val output = input.replace(old, replacement)
        record(input, output, edits, display)
        return output
    }

    fun regex(input: String, regex: Regex, display: Boolean = true, replace: (MatchResult) -> String): String {
        val edits = ArrayList<ContentEdit>()
        val output = regex.replace(input) { match ->
            replace(match).also { edits.add(ContentEdit(match.range.first, match.range.last + 1, it)) }
        }
        record(input, output, edits, display)
        return output
    }

    fun regex(input: String, regex: Regex, replacement: String, firstOnly: Boolean = false): String {
        val edits = ArrayList<ContentEdit>()
        val matcher = regex.toPattern().matcher(input)
        val result = StringBuffer()
        var previous = 0
        while (matcher.find()) {
            val start = result.length + matcher.start() - previous
            matcher.appendReplacement(result, replacement)
            edits.add(ContentEdit(matcher.start(), matcher.end(), result.substring(start)))
            previous = matcher.end()
            if (firstOnly) break
        }
        matcher.appendTail(result)
        return result.toString().also { record(input, it, edits) }
    }

    /** Only for an operation whose mutations are setCharAt and append, never a text alignment. */
    fun recordCharacterWrites(input: String, output: String) {
        require(output.length >= input.length)
        val edits = ArrayList<ContentEdit>()
        input.indices.forEach { index ->
            if (input[index] != output[index]) edits.add(ContentEdit(index, index + 1, output[index].toString()))
        }
        if (output.length > input.length) edits.add(ContentEdit(input.length, input.length, output.substring(input.length)))
        record(input, output, edits)
    }

    fun slice(input: String, start: Int, end: Int = input.length, display: Boolean = true): String {
        val edits = buildList {
            if (start > 0) add(ContentEdit(0, start, ""))
            if (end < input.length) add(ContentEdit(end, input.length, ""))
        }
        return input.substring(start, end).also { record(input, it, edits, display) }
    }

    fun retain(input: String, slices: List<Slice>, output: String, display: Boolean = false) {
        val edits = ArrayList<ContentEdit>()
        var cursor = 0
        slices.forEach { slice ->
            require(slice.start >= cursor && slice.end in slice.start..input.length)
            if (cursor < slice.start) edits.add(ContentEdit(cursor, slice.start, ""))
            if (slice.prefix.isNotEmpty()) edits.add(ContentEdit(slice.start, slice.start, slice.prefix))
            if (slice.suffix.isNotEmpty()) edits.add(ContentEdit(slice.end, slice.end, slice.suffix))
            cursor = slice.end
        }
        if (cursor < input.length) edits.add(ContentEdit(cursor, input.length, ""))
        record(input, output, edits, display)
    }

    fun trimLines(input: String): String {
        val slices = ArrayList<Slice>()
        val output = input.lines().joinToString("\n") { it.trim() }
        var start = 0
        val ends = Regex("\r\n|\n|\r").findAll(input).map { it.range.first to it.range.last + 1 }.toList()
        (ends + (input.length to input.length)).forEachIndexed { index, (end, next) ->
            var left = start
            var right = end
            while (left < right && input[left].isWhitespace()) left++
            while (right > left && input[right - 1].isWhitespace()) right--
            slices.add(Slice(left, right, suffix = if (index < ends.size) "\n" else ""))
            start = next
        }
        retain(input, slices, output)
        return output
    }
}
