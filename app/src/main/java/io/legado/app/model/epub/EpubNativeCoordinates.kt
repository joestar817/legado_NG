package io.legado.app.model.epub

import io.legado.app.constant.AppPattern

/** Snapshot of the existing text layout's content coordinates, with no native screen geometry. */
internal data class EpubContentLine(
    val position: Int,
    val paragraph: Int,
    val text: String,
    val title: Boolean,
    val image: Boolean,
    val paragraphEnd: Boolean,
)

internal data class EpubMappedContent(
    val text: String,
    val documents: List<EpubContentProjection.ProjectedDocument>,
)

/** Match within the owning paragraph, including whitespace moved after a block image by native layout. */
internal fun EpubContentProjection.bindNativeContent(paragraphs: List<String>, lines: List<EpubContentLine>): EpubMappedContent {
    data class Token(val offset: Int, val char: Char?, val media: Boolean)
    var paragraphOffset = 0
    val tokens = paragraphs.map { paragraph ->
        checkCancelled()
        val result = ArrayList<Token>()
        val matcher = AppPattern.imgPattern.matcher(paragraph)
        var cursor = 0
        while (matcher.find()) {
            for (index in cursor until matcher.start()) result.add(Token(paragraphOffset + index, paragraph[index], false))
            result.add(Token(paragraphOffset + matcher.start(), null, true))
            cursor = matcher.end()
        }
        for (index in cursor until paragraph.length) result.add(Token(paragraphOffset + index, paragraph[index], false))
        paragraphOffset += paragraph.length + 1
        result
    }
    val cursors = IntArray(paragraphs.size)
    val deferredWhitespace = arrayOfNulls<ArrayDeque<Token>>(paragraphs.size)
    val length = lines.maxOfOrNull { it.position + it.text.length + if (it.paragraphEnd) 1 else 0 } ?: 0
    val text = CharArray(length) { '\n' }
    val title = StringBuilder()
    val titlePositions = ArrayList<Int>()
    for (line in lines) {
        checkCancelled()
        line.text.toCharArray().copyInto(text, line.position)
        if (line.title) {
            if (title.isNotEmpty() && line.position > titlePositions.last() + 1) {
                title.append('\n'); titlePositions.add(titlePositions.last() + 1)
            }
            title.append(line.text)
            titlePositions.addAll(line.text.indices.map { line.position + it })
            continue
        }
        if (line.paragraph !in tokens.indices) continue // Generated chapter-title image.
        val sourceTokens = tokens[line.paragraph]
        var cursor = cursors[line.paragraph]
        for ((index, char) in line.text.withIndex()) {
            if (line.image) {
                // TextChapterLayout leaves a whitespace-only prefix in its text buffer while
                // emitting the block image first. The prefix may appear in a later text line.
                while (cursor < sourceTokens.size && sourceTokens[cursor].char?.isWhitespace() == true) {
                    val pending = deferredWhitespace[line.paragraph] ?: ArrayDeque<Token>().also {
                        deferredWhitespace[line.paragraph] = it
                    }
                    pending.addLast(sourceTokens[cursor++])
                }
            } else {
                val pending = deferredWhitespace[line.paragraph]
                // Whitespace can also be omitted by native layout; only consume exact matches.
                while (!pending.isNullOrEmpty() && pending.first().char != char) pending.removeFirst()
                if (!pending.isNullOrEmpty()) {
                    bind(pending.removeFirst().offset, line.position + index)
                    continue
                }
            }
            while (cursor < sourceTokens.size && sourceTokens[cursor].char?.isWhitespace() == true &&
                (line.image || sourceTokens[cursor].char != char)) cursor++
            val token = sourceTokens.getOrNull(cursor)
            if (token == null && char.isWhitespace()) continue
            requireNotNull(token) { "EPUB paragraph ${line.paragraph} has extra native text" }
            if (token.media) {
                require(line.image || char == '袮' || char == '꧁') { "EPUB image position was consumed as text" }
            } else require(token.char == char || token.char == '袮' && char == '祢') {
                "EPUB paragraph ${line.paragraph} differs at character $cursor"
            }
            bind(token.offset, line.position + index)
            cursor++
        }
        cursors[line.paragraph] = cursor
    }
    tokens.forEachIndexed { index, remaining ->
        require(remaining.drop(cursors[index]).all { it.char?.isWhitespace() == true }) {
            "EPUB paragraph $index was not fully represented by the current content layout"
        }
    }
    bindTitle(title.toString(), titlePositions.toIntArray())
    return EpubMappedContent(String(text), documents())
}
