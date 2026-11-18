package io.legado.app.ui.book.source.edit

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStateAtLeast
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
import io.legado.app.help.source.toSourceJson
import io.legado.app.help.source.writeSourceJson
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.source.edit.SourceEditorField
import io.legado.app.ui.source.edit.SourceEditorHost
import io.legado.app.ui.source.edit.SourceEditorRequest
import io.legado.app.ui.source.edit.SourceEditorSession
import io.legado.app.ui.source.edit.SourceEditorWindow
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.source.edit.SourceEditCodeHighlighter
import io.legado.app.ui.source.edit.hasSameEditorConfiguration
import io.legado.app.ui.source.edit.putSourceFieldSession
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.dialog.toUrlOption
import io.legado.app.ui.widget.keyboard.KeyboardAssistsConfig
import io.legado.app.ui.widget.text.EditEntity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.ArrayDeque

class BookSourceEditActivity :
    VMBaseActivity<ComposeActivityBinding, BookSourceEditViewModel>(imageBg = false),
    VariableDialog.Callback {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<BookSourceEditViewModel>()

    private val editor get() = viewModel.editor
    private val session get() = viewModel.session
    private var groupInsertTarget by mutableStateOf<BookSourceEditorSelection?>(null)
    private var groupOptions by mutableStateOf<List<String>>(emptyList())
    private var urlOptionEditorVisible by mutableStateOf(false)
    private var keyboardAssists by mutableStateOf<List<KeyboardAssist>>(emptyList())
    private var keyboardRowCount by mutableIntStateOf(AppConfig.showBoardLine)
    private var forceFinish = false
    private var restoringDraft = false
    private var childResultJob: Job? = null

    //    private val reviewEntities: ArrayList<EditEntity> = ArrayList()
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it?.let { text -> viewModel.importSource(text, ::applyImport) }
    }
    private val selectDoc = registerForActivityResult(SelectFileContract()) { uri ->
        session.receiveFile(uri)
        applyChildResult()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        if (!editor.initialized && savedInstanceState?.getString(SourceEditorSession.STATE_DRAFT_ID) == null) {
            intent.getStringExtra("sourceUrl")?.let { sourceUrl ->
                if (appDb.bookSourceDao.hasJsSource(sourceUrl)) {
                    startActivity<JsSourceEditActivity> { putExtra("sourceUrl", sourceUrl) }
                    super.finish()
                    return
                }
            }
        }
        observeKeyboardAssists()
        initView()
        if (editor.initialized) {
            applyChildResult()
        } else if (session.restoreInstanceState(savedInstanceState)) {
            restoringDraft = true
            lifecycleScope.launch {
                try {
                    restoreDraft(checkNotNull(session.read()))
                    editor.initialized = true
                    applyChildResult()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    AppLog.put("恢复书源草稿失败", error)
                    toastOnUi(R.string.source_editor_draft_restore_failed)
                    loadSource()
                } finally {
                    restoringDraft = false
                }
            }
        } else {
            loadSource()
        }
    }

    private fun loadSource() {
        viewModel.initData(intent) {
            if (isFinishing || isDestroyed || editor.initialized) return@initData
            editor.savedForm = viewModel.bookSource ?: BookSource()
            upSourceView(viewModel.bookSource)
            editor.initialized = true
            applyChildResult()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (editor.initialized || restoringDraft) session.writeInstanceState(outState)
        if (editor.initialized && !isFinishing) session.checkpoint(captureDraft())
    }

    override fun onStop() {
        super.onStop()
        if (editor.initialized && !isFinishing) session.checkpoint(captureDraft())
    }

    override fun onDestroy() {
        if (isFinishing) session.close()
        super.onDestroy()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!isFinishing && !LocalConfig.ruleHelpVersionIsLast) {
            showHelp("ruleHelp")
        }
    }

    private val textEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        session.receiveCode(result)
        applyChildResult()
    }

    private fun onFullEditClicked() {
        val focused = editor.focusedField
        val entity = focused?.let { findEditEntity(it.field) }
        if (focused == null || entity == null) {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
            return
        }
        openChild(focused, entity.value.orEmpty()) { request ->
            textEditLauncher.launch(Intent(this, CodeEditActivity::class.java).apply {
                putSourceFieldSession(request)
                putExtra("title", entity.hint)
                SourceEditCodeHighlighter.languageNameOf(focused.field.key)?.let { putExtra("languageName", it) }
            })
        }
    }

    private fun openChild(
        target: BookSourceEditorSelection,
        text: String?,
        launch: (SourceEditorRequest) -> Unit,
    ) {
        val revision = editor.revision
        val field = target.toSessionField()
        lifecycleScope.launch {
            try {
                session.openChild(field, revision, text, {
                    editor.initialized && !isFinishing && editor.revision == revision &&
                        editor.focusedField == target && editor.selectedTab == field.tab &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }, ::captureDraft, launch)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.put("打开书源子编辑失败", error)
                toastOnUi(R.string.source_editor_draft_checkpoint_failed)
            }
        }
    }

    private fun applyChildResult() {
        if (!editor.initialized || isFinishing || childResultJob?.isActive == true) return
        childResultJob = lifecycleScope.launch {
            try {
                session.applyResult(
                    isCurrent = { request ->
                        !isFinishing && editor.selectedTab == request.field.tab && editor.revision == request.revision &&
                            findEditEntity(request.field.toFieldId()) != null
                    },
                    applyCode = { target, text, cursor ->
                        val field = target.toFieldId()
                        val current = checkNotNull(findEditEntity(field)).value.orEmpty()
                        editor.focusedField = target.toSelection()
                        if (text != null && text != current) {
                            replaceFieldValue(field, text, cursor, cursor)
                        } else {
                            val position = cursor.coerceIn(0, current.length)
                            editor.focusedField = BookSourceEditorSelection(field, position, position)
                            editor.fieldValueRevision++
                        }
                    },
                    applyFile = { target, uri ->
                        uri.takePersistableReadPermission()
                        insertAt(target.toSelection(), if (uri.isContentScheme()) uri.toString() else uri.path.orEmpty())
                    },
                    capture = ::captureDraft,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.put("回填书源子编辑内容失败", error)
                toastOnUi(R.string.source_editor_result_failed)
            }
        }
    }

    private fun captureDraft(): BookSourceEditorDraft {
        val original = viewModel.bookSource
        val controls = editor.controls
        return BookSourceEditorDraft(
            SourceEditorWindow(
                intent.getStringExtra("sourceUrl"), viewModel.persistedSourceUrl, editor.selectedTab,
                editor.focusedField?.toSessionField(), editor.revision, editor.autoComplete, session.pending,
            ),
            original, editor.savedForm.takeUnless { it === original }, editor.draftBase.takeUnless { it === original },
            editor.tabs.map { tab -> tab.filter { it.value != it.originalValue }.associate { it.key to it.value } },
            controls.typeIndex, BookSourceDraftControls(
                controls.enabled, controls.enabledExplore, controls.enabledCookieJar,
                controls.eventListener, controls.customButton,
            ), editor.importedWeight,
        )
    }

    private fun restoreDraft(draft: BookSourceEditorDraft) {
        val window = draft.window
        check(window.entryUrl == intent.getStringExtra("sourceUrl") && draft.fields.size == editor.tabs.size)
        upSourceView(draft.base ?: draft.original)
        editor.savedForm = draft.savedForm ?: draft.original ?: BookSource()
        draft.fields.forEachIndexed { tab, fields ->
            editor.tabs[tab].forEach { field -> if (fields.containsKey(field.key)) field.value = fields[field.key] }
        }
        val controls = draft.controls
        editor.controls = BookSourceEditControls(
            typeIndex = draft.typeIndex.coerceIn(0, 4), enabled = controls.enabled,
            enabledExplore = controls.enabledExplore, enabledCookieJar = controls.cookieJar,
            eventListener = controls.eventListener, customButton = controls.customButton,
        )
        editor.importedWeight = draft.importedWeight
        editor.selectedTab = window.tab.coerceIn(editor.tabs.indices)
        editor.focusedField = window.selection?.takeIf { it.tab == editor.selectedTab }?.toSelection()
        editor.revision = window.revision
        editor.autoComplete = window.autoComplete
        session.restoreRequest(window.request)
        viewModel.restoreOriginal(window.persistedUrl, draft.original)
    }

    private fun scheduleDraftCheckpoint() {
        if (editor.initialized && !isFinishing) session.scheduleCheckpoint(::captureDraft)
    }

    private fun BookSourceEditorSelection.toSessionField() = SourceEditorField(field.tab, field.key, start, end)

    private fun SourceEditorField.toFieldId() = BookSourceFieldId(tab, key)

    private fun SourceEditorField.toSelection() = BookSourceEditorSelection(toFieldId(), start, end)

    private fun onEditorAction(itemId: Int) {
        when (itemId) {
            R.id.menu_fullscreen_edit -> onFullEditClicked()

            R.id.menu_save -> saveSource {
                setResult(RESULT_OK, Intent().putExtra("origin", it.bookSourceUrl))
                forceFinish = true
                finish()
            }

            R.id.menu_debug_source -> saveSource { source ->
                startActivity<BookSourceDebugActivity> {
                    putExtra("key", source.bookSourceUrl)
                }
            }

            R.id.menu_clear_cookie -> viewModel.clearCookie(sourceField("bookSourceUrl").orEmpty())
            R.id.menu_auto_complete -> {
                editor.autoComplete = !editor.autoComplete
                editor.revision++
                scheduleDraftCheckpoint()
            }
            R.id.menu_copy_source -> sendToClip(getSource().toSourceJson())
            R.id.menu_paste_source -> viewModel.pasteSource(::applyImport)
            R.id.menu_qr_code_camera -> qrCodeResult.launch()
            R.id.menu_share_str -> share(getSource().toSourceJson())
            R.id.menu_share_qr -> shareWithQr(
                getSource().toSourceJson(),
                getString(R.string.share_book_source),
                ErrorCorrectionLevel.L
            )

            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
            R.id.menu_help -> showHelp("ruleHelp")
            R.id.menu_login -> saveSource { source ->
                startActivity<SourceLoginActivity> {
                    putExtra("type", "bookSource")
                    putExtra("key", source.bookSourceUrl)
                }
            }

            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_search -> saveSource { source ->
                SearchActivity.start(this, source)
            }

        }
    }

    private fun initView() {
        binding.composeView.setContent {
            SourceEditorHost {
                BookSourceEditScreen(
                    controls = editor.controls,
                    selectedTab = editor.selectedTab,
                    editEntities = editEntitiesFor(editor.selectedTab),
                    sourceRevision = editor.sourceRevision,
                    fieldValueRevision = editor.fieldValueRevision,
                    editEntityMaxLine = Int.MAX_VALUE,
                    autoComplete = editor.autoComplete,
                    focusedField = editor.focusedField,
                    keyboardAssists = keyboardAssists,
                    keyboardRowCount = keyboardRowCount,
                    keyboardHelpActions = keyboardHelpActions(),
                    onControlsChange = {
                        if (editor.controls != it) {
                            editor.controls = it
                            editor.revision++
                            scheduleDraftCheckpoint()
                        }
                    },
                    onTabSelected = ::setEditEntities,
                    onBack = ::finish,
                    onAction = ::onEditorAction,
                    onPrepareOverflow = { !sourceField("loginUrl").isNullOrBlank() },
                    onFieldValueChange = ::onFieldValueChange,
                    onFieldFocused = { field, start, end ->
                        editor.focusedField = BookSourceEditorSelection(field, start, end)
                        scheduleDraftCheckpoint()
                    },
                    onKeyboardHelpAction = ::onHelpActionSelect,
                    onOpenKeyboardConfig = ::openKeyboardConfig,
                    onInsertText = { insertAt(editor.focusedField, it) },
                    onUndo = ::onUndoClicked,
                    onRedo = ::onRedoClicked,
                )
                if (editor.pendingExit) {
                    BookSourceExitDialog(
                        onDismiss = { editor.pendingExit = false },
                        onDiscard = {
                            editor.pendingExit = false
                            forceFinish = true
                            finish()
                        },
                    )
                }
                groupInsertTarget?.let { target ->
                    BookSourceGroupSelectorDialog(
                        groups = groupOptions,
                        onDismiss = { groupInsertTarget = null },
                        onSelected = { group ->
                            groupInsertTarget = null
                            insertAt(target, group)
                        },
                    )
                }
                if (urlOptionEditorVisible) {
                    val target = remember { editor.focusedField }
                    BookSourceUrlOptionDialog(
                        charsets = AppConst.charsets,
                        onDismiss = { urlOptionEditorVisible = false },
                        onConfirm = { input ->
                            urlOptionEditorVisible = false
                            insertAt(target, GSON.toJson(input.toUrlOption()))
                        },
                    )
                }
            }
        }
    }

    override fun finish() {
        if (forceFinish) {
            session.close()
            super.finish()
            return
        }
        val source = getSource(completeRules = false)
        val weightChanged = editor.importedWeight?.let { it != editor.savedForm.weight } ?: false
        if (weightChanged || !source.hasSameEditorConfiguration(editor.savedForm)) {
            editor.pendingExit = true
        } else {
            session.close()
            super.finish()
        }
    }

    private fun editEntitiesFor(tabPosition: Int): List<EditEntity> {
        return when (tabPosition) {
            1 -> editor.tabs[1]
            2 -> editor.tabs[2]
            3 -> editor.tabs[3]
            4 -> editor.tabs[4]
            5 -> editor.tabs[5]
//            6 -> reviewEntities
            else -> editor.tabs[0]
        }
    }

    private fun findEditEntity(field: BookSourceFieldId): EditEntity? =
        editEntitiesFor(field.tab).firstOrNull { it.key == field.key }

    private fun onFieldValueChange(field: BookSourceFieldId, value: String, start: Int, end: Int) {
        val entity = findEditEntity(field) ?: return
        val current = entity.value.orEmpty()
        if (current != value) {
            pushHistory(
                editor.undo,
                field,
                BookSourceFieldSnapshot(
                    current,
                    editor.focusedField?.takeIf { it.field == field }?.start ?: current.length,
                    editor.focusedField?.takeIf { it.field == field }?.end ?: current.length,
                ),
            )
            editor.redo.remove(field)
            entity.value = value
            editor.revision++
        }
        editor.focusedField = BookSourceEditorSelection(
            field,
            start.coerceIn(0, value.length),
            end.coerceIn(0, value.length),
        )
        scheduleDraftCheckpoint()
    }

    private fun replaceFieldValue(
        field: BookSourceFieldId,
        value: String,
        start: Int,
        end: Int,
        recordHistory: Boolean = true,
    ) {
        val entity = findEditEntity(field) ?: return
        val current = entity.value.orEmpty()
        if (current != value && recordHistory) {
            pushHistory(
                editor.undo,
                field,
                BookSourceFieldSnapshot(
                    current,
                    editor.focusedField?.takeIf { it.field == field }?.start ?: current.length,
                    editor.focusedField?.takeIf { it.field == field }?.end ?: current.length,
                ),
            )
            editor.redo.remove(field)
        }
        if (current != value) {
            editor.revision++
            entity.value = value
        }
        if (editor.focusedField?.field == field) {
            editor.focusedField = BookSourceEditorSelection(
                field,
                start.coerceIn(0, value.length),
                end.coerceIn(0, value.length),
            )
        }
        editor.fieldValueRevision += 1
        scheduleDraftCheckpoint()
    }

    private fun pushHistory(
        histories: MutableMap<BookSourceFieldId, ArrayDeque<BookSourceFieldSnapshot>>,
        field: BookSourceFieldId,
        snapshot: BookSourceFieldSnapshot,
    ) {
        val history = histories.getOrPut(field) { ArrayDeque() }
        if (history.peekLast() != snapshot) history.addLast(snapshot)
        while (history.size > MAX_FIELD_HISTORY) history.removeFirst()
    }

    private fun setEditEntities(tabPosition: Int) {
        editor.selectedTab = tabPosition
        editor.focusedField = null
        scheduleDraftCheckpoint()
    }

    private fun upSourceView(bookSource: BookSource?) {
        val bs = bookSource ?: BookSource()
        editor.draftBase = bs
        editor.controls = BookSourceEditControls(
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
        editor.originalTypeIndex = editor.controls.typeIndex
        // 基本信息
        editor.tabs[0].clear()
        editor.tabs[0].apply {
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
        val sr = bs.ruleSearch ?: SearchRule()
        editor.tabs[1].clear()
        editor.tabs[1].apply {
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
        val er = bs.ruleExplore ?: ExploreRule()
        editor.tabs[2].clear()
        editor.tabs[2].apply {
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
        val ir = bs.ruleBookInfo ?: BookInfoRule()
        editor.tabs[3].clear()
        editor.tabs[3].apply {
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
        val tr = bs.ruleToc ?: TocRule()
        editor.tabs[4].clear()
        editor.tabs[4].apply {
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
        val cr = bs.ruleContent ?: ContentRule()
        editor.tabs[5].clear()
        editor.tabs[5].apply {
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
        editor.selectedTab = 0
        editor.focusedField = null
        groupInsertTarget = null
        urlOptionEditorVisible = false
        editor.undo.clear()
        editor.redo.clear()
        editor.sourceRevision += 1
        editor.fieldValueRevision += 1
        editor.revision++
    }

    private fun getSource(completeRules: Boolean = true): BookSource {
        fun complete(rule: String?, base: String? = null, type: Int = 1): String? =
            if (completeRules && !rule.isNullOrBlank()) viewModel.ruleComplete(rule, base, type) else rule

        val source = editor.draftBase.copy()
        source.enabled = editor.controls.enabled
        source.enabledExplore = editor.controls.enabledExplore
        if (editor.controls.enabledCookieJar != (editor.draftBase.enabledCookieJar == true)) {
            source.enabledCookieJar = editor.controls.enabledCookieJar
        }
        if (editor.controls.typeIndex != editor.originalTypeIndex) {
            source.bookSourceType = when (editor.controls.typeIndex) {
                4 -> BookSourceType.video
                3 -> BookSourceType.file
                2 -> BookSourceType.image
                1 -> BookSourceType.audio
                else -> BookSourceType.default
            }
        }
        source.eventListener = editor.controls.eventListener
        source.customButton = editor.controls.customButton
        val searchRule = source.ruleSearch?.copy() ?: SearchRule()
        val exploreRule = source.ruleExplore?.copy() ?: ExploreRule()
        val bookInfoRule = source.ruleBookInfo?.copy() ?: BookInfoRule()
        val tocRule = source.ruleToc?.copy() ?: TocRule()
        val contentRule = source.ruleContent?.copy() ?: ContentRule()
        editor.tabs[0].forEach {
            val value = it.editedValue
            when (it.key) {
                "bookSourceUrl" -> source.bookSourceUrl = value ?: ""
                "bookSourceName" -> source.bookSourceName = value ?: ""
                "bookSourceGroup" -> source.bookSourceGroup = value
                "loginUrl" -> source.loginUrl = value
                "loginUi" -> source.loginUi = value
                "loginCheckJs" -> source.loginCheckJs = value
                "coverDecodeJs" -> source.coverDecodeJs = value
                "bookUrlPattern" -> source.bookUrlPattern = value
                "header" -> source.header = value
                "bookSourceComment" -> source.bookSourceComment = value
                "concurrentRate" -> source.concurrentRate = value
                "variableComment" -> source.variableComment = value
                "jsLib" -> source.jsLib = value
            }
        }
        editor.tabs[1].forEach {
            val value = it.editedValue
            when (it.key) {
                "searchUrl" -> source.searchUrl = value
                "checkKeyWord" -> searchRule.checkKeyWord = value
                "bookList" -> searchRule.bookList = value
                "name" -> searchRule.name =
                    complete(value, searchRule.bookList)

                "author" -> searchRule.author =
                    complete(value, searchRule.bookList)

                "kind" -> searchRule.kind =
                    complete(value, searchRule.bookList)

                "intro" -> searchRule.intro =
                    complete(value, searchRule.bookList)

                "wordCount" -> searchRule.wordCount =
                    complete(value, searchRule.bookList)

                "lastChapter" -> searchRule.lastChapter =
                    complete(value, searchRule.bookList)

                "coverUrl" -> searchRule.coverUrl =
                    complete(value, searchRule.bookList, 3)

                "bookUrl" -> searchRule.bookUrl =
                    complete(value, searchRule.bookList, 2)
            }
        }
        editor.tabs[2].forEach {
            val value = it.editedValue
            when (it.key) {
                "exploreUrl" -> source.exploreUrl = value
                "bookList" -> exploreRule.bookList = value
                "name" -> exploreRule.name =
                    complete(value, exploreRule.bookList)

                "author" -> exploreRule.author =
                    complete(value, exploreRule.bookList)

                "kind" -> exploreRule.kind =
                    complete(value, exploreRule.bookList)

                "intro" -> exploreRule.intro =
                    complete(value, exploreRule.bookList)

                "wordCount" -> exploreRule.wordCount =
                    complete(value, exploreRule.bookList)

                "lastChapter" -> exploreRule.lastChapter =
                    complete(value, exploreRule.bookList)

                "coverUrl" -> exploreRule.coverUrl =
                    complete(value, exploreRule.bookList, 3)

                "bookUrl" -> exploreRule.bookUrl =
                    complete(value, exploreRule.bookList, 2)
            }
        }
        editor.tabs[3].forEach {
            val value = it.editedValue
            when (it.key) {
                "init" -> bookInfoRule.init = value
                "name" -> bookInfoRule.name = complete(value, bookInfoRule.init)
                "author" -> bookInfoRule.author =
                    complete(value, bookInfoRule.init)

                "kind" -> bookInfoRule.kind =
                    complete(value, bookInfoRule.init)

                "intro" -> bookInfoRule.intro =
                    complete(value, bookInfoRule.init)

                "wordCount" -> bookInfoRule.wordCount =
                    complete(value, bookInfoRule.init)

                "lastChapter" -> bookInfoRule.lastChapter =
                    complete(value, bookInfoRule.init)

                "coverUrl" -> bookInfoRule.coverUrl =
                    complete(value, bookInfoRule.init, 3)

                "tocUrl" -> bookInfoRule.tocUrl =
                    complete(value, bookInfoRule.init, 2)

                "canReName" -> bookInfoRule.canReName = value
                "downloadUrls" -> bookInfoRule.downloadUrls =
                    complete(value, bookInfoRule.init)
            }
        }
        editor.tabs[4].forEach {
            val value = it.editedValue
            when (it.key) {
                "preUpdateJs" -> tocRule.preUpdateJs = value
                "chapterList" -> tocRule.chapterList = value
                "chapterName" -> tocRule.chapterName =
                    complete(value, tocRule.chapterList)

                "chapterUrl" -> tocRule.chapterUrl =
                    complete(value, tocRule.chapterList, 2)

                "formatJs" -> tocRule.formatJs = value
                "isVolume" -> tocRule.isVolume = value
                "updateTime" -> tocRule.updateTime = value
                "isVip" -> tocRule.isVip = value
                "isPay" -> tocRule.isPay = value
                "nextTocUrl" -> tocRule.nextTocUrl =
                    complete(value, tocRule.chapterList, 2)
            }
        }
        editor.tabs[5].forEach {
            val value = it.editedValue
            when (it.key) {
                "content" -> contentRule.content = complete(value)
                "nextContentUrl" -> contentRule.nextContentUrl =
                    complete(value, type = 2)
                "subContent" -> contentRule.subContent = complete(value)
                "title" -> contentRule.title = complete(value)

                "webJs" -> contentRule.webJs = value
                "sourceRegex" -> contentRule.sourceRegex = value
                "replaceRegex" -> contentRule.replaceRegex = value
                "imageStyle" -> contentRule.imageStyle = value
                "imageDecode" -> contentRule.imageDecode = value
                "payAction" -> contentRule.payAction = value
                "callBackJs" -> contentRule.callBackJs = value
            }
        }
        source.ruleSearch = searchRule.takeIf { editor.draftBase.ruleSearch != null || it != SearchRule() }
        source.ruleExplore = exploreRule.takeIf { editor.draftBase.ruleExplore != null || it != ExploreRule() }
        source.ruleBookInfo = bookInfoRule.takeIf { editor.draftBase.ruleBookInfo != null || it != BookInfoRule() }
        source.ruleToc = tocRule.takeIf { editor.draftBase.ruleToc != null || it != TocRule() }
        source.ruleContent = contentRule.takeIf { editor.draftBase.ruleContent != null || it != ContentRule() }
        return source
    }

    private fun alertGroups() {
        val target = editor.focusedField ?: return
        val revision = editor.sourceRevision
        lifecycleScope.launch {
            val groups = withContext(IO) {
                appDb.bookSourceDao.allGroups()
            }
            if (groups.isNotEmpty() && revision == editor.sourceRevision && editor.focusedField == target) {
                groupOptions = groups
                groupInsertTarget = target
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
        editor.focusedField?.let { focused ->
            when (focused.field.key) {
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
            "selectFile" -> {
                editor.focusedField?.let { target -> openChild(target, null) { selectDoc.launch(arrayOf("*/*")) } }
            }
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

    private fun insertAt(focused: BookSourceEditorSelection?, text: String) {
        if (text.isEmpty()) return
        if (focused == null) return
        val entity = findEditEntity(focused.field) ?: return
        val current = entity.value.orEmpty()
        val start = minOf(focused.start, focused.end).coerceIn(0, current.length)
        val end = maxOf(focused.start, focused.end).coerceIn(0, current.length)
        val updated = current.replaceRange(start, end, text)
        val cursor = start + text.length
        replaceFieldValue(focused.field, updated, cursor, cursor)
    }

    private fun saveSource(onSuccess: (BookSource) -> Unit) {
        val revision = editor.revision
        val baseRevision = editor.sourceRevision
        val submittedForm = getSource(completeRules = false)
        val sourceToSave = if (editor.autoComplete) getSource() else submittedForm
        viewModel.save(sourceToSave, editor.importedWeight) { source ->
            if (isFinishing) return@save
            editor.savedForm = if (sourceToSave === submittedForm) source else submittedForm.copy(weight = source.weight)
            if (baseRevision == editor.sourceRevision) {
                editor.draftBase = editor.draftBase.copy(
                    customOrder = source.customOrder,
                    respondTime = source.respondTime,
                    weight = source.weight,
                    lastUpdateTime = source.lastUpdateTime,
                )
                editor.importedWeight = null
            }
            session.checkpoint(captureDraft())
            if (!isDestroyed && revision == editor.revision) onSuccess(source)
        }
    }

    private fun sourceField(key: String): String? =
        editor.tabs[0].firstOrNull { it.key == key }?.editedValue

    private fun applyImport(imported: BookSourceEditImport) {
        if (isFinishing || isDestroyed) return
        if (imported.source.mainJs.isNullOrBlank()) {
            val old = viewModel.bookSource
            val source = imported.source.copy(
                customOrder = old?.customOrder ?: imported.source.customOrder,
                respondTime = old?.respondTime ?: imported.source.respondTime,
                weight = imported.weight ?: old?.weight ?: imported.source.weight,
            )
            editor.importedWeight = imported.weight
            upSourceView(source)
            scheduleDraftCheckpoint()
            return
        }

        lifecycleScope.launch {
            var file: File? = null
            try {
                withContext(IO) {
                    val staged = ImportBookSourceDialog.createEditorPreviewFile(application.cacheDir)
                    file = staged
                    staged.bufferedWriter().use(imported.source::writeSourceJson)
                }
                lifecycle.withStateAtLeast(Lifecycle.State.RESUMED) {
                    val tag = "book-source-editor-import"
                    if (!isFinishing && !supportFragmentManager.isStateSaved &&
                        supportFragmentManager.findFragmentByTag(tag) == null
                    ) {
                        ImportBookSourceDialog.forEditorPreview(checkNotNull(file))
                            .showNow(supportFragmentManager, tag)
                        file = null
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                toastOnUi(error.localizedMessage)
            } finally {
                file?.let { unsubmitted -> withContext(NonCancellable + IO) { unsubmitted.delete() } }
            }
        }
    }

    private fun setSourceVariable() {
        saveSource { source ->
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
        val focused = editor.focusedField ?: return
        val entity = findEditEntity(focused.field) ?: return
        val target = editor.undo[focused.field]?.pollLast() ?: return
        pushHistory(
            editor.redo,
            focused.field,
            BookSourceFieldSnapshot(entity.value.orEmpty(), focused.start, focused.end),
        )
        replaceFieldValue(
            focused.field,
            target.text,
            target.start,
            target.end,
            recordHistory = false,
        )
    }

    private fun onRedoClicked() {
        val focused = editor.focusedField ?: return
        val entity = findEditEntity(focused.field) ?: return
        val target = editor.redo[focused.field]?.pollLast() ?: return
        pushHistory(
            editor.undo,
            focused.field,
            BookSourceFieldSnapshot(entity.value.orEmpty(), focused.start, focused.end),
        )
        replaceFieldValue(
            focused.field,
            target.text,
            target.start,
            target.end,
            recordHistory = false,
        )
    }

    private companion object {
        const val MAX_FIELD_HISTORY = 100
    }

}
