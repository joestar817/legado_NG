package io.legado.app.web.mcp

/**
 * Removes non-text payloads that are useful to the reader UI but waste model context.
 * The original cached chapter is never modified.
 */
internal object McpTextSanitizer {

    private val embeddedImageTag = Regex("""(?is)<img\b[^>]*data:image/[^>]*>""")
    private val imageTag = Regex("""(?is)<img\b[^>]*>""")
    private val altAttribute = Regex("""(?is)\balt\s*=\s*(["'])(.*?)\1""")

    fun forModel(content: String): String {
        val withoutEmbeddedImages = embeddedImageTag.replace(content) { match ->
            imageAlt(match.value).orEmpty()
        }
        return imageTag.replace(withoutEmbeddedImages) { match ->
            imageAlt(match.value) ?: "[图片]"
        }
    }

    private fun imageAlt(tag: String): String? {
        return altAttribute.find(tag)
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.take(200)
    }
}

internal fun mcpInclusiveChapterEnd(start: Int, chapterCount: Int): Int {
    return start.coerceAtLeast(0) + chapterCount.coerceAtLeast(1) - 1
}
