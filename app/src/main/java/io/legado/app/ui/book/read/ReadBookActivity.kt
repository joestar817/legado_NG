package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.CheckBox
import android.widget.EditText
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
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
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Status
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.IntentData
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
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.accentColor
import io.legado.app.model.ReadAloud
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
import io.legado.app.ui.book.read.config.AutoReadDialog
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.BG_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_ACCENT_COLOR
import io.legado.app.ui.book.read.config.BgTextConfigDialog.Companion.TEXT_COLOR
import io.legado.app.ui.book.read.config.MoreConfigDialog
import io.legado.app.ui.book.read.config.ReadAloudDialog
import io.legado.app.ui.book.read.config.ReadStyleDialog
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_COLOR
import io.legado.app.ui.book.read.config.TipConfigDialog.Companion.TIP_DIVIDER_COLOR
import io.legado.app.ui.book.read.page.ContentTextView
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.delegate.ScrollPageDelegate
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.ui.book.read.page.entities.TextPage
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.page.provider.LayoutProgressListener
import io.legado.app.ui.book.searchContent.SearchContentActivity
import io.legado.app.ui.book.searchContent.SearchResult
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.book.toc.rule.TxtTocRuleDialog
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.dict.DictDialog
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.replace.edit.ReplaceEditActivity
import io.legado.app.ui.widget.PopupAction
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.ACache
import io.legado.app.utils.Debounce
import io.legado.app.utils.LogUtils
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyOpenTint
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.dismissDialogFragment
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.iconItemOnLongClick
import io.legado.app.utils.invisible
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isTrue
import io.legado.app.utils.launch
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
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
    PopupMenu.OnMenuItemClickListener,
    ReadMenu.CallBack,
    SearchMenu.CallBack,
    ReadAloudDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeChapterSourceDialog.CallBack,
    ReadBook.CallBack,
    AutoReadDialog.CallBack,
    TxtTocRuleDialog.CallBack,
    ColorPickerDialogListener,
    LayoutProgressListener {

    private val tocActivity =
        registerForActivityResult(TocActivityResult()) {
            it?.let {
                viewModel.openChapter(it[0] as Int, it[1] as Int)
            }
        }
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
    private val searchContentActivity =
        registerForActivityResult(StartActivityContract(SearchContentActivity::class.java)) {
            val data = it.data ?: return@registerForActivityResult
            val key = data.getLongExtra("key", System.currentTimeMillis())
            val index = data.getIntExtra("index", 0)
            val searchResult = IntentData.get<SearchResult>("searchResult$key")
            val searchResultList = IntentData.get<List<SearchResult>>("searchResultList$key")
            if (searchResult != null && searchResultList != null) {
                viewModel.searchContentQuery = searchResult.query
                binding.searchMenu.upSearchResultList(searchResultList)
                isShowingSearchResult = true
                viewModel.searchResultIndex = index
                binding.searchMenu.updateSearchResultIndex(index)
                binding.searchMenu.selectedSearchResult?.let { currentResult ->
                    ReadBook.saveCurrentBookProgress() //退出全文搜索恢复此时进度
                    skipToSearch(currentResult)
                    showActionMenu()
                }
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
    private val selectImageDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            ACache.get().put(AppConst.imagePathKey, uri.toString())
            viewModel.saveImage(it.value, uri)
        }
    }
    private var menu: Menu? = null
    private var backupJob: Job? = null
    private var aiPurifyJob: Job? = null
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
    override var isShowingSearchResult = false
    override var isSelectingSearchResult = false
        set(value) {
            field = value && isShowingSearchResult
        }
    private val timeBatteryReceiver = TimeBatteryReceiver()
    private var screenTimeOut: Long = 0
    private var loadStates: Boolean = false
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
    private var syncDialog: AlertDialog? = null

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
            if (BaseReadAloudService.isPlay()) {
                ReadAloud.pause(this@ReadBookActivity)
                toastOnUi(R.string.read_aloud_pause)
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
        Looper.myQueue().addIdleHandler {
            viewModel.initData(intent)
            false
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
            binding.readMenu.upBrightnessState()
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
        menu.iconItemOnLongClick(R.id.menu_change_source) {
            PopupMenu(this, it).apply {
                inflate(R.menu.book_read_change_source)
                this.menu.applyOpenTint(this@ReadBookActivity)
                setOnMenuItemClickListener(this@ReadBookActivity)
            }.show()
        }
        menu.iconItemOnLongClick(R.id.menu_refresh) {
            PopupMenu(this, it).apply {
                inflate(R.menu.book_read_refresh)
                this.menu.applyOpenTint(this@ReadBookActivity)
                setOnMenuItemClickListener(this@ReadBookActivity)
            }.show()
        }
        binding.readMenu.refreshMenuColorFilter()
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        this.menu = menu
        upMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.findItem(R.id.menu_same_title_removed)?.isChecked =
            ReadBook.curTextChapter?.sameTitleRemoved == true
        return super.onMenuOpened(featureId, menu)
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
                R.id.menu_group_on_line -> item.isVisible = onLine
                R.id.menu_group_local -> item.isVisible = !onLine
                R.id.menu_group_text -> item.isVisible = book.isLocalTxt
                R.id.menu_group_epub -> item.isVisible = book.isEpub
                else -> when (item.itemId) {
                    R.id.menu_enable_replace -> item.isChecked = book.getUseReplaceRule()
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
            R.id.menu_book_change_source -> {
                binding.readMenu.runMenuOut()
                ReadBook.book?.let {
                    showDialogFragment(ChangeBookSourceDialog(it.name, it.author))
                }
            }

            R.id.menu_chapter_change_source -> lifecycleScope.launch {
                val book = ReadBook.book ?: return@launch
                val chapter =
                    appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
                        ?: return@launch
                binding.readMenu.runMenuOut()
                showDialogFragment(
                    ChangeChapterSourceDialog(book.name, book.author, chapter.index, chapter.title)
                )
            }

            R.id.menu_refresh,
            R.id.menu_refresh_dur -> {
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

            R.id.menu_refresh_after -> {
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

            R.id.menu_refresh_all -> {
                if (ReadBook.bookSource == null) {
                    upContent()
                } else {
                    ReadBook.book?.let {
                        refreshContentAll(it)
                    }
                }
            }

            R.id.menu_download -> showDownloadDialog()
            R.id.menu_add_bookmark -> addBookmark()
            R.id.menu_simulated_reading -> showSimulatedReading()
            R.id.menu_edit_content -> showDialogFragment(ContentEditDialog())
            R.id.menu_update_toc -> ReadBook.book?.let {
                if (it.isEpub) {
                    BookHelp.clearCache(it)
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

            R.id.menu_page_anim -> showPageAnimConfig {
                binding.readView.upPageAnim()
                ReadBook.loadContent(false)
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
            R.id.menu_image_style -> {
                val imgStyles =
                    arrayListOf(
                        Book.imgStyleDefault, Book.imgStyleFull, Book.imgStyleText,
                        Book.imgStyleSingle
                    )
                selector(
                    R.string.image_style,
                    imgStyles
                ) { _, index ->
                    val imageStyle = imgStyles[index]
                    ReadBook.book?.setImageStyle(imageStyle)
                    if (imageStyle == Book.imgStyleSingle) {
                        ReadBook.book?.setPageAnim(0)  // 切换图片样式single后，自动切换为覆盖
                        binding.readView.upPageAnim()
                    }
                    ReadBook.loadContent(false)
                }
            }

            R.id.menu_get_progress -> ReadBook.book?.let {
                viewModel.syncBookProgress(it) { progress ->
                    sureSyncProgress(progress)
                }
            }

            R.id.menu_cover_progress -> ReadBook.book?.let {
                ReadBook.uploadProgress(true) { toastOnUi(R.string.upload_book_success) }
            }

            R.id.menu_same_title_removed -> {
                ReadBook.book?.let {
                    val contentProcessor = ContentProcessor.get(it)
                    val textChapter = ReadBook.curTextChapter
                    if (textChapter != null
                        && !textChapter.sameTitleRemoved
                        && !contentProcessor.removeSameTitleCache.contains(
                            textChapter.chapter.getFileName("nr")
                        )
                    ) {
                        toastOnUi("未找到可移除的重复标题")
                    }
                }
                viewModel.reverseRemoveSameTitle()
            }

            R.id.menu_effective_replaces -> showDialogFragment<EffectiveReplacesDialog>()

            R.id.menu_help -> showHelp()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun refreshContentAll(book: Book) {
        ReadBook.clearTextChapter()
        binding.readView.upContent()
        viewModel.refreshContentAll(book)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return onCompatOptionsItemSelected(item)
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
            binding.textMenuPosition.x.toInt(),
            binding.textMenuPosition.y.toInt(),
            binding.cursorLeft.y.toInt() + binding.cursorLeft.height,
            binding.cursorRight.x.toInt(),
            binding.cursorRight.y.toInt() + binding.cursorRight.height
        )
    }

    /**
     * 当前选择的文本
     */
    override val selectedText: String get() = binding.readView.getSelectText()

    /**
     * 文本选择菜单操作
     */
    override fun onMenuItemSelected(itemId: Int): Boolean {
        when (itemId) {
            R.id.menu_aloud -> when (AppConfig.contentSelectSpeakMod) {
                1 -> lifecycleScope.launch {
                    binding.readView.aloudStartSelect()
                }

                else -> speak(binding.readView.getSelectText())
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
                replaceActivity.launch(
                    ReplaceEditActivity.startIntent(
                        this,
                        pattern = text,
                        scope = scopes.joinToString(";")
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
                openSearchActivity(selectedText)
                return true
            }

            R.id.menu_dict -> {
                showDialogFragment(DictDialog(selectedText))
                return true
            }
        }
        return false
    }

    private fun startAiPurifySelectedText(text: String = selectedText) {
        val source = AiPurifyHelper.normalizeSelectedText(text)
        if (source.isBlank()) {
            toastOnUi("选中文本为空")
            return
        }
        aiPurifyJob?.cancel()
        val waitDialog = WaitDialog(this).apply {
            setText(R.string.ai_purify)
            setOnCancelListener {
                aiPurifyJob?.cancel()
            }
            show()
        }
        aiPurifyJob = lifecycleScope.launch {
            try {
                val startedAt = SystemClock.elapsedRealtime()
                val result = withContext(IO) {
                    AiPurifyHelper.purify(source)
                }
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                waitDialog.dismiss()
                if (shouldAutoApplyAiPurifyResult(result)) {
                    applyAiPurifyResult(result)
                } else {
                    showAiPurifyConfirmDialog(result, elapsedMs)
                }
            } catch (e: Throwable) {
                waitDialog.dismiss()
                alert(titleResource = R.string.ai_purify) {
                    setMessage(e.localizedMessage ?: e.toString())
                    okButton()
                }
            }
        }
    }

    private fun showAiPurifyConfirmDialog(result: AiPurifyResult, elapsedMs: Long) {
        val view = layoutInflater.inflate(R.layout.dialog_ai_purify_confirm, null)
        view.findViewById<TextView>(R.id.tv_purify_deleted_count).text = result.deletedCount.toString()
        view.findViewById<TextView>(R.id.tv_purify_rule_count).text = "1"
        view.findViewById<TextView>(R.id.tv_purify_elapsed).text = formatAiPurifyElapsed(elapsedMs)
        view.findViewById<TextView>(R.id.tv_purify_model).text = formatAiPurifyModel(result.model)
        view.findViewById<TextView>(R.id.tv_original).text = result.original
        view.findViewById<TextView>(R.id.tv_cleaned).text = result.cleaned
        view.findViewById<TextView>(R.id.tv_deleted).text = formatAiPurifyInlineChangeSummary(result)
        view.prepareAiPurifyDialogSize(R.id.scroll_ai_purify_content)

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        view.findViewById<View>(R.id.btn_apply).setOnClickListener {
            dialog.dismiss()
            applyAiPurifyResult(result)
        }
        view.findViewById<View>(R.id.btn_retry).setOnClickListener {
            dialog.dismiss()
            startAiPurifySelectedText(result.original)
        }
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.run {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val width = resources.displayMetrics.widthPixels - 48.dpToPx()
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
    }

    private fun View.prepareAiPurifyDialogSize(scrollViewId: Int) {
        val maxHeight = (resources.displayMetrics.heightPixels * 0.86f).toInt()
        val width = resources.displayMetrics.widthPixels - 48.dpToPx()
        measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        if (measuredHeight <= maxHeight) {
            return
        }
        layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxHeight
        )
        findViewById<ScrollView>(scrollViewId).layoutParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 10.dpToPx()
            }
    }

    private fun shouldAutoApplyAiPurifyResult(result: AiPurifyResult): Boolean {
        if (!AiConfig.purifyAutoApply || result.original == result.cleaned) {
            return false
        }
        return !AiConfig.purifyExceptionIntercept || result.canAutoApply
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
                alert(titleResource = R.string.ai_purify) {
                    setMessage(e.localizedMessage ?: e.toString())
                    okButton()
                }
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
                alert(titleResource = R.string.ai_purify) {
                    setMessage(e.localizedMessage ?: e.toString())
                    okButton()
                }
            }
        }
    }

    override fun onClickAiPurifyChapter() {
        showAiPurifyChapterRangeDialog()
    }

    private fun showAiPurifyChapterRangeDialog() {
        if (ReadBook.chapterSize <= 0) {
            toastOnUi("当前书籍没有可采样章节")
            return
        }
        val labels = arrayOf(
            getString(R.string.ai_purify_sample_current_chapter),
            getString(R.string.ai_purify_sample_custom_range)
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.ai_purify_chapter_sample_range)
            .setItems(labels) { _, which ->
                if (which == 0) {
                    startAiPurifyChapterRange(ReadBook.durChapterIndex, ReadBook.durChapterIndex)
                } else {
                    showAiPurifyCustomChapterRangeDialog()
                }
            }
            .show()
    }

    private fun showAiPurifyCustomChapterRangeDialog() {
        val total = ReadBook.chapterSize
        val limit = AiConfig.purifyChapterSampleLimit
        val currentChapter = (ReadBook.durChapterIndex + 1).coerceIn(1, total.coerceAtLeast(1))
        val defaultEnd = (currentChapter + limit - 1).coerceAtMost(total)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 0)
            addView(TextView(this@ReadBookActivity).apply {
                text = getString(R.string.ai_purify_sample_range_hint, total, limit)
                setTextColor(ContextCompat.getColor(this@ReadBookActivity, R.color.ng_on_surface_variant))
                textSize = 13f
            })
        }
        val startEdit = createAiPurifyRangeEditText(
            label = getString(R.string.ai_purify_sample_range_start),
            value = currentChapter
        ).also { content.addView(it.first) }.second
        val endEdit = createAiPurifyRangeEditText(
            label = getString(R.string.ai_purify_sample_range_end),
            value = defaultEnd
        ).also { content.addView(it.first) }.second
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.ai_purify_sample_custom_range)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val start = startEdit.text?.toString()?.toIntOrNull()
                val end = endEdit.text?.toString()?.toIntOrNull()
                when {
                    start == null || end == null || start > end ->
                        toastOnUi(getString(R.string.ai_purify_sample_range_invalid))
                    start < 1 || end > total ->
                        toastOnUi(getString(R.string.ai_purify_sample_range_out_of_bounds, total))
                    end - start + 1 > limit ->
                        toastOnUi(getString(R.string.ai_purify_sample_range_exceeded, limit))
                    else -> {
                        dialog.dismiss()
                        startAiPurifyChapterRange(start - 1, end - 1)
                    }
                }
            }
        }
        dialog.show()
    }

    private fun createAiPurifyRangeEditText(label: String, value: Int): Pair<LinearLayout, EditText> {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
            gravity = Gravity.CENTER
            setText(value.toString())
            setSelection(0, text?.length ?: 0)
            textSize = 16f
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 10.dpToPx(), 0, 0)
            addView(TextView(this@ReadBookActivity).apply {
                text = label
                setTextColor(ContextCompat.getColor(this@ReadBookActivity, R.color.ng_on_surface))
                textSize = 15f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(editText, LinearLayout.LayoutParams(96.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return row to editText
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
        val waitDialog = WaitDialog(this).apply {
            setText(
                if (sampleCount == 1) {
                    getString(R.string.ai_purify_chapter)
                } else {
                    getString(R.string.ai_purify_chapter_sampling, sampleCount)
                }
            )
            setOnCancelListener {
                aiPurifyJob?.cancel()
            }
            show()
        }
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
                waitDialog.dismiss()
                if (failures.isNotEmpty()) {
                    toastOnUi("已跳过 ${failures.size} 章失败结果，可重试")
                }
                when {
                    candidates.isEmpty() -> toastOnUi("AI 未生成可应用的净化规则")
                    sampleCount == 1 && AiConfig.purifyChapterAutoApply ->
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
                waitDialog.dismiss()
                alert(titleResource = R.string.ai_purify_chapter) {
                    setMessage(e.localizedMessage ?: e.toString())
                    okButton()
                }
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
        val view = layoutInflater.inflate(R.layout.dialog_ai_purify_chapter_confirm, null)
        view.findViewById<TextView>(R.id.tv_chapter_original_count).text =
            originalCharCount.toString()
        view.findViewById<TextView>(R.id.tv_chapter_cleaned_count).text =
            cleanedCharCount.toString()
        view.findViewById<TextView>(R.id.tv_chapter_rule_count).text = candidates.size.toString()
        view.findViewById<TextView>(R.id.tv_chapter_elapsed).text = formatAiPurifyElapsed(elapsedMs)
        view.findViewById<TextView>(R.id.tv_chapter_model).text = model
        val selectedIndexes = candidates.indices.toMutableSet()
        val ruleCountView = view.findViewById<TextView>(R.id.tv_chapter_rule_count)
        val visibleRuleContentHeaderWidth = (
            resources.displayMetrics.widthPixels -
                48.dpToPx() -
                40.dpToPx() -
                8.dpToPx() -
                36.dpToPx() -
                42.dpToPx() -
                48.dpToPx()
            ).coerceAtLeast(120.dpToPx())
        fun updateSelectionState() {
            ruleCountView.text = "${selectedIndexes.size}/${candidates.size}"
        }
        fun selectionActionText(): String {
            return getString(
                if (selectedIndexes.size == candidates.size) {
                    R.string.revert_selection
                } else {
                    R.string.select_all
                }
            )
        }
        val ruleTable = view.findViewById<LinearLayout>(R.id.layout_chapter_rule_table)
        lateinit var bindRuleTable: () -> Unit
        val toggleRuleSelection = {
            if (selectedIndexes.size == candidates.size) {
                selectedIndexes.clear()
            } else {
                selectedIndexes.clear()
                selectedIndexes.addAll(candidates.indices)
            }
            bindRuleTable()
            updateSelectionState()
        }
        bindRuleTable = {
            bindAiPurifyChapterRuleTable(
                ruleTable = ruleTable,
                candidates = candidates,
                selectedIndexes = selectedIndexes,
                selectionActionText = selectionActionText(),
                visibleContentHeaderWidth = visibleRuleContentHeaderWidth,
                onSelectionAction = toggleRuleSelection,
                onSelectionChanged = {
                    bindRuleTable()
                    updateSelectionState()
                }
            )
        }
        bindRuleTable()
        updateSelectionState()
        view.prepareAiPurifyDialogSize(R.id.scroll_ai_purify_chapter_content)
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .create()
        view.findViewById<View>(R.id.btn_retry).setOnClickListener {
            dialog.dismiss()
            startAiPurifyChapterRange(sampleStartIndex, sampleEndIndex)
        }
        view.findViewById<View>(R.id.btn_apply).setOnClickListener {
            val selectedCandidates = candidates.filterIndexed { index, _ -> index in selectedIndexes }
            if (selectedCandidates.isEmpty()) {
                toastOnUi("未选择净化规则")
                return@setOnClickListener
            }
            dialog.dismiss()
            applyAiPurifyRuleCandidates(selectedCandidates)
        }
        view.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.setOnShowListener {
            dialog.window?.run {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                val width = resources.displayMetrics.widthPixels - 48.dpToPx()
                setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
        dialog.show()
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

    private fun bindAiPurifyChapterRuleTable(
        ruleTable: LinearLayout,
        candidates: List<AiPurifyRuleCandidate>,
        selectedIndexes: MutableSet<Int>,
        selectionActionText: String,
        visibleContentHeaderWidth: Int,
        onSelectionAction: () -> Unit,
        onSelectionChanged: () -> Unit
    ) {
        ruleTable.removeAllViews()
        ruleTable.addView(
            createAiPurifyChapterRuleTableRow(
                checked = null,
                hitCount = getString(R.string.ai_purify_rule_column_hit_count),
                type = getString(R.string.ai_purify_rule_column_type),
                content = getString(R.string.ai_purify_rule_column_content),
                isHeader = true,
                applyHeaderText = selectionActionText,
                contentHeaderWidthPx = visibleContentHeaderWidth,
                onApplyHeaderClick = onSelectionAction
            )
        )
        candidates.forEachIndexed { index, candidate ->
            val checked = index in selectedIndexes
            ruleTable.addView(
                createAiPurifyChapterRuleTableRow(
                    checked = checked,
                    hitCount = getString(
                        R.string.ai_purify_rule_hit_count_value,
                        candidate.evidenceLabels.size
                    ),
                    type = candidate.aiPurifyRuleTypeLabel(),
                    content = candidate.aiPurifyRuleContentSummary(),
                    isHeader = false,
                    onCheckedChanged = { isChecked ->
                        if (isChecked) {
                            selectedIndexes.add(index)
                        } else {
                            selectedIndexes.remove(index)
                        }
                        onSelectionChanged()
                    }
                )
            )
        }
    }

    private fun createAiPurifyChapterRuleTableRow(
        checked: Boolean?,
        hitCount: String,
        type: String,
        content: String,
        isHeader: Boolean,
        applyHeaderText: String? = null,
        contentHeaderWidthPx: Int? = null,
        onApplyHeaderClick: (() -> Unit)? = null,
        onCheckedChanged: ((Boolean) -> Unit)? = null
    ): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 1.dpToPx(), 0, 1.dpToPx())
            addView(
                if (checked == null) {
                    createAiPurifyTableText(
                        text = applyHeaderText ?: getString(R.string.ai_purify_rule_column_apply),
                        widthDp = 36,
                        isHeader = isHeader,
                        gravityValue = Gravity.CENTER
                    ).apply {
                        if (onApplyHeaderClick != null) {
                            setTextColor(
                                ContextCompat.getColor(
                                    this@ReadBookActivity,
                                    R.color.ng_error
                                )
                            )
                            isClickable = true
                            isFocusable = true
                            setOnClickListener { onApplyHeaderClick.invoke() }
                        }
                    }
                } else {
                    CheckBox(this@ReadBookActivity).apply {
                        isChecked = checked
                        gravity = Gravity.CENTER
                        buttonTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this@ReadBookActivity, R.color.ng_error)
                        )
                        setOnCheckedChangeListener { _, value ->
                            onCheckedChanged?.invoke(value)
                        }
                        minWidth = 0
                        minHeight = 0
                        minimumWidth = 0
                        minimumHeight = 0
                        setPadding(0, 0, 0, 0)
                        scaleX = 0.82f
                        scaleY = 0.82f
                        layoutParams = LinearLayout.LayoutParams(36.dpToPx(), 32.dpToPx())
                    }
                }
            )
            addView(
                createAiPurifyTableText(
                    hitCount,
                    42,
                    isHeader = isHeader,
                    gravityValue = Gravity.CENTER
                )
            )
            addView(
                createAiPurifyTableText(
                    type,
                    48,
                    isHeader = isHeader,
                    gravityValue = Gravity.CENTER
                )
            )
            addView(
                createAiPurifyTableText(
                    content,
                    360,
                    isHeader = isHeader,
                    gravityValue = if (isHeader) Gravity.CENTER else Gravity.CENTER_VERTICAL,
                    fixedWidthPx = contentHeaderWidthPx
                )
            )
        }
    }

    private fun createAiPurifyTableText(
        text: String,
        widthDp: Int,
        weight: Float = 0f,
        isHeader: Boolean,
        gravityValue: Int,
        fixedWidthPx: Int? = null
    ): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(
                ContextCompat.getColor(
                    this@ReadBookActivity,
                    if (isHeader) R.color.ng_on_surface_variant else R.color.ng_on_surface
                )
            )
            textSize = if (isHeader) 11f else 12f
            typeface = if (isHeader) android.graphics.Typeface.DEFAULT_BOLD else null
            gravity = gravityValue
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxLines = 1
            setLineSpacing(1.dpToPx().toFloat(), 1.0f)
            setPadding(2.dpToPx(), 6.dpToPx(), 2.dpToPx(), 6.dpToPx())
            minHeight = 30.dpToPx()
            layoutParams = if (weight > 0f) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            } else if (fixedWidthPx != null) {
                LinearLayout.LayoutParams(fixedWidthPx, LinearLayout.LayoutParams.WRAP_CONTENT)
            } else {
                LinearLayout.LayoutParams(widthDp.dpToPx(), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
        }
    }

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
            upMenu()
            binding.readMenu.upBookView()
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

    /**
     * 内容加载完成
     */
    override fun contentLoadFinish() {
        if (intent.getBooleanExtra("readAloud", false)) {
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

    /**
     * 页面改变
     */
    override fun pageChanged() {
        pageChanged = true
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
    override fun showMenuBar() {
        binding.readMenu.runMenuIn()
    }

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
            BaseReadAloudService.isRun -> showReadAloudDialog()
            isAutoPage -> showDialogFragment<AutoReadDialog>()
            isShowingSearchResult -> binding.searchMenu.runMenuIn()
            else -> binding.readMenu.runMenuIn()
        }
    }

    /**
     * 显示朗读菜单
     */
    override fun showReadAloudDialog() {
        showDialogFragment<ReadAloudDialog>()
    }

    /**
     * 自动翻页
     */
    override fun autoPage() {
        ReadAloud.stop(this)
        if (isAutoPage) {
            autoPageStop()
        } else {
            binding.readView.autoPager.start()
            binding.readMenu.setAutoPage(true)
            screenTimeOut = -1L
            screenOffTimerStart()
        }
    }

    override fun autoPageStop() {
        if (isAutoPage) {
            binding.readView.autoPager.stop()
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
        ReadBook.book?.let {
            tocActivity.launch(it.bookUrl)
        }
    }

    /**
     * 打开搜索界面
     */
    override fun openSearchActivity(searchWord: String?) {
        val book = ReadBook.book ?: return
        searchContentActivity.launch {
            putExtra("bookUrl", book.bookUrl)
            putExtra("searchWord", searchWord ?: viewModel.searchContentQuery)
            putExtra("searchResultIndex", viewModel.searchResultIndex)
            viewModel.searchResultList?.first()?.let {
                if (it.query == viewModel.searchContentQuery) {
                    IntentData.put("searchResultList", viewModel.searchResultList)
                }
            }
        }
    }

    /**
     * 禁用书源
     */
    override fun disableSource() {
        viewModel.disableSource()
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

    override fun showSearchSetting() {
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

    /* 恢复到 全文搜索/进度条跳转前的位置 */
    private fun restoreLastBookProcess() {
        if (confirmRestoreProcess == true) {
            ReadBook.restoreLastBookProgress()
        } else if (confirmRestoreProcess == null) {
            alert(R.string.draw) {
                setMessage(R.string.restore_last_book_process)
                yesButton {
                    confirmRestoreProcess = true
                    ReadBook.restoreLastBookProgress() //恢复启动全文搜索前的进度
                }
                noButton {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                }
                onCancelled {
                    ReadBook.lastBookProgress = null
                    confirmRestoreProcess = false
                }
            }
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
        alert(R.string.chapter_pay) {
            setMessage(chapter.title)
            yesButton {
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
            }
            noButton()
        }
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


    /**
     * 朗读按钮
     */
    override fun onClickReadAloud() {
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

            BaseReadAloudService.pause -> {
                val scrollPageAnim = ReadBook.pageAnim() == 3
                if (scrollPageAnim && pageChanged) {
                    pageChanged = false
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
                    ReadAloud.resume(this)
                }
            }

            else -> ReadAloud.pause(this)
        }
    }

    override fun showHelp() {
        showHelp("readMenuHelp")
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
                        selectImageDir.launch {
                            value = src
                        }
                    } else {
                        viewModel.saveImage(src, path.toUri())
                    }
                }

                "menu" -> showActionMenu()
                "selectFolder" -> selectImageDir.launch()
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
            TEXT_COLOR -> {
                setCurTextColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            TEXT_ACCENT_COLOR -> {
                setCurTextAccentColor(color)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6, 9, 11))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

            BG_COLOR -> {
                setCurBg(0, "#${color.hexString}")
                postEvent(EventBus.UP_CONFIG, arrayListOf(1))
                if (AppConfig.readBarStyleFollowPage) {
                    postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
                }
            }

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
        alert(R.string.get_book_progress) {
            setMessage(R.string.current_progress_exceeds_cloud)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
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
    }

    override fun onMenuHide() {
        binding.readView.autoPager.resume()
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
        syncDialog = alert(R.string.get_book_progress) {
            setMessage(R.string.cloud_progress_exceeds_current)
            okButton {
                ReadBook.setProgress(progress)
            }
            noButton()
        }
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
            alert(title = getString(R.string.add_to_bookshelf)) {
                setMessage(getString(R.string.check_add_bookshelf, book.name))
                okButton {
                    ReadBook.book?.removeType(BookType.notShelf)
                    ReadBook.book?.save()
                    SourceCallBack.callBackBook(SourceCallBack.ADD_BOOK_SHELF, ReadBook.bookSource, ReadBook.book)
                    ReadBook.inBookshelf = true
                    setResult(RESULT_OK)
                }
                noButton {
                    callBackBookEnd()
                    viewModel.removeFromBookshelf { super.finish() }
                }
            }
        }
    }

    private fun callBackBookEnd() {
        SourceCallBack.callBackBook(SourceCallBack.END_READ, ReadBook.bookSource, ReadBook.book, ReadBook.curTextChapter?.chapter)
    }

    override fun onDestroy() {
        super.onDestroy()
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
        observeEvent<Boolean>(EventBus.MEDIA_BUTTON) {
            if (it) {
                onClickReadAloud()
            } else {
                ReadBook.readAloud(!BaseReadAloudService.pause)
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
            if (it == Status.STOP || it == Status.PAUSE) {
                ReadBook.curTextChapter?.let { textChapter ->
                    val page = textChapter.getPageByReadPos(ReadBook.durChapterPos)
                    if (page != null) {
                        page.removePageAloudSpan()
                        readView.upContent(resetPageOffset = false)
                    }
                }
            }
        }
        observeEventSticky<Int>(EventBus.TTS_PROGRESS) { chapterStart ->
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
            readMenu.upBrightnessState()
        }
        observeEvent<List<SearchResult>>(EventBus.SEARCH_RESULT) {
            viewModel.searchResultList = it
        }
        observeEvent<Boolean>(EventBus.UPDATE_READ_ACTION_BAR) {
            readMenu.reset()
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

    companion object {
        const val RESULT_DELETED = 100
    }

}
