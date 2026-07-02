package io.legado.app.ui.book.info

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import io.legado.app.ui.widget.text.ScrollTextView
import android.view.textclassifier.TextClassifier
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.BookType
import io.legado.app.constant.EventBus
import io.legado.app.constant.Theme
import io.legado.app.data.appDb
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookCharacter
import io.legado.app.data.entities.BookCharacterProfile
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ActivityBookInfoBinding
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.AppWebDav
import io.legado.app.help.GlideImageGetter
import io.legado.app.help.TextViewTagHandler
import io.legado.app.help.WebCacheManager
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
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.ThemeUtils
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.model.CacheBook
import io.legado.app.model.SourceCallBack
import io.legado.app.model.remote.RemoteBookWebDav
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.audio.AudioPlayActivity
import io.legado.app.ui.book.changecover.ChangeCoverDialog
import io.legado.app.ui.book.character.BookCharacterActivity
import io.legado.app.ui.book.character.BookCharacterLabels
import io.legado.app.ui.book.changesource.ChangeBookSourceDialog
import io.legado.app.ui.book.group.GroupSelectDialog
import io.legado.app.ui.book.info.edit.BookInfoEditActivity
import io.legado.app.ui.book.manga.ReadMangaActivity
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadBookActivity.Companion.RESULT_DELETED
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchAdapter
import io.legado.app.ui.association.OnLineImportActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.toc.TocActivityResult
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.video.VideoPlayerActivity
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.ui.widget.dialog.PhotoDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.WaitDialog
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.ConvertUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.GSON
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.longSnackbar
import io.legado.app.utils.longToastOnUi
import io.legado.app.utils.observeEvent
import io.legado.app.utils.openFileUri
import io.legado.app.utils.openUrl
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setHtml
import io.legado.app.utils.setMarkdown
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.visible
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
    VMBaseActivity<ActivityBookInfoBinding, BookInfoViewModel>(toolBarTheme = Theme.Dark, showOpenMenuIcon = false),
    GroupSelectDialog.CallBack,
    ChangeBookSourceDialog.CallBack,
    ChangeCoverDialog.CallBack,
    VariableDialog.Callback,
    SearchAdapter.CallBack {

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
    private val localBookTreeSelect = registerForActivityResult(HandleFileContract()) {
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
                upTvBookshelf()
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
    private var editMenuItem: MenuItem? = null
    private var menuCustomBtn: MenuItem? = null
    private var bookInfoMenu: Menu? = null
    private var characterPreviewJob: Job? = null
    private var otherWorksRawBooks = emptyList<SearchBook>()
    private var otherWorksGroupCounts = emptyMap<String, Int>()
    private var otherWorksGroupPrimaryBookUrls = emptyMap<String, String>()
    private val expandedOtherWorksKeys = linkedSetOf<String>()
    private val bookInfoPopupItemIds = intArrayOf(
        R.id.menu_upload,
        R.id.menu_refresh,
        R.id.menu_login,
        R.id.menu_top,
        R.id.menu_set_source_variable,
        R.id.menu_set_book_variable,
        R.id.menu_copy_book_url,
        R.id.menu_copy_toc_url,
        R.id.menu_can_update,
        R.id.menu_split_long_chapter,
        R.id.menu_delete_alert,
        R.id.menu_clear_cache,
        R.id.menu_log,
        R.id.menu_network_log
    )
    private val otherWorksAdapter by lazy {
        SearchAdapter(
            this,
            this,
            SearchAdapter.Config(
                horizontalMarginDp = 0,
                backgroundRes = R.drawable.ng_bg_search_result_card_compact,
                showInBookshelf = false,
                minOriginCount = 2,
                originCountProvider = ::otherWorksGroupCount,
                longClickEnabledProvider = ::canExpandOtherWorks
            )
        )
    }
    private val book get() = viewModel.getBook(false)

    override val binding by viewBinding(ActivityBookInfoBinding::inflate)
    override val viewModel by viewModels<BookInfoViewModel>()
    override val bindNgToolbarMenu: Boolean = false
    private var initIntroView = false
    private var cacheProgressJob: Job? = null
    private var cacheProgressBookUrl: String? = null
    private var cacheProgressCached = 0
    private var cacheProgressTotal = 0
    private val introTextView by lazy {
        initIntroView = true
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.view_book_intro, binding.tvIntroContainer, false) as ScrollTextView
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            view.revealOnFocusHint = false
        }
        view
    }

    private var pooledWebView: PooledWebView? = null

    private val imgAvailableWidth by lazy {
        val textView = introTextView
        textView.width - textView.paddingLeft - textView.paddingRight - 8.dpToPx()  //8是为了文字对齐额外的右边距
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
        binding.titleBar.setBackgroundResource(R.color.transparent)
        binding.bgBook.setImageDrawable(null)
        binding.refreshLayout?.setColorSchemeColors(accentColor)
        binding.arcView?.setBgColor(backgroundColor)
        val isPortrait =
            resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        binding.llInfo.setBackgroundColor(
            if (isPortrait) android.graphics.Color.TRANSPARENT else backgroundColor
        )
        binding.ivCoverC?.setCardBackgroundColor(
            if (isPortrait) android.graphics.Color.TRANSPARENT else backgroundColor
        )
        binding.flAction.setBackgroundColor(
            if (isPortrait) android.graphics.Color.TRANSPARENT else bottomBackground
        )
        binding.vwBg.applyNavigationBarPadding()
        binding.tvShelf.setTextColor(
            if (isPortrait) accentColor else getPrimaryTextColor(ColorUtils.isColorLight(bottomBackground))
        )
        binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))
        initOtherWorksView()
        viewModel.bookData.observe(this) { showBook(it) }
        viewModel.chapterListData.observe(this) {
            upLoading(false, it)
            upCacheProgress(viewModel.getBook(false), it)
        }
        viewModel.otherWorksData.observe(this) { showOtherWorks(it) }
        viewModel.bookshelfChanged.observe(this) {
            otherWorksAdapter.notifyItemRangeChanged(
                0,
                otherWorksAdapter.itemCount,
                bundleOf("isInBookshelf" to null)
            )
        }
        viewModel.waitDialogData.observe(this) { upWaitDialogStatus(it) }
        viewModel.initData(intent)
        initViewEvent()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        binding.scrollView?.scrollTo(0, 0)
        binding.scrollViewL?.scrollTo(0, 0)
        binding.scrollViewR?.scrollTo(0, 0)
        viewModel.initData(intent)
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_info, menu)
        bookInfoMenu = menu
        editMenuItem = menu.findItem(R.id.menu_edit)
        menuCustomBtn = menu.findItem(R.id.menu_custom_btn).also {
            it.isVisible = viewModel.hasCustomBtn
        }
        menu.findItem(R.id.menu_more)?.setActionView(createBookInfoMoreButton())
        hideBookInfoSystemOverflowItems(menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    private fun createBookInfoMoreButton(): View {
        return ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(48.dpToPx(), 48.dpToPx())
            background = ThemeUtils.resolveDrawable(
                this@BookInfoActivity,
                android.R.attr.selectableItemBackgroundBorderless
            )
            contentDescription = getString(R.string.more)
            setImageResource(R.drawable.ic_more_vert)
            setColorFilter(getPrimaryTextColor(ColorUtils.isColorLight(bottomBackground)))
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            setOnClickListener {
                showBookInfoActionMenu(this)
            }
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        updateBookInfoMenuState(menu)
        hideBookInfoSystemOverflowItems(menu)
        return super.onMenuOpened(featureId, menu)
    }

    private fun updateBookInfoMenuState(menu: Menu) {
        menu.findItem(R.id.menu_can_update)?.isChecked =
            viewModel.bookData.value?.canUpdate ?: true
        menu.findItem(R.id.menu_split_long_chapter)?.isChecked =
            viewModel.bookData.value?.getSplitLongChapter() ?: true
        menu.findItem(R.id.menu_login)?.isVisible =
            !viewModel.bookSource?.loginUrl.isNullOrBlank()
        menu.findItem(R.id.menu_set_source_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_set_book_variable)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_can_update)?.isVisible =
            viewModel.bookSource != null
        menu.findItem(R.id.menu_split_long_chapter)?.isVisible =
            viewModel.bookData.value?.isLocalTxt ?: false
        menu.findItem(R.id.menu_upload)?.isVisible =
            viewModel.bookData.value?.isLocal ?: false
        menu.findItem(R.id.menu_delete_alert)?.isChecked =
            LocalConfig.bookInfoDeleteAlert
    }

    private fun hideBookInfoSystemOverflowItems(menu: Menu) {
        bookInfoPopupItemIds.forEach {
            menu.findItem(it)?.isVisible = false
        }
    }

    private fun showBookInfoActionMenu(anchor: View) {
        val menu = bookInfoMenu ?: return
        updateBookInfoMenuState(menu)
        hideBookInfoSystemOverflowItems(menu)
        NgActionPopup(this, buildBookInfoActionItems()) { action ->
            menu.findItem(action.itemId)?.let {
                onCompatOptionsItemSelected(it)
            }
        }.show(anchor)
    }

    private fun buildBookInfoActionItems(): List<NgActionPopupItem> {
        val book = viewModel.bookData.value
        val source = viewModel.bookSource
        return buildList {
            if (book?.isLocal == true) {
                add(NgActionPopupItem(R.id.menu_upload, R.string.upload_to_remote, R.drawable.ic_outline_cloud_24))
            }
            add(NgActionPopupItem(R.id.menu_refresh, R.string.refresh, R.drawable.ic_refresh_black_24dp))
            if (!source?.loginUrl.isNullOrBlank()) {
                add(NgActionPopupItem(R.id.menu_login, R.string.login, R.drawable.ic_lock_outline))
            }
            add(NgActionPopupItem(R.id.menu_top, R.string.to_top, R.drawable.ic_arrow_drop_up))
            if (source != null) {
                add(NgActionPopupItem(R.id.menu_set_source_variable, R.string.set_source_variable, R.drawable.ic_code, dividerBefore = true))
                add(NgActionPopupItem(R.id.menu_set_book_variable, R.string.set_book_variable, R.drawable.ic_code))
                add(NgActionPopupItem(R.id.menu_can_update, R.string.allow_update, R.drawable.ic_update, checked = book?.canUpdate ?: true))
            }
            add(NgActionPopupItem(R.id.menu_copy_book_url, R.string.copy_book_url, R.drawable.ic_copy, dividerBefore = true))
            add(NgActionPopupItem(R.id.menu_copy_toc_url, R.string.copy_toc_url, R.drawable.ic_copy))
            if (book?.isLocalTxt == true) {
                add(NgActionPopupItem(R.id.menu_split_long_chapter, R.string.split_long_chapter, R.drawable.ic_chapter_list, checked = book.getSplitLongChapter()))
            }
            add(NgActionPopupItem(R.id.menu_delete_alert, R.string.delete_alert, R.drawable.ic_outline_delete, checked = LocalConfig.bookInfoDeleteAlert, dividerBefore = true))
            add(NgActionPopupItem(R.id.menu_clear_cache, R.string.clear_cache, R.drawable.ic_clear_all))
            add(NgActionPopupItem(R.id.menu_log, R.string.log, R.drawable.ic_cfg_about, dividerBefore = true))
            add(NgActionPopupItem(R.id.menu_network_log, R.string.network_request_log, R.drawable.ic_network_check))
        }
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_more -> {
                item.actionView?.let(::showBookInfoActionMenu)
                return true
            }

            R.id.menu_custom_btn -> {
                viewModel.bookSource?.customButton?.let {
                    viewModel.getBook()?.let { book ->
                        SourceCallBack.callBackBtn(
                            this,
                            SourceCallBack.CLICK_CUSTOM_BUTTON,
                            viewModel.bookSource,
                            book,
                            null
                        )
                    }
                }
            }

            R.id.menu_edit -> {
                viewModel.getBook()?.let {
                    infoEditResult.launch {
                        putExtra("bookUrl", it.bookUrl)
                    }
                }
            }

            R.id.menu_share_it -> {
                viewModel.getBook()?.let {
                    val bookJson = GSON.toJson(it)
                    val shareStr = "${it.bookUrl}#$bookJson"
                    SourceCallBack.callBackBtn(
                        this,
                        SourceCallBack.CLICK_SHARE_BOOK,
                        viewModel.bookSource,
                        it,
                        null,
                        result = shareStr
                    ) {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.putExtra(Intent.EXTRA_TEXT, shareStr)
                        intent.type = "text/plain"
                        startActivity(Intent.createChooser(intent, it.name))
                    }
                }
            }

            R.id.menu_refresh -> {
                refreshBook()
            }

            R.id.menu_login -> viewModel.bookSource?.let {
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", it.bookSourceUrl)
                    putExtra("bookUrl", book?.bookUrl)
                }
            }

            R.id.menu_top -> viewModel.topBook()
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_set_book_variable -> setBookVariable()
            R.id.menu_copy_book_url -> viewModel.getBook()?.let {
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_COPY_BOOK_URL,
                    viewModel.bookSource,
                    it,
                    null,
                    result = it.bookUrl
                ) {
                    sendToClip(it.bookUrl)
                }
            }

            R.id.menu_copy_toc_url -> viewModel.getBook()?.let {
                SourceCallBack.callBackBtn(
                    this,
                    SourceCallBack.CLICK_COPY_TOC_URL,
                    viewModel.bookSource,
                    it,
                    null,
                    result = it.tocUrl
                ) {
                    sendToClip(it.tocUrl)
                }
            }

            R.id.menu_can_update -> {
                viewModel.getBook()?.let {
                    it.canUpdate = !it.canUpdate
                    if (viewModel.inBookshelf) {
                        if (!it.canUpdate) {
                            it.removeType(BookType.updateError)
                        }
                        viewModel.saveBook(it)
                    }
                }
            }

            R.id.menu_clear_cache -> viewModel.getBook()?.let {
                    SourceCallBack.callBackBtn(this, SourceCallBack.CLICK_CLEAR_CACHE, viewModel.bookSource, it, null) {
                        viewModel.clearCache(it)
                    }
                }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
            R.id.menu_split_long_chapter -> {
                upLoading(true)
                viewModel.getBook()?.let {
                    it.setSplitLongChapter(!item.isChecked)
                    viewModel.loadBookInfo(it, false)
                }
                item.isChecked = !item.isChecked
                if (!item.isChecked) longToastOnUi(R.string.need_more_time_load_content)
            }

            R.id.menu_delete_alert -> LocalConfig.bookInfoDeleteAlert = !item.isChecked
            R.id.menu_upload -> {
                viewModel.getBook()?.let { book ->
                    book.getRemoteUrl()?.let {
                        alert(R.string.draw, R.string.sure_upload) {
                            okButton {
                                upLoadBook(book)
                            }
                            cancelButton()
                        }
                    } ?: upLoadBook(book)
                }
            }
        }
        return super.onCompatOptionsItemSelected(item)
    }

    override fun observeLiveBus() {
        viewModel.actionLive.observe(this) {
            when (it) {
                "selectBooksDir" -> localBookTreeSelect.launch {
                    title = getString(R.string.select_book_folder)
                }
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

    private fun showBook(book: Book) = binding.run {
        showCover(book)
        (tvName as android.widget.TextView).text = book.name
        tvAuthor.text = book.getRealAuthor()
        tvOrigin.text = getString(R.string.origin_show, book.originName)
        tvLasted.text = getString(R.string.lasted_show, book.latestChapterTitle)
        showBookIntro(book)
        if (book.isWebFile) {
            llToc.gone()
            llCache.gone()
            tvLasted.text = getString(R.string.lasted_show, "下载中...")
        } else {
            llToc.visible()
            llCache.visible()
        }
        menuCustomBtn?.isVisible = viewModel.hasCustomBtn
        upTvBookshelf()
        upKinds(book)
        upGroup(book.group)
        observeCharacterPreview(book)
        viewModel.prepareOtherWorks(book)
        upCacheProgress(book)
        if (book.isLocal || book.getRealAuthor().isBlank()) {
            llOtherWorks.gone()
        } else {
            llOtherWorks.visible()
        }
    }

    private fun initOtherWorksView() = binding.run {
        rvOtherWorks.layoutManager = LinearLayoutManager(this@BookInfoActivity)
        rvOtherWorks.adapter = otherWorksAdapter
        rvOtherWorks.itemAnimator = null
        rvOtherWorks.isNestedScrollingEnabled = false
        rvOtherWorks.setEdgeEffectColor(accentColor)
        ivOtherWorksRefresh.setOnClickListener {
            viewModel.searchOtherWorks()
        }
    }

    private fun showOtherWorks(state: BookInfoViewModel.OtherWorksState) = binding.run {
        ivOtherWorksRefresh.isEnabled = state !is BookInfoViewModel.OtherWorksState.Loading
        when (state) {
            BookInfoViewModel.OtherWorksState.Idle -> {
                rvOtherWorks.gone()
                tvOtherWorksState.gone()
                resetOtherWorksDisplay()
            }

            BookInfoViewModel.OtherWorksState.Loading -> {
                rvOtherWorks.gone()
                tvOtherWorksState.visible()
                tvOtherWorksState.text = getString(R.string.book_other_works_loading)
                resetOtherWorksDisplay()
            }

            BookInfoViewModel.OtherWorksState.Empty -> {
                rvOtherWorks.gone()
                tvOtherWorksState.visible()
                tvOtherWorksState.text = getString(R.string.book_other_works_empty)
                resetOtherWorksDisplay()
            }

            is BookInfoViewModel.OtherWorksState.Success -> {
                tvOtherWorksState.gone()
                rvOtherWorks.visible()
                updateOtherWorksDisplay(state.books)
            }

            is BookInfoViewModel.OtherWorksState.Error -> {
                rvOtherWorks.gone()
                tvOtherWorksState.visible()
                tvOtherWorksState.text =
                    getString(R.string.book_other_works_error, state.message)
                resetOtherWorksDisplay()
            }
        }
    }

    private fun resetOtherWorksDisplay() {
        otherWorksRawBooks = emptyList()
        otherWorksGroupCounts = emptyMap()
        otherWorksGroupPrimaryBookUrls = emptyMap()
        expandedOtherWorksKeys.clear()
        otherWorksAdapter.setItems(emptyList())
    }

    private fun updateOtherWorksDisplay(books: List<SearchBook>) {
        otherWorksRawBooks = books
        rebuildOtherWorksGroups()
        otherWorksAdapter.setItems(buildOtherWorksItems())
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

    private fun observeCharacterPreview(book: Book) {
        val workKey = BookCharacterProfile.workKey(book.name, book.author)
        characterPreviewJob?.cancel()
        binding.ivCharacterAiAssistant.setOnClickListener {
            openCharacterCardAiAssistant(book)
        }
        binding.llCharacterOpen.setOnClickListener {
            openCharacterActivity(book, workKey)
        }
        binding.ivCharacterOpen.setOnClickListener {
            openCharacterActivity(book, workKey)
        }
        characterPreviewJob = lifecycleScope.launch {
            appDb.bookCharacterDao.flowCharacters(workKey)
                .catch {
                    AppLog.put("角色卡预览加载失败\n${it.localizedMessage}", it)
                }
                .flowOn(IO)
                .collect { characters ->
                    showCharacterPreview(book, workKey, characters)
                }
        }
    }

    private fun showCharacterPreview(book: Book, workKey: String, characters: List<BookCharacter>) {
        binding.llCharacters.visible()
        binding.tvCharacterCount.text = getString(R.string.character_count_format, characters.size)
        binding.llCharacterPreview.removeAllViews()
        if (characters.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.book_character_empty)
                setTextColor(getColor(R.color.tv_text_summary))
                textSize = 13f
                includeFontPadding = false
                setPadding(0, 6.dpToPx(), 0, 6.dpToPx())
                setOnClickListener { openCharacterActivity(book, workKey) }
            }
            binding.llCharacterPreview.addView(emptyView)
            return
        }
        characters.take(6).forEach { character ->
            binding.llCharacterPreview.addView(createCharacterPreviewView(character))
        }
    }

    private fun createCharacterPreviewView(character: BookCharacter): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            background = getDrawable(R.drawable.ng_bg_book_detail_card)
            setPadding(8.dpToPx(), 8.dpToPx(), 10.dpToPx(), 8.dpToPx())
            layoutParams = LinearLayout.LayoutParams(150.dpToPx(), 64.dpToPx()).apply {
                marginEnd = 8.dpToPx()
            }
            setOnClickListener {
                book?.let { openCharacterActivity(it, BookCharacterProfile.workKey(it.name, it.author)) }
            }
            addView(TextView(this@BookInfoActivity).apply {
                text = character.name.firstOrNull()?.toString().orEmpty()
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundResource(character.avatarBackground())
                layoutParams = LinearLayout.LayoutParams(38.dpToPx(), 38.dpToPx()).apply {
                    marginEnd = 8.dpToPx()
                }
            })
            addView(LinearLayout(this@BookInfoActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    1f
                )
                addView(TextView(this@BookInfoActivity).apply {
                    text = character.name
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(getColor(R.color.primaryText))
                    textSize = 13f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                })
                val intro = character.displayIntro()
                    ?: BookCharacterLabels.roleLabel(this@BookInfoActivity, character.roleTag)
                addView(TextView(this@BookInfoActivity).apply {
                    text = intro
                    includeFontPadding = false
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(getColor(R.color.tv_text_summary))
                    textSize = 11f
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 5.dpToPx()
                    }
                })
            })
        }
    }

    override fun isInBookshelf(book: SearchBook): Boolean {
        return viewModel.isInBookShelf(book)
    }

    override fun showBookInfo(name: String, author: String, bookUrl: String) {
        startActivity<BookInfoActivity> {
            putExtra("bookUrl", bookUrl)
        }
    }

    override fun showAllSources(book: SearchBook) {
        val key = otherWorksGroupKey(book)
        if ((otherWorksGroupCounts[key] ?: 1) <= 1) {
            return
        }
        if (expandedOtherWorksKeys.add(key)) {
            otherWorksAdapter.setItems(buildOtherWorksItems())
        }
    }

    private fun BookCharacter.avatarBackground(): Int {
        return when (gender) {
            BookCharacter.Gender.MALE -> R.drawable.bg_character_avatar_male
            BookCharacter.Gender.FEMALE -> R.drawable.bg_character_avatar_female
            else -> R.drawable.bg_character_avatar_unknown
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
        val contextAttachment = GSON.toJson(
            mapOf(
                "id" to "book_detail_character_card",
                "title" to "书籍详情：${book.name}",
                "subtitle" to book.getRealAuthor(),
                "prompt" to buildBookDetailAiContextPrompt(book, workKey)
            )
        )
        startActivity<AiChatActivity> {
            putStringArrayListExtra(
                AiChatActivity.EXTRA_LOADED_SKILL_IDS,
                arrayListOf(AiSkillRegistry.SKILL_CHARACTER_CARD_GENERATE)
            )
            putStringArrayListExtra(
                AiChatActivity.EXTRA_CONTEXT_ATTACHMENTS,
                arrayListOf(contextAttachment)
            )
            putExtra(AiChatActivity.EXTRA_EXPAND_SUGGESTIONS, true)
        }
    }

    private fun buildBookDetailAiContextPrompt(book: Book, workKey: String): String {
        val intro = book.getDisplayIntro().orEmpty().toPlainBookIntro().limitAiContextText(1800)
        return buildString {
            appendLine("这是从书籍详情页进入 AI 助理时附带的当前书籍上下文。")
            appendLine("这是专用入口，目标书籍已经明确，不需要再次询问用户是哪本书。")
            appendLine()
            appendLine("书名：${book.name}")
            appendLine("作者：${book.getRealAuthor()}")
            appendLine("book_url：${book.bookUrl}")
            appendLine("work_key：$workKey")
            appendLine("书源：${book.originName}")
            book.kind?.takeIf { it.isNotBlank() }?.let { appendLine("分类：$it") }
            book.wordCount?.takeIf { it.isNotBlank() }?.let { appendLine("字数：$it") }
            appendLine("目录章节数：${book.totalChapterNum}")
            book.durChapterTitle?.takeIf { it.isNotBlank() }?.let {
                appendLine("当前阅读章节：第 ${book.durChapterIndex + 1} 章 $it")
            }
            book.latestChapterTitle?.takeIf { it.isNotBlank() }?.let {
                appendLine("最新章节：$it")
            }
            if (intro.isNotBlank()) {
                appendLine()
                appendLine("书籍简介：")
                appendLine(intro)
            }
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
            view?.post {
                binding.tvIntroContainer.requestLayout()
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
                webView.addJavascriptInterface(WebCacheManager, nameCache)
                viewModel.bookSource?.let {
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
                binding.tvIntroContainer.removeAllViews()
                binding.tvIntroContainer.addView(webView)
            }
            val bookUrl = viewModel.getBook()?.bookUrl
                ?.takeIf { it.startsWith("http", true) }
                ?.substringBefore(",")
            webView.loadDataWithBaseURL(bookUrl, html, "text/html", "utf-8", bookUrl)
            return
        }
        if (!initIntroView || pooledWebView != null) {
            destroyWeb()
            binding.tvIntroContainer.removeAllViews()
            binding.tvIntroContainer.addView(introTextView)
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

    private fun upKinds(book: Book) = binding.run {
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
            if (kinds.isEmpty()) {
                lbKind.gone()
            } else {
                lbKind.visible()
                val source = viewModel.bookSource
                if (source == null) {
                    lbKind.setLabels(kinds)
                    return@launch
                }
                lbKind.setLabels(
                    kinds,
                    { kind ->
                        SourceCallBack.callBackBtn(
                            this@BookInfoActivity,
                            SourceCallBack.CLICK_BOOK_LABEL,
                            source,
                            book,
                            null,
                            result = kind
                        ) {
                            SearchActivity.start(this@BookInfoActivity, source, kind)
                        }
                    },
                    { kind ->
                        SourceCallBack.callBackBtn(
                            this@BookInfoActivity,
                            SourceCallBack.LONG_CLICK_BOOK_LABEL,
                            source,
                            book,
                            null,
                            result = kind
                        )
                        true
                    }
                )
            }
        }
    }

    private fun showCover(book: Book) {
        binding.ivCover.load(book, false)
    }

    private fun upLoading(isLoading: Boolean, chapterList: List<BookChapter>? = null) {
        when {
            isLoading -> {
                binding.tvToc.text = getString(R.string.toc_s, getString(R.string.loading))
            }

            chapterList.isNullOrEmpty() -> {
                binding.tvToc.text = getString(
                    R.string.toc_s,
                    getString(R.string.error_load_toc)
                )
                binding.tvLasted.text = getString(R.string.lasted_show, book?.latestChapterTitle)
            }

            else -> {
                book?.let {
                    binding.tvToc.text = getString(R.string.toc_s, it.durChapterTitle)
                    binding.tvLasted.text = getString(R.string.lasted_show, it.latestChapterTitle)
                }
            }
        }
    }

    private fun upTvBookshelf() {
        if (viewModel.inBookshelf) {
            binding.tvShelf.text = getString(R.string.remove_from_bookshelf)
        } else {
            binding.tvShelf.text = getString(R.string.add_to_bookshelf)
        }
        editMenuItem?.isVisible = viewModel.inBookshelf
    }

    private fun upGroup(groupId: Long) {
        viewModel.loadGroup(groupId) {
            if (it.isNullOrEmpty()) {
                binding.tvGroup.text = if (book?.isLocal == true) {
                    getString(R.string.group_s, getString(R.string.local_no_group))
                } else {
                    getString(R.string.group_s, getString(R.string.no_group))
                }
            } else {
                binding.tvGroup.text = getString(R.string.group_s, it)
            }
        }
    }

    private fun initViewEvent() = binding.run {
        ivCover.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    ChangeCoverDialog(it.name, it.author)
                )
            }
        }
        ivCover.setOnLongClickListener {
            viewModel.getBook()?.getDisplayCover()?.let { path ->
                showDialogFragment(PhotoDialog(path, isBook = true))
            }
            true
        }
        tvRead.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isWebFile) {
                    showWebFileDownloadAlert {
                        readBook(it)
                    }
                } else {
                    readBook(book)
                }
            }
        }
        tvShelf.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (viewModel.inBookshelf) {
                    deleteBook()
                } else {
                    if (book.isWebFile) {
                        showWebFileDownloadAlert()
                    } else {
                        viewModel.addToBookshelf {
                            upTvBookshelf()
                        }
                    }
                }
            }
        }
        tvOrigin.setOnClickListener {
            viewModel.getBook()?.let { book ->
                if (book.isLocal) return@let
                if (!appDb.bookSourceDao.has(book.origin)) {
                    toastOnUi(R.string.error_no_source)
                    return@let
                }
                editSourceResult.launch {
                    putExtra("sourceUrl", book.origin)
                }
            }
        }
        tvChangeSource.setOnClickListener {
            viewModel.getBook()?.let { book ->
                showDialogFragment(ChangeBookSourceDialog(book.name, book.author))
            }
        }
        tvTocView.setOnClickListener {
            if (viewModel.chapterListData.value.isNullOrEmpty()) {
                toastOnUi(R.string.chapter_list_empty)
                return@setOnClickListener
            }
            viewModel.getBook()?.let { book ->
                if (!viewModel.inBookshelf) {
                    viewModel.saveBook(book) { //点击目录会保存book
                        viewModel.saveChapterList {
                            openChapterList()
                        }
                    }
                } else {
                    openChapterList()
                }
            }
        }
        tvCacheBook.setOnClickListener {
            viewModel.getBook()?.let { book ->
                startCacheBook(book)
            }
        }
        tvChangeGroup.setOnClickListener {
            viewModel.getBook()?.let {
                showDialogFragment(
                    GroupSelectDialog(it.group)
                )
            }
        }
        tvAuthor.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.CLICK_AUTHOR,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.author
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.author)
                }
            }
        }
        tvAuthor.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_AUTHOR,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.author
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.author)
                }
            }
            true
        }
        tvName.setOnClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.CLICK_BOOK_NAME,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.name
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.name)
                }
            }
        }
        tvName.setOnLongClickListener {
            viewModel.getBook(false)?.let { book ->
                SourceCallBack.callBackBtn(
                    this@BookInfoActivity,
                    SourceCallBack.LONG_CLICK_BOOK_NAME,
                    viewModel.bookSource,
                    book,
                    null,
                    result = book.name
                ) {
                    SearchActivity.start(this@BookInfoActivity, book.name)
                }
            }
            true
        }
        refreshLayout?.setOnRefreshListener {
            refreshLayout.isRefreshing = false
            refreshBook()
        }
    }

    private fun upCacheProgress(
        book: Book? = viewModel.getBook(false),
        chapterList: List<BookChapter>? = viewModel.chapterListData.value
    ) = binding.run {
        if (book == null || book.isWebFile) {
            cacheProgressJob?.cancel()
            llCache.gone()
            cacheProgressBookUrl = null
            cacheProgressCached = 0
            cacheProgressTotal = 0
            return@run
        }
        llCache.visible()
        val chapters = chapterList.orEmpty()
        val total = when {
            chapters.isNotEmpty() -> chapters.size
            book.totalChapterNum > 0 -> book.totalChapterNum
            else -> 0
        }
        val cacheEnabled = !book.isLocal && total > 0
        tvCacheBook.isEnabled = cacheEnabled
        tvCacheBook.alpha = if (cacheEnabled) 1f else 0.45f
        if (book.isLocal) {
            cacheProgressJob?.cancel()
            showCacheProgress(total, total)
            return@run
        }
        val isSameBook = cacheProgressBookUrl == book.bookUrl
        if (!isSameBook) {
            cacheProgressJob?.cancel()
            showCacheProgress(0, total)
        } else if (cacheProgressTotal != total) {
            cacheProgressJob?.cancel()
            showCacheProgress(cacheProgressCached.coerceAtMost(total), total)
        } else if (cacheProgressJob?.isActive == true) {
            return@run
        }
        if (chapters.isEmpty()) {
            return@run
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

    private fun showCacheProgress(cached: Int, total: Int) = binding.run {
        cacheProgressBookUrl = viewModel.getBook(false)?.bookUrl
        cacheProgressCached = cached
        cacheProgressTotal = total
        tvCache.text = getString(R.string.book_cache_progress, cached, total)
        tvCache.setProgress(cached, total)
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

    @SuppressLint("InflateParams")
    private fun deleteBook() {
        viewModel.getBook()?.let { book ->
            if (LocalConfig.bookInfoDeleteAlert) {
                alert(
                    titleResource = R.string.draw,
                    messageResource = R.string.sure_del
                ) {
                    var checkBox: CheckBox? = null
                    if (book.isLocal) {
                        checkBox = CheckBox(this@BookInfoActivity).apply {
                            setText(R.string.delete_book_file)
                            isChecked = LocalConfig.deleteBookOriginal
                        }
                        val view = LinearLayout(this@BookInfoActivity).apply {
                            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                            addView(checkBox)
                        }
                        customView { view }
                    }
                    yesButton {
                        if (checkBox != null) {
                            LocalConfig.deleteBookOriginal = checkBox.isChecked
                        }
                        SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, viewModel.bookSource, book) //确认后删除书架
                        viewModel.delBook(LocalConfig.deleteBookOriginal) {
                            setResult(RESULT_OK)
                            finish()
                        }
                    }
                    noButton()
                }
            } else {
                SourceCallBack.callBackBook(SourceCallBack.DEL_BOOK_SHELF, viewModel.bookSource, book) //点按钮直接删除书架
                viewModel.delBook(LocalConfig.deleteBookOriginal) {
                    setResult(RESULT_OK)
                    finish()
                }
            }
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
        selector(
            R.string.download_and_import_file,
            webFiles
        ) { _, webFile, _ ->
            if (webFile.isSupported) {
                /* import */
                viewModel.importOrDownloadWebFile<Book>(webFile) {
                    onClick?.invoke(it)
                }
            } else if (webFile.isSupportDecompress) {
                /* 解压筛选后再选择导入项 */
                viewModel.importOrDownloadWebFile<Uri>(webFile) { uri ->
                    viewModel.getArchiveFilesName(uri) { fileNames ->
                        if (fileNames.size == 1) {
                            viewModel.importArchiveBook(uri, fileNames[0]) {
                                onClick?.invoke(it)
                            }
                        } else {
                            showDecompressFileImportAlert(uri, fileNames, onClick)
                        }
                    }
                }
            } else {
                alert(
                    title = getString(R.string.draw),
                    message = getString(R.string.file_not_supported, webFile.name)
                ) {
                    neutralButton(R.string.open_fun) {
                        /* download only */
                        viewModel.importOrDownloadWebFile<Uri>(webFile) {
                            openFileUri(it, "*/*")
                        }
                    }
                    noButton()
                }
            }
        }
    }

    private fun showDecompressFileImportAlert(
        archiveFileUri: Uri,
        fileNames: List<String>,
        success: ((Book) -> Unit)? = null,
    ) {
        if (fileNames.isEmpty()) {
            toastOnUi(R.string.unsupport_archivefile_entry)
            return
        }
        selector(
            R.string.import_select_book,
            fileNames
        ) { _, name, _ ->
            viewModel.importArchiveBook(archiveFileUri, name) {
                success?.invoke(it)
            }
        }
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

    private fun startReadActivity(book: Book) {
        when {
            book.isAudio -> readBookResult.launch(
                Intent(this, AudioPlayActivity::class.java)
                    .putExtra("bookUrl", book.bookUrl)
                    .putExtra("inBookshelf", viewModel.inBookshelf)
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
            showCover(book)
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            }
        }
    }

    override fun upGroup(requestCode: Int, groupId: Long) {
        upGroup(groupId)
        viewModel.getBook()?.let { book ->
            book.group = groupId
            if (viewModel.inBookshelf) {
                viewModel.saveBook(book)
            } else if (groupId > 0) {
                viewModel.addToBookshelf {
                    upTvBookshelf()
                }
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
