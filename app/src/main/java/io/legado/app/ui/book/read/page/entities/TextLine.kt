package io.legado.app.ui.book.read.page.entities

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Paint.FontMetrics
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import android.text.TextPaint
import androidx.annotation.Keep
import androidx.core.graphics.PathParser
import androidx.core.graphics.withSave
import io.legado.app.help.PaintPool
import io.legado.app.help.book.isImage
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.entities.TextPage.Companion.emptyTextPage
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.canvasrecorder.recordIfNeededThenDraw
import io.legado.app.utils.dpToPx
import splitties.init.appCtx
import java.io.File

/**
 * 行信息
 */
@Keep
@Suppress("unused", "MemberVisibilityCanBePrivate")
data class TextLine(
    var text: String = "",
    private val textColumns: ArrayList<BaseColumn> = arrayListOf(),
    var lineTop: Float = 0f,
    var lineBase: Float = 0f,
    var lineBottom: Float = 0f,
    var indentWidth: Float = 0f,
    var paragraphNum: Int = 0,
    var chapterPosition: Int = 0,
    var pagePosition: Int = 0,
    val isTitle: Boolean = false,
    var titleTextSize: Float? = null,
    var isParagraphEnd: Boolean = false,
    var isImage: Boolean = false,
    var isHtml: Boolean = false,
    var startX: Float = 0f,
    var indentSize: Int = 0,
    var extraLetterSpacing: Float = 0f,
    var extraLetterSpacingOffsetX: Float = 0f,
    var wordSpacing: Float = 0f,
    var exceed: Boolean = false,
    var onlyTextColumn: Boolean = true,
) {

    val columns: List<BaseColumn> get() = textColumns
    val charSize: Int get() = text.length
    val lineStart: Float get() = textColumns.firstOrNull()?.start ?: 0f
    val lineEnd: Float get() = textColumns.lastOrNull()?.end ?: 0f
    val chapterIndices: IntRange get() = chapterPosition..chapterPosition + charSize
    val height: Float inline get() = lineBottom - lineTop
    val canvasRecorder = CanvasRecorderFactory.create()
    var searchResultColumnCount = 0
    var hasReadStyle = false
    var isReadAloud: Boolean = false
        set(value) {
            if (field != value) {
                invalidate()
            }
            if (value) {
                textPage.hasReadAloudSpan = true
            }
            field = value
        }
    var textPage: TextPage = emptyTextPage
    var isLeftLine = true

    fun addColumn(column: BaseColumn) {
        if (column !is TextColumn) {
            onlyTextColumn = false
        }
        column.textLine = this
        textColumns.add(column)
    }

    fun addColumns(columns: Collection<BaseColumn>) {
        onlyTextColumn = false
        columns.forEach { column ->
            column.textLine = this
        }
        textColumns.addAll(columns)
    }

    fun getColumn(index: Int): BaseColumn {
        return textColumns.getOrElse(index) {
            textColumns.last()
        }
    }

    fun getColumnReverseAt(index: Int, offset: Int = 0): BaseColumn {
        return textColumns[textColumns.lastIndex - offset - index]
    }

    fun getColumnsCount(): Int {
        return textColumns.size
    }

    fun upTopBottom(durY: Float, textHeight: Float, fontMetrics: FontMetrics) {
        lineTop = ChapterProvider.paddingTop + durY
        lineBottom = lineTop + textHeight
        lineBase = lineBottom - fontMetrics.descent
    }

    fun isTouch(x: Float, y: Float, relativeOffset: Float): Boolean {
        return y > lineTop + relativeOffset
                && y < lineBottom + relativeOffset
                && x >= lineStart
                && x <= lineEnd + 20.dpToPx()
    }

    fun isTouchY(y: Float, relativeOffset: Float): Boolean {
        return y > lineTop + relativeOffset
                && y < lineBottom + relativeOffset
    }

    fun isVisible(relativeOffset: Float): Boolean {
        val top = lineTop + relativeOffset
        val bottom = lineBottom + relativeOffset
        val width = bottom - top
        val visibleTop = ChapterProvider.paddingTop
        val visibleBottom = ChapterProvider.visibleBottom
        val visible = when {
            // 完全可视
            top >= visibleTop && bottom <= visibleBottom -> true
            top <= visibleTop && bottom >= visibleBottom -> true
            // 上方第一行部分可视
            top < visibleTop && bottom > visibleTop && bottom < visibleBottom -> {
                if (isImage) {
                    true
                } else {
                    val visibleRate = (bottom - visibleTop) / width
                    visibleRate > 0.6
                }
            }
            // 下方第一行部分可视
            top > visibleTop && top < visibleBottom && bottom > visibleBottom -> {
                if (isImage) {
                    true
                } else {
                    val visibleRate = (visibleBottom - top) / width
                    visibleRate > 0.6
                }
            }
            // 不可视
            else -> false
        }
        return visible
    }

    fun draw(view: ContentTextView, canvas: Canvas) {
        val inlineNoteSpacers = view.textHighlightNoteSpacerPositions(this)
        if (inlineNoteSpacers.isNotEmpty()) {
            drawTextLineWithInlineNoteSpacers(view, canvas, inlineNoteSpacers)
        } else if (AppConfig.optimizeRender) {
            canvasRecorder.recordIfNeededThenDraw(canvas, view.width, height.toInt()) {
                drawTextLine(view, this)
            }
        } else {
            drawTextLine(view, canvas)
        }
    }

    private fun drawTextLine(view: ContentTextView, canvas: Canvas) {
        drawHighlightBackgrounds(canvas)
        if (checkFastDraw()) {
            fastDrawTextLine(view, canvas)
        } else {
            for (i in columns.indices) {
                columns[i].draw(view, canvas)
            }
        }

        // 墨水屏模式下的朗读和搜索下划线
        if (AppConfig.isEInkMode && (isReadAloud || searchResultColumnCount > 0)) {
            val underlinePaint = PaintPool.obtain()
            underlinePaint.set(ChapterProvider.contentPaint)
            underlinePaint.strokeWidth = 1.dpToPx().toFloat()
            val lineY = height - 1.dpToPx()
            canvas.drawLine(lineStart + indentWidth, lineY, lineEnd, lineY, underlinePaint)
            PaintPool.recycle(underlinePaint)
        }

        drawHighlightUnderlines(canvas)
        if (!isImage && !isHtml && ReadBook.book?.isImage != true &&
            ReadBookConfig.fullLineUnderlineEnabled
        ) {
            drawFullLineUnderline(canvas)
        }
    }

    private fun drawTextLineWithInlineNoteSpacers(
        view: ContentTextView,
        canvas: Canvas,
        spacerPositions: List<Float>,
    ) {
        var sourceStart = 0f
        var inlineOffset = 0f
        spacerPositions.forEach { spacerX ->
            drawTextLineSegment(
                view = view,
                canvas = canvas,
                clipStart = sourceStart + inlineOffset,
                clipEnd = spacerX + inlineOffset,
                inlineOffset = inlineOffset,
            )
            sourceStart = spacerX
            inlineOffset += view.textHighlightNoteSpacerWidthPx
        }
        drawTextLineSegment(
            view = view,
            canvas = canvas,
            clipStart = sourceStart + inlineOffset,
            clipEnd = view.width.toFloat(),
            inlineOffset = inlineOffset,
        )
    }

    private fun drawTextLineSegment(
        view: ContentTextView,
        canvas: Canvas,
        clipStart: Float,
        clipEnd: Float,
        inlineOffset: Float,
    ) {
        if (clipEnd <= clipStart) return
        val underlineOverflow = 10.dpToPx().toFloat()
        canvas.withSave {
            clipRect(clipStart, -underlineOverflow, clipEnd, height + underlineOverflow)
            translate(inlineOffset, 0f)
            drawTextLine(view, this)
        }
    }

    private fun drawHighlightBackgrounds(canvas: Canvas) {
        if (!hasReadStyle) return
        val columns = textColumns.filterIsInstance<TextColumn>()
        val isNight = ReadBookConfig.isNightTheme
        var index = 0
        while (index < columns.size) {
            val column = columns[index]
            val color = column.readStyle?.resolveBackgroundColor(isNight)
            if (color == null) {
                index++
                continue
            }
            var end = column.end
            var next = index + 1
            while (
                next < columns.size &&
                columns[next].readStyle?.resolveBackgroundColor(isNight) == color
            ) {
                end = columns[next].end
                next++
            }
            val paint = PaintPool.obtain()
            paint.style = Paint.Style.FILL
            paint.color = color
            canvas.drawRect(column.start, 0f, end, height, paint)
            PaintPool.recycle(paint)
            index = next
        }
        index = 0
        while (index < columns.size) {
            val column = columns[index]
            val style = column.readStyle
            if (style != null && style.bgImage.isNotBlank()) {
                var end = column.end
                var next = index + 1
                while (next < columns.size && columns[next].readStyle?.let {
                        it.bgImage == style.bgImage &&
                            it.bgImageFit == style.bgImageFit &&
                            it.bgImageScale == style.bgImageScale
                    } == true
                ) {
                    end = columns[next].end
                    next++
                }
                drawHighlightImage(canvas, column.start, end, style)
                index = next
            } else {
                index++
            }
        }
    }

    private fun drawHighlightImage(
        canvas: Canvas,
        start: Float,
        end: Float,
        style: io.legado.app.ui.book.read.page.provider.ReadCharStyle,
    ) {
        val bitmap = getHighlightBitmap(style.bgImage) ?: return
        val paint = PaintPool.obtain().apply {
            isAntiAlias = true
            isFilterBitmap = true
            this.style = Paint.Style.FILL
        }
        val top = 1.dpToPx().toFloat()
        val bottom = height - 1.dpToPx()
        val destination = RectF(start, top, end, bottom)
        val scale = style.bgImageScale.coerceIn(0.1f, 5f)
        when (style.bgImageFit) {
            1 -> {
                val width = destination.width() * scale
                val drawHeight = destination.height() * scale
                val target = RectF(
                    destination.centerX() - width / 2,
                    destination.centerY() - drawHeight / 2,
                    destination.centerX() + width / 2,
                    destination.centerY() + drawHeight / 2,
                )
                canvas.save()
                canvas.clipRect(destination)
                canvas.drawBitmap(bitmap, null, target, paint)
                canvas.restore()
            }
            2 -> {
                val cover = maxOf(destination.width() / bitmap.width, destination.height() / bitmap.height) * scale
                val width = bitmap.width * cover
                val drawHeight = bitmap.height * cover
                val target = RectF(
                    destination.centerX() - width / 2,
                    destination.centerY() - drawHeight / 2,
                    destination.centerX() + width / 2,
                    destination.centerY() + drawHeight / 2,
                )
                canvas.save()
                canvas.clipRect(destination)
                canvas.drawBitmap(bitmap, null, target, paint)
                canvas.restore()
            }
            3 -> drawNineSlice(canvas, bitmap, destination, style, paint)
            else -> {
                val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                shader.setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(start, top)
                })
                paint.shader = shader
                canvas.drawRect(destination, paint)
            }
        }
        PaintPool.recycle(paint)
    }

    private fun drawNineSlice(
        canvas: Canvas,
        bitmap: Bitmap,
        destination: RectF,
        style: io.legado.app.ui.book.read.page.provider.ReadCharStyle,
        paint: Paint,
    ) {
        val sourceX = intArrayOf(
            0,
            (bitmap.width * style.npLeft).toInt().coerceIn(0, bitmap.width),
            (bitmap.width * (1f - style.npRight)).toInt().coerceIn(0, bitmap.width),
            bitmap.width,
        )
        val sourceY = intArrayOf(
            0,
            (bitmap.height * style.npTop).toInt().coerceIn(0, bitmap.height),
            (bitmap.height * (1f - style.npBottom)).toInt().coerceIn(0, bitmap.height),
            bitmap.height,
        )
        val left = minOf((sourceX[1] - sourceX[0]).toFloat(), destination.width() / 2)
        val right = minOf((sourceX[3] - sourceX[2]).toFloat(), destination.width() / 2)
        val top = minOf((sourceY[1] - sourceY[0]).toFloat(), destination.height() / 2)
        val bottom = minOf((sourceY[3] - sourceY[2]).toFloat(), destination.height() / 2)
        val targetX = floatArrayOf(destination.left, destination.left + left, destination.right - right, destination.right)
        val targetY = floatArrayOf(destination.top, destination.top + top, destination.bottom - bottom, destination.bottom)
        for (row in 0..2) for (column in 0..2) {
            if (sourceX[column] >= sourceX[column + 1] || sourceY[row] >= sourceY[row + 1]) continue
            canvas.drawBitmap(
                bitmap,
                Rect(sourceX[column], sourceY[row], sourceX[column + 1], sourceY[row + 1]),
                RectF(targetX[column], targetY[row], targetX[column + 1], targetY[row + 1]),
                paint,
            )
        }
    }

    private fun drawHighlightUnderlines(canvas: Canvas) {
        if (!hasReadStyle) return
        val columns = textColumns.filterIsInstance<TextColumn>()
        var index = 0
        while (index < columns.size) {
            val column = columns[index]
            val style = column.readStyle
            if (style == null || style.underlineMode == 0) {
                index++
                continue
            }
            var end = column.end
            var next = index + 1
            while (next < columns.size && columns[next].readStyle == style) {
                end = columns[next].end
                next++
            }
            val paint = PaintPool.obtain()
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = style.underlineWidth.dpToPx()
            paint.color = style.resolveUnderlineColor(ReadBookConfig.isNightTheme)
                ?: style.resolveTextColor(ReadBookConfig.isNightTheme)
                ?: ReadBookConfig.textColor
            val lineY = height + style.underlineOffset.dpToPx()
            when (style.underlineMode) {
                1 -> canvas.drawLine(column.start, lineY, end, lineY, paint)
                2 -> {
                    paint.pathEffect = DashPathEffect(floatArrayOf(8.dpToPx().toFloat(), 5.dpToPx().toFloat()), 0f)
                    canvas.drawLine(column.start, lineY, end, lineY, paint)
                }
                3 -> drawWavyUnderline(canvas, paint, column.start, end, lineY)
                4 -> {
                    canvas.drawLine(column.start, lineY, end, lineY, paint)
                    canvas.drawLine(column.start, lineY + 3.dpToPx(), end, lineY + 3.dpToPx(), paint)
                }
                5 -> drawSvgUnderline(canvas, paint, column.start, end, lineY, style.underlineSvgPath)
            }
            PaintPool.recycle(paint)
            index = next
        }
    }

    private fun drawWavyUnderline(canvas: Canvas, paint: Paint, start: Float, end: Float, y: Float) {
        val path = Path().apply { moveTo(start, y) }
        var x = start
        var upwards = true
        val length = 6.dpToPx().toFloat()
        while (x < end) {
            val next = (x + length).coerceAtMost(end)
            path.quadTo((x + next) / 2, y + if (upwards) -3.dpToPx() else 3.dpToPx(), next, y)
            upwards = !upwards
            x = next
        }
        canvas.drawPath(path, paint)
    }

    private fun drawSvgUnderline(
        canvas: Canvas,
        paint: Paint,
        start: Float,
        end: Float,
        y: Float,
        pathData: String,
    ) {
        val path = runCatching { PathParser.createPathFromPathData(pathData) }.getOrNull() ?: return
        canvas.save()
        canvas.translate(start, y - 50f)
        canvas.scale((end - start) / 100f, 1f)
        canvas.drawPath(path, paint)
        canvas.restore()
    }

    private fun drawFullLineUnderline(canvas: Canvas) {
        val paint = PaintPool.obtain()
        paint.set(ChapterProvider.contentPaint)
        paint.clearShadowLayer()
        paint.color = ReadBookConfig.resolvedUnderlineColor
        paint.strokeWidth = ReadBookConfig.underlineHeight.toFloat()
        paint.style = Paint.Style.STROKE
        paint.pathEffect = if (ReadBookConfig.dottedLine && !AppConfig.isEInkMode) {
            DashPathEffect(floatArrayOf(ReadBookConfig.dottedBase, ReadBookConfig.dottedRatio), 0f)
        } else {
            null
        }
        val lineY = height + (ReadBookConfig.underlinePadding - 10).dpToPx()
        val pageOffset = if (textPage.doublePage && !isLeftLine) {
            ChapterProvider.viewWidth / 2f
        } else {
            0f
        }
        val startX = if (ReadBookConfig.underlineExtend) {
            pageOffset + ChapterProvider.paddingLeft
        } else {
            lineStart + indentWidth
        }
        val endX = if (ReadBookConfig.underlineExtend) {
            pageOffset + ChapterProvider.paddingLeft + ChapterProvider.visibleWidth
        } else {
            lineEnd
        }
        var segmentStart = startX
        highlightUnderlineRanges().forEach { (highlightStart, highlightEnd) ->
            val blockedStart = highlightStart.coerceIn(startX, endX)
            val blockedEnd = highlightEnd.coerceIn(startX, endX)
            if (blockedEnd <= segmentStart) return@forEach
            if (blockedStart > segmentStart) {
                canvas.drawLine(segmentStart, lineY, blockedStart, lineY, paint)
            }
            segmentStart = maxOf(segmentStart, blockedEnd)
        }
        if (segmentStart < endX) {
            canvas.drawLine(segmentStart, lineY, endX, lineY, paint)
        }
        PaintPool.recycle(paint)
    }

    private fun highlightUnderlineRanges(): List<Pair<Float, Float>> {
        if (!hasReadStyle) return emptyList()
        val ranges = textColumns.filterIsInstance<TextColumn>()
            .filter { it.readStyle?.underlineMode?.let { mode -> mode != 0 } == true }
            .map { it.start to it.end }
            .sortedBy(Pair<Float, Float>::first)
        if (ranges.isEmpty()) return emptyList()
        return buildList {
            var start = ranges.first().first
            var end = ranges.first().second
            ranges.drop(1).forEach { (nextStart, nextEnd) ->
                if (nextStart <= end) {
                    end = maxOf(end, nextEnd)
                } else {
                    add(start to end)
                    start = nextStart
                    end = nextEnd
                }
            }
            add(start to end)
        }
    }

    @SuppressLint("NewApi")
    private fun fastDrawTextLine(view: ContentTextView, canvas: Canvas) {
        val textPaint = if (isTitle) {
            ChapterProvider.titlePaint
        } else {
            ChapterProvider.contentPaint
        }
        val textColor = if (isReadAloud) {
            ReadBookConfig.textAccentColor
        } else if (isTitle) {
            ReadBookConfig.resolvedTitleColor
        } else {
            ReadBookConfig.textColor
        }
        if (textPaint.color != textColor) {
            textPaint.color = textColor
        }
        val paint = PaintPool.obtain()
        paint.set(textPaint)
        val letterSpacing = paint.letterSpacing * paint.textSize
        val letterSpacingHalf = letterSpacing * 0.5f
        if (extraLetterSpacing != 0f) {
            paint.letterSpacing += extraLetterSpacing
        }
        if (wordSpacing != 0f) {
            paint.wordSpacing = wordSpacing
        }
        val offsetX = if (atLeastApi35) letterSpacingHalf else extraLetterSpacingOffsetX
        canvas.drawText(text, indentSize, text.length, startX + offsetX, lineBase - lineTop, paint)
        PaintPool.recycle(paint)
        for (i in columns.indices) {
            val column = columns[i] as TextColumn
            if (column.selected) {
                canvas.drawRect(column.start, 0f, column.end, height, view.selectedPaint)
            }
        }
    }

    fun checkFastDraw(): Boolean {
        if (!AppConfig.optimizeRender || exceed || !onlyTextColumn || textPage.isMsgPage) {
            return false
        }
        if (wordSpacing != 0f && (!atLeastApi26 || !wordSpacingWorking)) {
            return false
        }
        if (hasReadStyle) return false
        if (titleTextSize != null) return false
        return searchResultColumnCount == 0
    }

    fun invalidate() {
        invalidateSelf()
        textPage.invalidate()
    }

    fun invalidateSelf() {
        canvasRecorder.invalidate()
    }

    fun recycleRecorder() {
        canvasRecorder.recycle()
    }

    @SuppressLint("NewApi")
    companion object {
        val emptyTextLine = TextLine()
        private val atLeastApi26 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
        val atLeastApi28 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
        private val atLeastApi35 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        private val highlightBitmapCache = LruCache<String, Bitmap>(16)
        private val wordSpacingWorking by lazy {
            // issue 3785 3846
            val paint = PaintPool.obtain()
            val text = "一二 三"
            val width1 = paint.measureText(text)
            try {
                paint.wordSpacing = 10f
                val width2 = paint.measureText(text)
                width2 - width1 == 10f
            } catch (e: NoSuchMethodError) {
                false
            } finally {
                PaintPool.recycle(paint)
            }
        }

        private fun getHighlightBitmap(path: String): Bitmap? {
            if (path.isBlank()) return null
            highlightBitmapCache.get(path)?.let { return it }
            val bitmap = runCatching {
                when {
                    path.startsWith("assets://") -> appCtx.assets.open(path.removePrefix("assets://"))
                        .use(BitmapFactory::decodeStream)
                    path.startsWith("content://") -> appCtx.contentResolver.openInputStream(
                        android.net.Uri.parse(path)
                    )?.use(BitmapFactory::decodeStream)
                    else -> File(path).takeIf(File::isFile)?.let { BitmapFactory.decodeFile(it.absolutePath) }
                }
            }.getOrNull() ?: return null
            highlightBitmapCache.put(path, bitmap)
            return bitmap
        }
    }

}
