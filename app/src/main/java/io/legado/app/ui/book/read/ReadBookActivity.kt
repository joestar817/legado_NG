package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.Window
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.get
import androidx.core.view.size
import androidx.lifecycle.lifecycleScope
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PageAnim
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.TTS
import io.legado.app.help.ai.AiConfig
import io.legado.app.help.ai.AiPurifyHelper
import io.legado.app.help.ai.AiPurifyResult
import io.legado.app.help.ai.AiPurifyRuleCandidate as AiPurifyGeneratedRule
import io.legado.app.help.ai.AiPurifyRuleGenerateResult
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isEpub
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isMobi
import io.legado.app.help.book.removeType
import io.legado.app.help.book.update
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.getSourceType
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ReadAloud
import io.legado.app.model.BookCacheManager
import io.legado.app.model.ReadBook
import io.legado.app.model.analyzeRule.AnalyzeRule
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setChapter
import io.legado.app.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonObject
import io.legado.app.model.localBook.EpubFile
import io.legado.app.model.localBook.MobiFile
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.receiver.TimeBatteryReceiver
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.bookmark.BookmarkDialog
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.changesource.ChangeChapterSourceDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.read.aloud.ReadAloudLauncher
import io.legado.app.ui.book.read.aloud.ReadAloudMiniPlayer
import io.legado.app.ui.book.read.config.AutoReadDialog
import io.legado.app.ui.book.read.config.AiPurifyChapterConfirmDialogContent
import io.legado.app.ui.book.read.config.AiPurifyChapterSummaryUi
import io.legado.app.ui.book.read.config.AiPurifyPreviewDialogContent
import io.legado.app.ui.book.read.config.AiPurifyPreviewUi
import io.legado.app.ui.book.read.config.AiPurifyProgressDialogContent
import io.legado.app.ui.book.read.config.AiPurifyRangeDialogContent
import io.legado.app.ui.book.read.config.AiPurifyRuleDetailDialogContent
import io.legado.app.ui.book.read.config.AiPurifyRuleUi
import io.legado.app.ui.book.read.config.MoreConfigDialog
import io.legado.app.ui.book.read.config.ReadStyleDialog
import io.legado.app.ui.book.read.config.showReadConfirmDialog
import io.legado.app.ui.book.read.config.showReadComposeDialog
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_COLOR
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_DIVIDER_COLOR
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.book.searchContent.ReadSearchDialog
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleDialog
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.config.AiConfigFragment
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.dict.DictDialog
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.replace.edit.ReplaceRuleEditDialog
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgMenuPopup
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.TitleBar
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.utils.ACache
import io.legado.app.utils.Debounce
import io.legado.app.utils.LogUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.dismissDialogFragment
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.invisible
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.navigationBarGravity
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.postEvent
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.sysScreenOffTime
import io.legado.app.utils.throttle
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import com.script.rhino.runScriptWithContext
import io.legado.app.model.analyzeRule.AnalyzeUrl.Companion.paramPattern
import io.legado.app.ui.login.SourceLoginJsExtensions
import java.text.Normalizer

/**
 * 阅读界面
 */
