package io.legado.app.ui.rss.source.edit

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
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
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.RssComposeBinding
import io.legado.app.ui.rss.source.debug.RssSourceDebugActivity
import io.legado.app.ui.source.edit.SourceEditCodeHighlighter
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

    private var source by mutableStateOf(
        RssSource(),
        referentialEqualityPolicy()
    )
    private var originalSource = RssSource()
    private var selectedTab by mutableIntStateOf(0)
    private var focusedField by mutableStateOf<RssEditorSelection?>(null)
    private var sourceRevision by mutableIntStateOf(0)
    private var fieldValueRevision by mutableIntStateOf(0)
    private var keyboardAssists by mutableStateOf<List<KeyboardAssist>>(emptyList())
    private var keyboardRowCount by mutableIntStateOf(AppConfig.showBoardLine)
    private val editHistory = RssSourceEditorHistory()
    // 保留输入中的空白；空字段转 null 仍由原 source 更新逻辑处理。
    private val draftTextValues = mutableStateMapOf<String, String>()
    private var pendingInsert: RssEditorSelection? = null
    private var editingFieldKey: String? = null
    private var pendingExit by mutableStateOf(false)
    private var forceFinish = false

    private val selectDoc = registerForActivityResult(SelectFileContract()) { uri ->
        val target = pendingInsert
        pendingInsert = null
        uri?.let {
            it.takePersistableReadPermission()
            insertAt(target, if (it.isContentScheme()) it.toString() else it.path.orEmpty())
        }
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
        val key = editingFieldKey
        editingFieldKey = null
        if (result.resultCode == RESULT_OK && key != null) {
            result.data?.let { data ->
                val text = data.getStringExtra("text") ?: fieldValue(key)
                val cursor = data.getIntExtra("cursorPosition", text.length).coerceIn(0, text.length)
                replaceFieldValue(key, RssEditorSnapshot(text, cursor, cursor))
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        observeKeyboardAssists()
        binding.root.setContent {
            NgAppTheme {
                RssSourceEditScreen(
                    source = source,
                    selectedTab = selectedTab,
                    autoComplete = viewModel.autoComplete,
                    sourceRevision = sourceRevision,
                    fieldValueRevision = fieldValueRevision,
                    focusedField = focusedField,
                    draftTextValues = draftTextValues,
                    editEntityMaxLine = Int.MAX_VALUE,
                    keyboardAssists = keyboardAssists,
                    keyboardRowCount = keyboardRowCount,
                    onAction = ::handleAction
                )
                if (pendingExit) {
                    RssSourceExitDialog(
                        onDismiss = { pendingExit = false },
                        onDiscard = {
                            pendingExit = false
                            forceFinish = true
                            finish()
                        }
                    )
                }
            }
        }
        viewModel.initData(intent) {
            originalSource = viewModel.rssSource?.copy() ?: RssSource()
            replaceSource(originalSource)
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        if (!LocalConfig.ruleHelpVersionIsLast) showHelp("rssRuleHelp")
    }

    override fun finish() {
        if (!forceFinish && !source.equal(originalSource)) {
            pendingExit = true
            return
        }
        super.finish()
    }

    private fun handleAction(action: RssSourceEditAction) {
        when (action) {
            RssSourceEditAction.Undo -> restoreHistory(redo = false)
            RssSourceEditAction.Redo -> restoreHistory(redo = true)
            RssSourceEditAction.KeyboardConfig -> openKeyboardConfig()
            is RssSourceEditAction.InsertText -> insertAt(focusedField, action.text)
            is RssSourceEditAction.EditText -> {
                editHistory.record(action.key, currentSnapshot(action.key), action.value)
                if (fieldValue(action.key) != action.value) {
                    draftTextValues[action.key] = action.value
                    updateField(action.key, action.value)
                }
                focusedField = RssEditorSelection(action.key, action.start, action.end)
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
                val target = focusedField
                UrlOptionDialog(this) { insertAt(target, it) }.show()
            }
            RssSourceEditAction.JsHelp -> showHelp("jsHelp")
            RssSourceEditAction.RegexHelp -> showHelp("regexHelp")
            RssSourceEditAction.SelectFile -> {
                pendingInsert = focusedField
                selectDoc.launch(arrayOf("*/*"))
            }
            is RssSourceEditAction.SelectTab -> {
                focusedField = null
                selectedTab = action.index.coerceIn(0, 3)
            }
            is RssSourceEditAction.UpdateField -> updateField(action.key, action.value)
            is RssSourceEditAction.FocusField -> {
                focusedField = RssEditorSelection(action.key, action.selectionStart, action.selectionEnd)
            }
            is RssSourceEditAction.ExpandField -> openFullEditor(action.key, action.label)
            is RssSourceEditAction.UpdateSource -> source = action.source
            is RssSourceEditAction.AutoCompleteChanged -> {
                viewModel.autoComplete = action.enabled
                source = source.copy()
            }
        }
    }

    private fun saveAndFinish() {
        saveSource {
            setResult(RESULT_OK)
            forceFinish = true
            finish()
        }
    }

    private fun saveSource(onSuccess: (RssSource) -> Unit) {
        viewModel.save(normalizedSource()) { saved ->
            source = saved.copy()
            draftTextValues.clear()
            fieldValueRevision += 1
            originalSource = saved.copy()
            onSuccess(saved)
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
        editingFieldKey = key
        val value = fieldValue(key)
        textEditLauncher.launch(
            Intent(this, CodeEditActivity::class.java).apply {
                putExtra("text", value)
                putExtra("title", label)
                putExtra(
                    "cursorPosition",
                    if (focusedField?.key == key) {
                        focusedField!!.end.coerceIn(0, value.length)
                    } else {
                        value.length
                    }
                )
                SourceEditCodeHighlighter.languageNameOf(key)?.let {
                    putExtra("languageName", it)
                }
            }
        )
    }

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
        val selection = focusedField?.takeIf { it.key == key }
        return RssEditorSnapshot(text, selection?.start ?: text.length, selection?.end ?: text.length)
    }

    private fun replaceFieldValue(key: String, value: RssEditorSnapshot, recordHistory: Boolean = true) {
        if (recordHistory) editHistory.record(key, currentSnapshot(key), value.text)
        draftTextValues[key] = value.text
        updateField(key, value.text)
        focusedField = RssEditorSelection(
            key, value.start.coerceIn(0, value.text.length), value.end.coerceIn(0, value.text.length),
        )
        fieldValueRevision += 1
    }

    private fun restoreHistory(redo: Boolean) {
        val key = focusedField?.key ?: return
        val current = currentSnapshot(key)
        val restored = if (redo) editHistory.redo(key, current) else editHistory.undo(key, current)
        restored?.let { replaceFieldValue(key, it, recordHistory = false) }
    }

    private fun replaceSource(updated: RssSource) {
        source = updated.copy()
        focusedField = null
        editingFieldKey = null
        pendingInsert = null
        editHistory.clear()
        draftTextValues.clear()
        sourceRevision += 1
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

    private fun valueOrNull(value: String): String? = value.takeIf(String::isNotBlank)

    private fun updateField(key: String, value: String) {
        source = when (key) {
            "sourceName" -> source.copy(sourceName = value)
            "sourceUrl" -> source.copy(sourceUrl = value)
            "sourceIcon" -> source.copy(sourceIcon = value)
            "sourceGroup" -> source.copy(sourceGroup = valueOrNull(value))
            "sourceComment" -> source.copy(sourceComment = valueOrNull(value))
            "searchUrl" -> source.copy(searchUrl = valueOrNull(value))
            "sortUrl" -> source.copy(sortUrl = valueOrNull(value))
            "loginUrl" -> source.copy(loginUrl = valueOrNull(value))
            "loginUi" -> source.copy(loginUi = valueOrNull(value))
            "loginCheckJs" -> source.copy(loginCheckJs = valueOrNull(value))
            "coverDecodeJs" -> source.copy(coverDecodeJs = valueOrNull(value))
            "header" -> source.copy(header = valueOrNull(value))
            "variableComment" -> source.copy(variableComment = valueOrNull(value))
            "concurrentRate" -> source.copy(concurrentRate = valueOrNull(value))
            "jsLib" -> source.copy(jsLib = valueOrNull(value))
            "startHtml" -> source.copy(startHtml = valueOrNull(value))
            "startStyle" -> source.copy(startStyle = valueOrNull(value))
            "startJs" -> source.copy(startJs = valueOrNull(value))
            "preloadJs" -> source.copy(preloadJs = valueOrNull(value))
            "ruleArticles" -> source.copy(ruleArticles = valueOrNull(value))
            "ruleNextPage" -> source.copy(ruleNextPage = valueOrNull(value))
            "ruleTitle" -> source.copy(ruleTitle = valueOrNull(value))
            "rulePubDate" -> source.copy(rulePubDate = valueOrNull(value))
            "ruleDescription" -> source.copy(ruleDescription = valueOrNull(value))
            "ruleImage" -> source.copy(ruleImage = valueOrNull(value))
            "ruleLink" -> source.copy(ruleLink = valueOrNull(value))
            "enableJs" -> source.copy(enableJs = value.toBoolean())
            "loadWithBaseUrl" -> source.copy(loadWithBaseUrl = value.toBoolean())
            "showWebLog" -> source.copy(showWebLog = value.toBoolean())
            "cacheFirst" -> source.copy(cacheFirst = value.toBoolean())
            "ruleContent" -> source.copy(ruleContent = valueOrNull(value))
            "style" -> source.copy(style = valueOrNull(value))
            "injectJs" -> source.copy(injectJs = valueOrNull(value))
            "contentWhitelist" -> source.copy(contentWhitelist = valueOrNull(value))
            "contentBlacklist" -> source.copy(contentBlacklist = valueOrNull(value))
            "shouldOverrideUrlLoading" -> {
                source.copy(shouldOverrideUrlLoading = valueOrNull(value))
            }
            else -> source
        }
    }

    private fun fieldValue(key: String): String = draftTextValues[key] ?: when (key) {
        "sourceName" -> source.sourceName
        "sourceUrl" -> source.sourceUrl
        "sourceIcon" -> source.sourceIcon
        "sourceGroup" -> source.sourceGroup.orEmpty()
        "sourceComment" -> source.sourceComment.orEmpty()
        "searchUrl" -> source.searchUrl.orEmpty()
        "sortUrl" -> source.sortUrl.orEmpty()
        "loginUrl" -> source.loginUrl.orEmpty()
        "loginUi" -> source.loginUi.orEmpty()
        "loginCheckJs" -> source.loginCheckJs.orEmpty()
        "coverDecodeJs" -> source.coverDecodeJs.orEmpty()
        "header" -> source.header.orEmpty()
        "variableComment" -> source.variableComment.orEmpty()
        "concurrentRate" -> source.concurrentRate.orEmpty()
        "jsLib" -> source.jsLib.orEmpty()
        "startHtml" -> source.startHtml.orEmpty()
        "startStyle" -> source.startStyle.orEmpty()
        "startJs" -> source.startJs.orEmpty()
        "preloadJs" -> source.preloadJs.orEmpty()
        "ruleArticles" -> source.ruleArticles.orEmpty()
        "ruleNextPage" -> source.ruleNextPage.orEmpty()
        "ruleTitle" -> source.ruleTitle.orEmpty()
        "rulePubDate" -> source.rulePubDate.orEmpty()
        "ruleDescription" -> source.ruleDescription.orEmpty()
        "ruleImage" -> source.ruleImage.orEmpty()
        "ruleLink" -> source.ruleLink.orEmpty()
        "ruleContent" -> source.ruleContent.orEmpty()
        "style" -> source.style.orEmpty()
        "injectJs" -> source.injectJs.orEmpty()
        "contentWhitelist" -> source.contentWhitelist.orEmpty()
        "contentBlacklist" -> source.contentBlacklist.orEmpty()
        "shouldOverrideUrlLoading" -> source.shouldOverrideUrlLoading.orEmpty()
        else -> ""
    }
}
