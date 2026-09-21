package io.legado.app.ui.book.import.local

import android.net.Uri
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppPattern
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.model.localBook.LocalBook
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.ArchiveUtils
import io.legado.app.utils.FileDoc
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isUri
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/** 方案 2：单一亮白目录面板的本地书籍导入页。 */
class ImportBookActivity :
    VMBaseActivity<ComposeActivityBinding, ImportBookViewModel>() {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<ImportBookViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private var items by mutableStateOf<List<ImportBook>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var selectedItems by mutableStateOf<Set<ImportBook>>(emptySet())
    private var query by mutableStateOf("")
    private var searchExpanded by mutableStateOf(false)
    private var pathSegments by mutableStateOf<List<String>>(emptyList())
    private var isAtRoot by mutableStateOf(true)
    private var isLoading by mutableStateOf(false)
    private var currentSort by mutableIntStateOf(0)
    private var importing by mutableStateOf(false)
    private var importTotal by mutableIntStateOf(-1)
    private var importDone by mutableIntStateOf(0)
    private var archivePickerState by mutableStateOf<ArchivePickerState>(
        ArchivePickerState.Hidden,
    )
    private var scanDocJob: Job? = null
    private var localBookTreeSelectListener: ((Boolean) -> Unit)? = null

    private val localBookTreeSelect = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
            localBookTreeSelectListener?.invoke(true)
        } ?: localBookTreeSelectListener?.invoke(false)
    }

    private val selectFolder = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { uri ->
            AppConfig.importBookPath = uri.toString()
            initRootDoc(changedFolder = true)
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        currentSort = viewModel.sort
        initContent()
        onBackPressedDispatcher.addCallback(this) {
            when {
                archivePickerState !is ArchivePickerState.Hidden -> {
                    archivePickerState = ArchivePickerState.Hidden
                }

                searchExpanded -> closeSearch()
                !goBackDir() -> finish()
            }
        }
        lifecycleScope.launch {
            if (setBookStorage() && AppConfig.importBookPath.isNullOrBlank()) {
                AppConfig.importBookPath = AppConfig.defaultBookTreeUri
            }
            initData()
        }
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                ImportBookScreen(
                    items = items,
                    selectedItems = selectedItems,
                    query = query,
                    searchExpanded = searchExpanded,
                    pathSegments = pathSegments,
                    isAtRoot = isAtRoot,
                    isLoading = isLoading,
                    sort = currentSort,
                    importProgressText = if (importTotal >= 0) "$importDone/$importTotal" else null,
                    archivePickerState = archivePickerState,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSearchExpandedChange = { expanded ->
                        if (expanded) searchExpanded = true else closeSearch()
                    },
                    onQueryChange = ::updateQuery,
                    onSelectFolder = { selectFolder.launch(null) },
                    onGoUp = { goBackDir() },
                    onScanFolder = ::scanFolder,
                    onSortChange = ::upSort,
                    onItemClick = ::onItemClick,
                    onToggleItem = ::toggleItem,
                    onSelectAll = ::selectAllVisible,
                    onInvertSelection = ::invertVisibleSelection,
                    onDeleteSelected = ::deleteSelected,
                    onAddSelected = ::addSelected,
                    onDismissArchive = {
                        archivePickerState = ArchivePickerState.Hidden
                    },
                    onArchiveEntryClick = ::onArchiveEntryClick,
                    onImportArchiveEntries = ::importArchiveEntries,
                )
            }
        }
    }

    private fun initData() {
        viewModel.dataFlowStart = { initRootDoc() }
        lifecycleScope.launch {
            viewModel.dataFlow.conflate().collect { docs ->
                items = docs.toList()
            }
        }
    }

    private suspend fun setBookStorage() = suspendCancellableCoroutine { continuation ->
        localBookTreeSelectListener = { selected ->
            localBookTreeSelectListener = null
            if (continuation.isActive) continuation.resume(selected)
        }
        if (!AppConfig.defaultBookTreeUri.isNullOrBlank()) {
            localBookTreeSelectListener = null
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        val storageHelp = String(assets.open("storageHelp.md").readBytes())
        alert(getString(R.string.select_book_folder), storageHelp) {
            okButton { localBookTreeSelect.launch(null) }
            cancelButton {
                localBookTreeSelectListener = null
                if (continuation.isActive) continuation.resume(false)
            }
            onCancelled {
                localBookTreeSelectListener = null
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    private fun initRootDoc(changedFolder: Boolean = false) {
        if (viewModel.rootDoc != null && !changedFolder) {
            upPath()
            return
        }
        val lastPath = AppConfig.importBookPath
        if (lastPath.isNullOrBlank()) {
            selectFolder.launch(null)
            return
        }
        val rootUri = if (lastPath.isUri()) lastPath.toUri() else Uri.fromFile(File(lastPath))
        if (rootUri.isContentScheme()) {
            initRootPath(rootUri)
        } else {
            rootUri.path?.let(::initRootPath) ?: selectFolder.launch(null)
        }
    }

    private fun initRootPath(rootUri: Uri) {
        runCatching {
            val doc = DocumentFile.fromTreeUri(this, rootUri)
            if (doc == null || doc.name.isNullOrEmpty() || !doc.isDirectory) {
                selectFolder.launch(null)
            } else {
                viewModel.subDocs.clear()
                viewModel.rootDoc = FileDoc.fromDocumentFile(doc)
                upPath()
            }
        }.onFailure {
            selectFolder.launch(null)
        }
    }

    private fun initRootPath(path: String) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                runCatching {
                    val file = File(path)
                    if (!file.isDirectory) {
                        selectFolder.launch(null)
                    } else {
                        viewModel.subDocs.clear()
                        viewModel.rootDoc = FileDoc.fromFile(file)
                        upPath()
                    }
                }.onFailure {
                    selectFolder.launch(null)
                }
            }
            .request()
    }

    @Synchronized
    private fun upPath() {
        viewModel.rootDoc?.let { root ->
            scanDocJob?.cancel()
            pathSegments = buildList {
                add(root.name)
                addAll(viewModel.subDocs.map(FileDoc::name))
            }
            isAtRoot = viewModel.subDocs.isEmpty()
            selectedItems = emptySet()
            val current = viewModel.subDocs.lastOrNull() ?: root
            isLoading = true
            viewModel.dataCallback?.clear()
            viewModel.loadDoc(current) { isLoading = false }
        }
    }

    @Synchronized
    private fun goBackDir(): Boolean {
        return if (viewModel.subDocs.isNotEmpty()) {
            viewModel.subDocs.removeAt(viewModel.subDocs.lastIndex)
            upPath()
            true
        } else {
            false
        }
    }

    private fun updateQuery(value: String) {
        query = value
        viewModel.updateCallBackFlow(value)
    }

    private fun closeSearch() {
        searchExpanded = false
        updateQuery("")
    }

    private fun upSort(sort: Int) {
        viewModel.sort = sort
        currentSort = sort
        putPrefInt(PreferKey.localBookImportSort, sort)
        if (scanDocJob?.isActive != true) {
            viewModel.dataCallback?.upAdapter()
        }
    }

    private fun scanFolder() {
        val root = viewModel.rootDoc ?: return
        val current = viewModel.subDocs.lastOrNull() ?: root
        selectedItems = emptySet()
        isLoading = true
        viewModel.dataCallback?.clear()
        scanDocJob?.cancel()
        scanDocJob = lifecycleScope.launch(IO) {
            try {
                viewModel.scanDoc(current)
            } finally {
                withContext(Main) { isLoading = false }
            }
        }
    }

    private fun onItemClick(item: ImportBook) {
        when {
            item.isDir -> {
                viewModel.subDocs.add(item.file)
                upPath()
            }

            ArchiveUtils.isArchive(item.name) -> openArchive(item.file)
            item.isOnBookShelf -> startRead(item.file)
            else -> toggleItem(item)
        }
    }

    private fun toggleItem(item: ImportBook) {
        if (!item.isSelectableForImport) return
        selectedItems = if (item in selectedItems) {
            selectedItems - item
        } else {
            selectedItems + item
        }
    }

    private fun selectAllVisible() {
        val selectable = items.filter(ImportBook::isSelectableForImport)
        selectedItems = if (selectable.isNotEmpty() && selectable.all(selectedItems::contains)) {
            selectedItems - selectable.toSet()
        } else {
            selectedItems + selectable
        }
    }

    private fun invertVisibleSelection() {
        var updated = selectedItems
        items.filter(ImportBook::isSelectableForImport).forEach { item ->
            updated = if (item in updated) updated - item else updated + item
        }
        selectedItems = updated
    }

    private fun addSelected() {
        val selected = HashSet(selectedItems)
        if (selected.isEmpty() || importing) return
        importing = true
        importTotal = selected.size
        importDone = 0
        if (importTotal > 3) {
            toastOnUi("共选择 $importTotal 本，正在后台导入，进度及结果请查看日志")
        }
        isLoading = true
        viewModel.addToBookshelf(selected,
            onItemDone = { fileName, success ->
                importDone++
                if (success) {
                    //实时更新对应行的状态为已在书架
                    items.find { it.isSelectableForImport && it.name == fileName }
                        ?.let { it.isOnBookShelf = true }
                    items = items.toList()
                }
            },
            onComplete = { result ->
                when {
                    result.failures.isEmpty() -> toastOnUi("添加书架成功")
                    importTotal <= 3 -> showImportResultDialog(result)
                    else -> toastOnUi(
                        "导入完成：成功 ${result.successCount} 本，" +
                                "失败 ${result.failures.size} 本，失败详情见日志"
                    )
                }
            }
        ) {
            importing = false
            importTotal = -1
            importDone = 0
            isLoading = false
            selectedItems = emptySet()
            viewModel.dataCallback?.upAdapter()
        }
    }

    private fun showImportResultDialog(result: LocalBook.ImportResult) {
        val message = buildString {
            append("成功导入 ").append(result.successCount).append(" 本")
            if (result.failures.isNotEmpty()) {
                append("，失败 ").append(result.failures.size).append(" 本")
                append("\n\n失败列表：")
                result.failures.take(20).forEach {
                    append("\n").append(it.fileName).append("：").append(it.reason)
                }
                if (result.failures.size > 20) {
                    append("\n…等 ").append(result.failures.size).append(" 个文件，详见日志")
                }
            }
        }
        alert("导入结果", message) {
            okButton()
        }
    }

    private fun deleteSelected() {
        val selected = HashSet(selectedItems)
        if (selected.isEmpty()) return
        viewModel.deleteDoc(selected) {
            selectedItems = emptySet()
            upPath()
        }
    }

    private fun startRead(fileDoc: FileDoc) {
        appDb.bookDao.getBookByFileName(fileDoc.name)?.let { book ->
            val filePath = fileDoc.toString()
            if (book.bookUrl != filePath) {
                book.bookUrl = filePath
                appDb.bookDao.insert(book)
            }
            startActivityForBook(book)
        }
    }

    private fun openArchive(fileDoc: FileDoc) {
        archivePickerState = ArchivePickerState.Loading(fileDoc)
        lifecycleScope.launch(IO) {
            val result = runCatching {
                ArchiveUtils.getArchiveFilesName(fileDoc) { entryName ->
                    entryName.matches(AppPattern.bookFileRegex)
                }.map { entryName ->
                    val displayName = entryName.substringAfterLast('/').substringAfterLast('\\')
                    ArchiveBookEntry(
                        entryName = entryName,
                        displayName = displayName,
                        isOnBookShelf = LocalBook.isOnBookShelf(displayName),
                    )
                }
            }
            withContext(Main) {
                result.onSuccess { entries ->
                    if (entries.isEmpty()) {
                        archivePickerState = ArchivePickerState.Hidden
                        toastOnUi(R.string.unsupport_archivefile_entry)
                    } else {
                        archivePickerState = ArchivePickerState.Ready(
                            archive = fileDoc,
                            entries = entries,
                        )
                    }
                }.onFailure {
                    archivePickerState = ArchivePickerState.Hidden
                    toastOnUi(it.localizedMessage ?: getString(R.string.error))
                }
            }
        }
    }

    private fun onArchiveEntryClick(entry: ArchiveBookEntry) {
        val state = archivePickerState as? ArchivePickerState.Ready ?: return
        if (entry.isOnBookShelf) {
            appDb.bookDao.getBookByFileName(entry.displayName)?.let(::startActivityForBook)
            return
        }
        val selected = if (entry.entryName in state.selectedEntryNames) {
            state.selectedEntryNames - entry.entryName
        } else {
            state.selectedEntryNames + entry.entryName
        }
        archivePickerState = state.copy(selectedEntryNames = selected)
    }

    private fun importArchiveEntries() {
        val state = archivePickerState as? ArchivePickerState.Ready ?: return
        if (state.selectedEntryNames.isEmpty() || state.importing) return
        archivePickerState = state.copy(importing = true)
        viewModel.addArchiveEntries(state.archive, state.selectedEntryNames) { success ->
            archivePickerState = if (success) {
                ArchivePickerState.Hidden
            } else {
                state.copy(importing = false)
            }
        }
    }
}
