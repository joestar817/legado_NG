package io.legado.app.ui.book.source.edit

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.rule.BookInfoRule
import io.legado.app.data.entities.rule.ContentRule
import io.legado.app.data.entities.rule.ExploreRule
import io.legado.app.data.entities.rule.SearchRule
import io.legado.app.data.entities.rule.TocRule
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.source.edit.SourceEditCodeHighlighter
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.keyboard.KeyboardAssistsConfig
import io.legado.app.ui.widget.text.EditEntity
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.utils.GSON
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.shareWithQr
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.takePersistableReadPermission
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.ArrayDeque

class BookSourceEditActivity :
    VMBaseActivity<ComposeActivityBinding, BookSourceEditViewModel>(imageBg = false),
    VariableDialog.Callback {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<BookSourceEditViewModel>()

    private val sourceEntities: ArrayList<EditEntity> = ArrayList()
    private val searchEntities: ArrayList<EditEntity> = ArrayList()
    private val exploreEntities: ArrayList<EditEntity> = ArrayList()
    private val infoEntities: ArrayList<EditEntity> = ArrayList()
    private val tocEntities: ArrayList<EditEntity> = ArrayList()
    private val contentEntities: ArrayList<EditEntity> = ArrayList()
    private var controls by mutableStateOf(BookSourceEditControls())
    private var selectedTab by mutableIntStateOf(0)
    private var sourceRevision by mutableIntStateOf(0)
    private var fieldValueRevision by mutableIntStateOf(0)
    private var autoComplete by mutableStateOf(false)
    private var focusedField by mutableStateOf<BookSourceEditorSelection?>(null)
    private var pendingExit by mutableStateOf(false)
    private var groupSelectorVisible by mutableStateOf(false)
    private var groupOptions by mutableStateOf<List<String>>(emptyList())
    private var urlOptionEditorVisible by mutableStateOf(false)
    private var keyboardAssists by mutableStateOf<List<KeyboardAssist>>(emptyList())
    private var keyboardRowCount by mutableIntStateOf(AppConfig.showBoardLine)
    private var forceFinish = false
    private var editingFieldKey: String? = null
    private val undoHistory = mutableMapOf<String, ArrayDeque<FieldSnapshot>>()
    private val redoHistory = mutableMapOf<String, ArrayDeque<FieldSnapshot>>()

    //    private val reviewEntities: ArrayList<EditEntity> = ArrayList()
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        viewModel.importSource(it) { source ->
            upSourceView(source)
        }
    }
    private val selectDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            if (it.isContentScheme()) {
                sendText(it.toString())
            } else {
                sendText(it.path.toString())
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        intent.getStringExtra("sourceUrl")?.let { sourceUrl ->
            if (appDb.bookSourceDao.hasJsSource(sourceUrl)) {
                startActivity<JsSourceEditActivity> {
                    putExtra("sourceUrl", sourceUrl)
                }
                super.finish()
                return
            }
        }
        observeKeyboardAssists()
        autoComplete = viewModel.autoComplete
        initView()
        upSourceView(null)
        viewModel.initData(intent) {
            upSourceView(viewModel.bookSource)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!isFinishing && !LocalConfig.ruleHelpVersionIsLast) {
            showHelp("ruleHelp")
        }
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val key = editingFieldKey
        editingFieldKey = null
        if (result.resultCode == RESULT_OK && key != null) {
            result.data?.getStringExtra("text")?.let { text ->
                val cursor = result.data?.getIntExtra("cursorPosition", text.length)
                    ?.coerceIn(0, text.length)
                    ?: text.length
                replaceFieldValue(key, text, cursor, cursor)
            }
        }
    }

    private fun onFullEditClicked() {
        focusedField?.let { focused ->
            val entity = findEditEntity(focused.key) ?: return@let
            val currentText = entity.value.orEmpty()
            editingFieldKey = focused.key
            val intent = Intent(this, CodeEditActivity::class.java).apply {
                putExtra("text", currentText)
                putExtra("title", entity.hint)
                putExtra("cursorPosition", focused.end.coerceIn(0, currentText.length))
                SourceEditCodeHighlighter.languageNameOf(focused.key)?.let {
                    putExtra("languageName", it)
                }
            }
            textEditLauncher.launch(intent)
        } ?: run {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
        }
    }

    private fun onEditorAction(itemId: Int) {
        when (itemId) {
            R.id.menu_fullscreen_edit -> onFullEditClicked()

            R.id.menu_save -> viewModel.save(getSource()) {
                setResult(RESULT_OK, Intent().putExtra("origin", it.bookSourceUrl))
                finish()
            }

            R.id.menu_debug_source -> viewModel.save(getSource()) { source ->
                startActivity<BookSourceDebugActivity> {
                    putExtra("key", source.bookSourceUrl)
                }
            }

            R.id.menu_clear_cookie -> viewModel.clearCookie(getSource().bookSourceUrl)
            R.id.menu_auto_complete -> {
                autoComplete = !autoComplete
                viewModel.autoComplete = autoComplete
            }
            R.id.menu_copy_source -> sendToClip(GSON.toJson(getSource()))
            R.id.menu_paste_source -> viewModel.pasteSource { upSourceView(it) }
            R.id.menu_qr_code_camera -> qrCodeResult.launch()
            R.id.menu_share_str -> share(GSON.toJson(getSource()))
            R.id.menu_share_qr -> shareWithQr(
                GSON.toJson(getSource()),
                getString(R.string.share_book_source),
                ErrorCorrectionLevel.L
            )

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
            R.id.menu_help -> showHelp("ruleHelp")
            R.id.menu_login -> viewModel.save(getSource()) { source ->
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }
            }

            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_search -> viewModel.save(getSource()) { source ->
                SearchActivity.start(this, source)
            }

        }
    }

    private fun initView() {
        binding.composeView.setContent {
            NgAppTheme {
                BookSourceEditScreen(
                    controls = controls,
                    selectedTab = selectedTab,
                    editEntities = editEntitiesFor(selectedTab),
                    sourceRevision = sourceRevision,
                    fieldValueRevision = fieldValueRevision,
                    editEntityMaxLine = Int.MAX_VALUE,
                    autoComplete = autoComplete,
                    focusedField = focusedField,
                    keyboardAssists = keyboardAssists,
                    keyboardRowCount = keyboardRowCount,
                    keyboardHelpActions = keyboardHelpActions(),
                    onControlsChange = { controls = it },
                    onTabSelected = ::setEditEntities,
                    onBack = ::finish,
                    onAction = ::onEditorAction,
                    onPrepareOverflow = { !getSource().loginUrl.isNullOrBlank() },
                    onFieldValueChange = ::onFieldValueChange,
                    onFieldFocused = { key, start, end ->
                        focusedField = BookSourceEditorSelection(key, start, end)
                    },
                    onKeyboardHelpAction = ::onHelpActionSelect,
                    onOpenKeyboardConfig = ::openKeyboardConfig,
                    onInsertText = ::sendText,
                    onUndo = ::onUndoClicked,
                    onRedo = ::onRedoClicked,
                )
                if (pendingExit) {
                    BookSourceExitDialog(
                        onDismiss = { pendingExit = false },
                        onDiscard = {
                            pendingExit = false
                            forceFinish = true
                            finish()
                        },
                    )
                }
                if (groupSelectorVisible) {
                    BookSourceGroupSelectorDialog(
                        groups = groupOptions,
                        onDismiss = { groupSelectorVisible = false },
                        onSelected = { group ->
                            groupSelectorVisible = false
                            sendText(group)
                        },
                    )
                }
                if (urlOptionEditorVisible) {
                    BookSourceUrlOptionDialog(
                        charsets = AppConst.charsets,
                        onDismiss = { urlOptionEditorVisible = false },
                        onConfirm = { input ->
                            urlOptionEditorVisible = false
                            insertUrlOption(input)
                        },
                    )
                }
            }
        }
    }

    override fun finish() {
        if (forceFinish) {
            super.finish()
            return
        }
        val source = getSource()
        if (!source.equal(viewModel.bookSource ?: BookSource())) {
            pendingExit = true
        } else {
            super.finish()
        }
    }

    private fun editEntitiesFor(tabPosition: Int): List<EditEntity> {
        return when (tabPosition) {
            1 -> searchEntities
            2 -> exploreEntities
            3 -> infoEntities
            4 -> tocEntities
            5 -> contentEntities
//            6 -> reviewEntities
            else -> sourceEntities
        }
    }

    private fun findEditEntity(key: String): EditEntity? {
        return sequenceOf(
            sourceEntities,
            searchEntities,
            exploreEntities,
            infoEntities,
            tocEntities,
            contentEntities,
        ).flatMap(List<EditEntity>::asSequence).firstOrNull { it.key == key }
    }

    private fun onFieldValueChange(key: String, value: String, start: Int, end: Int) {
        val entity = findEditEntity(key) ?: return
        val current = entity.value.orEmpty()
        if (current != value) {
            pushHistory(
                undoHistory,
                key,
                FieldSnapshot(
                    current,
                    focusedField?.takeIf { it.key == key }?.start ?: current.length,
                    focusedField?.takeIf { it.key == key }?.end ?: current.length,
                ),
            )
            redoHistory.remove(key)
            entity.value = value
        }
        focusedField = BookSourceEditorSelection(
            key,
            start.coerceIn(0, value.length),
            end.coerceIn(0, value.length),
        )
    }

    private fun replaceFieldValue(
        key: String,
        value: String,
        start: Int,
        end: Int,
        recordHistory: Boolean = true,
    ) {
        val entity = findEditEntity(key) ?: return
        val current = entity.value.orEmpty()
        if (current != value && recordHistory) {
            pushHistory(
                undoHistory,
                key,
                FieldSnapshot(
                    current,
                    focusedField?.takeIf { it.key == key }?.start ?: current.length,
                    focusedField?.takeIf { it.key == key }?.end ?: current.length,
                ),
            )
            redoHistory.remove(key)
        }
        entity.value = value
        focusedField = BookSourceEditorSelection(
            key,
            start.coerceIn(0, value.length),
            end.coerceIn(0, value.length),
        )
        fieldValueRevision += 1
    }

    private fun pushHistory(
        histories: MutableMap<String, ArrayDeque<FieldSnapshot>>,
        key: String,
        snapshot: FieldSnapshot,
    ) {
        val history = histories.getOrPut(key) { ArrayDeque() }
        if (history.peekLast() != snapshot) history.addLast(snapshot)
        while (history.size > MAX_FIELD_HISTORY) history.removeFirst()
    }

    private fun setEditEntities(tabPosition: Int) {
        selectedTab = tabPosition
        focusedField = null
    }

    private fun upSourceView(bookSource: BookSource?) {
        val bs = bookSource ?: BookSource()
        controls = BookSourceEditControls(
            typeIndex = when (bs.bookSourceType) {
                BookSourceType.video -> 4
                BookSourceType.file -> 3
                BookSourceType.image -> 2
                BookSourceType.audio -> 1
                else -> 0
            },
            enabled = bs.enabled,
            enabledExplore = bs.enabledExplore,
            enabledCookieJar = bs.enabledCookieJar ?: false,
            eventListener = bs.eventListener,
            customButton = bs.customButton,
        )
        // 基本信息
        sourceEntities.clear()
        sourceEntities.apply {
            add(EditEntity("bookSourceUrl", bs.bookSourceUrl, R.string.source_url))
            add(EditEntity("bookSourceName", bs.bookSourceName, R.string.source_name))
            add(EditEntity("bookSourceGroup", bs.bookSourceGroup, R.string.source_group))
            add(EditEntity("bookSourceComment", bs.bookSourceComment, R.string.comment))
            add(EditEntity("loginUrl", bs.loginUrl, R.string.login_url))
            add(EditEntity("loginUi", bs.loginUi, R.string.login_ui))
            add(EditEntity("loginCheckJs", bs.loginCheckJs, R.string.login_check_js))
            add(EditEntity("coverDecodeJs", bs.coverDecodeJs, R.string.cover_decode_js))
            add(EditEntity("bookUrlPattern", bs.bookUrlPattern, R.string.book_url_pattern))
            add(EditEntity("header", bs.header, R.string.source_http_header))
            add(EditEntity("variableComment", bs.variableComment, R.string.variable_comment))
            add(EditEntity("concurrentRate", bs.concurrentRate, R.string.concurrent_rate))
            add(EditEntity("jsLib", bs.jsLib, "jsLib"))
        }
        // 搜索
        val sr = bs.getSearchRule()
        searchEntities.clear()
        searchEntities.apply {
            add(EditEntity("searchUrl", bs.searchUrl, R.string.r_search_url))
            add(EditEntity("checkKeyWord", sr.checkKeyWord, R.string.check_key_word))
            add(EditEntity("bookList", sr.bookList, R.string.r_book_list))
            add(EditEntity("name", sr.name, R.string.r_book_name))
            add(EditEntity("author", sr.author, R.string.r_author))
            add(EditEntity("kind", sr.kind, R.string.rule_book_kind))
            add(EditEntity("wordCount", sr.wordCount, R.string.rule_word_count))
            add(EditEntity("lastChapter", sr.lastChapter, R.string.rule_last_chapter))
            add(EditEntity("intro", sr.intro, R.string.rule_book_intro))
            add(EditEntity("coverUrl", sr.coverUrl, R.string.rule_cover_url))
            add(EditEntity("bookUrl", sr.bookUrl, R.string.r_book_url))
        }
        // 发现
        val er = bs.getExploreRule()
        exploreEntities.clear()
        exploreEntities.apply {
            add(EditEntity("exploreUrl", bs.exploreUrl, R.string.r_find_url))
            add(EditEntity("bookList", er.bookList, R.string.r_book_list))
            add(EditEntity("name", er.name, R.string.r_book_name))
            add(EditEntity("author", er.author, R.string.r_author))
            add(EditEntity("kind", er.kind, R.string.rule_book_kind))
            add(EditEntity("wordCount", er.wordCount, R.string.rule_word_count))
            add(EditEntity("lastChapter", er.lastChapter, R.string.rule_last_chapter))
            add(EditEntity("intro", er.intro, R.string.rule_book_intro))
            add(EditEntity("coverUrl", er.coverUrl, R.string.rule_cover_url))
            add(EditEntity("bookUrl", er.bookUrl, R.string.r_book_url))
        }
        // 详情页
        val ir = bs.getBookInfoRule()
        infoEntities.clear()
        infoEntities.apply {
            add(EditEntity("init", ir.init, R.string.rule_book_info_init))
            add(EditEntity("name", ir.name, R.string.r_book_name))
            add(EditEntity("author", ir.author, R.string.r_author))
            add(EditEntity("kind", ir.kind, R.string.rule_book_kind))
            add(EditEntity("wordCount", ir.wordCount, R.string.rule_word_count))
            add(EditEntity("lastChapter", ir.lastChapter, R.string.rule_last_chapter))
            add(EditEntity("intro", ir.intro, R.string.rule_book_intro))
            add(EditEntity("coverUrl", ir.coverUrl, R.string.rule_cover_url))
            add(EditEntity("tocUrl", ir.tocUrl, R.string.rule_toc_url))
            add(EditEntity("canReName", ir.canReName, R.string.rule_can_re_name))
            add(EditEntity("downloadUrls", ir.downloadUrls, R.string.download_url_rule))
        }
        // 目录页
        val tr = bs.getTocRule()
        tocEntities.clear()
        tocEntities.apply {
            add(EditEntity("preUpdateJs", tr.preUpdateJs, R.string.pre_update_js))
            add(EditEntity("chapterList", tr.chapterList, R.string.rule_chapter_list))
            add(EditEntity("chapterName", tr.chapterName, R.string.rule_chapter_name))
            add(EditEntity("chapterUrl", tr.chapterUrl, R.string.rule_chapter_url))
            add(EditEntity("formatJs", tr.formatJs, R.string.format_js_rule))
            add(EditEntity("isVolume", tr.isVolume, R.string.rule_is_volume))
            add(EditEntity("updateTime", tr.updateTime, R.string.rule_update_time))
            add(EditEntity("isVip", tr.isVip, R.string.rule_is_vip))
            add(EditEntity("isPay", tr.isPay, R.string.rule_is_pay))
            add(EditEntity("nextTocUrl", tr.nextTocUrl, R.string.rule_next_toc_url))
        }
        // 正文页
        val cr = bs.getContentRule()
        contentEntities.clear()
        contentEntities.apply {
            add(EditEntity("content", cr.content, R.string.rule_book_content))
            add(EditEntity("nextContentUrl", cr.nextContentUrl, R.string.rule_next_content))
            add(EditEntity("subContent", cr.subContent, R.string.rule_sub_content))
            add(EditEntity("replaceRegex", cr.replaceRegex, R.string.rule_replace_regex))
            add(EditEntity("title", cr.title, R.string.rule_chapter_name))
            add(EditEntity("sourceRegex", cr.sourceRegex, R.string.rule_source_regex))
            add(EditEntity("imageStyle", cr.imageStyle, R.string.rule_image_style))
            add(EditEntity("imageDecode", cr.imageDecode, R.string.rule_image_decode))
            add(EditEntity("webJs", cr.webJs, R.string.rule_web_js))
            add(EditEntity("payAction", cr.payAction, R.string.rule_pay_action))
            add(EditEntity("callBackJs", cr.callBackJs, R.string.rule_call_back))
        }
        // 段评
//        val rr = bs.getReviewRule()
//        reviewEntities.clear()
//        reviewEntities.apply {
//            add(EditEntity("reviewUrl", rr.reviewUrl, R.string.rule_review_url))
//            add(EditEntity("avatarRule", rr.avatarRule, R.string.rule_avatar))
//            add(EditEntity("contentRule", rr.contentRule, R.string.rule_review_content))
//            add(EditEntity("postTimeRule", rr.postTimeRule, R.string.rule_post_time))
//            add(EditEntity("reviewQuoteUrl", rr.reviewQuoteUrl, R.string.rule_review_quote))
//            add(EditEntity("voteUpUrl", rr.voteUpUrl, R.string.review_vote_up))
//            add(EditEntity("voteDownUrl", rr.voteDownUrl, R.string.review_vote_down))
//            add(EditEntity("postReviewUrl", rr.postReviewUrl, R.string.post_review_url))
//            add(EditEntity("postQuoteUrl", rr.postQuoteUrl, R.string.post_quote_url))
//            add(EditEntity("deleteUrl", rr.deleteUrl, R.string.delete_review_url))
//        }
        selectedTab = 0
        focusedField = null
        undoHistory.clear()
        redoHistory.clear()
        sourceRevision += 1
        fieldValueRevision += 1
    }

    private fun getSource(): BookSource {
        val source = viewModel.bookSource?.copy() ?: BookSource()
        source.enabled = controls.enabled
        source.enabledExplore = controls.enabledExplore
        source.enabledCookieJar = controls.enabledCookieJar
        source.bookSourceType = when (controls.typeIndex) {
            4 -> BookSourceType.video
            3 -> BookSourceType.file
            2 -> BookSourceType.image
            1 -> BookSourceType.audio
            else -> BookSourceType.default
        }
        source.eventListener = controls.eventListener
        source.customButton = controls.customButton
        val searchRule = SearchRule()
        val exploreRule = ExploreRule()
        val bookInfoRule = BookInfoRule()
        val tocRule = TocRule()
        val contentRule = ContentRule()
//        val reviewRule = ReviewRule()
        sourceEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "bookSourceUrl" -> source.bookSourceUrl = it.value ?: ""
                "bookSourceName" -> source.bookSourceName = it.value ?: ""
                "bookSourceGroup" -> source.bookSourceGroup = it.value
                "loginUrl" -> source.loginUrl = it.value
                "loginUi" -> source.loginUi = it.value
                "loginCheckJs" -> source.loginCheckJs = it.value
                "coverDecodeJs" -> source.coverDecodeJs = it.value
                "bookUrlPattern" -> source.bookUrlPattern = it.value
                "header" -> source.header = it.value
                "bookSourceComment" -> source.bookSourceComment = it.value
                "concurrentRate" -> source.concurrentRate = it.value
                "variableComment" -> source.variableComment = it.value
                "jsLib" -> source.jsLib = it.value
            }
        }
        searchEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "searchUrl" -> source.searchUrl = it.value
                "checkKeyWord" -> searchRule.checkKeyWord = it.value
                "bookList" -> searchRule.bookList = it.value
                "name" -> searchRule.name =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "author" -> searchRule.author =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "kind" -> searchRule.kind =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "intro" -> searchRule.intro =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

