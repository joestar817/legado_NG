package io.legado.app.ui.book.source.manage

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.model.CheckSource
import io.legado.app.model.CheckSourceTaskStatus
import io.legado.app.model.CheckSourceTaskStore
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.manage.BookSourceExportSheet
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.edit.JsSourceEditActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.rss.source.manage.RssSourceTextDialog
import io.legado.app.utils.ACache
import io.legado.app.utils.CreateFileContract
import io.legado.app.utils.SelectFileContract
import io.legado.app.utils.cnCompare
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.takePersistableReadPermission
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.util.Locale

private enum class BookSourceInputRequest { IMPORT_ONLINE }

private data class BookSourceSectionDeleteRequest(
    val groupName: String?,
    val title: String,
    val sources: List<BookSourcePart>,
)

private data class BookSourceDeleteConfirmRequest(
    val sources: List<BookSourcePart>,
    val itemName: String? = null,
)

private data class BookSourceAutoGroupRequest(
    val sources: List<BookSourcePart>,
)

/**
 * 书源管理的 Compose 宿主。声明式规则源与单文件 JavaScript 源共用筛选、分组和批量管理。
 */
class BookSourceActivity :
    VMBaseActivity<ComposeActivityBinding, BookSourceViewModel>() {

    companion object {
        const val EXTRA_OPEN_GROUP_VIEW = "openGroupView"
    }

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<BookSourceViewModel>()

    private val importRecordKey = "bookSourceRecordKey"
    private var sources by mutableStateOf<List<BookSourcePart>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var groups by mutableStateOf<List<String>>(emptyList())
    private var query by mutableStateOf("")
    private var selectedUrls by mutableStateOf<Set<String>>(emptySet())
    private var viewMode by mutableStateOf(BookSourceViewMode.LIST)
    private var sort by mutableStateOf(BookSourceSort.Default)
    private var sortAscending by mutableStateOf(true)
    private var expandedSections by mutableStateOf<Set<String>>(emptySet())
    private var inputRequest by mutableStateOf<BookSourceInputRequest?>(null)
    private var deleteConfirmRequest by mutableStateOf<BookSourceDeleteConfirmRequest?>(null)
    private var sectionDeleteRequest by mutableStateOf<BookSourceSectionDeleteRequest?>(null)
    private var autoGroupRequest by mutableStateOf<BookSourceAutoGroupRequest?>(null)
    private var clearGroupsRequest by mutableStateOf<List<BookSourcePart>?>(null)
    private var switchRequest by mutableStateOf<BookSourcePart?>(null)
    private var selectionCapabilityRequest by mutableStateOf<List<BookSourcePart>?>(null)
    private var checkRequest by mutableStateOf<List<BookSourcePart>?>(null)
    private var sourceFlowJob: Job? = null

    private val qrResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportBookSourceDialog(it))
    }
    private val importDoc = registerForActivityResult(SelectFileContract()) { uri ->
        uri?.let {
            it.takePersistableReadPermission()
            showDialogFragment(ImportBookSourceDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(CreateFileContract()) {
        it.save(this, this) { toastOnUi(R.string.export_success) }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        applyNavigationIntent(intent, refreshSources = false)
        binding.composeView.setContent {
            NgAppTheme {
                val checkTaskState by CheckSourceTaskStore.state.collectAsState()
                BookSourceManageScreen(
                    sources = sources,
                    groups = groups,
                    query = query,
                    selectedUrls = selectedUrls,
                    viewMode = viewMode,
                    sort = sort,
                    sortAscending = sortAscending,
                    expandedSections = expandedSections,
                    checkTaskState = checkTaskState,
                    onAction = ::handleAction,
                )
                inputRequest?.let { request ->
                    RssSourceTextDialog(
                        title = getString(R.string.import_on_line),
                        placeholder = "URL",
                        suggestions = importHistory(),
                        onDismiss = { inputRequest = null },
                        onConfirm = { handleInput(request, it) },
                    )
                }
                deleteConfirmRequest?.let { request ->
                    BookSourceDeleteConfirmDialog(
                        itemName = request.itemName,
                        onDismiss = { deleteConfirmRequest = null },
                        onConfirm = {
                            viewModel.del(request.sources)
                            selectedUrls = selectedUrls - request.sources
                                .map(BookSourcePart::bookSourceUrl)
                                .toSet()
                            deleteConfirmRequest = null
                        },
                    )
                }
                sectionDeleteRequest?.let { request ->
                    BookSourceSectionDeleteDialog(
                        title = request.title,
                        groupName = request.groupName,
                        sourceCount = request.sources.size,
                        onDismiss = { sectionDeleteRequest = null },
                        onConfirm = { mode ->
                            when (mode) {
                                BookSourceSectionDeleteMode.GROUP_ONLY -> {
                                    request.groupName?.let(viewModel::delGroup)
                                }
                                BookSourceSectionDeleteMode.GROUP_AND_SOURCES -> {
                                    request.groupName?.let(viewModel::delGroupAndSources)
                                        ?: viewModel.del(request.sources)
                                    selectedUrls = selectedUrls - request.sources
                                        .map(BookSourcePart::bookSourceUrl)
                                        .toSet()
                                }
                            }
                            sectionDeleteRequest = null
                        },
                    )
                }
                autoGroupRequest?.let { request ->
                    BookSourceAutoGroupDialog(
                        onDismiss = { autoGroupRequest = null },
                        onConfirm = { selectedRuleTypes ->
                            viewModel.selectionAutoGroup(request.sources, selectedRuleTypes)
                            autoGroupRequest = null
                        },
                    )
                }
                clearGroupsRequest?.let { request ->
                    BookSourceClearGroupsDialog(
                        sourceCount = request.size,
                        onDismiss = { clearGroupsRequest = null },
                        onConfirm = {
                            viewModel.selectionClearGroups(request)
                            clearGroupsRequest = null
                        },
                    )
                }
                switchRequest?.let { source ->
                    BookSourceSwitchDialog(
                        source = source,
                        onDismiss = { switchRequest = null },
                        onConfirm = { searchEnabled, exploreEnabled ->
                            viewModel.updateSourceSwitches(
                                source = source,
                                searchEnabled = searchEnabled,
                                exploreEnabled = exploreEnabled,
                            )
                            switchRequest = null
                        },
                    )
                }
                selectionCapabilityRequest?.let { selection ->
                    BookSourceSelectionCapabilityDialog(
                        sources = selection,
                        onDismiss = { selectionCapabilityRequest = null },
                        onConfirm = { searchEnabled, exploreEnabled ->
                            viewModel.updateSelectionCapabilities(
                                sources = selection,
                                searchEnabled = searchEnabled,
                                exploreEnabled = exploreEnabled,
                            )
                            selectionCapabilityRequest = null
                        },
                    )
                }
                checkRequest?.let { selection ->
                    BookSourceCheckDialog(
                        onDismiss = { checkRequest = null },
                        onConfirm = { result ->
                            startCheckSource(selection, result)
                            checkRequest = null
                        },
                    )
                }
            }
        }
        lifecycleScope.launch {
            appDb.bookSourceDao.flowGroups().conflate().collect { groups = it }
        }
        updateSourceFlow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyNavigationIntent(intent, refreshSources = true)
    }

    private fun applyNavigationIntent(intent: Intent, refreshSources: Boolean) {
        if (!intent.getBooleanExtra(EXTRA_OPEN_GROUP_VIEW, false)) return
        intent.removeExtra(EXTRA_OPEN_GROUP_VIEW)
        query = ""
        viewMode = BookSourceViewMode.GROUP
        expandedSections = emptySet()
        if (refreshSources) updateSourceFlow("")
    }

    private fun handleAction(action: BookSourceManageAction) {
        val selection = selectedSources()
        when (action) {
            BookSourceManageAction.Back -> finish()
            BookSourceManageAction.AddDeclarative -> startActivity<BookSourceEditActivity>()
            BookSourceManageAction.AddJavaScript -> startActivity<JsSourceEditActivity>()
            BookSourceManageAction.ImportLocal -> {
                importDoc.launch(arrayOf("text/*", "application/json", "application/javascript"))
            }
            BookSourceManageAction.ImportOnline -> {
                inputRequest = BookSourceInputRequest.IMPORT_ONLINE
            }
            BookSourceManageAction.ImportQr -> qrResult.launch()
            BookSourceManageAction.ManageGroups -> showDialogFragment<GroupManageDialog>()
            BookSourceManageAction.ToggleSortDirection -> {
                sortAscending = !sortAscending
                updateSourceFlow()
            }
            BookSourceManageAction.SelectAll -> {
                selectedUrls = selectableSources().mapTo(linkedSetOf(), BookSourcePart::bookSourceUrl)
            }
            BookSourceManageAction.InvertSelection -> {
                selectedUrls = selectableSources().mapNotNullTo(linkedSetOf()) {
                    it.bookSourceUrl.takeUnless(selectedUrls::contains)
                }
            }
            BookSourceManageAction.ConfigureSelectionCapabilities -> {
                if (selection.isNotEmpty()) selectionCapabilityRequest = selection
            }
            BookSourceManageAction.AddSelectionToGroup -> if (selection.isNotEmpty()) {
                BookSourceAddGroupSheet(
                    activity = this,
                    sources = selection,
                    onAddToGroup = viewModel::selectionAddToGroups,
                ).show()
            }
            BookSourceManageAction.ClearSelectionGroups -> if (selection.isNotEmpty()) {
                clearGroupsRequest = selection
            }
            BookSourceManageAction.AutoGroupSelection -> if (selection.isNotEmpty()) {
                autoGroupRequest = BookSourceAutoGroupRequest(selection)
            }
            BookSourceManageAction.CheckSelection -> {
                if (CheckSourceTaskStore.state.value.status == CheckSourceTaskStatus.RUNNING) {
                    startActivity<BookSourceCheckActivity>()
                } else if (selection.isNotEmpty()) {
                    checkRequest = selection
                }
            }
            BookSourceManageAction.OpenCheckTask -> startActivity<BookSourceCheckActivity>()
            BookSourceManageAction.DismissCheckTask -> {
                CheckSourceTaskStore.dismissManageEntry()
            }
            BookSourceManageAction.CompleteSelectionInterval -> completeSelectionInterval()
            BookSourceManageAction.TopSelection -> viewModel.topSource(*selection.toTypedArray())
            BookSourceManageAction.BottomSelection -> viewModel.bottomSource(*selection.toTypedArray())
            BookSourceManageAction.ExportOrShareSelection -> showExportSheet(selection)
            BookSourceManageAction.DeleteSelection -> if (selection.isNotEmpty()) {
                deleteConfirmRequest = BookSourceDeleteConfirmRequest(selection)
            }
            is BookSourceManageAction.QueryChanged -> {
                query = action.query
                expandedSections = emptySet()
                updateSourceFlow(action.query)
            }
            is BookSourceManageAction.ViewModeChanged -> {
                viewMode = action.mode
                expandedSections = emptySet()
            }
            is BookSourceManageAction.SortChanged -> {
                sort = action.sort
                updateSourceFlow()
            }
            is BookSourceManageAction.SelectionChanged -> updateSelection(
                action.source.bookSourceUrl,
                action.selected,
            )
            is BookSourceManageAction.SectionSelectionChanged -> {
                val urls = action.sources.map(BookSourcePart::bookSourceUrl).toSet()
                selectedUrls = if (action.selected) selectedUrls + urls else selectedUrls - urls
            }
            is BookSourceManageAction.ConfigureCapabilities -> switchRequest = action.source
            is BookSourceManageAction.Edit -> editSource(action.source)
            is BookSourceManageAction.Login -> startActivity<io.legado.app.ui.login.SourceLoginActivity> {
                putExtra("type", "bookSource")
                putExtra("key", action.source.bookSourceUrl)
            }
            is BookSourceManageAction.Search -> SearchActivity.start(this, action.source)
            is BookSourceManageAction.Debug -> startActivity<BookSourceDebugActivity> {
                putExtra("key", action.source.bookSourceUrl)
            }
            is BookSourceManageAction.Delete -> {
                deleteConfirmRequest = BookSourceDeleteConfirmRequest(
                    sources = listOf(action.source),
                    itemName = action.source.bookSourceName,
                )
            }
            is BookSourceManageAction.Top -> {
                if (sortAscending) viewModel.topSource(action.source)
                else viewModel.bottomSource(action.source)
            }
            is BookSourceManageAction.Bottom -> {
                if (sortAscending) viewModel.bottomSource(action.source)
                else viewModel.topSource(action.source)
            }
            is BookSourceManageAction.Reorder -> reorderSources(action.sources)
            is BookSourceManageAction.ToggleSection -> {
                expandedSections = expandedSections.toMutableSet().apply {
                    if (!add(action.key)) remove(action.key)
                }
            }
            is BookSourceManageAction.DeleteSection -> {
                sectionDeleteRequest = BookSourceSectionDeleteRequest(
                    groupName = action.groupName,
                    title = action.title,
                    sources = action.sources,
                )
            }
        }
    }

    private fun updateSelection(url: String, selected: Boolean) {
        selectedUrls = selectedUrls.toMutableSet().apply {
            if (selected) add(url) else remove(url)
        }
    }

    private fun editSource(source: BookSourcePart) {
        if (source.hasJs) {
            startActivity<JsSourceEditActivity> { putExtra("sourceUrl", source.bookSourceUrl) }
        } else {
            startActivity<BookSourceEditActivity> { putExtra("sourceUrl", source.bookSourceUrl) }
        }
    }

    private fun reorderSources(reordered: List<BookSourcePart>) {
        if (reordered.size != sources.size) return
        val updated = reordered.mapIndexed { index, source ->
            source.copy(
                customOrder = if (sortAscending) index + 1 else -(index + 1)
            )
        }
        sources = updated
        viewModel.upOrder(updated)
    }

    private fun updateSourceFlow(searchKey: String? = query) {
        sourceFlowJob?.cancel()
        sourceFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> appDb.bookSourceDao.flowAll()
                searchKey == getString(R.string.enabled) -> appDb.bookSourceDao.flowEnabled()
                searchKey == getString(R.string.disabled) -> appDb.bookSourceDao.flowDisabled()
                searchKey == getString(R.string.need_login) -> appDb.bookSourceDao.flowLogin()
                searchKey == getString(R.string.no_group) -> appDb.bookSourceDao.flowNoGroup()
                searchKey == getString(R.string.enabled_explore) -> appDb.bookSourceDao.flowEnabledExplore()
                searchKey == getString(R.string.disabled_explore) -> appDb.bookSourceDao.flowDisabledExplore()
                searchKey.startsWith("group:") -> {
                    appDb.bookSourceDao.flowGroupSearch(searchKey.substringAfter("group:"))
                }
                else -> appDb.bookSourceDao.flowSearch(searchKey)
            }.catch {
                AppLog.put("书源管理界面更新数据出错", it)
            }.flowOn(IO).conflate().collect { list ->
                sources = sortSources(list)
                selectedUrls = selectedUrls.intersect(
                    sources.mapTo(hashSetOf(), BookSourcePart::bookSourceUrl)
                )
                if (viewMode == BookSourceViewMode.GROUP && query.isNotBlank()) {
                    expandedSections = sourceGroupKeys(sources)
                }
                delay(100)
            }
        }
    }

    private fun sortSources(data: List<BookSourcePart>): List<BookSourcePart> {
        return if (sortAscending) {
            when (sort) {
                BookSourceSort.Weight -> data.sortedBy(BookSourcePart::weight)
                BookSourceSort.Name -> data.sortedWith { a, b ->
                    a.bookSourceName.cnCompare(b.bookSourceName)
                }
                BookSourceSort.Url -> data.sortedBy(BookSourcePart::bookSourceUrl)
                BookSourceSort.Update -> data.sortedByDescending(BookSourcePart::lastUpdateTime)
                BookSourceSort.Respond -> data.sortedBy(BookSourcePart::respondTime)
                BookSourceSort.Enable -> data.sortedWith(
                    compareByDescending<BookSourcePart> { it.enabled }
                        .thenComparator { a, b -> a.bookSourceName.cnCompare(b.bookSourceName) }
                )
                BookSourceSort.Default -> data
            }
        } else {
            when (sort) {
                BookSourceSort.Weight -> data.sortedByDescending(BookSourcePart::weight)
                BookSourceSort.Name -> data.sortedWith { a, b ->
                    b.bookSourceName.cnCompare(a.bookSourceName)
                }
                BookSourceSort.Url -> data.sortedByDescending(BookSourcePart::bookSourceUrl)
                BookSourceSort.Update -> data.sortedBy(BookSourcePart::lastUpdateTime)
                BookSourceSort.Respond -> data.sortedByDescending(BookSourcePart::respondTime)
                BookSourceSort.Enable -> data.sortedWith(
                    compareBy<BookSourcePart> { it.enabled }
                        .thenComparator { a, b -> a.bookSourceName.cnCompare(b.bookSourceName) }
                )
                BookSourceSort.Default -> data.reversed()
            }
        }
    }

    private fun selectableSources(): List<BookSourcePart> {
        return sources.distinctBy(BookSourcePart::bookSourceUrl)
    }

    private fun selectedSources(): List<BookSourcePart> = selectableSources().filter {
        it.bookSourceUrl in selectedUrls
    }

    private fun sourceGroupKeys(list: List<BookSourcePart>): Set<String> = buildSet {
        list.forEach { source ->
            val sourceGroups = source.bookSourceGroup
                ?.splitNotBlank(AppPattern.splitGroupRegex)
                .orEmpty()
            if (sourceGroups.isEmpty()) add("group:")
            else sourceGroups.forEach { add("group:$it") }
        }
    }

    private fun completeSelectionInterval() {
        val visible = selectableSources()
        val selectedIndices = visible.indices.filter {
            visible[it].bookSourceUrl in selectedUrls
        }
        val min = selectedIndices.minOrNull() ?: return
        val max = selectedIndices.maxOrNull() ?: return
        selectedUrls = selectedUrls + visible.subList(min, max + 1)
            .map(BookSourcePart::bookSourceUrl)
    }

    private fun handleInput(request: BookSourceInputRequest, value: String) {
        val text = value.trim()
        when (request) {
            BookSourceInputRequest.IMPORT_ONLINE -> if (text.isNotBlank()) {
                val history = importHistory().toMutableList()
                if (text.isAbsUrl() && text !in history) {
                    history.add(0, text)
                    ACache.get(cacheDir = false).put(importRecordKey, history.joinToString(","))
                }
                showDialogFragment(ImportBookSourceDialog(text))
            }
        }
        inputRequest = null
    }

    private fun importHistory(): List<String> = ACache.get(cacheDir = false)
        .getAsString(importRecordKey)
        ?.splitNotBlank(",")
        ?.toList()
        .orEmpty()

    private fun showExportSheet(selection: List<BookSourcePart>) {
        if (selection.isEmpty()) return
        BookSourceExportSheet(
            context = this,
            onShare = { shareSelection(selection) },
            onSaveLocally = { saveSelectionLocally(selection) },
        ).show()
    }

    private fun shareSelection(selection: List<BookSourcePart>) {
        viewModel.saveToFile(selection) { file, _ -> share(file) }
    }

    private fun saveSelectionLocally(selection: List<BookSourcePart>) {
        if (selection.isEmpty()) return
        viewModel.saveToFile(selection) { file, name ->
            exportResult.launch(
                CreateFileContract.FileData(
                    name,
                    file,
                    if (name.lowercase(Locale.ROOT).endsWith(".js")) {
                        "application/javascript"
                    } else {
                        "application/json"
                    },
                )
            )
        }
    }

    private fun startCheckSource(
        selection: List<BookSourcePart>,
        result: BookSourceCheckDialogResult,
    ) {
        result.keyword.trim().takeIf(String::isNotEmpty)?.let {
            CheckSource.keyword = it
        }
        CheckSource.timeout = result.timeoutSeconds * 1000L
        CheckSource.wSourceComment = result.writeSourceComment
        CheckSource.checkDomain = result.checkDomain
        CheckSource.checkSearch = result.checkSearch
        CheckSource.checkDiscovery = result.checkDiscovery
        CheckSource.checkInfo = result.checkInfo
        CheckSource.checkCategory = result.checkCategory
        CheckSource.checkContent = result.checkContent
        CheckSource.blockSourceDialogs = result.blockSourceDialogs
        CheckSource.putConfig()
        CheckSource.start(this, selection)
        startActivity<BookSourceCheckActivity>()
    }

    override fun finish() {
        if (query.isBlank()) {
            super.finish()
        } else {
            query = ""
            updateSourceFlow("")
        }
    }

}
