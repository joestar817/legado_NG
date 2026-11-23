package io.legado.app.ui.config

private const val chatSearchWhitespacePattern = """[\s\p{Z}]+"""
private val chatSearchWhitespace = Regex(chatSearchWhitespacePattern)

internal class AiChatSearchQuery(val text: String) {
    private val pattern = Regex(
        text.split(chatSearchWhitespace).joinToString(separator = chatSearchWhitespacePattern) {
            Regex.escape(it)
        },
        RegexOption.IGNORE_CASE,
    )

    fun findIn(value: CharSequence): MatchResult? = pattern.find(value)

    fun findAllIn(value: CharSequence): Sequence<MatchResult> = pattern.findAll(value)

    fun excerpt(value: String, match: IntRange): String {
        val first = (match.first - 10).coerceAtLeast(0).let { index ->
            if (index > 0 && Character.isSurrogatePair(value[index - 1], value[index])) index - 1
            else index
        }
        val end = maxOf(first + 54, match.last + 15).coerceAtMost(value.length).let { index ->
            if (index < value.length && Character.isSurrogatePair(value[index - 1], value[index])) index + 1
            else index
        }
        return buildString {
            if (first > 0) append('…')
            append(value.substring(first, end).replace(chatSearchWhitespace, " ").trim())
            if (end < value.length) append('…')
        }
    }
}
