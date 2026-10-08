package io.legado.app.ui.book.read

/** Display-only metadata: keep source information intact in the stored chapter. */
internal data class CatalogChapterMetadata(val wordCount: String?, val description: String?)

private const val COUNT_NUMBER = "(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)(?:\\.[0-9]+)?[万千百]?"
private val labeledCount = Regex("(?:章节)?字数\\s*[:：]\\s*($COUNT_NUMBER)\\s*字?")
private val standaloneCount = Regex("($COUNT_NUMBER)\\s*字")
private val embeddedCount = Regex("(?<![^\\s|·,，;；])($COUNT_NUMBER)\\s*字")
// A bare timestamp/date must remain source metadata, not become a word count.
private val bareCount = Regex("(?:[0-9]{1,6}|[0-9]{1,3}(?:,[0-9]{3}){1,2})(?:\\.[0-9]+)?[万千百]?")
private val labeledUpdateTime = Regex(
    """(?:更新)?时间\s*[:：]\s*(\d{4}[-/.]\d{1,2}[-/.]\d{1,2}(?:\s+\d{1,2}:\d{2}(?::\d{2})?)?)"""
)
private val repeatedSeparators = Regex("([|·,，;；])(?:\\s*[|·,，;；])+")
private val repeatedSpaces = Regex("[ \\t]{2,}")

internal fun catalogChapterMetadata(
    tag: String?,
    wordCount: String?,
    cached: Boolean = false,
): CatalogChapterMetadata {
    val localCount = wordCount?.trim()?.takeIf { cached && it.isNotEmpty() }
    var description = tag?.trim().orEmpty()
    when {
        standaloneCount.matches(description) || bareCount.matches(description) -> description = ""
        else -> (labeledCount.find(description) ?: embeddedCount.find(description))?.let { match ->
            description = description.removeRange(match.range)
        }
    }
    description = labeledUpdateTime.replace(description) { it.groupValues[1] }
        .replace(repeatedSeparators, "$1")
        .replace(repeatedSpaces, "  ")
        .trim { it.isWhitespace() || it in "|·,，;；" }
    return CatalogChapterMetadata(
        // Source metadata is never a substitute for the local count of a cached chapter.
        wordCount = localCount,
        description = description.takeIf(String::isNotEmpty),
    )
}
