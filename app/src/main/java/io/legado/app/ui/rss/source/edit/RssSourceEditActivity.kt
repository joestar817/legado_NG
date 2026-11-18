package io.legado.app.ui.rss.source.edit

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.RssSource
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.source.edit.SourceEditorField
import io.legado.app.ui.source.edit.SourceEditorHost
import io.legado.app.ui.source.edit.SourceEditorRequest
import io.legado.app.ui.source.edit.SourceEditorWindow
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.RssComposeBinding
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.source.edit.SourceEditCodeHighlighter
import io.legado.app.ui.source.edit.hasSameEditorConfiguration
import io.legado.app.ui.source.edit.putSourceFieldSession
import io.legado.app.ui.widget.dialog.UrlOptionDialog
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.ui.widget.keyboard.KeyboardAssistsConfig
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 订阅源规则编辑器。动态规则字段与工具入口完整保留，可见表单使用 Compose。 */
class RssSourceEditActivity :
    VMBaseActivity<RssComposeBinding, RssSourceEditViewModel>(imageBg = false),
    VariableDialog.Callback {

    override val binding by viewBinding(RssComposeBinding::inflate)
    override val viewModel by viewModels<RssSourceEditViewModel>()

    private val editor get() = viewModel.editor
    private val session get() = viewModel.session
    private var source: RssSource
        get() = editor.source
        set(value) { editor.source = value }
    private var keyboardAssists by mutableStateOf<List<KeyboardAssist>>(emptyList())
    private var keyboardRowCount by mutableIntStateOf(AppConfig.showBoardLine)
    private var forceFinish = false
    private var restoringDraft = false
    private var childResultJob: Job? = null

    private val selectDoc = registerForActivityResult(SelectFileContract()) { uri ->
        session.receiveFile(uri)
        applyChildResult()
    }
    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it?.let { text ->
            viewModel.importSource(text) { imported ->
                runOnUiThread { replaceSource(imported) }
            }
        }
    }
    private val textEditLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        session.receiveCode(result)
        applyChildResult()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        observeKeyboardAssists()
        binding.root.setContent {
            SourceEditorHost {
                RssSourceEditScreen(
                    source = source,
                    selectedTab = editor.selectedTab,
                    autoComplete = viewModel.autoComplete,
                    sourceRevision = editor.sourceRevision,
                    fieldValueRevision = editor.fieldValueRevision,
                    focusedField = editor.focusedField,
                    draftTextValues = editor.draftTextValues,
                    editEntityMaxLine = Int.MAX_VALUE,
                    keyboardAssists = keyboardAssists,
                    keyboardRowCount = keyboardRowCount,
                    onAction = ::handleAction
                )
                if (editor.pendingExit) {
                    RssSourceExitDialog(
                        onDismiss = { editor.pendingExit = false },
                        onDiscard = {
                            editor.pendingExit = false
                            forceFinish = true
                            finish()
                        }
                    )
                }
            }
        }
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
                    AppLog.put("恢复订阅草稿失败", error)
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
            editor.savedForm = viewModel.rssSource ?: RssSource()
            replaceSource(editor.savedForm)
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
        if (!LocalConfig.ruleHelpVersionIsLast) showHelp("rssRuleHelp")
    }

    override fun finish() {
        if (!forceFinish && !source.hasSameEditorConfiguration(editor.savedForm)) {
            editor.pendingExit = true
            return
        }
        session.close()
        super.finish()
    }

    private fun handleAction(action: RssSourceEditAction) {
        when (action) {
            RssSourceEditAction.Undo -> restoreHistory(redo = false)
            RssSourceEditAction.Redo -> restoreHistory(redo = true)
            RssSourceEditAction.KeyboardConfig -> openKeyboardConfig()
            is RssSourceEditAction.InsertText -> insertAt(editor.focusedField, action.text)
            is RssSourceEditAction.EditText -> {
                editor.history.record(action.key, currentSnapshot(action.key), action.value)
                if (fieldValue(action.key) != action.value) {
                    editor.draftTextValues[action.key] = action.value
                    updateField(action.key, action.value)
                }
                editor.focusedField = RssEditorSelection(action.key, action.start, action.end)
            }
            RssSourceEditAction.Back -> finish()
            RssSourceEditAction.Save -> saveAndFinish()
            RssSourceEditAction.Debug -> saveSource { saved ->
                startActivity<RssSourceDebugActivity> { putExtra("key", saved.sourceUrl) }
            }
            RssSourceEditAction.Login -> saveSource { saved ->
                startActivity<SourceLoginActivity> {
                    putExtra("type", "rssSource")
                    putExtra("key", saved.sourceUrl)
                }
            }
            RssSourceEditAction.SetVariable -> setSourceVariable()
            RssSourceEditAction.ClearCookie -> viewModel.clearCookie(source.sourceUrl)
            RssSourceEditAction.Copy -> sendToClip(GSON.toJson(normalizedSource()))
            RssSourceEditAction.Paste -> viewModel.pasteSource(::replaceSource)
            RssSourceEditAction.ImportQr -> qrCodeResult.launch()
            RssSourceEditAction.ShareText -> share(GSON.toJson(normalizedSource()))
            RssSourceEditAction.ShareQr -> shareWithQr(
                GSON.toJson(normalizedSource()),
                getString(R.string.share_rss_source),
                ErrorCorrectionLevel.L
            )
            RssSourceEditAction.AppLog -> showDialogFragment<AppLogDialog>()
            RssSourceEditAction.NetworkLog -> showDialogFragment<NetworkLogDialog>()
            RssSourceEditAction.Help -> showHelp("rssRuleHelp")
            RssSourceEditAction.InsertUrlOption -> {
                val target = editor.focusedField
                UrlOptionDialog(this) { insertAt(target, it) }.show()
            }
            RssSourceEditAction.JsHelp -> showHelp("jsHelp")
            RssSourceEditAction.RegexHelp -> showHelp("regexHelp")
            RssSourceEditAction.SelectFile -> {
                editor.focusedField?.let { target -> openChild(target, null) { selectDoc.launch(arrayOf("*/*")) } }
            }
            is RssSourceEditAction.SelectTab -> {
                editor.focusedField = null
                editor.selectedTab = action.index.coerceIn(0, 3)
            }
            is RssSourceEditAction.UpdateField -> updateField(action.key, action.value)
            is RssSourceEditAction.FocusField -> {
                editor.focusedField = RssEditorSelection(action.key, action.selectionStart, action.selectionEnd)
            }
            is RssSourceEditAction.ExpandField -> openFullEditor(action.key, action.label)
            is RssSourceEditAction.UpdateSource -> {
                val updated = action.source
                source = if (editor.draftBase.enabledCookieJar == null && updated.enabledCookieJar == false) {
                    updated.copy(enabledCookieJar = null)
                } else {
                    updated
                }
                editor.revision++
            }
            is RssSourceEditAction.AutoCompleteChanged -> {
                viewModel.autoComplete = action.enabled
                source = source.copy()
                editor.revision++
            }
        }
        scheduleDraftCheckpoint()
    }

    private fun saveAndFinish() {
        saveSource {
            setResult(RESULT_OK)
            forceFinish = true
            finish()
        }
    }

    private fun saveSource(onSuccess: (RssSource) -> Unit) {
        val savedDraft = source
        viewModel.save(normalizedSource()) { saved ->
            if (isFinishing) return@save
            val draftStillCurrent = source === savedDraft
            editor.savedForm = if (draftStillCurrent) saved else savedDraft
            if (draftStillCurrent) {
                if (!saved.hasSameEditorConfiguration(savedDraft)) editor.revision++
                editor.draftBase = saved
                source = saved
                editor.draftTextValues.clear()
                editor.fieldValueRevision += 1
            }
            session.checkpoint(captureDraft())
            if (!isDestroyed && draftStillCurrent) onSuccess(saved)
        }
    }

    private fun normalizedSource(): RssSource {
        return source.copy(
            ruleNextPage = viewModel.ruleComplete(source.ruleNextPage, source.ruleArticles, 2),
            ruleTitle = viewModel.ruleComplete(source.ruleTitle, source.ruleArticles),
            rulePubDate = viewModel.ruleComplete(source.rulePubDate, source.ruleArticles),
            ruleDescription = viewModel.ruleComplete(source.ruleDescription, source.ruleArticles),
            ruleImage = viewModel.ruleComplete(source.ruleImage, source.ruleArticles, 3),
            ruleLink = viewModel.ruleComplete(source.ruleLink, source.ruleArticles),
            ruleContent = viewModel.ruleComplete(source.ruleContent, source.ruleArticles)
        )
    }

    private fun openFullEditor(key: String, label: String) {
        if (key.isEmpty()) {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
            return
        }
        val value = fieldValue(key)
        val target = editor.focusedField?.takeIf { it.key == key }
            ?: RssEditorSelection(key, value.length, value.length)
        openChild(target, value) { request ->
            textEditLauncher.launch(Intent(this, CodeEditActivity::class.java).apply {
                putSourceFieldSession(request)
                putExtra("title", label)
                SourceEditCodeHighlighter.languageNameOf(key)?.let {
                    putExtra("languageName", it)
                }
            })
        }
    }

    private fun openChild(target: RssEditorSelection, text: String?, launch: (SourceEditorRequest) -> Unit) {
        val revision = editor.revision
        val field = target.toSessionField()
        val focus = editor.focusedField
        lifecycleScope.launch {
            try {
                session.openChild(field, revision, text, {
                    editor.initialized && !isFinishing && editor.revision == revision &&
                        editor.focusedField == focus && editor.selectedTab == field.tab &&
                        lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                }, ::captureDraft, launch)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                AppLog.put("打开订阅子编辑失败", error)
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
                        !isFinishing && editor.selectedTab == request.field.tab && editor.revision == request.revision
                    },
                    applyCode = { target, text, cursor ->
                        val current = fieldValue(target.key)
                        editor.focusedField = target.toSelection()
                        if (text != null && text != current) {
                            replaceFieldValue(target.key, RssEditorSnapshot(text, cursor, cursor))
                        } else {
                            val position = cursor.coerceIn(0, current.length)
                            editor.focusedField = RssEditorSelection(target.key, position, position)
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
                AppLog.put("回填订阅子编辑内容失败", error)
                toastOnUi(R.string.source_editor_result_failed)
            }
        }
    }

    private fun captureDraft(): RssSourceEditorDraft {
        val original = viewModel.rssSource
        return RssSourceEditorDraft(
            SourceEditorWindow(
                intent.getStringExtra("sourceUrl"), viewModel.persistedSourceUrl, editor.selectedTab,
                editor.focusedField?.toSessionField(), editor.revision, viewModel.autoComplete, session.pending,
            ),
            original, editor.savedForm.takeUnless { it === original }, editor.draftBase.takeUnless { it === original },
            source.takeUnless { it === original }, editor.draftTextValues.toMap(),
        )
    }

    private fun restoreDraft(draft: RssSourceEditorDraft) {
        val window = draft.window
        check(window.entryUrl == intent.getStringExtra("sourceUrl"))
        val original = draft.original
        editor.savedForm = draft.savedForm ?: original ?: RssSource()
        editor.draftBase = draft.base ?: original ?: RssSource()
        source = draft.source ?: original ?: RssSource()
        editor.draftTextValues.putAll(draft.fields)
        editor.selectedTab = window.tab.coerceIn(0, 3)
        editor.focusedField = window.selection?.takeIf { it.tab == editor.selectedTab }?.toSelection()
        editor.revision = window.revision
        editor.sourceRevision++
        viewModel.autoComplete = window.autoComplete
        session.restoreRequest(window.request)
        viewModel.restoreOriginal(window.persistedUrl, original)
    }

    private fun scheduleDraftCheckpoint() {
        if (editor.initialized && !isFinishing) session.scheduleCheckpoint(::captureDraft)
    }

    private fun RssEditorSelection.toSessionField() = SourceEditorField(editor.selectedTab, key, start, end)

    private fun SourceEditorField.toSelection() = RssEditorSelection(key, start, end)

    private fun insertAt(target: RssEditorSelection?, text: String) {
        val selection = target ?: run {
            toastOnUi(R.string.please_focus_cursor_on_textbox)
            return
        }
        val current = RssEditorSnapshot(fieldValue(selection.key), selection.start, selection.end)
        replaceFieldValue(selection.key, current.insert(text))
    }

    private fun currentSnapshot(key: String): RssEditorSnapshot {
        val text = fieldValue(key)
        val selection = editor.focusedField?.takeIf { it.key == key }
        return RssEditorSnapshot(text, selection?.start ?: text.length, selection?.end ?: text.length)
    }

    private fun replaceFieldValue(key: String, value: RssEditorSnapshot, recordHistory: Boolean = true) {
        if (recordHistory) editor.history.record(key, currentSnapshot(key), value.text)
        editor.draftTextValues[key] = value.text
        updateField(key, value.text)
        editor.focusedField = RssEditorSelection(
            key, value.start.coerceIn(0, value.text.length), value.end.coerceIn(0, value.text.length),
        )
        editor.fieldValueRevision += 1
    }

    private fun restoreHistory(redo: Boolean) {
        val key = editor.focusedField?.key ?: return
        val current = currentSnapshot(key)
        val restored = if (redo) editor.history.redo(key, current) else editor.history.undo(key, current)
        restored?.let { replaceFieldValue(key, it, recordHistory = false) }
    }

    private fun replaceSource(updated: RssSource) {
        if (isFinishing || isDestroyed) return
        editor.draftBase = updated
        source = updated
        editor.focusedField = null
        editor.history.clear()
        editor.draftTextValues.clear()
        editor.sourceRevision += 1
        editor.revision++
        scheduleDraftCheckpoint()
    }

    private fun observeKeyboardAssists() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appDb.keyboardAssistsDao.flowByType(0)
                    .catch { AppLog.put("键盘帮助浮窗获取数据失败\n${it.localizedMessage}", it) }
                    .flowOn(IO)
                    .collect { keyboardAssists = it }
            }
        }
    }

    private fun openKeyboardConfig() {
        showDialogFragment(KeyboardAssistsConfig(object : KeyboardAssistsConfig.CallBack {
            override fun requestLayout() {
                keyboardRowCount = AppConfig.showBoardLine
            }
        }))
    }

    private fun setSourceVariable() {
        saveSource { saved ->
            lifecycleScope.launch {
                val variable = withContext(IO) { saved.getVariable() }
                showDialogFragment(
                    VariableDialog(
                        getString(R.string.set_source_variable),
                        saved.getKey(),
                        variable,
                        saved.getDisplayVariableComment(
                            "源变量可在js中通过source.getVariable()获取"
                        )
                    )
                )
            }
        }
    }

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    private fun updateField(key: String, value: String) {
        val optionalValue = value.takeIf(String::isNotBlank)
            ?: editor.draftBase.textValue(key)?.takeIf { it == value }
        source = when (key) {
            "sourceName" -> source.copy(sourceName = value)
            "sourceUrl" -> source.copy(sourceUrl = value)
            "sourceIcon" -> source.copy(sourceIcon = value)
            "sourceGroup" -> source.copy(sourceGroup = optionalValue)
            "sourceComment" -> source.copy(sourceComment = optionalValue)
            "searchUrl" -> source.copy(searchUrl = optionalValue)
            "sortUrl" -> source.copy(sortUrl = optionalValue)
            "loginUrl" -> source.copy(loginUrl = optionalValue)
            "loginUi" -> source.copy(loginUi = optionalValue)
            "loginCheckJs" -> source.copy(loginCheckJs = optionalValue)
            "coverDecodeJs" -> source.copy(coverDecodeJs = optionalValue)
            "header" -> source.copy(header = optionalValue)
            "variableComment" -> source.copy(variableComment = optionalValue)
            "concurrentRate" -> source.copy(concurrentRate = optionalValue)
            "jsLib" -> source.copy(jsLib = optionalValue)
            "startHtml" -> source.copy(startHtml = optionalValue)
            "startStyle" -> source.copy(startStyle = optionalValue)
            "startJs" -> source.copy(startJs = optionalValue)
            "preloadJs" -> source.copy(preloadJs = optionalValue)
            "ruleArticles" -> source.copy(ruleArticles = optionalValue)
            "ruleNextPage" -> source.copy(ruleNextPage = optionalValue)
            "ruleTitle" -> source.copy(ruleTitle = optionalValue)
            "rulePubDate" -> source.copy(rulePubDate = optionalValue)
            "ruleDescription" -> source.copy(ruleDescription = optionalValue)
            "ruleImage" -> source.copy(ruleImage = optionalValue)
            "ruleLink" -> source.copy(ruleLink = optionalValue)
            "enableJs" -> source.copy(enableJs = value.toBoolean())
            "loadWithBaseUrl" -> source.copy(loadWithBaseUrl = value.toBoolean())
            "showWebLog" -> source.copy(showWebLog = value.toBoolean())
            "cacheFirst" -> source.copy(cacheFirst = value.toBoolean())
            "ruleContent" -> source.copy(ruleContent = optionalValue)
            "style" -> source.copy(style = optionalValue)
            "injectJs" -> source.copy(injectJs = optionalValue)
            "contentWhitelist" -> source.copy(contentWhitelist = optionalValue)
            "contentBlacklist" -> source.copy(contentBlacklist = optionalValue)
            "shouldOverrideUrlLoading" -> {
                source.copy(shouldOverrideUrlLoading = optionalValue)
            }
            else -> source
        }
        editor.revision++
    }

    private fun fieldValue(key: String): String = editor.draftTextValues[key] ?: source.textValue(key).orEmpty()

    private fun RssSource.textValue(key: String): String? = when (key) {
        "sourceName" -> sourceName
        "sourceUrl" -> sourceUrl
        "sourceIcon" -> sourceIcon
        "sourceGroup" -> sourceGroup
        "sourceComment" -> sourceComment
        "searchUrl" -> searchUrl
        "sortUrl" -> sortUrl
        "loginUrl" -> loginUrl
        "loginUi" -> loginUi
        "loginCheckJs" -> loginCheckJs
        "coverDecodeJs" -> coverDecodeJs
        "header" -> header
        "variableComment" -> variableComment
        "concurrentRate" -> concurrentRate
        "jsLib" -> jsLib
        "startHtml" -> startHtml
        "startStyle" -> startStyle
        "startJs" -> startJs
        "preloadJs" -> preloadJs
        "ruleArticles" -> ruleArticles
        "ruleNextPage" -> ruleNextPage
        "ruleTitle" -> ruleTitle
        "rulePubDate" -> rulePubDate
        "ruleDescription" -> ruleDescription
        "ruleImage" -> ruleImage
        "ruleLink" -> ruleLink
        "ruleContent" -> ruleContent
        "style" -> style
        "injectJs" -> injectJs
        "contentWhitelist" -> contentWhitelist
        "contentBlacklist" -> contentBlacklist
        "shouldOverrideUrlLoading" -> shouldOverrideUrlLoading
        else -> null
    }
}
