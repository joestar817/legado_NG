package io.legado.app.ui.config

/**
 * Normalizes model Markdown before it reaches Markwon.
 *
 * CommonMark does not treat `**` after closing Chinese punctuation as a closing
 * delimiter when a Chinese letter follows immediately. Model output commonly
 * uses that form, so convert only that ambiguous shape to the equivalent HTML
 * strong tag. Skill-internal links are useful to the agent but have no valid
 * navigation target in the app; keep their label while allowing web links.
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
    return ambiguousChineseStrongRegex.replace(withoutInternalLinks) { match ->
        "<strong>${match.groupValues[1].escapeHtml()}</strong>"
    }
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

private val markdownInlineLinkRegex = Regex(
    """(?<!!)\[([^]\r\n]+)]\((<[^>\r\n]+>|[^\s)\r\n]+)(?:\s+(?:"[^"\r\n]*"|'[^'\r\n]*'))?\)"""
)

private val ambiguousChineseStrongRegex = Regex(
    """\*\*([^*\r\n<>]+?[）】》〉」』”’"'。！？：；，、])\*\*(?=[\p{L}\p{N}])"""
)