//                "updateTime" -> searchRule.updateTime =
//                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "wordCount" -> searchRule.wordCount =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "lastChapter" -> searchRule.lastChapter =
                    viewModel.ruleComplete(it.value, searchRule.bookList)

                "coverUrl" -> searchRule.coverUrl =
                    viewModel.ruleComplete(it.value, searchRule.bookList, 3)

                "bookUrl" -> searchRule.bookUrl =
                    viewModel.ruleComplete(it.value, searchRule.bookList, 2)
            }
        }
        exploreEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "exploreUrl" -> source.exploreUrl = it.value
                "bookList" -> exploreRule.bookList = it.value
                "name" -> exploreRule.name =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "author" -> exploreRule.author =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "kind" -> exploreRule.kind =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "intro" -> exploreRule.intro =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

//                "updateTime" -> exploreRule.updateTime =
//                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "wordCount" -> exploreRule.wordCount =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "lastChapter" -> exploreRule.lastChapter =
                    viewModel.ruleComplete(it.value, exploreRule.bookList)

                "coverUrl" -> exploreRule.coverUrl =
                    viewModel.ruleComplete(it.value, exploreRule.bookList, 3)

                "bookUrl" -> exploreRule.bookUrl =
                    viewModel.ruleComplete(it.value, exploreRule.bookList, 2)
            }
        }
        infoEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "init" -> bookInfoRule.init = it.value
                "name" -> bookInfoRule.name = viewModel.ruleComplete(it.value, bookInfoRule.init)
                "author" -> bookInfoRule.author =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "kind" -> bookInfoRule.kind =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "intro" -> bookInfoRule.intro =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

