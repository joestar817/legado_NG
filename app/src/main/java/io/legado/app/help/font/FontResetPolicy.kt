package io.legado.app.help.font

import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadHighlightRule

internal const val SYSTEM_DEFAULT_FONT = "system"

internal fun ReadHighlightRule.resetDeletedFont(matches: (String?) -> Boolean): ReadHighlightRule =
    if (matches(fontPath)) copy(fontPath = SYSTEM_DEFAULT_FONT) else this

/** A blank title font inherits the body, so deletion needs an explicit system font. */
internal fun ReadBookConfig.Config.resetDeletedFont(matches: (String?) -> Boolean): Boolean {
    var changed = false
    fun switched(path: String): String {
        if (!matches(path)) return path
        changed = true
        return SYSTEM_DEFAULT_FONT
    }
    textFont = switched(textFont)
    titleFont = switched(titleFont)
    headerFont = switched(headerFont)
    footerFont = switched(footerFont)
    highlightRules.replaceAll { rule ->
        rule.resetDeletedFont(matches).also { if (it !== rule) changed = true }
    }
    return changed
}
