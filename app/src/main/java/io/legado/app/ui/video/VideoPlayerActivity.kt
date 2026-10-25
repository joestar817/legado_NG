package io.legado.app.ui.video

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.textclassifier.TextClassifier
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.ui.design.theme.NgInterfaceFontMarkwonPlugin
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.RssSource
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.source.webCacheObject
import io.legado.app.help.http.BookSourceCookieStore
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.getInjectionString
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebViewPool
import io.legado.app.model.VideoPlay
import io.legado.app.service.VideoPlayService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.favorites.RssFavoritesDialog
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.text.ScrollTextView
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.dpToPx
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setHtml
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import android.widget.FrameLayout
import android.provider.Settings
import androidx.compose.runtime.*
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.help.gsyVideo.VideoPlaybackBridge
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat

class VideoPlayerActivity : VMBaseActivity<ComposeActivityBinding, VideoPlayerViewModel>(imageBg = false),
    RssFavoritesDialog.Callback {
    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<VideoPlayerViewModel>()
    private val introContainer by lazy { FrameLayout(this) }
    private var presentation by mutableStateOf(VideoPresentation.WINDOW)
    private var revision by mutableIntStateOf(0)
    private var showShelfConfirm by mutableStateOf(false)
    private var pendingFloat = false
    private var handedOff = false
    private var initialPresentation = false
    private var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    private var windowedCutoutMode: Int? = null
    private var immersiveWindowApplied = false
    private var initIntroView = false
    private val introTextView by lazy {
        initIntroView = true
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_book_intro, introContainer, false) as ScrollTextView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            view.revealOnFocusHint = false
        }
        view
    }
    private var pooledWebView: PooledWebView? = null
    private val imgAvailableWidth by lazy {
        val textView = introTextView
        textView.width - textView.paddingLeft - textView.paddingRight - 8.dpToPx()
    }
    private var initGetter = false
    private val glideImageGetter by lazy {
        initGetter = true
        GlideImageGetter(
            this,
            introTextView,
            lifecycle,
            imgAvailableWidth,
            VideoPlay.source?.getKey()
        )
    }

    private val textViewTagHandler by lazy {
        TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                viewModel.onButtonClick(this@VideoPlayerActivity, "info button $name" , click)
            }
        })
    }

    private val bookSourceEditResult = registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
        if (it.resultCode == RESULT_OK) viewModel.upSource { revision++ }
    }
    private val rssSourceEditResult = registerForActivityResult(StartActivityContract(RssSourceEditActivity::class.java)) {
        if (it.resultCode == RESULT_OK) viewModel.upSource { revision++ }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        val continuing = (!intent.getBooleanExtra("isNew", true) || savedInstanceState != null) && VideoPlay.source != null
        if (!continuing) {
            VideoPlay.singleUrl = intent.hasExtra("videoUrl")
            VideoPlay.videoUrl = intent.getStringExtra("videoUrl")
            VideoPlay.videoTitle = intent.getStringExtra("videoTitle")
            VideoPlay.inBookshelf = intent.getBooleanExtra("inBookshelf", true)
            if (!VideoPlay.initSource(intent.getStringExtra("sourceKey"), intent.getIntExtra("sourceType", 0),
                    intent.getStringExtra("bookUrl"), intent.getStringExtra("record"))) {
                super.finish(); return
            }
            VideoPlay.startPlay(VideoPlaybackBridge)
            if (!VideoPlay.singleUrl) VideoPlay.saveRead(VideoPlay.durChapterPos)
        }
        savedInstanceState?.getString("videoPresentation")?.let {
            presentation = runCatching { VideoPresentation.valueOf(it) }.getOrDefault(VideoPresentation.WINDOW)
            initialPresentation = true
            setupSystemBar()
        }
        VideoPlay.book?.takeIf { book ->
            val intro = book.getDisplayIntro().orEmpty()
            intro.startsWith("<useweb>") || intro.startsWith("<usehtml>") || intro.startsWith("<md>")
        }?.let(::showBookIntro)
        viewModel.upStarMenuData.observe(this) { revision++ }
        binding.composeView.setContent {
            NgAppTheme(updateSystemBars = false) {
                VideoPlayerScreen(this, presentation, revision, introContainer, ::changePresentation,
                    { onBackPressedDispatcher.onBackPressed() }, ::handleAction, ::selectEpisode)
                if (showShelfConfirm) Dialog({ showShelfConfirm = false }) {
                    NgDialog(getString(R.string.add_to_bookshelf), variant = NgDialogVariant.CLASSIC_CONFIRMATION,
                        titleFontSize = 18.sp, titleFontWeight = FontWeight.Medium, actions = {
                        NgDialogTextActionButton(getString(R.string.video_shelf_decline), {
                            showShelfConfirm = false; callBackBookEnd()
                            viewModel.removeFromBookshelf { super.finish() }
                        }, secondary = true)
                        NgDialogTextActionButton(getString(R.string.add), {
                            VideoPlay.book?.removeType(BookType.notShelf)
                            VideoPlay.book?.save(); VideoPlay.inBookshelf = true
                            setResult(RESULT_OK); showShelfConfirm = false
                        })
                    }) {
                        Text(VideoPlay.book?.name.orEmpty(), color = Color(NgTheme.colors.onSurface),
                            fontSize = 14.sp, lineHeight = 20.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this) {
            if (presentation != VideoPresentation.WINDOW) changePresentation(VideoPresentation.WINDOW) else finish()
        }
        lifecycleScope.launch {
            VideoPlaybackBridge.owner.collect { owner ->
                if (pendingFloat && owner is VideoPlayService) {
                    pendingFloat = false; handedOff = true; super@VideoPlayerActivity.finish()
                }
            }
        }
        lifecycleScope.launch {
            VideoPlaybackBridge.state.collect { state ->
                if (state.playing || state.preparing) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (!initialPresentation && state.prepared && state.width > 0 && state.height > 0) {
                    initialPresentation = true
                    if (VideoPlay.startFull && VideoPlay.autoPlay) changePresentation(VideoPresentation.FULLSCREEN)
                    else if (VideoPlay.isPortraitVideo) changePresentation(VideoPresentation.IMMERSIVE)
                }
            }
        }
    }

    private fun selectEpisode(volumeIndex: Int, episodeIndex: Int) {
        val groups = videoEpisodeGroups(VideoPlay.toc.orEmpty(), { it.isVolume }, {
            it.url.isNotBlank() && !it.url.startsWith(it.title)
        })
        if (groups.getOrNull(volumeIndex)?.items?.none { it.episodeIndex == episodeIndex } != false) return
        if (volumeIndex == VideoPlay.durVolumeIndex && episodeIndex == VideoPlay.chapterInVolumeIndex) return
        VideoPlay.durVolumeIndex = volumeIndex
        VideoPlay.chapterInVolumeIndex = episodeIndex
        VideoPlay.upEpisodes(); VideoPlay.saveRead(0); revision++
        VideoPlay.startPlay(VideoPlaybackBridge)
    }
    private fun changePresentation(mode: VideoPresentation) {
        if (presentation == VideoPresentation.WINDOW) {
            orientation = when (requestedOrientation) {
                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                ActivityInfo.SCREEN_ORIENTATION_USER,
                ActivityInfo.SCREEN_ORIENTATION_SENSOR ->
                    if (resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT)
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> requestedOrientation
            }
        }
        presentation = mode
        setupSystemBar()
        requestedOrientation = when (mode) {
            VideoPresentation.WINDOW -> orientation
            VideoPresentation.IMMERSIVE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            VideoPresentation.FULLSCREEN -> if (VideoPlay.isPortraitVideo) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }
    override fun setupSystemBar() {
        val windowed = presentation == VideoPresentation.WINDOW
        if (windowed && immersiveWindowApplied) {
            // 分屏时 Base 不执行 fullScreen，也要恢复进入沉浸前的窗口适配。
            WindowCompat.setDecorFitsSystemWindows(window, true)
        }
        super.setupSystemBar()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val attributes = window.attributes
            val cutoutMode = if (windowed) {
                windowedCutoutMode ?: attributes.layoutInDisplayCutoutMode
            } else {
                if (windowedCutoutMode == null) {
                    windowedCutoutMode = attributes.layoutInDisplayCutoutMode
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            if (attributes.layoutInDisplayCutoutMode != cutoutMode) {
                attributes.layoutInDisplayCutoutMode = cutoutMode
                window.attributes = attributes
            }
        }
        if (!windowed) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        immersiveWindowApplied = !windowed
        toggleSystemBar(windowed)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("videoPresentation", presentation.name)
        super.onSaveInstanceState(outState)
    }
    private fun handleAction(id: Int) {
        when (id) {
            R.id.menu_custom_btn -> {
                (VideoPlay.source as? BookSource)?.let {source ->
                    VideoPlay.book?.let { book ->
                        SourceCallBack.callBackBtn(
                            this,
                            SourceCallBack.CLICK_CUSTOM_BUTTON,
                            source,
                            book,
                            VideoPlay.chapter,
                            BookType.video
                        )
                    }
                }
            }
            R.id.menu_rss_star -> viewModel.addFavorite {
                VideoPlay.rssStar?.let { showDialogFragment(RssFavoritesDialog(it)) }
            }
            R.id.menu_float_window -> startFloatingWindow()
            R.id.menu_config_settings -> Unit
            R.id.menu_login -> VideoPlay.source?.let {s ->
               when (s) {
                    is BookSource -> {
                        startActivity<SourceLoginActivity> {
                            putExtra("bookType", BookType.video)
                        }
                    }
                    is RssSource -> {
                        startActivity<SourceLoginActivity> {
                            putExtra("type", "rssSource")
                            putExtra("key", s.getKey())
                        }
                    }
                }
            }

            R.id.menu_copy_video_url -> {
                val url = VideoPlay.videoUrl
                if (url.isNullOrBlank()){
                    this.toastOnUi("暂无播放地址")
                    return
                }
                VideoPlay.book?.let {
                    SourceCallBack.callBackBtn(
                        this,
                        SourceCallBack.CLICK_COPY_PLAY_URL,
                        VideoPlay.source as? BookSource,
                        it,
                        VideoPlay.chapter,
                        BookType.video,
                        url
                    ) {
                        sendToClip(url)
                    }
                }
            }
            R.id.menu_open_other_video_player -> {
                val url = VideoPlay.videoUrl
                if (url.isNullOrBlank()){
                    this.toastOnUi("暂无播放地址")
                    return
                }
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(url.toUri(), "video/*")
                }
                startActivity(intent)
            }
            R.id.menu_edit_source -> VideoPlay.source?.let {s  ->
                when (s) {
                    is BookSource -> bookSourceEditResult.launch {
                        putExtra("sourceUrl", s.getKey())
                    }
                    is RssSource -> rssSourceEditResult.launch {
                        putExtra("sourceUrl", s.getKey())
                    }
                }
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
        }
    }

    private fun startFloatingWindow() {
        if (pendingFloat) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            PermissionsCompat.Builder().addPermissions(Permissions.SYSTEM_ALERT_WINDOW)
                .rationale(R.string.float_permission_rationale).onGranted { startFloatingWindow() }.request()
            return
        }
        pendingFloat = true
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, VideoPlayService::class.java).putExtra("isNew", false))
        }.onFailure {
            pendingFloat = false; toastOnUi("无法打开小窗：${it.localizedMessage}")
        }
        lifecycleScope.launch {
            kotlinx.coroutines.delay(5000)
            if (pendingFloat) { pendingFloat = false; toastOnUi("小窗未能启动，请重试") }
        }
    }
    override fun observeLiveBus() {
        observeEventSticky<String>(EventBus.VIDEO_SUB_TITLE) { revision++ }
        observeEvent<ArrayList<Int>>(EventBus.UP_VIDEO_INFO) { revision++ }
    }
    override fun finish() {
        if (pendingFloat) return
        if (handedOff || VideoPlay.book == null) return super.finish()
        if (VideoPlay.inBookshelf) { callBackBookEnd(); return super.finish() }
        if (!AppConfig.showAddToShelfAlert) {
            callBackBookEnd(); viewModel.removeFromBookshelf { super.finish() }
        } else showShelfConfirm = true
    }
    private fun callBackBookEnd() {
        SourceCallBack.callBackBook(SourceCallBack.END_READ, VideoPlay.source as? BookSource, VideoPlay.book, VideoPlay.chapter)
    }
    override fun updateFavorite(title: String?, group: String?) = viewModel.updateFavorite(title, group)
    override fun deleteFavorite() = viewModel.delFavorite()
    override fun onStart() { super.onStart(); if (initGetter) glideImageGetter.start() }
    override fun onStop() { super.onStop(); if (initGetter) glideImageGetter.stop() }
    override fun onDestroy() {
        destroyWeb()
        if (initGetter) glideImageGetter.clear()
        if (!handedOff && !isChangingConfigurations) VideoPlaybackBridge.release(this)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }
    private fun destroyWeb() {
        pooledWebView?.let { WebViewPool.release(it) }; pooledWebView = null
    }
    inner class CustomWebViewClient : WebViewClient() {
        private val jsStr = getInjectionString
        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            request?.let {
                val uri = it.url
                return when (uri.scheme) {
                    "http", "https" -> false
                    "legado", "yuedu" -> {
                        startActivity<OnLineImportActivity> {
                            data = uri
                        }
                        true
                    }

                    else -> {
                        binding.root.longSnackbar(R.string.jump_to_another_app, R.string.confirm) {
                            openUrl(uri)
                        }
                        true
                    }
                }
            }
            return true
        }
        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            super.onPageStarted(view, url, favicon)
            view?.evaluateJavascript(jsStr, null)
        }
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            if (view != null && url != null) {
                BookSourceCookieStore.forBookSource(VideoPlay.source)?.captureFromWebView(
                    pageUrl = url
                )
            }
            view?.post {
                introContainer.requestLayout()
            }
        }
    }

    private fun showBookIntro(book: Book) {
        val intro = book.getDisplayIntro()
        if (intro?.startsWith("<useweb>") == true) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 8) {
                introTextView.text = intro
                return
            }
            val html = intro.substring(8, lastIndex)
            val pooledWebView = this.pooledWebView ?: let{
                val pooledWebView = WebViewPool.acquire(this)
                val webView = pooledWebView.realWebView
                webView.onResume()
                webView.webViewClient = CustomWebViewClient()
                val source = VideoPlay.source
                webView.addJavascriptInterface(source.webCacheObject(), nameCache)
                source?.let {
                    webView.addJavascriptInterface(it, nameSource)
                    val webJsExtensions = WebJsExtensions(it, null, webView)
                    webView.addJavascriptInterface(webJsExtensions, nameJava)
                }
                pooledWebView
            }
            val webView = pooledWebView.realWebView
            if (initIntroView || this.pooledWebView == null) {
                initIntroView = false
                this.pooledWebView = pooledWebView
                introContainer.removeAllViews()
                introContainer.addView(webView)
            }
            val bookUrl = VideoPlay.book?.bookUrl
                ?.takeIf { it.startsWith("http", true) }
                ?.substringBefore(",")
            if (bookUrl != null) {
                BookSourceCookieStore.forBookSource(VideoPlay.source)?.applyToWebView(
                    cookieUrl = bookUrl,
                    targetUrl = bookUrl
                )
            }
            webView.loadDataWithBaseURL(bookUrl, html, "text/html", "utf-8", bookUrl)
            return
        }
        if (!initIntroView || pooledWebView != null) {
            destroyWeb()
            introContainer.removeAllViews()
            introContainer.addView(introTextView)
        }
        if (intro.isNullOrBlank()) {
            return
        }
        val tvIntro = introTextView
        if (intro.startsWith("<usehtml>")) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 9) {
                tvIntro.text = intro
                return
            }
            val html = intro.substring(9, lastIndex)
            tvIntro.setHtml(
                html,
                glideImageGetter,
                textViewTagHandler,
                imgOnLongClickListener = {
                    showDialogFragment(PhotoDialog(it, VideoPlay.source?.getKey()))
                },
                imgOnClickListener = {
                    viewModel.onButtonClick(this@VideoPlayerActivity, "info image" , it)
                }
            )
        } else if (intro.startsWith("<md>")) {
            val lastIndex = intro.lastIndexOf("<")
            if (lastIndex < 4) {
                tvIntro.text = intro
                return
            }
            val mark = intro.substring(4, lastIndex)
            lifecycleScope.launch {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    tvIntro.setTextClassifier(TextClassifier.NO_OP)
                }
                val context = this@VideoPlayerActivity
                val markwon: Markwon
                val markdown = withContext(IO) {
                    markwon = Markwon.builder(context)
                        .usePlugin(NgInterfaceFontMarkwonPlugin(context))
                        .usePlugin(
                            GlideImagesPlugin.create(
                                Glide.with(context)
                                    .applyDefaultRequestOptions(
                                        RequestOptions()
                                            .override(imgAvailableWidth)
                                            .encodeQuality(88)
                                    )
                            )
                        )
                        .usePlugin(HtmlPlugin.create())
                        .usePlugin(TablePlugin.create(context))
                        .build()
                    markwon.toMarkdown(mark)
                }
                tvIntro.setMarkdown(
                    markwon,
                    markdown,
                    imgOnLongClickListener = { source ->
                        showDialogFragment(PhotoDialog(source, VideoPlay.source?.getKey()))
                    }
                )
            }
        } else {
            tvIntro.text = intro
        }
    }

}