//                "updateTime" -> bookInfoRule.updateTime =
//                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "wordCount" -> bookInfoRule.wordCount =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "lastChapter" -> bookInfoRule.lastChapter =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)

                "coverUrl" -> bookInfoRule.coverUrl =
                    viewModel.ruleComplete(it.value, bookInfoRule.init, 3)

                "tocUrl" -> bookInfoRule.tocUrl =
                    viewModel.ruleComplete(it.value, bookInfoRule.init, 2)

                "canReName" -> bookInfoRule.canReName = it.value
                "downloadUrls" -> bookInfoRule.downloadUrls =
                    viewModel.ruleComplete(it.value, bookInfoRule.init)
            }
        }
        tocEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "preUpdateJs" -> tocRule.preUpdateJs = it.value
                "chapterList" -> tocRule.chapterList = it.value
                "chapterName" -> tocRule.chapterName =
                    viewModel.ruleComplete(it.value, tocRule.chapterList)

                "chapterUrl" -> tocRule.chapterUrl =
                    viewModel.ruleComplete(it.value, tocRule.chapterList, 2)

                "formatJs" -> tocRule.formatJs = it.value
                "isVolume" -> tocRule.isVolume = it.value
                "updateTime" -> tocRule.updateTime = it.value
                "isVip" -> tocRule.isVip = it.value
                "isPay" -> tocRule.isPay = it.value
                "nextTocUrl" -> tocRule.nextTocUrl =
                    viewModel.ruleComplete(it.value, tocRule.chapterList, 2)
            }
        }
        contentEntities.forEach {
            it.value = it.value?.takeIf { s -> s.isNotBlank() }
            when (it.key) {
                "content" -> contentRule.content = viewModel.ruleComplete(it.value)
                "nextContentUrl" -> contentRule.nextContentUrl =
                    viewModel.ruleComplete(it.value, type = 2)
                "subContent" -> contentRule.subContent = viewModel.ruleComplete(it.value)
                "title" -> contentRule.title = viewModel.ruleComplete(it.value)

                "webJs" -> contentRule.webJs = it.value
                "sourceRegex" -> contentRule.sourceRegex = it.value
                "replaceRegex" -> contentRule.replaceRegex = it.value
                "imageStyle" -> contentRule.imageStyle = it.value
                "imageDecode" -> contentRule.imageDecode = it.value
                "payAction" -> contentRule.payAction = it.value
                "callBackJs" -> contentRule.callBackJs = it.value
            }
        }
