package io.legado.app.ui.book.read.page.provider

/** MD3 九宫格：中心属于文字行，四周固定区属于行外边框。 */
internal data class ReadNineSliceGeometry(
    val width: Int,
    val height: Int,
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int,
    private val frameScale: Float = 1f,
) {
    val leftWidth: Float get() = left * frameScale
    val rightWidth: Float get() = (width - right) * frameScale

    /** 四角横纵共用倍率；同时受文字高度和行间距约束，不能仅压缩纵向。 */
    fun forLine(lineHeight: Float, lineSpacing: Float): ReadNineSliceGeometry {
        val halfGap = (lineSpacing - 1f).coerceAtLeast(0f) * lineHeight * 0.5f
        val centerScale = lineHeight.coerceAtLeast(0f) / (bottom - top).coerceAtLeast(1)
        val gapScale = halfGap / maxOf(top.toFloat(), (height - bottom).toFloat(), 0.1f)
        return copy(frameScale = minOf(centerScale, gapScale, 1f).coerceAtLeast(0f))
    }

    fun verticalInsets(lineHeight: Float, lineSpacing: Float): Pair<Float, Float> {
        val scale = forLine(lineHeight, lineSpacing).frameScale
        return top * scale to (height - bottom) * scale
    }

    companion object {
        fun from(width: Int, height: Int, style: ReadCharStyle) = ReadNineSliceGeometry(
            width, height,
            (width * style.npLeft.coerceIn(0f, 0.5f)).toInt(),
            (width * (1f - style.npRight.coerceIn(0f, 0.5f))).toInt(),
            (height * style.npTop.coerceIn(0f, 0.5f)).toInt(),
            (height * (1f - style.npBottom.coerceIn(0f, 0.5f))).toInt(),
        )
    }
}

internal fun ReadCharStyle.sameImageAs(other: ReadCharStyle): Boolean =
    bgImage == other.bgImage && bgImageFit == other.bgImageFit &&
        bgImageScale == other.bgImageScale && npLeft == other.npLeft &&
        npRight == other.npRight && npTop == other.npTop && npBottom == other.npBottom

internal data class ReadNineSliceLineInsets(val before: FloatArray, val after: Float)

/** 前缀和只在排版时建立；候选行宽查询不重复扫描规则或解码图片。 */
internal class ReadNineSliceWidthBudget(
    styles: Array<ReadCharStyle?>,
    geometry: (ReadCharStyle) -> ReadNineSliceGeometry?,
    private val textInset: Float = 0f,
) {
    private val left = FloatArray(styles.size)
    private val right = FloatArray(styles.size)
    private val boundaries = FloatArray(styles.size)

    init {
        var previous: ReadCharStyle? = null
        var cuts: ReadNineSliceGeometry? = null
        styles.forEachIndexed { index, style ->
            val current = style?.takeIf { it.bgImageFit == 3 && it.bgImage.isNotBlank() }
            val changed = current == null || previous == null || !current.sameImageAs(previous)
            if (changed) cuts = current?.let(geometry)
            left[index] = cuts?.let { it.leftWidth + textInset } ?: 0f
            right[index] = cuts?.let { it.rightWidth + textInset } ?: 0f
            if (index > 0) {
                boundaries[index] = boundaries[index - 1] +
                    if (changed) right[index - 1] + left[index] else 0f
            }
            previous = current
        }
    }

    fun width(start: Int, end: Int): Float = if (start >= end) 0f else
        left[start] + right[end - 1] + boundaries[end - 1] - boundaries[start]
}

/** 每行独立留出左右框；按 UTF-16 长度消费样式，避免增补字符后的规则错位。 */
internal fun nineSliceLineInsets(
    words: List<String>,
    styles: Array<ReadCharStyle?>?,
    lineStart: Int,
    textInset: Float = 0f,
    geometry: (ReadCharStyle) -> ReadNineSliceGeometry?,
): ReadNineSliceLineInsets {
    val before = FloatArray(words.size)
    var previous: ReadCharStyle? = null
    var previousGeometry: ReadNineSliceGeometry? = null
    var sourceIndex = lineStart
    words.forEachIndexed { index, word ->
        val current = styles?.getOrNull(sourceIndex)?.takeIf { it.bgImageFit == 3 && it.bgImage.isNotBlank() }
        if (current == null || previous == null || !current.sameImageAs(previous)) {
            before[index] += previousGeometry?.let { it.rightWidth + textInset } ?: 0f
            previousGeometry = current?.let(geometry)
            before[index] += previousGeometry?.let { it.leftWidth + textInset } ?: 0f
        }
        previous = current
        sourceIndex += word.length
    }
    return ReadNineSliceLineInsets(before, previousGeometry?.let { it.rightWidth + textInset } ?: 0f)
}
