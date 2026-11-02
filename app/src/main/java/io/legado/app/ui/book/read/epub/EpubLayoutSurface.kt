package io.legado.app.ui.book.read.epub

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.net.http.SslError
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.ConsoleMessage
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.WebSettingsCompat
import io.legado.app.model.epub.EpubResourceGateway
import io.legado.app.model.epub.EpubResourceLink
import io.legado.app.model.epub.EpubPublicationSession
import org.json.JSONObject
import org.json.JSONArray
import java.io.Closeable

/** Only document rendering and geometry. Navigation, progress and reading actions belong to NG. */
@SuppressLint("SetJavaScriptEnabled")
internal class EpubLayoutSurface(
    context: Context,
    publication: EpubPublicationSession,
    private var onReady: (JSONObject) -> Unit,
    private var onError: (String) -> Unit,
    private var onViewportRequired: ((Boolean) -> Unit)? = null,
    private val captureOnly: Boolean = false,
) : FrameLayout(context), Closeable {
    private val gateway = EpubResourceGateway(publication, readerRuntime = true)
    // Android 14's UiModeManager callback can retain the Context that created it.
    // This process-wide service must not retain an EPUB Activity or preparation window.
    val webView = WebView(object : ContextWrapper(context) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.UI_MODE_SERVICE) applicationContext.getSystemService(name)
            else super.getSystemService(name)
    })
    private val runtime = listOf("epub/lines.js", "epub/content.js", "epub/reader.js", "epub/continuous.js", "epub/canvas.js", "epub/container.js").joinToString("\n") { asset ->
        context.assets.open(asset).bufferedReader().use { it.readText() }
    }
    private var loadUrl: String? = null
    private var runtimeDocumentUrl: String? = null
    private var contentKey: String? = null
    private var suppliedContent: Triple<String, String, JSONObject>? = null
    private var documentsKey = ""
    private var documentOptions = JSONArray()
    private var container = false
    private val api get() = if (container) "window.__ngEpubContainer" else "window.__ngEpub"
    private var closed = false
    private var failed = false
    var rendererTerminated = false
        private set
    private val main = Handler(Looper.getMainLooper())
    private val watchdog = EpubRenderWatchdog({ task, delay -> main.postDelayed(task, delay); Unit }, main::removeCallbacks)
    private var revision = 0L
    private var loaded = false
    private var optionsReady = false
    private var stagedDocument: Pair<String, String>? = null
    private var path: String? = null
    private var options = JSONObject()
    private var poll: Runnable? = null
    // The outer move Promise owns readiness on both surfaces. A notification only starts
    // the existing state/marks/visual checks; foreground moves still keep their two rAFs.
    private val captureReadyNotifications = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private var notifiedMove: String? = null
    private val pendingInteractions = LinkedHashSet<(JSONObject?) -> Unit>()
    private val pendingTapResults = LinkedHashSet<(JSONObject?) -> Unit>()
    private var documentReadyPoll: Runnable? = null
    private var startup: EpubStartupTiming? = null
    private var deadline = 0L
    private var scrollDelta = 0f
    private var scrollTask: Runnable? = null
    private var scrollRevision = 0L
    private var scrollFinished: ((JSONObject) -> Unit)? = null
    private var readyContentKey: String? = null
    private var readyContentUrl = ""
    private var readyDocumentsKey = ""
    private var readyReaderFontUrl = ""
    private var readyTitleFontUrl = ""
    private var readyLayoutOptions = ""
    private data class PreparedOptionsSnapshot(
        val source: String,
        val decorationPath: String?,
        val exportedReadStyle: Boolean,
        val normalized: String,
    ) {
        // Own the parsed values; callers may later mutate their nested JSON in place.
        val tree = JSONObject(source)
    }
    private var preparedOptionsSnapshot: PreparedOptionsSnapshot? = null
    private data class PreparedPreloadStamp(
        val document: EpubPreparedContent.Document,
        val generation: String,
        val layout: String,
        val documents: String,
        val readerFont: String,
        val titleFont: String,
        val paint: String,
        val width: Int,
        val height: Int,
    )
    private var preparedPreloadStamp: PreparedPreloadStamp? = null
    private var preparedPreloadRevision = 0L
    private val preparedNavigationKeys = setOf("token", "page", "location", "last", "fragment", "textOffset", "spreadPage",
        "preservePosition", "retainWindowPosition", "galleryIndexes", "aloud", "width", "height", "deviceWidth", "deviceHeight",
        "viewportFull", "contentUrl", "documents")
    private var readyWidth = 0
    private var readyHeight = 0
    private var passiveTapAvailable = true
    private var passiveTapDocumentKey: String? = null
    var viewportFull = false
    var state: JSONObject? = null
        private set
    /** The previous page can stay visible while a new revision is being prepared. */
    val readyState: JSONObject?
        get() = state?.takeIf { !closed && it.optString("token") == revision.toString() }
    val requestedToken: String? get() = revision.toString().takeIf { !closed && optionsReady }
    val hasPendingScroll: Boolean get() = scrollTask != null || scrollDelta != 0f

    /** A document without tap targets needs no DOM coordinate query during a same-document move. */
    fun canResolveBlankTap(documentKey: String): Boolean {
        if (!passiveTapAvailable || captureOnly || closed || failed || !loaded || !optionsReady ||
            passiveTapDocumentKey != documentKey || pendingInteractions.isNotEmpty() || hasPendingScroll || isLayoutRequested ||
            readyContentKey != contentKey || readyDocumentsKey != documentsKey || readyWidth != width || readyHeight != height ||
            readyContentUrl != gateway.contentUrl() || readyReaderFontUrl != gateway.readerFontUrl() ||
            readyTitleFontUrl != gateway.titleFontUrl() ||
            options.optBoolean("fixed") || (options.optJSONArray("highlights")?.length() ?: 0) != 0) return false
        val aloud = options.optJSONObject("aloud")
        return aloud == null || aloud.isNull("range") && (aloud.optJSONArray("ranges")?.length() ?: 0) == 0
    }

    /** awaitLayout applied this stored speech paint before the current visual-state fence. */
    fun hasConfirmedEmptyAloudPaint(): Boolean {
        if (readyState?.optString("status") != "ready" || readyContentKey != contentKey ||
            readyDocumentsKey != documentsKey || readyContentUrl != gateway.contentUrl()) return false
        val aloud = options.optJSONObject("aloud")
        return aloud == null || aloud.isNull("range") && (aloud.optJSONArray("ranges")?.length() ?: 0) == 0
    }

    init {
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        webView.alpha = 0f
        webView.setBackgroundColor(Color.TRANSPARENT)
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        webView.settings.apply {
            javaScriptEnabled = true // Only the bundled runtime; the resource CSP denies book scripts.
            allowFileAccess = false
            allowContentAccess = false
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            blockNetworkLoads = true
            // This WebView only renders generated, per-session origins served by the local
            // gateway (including every rejection). URL reputation checks still run for
            // intercepted requests and can delay these offline documents by seconds.
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                WebSettingsCompat.setSafeBrowsingEnabled(this, false)
            }
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            domStorageEnabled = false
            databaseEnabled = false
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            setSupportZoom(false)
            textZoom = 100
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, false)
        if (captureReadyNotifications) WebViewCompat.addWebMessageListener(webView, "__ngEpubCaptureReady", setOf(gateway.origin)) {
                _, message, sourceOrigin, isMainFrame, _ ->
            val token = runCatching { message.data }.getOrNull()
            if (isMainFrame && sourceOrigin.toString() == gateway.origin && token != null && token.length in 1..20 &&
                !closed && token == notifiedMove && token == revision.toString()) {
                notifiedMove = null
                startup?.mark("move-ready-notified")
                awaitLayout(token, fast = true)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (io.legado.app.BuildConfig.DEBUG && message.message().startsWith("EpubAloudTrace ")) {
                    android.util.Log.d("EpubAloudTrace", message.message().removePrefix("EpubAloudTrace "))
                    return true
                }
                return super.onConsoleMessage(message)
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                runtimeDocumentUrl = null
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                val notify = !closed
                rendererTerminated = true
                close()
                if (notify) onError("EPUB 渲染进程已退出")
                return true
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val requestedUrl = request.url.toString()
                val began = SystemClock.elapsedRealtime()
                val response = gateway.serve(requestedUrl, request.method, request.isForMainFrame, request.requestHeaders)
                if (request.isForMainFrame) {
                    val elapsed = SystemClock.elapsedRealtime() - began
                    main.post {
                        if (!closed && requestedUrl == loadUrl) startup?.mark("main-resource-served cost=${elapsed}ms")
                    }
                }
                val encoding = if (response.headers["Content-Type"]?.contains("charset=utf-8") == true) "UTF-8" else null
                return WebResourceResponse(response.mediaType, encoding, response.status, response.reason, response.headers, response.data)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            @Deprecated("Legacy WebView callback")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = true

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()
            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) = handler.cancel()

            override fun onPageFinished(view: WebView, url: String) {
                if (closed || failed || url != loadUrl || loaded) return
                startup?.mark("load-ready")
                documentReadyPoll?.let(webView::removeCallbacks)
                documentReadyPoll = null
                loaded = true
                watchdog.cancel()
                configure()
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (closed || failed || url != loadUrl || loaded) return
                startup?.mark("page-commit")
                awaitDocument(url)
            }
        }
    }

    /** Check that the committed document has finished loading before configuring it.
     * A non-null link.sheet can still have pending @imports, so interactive is insufficient.
     */
    private fun awaitDocument(url: String) {
        val task = object : Runnable {
            override fun run() {
                if (documentReadyPoll !== this || closed || failed || url != loadUrl || loaded) return
                webView.evaluateJavascript("location.href === ${JSONObject.quote(url)} && " +
                    "document.readyState === 'complete' && [].slice.call(document.querySelectorAll('link[rel~=stylesheet]')).every(" +
                    "function(e) { return e.disabled || (e.media && !matchMedia(e.media).matches) || e.sheet != null; })") { ready ->
                    if (documentReadyPoll !== this || closed || failed || url != loadUrl || loaded) return@evaluateJavascript
                    if (ready == "true") {
                        startup?.mark("dom-css-ready")
                        documentReadyPoll = null
                        loaded = true
                        watchdog.cancel()
                        configure()
                    } else webView.postDelayed(this, 32)
                }
            }
        }
        documentReadyPoll?.let(webView::removeCallbacks)
        documentReadyPoll = task
        webView.post(task)
    }

    fun adopt(onReady: (JSONObject) -> Unit, onError: (String) -> Unit, onViewportRequired: (Boolean) -> Unit) {
        check(!closed && !optionsReady && state == null) { "EPUB 预加载已结束" }
        this.onReady = onReady
        this.onError = onError
        this.onViewportRequired = onViewportRequired
    }

    /** Load only the hidden original document. Business coordinates are still required by open(). */
    fun stage(location: EpubResourceLink, html: String) {
        if (closed || loadUrl != null) return
        cancelPending()
        stagedDocument = location.path to html
        path = location.path
        suppliedContent = null
        gateway.setContent(location.path, html, "{}")
        startup = EpubStartupTiming("visible")
        loadUrl = gateway.prepareDocument(location.copy(query = "ng-load=" + revision, fragment = null))
        startup?.mark("stage-navigate")
        watchdog.arm { fail("EPUB 文档就绪超时") }
        webView.loadUrl(loadUrl!!)
    }

    fun open(location: EpubResourceLink, value: JSONObject) {
        if (closed) return
        failed = false
        val useContainer = !value.isNull("containerMode")
        if (useContainer) onViewportRequired?.invoke(true)
        if (useContainer != container) { loaded = false; container = useContainer }
        options = effectiveOptions(value)
        optionsReady = true
        // The resource fragment bounds the legacy chapter; it is not a new seek on every open.
        if (!options.has("fragment")) options.put("fragment", JSONObject.NULL)
        val staged = stagedDocument
        stagedDocument = null
        if (!useContainer && staged?.first == location.path && contentKey != null) {
            // content() retained the stage only after exact HTML validation. It may still be loading.
            if (loaded) configure()
            return
        }
        val sameDocument = path == location.path
        path = location.path
        if ((container || sameDocument) && loaded) configure() else {
            cancelPending()
            loaded = false
            webView.alpha = 0f
            val target = location.copy(query = "ng-load=" + revision, fragment = null)
            startup = EpubStartupTiming(if (onViewportRequired != null) "visible" else "preparation")
            loadUrl = if (container) gateway.prepareContainer() + "?ng-load=" + revision else gateway.prepareDocument(target)
            startup?.mark("navigate")
            watchdog.arm { fail("EPUB 文档就绪超时") }
            webView.loadUrl(loadUrl!!)
        }
    }

    /** A private canvas only needs its committed document until its capture target changes. */
    fun preparedDocuments(values: List<EpubPreparedContent.Document>, index: Int) {
        if (!canRetainPreparedDocuments(values, index)) documents(values)
    }

    private fun canRetainPreparedDocuments(values: List<EpubPreparedContent.Document>, index: Int): Boolean {
        return captureOnly && canConfirmPreparedDocument(values, index)
    }

    private fun canConfirmPreparedDocument(values: List<EpubPreparedContent.Document>, index: Int): Boolean {
        val document = values.getOrNull(index) ?: return false
        val ready = readyState ?: return false
        val committed = documentOptions.optJSONObject(index) ?: return false
        val supplied = suppliedContent ?: return false
        // Do not cancel the current revision merely because an unused neighbour was added.
        // A different target or projection still installs the full requested window, and
        // movePrepared remains responsible for the requested style and paint comparisons.
        return container && !failed && loaded && optionsReady &&
            options.optString("containerMode") == "canvas" && !options.optBoolean("fixed") &&
            ready.optString("status") == "ready" && !ready.optBoolean("busy") &&
            ready.optBoolean("canvas") && ready.optString("mode") == "HORIZONTAL" && !ready.optBoolean("scrolled") &&
            index == options.optInt("index", -1) && index == ready.optInt("index", -1) &&
            document.svgSize == null && !committed.optBoolean("fixedSvg") &&
            path == document.location.path && supplied.first == document.location.path &&
            supplied.second === document.html && supplied.third === document.payload &&
            readyContentKey == contentKey && contentKey == document.payload.optString("key") &&
            readyDocumentsKey == documentsKey && readyWidth == width && readyHeight == height &&
            readyContentUrl == gateway.contentUrl() && readyReaderFontUrl == gateway.readerFontUrl() &&
            readyTitleFontUrl == gateway.titleFontUrl() &&
            committed.optString("identity") == contentKey && committed.optString("path") == document.location.path &&
            committed.optString("url") == ready.optString("canvasDocumentKey") &&
            committed.optString("startFragment").takeUnless { committed.isNull("startFragment") } == document.location.fragment &&
            committed.optString("endFragment").takeUnless { committed.isNull("endFragment") } == document.endFragment
    }

    fun documents(values: List<EpubPreparedContent.Document>) {
        val key = values.joinToString("/") { it.payload.getString("key") }
        if (documentsKey == key) return
        passiveTapDocumentKey = null
        documentsKey = key
        gateway.setStyleFonts(values.flatMap { it.styleFonts.entries }.associate { it.key to it.value })
        gateway.setNineSlices(values.flatMap { it.nineSlices.entries }.associate { it.key to it.value })
        gateway.setBackgrounds(values.flatMap { it.backgrounds.entries }.associate { it.key to it.value })
        if (container) {
            // Container windows reconcile stable document identities in JS. A new neighbour
            // must not navigate the parent WebView and discard the visible chapter.
            if (loaded && options.optString("containerMode") in listOf("continuous", "canvas")) {
                startup = EpubStartupTiming("window").apply { mark("documents-updated") }
            } else loaded = false
            cancelPending(keepTapResults = loaded && options.optString("containerMode") == "canvas")
        }
        gateway.setDocumentContents(values.map { Triple(it.payload.getString("key"), it.location.path, it.html to it.payloadJson) })
        documentOptions = createDocumentOptions(values)
    }

    private fun createDocumentOptions(values: List<EpubPreparedContent.Document>) = JSONArray().apply {
        values.forEachIndexed { index, document ->
            val identity = document.payload.getString("key")
            put(JSONObject().put("url", gateway.documentUrl(document.location.copy(query = "document=$identity", fragment = null)))
                .put("contentUrl", gateway.contentUrl(identity)).put("identity", identity)
                .put("path", document.location.path).put("occurrence", index)
                .put("startFragment", document.location.fragment ?: JSONObject.NULL).put("endFragment", document.endFragment ?: JSONObject.NULL)
                .apply { document.svgSize?.let { put("fixedWidth", it.width).put("fixedHeight", it.height).put("fixedSvg", true) } })
        }
    }

    fun content(path: String, html: String, value: JSONObject, payloadJson: String) {
        // Prepared projections are replaced (including their payload) on content/mark
        // changes. Re-seeking the same immutable projection need not serialize it again.
        val supplied = suppliedContent
        if (supplied?.first == path && supplied.second === html && supplied.third === value) return
        passiveTapDocumentKey = null
        val key = value.getString("key")
        if (key != contentKey) {
            contentKey = key
            if (stagedDocument != (path to html)) {
                stagedDocument = null
                if (container && loaded && options.optString("containerMode") in listOf("continuous", "canvas")) {
                    if (startup == null) startup = EpubStartupTiming("window").apply { mark("content-updated") }
                } else loaded = false
                cancelPending(keepTapResults = container && loaded && options.optString("containerMode") == "canvas")
            }
        }
        gateway.setContent(path, html, payloadJson)
        suppliedContent = Triple(path, html, value)
    }

    fun restyle(value: JSONObject) {
        preparedPreloadStamp = null
        options = effectiveOptions(value).put("preservePosition", true)
        if (loaded) configure()
    }

    fun updateStyles(values: List<EpubPreparedContent.Document>) {
        preparedPreloadStamp = null
        passiveTapDocumentKey = null
        suppliedContent = null
        gateway.setStyleFonts(values.flatMap { it.styleFonts.entries }.associate { it.key to it.value })
        gateway.setNineSlices(values.flatMap { it.nineSlices.entries }.associate { it.key to it.value })
        gateway.setBackgrounds(values.flatMap { it.backgrounds.entries }.associate { it.key to it.value })
        gateway.setDocumentContents(values.map { Triple(it.payload.getString("key"), it.location.path, it.html to it.payloadJson) })
        values.firstOrNull { it.payload.getString("key") == contentKey }?.let {
            gateway.setContent(it.location.path, it.html, it.payloadJson)
        }
        if (closed || !loaded) return
        val updates = JSONArray().apply { values.forEach { document ->
            put(JSONObject().put("key", document.payload.getString("key"))
                .put("charStyles", document.payload.getJSONArray("charStyles"))
                .put("nightCharStyles", document.payload.getJSONArray("nightCharStyles"))
                .put("nightStyleRanges", document.payload.getJSONArray("nightStyleRanges"))
                .put("styleRanges", document.payload.getJSONArray("styleRanges")))
        } }
        val token = begin()
        options.put("token", token)
        val request = JSONObject().put("token", token).put("documents", updates)
        webView.evaluateJavascript("$api.updateStyles($request)", null)
        awaitLayout(token)
    }

    fun readerFont(bytes: ByteArray?) {
        gateway.setReaderFont(bytes)
    }

    fun titleFont(bytes: ByteArray?) {
        gateway.setTitleFont(bytes)
    }

    fun refreshViewport() { if (loaded) configure() }

    /** Preparation-only cancellation; keep the WebView, never publish its old revision. */
    fun cancelPreparation(keepDocument: Boolean = false) {
        if (closed) return
        preparedPreloadStamp = null
        cancelPending()
        state = null
        optionsReady = false
        startup = null
        webView.evaluateJavascript("$api && $api.${if (container && !keepDocument) "close" else "cancelConfiguration"}()", null)
        if (!loaded || container && !keepDocument) {
            webView.stopLoading()
            loaded = false
            loadUrl = null
            path = null
            stagedDocument = null
        }
    }

    private fun configure() {
        passiveTapDocumentKey = null
        if (closed || failed || !loaded || !optionsReady || width == 0 || height == 0) return
        // A requested viewport resize must reach layout before starting pagination.
        if (layoutParams?.let { it.width > 0 && it.width != width || it.height > 0 && it.height != height } == true) return
        val token = begin()
        startup?.mark("configure-${width}x$height")
        populateLayoutOptions(options, token)
        val documentUrl = loadUrl ?: return
        if (runtimeDocumentUrl == documentUrl) {
            webView.evaluateJavascript("$api.configure($options)", null)
            awaitLayout(token)
            return
        }
        webView.evaluateJavascript(EpubWebViewCapabilities.CHECK) { supported ->
            if (closed || revision.toString() != token) return@evaluateJavascript
            if (supported != "true") {
                fail("系统 WebView 过旧，请更新后重新打开本书")
                return@evaluateJavascript
            }
            webView.evaluateJavascript(runtime, null)
            runtimeDocumentUrl = documentUrl
            webView.evaluateJavascript("$api.configure($options)", null)
            awaitLayout(token)
        }
    }

    private fun populateLayoutOptions(value: JSONObject, token: String) {
        val density = resources.displayMetrics.density
        value.put("token", token).put("width", width / density).put("height", height / density)
            .put("deviceWidth", width / density).put("deviceHeight", height / density)
        if (contentKey != null) value.put("contentUrl", gateway.contentUrl())
        if (container) value.put("documents", documentOptions)
        if (!container && onViewportRequired != null) value.put("viewportFull", viewportFull)
        else value.remove("viewportFull")
        value.optJSONObject("readerStyle")?.takeIf { it.optBoolean("hasFont") }?.put("fontUrl", gateway.readerFontUrl())
        value.optJSONObject("readerDefaults")?.takeIf { it.optBoolean("hasFont") }?.put("fontUrl", gateway.readerFontUrl())
        listOf("readerStyle", "readerDefaults").forEach { key ->
            value.optJSONObject(key)?.optJSONObject("title")?.takeIf { it.optBoolean("hasFont") }
                ?.put("fontUrl", gateway.titleFontUrl())
        }
    }

    fun move(page: Int, location: JSONObject? = null, reason: String = "page", preserveSelection: Boolean = false,
             selectionProjection: JSONObject? = null) {
        if (closed || state == null) return
        val beforePage = state?.optInt("pageIndex", -1)
        startup = EpubStartupTiming(if (onViewportRequired != null) "visible-move" else "preparation-move")
        val token = begin()
        val value = JSONObject().put("token", token).put("page", page).put("preserveSelection", preserveSelection)
            .put("selectionProjection", selectionProjection ?: JSONObject.NULL)
        if (captureOnly && WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK))
            value.put("nativeCaptureFence", true)
        location?.let { value.put("location", it) }
        if (io.legado.app.BuildConfig.DEBUG && onViewportRequired != null) {
            value.put("debugAloudTrace", true).put("debugTraceReason", reason)
            android.util.Log.d("EpubAloudTrace", "surface-move token=$token reason=$reason beforePage=$beforePage " +
                "page=$page offset=${location?.optInt("textOffset", -1)} document=${location?.optInt("index", -1)}")
        }
        if (captureReadyNotifications) {
            notifiedMove = token
            val script = """
                (function(){var notify=function(){window.__ngEpubCaptureReady.postMessage('$token');};
                Promise.resolve().then(function(){return $api.move($value);}).then(notify,notify);})()
            """.trimIndent()
            webView.evaluateJavascript(script, null)
        } else if (captureOnly) {
            webView.evaluateJavascript("$api.move($value)") {
                if (!closed && revision.toString() == token) awaitLayout(token, fast = true)
            }
        } else {
            webView.evaluateJavascript("$api.move($value)", null)
            awaitLayout(token)
        }
    }

    fun invalidatePreparedFrame() { if (captureOnly) webView.invalidate() }

    fun preloadDocument(index: Int) {
        if (captureOnly || !container || options.optString("containerMode") != "canvas" ||
            index !in 0 until documentOptions.length() || !canConfirmCaptureOptions(readyState)) return
        val request = JSONObject().put("token", revision.toString()).put("index", index)
        webView.evaluateJavascript("$api.preloadDocument($request)", null)
    }

    /** One inactive layout, using the next capture's ordinary target options. */
    private fun canPreloadPreparedDocuments(values: List<EpubPreparedContent.Document>, index: Int): Boolean {
        if (index !in values.indices || index == options.optInt("index", -1) || values[index].svgSize != null ||
            values.size < documentOptions.length() || !canConfirmPreparedDocument(values, options.optInt("index", -1))) return false
        return (0 until documentOptions.length()).all { i ->
            val previous = documentOptions.optJSONObject(i) ?: return@all false
            val document = values[i]
            previous.optString("identity") == document.payload.optString("key") &&
                previous.optString("path") == document.location.path && previous.optInt("occurrence", i) == i &&
                previous.optString("startFragment").takeUnless { previous.isNull("startFragment") } == document.location.fragment &&
                previous.optString("endFragment").takeUnless { previous.isNull("endFragment") } == document.endFragment &&
                previous.optBoolean("fixedSvg") == (document.svgSize != null) &&
                (document.svgSize?.let { previous.optDouble("fixedWidth") == it.width.toDouble() &&
                    previous.optDouble("fixedHeight") == it.height.toDouble() } ?: true)
        }
    }

    fun preloadPreparedDocument(values: List<EpubPreparedContent.Document>, index: Int, generation: String,
                                configuration: () -> JSONObject): Boolean {
        if (!container || options.optString("containerMode") != "canvas" ||
            !canConfirmCaptureOptions(readyState) || !hasConfirmedEmptyAloudPaint() || readyState?.optBoolean("cover") != false ||
            width <= 0 || height <= 0 || !canPreloadPreparedDocuments(values, index)) return false
        val document = values[index]
        val staged = if (index >= documentOptions.length()) values.take(index + 1) else null
        val windowKey = staged?.joinToString("/") { it.payload.getString("key") } ?: documentsKey
        val paint = options.optJSONObject("aloud")?.toString() ?: "null"
        val stamp = PreparedPreloadStamp(document, generation, readyLayoutOptions, windowKey,
            gateway.readerFontUrl(), gateway.titleFontUrl(), paint, width, height)
        preparedPreloadStamp?.let { previous ->
            if (previous.document === document && previous == stamp) return true
        }
        val target = runCatching {
            effectiveOptions(configuration()).put("index", index)
                .put("aloud", options.optJSONObject("aloud")?.let { JSONObject(it.toString()) } ?: JSONObject.NULL)
        }.getOrNull() ?: return false
        if (target.optBoolean("fixed") || target.optString("flow").startsWith("scrolled-")) return false
        populateLayoutOptions(target, revision.toString())
        if (staged != null) {
            // Register only the existing window's appended prefix through this target.
            // Immutable retained resources keep their URLs; the normal configure still
            // owns documentOptions/documentsKey/options and the confirmed viewport.
            gateway.setStyleFonts(staged.flatMap { it.styleFonts.entries }.associate { it.key to it.value })
            gateway.setNineSlices(staged.flatMap { it.nineSlices.entries }.associate { it.key to it.value })
            gateway.setBackgrounds(staged.flatMap { it.backgrounds.entries }.associate { it.key to it.value })
            gateway.setDocumentContents(staged.map { Triple(it.payload.getString("key"), it.location.path, it.html to it.payloadJson) })
            target.put("documents", createDocumentOptions(staged))
        }
        val request = JSONObject().put("token", revision.toString()).put("index", index)
            .put("layout", target).put("layoutRevision", (++preparedPreloadRevision).toString())
            .put("foregroundPreload", !captureOnly)
            .put("nativeCaptureFence", captureOnly && WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK))
        preparedPreloadStamp = stamp
        webView.evaluateJavascript("$api.preloadDocument($request)", null)
        return true
    }

    private fun rejectPreparedMove(reason: String, request: JSONObject): Boolean {
        if (io.legado.app.BuildConfig.DEBUG) android.util.Log.d("EpubStartup",
            "prepared-reuse-rejected reason=$reason requested=${request.optInt("index", -1)} " +
                "stored=${options.optInt("index", -1)} confirmed=${state?.optInt("index", -1)} " +
                "loaded=$loaded optionsReady=$optionsReady status=${state?.optString("status")} " +
                "tokenMatches=${state?.optString("token") == revision.toString()}")
        return false
    }

    /** Preparation-only seek; its owner discards the surface on style/viewport/content changes. */
    fun movePrepared(location: EpubResourceLink, value: JSONObject): Boolean {
        val ready = readyState ?: return rejectPreparedMove("not-ready", value)
        if (!loaded) return rejectPreparedMove("not-loaded", value)
        if (options.optBoolean("fixed")) return rejectPreparedMove("stored-fixed", value)
        if (value.optBoolean("fixed")) return rejectPreparedMove("requested-fixed", value)
        if (path != location.path) return rejectPreparedMove("document-path", value)
        if (ready.optString("status") != "ready") return rejectPreparedMove("status", value)
        if (ready.optBoolean("busy")) return rejectPreparedMove("busy", value)
        if (contentKey == null) return rejectPreparedMove("missing-content", value)
        if (readyContentKey != contentKey) return rejectPreparedMove("content-key", value)
        if (readyDocumentsKey != documentsKey) return rejectPreparedMove("window-key", value)
        if (readyContentUrl != gateway.contentUrl()) return rejectPreparedMove("content-url", value)
        if (readyReaderFontUrl != gateway.readerFontUrl()) return rejectPreparedMove("reader-font", value)
        if (readyTitleFontUrl != gateway.titleFontUrl()) return rejectPreparedMove("title-font", value)
        if (readyWidth != width) return rejectPreparedMove("width", value)
        if (readyHeight != height) return rejectPreparedMove("height", value)
        if (readyLayoutOptions != preparedLayoutOptions(value)) return rejectPreparedMove("layout-key", value)
        if (container) {
            // Only the already committed, reflowable canvas document can seek without a
            // configure. Fixed spreads and continuous windows retain their existing lifecycle.
            val index = value.optInt("index", -1)
            val document = documentOptions.optJSONObject(index) ?: return rejectPreparedMove("missing-document", value)
            if (options.optString("containerMode") != "canvas") return rejectPreparedMove("stored-container", value)
            if (value.optString("containerMode") != "canvas") return rejectPreparedMove("requested-container", value)
            if (!ready.optBoolean("canvas")) return rejectPreparedMove("ready-container", value)
            if (ready.optString("mode") == "FIXED") return rejectPreparedMove("ready-fixed", value)
            if (ready.optBoolean("scrolled")) return rejectPreparedMove("scrolled", value)
            if (index != options.optInt("index", -1)) return rejectPreparedMove("stored-index", value)
            if (index != ready.optInt("index", -1)) return rejectPreparedMove("ready-index", value)
            if (document.optString("path") != location.path) return rejectPreparedMove("descriptor-path", value)
            if (document.optString("url").isEmpty()) return rejectPreparedMove("missing-document-url", value)
            if (ready.optString("canvasDocumentKey") != document.optString("url")) return rejectPreparedMove("document-url", value)
            val targetIndex = value.optJSONObject("location")?.optInt("index", index) ?: index
            if (targetIndex != index) return rejectPreparedMove("location-index", value)
        } else if (!value.isNull("containerMode")) return rejectPreparedMove("unexpected-container", value)
        // A gallery may have changed on the visible surface since the last captured frame.
        if (value.has("galleryIndexes") &&
            value.optJSONArray("galleryIndexes")?.toString() != ready.optJSONArray("galleryIndexes")?.toString())
            return rejectPreparedMove("gallery-indexes", value)
        if (value.optBoolean("last")) return rejectPreparedMove("last", value)
        if (!value.isNull("fragment")) return rejectPreparedMove("fragment", value)
        if (value.has("textOffset")) return rejectPreparedMove("text-offset", value)
        options.put("aloud", value.optJSONObject("aloud") ?: JSONObject.NULL)
        move(value.optInt("page"), value.optJSONObject("location"))
        return true
    }

    private fun effectiveOptions(value: JSONObject) = JSONObject(value.toString()).apply {
        put("ngNativePageDecorationCssPath", gateway.nativePageDecorationPath(
            optBoolean("ngNativePageDecorations", false)) ?: JSONObject.NULL)
        put("ngExportedReadStyle", gateway.hasExportedReadStyle())
    }

    /** Compare against the owned snapshot without rewriting unchanged JSON on every move. */
    private fun samePreparedJson(value: Any?, saved: Any?): Boolean {
        if (value == null || value === JSONObject.NULL) return saved == null || saved === JSONObject.NULL
        return when (value) {
            is JSONObject -> saved is JSONObject && value.length() == saved.length() &&
                value.keys().asSequence().all { name -> saved.has(name) && samePreparedJson(value.opt(name), saved.opt(name)) }
            is JSONArray -> saved is JSONArray && value.length() == saved.length() &&
                (0 until value.length()).all { index -> samePreparedJson(value.opt(index), saved.opt(index)) }
            is Number -> saved is Number && JSONObject.numberToString(value) == JSONObject.numberToString(saved)
            is String -> saved is String && value == saved
            is Boolean -> saved is Boolean && value == saved
            // JSONStringer serializes other scalar values as their quoted toString().
            else -> saved is String && value.toString() == saved
        }
    }

    /** Compare the same effective inputs used by open, independent of seeks and gateway URLs. */
    private fun preparedLayoutOptions(value: JSONObject): String {
        // Keep an immutable key: highlights and other nested JSON can change in place.
        // Only the committed options populate this single-entry memo; a preparation
        // request must not evict it merely because its excluded seek fields differ.
        val document = if (captureOnly && value.optString("containerMode") == "canvas" && !value.optBoolean("fixed"))
            documentOptions.optJSONObject(value.optInt("index", -1)) else null
        val occurrence = document?.optInt("occurrence", value.optInt("index", -1))?.toString()
        val sourceTree = JSONObject().apply {
            value.keys().forEach { name ->
                if (name !in preparedNavigationKeys) {
                    val titles = if (name == "titleSegmentsByDocument" && occurrence != null) value.optJSONObject(name) else null
                    // Canvas passes only this occurrence's title segments to the active
                    // document. A prepared neighbour's titles cannot invalidate its layout.
                    put(name, if (titles != null && occurrence != null) JSONObject().apply {
                        if (titles.has(occurrence)) put(occurrence, titles.get(occurrence))
                    } else value.get(name))
                }
            }
        }
        val decorationPath = gateway.nativePageDecorationPath(true)
        val exportedReadStyle = gateway.hasExportedReadStyle()
        preparedOptionsSnapshot?.let { snapshot ->
            if (snapshot.decorationPath == decorationPath &&
                samePreparedJson(sourceTree, snapshot.tree) &&
                snapshot.exportedReadStyle == exportedReadStyle) return snapshot.normalized
        }
        val source = sourceTree.toString()
        val layout = JSONObject(source).apply {
            put("ngNativePageDecorationCssPath", decorationPath.takeIf {
                optBoolean("ngNativePageDecorations", false)
            } ?: JSONObject.NULL)
            put("ngExportedReadStyle", exportedReadStyle)
        }
        listOf("readerStyle", "readerDefaults").forEach { name ->
            layout.optJSONObject(name)?.let { reader ->
                reader.remove("fontUrl")
                reader.optJSONObject("title")?.remove("fontUrl")
            }
        }
        fun ordered(item: Any?, output: StringBuilder) {
            when (item) {
                is JSONObject -> {
                    output.append('{')
                    item.keys().asSequence().sorted().forEachIndexed { index, key ->
                        if (index > 0) output.append(", ")
                        output.append(JSONObject.quote(key)).append(':')
                        ordered(item.opt(key), output)
                    }
                    output.append('}')
                }
                is JSONArray -> {
                    output.append('[')
                    for (index in 0 until item.length()) {
                        if (index > 0) output.append(", ")
                        ordered(item.opt(index), output)
                    }
                    output.append(']')
                }
                is String -> output.append(JSONObject.quote(item))
                else -> output.append(item.toString())
            }
        }
        return buildString { ordered(layout, this) }.also { normalized ->
            if (value === options) preparedOptionsSnapshot =
                PreparedOptionsSnapshot(source, decorationPath, exportedReadStyle, normalized)
        }
    }

    fun advance(direction: Int, preserveSelection: Boolean = false) {
        if (closed || state == null) return
        startup = EpubStartupTiming(if (onViewportRequired != null) "visible-move" else "preparation-move")
        val token = begin()
        val value = JSONObject().put("token", token).put("delta", direction).put("preserveSelection", preserveSelection)
        webView.evaluateJavascript("$api.move($value)", null)
        awaitLayout(token)
    }

    fun interact(value: JSONObject, callback: (JSONObject?) -> Unit) {
        val token = revision.toString()
        val action = value.optString("action")
        val emptyAloud = action == "aloud" && value.isNull("range") &&
            (value.optJSONArray("ranges")?.length() ?: 0) == 0 &&
            options.optJSONObject("aloud")?.let { it.isNull("range") && (it.optJSONArray("ranges")?.length() ?: 0) == 0 } != false
        if (!emptyAloud && action !in listOf("containsPosition", "pageBounds")) passiveTapDocumentKey = null
        val clear = action == "clear" || action == "clearRange"
        if (clear) options.remove("selectionProjection")
        if (closed || !loaded || !clear && state?.optString("token") != token) { callback(null); return }
        // This is the current paint payload, also consumed by prepared animation pages.
        if (action == "aloud") options.put("aloud", JSONObject(value.toString()))
        value.put("token", token)
        var delivered = false
        lateinit var deliver: (JSONObject?) -> Unit
        val timeout = Runnable { deliver(null) }
        deliver = { result ->
            if (!delivered) {
                delivered = true
                main.removeCallbacks(timeout)
                pendingInteractions.remove(deliver)
                pendingTapResults.remove(deliver)
                callback(result)
            }
        }
        pendingInteractions.add(deliver)
        if (action == "tap") pendingTapResults.add(deliver)
        main.postDelayed(timeout, 25_000)
        // A cancellation can arrive while a viewport is still preparing. It must
        // retire that render request rather than wait for a now-obsolete ready.
        val request = if (clear) "Object.assign($value,{token:$api.state().token})" else value.toString()
        webView.evaluateJavascript("$api && $api.interact($request)") { raw ->
            val result = runCatching { JSONObject(raw) }.getOrNull()
            // The synchronous JS hit can finish before a later move while its native
            // response arrives afterwards. Its original token proves which viewport was
            // queried; the controller still rejects book/layout epoch changes.
            val capturedTap = action == "tap" && result != null && result.optString("token") == token &&
                !result.optBoolean("handled") && !result.optBoolean("dismissed") && result.optString("highlight").isEmpty() &&
                (result.isNull("href") || result.optString("href").isEmpty())
            if (io.legado.app.BuildConfig.DEBUG && capturedTap && revision.toString() != token)
                android.util.Log.d("EpubStartup", "tap-result-retained token=$token current=$revision")
            deliver(result.takeIf { !closed && (revision.toString() == token || capturedTap) })
        }
    }

    fun resolve(url: String): EpubResourceLink? = gateway.resolve(url)

    fun setHighlights(values: JSONArray) {
        if (values.length() != 0 || (options.optJSONArray("highlights")?.length() ?: 0) != 0) passiveTapDocumentKey = null
        options.put("highlights", values)
        if (!closed && loaded && state != null) {
            webView.evaluateJavascript("$api.setHighlights($values)", null)
        }
    }

    fun setSelectionHighlightTransparent(transparent: Boolean) {
        if (options.optBoolean("selectionTransparent") != transparent) passiveTapDocumentKey = null
        options.put("selectionTransparent", transparent)
        if (!closed && loaded && state != null) {
            webView.evaluateJavascript("$api.setSelectionTransparent($transparent)", null)
        }
    }

    fun beginScroll() {
        scrollFinished = null
        if (!closed && state != null) webView.evaluateJavascript("$api.beginScroll()", null)
    }

    fun cancelScroll() {
        // Keep the in-flight batch's confirmed location, but discard queued momentum.
        scrollDelta = 0f
        scrollFinished = null
    }

    fun finishScroll(callback: (JSONObject) -> Unit) {
        if (closed || readyState?.optBoolean("scrolled") != true) return
        scrollFinished = callback
        if (scrollTask == null) completeScroll()
    }

    private fun completeScroll() {
        val callback = scrollFinished ?: return
        scrollFinished = null
        readyState?.let(callback)
    }

    fun scroll(delta: Float) {
        if (closed || state?.optBoolean("scrolled") != true) return
        scrollDelta += delta
        if (scrollTask != null) return
        val token = revision
        val task = Runnable {
            if (closed || token != revision) return@Runnable
            watchdog.arm { fail("EPUB 排版超时") }
            val amount = scrollDelta
            scrollDelta = 0f
            val request = ++scrollRevision
            if (container) {
                val value = JSONObject().put("token", token.toString()).put("request", request).put("delta", amount)
                webView.evaluateJavascript("$api.requestScroll($value)", null)
            } else {
                webView.evaluateJavascript("$api.scrollBy($amount);$api.captureLocation()", null)
            }
            readScrolledState(token, request, SystemClock.uptimeMillis() + 25_000)
        }
        scrollTask = task
        webView.postOnAnimation(task)
    }

    private fun readScrolledState(token: Long, request: Long, until: Long) {
        if (closed || token != revision || request != scrollRevision) return
        val query = if (container) "$api.scrollResult(${JSONObject.quote(token.toString())},$request)" else "$api.state()"
        webView.evaluateJavascript(query) { raw ->
            if (closed || token != revision || request != scrollRevision) return@evaluateJavascript
            val report = runCatching { JSONObject(raw) }.getOrNull()
            when {
                report?.optString("token") == token.toString() && report.optString("status") == "error" -> {
                    scrollTask = null
                    scrollDelta = 0f
                    fail(report.optString("error"))
                }
                report?.optString("token") == token.toString() && report.optString("status") == "ready" && !report.optBoolean("busy") -> {
                    scrollTask = null
                    val count = report.optInt("pageCount")
                    val index = report.optInt("pageIndex", -1)
                    if (count !in 1..100_000 || index !in 0 until count) fail("EPUB 返回了无效的分页结果")
                    else publishState(report)
                    // Keep only one geometry query in flight. Incoming drag/auto-scroll
                    // deltas accumulate until its position has reached the native owner.
                    if (!closed && token == revision && scrollDelta != 0f) scroll(0f)
                    else if (!closed && token == revision && scrollTask == null) completeScroll()
                }
                SystemClock.uptimeMillis() >= until -> {
                    scrollTask = null
                    scrollDelta = 0f
                    fail("EPUB 排版超时")
                }
                else -> webView.postOnAnimation { readScrolledState(token, request, until) }
            }
        }
    }

    /** The accepted source revision is sufficient for a static, idle page turn. */
    fun confirmedCaptureOptions(): JSONObject? {
        val report = readyState ?: return null
        if (!canConfirmCaptureOptions(report)) return null
        return captureOptions(report)
    }

    private fun canConfirmCaptureOptions(report: JSONObject?): Boolean {
        if (report == null || !loaded || !optionsReady || failed || hasPendingScroll || isLayoutRequested ||
            pendingInteractions.isNotEmpty() || report.optString("status") != "ready" || report.optBoolean("busy") ||
            report.optBoolean("scrolled") || report.optString("mode") != "HORIZONTAL" ||
            (report.optJSONArray("media")?.length() ?: 0) != 0 ||
            (report.optJSONArray("galleryIndexes")?.length() ?: 0) != 0) return false
        return true
    }

    private fun captureOptions(report: JSONObject) = JSONObject(options.toString()).apply {
        // Snapshot the current location, not the seek used when the chapter was opened.
        // configure() applies textOffset after location, so retaining it can rewind a frame.
        remove("textOffset")
        remove("spreadPage")
    }.put("preservePosition", false)
        .put("fragment", JSONObject.NULL).put("last", false)
        .put("page", report.optInt("pageIndex"))
        .put("location", report.optJSONObject("location") ?: JSONObject.NULL)
        .put("galleryIndexes", report.optJSONArray("galleryIndexes") ?: JSONObject.NULL)
        .put("index", report.optInt("index", options.optInt("index")))

    fun captureOptions(callback: (JSONObject?) -> Unit) {
        val token = revision.toString()
        val until = SystemClock.uptimeMillis() + 25_000
        fun capture() {
            if (closed || revision.toString() != token) { callback(null); return }
            webView.evaluateJavascript("$api && $api.state()") { raw ->
                if (closed || revision.toString() != token) { callback(null); return@evaluateJavascript }
                val report = runCatching { JSONObject(raw) }.getOrNull()
                if (report?.optString("status") != "ready" || report.optString("token") != token || report.optBoolean("busy")) {
                    if (report?.optString("status") == "error" || SystemClock.uptimeMillis() >= until) callback(null)
                    else webView.postDelayed({ capture() }, 50)
                    return@evaluateJavascript
                }
                callback(captureOptions(report))
            }
        }
        capture()
    }

    private fun begin(): String {
        cancelPending(keepTapResults = true)
        state = null
        deadline = SystemClock.uptimeMillis() + 25_000
        watchdog.arm { fail("EPUB 排版超时") }
        return revision.toString()
    }

    private fun fail(message: String) {
        if (closed || failed) return
        failed = true
        state = null
        cancelPending()
        onError(message)
    }

    private fun publishState(report: JSONObject) {
        options.remove("selectionProjection")
        watchdog.cancel()
        startup?.mark("publish")
        startup = null
        // Canvas documents clip their text inside JS. Clipping the Surface would also cut
        // their page background away from the native information bars and reader margins.
        val full = report.optBoolean("bleed") || report.optBoolean("fullViewport")
        val chrome = options.optJSONObject("chromeInsets")
        clipBounds = if (report.optBoolean("canvas") || !full || report.optBoolean("cover") || report.optString("mode") == "FIXED" || chrome == null) null
        else {
            val density = resources.displayMetrics.density
            val top = if (report.optBoolean("hideHeader")) 0 else (chrome.optDouble("top") * density).toInt().coerceIn(0, height)
            val bottom = if (report.optBoolean("hideFooter")) height else (height - chrome.optDouble("bottom") * density).toInt().coerceIn(top, height)
            Rect(0, top, width, bottom)
        }
        readyContentKey = contentKey
        readyContentUrl = gateway.contentUrl()
        readyDocumentsKey = documentsKey
        readyReaderFontUrl = gateway.readerFontUrl()
        readyTitleFontUrl = gateway.titleFontUrl()
        readyLayoutOptions = preparedLayoutOptions(options)
        readyWidth = width
        readyHeight = height
        passiveTapDocumentKey = if (report.optBoolean("canvas") && report.optString("mode") == "HORIZONTAL" &&
            !report.optBoolean("scrolled") && !report.optBoolean("cover") && !report.optBoolean("busy") &&
            !report.optBoolean("hasTapTargets", true) && report.isNull("selectionHit") &&
            (report.optJSONArray("media")?.length() ?: 0) == 0 && (report.optJSONArray("galleryIndexes")?.length() ?: 0) == 0)
            report.optJSONObject("document")?.optString("identity")?.takeIf { it.isNotEmpty() } else null
        state = report
        onReady(report)
        // The visual-state callback has completed and native clipping/chrome is now in sync.
        webView.alpha = 1f
    }

    private fun cancelPending(keepTapResults: Boolean = false) {
        watchdog.cancel()
        documentReadyPoll?.let(webView::removeCallbacks)
        documentReadyPoll = null
        revision++
        notifiedMove = null
        val cancelled = pendingInteractions.filterNot { keepTapResults && it in pendingTapResults }
        cancelled.forEach { it(null) }
        poll?.let(webView::removeCallbacks)
        poll = null
        scrollTask?.let(webView::removeCallbacks)
        scrollTask = null
        scrollDelta = 0f
        scrollRevision++
        scrollFinished = null
    }

    private fun awaitLayout(token: String, fast: Boolean = false) {
        var immediateQueries = 0
        val task = Runnable {
            if (closed || revision.toString() != token) return@Runnable
            webView.evaluateJavascript("$api && $api.state()") { raw ->
                if (closed || revision.toString() != token) return@evaluateJavascript
                val report = runCatching { JSONObject(raw) }.getOrNull()
                when {
                    report?.optString("token") == token && report.optString("status") == "viewport" && onViewportRequired != null ->
                        onViewportRequired?.invoke(report.optBoolean("fullViewport"))
                    report?.optString("token") == token && report.optString("status") == "error" ->
                        fail(report.optString("error"))
                    report?.optString("token") == token && report.optString("status") == "ready" -> {
                        startup?.mark("layout-ready")
                        if (report.optBoolean("documentPreloaded")) startup?.mark("document-preloaded")
                        if (report.optBoolean("preloadedLayoutReused")) startup?.mark("prepared-layout-reused")
                        report.optJSONObject("timings")?.let { times ->
                            // Only numeric durations, never document text or resource URLs.
                            startup?.mark("js resources=${times.optInt("resourcesMs")} layout=${times.optInt("layoutMs")} " +
                                "frameWait=${times.optInt("frameWaitMs")} frameMax=${times.optInt("frameMaxMs")} frames=${times.optInt("frameWaits")}")
                        }
                        val count = report.optInt("pageCount")
                        val index = report.optInt("pageIndex", -1)
                        if (count !in 1..100_000 || index !in 0 until count) {
                            fail("EPUB 返回了无效的分页结果")
                            return@evaluateJavascript
                        }
                        val ready = {
                            if (!closed && revision.toString() == token) {
                                publishState(report)
                            }
                        }
                        // Visible pages and prepared animation frames consume the same marks.
                        // Include changes received while XHTML was still configuring.
                        val highlights = options.optJSONArray("highlights") ?: JSONArray()
                        val transparent = options.optBoolean("selectionTransparent")
                        val aloud = options.optJSONObject("aloud")?.let { JSONObject(it.toString()) }
                            ?: JSONObject().put("action", "aloud").put("ranges", JSONArray()).put("range", JSONObject.NULL)
                        aloud.put("token", token)
                        webView.evaluateJavascript("$api.setHighlights($highlights);$api.setSelectionTransparent($transparent);$api.interact($aloud)") {
                            if (!closed && revision.toString() == token) {
                                startup?.mark("marks-ready")
                                if (WebViewFeature.isFeatureSupported(WebViewFeature.VISUAL_STATE_CALLBACK)) {
                                    WebViewCompat.postVisualStateCallback(webView, revision, object : WebViewCompat.VisualStateCallback {
                                        override fun onComplete(requestId: Long) { ready() }
                                    })
                                } else {
                                    webView.postOnAnimation { ready() }
                                }
                            }
                        }
                    }
                    SystemClock.uptimeMillis() >= deadline -> fail("EPUB 排版超时")
                    else -> poll?.let {
                        // A settled preparation move uses zero-delay JS frames. Permit two
                        // acknowledgement queries before the ordinary bounded polling rate.
                        if (fast && immediateQueries++ < 2) webView.post(it)
                        else webView.postDelayed(it, 32)
                    }
                }
            }
        }
        poll = task
        webView.post(task)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && loaded) {
            options.put("preservePosition", true)
            configure()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        preparedOptionsSnapshot = null
        preparedPreloadStamp = null
        cancelPending()
        if (!rendererTerminated) webView.stopLoading()
        webView.setOnTouchListener(null)
        removeView(webView)
        webView.destroy()
        gateway.close()
    }
}