//        reviewEntities.forEach {
//            when (it.key) {
//                "reviewUrl" -> reviewRule.reviewUrl = it.value
//                "avatarRule" -> reviewRule.avatarRule =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl, 3)
//
//                "contentRule" -> reviewRule.contentRule =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl)
//
//                "postTimeRule" -> reviewRule.postTimeRule =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl)
//
//                "reviewQuoteUrl" -> reviewRule.reviewQuoteUrl =
//                    viewModel.ruleComplete(it.value, reviewRule.reviewUrl, 2)
//
//                "voteUpUrl" -> reviewRule.voteUpUrl = it.value
//                "voteDownUrl" -> reviewRule.voteDownUrl = it.value
//                "postReviewUrl" -> reviewRule.postReviewUrl = it.value
//                "postQuoteUrl" -> reviewRule.postQuoteUrl = it.value
//                "deleteUrl" -> reviewRule.deleteUrl = it.value
//            }
//        }
        source.ruleSearch = searchRule
        source.ruleExplore = exploreRule
        source.ruleBookInfo = bookInfoRule
        source.ruleToc = tocRule
        source.ruleContent = contentRule
//        source.ruleReview = reviewRule
        return source
    }

    private fun alertGroups() {
        lifecycleScope.launch {
            val groups = withContext(IO) {
                appDb.bookSourceDao.allGroups()
            }
            if (groups.isNotEmpty()) {
                groupOptions = groups
                groupSelectorVisible = true
            }
        }
    }

    private fun observeKeyboardAssists() {
        lifecycleScope.launch {
            appDb.keyboardAssistsDao.flowByType(0)
                .catch {
                    AppLog.put("键盘帮助浮窗获取数据失败\n${it.localizedMessage}", it)
                }
                .flowOn(IO)
                .collect { keyboardAssists = it }
        }
    }

    private fun keyboardHelpActions(): List<BookSourceKeyboardHelpAction> {
        val helpActions = arrayListOf(
            BookSourceKeyboardHelpAction("插入URL参数", "urlOption"),
            BookSourceKeyboardHelpAction("书源教程", "ruleHelp"),
            BookSourceKeyboardHelpAction("js教程", "jsHelp"),
            BookSourceKeyboardHelpAction("正则教程", "regexHelp"),
        )
        focusedField?.let { focused ->
            when (focused.key) {
                "bookSourceGroup" -> {
                    helpActions.add(
                        BookSourceKeyboardHelpAction("插入分组", "addGroup")
                    )
                }

                else -> {
                    helpActions.add(
                        BookSourceKeyboardHelpAction("选择文件", "selectFile")
                    )
                }
            }
        }
        return helpActions
    }

    private fun onHelpActionSelect(action: String) {
        when (action) {
            "addGroup" -> alertGroups()
            "urlOption" -> urlOptionEditorVisible = true
            "ruleHelp" -> showHelp("ruleHelp")
            "jsHelp" -> showHelp("jsHelp")
            "regexHelp" -> showHelp("regexHelp")
            "selectFile" -> selectDoc.launch(arrayOf("*/*"))
        }
    }

    private fun openKeyboardConfig() {
        showDialogFragment(
            KeyboardAssistsConfig(
                object : KeyboardAssistsConfig.CallBack {
                    override fun requestLayout() {
                        keyboardRowCount = AppConfig.showBoardLine
                    }
                }
            )
        )
    }

    private fun insertUrlOption(input: BookSourceUrlOptionInput) {
        val option = AnalyzeUrl.UrlOption().apply {
            useWebView(input.useWebView)
            setMethod(input.method)
            setCharset(input.charset)
            setHeaders(input.headers)
            setBody(input.body)
            setRetry(input.retry)
            setType(input.type)
            setWebJs(input.webJs)
            setJs(input.js)
            // 保留旧弹窗的实际序列化行为：bodyJs 会覆盖 js 字段。
            setJs(input.bodyJs)
            setDnsIp(input.dnsIp)
        }
        sendText(GSON.toJson(option))
    }

    private fun sendText(text: String) {
        if (text.isEmpty()) return
        val focused = focusedField ?: return
        val entity = findEditEntity(focused.key) ?: return
        val current = entity.value.orEmpty()
        val start = minOf(focused.start, focused.end).coerceIn(0, current.length)
        val end = maxOf(focused.start, focused.end).coerceIn(0, current.length)
        val updated = current.replaceRange(start, end, text)
        val cursor = start + text.length
        replaceFieldValue(focused.key, updated, cursor, cursor)
    }

    private fun setSourceVariable() {
        viewModel.save(getSource()) { source ->
            lifecycleScope.launch {
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
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.bookSource?.setVariable(variable)
    }

    private fun onUndoClicked() {
        val focused = focusedField ?: return
        val entity = findEditEntity(focused.key) ?: return
        val target = undoHistory[focused.key]?.pollLast() ?: return
        pushHistory(
            redoHistory,
            focused.key,
            FieldSnapshot(entity.value.orEmpty(), focused.start, focused.end),
        )
        replaceFieldValue(
            focused.key,
            target.text,
            target.start,
            target.end,
            recordHistory = false,
        )
    }

    private fun onRedoClicked() {
        val focused = focusedField ?: return
        val entity = findEditEntity(focused.key) ?: return
        val target = redoHistory[focused.key]?.pollLast() ?: return
        pushHistory(
            undoHistory,
            focused.key,
            FieldSnapshot(entity.value.orEmpty(), focused.start, focused.end),
        )
        replaceFieldValue(
            focused.key,
            target.text,
            target.start,
            target.end,
            recordHistory = false,
        )
    }

    private data class FieldSnapshot(
        val text: String,
        val start: Int,
        val end: Int,
    )

    private companion object {
        const val MAX_FIELD_HISTORY = 100
    }

}
