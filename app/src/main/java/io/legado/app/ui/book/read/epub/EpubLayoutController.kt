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
import io.legado.app.model.ReadBook
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
    private val nativeBackground: (Canvas) -> Unit,
    private val recordAnimationFrames: () -> Boolean,
    private val nativeChrome: (Canvas, Boolean, Boolean) -> Unit,
    private val animate: (EpubAnimationFrames, Int, () -> Unit, () -> Unit) -> Unit,
    private val finishAnimation: () -> Unit,
    private val completeAnimation: () -> Unit,
    private val animationsEnabled: () -> Boolean,
    private val autoPaging: () -> Boolean = { false },
    private val isScroll: () -> Boolean,
    private val scrollAnimationStart: () -> Unit,
    private val scrollAnimationStop: () -> Unit,
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
    private var contentJob: Job? = null
    private class PreparationKey(val chapter: TextChapter, val session: io.legado.app.model.epub.EpubPublicationSession,
        val source: String?, val style: Long, val highlight: Long, val removeHeadings: Boolean, val removeRuby: Boolean) {
        val positions = chapter.contentPositionMap
        fun matches(other: PreparationKey) = chapter === other.chapter && positions === other.positions &&
            session === other.session && source == other.source && style == other.style && highlight == other.highlight &&
            removeHeadings == other.removeHeadings && removeRuby == other.removeRuby && chapter.isCompleted
    }
    private val contentSlot = EpubPreparationSlot<PreparationKey, EpubPreparedContent>(scope) { a, b -> a.matches(b) }
    private var mappingWrite: Job? = null
    private var mappingRevision = EpubMappingPersistence.next()
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
    private var pendingSelectionHit: Long? = null
    private var selectionFinished = false
    private var selectionDrag: EpubSelectionDrag? = null
    private var selectionEdgeTask: Runnable? = null
    private var selectionNavigationTimeout: Runnable? = null
    private var selectionHandleActive = false
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
    private var visibleChromeMask = 0
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
    private val turnHandoff = EpubTurnHandoff()
    private var preparationFrame: Bitmap? = null
    private var pendingAnimation: EpubAnimationFrames? = null
    private var turnTimeout: Runnable? = null
    private val frames = EpubFrameWindow<EpubCapturedFrame>(maxBytes = 48L * 1024 * 1024,
        sizeOf = { it.bitmap.allocationByteCount.toLong() }) { it.close() }
    private var borrowedFrames = emptyList<EpubFrameWindow.Lease<EpubCapturedFrame>>()
    private var frameEpoch = 0L
    private var preparationEpoch = 0L
    private var frameDocument = -1
    private class TurnFrameTarget(val documentKey: String, val key: Int, val frame: EpubCapturedFrame,
                                  val sourceDocumentKey: String, val sourceKey: Int, val sourceFrame: EpubCapturedFrame)
    private var pendingTurnFrame: TurnFrameTarget? = null
    private var preparationWorking = false
    private var anticipatedPage: Int? = null
    private var lastDirection = 1
    private class TurnRequest(val direction: Int, receivedAt: Long, val sequence: Long) {
        val timing = EpubStartupTiming("turn", receivedAt)
        var waiting = false
    }
    private val pendingTurns = EpubTurnQueue<TurnRequest>(compareBy { it.sequence })
    private var activeTurn: TurnRequest? = null
    private var inputSequence = 0L
    private var inputTicket: Long? = null
    private var interactionEpoch = 0L
    private var activeInteraction: Long? = null
    private var inputReceivedAt: Long? = null
    private var pointerReceivedAt: Long? = null
    private var turnTiming: EpubStartupTiming? = null
    private var chapterTiming: EpubStartupTiming? = null
    private var awaitingAnimationDraw = false
    private var turnPhase = "等待准备"
    private var visibleInteractionToken: String? = null
    private var visibleInteractionTarget: Pair<String, Int>? = null
    private var visibleInteractionEpoch = -1L
    private var committedTapToken: String? = null
    private var committedTapTarget: Pair<String, Int>? = null
    private var warmingPage: Pair<Int, Int>? = null
    private var warmingPaint = ""
    private class PendingInteraction(val sequence: Long, val run: () -> Unit)
    private val pendingInteractions = java.util.ArrayDeque<PendingInteraction>()
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
        // The original ReadBook loader owns a chapter transition requested by a drag.
        // Its first callback may have no chapter, or a not-yet-complete TextChapter.
        val selectionTarget = selectionDrag?.chapterTarget
        if (bookUrl == book.bookUrl && selectionTarget != null &&
            (value == null || value.chapter.index == selectionTarget)) {
            if (textChapter !== value) {
                contentGeneration++
                contentJob?.cancel()
                loading = false
                chapter = value?.chapter
                textChapter = value
                preparedContent = null
                reportedPosition = -1
                requestedPosition = position()
            }
            if (value?.isCompleted == true && !loading && preparedContent == null)
                showChapter(book, selectionNavigation = true)
            return
        }
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
                chapterTurnTarget != null && chapterTurnTarget == value?.chapter?.index)) {
            prepareNeighbor()
            return
        }
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
        } else if (value != null && preparedChapters[value.chapter.index]?.chapter === value &&
            documents.any { it.chapter === value }) {
            val chapterChanged = textChapter !== value
            if (chapterChanged) {
                textChapter = value; chapter = value.chapter; preparedContent = preparedChapters[value.chapter.index]
                requestedPosition = position()
            }
            if (!loading && (chapterChanged || contentJob?.isActive != true) && hasUnpreparedWindowNeighbors()) showChapter(book,
                retainWindowPosition = documents.getOrNull(documentIndex)?.chapter === value &&
                position() == reportedPosition)
            // Equal offsets in different chapters are not the same reading location. A window
            // callback may already have displayed this chapter; a TOC jump still needs a seek.
            else if (chapterChanged && documents.getOrNull(documentIndex)?.chapter !== value ||
                position() != reportedPosition && position() != requestedPosition) jump(position(), "bind-chapter-position")
        } else if (textChapter !== value || preparedContent == null && value?.isCompleted == true && !loading) {
            if (snapshot != null) {
                contentGeneration++
                contentJob?.cancel()
                loading = false
            }
            chapter = value?.chapter
            textChapter = value
            // Native layout publishes the new TextChapter before it is complete. Drop the old
            // binding now so the completion notification will prepare the new document.
            preparedContent = null
            reportedPosition = -1
            requestedPosition = position()
            if (!loading) showChapter(book)
        } else if (position() != reportedPosition && position() != requestedPosition) {
            jump(position(), "bind-position")
        }
        if (!loading && preparedContent != null) scheduleWarm()
    }

    private fun hasUnpreparedWindowNeighbors() =
        selection == null && selectionAnchor == null && pendingSelectionHit == null && gestures?.isActive != true &&
        surface?.hasPendingScroll != true && activeInteraction == null && pendingInteractions.isEmpty() &&
        neighboringChapters().any {
            it.isCompleted && preparedChapters[it.chapter.index]?.chapter !== it && failedNeighbors[it.chapter.index] !== it &&
                (it.sourceParagraphs.sumOf { paragraph -> paragraph.length.toLong() } <= 1_000_000 ||
                    isScroll() || autoPaging() || options().optBoolean("fixed"))
        }

    private fun attachSurface() {
        val publication = snapshot?.publicationSession ?: return
        visibleChromeMask = 0
        lateinit var onReady: (JSONObject) -> Unit
        onReady = ready@ { state ->
                if (preparedContent?.chapter !== textChapter || preparedContent == null) return@ready
                if (turnHandoff.deferReady { onReady(state) }) {
                    if (turning && !committing) scheduleWarm()
                    return@ready
                }
                visibleChromeMask = (if (state.optBoolean("hideHeader")) 1 else 0) or
                    (if (state.optBoolean("hideFooter")) 2 else 0)
                host.invalidate()
                visibleInteractionToken = state.optString("token")
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
                visibleInteractionTarget = documents[documentIndex].payload.getString("key") to state.optInt("pageIndex", -1)
                visibleInteractionEpoch = interactionEpoch
                if (pendingTurnFrame != null || frameDocument != documentIndex) {
                    if (!promoteTurnFrame(state)) clearFrames()
                }
                pendingTurnFrame = null
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
                if (completesChapterTurn) {
                    chapterTurnTarget = null
                    chapterTiming?.mark("chapter-visible")
                    chapterTiming = null
                }
                if (committing) {
                    committing = false
                    turning = false
                    turnTimeout?.let(host::removeCallbacks)
                    turnTimeout = null
                    finishAnimation()
                    turnTiming?.mark("visible-ready")
                    turnTiming = null
                    awaitingAnimationDraw = false
                    activeTurn = null
                }
                committedTapToken = null
                committedTapTarget = null
                anticipatedPage = null
                syncAloudHighlight()
                speechFollowPosition?.let(::followReadAloud)
                applySearchSelection()
                val drag = selectionDrag
                if (drag?.awaitingViewport == true) {
                    selectionNavigationTimeout?.let(host::removeCallbacks)
                    selectionNavigationTimeout = null
                    drag.awaitingViewport = false
                    drag.inFlight = false
                    drag.chapterTarget = null
                    requestSelectionHit(drag, state.optJSONObject("selectionHit"))
                } else if (selection != null) renderSelection()
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
                val receivedAt = pointerReceivedAt ?: android.os.SystemClock.elapsedRealtime()
                val timing = EpubStartupTiming("tap", receivedAt)
                interactReady(JSONObject().put("action", "tap").put("x", css(x)).put("y", css(y))) { result ->
                    if (result?.optBoolean("retainedChapterBlank") == true) timing.mark("chapter-blank-retained")
                    timing.mark(if (result == null) "hit-cancelled" else if (result.optBoolean("nativeBlank")) "hit-ready native-blank" else "hit-ready")
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
                    if (href.isEmpty()) {
                        inputReceivedAt = receivedAt
                        try { blankTap(x, y + viewportTop) } finally { inputReceivedAt = null }
                    }
                    else next.resolve(href)?.let(::followLink)
                }
            },
            longPress = { x, y -> hitSelection(x, y, initial = true) },
            extend = { x, y -> hitSelection(x, y, initial = false) },
            finishSelection = { x, y ->
                selectionDrag?.takeIf { it.moved }?.update(x, y)
                finishSelectionDrag()
            },
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
                next.finishScroll { state ->
                    val overshoot = state.optDouble("scrollOvershoot", 0.0)
                    if (kotlin.math.abs(overshoot) > 48) turn(if (overshoot > 0) 1 else -1)
                }
            },
            canSelect = { selectionEnabled },
            horizontalScroll = { next.state?.optString("scrollAxis") == "x" },
            canFling = {
                !closed && !renderFailed && !loading && surface === next &&
                    next.readyState?.let { it.optBoolean("scrolled") &&
                        kotlin.math.abs(it.optDouble("scrollOvershoot", 0.0)) <= 48 } == true
            },
            cancelScroll = { stopSelectionDrag(); next.cancelScroll() },
            onFlingStart = scrollAnimationStart,
            onFlingStop = { scrollAnimationStop(); host.post { continueTurns() } },
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
                stopScroll()
                cancelTurn()
                if (modeChanged) view.refreshViewport() else view.restyle(options())
            }
            insetsChanged = false
            return
        }
        insetsChanged = false
        stopScroll()
        cancelTurn()
        preparation?.close()
        preparation = null
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

    private fun traceIgnoredTouch(event: MotionEvent, reason: String) {
        if (!io.legado.app.BuildConfig.DEBUG || event.actionMasked != MotionEvent.ACTION_UP) return
        val receivedAt = android.os.SystemClock.elapsedRealtime() -
            (android.os.SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0)
        EpubStartupTiming("pointer-suppressed", receivedAt).mark(
            "$reason loading=$loading contentReady=${preparedContent != null} chapterTarget=${chapterTurnTarget != null}")
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
        if ((loading || preparedContent == null) && chapterTurnTarget == null) {
            traceIgnoredTouch(event, "not-ready")
            return true
        }
        val view = surface ?: run {
            traceIgnoredTouch(event, "surface-missing")
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            if (turning && !committing) completeAnimation()
            view.beginScroll()
            downX = event.x - view.left
            downY = event.y - view.top
        }
        val translated = MotionEvent.obtain(event)
        val previousTime = pointerReceivedAt
        if (event.actionMasked == MotionEvent.ACTION_UP) pointerReceivedAt = android.os.SystemClock.elapsedRealtime() -
            (android.os.SystemClock.uptimeMillis() - event.eventTime).coerceAtLeast(0)
        if (io.legado.app.BuildConfig.DEBUG && event.actionMasked == MotionEvent.ACTION_UP)
            EpubStartupTiming("pointer", pointerReceivedAt!!).mark("up")
        try {
            translated.offsetLocation(-view.left.toFloat(), -view.top.toFloat())
            val handled = gestures?.onTouch(view, translated) ?: true
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL ||
                event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) host.post { continueTurns() }
            return handled
        } finally {
            pointerReceivedAt = previousTime
            translated.recycle()
        }
    }

    fun containsVisibleDocument(x: Float, y: Float): Boolean {
        if (renderFailed) return true
        val view = surface ?: return false
        val state = view.readyState
        if (state == null || state.optBoolean("canvas")) {
            val localX = css(x - view.left)
            val localY = css(y - view.top)
            val top = if (visibleChromeMask and 1 != 0) 0.0 else chromeInsets.optDouble("top", 0.0)
            val bottom = css(view.height.toFloat()) -
                if (visibleChromeMask and 2 != 0) 0.0 else chromeInsets.optDouble("bottom", 0.0)
            if (localY < top || localY >= bottom) return false
            state?.optJSONObject("documentRect")?.let { rect ->
                return localX >= rect.optDouble("left") && localY >= rect.optDouble("top") &&
                    localX < rect.optDouble("left") + rect.optDouble("width") &&
                    localY < rect.optDouble("top") + rect.optDouble("height")
            }
        }
        return view.clipBounds?.contains((x - view.left).toInt(), (y - view.top).toInt()) ?: true
    }

    fun drawInformation(canvas: Canvas) {
        if (active) nativeChrome(canvas, visibleChromeMask and 1 != 0, visibleChromeMask and 2 != 0)
    }

    private fun failRendering(message: String, rendererGone: Boolean = false) {
        if (closed || renderFailed) return
        contentSlot.clear()
        contentJob?.cancel()
        renderFailed = true
        loading = false
        contentGeneration++
        highlightRevision++
        pendingReadAloud = null
        speechFollowPosition = null
        speechFollowRevision++
        cancelTurn()
        preparation?.close()
        preparation = null
        clearSelection()
        gestures?.cancel()
        gestures = null
        surface?.let { host.removeView(it); it.close() }
        surface = null
        visibleChromeMask = 0
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

    private fun showChapter(book: Book, retainWindowPosition: Boolean = false, selectionNavigation: Boolean = false) {
        val mine = ++contentGeneration
        contentJob?.cancel()
        mappingWrite?.cancel()
        mappingRevision = EpubMappingPersistence.next()
        val current = textChapter
        val session = snapshot?.publicationSession
        val view = surface
        val displayed = documents.getOrNull(documentIndex)
        // A neighbour projection does not replace the currently displayed chapter. Keep
        // its input owner while preparing; the existing ready gate still owns publication.
        val retained = preparedContent?.takeIf {
            retainWindowPosition && !selectionNavigation && !autoPaging() && current != null && current.isCompleted && session != null && view != null &&
                it.chapter === current && displayed != null && displayed.chapter === current && position() == reportedPosition &&
                chapterTurnTarget == null && !openAtEnd && pendingChapterLink == null &&
                session.publication.spine.getOrNull(displayed.spineIndex)?.let { item ->
                    session.publication.layoutFor(item) != EpubLayout.FIXED
                } == true
        }
        if (retained == null)
            cancelTurn(keepChapterTurn = chapterTurnTarget != null && chapterTurnTarget == current?.chapter?.index)
        if (current == null || session == null) return
        val style = styleGeneration
        val highlight = highlightRevision
        val epoch = interactionEpoch
        val scrolling = isScroll()
        val refresh = if (retained != null && io.legado.app.BuildConfig.DEBUG)
            EpubStartupTiming("window-refresh").apply { mark("prepare-retained") } else null
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
        val highlightRules = ReadBookConfig.highlightRules.map { it.copy() }
        if (retained == null) {
            preparedContent = null
            if (!selectionNavigation) {
                stopSelectionDrag()
                selection = null
                selectionAnchor = null
                selectionFinished = false
            }
            loading = true
        }
        val openingDocument = openingPreparation()?.openingDocument(book, session)
            ?: EpubOpeningMappingCache.openingDocument(book, current, sourceRevision)
        if (retained == null && !isScroll()) openingDocument?.let { document ->
            val item = publication.spine.firstOrNull { publication.resourcesById[it.idref]?.location?.path == document.location.path }
            if (item != null && publication.layoutFor(item) != EpubLayout.FIXED) {
                surface?.stage(document.location, document.html)
            }
        }
        contentJob = scope.launch {
            try {
                val content = previous[current.chapter.index]?.takeIf { it.chapter === current }
                    ?: try { prepareContent(book, current, session, sourceRevision, opening = true).await() }
                    catch (cancelled: CancellationException) {
                        // An in-flight speculative build can decline its size budget after we
                        // start awaiting it. An active foreground owner must still render it.
                        currentCoroutineContext().ensureActive()
                        contentSlot.clear()
                        prepareContent(book, current, session, sourceRevision, opening = true).await()
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
                    val candidates = neighboringChapters().filter { it.isCompleted &&
                        (window || it.sourceParagraphs.sumOf { paragraph -> paragraph.length.toLong() } <= 1_000_000) }
                    candidates.forEach { candidate ->
                        currentCoroutineContext().ensureActive()
                        if (loaded[candidate.chapter.index]?.chapter !== candidate && failed[candidate.chapter.index] !== candidate) {
                            val prepared = previous[candidate.chapter.index]?.takeIf { it.chapter === candidate }
                                ?: try {
                                    // The slot belongs to Main. Reuse its exact projection before
                                    // starting background work for a newly completed neighbour.
                                    val key = PreparationKey(candidate, session, sourceRevision, styleGeneration,
                                        highlightRevision, book.getDelTag(Book.hTag), book.getDelTag(Book.rubyTag))
                                    val cached = contentSlot.find(key)?.takeUnless { it.isCancelled }?.let {
                                        try { it.await() }
                                        catch (cancelled: CancellationException) {
                                            // A speculative size-budget cancellation does not cancel
                                            // this window owner, which still needs the neighbour.
                                            currentCoroutineContext().ensureActive()
                                            null
                                        }
                                    }
                                    cached ?: withContext(Dispatchers.Default) {
                                        val work = currentCoroutineContext()
                                        EpubPreparedContent.create(book, candidate, session,
                                            matcher = io.legado.app.ui.book.read.page.provider.ReadHighlightMatcher(highlightRules),
                                            checkCancelled = { work.ensureActive() })
                                    }
                                }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { failed[candidate.chapter.index] = candidate; null }
                            if (prepared != null) loaded[candidate.chapter.index] = prepared
                        }
                    }
                    val available = loaded.values.flatMap { it.documents }.map { it.spineIndex }.toSet()
                    if (available.containsAll(required)) break
                    check(android.os.SystemClock.uptimeMillis() < deadline) { "固定版式配对页尚未准备完成" }
                    // The existing reader is already loading both neighbours. Wait for that
                    // result; do not extract/process a parallel copy of their business content.
                    delay(32)
                } while (true)
                if (retained != null) {
                    refresh?.mark("projection-ready")
                    while (true) {
                        if (closed || mine != contentGeneration || current !== textChapter || preparedContent !== retained || surface !== view ||
                            style != styleGeneration || highlight != highlightRevision || epoch != interactionEpoch ||
                            scrolling != isScroll() || autoPaging()) return@launch
                        // An UP received during preparation still belongs to the original
                        // viewport. Drain it through the ordinary queue before changing windows.
                        if (!loading && !turning && !committing && !pageTextMeasuring && chapterTurnTarget == null &&
                            !turnHandoff.waitingForMove && activeInteraction == null && pendingInteractions.isEmpty() &&
                            pendingTurns.peek() == null && surface?.readyState != null && surface?.hasPendingScroll == false &&
                            gestures?.isActive != true && selection == null && selectionAnchor == null &&
                            selectionDrag == null && pendingSelectionHit == null && pendingSearchSelection == null &&
                            pendingChapterLink == null && !pendingHighlightUpdate && !openAtEnd) break
                        delay(16)
                    }
                }
                if (closed || mine != contentGeneration || current !== textChapter) return@launch
                content.publishMapping(book, sourceRevision)
                // A completed bounded window can prune/reindex neighbours. Retire its
                // old pixel keys at publication without invalidating accepted input tickets.
                if (retained != null) clearFrames()
                preparedContent = content
                // Keep source mappings for the one active logical selection, while the
                // renderer itself continues to receive only its ordinary bounded window.
                preparedChapters = retainedSelectionChapters(previous) + loaded
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
                else openDocument(offset = requestedPosition, retainWindowPosition = retainWindowPosition)
                refresh?.mark("window-submitted")
                val record = content.cacheRecord
                if (record != null) {
                    val ticket = mappingRevision
                    mappingWrite = scope.launch(Dispatchers.IO) {
                        // Frozen mapping only; never delay visible-page configuration for persistence.
                        EpubOpeningMappingFile.write(File(host.context.cacheDir, "epub-layout/opening-mapping.bin"), record) { commit ->
                            if (isActive) EpubMappingPersistence.publish(ticket, commit)
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (!closed && mine == contentGeneration) {
                    failRendering(error.localizedMessage ?: "EPUB 章节解析失败")
                }
            } finally {
                if (mine == contentGeneration) {
                    loading = false
                    if (!closed && textChapter !== current) showChapter(book)
                    else if (retained != null && !closed) host.post { continueTurns() }
                }
            }
        }
    }

    private val noteMarker by lazy { EpubNoteMarker(host.context) }

    private fun prepareContent(book: Book, chapter: TextChapter,
        session: io.legado.app.model.epub.EpubPublicationSession, revision: String?, opening: Boolean = false
    ): kotlinx.coroutines.Deferred<EpubPreparedContent> {
        val key = PreparationKey(chapter, session, revision, styleGeneration, highlightRevision,
            book.getDelTag(Book.hTag), book.getDelTag(Book.rubyTag))
        val existing = contentSlot.find(key)
        if (existing != null && !existing.isCancelled) return existing
        if (existing != null) contentSlot.clear()
        val copy = book.copy(readConfig = book.readConfig?.copy())
        val rules = ReadBookConfig.highlightRules.map { it.copy() }
        val source = if (opening) openingPreparation()?.takeSource(book, chapter.chapter, session) else null
        val record = if (opening) openingPreparation()?.takeRecord(book, chapter.chapter, session) else null
        return contentSlot.prepare(key) {
            val work = currentCoroutineContext()
            EpubPreparedContent.create(copy, chapter, session, source, revision, record,
                io.legado.app.ui.book.read.page.provider.ReadHighlightMatcher(rules)) { work.ensureActive() }
                .also {
                    work.ensureActive()
                    // Speculative payloads have one slot and a byte budget; large chapters prepare on demand.
                    if (!opening && it.estimatedBytes() > 8L * 1024 * 1024) throw CancellationException("EPUB prefetch budget")
                }
        }
    }

    private fun prepareNeighbor() {
        if (closed || loading || chapterTurnTarget != null || pageTextMeasuring || !host.isShown ||
            activeInteraction != null || pendingInteractions.isNotEmpty()) return
        val current = preparedContent?.chapter ?: return
        val ready = surface?.readyState
        val idle = !turning && pendingTurns.peek() == null
        var prelayout = false
        // Manual no-animation turns need the same complete target layout. Loading
        // only its HTML still leaves reader styles and pagination on the foreground move.
        if (ready != null && (animationsEnabled() && recordAnimationFrames() ||
                !animationsEnabled() && !autoPaging()) && !isScroll() &&
            selection == null && selectionDrag == null && gestures?.isActive != true) {
            val target = documentIndex + lastDirection
            if (target in documents.indices) {
                prelayout = surface?.preloadPreparedDocument(documents, target, "$styleGeneration/$highlightRevision/$aloudPaintSignature") {
                    val previous = documentIndex
                    documentIndex = target
                    try { options() } finally { documentIndex = previous }
                } == true
            }
        }
        // A complete target layout keeps its single inactive frame. Unsupported
        // targets retain the original idle-only runtime preload.
        if (!prelayout && idle && ready != null && selection == null && selectionDrag == null && gestures?.isActive != true) {
            val page = ready.optInt("pageIndex")
            val target = when {
                page <= 1 && documentIndex > 0 -> documentIndex - 1
                page >= ready.optInt("pageCount") - 2 && documentIndex + 1 < documents.size -> documentIndex + 1
                else -> documentIndex + lastDirection
            }
            if (target in documents.indices) surface?.preloadDocument(target)
        }
        if (animationsEnabled() && recordAnimationFrames() && !isScroll() && selection == null &&
            selectionDrag == null && gestures?.isActive != true && !preparationWorking) {
            val target = documentIndex + lastDirection
            if (target in documents.indices) {
                preparation?.preloadDocument(documents, target, "$styleGeneration/$highlightRevision/$aloudPaintSignature") {
                    val previous = documentIndex
                    documentIndex = target
                    try { options() } finally { documentIndex = previous }
                }
            }
        }
        if (!idle && (selection != null || selectionDrag != null || gestures?.isActive == true)) return
        // The foreground/window job already owns any awaited slot. Do not replace
        // its projection with another speculative native build while it is suspended.
        if (contentJob?.isActive == true) return
        val book = boundBook ?: return
        val opened = snapshot ?: return
        val target = neighboringChapters().firstOrNull {
            it.isCompleted && it.chapter.index == current.chapter.index + lastDirection
        } ?: return
        if (preparedChapters[target.chapter.index]?.chapter === target) return
        // Do not repeatedly retry a failed speculative build. A foreground request may retry it.
        val key = PreparationKey(target, opened.publicationSession, opened.identity.contentRevision, styleGeneration,
            highlightRevision, book.getDelTag(Book.hTag), book.getDelTag(Book.rubyTag))
        if (contentSlot.find(key) != null) return
        if (target.sourceParagraphs.sumOf { it.length.toLong() } > 1_000_000) return
        if (io.legado.app.BuildConfig.DEBUG)
            android.util.Log.d("EpubStartup", "neighbor-prefetch chapter=${target.chapter.index} busy=${!idle}")
        prepareContent(book, target, opened.publicationSession, opened.identity.contentRevision)
    }

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
                .put("hidden", ReadBookConfig.titleMode == 2)
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
            .put("ngHighlightNight", ReadBookConfig.isNightTheme)
            // PageView owns decorations for both per-book and global reading styles.
            .put("ngNativePageDecorations", boundBook?.let {
                it.bookUrl == ReadBook.book?.bookUrl
            } == true)
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
        } else value.put("containerMode", if (isScroll()) "continuous" else "canvas")
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
            if (reflow) { stopSelectionDrag(); surface?.restyle(value) }
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
        // The ready surface already fenced this empty paint in awaitLayout. Re-sending
        // it adds an idle JS interaction that delays both taps and capture snapshots.
        if (!paintChanged && ranges.length() == 0 && view.hasConfirmedEmptyAloudPaint()) return
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
        if (target >= 0 && target != documentIndex) { jump(position, "aloud-document"); return }
        val mine = ++speechFollowRevision
        val trace = io.legado.app.BuildConfig.DEBUG && io.legado.app.model.ReadAloud.httpTtsEngineV2 == null
        view.interact(JSONObject().put("action", "containsPosition").put("offset", position).put("index", documentIndex).apply {
            if (trace) put("debugProbe", true)
        }) {
            if (mine != speechFollowRevision || speechFollowPosition != position) return@interact
            if (trace) android.util.Log.d("EpubAloudTrace", "contains chapter=${chapter?.index} offset=$position " +
                "page=${view.state?.optInt("pageIndex", -1)} reported=$reportedPosition " +
                "mapped=${it?.optBoolean("mapped")} visible=${it?.optBoolean("visible")} probe=${it?.optJSONObject("probe")}")
            // HTTP playback seeks through integer milliseconds, then estimates a
            // text position from them. The round trip can report the character
            // immediately before the page start before advancing to that start.
            // Native paging does not turn back for this boundary update either.
            // Consume the progress normally, but keep the visible EPUB page.
            val atLeadingBoundary = reportedPosition > 0 && position == reportedPosition - 1
            if (it?.optBoolean("mapped") == true && !it.optBoolean("visible") && !atLeadingBoundary) jump(position, "aloud-offscreen")
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
                val range = io.legado.app.ui.book.read.page.provider.NativeTextHighlightResolver.resolve(
                    mark, chapterIndex, document.chapter.highlightPositionMap,
                ) ?: return@forEach
                val from = maxOf(document.first, range.start)
                val to = minOf(document.last, range.endExclusive)
                val noteEnd = if (mark.time in noteIds) {
                    io.legado.app.ui.book.read.page.provider.NativeTextHighlightResolver.resolveEnd(
                        mark, chapterIndex, document.chapter.highlightPositionMap,
                    )
                } else null
                if (to > from) put(JSONObject().put("id", mark.time.toString()).put("occurrence", index)
                    .put("from", from).put("to", to).put("style", mark.highlightStyle)
                    .put("color", String.format("#%06X", mark.highlightColor and 0xffffff))
                    .put("note", noteEnd != null && to == noteEnd))
            }
        }
    }

    private fun openDocument(last: Boolean = false, fragment: String? = null, offset: Int? = null,
                             retainWindowPosition: Boolean = false) {
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
                boundBook?.let { showChapter(it) }
            }
            return
        }
        val view = surface ?: return
        view.documents(documents)
        view.content(document.location.path, document.html, document.payload, document.payloadJson)
        view.open(document.location.copy(fragment = fragment ?: document.location.fragment), options(last).apply {
            if (!isScroll() && !optBoolean("fixed") && selectionDrag?.awaitingViewport == true)
                selectionProjection()?.let { put("selectionProjection", it) }
            if (retainWindowPosition) put("retainWindowPosition", true)
            put("fragment", fragment ?: JSONObject.NULL)
            offset?.let { put("textOffset", it) }
        })
    }

    fun jump(offset: Int, reason: String = "external") {
        if (io.legado.app.BuildConfig.DEBUG) android.util.Log.d("EpubAloudTrace", "jump reason=$reason " +
            "chapter=${chapter?.index} document=$documentIndex offset=$offset position=${position()} " +
            "reported=$reportedPosition requested=$requestedPosition following=$speechFollowPosition " +
            "page=${surface?.state?.optInt("pageIndex", -1)} token=${surface?.state?.optString("token")} loading=$loading")
        stopScroll()
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
                boundBook?.let { showChapter(it) }
                return
            }
        }
        if (target != documentIndex) { documentIndex = target; openDocument(offset = offset) }
        else surface?.move(0, JSONObject().put("textOffset", offset).put("index", target), reason)
    }

    private fun hitSelection(x: Float, y: Float, initial: Boolean) {
        if (!initial) {
            val drag = selectionDrag ?: return
            if (!drag.pressed) return
            drag.update(x, y)
            selectionEdgeTask?.let(host::removeCallbacks)
            selectionEdgeTask = null
            requestSelectionHit(drag)
            return
        }
        cancelTurn()
        clearSelection()
        val drag = EpubSelectionDrag(x, y).apply { initialHit = true }
        selectionDrag = drag
        requestSelectionHit(drag)
    }

    private fun requestSelectionHit(drag: EpubSelectionDrag, projected: JSONObject? = null) {
        if (selectionDrag !== drag || drag.inFlight || drag.awaitingViewport) return
        val view = surface ?: return
        if (view.readyState == null) { stopSelectionDrag(); return }
        val initial = drag.initialHit
        drag.initialHit = false
        if (!drag.pressed) drag.finalHit = false
        val pointerRevision = drag.pointerRevision
        val x = drag.x
        val y = drag.y
        drag.inFlight = true
        val mine = ++selectionRevision
        pendingSelectionHit = mine
        val consume: (JSONObject?) -> Unit = consume@ { result ->
            if (pendingSelectionHit == mine) pendingSelectionHit = null
            host.post { continueTurns() }
            if (mine != selectionRevision || selectionDrag !== drag) return@consume
            drag.inFlight = false
            if (initial && result?.isNull("image") == false) {
                view.resolve(result.getString("image"))?.let {
                    imageLongPress(x + view.left, y + view.top, it.path)
                }
                stopSelectionDrag()
                return@consume
            }
            if (!selectionEnabled) { stopSelectionDrag(); return@consume }
            val document = result?.let { documents.getOrNull(it.optInt("index", documentIndex)) }
            val offset = result?.optInt("offset", -1) ?: -1
            if (document != null && offset in 0..document.text.length) {
                val text = document.text
                val chapterIndex = document.chapter.chapter.index
                if (initial && text.isNotEmpty()) {
                    val at = offset.coerceAtMost(text.lastIndex)
                    val from = if (at == 0) 0 else text.lastIndexOf('\n', at - 1) + 1
                    val to = text.indexOf('\n', at).takeIf { it >= 0 } ?: text.length
                    readerWordBoundary(text.substring(from, to), at - from)?.let { word ->
                        selectionAnchor = chapterIndex to from + word.first
                        setSelectionEnd(chapterIndex, from + word.last + 1)
                    }
                } else if (!initial && drag.pointerRevision == pointerRevision)
                    setSelectionEnd(chapterIndex, offset, result?.optJSONObject("selection"))
            }
            drag.updateEdge(if (!initial && drag.pointerRevision == pointerRevision) result?.optInt("edge") ?: 0 else 0,
                android.os.SystemClock.uptimeMillis())
            when {
                !drag.pressed && !drag.finalHit -> completeSelectionDrag(drag)
                drag.pointerRevision != pointerRevision || !drag.pressed -> requestSelectionHit(drag)
                else -> scheduleSelectionEdge(drag)
            }
        }
        if (!initial && projected?.optString("token") == view.readyState?.optString("token") &&
            projected?.optLong("pointerRevision", -1) == pointerRevision) consume(projected)
        else view.interact(JSONObject().put("action", "hit").put("nearest", !initial)
            .put("x", css(x)).put("y", css(y)), consume)
    }

    /** Renderer projection only; the native anchor and source range remain authoritative. */
    private fun selectionProjection(): JSONObject? {
        val drag = selectionDrag ?: return null
        val anchor = selectionAnchor ?: return null
        if (isScroll()) return null
        val anchors = JSONArray()
        documents.forEachIndexed { index, document ->
            val fixed = when {
                anchor.first < document.chapter.chapter.index -> document.first
                anchor.first > document.chapter.chapter.index -> document.last
                else -> anchor.second.coerceIn(document.first, document.last)
            }
            anchors.put(JSONObject().put("index", index).put("anchor", fixed)
                .put("from", document.first).put("to", document.last))
        }
        return JSONObject().put("x", css(drag.x)).put("y", css(drag.y))
            .put("pointerRevision", drag.pointerRevision).put("anchors", anchors)
    }

    private fun scheduleSelectionEdge(drag: EpubSelectionDrag) {
        selectionEdgeTask?.let(host::removeCallbacks)
        selectionEdgeTask = null
        if (selectionDrag !== drag || !drag.pressed || drag.edge == 0 || selection == null) return
        val task = Runnable {
            selectionEdgeTask = null
            if (selectionDrag !== drag || !drag.pressed) return@Runnable
            if (drag.canAdvance(android.os.SystemClock.uptimeMillis())) advanceSelectionViewport(drag)
            else if (!drag.inFlight && !drag.awaitingViewport) scheduleSelectionEdge(drag)
        }
        selectionEdgeTask = task
        host.postDelayed(task, 80)
    }

    private fun advanceSelectionViewport(drag: EpubSelectionDrag) {
        val view = surface ?: return
        val state = view.readyState ?: return
        if (selectionDrag !== drag || selection == null || loading || state.optString("mode") == "FIXED") return
        val direction = drag.edge
        drag.didAdvance(android.os.SystemClock.uptimeMillis(), if (state.optBoolean("scrolled")) 80L else 350L)
        drag.awaitingViewport = true
        selectionRevision++
        pendingSelectionHit = null
        clearFrames()
        val scrolled = state.optBoolean("scrolled")
        val atBoundary = if (scrolled) state.optBoolean(if (direction > 0) "atEnd" else "atStart")
            else state.optInt("pageIndex") + direction !in 0 until state.optInt("pageCount")
        if (!atBoundary) {
            if (scrolled) {
                view.scroll(32f * direction)
                // scroll publishes through the same onReady/token handshake as ordinary dragging.
            } else view.move(state.optInt("pageIndex") + direction, preserveSelection = true,
                selectionProjection = selectionProjection())
        } else {
            val target = if (scrolled) (if (direction > 0) documents.size else -1) else documentIndex + direction
            val document = documents.getOrNull(target)
            if (document != null) {
                view.move(0, JSONObject().put("index", target)
                    .put("textOffset", if (direction > 0) document.first else document.last), preserveSelection = true,
                    selectionProjection = selectionProjection())
            } else {
                val boundary = if (direction > 0) documents.lastOrNull() else documents.firstOrNull()
                val targetChapter = boundary?.chapter?.chapter?.index?.plus(direction)
                if (targetChapter == null || targetChapter !in 0 until (boundBook?.totalChapterNum ?: 0)) {
                    drag.awaitingViewport = false
                    drag.updateEdge(0, android.os.SystemClock.uptimeMillis())
                    return
                }
                drag.chapterTarget = targetChapter
                openAtEnd = direction < 0
                chapterTurn(targetChapter - (chapter?.index ?: targetChapter - direction))
            }
        }
        if (selectionDrag === drag && drag.awaitingViewport) {
            val timeout = Runnable { if (selectionDrag === drag && drag.awaitingViewport) stopSelectionDrag() }
            selectionNavigationTimeout = timeout
            host.postDelayed(timeout, 25_000)
        }
    }

    /** Only mappings still needed by the active range/anchor survive a window replacement. */
    private fun retainedSelectionChapters(values: Map<Int, EpubPreparedContent>): Map<Int, EpubPreparedContent> {
        val value = selection ?: return emptyMap()
        val anchor = selectionAnchor?.first ?: value.chapterIndex
        val target = selectionDrag?.chapterTarget ?: value.endChapterIndex
        val from = minOf(value.chapterIndex, anchor, target)
        val to = maxOf(value.endChapterIndex, anchor, target)
        return values.filterKeys { it in from..to }
    }

    private fun releaseSelectionChapters() {
        val visible = documents.map { it.chapter.chapter.index }.toSet()
        preparedChapters = preparedChapters.filterKeys { it in visible } + retainedSelectionChapters(preparedChapters)
    }

    private fun stopSelectionDrag() {
        if (selectionDrag?.awaitingViewport == true)
            surface?.interact(JSONObject().put("action", "clear")) {}
        selectionEdgeTask?.let(host::removeCallbacks)
        selectionEdgeTask = null
        selectionNavigationTimeout?.let(host::removeCallbacks)
        selectionNavigationTimeout = null
        selectionDrag = null
        selectionHandleActive = false
        selectionRevision++
        pendingSelectionHit = null
        releaseSelectionChapters()
    }

    private fun completeSelectionDrag(drag: EpubSelectionDrag) {
        if (selectionDrag !== drag) return
        stopSelectionDrag()
        selectionFinished = true
        renderSelection()
    }

    private fun setSelectionEnd(chapterIndex: Int, offset: Int, geometry: JSONObject? = null) {
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
        releaseSelectionChapters()
        if (geometry == null) renderSelection() else {
            surface?.let { view -> geometry.put("leftPx", view.left).put("topPx", view.top) }
            selectionChanged(geometry, false)
            clearFrames()
        }
    }

    private fun renderSelection() {
        val value = selection ?: return
        val view = surface ?: return
        val mine = selectionRevision
        val finished = selectionFinished
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
                selectionChanged(geometry, finished)
            }
        }
        clearFrames()
    }

    fun finishSelectionDrag() {
        val drag = selectionDrag
        if (drag != null) {
            selectionEdgeTask?.let(host::removeCallbacks)
            selectionEdgeTask = null
            drag.finish()
            if (!drag.inFlight && !drag.awaitingViewport && !drag.finalHit) completeSelectionDrag(drag)
            else requestSelectionHit(drag)
        } else if (selectionHandleActive) {
            stopSelectionDrag()
            selectionFinished = true
            renderSelection()
        }
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

    fun beginSelectionHandleDrag(start: Boolean) {
        stopSelectionDrag()
        val selected = selection ?: return
        selectionHandleActive = true
        selectionAnchor = if (start) selected.endChapterIndex to selected.endChapterPosition
            else selected.chapterIndex to selected.chapterPosition
        selectionFinished = false
    }

    fun cancelSelectionHandleDrag() { stopSelectionDrag() }

    fun moveSelection(start: Boolean, x: Float, y: Float) {
        if (!selectionHandleActive) return
        val view = surface ?: return
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        val localX = x - location[0]
        val localY = y - location[1]
        val drag = selectionDrag ?: EpubSelectionDrag(localX, localY).also { selectionDrag = it }
        if (!drag.pressed) return
        hitSelection(localX, localY, initial = false)
    }

    fun clearSelection() {
        stopSelectionDrag()
        selectionRevision++
        pendingSelectionHit = null
        selectionAnchor = null
        selectionFinished = false
        if (selection != null || pendingSearchSelection != null) clearFrames()
        pendingSearchSelection = null
        selection = null
        releaseSelectionChapters()
        surface?.interact(JSONObject().put("action", "clear")) { host.post { continueTurns() } }
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
                host.post { continueTurns() }
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
    internal fun highlightChapter(index: Int): TextChapter? = preparedChapters[index]?.chapter

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
        stopSelectionDrag()
        if (!loading) contentSlot.clear()
        val mine = ++highlightRevision
        pendingHighlightUpdate = true
        val current = preparedContent ?: return true
        if (loading || surface?.state == null) return true
        val previous = preparedChapters
        val window = documents
        val content = contentGeneration
        val matcher = io.legado.app.ui.book.read.page.provider.ReadHighlightMatcher(ReadBookConfig.highlightRules)
        cancelTurn()
        scope.launch {
            try {
                val updated = withContext(Dispatchers.Default) {
                    previous.mapValues { (_, chapter) -> chapter.withHighlightStyles(matcher) }
                }
                if (closed || mine != highlightRevision || content != contentGeneration || preparedContent !== current || documents !== window) return@launch
                val updatedWindow = window.map { document ->
                    checkNotNull(updated[document.chapter.chapter.index]?.documents?.firstOrNull {
                        it.chapter === document.chapter && it.payload.getString("key") == document.payload.getString("key") &&
                            it.spineIndex == document.spineIndex && it.first == document.first && it.last == document.last
                    }) { "EPUB 高亮窗口已变更" }
                }
                preparedChapters = updated
                releaseSelectionChapters()
                preparedContent = updated[current.chapter.chapter.index]
                documents = updatedWindow
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
        stopSelectionDrag()
        if (!loading) contentSlot.clear()
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
        val view = surface ?: run { callback(null); return }
        val sequence = ++inputSequence
        val epoch = interactionEpoch
        // DOWN can already have completed to the frozen target. Bind its UP to that exact
        // document/page, including a harmless neighbour-window reconciliation of its token.
        val visibleToken = if (value.optString("action") == "tap") committedTapToken ?: visibleInteractionToken
            else visibleInteractionToken
        val displayedTarget = if (value.optString("action") == "tap") committedTapTarget ?:
            visibleInteractionTarget.takeIf { visibleInteractionEpoch == epoch } else null
        fun hasBlankTapContext(): Boolean = value.optString("action") == "tap" && displayedTarget != null &&
            !closed && !renderFailed && !pageTextMeasuring && !isScroll() && !autoPaging() &&
            selection == null && selectionAnchor == null && selectionDrag == null && pendingSelectionHit == null &&
            pendingSearchSelection == null
        fun canResolveBlankTap(): Boolean = hasBlankTapContext() && !loading && chapterTurnTarget == null &&
            view.canResolveBlankTap(checkNotNull(displayedTarget).first)
        // An older queued turn can replace the canvas document before this hit is drained.
        // Keep only the existing negative proof for the viewport that received this UP.
        val confirmedBlankTap = visibleToken != null &&
            hasBlankTapContext() && view.canResolveBlankTap(checkNotNull(displayedTarget).first)
        val blankViewport = if (confirmedBlankTap) Rect(view.left, view.top, view.right, view.bottom) else null
        val blankHostWidth = host.width
        val blankHostHeight = host.height
        var delivered = false
        val deliver: (JSONObject?) -> Unit = deliver@ { result ->
            if (delivered) return@deliver
            delivered = true
            if (activeInteraction == sequence) activeInteraction = null
            val previous = inputTicket
            inputTicket = sequence
            try { callback(result.takeIf { epoch == interactionEpoch }) }
            finally { inputTicket = previous }
            host.post { continueTurns() }
        }
        val interaction = PendingInteraction(sequence) {
            activeInteraction = sequence
            val targetMatches = displayedTarget != null && displayedTarget.second >= 0 &&
                documents.getOrNull(documentIndex)?.payload?.optString("key") == displayedTarget.first &&
                view.readyState?.optInt("pageIndex", -1) == displayedTarget.second
            val sameOwner = epoch == interactionEpoch && view === surface
            if (sameOwner &&
                (visibleToken != null && visibleToken == visibleInteractionToken || targetMatches))
                view.interact(value, deliver)
            else if (sameOwner && confirmedBlankTap && hasBlankTapContext() &&
                blankViewport?.let { it.left == view.left && it.top == view.top && it.right == view.right && it.bottom == view.bottom } == true &&
                host.width == blankHostWidth && host.height == blankHostHeight)
                deliver(JSONObject().put("token", visibleToken).put("href", JSONObject.NULL)
                    .put("nativeBlank", true).put("retainedChapterBlank", true))
            else deliver(null)
        }
        if (activeInteraction == null && pendingInteractions.isEmpty() && canResolveBlankTap()) {
            activeInteraction = sequence
            // Keep the asynchronous callback boundary and the original input ticket/turn queue.
            // A selection or document change before this message restores the ordinary hit test.
            host.post {
                when {
                    epoch != interactionEpoch || view !== surface || closed -> deliver(null)
                    canResolveBlankTap() -> deliver(JSONObject().put("token", visibleToken).put("href", JSONObject.NULL).put("nativeBlank", true))
                    else -> {
                        if (activeInteraction == sequence) activeInteraction = null
                        pendingInteractions.addFirst(interaction)
                        continueTurns()
                    }
                }
            }
            return
        }
        pendingInteractions.addLast(interaction)
        continueTurns()
    }

    private fun continueTurns() {
        if (closed || renderFailed || loading || pageTextMeasuring || chapterTurnTarget != null ||
            turnHandoff.waitingForMove || activeInteraction != null || preparedContent == null || surface?.readyState == null) return
        val interaction = pendingInteractions.peekFirst()
        val request = pendingTurns.peek()
        // Resolve coordinates while their displayed viewport still exists. Their callbacks
        // keep their input tickets, so accepted turns still execute in input order. Starting
        // an older resolved turn here would invalidate later taps received on this same page.
        if (interaction != null) {
            pendingInteractions.removeFirst().run()
            return
        }
        if (turning) return
        when {
            request != null -> {
                // This existing bounded projection must not depend on the pixel producer getting ahead.
                if (request.direction == lastDirection && animationsEnabled() && recordAnimationFrames() && !isScroll()) prepareNeighbor()
                if (reconcileQueuedWindow(request)) return
                var started = false
                pendingTurns.startNext { started = startTurn(it); started }
                // A blocked producer has not changed the displayed viewport. Resolve later
                // coordinates there now; their resulting intents retain their input order.
                if (!started && pendingInteractions.isNotEmpty()) pendingInteractions.removeFirst().run()
                else if (started && pendingInteractions.isNotEmpty()) host.post { continueTurns() }
            }
            else -> {
                val book = boundBook
                // A neighbour can finish while a window is loading and its bind notification
                // is ignored. Recheck after ready, without overtaking turns or seeking back.
                if (book != null && contentJob?.isActive != true && documents.getOrNull(documentIndex)?.chapter === textChapter &&
                    position() == reportedPosition && hasUnpreparedWindowNeighbors()) {
                    showChapter(book, retainWindowPosition = true)
                } else scheduleWarm()
            }
        }
    }

    /** Consume only a completed native projection, retaining the displayed page and input queue. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun reconcileQueuedWindow(request: TurnRequest): Boolean {
        val preparing = preparationWorking || preparation?.hasPendingJob == true
        if (closed || loading || turning || committing || pageTextMeasuring ||
            chapterTurnTarget != null || pendingTurnFrame != null ||
            activeInteraction != null || pendingInteractions.isNotEmpty() || selection != null ||
            selectionAnchor != null || selectionDrag != null || pendingSelectionHit != null ||
            pendingSearchSelection != null || pendingChapterLink != null || pendingHighlightUpdate ||
            speechFollowPosition != null || pendingReadAloud != null || aloudPaintSignature != "[]" ||
            textHighlights.isNotEmpty() || gestures?.isActive == true ||
            autoPaging() || isScroll() || !animationsEnabled() || !recordAnimationFrames() ||
            !host.isShown || request.direction != lastDirection) return false
        val current = preparedContent ?: return false
        val native = textChapter ?: return false
        val view = surface ?: return false
        val state = view.readyState ?: return false
        val oldIndex = documentIndex
        val displayed = documents.getOrNull(oldIndex) ?: return false
        if (current.chapter !== native || displayed.chapter !== native || position() != reportedPosition ||
            view.isLayoutRequested || view.width <= 0 || view.height <= 0 ||
            view.width.toLong() * view.height * 8 > 48L * 1024 * 1024 || view.hasPendingScroll ||
            state.optString("status") != "ready" || state.optBoolean("busy") ||
            state.optString("mode") != "HORIZONTAL" || !state.optBoolean("canvas") ||
            state.optBoolean("cover") || state.optBoolean("scrolled") ||
            (state.optJSONArray("media")?.length() ?: -1) != 0 ||
            (state.optJSONArray("galleryIndexes")?.length() ?: -1) != 0 ||
            documents.getOrNull(oldIndex + request.direction) != null) return false
        if (preparing) {
            val warming = warmingPage ?: return false
            // A current-document capture can finish against the unchanged prefix.
            // Foreground snapshot queries and cross-document jobs retain the old guard.
            if (request.direction != 1 || !preparationWorking || preparation?.hasPendingJob != true ||
                warming.first != oldIndex || warming.second <= state.optInt("pageIndex") ||
                warming.second >= state.optInt("pageCount") || warmingPaint != aloudPaintSignature ||
                frameDocument != oldIndex || frames[state.optInt("pageIndex")] == null) return false
        }
        val neighbors = neighboringChapters()
        val target = neighbors.firstOrNull {
            it.isCompleted && it.chapter.index == native.chapter.index + request.direction
        } ?: return false
        if (preparedChapters[target.chapter.index]?.chapter === target) return false
        val book = boundBook ?: return false
        val opened = snapshot ?: return false
        val key = PreparationKey(target, opened.publicationSession, opened.identity.contentRevision,
            styleGeneration, highlightRevision, book.getDelTag(Book.hTag), book.getDelTag(Book.rubyTag))
        val completed = contentSlot.find(key)?.takeIf { it.isCompleted && !it.isCancelled } ?: return false
        val next = runCatching { completed.getCompleted() }.getOrNull()?.takeIf { it.chapter === target } ?: return false
        // Repeated spine paths and annotation geometry keep their original reconciliation lifecycle.
        if (next.documents.any { it.location.path == displayed.location.path }) return false
        val prepared = preparedChapters.filterValues { value ->
            value === current || neighbors.any { it.isCompleted && value.chapter === it }
        } + (target.chapter.index to next)
        val updated = prepared.values.sortedBy { it.chapter.chapter.index }.flatMap { it.documents }
        if (updated.map { it.location.path }.distinct().size != updated.size) return false
        val index = updated.indexOfFirst { it === displayed }
        if (index < 0 || updated.size == documents.size && updated.indices.all { updated[it] === documents[it] }) return false
        if (preparing && (index != oldIndex || updated.size <= documents.size ||
                documents.indices.any { updated[it] !== documents[it] })) return false
        // Negative cache keys name the two adjacent document identities, not their window indexes.
        // Only an absent neighbour may be added; every existing cached neighbour must remain exact.
        if (listOf(-1, 1).any { step ->
                documents.getOrNull(oldIndex + step)?.let { it !== updated.getOrNull(index + step) } == true
            } || frameDocument >= 0 && frameDocument != oldIndex) return false
        val producer = preparation
        val producerEpoch = preparationEpoch
        val cachedEpoch = frameEpoch
        if (preparing) request.timing.mark("neighbor-window-overlap")
        request.timing.mark("neighbor-window-reconcile")
        preparedChapters = prepared
        failedNeighbors = failedNeighbors - target.chapter.index
        documents = updated
        documentIndex = index
        if (frameDocument == oldIndex) frameDocument = index
        // Existing canvas reconciliation retains the settled transform and original onReady path.
        // Its revision gate pauses consumption, while incoming intents keep their original tickets.
        openDocument(offset = reportedPosition, retainWindowPosition = true)
        if (preparing) request.timing.mark(if (preparation === producer && preparationEpoch == producerEpoch &&
                frameEpoch == cachedEpoch && preparation?.hasPendingJob == true) "neighbor-window-work-retained"
            else "neighbor-window-work-changed")
        return true
    }

    private fun scheduleWarm() {
        host.removeCallbacks(warmTask)
        host.postOnAnimation(warmTask)
    }

    private fun clearFrames(keepPreparedDocument: Boolean = false, releaseHost: Boolean = false) {
        host.removeCallbacks(warmTask)
        pendingTurnFrame = null
        frames.clear()
        // Paint changes do not change document geometry. Let an in-flight warm
        // capture finish; its paint signature below prevents publishing old ink.
        if (keepPreparedDocument) return
        frameEpoch++
        frameDocument = -1
        cancelPreparation(releaseHost)
    }

    private fun cancelPreparation(releaseHost: Boolean = false) {
        val wasWorking = preparationWorking || preparation?.hasPendingJob == true
        warmWatchdog.cancel()
        preparationEpoch++
        preparationWorking = false
        warmingPage = null
        borrowedFrames.forEach { it.close() }
        borrowedFrames = emptyList()
        if (releaseHost) {
            preparation?.close()
            preparation = null
        } else if (wasWorking) preparation?.cancel()
        // An idle preparer has no outstanding callback to cancel. Retain its settled layout;
        // movePrepared still checks content, documents, fonts, viewport and effective options.
    }

    private fun promoteTurnFrame(state: JSONObject): Boolean {
        val target = pendingTurnFrame ?: return false
        val page = state.optInt("pageIndex", -1)
        if (page < 0 || target.frame.pageIndex != page ||
            documents.getOrNull(documentIndex)?.payload?.optString("key") != target.documentKey ||
            frames[target.key] !== target.frame) return false
        val step = if (target.key == -1) 1 else -1
        val previousKey = if (step > 0) -2 else -1
        val retainSource = target.sourceKey >= 0 && target.sourceFrame.pageIndex == target.sourceKey &&
            target.sourceFrame.staticPage && target.frame.staticPage && target.sourceFrame.canvas && target.frame.canvas &&
            target.sourceFrame.bitmap.width == target.frame.bitmap.width && target.sourceFrame.bitmap.height == target.frame.bitmap.height &&
            frameDocument == documentIndex - step &&
            documents.getOrNull(frameDocument)?.payload?.optString("key") == target.sourceDocumentKey &&
            frames[target.sourceKey] === target.sourceFrame
        cancelPreparation()
        // Move the old edge first: a one-page chapter's source key can be the new page zero.
        // This transfers its existing cache entry and leases, without allocating another frame.
        val retainedSource = retainSource && frames.rekey(target.sourceKey, previousKey)
        if (!frames.rekey(target.key, page)) return false
        val retained = mutableSetOf(page)
        if (retainedSource) {
            retained.add(previousKey)
            turnTiming?.mark("boundary-source-retained")
        }
        // Carry only the adjacent document's already completed lookahead frames.
        // Their negative keys still belong to this exact source window and epoch.
        val keys = if (step > 0) listOf(-3, -5) else listOf(-4, -6)
        keys.forEachIndexed { offset, key ->
            val extra = frames[key]
            val expected = page + step * (offset + 1)
            if (expected in 0 until state.optInt("pageCount") && extra?.pageIndex == expected &&
                frames.rekey(key, expected)) retained.add(expected)
        }
        frames.retain(retained)
        frameEpoch++
        frameDocument = documentIndex
        turnTiming?.mark("boundary-frame-promoted")
        return true
    }

    private fun preparer(current: EpubLayoutSurface, chrome: Bitmap?): EpubPreparedTurn {
        val viewport = Rect(current.left, current.top, current.right, current.bottom)
        val hardwareFrames = chrome == null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q
        val width = chrome?.width ?: host.width
        val height = chrome?.height ?: host.height
        preparation?.let {
            if (it.matches(width, height, viewport, hardwareFrames)) return it
            it.close()
            preparation = null
        }
        return EpubPreparedTurn(host.context, snapshot!!.publicationSession,
            width, height, viewport, hardwareFrames, nativeBackground,
            onError = failure@ { error ->
                if (warmingPage != null && turning) {
                    cancelPreparation(releaseHost = true)
                    return@failure
                }
                val requested = turning || pendingTurns.isNotEmpty() || pageTextMeasuring
                clearFrames(releaseHost = true)
                if (requested) {
                    cancelTurn()
                    reportError(error.localizedMessage ?: "EPUB 翻页画面准备失败")
                }
            },
            setContent = { view, location, configuration -> documents.getOrNull(configuration.optInt("index", -1))
                ?.takeIf { it.location.path == location.path }?.let {
                    view.readerFont(readerFontBytes)
                    view.titleFont(titleFontBytes)
                    view.preparedDocuments(documents, configuration.optInt("index", -1))
                    view.content(it.location.path, it.html, it.payload, it.payloadJson)
                } },
        ).also { preparation = it }
    }

    private fun warmFrames(capturedOptions: JSONObject? = null) {
        // The boundary frames keep the old window's identity until original onReady commits it.
        if (pendingTurnFrame != null) return
        val current = surface ?: return
        val state = current.readyState ?: return
        // The overlap owner can snapshot the settled source before starting its live move.
        // Its frozen frames stay leased even if a queued input has already ended the animation.
        val beforeLiveMove = capturedOptions != null
        if (!beforeLiveMove && turnHandoff.waitingForMove) {
            // The turn already owns a source snapshot. A scheduled warm query here would
            // race it, claim preparationWorking, then be cancelled by that turn's move.
            turnTiming?.mark("warm-await-owned-snapshot")
            return
        }
        val animating = turning && (!committing || beforeLiveMove) && (beforeLiveMove || !awaitingAnimationDraw) &&
            anticipatedPage != null && (recordAnimationFrames() || beforeLiveMove)
        if (closed || loading || turning && !animating || committing && !beforeLiveMove || preparationWorking || pageTextMeasuring || !host.isShown || chapterTurnTarget != null ||
            pendingTurns.isNotEmpty() && !animating || activeInteraction != null ||
            pendingInteractions.isNotEmpty() && !beforeLiveMove || preparedContent == null ||
            current.isLayoutRequested || current.width <= 0 || current.height <= 0) return
        // A plain manual no-animation move uses the live prelaid document. Creating
        // an unused pixel producer at its edge can block input on the UI thread.
        val manualNoAnimation = !animationsEnabled() && !autoPaging() && selection == null && selectionDrag == null &&
            speechFollowPosition == null && pendingReadAloud == null && aloudPaintSignature == "[]"
        if (manualNoAnimation || (!animationsEnabled() && !recordAnimationFrames()) || isScroll() || state.optString("mode") == "FIXED" ||
            (state.optJSONArray("media")?.length() ?: 0) > 0 ||
            current.width.toLong() * current.height * 8 > 48L * 1024 * 1024) { prepareNeighbor(); return }
        val page = state.optInt("pageIndex")
        val center = anticipatedPage ?: page
        val count = state.optInt("pageCount")
        // At most four full-resolution frames, with fewer on large displays (48 MiB budget).
        val capacity = (48L * 1024 * 1024 / (current.width.toLong() * current.height * 4)).toInt().coerceAtMost(4)
        if (capacity < 2) return
        val window = epubWarmTargets(documentIndex, center, count, documents.size, lastDirection, capacity,
            boundaryOnly = !animationsEnabled())
        if (frameDocument != documentIndex) { clearFrames(); frameDocument = documentIndex }
        frames.retain(window.map { it.key }.toSet())
        val target = window.firstOrNull {
            frames[it.key] == null && (!it.last || it.page == -1 ||
                frames[-2]?.pageIndex?.let { last -> last + it.page + 1 >= 0 } == true)
        } ?: run { prepareNeighbor(); return }
        // Leave speculative reverse reflow for idle. Starting it before this live
        // move competes with the foreground renderer without serving the turn.
        if (beforeLiveMove && target.document != documentIndex &&
            (target.document - documentIndex) * lastDirection < 0) return
        val targetPage = if (target.last && target.page < -1) checkNotNull(frames[-2]).pageIndex + target.page + 1
            else target.page
        if (!frames.makeRoom(current.width.toLong() * current.height * 4, keep = window.map { it.key }.toSet())) {
            scheduleWarm()
            return
        }
        if (window.take(2).all { frames[it.key] != null }) prepareNeighbor()
        val mine = preparationEpoch
        val doc = documentIndex
        val paint = aloudPaintSignature
        warmingPage = target.document to targetPage
        warmingPaint = paint
        preparationWorking = true
        warmWatchdog.arm {
            if (turning) { cancelPreparation(releaseHost = true); return@arm }
            val requested = pendingTurns.isNotEmpty()
            clearFrames(releaseHost = true)
            if (requested) {
                cancelTurn()
                reportError("EPUB 翻页画面准备超时，请重试")
            }
        }
        val capture: (JSONObject?) -> Unit = capture@ { captured ->
            if (closed || mine != preparationEpoch) return@capture
            if (captured == null) {
                warmWatchdog.cancel()
                preparationWorking = false
                warmingPage = null
                if (pendingTurns.isNotEmpty()) host.post { continueTurns() }
                return@capture
            }
            // captureOptions transfers a fresh deep copy to this callback. Its only
            // consumer changes these top-level seek fields before the private job owns it.
            val config = (if (target.document == doc) captured else {
                documentIndex = target.document
                try { options(last = target.last).put("aloud", captured.optJSONObject("aloud") ?: JSONObject.NULL) }
                finally { documentIndex = doc }
            }).apply {
                remove("location"); remove("textOffset"); remove("spreadPage")
                put("page", targetPage); put("last", target.last && target.page == -1); put("preservePosition", false)
            }
            val chrome = if (recordAnimationFrames()) null else nativeFrame()
            val prepared = preparer(current, chrome)
            prepared.capturePage(chrome, documents[target.document].location, config,
                allowBufferQualification = { !turning && pendingTurns.peek() == null && animationsEnabled() && recordAnimationFrames() }) { frame ->
                if (closed || mine != preparationEpoch) { frame.close(); return@capturePage }
                warmWatchdog.cancel()
                preparationWorking = false
                warmingPage = null
                if (paint == aloudPaintSignature) {
                    frames.put(target.key, frame)
                    if (recordAnimationFrames() && frames[target.key] === frame) frame.bitmap.prepareToDraw()
                } else frame.close()
                if (pendingTurns.isNotEmpty() && !turning) {
                    // Consume the completed animated source before a previously posted speculative warm.
                    if (animationsEnabled() && recordAnimationFrames()) host.removeCallbacks(warmTask)
                    host.post { continueTurns() }
                } else scheduleWarm()
            }
            chrome?.recycle()
        }
        if (capturedOptions != null) capture(capturedOptions) else current.captureOptions(capture)
    }

    private fun drawFrame(canvas: Canvas, frame: EpubCapturedFrame, current: EpubLayoutSurface, recording: Boolean = false) {
        val saved = canvas.save()
        canvas.translate(current.left.toFloat(), current.top.toFloat())
        frame.clip?.let(canvas::clipRect)
        if (!recording && !canvas.isHardwareAccelerated &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && frame.bitmap.config == Bitmap.Config.HARDWARE) {
            // The original software consumers (for example simulation/auto paging) still
            // require pixels. Hardware page animations never enter this conversion.
            val pixels = checkNotNull(frame.bitmap.copy(Bitmap.Config.ARGB_8888, false))
            try { canvas.drawBitmap(pixels, 0f, 0f, null) } finally { pixels.recycle() }
        } else canvas.drawBitmap(frame.bitmap, 0f, 0f, null)
        canvas.restoreToCount(saved)
        if (frame.canvas) nativeChrome(canvas, frame.hideHeader, frame.hideFooter)
    }

    private fun cachedAnimationFrames(before: EpubCapturedFrame, after: EpubCapturedFrame,
        current: EpubLayoutSurface, beforeKey: Int, afterKey: Int): EpubAnimationFrames {
        val leases = listOfNotNull(frames.acquire(beforeKey), frames.acquire(afterKey))
        if (leases.size != 2) {
            leases.forEach { it.close() }
            error("EPUB cached animation lost its source frames")
        }
        return composeAnimationFrames(before, after, current, leases)
    }

    private fun composeAnimationFrames(before: EpubCapturedFrame, after: EpubCapturedFrame,
        current: EpubLayoutSurface, leases: List<EpubFrameWindow.Lease<EpubCapturedFrame>>): EpubAnimationFrames {
        if (!recordAnimationFrames() || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            try {
                val chrome = nativeFrame()
                val next = chrome.copy(Bitmap.Config.ARGB_8888, true)
                drawFrame(Canvas(chrome), before, current)
                drawFrame(Canvas(next), after, current)
                return EpubAnimationFrames(EpubAnimationFrames.Frame.raster(chrome), EpubAnimationFrames.Frame.raster(next))
            } finally { leases.forEach { it.close() } }
        }
        var first: EpubAnimationFrames.Frame? = null
        try {
            fun body(frame: EpubCapturedFrame): Bitmap? = frame.bitmap.takeIf {
                frame.staticPage && frame.canvas && android.graphics.Color.alpha(ReadBookConfig.bgMeanColor) == 255 &&
                    current.left == 0 && current.top == 0 && current.width == host.width && current.height == host.height &&
                    it.width == host.width && it.height == host.height &&
                    (frame.clip == null || frame.clip.contains(0, 0, host.width, host.height))
            }
            first = EpubAnimationFrames.Frame.record(host.width, host.height, body(before)) { canvas ->
                nativeBackground(canvas)
                drawFrame(canvas, before, current, recording = true)
            }
            val second = EpubAnimationFrames.Frame.record(host.width, host.height, body(after)) { canvas ->
                nativeBackground(canvas)
                drawFrame(canvas, after, current, recording = true)
            }
            return EpubAnimationFrames(first, second) { leases.forEach { it.close() } }
        } catch (error: Throwable) {
            try { first?.close() } finally { leases.forEach { it.close() } }
            throw error
        }
    }

    fun turn(direction: Int) {
        if (inputTicket != null) {
            stopSelectionDrag()
            gestures?.cancelForQueuedTap()
            surface?.cancelScroll()
        } else stopScroll()
        speechFollowRevision++
        speechFollowPosition = null
        if (closed) return
        if (renderFailed) { retryRendering(); return }
        if ((loading || preparedContent == null) && chapterTurnTarget == null) return
        val request = TurnRequest(direction, inputReceivedAt ?: android.os.SystemClock.elapsedRealtime(), inputTicket ?: ++inputSequence)
        request.timing.mark("enqueued")
        pendingTurns.add(request)
        if (turning && !committing) completeAnimation()
        continueTurns()
    }

    /** False leaves this exact request at the head; it must never be re-enqueued. */
    private fun startTurn(request: TurnRequest): Boolean {
        val direction = request.direction
        val currentSurface = surface ?: return false
        val state = currentSurface.readyState ?: return false
        if (isScroll() && state.optBoolean("scrolled")) {
            if (state.optBoolean(if (direction > 0) "atEnd" else "atStart")) {
                openAtEnd = direction < 0
                turnChapter(direction, request.timing)
            }
            else surface?.advance(direction)
            return true
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
            turnChapter(delta, request.timing)
            return true
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
                if (delta != 0) turnChapter(delta, request.timing)
                else {
                    requestedPosition = target.first
                    boundBook?.let { showChapter(it) }
                }
                return true
            }
        }
        val current = surface ?: return false
        if (current.width <= 0 || current.height <= 0) return false
        val originalIndex = documentIndex
        val beforeFrame = if (frameDocument == originalIndex) frames[state.optInt("pageIndex")] else null
        val afterKey = if (targetDocument == originalIndex) index else if (direction > 0) -1 else -2
        val afterFrame = if (frameDocument == originalIndex) frames[afterKey] else null
        if (beforeFrame != null && afterFrame != null && preparationWorking && warmingPage != null)
            request.timing.mark("keep-inflight-warm")
        if (animationsEnabled() && (beforeFrame == null || afterFrame == null) && preparationWorking) {
            val needed = warmingPaint == aloudPaintSignature && (warmingPage == (originalIndex to state.optInt("pageIndex")) ||
                warmingPage == (targetDocument to if (targetDocument == originalIndex) index else if (direction > 0) 0 else -1))
            if (needed || warmingPage == null) {
                if (!request.waiting) { request.timing.mark("wait-required-frame"); request.waiting = true }
                return false
            }
            request.timing.mark("cancel-unrelated-warm")
            cancelPreparation()
        }
        val recordedTurn = animationsEnabled() && recordAnimationFrames() &&
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            state.optString("mode") != "FIXED" &&
            (state.optJSONArray("media")?.length() ?: 0) == 0 &&
            current.width.toLong() * current.height * 8 <= 48L * 1024 * 1024
        val missingFrames = (if (beforeFrame == null) 1 else 0) + (if (afterFrame == null) 1 else 0)
        if (recordedTurn && missingFrames > 0 && !frames.makeRoom(
                current.width.toLong() * current.height * 4 * missingFrames, missingFrames,
                setOf(state.optInt("pageIndex"), afterKey))) {
            if (!request.waiting) { request.timing.mark("wait-frame-budget"); request.waiting = true }
            host.postOnAnimation { continueTurns() }
            return false
        }
        request.timing.mark("start cached=${beforeFrame != null && afterFrame != null}")
        activeTurn = request
        turnTiming = request.timing
        turnPhase = "读取页面状态"
        val requestedAt = android.os.SystemClock.uptimeMillis()
        val requestEpoch = frameEpoch
        lastDirection = direction
        turning = true
        val mine = generation
        turnTimeout = Runnable {
            if (turning) {
                val phase = if (turnPhase == "捕获翻页画面") preparation?.waitingFor ?: turnPhase else turnPhase
                cancelTurn()
                reportError("EPUB 翻页画面准备超时（$phase）")
            }
        }.also { host.postDelayed(it, 25_000) }
        if (!animationsEnabled() && (!recordAnimationFrames() || beforeFrame == null || afterFrame == null)) {
            turnPhase = "等待页面显示"
            committing = true
            documentIndex = targetDocument
            if (targetDocument == originalIndex) current.move(index)
            else openDocument(last = direction < 0)
            // Match the animated commit path: a subsequent UP belongs to this
            // requested viewport and must wait for its original ready/token checks.
            committedTapToken = current.requestedToken
            committedTapTarget = documents[targetDocument].payload.getString("key") to
                if (targetDocument == originalIndex) index else if (direction > 0) 0 else -1
            return true
        }
        if (beforeFrame != null && afterFrame != null) {
            // Freeze and lease both sources before the asynchronous cross-document options
            // request; a useful warm capture may publish into the window while it is pending.
            val drawings = cachedAnimationFrames(beforeFrame, afterFrame, current, state.optInt("pageIndex"), afterKey)
            pendingAnimation = drawings
            val publish: (JSONObject?) -> Unit = publish@ { captured ->
                if (closed || mine != generation || requestEpoch != frameEpoch || !turning) {
                    if (pendingAnimation === drawings) pendingAnimation = null
                    drawings.close()
                    return@publish
                }
                if (targetDocument != originalIndex && captured == null) {
                    cancelTurn()
                    return@publish
                }
                val targetOptions = if (targetDocument != originalIndex) {
                    documentIndex = targetDocument
                    try { options(last = direction < 0).put("page", 0)
                        .put("aloud", captured?.optJSONObject("aloud") ?: JSONObject.NULL) }
                    finally { documentIndex = originalIndex }
                } else null
                anticipatedPage = if (targetDocument == originalIndex) index else null
                if (io.legado.app.BuildConfig.DEBUG) android.util.Log.d("EpubTurnFrames",
                    "cached=true prepareMs=${android.os.SystemClock.uptimeMillis() - requestedAt}")
                turnTiming?.mark("animation-request")
                awaitingAnimationDraw = true
                pendingAnimation = null // Ownership moves to the existing animation host.
                if (targetDocument != originalIndex)
                    pendingTurnFrame = TurnFrameTarget(documents[targetDocument].payload.getString("key"), afterKey, afterFrame,
                        documents[originalIndex].payload.getString("key"), state.optInt("pageIndex"), beforeFrame)
                animateAndMove(drawings, direction, documents[targetDocument].payload.getString("key") to afterFrame.pageIndex) {
                    if (targetDocument == originalIndex) current.move(index)
                    else {
                        documentIndex = targetDocument
                        val document = documents[targetDocument]
                        current.content(document.location.path, document.html, document.payload, document.payloadJson)
                        current.open(document.location, targetOptions!!)
                    }
                }
                scheduleWarm()
            }
            if (targetDocument == originalIndex) publish(null)
            else current.confirmedCaptureOptions()?.let(publish) ?: current.captureOptions(publish)
            return true
        }
        // Paint changes can retire cached frames while the missing side is still rendering.
        // Keep those borrowed sources alive until composition finishes or this turn is cancelled.
        borrowedFrames = listOfNotNull(
            if (beforeFrame != null) frames.acquire(state.optInt("pageIndex")) else null,
            if (afterFrame != null) frames.acquire(afterKey) else null,
        )
        preparationWorking = true
        val captureEpoch = preparationEpoch
        current.captureOptions { beforeOptions ->
            if (closed || mine != generation || requestEpoch != frameEpoch || captureEpoch != preparationEpoch || !turning) return@captureOptions
            if (beforeOptions == null) {
                cancelTurn()
                reportError("EPUB 页面状态尚未就绪，请重试翻页")
                return@captureOptions
            }
            val chrome = if (recordedTurn) null else nativeFrame()
            preparationFrame = chrome
            turnPhase = "捕获翻页画面"
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
                if (closed || mine != generation || requestEpoch != frameEpoch || captureEpoch != preparationEpoch || !turning) {
                    // Cached frames remain owned by the window, including cancellation.
                    if (oldBody !== beforeFrame) oldBody.close()
                    if (newBody !== afterFrame) newBody.close()
                    return@delivery
                }
                preparationWorking = false
                val cacheable = targetDocument == originalIndex && state.optString("mode") != "FIXED" &&
                    (state.optJSONArray("media")?.length() ?: 0) == 0 &&
                    current.width.toLong() * current.height * 8 <= 48L * 1024 * 1024
                val drawings: EpubAnimationFrames
                if (recordedTurn) {
                    // New and borrowed sources use the same factory. Reserve before capture,
                    // then hold every source (including retired paint) until the live handoff.
                    val sources = borrowedFrames.toMutableList()
                    borrowedFrames = emptyList()
                    frameDocument = originalIndex
                    if (oldBody !== beforeFrame) {
                        frames.put(state.optInt("pageIndex"), oldBody)
                        frames.acquire(state.optInt("pageIndex"))?.let(sources::add)
                    }
                    if (newBody !== afterFrame) {
                        frames.put(afterKey, newBody)
                        frames.acquire(afterKey)?.let(sources::add)
                    }
                    if (sources.size != 2) {
                        sources.forEach { it.close() }
                        cancelTurn()
                        return@delivery
                    }
                    drawings = composeAnimationFrames(oldBody, newBody, current, sources)
                    chrome?.recycle()
                    if (targetDocument != originalIndex)
                        pendingTurnFrame = TurnFrameTarget(documents[targetDocument].payload.getString("key"), afterKey, newBody,
                            documents[originalIndex].payload.getString("key"), state.optInt("pageIndex"), oldBody)
                    else if (!cacheable) { frames.clear(); frameDocument = -1 }
                } else {
                    val before = checkNotNull(chrome)
                    val after = before.copy(Bitmap.Config.ARGB_8888, true)
                    drawFrame(Canvas(before), oldBody, current)
                    drawFrame(Canvas(after), newBody, current)
                    drawings = EpubAnimationFrames(EpubAnimationFrames.Frame.raster(before), EpubAnimationFrames.Frame.raster(after))
                    if (cacheable) {
                        frameDocument = originalIndex
                        if (oldBody !== beforeFrame) frames.put(state.optInt("pageIndex"), oldBody)
                        if (newBody !== afterFrame) frames.put(index, newBody)
                    } else {
                        if (oldBody !== beforeFrame) oldBody.close()
                        if (newBody !== afterFrame) newBody.close()
                    }
                }
                if (cacheable) anticipatedPage = index
                borrowedFrames.forEach { it.close() }
                borrowedFrames = emptyList()
                preparationFrame = null // Ownership moves to the existing animation host.
                turnTiming?.mark("animation-request")
                awaitingAnimationDraw = true
                if (targetDocument == originalIndex) animateAndMove(drawings, direction,
                    documents[originalIndex].payload.getString("key") to newBody.pageIndex) { current.move(index) }
                else {
                    turnPhase = "等待动画结束"
                    animate(drawings, direction, {}) {
                        if (!closed && mine == generation && requestEpoch == frameEpoch && turning) {
                            turnPhase = "等待页面显示"
                            committing = true
                            documentIndex = targetDocument
                            val document = documents[targetDocument]
                            current.content(to.path, document.html, document.payload, document.payloadJson)
                            current.open(to, targetOptions)
                            committedTapToken = current.requestedToken
                            committedTapTarget = document.payload.getString("key") to newBody.pageIndex
                        }
                    }
                }
                if (io.legado.app.BuildConfig.DEBUG) android.util.Log.d("EpubTurnFrames",
                    "cached=false recorded=${drawings.recorded} ownedFrames=${frames.ownedCount} ownedBytes=${frames.ownedBytes} " +
                        "prepareMs=${android.os.SystemClock.uptimeMillis() - requestedAt}")
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
        return true
    }

    fun onAnimationFrame() {
        if (awaitingAnimationDraw) {
            awaitingAnimationDraw = false
            turnTiming?.mark("animation-draw")
            val request = activeTurn
            if (pendingTurns.hasOtherThan(request)) host.post {
                if (turning && !committing && activeTurn === request) {
                    turnTiming?.mark("complete-for-queued-input")
                    completeAnimation()
                }
            }
            scheduleWarm()
        }
    }

    private fun animateAndMove(frames: EpubAnimationFrames, direction: Int,
        tapTarget: Pair<String, Int>? = null, move: () -> Unit) {
        val epoch = frameEpoch
        turnPhase = "等待动画结束"
        val state = surface?.readyState
        val sameDocument = tapTarget?.first == documents.getOrNull(documentIndex)?.payload?.optString("key")
        val staticBoundary = state != null && state.optString("mode") == "HORIZONTAL" && !state.optBoolean("cover") &&
            (state.optJSONArray("galleryIndexes")?.length() ?: 0) == 0 &&
            pendingTurnFrame?.let { it.documentKey == tapTarget?.first && it.frame.staticPage } == true
        val canOverlap = animationsEnabled() && !autoPaging() && !isScroll() &&
            state != null && state.optString("mode") != "FIXED" && (state.optJSONArray("media")?.length() ?: 0) == 0 &&
            tapTarget != null && tapTarget.second >= 0 &&
            (sameDocument || staticBoundary)
        var ticket: Long? = null
        // The existing owner installs the frozen drawings before move starts. Keep its
        // original timing, and publish business state only after both completion signals.
        val started: () -> Unit = started@ {
            if (closed || !turning || frameEpoch != epoch || !canOverlap ||
                selection != null || selectionAnchor != null || selectionDrag != null || pendingSelectionHit != null ||
                pendingSearchSelection != null || gestures?.isActive == true ||
                activeInteraction != null || pendingInteractions.isNotEmpty()) return@started
            // A delayed GPU preparation must not block coordinate hit tests on the old page.
            // Join the handoff only now, after the animation host owns its actual frames.
            val activeTicket = turnHandoff.begin()
            ticket = activeTicket
            val current = surface ?: return@started
            val capture: (JSONObject?) -> Unit = capture@ { captured ->
                if (closed || !turning || frameEpoch != epoch || !turnHandoff.isCurrent(activeTicket)) return@capture
                // Start useful preparation while the source token is still valid. Starting
                // move first clears readyState and cancels this otherwise harmless snapshot.
                if (captured != null && sameDocument) warmFrames(captured)
                if (closed || !turning || frameEpoch != epoch || !turnHandoff.isCurrent(activeTicket)) return@capture
                if (!turnHandoff.startMove(activeTicket)) return@capture
                turnTiming?.mark("live-move-overlapped")
                move()
                if (committing) committedTapToken = surface?.requestedToken
            }
            val captured = current.confirmedCaptureOptions()
            if (captured != null) {
                turnTiming?.mark("source-snapshot-reused")
                capture(captured)
            } else current.captureOptions(capture)
        }
        animate(frames, direction, started) {
            if (!closed && turning && frameEpoch == epoch) {
                turnPhase = "等待页面显示"
                committing = true
                if (ticket == null) move()
                committedTapToken = surface?.requestedToken
                committedTapTarget = tapTarget
                ticket?.let(turnHandoff::finishAnimation)
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
                boundBook?.let { showChapter(it) }
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

    fun stopScroll() {
        stopSelectionDrag()
        gestures?.cancel()
        surface?.cancelScroll()
    }

    private fun turnChapter(direction: Int, timing: EpubStartupTiming? = null) {
        val target = chapter?.index?.plus(direction)
        chapterTurnTarget = target?.takeIf { it in 0 until (boundBook?.totalChapterNum ?: 0) }
        chapterTiming = timing
        timing?.mark("chapter-request")
        chapterTurn(direction)
        // At the book boundary there is no layout-ready callback to resume the queue.
        if (chapterTurnTarget == null) host.post { continueTurns() }
    }

    private fun cancelTurn(keepChapterTurn: Boolean = false) {
        turnHandoff.cancel()
        pageTextRequest++
        pageTextMeasuring = false
        turnTimeout?.let(host::removeCallbacks)
        turnTimeout = null
        turning = false
        committing = false
        committedTapToken = null
        committedTapTarget = null
        activeTurn = null
        turnTiming?.mark("cancelled")
        turnTiming = null
        awaitingAnimationDraw = false
        clearFrames()
        anticipatedPage = null
        if (!keepChapterTurn) {
            chapterTurnTarget = null
            chapterTiming = null
            pendingTurns.clear()
            pendingInteractions.clear()
            interactionEpoch++
            activeInteraction = null
        }
        preparationFrame?.recycle()
        preparationFrame = null
        pendingAnimation?.close()
        pendingAnimation = null
        finishAnimation()
    }

    private fun css(px: Float) = px / host.resources.displayMetrics.density

    private fun releasePublication() {
        stopSelectionDrag()
        contentJob?.cancel()
        contentJob = null
        contentSlot.clear()
        mappingWrite?.cancel()
        mappingRevision = EpubMappingPersistence.next()
        renderFailed = false
        rendererRecoveryUsed = false
        speechFollowPosition = null
        speechFollowRevision++
        pageTextRequest++
        pendingReadAloud = null
        selectionRevision++
        pendingSelectionHit = null
        selectionAnchor = null
        generation++
        contentGeneration++
        styleGeneration++
        highlightRevision++
        pendingHighlightUpdate = false
        cancelTurn()
        preparation?.close()
        preparation = null
        gestures?.cancel()
        gestures = null
        surface?.let { host.removeView(it); it.close() }
        surface = null
        visibleChromeMask = 0
        visibleInteractionToken = null
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
