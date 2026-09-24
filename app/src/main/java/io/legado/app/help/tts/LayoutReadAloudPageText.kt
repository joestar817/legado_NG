package io.legado.app.help.tts

/** Read-only slices of the existing chapter text at page boundaries supplied by its layout. */
internal class LayoutReadAloudPageText(private val content: String, starts: List<Int>) : ReadAloudPageText {
    init {
        require(starts.all { it in 0..content.length &&
            (it == 0 || it == content.length || !content[it].isLowSurrogate() || !content[it - 1].isHighSurrogate()) })
    }
    private val boundaries = (listOf(0) + starts.filter { it in 1 until content.length } + content.length).distinct().sorted()
    override val size: Int get() = boundaries.size - 1
    override fun textAt(index: Int): String = content.substring(boundaries[index], boundaries[index + 1])
    override fun startAt(index: Int): Int = boundaries[index.coerceIn(0, boundaries.lastIndex)]
    override val paragraphs: List<ReadAloudParagraph> = buildList {
        for (page in 0 until this@LayoutReadAloudPageText.size) {
            var from = boundaries[page]
            val end = boundaries[page + 1]
            while (from < end) {
                val newline = content.indexOf('\n', from).takeIf { it in from until end }
                val to = newline ?: end
                if (to > from) {
                    val number = size + 1
                    val start = from
                    val value = content.substring(start, to)
                    add(object : ReadAloudParagraph {
                        override val num = number
                        override val text = value
                        override val length = value.length
                        override val chapterPosition = start
                        override val chapterIndices = start..to
                        override val isParagraphEnd = newline != null || to == content.length
                    })
                }
                from = to + 1
            }
        }
    }
}
