package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import java.util.regex.Pattern

internal object HtmlImageTags {

    val anyImageTag = Regex(
        pattern = """<img\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val formattedImagePattern = Pattern.compile(
        AppPattern.imgPattern.pattern(),
        Pattern.CASE_INSENSITIVE,
    )

    fun removeEmptySources(content: String): String {
        return anyImageTag.replace(content) { matchResult ->
            val matcher = formattedImagePattern.matcher(matchResult.value)
            if (!matcher.find()) {
                return@replace matchResult.value
            }
            val src = matcher.group(1).orEmpty()
            val optionMatcher = paramPattern.matcher(src)
            val imageUrl = if (optionMatcher.find()) {
                src.substring(0, optionMatcher.start())
            } else {
                src
            }
            if (imageUrl.isBlank()) "" else matchResult.value
        }
    }
}
