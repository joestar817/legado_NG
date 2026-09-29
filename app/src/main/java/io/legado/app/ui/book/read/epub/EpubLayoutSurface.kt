package io.legado.app.ui.book.read.epub

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Rect
import android.net.http.SslError
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
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
    private var contentKey: String? = null
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
    private val pendingInteractions = LinkedHashSet<(JSONObject?) -> Unit>()
    private var documentReadyPoll: Runnable? = null
    private var startup: EpubStartupTiming? = null
    private var deadline = 0L
    private var scrollDelta = 0f
    private var scrollTask: Runnable? = null
    private var scrollRevision = 0L
    private var readyContentKey: String? = null
    private var readyContentUrl = ""
    private var readyDocumentsKey = ""
    private var readyReaderFontUrl = ""
    private var readyTitleFontUrl = ""
    private var readyLayoutOptions = ""
    private var readyWidth = 0
    private var readyHeight = 0
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
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
        }
        webView.webViewClient = object : WebViewClient() {
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

    /** Preparation-only cancellation; keep the WebView, never publish its old revision. */
    fun cancelPreparation() {
        if (closed) return
        cancelPending()
        state = null
        optionsReady = false
        startup = null
        webView.evaluateJavascript("$api && $api.${if (container) "close" else "cancelConfiguration"}()", null)
        if (!loaded || container) {
            webView.stopLoading()
            loaded = false
            loadUrl = null
            path = null
            stagedDocument = null
        }
    }

    private fun configure() {
        if (closed || failed || !loaded || !optionsReady || width == 0 || height == 0) return
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
                fail("系统 WebView 过旧，请更新后重新打开本书")
                return@evaluateJavascript
            }
            webView.evaluateJavascript(runtime, null)
            webView.evaluateJavascript("$api.configure($options)", null)
            awaitLayout(token)
        }
    }

    fun move(page: Int, location: JSONObject? = null) {
        if (closed || state == null) return
        startup = EpubStartupTiming(if (onViewportRequired != null) "visible-move" else "preparation-move")
        val token = begin()
        val value = JSONObject().put("token", token).put("page", page)
        location?.let { value.put("location", it) }
        webView.evaluateJavascript("$api.move($value)", null)
        awaitLayout(token)
    }

    /** Preparation-only seek; its owner discards the surface on style/viewport/content changes. */
    fun movePrepared(location: EpubResourceLink, value: JSONObject): Boolean {
        val ready = readyState ?: return false
        if (!loaded || options.optBoolean("fixed") || value.optBoolean("fixed") || path != location.path ||
            ready.optString("status") != "ready" || ready.optBoolean("busy") ||
            contentKey == null || readyContentKey != contentKey || readyDocumentsKey != documentsKey ||
            readyContentUrl != gateway.contentUrl() || readyReaderFontUrl != gateway.readerFontUrl() ||
            readyTitleFontUrl != gateway.titleFontUrl() ||
            readyWidth != width || readyHeight != height || readyLayoutOptions != preparedLayoutOptions(value)) return false
        if (container) {
            // Only the already committed, reflowable canvas document can seek without a
            // configure. Fixed spreads and continuous windows retain their existing lifecycle.
            val index = value.optInt("index", -1)
            val document = documentOptions.optJSONObject(index) ?: return false
            if (options.optString("containerMode") != "canvas" || value.optString("containerMode") != "canvas" ||
                !ready.optBoolean("canvas") || ready.optString("mode") == "FIXED" || ready.optBoolean("scrolled") ||
                index != options.optInt("index", -1) || index != ready.optInt("index", -1) ||
                document.optString("path") != location.path ||
                document.optString("url").isEmpty() || ready.optString("canvasDocumentKey") != document.optString("url")) return false
            val targetIndex = value.optJSONObject("location")?.optInt("index", index) ?: index
            if (targetIndex != index) return false
        } else if (!value.isNull("containerMode")) return false
        // A gallery may have changed on the visible surface since the last captured frame.
        if (value.has("galleryIndexes") &&
            value.optJSONArray("galleryIndexes")?.toString() != ready.optJSONArray("galleryIndexes")?.toString()) return false
        if (value.optBoolean("last") || !value.isNull("fragment") || value.has("textOffset")) return false
        options.put("aloud", value.optJSONObject("aloud") ?: JSONObject.NULL)
        move(value.optInt("page"), value.optJSONObject("location"))
        return true
    }

    /** Compare layout inputs independently of the frame's seek and generated gateway URLs. */
    private fun preparedLayoutOptions(value: JSONObject): String {
        val layout = JSONObject(value.toString())
        listOf("token", "page", "location", "last", "fragment", "textOffset", "spreadPage", "preservePosition",
            "galleryIndexes", "aloud", "width", "height", "deviceWidth", "deviceHeight", "viewportFull",
            "contentUrl", "documents").forEach(layout::remove)
        listOf("readerStyle", "readerDefaults").forEach { name ->
            layout.optJSONObject(name)?.let { reader ->
                reader.remove("fontUrl")
                reader.optJSONObject("title")?.remove("fontUrl")
            }
        }
        fun ordered(item: Any?): String = when (item) {
            is JSONObject -> item.keys().asSequence().sorted().joinToString(prefix = "{", postfix = "}") {
                JSONObject.quote(it) + ":" + ordered(item.opt(it))
            }
            is JSONArray -> (0 until item.length()).joinToString(prefix = "[", postfix = "]") { ordered(item.opt(it)) }
            is String -> JSONObject.quote(item)
            else -> item.toString()
        }
        return ordered(layout)
    }

    fun advance(direction: Int) {
        if (closed || state == null) return
        startup = EpubStartupTiming(if (onViewportRequired != null) "visible-move" else "preparation-move")
        val token = begin()
        val value = JSONObject().put("token", token).put("delta", direction)
        webView.evaluateJavascript("$api.move($value)", null)
        awaitLayout(token)
    }

    fun interact(value: JSONObject, callback: (JSONObject?) -> Unit) {
        val token = revision.toString()
        if (closed || state?.optString("token") != token) { callback(null); return }
        // This is the current paint payload, also consumed by prepared animation pages.
        if (value.optString("action") == "aloud") options.put("aloud", JSONObject(value.toString()))
        value.put("token", token)
        var delivered = false
        lateinit var deliver: (JSONObject?) -> Unit
        val timeout = Runnable { deliver(null) }
        deliver = { result ->
            if (!delivered) {
                delivered = true
                main.removeCallbacks(timeout)
                pendingInteractions.remove(deliver)
                callback(result)
            }
        }
        pendingInteractions.add(deliver)
        main.postDelayed(timeout, 25_000)
        webView.evaluateJavascript("$api.interact($value)") { raw ->
            deliver(if (!closed && revision.toString() == token) runCatching { JSONObject(raw) }.getOrNull() else null)
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
            watchdog.arm { fail("EPUB 排版超时") }
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
        state = report
        onReady(report)
        // The visual-state callback has completed and native clipping/chrome is now in sync.
        webView.alpha = 1f
    }

    private fun cancelPending() {
        watchdog.cancel()
        documentReadyPoll?.let(webView::removeCallbacks)
        documentReadyPoll = null
        revision++
        val cancelled = pendingInteractions.toList()
        pendingInteractions.clear()
        cancelled.forEach { it(null) }
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
                        fail(report.optString("error"))
                    report?.optString("token") == token && report.optString("status") == "ready" -> {
                        startup?.mark("layout-ready")
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
        if (!rendererTerminated) webView.stopLoading()
        webView.setOnTouchListener(null)
        removeView(webView)
        webView.destroy()
        gateway.close()
    }
}
