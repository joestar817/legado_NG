package io.legado.app.help.book

import io.legado.app.constant.AppPattern
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import java.util.regex.Pattern

internal object HtmlImageTags {

    private const val PLACEHOLDER_START = '\uE000'
    private const val PLACEHOLDER_END = '\uE001'

    val anyImageTag = Regex(
        pattern = """<img\b(?:[^>\"']|\"[^\"]*\"|'[^']*')*>""",
        options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )

    private val formattedImagePattern = Pattern.compile(
        AppPattern.imgPattern.pattern(),
        Pattern.CASE_INSENSITIVE,
    )

    /**
     * 在系统隐式文本处理期间将完整图片标签视为不可分割内容。
     * 显式用户替换规则应在本方法返回后执行，以继续支持主动删图或改写图片地址。
     */
    fun preserveDuringTextTransform(
        content: String,
        transform: (String) -> String,
    ): String {
        if (!content.contains("<img", ignoreCase = true)) {
            return transform(content)
        }
        val imageTags = arrayListOf<String>()
        val placeholderPrefix = uniquePlaceholderPrefix(content)
        val protectedContent = anyImageTag.replace(content) { matchResult ->
            val index = imageTags.size
            imageTags.add(matchResult.value)
            "$placeholderPrefix$index$PLACEHOLDER_END"
        }
        if (imageTags.isEmpty()) {
            return transform(content)
        }
        val transformedContent = transform(protectedContent)
        val placeholderRegex = Regex(
            "${Regex.escape(placeholderPrefix)}(\\d+)${Regex.escape(PLACEHOLDER_END.toString())}"
        )
        return placeholderRegex.replace(transformedContent) { matchResult ->
            val index = matchResult.groupValues[1].toIntOrNull()
            index?.let(imageTags::getOrNull) ?: matchResult.value
        }
    }

    private fun uniquePlaceholderPrefix(content: String): String {
        var version = 0
        while (true) {
            val prefix = "${PLACEHOLDER_START}LEGADOIMG$version"
            if (!content.contains(prefix)) {
                return prefix
            }
            version++
        }
    }

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
