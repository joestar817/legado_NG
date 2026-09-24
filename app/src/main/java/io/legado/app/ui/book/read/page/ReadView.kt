package io.legado.app.ui.book.read.page

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.widget.FrameLayout
import io.legado.app.R
import io.legado.app.constant.PageAnim
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.Bookmark
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.EpubLayoutPreferences
import io.legado.app.help.book.isEpub
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.ContentEditDialog
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.epub.EpubLayoutController
import io.legado.app.ui.book.read.epub.EpubStartupTiming
import io.legado.app.ui.book.read.createBookmark
import io.legado.app.ui.book.read.createTextHighlight
import io.legado.app.ui.book.read.page.api.DataSource
import io.legado.app.ui.book.read.page.api.ReaderContentEditTarget
import io.legado.app.ui.book.read.page.api.ReaderSelection
import io.legado.app.ui.book.read.page.api.ReaderSelectionSource
import io.legado.app.ui.book.read.page.api.readerWordBoundary
import io.legado.app.ui.book.read.page.delegate.CoverPageDelegate
import io.legado.app.ui.book.read.page.delegate.HorizontalPageDelegate
import io.legado.app.ui.book.read.page.delegate.NoAnimPageDelegate
import io.legado.app.ui.book.read.page.delegate.PageDelegate
import io.legado.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.legado.app.ui.book.read.page.delegate.SimulationPageDelegate
import io.legado.app.ui.book.read.page.delegate.SlidePageDelegate
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.entities.TextLine
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.entities.TextPos
import io.legado.app.ui.book.read.page.entities.column.TextBaseColumn
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.book.read.page.provider.NativeReaderSelectionSource
import io.legado.app.ui.book.read.page.provider.TextPageFactory
import io.legado.app.utils.activity
import io.legado.app.utils.invisible
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.throttle
import io.legado.app.utils.getPrefBoolean
import kotlin.math.abs

/**
 * 阅读视图
 */
