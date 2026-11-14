package io.legado.app.ui.book.manga

import android.content.Intent
import android.os.Bundle
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.viewModels
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import com.bumptech.glide.Glide
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookProgress
import io.legado.app.data.entities.BookSource
import io.legado.app.help.book.isImage
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.removeType
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.getSourceType
import io.legado.app.help.storage.Backup
import io.legado.app.model.ReadManga
import io.legado.app.receiver.NetworkChangedListener
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.manga.config.MangaFooterConfig
import io.legado.app.ui.book.manga.config.withContent
import io.legado.app.ui.book.manga.config.MangaReadingStyle
import io.legado.app.ui.book.manga.config.mangaScrollIntervals
import io.legado.app.ui.book.manga.entities.BaseMangaPage
import io.legado.app.ui.book.manga.entities.MangaPage
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.read.ReadCatalogStyle
import io.legado.app.ui.book.read.config.showReadConfirmDialog
import io.legado.app.ui.book.read.executeReadSourcePayAction
import io.legado.app.ui.book.read.resolveReadSourceActionVisibility
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.JsSourceEditActivity
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.putPrefString
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openUrl
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.toggleSystemBar
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 漫画 UI 使用 Compose；书籍业务仍由 ReadManga 和原 ViewModel 唯一负责。 */
class ReadMangaActivity : VMBaseActivity<ComposeActivityBinding, ReadMangaViewModel>(imageBg = false),
    ReadManga.Callback, ChangeBookSourceDialog.CallBack {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<ReadMangaViewModel>()
    override val bindNgToolbarMenu = false
    private val ui = MangaReaderState()
    private val networkChangedListener by lazy { NetworkChangedListener(this) }
    private var justInitData = false
    private var confirmation: ComponentDialog? = null
    private var confirmationVisible = false
    private val dialogs = mutableSetOf<Fragment>()
    private val readingStyleMutex = Mutex()
    private var contentJob: Job? = null
    private var lastVisiblePageKey: String? = null
    private var filterDirty = false
    private var footerDirty = false
    private var thresholdDirty = false
    private val dialogCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            if (f is DialogFragment) { dialogs.add(f); updateModalState() }
        }
        override fun onFragmentStopped(fm: FragmentManager, f: Fragment) {
            if (dialogs.remove(f)) updateModalState()
        }
    }
    private val bookInfoActivity = registerForActivityResult(StartActivityContract(BookInfoActivity::class.java)) {
        if (it.resultCode == RESULT_OK) { setResult(RESULT_DELETED); super.finish() }
        else ReadManga.loadOrUpContent()
    }
    private val sourceEditActivity = registerForActivityResult(StartActivityContract(BookSourceEditActivity::class.java)) {
        if (it.resultCode == RESULT_OK) viewModel.upBookSource(::updateSourceUi)
    }
    private val jsSourceEditActivity = registerForActivityResult(StartActivityContract(JsSourceEditActivity::class.java)) {
        if (it.resultCode == RESULT_OK) viewModel.upBookSource(::updateSourceUi)
    }
    internal val readingRecordBookKey: String?
        get() = viewModel.readingRecordBookKey ?: intent.getStringExtra("bookUrl")

    override fun onCreate(savedInstanceState: Bundle?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        super.onCreate(savedInstanceState)
    }
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        ui.catalogStyle = currentCatalogStyle()
        updateWindowBrightness(ui.filter.l)
        ui.panel = MangaPanel.entries.firstOrNull { it.name == savedInstanceState?.getString("mangaPanel") } ?: MangaPanel.NONE
        ui.autoMode = MangaAutoMode.entries.firstOrNull { it.name == savedInstanceState?.getString("mangaAutoMode") } ?: MangaAutoMode.OFF
        ReadManga.register(this)
        supportFragmentManager.registerFragmentLifecycleCallbacks(dialogCallbacks, false)
        toggleSystemBar(ui.panel != MangaPanel.NONE)
        binding.composeView.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        binding.composeView.setContent {
            NgAppTheme(updateSystemBars = false) {
                MangaReaderScreen(ui, ::handleAction, ::changeSetting, ::setPanel, ::onPageVisible, ::skipToPage) {
                    ui.menuVisible = it
                    updateModalState()
                }
            }
        }
    }
    override fun observeLiveBus() {
        observeEvent<MangaFooterConfig>(EventBus.UP_MANGA_CONFIG) { ui.footer = it.copy() }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_CONTENT) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadManga.book?.let(viewModel::refreshContentDur)
            }
        }
        observeEvent<Boolean>(EventBus.REFRESH_BOOK_TOC) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                ReadManga.book?.let(viewModel::refreshChapterList)
            }
        }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("mangaPanel", ui.panel.name)
        outState.putString("mangaAutoMode", ui.autoMode.name)
        super.onSaveInstanceState(outState)
    }
    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        justInitData = true
        viewModel.initData(intent)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        setPanel(MangaPanel.NONE)
        ui.autoMode = MangaAutoMode.OFF
        showLoading()
        viewModel.initData(intent)
    }
    override fun upContent() {
        contentJob?.cancel()
        contentJob = lifecycleScope.launch {
            val data = withContext(IO) { ReadManga.mangaContents }
            // ReadManga constructs only MangaPage / ReaderLoading entries; retain its list identity.
            @Suppress("UNCHECKED_CAST")
            val pages = data.items as List<BaseMangaPage>
            val book = ReadManga.book
            val reposition = ui.loading || ui.bookUrl != book?.bookUrl.orEmpty()
            MangaScrollDiagnostics.event("CONTENT") {
                "reposition=$reposition current=${ReadManga.durChapterIndex}:${ReadManga.durChapterPos} " +
                    "pos=${data.pos} oldCount=${ui.pages.size} count=${pages.size} " +
                    "first=${pages.firstOrNull()?.let(::mangaPageKey)} last=${pages.lastOrNull()?.let(::mangaPageKey)}"
            }
            ui.bookUrl = book?.bookUrl.orEmpty()
            ui.bookName = book?.name.orEmpty()
            updateSourceUi()
            ui.bookStyle = book?.readConfig?.mangaReadingStyle != null
            ui.reading = readMangaStyle(book?.readConfig?.mangaReadingStyle)
            if (ui.autoMode != MangaAutoMode.OFF) ui.autoMode = if (ui.paged) MangaAutoMode.PAGE else MangaAutoMode.SCROLL
            ui.autoScrollLevel = getPrefInt(PreferKey.mangaAutoScrollLevel, 4).coerceIn(mangaScrollIntervals.indices)
            ui.chapterCount = ReadManga.simulatedChapterSize
            ui.chapterIndex = ReadManga.durChapterIndex
            ui.imageIndex = ReadManga.durChapterPos
            ui.imageCount = ReadManga.curMangaChapter?.imageCount ?: 0
            ui.chapterUrl = ReadManga.curMangaChapter?.chapter?.getAbsoluteURL().orEmpty()
            ui.chapterName = (pages.getOrNull(data.pos) as? MangaPage)?.mChapterName
                ?: ReadManga.curMangaChapter?.chapter?.title.orEmpty()
            ui.pages = pages
            if (reposition && data.curFinish) {
                ui.jumpTo(data.pos)
                ui.currentPage = data.items.getOrNull(data.pos) as? MangaPage
                ui.loading = false
                ui.error = null
            }
            if (data.curFinish) {
                ui.tailLoading = ReadManga.hasNextChapter && !data.nextFinish
                ui.tailRetry = false
                ui.tailMessage = when {
                    !ReadManga.hasNextChapter -> "暂无章节了！"
                    data.nextFinish -> ""
                    else -> "加载中"
                }
                justInitData = false
            }
        }
    }
    private fun onPageVisible(page: BaseMangaPage) {
        if (ui.loading) return
        MangaScrollDiagnostics.event("ACTIVE") {
            "candidate=${mangaPageKey(page)} current=${ReadManga.durChapterIndex}:${ReadManga.durChapterPos}"
        }
        when {
            page.chapterIndex > ReadManga.durChapterIndex -> ReadManga.moveToNextChapter()
            page.chapterIndex < ReadManga.durChapterIndex -> ReadManga.moveToPrevChapter()
            page is MangaPage -> {
                val visibleKey = "${ui.bookUrl}:${mangaPageKey(page)}"
                ReadManga.durChapterPos = page.index
                if (lastVisiblePageKey != visibleKey) {
                    lastVisiblePageKey = visibleKey
                    ReadManga.curPageChanged()
                }
                ui.currentPage = page
                ui.chapterName = page.mChapterName
                ui.chapterIndex = page.chapterIndex
                ui.imageIndex = page.index
                ui.imageCount = page.imageCount
                ui.chapterCount = page.chapterSize
                ui.chapterUrl = ReadManga.curMangaChapter?.chapter?.getAbsoluteURL().orEmpty()
            }
        }
    }
    fun skipToPage(index: Int) {
        val position = ui.pages.binarySearch {
            val chapter = it.chapterIndex - ReadManga.durChapterIndex
            if (chapter != 0) chapter else it.index - index
        }
        if (position < 0) return
        ReadManga.durChapterPos = index
        ui.imageIndex = index
        ui.currentPage = ui.pages[position] as? MangaPage
        ui.jumpTo(position)
    }
    override fun showLoading() { lifecycleScope.launch { ui.loading = true; ui.error = null } }
    override fun startLoad() { lifecycleScope.launch { ui.tailLoading = true; ui.tailRetry = false; ui.tailMessage = "加载中" } }
    override fun loadFail(msg: String, retry: Boolean) {
        lifecycleScope.launch {
            if (ui.loading) { ui.error = msg; ui.retryAllowed = retry }
            else { ui.tailLoading = false; ui.tailRetry = true; ui.tailMessage = "加载失败，点击重试" }
        }
    }
    override fun sureNewProgress(progress: BookProgress) {
        showConfirmation(getString(R.string.get_book_progress), getString(R.string.cloud_progress_exceeds_current),
            getString(R.string.yes), getString(R.string.no), { ReadManga.setProgress(progress) })
    }
    private fun setPanel(panel: MangaPanel) {
        persistDisplaySettings()
        if (panel == MangaPanel.MENU) updateSourceUi()
        ui.panel = panel
        toggleSystemBar(panel != MangaPanel.NONE || ui.menuVisible || ui.externalModal)
    }
    private fun persistDisplaySettings() {
        if (filterDirty) { AppConfig.mangaColorFilter = ui.filter.toJson(); filterDirty = false }
        if (footerDirty) { AppConfig.mangaFooterConfig = GSON.toJson(ui.footer); footerDirty = false }
        if (thresholdDirty) { AppConfig.mangaEInkThreshold = ui.threshold; thresholdDirty = false }
    }
    private fun handleAction(action: MangaAction) {
        when (action) {
            MangaAction.BACK -> finish()
            MangaAction.INFO -> {
                ui.externalModal = true
                setPanel(MangaPanel.NONE)
                ReadManga.book?.let { book -> bookInfoActivity.launch { putExtra("name", book.name); putExtra("author", book.author) } }
            }
            MangaAction.CHANGE_SOURCE -> {
                ui.externalModal = true
                setPanel(MangaPanel.NONE)
                ReadManga.book?.let { showDialogFragment(ChangeBookSourceDialog(it.name, it.author)) }
            }
            MangaAction.SOURCE_LOGIN -> ReadManga.bookSource?.let {
                startActivity<SourceLoginActivity> { putExtra("bookType", BookType.image) }
            }
            MangaAction.SOURCE_EDIT -> openSourceEditor()
            MangaAction.SOURCE_DISABLE -> { viewModel.setSourceEnabled(false); updateSourceUi() }
            MangaAction.SOURCE_ENABLE -> { viewModel.setSourceEnabled(true); updateSourceUi() }
            MangaAction.CHAPTER_PAY -> payCurrentChapter()
            MangaAction.CATALOG -> {
                if (ReadManga.book == null) return
                ui.externalModal = true
                setPanel(MangaPanel.NONE)
                val catalog = when (currentCatalogStyle()) {
                    ReadCatalogStyle.CARD -> MangaCatalogDialog()
                    ReadCatalogStyle.COMPACT_SIDE -> MangaCompactCatalogDialog()
                    ReadCatalogStyle.DENSE_LIST -> MangaDenseCatalogDialog()
                }
                catalog.show(supportFragmentManager, "mangaCatalog")
            }
            MangaAction.REFRESH -> { showLoading(); ReadManga.book?.let(viewModel::refreshContentDur) }
            MangaAction.PREVIOUS_CHAPTER -> ReadManga.moveToPrevChapter(true)
            MangaAction.NEXT_CHAPTER -> ReadManga.moveToNextChapter(true)
            MangaAction.RETRY -> { showLoading(); ReadManga.loadOrUpContent() }
            MangaAction.TAIL_RETRY -> { startLoad(); ReadManga.loadOrUpContent() }
            MangaAction.RESET_READING -> confirmResetReading()
            MangaAction.OPEN_WEB -> openChapterWeb()
            MangaAction.WEB_MODE -> showConfirmation(getString(R.string.open_fun), getString(R.string.use_browser_open),
                getString(R.string.yes), getString(R.string.no), { AppConfig.readUrlInBrowser = true }, { AppConfig.readUrlInBrowser = false })
        }
    }
    private fun updateSourceUi() {
        val source = ReadManga.bookSource
        val chapter = ReadManga.curMangaChapter?.chapter
        val visibility = resolveReadSourceActionVisibility(
            loginUrl = source?.loginUrl,
            loginUi = source?.loginUi,
            chapterIsVip = chapter?.isVip == true,
            chapterIsPay = chapter?.isPay == true,
        )
        ui.sourceName = source?.bookSourceName ?: getString(R.string.book_source)
        ui.sourceOrigin = ReadManga.book?.origin.orEmpty()
        ui.sourceAvailable = source != null && ReadManga.book?.isLocal == false
        ui.sourceHasLogin = visibility.showLogin
        ui.sourceEnabled = source?.enabled != false
        ui.chapterCanPay = visibility.showChapterPay
    }
    private fun currentCatalogStyle(): ReadCatalogStyle = ReadCatalogStyle.fromValue(
        getPrefString(PreferKey.readCatalogStyle, ReadCatalogStyle.CARD.value)
    )
    private fun openSourceEditor() {
        val sourceUrl = ReadManga.bookSource?.bookSourceUrl ?: return
        lifecycleScope.launch {
            val isJsSource = withContext(IO) { appDb.bookSourceDao.hasJsSource(sourceUrl) }
            if (ReadManga.bookSource?.bookSourceUrl != sourceUrl) return@launch
            if (isJsSource) jsSourceEditActivity.launch { putExtra("sourceUrl", sourceUrl) }
            else sourceEditActivity.launch { putExtra("sourceUrl", sourceUrl) }
        }
    }
    private fun payCurrentChapter() {
        val book = ReadManga.book ?: return
        if (book.isLocal) return
        val chapter = ReadManga.curMangaChapter?.chapter ?: return toastOnUi("no chapter")
        showConfirmation(getString(R.string.chapter_pay), chapter.title,
            getString(R.string.yes), getString(R.string.no), {
                executeReadSourcePayAction(this, book, chapter, BookType.image, { ReadManga.bookSource }) {
                    BookHelp.delContent(book, chapter)
                    viewModel.refreshChapterList(book)
                }
            })
    }
    private fun changeSetting(setting: MangaSetting, value: Int) {
        when (setting) {
            MangaSetting.MODE, MangaSetting.LAYOUT, MangaSetting.DIRECTION, MangaSetting.EFFECT,
            MangaSetting.BOOK_STYLE -> if (ui.loading || ReadManga.book?.bookUrl != ui.bookUrl) return
            else -> Unit
        }
        val enabled = value != 0
        when (setting) {
            MangaSetting.BRIGHTNESS, MangaSetting.RED, MangaSetting.GREEN, MangaSetting.BLUE, MangaSetting.ALPHA -> filterDirty = true
            MangaSetting.THRESHOLD -> thresholdDirty = true
            MangaSetting.FOOTER, MangaSetting.FOOTER_LEFT, MangaSetting.FOOTER_MIDDLE,
            MangaSetting.FOOTER_RIGHT -> footerDirty = true
            else -> Unit
        }
        when (setting) {
            MangaSetting.MODE -> {
                ui.autoMode = MangaAutoMode.OFF
                updateMangaStyle(ui.reading.copy(mode = value))
            }
            MangaSetting.LAYOUT -> updateMangaStyle(ui.reading.copy(layout = value))
            MangaSetting.DIRECTION -> updateMangaStyle(ui.reading.copy(rtl = enabled))
            MangaSetting.EFFECT -> updateMangaStyle(ui.reading.copy(effect = value))
            MangaSetting.BOOK_STYLE -> {
                ui.bookStyle = enabled
                saveBookMangaStyle(if (enabled) GSON.toJson(ui.reading) else null)
                if (!enabled) updateMangaStyle(readMangaStyle(null))
            }
            MangaSetting.AUTO -> ui.autoMode = if (enabled) {
                if (ui.paged) MangaAutoMode.PAGE else MangaAutoMode.SCROLL
            } else MangaAutoMode.OFF
            MangaSetting.INTERVAL -> { ui.autoInterval = value.coerceIn(1, 9999); AppConfig.mangaAutoPageSpeed = ui.autoInterval }
            MangaSetting.SPEED -> {
                ui.autoScrollLevel = value.coerceIn(mangaScrollIntervals.indices)
                putPrefInt(PreferKey.mangaAutoScrollLevel, ui.autoScrollLevel)
            }
            MangaSetting.SCALE -> { ui.scaleEnabled = enabled; AppConfig.disableMangaScale = !enabled }
            MangaSetting.CLICK -> { ui.clickTurn = enabled; AppConfig.disableClickScroll = !enabled }
            MangaSetting.PRELOAD -> { ui.preload = value; AppConfig.mangaPreDownloadNum = value }
            MangaSetting.CATALOG_STYLE -> {
                ui.catalogStyle = ReadCatalogStyle.entries[value]
                putPrefString(PreferKey.readCatalogStyle, ui.catalogStyle.value)
            }
            MangaSetting.HINTS -> { ui.chapterHints = enabled; AppConfig.hideMangaTitle = !enabled; showLoading(); ReadManga.loadContent() }
            MangaSetting.COLOR -> {
                ui.colorMode = MangaColorMode.entries[value]
                AppConfig.enableMangaGray = ui.colorMode == MangaColorMode.GRAY
                AppConfig.enableMangaEInk = ui.colorMode == MangaColorMode.INK
            }
            MangaSetting.THRESHOLD -> ui.threshold = value
            MangaSetting.BRIGHTNESS -> { ui.filter = ui.filter.copy(l = value); updateWindowBrightness(value) }
            MangaSetting.RED -> ui.filter = ui.filter.copy(r = value)
            MangaSetting.GREEN -> ui.filter = ui.filter.copy(g = value)
            MangaSetting.BLUE -> ui.filter = ui.filter.copy(b = value)
            MangaSetting.ALPHA -> ui.filter = ui.filter.copy(a = value)
            MangaSetting.FOOTER -> ui.footer = ui.footer.copy(hideFooter = !enabled)
            MangaSetting.FOOTER_LEFT -> ui.footer = ui.footer.withContent(0, value)
            MangaSetting.FOOTER_MIDDLE -> ui.footer = ui.footer.withContent(1, value)
            MangaSetting.FOOTER_RIGHT -> ui.footer = ui.footer.withContent(2, value)
        }
    }
    private fun readMangaStyle(bookStyle: String?): MangaReadingStyle =
        GSON.fromJsonObject<MangaReadingStyle>(bookStyle ?: getPrefString(PreferKey.mangaReadingStyle))
            .getOrNull()?.normalized() ?: MangaReadingStyle()

    private fun confirmResetReading() {
        if (ui.loading || ReadManga.book?.bookUrl != ui.bookUrl) return
        val independent = ui.bookStyle
        val bookUrl = ui.bookUrl
        val message = if (independent) "清除本书独立设置，恢复漫画默认设置。"
            else "恢复初始阅读设置，未开启独立设置的漫画也会更新。"
        showConfirmation("恢复默认", message, "恢复", "取消", {
            if (ui.loading || ui.bookUrl != bookUrl || ReadManga.book?.bookUrl != bookUrl ||
                ui.bookStyle != independent) return@showConfirmation
            ui.autoMode = MangaAutoMode.OFF
            if (independent) {
                ui.bookStyle = false
                saveBookMangaStyle(null)
                updateMangaStyle(readMangaStyle(null))
            } else {
                ui.autoInterval = 3
                AppConfig.mangaAutoPageSpeed = ui.autoInterval
                ui.autoScrollLevel = 4
                putPrefInt(PreferKey.mangaAutoScrollLevel, ui.autoScrollLevel)
                updateMangaStyle(MangaReadingStyle())
            }
        })
    }

    private fun updateMangaStyle(style: MangaReadingStyle) {
        ui.appliedTurnRevision = ui.turnRevision
        val normalized = style.normalized()
        if (ui.reading.mode != normalized.mode) ui.autoMode = MangaAutoMode.OFF
        ui.reading = normalized
        ui.scale = 1f
        if (ui.bookStyle) saveBookMangaStyle(GSON.toJson(ui.reading))
        else putPrefString(PreferKey.mangaReadingStyle, GSON.toJson(ui.reading))
        skipToPage(ReadManga.durChapterPos)
    }

    private fun saveBookMangaStyle(style: String?) {
        val book = ReadManga.book ?: return
        book.config.mangaReadingStyle = style
        lifecycleScope.launch(IO) { readingStyleMutex.withLock {
            // Merge only this field; concurrent book edits own the rest of ReadConfig.
            appDb.runInTransaction {
                val config = appDb.bookDao.getBook(book.bookUrl)?.config ?: Book.ReadConfig()
                config.mangaReadingStyle = book.config.mangaReadingStyle
                appDb.bookDao.updateReadStyleConfig(book.bookUrl, config)
            }
        } }
    }
    fun updateWindowBrightness(brightness: Int) {
        val attributes = window.attributes
        attributes.screenBrightness = if (brightness == 0) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            else (brightness / 255f).coerceIn(0f, 1f)
        window.attributes = attributes
        window.decorView.postInvalidate()
    }
    private fun openChapterWeb() {
        if (ReadManga.book?.isLocal == true || ui.chapterUrl.isBlank()) return
        val url = ui.chapterUrl
        if (AppConfig.readUrlInBrowser) openUrl(url.substringBefore(",{"))
        else startActivity<WebViewActivity> {
            val source = ReadManga.bookSource
            putExtra("title", ui.currentPage?.mChapterName)
            putExtra("url", url)
            putExtra("sourceOrigin", source?.bookSourceUrl)
            putExtra("sourceName", source?.bookSourceName)
            putExtra("sourceType", source?.getSourceType())
        }
    }
    override val oldBook: Book? get() = ReadManga.book
    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        if (book.isImage) { showLoading(); viewModel.changeTo(book, toc) } else toastOnUi("所选择的源不是漫画源")
    }
    private fun updateModalState() {
        ui.externalModal = confirmationVisible || dialogs.isNotEmpty()
        toggleSystemBar(ui.panel != MangaPanel.NONE || ui.menuVisible || ui.externalModal)
    }
    private fun showConfirmation(title: String, message: String, confirmLabel: String, cancelLabel: String,
        onConfirm: () -> Unit, onCancel: () -> Unit = {}) {
        confirmation?.dismiss()
        confirmationVisible = true
        updateModalState()
        fun close() { confirmationVisible = false; updateModalState() }
        confirmation = showReadConfirmDialog(this, title, message, confirmLabel, cancelLabel,
            onConfirm = { close(); onConfirm() }, onCancel = { close(); onCancel() }, onOutsideDismiss = ::close,
            themeSnapshot = NgThemeResolver.resolve(this))
    }
    override fun finish() {
        val book = ReadManga.book ?: return super.finish()
        if (ReadManga.inBookshelf) return super.finish()
        if (!AppConfig.showAddToShelfAlert) viewModel.removeFromBookshelf { super.finish() }
        else showConfirmation(getString(R.string.add_to_bookshelf), getString(R.string.check_add_bookshelf, book.name),
            getString(R.string.yes), getString(R.string.no), {
                ReadManga.book?.removeType(BookType.notShelf)
                ReadManga.book?.save()
                ReadManga.inBookshelf = true
                setResult(RESULT_OK)
            }, { viewModel.removeFromBookshelf { super.finish() } })
    }
    override fun onResume() {
        super.onResume()
        ui.catalogStyle = currentCatalogStyle()
        ui.active = true
        updateModalState()
        networkChangedListener.register()
        networkChangedListener.onNetworkChanged = {
            if (AppConfig.syncBookProgressPlus && NetworkUtils.isAvailable() && !justInitData && ReadManga.inBookshelf) {
                ReadManga.syncProgress({ progress -> sureNewProgress(progress) })
            }
        }
    }
    override fun onPause() {
        ui.active = false
        persistDisplaySettings()
        super.onPause()
        if (ReadManga.inBookshelf) {
            ReadManga.saveRead()
            if (!BuildConfig.DEBUG) { if (AppConfig.syncBookProgressPlus) ReadManga.syncProgress() else ReadManga.uploadProgress() }
        }
        if (!BuildConfig.DEBUG) Backup.autoBack(this)
        ReadManga.cancelPreDownloadTask()
        networkChangedListener.unRegister()
    }
    override fun onDestroy() {
        confirmation?.dismiss()
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(dialogCallbacks)
        ReadManga.unregister(this)
        super.onDestroy()
    }
    override fun onLowMemory() { super.onLowMemory(); Glide.get(this).clearMemory() }
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_VOLUME_UP -> { ui.turn(-1); true }
        KeyEvent.KEYCODE_VOLUME_DOWN -> { ui.turn(1); true }
        KeyEvent.KEYCODE_MENU -> { if (event?.repeatCount == 0) setPanel(if (ui.panel == MangaPanel.NONE) MangaPanel.MENU else MangaPanel.NONE); true }
        else -> super.onKeyDown(keyCode, event)
    }
}