class ReadBookActivity : BaseReadBookActivity(),
    View.OnTouchListener,
    ReadView.CallBack,
    TextActionMenu.CallBack,
    ContentTextView.CallBack,
    ReadMenu.CallBack,
    SearchMenu.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeChapterSourceDialog.CallBack,
    ReadBook.CallBack,
    AutoReadDialog.CallBack,
    TxtTocRuleDialog.CallBack,
    ReplaceRuleEditDialog.Callback,
    ColorPickerDialogListener,
    LayoutProgressListener {

    protected override val bindNgToolbarMenu: Boolean = false

    private val sourceEditActivity =
        registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                viewModel.upBookSource {
                    upMenuView()
                }
            }
        }
    private val replaceActivity =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                viewModel.replaceRuleChanged()
            }
        }
    private val bookInfoActivity =
        registerForActivityResult(StartActivityContract(BookInfoActivity::class.java)) {
            if (it.resultCode == RESULT_OK) {
                setResult(RESULT_DELETED)
                super.finish()
            } else {
                ReadBook.loadOrUpContent()
            }
        }
    private val selectImageDir = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { uri ->
            ACache.get().put(AppConst.imagePathKey, uri.toString())
            viewModel.saveImage(it.value, uri)
        }
    }
    private var menu: Menu? = null
    private var backupJob: Job? = null
    private var aiPurifyJob: Job? = null
    private var textHighlightObserveJob: Job? = null
    private var observedTextHighlightBook: Pair<String, String>? = null
    private var activeTextHighlight: Bookmark? = null
    private val textHighlightWriteMutex = Mutex()
    private var tts: TTS? = null
    val textActionMenu: TextActionMenu by lazy {
        TextActionMenu(this, this)
    }
    private val popupAction: PopupAction by lazy {
        PopupAction(this)
    }
    override val isInitFinish: Boolean get() = viewModel.isInitFinish
    override val isScroll: Boolean get() = binding.readView.isScroll
    private val isAutoPage get() = binding.readView.isAutoPage
    var isShowingSearchResult = false
    override var isSelectingSearchResult = false
        set(value) {
            field = value && isShowingSearchResult
        }
    private val timeBatteryReceiver = TimeBatteryReceiver()
    private var screenTimeOut: Long = 0
    private var loadStates: Boolean = false
    private var replaceRuleRenderBatchDepth = 0
    private var replaceRuleRenderPending = false
    private var replaceRuleRenderResetPageOffset = false
    private var replaceRuleRenderFlushScheduled = false
    private val replaceRuleRenderSuccessActions = arrayListOf<() -> Unit>()
    private val replaceRuleRenderFlushRunnable = Runnable { flushReplaceRuleRender() }
    override val pageFactory get() = binding.readView.pageFactory
    override val pageDelegate get() = binding.readView.pageDelegate
    override val headerHeight: Int get() = binding.readView.curPage.headerHeight
    override val imgBgPaddingStart: Int get() = binding.readView.curPage.imgBgPaddingStart
    private val nextPageDebounce by lazy { Debounce { keyPage(PageDirection.NEXT) } }
    private val prevPageDebounce by lazy { Debounce { keyPage(PageDirection.PREV) } }
    private var bookChanged = false
    private var pageChanged = false
    private val handler by lazy { buildMainHandler() }
    private val screenOffRunnable by lazy { Runnable { keepScreenOn(false) } }
    private val executor = ReadBook.executor
    private val upSeekBarThrottle = throttle(200) {
        runOnUiThread {
            upSeekBarProgress()
            binding.readMenu.upSeekBar()
        }
    }

    //恢复跳转前进度对话框的交互结果
    private var confirmRestoreProcess: Boolean? = null
    private val networkChangedListener by lazy {
        NetworkChangedListener(this)
    }
    private var justInitData: Boolean = false
    private var syncDialog: Dialog? = null

    @SuppressLint("ClickableViewAccessibility")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        binding.cursorLeft.setColorFilter(accentColor)
        binding.cursorRight.setColorFilter(accentColor)
        binding.cursorLeft.setOnTouchListener(this)
        binding.cursorRight.setOnTouchListener(this)
        window.setBackgroundDrawable(null)
        upScreenTimeOut()
        ReadBook.register(this)
        onBackPressedDispatcher.addCallback(this) {
            if (isShowingSearchResult) {
                exitSearchMenu()
                restoreLastBookProcess()
                return@addCallback
            }
            //拦截返回供恢复阅读进度
            if (ReadBook.lastBookProgress != null && confirmRestoreProcess != false) {
                restoreLastBookProcess()
                return@addCallback
            }
            if (isAutoPage) {
                autoPageStop()
                return@addCallback
            }
            if (getPrefBoolean("disableReturnKey") && !menuLayoutIsVisible) {
                return@addCallback
            }
            finish()
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        viewModel.initReadBookConfig(intent)
        binding.root.post {
            viewModel.initData(intent)
        }
        justInitData = true
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.initData(intent)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        upSystemUiVisibility()
        if (hasFocus) {
            if (ReadBookConfig.syncFollowSystemTheme()) {
                onReadThemeChanged()
            } else {
                binding.readMenu.upBrightnessState()
            }
        } else if (!menuLayoutIsVisible) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        upSystemUiVisibility()
        binding.readView.upStatusBar()
    }

    override fun onTopResumedActivityChanged(isTopResumedActivity: Boolean) {
        if (!isTopResumedActivity) {
            ReadBook.cancelPreDownloadTask()
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()
        ReadBook.readStartTime = System.currentTimeMillis()
        if (bookChanged) {
            bookChanged = false
            ReadBook.callBack = this
            viewModel.initData(intent)
            justInitData = true
        } else {
            //web端阅读时，app处于阅读界面，本地记录会覆盖web保存的进度，在此处恢复
            ReadBook.webBookProgress?.let {
                ReadBook.setProgress(it)
                ReadBook.webBookProgress = null
            }
        }
        upSystemUiVisibility()
        viewModel.resetReplaceRuleStateAfterResume()
        registerReceiver(timeBatteryReceiver, timeBatteryReceiver.filter)
        binding.readView.upTime()
        screenOffTimerStart()
        // 网络监听，当从无网切换到网络环境时同步进度（注意注册的同时就会收到监听，因此界面激活时无需重复执行同步操作）
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            // 当网络是可用状态且无需初始化时同步进度（初始化中已有同步进度逻辑）
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData && ReadBook.inBookshelf) {
                ReadBook.syncProgress({ progress -> sureNewProgress(progress) })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        autoPageStop()
        backupJob?.cancel()
        ReadBook.saveRead()
        ReadBook.cancelPreDownloadTask()
        unregisterReceiver(timeBatteryReceiver)
        upSystemUiVisibility()
        if (!BuildConfig.DEBUG && ReadBook.inBookshelf) {
            if (AppConfig.syncBookProgressPlus) {
                ReadBook.syncProgress()
            } else {
                ReadBook.uploadProgress()
            }
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
        justInitData = false
        networkChangedListener.unRegister()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_read, menu)
        binding.readMenu.refreshMenuColorFilter()
        NgMenuPopup.bindReadingToolbarMenu(
            context = this,
            toolbar = findViewById<TitleBar>(R.id.title_bar)?.toolbar,
            menu = menu,
            themeSnapshotProvider = binding.readMenu::currentThemeSnapshot,
            glassStyleProvider = binding.readMenu::currentFloatingGlassStyle,
            prepareMenu = {
                onPrepareOptionsMenu(menu)
                onMenuOpened(Window.FEATURE_OPTIONS_PANEL, menu)
            },
            onItemClick = { onCompatOptionsItemSelected(it) }
        )
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun showReadChangeSourceMenu(anchor: View) {
        NgActionPopup(
            context = this,
            items = listOf(
                NgActionPopupItem(
                    R.id.menu_chapter_change_source,
                    R.string.chapter_change_source,
                    R.drawable.ic_bubble_chart
                ),
                NgActionPopupItem(
                    R.id.menu_book_change_source,
                    R.string.book_change_source,
                    R.drawable.ic_bubble_chart
                )
            ),
            themeSnapshot = binding.readMenu.currentThemeSnapshot(),
        ) { item ->
            when (item.itemId) {
                R.id.menu_chapter_change_source -> showChapterChangeSource()
                R.id.menu_book_change_source -> showBookChangeSource()
            }
        }.show(anchor)
    }

    override fun showReadRefreshMenu(anchor: View) {
        NgActionPopup(
            context = this,
            items = listOf(
                NgActionPopupItem(
                    R.id.menu_refresh_dur,
                    R.string.menu_refresh_dur,
                    R.drawable.ic_refresh_black_24dp
                ),
                NgActionPopupItem(
                    R.id.menu_refresh_after,
                    R.string.menu_refresh_after,
                    R.drawable.ic_refresh_black_24dp
                ),
                NgActionPopupItem(
                    R.id.menu_refresh_all,
                    R.string.menu_refresh_all,
                    R.drawable.ic_refresh_black_24dp
                )
            ),
            themeSnapshot = binding.readMenu.currentThemeSnapshot(),
        ) { item ->
            when (item.itemId) {
                R.id.menu_refresh_dur -> refreshContentDur()
                R.id.menu_refresh_after -> refreshContentAfter()
                R.id.menu_refresh_all -> refreshContentAll()
            }
        }.show(anchor)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        upMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * 更新菜单
     */
    private fun upMenu() {
        val menu = menu ?: return
        val book = ReadBook.book ?: return
        val onLine = !book.isLocal
        for (i in 0 until menu.size) {
            val item = menu[i]
            when (item.groupId) {
                R.id.menu_group_local -> item.isVisible = !onLine
                R.id.menu_group_text -> item.isVisible = book.isLocalTxt
                R.id.menu_group_epub -> item.isVisible = book.isEpub
                else -> when (item.itemId) {
                    R.id.menu_enable_replace -> item.isChecked = book.getUseReplaceRule()
                    R.id.menu_same_title_removed -> item.isChecked = book.getRemoveSameTitle()
                    R.id.menu_re_segment -> item.isChecked = book.getReSegment()
//                    R.id.menu_enable_review -> {
//                        item.isVisible = BuildConfig.DEBUG
//                        item.isChecked = AppConfig.enableReview
//                    }

                    R.id.menu_reverse_content -> item.isVisible = onLine
                    R.id.menu_del_ruby_tag -> item.isChecked = book.getDelTag(Book.rubyTag)
                    R.id.menu_del_h_tag -> item.isChecked = book.getDelTag(Book.hTag)
                }
            }
        }
        lifecycleScope.launch {
            val show = ReadBook.inBookshelf && withContext(IO) {
                AppWebDav.isOk
            }
            menu.findItem(R.id.menu_get_progress)?.isVisible = show
            menu.findItem(R.id.menu_cover_progress)?.isVisible = show
        }
    }

    /**
     * 菜单
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_change_source,
            R.id.menu_book_change_source -> showBookChangeSource()

            R.id.menu_chapter_change_source -> showChapterChangeSource()

            R.id.menu_refresh,
            R.id.menu_refresh_dur -> refreshContentDur()

            R.id.menu_refresh_after -> refreshContentAfter()

            R.id.menu_refresh_all -> refreshContentAll()

            R.id.menu_download -> showDownloadDialog()
            R.id.menu_add_bookmark -> addBookmark()
            R.id.menu_edit_content -> showDialogFragment(ContentEditDialog())
            R.id.menu_update_toc -> ReadBook.book?.let {
                if (it.isEpub) {
                    BookCacheManager.clear(it)
                    EpubFile.clear()
                }
                if (it.isMobi) {
                    MobiFile.clear()
                }
                loadChapterList(it)
            }

            R.id.menu_enable_replace -> changeReplaceRuleState()
            R.id.menu_re_segment -> ReadBook.book?.let {
                it.setReSegment(!it.getReSegment())
                item.isChecked = it.getReSegment()
                ReadBook.loadContent(false)
            }

//            R.id.menu_enable_review -> {
//                AppConfig.enableReview = !AppConfig.enableReview
//                item.isChecked = AppConfig.enableReview
//                ReadBook.loadContent(false)
//            }

            R.id.menu_del_ruby_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.rubyTag)
                } else {
                    it.removeDelTag(Book.rubyTag)
                }
                refreshContentAll(it)
            }

            R.id.menu_del_h_tag -> ReadBook.book?.let {
                item.isChecked = !item.isChecked
                if (item.isChecked) {
                    it.addDelTag(Book.hTag)
                } else {
                    it.removeDelTag(Book.hTag)
                }
                refreshContentAll(it)
            }

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
            R.id.menu_toc_regex -> showDialogFragment(
                TxtTocRuleDialog(ReadBook.book?.tocUrl)
            )

            R.id.menu_reverse_content -> ReadBook.book?.let {
                viewModel.reverseContent(it)
            }

            R.id.menu_set_charset -> showCharsetConfig()

            R.id.menu_get_progress -> ReadBook.book?.let {
                viewModel.syncBookProgress(it) { progress ->
                    sureSyncProgress(progress)
                }
            }

            R.id.menu_cover_progress -> ReadBook.book?.let {
                ReadBook.uploadProgress(true) { toastOnUi(R.string.upload_book_success) }
            }

            R.id.menu_same_title_removed -> ReadBook.book?.let {
                it.setRemoveSameTitle(!it.getRemoveSameTitle())
                item.isChecked = it.getRemoveSameTitle()
                ReadBook.saveRead()
                ReadBook.loadContent(false)
            }

            R.id.menu_effective_replaces -> showDialogFragment<EffectiveReplacesDialog>()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    fun applyImageStyleConfig(imageStyle: String) {
        ReadBook.book?.setImageStyle(imageStyle)
        if (imageStyle == Book.imgStyleSingle) {
            ReadBook.book?.setPageAnim(0)
            binding.readView.upPageAnim()
        }
        ReadBook.saveRead()
        ReadBook.loadContent(false)
    }

    private fun showBookChangeSource() {
        binding.readMenu.runMenuOut()
        ReadBook.book?.let {
            showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
        }
    }

    override fun onHeaderChangeSource() {
        showBookChangeSource()
    }

    override fun onHeaderRefresh() {
        refreshContentDur()
    }

    override fun onHeaderDownload() {
        showDownloadDialog()
    }

    private fun showChapterChangeSource() = lifecycleScope.launch {
        val book = ReadBook.book ?: return@launch
        val chapter =
            appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                ?: return@launch
        binding.readMenu.runMenuOut()
        showDialogFragment(
            ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
        )
    }

    private fun refreshContentDur() {
        if (ReadBook.bookSource == null) {
            upContent()
        } else {
            ReadBook.book?.let {
                ReadBook.curTextChapter = null
                binding.readView.upContent()
                viewModel.refreshContentDur(it)
            }
        }
    }

    private fun refreshContentAfter() {
        if (ReadBook.bookSource == null) {
            upContent()
        } else {
            ReadBook.book?.let {
                ReadBook.clearTextChapter()
                binding.readView.upContent()
                viewModel.refreshContentAfter(it)
            }
        }
    }

    private fun refreshContentAll() {
        if (ReadBook.bookSource == null) {
            upContent()
        } else {
            ReadBook.book?.let {
                refreshContentAll(it)
            }
        }
    }

    private fun refreshContentAll(book: Book) {
        ReadBook.clearTextChapter()
        binding.readView.upContent()
        viewModel.refreshContentAll(book)
    }

    /**
     * 按键拦截,显示菜单
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val isDown = action == 0

        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.runMenuIn()
                return true
            }
            if (!isDown && !binding.readMenu.canShowMenu) {
                binding.readMenu.canShowMenu = true
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * 鼠标滚轮事件
     */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (0 != (event.source and InputDevice.SOURCE_CLASS_POINTER)) {
            if (event.action == MotionEvent.ACTION_SCROLL) {
                val axisValue = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                LogUtils.d("onGenericMotionEvent", "axisValue = $axisValue")
                // 获得垂直坐标上的滚动方向
                if (axisValue < 0.0f) { // 滚轮向下滚
                    mouseWheelPage(PageDirection.NEXT, axisValue)
                } else { // 滚轮向上滚
                    mouseWheelPage(PageDirection.PREV, axisValue)
                }
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }

    /**
     * 按键事件
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (menuLayoutIsVisible) {
            return super.onKeyDown(keyCode, event)
        }
        val longPress = event.repeatCount > 0
        when {
            isPrevKey(keyCode) -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            isNextKey(keyCode) -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> if (volumeKeyPage(PageDirection.PREV, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> if (volumeKeyPage(PageDirection.NEXT, longPress)) {
                return true
            }

            KeyEvent.KEYCODE_PAGE_UP -> {
                handleKeyPage(PageDirection.PREV, longPress)
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }

            KeyEvent.KEYCODE_SPACE -> {
                handleKeyPage(PageDirection.NEXT, longPress)
                return true
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    /**
     * 松开按键事件
     */
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (volumeKeyPage(PageDirection.NONE, false)) {
                    return true
                }
            }

        }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * view触摸,文字选择
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean = binding.run {
        if (!binding.readView.isTextSelected) {
            return false
        }
        when (event.action) {
            MotionEvent.ACTION_DOWN -> textActionMenu.dismiss()
            MotionEvent.ACTION_MOVE -> {
                when (v.id) {
                    R.id.cursor_left -> if (!readView.curPage.getReverseStartCursor()) {
                        readView.curPage.selectStartMove(
                            event.rawX + cursorLeft.width,
                            event.rawY - cursorLeft.height
                        )
                    } else {
                        readView.curPage.selectEndMove(
                            event.rawX - cursorRight.width,
                            event.rawY - cursorRight.height
                        )
                    }

                    R.id.cursor_right -> if (readView.curPage.getReverseEndCursor()) {
                        readView.curPage.selectStartMove(
                            event.rawX + cursorLeft.width,
                            event.rawY - cursorLeft.height
                        )
                    } else {
                        readView.curPage.selectEndMove(
                            event.rawX - cursorRight.width,
                            event.rawY - cursorRight.height
                        )
                    }
                }
            }

            MotionEvent.ACTION_UP -> {
                readView.curPage.resetReverseCursor()
                showTextActionMenu()
            }
        }
        return true
    }

    /**
     * 更新文字选择开始位置
     */
    override fun upSelectedStart(x: Float, y: Float, top: Float) = binding.run {
        cursorLeft.x = x - cursorLeft.width
        cursorLeft.y = y
        cursorLeft.visible(true)
        textMenuPosition.x = x
        textMenuPosition.y = top
    }

    /**
     * 更新文字选择结束位置
     */
    override fun upSelectedEnd(x: Float, y: Float) = binding.run {
        cursorRight.x = x
        cursorRight.y = y
        cursorRight.visible(true)
    }

    /**
     * 取消文字选择
     */
    override fun onCancelSelect() = binding.run {
        cursorLeft.invisible()
        cursorRight.invisible()
        textActionMenu.dismiss()
    }

    override fun onLongScreenshotTouchEvent(event: MotionEvent): Boolean {
        return binding.readView.onTouchEvent(event)
    }

    /**
     * 显示文本操作菜单
     */
    override fun showTextActionMenu() {
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        textActionMenu.show(
            binding.textMenuPosition,
            binding.root.height + navigationBarHeight,
            binding.textMenuPosition.y.toInt(),
            binding.cursorLeft.y.toInt() + binding.cursorLeft.height,
            binding.cursorRight.y.toInt() + binding.cursorRight.height
        )
    }

    /**
     * 当前选择的文本
     */
    override val selectedText: String
        get() = activeTextHighlight?.bookText ?: binding.readView.getSelectText()

    /**
     * 文本选择菜单操作
     */
    override fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> when {
                activeTextHighlight != null -> speak(selectedText)
                AppConfig.contentSelectSpeakMod == 1 -> lifecycleScope.launch {
                    binding.readView.aloudStartSelect()
                }
                else -> speak(selectedText)
            }

            R.id.menu_bookmark -> binding.readView.curPage.let {
                val bookmark = it.createBookmark()
                if (bookmark == null) {
                    toastOnUi(R.string.create_bookmark_error)
                } else {
                    showDialogFragment(BookmarkDialog(bookmark))
                }
                return true
            }

            R.id.menu_replace -> {
                val scopes = arrayListOf<String>()
                ReadBook.book?.name?.let {
                    scopes.add(it)
                }
                ReadBook.bookSource?.bookSourceUrl?.let {
                    scopes.add(it)
                }
                val text = selectedText.lineSequence().map { it.trim() }.joinToString("\n")
                showDialogFragment(
                    ReplaceRuleEditDialog.newRule(
                        pattern = text,
                        scope = scopes.joinToString(";"),
                    )
                )
                return true
            }

            R.id.menu_ai_purify -> {
                startAiPurifySelectedText()
                return true
            }

            R.id.menu_search_content -> {
                viewModel.searchContentQuery = selectedText
                openSearchDrawer(selectedText)
                return true
            }

            R.id.menu_dict -> {
                showDialogFragment(DictDialog(selectedText))
                return true
            }
        }
        return false
    }

    override fun onTextHighlightCreate(): Bookmark? {
        val textHighlight = binding.readView.curPage.createTextHighlight()
        if (textHighlight == null) {
            toastOnUi(R.string.create_bookmark_error)
            return null
        }
        activeTextHighlight = textHighlight
        lifecycleScope.launch(IO) {
            textHighlightWriteMutex.withLock {
                appDb.bookmarkDao.insert(textHighlight)
            }
        }
        return textHighlight
    }

    override fun onTextHighlightOpened(bookmark: Bookmark) {
        activeTextHighlight = bookmark
    }

    override fun onTextHighlightUpdate(bookmark: Bookmark) {
        activeTextHighlight = bookmark
        lifecycleScope.launch(IO) {
            textHighlightWriteMutex.withLock {
                appDb.bookmarkDao.update(bookmark)
            }
        }
    }

    override fun onTextHighlightDelete(bookmark: Bookmark) {
        lifecycleScope.launch(IO) {
            textHighlightWriteMutex.withLock {
                appDb.bookmarkDao.delete(bookmark)
            }
        }
    }

    override fun onTextHighlightMenuDismissed() {
        activeTextHighlight = null
    }

    private fun startAiPurifySelectedText(text: String = selectedText) {
        val source = AiPurifyHelper.normalizeSelectedText(text)
        if (source.isBlank()) {
            toastOnUi("选中文本为空")
            return
        }
        aiPurifyJob?.cancel()
        val waitDialog = showAiPurifyProgressDialog(getString(R.string.ai_purify))
        aiPurifyJob = lifecycleScope.launch {
            try {
                val startedAt = SystemClock.elapsedRealtime()
                val result = withContext(IO) {
                    AiPurifyHelper.purify(source)
                }
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                waitDialog.dismissSafely()
                if (shouldAutoApplyAiPurifyResult(result)) {
                    applyAiPurifyResult(result)
                } else {
                    showAiPurifyConfirmDialog(result, elapsedMs)
                }
            } catch (e: Throwable) {
                waitDialog.dismissSafely()
                showAiPurifyErrorIfNeeded(R.string.ai_purify, e)
            }
        }
    }

    private fun showAiPurifyConfirmDialog(result: AiPurifyResult, elapsedMs: Long) {
        showReadComposeDialog(this) { dismiss ->
            AiPurifyPreviewDialogContent(
                title = getString(R.string.ai_purify_confirm),
                preview = AiPurifyPreviewUi(
                    deletedCount = result.deletedCount.toString(),
                    ruleCount = "1",
                    elapsed = formatAiPurifyElapsed(elapsedMs),
                    model = formatAiPurifyModel(result.model),
                    original = result.original,
                    cleaned = result.cleaned,
                    deleted = formatAiPurifyInlineChangeSummary(result),
                ),
                originalLabel = getString(R.string.original_text),
                cleanedLabel = getString(R.string.ai_purify_cleaned_text),
                deletedLabel = getString(R.string.ai_purify_deleted_content),
                deletedCountLabel = getString(R.string.ai_purify_chapter_deleted_count),
                ruleCountLabel = getString(R.string.ai_purify_chapter_rule_count),
                elapsedLabel = getString(R.string.ai_purify_api_elapsed),
                modelLabel = getString(R.string.ai_purify_model),
                retryLabel = getString(R.string.ai_purify_retry),
                cancelLabel = getString(R.string.cancel),
                applyLabel = getString(R.string.ai_purify_apply),
                onRetry = {
                    dismiss()
                    startAiPurifySelectedText(result.original)
                },
                onCancel = dismiss,
                onApply = {
                    dismiss()
                    applyAiPurifyResult(result)
                },
            )
        }
    }

    private fun shouldAutoApplyAiPurifyResult(result: AiPurifyResult): Boolean {
        if (!AiConfig.purifyAutoApply || result.original == result.cleaned) {
            return false
        }
        return !AiConfig.purifyExceptionIntercept || result.canAutoApply
    }

    private fun shouldAutoApplyAiPurifyChapterCandidates(
        sampleCount: Int,
        candidates: List<AiPurifyRuleCandidate>
    ): Boolean {
        if (sampleCount != 1 || !AiConfig.purifyChapterAutoApply || candidates.isEmpty()) {
            return false
        }
        return !AiConfig.purifyChapterExceptionIntercept
    }

    private fun canShowDialogSafely(): Boolean {
        return !isFinishing && !isDestroyed
    }

    private fun Dialog.dismissSafely() {
        runCatching {
            if (isShowing) {
                dismiss()
            }
        }
    }

    private fun showAiPurifyProgressDialog(title: String): Dialog {
        lateinit var progressDialog: Dialog
        progressDialog = showReadComposeDialog(
            context = this,
            cancelOnTouchOutside = false,
        ) {
            AiPurifyProgressDialogContent(
                title = title,
                cancelLabel = getString(R.string.cancel),
                onCancel = progressDialog::cancel,
            )
        }
        progressDialog.setOnCancelListener {
            aiPurifyJob?.cancel()
        }
        return progressDialog
    }

    private fun showAiPurifyErrorIfNeeded(titleResource: Int, error: Throwable) {
        if (error is CancellationException) {
            return
        }
        if (!canShowDialogSafely()) {
            return
        }
        runCatching {
            showReadConfirmDialog(
                context = this,
                title = getString(titleResource),
                message = error.localizedMessage ?: error.toString(),
                confirmLabel = getString(R.string.ok),
                onConfirm = {},
            )
        }
    }

    private data class AiPurifyChapterSample(
        val index: Int,
        val title: String,
        val paragraphs: List<String>
    )

    private data class AiPurifyChapterFailure(
        val sample: AiPurifyChapterSample,
        val error: Throwable
    )

    private data class AiPurifyRuleCandidate(
        val pattern: String,
        val replacement: String,
        val evidenceLabels: MutableList<String>,
        val type: String = "typo"
    )

    private data class AiPurifyRuleSource(
        val chapterIndex: Int,
        val paragraphIndex: Int,
        val text: String
    )

    private data class AiPurifyRulePart(
        val pattern: String,
        val replacement: String,
        val generalPattern: String? = null,
        val generalReplacement: String? = null,
        val hasRawDeletion: Boolean = false,
        val hasRawReplacement: Boolean = false,
        val deletedPattern: String = ""
    )

    private data class AiPurifyRuleDiff(
        val parts: List<AiPurifyRulePart>,
        val hasInsertion: Boolean
    )

    private data class AiPurifyDiffOp(
        val kind: Int,
        val original: String,
        val cleaned: String
    )

    private fun applyAiPurifyResult(result: AiPurifyResult) {
        if (result.original == result.cleaned) {
            toastOnUi("AI 未修改任何内容")
            return
        }
        applyAiPurifyResults(listOf(result))
    }

    private fun applyAiPurifyResults(results: List<AiPurifyResult>) {
        val changedResults = results
            .filter { it.original != it.cleaned }
        if (changedResults.isEmpty()) {
            toastOnUi("AI 未修改任何内容")
            return
        }
        lifecycleScope.launch {
            try {
                withContext(IO) {
                    val scope = currentReplaceScope()
                    val maxOrder = appDb.replaceRuleDao.maxOrder
                    val baseId = System.currentTimeMillis()
                    val rules = changedResults.mapIndexed { index, result ->
                        ReplaceRule(
                            id = baseId + index,
                            name = result.original.ruleNamePreview(),
                            group = "AI净化",
                            pattern = result.original,
                            replacement = result.cleaned,
                            scope = scope,
                            scopeTitle = false,
                            scopeContent = true,
                            isEnabled = true,
                            isRegex = false,
                            timeoutMillisecond = 3000L,
                            order = maxOrder + index + 1
                        )
                    }
                    appDb.replaceRuleDao.insert(*rules.toTypedArray())
                    ContentProcessor.upReplaceRules()
                }
                ReadBook.book?.let { book ->
                    if (!book.getUseReplaceRule()) {
                        book.setUseReplaceRule(true)
                        ReadBook.saveRead()
                        menu?.findItem(R.id.menu_enable_replace)?.isChecked = true
                    }
                }
                viewModel.replaceRuleChanged()
                toastOnUi(
                    if (changedResults.size == 1) {
                        getString(R.string.ai_purify_saved)
                    } else {
                        "已添加 ${changedResults.size} 条AI净化规则"
                    }
                )
            } catch (e: Throwable) {
                showAiPurifyErrorIfNeeded(R.string.ai_purify, e)
            }
        }
    }

    private fun applyAiPurifyRuleCandidates(candidates: List<AiPurifyRuleCandidate>) {
        val selectedCandidates = candidates
            .filter { it.pattern.isNotBlank() && !it.pattern.isNormalizedSameAs(it.replacement) }
        if (selectedCandidates.isEmpty()) {
            toastOnUi("未选择有效净化规则")
            return
        }
        lifecycleScope.launch {
            try {
                withContext(IO) {
                    val scope = currentReplaceScope()
                    val maxOrder = appDb.replaceRuleDao.maxOrder
                    val baseId = System.currentTimeMillis()
                    val rules = selectedCandidates.mapIndexed { index, candidate ->
                        ReplaceRule(
                            id = baseId + index,
                            name = candidate.pattern.ruleNamePreview(),
                            group = "AI净化",
                            pattern = candidate.pattern,
                            replacement = candidate.replacement,
                            scope = scope,
                            scopeTitle = false,
                            scopeContent = true,
                            isEnabled = true,
                            isRegex = false,
                            timeoutMillisecond = 3000L,
                            order = maxOrder + index + 1
                        )
                    }
                    appDb.replaceRuleDao.insert(*rules.toTypedArray())
                    ContentProcessor.upReplaceRules()
                }
                ReadBook.book?.let { book ->
                    if (!book.getUseReplaceRule()) {
                        book.setUseReplaceRule(true)
                        ReadBook.saveRead()
                        menu?.findItem(R.id.menu_enable_replace)?.isChecked = true
                    }
                }
                viewModel.replaceRuleChanged()
                toastOnUi(
                    if (selectedCandidates.size == 1) {
                        getString(R.string.ai_purify_saved)
                    } else {
                        "已添加 ${selectedCandidates.size} 条AI净化规则"
                    }
                )
            } catch (e: Throwable) {
                showAiPurifyErrorIfNeeded(R.string.ai_purify, e)
            }
        }
    }

    override fun onClickAiPurifyChapter() {
        showAiPurifyChapterRangeDialog()
    }

    override fun onOpenAiPurifySettings() {
        startActivity(Intent(this, ConfigActivity::class.java).apply {
            putExtra("configTag", ConfigTag.AI_CONFIG)
            putExtra(AiConfigFragment.EXTRA_INITIAL_PAGE, AiConfigFragment.PAGE_PURIFY)
        })
    }

    private fun showAiPurifyChapterRangeDialog() {
        if (ReadBook.chapterSize <= 0) {
            toastOnUi("当前书籍没有可采样章节")
            return
        }
        val total = ReadBook.chapterSize
        val limit = AiConfig.purifyChapterSampleLimit
        val currentChapter = (ReadBook.durChapterIndex + 1).coerceIn(1, total)
        val defaultEnd = (currentChapter + limit - 1).coerceAtMost(total)
        val customSelected = androidx.compose.runtime.mutableStateOf(false)
        val startText = androidx.compose.runtime.mutableStateOf(currentChapter.toString())
        val endText = androidx.compose.runtime.mutableStateOf(defaultEnd.toString())
        showReadComposeDialog(this, marginDp = 28) { dismiss ->
            AiPurifyRangeDialogContent(
                title = getString(R.string.ai_purify_chapter_sample_range),
                currentChapterLabel = getString(R.string.ai_purify_sample_current_chapter),
                customRangeLabel = getString(R.string.ai_purify_sample_custom_range),
                customSelected = customSelected.value,
                currentChapterHint = getString(R.string.ai_purify_sample_current_hint),
                hint = getString(R.string.ai_purify_sample_range_hint, total, limit),
                startLabel = getString(R.string.ai_purify_sample_range_start),
                endLabel = getString(R.string.ai_purify_sample_range_end),
                start = startText.value,
                end = endText.value,
                cancelLabel = getString(R.string.cancel),
                confirmLabel = getString(R.string.ai_purify_start),
                onModeSelected = { customSelected.value = it },
                onStartChanged = { startText.value = it },
                onEndChanged = { endText.value = it },
                onCancel = dismiss,
                onConfirm = {
                    if (!customSelected.value) {
                        dismiss()
                        startAiPurifyChapterRange(
                            ReadBook.durChapterIndex,
                            ReadBook.durChapterIndex,
                        )
                    } else {
                        val start = startText.value.toIntOrNull()
                        val end = endText.value.toIntOrNull()
                        when {
                            start == null || end == null || start > end ->
                                toastOnUi(getString(R.string.ai_purify_sample_range_invalid))
                            start < 1 || end > total ->
                                toastOnUi(
                                    getString(R.string.ai_purify_sample_range_out_of_bounds, total)
                                )
                            end - start + 1 > limit ->
                                toastOnUi(getString(R.string.ai_purify_sample_range_exceeded, limit))
                            else -> {
                                dismiss()
                                startAiPurifyChapterRange(start - 1, end - 1)
                            }
                        }
                    }
                },
            )
        }
    }

    private fun startAiPurifyChapter() {
        startAiPurifyChapterRange(ReadBook.durChapterIndex, ReadBook.durChapterIndex)
    }

    private fun startAiPurifyChapters(sampleCount: Int) {
        val startIndex = ReadBook.durChapterIndex
        val safeCount = sampleCount.coerceIn(1, AiConfig.purifyChapterSampleLimit)
        val endIndex = (startIndex + safeCount - 1).coerceAtMost(ReadBook.chapterSize - 1)
        startAiPurifyChapterRange(startIndex, endIndex)
    }

    private fun startAiPurifyChapterRange(startIndex: Int, endIndex: Int) {
        val sampleCount = endIndex - startIndex + 1
        if (sampleCount <= 0 || startIndex < 0 || endIndex >= ReadBook.chapterSize) {
            toastOnUi(getString(R.string.ai_purify_sample_range_invalid))
            return
        }
        val sampleLimit = AiConfig.purifyChapterSampleLimit
        if (sampleCount > sampleLimit) {
            toastOnUi(getString(R.string.ai_purify_sample_range_exceeded, sampleLimit))
            return
        }
        aiPurifyJob?.cancel()
        val waitDialog = showAiPurifyProgressDialog(
            if (sampleCount == 1) {
                getString(R.string.ai_purify_chapter)
            } else {
                getString(R.string.ai_purify_chapter_sampling, sampleCount)
            }
        )
        aiPurifyJob = lifecycleScope.launch {
            try {
                val startedAt = SystemClock.elapsedRealtime()
                val samples = withContext(IO) {
                    loadAiPurifyChapterSamples(startIndex, endIndex)
                }
                val ruleAttempts = withContext(IO) {
                    val semaphore = Semaphore(AiConfig.purifyChapterConcurrencyLimit)
                    samples.map { sample ->
                        async {
                            semaphore.withPermit {
                                runCatching {
                                    sample to AiPurifyHelper.generateRuleCandidates(
                                        sample.paragraphs,
                                        sample.title
                                    )
                                }
                            }
                        }
                    }.awaitAll()
                }
                val ruleResults = ruleAttempts.mapNotNull { it.getOrNull() }
                val failures = ruleAttempts.mapIndexedNotNull { index, result ->
                    result.exceptionOrNull()?.let { error ->
                        AiPurifyChapterFailure(samples[index], error)
                    }
                }
                if (ruleResults.isEmpty() && failures.isNotEmpty()) {
                    throw NoStackTraceException(formatAiPurifyChapterFailures(failures))
                }
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                val candidates = buildAiPurifyRuleCandidatesFromGenerated(ruleResults)
                waitDialog.dismissSafely()
                if (failures.isNotEmpty()) {
                    toastOnUi("已跳过 ${failures.size} 章失败结果，可重试")
                }
                when {
                    candidates.isEmpty() -> toastOnUi("AI 未生成可应用的净化规则")
                    shouldAutoApplyAiPurifyChapterCandidates(sampleCount, candidates) ->
                        applyAiPurifyRuleCandidates(candidates)
                    else -> showAiPurifyChapterConfirmDialog(
                        candidates = candidates,
                        originalCharCount = ruleResults.sumOf { it.second.originalCharCount },
                        cleanedCharCount = estimateAiPurifyCleanedCount(samples, candidates),
                        model = formatAiPurifyModelNames(ruleResults.mapNotNull { it.second.model }),
                        elapsedMs = elapsedMs,
                        sampleStartIndex = startIndex,
                        sampleEndIndex = endIndex
                    )
                }
            } catch (e: Throwable) {
                waitDialog.dismissSafely()
                showAiPurifyErrorIfNeeded(R.string.ai_purify_chapter, e)
            }
        }
    }

    private fun formatAiPurifyChapterFailures(failures: List<AiPurifyChapterFailure>): String {
        return buildString {
            append("AI 净化失败 ")
            append(failures.size)
            append(" 章")
            failures.take(5).forEach { failure ->
                append('\n')
                append("第")
                append(failure.sample.index + 1)
                append("章")
                failure.sample.title.takeIf { it.isNotBlank() }?.let {
                    append("《")
                    append(it)
                    append("》")
                }
                append("：")
                append(failure.error.localizedMessage ?: failure.error.toString())
            }
            if (failures.size > 5) {
                append("\n...")
            }
        }
    }

    private fun loadAiPurifyChapterSamples(startIndex: Int, endIndex: Int): List<AiPurifyChapterSample> {
        val book = ReadBook.book ?: throw NoStackTraceException("当前书籍为空")
        val processor = ContentProcessor.get(book)
        return (startIndex..endIndex).map { index ->
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, index)
                ?: throw NoStackTraceException("找不到第${index + 1}章")
            val rawContent = BookHelp.getContent(book, chapter)
                ?: throw NoStackTraceException("第${index + 1}章正文未缓存，请先打开或缓存该章节后再净化")
            val bookContent = processor.getContent(book, chapter, rawContent)
            val paragraphs = bookContent.textList
                .map { AiPurifyHelper.normalizeSelectedText(it) }
                .filter { it.isNotBlank() }
            if (paragraphs.isEmpty()) {
                throw NoStackTraceException("第${index + 1}章正文为空")
            }
            AiPurifyChapterSample(
                index = index,
                title = chapter.title,
                paragraphs = paragraphs
            )
        }
    }

    private fun shouldAutoApplyAiPurifyChapterResults(results: List<AiPurifyResult>): Boolean {
        if (!AiConfig.purifyChapterAutoApply || results.isEmpty()) {
            return false
        }
        return !AiConfig.purifyChapterExceptionIntercept || results.all { it.canAutoApply }
    }

    private fun showAiPurifyChapterConfirmDialog(
        candidates: List<AiPurifyRuleCandidate>,
        originalCharCount: Int,
        cleanedCharCount: Int,
        model: String,
        elapsedMs: Long,
        sampleStartIndex: Int,
        sampleEndIndex: Int
    ) {
        if (candidates.isEmpty()) {
            toastOnUi("AI 未生成可应用的净化规则")
            return
        }
        val ruleUi = candidates.map { it.toAiPurifyRuleUi() }
        showReadComposeDialog(this) { dismiss ->
            AiPurifyChapterConfirmDialogContent(
                title = getString(R.string.ai_purify_chapter_confirm),
                summary = AiPurifyChapterSummaryUi(
                    originalCount = originalCharCount.toString(),
                    cleanedCount = cleanedCharCount.toString(),
                    elapsed = formatAiPurifyElapsed(elapsedMs),
                    model = model,
                ),
                rules = ruleUi,
                originalCountLabel = getString(R.string.ai_purify_chapter_original_count),
                cleanedCountLabel = getString(R.string.ai_purify_chapter_cleaned_count),
                ruleCountLabel = getString(R.string.ai_purify_chapter_rule_count),
                elapsedLabel = getString(R.string.ai_purify_api_elapsed),
                modelLabel = getString(R.string.ai_purify_model),
                hitCountColumnLabel = getString(R.string.ai_purify_rule_column_hit_count),
                typeColumnLabel = getString(R.string.ai_purify_rule_column_type),
                contentColumnLabel = getString(R.string.ai_purify_rule_column_content),
                selectAllLabel = getString(R.string.select_all),
                clearSelectionLabel = getString(R.string.revert_selection),
                retryLabel = getString(R.string.ai_purify_retry),
                cancelLabel = getString(R.string.cancel),
                applyLabel = getString(R.string.ai_purify_apply),
                onRuleClick = { index ->
                    candidates.getOrNull(index)?.let(::showAiPurifyRuleDetailDialog)
                },
                onRetry = {
                    dismiss()
                    startAiPurifyChapterRange(sampleStartIndex, sampleEndIndex)
                },
                onCancel = dismiss,
                onApply = { selectedIndexes ->
                    val selectedCandidates = candidates.filterIndexed { index, _ ->
                        index in selectedIndexes
                    }
                    if (selectedCandidates.isEmpty()) {
                        toastOnUi("未选择净化规则")
                    } else {
                        dismiss()
                        applyAiPurifyRuleCandidates(selectedCandidates)
                    }
                },
            )
        }
    }

    private fun formatAiPurifyModels(results: List<AiPurifyResult>): String {
        val models = results
            .mapNotNull { it.model?.takeIf { model -> model.isNotBlank() } }
            .distinct()
        return formatAiPurifyModelNames(models)
    }

    private fun formatAiPurifyModelNames(models: List<String>): String {
        val distinctModels = models
            .filter { it.isNotBlank() }
            .distinct()
        if (distinctModels.isEmpty()) {
            return formatAiPurifyModel(null)
        }
        return if (distinctModels.size == 1) {
            distinctModels.first()
        } else {
            "${distinctModels.first()} +${distinctModels.size - 1}"
        }
    }

    private fun formatAiPurifyModel(model: String?): String {
        return model?.takeIf { it.isNotBlank() }
            ?: getString(R.string.ai_purify_model_unknown)
    }

    private fun formatAiPurifyElapsed(elapsedMs: Long): String {
        return getString(R.string.ai_purify_elapsed_seconds, elapsedMs / 1000f)
    }

    private fun showAiPurifyRuleDetailDialog(candidate: AiPurifyRuleCandidate) {
        showReadComposeDialog(this, marginDp = 28) { dismiss ->
            AiPurifyRuleDetailDialogContent(
                title = getString(R.string.ai_purify_rule_detail),
                originalLabel = getString(R.string.original_text),
                cleanedLabel = getString(R.string.ai_purify_cleaned_text),
                deletedLabel = getString(R.string.ai_purify_deleted_content),
                rule = candidate.toAiPurifyRuleUi(),
                closeLabel = getString(R.string.close),
                onClose = dismiss,
            )
        }
    }

    private fun AiPurifyRuleCandidate.toAiPurifyRuleUi() = AiPurifyRuleUi(
        hitCount = getString(R.string.ai_purify_rule_hit_count_value, evidenceLabels.size),
        type = aiPurifyRuleTypeLabel(),
        summary = aiPurifyRuleContentSummary(),
        original = pattern,
        cleaned = replacement.ifBlank {
            getString(R.string.ai_purify_rule_deleted_result)
        },
        deleted = aiPurifyRuleBriefContentSummary(),
    )

    private fun AiPurifyRuleCandidate.aiPurifyRuleTypeLabel(): String {
        return when (type) {
            "typo" -> getString(R.string.ai_purify_rule_type_typo)
            "noise" -> getString(R.string.ai_purify_rule_type_noise)
            "ad" -> getString(R.string.ai_purify_rule_type_ad)
            else -> when {
                replacement.isEmpty() -> getString(R.string.ai_purify_rule_type_delete)
                pattern.isNotEmpty() -> getString(R.string.ai_purify_rule_type_replace)
                else -> getString(R.string.ai_purify_rule_type_change)
            }
        }
    }

    private fun AiPurifyRuleCandidate.aiPurifyRuleContentSummary(): String {
        return if (replacement.isEmpty()) {
            getString(R.string.ai_purify_deleted_change, pattern)
        } else {
            getString(R.string.ai_purify_replaced_change, "$pattern -> $replacement")
        }
    }

    private fun AiPurifyRuleCandidate.aiPurifyRuleBriefContentSummary(): String {
        if (replacement.isEmpty()) {
            return getString(R.string.ai_purify_deleted_change, pattern.compactAiPurifyRulePreview())
        }
        val diff = AiPurifyResult(
            original = pattern,
            cleaned = replacement,
            deletedCount = 0,
            replacementCount = 0,
            deletedPreview = "",
            replacementPreview = "",
            canAutoApply = true,
            riskReason = null,
            model = null
        ).toAiPurifyRuleDiff()
        val changes = arrayListOf<String>()
        val deletedPreview = diff.parts
            .filter { it.replacement.isEmpty() }
            .joinToString("") { it.pattern }
            .compactAiPurifyRulePreview()
        if (deletedPreview.isNotBlank()) {
            changes.add(getString(R.string.ai_purify_deleted_change, deletedPreview))
        }
        val replacementPreview = diff.parts
            .filter { it.replacement.isNotEmpty() }
            .map {
                val oldValue = (it.generalPattern ?: it.pattern).compactAiPurifyRulePreview(18)
                val newValue = (it.generalReplacement ?: it.replacement).compactAiPurifyRulePreview(18)
                "$oldValue -> $newValue"
            }
            .filterNot { it.isSameSideReplacementPreview() }
            .joinToString("、")
            .compactAiPurifyRulePreview()
        if (replacementPreview.isNotBlank()) {
            changes.add(getString(R.string.ai_purify_replaced_change, replacementPreview))
        }
        return changes.joinToString("\n").ifBlank {
            getString(
                R.string.ai_purify_replaced_change,
                "${pattern.compactAiPurifyRulePreview(28)} -> ${replacement.compactAiPurifyRulePreview(28)}"
            )
        }
    }

    private fun String.compactAiPurifyRulePreview(maxLength: Int = 56): String {
        val compact = replace("\n", "\\n").trim()
        return if (compact.length <= maxLength) {
            compact
        } else {
            compact.take(maxLength) + "..."
        }
    }

    private fun buildAiPurifyRuleCandidatesFromGenerated(
        sampleResults: List<Pair<AiPurifyChapterSample, AiPurifyRuleGenerateResult>>
    ): List<AiPurifyRuleCandidate> {
        val sources = sampleResults.flatMap { (sample, _) -> sample.aiPurifyRuleSources() }
        val directRules = sampleResults
            .flatMap { it.second.rules }
            .filter { AiConfig.isPurifyChapterRuleTypeEnabled(it.type) }
        val candidates = linkedMapOf<String, AiPurifyRuleCandidate>()
        val derivedRules = directRules
            .flatMap { it.derivedAiPurifyTypoRules() }
            .distinct()
            .filter { (pattern, replacement) ->
                sources.aiPurifyEvidenceLabels(pattern).distinct().size > 1 &&
                        !pattern.isNormalizedSameAs(replacement)
            }
        derivedRules.forEach { (pattern, replacement) ->
            candidates.addAiPurifyRuleCandidate(
                type = "typo",
                pattern = pattern,
                replacement = replacement,
                sources = sources
            )
        }
        directRules.forEach { rule ->
            if (rule.old.isBlank() || rule.old.isNormalizedSameAs(rule.new)) {
                return@forEach
            }
            if (
                rule.type == "typo" &&
                derivedRules.any { (pattern, replacement) ->
                    rule.old.replace(pattern, replacement) == rule.new
                }
            ) {
                return@forEach
            }
            candidates.addAiPurifyRuleCandidate(
                type = rule.type,
                pattern = rule.old,
                replacement = rule.new,
                sources = sources
            )
        }
        return candidates.values.toList()
    }

    private fun MutableMap<String, AiPurifyRuleCandidate>.addAiPurifyRuleCandidate(
        type: String,
        pattern: String,
        replacement: String,
        sources: List<AiPurifyRuleSource>
    ) {
        if (pattern.isBlank() || pattern.isNormalizedSameAs(replacement)) {
            return
        }
        val evidenceLabels = sources.aiPurifyEvidenceLabels(pattern)
        if (evidenceLabels.isEmpty()) {
            return
        }
        val key = pattern + "\u0000" + replacement
        val candidate = getOrPut(key) {
            AiPurifyRuleCandidate(
                pattern = pattern,
                replacement = replacement,
                evidenceLabels = arrayListOf(),
                type = type
            )
        }
        candidate.evidenceLabels.addAll(evidenceLabels)
    }

    private fun AiPurifyGeneratedRule.derivedAiPurifyTypoRules(): List<Pair<String, String>> {
        if (type != "typo" || old.length != new.length || old.length < 2) {
            return emptyList()
        }
        val rules = arrayListOf<Pair<String, String>>()
        old.indices.forEach { index ->
            val oldChar = old[index]
            val newChar = new[index]
            if (!oldChar.isSafeAiPurifyVariantReplacement(newChar)) {
                return@forEach
            }
            val starts = listOf(index - 1, index)
            starts.forEach { start ->
                if (start < 0 || start + 2 > old.length) {
                    return@forEach
                }
                val pattern = old.substring(start, start + 2)
                val replacement = new.substring(start, start + 2)
                if (
                    pattern != replacement &&
                    pattern.any { it == oldChar } &&
                    pattern.all { it.isCjkIdeographForAiPurify() }
                ) {
                    rules.add(pattern to replacement)
                }
            }
        }
        return rules.distinct()
    }

    private fun Char.isSafeAiPurifyVariantReplacement(replacement: Char): Boolean {
        return when (this) {
            '幺', '麽' -> replacement == '么'
            '擡' -> replacement == '抬'
            else -> false
        }
    }

    private fun AiPurifyChapterSample.aiPurifyRuleSources(): List<AiPurifyRuleSource> {
        return paragraphs.mapIndexedNotNull { index, text ->
            val source = AiPurifyHelper.normalizeSelectedText(text)
            if (source.isBlank() || index == 0 && source == title) {
                null
            } else {
                AiPurifyRuleSource(
                    chapterIndex = this.index,
                    paragraphIndex = index + 1,
                    text = source
                )
            }
        }
    }

    private fun List<AiPurifyRuleSource>.aiPurifyEvidenceLabels(pattern: String): List<String> {
        return flatMap { source ->
            val count = source.text.countAiPurifyLiteralHits(pattern)
            List(count) {
                getString(
                    R.string.ai_purify_rule_chapter_paragraph_value,
                    source.chapterIndex + 1,
                    source.paragraphIndex
                )
            }
        }
    }

    private fun String.countAiPurifyLiteralHits(pattern: String): Int {
        if (pattern.isEmpty()) {
            return 0
        }
        var count = 0
        var start = indexOf(pattern)
        while (start >= 0) {
            count++
            start = indexOf(pattern, start + pattern.length)
        }
        return count
    }

    private fun estimateAiPurifyCleanedCount(
        samples: List<AiPurifyChapterSample>,
        candidates: List<AiPurifyRuleCandidate>
    ): Int {
        return samples
            .flatMap { it.aiPurifyRuleSources() }
            .sumOf { source ->
                candidates.fold(source.text) { text, candidate ->
                    text.replace(candidate.pattern, candidate.replacement)
                }.length
            }
    }

    private fun buildAiPurifyRuleCandidates(
        results: List<AiPurifyResult>
    ): List<AiPurifyRuleCandidate> {
        val candidateSources = arrayListOf<Pair<AiPurifyResult, List<AiPurifyRulePart>>>()
        val generalCandidates = linkedMapOf<String, AiPurifyRuleCandidate>()
        results.forEach { result ->
            val diff = result.toAiPurifyRuleDiff()
            if (diff.hasInsertion) {
                return@forEach
            }
            val parts = diff.parts
                .takeIf { it.isNotEmpty() }
                ?: listOf(AiPurifyRulePart(result.original, result.cleaned))
            if (!result.canBuildAiPurifyRuleCandidates(parts)) {
                return@forEach
            }
            candidateSources.add(result to parts)
            parts.forEach { part ->
                val generalPattern = part.generalPattern ?: return@forEach
                val generalReplacement = part.generalReplacement ?: return@forEach
                if (
                    generalPattern.isBlank() ||
                    generalPattern.isNormalizedSameAs(generalReplacement)
                ) {
                    return@forEach
                }
                val key = generalPattern + "\u0000" + generalReplacement
                val candidate = generalCandidates.getOrPut(key) {
                    AiPurifyRuleCandidate(
                        pattern = generalPattern,
                        replacement = generalReplacement,
                        evidenceLabels = arrayListOf()
                    )
                }
                val label = result.aiPurifyEvidenceLabel()
                if (!candidate.evidenceLabels.contains(label)) {
                    candidate.evidenceLabels.add(label)
                }
            }
        }
        val keptGeneralKeys = generalCandidates
            .filterValues { it.evidenceLabels.distinct().size > 1 }
            .keys
            .toSet()
        val candidates = linkedMapOf<String, AiPurifyRuleCandidate>()
        generalCandidates.forEach { (key, candidate) ->
            if (key in keptGeneralKeys) {
                candidates[key] = candidate
            }
        }
        candidateSources.forEach { (result, parts) ->
            parts.forEach { part ->
                val generalKey = part.generalPattern
                    ?.let { generalPattern ->
                        part.generalReplacement?.let { generalReplacement ->
                            generalPattern + "\u0000" + generalReplacement
                        }
                    }
                val pattern: String
                val replacement: String
                if (generalKey != null && generalKey in keptGeneralKeys) {
                    return@forEach
                } else {
                    pattern = part.pattern
                    replacement = part.replacement
                }
                if (pattern.isBlank() || pattern.isNormalizedSameAs(replacement)) {
                    return@forEach
                }
                val key = pattern + "\u0000" + replacement
                val candidate = candidates.getOrPut(key) {
                    AiPurifyRuleCandidate(
                        pattern = pattern,
                        replacement = replacement,
                        evidenceLabels = arrayListOf()
                    )
                }
                val label = result.aiPurifyEvidenceLabel()
                if (!candidate.evidenceLabels.contains(label)) {
                    candidate.evidenceLabels.add(label)
                }
            }
        }
        return candidates.values.toList()
    }

    private fun AiPurifyResult.aiPurifyEvidenceLabel(): String {
        val paragraphLabel = getString(
            R.string.ai_purify_rule_paragraph_value,
            paragraphIndex ?: 0
        )
        return chapterIndex?.let { chapterIndex ->
            getString(R.string.ai_purify_rule_chapter_paragraph_value, chapterIndex + 1, paragraphIndex ?: 0)
        } ?: paragraphLabel
    }

    private fun AiPurifyResult.canBuildAiPurifyRuleCandidates(
        parts: List<AiPurifyRulePart>
    ): Boolean {
        return parts.all { part ->
            when {
                part.hasRawDeletion && part.hasRawReplacement -> false
                part.hasRawDeletion -> isSafeAiPurifyDeletionRule(part.deletedPattern)
                else -> true
            }
        }
    }

    private fun AiPurifyResult.isSafeAiPurifyDeletionRule(pattern: String): Boolean {
        return if (cleaned.isBlank() && pattern == original) {
            pattern.isLikelyStandaloneAiPurifyPollution()
        } else {
            pattern.isLikelyInlineAiPurifyNoise()
        }
    }

    private fun String.isLikelyStandaloneAiPurifyPollution(): Boolean {
        val value = trim()
        if (value.isBlank()) {
            return false
        }
        val lower = value.lowercase()
        val pollutionKeywords = listOf(
            "http",
            "www",
            "com",
            "域名",
            "首发",
            "无错章节",
            "乱序章节",
            "记住我们网",
            "书友",
            "读者",
            "推荐票",
            "月票",
            "收藏",
            "打赏",
            "盟主",
            "ps",
            "本书",
            "新书",
            "活动",
            "徽章",
            "抽奖"
        )
        return pollutionKeywords.any { lower.contains(it) } || value.isLikelyInlineAiPurifyNoise()
    }

    private fun String.isLikelyInlineAiPurifyNoise(): Boolean {
        val value = trim()
        if (value.isBlank()) {
            return false
        }
        if (value.length == 1 && value[0].isCjkIdeographForAiPurify()) {
            return false
        }
        return value.any { it.isAiPurifyNoiseMarker() }
    }

    private fun Char.isAiPurifyNoiseMarker(): Boolean {
        if (this == '\uFFFD') {
            return true
        }
        if (isLetterOrDigit() && !isCjkIdeographForAiPurify()) {
            return true
        }
        if (this in '①'..'⑳' || this in '⓪'..'⓿') {
            return true
        }
        return when (this) {
            '(', ')', '[', ']', '{', '}', '<', '>', '（', '）', '【', '】',
            '?', '？', '%', '@', '#', '$', '^', '&', '*', '_', '+', '=',
            '|', '\\', '/', '~', '`', '⊙', '∞', '�' -> true
            else -> false
        }
    }

    private fun Char.isCjkIdeographForAiPurify(): Boolean {
        return when (Character.UnicodeBlock.of(this)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS -> true
            else -> false
        }
    }

    private fun AiPurifyResult.toAiPurifyRuleDiff(): AiPurifyRuleDiff {
        if (original == cleaned) {
            return AiPurifyRuleDiff(emptyList(), false)
        }
        val cellCount = (original.length + 1L) * (cleaned.length + 1L)
        if (cellCount > 4_000_000L) {
            return AiPurifyRuleDiff(listOf(AiPurifyRulePart(original, cleaned)), false)
        }
        val width = cleaned.length + 1
        val cost = IntArray((original.length + 1) * width)
        for (i in 0..original.length) {
            cost[i * width] = i
        }
        for (j in 0..cleaned.length) {
            cost[j] = j
        }
        for (i in 1..original.length) {
            for (j in 1..cleaned.length) {
                val replaceCost = if (original[i - 1] == cleaned[j - 1]) 0 else 1
                cost[i * width + j] = minOf(
                    cost[(i - 1) * width + j] + 1,
                    cost[i * width + j - 1] + 1,
                    cost[(i - 1) * width + j - 1] + replaceCost
                )
            }
        }
        val ops = arrayListOf<AiPurifyDiffOp>()
        var i = original.length
        var j = cleaned.length
        while (i > 0 || j > 0) {
            val current = cost[i * width + j]
            if (
                i > 0 &&
                j > 0 &&
                original[i - 1] == cleaned[j - 1] &&
                current == cost[(i - 1) * width + j - 1]
            ) {
                ops.add(AiPurifyDiffOp(0, original[i - 1].toString(), cleaned[j - 1].toString()))
                i--
                j--
            } else if (
                i > 0 &&
                j > 0 &&
                current == cost[(i - 1) * width + j - 1] + 1
            ) {
                ops.add(AiPurifyDiffOp(1, original[i - 1].toString(), cleaned[j - 1].toString()))
                i--
                j--
            } else if (i > 0 && current == cost[(i - 1) * width + j] + 1) {
                ops.add(AiPurifyDiffOp(2, original[i - 1].toString(), ""))
                i--
            } else {
                ops.add(AiPurifyDiffOp(3, "", cleaned[j - 1].toString()))
                j--
            }
        }
        ops.reverse()
        val hasInsertion = ops.any { it.kind == 3 }
        val parts = arrayListOf<AiPurifyRulePart>()
        var index = 0
        while (index < ops.size) {
            if (ops[index].kind == 0) {
                index++
                continue
            }
            val rawStart = index
            while (index < ops.size && ops[index].kind != 0) {
                index++
            }
            val rawEnd = index
            val rawOps = ops.subList(rawStart, rawEnd)
            val hasRawDeletion = rawOps.any { it.kind == 2 }
            val hasRawReplacement = rawOps.any { it.kind == 1 }
            val deletedPattern = rawOps.joinToString("") {
                if (it.kind == 2) it.original else ""
            }
            val start = expandAiPurifyContextStart(ops, rawStart)
            val end = expandAiPurifyContextEnd(ops, rawEnd)
            val originalPart: String
            val cleanedPart: String
            if (hasRawDeletion && !hasRawReplacement) {
                originalPart = deletedPattern
                cleanedPart = ""
            } else {
                originalPart = ops.subList(start, end).joinToString("") { it.original }
                cleanedPart = ops.subList(start, end).joinToString("") { it.cleaned }
            }
            val generalPart = rawOps
                .takeIf { candidateOps -> candidateOps.all { it.kind == 1 } }
                ?.let { rawOps ->
                    val generalPattern = rawOps.joinToString("") { it.original }
                    val generalReplacement = rawOps.joinToString("") { it.cleaned }
                    if (
                        generalPattern.isNotBlank() &&
                        !generalPattern.isNormalizedSameAs(generalReplacement)
                    ) {
                        generalPattern to generalReplacement
                    } else {
                        null
                    }
                }
            val part = AiPurifyRulePart(
                pattern = originalPart,
                replacement = cleanedPart,
                generalPattern = generalPart?.first,
                generalReplacement = generalPart?.second,
                hasRawDeletion = hasRawDeletion,
                hasRawReplacement = hasRawReplacement,
                deletedPattern = deletedPattern
            )
            if (part.pattern.isNotBlank() && !part.pattern.isNormalizedSameAs(part.replacement)) {
                parts.add(part)
            }
        }
        return AiPurifyRuleDiff(parts, hasInsertion)
    }

    private fun expandAiPurifyContextStart(
        ops: List<AiPurifyDiffOp>,
        rawStart: Int
    ): Int {
        var start = rawStart
        var count = 0
        while (
            start > 0 &&
            count < 2 &&
            ops[start - 1].kind == 0 &&
            ops[start - 1].original.singleOrNull()?.isCjkIdeographForAiPurify() == true
        ) {
            start--
            count++
        }
        return start
    }

    private fun expandAiPurifyContextEnd(
        ops: List<AiPurifyDiffOp>,
        rawEnd: Int
    ): Int {
        var end = rawEnd
        var count = 0
        while (
            end < ops.size &&
            count < 2 &&
            ops[end].kind == 0 &&
            ops[end].original.singleOrNull()?.isCjkIdeographForAiPurify() == true
        ) {
            end++
            count++
        }
        return end
    }

    private fun String.normalizedAiPurifyReplacementPreview(): String {
        if (isBlank()) return ""
        return split("、")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.isSameSideReplacementPreview() }
            .joinToString("、")
    }

    private fun String.isSameSideReplacementPreview(): Boolean {
        val arrowIndex = indexOf(" -> ")
        if (arrowIndex <= 0) return false
        val left = substring(0, arrowIndex).trim()
        val right = substring(arrowIndex + 4)
            .substringBefore("×")
            .trim()
        return left.isNormalizedSameAs(right)
    }

    private fun String.isNormalizedSameAs(other: String): Boolean {
        return this == other ||
                Normalizer.normalize(this, Normalizer.Form.NFKC) ==
                Normalizer.normalize(other, Normalizer.Form.NFKC)
    }

    private fun formatAiPurifyInlineChangeSummary(result: AiPurifyResult): String {
        val changes = arrayListOf<String>()
        if (result.deletedCount > 0) {
            changes.add(
                getString(
                    R.string.ai_purify_deleted_change,
                    result.deletedPreview.ifBlank { getString(R.string.ai_purify_deleted_content_none) }
                )
            )
        }
        val replacement = result.replacementPreview.normalizedAiPurifyReplacementPreview()
        if (result.replacementCount > 0 && replacement.isNotBlank()) {
            changes.add(
                getString(
                    R.string.ai_purify_replaced_change,
                    replacement
                )
            )
        }
        return changes.joinToString("\n")
            .ifBlank { getString(R.string.ai_purify_change_content_none) }
    }

    private fun currentReplaceScope(): String {
        val scopes = arrayListOf<String>()
        ReadBook.book?.name?.let { scopes.add(it) }
        ReadBook.bookSource?.bookSourceUrl?.let { scopes.add(it) }
        return scopes.joinToString(";")
    }

    private fun String.ruleNamePreview(): String {
        val compact = replace("\n", " ").trim()
        return "AI净化: " + if (compact.length <= 40) compact else compact.take(40) + "..."
    }

    /**
     * 文本选择菜单操作完成
     */
    override fun onMenuActionFinally() = binding.run {
        textActionMenu.dismiss()
        readView.cancelSelect()
    }

    private fun speak(text: String) {
        if (tts == null) {
            tts = TTS()
        }
        tts?.speak(text)
    }

    /**
     * 鼠标滚轮翻页
     */
    private fun mouseWheelPage(direction: PageDirection, distance: Float) {
        if (menuLayoutIsVisible || !AppConfig.mouseWheelPage) {
            return
        }
        if (binding.readView.isScroll) {
            // 滚动视图时滚动,否则翻页
            (binding.readView.pageDelegate as? ScrollPageDelegate)?.curPage?.scroll((distance * 50).toInt())
        } else {
            keyPageDebounce(direction, mouseWheel = true, longPress = false)
        }
    }
    /**
     * 音量键翻页
     */
    private fun volumeKeyPage(direction: PageDirection, longPress: Boolean): Boolean {
        if (!AppConfig.volumeKeyPage) {
            return false
        }
        if (!AppConfig.volumeKeyPageOnPlay && BaseReadAloudService.isPlay()) {
            return false
        }
        handleKeyPage(direction, longPress)
        return true
    }

    private fun handleKeyPage(direction: PageDirection, longPress: Boolean) {
        if (AppConfig.keyPageOnLongPress || direction == PageDirection.NONE) {
            keyPage(direction)
        } else {
            keyPageDebounce(direction, longPress = longPress)
        }
    }

    private fun keyPageDebounce(
        direction: PageDirection,
        mouseWheel: Boolean = false,
        longPress: Boolean
    ) {
        if (longPress) {
            return
        }
        nextPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        prevPageDebounce.apply {
            wait = if (mouseWheel) 200L else 600L
            leading = !mouseWheel
            trailing = mouseWheel
        }
        when (direction) {
            PageDirection.NEXT -> nextPageDebounce.invoke()
            PageDirection.PREV -> prevPageDebounce.invoke()
            else -> {}
        }
    }

    private fun keyPage(direction: PageDirection) {
        binding.readView.cancelSelect()
        binding.readView.pageDelegate?.isCancel = false
        binding.readView.pageDelegate?.keyTurnPage(direction)
    }

    override fun upMenuView() {
        handler.post {
            observeTextHighlights()
            upMenu()
            binding.readMenu.upBookView()
        }
    }

    private fun observeTextHighlights() {
        val book = ReadBook.book
        val bookKey = book?.let { it.name to it.author }
        if (bookKey == observedTextHighlightBook) return
        observedTextHighlightBook = bookKey
        textHighlightObserveJob?.cancel()
        binding.readView.setTextHighlights(emptyList())
        if (book == null) return
        textHighlightObserveJob = lifecycleScope.launch {
            appDb.bookmarkDao.flowByBook(book.name, book.author).collectLatest { bookmarks ->
                binding.readView.setTextHighlights(
                    bookmarks.filter(Bookmark::isTextHighlight)
                )
            }
        }
    }

    override fun onReadThemeChanged() {
        binding.readView.upBg()
        binding.readView.upStyle()
        binding.readView.upContent(0, false)
        binding.readMenu.reset()
        binding.readMenu.upBrightnessState()
        upSystemUiVisibility()
    }

    override fun loadChapterList(book: Book) {
        ReadBook.upMsg(getString(R.string.toc_updateing))
        viewModel.loadChapterList(book)
    }

    override fun beginReplaceRuleRenderBatch() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (replaceRuleRenderFlushScheduled) {
                binding.root.removeCallbacks(replaceRuleRenderFlushRunnable)
                replaceRuleRenderFlushScheduled = false
            }
            replaceRuleRenderBatchDepth++
        }
    }

    override fun endReplaceRuleRenderBatch() {
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread
            if (replaceRuleRenderBatchDepth > 0) {
                replaceRuleRenderBatchDepth--
            }
            scheduleReplaceRuleRenderFlush()
        }
    }

    private fun deferReplaceRuleRender(
        resetPageOffset: Boolean,
        success: (() -> Unit)? = null
    ): Boolean {
        if (replaceRuleRenderBatchDepth == 0 && !replaceRuleRenderFlushScheduled) {
            return false
        }
        replaceRuleRenderPending = true
        replaceRuleRenderResetPageOffset =
            replaceRuleRenderResetPageOffset || resetPageOffset
        success?.let(replaceRuleRenderSuccessActions::add)
        loadStates = false
        return true
    }

    private fun scheduleReplaceRuleRenderFlush() {
        if (replaceRuleRenderBatchDepth != 0 ||
            !replaceRuleRenderPending ||
            replaceRuleRenderFlushScheduled
        ) {
            return
        }
        replaceRuleRenderFlushScheduled = true
        binding.root.postOnAnimation(replaceRuleRenderFlushRunnable)
    }

    private fun flushReplaceRuleRender() {
        replaceRuleRenderFlushScheduled = false
        if (replaceRuleRenderBatchDepth != 0 || !replaceRuleRenderPending) {
            return
        }
        val resetPageOffset = replaceRuleRenderResetPageOffset
        val successActions = replaceRuleRenderSuccessActions.toList()
        replaceRuleRenderPending = false
        replaceRuleRenderResetPageOffset = false
        replaceRuleRenderSuccessActions.clear()
        binding.readView.upContent(0, resetPageOffset)
        upSeekBarProgress()
        successActions.forEach { it.invoke() }
    }

    /**
     * 内容加载完成
     */
    override fun contentLoadFinish() {
        ReadAloudMiniPlayer.preloadCover(this)
        val startReadAloud = intent.getBooleanExtra("readAloud", false)
        if (startReadAloud) {
            intent.removeExtra("readAloud")
            ReadBook.readAloud()
        }
        loadStates = true
    }

    /**
     * 更新内容
     */
    override fun upContent(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) {
        lifecycleScope.launch {
            if (deferReplaceRuleRender(resetPageOffset, success)) {
                return@launch
            }
            binding.readView.upContent(relativePosition, resetPageOffset)
            if (relativePosition == 0) {
                upSeekBarProgress()
            }
            loadStates = false
            success?.invoke()
        }
    }

    override suspend fun upContentAwait(
        relativePosition: Int,
        resetPageOffset: Boolean,
        success: (() -> Unit)?
    ) = withContext(Main.immediate) {
        if (deferReplaceRuleRender(resetPageOffset)) {
            return@withContext
        }
        binding.readView.upContent(relativePosition, resetPageOffset)
        if (relativePosition == 0) {
            upSeekBarProgress()
        }
        loadStates = false
    }

    override fun upPageAnim(upRecorder: Boolean) {
        lifecycleScope.launch {
            binding.readView.upPageAnim(upRecorder)
        }
    }

    override fun notifyBookChanged() {
        bookChanged = true
        if (!ReadBook.inBookshelf) {
            viewModel.removeFromBookshelf { super.finish() }
        }
    }

    override fun cancelSelect() {
        runOnUiThread {
            binding.readView.cancelSelect()
        }
    }

    override fun dismissTextActionMenu() {
        textActionMenu.dismiss()
    }

    /**
     * 页面改变
     */
    override fun pageChanged(manual: Boolean) {
        if (manual) {
            pageChanged = true
        }
        binding.readView.onPageChange()
        handler.post {
            upSeekBarProgress()
        }
        executor.execute {
            startBackupJob()
        }
    }

    /**
     * 更新进度条位置
     */
    private fun upSeekBarProgress() {
        val progress = when (AppConfig.progressBarBehavior) {
            "page" -> ReadBook.durPageIndex
            else /* chapter */ -> ReadBook.durChapterIndex
        }
        binding.readMenu.setSeekPage(progress)
    }

    /**
     * 显示菜单
     */
    override val oldBook: Book?
        get() = ReadBook.book

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (!book.isAudio) {
            viewModel.changeTo(book, toc)
        } else {
            ReadAloud.stop(this)
            lifecycleScope.launch {
                withContext(IO) {
                    ReadBook.book?.migrateTo(book, toc)
                    book.removeType(BookType.updateError)
                    ReadBook.book?.delete()
                    appDb.bookDao.insert(book)
                }
                startActivityForBook(book)
                finish()
            }
        }
    }

    override fun replaceContent(content: String) {
        ReadBook.book?.let {
            viewModel.saveContent(it, content)
        }
    }

    override fun showActionMenu() {
        when {
            isAutoPage -> showDialogFragment<AutoReadDialog>()
            isShowingSearchResult -> binding.searchMenu.runMenuIn()
            else -> binding.readMenu.runMenuIn()
        }
    }

    /**
     * 自动翻页
     */
    override fun autoPage() {
        ReadAloud.stop(this)
        if (isAutoPage) {
            autoPageStop()
        } else {
            applyAutoPageMode()
            binding.readView.autoPager.start()
            binding.readMenu.setAutoPage(true)
            screenTimeOut = -1L
            screenOffTimerStart()
        }
    }

    internal fun applyAutoPageMode(
        @PageAnim.Anim pageAnim: Int = ReadBookConfig.autoReadPageMode,
    ) {
        val mode = if (pageAnim == PageAnim.coverPageAnim) {
            PageAnim.coverPageAnim
        } else {
            PageAnim.scrollPageAnim
        }
        ReadBookConfig.autoReadPageMode = mode
        binding.readView.autoPager.reset()
        binding.readView.upPageAnim(mode)
        ReadBook.loadContent(false)
    }

    override fun autoPageStop() {
        if (isAutoPage) {
            binding.readView.autoPager.stop()
            binding.readView.upPageAnim()
            ReadBook.loadContent(false)
            binding.readMenu.setAutoPage(false)
            dismissDialogFragment<AutoReadDialog>()
            upScreenTimeOut()
        }
    }

    override fun openSourceEditActivity() {
        ReadBook.bookSource?.let {
            sourceEditActivity.launch {
                putExtra("sourceUrl", it.bookSourceUrl)
            }
        }
    }

    override fun openBookInfoActivity() {
        ReadBook.book?.let {
            bookInfoActivity.launch {
                putExtra("name", it.name)
                putExtra("author", it.author)
            }
        }
    }

    /**
     * 替换
     */
    override fun openReplaceRule() {
        replaceActivity.launch(
            ReplaceRuleActivity.startIntent(
                this,
                bookName = ReadBook.book?.name,
                sourceName = ReadBook.bookSource?.bookSourceName,
                sourceUrl = ReadBook.bookSource?.bookSourceUrl
            )
        )
    }

    /**
     * 打开目录
     */
    override fun openChapterList() {
        if (ReadBook.book == null) return
        ReadCatalogDialog().show(supportFragmentManager, "readCatalog")
    }

    /**
     * 打开阅读页内的全文搜索抽屉
     */
    override fun openSearchDrawer(searchWord: String?) {
        if (ReadBook.book == null) return
        if (supportFragmentManager.findFragmentByTag(ReadSearchDialog.TAG) != null) return
        val resolvedQuery = searchWord ?: viewModel.searchContentQuery
        val selectedIndex = if (resolvedQuery == viewModel.searchContentQuery) {
            viewModel.searchResultIndex
        } else {
            -1
        }
        ReadSearchDialog.newInstance(resolvedQuery, selectedIndex)
            .show(supportFragmentManager, ReadSearchDialog.TAG)
    }

    internal fun showSearchResult(searchResults: List<SearchResult>, index: Int) {
        if (searchResults.isEmpty()) return
        val safeIndex = index.coerceIn(searchResults.indices)
        val searchResult = searchResults[safeIndex]
        if (!isShowingSearchResult) {
            ReadBook.saveCurrentBookProgress() // 退出全文搜索时恢复进入前的进度
        }
        viewModel.searchContentQuery = searchResult.query
        viewModel.searchResultList = searchResults.toList()
        viewModel.searchResultIndex = safeIndex
        binding.searchMenu.upSearchResultList(searchResults)
        binding.searchMenu.updateSearchResultIndex(safeIndex)
        isShowingSearchResult = true
        skipToSearch(searchResult)
        showActionMenu()
    }

    override fun setSourceEnabled(enabled: Boolean) {
        viewModel.setSourceEnabled(enabled)
    }

    /**
     * 显示阅读样式配置
     */
    override fun showReadStyle() {
        showDialogFragment<ReadStyleDialog>()
    }

    /**
     * 显示更多设置
     */
    override fun showMoreSetting() {
        showDialogFragment<MoreConfigDialog>()
    }

    /**
     * 更新状态栏,导航栏
     */
    override fun upSystemUiVisibility() {
        upSystemUiVisibility(isInMultiWindow, !menuLayoutIsVisible, bottomDialog > 0)
        upNavigationBarColor()
    }

    // 退出全文搜索
    override fun exitSearchMenu() {
        if (isShowingSearchResult) {
            isShowingSearchResult = false
            binding.searchMenu.invalidate()
            binding.searchMenu.invisible()
            ReadBook.clearSearchResult()
            binding.readView.cancelSelect(true)
        }
    }

    override fun restoreSearchOrigin() {
        if (!isShowingSearchResult) return
        exitSearchMenu()
        ReadBook.restoreLastBookProgress()
    }

    /* 恢复到 全文搜索/进度条跳转前的位置 */
    private fun restoreLastBookProcess() {
        if (confirmRestoreProcess == true) {
            ReadBook.restoreLastBookProgress()
        } else if (confirmRestoreProcess == null) {
            showReadConfirmDialog(
                context = this,
                title = getString(R.string.draw),
                message = getString(R.string.restore_last_book_process),
                confirmLabel = getString(R.string.yes),
                cancelLabel = getString(R.string.no),
                onConfirm = {
                    confirmRestoreProcess = true
                    ReadBook.restoreLastBookProgress() //恢复启动全文搜索前的进度
                },
                onCancel = {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                },
                onOutsideDismiss = {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                },
            )
        }
    }

    override fun showLogin() {
        ReadBook.bookSource?.let {
            startActivity<SourceLoginActivity> {
                putExtra("bookType", BookType.text)
            }
        }
    }

    override fun payAction() {
        val book = ReadBook.book ?: return
        if (book.isLocal) return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
        if (chapter == null) {
            toastOnUi("no chapter")
            return
        }
        showReadConfirmDialog(
            context = this,
            title = getString(R.string.chapter_pay),
            message = chapter.title,
            confirmLabel = getString(R.string.yes),
            cancelLabel = getString(R.string.no),
            onConfirm = {
                Coroutine.async(lifecycleScope) {
                    val source =
                        ReadBook.bookSource ?: throw NoStackTraceException("no book source")
                    val payAction = source.getContentRule().payAction
                    if (payAction.isNullOrBlank()) {
                        throw NoStackTraceException("no pay action")
                    }
                    val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
                    runScriptWithContext {
                        source.evalJS(payAction) {
                            put("java", java)
                            put("book", book)
                            put("chapter", chapter)
                            put("title", chapter.title)
                            put("baseUrl", chapter.url)
                            put("result", null)
                            put("src", null)
                        }.toString()
                    }
                }.onSuccess(IO) {
                    if (it.isAbsUrl()) {
                        startActivity<WebViewActivity> {
                            val bookSource = ReadBook.bookSource
                            putExtra("title", getString(R.string.chapter_pay))
                            putExtra("url", it)
                            putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                            putExtra("sourceName", bookSource?.bookSourceName)
                            putExtra("sourceType", bookSource?.getSourceType())
                        }
                    } else if (it.isTrue()) {
                        //购买成功后刷新目录
                        ReadBook.book?.let {
                            ReadBook.curTextChapter = null
                            BookHelp.delContent(book, chapter)
                            loadChapterList(book)
                        }
                    }
                }.onError {
                    AppLog.put("执行购买操作出错\n${it.localizedMessage}", it, true)
                }
            },
        )
    }

    /**
     * 点击图片
     */
    override fun oldClickImg(src: String): Boolean {
        val urlMatcher = paramPattern.matcher(src)
        if (urlMatcher.find()) {
            val urlOptionStr = src.substring(urlMatcher.end())
            val urlOptionMap = GSON.fromJsonObject<Map<String, String>>(urlOptionStr).getOrNull()
            val click = urlOptionMap?.get("click")
            if (click != null) {
                Coroutine.async(lifecycleScope,IO) {
                    val source = ReadBook.bookSource ?: return@async
                    val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
                    val book = ReadBook.book ?: return@async
                    val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
                    runScriptWithContext {
                        source.evalJS(click) {
                            put("java", java)
                            put("book", book)
                            put("chapter", chapter)
                            put("result", src)
                        }
                    }
                }.onError {
                    AppLog.put("执行图片链接click键值出错\n${it.localizedMessage}", it, true)
                }
                return true
            }
            val jsStr = urlOptionMap?.get("js") ?: return false
            Coroutine.async(lifecycleScope, IO) {
                val source = ReadBook.bookSource ?: return@async
                val book = ReadBook.book ?: return@async
                val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
                val urlNoOption = src.take(urlMatcher.start())
                AnalyzeRule(book, source).apply {
                    setCoroutineContext(coroutineContext)
                    setBaseUrl(chapter.url)
                    setChapter(chapter)
                    evalJS(jsStr, urlNoOption)
                }
            }.onError {
                AppLog.put("执行图片链接js键值出错\n${it.localizedMessage}", it, true)
            }
            return true
        }
        return false
    }

    override fun clickImg(click: String, src: String) {
        Coroutine.async(lifecycleScope,IO) {
            val source = ReadBook.bookSource ?: return@async
            val java = SourceLoginJsExtensions(this@ReadBookActivity, source, BookType.text)
            val book = ReadBook.book ?: return@async
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex) ?: throw Exception("no find chapter")
            runScriptWithContext {
                source.evalJS(click) {
                    put("java", java)
                    put("book", book)
                    put("chapter", chapter)
                    put("result", src)
                }
            }
        }.onError {
            AppLog.put("执行图片链接click键值出错\n${it.localizedMessage}", it, true)
        }
    }

    override fun onTextHighlightClick(
        bookmark: Bookmark,
        anchorX: Float,
        top: Float,
        bottom: Float,
    ) {
        binding.readView.cancelSelect()
        activeTextHighlight = bookmark
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM) {
                binding.navigationBar.height
            } else {
                0
            }
        textActionMenu.showTextHighlight(
            view = binding.textMenuPosition,
            windowHeight = binding.root.height + navigationBarHeight,
            anchorX = anchorX.toInt(),
            anchorTopY = top.toInt(),
            anchorBottomY = bottom.toInt(),
            textHighlight = bookmark,
        )
    }


    /**
     * 朗读按钮
     */
    override fun onClickReadAloud() {
        if (BaseReadAloudService.isRun) {
            ReadAloudMiniPlayer.attach(this)
            toggleReadAloud()
            return
        }
        ReadAloudMiniPlayer.showStarting(this)
        binding.root.postOnAnimation {
            binding.root.postOnAnimation {
                if (!isFinishing && !isDestroyed) {
                    toggleReadAloud()
                }
            }
        }
    }

    private fun toggleReadAloud() {
        autoPageStop()
        when {
            !BaseReadAloudService.isRun -> {
                ReadAloud.upReadAloudClass()
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim) {
                    val pos = binding.readView.getReadAloudPos()
                    if (pos != null) {
                        val (index, line) = pos
                        if (ReadBook.durChapterIndex != index) {
                            ReadBook.openChapter(index, line.chapterPosition, false) {
                                ReadBook.readAloud(startPos = line.pagePosition)
                            }
                        } else {
                            ReadBook.durChapterPos = line.chapterPosition
                            ReadBook.readAloud(startPos = line.pagePosition)
                        }
                    } else {
                        ReadBook.readAloud()
                    }
                } else {
                    ReadBook.readAloud()
                }
            }

            !BaseReadAloudService.isPlay() -> {
                if (pageChanged) {
                    pageChanged = false
                    startReadAloudFromVisiblePosition()
                } else {
                    ReadAloud.resume(this)
                }
            }

            else -> ReadAloud.pause(this)
        }
    }

    private fun startReadAloudFromVisiblePosition() {
        val pos = binding.readView.getReadAloudPos()
        if (pos == null) {
            ReadBook.readAloud()
            return
        }
        val (index, line) = pos
        if (ReadBook.durChapterIndex != index) {
            ReadBook.openChapter(index, line.chapterPosition, false) {
                ReadBook.readAloud(startPos = line.pagePosition)
            }
        } else {
            ReadBook.durChapterPos = line.chapterPosition
            ReadBook.readAloud(startPos = line.pagePosition)
        }
    }

    /**
     * 长按图片
     */
    @SuppressLint("RtlHardcoded")
    override fun onImageLongPress(x: Float, y: Float, src: String) {
        popupAction.setItems(
            listOf(
                SelectItem(getString(R.string.show), "show"),
                SelectItem(getString(R.string.refresh), "refresh"),
                SelectItem(getString(R.string.action_save), "save"),
                SelectItem(getString(R.string.menu), "menu"),
                SelectItem(getString(R.string.select_folder), "selectFolder")
            )
        )
        popupAction.onActionClick = {
            when (it) {
                "show" -> showDialogFragment(PhotoDialog(src, isBook = true))
                "refresh" -> viewModel.refreshImage(src)
                "save" -> {
                    val path = ACache.get().getAsString(AppConst.imagePathKey)
                    if (path.isNullOrEmpty()) {
                        selectImageDir.launch(
                            SelectDirectoryContract.Request(value = src)
                        )
                    } else {
                        viewModel.saveImage(src, path.toUri())
                    }
                }

                "menu" -> showActionMenu()
                "selectFolder" -> selectImageDir.launch(null)
            }
            popupAction.dismiss()
        }
        val navigationBarHeight =
            if (!ReadBookConfig.hideNavigationBar && navigationBarGravity == Gravity.BOTTOM)
                binding.navigationBar.height else 0
        popupAction.showAtLocation(
            binding.readView, Gravity.BOTTOM or Gravity.LEFT, x.toInt(),
            binding.root.height + navigationBarHeight - y.toInt()
        )
    }

    /**
     * colorSelectDialog
     */
    override fun onColorSelected(dialogId: Int, color: Int) = ReadBookConfig.durConfig.run {
        when (dialogId) {
            TIP_COLOR -> {
                ReadTipConfig.tipColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }

            TIP_DIVIDER_COLOR -> {
                ReadTipConfig.tipDividerColor = color
                postEvent(EventBus.TIP_COLOR, "")
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    /**
     * colorSelectDialog
     */
    override fun onDialogDismissed(dialogId: Int) = Unit

    override fun onTocRegexDialogResult(tocRegex: String) {
        ReadBook.book?.let {
            it.tocUrl = tocRegex
            loadChapterList(it)
        }
    }

    private fun sureSyncProgress(progress: BookProgress) {
        showReadConfirmDialog(
            context = this,
            title = getString(R.string.get_book_progress),
            message = getString(R.string.current_progress_exceeds_cloud),
            confirmLabel = getString(R.string.ok),
            cancelLabel = getString(R.string.no),
            onConfirm = {
                ReadBook.setProgress(progress)
            },
        )
    }

    /* 进度条跳转到指定章节 */
    override fun skipToChapter(index: Int) {
        ReadBook.saveCurrentBookProgress() //退出章节跳转恢复此时进度
        viewModel.openChapter(index)
    }

    /* 全文搜索跳转 */
    override fun navigateToSearch(searchResult: SearchResult, index: Int) {
        viewModel.searchResultIndex = index
        skipToSearch(searchResult)
    }

    override fun onMenuShow() {
        binding.readView.autoPager.pause()
        binding.readView.upTipVisibility(true)
    }

    override fun onMenuHide() {
        binding.readView.autoPager.resume()
        binding.readView.upTipVisibility(false)
    }

    override fun onLayoutPageCompleted(index: Int, page: TextPage) {
        upSeekBarThrottle.invoke()
        binding.readView.onLayoutPageCompleted(index, page)
    }

    /* 全文搜索跳转 */
    private fun skipToSearch(searchResult: SearchResult) {
        if (searchResult.chapterIndex != ReadBook.durChapterIndex) {
            viewModel.openChapter(searchResult.chapterIndex) {
                jumpToPosition(searchResult)
            }
        } else {
            jumpToPosition(searchResult)
        }
    }

    private fun jumpToPosition(searchResult: SearchResult) {
        val curTextChapter = ReadBook.curTextChapter ?: return
        binding.searchMenu.updateSearchInfo()
        val searchResultPositions =
            viewModel.searchResultPositions(curTextChapter, searchResult)
        val (pageIndex, lineIndex, charIndex, addLine, charIndex2) = searchResultPositions
        ReadBook.skipToPage(pageIndex) {
            isSelectingSearchResult = true
            binding.readView.curPage.selectStartMoveIndex(0, lineIndex, charIndex)
            when (addLine) {
                0 -> binding.readView.curPage.selectEndMoveIndex(
                    0,
                    lineIndex,
                    charIndex + searchResultPositions[5] - 1
                )

                1 -> binding.readView.curPage.selectEndMoveIndex(
                    0, lineIndex + 1, charIndex2
                )
                //consider change page, jump to scroll position
                -1 -> binding.readView.curPage.selectEndMoveIndex(1, 0, charIndex2)
            }
            binding.readView.isTextSelected = true
            isSelectingSearchResult = false
        }
    }

    override fun addBookmark() {
        val book = ReadBook.book
        val page = ReadBook.curTextChapter?.getPage(ReadBook.durPageIndex)
        if (book != null && page != null) {
            val bookmark = book.createBookMark().apply {
                chapterIndex = ReadBook.durChapterIndex
                chapterPos = ReadBook.durChapterPos
                chapterName = page.title
                bookText = page.text.trim()
            }
            showDialogFragment(BookmarkDialog(bookmark))
        }
    }

    override fun changeReplaceRuleState() {
        ReadBook.book?.let {
            it.setUseReplaceRule(!it.getUseReplaceRule())
            ReadBook.saveRead()
            menu?.findItem(R.id.menu_enable_replace)?.isChecked = it.getUseReplaceRule()
            viewModel.replaceRuleChanged()
        }
    }

    private fun startBackupJob() {
        backupJob?.cancel()
        backupJob = lifecycleScope.launch(IO) {
            delay(300000)
            ReadBook.book?.let {
                AppWebDav.uploadBookProgress(it)
                ensureActive()
                it.update()
                Backup.autoBack(this@ReadBookActivity)
            }
        }
    }

    override fun sureNewProgress(progress: BookProgress) {
        syncDialog?.dismiss()
        syncDialog = showReadConfirmDialog(
            context = this,
            title = getString(R.string.get_book_progress),
            message = getString(R.string.cloud_progress_exceeds_current),
            confirmLabel = getString(R.string.ok),
            cancelLabel = getString(R.string.no),
            onConfirm = {
                ReadBook.setProgress(progress)
            },
        )
    }

    override fun finish() {
        val book = ReadBook.book ?: return super.finish()
        if (ReadBook.inBookshelf) {
            callBackBookEnd()
            return super.finish()
        }
        if (!AppConfig.showAddToShelfAlert) {
            callBackBookEnd()
            viewModel.removeFromBookshelf { super.finish() }
        } else {
            showReadConfirmDialog(
                context = this,
                title = getString(R.string.add_to_bookshelf),
                message = getString(R.string.check_add_bookshelf, book.name),
                confirmLabel = getString(R.string.ok),
                cancelLabel = getString(R.string.no),
                onConfirm = {
                    ReadBook.book?.removeType(BookType.notShelf)
                    ReadBook.book?.save()
                    SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, ReadBook.bookSource, ReadBook.book)
                    ReadBook.inBookshelf = true
                    setResult(RESULT_OK)
                },
                onCancel = {
                    callBackBookEnd()
                    viewModel.removeFromBookshelf { super.finish() }
                },
            )
        }
    }

    private fun callBackBookEnd() {
        SourceCallBack.callBackBook(SourceCallBack.END_READ, ReadBook.bookSource, ReadBook.book, ReadBook.curTextChapter?.chapter)
    }

    override fun onDestroy() {
        super.onDestroy()
        binding.root.removeCallbacks(replaceRuleRenderFlushRunnable)
        aiPurifyJob?.cancel()
        tts?.clearTts()
        textActionMenu.dismiss()
        popupAction.dismiss()
        binding.readView.onDestroy()
        ReadBook.unregister(this)
        handler.removeCallbacksAndMessages(null) // 清理Handler消息
        if (!ReadBook.inBookshelf && !isChangingConfigurations) {
            viewModel.removeFromBookshelf(null)
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    override fun observeLiveBus() = binding.run {
        observeEvent<String>(EventBus.TIME_CHANGED) { readView.upTime() }
        observeEvent<Int>(EventBus.BATTERY_CHANGED) { readView.upBattery(it) }
        observeEvent<Boolean>(EventBus.SYSTEM_UI_MODE_CHANGED) { systemNightMode ->
            if (ReadBookConfig.syncFollowSystemTheme(systemNightMode)) {
                onReadThemeChanged()
            }
        }
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                toggleReadAloud()
            } else {
                ReadBook.readAloud(BaseReadAloudService.isPlay())
            }
        }
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            it.forEach { value ->
                when (value) {
                    0 -> upSystemUiVisibility()
                    1 -> readView.upBg()
                    2 -> readView.upStyle()
                    3 -> readView.upBgAlpha()
                    4 -> readView.upPageSlopSquare()
                    5 -> if (isInitFinish) ReadBook.loadContent(resetPageOffset = false)
                    6 -> readView.upContent(resetPageOffset = false)
                    8 -> ChapterProvider.upStyle()
                    9 -> readView.invalidateTextPage()
                    10 -> ChapterProvider.upLayout()
                    11 -> readView.submitRenderTask()
                    12 -> readView.upPageTouchClick()
                }
            }
        }
        observeEvent<Int>(EventBus.ALOUD_STATE) {
            val keepManualPosition = pageChanged
            if (it == Status.STOP || it == Status.PAUSE) {
                if (!keepManualPosition) {
                    ReadBook.curTextChapter?.let { textChapter ->
                        val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                        if (page != null) {
                            page.removePageAloudSpan()
                            readView.upContent(resetPageOffset = false)
                        }
                    }
                }
            }
            if (it == Status.PLAY || it == Status.STOP) {
                pageChanged = false
            }
        }
        observeEventSticky<Int>(EventBus.TTS_PROGRESS) { chapterStart ->
            if (pageChanged) {
                return@observeEventSticky
            }
            lifecycleScope.launch(IO) {
                if (BaseReadAloudService.isPlay()) {
                    ReadBook.curTextChapter?.let { textChapter ->
                        ReadBook.durChapterPos = chapterStart
                        val pageIndex = ReadBook.durPageIndex
                        val aloudSpanStart = chapterStart - textChapter.getReadLength(pageIndex)
                        textChapter.getPage(pageIndex)
                            ?.upPageAloudSpan(aloudSpanStart)
                        upContent()
                    }
                }
            }
        }
        observeEvent<Boolean>(PreferKey.keepLight) {
            upScreenTimeOut()
        }
        observeEvent<Boolean>(PreferKey.textSelectAble) {
            readView.curPage.upSelectAble(it)
        }
        observeEvent<String>(PreferKey.showBrightnessView) {
            readMenu.upFloatingToolVisibility()
        }
        observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            readMenu.reset()
        }
        observeEvent<Boolean>(EventBus.READ_ALOUD_OPEN_PLAYER) {
            ReadAloudLauncher.openPlayer(this@ReadBookActivity)
        }
        observeEvent<Boolean>(EventBus.UP_SEEK_BAR) {
            readMenu.upSeekBar()
        }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_CONTENT) { //书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadBook.book?.let {
                    ReadBook.curTextChapter = null
                    binding.readView.upContent()
                    viewModel.refreshContentDur(it)
                }
            }
        }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_TOC) { //书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadBook.book?.let {
                    loadChapterList(it)
                }
            }
        }
    }

    private fun upScreenTimeOut() {
        val keepLightPrefer = getPrefString(PreferKey.keepLight)?.toInt() ?: 0
        screenTimeOut = keepLightPrefer * 1000L
        screenOffTimerStart()
    }

    /**
     * 重置黑屏时间
     */
    override fun screenOffTimerStart() {
        handler.post {
            if (screenTimeOut < 0) {
                keepScreenOn(true)
                return@post
            }
            val t = screenTimeOut - sysScreenOffTime
            if (t > 0) {
                keepScreenOn(true)
                handler.removeCallbacks(screenOffRunnable)
                handler.postDelayed(screenOffRunnable, screenTimeOut)
            } else {
                keepScreenOn(false)
            }
        }
    }

    override fun onReplaceRuleSaved() {
        viewModel.replaceRuleChanged()
    }

    companion object {
        const val RESULT_DELETED = 100
    }

}
