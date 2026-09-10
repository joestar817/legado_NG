package io.legado.app.ui.book.read.page.provider

import android.text.Layout
import android.text.TextPaint
import java.text.BreakIterator

/** 普通断行使用 Unicode 行边界；仅九宫格段落需要额外的边框宽度预算。 */
internal class ReadNineSliceLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    words: List<String>,
    widths: List<Float>,
    budget: ReadNineSliceWidthBudget,
) : Layout(text, paint, width, Alignment.ALIGN_NORMAL, 0f, 0f) {
    private val starts: IntArray

    init {
        val iterator = BreakIterator.getLineInstance().apply { setText(text) }
        val boundaries = BooleanArray(text.length + 1)
        var boundary = iterator.first()
        while (boundary != BreakIterator.DONE) {
            boundaries[boundary] = true
            boundary = iterator.next()
        }
        starts = nineSliceLineStarts(words, widths, width.toFloat(), budget::width) { boundaries[it] }
    }

    override fun getLineCount() = starts.size - 1
    override fun getLineStart(line: Int) = starts[line]
    override fun getLineTop(line: Int) = 0
    override fun getLineDescent(line: Int) = 0
    override fun getParagraphDirection(line: Int) = 1
    override fun getLineContainsTab(line: Int) = false
    override fun getLineDirections(line: Int): Directions? = null
    override fun getTopPadding() = 0
    override fun getBottomPadding() = 0
    override fun getEllipsisStart(line: Int) = 0
    override fun getEllipsisCount(line: Int) = 0
}
