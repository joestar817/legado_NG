package io.legado.app.help.ai

/**
 * 仅用于渲染历史消息的兼容描述表。这里不注册工作流、Hook 或业务处理器。
 */
internal object AiLegacyArtifactRegistry {

    private val fencedNames = listOf(
        "legado-character-scan",
        "character_scan_meta",
        "legado-book-scan",
        "book_scan_delta"
    )
    private val xmlNames = listOf("book_scan_delta", "character_scan_meta")

    val completedBlockRegex: Regex by lazy {
        val fenced = fencedNames.joinToString("|") { Regex.escape(it) }
        val xml = xmlNames.joinToString("|") { Regex.escape(it) }
        Regex(
            "(?s)(?:```[ \\t]*(?:$fenced)[^\\r\\n]*\\r?\\n.*?\\r?\\n```|" +
                "<(?:$xml)>.*?</(?:$xml)>)"
        )
    }

    val partialBlockRegex: Regex by lazy {
        val fenced = fencedNames.joinToString("|") { Regex.escape(it) }
        val xml = xmlNames.joinToString("|") { Regex.escape(it) }
        Regex(
            "(?s)(?:```[ \\t]*(?:$fenced)[^\\r\\n]*(?:\\r?\\n.*)?$|" +
                "<(?:$xml)>.*$)"
        )
    }
}
