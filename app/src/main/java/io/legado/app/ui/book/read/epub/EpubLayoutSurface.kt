package io.legado.app.ui.book.read.epub

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Rect
import android.net.http.SslError
import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
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
) : FrameLayout(context), Closeable {
    private val gateway = EpubResourceGateway(publication, readerRuntime = true)
    // Android 14's UiModeManager callback can retain the Context that created it.
    // This process-wide service must not retain an EPUB Activity or preparation window.
    val webView = WebView(object : ContextWrapper(context) {
        override fun getSystemService(name: String): Any? =
            if (name == Context.UI_MODE_SERVICE) applicationContext.getSystemService(name)
            else super.getSystemService(name)
    })
    private val runtime = listOf("epub/lines.js", "epub/content.js", "epub/reader.js", "epub/continuous.js", "epub/container.js").joinToString("\n") { asset ->
        context.assets.open(asset).bufferedReader().use { it.readText() }
    }
    private var loadUrl: String? = null
    private var contentKey: String? = null
    private var documentsKey = ""
    private var documentOptions = JSONArray()
    private var container = false
    private val api get() = if (container) "window.__ngEpubContainer" else "window.__ngEpub"
    private var closed = false
    private var revision = 0L
    private var loaded = false
    private var optionsReady = false
    private var stagedDocument: Pair<String, String>? = null
    private var path: String? = null
    private var options = JSONObject()
    private var poll: Runnable? = null
    private var documentReadyPoll: Runnable? = null
    private var startup: EpubStartupTiming? = null
    private var deadline = 0L
    private var scrollDelta = 0f
    private var scrollTask: Runnable? = null
    private var scrollRevision = 0L
    var viewportFull = false
    var state: JSONObject? = null
        private set
    /** The previous page can stay visible while a new revision is being prepared. */
    val readyState: JSONObject?
        get() = state?.takeIf { !closed && it.optString("token") == revision.toString() }

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
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse {
                val response = gateway.serve(request.url.toString(), request.method, request.isForMainFrame, request.requestHeaders)
                val encoding = if (response.headers["Content-Type"]?.contains("charset=utf-8") == true) "UTF-8" else null
                return WebResourceResponse(response.mediaType, encoding, response.status, response.reason, response.headers, response.data)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true

            @Deprecated("Legacy WebView callback")
            override fun shouldOverrideUrlLoading(view: WebView, url: String) = true

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) = handler.cancel()
            override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) = handler.cancel()

            override fun onPageFinished(view: WebView, url: String) {
                if (closed || url != loadUrl || loaded) return
                startup?.mark("load-ready")
                loaded = true
                configure()
            }

            override fun onPageCommitVisible(view: WebView, url: String) {
                if (closed || url != loadUrl || loaded) return
                startup?.mark("page-commit")
                val readyDeadline = SystemClock.uptimeMillis() + 25_000
                // DOM and stylesheets are enough to select the viewport. The reader's
                // resource gate still waits for fonts/images before publishing a page.
                val task = object : Runnable {
                    override fun run() {
                        if (closed || url != loadUrl || loaded) return
                        if (SystemClock.uptimeMillis() >= readyDeadline) {
                            onError("EPUB 文档就绪超时")
                            return
                        }
                        view.evaluateJavascript("location.href === ${JSONObject.quote(url)} && " +
                            "document.readyState !== 'loading' && [].slice.call(document.querySelectorAll('link[rel~=stylesheet]')).every(" +
                            "function(e) { return e.disabled || (e.media && !matchMedia(e.media).matches) || e.sheet != null; })") { ready ->
                            if (closed || url != loadUrl || loaded) return@evaluateJavascript
                            if (ready == "true") { startup?.mark("dom-css-ready"); loaded = true; configure() }
                            else view.postDelayed(this, 16)
                        }
                    }
                }
                documentReadyPoll?.let(view::removeCallbacks)
                documentReadyPoll = task
                view.post(task)
            }
        }
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
        gateway.setContent(location.path, html, "{}")
        startup = EpubStartupTiming("visible")
        loadUrl = gateway.prepareDocument(location.copy(query = "ng-load=" + revision, fragment = null))
        startup?.mark("stage-navigate")
        webView.loadUrl(loadUrl!!)
    }

    fun open(location: EpubResourceLink, value: JSONObject) {
        if (closed) return
        val useContainer = !value.isNull("containerMode")
        if (useContainer) onViewportRequired?.invoke(true)
        if (useContainer != container) { loaded = false; container = useContainer }
        options = JSONObject(value.toString())
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
        if ((container || path == location.path) && loaded) configure() else {
            cancelPending()
            loaded = false
            webView.alpha = 0f
            path = location.path
            val target = location.copy(query = "ng-load=" + revision, fragment = null)
            startup = EpubStartupTiming(if (onViewportRequired != null) "visible" else "preparation")
            loadUrl = if (container) gateway.prepareContainer() + "?ng-load=" + revision else gateway.prepareDocument(target)
            startup?.mark("navigate")
            webView.loadUrl(loadUrl!!)
        }
    }

    fun documents(values: List<EpubPreparedContent.Document>) {
        val key = values.joinToString("/") { it.payload.getString("key") }
        if (documentsKey == key) return
        documentsKey = key
        gateway.setStyleFonts(values.flatMap { it.styleFonts.entries }.associate { it.key to it.value })
        gateway.setNineSlices(values.flatMap { it.nineSlices.entries }.associate { it.key to it.value })
        gateway.setBackgrounds(values.flatMap { it.backgrounds.entries }.associate { it.key to it.value })
        if (container) { loaded = false; cancelPending() }
        gateway.setDocumentContents(values.map { Triple(it.payload.getString("key"), it.location.path, it.html to it.payload.toString()) })
        documentOptions = JSONArray().apply { values.forEachIndexed { index, document ->
            val identity = document.payload.getString("key")
            put(JSONObject().put("url", gateway.documentUrl(document.location.copy(query = "document=$identity", fragment = null)))
                .put("contentUrl", gateway.contentUrl(identity)).put("path", document.location.path).put("occurrence", index)
                .put("startFragment", document.location.fragment ?: JSONObject.NULL).put("endFragment", document.endFragment ?: JSONObject.NULL)
                .apply { document.svgSize?.let { put("fixedWidth", it.width).put("fixedHeight", it.height).put("fixedSvg", true) } })
        } }
    }

    fun content(path: String, html: String, value: JSONObject) {
        val key = value.getString("key")
        if (key != contentKey) {
            contentKey = key
            if (stagedDocument != (path to html)) {
                stagedDocument = null
                loaded = false
                cancelPending()
            }
        }
        gateway.setContent(path, html, value.toString())
    }

    fun restyle(value: JSONObject) {
        options = JSONObject(value.toString()).put("preservePosition", true)
        if (loaded) configure()
    }

    fun updateStyles(values: List<EpubPreparedContent.Document>) {
        gateway.setStyleFonts(values.flatMap { it.styleFonts.entries }.associate { it.key to it.value })
        gateway.setNineSlices(values.flatMap { it.nineSlices.entries }.associate { it.key to it.value })
        gateway.setBackgrounds(values.flatMap { it.backgrounds.entries }.associate { it.key to it.value })
        gateway.setDocumentContents(values.map { Triple(it.payload.getString("key"), it.location.path, it.html to it.payload.toString()) })
        values.firstOrNull { it.payload.getString("key") == contentKey }?.let {
            gateway.setContent(it.location.path, it.html, it.payload.toString())
        }
        if (closed || !loaded) return
        val updates = JSONArray().apply { values.forEach { document ->
            put(JSONObject().put("key", document.payload.getString("key"))
                .put("charStyles", document.payload.getJSONArray("charStyles"))
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

    private fun configure() {
        if (closed || !loaded || !optionsReady || width == 0 || height == 0) return
        // A requested viewport resize must reach layout before starting pagination.
        if (layoutParams?.let { it.width > 0 && it.width != width || it.height > 0 && it.height != height } == true) return
        val token = begin()
        val density = resources.displayMetrics.density
        startup?.mark("configure-${width}x$height")
        options.put("token", token).put("width", width / density).put("height", height / density)
            .put("deviceWidth", width / density).put("deviceHeight", height / density)
        if (contentKey != null) options.put("contentUrl", gateway.contentUrl())
        if (container) options.put("documents", documentOptions)
        if (!container && onViewportRequired != null) options.put("viewportFull", viewportFull)
        else options.remove("viewportFull")
        options.optJSONObject("readerStyle")?.takeIf { it.optBoolean("hasFont") }?.put("fontUrl", gateway.readerFontUrl())
        options.optJSONObject("readerDefaults")?.takeIf { it.optBoolean("hasFont") }?.put("fontUrl", gateway.readerFontUrl())
        listOf("readerStyle", "readerDefaults").forEach { key ->
            options.optJSONObject(key)?.optJSONObject("title")?.takeIf { it.optBoolean("hasFont") }
                ?.put("fontUrl", gateway.titleFontUrl())
        }
        webView.evaluateJavascript(EpubWebViewCapabilities.CHECK) { supported ->
            if (closed || revision.toString() != token) return@evaluateJavascript
            if (supported != "true") {
                onError("系统 WebView 过旧，请更新后重新打开本书")
                return@evaluateJavascript
            }
            webView.evaluateJavascript(runtime, null)
            webView.evaluateJavascript("$api.configure($options)", null)
            awaitLayout(token)
        }
    }

    fun move(page: Int, location: JSONObject? = null) {
        if (closed || state == null) return
        val token = begin()
        val value = JSONObject().put("token", token).put("page", page)
        location?.let { value.put("location", it) }
        webView.evaluateJavascript("$api.move($value)", null)
        awaitLayout(token)
    }

    /** Preparation-only seek; its owner discards the surface on style/viewport/content changes. */
    fun movePrepared(location: EpubResourceLink, value: JSONObject): Boolean {
        val ready = state ?: return false
        if (closed || !loaded || container || options.optBoolean("fixed") || path != location.path ||
            ready.optString("status") != "ready") return false
        // A gallery may have changed on the visible surface since the last captured frame.
        if (value.has("galleryIndexes") &&
            value.optJSONArray("galleryIndexes")?.toString() != ready.optJSONArray("galleryIndexes")?.toString()) return false
        if (value.optBoolean("last") || !value.isNull("fragment") || value.has("textOffset")) return false
        options.put("aloud", value.optJSONObject("aloud") ?: JSONObject.NULL)
        move(value.optInt("page"), value.optJSONObject("location"))
        return true
    }

    fun advance(direction: Int) {
        if (closed || state == null) return
        val token = begin()
        val value = JSONObject().put("token", token).put("delta", direction)
        webView.evaluateJavascript("$api.move($value)", null)
        awaitLayout(token)
    }

    fun interact(value: JSONObject, callback: (JSONObject?) -> Unit) {
        val token = revision.toString()
        if (closed || state?.optString("token") != token) return
        // This is the current paint payload, also consumed by prepared animation pages.
        if (value.optString("action") == "aloud") options.put("aloud", JSONObject(value.toString()))
        value.put("token", token)
        webView.evaluateJavascript("$api.interact($value)") { raw ->
            if (!closed && revision.toString() == token) callback(runCatching { JSONObject(raw) }.getOrNull())
        }
    }

    fun resolve(url: String): EpubResourceLink? = gateway.resolve(url)

    fun setHighlights(values: JSONArray) {
        options.put("highlights", values)
        if (!closed && loaded && state != null) {
            webView.evaluateJavascript("$api.setHighlights($values)", null)
        }
    }

    fun setSelectionHighlightTransparent(transparent: Boolean) {
        options.put("selectionTransparent", transparent)
        if (!closed && loaded && state != null) {
            webView.evaluateJavascript("$api.setSelectionTransparent($transparent)", null)
        }
    }

    fun beginScroll() {
        if (!closed && state != null) webView.evaluateJavascript("$api.beginScroll()", null)
    }

    fun scroll(delta: Float) {
        if (closed || state?.optBoolean("scrolled") != true) return
        scrollDelta += delta
        if (scrollTask != null) return
        val token = revision
        val task = Runnable {
            if (closed || token != revision) return@Runnable
            val amount = scrollDelta
            scrollDelta = 0f
            val request = ++scrollRevision
            webView.evaluateJavascript("$api.scrollBy($amount);$api.captureLocation()", null)
            if (container) webView.evaluateJavascript("$api.settle()", null)
            readScrolledState(token, request, SystemClock.uptimeMillis() + 25_000)
        }
        scrollTask = task
        webView.postOnAnimation(task)
    }

    private fun readScrolledState(token: Long, request: Long, until: Long) {
        if (closed || token != revision || request != scrollRevision) return
        webView.evaluateJavascript("$api.state()") { raw ->
            if (closed || token != revision || request != scrollRevision) return@evaluateJavascript
            val report = runCatching { JSONObject(raw) }.getOrNull()
            when {
                report?.optString("token") == token.toString() && report.optString("status") == "error" -> {
                    scrollTask = null
                    scrollDelta = 0f
                    onError(report.optString("error"))
                }
                report?.optString("token") == token.toString() && report.optString("status") == "ready" && !report.optBoolean("busy") -> {
                    scrollTask = null
                    val count = report.optInt("pageCount")
                    val index = report.optInt("pageIndex", -1)
                    if (count !in 1..100_000 || index !in 0 until count) onError("EPUB 返回了无效的分页结果")
                    else publishState(report)
                    // Keep only one geometry query in flight. Incoming drag/auto-scroll
                    // deltas accumulate until its position has reached the native owner.
                    if (!closed && token == revision && scrollDelta != 0f) scroll(0f)
                }
                SystemClock.uptimeMillis() >= until -> {
                    scrollTask = null
                    scrollDelta = 0f
                    onError("EPUB 排版超时")
                }
                else -> webView.postOnAnimation { readScrolledState(token, request, until) }
            }
        }
    }

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
                callback(JSONObject(options.toString()).apply {
                    // Snapshot the current location, not the seek used when the chapter was opened.
                    // configure() applies textOffset after location, so retaining it can rewind a frame.
                    remove("textOffset")
                    remove("spreadPage")
                }.put("preservePosition", false)
                    .put("fragment", JSONObject.NULL).put("last", false)
                    .put("page", report.optInt("pageIndex"))
                    .put("location", report.optJSONObject("location") ?: JSONObject.NULL)
                    .put("galleryIndexes", report.optJSONArray("galleryIndexes") ?: JSONObject.NULL)
                    .put("index", report.optInt("index", options.optInt("index"))))
            }
        }
        capture()
    }

    private fun begin(): String {
        cancelPending()
        state = null
        deadline = SystemClock.uptimeMillis() + 25_000
        return revision.toString()
    }

    private fun publishState(report: JSONObject) {
        startup?.mark("publish")
        startup = null
        // Clip only the document layer, leaving the existing native information bars visible.
        // The same Surface is used for animated page captures, so their chrome stays identical.
        val full = report.optBoolean("bleed") || report.optBoolean("fullViewport")
        val chrome = options.optJSONObject("chromeInsets")
        clipBounds = if (!full || report.optBoolean("cover") || report.optString("mode") == "FIXED" || chrome == null) null
        else {
            val density = resources.displayMetrics.density
            val top = if (report.optBoolean("hideHeader")) 0 else (chrome.optDouble("top") * density).toInt().coerceIn(0, height)
            val bottom = if (report.optBoolean("hideFooter")) height else (height - chrome.optDouble("bottom") * density).toInt().coerceIn(top, height)
            Rect(0, top, width, bottom)
        }
        state = report
        onReady(report)
        // The visual-state callback has completed and native clipping/chrome is now in sync.
        webView.alpha = 1f
    }

    private fun cancelPending() {
        documentReadyPoll?.let(webView::removeCallbacks)
        documentReadyPoll = null
        revision++
        poll?.let(webView::removeCallbacks)
        poll = null
        scrollTask?.let(webView::removeCallbacks)
        scrollTask = null
        scrollDelta = 0f
        scrollRevision++
    }

    private fun awaitLayout(token: String) {
        val task = Runnable {
            if (closed || revision.toString() != token) return@Runnable
            webView.evaluateJavascript("$api && $api.state()") { raw ->
                if (closed || revision.toString() != token) return@evaluateJavascript
                val report = runCatching { JSONObject(raw) }.getOrNull()
                when {
                    report?.optString("token") == token && report.optString("status") == "viewport" && onViewportRequired != null ->
                        onViewportRequired?.invoke(report.optBoolean("fullViewport"))
                    report?.optString("token") == token && report.optString("status") == "error" ->
                        onError(report.optString("error"))
                    report?.optString("token") == token && report.optString("status") == "ready" -> {
                        startup?.mark("layout-ready")
                        val count = report.optInt("pageCount")
                        val index = report.optInt("pageIndex", -1)
                        if (count !in 1..100_000 || index !in 0 until count) {
                            onError("EPUB 返回了无效的分页结果")
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
                    SystemClock.uptimeMillis() >= deadline -> onError("EPUB 排版超时")
                    else -> poll?.let { webView.postDelayed(it, 32) }
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
        cancelPending()
        webView.stopLoading()
        webView.setOnTouchListener(null)
        removeView(webView)
        webView.destroy()
        gateway.close()
    }
}
