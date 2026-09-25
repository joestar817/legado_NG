package io.legado.app.ui.book.read.epub

import android.os.ParcelFileDescriptor
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isEpub
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.EpubLayoutPreferences
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.ReadNoteMarkerStyle
import io.legado.app.ui.book.read.page.api.ReaderSelectionSource
import io.legado.app.ui.book.read.page.api.ReaderSelection
import io.legado.app.ui.book.read.page.api.ReaderContentEditTarget
import io.legado.app.ui.book.read.page.api.readerWordBoundary
import io.legado.app.model.epub.EpubLayout
import io.legado.app.model.epub.EpubSnapshotSession
import io.legado.app.model.epub.EpubSnapshotStore
import io.legado.app.model.epub.EpubSpreadLayout
import io.legado.app.model.epub.EpubSpreadPage
import io.legado.app.model.epub.EpubPageProgressionDirection
import io.legado.app.model.epub.EpubResourceLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.Closeable
import java.io.File
import java.io.IOException

/** Owns only the current publication resources and document viewport, never book progress. */
internal class EpubLayoutController(
    private val host: FrameLayout,
    private val blankTap: (Float, Float) -> Unit,
    private val chapterTurn: (Int) -> Unit,
    private val reportPage: (Int, Int, Boolean) -> Unit,
    private val requestViewport: (Boolean) -> Unit,
    private val reportError: (String) -> Unit,
    private val nativeFrame: () -> Bitmap,
    private val animate: (Bitmap, Bitmap, Int, () -> Unit) -> Unit,
    private val finishAnimation: () -> Unit,
    private val completeAnimation: () -> Unit,
    private val animationsEnabled: () -> Boolean,
    private val autoPaging: () -> Boolean = { false },
    private val isScroll: () -> Boolean,
    private val publisherStyle: () -> Boolean,
    private val position: () -> Int,
    private val commitPosition: (Int, Int) -> Unit,
    private val neighboringChapters: () -> List<TextChapter>,
    private val selectionChanged: (JSONObject?, Boolean) -> Unit,
    private val highlightClick: (Bookmark, Float, Float, Float) -> Unit,
    private val imageLongPress: (Float, Float, String) -> Unit = { _, _, _ -> },
    private val openingPreparation: () -> EpubOpeningPreparation? = { null },
    private val openingPreview: () -> EpubOpeningPreview? = { null },
) : Closeable, ReaderSelectionSource {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var generation = 0L
    private var bookUrl: String? = null
    private var boundBook: Book? = null
    private var chapter: BookChapter? = null
    private var textChapter: TextChapter? = null
    private var preparedContent: EpubPreparedContent? = null
    private var preparedChapters = emptyMap<Int, EpubPreparedContent>()
    private var failedNeighbors = emptyMap<Int, TextChapter>()
    private var openAtEnd = false
    /** A requested chapter viewport, cleared when its first layout commits. */
    private var chapterTurnTarget: Int? = null
    private var selection: ReaderSelection? = null
    private var selectionAnchor: Pair<Int, Int>? = null
    private var selectionRevision = 0L
    private var selectionFinished = false
    private var aloudSignature = ""
    private var aloudPaintSignature = ""
    private var fontWarningSignature = ""
    private var pageTextRequest = 0L
    private var pageTextMeasuring = false
    private var pendingReadAloud: Pair<Int, () -> Unit>? = null
    private var speechFollowPosition: Int? = null
    private var speechFollowRevision = 0L
    private var selectionEnabled = true
    private var selectionTransparent = false
    private var textHighlights: List<Bookmark> = emptyList()
    private data class SearchSelection(val chapter: TextChapter, val from: Int, val to: Int, var needsSeek: Boolean)
    private var pendingSearchSelection: SearchSelection? = null
    private var requestedPosition = 0
    private var reportedPosition = -1
    private var contentGeneration = 0L
    private var highlightRevision = 0L
    private var pendingHighlightUpdate = false
    private var readerFontBytes: ByteArray? = null
    private var readerFontPath = ""
    private var titleFontBytes: ByteArray? = null
    private var titleFontPath = ""
    private var styleGeneration = 0L
    private var readerInsets = JSONObject()
    private var chromeInsets = JSONObject()
    private var insetsChanged = false
    private var snapshot: EpubSnapshotSession? = null
    private var surface: EpubLayoutSurface? = null
    private var gestures: EpubReaderGestures? = null
    private var documents = emptyList<EpubPreparedContent.Document>()
    private var documentIndex = 0
    private var linkRequest = 0L
    private var pendingChapterLink: Pair<Int, EpubResourceLink>? = null
    private var loading = false
    private var renderFailed = false
    private var rendererRecoveryUsed = false
    @Volatile private var closed = false
    private var downX = 0f
    private var downY = 0f
    private var viewportTop = 0
    private var preparation: EpubPreparedTurn? = null
    private var turning = false
    private var committing = false
    private var preparationFrame: Bitmap? = null
    private var turnTimeout: Runnable? = null
    private val frames = EpubFrameWindow<EpubCapturedFrame> { it.close() }
    private var frameEpoch = 0L
    private var frameDocument = -1
    private var preparationWorking = false
    private var anticipatedPage: Int? = null
    private var lastDirection = 1
    private val pendingTurns = java.util.ArrayDeque<Int>()
    private val pendingInteractions = java.util.ArrayDeque<() -> Unit>()
    private val warmTask = Runnable { warmFrames() }
    private val warmWatchdog = EpubRenderWatchdog({ task, delay -> host.postDelayed(task, delay); Unit }, host::removeCallbacks)
    var active = false
        private set

    fun bind(book: Book?, value: TextChapter?) {
        if (closed) return
        if (book?.isEpub != true) {
            boundBook = null
            releasePublication()
            return
        }
        boundBook = book
        // Completion notifications of a failed revision must not start a retry loop.
        if (renderFailed && bookUrl == book.bookUrl) {
            if (value === textChapter && position() == requestedPosition) return
            chapter = value?.chapter
            textChapter = value
            retryRendering()
            return
        }
        renderFailed = false
        if (value != null && pendingSearchSelection?.chapter !== value) pendingSearchSelection = null
        active = true
        // Progress repaints of this same chapter must not seek back while a
        // manual turn is waiting to commit. A new book/chapter still binds normally.
        if (bookUrl == book.bookUrl && value === textChapter && preparedContent != null &&
            (turning || pendingTurns.isNotEmpty() ||
                chapterTurnTarget != null && chapterTurnTarget == value?.chapter?.index)) return
        if (bookUrl != book.bookUrl) {
            releasePublication()
            active = true
            bookUrl = book.bookUrl
            chapter = value?.chapter
            textChapter = value
            requestedPosition = position()
            val mine = ++generation
            val fontGeneration = styleGeneration
            val fontPath = ReadBookConfig.textFont
            val titlePath = ReadBookConfig.titleFont.ifBlank { fontPath }
            loading = true
            val startup = EpubStartupTiming("open")
            startup.mark("bound")
            scope.launch {
                // Keep the closeable in a local owner across the cancellable dispatcher boundary.
                var opened: EpubSnapshotSession? = null
                try {
                    val font = withContext(Dispatchers.IO) {
                        opened = openingPreparation()?.take(book.bookUrl) ?: EpubSnapshotStore(File(host.context.cacheDir, "epub-layout"),
                            onStage = startup::mark).open(
                            book.bookUrl,
                            openInput = {
                                val descriptor = BookHelp.getBookPFD(book) ?: throw IOException("无法打开 EPUB")
                                ParcelFileDescriptor.AutoCloseInputStream(descriptor)
                            },
                            checkCancelled = { if (closed || mine != generation) throw IOException("EPUB 打开已取消") },
                        )
                        startup.mark("snapshot-ready")
                        val bodyFont = runCatching { readEpubReaderFont(host.context, fontPath) }.getOrNull()
                        val titleFont = if (titlePath == fontPath) bodyFont else
                            runCatching { readEpubReaderFont(host.context, titlePath) }.getOrNull()
                        (bodyFont to titleFont).also {
                            startup.mark("reader-font-ready")
                        }
                    }
                    if (closed || mine != generation) return@launch
                    if (fontGeneration == styleGeneration) {
                        readerFontPath = fontPath
                        readerFontBytes = font.first
                        titleFontPath = titlePath
                        titleFontBytes = font.second
                    }
                    snapshot = opened
                    opened = null
                    loading = false
                    startup.mark("resources-ready")
                    showChapter(book)
                } catch (error: Exception) {
                    if (!closed && mine == generation) {
                        loading = false
                        failRendering(error.localizedMessage ?: "EPUB 打开失败")
                    }
                } finally {
                    withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { opened?.close() }
                }
            }
        } else if (value != null && preparedChapters[value.chapter.index]?.chapter === value) {
            val chapterChanged = textChapter !== value
            if (chapterChanged) {
                textChapter = value; chapter = value.chapter; preparedContent = preparedChapters[value.chapter.index]
                requestedPosition = position()
            }
            if (!loading && (isScroll() || autoPaging() || options().optBoolean("fixed")) && neighboringChapters().any {
                it.isCompleted && preparedChapters[it.chapter.index]?.chapter !== it && failedNeighbors[it.chapter.index] !== it
            }) showChapter(book)
            // Equal offsets in different chapters are not the same reading location. A window
            // callback may already have displayed this chapter; a TOC jump still needs a seek.
            else if (chapterChanged && documents.getOrNull(documentIndex)?.chapter !== value ||
                position() != reportedPosition && position() != requestedPosition) jump(position())
        } else if (textChapter !== value || preparedContent == null && value?.isCompleted == true && !loading) {
            chapter = value?.chapter
            textChapter = value
            // Native layout publishes the new TextChapter before it is complete. Drop the old
            // binding now so the completion notification will prepare the new document.
            preparedContent = null
            reportedPosition = -1
            requestedPosition = position()
            if (!loading) showChapter(book)
        } else if (position() != reportedPosition && position() != requestedPosition) {
            jump(position())
        }
    }

    private fun attachSurface() {
        val publication = snapshot?.publicationSession ?: return
        val onReady: (JSONObject) -> Unit = ready@ { state ->
                if (preparedContent?.chapter !== textChapter || preparedContent == null) return@ready
                val completesChapterTurn = chapterTurnTarget != null && chapterTurnTarget == textChapter?.chapter?.index
                if (completesChapterTurn) stopFollowingReadAloud()
                val fontWarnings = state.optJSONArray("fontWarnings")
                val fonts = (0 until (fontWarnings?.length() ?: 0)).map { fontWarnings!!.optString(it) }.toSet()
                val fontWarning = when {
                    "reader" in fonts && "title" in fonts -> "阅读和标题字体无法用于 EPUB，暂用备用字体"
                    "reader" in fonts -> "阅读字体无法用于 EPUB，暂用备用字体"
                    "title" in fonts -> "标题字体无法用于 EPUB，暂用正文字体"
                    else -> ""
                }
                if (fontWarning.isNotEmpty() && fontWarning != fontWarningSignature) reportError(fontWarning)
                fontWarningSignature = fontWarning
                if (state.has("index")) documentIndex = state.optInt("readingIndex", state.optInt("index")).coerceIn(documents.indices)
                if (frameDocument != documentIndex) clearFrames()
                reportPage(state.optInt("displayPageIndex", state.optInt("pageIndex")) + 1,
                    state.optInt("displayPageCount", state.optInt("pageCount")),
                    state.optBoolean("cover") || state.optBoolean("fullViewport") || state.optBoolean("bleed"))
                val location = state.optJSONObject(if (state.has("readingIndex")) "readingLocation" else "location")
                val offset = location?.optInt("textOffset", -1)
                    ?.takeIf { it >= 0 } ?: documents.getOrNull(documentIndex)?.first
                if (offset != null) {
                    reportedPosition = offset
                    requestedPosition = speechFollowPosition ?: offset
                    if (speechFollowPosition == null) commitPosition(documents[documentIndex].chapter.chapter.index, offset)
                }
                if (completesChapterTurn) chapterTurnTarget = null
                if (committing) {
                    committing = false
                    turning = false
                    turnTimeout?.let(host::removeCallbacks)
                    turnTimeout = null
                    finishAnimation()
                }
                anticipatedPage = null
                syncAloudHighlight()
                speechFollowPosition?.let(::followReadAloud)
                applySearchSelection()
                pendingReadAloud?.takeIf { it.first == textChapter?.chapter?.index }?.let {
                    pendingReadAloud = null
                    host.post { whenChapterReady(it.first, it.second) }
                }
                host.post { continueTurns() }
                if (pendingHighlightUpdate) host.post { refreshHighlightRules() }
            }
        lateinit var next: EpubLayoutSurface
        val onFailure: (String) -> Unit = { message ->
            if (surface === next) failRendering(message, next.rendererTerminated)
        }
        next = openingPreview()?.take(bookUrl, publication)?.also {
            it.adopt(onReady, onFailure, requestViewport)
        } ?: EpubLayoutSurface(host.context, publication, onReady, onFailure, requestViewport)
        surface = next
        next.readerFont(readerFontBytes)
        next.titleFont(titleFontBytes)
        host.addView(next, FrameLayout.LayoutParams(host.width, 1))
        gestures = EpubReaderGestures(
            ViewConfiguration.get(host.context).scaledTouchSlop,
            tap = { x, y ->
                interactReady(JSONObject().put("action", "tap").put("x", css(x)).put("y", css(y))) { result ->
                    if (result == null) return@interactReady
                    if (result.optBoolean("handled")) { clearFrames(); scheduleWarm(); return@interactReady }
                    if (result.optBoolean("dismissed")) { clearSelection(); return@interactReady }
                    val highlight = textHighlights.firstOrNull { it.time.toString() == result.optString("highlight") }
                    val rect = result.optJSONObject("highlightRect")
                    if (highlight != null && rect != null) {
                        val scale = host.resources.displayMetrics.density
                        highlightClick(highlight, x + next.left,
                            next.top + rect.optDouble("top").toFloat() * scale,
                            next.top + rect.optDouble("bottom").toFloat() * scale)
                        return@interactReady
                    }
                    val href = if (result.isNull("href")) "" else result.optString("href")
                    if (href.isEmpty()) blankTap(x, y + viewportTop)
                    else next.resolve(href)?.let(::followLink)
                }
            },
            longPress = { x, y -> hitSelection(x, y, initial = true) },
            extend = { x, y -> hitSelection(x, y, initial = false) },
            finishSelection = ::finishSelectionDrag,
            swipe = { direction ->
                interactReady(JSONObject().put("action", "gallerySwipe").put("x", css(downX))
                    .put("y", css(downY)).put("start", direction < 0)) { result ->
                    if (result?.optBoolean("handled") != true) turn(direction)
                    else { clearFrames(); scheduleWarm() }
                }
            },
            cancelSelection = { clearSelection() },
            scroll = { dx, dy ->
                val state = next.state
                val horizontal = state?.optString("scrollAxis") == "x"
                if (state?.optBoolean("scrolled") == true &&
                    (horizontal || kotlin.math.abs(dy) >= kotlin.math.abs(dx))) {
                    next.scroll(css(if (horizontal) dx * state.optInt("scrollSign", 1) else dy))
                    true
                } else false
            },
            finishScroll = {
                val overshoot = next.state?.optDouble("scrollOvershoot", 0.0) ?: 0.0
                if (kotlin.math.abs(overshoot) > 48) turn(if (overshoot > 0) 1 else -1)
            },
            canSelect = { selectionEnabled },
        )
    }

    fun viewport(left: Int, top: Int, width: Int, height: Int, full: Boolean = false) {
        viewportTop = top
        val view = surface ?: return
        val modeChanged = view.viewportFull != full
        if (modeChanged) {
            view.viewportFull = full
            insetsChanged = true
        }
        if (width <= 0 || height <= 0) return
        val params = view.layoutParams as FrameLayout.LayoutParams
        if (params.width == width && params.height == height && params.leftMargin == left && params.topMargin == top) {
            if (insetsChanged && preparedContent != null && !loading) {
                cancelTurn()
                if (modeChanged) view.refreshViewport() else view.restyle(options())
            }
            insetsChanged = false
            return
        }
        insetsChanged = false
        cancelTurn()
        view.layoutParams = params.apply {
            this.width = width
            this.height = height
            leftMargin = left
            topMargin = top
        }
    }

    private fun followLink(link: EpubResourceLink) {
        stopFollowingReadAloud()
        val request = ++linkRequest
        val exact = documents.indexOfFirst { it.location.path == link.path && it.location.fragment == link.fragment }
        val local = if (link.fragment.isNullOrEmpty()) documents.indexOfFirst { it.location.path == link.path }
            else exact
        if (local >= 0) {
            documentIndex = local
            openDocument(fragment = link.fragment)
            return
        }
        val book = boundBook ?: return
        val currentIndex = textChapter?.chapter?.index ?: return
        val owner = generation
        scope.launch {
            val target = withContext(Dispatchers.IO) {
                EpubChapterLinks.find(appDb.bookChapterDao.getChapterList(book.bookUrl), link)
            }
            if (closed || request != linkRequest || owner != generation || bookUrl != book.bookUrl) return@launch
            if (target == null) { reportError("EPUB 链接目标不在目录中"); return@launch }
            if (target.index == currentIndex) {
                val index = documents.indexOfFirst { it.location.path == link.path &&
                    it.chapter.chapter.index == target.index }
                if (index < 0) { reportError("EPUB 链接目标不可用"); return@launch }
                documentIndex = index
                openDocument(fragment = link.fragment)
            } else {
                pendingChapterLink = target.index to link
                chapterTurn(target.index - currentIndex)
            }
        }
    }

    fun insets(left: Int, top: Int, right: Int, bottom: Int, header: Int, footer: Int) {
        val next = JSONObject().put("left", css(left.toFloat())).put("top", css(top.toFloat()))
            .put("right", css(right.toFloat())).put("bottom", css(bottom.toFloat()))
        val chrome = JSONObject().put("top", css(header.toFloat())).put("bottom", css(footer.toFloat()))
        if (readerInsets.toString() != next.toString() || chromeInsets.toString() != chrome.toString()) {
            readerInsets = next
            chromeInsets = chrome
            insetsChanged = true
        }
    }

    fun touch(event: MotionEvent): Boolean {
        if (renderFailed) {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) { downX = event.x; downY = event.y }
            if (event.actionMasked == MotionEvent.ACTION_UP &&
                kotlin.math.abs(event.x - downX) < ViewConfiguration.get(host.context).scaledTouchSlop &&
                kotlin.math.abs(event.y - downY) < ViewConfiguration.get(host.context).scaledTouchSlop) {
                blankTap(event.x, event.y)
            }
            return true
        }
        if ((loading || preparedContent == null) && chapterTurnTarget == null) return true
        val view = surface ?: return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (turning && !committing) completeAnimation()
            view.beginScroll()
            downX = event.x - view.left
            downY = event.y - view.top
        }
        val translated = MotionEvent.obtain(event)
        try {
            translated.offsetLocation(-view.left.toFloat(), -view.top.toFloat())
            return gestures?.onTouch(view, translated) ?: true
        } finally {
            translated.recycle()
        }
    }

    fun containsVisibleDocument(x: Float, y: Float): Boolean {
        if (renderFailed) return true
        val view = surface ?: return false
        return view.clipBounds?.contains((x - view.left).toInt(), (y - view.top).toInt()) ?: true
    }

    private fun failRendering(message: String, rendererGone: Boolean = false) {
        if (closed || renderFailed) return
        renderFailed = true
        loading = false
        contentGeneration++
        highlightRevision++
        pendingReadAloud = null
        speechFollowPosition = null
        speechFollowRevision++
        cancelTurn()
        clearSelection()
        gestures?.cancel()
        gestures = null
        surface?.let { host.removeView(it); it.close() }
        surface = null
        // The native reader owns the last confirmed location, including cross-chapter moves.
        requestedPosition = position()
        openAtEnd = false
        if (rendererGone && !rendererRecoveryUsed) {
            rendererRecoveryUsed = true
            val mine = generation
            host.post {
                if (!closed && mine == generation && renderFailed) retryRendering()
            }
        } else reportError(message)
    }

    private fun retryRendering() {
        if (closed || !renderFailed) return
        val book = boundBook ?: return
        val current = textChapter ?: return
        renderFailed = false
        preparedContent = null
        requestedPosition = position()
        if (snapshot == null) {
            bookUrl = null
            bind(book, current)
        } else showChapter(book)
    }

    private fun showChapter(book: Book) {
        val current = textChapter
        cancelTurn(keepChapterTurn = chapterTurnTarget != null && chapterTurnTarget == current?.chapter?.index)
        if (current == null) return
        val session = snapshot?.publicationSession ?: return
        val sourceRevision = snapshot?.identity?.contentRevision
        val publication = session.publication
        // Let the existing reader finish its book/file initialization before starting Chromium.
        // A first native page is enough to construct the hidden surface; publication still waits
        // for the exact completed coordinates and the existing visual-state gate.
        if (surface == null) try {
            val startup = EpubStartupTiming("surface")
            attachSurface()
            startup.mark("created")
            if (!isScroll()) openingPreparation()?.cachedOpeningDocument(book, session)?.let { document ->
                val item = publication.spine.firstOrNull { publication.resourcesById[it.idref]?.location?.path == document.location.path }
                if (item != null && publication.layoutFor(item) != EpubLayout.FIXED) surface?.stage(document.location, document.html)
            }
        } catch (error: Exception) {
            failRendering(error.localizedMessage ?: "EPUB 打开失败")
            return
        }
        if (!current.isCompleted) return
        val previous = preparedChapters
        val mine = ++contentGeneration
        preparedContent = null
        selection = null
        loading = true
        val openingDocument = openingPreparation()?.openingDocument(book, session)
            ?: EpubOpeningMappingCache.openingDocument(book, current, sourceRevision)
        if (!isScroll()) openingDocument?.let { document ->
            val item = publication.spine.firstOrNull { publication.resourcesById[it.idref]?.location?.path == document.location.path }
            if (item != null && publication.layoutFor(item) != EpubLayout.FIXED) {
                surface?.stage(document.location, document.html)
            }
        }
        scope.launch {
            try {
                val content = withContext(Dispatchers.Default) {
                    previous[current.chapter.index]?.takeIf { it.chapter === current }
                        ?: EpubPreparedContent.create(book, current, session,
                            openingPreparation()?.takeSource(book, current.chapter, session), sourceRevision,
                            openingPreparation()?.takeRecord(book, current.chapter, session),
                            File(host.context.cacheDir, "epub-layout/opening-mapping.bin"))
                }
                val window = isScroll() || autoPaging() || content.documents.any { doc -> publication.spine.getOrNull(doc.spineIndex)
                    ?.let { publication.layoutFor(it) == EpubLayout.FIXED } == true }
                val target = if (openAtEnd) content.documents.last() else content.documents[content.documentAt(requestedPosition)]
                val required = EpubSpreadLayout.fixedRun(publication, target.spineIndex, host.width > host.height)
                    .firstOrNull { target.spineIndex in it.pagesInReadingOrder(false).map(EpubSpreadPage::occurrence) }
                    ?.pagesInReadingOrder(false)?.map(EpubSpreadPage::occurrence).orEmpty().toSet()
                val loaded = linkedMapOf(current.chapter.index to content)
                val failed = HashMap<Int, TextChapter>()
                val deadline = android.os.SystemClock.uptimeMillis() + 25_000
                do {
                    if (closed || mine != contentGeneration || current !== textChapter) return@launch
                    val candidates = if (window) neighboringChapters().filter { it.isCompleted } else emptyList()
                    if (candidates.isNotEmpty()) withContext(Dispatchers.Default) {
                        candidates.forEach { candidate ->
                            if (loaded[candidate.chapter.index]?.chapter !== candidate && failed[candidate.chapter.index] !== candidate) {
                                val prepared = previous[candidate.chapter.index]?.takeIf { it.chapter === candidate }
                                    ?: runCatching { EpubPreparedContent.create(book, candidate, session) }
                                        .onFailure { failed[candidate.chapter.index] = candidate }.getOrNull()
                                if (prepared != null) loaded[candidate.chapter.index] = prepared
                            }
                        }
                    }
                    val available = loaded.values.flatMap { it.documents }.map { it.spineIndex }.toSet()
                    if (available.containsAll(required)) break
                    check(android.os.SystemClock.uptimeMillis() < deadline) { "固定版式配对页尚未准备完成" }
                    // The existing reader is already loading both neighbours. Wait for that
                    // result; do not extract/process a parallel copy of their business content.
                    delay(32)
                } while (true)
                if (closed || mine != contentGeneration || current !== textChapter) return@launch
                preparedContent = content
                preparedChapters = loaded
                failedNeighbors = failed
                documents = loaded.values.sortedBy { it.chapter.chapter.index }.flatMap { it.documents }
                documentIndex = documents.indexOf(if (openAtEnd) content.documents.last() else content.documents[content.documentAt(requestedPosition)])
                val pending = pendingChapterLink?.takeIf { it.first == current.chapter.index }
                val linked = pending?.second?.let { link -> documents.indexOfFirst {
                    it.chapter.chapter.index == current.chapter.index && it.location.path == link.path
                } } ?: -1
                if (pending != null) pendingChapterLink = null
                if (linked >= 0) { openAtEnd = false; documentIndex = linked; openDocument(fragment = pending?.second?.fragment) }
                else if (openAtEnd) { openAtEnd = false; openDocument(last = true) }
                else openDocument(offset = requestedPosition)
            } catch (error: Exception) {
                if (!closed && mine == contentGeneration) {
                    failRendering(error.localizedMessage ?: "EPUB 章节解析失败")
                }
            } finally {
                if (mine == contentGeneration) {
                    loading = false
                    if (!closed && textChapter !== current) showChapter(book)
                }
            }
        }
    }

    private val noteMarker by lazy { EpubNoteMarker(host.context) }

    private fun options(last: Boolean = false): JSONObject {
        val publication = snapshot!!.publicationSession.publication
        val document = documents[documentIndex]
        val item = publication.spine.getOrNull(document.spineIndex)
        val metrics = host.resources.displayMetrics
        val reader = JSONObject()
            .put("color", String.format("#%06X", ReadBookConfig.textColor and 0xffffff))
            .put("fontSize", ChapterProvider.contentPaint.textSize / metrics.density)
            .put("weight", if (android.os.Build.VERSION.SDK_INT >= 28) ChapterProvider.contentPaint.typeface?.weight ?: 400
                else if (ChapterProvider.contentPaint.typeface?.isBold == true) 700 else 400)
            .put("italic", ChapterProvider.contentPaint.textSkewX != 0f)
            .put("align", if (ReadBookConfig.textFullJustify) "justify" else "start")
            .put("bottomJustify", ReadBookConfig.textBottomJustify)
            .put("shadow", if (ReadBookConfig.textShadow) JSONObject()
                .put("x", ReadBookConfig.shadowDx / metrics.density).put("y", ReadBookConfig.shadowDy / metrics.density)
                .put("radius", ReadBookConfig.shadowRadius / metrics.density)
                .put("color", EpubCharStyles.cssColor(ReadBookConfig.textShadowColor)) else JSONObject.NULL)
            .put("letterSpacing", ReadBookConfig.letterSpacing)
            .put("lineSpacing", ReadBookConfig.lineSpacingExtra / 10f)
            .put("paragraphSpacing", ReadBookConfig.paragraphSpacing / 10f)
            .put("hasFont", readerFontBytes != null)
            .put("fontFamily", when (AppConfig.systemTypefaces) { 1 -> "serif"; 2 -> "monospace"; else -> "sans-serif" })
            .put("indent", ReadBookConfig.paragraphIndent.length)
            .put("lineHeight", ChapterProvider.contentPaintTextHeight * ReadBookConfig.lineSpacingExtra / 10f / metrics.density)
            .put("paragraphSpacingPx", ChapterProvider.contentPaintTextHeight * ReadBookConfig.paragraphSpacing / 10f / metrics.density)
            .put("indentPx", ChapterProvider.indentCharWidth * ReadBookConfig.paragraphIndent.length / metrics.density)
            .put("title", JSONObject()
                .put("color", EpubCharStyles.cssColor(ReadBookConfig.resolvedTitleColor))
                .put("fontSize", ChapterProvider.titlePaint.textSize / metrics.density)
                .put("weight", if (android.os.Build.VERSION.SDK_INT >= 28) ChapterProvider.titlePaint.typeface?.weight ?: 400
                    else if (ChapterProvider.titlePaint.typeface?.isBold == true) 700 else 400)
                .put("hasFont", titleFontBytes != null && titleFontBytes !== readerFontBytes)
                .put("align", if (ReadBookConfig.titleMode == 1) "center" else "start")
                .put("lineHeight", ChapterProvider.titlePaintTextHeight * ChapterProvider.titleLineSpacingExtra / metrics.density)
                .put("subLineHeight", (ChapterProvider.titlePaintFontMetrics.bottom - ChapterProvider.titlePaintFontMetrics.top) *
                    ReadBookConfig.titleSegScaling * ChapterProvider.titleLineSpacingExtra / metrics.density)
                .put("top", ReadBookConfig.titleTopSpacing).put("bottom", ReadBookConfig.titleBottomSpacing))
        val fixed = item?.let { publication.layoutFor(it) == EpubLayout.FIXED } ?: false
        val value = JSONObject().put("fixed", fixed)
            .put("fullLineUnderline", if (ReadBookConfig.fullLineUnderlineEnabled) JSONObject()
                .put("color", EpubCharStyles.cssColor(ReadBookConfig.resolvedUnderlineColor))
                .put("width", ReadBookConfig.underlineHeight / metrics.density)
                .put("offset", ReadBookConfig.underlinePadding - 10)
                .put("extend", ReadBookConfig.underlineExtend)
                .put("dash", if (ReadBookConfig.dottedLine && !AppConfig.isEInkMode) JSONArray()
                    .put(ReadBookConfig.dottedBase / metrics.density).put(ReadBookConfig.dottedRatio / metrics.density)
                    else JSONObject.NULL) else JSONObject.NULL)
            .put("features", JSONObject(EpubLayoutPreferences.read(boundBook?.bookUrl)))
            .put("flow", if (isScroll()) "scrolled-doc" else publication.renditionProperty(item, "flow", "paginated"))
            .put("last", last)
            .put("startFragment", document.location.fragment ?: JSONObject.NULL)
            .put("endFragment", document.endFragment ?: JSONObject.NULL)
            .put("readerDefaults", reader)
            .put("readerStyle", if (publisherStyle()) JSONObject.NULL else reader)
            .put("index", documentIndex).put("readerInsets", readerInsets).put("chromeInsets", chromeInsets)
        if (fixed) {
            val plan = EpubSpreadLayout.fixedRun(publication, document.spineIndex, host.width > host.height)
            val spreads = EpubSpreadLayout.loadedWindow(plan, documents.map { it.spineIndex }.toSet())
            check(spreads.any { spread -> spread.pagesInReadingOrder(false).any { it.occurrence == document.spineIndex } }) {
                "固定版式配对页尚未准备完成"
            }
            value.put("containerMode", "spread").put("rtl", publication.pageProgressionDirection == EpubPageProgressionDirection.RTL)
                .put("spreads", JSONArray().apply { spreads.forEach { spread ->
                    fun index(page: EpubSpreadPage?) = page?.let { documents.indexOfFirst { doc -> doc.spineIndex == it.occurrence }.takeIf { it >= 0 } }
                    put(JSONObject().put("left", index(spread.left) ?: JSONObject.NULL)
                        .put("right", index(spread.right) ?: JSONObject.NULL).put("center", index(spread.center) ?: JSONObject.NULL))
                } })
        } else if (isScroll()) value.put("containerMode", "continuous")
        val highlights = highlightData(value.has("containerMode"))
        val notes = JSONArray().apply { for (i in 0 until highlights.length()) {
            val mark = highlights.getJSONObject(i)
            if (mark.optBoolean("note")) put(JSONObject().put("id", mark.getString("id"))
                .put("end", mark.getInt("to")).put("occurrence", mark.getInt("occurrence")))
        } }
        val titleSegments = JSONObject().apply { documents.forEachIndexed { index, doc ->
            put(index.toString(), JSONArray().apply { doc.titles.forEach { title ->
                val ranges = title.segments(ReadBookConfig.titleSegType, ReadBookConfig.titleSegDistance,
                    ReadBookConfig.titleSegFlag, ReadBookConfig.titleSegScaling)
                for (i in 0 until ranges.length()) put(ranges.getJSONObject(i))
            } })
        } }
        return value.put("highlights", highlights).put("noteMarkers", notes).put("titleSegmentsByDocument", titleSegments)
            .put("titleSegments", titleSegments.optJSONArray(documentIndex.toString()))
            .put("noteMarkerStyle", if (notes.length() > 0) noteMarker.style() ?: JSONObject.NULL else JSONObject.NULL)
            .put("selectionTransparent", selectionTransparent)
            .put("selectionColor", EpubCharStyles.cssColor(ContentTextView.selectionHighlightColor(host.context)))
    }

    fun setSelectionEnabled(enabled: Boolean) {
        if (selectionEnabled == enabled) return
        selectionEnabled = enabled
        if (!enabled) { gestures?.cancel(); clearSelection() }
    }

    fun setSelectionHighlightTransparent(transparent: Boolean) {
        selectionTransparent = transparent
        surface?.setSelectionHighlightTransparent(transparent)
    }

    fun setTextHighlights(bookmarks: List<Bookmark>) {
        if (textHighlights == bookmarks) return
        fun noteGeometry(values: List<Bookmark>) = ReadNoteMarkerStyle.notes(values)
            .map { Triple(it.time, it.endChapterIndex, it.endChapterPos) }
        val reflow = noteGeometry(textHighlights) != noteGeometry(bookmarks)
        textHighlights = bookmarks.map { it.copy() }
        cancelTurn()
        if (snapshot != null && documents.getOrNull(documentIndex) != null) {
            val value = options()
            if (reflow) surface?.restyle(value)
            else surface?.setHighlights(value.getJSONArray("highlights"))
            scheduleWarm()
        }
    }

    fun syncAloudHighlight() {
        // Speech paint must not invalidate the frames of an accepted page turn.
        // onReady applies the latest native highlight after that turn commits.
        if (turning || pendingTurns.isNotEmpty()) return
        val current = textChapter ?: return
        val view = surface ?: return
        if (view.state == null) return
        val line = if (BaseReadAloudService.isPlay())
            current.getPageByReadPos(position())?.lines?.firstOrNull { it.isReadAloud } else null
        val paragraph = line?.let { active -> current.paragraphs.firstOrNull { it.num == active.paragraphNum } }
        val ranges = JSONArray()
        documents.forEachIndexed { index, document ->
            if (paragraph != null && document.chapter === current) {
                val from = maxOf(document.first, paragraph.chapterPosition)
                val to = minOf(document.last, paragraph.chapterPosition + paragraph.text.length)
                if (to > from) ranges.put(JSONObject().put("index", index).put("from", from).put("to", to)
                    .put("color", String.format("#%06X", ReadBookConfig.textAccentColor and 0xffffff))
                    .put("underline", AppConfig.isEInkMode))
            }
        }
        val paintSignature = ranges.toString()
        val signature = view.state?.optString("token") + paintSignature
        if (signature == aloudSignature) return
        aloudSignature = signature
        val paintChanged = paintSignature != aloudPaintSignature
        aloudPaintSignature = paintSignature
        view.interact(JSONObject().put("action", "aloud").put("ranges", ranges)
            .put("range", (0 until ranges.length()).map { ranges.getJSONObject(it) }
                .firstOrNull { it.optInt("index") == documentIndex } ?: JSONObject.NULL)) {}
        // A new page revision still needs the overlay, but unchanged paint does
        // not invalidate the already captured neighboring pages.
        if (paintChanged && !pageTextMeasuring) {
            clearFrames(keepPreparedDocument = true)
            scheduleWarm()
        }
    }

    fun followReadAloud(position: Int) {
        // A manual turn already owns the next viewport. Its normal commit will
        // let ReadBook decide how playback follows the new content position.
        if (closed || turning || pendingTurns.isNotEmpty() || chapterTurnTarget != null) return
        speechFollowPosition = position
        requestedPosition = position
        val view = surface ?: return
        if (view.state == null || loading) return
        val target = documents.indexOfLast { it.chapter === textChapter && it.first <= position }
        if (target >= 0 && target != documentIndex) { jump(position); return }
        val mine = ++speechFollowRevision
        view.interact(JSONObject().put("action", "containsPosition").put("offset", position).put("index", documentIndex)) {
            if (mine != speechFollowRevision || speechFollowPosition != position) return@interact
            // HTTP playback seeks through integer milliseconds, then estimates a
            // text position from them. The round trip can report the character
            // immediately before the page start before advancing to that start.
            // Native paging does not turn back for this boundary update either.
            // Consume the progress normally, but keep the visible EPUB page.
            val atLeadingBoundary = reportedPosition > 0 && position == reportedPosition - 1
            if (it?.optBoolean("mapped") == true && !it.optBoolean("visible") && !atLeadingBoundary) jump(position)
            syncAloudHighlight()
        }
    }

    fun stopFollowingReadAloud() {
        speechFollowPosition = null
        speechFollowRevision++
    }

    private fun highlightData(container: Boolean): JSONArray = JSONArray().apply {
        val book = boundBook ?: return@apply
        val noteIds = ReadNoteMarkerStyle.notes(textHighlights).mapTo(HashSet()) { it.time }
        documents.forEachIndexed { index, document ->
            if (!container && index != documentIndex) return@forEachIndexed
            val chapterIndex = document.chapter.chapter.index
            textHighlights.asSequence().filter {
                it.bookName == book.name && it.bookAuthor == book.author && it.coversChapter(chapterIndex)
            }.sortedBy(Bookmark::time).forEach { mark ->
                val from = maxOf(document.first, if (chapterIndex == mark.chapterIndex) mark.chapterPos else 0)
                val to = minOf(document.last, if (chapterIndex == mark.endChapterIndex) mark.endChapterPos else document.text.length)
                if (to > from) put(JSONObject().put("id", mark.time.toString()).put("occurrence", index)
                    .put("from", from).put("to", to).put("style", mark.highlightStyle)
                    .put("color", String.format("#%06X", mark.highlightColor and 0xffffff))
                    .put("note", mark.time in noteIds && chapterIndex == mark.endChapterIndex && to == mark.endChapterPos))
            }
        }
    }

    private fun openDocument(last: Boolean = false, fragment: String? = null, offset: Int? = null) {
        val document = documents.getOrNull(documentIndex) ?: return
        val publication = snapshot?.publicationSession?.publication ?: return
        val pair = EpubSpreadLayout.fixedRun(publication, document.spineIndex, host.width > host.height)
            .firstOrNull { spread -> spread.pagesInReadingOrder(false).any { it.occurrence == document.spineIndex } }
        val available = documents.map { it.spineIndex }.toSet()
        if (pair != null && pair.pagesInReadingOrder(false).any { it.occurrence !in available }) {
            val delta = document.chapter.chapter.index - (textChapter?.chapter?.index ?: document.chapter.chapter.index)
            if (delta != 0) { openAtEnd = last; chapterTurn(delta) }
            else {
                requestedPosition = offset ?: document.first
                openAtEnd = last
                boundBook?.let(::showChapter)
            }
            return
        }
        val view = surface ?: return
        view.documents(documents)
        view.content(document.location.path, document.html, document.payload)
        view.open(document.location.copy(fragment = fragment ?: document.location.fragment), options(last).apply {
            put("fragment", fragment ?: JSONObject.NULL)
            offset?.let { put("textOffset", it) }
        })
    }

    fun jump(offset: Int) {
        if (renderFailed) { retryRendering(); return }
        val content = preparedContent ?: return
        requestedPosition = offset
        cancelTurn()
        clearSelection()
        val target = documents.indexOf(content.documents[content.documentAt(offset)])
        val document = documents.getOrNull(target)
        val publication = snapshot?.publicationSession?.publication
        if (document != null && publication != null) {
            val pair = EpubSpreadLayout.fixedRun(publication, document.spineIndex, host.width > host.height)
                .firstOrNull { spread -> spread.pagesInReadingOrder(false).any { it.occurrence == document.spineIndex } }
            val available = documents.map { it.spineIndex }.toSet()
            if (pair != null && pair.pagesInReadingOrder(false).any { it.occurrence !in available }) {
                boundBook?.let(::showChapter)
                return
            }
        }
        if (target != documentIndex) { documentIndex = target; openDocument(offset = offset) }
        else surface?.move(0, JSONObject().put("textOffset", offset).put("index", target))
    }

    private fun hitSelection(x: Float, y: Float, initial: Boolean) {
        val view = surface ?: return
        if (initial) { cancelTurn(); clearSelection(); selectionFinished = false }
        val mine = ++selectionRevision
        view.interact(JSONObject().put("action", "hit").put("nearest", !initial)
            .put("x", css(x)).put("y", css(y))) { result ->
            if (mine != selectionRevision || result == null) return@interact
            if (initial && !result.isNull("image")) {
                view.resolve(result.getString("image"))?.let {
                    imageLongPress(x + view.left, y + view.top, it.path)
                }
                return@interact
            }
            if (!selectionEnabled) return@interact
            val document = documents.getOrNull(result.optInt("index", documentIndex)) ?: return@interact
            val text = document.text
            val offset = result.optInt("offset", -1)
            if (offset !in 0..text.length) return@interact
            val chapterIndex = document.chapter.chapter.index
            if (initial) {
                if (text.isEmpty()) return@interact
                val at = offset.coerceAtMost(text.lastIndex)
                val from = if (at == 0) 0 else text.lastIndexOf('\n', at - 1) + 1
                val to = text.indexOf('\n', at).takeIf { it >= 0 } ?: text.length
                val word = readerWordBoundary(text.substring(from, to), at - from) ?: return@interact
                selectionAnchor = chapterIndex to from + word.first
                setSelectionEnd(chapterIndex, from + word.last + 1)
            } else setSelectionEnd(chapterIndex, offset)
        }
    }

    private fun setSelectionEnd(chapterIndex: Int, offset: Int) {
        val anchor = selectionAnchor ?: return
        val point = chapterIndex to offset
        val forward = point.first > anchor.first || point.first == anchor.first && point.second >= anchor.second
        val from = if (forward) anchor else point
        val to = if (forward) point else anchor
        val chapters = (from.first..to.first).map { preparedChapters[it] ?: return }
        val text = chapters.joinToString("") { content ->
            val index = content.chapter.chapter.index
            content.mapped.text.substring(if (index == from.first) from.second else 0,
                if (index == to.first) to.second else content.mapped.text.length)
        }
        if (text.isEmpty()) return
        selection = ReaderSelection(from.first, from.second, chapters.first().chapter.title, text, to.first, to.second)
        renderSelection()
    }

    private fun renderSelection() {
        val value = selection ?: return
        val view = surface ?: return
        val mine = selectionRevision
        val ranges = JSONArray()
        documents.forEachIndexed { index, document ->
            val chapterIndex = document.chapter.chapter.index
            if (chapterIndex in value.chapterIndex..value.endChapterIndex) {
                val from = maxOf(document.first, if (chapterIndex == value.chapterIndex) value.chapterPosition else 0)
                val to = minOf(document.last, if (chapterIndex == value.endChapterIndex) value.endChapterPosition else document.text.length)
                if (to > from) ranges.put(JSONObject().put("index", index).put("from", from).put("to", to))
            }
        }
        view.interact(JSONObject().put("action", "setRanges").put("ranges", ranges)) { result ->
            if (mine != selectionRevision || selection !== value) return@interact
            result?.optJSONObject("selection")?.let { geometry ->
                geometry.put("leftPx", view.left).put("topPx", view.top)
                selectionChanged(geometry, selectionFinished)
            }
        }
        clearFrames()
    }

    fun finishSelectionDrag() {
        selectionFinished = true
        renderSelection()
    }

    fun showSearchResult(chapter: TextChapter, from: Int, length: Int) {
        stopFollowingReadAloud()
        cancelTurn()
        clearSelection()
        if (from < 0 || length <= 0 || from > chapter.getContent().length - length) return
        requestedPosition = from
        val canSeek = !loading && preparedContent?.chapter === chapter && surface?.state != null
        if (canSeek) jump(from)
        // The chapter may still be preparing its XHTML. Apply only after the matching
        // document reports ready, never to the previous chapter's visible surface.
        pendingSearchSelection = SearchSelection(chapter, from, from + length, !canSeek)
    }

    private fun applySearchSelection() {
        val request = pendingSearchSelection ?: return
        val document = documents.getOrNull(documentIndex) ?: return
        if (document.chapter !== request.chapter || textChapter !== request.chapter) return
        val view = surface ?: return
        if (request.needsSeek) {
            request.needsSeek = false
            jump(request.from)
            pendingSearchSelection = request
            return
        }
        view.interact(JSONObject().put("action", "setRange").put("index", documentIndex)
            .put("from", request.from).put("to", request.to).put("anchor", request.from)) { result ->
            if (pendingSearchSelection !== request) return@interact
            pendingSearchSelection = null
            clearFrames()
            if (result?.has("error") == true) reportError(result.optString("error"))
            updateSelection(result, false)
        }
    }

    private fun updateSelection(result: JSONObject?, finished: Boolean) {
        val document = documents.getOrNull(result?.optInt("index", documentIndex) ?: documentIndex) ?: return
        val current = document.chapter
        val value = result?.optJSONObject("selection")
        val from = value?.optInt("from", -1) ?: -1
        val to = value?.optInt("to", -1) ?: -1
        val text = document.text
        selection = if (from >= 0 && to > from && to <= text.length) ReaderSelection(
            current.chapter.index, from, current.title, text.substring(from, to),
            current.chapter.index, to,
        ) else null
        selectionChanged(if (selection != null) value?.apply {
            put("leftPx", surface?.left ?: 0).put("topPx", surface?.top ?: 0)
        } else null, finished)
    }

    fun moveSelection(start: Boolean, x: Float, y: Float) {
        val view = surface ?: return
        val selected = selection ?: return
        if (selectionFinished) selectionAnchor = null
        if (selectionAnchor == null) selectionAnchor = if (start)
            selected.endChapterIndex to selected.endChapterPosition else selected.chapterIndex to selected.chapterPosition
        selectionFinished = false
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        hitSelection(x - location[0], y - location[1], initial = false)
    }

    fun clearSelection() {
        selectionRevision++
        selectionAnchor = null
        selectionFinished = false
        if (selection != null || pendingSearchSelection != null) clearFrames()
        pendingSearchSelection = null
        selection = null
        surface?.interact(JSONObject().put("action", "clear")) {}
        selectionChanged(null, false)
    }

    override val selectedText: String get() = selection?.text.orEmpty()
    fun visiblePosition(): Pair<Int, Int>? {
        if (closed || loading || surface?.state == null) return null
        val document = documents.getOrNull(documentIndex) ?: return null
        return document.chapter.chapter.index to reportedPosition.coerceIn(document.first, document.last)
    }

    fun whenChapterReady(index: Int, action: () -> Unit) {
        if (readyForAutoPage() && preparedContent?.chapter?.chapter?.index == index) action()
        else pendingReadAloud = index to action
    }

    /** Explicit page-based reading only; normal speech never asks for document measurement. */
    fun prepareReadAloudPages(complete: () -> Unit) {
        val current = textChapter ?: return
        val view = surface ?: return
        if (!readyForAutoPage()) return
        cancelTurn()
        val request = ++pageTextRequest
        pageTextMeasuring = true
        val content = contentGeneration
        val revision = styleGeneration
        val bounds = HashMap<Int, Pair<String?, List<Int>>>()
        fun valid() = !closed && request == pageTextRequest && current === textChapter &&
            content == contentGeneration && revision == styleGeneration
        fun collect(result: JSONObject?) {
            val pages = result?.optJSONArray("pages") ?: return
            for (i in 0 until pages.length()) {
                val row = pages.getJSONObject(i)
                val index = row.getInt("index")
                val doc = documents.getOrNull(index) ?: continue
                val starts = row.getJSONArray("starts")
                bounds[index] = (if (row.has("group")) row.getString("group") else null) to
                    (listOf(doc.first) + (0 until starts.length()).map { starts.getInt(it) }.filter { it in doc.first..doc.last })
            }
        }
        fun next() {
            if (!valid()) return
            val missing = documents.indices.firstOrNull { documents[it].chapter === current && it !in bounds }
            if (missing == null) {
                val groups = HashMap<String, Int>()
                val starts = ArrayList<Int>()
                bounds.forEach { (index, value) ->
                    if (documents[index].chapter === current) {
                        val group = value.first
                        if (group == null) starts.addAll(value.second)
                        else groups[group] = minOf(groups[group] ?: Int.MAX_VALUE, value.second.minOrNull() ?: 0)
                    }
                }
                starts.addAll(groups.values)
                current.layoutReadAloudPages = io.legado.app.help.tts.LayoutReadAloudPageText(current.getContent(), starts)
                pageTextMeasuring = false
                preparation?.close(); preparation = null
                complete()
                scheduleWarm()
                return
            }
            val previousIndex = documentIndex
            documentIndex = missing
            val configuration = options().put("page", 0)
            documentIndex = previousIndex
            val chrome = nativeFrame()
            preparer(view, chrome).measurePageBounds(chrome, documents[missing].location, configuration) { result ->
                if (!valid()) return@measurePageBounds
                collect(result)
                if (missing !in bounds) { cancelTurn(); reportError("EPUB 页面范围尚未就绪"); return@measurePageBounds }
                next()
            }
            chrome.recycle()
        }
        view.interact(JSONObject().put("action", "pageBounds").put("index", documentIndex)) { result ->
            if (valid()) { collect(result); next() }
        }
    }
    override fun highlightSelection() = selection
    override fun bookmarkSelection(): ReaderSelection? {
        selection?.let { return it }
        val current = textChapter ?: return null
        val text = preparedContent?.mapped?.text ?: return null
        val start = reportedPosition.coerceIn(0, text.length)
        return ReaderSelection(current.chapter.index, start, current.title,
            text.substring(start).substringBefore('\n'))
    }
    override fun contentEditTarget(highlight: ReaderSelection?): ReaderContentEditTarget? {
        val range = highlight ?: selection ?: return null
        val current = preparedChapters[range.chapterIndex]?.chapter ?: return null
        val lines = current.pages.asSequence().flatMap { it.lines.asSequence() }
        val line = lines.lastOrNull { it.chapterPosition <= range.chapterPosition } ?: return null
        val paragraphStart = lines.firstOrNull { it.sourceParagraphIndex == line.sourceParagraphIndex }?.chapterPosition ?: return null
        return ReaderContentEditTarget(range.chapterIndex, current.title, line.sourceParagraphIndex.coerceAtLeast(0),
            (range.chapterPosition - paragraphStart).coerceAtLeast(0), range.text.substringBefore('\n').take(128))
    }

    fun refreshHighlightRules(): Boolean {
        if (closed || !active) return false
        val mine = ++highlightRevision
        pendingHighlightUpdate = true
        val current = preparedContent ?: return true
        if (loading || surface?.state == null) return true
        val previous = preparedChapters
        val content = contentGeneration
        val matcher = io.legado.app.ui.book.read.page.provider.ReadHighlightMatcher(ReadBookConfig.highlightRules)
        cancelTurn()
        scope.launch {
            try {
                val updated = withContext(Dispatchers.Default) {
                    previous.mapValues { (_, chapter) -> chapter.withHighlightStyles(matcher) }
                }
                if (closed || mine != highlightRevision || content != contentGeneration || preparedContent !== current) return@launch
                preparedChapters = updated
                preparedContent = updated[current.chapter.chapter.index]
                documents = updated.values.sortedBy { it.chapter.chapter.index }.flatMap { it.documents }
                pendingHighlightUpdate = false
                surface?.updateStyles(documents)
            } catch (error: Exception) {
                if (!closed && mine == highlightRevision) {
                    pendingHighlightUpdate = false
                    reportError(error.localizedMessage ?: "高亮样式更新失败")
                }
            }
        }
        return true
    }

    fun restyle() {
        cancelTurn()
        val mine = ++styleGeneration
        val path = ReadBookConfig.textFont
        val titlePath = ReadBookConfig.titleFont.ifBlank { path }
        if (readerFontPath != path || titleFontPath != titlePath) {
            scope.launch {
                val (font, titleFont) = withContext(Dispatchers.IO) {
                    val bodyFont = runCatching { readEpubReaderFont(host.context, path) }.getOrNull()
                    bodyFont to if (titlePath == path) bodyFont else
                        runCatching { readEpubReaderFont(host.context, titlePath) }.getOrNull()
                }
                if (closed || mine != styleGeneration) return@launch
                readerFontPath = path
                readerFontBytes = font
                titleFontPath = titlePath
                titleFontBytes = titleFont
                surface?.readerFont(font)
                surface?.titleFont(titleFont)
                if (renderFailed) retryRendering()
                else if (preparedContent != null && !loading) openDocument(offset = position())
            }
        } else if (renderFailed) retryRendering()
        else if (preparedContent != null && !loading) openDocument(offset = position())
    }

    private fun interactReady(value: JSONObject, callback: (JSONObject?) -> Unit) {
        if (renderFailed) { callback(null); return }
        val view = surface ?: return
        val deliver: (JSONObject?) -> Unit = { result ->
            callback(result)
            if (!turning) host.post { continueTurns() }
        }
        if (loading || view.readyState == null || committing) {
            pendingInteractions.addLast { view.interact(value, deliver) }
        } else view.interact(value, deliver)
    }

    private fun continueTurns() {
        if (closed || loading || turning || surface?.readyState == null) return
        when {
            pendingTurns.isNotEmpty() -> turn(pendingTurns.removeFirst())
            pendingInteractions.isNotEmpty() -> pendingInteractions.removeFirst().invoke()
            else -> scheduleWarm()
        }
    }

    private fun scheduleWarm() {
        host.removeCallbacks(warmTask)
        host.postOnAnimation(warmTask)
    }

    private fun clearFrames(keepPreparedDocument: Boolean = false) {
        host.removeCallbacks(warmTask)
        frames.clear()
        // Paint changes do not change document geometry. Let an in-flight warm
        // capture finish; its paint signature below prevents publishing old ink.
        if (keepPreparedDocument) return
        warmWatchdog.cancel()
        frameEpoch++
        frameDocument = -1
        preparationWorking = false
        preparation?.close()
        preparation = null
    }

    private fun preparer(current: EpubLayoutSurface, chrome: Bitmap): EpubPreparedTurn =
        preparation ?: EpubPreparedTurn(host.context, snapshot!!.publicationSession,
            chrome.width, chrome.height, Rect(current.left, current.top, current.right, current.bottom),
            onError = { error ->
                val requested = turning || pendingTurns.isNotEmpty() || pageTextMeasuring
                clearFrames()
                if (requested) {
                    cancelTurn()
                    reportError(error.localizedMessage ?: "EPUB 翻页画面准备失败")
                }
            },
            setContent = { view, location, configuration -> documents.getOrNull(configuration.optInt("index", -1))
                ?.takeIf { it.location.path == location.path }?.let {
                    view.readerFont(readerFontBytes)
                    view.titleFont(titleFontBytes)
                    view.documents(documents)
                    view.content(it.location.path, it.html, it.payload)
                } },
        ).also { preparation = it }

    private fun warmFrames() {
        val current = surface ?: return
        val state = current.readyState ?: return
        if (closed || loading || turning || committing || preparationWorking || pageTextMeasuring || preparedContent == null ||
            !animationsEnabled() || isScroll() || state.optString("mode") == "FIXED" ||
            (state.optJSONArray("media")?.length() ?: 0) > 0 ||
            current.isLayoutRequested ||
            current.width <= 0 || current.height <= 0) return
        val page = state.optInt("pageIndex")
        val center = anticipatedPage ?: page
        val count = state.optInt("pageCount")
        // At most four full-resolution frames, with fewer on large displays (48 MiB budget).
        val capacity = (48L * 1024 * 1024 / (current.width.toLong() * current.height * 4)).toInt().coerceAtMost(4)
        if (capacity < 2) return
        val window = listOf(center, center + lastDirection, center - lastDirection, center + 2 * lastDirection)
            .filter { it in 0 until count }.distinct().take(capacity)
        if (frameDocument != documentIndex) { clearFrames(); frameDocument = documentIndex }
        frames.retain(window.toSet())
        val target = window.firstOrNull { frames[it] == null } ?: return
        val mine = frameEpoch
        val doc = documentIndex
        val paint = aloudPaintSignature
        preparationWorking = true
        warmWatchdog.arm {
            val requested = pendingTurns.isNotEmpty()
            clearFrames()
            if (requested) {
                cancelTurn()
                reportError("EPUB 翻页画面准备超时，请重试")
            }
        }
        current.captureOptions { captured ->
            if (closed || mine != frameEpoch) return@captureOptions
            if (captured == null) {
                warmWatchdog.cancel()
                preparationWorking = false
                if (pendingTurns.isNotEmpty()) host.post { continueTurns() }
                return@captureOptions
            }
            val config = JSONObject(captured.toString()).apply {
                remove("location"); remove("textOffset"); remove("spreadPage")
                put("page", target); put("last", false); put("preservePosition", false)
            }
            val chrome = nativeFrame()
            val prepared = preparer(current, chrome)
            prepared.capturePage(chrome, documents[doc].location, config) { frame ->
                if (closed || mine != frameEpoch) { frame.close(); return@capturePage }
                warmWatchdog.cancel()
                preparationWorking = false
                if (paint == aloudPaintSignature) frames.put(target, frame) else frame.close()
                if (pendingTurns.isNotEmpty() && !turning) host.post { continueTurns() }
                else scheduleWarm()
            }
            chrome.recycle()
        }
    }

    private fun drawFrame(canvas: Canvas, frame: EpubCapturedFrame, current: EpubLayoutSurface) {
        val saved = canvas.save()
        canvas.translate(current.left.toFloat(), current.top.toFloat())
        frame.clip?.let(canvas::clipRect)
        canvas.drawBitmap(frame.bitmap, 0f, 0f, null)
        canvas.restoreToCount(saved)
    }

    fun turn(direction: Int) {
        speechFollowRevision++
        speechFollowPosition = null
        if (closed) return
        if (renderFailed) { retryRendering(); return }
        if (loading || preparedContent == null) {
            if (chapterTurnTarget != null) pendingTurns.addLast(direction)
            return
        }
        if (turning) {
            pendingTurns.addLast(direction)
            if (!committing) completeAnimation()
            return
        }
        val currentSurface = surface ?: return
        val state = currentSurface.readyState
        if (state == null) {
            pendingTurns.addLast(direction)
            return
        }
        if (isScroll() && state.optBoolean("scrolled")) {
            if (state.optBoolean(if (direction > 0) "atEnd" else "atStart")) {
                openAtEnd = direction < 0
                turnChapter(direction)
            }
            else surface?.advance(direction)
            return
        }
        val index = state.optInt("pageIndex") + direction
        val targetDocument = if (index in 0 until state.optInt("pageCount")) documentIndex
        else if (state.optString("mode") == "FIXED") {
            val spans = options().optJSONArray("spreads")
            val indices = buildList { if (spans != null) for (i in 0 until spans.length()) {
                val span = spans.getJSONObject(i)
                for (slot in listOf("left", "right", "center")) if (!span.isNull(slot)) add(span.getInt(slot))
            } }
            (if (direction > 0) indices.maxOrNull() else indices.minOrNull())?.plus(direction) ?: documentIndex + direction
        } else documentIndex + direction
        if (targetDocument !in documents.indices) {
            openAtEnd = direction < 0
            val boundary = documents.getOrNull(targetDocument - direction)
            val delta = if (state.optString("mode") == "FIXED" && boundary != null)
                boundary.chapter.chapter.index + direction - (chapter?.index ?: boundary.chapter.chapter.index)
            else direction
            turnChapter(delta)
            return
        }
        if (targetDocument != documentIndex) {
            val target = documents[targetDocument]
            val publication = snapshot!!.publicationSession.publication
            val pair = EpubSpreadLayout.fixedRun(publication, target.spineIndex, host.width > host.height)
                .firstOrNull { spread -> spread.pagesInReadingOrder(false).any { it.occurrence == target.spineIndex } }
            val available = documents.map { it.spineIndex }.toSet()
            if (pair != null && pair.pagesInReadingOrder(false).any { it.occurrence !in available }) {
                openAtEnd = direction < 0
                val delta = target.chapter.chapter.index - (chapter?.index ?: target.chapter.chapter.index)
                if (delta != 0) turnChapter(delta)
                else {
                    requestedPosition = target.first
                    boundBook?.let(::showChapter)
                }
                return
            }
        }
        val current = surface ?: return
        if (current.width <= 0 || current.height <= 0) return
        val originalIndex = documentIndex
        val beforeFrame = if (frameDocument == originalIndex) frames[state.optInt("pageIndex")] else null
        val afterFrame = if (frameDocument == targetDocument && targetDocument == originalIndex) frames[index] else null
        if (animationsEnabled() && (beforeFrame == null || afterFrame == null) && preparationWorking) {
            pendingTurns.addLast(direction)
            return
        }
        val requestedAt = android.os.SystemClock.uptimeMillis()
        val requestEpoch = frameEpoch
        lastDirection = direction
        turning = true
        val mine = generation
        turnTimeout = Runnable {
            if (turning) {
                cancelTurn()
                reportError("EPUB 翻页画面准备超时")
            }
        }.also { host.postDelayed(it, 25_000) }
        if (!animationsEnabled()) {
            committing = true
            documentIndex = targetDocument
            if (targetDocument == originalIndex) current.move(index)
            else openDocument(last = direction < 0)
            return
        }
        if (beforeFrame != null && afterFrame != null) {
            val chrome = nativeFrame()
            val after = chrome.copy(Bitmap.Config.ARGB_8888, true)
            drawFrame(Canvas(chrome), beforeFrame, current)
            drawFrame(Canvas(after), afterFrame, current)
            anticipatedPage = index
            if (io.legado.app.BuildConfig.DEBUG) android.util.Log.d("EpubTurnFrames",
                "cached=true prepareMs=${android.os.SystemClock.uptimeMillis() - requestedAt}")
            animate(chrome, after, direction) {
                if (!closed && turning && requestEpoch == frameEpoch) { committing = true; current.move(index) }
            }
            scheduleWarm()
            return
        }
        preparationWorking = true
        current.captureOptions { beforeOptions ->
            if (closed || mine != generation || requestEpoch != frameEpoch || !turning) return@captureOptions
            if (beforeOptions == null) {
                cancelTurn()
                reportError("EPUB 页面状态尚未就绪，请重试翻页")
                return@captureOptions
            }
            val chrome = nativeFrame()
            preparationFrame = chrome
            val prepared = preparer(current, chrome)
            documentIndex = targetDocument
            val targetOptions = options(last = targetDocument != originalIndex && direction < 0)
                .put("page", if (targetDocument == originalIndex) index else 0)
                .put("aloud", beforeOptions.optJSONObject("aloud") ?: JSONObject.NULL)
            if (state.optString("mode") == "FIXED") targetOptions.put("spreadPage", index)
            documentIndex = originalIndex
            val from = documents[originalIndex].location
            val to = documents[targetDocument].location
            val deliver: (EpubCapturedFrame, EpubCapturedFrame) -> Unit = delivery@ { oldBody, newBody ->
                if (closed || mine != generation || requestEpoch != frameEpoch || !turning) {
                    // Cached frames remain owned by the window, including cancellation.
                    if (oldBody !== beforeFrame) oldBody.close()
                    if (newBody !== afterFrame) newBody.close()
                    return@delivery
                }
                val after = chrome.copy(Bitmap.Config.ARGB_8888, true)
                drawFrame(Canvas(chrome), oldBody, current)
                drawFrame(Canvas(after), newBody, current)
                preparationWorking = false
                if (targetDocument == originalIndex && state.optString("mode") != "FIXED" &&
                    (state.optJSONArray("media")?.length() ?: 0) == 0 &&
                    current.width.toLong() * current.height * 8 <= 48L * 1024 * 1024) {
                    frameDocument = originalIndex
                    if (oldBody !== beforeFrame) frames.put(state.optInt("pageIndex"), oldBody)
                    if (newBody !== afterFrame) frames.put(index, newBody)
                    anticipatedPage = index
                } else {
                    if (oldBody !== beforeFrame) oldBody.close()
                    if (newBody !== afterFrame) newBody.close()
                }
                preparationFrame = null // Ownership moves to the existing animation host.
                animate(chrome, after, direction) {
                    if (!closed && mine == generation && requestEpoch == frameEpoch && turning) {
                        committing = true
                        documentIndex = targetDocument
                        if (targetDocument == originalIndex) current.move(index)
                        else {
                            val document = documents[targetDocument]
                            current.content(to.path, document.html, document.payload)
                            current.open(to, targetOptions)
                        }
                    }
                }
                if (io.legado.app.BuildConfig.DEBUG) android.util.Log.d("EpubTurnFrames",
                    "cached=false prepareMs=${android.os.SystemClock.uptimeMillis() - requestedAt}")
                scheduleWarm()
            }
            // A click can arrive between two warm captures. Only render the missing
            // viewport; recapturing the cached side adds another compositor round trip.
            when {
                beforeFrame != null -> prepared.capturePage(chrome, to, targetOptions) { deliver(beforeFrame, it) }
                afterFrame != null -> prepared.capturePage(chrome, from, beforeOptions) { deliver(it, afterFrame) }
                else -> prepared.prepare(chrome, from, beforeOptions, to, targetOptions, deliver)
            }
        }
    }

    fun hasNextPage(): Boolean {
        val state = surface?.state
        if (state != null && state.optInt("pageIndex") + 1 < state.optInt("pageCount")) return true
        if (documentIndex + 1 < documents.size) return true
        return (chapter?.index ?: 0) + 1 < (textChapter?.chaptersSize ?: Int.MAX_VALUE)
    }

    fun readyForAutoPage(): Boolean = !loading && !turning && !pageTextMeasuring && surface?.readyState != null

    fun prepareAutoPage() {
        if (!readyForAutoPage()) return
        val current = textChapter ?: return
        if (neighboringChapters().any { it.chapter.index == current.chapter.index + 1 &&
                preparedChapters[it.chapter.index]?.chapter !== it }) {
            if (neighboringChapters().any { it.chapter.index == current.chapter.index + 1 && it.isCompleted }) {
                boundBook?.let(::showChapter)
            }
            return
        }
        turn(1)
    }

    fun scrollAutoPage(amount: Float) {
        if (!readyForAutoPage()) return
        if (surface?.state?.optBoolean("atEnd") == true) turn(1) else surface?.scroll(css(amount))
    }

    fun cancelAutoPage() { if (autoPaging()) return; cancelTurn() }

    private fun turnChapter(direction: Int) {
        val target = chapter?.index?.plus(direction)
        chapterTurnTarget = target?.takeIf { it in 0 until (boundBook?.totalChapterNum ?: 0) }
        chapterTurn(direction)
    }

    private fun cancelTurn(keepChapterTurn: Boolean = false) {
        pageTextRequest++
        pageTextMeasuring = false
        turnTimeout?.let(host::removeCallbacks)
        turnTimeout = null
        turning = false
        committing = false
        clearFrames()
        anticipatedPage = null
        if (!keepChapterTurn) {
            chapterTurnTarget = null
            pendingTurns.clear()
            pendingInteractions.clear()
        }
        preparationFrame?.recycle()
        preparationFrame = null
        finishAnimation()
    }

    private fun css(px: Float) = px / host.resources.displayMetrics.density

    private fun releasePublication() {
        renderFailed = false
        rendererRecoveryUsed = false
        speechFollowPosition = null
        speechFollowRevision++
        pageTextRequest++
        pendingReadAloud = null
        selectionRevision++
        selectionAnchor = null
        generation++
        contentGeneration++
        styleGeneration++
        highlightRevision++
        pendingHighlightUpdate = false
        cancelTurn()
        gestures?.cancel()
        gestures = null
        surface?.let { host.removeView(it); it.close() }
        surface = null
        val old = snapshot
        snapshot = null
        if (old != null) {
            openingPreview()?.discard(old.publicationSession)
            scope.launch(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { old.close() }
        }
        bookUrl = null
        linkRequest++
        pendingChapterLink = null
        chapter = null
        textChapter = null
        preparedContent = null
        preparedChapters = emptyMap()
        failedNeighbors = emptyMap()
        openAtEnd = false
        reportedPosition = -1
        aloudSignature = ""
        aloudPaintSignature = ""
        fontWarningSignature = ""
        selection = null
        pendingSearchSelection = null
        textHighlights = emptyList()
        readerFontBytes = null
        readerFontPath = ""
        titleFontBytes = null
        titleFontPath = ""
        documents = emptyList()
        active = false
    }

    override fun close() {
        if (closed) return
        closed = true
        releasePublication()
        boundBook = null
        scope.cancel()
    }
}