class ReadView(context: Context, attrs: AttributeSet) :
    FrameLayout(context, attrs),
    DataSource, LayoutProgressListener {

    val callBack: CallBack get() = activity as CallBack
    var pageFactory: TextPageFactory = TextPageFactory(this)
    var pageDelegate: PageDelegate? = null
        private set(value) {
            field?.onDestroy()
            field = null
            field = value
            upContent()
        }
    override var isScroll = false
    val prevPage by lazy { PageView(context) }
    val curPage by lazy { PageView(context) }
    val nextPage by lazy { PageView(context) }
    private val nativeSelectionSource: ReaderSelectionSource by lazy { NativeReaderSelectionSource(this) }
    private val selectionSource: ReaderSelectionSource
        get() = epubLayout?.takeIf { it.active } ?: nativeSelectionSource
    private var epubLayout: EpubLayoutController? = null
    private var openingTiming = if (io.legado.app.BuildConfig.DEBUG) EpubStartupTiming("reader").also { it.mark("created") } else null
    private var textSelectAble = AppConfig.textSelectAble
    private var textHighlights: List<Bookmark> = emptyList()
    private val epubViewport = Rect()
    private var epubPointerSequence = false
    private var epubCover = false
    internal var externalPageSnapshots: Pair<Bitmap, Bitmap>? = null
        private set
    private var externalAnimationCommit: (() -> Unit)? = null
    private var externalAnimationFinishing = false

    private fun captureNativeFrame(): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
        // PageView contains native chrome only while the external document is active.
        curPage.draw(Canvas(it))
    }

    private fun animateLayoutFrames(before: Bitmap, after: Bitmap, direction: Int, commit: () -> Unit) {
        finishLayoutFrames()
        val delegate = pageDelegate as? HorizontalPageDelegate
        if (delegate == null) {
            before.recycle(); after.recycle(); commit()
            return
        }
        delegate.abortAnim()
        before.prepareToDraw()
        after.prepareToDraw()
        externalPageSnapshots = before to after
        externalAnimationCommit = commit
        externalAnimationFinishing = false
        if (autoPager.isRunning && !AppConfig.isEInkMode) { invalidate(); return }
        delegate.startExternalAnimation(if (direction > 0) PageDirection.NEXT else PageDirection.PREV, defaultAnimationSpeed)
        invalidate()
    }

    private fun finishLayoutFrames() {
        val frames = externalPageSnapshots ?: return
        externalAnimationCommit = null
        // Abort while the external-frame guard is still installed; never advance native pages.
        (pageDelegate as? HorizontalPageDelegate)?.abortAnim()
        externalPageSnapshots = null
        externalAnimationCommit = null
        externalAnimationFinishing = false
        (pageDelegate as? HorizontalPageDelegate)?.upRecorder()
        post { frames.first.recycle(); frames.second.recycle() }
        invalidate()
    }

    private fun completeLayoutFrames() {
        val commit = externalAnimationCommit ?: return
        externalAnimationCommit = null
        externalAnimationFinishing = true
        // Like native repeated taps, complete the current turn before accepting the next one.
        (pageDelegate as? HorizontalPageDelegate)?.abortAnim()
        commit()
        invalidate()
    }

    private fun bindEpubLayout() {
        if (ReadBook.book?.isEpub == true && epubLayout == null) {
            epubLayout = EpubLayoutController(this,
                blankTap = { x, y -> startX = x; startY = y; onSingleTapUp() },
                chapterTurn = { direction ->
                    when (direction) {
                        1 -> ReadBook.moveToNextChapter(true)
                        -1 -> ReadBook.moveToPrevChapter(upContent = true, toLast = false)
                        else -> {
                            val target = ReadBook.durChapterIndex + direction
                            if (target in 0 until ReadBook.chapterSize) ReadBook.openChapter(target)
                        }
                    }
                },
                reportPage = { index, count, cover ->
                    curPage.setLayoutPageLabel(index, count)
                    setEpubViewportFull(cover)
                    if (count > 0) { openingTiming?.mark("epub-ready"); openingTiming = null }
                },
                requestViewport = ::setEpubViewportFull,
                reportError = { message -> context.longToastOnUi(message) },
                nativeFrame = ::captureNativeFrame,
                animate = ::animateLayoutFrames,
                finishAnimation = ::finishLayoutFrames,
                completeAnimation = ::completeLayoutFrames,
                animationsEnabled = { autoPager.isRunning && !AppConfig.isEInkMode ||
                    pageDelegate is HorizontalPageDelegate && pageDelegate !is NoAnimPageDelegate },
                autoPaging = { autoPager.isRunning },
                isScroll = { isScroll },
                publisherStyle = { EpubLayoutPreferences.read(ReadBook.book?.bookUrl).getValue(EpubLayoutPreferences.PUBLISHER) },
                position = { ReadBook.durChapterPos },
                neighboringChapters = { listOfNotNull(ReadBook.prevTextChapter, ReadBook.curTextChapter, ReadBook.nextTextChapter) },
                commitPosition = { chapter, position ->
                    when (chapter - ReadBook.durChapterIndex) {
                        0 -> if (position != ReadBook.durChapterPos) ReadBook.commitContentPosition(position)
                        1 -> ReadBook.moveToNextChapter(true, restartReadAloud = !isScroll, startPosition = position)
                        -1 -> ReadBook.moveToPrevChapter(true, restartReadAloud = !isScroll, startPosition = position)
                    }
                    upProgress()
                },
                selectionChanged = { value, finished ->
                    isTextSelected = value != null
                    if (value == null) callBack.onCancelSelect()
                    else {
                        val scale = resources.displayMetrics.density
                        val left = value.getInt("leftPx")
                        val top = value.getInt("topPx")
                        val start = value.getJSONObject("start")
                        val end = value.getJSONObject("end")
                        callBack.upSelectedStart(left + start.getDouble("left").toFloat() * scale,
                            top + start.getDouble("bottom").toFloat() * scale, top + start.getDouble("top").toFloat() * scale)
                        callBack.upSelectedEnd(left + end.getDouble("right").toFloat() * scale,
                            top + end.getDouble("bottom").toFloat() * scale)
                        if (finished) callBack.showTextActionMenu()
                    }
                },
                highlightClick = { bookmark, x, top, bottom ->
                    callBack.onTextHighlightClick(bookmark, x, top, bottom)
                },
                imageLongPress = { x, y, src -> (activity as? ReadBookActivity)?.onImageLongPress(x, y, src) },
                openingPreparation = { (activity as? ReadBookActivity)?.epubOpeningPreparation },
                openingPreview = { (activity as? ReadBookActivity)?.epubOpeningPreview },
            )
        }
        epubLayout?.bind(ReadBook.book, currentChapter)
        epubLayout?.setSelectionEnabled(textSelectAble)
        epubLayout?.setTextHighlights(textHighlights)
        epubLayout?.syncAloudHighlight()
        if (epubLayout?.active != true) { epubCover = false; curPage.setLayoutPageLabel(0, 0) }
        curPage.contentViewport.visibility = if (epubLayout?.active == true) INVISIBLE else VISIBLE
        post { updateEpubViewport() }
    }

    private fun setEpubViewportFull(full: Boolean) {
        if (epubCover == full) return
        epubCover = full
        post { updateEpubViewport() }
    }

    private fun updateEpubViewport() {
        val viewport = curPage.contentViewport
        val normal = Rect(0, 0, viewport.width, viewport.height)
        offsetDescendantRectToMyCoords(viewport, normal)
        val chromeTop = normal.top
        val chromeBottom = normal.bottom
        // TextView bounds exclude the information bars, but native text padding lives in
        // ChapterProvider rather than View.padding. EPUB must include that same padding.
        normal.left += ChapterProvider.paddingLeft
        normal.top += ChapterProvider.paddingTop
        normal.right -= ChapterProvider.paddingRight
        normal.bottom -= ChapterProvider.paddingBottom
        if (normal.width() <= 0 || normal.height() <= 0) return
        epubLayout?.insets(normal.left, normal.top, width - normal.right, height - normal.bottom,
            chromeTop, height - chromeBottom)
        if (epubCover) epubViewport.set(0, 0, width, height)
        else {
            epubViewport.set(normal)
        }
        epubLayout?.viewport(epubViewport.left, epubViewport.top, epubViewport.width(), epubViewport.height(), epubCover)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        updateEpubViewport()
    }
    val defaultAnimationSpeed = 300
    private var pressDown = false
    private var isMove = false

    //起始点
    var startX: Float = 0f
    var startY: Float = 0f

    //上一个触碰点
    var lastX: Float = 0f
    var lastY: Float = 0f

    //触碰点
    var touchX: Float = 0f
    var touchY: Float = 0f

    //是否停止动画动作
    var isAbortAnim = false

    //长按
    private var longPressed = false
    private val longPressTimeout = 600L
    private val longPressRunnable = Runnable {
        longPressed = true
        onLongPress()
    }
    var isTextSelected = false
    private var pressOnTextSelected = false
    private val initialTextPos = TextPos(0, 0, 0)

    private val slopSquare by lazy { ViewConfiguration.get(context).scaledTouchSlop }
    private var pageSlopSquare: Int = slopSquare
    var pageSlopSquare2: Int = pageSlopSquare * pageSlopSquare
    private var pageTouchClick: Int = 0
    private val tlRect = RectF()
    private val tcRect = RectF()
    private val trRect = RectF()
    private val mlRect = RectF()
    private val mcRect = RectF()
    private val mrRect = RectF()
    private val blRect = RectF()
    private val bcRect = RectF()
    private val brRect = RectF()
    private val upProgressThrottle = throttle(200) { post { upProgress() } }
    val autoPager = AutoPager(this)
    val isAutoPage get() = autoPager.isRunning

    init {
        if (!isInEditMode) {
            upBg()
            setWillNotDraw(false)
            upPageAnim()
            upPageSlopSquare()
        }
        addView(nextPage)
        addView(curPage)
        addView(prevPage)
        prevPage.invisible()
        nextPage.invisible()
        curPage.markAsMainView()
        upPageTouchClick()
        if (openingTiming != null) viewTreeObserver.addOnDrawListener(object : android.view.ViewTreeObserver.OnDrawListener {
            override fun onDraw() {
                if (openingTiming != null && ReadBook.book?.isEpub == false && currentChapter != null &&
                    curPage.textPage.textChapter === currentChapter && curPage.textPage.lineSize > 0 &&
                    !curPage.textPage.isMsgPage && curPage.contentViewport.visibility == VISIBLE) {
                    openingTiming?.mark("native-ready")
                    openingTiming = null
                }
                if (openingTiming == null) post { if (viewTreeObserver.isAlive) viewTreeObserver.removeOnDrawListener(this) }
            }
        })
    }

    private fun setRect9x() {
        tlRect.set(0f + pageTouchClick, 0f, width * 0.33f, height * 0.33f)
        tcRect.set(width * 0.33f, 0f, width * 0.66f, height * 0.33f)
        trRect.set(width * 0.36f, 0f, width.toFloat() - pageTouchClick, height * 0.33f)
        mlRect.set(0f + pageTouchClick, height * 0.33f, width * 0.33f, height * 0.66f)
        mcRect.set(width * 0.33f, height * 0.33f, width * 0.66f, height * 0.66f)
        mrRect.set(width * 0.66f, height * 0.33f, width.toFloat() - pageTouchClick, height * 0.66f)
        blRect.set(0f + pageTouchClick, height * 0.66f, width * 0.33f, height.toFloat())
        bcRect.set(width * 0.33f, height * 0.66f, width * 0.66f, height.toFloat())
        brRect.set(width * 0.66f, height * 0.66f, width.toFloat() - pageTouchClick, height.toFloat())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        setRect9x()
        prevPage.x = -w.toFloat()
        pageDelegate?.setViewSize(w, h)
        if (w > 0 && h > 0) {
            upBg()
            callBack.upSystemUiVisibility()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val frames = externalPageSnapshots
        // The animation frames already contain the complete page and chrome.
        // Resume the live document for its existing visual commit at handoff.
        if (frames == null || externalAnimationFinishing) super.dispatchDraw(canvas)
        if (frames != null) {
            canvas.drawBitmap(if (externalAnimationFinishing) frames.second else frames.first, 0f, 0f, null)
            if (!externalAnimationFinishing && !autoPager.isRunning) pageDelegate?.onDraw(canvas)
        } else if (epubLayout?.active != true) {
            pageDelegate?.onDraw(canvas)
        }
        autoPager.onDraw(canvas)
    }

    override fun computeScroll() {
        pageDelegate?.computeScroll()
        autoPager.computeOffset()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        return true
    }

    /**
     * 触摸事件
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
            callBack.screenOffTimerStart()
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            epubPointerSequence = epubLayout?.active == true && epubViewport.contains(event.x.toInt(), event.y.toInt()) &&
                epubLayout?.containsVisibleDocument(event.x, event.y) == true
        }
        if (epubPointerSequence) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                // Match the native path before handing the gesture to the EPUB layout.
                callBack.dismissTextActionMenu()
            }
            val handled = epubLayout?.touch(event) ?: true
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) epubPointerSequence = false
            return handled
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val insets = this.rootWindowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.mandatorySystemGestures()
            )
            val height = activity?.windowManager?.currentWindowMetrics?.bounds?.height()
            if (height != null) {
                if (event.y > height.minus(insets.bottom)
                    && event.action != MotionEvent.ACTION_UP
                    && event.action != MotionEvent.ACTION_CANCEL
                ) {
                    return true
                }
            }
        }

        //在多点触控时，事件不走ACTION_DOWN分支而产生的特殊事件处理
        if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN || event.actionMasked == MotionEvent.ACTION_POINTER_UP) {
            pageDelegate?.onTouch(event)
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isTextSelected) {
                    curPage.cancelSelect()
                    isTextSelected = false
                    pressOnTextSelected = true
                } else {
                    pressOnTextSelected = false
                    callBack.dismissTextActionMenu()
                }
                longPressed = false
                postDelayed(longPressRunnable, longPressTimeout)
                pressDown = true
                isMove = false
                pageDelegate?.onTouch(event)
                pageDelegate?.onDown()
                setStartPoint(event.x, event.y, false)
            }

            MotionEvent.ACTION_MOVE -> {
                if (!pressDown) return true
                val absX = abs(startX - event.x)
                val absY = abs(startY - event.y)
                if (!isMove) {
                    isMove = absX > slopSquare || absY > slopSquare
                }
                if (isMove) {
                    longPressed = false
                    removeCallbacks(longPressRunnable)
                    if (isTextSelected) {
                        selectText(event.x, event.y)
                    } else {
                        pageDelegate?.onTouch(event)
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (!pressDown) return true
                pressDown = false
                if (!pageDelegate!!.isMoved && !isMove) {
                    if (!longPressed && !pressOnTextSelected) {
                        if (!curPage.onClick(startX, startY, event.x, event.y)) {
                            onSingleTapUp()
                        }
                        return true
                    }
                }
                if (isTextSelected) {
                    callBack.showTextActionMenu()
                } else if (pageDelegate!!.isMoved) {
                    pageDelegate?.onTouch(event)
                }
                pressOnTextSelected = false
            }

            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                if (!pressDown) return true
                pressDown = false
                if (isTextSelected) {
                    callBack.showTextActionMenu()
                } else if (pageDelegate!!.isMoved) {
                    pageDelegate?.onTouch(event)
                }
                pressOnTextSelected = false
                autoPager.resume()
            }
        }
        return true
    }

    /** Dispatch the existing search match to the active layout; matching stays in the view model. */
    fun showSearchResult(chapter: TextChapter, positions: Array<Int>, selecting: (Boolean) -> Unit) {
        if (ReadBook.book?.isEpub == true) {
            bindEpubLayout()
            ReadBook.commitContentPosition(positions[6])
            epubLayout?.showSearchResult(chapter, positions[6], positions[5])
            return
        }
        val (pageIndex, lineIndex, charIndex, addLine, charIndex2) = positions
        ReadBook.skipToPage(pageIndex) {
            selecting(true)
            curPage.selectStartMoveIndex(0, lineIndex, charIndex)
            when (addLine) {
                0 -> curPage.selectEndMoveIndex(
                    0,
                    lineIndex,
                    charIndex + positions[5] - 1
                )

                1 -> curPage.selectEndMoveIndex(
                    0, lineIndex + 1, charIndex2
                )
                //consider change page, jump to scroll position
                -1 -> curPage.selectEndMoveIndex(1, 0, charIndex2)
            }
            isTextSelected = true
            selecting(false)
        }
    }

    fun cancelSelect(clearSearchResult: Boolean = false) {
        if (epubLayout?.active == true && clearSearchResult) {
            epubLayout?.clearSelection()
            isTextSelected = false
            return
        }
        if (isTextSelected) {
            if (epubLayout?.active == true) epubLayout?.clearSelection()
            else curPage.cancelSelect(clearSearchResult)
            isTextSelected = false
        }
    }

    fun moveSelectionHandle(start: Boolean, x: Float, y: Float) {
        if (epubLayout?.active == true) epubLayout?.moveSelection(start, x, y)
        else if (start) curPage.selectStartMove(x, y) else curPage.selectEndMove(x, y)
    }

    fun finishSelectionHandleDrag() { epubLayout?.finishSelectionDrag() }

    fun setSelectionHighlightTransparent(transparent: Boolean) {
        epubLayout?.setSelectionHighlightTransparent(transparent)
        curPage.setSelectionHighlightTransparent(transparent)
        prevPage.setSelectionHighlightTransparent(transparent)
        nextPage.setSelectionHighlightTransparent(transparent)
    }

    fun upSelectAble(enabled: Boolean) {
        textSelectAble = enabled
        curPage.upSelectAble(enabled)
        epubLayout?.setSelectionEnabled(enabled)
    }

    /**
     * 更新状态栏
     */
    fun upStatusBar() {
        curPage.upStatusBar()
        prevPage.upStatusBar()
        nextPage.upStatusBar()
    }

    fun setTextHighlights(bookmarks: List<Bookmark>) {
        textHighlights = bookmarks
        epubLayout?.setTextHighlights(bookmarks)
        curPage.setTextHighlights(bookmarks)
        prevPage.setTextHighlights(bookmarks)
        nextPage.setTextHighlights(bookmarks)
    }

    fun upTipVisibility(readerOverlayVisible: Boolean) {
        curPage.upTipVisibility(readerOverlayVisible)
        prevPage.upTipVisibility(readerOverlayVisible)
        nextPage.upTipVisibility(readerOverlayVisible)
    }

    /**
     * 保存开始位置
     */
    fun setStartPoint(x: Float, y: Float, invalidate: Boolean = true) {
        startX = x
        startY = y
        lastX = x
        lastY = y
        touchX = x
        touchY = y

        if (invalidate) {
            invalidate()
        }
    }

    /**
     * 保存当前位置
     */
    fun setTouchPoint(x: Float, y: Float, invalidate: Boolean = true) {
        lastX = touchX
        lastY = touchY
        touchX = x
        touchY = y
        if (invalidate) {
            invalidate()
        }
        pageDelegate?.onScroll()
        val offset = touchY - lastY
        touchY -= offset - offset.toInt()
    }

    /**
     * 长按选择
     */
    private fun onLongPress() {
        kotlin.runCatching {
            curPage.longPress(startX, startY) { textPos: TextPos ->
                isTextSelected = true
                pressOnTextSelected = true
                initialTextPos.upData(textPos)
                val startPos = textPos.copy()
                val endPos = textPos.copy()
                val page = curPage.relativePage(textPos.relativePagePos)
                val stringBuilder = StringBuilder()
                var cIndex = textPos.columnIndex
                var lineStart = textPos.lineIndex
                var lineEnd = textPos.lineIndex
                for (index in textPos.lineIndex - 1 downTo 0) {
                    val textLine = page.getLine(index)
                    if (textLine.isParagraphEnd) {
                        break
                    } else {
                        stringBuilder.insert(0, textLine.text)
                        lineStart -= 1
                        cIndex += textLine.charSize
                    }
                }
                for (index in textPos.lineIndex until page.lineSize) {
                    val textLine = page.getLine(index)
                    stringBuilder.append(textLine.text)
                    lineEnd += 1
                    if (textLine.isParagraphEnd) {
                        break
                    }
                }
                val word = readerWordBoundary(stringBuilder.toString(), cIndex) ?: return@longPress
                val start = word.first
                val end = word.last + 1
                kotlin.run {
                    var ci = 0
                    for (index in lineStart..lineEnd) {
                        val textLine = page.getLine(index)
                        for (j in textLine.columns.indices) {
                            if (ci == start) {
                                startPos.lineIndex = index
                                startPos.columnIndex = j
                            } else if (ci == end - 1) {
                                endPos.lineIndex = index
                                endPos.columnIndex = j
                                return@run
                            }
                            val column = textLine.getColumn(j)
                            if (column is TextBaseColumn) {
                                ci += column.charData.length
                            } else {
                                ci++
                            }
                        }
                    }
                }
                curPage.selectStartMoveIndex(startPos)
                curPage.selectEndMoveIndex(endPos)
            }
        }
    }

    /**
     * 单击
     */
    private fun onSingleTapUp() {
        when {
            isTextSelected -> Unit
            mcRect.contains(startX, startY) -> if (!isAbortAnim) {
                click(AppConfig.clickActionMC)
            }

            bcRect.contains(startX, startY) -> {
                click(AppConfig.clickActionBC)
            }

            blRect.contains(startX, startY) -> {
                click(AppConfig.clickActionBL)
            }

            brRect.contains(startX, startY) -> {
                click(AppConfig.clickActionBR)
            }

            mlRect.contains(startX, startY) -> {
                click(AppConfig.clickActionML)
            }

            mrRect.contains(startX, startY) -> {
                click(AppConfig.clickActionMR)
            }

            tlRect.contains(startX, startY) -> {
                click(AppConfig.clickActionTL)
            }

            tcRect.contains(startX, startY) -> {
                click(AppConfig.clickActionTC)
            }

            trRect.contains(startX, startY) -> {
                click(AppConfig.clickActionTR)
            }
        }
    }

    /**
     * 点击
     */
    private fun click(action: Int) {
        when (action) {
            0 -> {
                pageDelegate?.dismissSnackBar()
                callBack.showActionMenu()
            }

            1 -> turnPage(1)
            2 -> turnPage(-1)
            3 -> ReadBook.moveToNextChapter(true)
            4 -> ReadBook.moveToPrevChapter(upContent = true, toLast = false)
            5 -> ReadAloud.prevParagraph(context)
            6 -> ReadAloud.nextParagraph(context)
            7 -> callBack.addBookmark()
            8 -> activity?.showDialogFragment(ContentEditDialog())
            9 -> callBack.changeReplaceRuleState()
            10 -> callBack.openChapterList()
            11 -> callBack.openSearchDrawer(null)
            12 -> ReadBook.syncProgress(
                { progress -> callBack.sureNewProgress(progress) },
                { context.longToastOnUi(context.getString(R.string.upload_book_success)) },
                { context.longToastOnUi(context.getString(R.string.sync_book_progress_success)) })

            13 -> {
                if (BaseReadAloudService.isPlay()) {
                    ReadAloud.pause(context)
                } else {
                    ReadAloud.resume(context)
                }
            }
        }
    }

    /**
     * 选择文本
     */
    private fun selectText(x: Float, y: Float) {
        curPage.selectText(x, y) { textPos ->
            val compare = initialTextPos.compare(textPos)
            when {
                compare > 0 -> {
                    curPage.selectStartMoveIndex(textPos)
                    curPage.selectEndMoveIndex(
                        initialTextPos.relativePagePos,
                        initialTextPos.lineIndex,
                        initialTextPos.columnIndex - 1
                    )
                }

                else -> {
                    curPage.selectStartMoveIndex(initialTextPos)
                    curPage.selectEndMoveIndex(textPos)
                }
            }
        }
    }

    /**
     * 销毁事件
     */
    fun onDestroy() {
        finishLayoutFrames()
        epubLayout?.close()
        epubLayout = null
        pageDelegate?.onDestroy()
        curPage.cancelSelect()
        invalidateTextPage()
    }

    /**
     * 翻页动画完成后事件
     * @param direction 翻页方向
     */
    fun fillPage(direction: PageDirection): Boolean {
        if (externalPageSnapshots != null) {
            externalAnimationFinishing = true
            val commit = externalAnimationCommit
            externalAnimationCommit = null
            commit?.invoke()
            return true
        }
        if (autoPager.isRunning && epubLayout?.active == true) {
            if (epubLayout?.readyForAutoPage() != true) return true
            if (epubLayout?.hasNextPage() != true) return false
            epubLayout?.turn(if (direction == PageDirection.PREV) -1 else 1)
            return true
        }
        return when (direction) {
            PageDirection.PREV -> {
                pageFactory.moveToPrev(true)
            }

            PageDirection.NEXT -> {
                pageFactory.moveToNext(true)
            }

            else -> false
        }
    }

    /**
     * 更新翻页动画
     */
    fun upPageAnim(upRecorder: Boolean = false) {
        upPageAnim(ReadBook.pageAnim(), upRecorder)
    }

    /**
     * 临时应用指定翻页动画，不修改阅读配置
     */
    fun upPageAnim(@PageAnim.Anim pageAnim: Int, upRecorder: Boolean = false) {
        isScroll = pageAnim == PageAnim.scrollPageAnim
        ChapterProvider.upLayout()
        when (pageAnim) {
            PageAnim.coverPageAnim -> if (pageDelegate !is CoverPageDelegate) {
                pageDelegate = CoverPageDelegate(this)
            }

            PageAnim.slidePageAnim -> if (pageDelegate !is SlidePageDelegate) {
                pageDelegate = SlidePageDelegate(this)
            }

            PageAnim.simulationPageAnim -> if (pageDelegate !is SimulationPageDelegate) {
                pageDelegate = SimulationPageDelegate(this)
            }

            PageAnim.scrollPageAnim -> if (pageDelegate !is ScrollPageDelegate) {
                pageDelegate = ScrollPageDelegate(this)
            }

            else -> if (pageDelegate !is NoAnimPageDelegate) {
                pageDelegate = NoAnimPageDelegate(this)
            }
        }
        (pageDelegate as? ScrollPageDelegate)?.noAnim = AppConfig.noAnimScrollPage
        if (upRecorder) {
            (pageDelegate as? HorizontalPageDelegate)?.upRecorder()
            autoPager.upRecorder()
        }
        pageDelegate?.setViewSize(width, height)
        if (isScroll) {
            curPage.setAutoPager(autoPager)
        } else {
            curPage.setAutoPager(null)
        }
        curPage.setIsScroll(isScroll)
        bindEpubLayout()
        epubLayout?.restyle()
    }

    /**
     * 更新阅读内容
     * @param relativePosition 相对位置 -1 上一页 0 当前页 1 下一页
     * @param resetPageOffset 滚动阅读是是否重置位置
     */
    override fun upContent(relativePosition: Int, resetPageOffset: Boolean) {
        bindEpubLayout()
        post {
            curPage.setContentDescription(pageFactory.curPage.text)
        }
        if (isScroll && !isAutoPage) {
            if (relativePosition == 0) {
                curPage.setContent(pageFactory.curPage, resetPageOffset)
            } else {
                curPage.invalidateContentView()
            }
        } else {
            when (relativePosition) {
                -1 -> prevPage.setContent(pageFactory.prevPage)
                1 -> nextPage.setContent(pageFactory.nextPage)
                else -> {
                    curPage.setContent(pageFactory.curPage, resetPageOffset)
                    nextPage.setContent(pageFactory.nextPage)
                    prevPage.setContent(pageFactory.prevPage)
                }
            }
        }
        callBack.screenOffTimerStart()
    }

    private fun upProgress() {
        curPage.setProgress(pageFactory.curPage)
    }

    /**
     * 更新滑动距离
     */
    fun upPageSlopSquare() {
        val pageTouchSlop = AppConfig.pageTouchSlop
        this.pageSlopSquare = if (pageTouchSlop == 0) slopSquare else pageTouchSlop
        pageSlopSquare2 = this.pageSlopSquare * this.pageSlopSquare
    }

    /**
     * 更新边缘点击阈值
     */
    fun upPageTouchClick() {
        this.pageTouchClick = AppConfig.pageTouchClick
        setRect9x()
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        ChapterProvider.upStyle()
        curPage.upStyle()
        prevPage.upStyle()
        nextPage.upStyle()
        bindEpubLayout()
        epubLayout?.restyle()
        if (ReadBookConfig.isNineBgImg) {
            upBg()
        }
    }

    /**
     * 更新背景
     */
    fun refreshHighlightRules(): Boolean = epubLayout?.takeIf { it.active }?.refreshHighlightRules() ?: false

    fun upBg() {
        ReadBookConfig.upBg(width, height)
        curPage.upBg()
        prevPage.upBg()
        nextPage.upBg()
    }

    /**
     * 更新背景透明度
     */
    fun upBgAlpha() {
        curPage.upBgAlpha()
        prevPage.upBgAlpha()
        nextPage.upBgAlpha()
    }

    /**
     * 更新时间信息
     */
    fun upTime() {
        curPage.upTime()
        prevPage.upTime()
        nextPage.upTime()
    }

    /**
     * 更新电量信息
     */
    fun upBattery(battery: Int) {
        curPage.upBattery(battery)
        prevPage.upBattery(battery)
        nextPage.upBattery(battery)
    }

    /**
     * 从选择位置开始朗读
     */
    suspend fun aloudStartSelect() {
        if (epubLayout?.active == true) {
            val selected = selectionSource.highlightSelection() ?: return
            val bookUrl = ReadBook.book?.bookUrl ?: return
            val play = {
                ReadBook.curTextChapter?.takeIf {
                    ReadBook.book?.bookUrl == bookUrl && it.chapter.index == selected.chapterIndex && it.isCompleted
                }?.let { chapter ->
                    val pageIndex = chapter.getPageIndexByCharIndex(selected.chapterPosition)
                    ReadAloud.play(context, pageIndex = pageIndex,
                        startPos = selected.chapterPosition - chapter.getReadLength(pageIndex),
                        contentPosition = selected.chapterPosition)
                }
                Unit
            }
            val start = {
                epubLayout?.whenChapterReady(selected.chapterIndex) {
                    if (context.getPrefBoolean(io.legado.app.constant.PreferKey.readAloudByPage)) epubLayout?.prepareReadAloudPages(play)
                    else play()
                }
                Unit
            }
            if (selected.chapterIndex == ReadBook.durChapterIndex) start()
            else ReadBook.openChapter(selected.chapterIndex, selected.chapterPosition, success = start)
            return
        }
        val selectStartPos = curPage.selectStartPos
        var pagePos = selectStartPos.relativePagePos
        val line = selectStartPos.lineIndex
        val column = selectStartPos.columnIndex
        while (pagePos > 0) {
            if (!ReadBook.moveToNextPage()) {
                ReadBook.moveToNextChapterAwait(false)
            }
            pagePos--
        }
        val startPos = curPage.textPage.getPosByLineColumn(line, column)
        ReadBook.readAloud(startPos = startPos)
    }

    /**
     * @return 选择的文本
     */
    fun getSelectText(): String {
        return selectionSource.selectedText
    }

    private fun turnPage(direction: Int) {
        val layout = epubLayout
        if (layout?.active == true) layout.turn(direction)
        else if (direction > 0) pageDelegate?.nextPageByAnim(defaultAnimationSpeed)
        else pageDelegate?.prevPageByAnim(defaultAnimationSpeed)
    }

    internal fun turnLayoutPage(direction: Int): Boolean {
        val layout = epubLayout ?: return false
        if (!layout.active) return false
        layout.turn(direction)
        return true
    }

    fun createBookmark(): Bookmark? {
        val book = ReadBook.book ?: return null
        return selectionSource.bookmarkSelection()?.createBookmark(book)
    }

    fun createTextHighlight(): Bookmark? {
        val book = ReadBook.book ?: return null
        return selectionSource.highlightSelection()?.createTextHighlight(book)
    }

    fun getContentEditTarget(highlight: Bookmark?): ReaderContentEditTarget? {
        val selection = highlight?.let {
            ReaderSelection(
                chapterIndex = it.chapterIndex,
                chapterPosition = it.chapterPos,
                chapterTitle = it.chapterName,
                text = it.bookText,
                endChapterIndex = it.endChapterIndex,
                endChapterPosition = it.endChapterPos,
            )
        }
        return selectionSource.contentEditTarget(selection)
    }

    fun getCurVisiblePage(): TextPage {
        return curPage.getCurVisiblePage()
    }

    fun getReadAloudPos(): Pair<Int, TextLine>? {
        return curPage.getReadAloudPos()
    }

    fun followReadAloud(position: Int) { epubLayout?.followReadAloud(position) }
    fun stopFollowingReadAloud() {
        epubLayout?.stopFollowingReadAloud()
        // STOP/PAUSE must also clear the DOM paint when manual browsing skips a native repaint.
        epubLayout?.syncAloudHighlight()
    }

    internal fun prepareAutoPage(): Boolean {
        val layout = epubLayout?.takeIf { it.active } ?: return true
        if (externalPageSnapshots != null) return !externalAnimationFinishing
        if (!layout.readyForAutoPage()) return false
        if (!layout.hasNextPage()) { callBack.autoPageStop(); return false }
        if (isScroll) return layout.readyForAutoPage()
        if (externalPageSnapshots == null) layout.prepareAutoPage()
        return externalPageSnapshots != null && !externalAnimationFinishing
    }

    internal fun drawAutoPage(canvas: Canvas): Boolean {
        if (epubLayout?.active != true) return false
        externalPageSnapshots?.let { canvas.drawBitmap(it.second, 0f, 0f, null) }
        return true
    }

    internal fun scrollAutoPage(amount: Int) {
        val layout = epubLayout?.takeIf { it.active }
        if (layout != null) layout.scrollAutoPage(amount.toFloat()) else curPage.scroll(-amount)
    }

    internal fun cancelAutoPage() {
        epubLayout?.cancelAutoPage()
        finishLayoutFrames()
    }

    /** Returns true when the visible layout owns the request, including while it is loading. */
    fun aloudStartVisible(): Boolean {
        if (ReadBook.book?.isEpub != true) return false
        val (index, position) = epubLayout?.visiblePosition() ?: return true
        val bookUrl = ReadBook.book?.bookUrl ?: return true
        val play = {
            ReadBook.curTextChapter?.takeIf {
                ReadBook.book?.bookUrl == bookUrl && it.chapter.index == index && it.isCompleted
            }?.let { chapter ->
                val page = chapter.getPageIndexByCharIndex(position)
                ReadAloud.play(context, pageIndex = page, startPos = position - chapter.getReadLength(page),
                    contentPosition = position)
            }
            Unit
        }
        val start = {
            epubLayout?.whenChapterReady(index) {
                if (context.getPrefBoolean(io.legado.app.constant.PreferKey.readAloudByPage)) epubLayout?.prepareReadAloudPages(play)
                else play()
            }
            Unit
        }
        if (index == ReadBook.durChapterIndex) start() else ReadBook.openChapter(index, position, success = start)
        return true
    }

    fun invalidateTextPage() {
        if (!AppConfig.optimizeRender) {
            return
        }
        pageFactory.run {
            prevPage.invalidateAll()
            curPage.invalidateAll()
            nextPage.invalidateAll()
            nextPlusPage.invalidateAll()
        }
    }

    fun onScrollAnimStart() {
        autoPager.pause()
    }

    fun onScrollAnimStop() {
        autoPager.resume()
    }

    fun onPageChange() {
        autoPager.reset()
        submitRenderTask()
    }

    fun submitRenderTask() {
        if (!AppConfig.optimizeRender) {
            return
        }
        curPage.submitRenderTask()
    }

    fun isLongScreenShot(): Boolean {
        return curPage.isLongScreenShot()
    }

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        upProgressThrottle.invoke()
    }

    override fun onLayoutCompleted() {
        post { bindEpubLayout() }
    }

    override val currentChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) ReadBook.textChapter(0) else null
        }

    override val nextChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) ReadBook.textChapter(1) else null
        }

    override val prevChapter: TextChapter?
        get() {
            return if (callBack.isInitFinish) ReadBook.textChapter(-1) else null
        }

    override fun hasNextChapter(): Boolean {
        return ReadBook.durChapterIndex < ReadBook.simulatedChapterSize - 1
    }

    override fun hasPrevChapter(): Boolean {
        return ReadBook.durChapterIndex > 0
    }

    interface CallBack {
        val isInitFinish: Boolean
        fun showActionMenu()
        fun screenOffTimerStart()
        fun showTextActionMenu()
        fun autoPageStop()
        fun openChapterList()
        fun addBookmark()
        fun changeReplaceRuleState()
        fun openSearchDrawer(searchWord: String?)
        fun dismissTextActionMenu()
        fun upSystemUiVisibility()
        fun sureNewProgress(progress: BookProgress)
        fun upSelectedStart(x: Float, y: Float, top: Float)
        fun upSelectedEnd(x: Float, y: Float)
        fun onCancelSelect()
        fun onTextHighlightClick(bookmark: Bookmark, anchorX: Float, top: Float, bottom: Float)
    }
}
