package io.legado.app.help.tts

import kotlin.math.min

/** 只读正文输入，不持有播放状态；普通文字与段落读取不要求分页。 */
interface ReadAloudTextSource {
    val isReady: Boolean
    val hasContent: Boolean
    /** 已供朗读使用的正文；原生图片/按钮占位符由原生适配端转换。 */
    val text: String
    val paragraphs: List<ReadAloudParagraph>

    /** 仅显式按页读取时使用；尚未准备分页的内容可以返回 null。 */
    val pageText: ReadAloudPageText?
}

/** 朗读需要的段落信息，不暴露 TextLine 或页面几何。 */
interface ReadAloudParagraph {
    val num: Int
    val text: String
    val length: Int
    val chapterPosition: Int

    /** 沿用原生段落的边界语义，不在接口适配时更改区间端点。 */
    val chapterIndices: IntRange
    val isParagraphEnd: Boolean
}

/** 可选的分页文字能力，与普通正文输入分开。 */
interface ReadAloudPageText {
    val size: Int
    val paragraphs: List<ReadAloudParagraph>

    fun textAt(index: Int): String
    fun startAt(index: Int): Int = (0 until index.coerceIn(0, size)).sumOf { textAt(it).length }

    fun indexAt(position: Int): Int {
        var low = 0
        var high = size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (startAt(middle) <= position) low = middle + 1 else high = middle
        }
        return (low - 1).coerceAtLeast(0)
    }
}

private val readAloudPlaceholder = Regex("[袮꧁]")

fun ReadAloudTextSource.readText(pageSplit: Boolean = false): String {
    return if (pageSplit) {
        checkNotNull(pageText) { "按页朗读需要已准备的分页文本" }
            .readTextRange(pageSplit = true)
    } else {
        text
    }
}

fun ReadAloudTextSource.readParagraphs(pageSplit: Boolean): List<ReadAloudParagraph> {
    return if (pageSplit) {
        checkNotNull(pageText) { "按页朗读需要已准备的分页文本" }.paragraphs
    } else {
        paragraphs
    }
}

fun ReadAloudTextSource.paragraphNumberAt(position: Int, pageSplit: Boolean): Int {
    return readParagraphs(pageSplit).firstOrNull { position in it.chapterIndices }?.num ?: -1
}

/** 保持原有分页换行、占位符和页内起读偏移处理。 */
fun ReadAloudPageText.readTextRange(
    pageIndex: Int = 0,
    pageSplit: Boolean = false,
    startPos: Int = 0,
    pageEndIndex: Int = size - 1,
): String {
    val builder = StringBuilder()
    if (size > 0) {
        for (index in pageIndex..min(pageEndIndex, size - 1)) {
            builder.append(textAt(index).replace(readAloudPlaceholder, " "))
            if (pageSplit && !builder.endsWith("\n")) {
                builder.append("\n")
            }
        }
    }
    return builder.substring(startPos)
}
