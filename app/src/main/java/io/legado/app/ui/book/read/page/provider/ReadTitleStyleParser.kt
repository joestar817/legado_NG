package io.legado.app.ui.book.read.page.provider

import io.legado.app.ui.book.read.page.entities.ReadTitleSegment

internal object ReadTitleSegmentType {
    // Keep the serialized values used by existing NG and MD3 presets.
    const val NONE = 0
    const val CHARACTER_COUNT = 1
    const val DELIMITER = 2
    const val REGEX = 3
    const val CHAPTER_NUMBER = 4

    fun isCustom(type: Int): Boolean = type in CHARACTER_COUNT..REGEX
}

/** Converts title segmentation settings into the fragments used by the reader. */
object ReadTitleStyleParser {

    private val chapterPrefix = Regex(
        """^[\t\p{Zs}]*第[\t\p{Zs}]*(?:\p{Nd}+|[零〇一二两兩三四五六七八九十百千万萬亿億壹贰貳叁參肆伍陆陸柒捌玖拾佰仟]+)[\t\p{Zs}]*[章节節篇回集话話]"""
    )
    private val chapterNameContent = Regex("""[^\s\p{Z}\p{P}]""")

    fun parse(
        title: String,
        type: Int,
        distance: Int,
        flags: String,
        subTitleScale: Float,
    ): List<ReadTitleSegment> {
        if (title.isBlank()) return emptyList()
        val texts = when (type) {
            ReadTitleSegmentType.CHARACTER_COUNT -> if (distance in 1 until title.length) {
                listOf(title.take(distance), title.drop(distance))
            } else {
                listOf(title)
            }
            ReadTitleSegmentType.DELIMITER -> splitAfterFlags(title, flags.split(',').map(String::trim))
            ReadTitleSegmentType.REGEX -> splitAfterRegex(title, flags)
            ReadTitleSegmentType.CHAPTER_NUMBER -> splitAfterChapterNumber(title)
            else -> listOf(title)
        }
        return texts.filter(String::isNotBlank).mapIndexed { index, text ->
            ReadTitleSegment(text.trim(), index == 0, if (index == 0) 1f else subTitleScale)
        }
    }

    private fun splitAfterChapterNumber(title: String): List<String> {
        val prefix = chapterPrefix.find(title) ?: return listOf(title)
        val boundary = prefix.range.last + 1
        val chapterName = title.substring(boundary)
        // The source may already put the chapter number on its own line.
        if (!chapterNameContent.containsMatchIn(chapterName.lineSequence().first())) return listOf(title)
        return listOf(title.substring(0, boundary), chapterName)
    }

    private fun splitAfterFlags(title: String, flags: List<String>): List<String> {
        val activeFlags = flags.filter(String::isNotEmpty)
        if (activeFlags.isEmpty()) return listOf(title)
        val pattern = activeFlags.joinToString("|") { Regex.escape(it) }
        return splitAfterRegex(title, pattern)
    }

    private fun splitAfterRegex(title: String, pattern: String): List<String> {
        if (pattern.isBlank()) return listOf(title)
        return runCatching { title.split(splitRegex(pattern)) }
            .getOrDefault(listOf(title))
    }

    internal fun isValidSplitRegex(pattern: String): Boolean =
        runCatching { splitRegex(pattern) }.isSuccess

    private fun splitRegex(pattern: String) = Regex("(?<=$pattern)")
}
