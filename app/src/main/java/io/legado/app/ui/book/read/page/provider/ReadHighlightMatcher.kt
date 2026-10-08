package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadHighlightRule
import java.util.WeakHashMap

/** Immutable title or body text before wrapping. Shared by its paragraph slices, never persisted. */
internal class ReadHighlightContext(val text: String)

/** The existing matcher shared by native layout and EPUB style-only updates. */
internal class ReadHighlightMatcher(rules: List<ReadHighlightRule>) {
    private data class CompiledRule(
        val rule: ReadHighlightRule,
        val regex: Regex,
        val style: ReadCharStyle,
    )

    private val compiledHighlightRules = rules.mapNotNull { rule ->
        runCatching { CompiledRule(rule, Regex(rule.pattern), rule.toReadCharStyle()) }.getOrNull()
    }

    val hasCrossParagraphRules: Boolean = compiledHighlightRules.any { it.rule.matchAcrossParagraphs }

    // A cache belongs to these compiled rules. Values hold only offsets, not the context or match input.
    private val contextMatches = WeakHashMap<ReadHighlightContext, MutableMap<Int, List<IntRange>>>()

    fun match(
        text: String,
        isTitle: Boolean,
        context: ReadHighlightContext? = null,
        contextOffset: Int = 0,
    ): Array<ReadCharStyle?>? {
        if (text.isEmpty() || compiledHighlightRules.isEmpty()) return null
        require(context == null || contextOffset in 0..(context.text.length - text.length)) {
            "Highlight input must be a slice of its context"
        }
        var styles: Array<ReadCharStyle?>? = null
        compiledHighlightRules.forEachIndexed { index, compiled ->
            if (!compiled.rule.appliesToTitle(isTitle)) return@forEachIndexed
            val offset: Int
            val ranges: List<IntRange>
            if (compiled.rule.matchAcrossParagraphs && context != null) {
                offset = contextOffset
                ranges = matchesInContext(context, index, compiled.regex)
            } else {
                offset = 0
                ranges = findRanges(text, compiled.regex, compiled.rule.matchAcrossParagraphs)
            }
            styles = applyRanges(styles, text.length, offset, ranges, compiled.style)
        }
        return styles
    }

    /** Preview uses the same paragraph rules and exact original offsets as the reader. */
    fun matchSample(text: String, isTitle: Boolean): Array<ReadCharStyle?>? {
        if (text.isEmpty() || compiledHighlightRules.isEmpty()) return null
        val paragraphs = paragraphRanges(text)
        val context = ReadHighlightContext(text)
        var styles: Array<ReadCharStyle?>? = null
        compiledHighlightRules.forEachIndexed { index, compiled ->
            if (!compiled.rule.appliesToTitle(isTitle)) return@forEachIndexed
            val ranges = if (compiled.rule.matchAcrossParagraphs) {
                matchesInContext(context, index, compiled.regex)
            } else {
                paragraphs.flatMap { paragraph ->
                    compiled.regex.findAll(text.substring(paragraph)).mapNotNull { match ->
                        match.range.takeUnless(IntRange::isEmpty)?.let {
                            (paragraph.first + it.first)..(paragraph.first + it.last)
                        }
                    }.toList()
                }
            }
            styles = applyRanges(styles, text.length, 0, ranges, compiled.style)
        }
        return styles
    }

    private fun matchesInContext(
        context: ReadHighlightContext,
        ruleIndex: Int,
        regex: Regex,
    ): List<IntRange> = synchronized(contextMatches) {
        contextMatches.getOrPut(context) { mutableMapOf() }.getOrPut(ruleIndex) {
            findRanges(context.text, regex, splitAtMedia = true)
        }
    }

    private fun findRanges(text: String, regex: Regex, splitAtMedia: Boolean): List<IntRange> {
        if (!splitAtMedia || '\uFFFC' !in text) {
            return regex.findAll(text).mapNotNull { it.range.takeUnless(IntRange::isEmpty) }.toList()
        }
        val ranges = ArrayList<IntRange>()
        var start = 0
        while (start < text.length) {
            val end = text.indexOf('\uFFFC', start).takeIf { it >= 0 } ?: text.length
            if (start < end) {
                regex.findAll(text.substring(start, end)).forEach { match ->
                    if (!match.range.isEmpty()) {
                        ranges.add((start + match.range.first)..(start + match.range.last))
                    }
                }
            }
            start = end + 1
        }
        return ranges
    }

    private fun applyRanges(
        current: Array<ReadCharStyle?>?,
        length: Int,
        contextOffset: Int,
        ranges: List<IntRange>,
        style: ReadCharStyle,
    ): Array<ReadCharStyle?>? {
        var styles = current
        // Matches are ordered and non-overlapping; skip earlier chapter matches without a linear scan.
        var low = 0
        var high = ranges.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (ranges[mid].last < contextOffset) low = mid + 1 else high = mid
        }
        val end = contextOffset + length
        for (index in low until ranges.size) {
            val range = ranges[index]
            if (range.first >= end) break
            val active = styles ?: arrayOfNulls<ReadCharStyle>(length).also { styles = it }
            active.fill(
                style,
                fromIndex = maxOf(range.first, contextOffset) - contextOffset,
                toIndex = minOf(range.last + 1, end) - contextOffset,
            )
        }
        return styles
    }

    private fun paragraphRanges(text: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var start = 0
        text.forEachIndexed { index, char ->
            if (char == '\n' || char == '\r') {
                if (start < index) ranges.add(start until index)
                start = index + 1
            }
        }
        if (start < text.length) ranges.add(start until text.length)
        return ranges
    }

    private fun ReadHighlightRule.toReadCharStyle(): ReadCharStyle {
        return ReadCharStyle(
            textColor = textColor,
            bgColor = bgColor,
            underlineMode = underlineMode,
            underlineColor = underlineColor,
            underlineWidth = underlineWidth,
            underlineOffset = underlineOffset,
            underlineSvgPath = underlineSvgPath.orEmpty(),
            bgImage = bgImage.orEmpty(),
            bgImageFit = bgImageFit,
            bgImageScale = bgImageScale,
            fontPath = fontPath.orEmpty(),
            fontWeight = fontWeight,
            isItalic = isItalic,
            npLeft = npLeft,
            npRight = npRight,
            npTop = npTop,
            npBottom = npBottom,
        )
    }

}

/** Exact input and chapter offset captured before native line wrapping. Not persisted. */
internal data class ReadHighlightInput(
    val start: Int,
    val text: String,
    val isTitle: Boolean,
    val context: ReadHighlightContext? = null,
    val contextOffset: Int = 0,
)
