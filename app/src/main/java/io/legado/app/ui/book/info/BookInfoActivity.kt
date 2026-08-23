package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewGroup
import io.legado.app.ui.widget.text.ScrollTextView
import android.view.textclassifier.TextClassifier
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.source.webCacheObject
import io.legado.app.help.http.BookSourceCookieStore
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AiSkillRegistry
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.addType
import io.legado.app.help.book.getRemoteUrl
import io.legado.app.help.book.isAudio
import io.legado.app.help.book.isImage
import io.legado.app.help.book.isLocal
import io.legado.app.help.book.isLocalTxt
import io.legado.app.help.book.isVideo
import io.legado.app.help.book.isWebFile
import io.legado.app.help.book.removeType
import io.legado.app.help.book.supportsReadAloud
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebJsExtensions
import io.legado.app.help.webView.WebJsExtensions.Companion.getInjectionString
import io.legado.app.help.webView.WebJsExtensions.Companion.nameCache
import io.legado.app.help.webView.WebJsExtensions.Companion.nameJava
import io.legado.app.help.webView.WebJsExtensions.Companion.nameSource
import io.legado.app.help.webView.WebViewPool
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.CacheBook
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.character.BookCharacterActivity
import io.legado.app.ui.book.character.BookCharacterLabels
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.read.aloud.ReadAloudLauncher
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.dpToPx
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openFileUri
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setHtml
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BookInfoActivity :
    VMBaseActivity<ComposeActivityBinding, BookInfoViewModel>(toolBarTheme = Theme.Dark),
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback {

    private val tocActivityResult = registerForActivityResult(TocActivityResult()) {
        it?.let {
            viewModel.getBook(false)?.let { book ->
                lifecycleScope.launch {
                    withContext(IO) {
                        val durChapterIndex = it[0] as Int
                        val durChapterPos = it[1] as Int
                        val durVolumeIndex = it[3] as Int
                        val chapterInVolumeIndex = it[4] as Int
                        book.durChapterIndex = durChapterIndex
                        book.durChapterPos = durChapterPos
                        chapterChanged = it[2] as Boolean
                        book.durVolumeIndex = durVolumeIndex
                        book.chapterInVolumeIndex = chapterInVolumeIndex
                        appDb.bookDao.update(book)
                    }
                    startReadActivity(book)
                }
            }
        } ?: let {
            if (!viewModel.inBookshelf) {
                viewModel.delBook() //进目录会保存book，此时退出目录触发的book删除，不通知书源回调
            }
        }
    }
    private val localBookTreeSelect = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
        }
    }
    private val readBookResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.upBook(intent)
        when (it.resultCode) {
            RESULT_OK -> {
                viewModel.inBookshelf = true
                updateInBookshelfState()
            }

            RESULT_DELETED -> {
                setResult(RESULT_OK)
                finish()
            }
        }
    }
    private val infoEditResult = registerForActivityResult(
        StartActivityContract(BookInfoEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            viewModel.upEditBook()
        }
    }
    private val editSourceResult = registerForActivityResult(
        StartActivityContract(BookSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_CANCELED) {
            return@registerForActivityResult
        }
        book?.let { book ->
            viewModel.bookSource = appDb.bookSourceDao.getBookSource(book.origin)?.also { source ->
                viewModel.hasCustomBtn = source.customButton
            }
            viewModel.refreshBook(book)
        }
    }
    private var chapterChanged = false
    private val waitDialog by lazy { WaitDialog(this) }
    private var characterPreviewJob: Job? = null
    private var otherWorksRawBooks = emptyList<SearchBook>()
    private var otherWorksGroupCounts = emptyMap<String, Int>()
    private var otherWorksGroupPrimaryBookUrls = emptyMap<String, String>()
    private val expandedOtherWorksKeys = linkedSetOf<String>()
    private val book get() = viewModel.getBook(false)

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()
    override val bindNgToolbarMenu: Boolean = false
    private var uiState by mutableStateOf(
        BookInfoUiState(
            tocText = "",
            deleteAlertEnabled = LocalConfig.bookInfoDeleteAlert,
        ),
        referentialEqualityPolicy(),
    )
    private var pendingWebFileSuccess: ((Book) -> Unit)? = null
    private var pendingArchiveUri: Uri? = null
    private var pendingUnsupportedWebFile: BookInfoViewModel.WebFile? = null
    private var initIntroView = false
    private var cacheProgressJob: Job? = null
    private var cacheProgressBookUrl: String? = null
    private var cacheProgressCached = 0
    private var cacheProgressTotal = 0
    private val introContainer by lazy {
        FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
    }
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

    private val imgAvailableWidth: Int
        get() {
        val textView = introTextView
        val measured = textView.width - textView.paddingLeft - textView.paddingRight - 8.dpToPx()
        return measured.takeIf { it > 0 }
            ?: (resources.displayMetrics.widthPixels - 64.dpToPx()).coerceAtLeast(1)
        }
    private var initGetter = false
    private val glideImageGetter by lazy {
        initGetter = true
        GlideImageGetter(
            this,
            introTextView,
            lifecycle,
            imgAvailableWidth,
            viewModel.bookSource?.bookSourceUrl
        )
    }

    private val textViewTagHandler by lazy {
        TextViewTagHandler(object : TextViewTagHandler.OnButtonClickListener {
            override fun onButtonClick(name: String, click: String) {
                viewModel.onButtonClick(this@BookInfoActivity, "info button $name" , click)
            }
        })
    }

    @SuppressLint("PrivateResource")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        uiState = uiState.copy(
            tocText = getString(R.string.toc_s, getString(R.string.loading)),
            autoLoadOtherWorks = getPrefBoolean(PreferKey.autoLoadBookOtherWorks),
        )
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                BookInfoScreen(
                    state = uiState,
                    introView = introContainer,
                    onEvent = ::handleUiEvent,
                )
            }
        }
        viewModel.bookData.observe(this) { showBook(it) }
        viewModel.chapterListData.observe(this) {
            upLoading(false, it)
            upCacheProgress(viewModel.getBook(false), it)
        }
        viewModel.otherWorksData.observe(this) { showOtherWorks(it) }
        viewModel.otherWorksLoadingData.observe(this) {
            uiState = uiState.copy(otherWorksLoading = it)
        }
        viewModel.bookshelfChanged.observe(this) {
            updateOtherWorksUiItems()
        }
        viewModel.waitDialogData.observe(this) { upWaitDialogStatus(it) }
        viewModel.initData(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        uiState = uiState.copy(scrollResetToken = uiState.scrollResetToken + 1)
        viewModel.initData(intent)
    }

    private fun handleUiEvent(event: BookInfoUiEvent) {
        when (event) {
            BookInfoUiEvent.Back -> onBackPressedDispatcher.onBackPressed()
            BookInfoUiEvent.Refresh -> refreshBook()
            BookInfoUiEvent.CustomButton -> clickCustomButton()
            is BookInfoUiEvent.Menu -> handleBookInfoMenuAction(event.action)
            BookInfoUiEvent.CoverClick -> changeCover()
            BookInfoUiEvent.CoverLongClick -> showCoverPreview()
            BookInfoUiEvent.NameClick -> clickBookName(false)
            BookInfoUiEvent.NameLongClick -> clickBookName(true)
            BookInfoUiEvent.AuthorClick -> clickAuthor(false)
            BookInfoUiEvent.AuthorLongClick -> clickAuthor(true)
            is BookInfoUiEvent.TagClick -> clickTag(event.tag, false)
            is BookInfoUiEvent.TagLongClick -> clickTag(event.tag, true)
            BookInfoUiEvent.OriginClick -> editCurrentSource()
            BookInfoUiEvent.ChangeSource -> changeSource()
            BookInfoUiEvent.OpenToc -> openChapterListFromUi()
            BookInfoUiEvent.CacheBook -> viewModel.getBook()?.let(::startCacheBook)
            BookInfoUiEvent.CharacterAi -> viewModel.getBook()?.let(::openCharacterCardAiAssistant)
            BookInfoUiEvent.OpenCharacters -> viewModel.getBook()?.let { current ->
                openCharacterActivity(
                    current,
                    BookCharacterProfile.workKey(current.name, current.author),
                )
            }
            BookInfoUiEvent.RefreshOtherWorks -> viewModel.searchOtherWorks()
            is BookInfoUiEvent.OpenOtherWork -> showBookInfo(
                event.book.name,
                event.book.author,
                event.book.bookUrl,
            )
            is BookInfoUiEvent.ExpandOtherWork -> showAllSources(event.book)
            BookInfoUiEvent.Shelf -> clickShelf()
            BookInfoUiEvent.Listen -> clickListen()
            BookInfoUiEvent.Read -> clickRead()
            BookInfoUiEvent.DeleteDialogDismiss -> {
                uiState = uiState.copy(deleteDialogVisible = false)
            }
            is BookInfoUiEvent.DeleteOriginalChange -> {
                uiState = uiState.copy(deleteOriginal = event.checked)
            }
            BookInfoUiEvent.DeleteConfirm -> confirmDeleteBook()
            BookInfoUiEvent.FileDialogDismiss -> dismissFileDialog()
            is BookInfoUiEvent.FileDialogItemClick -> selectFileDialogItem(event.index)
            BookInfoUiEvent.UnsupportedFileOpen -> openUnsupportedWebFile()
        }
    }

    private fun handleBookInfoMenuAction(action: BookInfoMenuAction) {
        when (action) {
            BookInfoMenuAction.Edit -> viewModel.getBook()?.let {
                infoEditResult.launch { putExtra("bookUrl", it.bookUrl) }
            }
            BookInfoMenuAction.Refresh -> refreshBook()
            BookInfoMenuAction.Share -> shareBook()
            BookInfoMenuAction.Upload -> uploadBookFromMenu()
            BookInfoMenuAction.Login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                    putExtra("bookUrl", book?.bookUrl)
                }
            }
            BookInfoMenuAction.Top -> viewModel.topBook()
            BookInfoMenuAction.SetSourceVariable -> setSourceVariable()
            BookInfoMenuAction.SetBookVariable -> setBookVariable()
            BookInfoMenuAction.CopyBookUrl -> copyBookUrl()
            BookInfoMenuAction.CopyTocUrl -> copyTocUrl()
            BookInfoMenuAction.CanUpdate -> toggleCanUpdate()
            BookInfoMenuAction.SplitLongChapter -> toggleSplitLongChapter()
            BookInfoMenuAction.DeleteAlert -> {
                LocalConfig.bookInfoDeleteAlert = !LocalConfig.bookInfoDeleteAlert
                uiState = uiState.copy(deleteAlertEnabled = LocalConfig.bookInfoDeleteAlert)
            }
            BookInfoMenuAction.ClearCache -> viewModel.getBook()?.let {
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_CLEAR_CACHE,
                    viewModel.bookSource,
                    it,
                    null,
                ) { viewModel.clearCache(it) }
            }
            BookInfoMenuAction.Log -> showDialogFragment<AppLogDialog>()
            BookInfoMenuAction.NetworkLog -> showDialogFragment<NetworkLogDialog>()
            BookInfoMenuAction.AutoLoadOtherWorks -> {
                val enabled = !uiState.autoLoadOtherWorks
                putPrefBoolean(PreferKey.autoLoadBookOtherWorks, enabled)
                uiState = uiState.copy(autoLoadOtherWorks = enabled)
                viewModel.setAutoLoadOtherWorks(enabled)
            }
        }
    }

    private fun clickCustomButton() {
        if (!viewModel.hasCustomBtn) return
        viewModel.getBook()?.let { current ->
            SourceCallBack.callBackBtn(
                this,
                SourceCallBack.CLICK_CUSTOM_BUTTON,
                viewModel.bookSource,
                current,
                null,
            )
        }
    }

    private fun shareBook() {
        viewModel.getBook()?.let { current ->
            val bookJson = GSON.toJson(current)
            val shareStr = "${current.bookUrl}#$bookJson"
            SourceCallBack.callBackBtn(
                this,
                SourceCallBack.CLICK_SHARE_BOOK,
                viewModel.bookSource,
                current,
                null,
                result = shareStr,
            ) {
                val intent = Intent(Intent.ACTION_SEND)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.putExtra(Intent.EXTRA_TEXT, shareStr)
                intent.type = "text/plain"
                startActivity(Intent.createChooser(intent, current.name))
            }
        }
    }

    private fun copyBookUrl() {
        viewModel.getBook()?.let { current ->
            SourceCallBack.callBackBtn(
                this,
                SourceCallBack.CLICK_COPY_BOOK_URL,
                viewModel.bookSource,
                current,
                null,
                result = current.bookUrl,
            ) { sendToClip(current.bookUrl) }
        }
    }

    private fun copyTocUrl() {
        viewModel.getBook()?.let { current ->
            SourceCallBack.callBackBtn(
                this,
                SourceCallBack.CLICK_COPY_TOC_URL,
                viewModel.bookSource,
                current,
                null,
                result = current.tocUrl,
            ) { sendToClip(current.tocUrl) }
        }
    }

    private fun toggleCanUpdate() {
        viewModel.getBook()?.let { current ->
            current.canUpdate = !current.canUpdate
            if (viewModel.inBookshelf) {
                if (!current.canUpdate) current.removeType(BookType.updateError)
                viewModel.saveBook(current)
            }
            updateBookSnapshot(current)
        }
    }

    private fun toggleSplitLongChapter() {
        upLoading(true)
        viewModel.getBook()?.let { current ->
            val enabled = !current.getSplitLongChapter()
            current.setSplitLongChapter(enabled)
            updateBookSnapshot(current)
            viewModel.loadBookInfo(current, false)
            if (!enabled) longToastOnUi(R.string.need_more_time_load_content)
        }
    }

    private fun uploadBookFromMenu() {
        viewModel.getBook()?.let { current ->
            current.getRemoteUrl()?.let {
                alert(R.string.draw, R.string.sure_upload) {
                    okButton { upLoadBook(current) }
                    cancelButton()
                }
            } ?: upLoadBook(current)
        }
    }

    override fun observeLiveBus() {
        viewModel.actionLive.observe(this) {
            when (it) {
                "selectBooksDir" -> localBookTreeSelect.launch(null)
            }
        }

        observeEvent<Boolean>(EventBus.REFRESH_BOOK_INFO) { //书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                refreshBook()
            }
        }

        observeEvent<Boolean>(EventBus.REFRESH_BOOK_TOC) { //书源js函数触发刷新
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                refreshToc()
            }
        }

        observeEvent<Pair<Book, BookChapter>>(EventBus.SAVE_CONTENT) { (eventBook, _) ->
            if (eventBook.bookUrl == viewModel.getBook(false)?.bookUrl) {
                upCacheProgress()
            }
        }

        observeEvent<String>(EventBus.UP_DOWNLOAD) { bookUrl ->
            if (bookUrl == viewModel.getBook(false)?.bookUrl) {
                upCacheProgress()
            }
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (initIntroView && ev.action == MotionEvent.ACTION_DOWN) {
            currentFocus?.let {
                if (it === introTextView && introTextView.hasSelection()) {
                    it.clearFocus()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshBook() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.refreshBook(it)
        }
    }

    private fun refreshToc() {
        upLoading(true)
        viewModel.getBook()?.let {
            viewModel.loadChapter(it, true, isFromBookInfo = true)
        }
    }

    private fun upLoadBook(
        book: Book,
        bookWebDav: RemoteBookWebDav? = AppWebDav.defaultBookWebDav,
    ) {
        lifecycleScope.launch {
            waitDialog.setText("上传中.....")
            waitDialog.show()
            try {
                bookWebDav
                    ?.upload(book)
                    ?: throw NoStackTraceException("未配置webDav")
                //更新书籍最后更新时间,使之比远程书籍的时间新
                book.lastCheckTime = System.currentTimeMillis()
                viewModel.saveBook(book)
            } catch (e: Exception) {
                toastOnUi(e.localizedMessage)
            } finally {
                waitDialog.dismiss()
            }
        }
    }

    private fun showBook(book: Book) {
        updateBookSnapshot(book)
        showBookIntro(book)
        upKinds(book)
        upGroup(book.group)
        observeCharacterPreview(book)
        viewModel.prepareOtherWorks(book, uiState.autoLoadOtherWorks)
        upCacheProgress(book)
    }

    private fun updateBookSnapshot(book: Book) {
        val bookChanged = uiState.book?.bookUrl != book.bookUrl
        if (bookChanged) {
            otherWorksRawBooks = emptyList()
            otherWorksGroupCounts = emptyMap()
            otherWorksGroupPrimaryBookUrls = emptyMap()
            expandedOtherWorksKeys.clear()
        }
        uiState = uiState.copy(
            book = book.copy(),
            originText = getString(R.string.origin_show, book.originName),
            latestText = getString(
                R.string.lasted_show,
                if (book.isWebFile) "下载中..." else book.latestChapterTitle,
            ),
            inBookshelf = viewModel.inBookshelf,
            hasCustomButton = viewModel.hasCustomBtn,
            sourceAvailable = viewModel.bookSource != null,
            loginAvailable = !viewModel.bookSource?.loginUrl.isNullOrBlank(),
            showToc = !book.isWebFile,
            showCache = !book.isWebFile,
            showListen = book.supportsReadAloud,
            primaryActionIsPlay = book.isAudio || book.isVideo,
            otherWorksVisible = !book.isLocal && book.getRealAuthor().isNotBlank(),
            tags = if (bookChanged) emptyList() else uiState.tags,
            charactersVisible = if (bookChanged) false else uiState.charactersVisible,
            characters = if (bookChanged) emptyList() else uiState.characters,
            characterCount = if (bookChanged) 0 else uiState.characterCount,
            otherWorksState = if (bookChanged) {
                BookInfoOtherWorksState.Idle
            } else {
                uiState.otherWorksState
            },
            otherWorksLoading = if (bookChanged) false else uiState.otherWorksLoading,
            otherWorks = if (bookChanged) emptyList() else uiState.otherWorks,
            deleteAlertEnabled = LocalConfig.bookInfoDeleteAlert,
        )
    }

    private fun updateInBookshelfState() {
        uiState = uiState.copy(inBookshelf = viewModel.inBookshelf)
    }

    private fun showOtherWorks(state: BookInfoViewModel.OtherWorksState) {
        when (state) {
            BookInfoViewModel.OtherWorksState.Idle -> {
                resetOtherWorksDisplay(BookInfoOtherWorksState.Idle)
            }

            BookInfoViewModel.OtherWorksState.Loading -> {
                resetOtherWorksDisplay(BookInfoOtherWorksState.Loading)
            }

            BookInfoViewModel.OtherWorksState.Empty -> {
                resetOtherWorksDisplay(BookInfoOtherWorksState.Empty)
            }

            is BookInfoViewModel.OtherWorksState.Success -> {
                updateOtherWorksDisplay(state.books)
            }

            is BookInfoViewModel.OtherWorksState.Error -> {
                resetOtherWorksDisplay(BookInfoOtherWorksState.Error(state.message))
            }
        }
    }

    private fun resetOtherWorksDisplay(state: BookInfoOtherWorksState) {
        otherWorksRawBooks = emptyList()
        otherWorksGroupCounts = emptyMap()
        otherWorksGroupPrimaryBookUrls = emptyMap()
        expandedOtherWorksKeys.clear()
        uiState = uiState.copy(otherWorksState = state, otherWorks = emptyList())
    }

    private fun updateOtherWorksDisplay(books: List<SearchBook>) {
        otherWorksRawBooks = books
        rebuildOtherWorksGroups()
        uiState = uiState.copy(otherWorksState = BookInfoOtherWorksState.Success)
        updateOtherWorksUiItems()
    }

    private fun rebuildOtherWorksGroups() {
        val groups = otherWorksRawBooks.groupBy(::otherWorksGroupKey)
        otherWorksGroupCounts = groups.mapValues { it.value.size }
        otherWorksGroupPrimaryBookUrls = groups.mapValues { it.value.first().bookUrl }
        expandedOtherWorksKeys.retainAll(groups.keys)
    }

    private fun buildOtherWorksItems(): List<SearchBook> {
        val items = arrayListOf<SearchBook>()
        otherWorksRawBooks.groupBy(::otherWorksGroupKey).forEach { (key, books) ->
            if (books.isEmpty()) {
                return@forEach
            }
            items.add(books.first())
            if (key in expandedOtherWorksKeys && books.size > 1) {
                items.addAll(books.drop(1))
            }
        }
        return items
    }

    private fun otherWorksGroupKey(book: SearchBook): String {
        return "${book.name.trim()}\n${book.author.trim()}"
    }

    private fun otherWorksGroupCount(book: SearchBook): Int {
        val key = otherWorksGroupKey(book)
        if (key in expandedOtherWorksKeys) {
            return 1
        }
        val primaryBookUrl = otherWorksGroupPrimaryBookUrls[key]
        return if (primaryBookUrl == book.bookUrl) {
            otherWorksGroupCounts[key] ?: 1
        } else {
            1
        }
    }

    private fun canExpandOtherWorks(book: SearchBook): Boolean {
        return (otherWorksGroupCounts[otherWorksGroupKey(book)] ?: 1) > 1
    }

    private fun updateOtherWorksUiItems() {
        uiState = uiState.copy(
            otherWorks = buildOtherWorksItems().map { otherBook ->
                BookInfoOtherWorkUiItem(
                    book = otherBook,
                    inBookshelf = viewModel.isInBookShelf(otherBook),
                    originCount = otherWorksGroupCount(otherBook),
                    canExpand = canExpandOtherWorks(otherBook),
                )
            },
        )
    }

    private fun observeCharacterPreview(book: Book) {
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        characterPreviewJob?.cancel()
        characterPreviewJob = lifecycleScope.launch {
            appDb.bookCharacterDao.flowCharacters(workKey)
                .catch {
                    AppLog.put("角色卡预览加载失败\n${it.localizedMessage}", it)
                }
                .flowOn(IO)
                .collect { characters ->
                    showCharacterPreview(characters)
                }
        }
    }

    private fun showCharacterPreview(characters: List<BookCharacter>) {
        uiState = uiState.copy(
            charactersVisible = true,
            characterCount = characters.size,
            characters = characters.take(6).map { character ->
                BookInfoCharacterUiItem(
                    id = character.id,
                    name = character.name,
                    intro = character.displayIntro()
                        ?: BookCharacterLabels.roleLabel(this, character.roleTag),
                    avatarColorRes = character.avatarColor(),
                )
            },
        )
    }

    private fun showBookInfo(name: String, author: String, bookUrl: String) {
        startActivity<BookInfoActivity> {
            putExtra("bookUrl", bookUrl)
        }
    }

    private fun showAllSources(book: SearchBook) {
        val key = otherWorksGroupKey(book)
        if ((otherWorksGroupCounts[key] ?: 1) <= 1) {
            return
        }
        if (expandedOtherWorksKeys.add(key)) {
            updateOtherWorksUiItems()
        }
    }

    private fun BookCharacter.avatarColor(): Int {
        return when (gender) {
            BookCharacter.Gender.MALE -> R.color.character_avatar_male
            BookCharacter.Gender.FEMALE -> R.color.character_avatar_female
            else -> R.color.character_avatar_unknown
        }
    }

    private fun openCharacterActivity(book: Book, workKey: String) {
        startActivity<BookCharacterActivity> {
            putExtra(BookCharacterActivity.EXTRA_WORK_KEY, workKey)
            putExtra(BookCharacterActivity.EXTRA_BOOK_NAME, book.name)
            putExtra(BookCharacterActivity.EXTRA_BOOK_AUTHOR, book.author)
            putExtra(BookCharacterActivity.EXTRA_BOOK_URL, book.bookUrl)
        }
    }

    private fun openCharacterCardAiAssistant(book: Book) {
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        val payload = JsonObject().apply {
            addProperty("work_key", workKey)
            addProperty("book_name", book.name)
            addProperty("book_author", book.getRealAuthor())
            addProperty("book_url", book.bookUrl)
            addProperty("origin_name", book.originName)
            book.kind?.takeIf { it.isNotBlank() }?.let { addProperty("category", it) }
            book.wordCount?.takeIf { it.isNotBlank() }?.let { addProperty("word_count", it) }
            addProperty("total_chapters", book.totalChapterNum)
            book.durChapterTitle?.takeIf { it.isNotBlank() }?.let {
                addProperty("current_chapter_index", book.durChapterIndex + 1)
                addProperty("current_chapter_title", it)
            }
            book.latestChapterTitle?.takeIf { it.isNotBlank() }?.let {
                addProperty("latest_chapter_title", it)
            }
            book.getDisplayIntro()
                .orEmpty()
                .toPlainBookIntro()
                .limitAiContextText(1800)
                .takeIf { it.isNotBlank() }
                ?.let { addProperty("book_intro", it) }
        }
        val entryContext = AgentModeEntryContext(
            contextId = "book_detail_character_card",
            payload = payload
        )
        startActivity<AiChatActivity> {
            putExtra(AiChatActivity.EXTRA_ENTRY, AiChatActivity.ENTRY_BOOK_DETAIL)
            putStringArrayListExtra(
                AiChatActivity.EXTRA_LOADED_SKILL_IDS,
                arrayListOf(AiSkillRegistry.SKILL_CHARACTER_CARD_GENERATE)
            )
            putExtra(AiChatActivity.EXTRA_MODE_ENTRY_CONTEXT, entryContext.toJson())
            putExtra(AiChatActivity.EXTRA_EXPAND_SUGGESTIONS, true)
        }
    }

    private fun String.toPlainBookIntro(): String {
        return replace(Regex("""<use(html|web)>|</use(html|web)>|<md>|</md>"""), "")
            .replace(Regex("""<[^>]+>"""), "")
            .replace(Regex("""[ \t\r\n]+"""), " ")
            .trim()
    }

    private fun String.limitAiContextText(maxLength: Int): String {
        return if (length <= maxLength) {
            this
        } else {
            take(maxLength).trimEnd() + "..."
        }
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
                BookSourceCookieStore.forBookSource(viewModel.bookSource)?.captureFromWebView(
                    pageUrl = url
                )
            }
            view?.post {
                introContainer.requestLayout()
            }
        }
    }

    private fun showBookIntro(book: Book) {
        uiState = uiState.copy(introRevision = uiState.introRevision + 1)
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
                val source = viewModel.bookSource
                webView.addJavascriptInterface(source.webCacheObject(), nameCache)
                source?.let {
                    webView.addJavascriptInterface(it as BaseSource, nameSource)
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
            val bookUrl = viewModel.getBook()?.bookUrl
                ?.takeIf { it.startsWith("http", true) }
                ?.substringBefore(",")
            if (bookUrl != null) {
                BookSourceCookieStore.forBookSource(viewModel.bookSource)?.applyToWebView(
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
                    showDialogFragment(PhotoDialog(it, viewModel.bookSource?.bookSourceUrl))
                },
                imgOnClickListener = {
                    viewModel.onButtonClick(this@BookInfoActivity, "info image" , it)
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
                val context = this@BookInfoActivity
                val markwon: Markwon
                val markdown = withContext(IO) {
                    markwon = Markwon.builder(context)
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
                        showDialogFragment(PhotoDialog(source, viewModel.bookSource?.bookSourceUrl))
                    }
                )
            }
        } else {
            tvIntro.text = intro
        }
    }

    private fun upKinds(book: Book) {
        lifecycleScope.launch {
            var kinds = book.getKindList()
            if (book.isLocal) {
                withContext(IO) {
                    val size = FileDoc.fromFile(book.bookUrl).size
                    if (size > 0) {
                        kinds = kinds.toMutableList()
                        kinds.add(ConvertUtils.formatFileSize(size))
                    }
                }
            }
            if (viewModel.getBook(false)?.bookUrl == book.bookUrl) {
                uiState = uiState.copy(tags = kinds)
            }
        }
    }

    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        when {
            isLoading -> {
                uiState = uiState.copy(
                    tocText = getString(R.string.toc_s, getString(R.string.loading)),
                )
            }

            chapterList.isNullOrEmpty() -> {
                uiState = uiState.copy(
                    tocText = getString(
                        R.string.toc_s,
                        getString(R.string.error_load_toc),
                    ),
                    latestText = getString(R.string.lasted_show, book?.latestChapterTitle),
                )
            }

            else -> {
                book?.let {
                    uiState = uiState.copy(
                        book = it.copy(),
                        tocText = getString(R.string.toc_s, it.durChapterTitle),
                        latestText = getString(R.string.lasted_show, it.latestChapterTitle),
                    )
                }
            }
        }
    }

    private fun upGroup(groupId: Long) {
        uiState = uiState.copy(
            groupText = getString(R.string.group_s, getString(R.string.no_group)),
        )
        viewModel.loadGroup(groupId) {
            uiState = uiState.copy(
                groupText = getString(
                    R.string.group_s,
                    it?.takeIf(String::isNotEmpty) ?: getString(R.string.no_group),
                ),
            )
        }
    }

    private fun changeCover() {
        viewModel.getBook()?.let {
            showDialogFragment(ChangeCoverDialog(it.name, it.author))
        }
    }

    private fun showCoverPreview() {
        viewModel.getBook()?.getDisplayCover()?.let { path ->
            showDialogFragment(PhotoDialog(path, isBook = true))
        }
    }

    private fun clickRead() {
        viewModel.getBook()?.let { current ->
            if (current.isWebFile) {
                showWebFileDownloadAlert(::readBook)
            } else {
                readBook(current)
            }
        }
    }

    private fun clickListen() {
        viewModel.getBook()?.let { current ->
            if (!current.supportsReadAloud) return
            readAloudBook(current)
        }
    }

    private fun clickShelf() {
        viewModel.getBook()?.let { current ->
            if (viewModel.inBookshelf) {
                deleteBook()
            } else if (current.isWebFile) {
                showWebFileDownloadAlert()
            } else {
                viewModel.addToBookshelf(::updateInBookshelfState)
            }
        }
    }

    private fun editCurrentSource() {
        viewModel.getBook()?.let { current ->
            if (current.isLocal) return
            if (!appDb.bookSourceDao.has(current.origin)) {
                toastOnUi(R.string.error_no_source)
                return
            }
            editSourceResult.launch { putExtra("sourceUrl", current.origin) }
        }
    }

    private fun changeSource() {
        viewModel.getBook()?.let { current ->
            showDialogFragment(ChangeBookSourceDialog(current.name, current.author))
        }
    }

    private fun openChapterListFromUi() {
        if (viewModel.chapterListData.value.isNullOrEmpty()) {
            toastOnUi(R.string.chapter_list_empty)
            return
        }
        viewModel.getBook()?.let { current ->
            if (!viewModel.inBookshelf) {
                viewModel.saveBook(current) {
                    viewModel.saveChapterList(::openChapterList)
                }
            } else {
                openChapterList()
            }
        }
    }

    private fun clickAuthor(longClick: Boolean) {
        viewModel.getBook(false)?.let { current ->
            SourceCallBack.callBackBtn(
                this,
                if (longClick) SourceCallBack.LONG_CLICK_AUTHOR else SourceCallBack.CLICK_AUTHOR,
                viewModel.bookSource,
                current,
                null,
                result = current.author,
            ) { SearchActivity.start(this, current.author) }
        }
    }

    private fun clickBookName(longClick: Boolean) {
        viewModel.getBook(false)?.let { current ->
            SourceCallBack.callBackBtn(
                this,
                if (longClick) {
                    SourceCallBack.LONG_CLICK_BOOK_NAME
                } else {
                    SourceCallBack.CLICK_BOOK_NAME
                },
                viewModel.bookSource,
                current,
                null,
                result = current.name,
            ) { SearchActivity.start(this, current.name) }
        }
    }

    private fun clickTag(tag: String, longClick: Boolean) {
        val source = viewModel.bookSource ?: return
        val current = viewModel.getBook(false) ?: return
        SourceCallBack.callBackBtn(
            this,
            if (longClick) {
                SourceCallBack.LONG_CLICK_BOOK_LABEL
            } else {
                SourceCallBack.CLICK_BOOK_LABEL
            },
            source,
            current,
            null,
            result = tag,
        ) {
            if (!longClick) SearchActivity.start(this, source, tag)
        }
    }

    private fun upCacheProgress(
        book: Book? = viewModel.getBook(false),
        chapterList: List<BookChapter>? = viewModel.chapterListData.value
    ) {
        if (book == null || book.isWebFile) {
            cacheProgressJob?.cancel()
            cacheProgressBookUrl = null
            cacheProgressCached = 0
            cacheProgressTotal = 0
            uiState = uiState.copy(
                showCache = false,
                cache = BookInfoCacheUiState(),
            )
            return
        }
        val chapters = chapterList.orEmpty()
        val total = when {
            chapters.isNotEmpty() -> chapters.size
            book.totalChapterNum > 0 -> book.totalChapterNum
            else -> 0
        }
        val cacheEnabled = !book.isLocal && total > 0
        uiState = uiState.copy(
            showCache = true,
            cache = uiState.cache.copy(total = total, enabled = cacheEnabled),
        )
        if (book.isLocal) {
            cacheProgressJob?.cancel()
            showCacheProgress(total, total)
            return
        }
        val isSameBook = cacheProgressBookUrl == book.bookUrl
        if (!isSameBook) {
            cacheProgressJob?.cancel()
            showCacheProgress(0, total)
        } else if (cacheProgressTotal != total) {
            cacheProgressJob?.cancel()
            showCacheProgress(cacheProgressCached.coerceAtMost(total), total)
        } else if (cacheProgressJob?.isActive == true) {
            return
        }
        if (chapters.isEmpty()) {
            return
        }
        cacheProgressJob = lifecycleScope.launch {
            val cachedCount = withContext(IO) {
                val cacheFileNames = BookHelp.getChapterFiles(book)
                chapters.count { it.isVolume || cacheFileNames.contains(it.getFileName()) }
            }
            if (viewModel.getBook(false)?.bookUrl == book.bookUrl) {
                showCacheProgress(cachedCount, total)
            }
        }
    }

    private fun showCacheProgress(cached: Int, total: Int) {
        cacheProgressBookUrl = viewModel.getBook(false)?.bookUrl
        cacheProgressCached = cached
        cacheProgressTotal = total
        uiState = uiState.copy(
            cache = uiState.cache.copy(cached = cached, total = total),
        )
    }

    private fun startCacheBook(book: Book) {
        if (book.isLocal || book.isWebFile) {
            return
        }
        val chapterList = viewModel.chapterListData.value
        if (chapterList.isNullOrEmpty()) {
            toastOnUi(R.string.chapter_list_empty)
            return
        }
        val startCache = {
            val end = chapterList.lastIndex.coerceAtLeast(book.lastChapterIndex)
            CacheBook.cacheBookMap[book.bookUrl]?.let {
                if (!it.isStop()) {
                    CacheBook.remove(this, book.bookUrl)
                } else {
                    CacheBook.start(this, book, 0, end)
                }
            } ?: CacheBook.start(this, book, 0, end)
        }
        if (!viewModel.inBookshelf) {
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startCache()
                }
            }
        } else {
            startCache()
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val comment =
                source.getDisplayVariableComment("源变量可在js中通过source.getVariable()获取")
            val variable = withContext(IO) { source.getVariable() }
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_source_variable),
                    source.getKey(),
                    variable,
                    comment
                )
            )
        }
    }

    private fun setBookVariable() {
        lifecycleScope.launch {
            val source = viewModel.bookSource
            if (source == null) {
                toastOnUi("书源不存在")
                return@launch
            }
            val book = viewModel.getBook() ?: return@launch
            val variable = withContext(IO) { book.getCustomVariable() }
            val comment = source.getDisplayVariableComment(
                """书籍变量可在js中通过book.getVariable("custom")获取"""
            )
            showDialogFragment(
                VariableDialog(
                    getString(R.string.set_book_variable),
                    book.bookUrl,
                    variable,
                    comment
                )
            )
        }
    }

    override fun setVariable(key: String, variable: String?) {
        when (key) {
            viewModel.bookSource?.getKey() -> viewModel.bookSource?.setVariable(variable)
            viewModel.bookData.value?.bookUrl -> viewModel.bookData.value?.let {
                it.putCustomVariable(variable)
                if (viewModel.inBookshelf) {
                    viewModel.saveBook(it)
                }
            }
        }
    }

    private fun deleteBook() {
        viewModel.getBook()?.let { current ->
            if (LocalConfig.bookInfoDeleteAlert) {
                uiState = uiState.copy(
                    deleteDialogVisible = true,
                    deleteOriginal = current.isLocal && LocalConfig.deleteBookOriginal,
                )
            } else {
                performDeleteBook(current, LocalConfig.deleteBookOriginal)
            }
        }
    }

    private fun confirmDeleteBook() {
        val current = viewModel.getBook() ?: return
        val deleteOriginal = current.isLocal && uiState.deleteOriginal
        if (current.isLocal) {
            LocalConfig.deleteBookOriginal = deleteOriginal
        }
        uiState = uiState.copy(deleteDialogVisible = false)
        performDeleteBook(current, deleteOriginal)
    }

    private fun performDeleteBook(book: Book, deleteOriginal: Boolean) {
        SourceCallBack.callBackBook(
            SourceCallBack.DEL_BOOK_SHELF,
            viewModel.bookSource,
            book,
        )
        viewModel.delBook(deleteOriginal) {
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun openChapterList() {
        viewModel.getBook()?.let {
            tocActivityResult.launch(it.bookUrl)
        }
    }

    private fun showWebFileDownloadAlert(
        onClick: ((Book) -> Unit)? = null,
    ) {
        val webFiles = viewModel.webFiles
        if (webFiles.isEmpty()) {
            toastOnUi("Unexpected webFileData")
            return
        }
        pendingWebFileSuccess = onClick
        pendingArchiveUri = null
        pendingUnsupportedWebFile = null
        uiState = uiState.copy(
            fileDialog = BookInfoFileDialogState.WebFiles(webFiles.map { it.name }),
        )
    }

    private fun selectFileDialogItem(index: Int) {
        when (val dialog = uiState.fileDialog) {
            is BookInfoFileDialogState.WebFiles -> {
                val webFile = viewModel.webFiles.getOrNull(index) ?: return
                uiState = uiState.copy(fileDialog = null)
                handleSelectedWebFile(webFile)
            }
            is BookInfoFileDialogState.ArchiveFiles -> {
                val archiveUri = pendingArchiveUri ?: return
                val name = dialog.items.getOrNull(index) ?: return
                uiState = uiState.copy(fileDialog = null)
                viewModel.importArchiveBook(archiveUri, name, ::completeWebFileImport)
            }
            is BookInfoFileDialogState.UnsupportedFile, null -> Unit
        }
    }

    private fun handleSelectedWebFile(webFile: BookInfoViewModel.WebFile) {
        when {
            webFile.isSupported -> {
                viewModel.importOrDownloadWebFile<Book>(webFile, ::completeWebFileImport)
            }
            webFile.isSupportDecompress -> {
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        when (fileNames.size) {
                            0 -> {
                                toastOnUi(R.string.unsupport_archivefile_entry)
                                clearPendingFileSelection()
                            }
                            1 -> viewModel.importArchiveBook(
                                uri,
                                fileNames[0],
                                ::completeWebFileImport,
                            )
                            else -> {
                                pendingArchiveUri = uri
                                uiState = uiState.copy(
                                    fileDialog = BookInfoFileDialogState.ArchiveFiles(fileNames),
                                )
                            }
                        }
                    }
                }
            }
            else -> {
                pendingUnsupportedWebFile = webFile
                uiState = uiState.copy(
                    fileDialog = BookInfoFileDialogState.UnsupportedFile(webFile.name),
                )
            }
        }
    }

    private fun completeWebFileImport(book: Book) {
        val callback = pendingWebFileSuccess
        clearPendingFileSelection()
        callback?.invoke(book)
    }

    private fun openUnsupportedWebFile() {
        val webFile = pendingUnsupportedWebFile ?: return
        clearPendingFileSelection()
        viewModel.importOrDownloadWebFile<Uri>(webFile) {
            openFileUri(it, "*/*")
        }
    }

    private fun dismissFileDialog() {
        uiState = uiState.copy(fileDialog = null)
        clearPendingFileSelection()
    }

    private fun clearPendingFileSelection() {
        uiState = uiState.copy(fileDialog = null)
        pendingWebFileSuccess = null
        pendingArchiveUri = null
        pendingUnsupportedWebFile = null
    }

    private fun readBook(book: Book) {
        if (!viewModel.inBookshelf) {
            book.addType(BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadActivity(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadActivity(book)
            }
        }
    }

    private fun readAloudBook(book: Book) {
        if (!viewModel.inBookshelf) {
            book.addType(BookType.notShelf)
            viewModel.saveBook(book) {
                viewModel.saveChapterList {
                    startReadAloudPlayer(book)
                }
            }
        } else {
            viewModel.saveBook(book) {
                startReadAloudPlayer(book)
            }
        }
    }

    private fun startReadAloudPlayer(book: Book) {
        lifecycleScope.launch {
            val prepared = ReadAloudLauncher.prepareState(
                book = book,
                inBookshelf = viewModel.inBookshelf,
                chapterChanged = chapterChanged
            )
            if (prepared) {
                ReadAloudLauncher.openPlayer(this@BookInfoActivity, autoStart = true)
            } else {
                toastOnUi(ReadBook.msg ?: "初始化听书失败")
            }
        }
    }

    private fun startReadActivity(book: Book) {
        when {
            book.isAudio -> readBookResult.launch(
                Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
                    .also(AudioPlayActivity::applyAutoStart)
            )
            book.isVideo -> readBookResult.launch(
                Intent(this, VideoPlayerActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
            )

            else -> readBookResult.launch(
                Intent(
                    this,
                    if (!book.isLocal && book.isImage && AppConfig.showMangaUi) ReadMangaActivity::class.java
                    else ReadBookActivity::class.java
                )
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
                    .putExtra("chapterChanged", chapterChanged)
            )
        }
    }

    override val oldBook: Book?
        get() = viewModel.bookData.value

    override fun changeTo(source: BookSource, book: Book, toc: List<BookChapter>) {
        viewModel.changeTo(source, book, toc)
    }

    override fun coverChangeTo(coverUrl: String) {
        viewModel.bookData.value?.let { book ->
            book.customCoverUrl = coverUrl
            uiState = uiState.copy(
                book = book.copy(),
                coverRevision = uiState.coverRevision + 1,
            )
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            }
        }
    }

    private fun upWaitDialogStatus(isShow: Boolean) {
        val showText = "Loading....."
        if (isShow) {
            waitDialog.run {
                setText(showText)
                show()
            }
        } else {
            waitDialog.dismiss()
        }
    }

     override fun onStart() {
         super.onStart()
         if (initGetter) {
             glideImageGetter.start()
         }
     }

     override fun onStop() {
         super.onStop()
         if (initGetter) {
             glideImageGetter.stop()
         }
     }

    override fun onDestroy() {
        cacheProgressJob?.cancel()
        destroyWeb()
        super.onDestroy()
        if (initGetter) {
            glideImageGetter.clear()
        }
    }

    private fun destroyWeb() {
        pooledWebView?.let { WebViewPool.release(it) }
        pooledWebView = null
    }

}
