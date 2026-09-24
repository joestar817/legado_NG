package io.legado.app.ui.book.read.page.provider

import io.legado.app.help.config.ReadHighlightRule

/** The existing matcher shared by native layout and EPUB style-only updates. */
internal class ReadHighlightMatcher(rules: List<ReadHighlightRule>) {
    private val compiledHighlightRules = rules.mapNotNull { rule ->
        runCatching { rule to Regex(rule.pattern) }.getOrNull()
    }
    fun match(text: String, isTitle: Boolean): Array<ReadCharStyle?>? {
        if (text.isEmpty() || compiledHighlightRules.isEmpty()) return null
        var styles: Array<ReadCharStyle?>? = null
        compiledHighlightRules.forEach { (rule, regex) ->
            if (!rule.appliesToTitle(isTitle)) return@forEach
            val style = rule.toReadCharStyle()
            regex.findAll(text).forEach { match ->
                val active = styles ?: arrayOfNulls<ReadCharStyle>(text.length).also { styles = it }
                match.range.forEach { index ->
                    if (index in active.indices) active[index] = style
                }
            }
        }
        return styles
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
internal data class ReadHighlightInput(val start: Int, val text: String, val isTitle: Boolean)
