package io.legado.app.ui.book.read.page

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.graphics.drawable.DrawableCompat
import io.legado.app.R
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.book.isOnLineTxt
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.association.OpenUrlConfirmActivity
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.column.BaseColumn
import io.legado.app.ui.book.read.page.entities.column.ButtonColumn
import io.legado.app.ui.book.read.page.entities.column.TextHtmlColumn
import io.legado.app.ui.book.read.page.entities.column.ImageColumn
import io.legado.app.ui.book.read.page.entities.column.ReviewColumn
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.entities.column.TextColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.activity
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * 阅读内容视图
 */
class ContentTextView(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    var selectAble = AppConfig.textSelectAble
    val selectedPaint by lazy {
        Paint().apply {
            // 选区仍需可辨，但不能遮住正在预览的正文划线颜色。
            color = ColorUtils.withAlpha(
                context.getCompatColor(R.color.btn_bg_press_2),
                SELECTION_OVERLAY_ALPHA,
            )
            style = Paint.Style.FILL
        }
    }
    private var callBack: CallBack
    private val visibleRect = ChapterProvider.visibleRect
    val selectStart = TextPos(0, -1, -1)
    private val selectEnd = TextPos(0, -1, -1)
    private var textHighlights: List<Bookmark> = emptyList()
    private var textHighlightNotesByEndChapter: Map<Int, List<Bookmark>> = emptyMap()
    private val textHighlightNoteMarkers = mutableListOf<TextHighlightNoteMarker>()
    private val textHighlightNoteMarkerDrawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.ic_ai_chat_suggestion)
            ?.let { DrawableCompat.wrap(it) }
            ?.mutate()
    }
    var textPage: TextPage = TextPage()
        private set
    var isMainView = false
    var longScreenshot = false
    var reverseStartCursor = false
    var reverseEndCursor = false

    //滚动参数
    private val pageFactory get() = callBack.pageFactory
    private val pageDelegate get() = callBack.pageDelegate
    private var pageOffset = 0
    private var autoPager: AutoPager? = null
    private var isScroll = false
    private val renderRunnable by lazy { Runnable { preRenderPage() } }
    private var lastClickTime = 0L
    private var doubleClick = false

    //绘制图片的paint
    val imagePaint by lazy {
        Paint().apply {
            isAntiAlias = AppConfig.useAntiAlias
        }
    }

    init {
        callBack = activity as CallBack
    }

    /**
     * 设置内容
     */
    fun setContent(textPage: TextPage) {
        this.textPage = textPage
        textHighlightNoteMarkers.clear()
        // 非滑动翻页动画需要同步重绘，不然翻页可能会出现闪烁
        if (isScroll) {
            postInvalidate()
        } else {
            invalidate()
        }
    }

    fun setTextHighlights(bookmarks: List<Bookmark>) {
        textHighlights = bookmarks.filter(Bookmark::isTextHighlight)
        textHighlightNotesByEndChapter = textHighlights
            .filter { it.content.isNotBlank() }
            .groupBy(Bookmark::endChapterIndex)
            .mapValues { (_, highlights) -> highlights.sortedBy(Bookmark::time) }
        textHighlightNoteMarkers.clear()
        postInvalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (!isMainView) return
        ChapterProvider.upViewSize(w, h)
        textPage.format()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        autoPager?.onDraw(canvas)
        if (longScreenshot) {
            canvas.translate(0f, scrollY.toFloat())
        }
        check(!visibleRect.isEmpty) { "visibleRect 为空" }
        canvas.withSave {
            clipRect(visibleRect)
            drawPage(this)
        }
        drawTextHighlightNoteMarkerIcons(canvas)
    }

    /**
     * 绘制页面
     */
    private fun drawPage(canvas: Canvas) {
        textHighlightNoteMarkers.clear()
        var relativeOffset = relativeOffset(0)
        drawPageWithHighlights(canvas, textPage, relativeOffset)
        if (!callBack.isScroll) return
        //滚动翻页
        if (!pageFactory.hasNext()) return
        val textPage1 = relativePage(1)
        relativeOffset += textPage.height
        drawPageWithHighlights(canvas, textPage1, relativeOffset)
        if (!pageFactory.hasNextPlus()) return
        relativeOffset += textPage1.height
        if (relativeOffset < ChapterProvider.visibleHeight) {
            val textPage2 = relativePage(2)
            drawPageWithHighlights(canvas, textPage2, relativeOffset)
        }
    }

    private fun drawPageWithHighlights(
        canvas: Canvas,
        page: TextPage,
        relativeOffset: Float,
    ) {
        collectTextHighlightNoteMarkers(page, relativeOffset)
        drawTextHighlights(canvas, page, relativeOffset, backgroundPass = true)
        page.draw(this, canvas, relativeOffset)
        drawTextHighlights(canvas, page, relativeOffset, backgroundPass = false)
    }

    private fun drawTextHighlights(
        canvas: Canvas,
        page: TextPage,
        relativeOffset: Float,
        backgroundPass: Boolean,
    ) {
        val pageHighlights = textHighlights
            .filter { it.coversChapter(page.chapterIndex) }
            .filter {
                (it.highlightStyle == Bookmark.STYLE_BACKGROUND) == backgroundPass
            }
            .sortedBy(Bookmark::time)
        if (pageHighlights.isEmpty()) return
        canvas.withTranslation(0f, relativeOffset) {
            page.lines.forEach { line ->
                val columns = line.columns.filterIsInstance<TextBaseColumn>()
                if (columns.isEmpty()) return@forEach
                pageHighlights.forEach { highlight ->
                    var chapterPosition = line.chapterPosition
                    var segmentStart: Float? = null
                    var segmentEnd = 0f
                    columns.forEach { column ->
                        val marked = highlight.containsChapterPosition(
                            page.chapterIndex,
                            chapterPosition,
                        )
                        chapterPosition += column.charData.length
                        if (marked) {
                            if (segmentStart == null) segmentStart = column.start
                            segmentEnd = column.end
                        } else if (segmentStart != null) {
                            drawTextHighlightSegment(
                                canvas,
                                line,
                                segmentStart!!,
                                segmentEnd,
                                highlight,
                            )
                            segmentStart = null
                        }
                    }
                    segmentStart?.let { start ->
                        drawTextHighlightSegment(canvas, line, start, segmentEnd, highlight)
                    }
                }
            }
        }
    }

    private fun drawTextHighlightSegment(
        canvas: Canvas,
        line: TextLine,
        start: Float,
        end: Float,
        highlight: Bookmark,
    ) {
        if (end <= start) return
        var segmentStart = start
        var inlineOffset = 0f
        textHighlightNoteSpacerPositions(line).forEach { spacerX ->
            if (spacerX <= segmentStart) {
                inlineOffset += textHighlightNoteSpacerWidthPx
            } else if (spacerX < end) {
                drawTextHighlightSegmentWithoutSpacers(
                    canvas = canvas,
                    line = line,
                    start = segmentStart + inlineOffset,
                    end = spacerX + inlineOffset,
                    highlight = highlight,
                )
                segmentStart = spacerX
                inlineOffset += textHighlightNoteSpacerWidthPx
            }
        }
        drawTextHighlightSegmentWithoutSpacers(
            canvas = canvas,
            line = line,
            start = segmentStart + inlineOffset,
            end = end + inlineOffset,
            highlight = highlight,
        )
    }

    private fun drawTextHighlightSegmentWithoutSpacers(
        canvas: Canvas,
        line: TextLine,
        start: Float,
        end: Float,
        highlight: Bookmark,
    ) {
        if (end <= start) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = highlight.highlightColor
        }
        when (highlight.highlightStyle) {
            Bookmark.STYLE_UNDERLINE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 2.dpToPx().toFloat()
                val y = line.lineBottom - line.lineTop - 1.dpToPx()
                canvas.drawLine(start, line.lineTop + y, end, line.lineTop + y, paint)
            }

            Bookmark.STYLE_WAVY_UNDERLINE -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1.8f.dpToPx()
                val y = line.lineBottom - line.lineTop - 1.dpToPx()
                drawTextHighlightWave(canvas, paint, start, end, line.lineTop + y)
            }

            else -> {
                paint.style = Paint.Style.FILL
                paint.color = (highlight.highlightColor and 0x00FFFFFF) or (0x48 shl 24)
                val rect = RectF(
                    start,
                    line.lineTop + 1.dpToPx(),
                    end,
                    line.lineBottom - 1.dpToPx(),
                )
                canvas.drawRoundRect(rect, 2.dpToPx().toFloat(), 2.dpToPx().toFloat(), paint)
            }
        }
    }

    private fun collectTextHighlightNoteMarkers(
        page: TextPage,
        relativeOffset: Float,
    ) {
        val highlights = textHighlightNotesByEndChapter[page.chapterIndex].orEmpty()
        if (highlights.isEmpty()) return
        val markerSize = NOTE_MARKER_SIZE_DP.dpToPx().toFloat()
        val markerGap = NOTE_MARKER_GAP_DP.dpToPx().toFloat()
        val touchSize = NOTE_MARKER_TOUCH_SIZE_DP.dpToPx().toFloat()
        highlights.forEach { highlight ->
            val endpoint = findTextHighlightEndpoint(page, highlight.endChapterPos)
                ?: return@forEach
            // 备注标记属于划线终点装饰，不能为了避让正文被挪到整行末尾或页边。
            val inlineOffset = textHighlightNoteSpacerPositions(endpoint.line)
                .count { it < endpoint.x } * textHighlightNoteSpacerWidthPx
            val markerLeft = endpoint.x + inlineOffset + markerGap
            val markerTop = (endpoint.line.lineBottom - markerSize)
                .coerceAtLeast(endpoint.line.lineTop) + relativeOffset
            val markerRect = RectF(
                markerLeft,
                markerTop,
                markerLeft + markerSize,
                markerTop + markerSize,
            )
            val markerCenterX = markerRect.centerX()
            val markerCenterY = markerRect.centerY()
            val hitRect = RectF(
                markerCenterX - touchSize / 2,
                markerCenterY - touchSize / 2,
                markerCenterX + touchSize / 2,
                markerCenterY + touchSize / 2,
            )
            if (
                hitRect.intersect(
                    0f,
                    visibleRect.top.coerceAtLeast(0f),
                    width.toFloat(),
                    visibleRect.bottom.coerceAtMost(height.toFloat()),
                )
            ) {
                textHighlightNoteMarkers += TextHighlightNoteMarker(
                    bookmark = highlight,
                    markerRect = markerRect,
                    hitRect = hitRect,
                    anchorX = markerCenterX + callBack.imgBgPaddingStart,
                    anchorTop = endpoint.line.lineTop + relativeOffset + callBack.headerHeight,
                    anchorBottom = endpoint.line.lineBottom + relativeOffset + callBack.headerHeight,
                )
            }
        }
    }

    private fun drawTextHighlightNoteMarkerIcons(canvas: Canvas) {
        if (textHighlightNoteMarkers.isEmpty()) return
        val drawable = textHighlightNoteMarkerDrawable ?: return
        val markerColor = if (AppConfig.isEInkMode) {
            ReadBookConfig.textColor
        } else {
            ColorUtils.withAlpha(ReadBookConfig.textAccentColor, NOTE_MARKER_ALPHA)
        }
        DrawableCompat.setTint(drawable, markerColor)
        canvas.withSave {
            clipRect(
                0f,
                visibleRect.top.coerceAtLeast(0f),
                width.toFloat(),
                visibleRect.bottom.coerceAtMost(height.toFloat()),
            )
            textHighlightNoteMarkers.forEach { marker ->
                val rect = marker.markerRect
                drawable.setBounds(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt(),
                )
                drawable.draw(this)
            }
        }
    }

    private fun findTextHighlightEndpoint(
        page: TextPage,
        endChapterPosition: Int,
    ): TextHighlightEndpoint? {
        for (line in page.lines) {
            val endpointX = textHighlightNoteEndpointX(line, endChapterPosition) ?: continue
            return TextHighlightEndpoint(line = line, x = endpointX)
        }
        return null
    }

    internal fun hasTextHighlightNoteSpacers(page: TextPage): Boolean {
        if (textHighlightNotesByEndChapter[page.chapterIndex].isNullOrEmpty()) return false
        return page.lines.any { textHighlightNoteSpacerPositions(it).isNotEmpty() }
    }

    internal fun textHighlightNoteSpacerPositions(line: TextLine): List<Float> {
        val highlights = textHighlightNotesByEndChapter[line.textPage.chapterIndex].orEmpty()
        if (highlights.isEmpty()) return emptyList()
        return highlights
            .mapNotNull { textHighlightNoteEndpointX(line, it.endChapterPos) }
            .distinct()
            .sorted()
    }

    internal val textHighlightNoteSpacerWidthPx: Float
        get() = (
            NOTE_MARKER_GAP_DP + NOTE_MARKER_SIZE_DP + NOTE_MARKER_TRAILING_GAP_DP
        ).dpToPx().toFloat()

    private fun textHighlightNoteEndpointX(
        line: TextLine,
        endChapterPosition: Int,
    ): Float? {
        var chapterPosition = line.chapterPosition
        for (column in line.columns) {
            if (column !is TextBaseColumn) continue
            val columnEndPosition = chapterPosition + column.charData.length
            if (
                endChapterPosition > chapterPosition &&
                endChapterPosition <= columnEndPosition
            ) {
                return column.end
            }
            chapterPosition = columnEndPosition
        }
        return null
    }

    private fun drawTextHighlightWave(
        canvas: Canvas,
        paint: Paint,
        start: Float,
        end: Float,
        y: Float,
    ) {
        val waveLength = 6.dpToPx().toFloat()
        val amplitude = 1.8f.dpToPx()
        val path = Path().apply { moveTo(start, y) }
        var x = start
        var upwards = true
        while (x < end) {
            val next = (x + waveLength).coerceAtMost(end)
            path.quadTo(
                (x + next) / 2,
                y + if (upwards) -amplitude else amplitude,
                next,
                y,
            )
            upwards = !upwards
            x = next
        }
        canvas.drawPath(path, paint)
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
        autoPager?.computeOffset()
    }

    /**
     * 滚动事件
     * pageOffset 向上滚动 减小 向下滚动 增大
     * pageOffset 范围 0 ~ -textPage.height 大于0为上一页，小于-textPage.height为下一页
     * 以内容显示区域顶端为界，pageOffset的绝对值为textPage上方的高度
     * pageOffset + textPage.height 为 textPage 下方的高度
     */
    fun scroll(mOffset: Int) {
        pageOffset += mOffset
        if (longScreenshot) {
            scrollY += -mOffset
        }
        if (!pageFactory.hasPrev() && pageOffset > 0) {
            pageOffset = 0
            pageDelegate?.abortAnim()
        } else if (!pageFactory.hasNext()
            && pageOffset < 0
            && pageOffset + textPage.height < ChapterProvider.visibleHeight
        ) {
            val offset = (ChapterProvider.visibleHeight - textPage.height).toInt()
            pageOffset = min(0, offset)
            pageDelegate?.abortAnim()
        } else if (pageOffset > 0) {
            if (pageFactory.moveToPrev(true)) {
                pageOffset -= textPage.height.toInt()
            } else {
                pageOffset = 0
                pageDelegate?.abortAnim()
            }
        } else if (pageOffset < -textPage.height) {
            val height = textPage.height
            if (pageFactory.moveToNext(upContent = true)) {
                pageOffset += height.toInt()
            } else {
                pageOffset = -height.toInt()
                pageDelegate?.abortAnim()
            }
        }
        postInvalidate()
    }

    fun submitRenderTask() {
        renderThread.submit(renderRunnable)
    }

    private fun preRenderPage() {
        val view = this
        var invalidate = false
        pageFactory.run {
            if (hasPrev() && prevPage.render(view)) {
                invalidate = true
            }
            if (curPage.render(view)) {
                invalidate = true
            }
            if (hasNext() && nextPage.render(view) && callBack.isScroll) {
                invalidate = true
            }
            if (hasNextPlus() && nextPlusPage.render(view) && callBack.isScroll
                && relativeOffset(2) < ChapterProvider.visibleHeight
            ) {
                invalidate = true
            }
            if (invalidate) {
                postInvalidate()
                pageDelegate?.postInvalidate()
            }
        }
    }

    /**
     * 重置滚动位置
     */
    fun resetPageOffset() {
        pageOffset = 0
    }

    /**
     * 长按
     */
    fun longPress(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touch(x, y) { _, textPos, _, _, column ->
            when (column) {
                is ImageColumn -> callBack.onImageLongPress(x, y, column.src)
                is TextColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
                is TextHtmlColumn -> {
                    if (!selectAble) return@touch
                    column.selected = true
                    select(textPos)
                }
            }
        }
    }

    /**
     * 单击
     * @return true:已处理, false:未处理
     */
    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    fun click(x: Float, y: Float): Boolean {
        val currentTime = System.currentTimeMillis()
        val debounceClick = currentTime - lastClickTime < 300L //300毫秒防抖和双击
        lastClickTime = currentTime
        doubleClick = if (debounceClick) {
            !doubleClick
        } else {
            false
        }
        findTextHighlightNoteMarker(x, y)?.let { marker ->
            callBack.onTextHighlightClick(
                marker.bookmark,
                marker.anchorX,
                marker.anchorTop,
                marker.anchorBottom,
            )
            return true
        }
        var handled = false
        touch(x, y) { relativeOffset, textPos, textPage, textLine, column ->
            if (column is TextBaseColumn) {
                val chapterPosition = columnChapterPosition(textLine, column)
                val highlight = textHighlights
                    .asSequence()
                    .filter {
                        it.containsChapterPosition(textPage.chapterIndex, chapterPosition)
                    }
                    .maxByOrNull(Bookmark::time)
                if (highlight != null) {
                    callBack.onTextHighlightClick(
                        highlight,
                        x + callBack.imgBgPaddingStart,
                        textLine.lineTop + relativeOffset + callBack.headerHeight,
                        textLine.lineBottom + relativeOffset + callBack.headerHeight,
                    )
                    handled = true
                    return@touch
                }
            }
            when (column) {
                is ButtonColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }

                is ReviewColumn -> {
                    context.toastOnUi("Button Pressed!")
                    handled = true
                }

                is ImageColumn -> when (AppConfig.clickImgWay) {
                    "1" -> { //预览图片
                        activity?.showDialogFragment(PhotoDialog(column.src, isBook = true))
                        handled = true
                    }
                    "2" -> { //兼容处理
                        if (!debounceClick) {
                            if (ReadBook.book?.isOnLineTxt == true) {
                                val click = column.click
                                val src = column.src
                                if (!click.isNullOrBlank()) {
                                    callBack.clickImg(click, src)
                                    handled = true
                                } else {
                                    handled = callBack.oldClickImg(src)
                                }
                            }
                        }
                    }
                    "3" -> { //关闭
                        handled = false
                    }
                    "4" -> { //双击
                        if (doubleClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        } else {
                            handled = true
                        }
                    }
                    else -> { //默认点击
                        if (!debounceClick) {
                            val click = column.click
                            if (!click.isNullOrBlank()) {
                                callBack.clickImg(click, column.src)
                                handled = true
                            }
                        }
                    }
                }
                is TextHtmlColumn -> {
                    column.linkUrl?.let {
                        activity?.startActivity<OpenUrlConfirmActivity> {
                            putExtra("uri", it)
                        }
                        handled = true
                    }
                }
            }
        }
        return handled
    }

    private fun findTextHighlightNoteMarker(x: Float, y: Float): TextHighlightNoteMarker? {
        var match: TextHighlightNoteMarker? = null
        var nearestDistance = Float.MAX_VALUE
        textHighlightNoteMarkers.forEach { marker ->
            if (!marker.hitRect.contains(x, y)) return@forEach
            val deltaX = x - marker.markerRect.centerX()
            val deltaY = y - marker.markerRect.centerY()
            val distance = deltaX * deltaX + deltaY * deltaY
            if (
                distance < nearestDistance ||
                distance == nearestDistance && marker.bookmark.time > (match?.bookmark?.time ?: 0L)
            ) {
                match = marker
                nearestDistance = distance
            }
        }
        return match
    }

    private fun columnChapterPosition(textLine: TextLine, target: TextBaseColumn): Int {
        var position = textLine.chapterPosition
        textLine.columns.forEach { column ->
            if (column === target) return position
            if (column is TextBaseColumn) position += column.charData.length
        }
        return position
    }

    /**
     * 选择文字
     */
    fun selectText(
        x: Float,
        y: Float,
        select: (textPos: TextPos) -> Unit,
    ) {
        touchRough(x, y) { _, textPos, _, _, column ->
            if (column is TextBaseColumn) {
                column.selected = true
                select(textPos)
            }
        }
    }

    /**
     * 开始选择符移动
     */
    fun selectStartMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (selectStart.compare(textPos) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectEnd) <= 0) {
                selectStartMoveIndex(textPos)
            } else {
                touchRough(x - 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectEnd) > 0) {
                        reverseStartCursor = true
                        reverseEndCursor = false
                        selectEnd.columnIndex++
                        selectStartMoveIndex(selectEnd)
                        selectEndMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 结束选择符移动
     */
    fun selectEndMove(x: Float, y: Float) {
        touchRough(x, y) { _, textPos, _, _, _ ->
            if (textPos.compare(selectEnd) == 0) {
                return@touchRough
            }
            if (textPos.compare(selectStart) >= 0) {
                selectEndMoveIndex(textPos)
            } else {
                touchRough(x + 2 * cursorWidth, y) { _, textPos, _, _, _ ->
                    if (textPos.compare(selectStart) < 0) {
                        reverseEndCursor = true
                        reverseStartCursor = false
                        selectStart.columnIndex--
                        selectEndMoveIndex(selectStart)
                        selectStartMoveIndex(textPos)
                    }
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * @param touched 回调
     */
    private fun touch(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        if (!visibleRect.contains(x, y)) return
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                if (!textLine.isTouchY(y, relativeOffset)) continue
                val textX = sourceTextX(textLine, x) ?: return
                if (textLine.isTouch(textX, y, relativeOffset)) {
                    for ((charIndex, textColumn) in textLine.columns.withIndex()) {
                        if (textColumn.isTouch(textX)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    return
                }
            }
        }
    }

    /**
     * 触碰位置信息
     * 文本选择专用
     * @param touched 回调
     */
    private fun touchRough(
        x: Float,
        y: Float,
        touched: (
            relativeOffset: Float,
            textPos: TextPos,
            textPage: TextPage,
            textLine: TextLine,
            column: BaseColumn
        ) -> Unit
    ) {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) return
                if (relativeOffset >= ChapterProvider.visibleHeight) return
            }
            val textPage = relativePage(relativePos)
            for (lineIndex in textPage.lines.indices) {
                val textLine = textPage.getLine(lineIndex)
                if (textLine.isTouchY(y, relativeOffset)) {
                    if (textPage.doublePage) {
                        val halfWidth = width / 2
                        if (textLine.isLeftLine && x > halfWidth) {
                            continue
                        }
                        if (!textLine.isLeftLine && x < halfWidth) {
                            continue
                        }
                    }
                    val columns = textLine.columns
                    val textX = sourceTextX(textLine, x, snapSpacerToPrevious = true)
                        ?: continue
                    for (charIndex in columns.indices) {
                        val textColumn = columns[charIndex]
                        if (textColumn.isTouch(textX)) {
                            touched.invoke(
                                relativeOffset,
                                TextPos(relativePos, lineIndex, charIndex),
                                textPage, textLine, textColumn
                            )
                            return
                        }
                    }
                    val isLast = columns.first().start < textX
                    val charIndex = if (isLast) columns.lastIndex + 1 else -1
                    val textColumn = if (isLast) columns.last() else columns.first()
                    touched.invoke(
                        relativeOffset,
                        TextPos(relativePos, lineIndex, charIndex),
                        textPage, textLine, textColumn
                    )
                    return
                }
            }
        }
    }

    private fun sourceTextX(
        line: TextLine,
        visualX: Float,
        snapSpacerToPrevious: Boolean = false,
    ): Float? {
        var inlineOffset = 0f
        textHighlightNoteSpacerPositions(line).forEach { spacerX ->
            val spacerStart = spacerX + inlineOffset
            val spacerEnd = spacerStart + textHighlightNoteSpacerWidthPx
            when {
                visualX < spacerStart -> return visualX - inlineOffset
                visualX <= spacerEnd -> {
                    return if (snapSpacerToPrevious) spacerX - 0.5f else null
                }
            }
            inlineOffset += textHighlightNoteSpacerWidthPx
        }
        return visualX - inlineOffset
    }

    private fun decoratedTextX(
        line: TextLine,
        sourceX: Float,
        includeSpacerAtPosition: Boolean,
    ): Float {
        val spacerCount = textHighlightNoteSpacerPositions(line).count { spacerX ->
            spacerX < sourceX || includeSpacerAtPosition && spacerX == sourceX
        }
        return sourceX + spacerCount * textHighlightNoteSpacerWidthPx
    }

    fun getCurVisiblePage(): TextPage {
        val visiblePage = TextPage()
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    visiblePage.addLine(visibleLine)
                }
            }
        }
        return visiblePage
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        var relativeOffset: Float
        for (relativePos in 0..2) {
            relativeOffset = relativeOffset(relativePos)
            if (relativePos > 0) {
                //滚动翻页
                if (!callBack.isScroll) break
                if (relativeOffset >= ChapterProvider.visibleHeight) break
            }
            val textPage = relativePage(relativePos)
            val lines = textPage.lines
            for (i in lines.indices) {
                val textLine = lines[i]
                if (textLine.isVisible(relativeOffset)) {
                    val visibleLine = textLine.copy().apply {
                        lineTop += relativeOffset
                        lineBottom += relativeOffset
                    }
                    return textPage.chapterIndex to visibleLine
                }
            }
        }
        return null
    }

    /**
     * 选择开始文字
     */
    fun selectStartMoveIndex(
        relativePagePos: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectStart.relativePagePos = relativePagePos
        selectStart.lineIndex = lineIndex
        selectStart.columnIndex = max(0, charIndex)
        val textLine = relativePage(relativePagePos).getLine(lineIndex)
        val textColumn = textLine.getColumn(charIndex)
        val sourceX = if (charIndex < textLine.columns.size) {
            textColumn.start
        } else {
            textColumn.end
        }
        upSelectedStart(
            decoratedTextX(
                line = textLine,
                sourceX = sourceX,
                includeSpacerAtPosition = true,
            ),
            textLine.lineBottom + relativeOffset(relativePagePos),
            textLine.lineTop + relativeOffset(relativePagePos)
        )
        upSelectChars()
    }

    fun selectStartMoveIndex(textPos: TextPos) = textPos.run {
        selectStartMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    /**
     * 选择结束文字
     */
    fun selectEndMoveIndex(
        relativePage: Int,
        lineIndex: Int,
        charIndex: Int,
    ) {
        selectEnd.relativePagePos = relativePage
        selectEnd.lineIndex = lineIndex
        val textLine = relativePage(relativePage).getLine(lineIndex)
        selectEnd.columnIndex = min(charIndex, textLine.columns.lastIndex)
        val textColumn = textLine.getColumn(charIndex)
        val sourceX = if (charIndex > -1) textColumn.end else textColumn.start
        upSelectedEnd(
            decoratedTextX(
                line = textLine,
                sourceX = sourceX,
                includeSpacerAtPosition = false,
            ),
            textLine.lineBottom + relativeOffset(relativePage)
        )
        upSelectChars()
    }

    fun selectEndMoveIndex(textPos: TextPos) = textPos.run {
        selectEndMoveIndex(relativePagePos, lineIndex, columnIndex)
    }

    private fun upSelectChars() {
        if (!selectStart.isSelected() && !selectEnd.isSelected()) {
            return
        }
        val last = if (callBack.isScroll) 2 else 0
        val textPos = TextPos(0, 0, 0)
        for (relativePos in 0..last) {
            textPos.relativePagePos = relativePos
            val textPage = relativePage(relativePos)
            for ((lineIndex, textLine) in textPage.lines.withIndex()) {
                textPos.lineIndex = lineIndex
                for ((charIndex, column) in textLine.columns.withIndex()) {
                    textPos.columnIndex = charIndex
                    if (column is TextBaseColumn) {
                        val compareStart = textPos.compare(selectStart)
                        val compareEnd = textPos.compare(selectEnd)
                        column.selected = compareStart >= 0 && compareEnd <= 0
                        column.isSearchResult =
                            column.selected && callBack.isSelectingSearchResult
                        if (column.isSearchResult) {
                            textPage.searchResult.add(column)
                        }
                    }
                }
            }
        }
        postInvalidate()
    }

    private fun upSelectedStart(x: Float, y: Float, top: Float) {
        callBack.run {
            upSelectedStart(x + imgBgPaddingStart, y + headerHeight, top + headerHeight)
        }
    }

    private fun upSelectedEnd(x: Float, y: Float) {
        callBack.run {
            upSelectedEnd(x + imgBgPaddingStart, y + headerHeight)
        }
    }

    fun resetReverseCursor() {
        reverseStartCursor = false
        reverseEndCursor = false
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        val last = if (callBack.isScroll) 2 else 0
        for (relativePos in 0..last) {
            val textPage = relativePage(relativePos)
            textPage.lines.forEach { textLine ->
                textLine.columns.forEach {
                    if (it is TextBaseColumn) {
                        it.selected = false
                        if (clearSearchResult) {
                            it.isSearchResult = false
                            textPage.searchResult.remove(it)
                        }
                    }
                }
            }
        }
        selectStart.reset()
        selectEnd.reset()
        postInvalidate()
        callBack.onCancelSelect()
    }

    fun getSelectedText(): String {
        val textPos = TextPos(0, 0, 0)
        val builder = StringBuilder()
        for (relativePos in selectStart.relativePagePos..selectEnd.relativePagePos) {
            val textPage = relativePage(relativePos)
            textPos.relativePagePos = relativePos
            textPage.lines.forEachIndexed { lineIndex, textLine ->
                textPos.lineIndex = lineIndex
                textLine.columns.forEachIndexed { charIndex, column ->
                    textPos.columnIndex = charIndex
                    val compareStart = textPos.compare(selectStart)
                    val compareEnd = textPos.compare(selectEnd)
                    if (column is TextBaseColumn) {
                        when {
                            compareStart == -1 -> if (
                                selectStart.columnIndex == textLine.columns.size
                                && charIndex == textLine.columns.lastIndex
                            ) {
                                builder.append("\n")
                            }

                            compareEnd == 1 -> if (selectEnd.columnIndex == -1 && charIndex == 0) {
                                builder.append("\n")
                            }

                            compareStart >= 0 && compareEnd <= 0 -> {
                                builder.append(column.charData)
                                if (
                                    textLine.isParagraphEnd
                                    && charIndex == textLine.columns.lastIndex
                                    && compareEnd != 0
                                ) {
                                    builder.append("\n")
                                }
                            }
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    fun createBookmark(): Bookmark? {
        val page = relativePage(selectStart.relativePagePos)
        page.getTextChapter().let { chapter ->
            ReadBook.book?.let { book ->
                return book.createBookMark().apply {
                    chapterIndex = page.chapterIndex
                    chapterPos = chapter.getReadLength(page.index) +
                            page.getPosByLineColumn(selectStart.lineIndex, selectStart.columnIndex)
                    chapterName = chapter.title
                    bookText = getSelectedText()
                }
            }
        }
        return null
    }

    fun createTextHighlight(): Bookmark? {
        if (!selectStart.isSelected() || !selectEnd.isSelected()) return null
        val startPage = relativePage(selectStart.relativePagePos)
        val endPage = relativePage(selectEnd.relativePagePos)
        val startPosition = selectionChapterPosition(startPage, selectStart, includeColumnEnd = false)
        val endPosition = selectionChapterPosition(endPage, selectEnd, includeColumnEnd = true)
        if (
            endPage.chapterIndex < startPage.chapterIndex ||
            endPage.chapterIndex == startPage.chapterIndex && endPosition <= startPosition
        ) {
            return null
        }
        val book = ReadBook.book ?: return null
        return book.createBookMark().apply {
            bookmarkType = Bookmark.TYPE_TEXT_HIGHLIGHT
            chapterIndex = startPage.chapterIndex
            chapterPos = startPosition
            chapterName = startPage.getTextChapter().title
            endChapterIndex = endPage.chapterIndex
            endChapterPos = endPosition
            highlightStyle = Bookmark.STYLE_BACKGROUND
            highlightColor = Bookmark.DEFAULT_HIGHLIGHT_COLOR
            bookText = getSelectedText()
        }
    }

    private fun selectionChapterPosition(
        page: TextPage,
        textPos: TextPos,
        includeColumnEnd: Boolean,
    ): Int {
        val line = page.getLine(textPos.lineIndex)
        val targetIndex = textPos.columnIndex.coerceIn(0, line.columns.lastIndex)
        var position = line.chapterPosition
        line.columns.forEachIndexed { index, column ->
            if (index == targetIndex) {
                if (includeColumnEnd && column is TextBaseColumn) {
                    position += column.charData.length
                }
                return position
            }
            if (column is TextBaseColumn) position += column.charData.length
        }
        return position
    }

    private fun relativeOffset(relativePos: Int): Float {
        return when (relativePos) {
            0 -> pageOffset.toFloat()
            1 -> pageOffset + textPage.height
            else -> pageOffset + textPage.height + pageFactory.nextPage.height
        }
    }

    fun relativePage(relativePos: Int): TextPage {
        return when (relativePos) {
            0 -> textPage
            1 -> pageFactory.nextPage
            else -> pageFactory.nextPlusPage
        }
    }

    fun setAutoPager(autoPager: AutoPager?) {
        this.autoPager = autoPager
    }

    fun setIsScroll(value: Boolean) {
        isScroll = value
    }

    override fun canScrollVertically(direction: Int): Boolean {
        return callBack.isScroll && pageFactory.hasNext()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                longScreenshot = true
                scrollY = 0
            }

            MotionEvent.ACTION_UP -> {
                longScreenshot = false
                scrollY = 0
            }
        }
        return callBack.onLongScreenshotTouchEvent(event)
    }

    companion object {
        private val renderThread by lazy {
            Executors.newSingleThreadExecutor {
                Thread(it, "TextPageRender")
            }
        }
        private const val SELECTION_OVERLAY_ALPHA = 0.12f
        private const val NOTE_MARKER_SIZE_DP = 12
        private const val NOTE_MARKER_GAP_DP = 2
        private const val NOTE_MARKER_TRAILING_GAP_DP = 2
        private const val NOTE_MARKER_TOUCH_SIZE_DP = 24
        private const val NOTE_MARKER_ALPHA = 0.76f
        private val cursorWidth = 24.dpToPx()
    }

    private data class TextHighlightEndpoint(
        val line: TextLine,
        val x: Float,
    )

    private data class TextHighlightNoteMarker(
        val bookmark: Bookmark,
        val markerRect: RectF,
        val hitRect: RectF,
        val anchorX: Float,
        val anchorTop: Float,
        val anchorBottom: Float,
    )

    interface CallBack {
        val headerHeight: Int
        val imgBgPaddingStart: Int
        val pageFactory: TextPageFactory
        val pageDelegate: PageDelegate?
        val isScroll: Boolean
        var isSelectingSearchResult: Boolean
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onImageLongPress(x: Float, y: Float, src: String)
        fun onCancelSelect()
        fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean
        fun oldClickImg(src: String): Boolean
        fun clickImg(click: String, src: String)
        fun onTextHighlightClick(
            bookmark: Bookmark,
            anchorX: Float,
            top: Float,
            bottom: Float,
        )
    }
}
