package io.legado.app.ui.rss.source.manage

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.RssComposeBinding
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.utils.ACache
import io.legado.app.utils.CreateFileContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.takePersistableReadPermission
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private enum class SourceInputRequest { IMPORT_ONLINE, ADD_GROUP, REMOVE_GROUP }

private sealed interface SourceDeleteRequest {
    data class One(val source: RssSource) : SourceDeleteRequest
    data class Selection(val sources: List<RssSource>) : SourceDeleteRequest
}

/** 订阅源管理。选择、筛选、拖排、导入导出和批量动作均保留，UI 使用 Compose。 */
class RssSourceActivity :
    VMBaseActivity<RssComposeBinding, RssSourceViewModel>() {

    override val binding by viewBinding(RssComposeBinding::inflate)
    override val viewModel by viewModels<RssSourceViewModel>()

    private val importRecordKey = "rssSourceRecordKey"
    private var sources by mutableStateOf<List<RssSource>>(
        emptyList(),
        referentialEqualityPolicy()
    )
    private var groups by mutableStateOf<List<String>>(emptyList())
    private var query by mutableStateOf("")
    private var selectedUrls by mutableStateOf<Set<String>>(emptySet())
    private var inputRequest by mutableStateOf<SourceInputRequest?>(null)
    private var deleteRequest by mutableStateOf<SourceDeleteRequest?>(null)
    private var exportedPath by mutableStateOf<String?>(null)
    private var sourceFlowJob: Job? = null

    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportRssSourceDialog(it))
    }
    private val importDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            showDialogFragment(ImportRssSourceDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(CreateFileContract()) {
        it.save(this, this) { uri -> exportedPath = uri.toString() }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.root.setContent {
            NgAppTheme {
                RssSourceManageScreen(
                    sources = sources,
                    groups = groups,
                    query = query,
                    selectedUrls = selectedUrls,
                    onAction = ::handleAction
                )
                inputRequest?.let { request ->
                    val title = when (request) {
                        SourceInputRequest.IMPORT_ONLINE -> getString(R.string.import_on_line)
                        SourceInputRequest.ADD_GROUP -> getString(R.string.add_group)
                        SourceInputRequest.REMOVE_GROUP -> getString(R.string.remove_group)
                    }
                    RssSourceTextDialog(
                        title = title,
                        placeholder = if (request == SourceInputRequest.IMPORT_ONLINE) {
                            getString(R.string.rss_source_import_url_hint)
                        } else {
                            title
                        },
                        suggestions = if (request == SourceInputRequest.IMPORT_ONLINE) {
                            importHistory()
                        } else {
                            groups
                        },
                        onDismiss = { inputRequest = null },
                        onConfirm = { handleInput(request, it) }
                    )
                }
                deleteRequest?.let { request ->
                    val message = when (request) {
                        is SourceDeleteRequest.One -> {
                            "${getString(R.string.sure_del)}\n${request.source.sourceName}"
                        }
                        is SourceDeleteRequest.Selection -> {
                            "${getString(R.string.sure_del)}\n${request.sources.size}"
                        }
                    }
                    RssSourceConfirmDialog(
                        title = getString(R.string.draw),
                        message = message,
                        onDismiss = { deleteRequest = null },
                        onConfirm = {
                            when (request) {
                                is SourceDeleteRequest.One -> viewModel.del(request.source)
                                is SourceDeleteRequest.Selection -> {
                                    viewModel.del(*request.sources.toTypedArray())
                                    selectedUrls = emptySet()
                                }
                            }
                            deleteRequest = null
                        }
                    )
                }
                exportedPath?.let { path ->
                    RssSourceTextDialog(
                        title = getString(R.string.export_success),
                        initialValue = path,
                        onDismiss = { exportedPath = null },
                        onConfirm = {
                            sendToClip(it)
                            exportedPath = null
                        }
                    )
                }
            }
        }
        lifecycleScope.launch {
            appDb.rssSourceDao.flowGroups().conflate().collect { groups = it }
        }
        updateSourceFlow()
    }

    private fun handleAction(action: RssSourceManageAction) {
        val selection = selectedSources()
        when (action) {
            RssSourceManageAction.Back -> finish()
            RssSourceManageAction.Add -> startActivity<RssSourceEditActivity>()
            RssSourceManageAction.ImportLocal -> {
                importDoc.launch(arrayOf("text/*", "application/json"))
            }
            RssSourceManageAction.ImportOnline -> inputRequest = SourceInputRequest.IMPORT_ONLINE
            RssSourceManageAction.ImportQr -> qrCodeResult.launch()
            RssSourceManageAction.ImportDefault -> viewModel.importDefault()
            RssSourceManageAction.ManageGroups -> showDialogFragment<GroupManageDialog>()
            RssSourceManageAction.Help -> showHelp("SourceMRssHelp")
            RssSourceManageAction.DeleteSelection -> {
                if (selection.isNotEmpty()) deleteRequest = SourceDeleteRequest.Selection(selection)
            }
            RssSourceManageAction.EnableSelection -> viewModel.enableSelection(selection)
            RssSourceManageAction.DisableSelection -> viewModel.disableSelection(selection)
            RssSourceManageAction.AddSelectionToGroup -> {
                if (selection.isNotEmpty()) inputRequest = SourceInputRequest.ADD_GROUP
            }
            RssSourceManageAction.RemoveSelectionFromGroup -> {
                if (selection.isNotEmpty()) inputRequest = SourceInputRequest.REMOVE_GROUP
            }
            RssSourceManageAction.TopSelection -> viewModel.topSource(*selection.toTypedArray())
            RssSourceManageAction.BottomSelection -> viewModel.bottomSource(*selection.toTypedArray())
            RssSourceManageAction.ExportSelection -> viewModel.saveToFile(selection) { file, name ->
                exportResult.launch(CreateFileContract.FileData(name, file, "application/json"))
            }
            RssSourceManageAction.ShareSelection -> viewModel.saveToFile(selection) { file, _ ->
                share(file)
            }
            RssSourceManageAction.CompleteSelectionInterval -> completeSelectionInterval()
            RssSourceManageAction.SelectAll -> {
                selectedUrls = sources.mapTo(linkedSetOf(), RssSource::sourceUrl)
            }
            RssSourceManageAction.InvertSelection -> {
                selectedUrls = sources.mapNotNullTo(linkedSetOf()) {
                    it.sourceUrl.takeUnless(selectedUrls::contains)
                }
            }
            is RssSourceManageAction.QueryChanged -> {
                query = action.query
                selectedUrls = emptySet()
                updateSourceFlow(action.query)
            }
            is RssSourceManageAction.SelectionChanged -> {
                selectedUrls = selectedUrls.toMutableSet().apply {
                    if (action.selected) {
                        add(action.source.sourceUrl)
                    } else {
                        remove(action.source.sourceUrl)
                    }
                }
            }
            is RssSourceManageAction.ToggleEnabled -> {
                viewModel.update(action.source.copy(enabled = action.enabled))
            }
            is RssSourceManageAction.Edit -> startActivity<RssSourceEditActivity> {
                putExtra("sourceUrl", action.source.sourceUrl)
            }
            is RssSourceManageAction.Delete -> {
                selectedUrls = selectedUrls - action.source.sourceUrl
                deleteRequest = SourceDeleteRequest.One(action.source)
            }
            is RssSourceManageAction.Top -> viewModel.topSource(action.source)
            is RssSourceManageAction.Bottom -> viewModel.bottomSource(action.source)
            is RssSourceManageAction.Reorder -> {
                val reordered = action.sources.mapIndexed { index, source ->
                    source.copy(customOrder = index + 1)
                }
                viewModel.update(*reordered.toTypedArray())
            }
        }
    }

    private fun updateSourceFlow(searchKey: String? = query) {
        sourceFlowJob?.cancel()
        sourceFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> appDb.rssSourceDao.flowAll()
                searchKey == getString(R.string.enabled) -> appDb.rssSourceDao.flowEnabled()
                searchKey == getString(R.string.disabled) -> appDb.rssSourceDao.flowDisabled()
                searchKey == getString(R.string.need_login) -> appDb.rssSourceDao.flowLogin()
                searchKey == getString(R.string.no_group) -> appDb.rssSourceDao.flowNoGroup()
                searchKey.startsWith("group:") -> {
                    appDb.rssSourceDao.flowGroupSearch(searchKey.substringAfter("group:"))
                }
                else -> appDb.rssSourceDao.flowSearch(searchKey)
            }.catch {
                AppLog.put("订阅源管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect { list ->
                sources = list
                selectedUrls = selectedUrls.intersect(list.mapTo(hashSetOf(), RssSource::sourceUrl))
                delay(100)
            }
        }
    }

    private fun selectedSources(): List<RssSource> {
        return sources.filter { it.sourceUrl in selectedUrls }
    }

    private fun completeSelectionInterval() {
        val selectedIndices = sources.indices.filter { sources[it].sourceUrl in selectedUrls }
        val min = selectedIndices.minOrNull() ?: return
        val max = selectedIndices.maxOrNull() ?: return
        selectedUrls = selectedUrls + sources.subList(min, max + 1).map(RssSource::sourceUrl)
    }

    private fun handleInput(request: SourceInputRequest, value: String) {
        val text = value.trim()
        when (request) {
            SourceInputRequest.IMPORT_ONLINE -> if (text.isNotBlank()) {
                val history = importHistory().toMutableList()
                if (text.isAbsUrl() && text !in history) {
                    history.add(0, text)
                    ACache.get(cacheDir = false).put(importRecordKey, history.joinToString(","))
                }
                showDialogFragment(ImportRssSourceDialog(text))
            }
            SourceInputRequest.ADD_GROUP -> if (text.isNotBlank()) {
                viewModel.selectionAddToGroups(selectedSources(), text)
            }
            SourceInputRequest.REMOVE_GROUP -> if (text.isNotBlank()) {
                viewModel.selectionRemoveFromGroups(selectedSources(), text)
            }
        }
        inputRequest = null
    }

    private fun importHistory(): List<String> {
        return ACache.get(cacheDir = false)
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toList()
            .orEmpty()
    }
}
