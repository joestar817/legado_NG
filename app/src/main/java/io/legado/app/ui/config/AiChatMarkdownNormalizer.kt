package io.legado.app.ui.config

/**
 * Normalizes model Markdown before it reaches Markwon.
 *
 * CommonMark can reject a strong delimiter next to Chinese punctuation, such as
 * the opening stars in "是**《书名》**" or closing stars before a letter. Use
 * HTML strong tags for those cases. Skill-internal links keep their label only.
 */
internal fun normalizeAiChatMarkdown(content: String): String {
    if (content.isBlank()) return content
    return transformOutsideInlineCode(content, ::normalizeMarkdownProse)
}

private fun normalizeMarkdownProse(content: String): String {
    val withoutInternalLinks = markdownInlineLinkRegex.replace(content) { match ->
        val destination = match.groupValues[2].removeSurrounding("<", ">")
        if (destination.isHttpUrl()) match.value else match.groupValues[1]
    }
    val withBookTitleEmphasis = if ("**《" in withoutInternalLinks) {
        bookTitleOrProtectedInlineRegex.replace(withoutInternalLinks) { match ->
            val title = match.groupValues[1]
            if (title.isEmpty()) match.value else title.asStrongHtml()
        }
    } else {
        withoutInternalLinks
    }
    return ambiguousChineseStrongRegex.replace(withBookTitleEmphasis) { it.groupValues[1].asStrongHtml() }
}

private fun transformOutsideInlineCode(
    content: String,
    transform: (String) -> String
): String {
    val result = StringBuilder(content.length)
    var cursor = 0
    while (cursor < content.length) {
        val opening = content.indexOf('`', cursor)
        if (opening < 0) {
            result.append(transform(content.substring(cursor)))
            break
        }
        result.append(transform(content.substring(cursor, opening)))
        val delimiterLength = content.runLengthAt(opening, '`')
        val delimiter = "`".repeat(delimiterLength)
        val closing = content.indexOf(delimiter, opening + delimiterLength)
        if (closing < 0) {
            result.append(content.substring(opening))
            break
        }
        result.append(content, opening, closing + delimiterLength)
        cursor = closing + delimiterLength
    }
    return result.toString()
}

private fun String.runLengthAt(start: Int, char: Char): Int {
    var end = start
    while (end < length && this[end] == char) end++
    return end - start
}

private fun String.isHttpUrl(): Boolean {
    return startsWith("http://", ignoreCase = true) ||
        startsWith("https://", ignoreCase = true)
}

private fun String.escapeHtml(): String {
    return replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun String.asStrongHtml(): String = "<strong>${escapeHtml()}</strong>"

private const val markdownInlineLinkPattern =
    """\[([^]\r\n]+)]\((<[^>\r\n]+>|[^\s)\r\n]+)(?:\s+(?:"[^"\r\n]*"|'[^'\r\n]*'))?\)"""
private val markdownInlineLinkRegex = Regex("(?<!!)$markdownInlineLinkPattern")

// Consume inline links and HTML tags as a unit, so their attributes and destinations stay intact.
private val bookTitleOrProtectedInlineRegex = Regex(
    """(?<=[\p{L}\p{N}])\*\*(《[^*\r\n<>\[\]\\]+》)\*\*(?!\*)|!?$markdownInlineLinkPattern|<[^<>\r\n]+>"""
)

private val ambiguousChineseStrongRegex = Regex(
    """\*\*([^*\r\n<>]+?[）】》〉」』”’"'。！？：；，、])\*\*(?=[\p{L}\p{N}])"""
)
