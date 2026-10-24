package io.legado.app.ui.book.import.remote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.model.remote.RemoteBook
import io.legado.app.model.remote.WebDavBookLocation
import io.legado.app.model.remote.webDavBookKey
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.SelectDirectoryContract
import io.legado.app.utils.find
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** 以 Compose 呈现远程书籍目录，沿用既有 WebDAV 与导入逻辑。 */
class RemoteBookActivity :
    VMBaseActivity<ComposeActivityBinding, RemoteBookViewModel>(),
    ServersDialog.Callback {

    companion object {
        internal fun open(context: Context, location: WebDavBookLocation, checkUpdate: Boolean = false) {
            context.startActivity(createIntent(context, location, checkUpdate))
        }

        internal fun createIntent(context: Context, location: WebDavBookLocation, checkUpdate: Boolean = false): Intent =
            Intent(context, RemoteBookActivity::class.java).apply {
                putExtra(WebDavBookLocation.EXTRA_SERVER, location.serverId ?: -1L)
                putExtra(WebDavBookLocation.EXTRA_PATH, location.path)
                putExtra(WebDavBookLocation.EXTRA_CHECK_UPDATE, checkUpdate)
            }
    }

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<RemoteBookViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private var items by mutableStateOf<List<RemoteBook>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var selectedItems by mutableStateOf<Set<RemoteBook>>(emptySet())
    private var query by mutableStateOf("")
    private var searchExpanded by mutableStateOf(false)
    private var pathText by mutableStateOf("/")
    private var isAtRoot by mutableStateOf(true)
    private var isLoading by mutableStateOf(false)
    private var currentSortKey by mutableStateOf(RemoteBookSort.Default)
    private var actionItem by mutableStateOf<RemoteBook?>(null)
    private var batchResult by mutableStateOf<RemoteBookViewModel.BatchResult?>(null)
    private val requestedLocation by lazy {
        intent.getStringExtra(WebDavBookLocation.EXTRA_PATH)?.let {
            WebDavBookLocation(intent.getLongExtra(WebDavBookLocation.EXTRA_SERVER, -1L).takeIf { id -> id >= 0 }, it)
        }
    }
    private var checkUpdate = false
    private var localBookTreeSelectListener: ((Boolean) -> Unit)? = null

    private val localBookTreeSelect = registerForActivityResult(SelectDirectoryContract()) {
        it.uri?.let { treeUri ->
            AppConfig.defaultBookTreeUri = treeUri.toString()
            localBookTreeSelectListener?.invoke(true)
        } ?: localBookTreeSelectListener?.invoke(false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        currentSortKey = viewModel.sortKey
        checkUpdate = savedInstanceState == null && intent.getBooleanExtra(WebDavBookLocation.EXTRA_CHECK_UPDATE, false)
        initContent()
        onBackPressedDispatcher.addCallback(this) {
            if (viewModel.transfer.value != null) viewModel.cancelTransfer()
            else if (!goBackDir()) finish()
        }
        lifecycleScope.launch {
            if (!LocalConfig.webDavBookHelpVersionIsLast) {
                showHelp("webDavBookHelp")
            }
            launch {
                viewModel.dataFlow.collect { remoteBooks ->
                    items = remoteBooks.toList()
                }
            }
            viewModel.initData(requestedLocation, onSuccess = ::upPath)
        }
    }

    override fun observeLiveBus() {
        viewModel.permissionDenialLiveData.observe(this) {
            localBookTreeSelect.launch(null)
        }
        viewModel.batchResult.observe(this) { batchResult = it }
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                val statuses by viewModel.statuses.collectAsState()
                val loading by viewModel.loading.collectAsState()
                val transfer by viewModel.transfer.collectAsState()
                val conflict by viewModel.conflict.collectAsState()
                RemoteBookScreen(
                    items = items,
                    selectedItems = selectedItems,
                    query = query,
                    searchExpanded = searchExpanded,
                    pathText = pathText,
                    isAtRoot = isAtRoot,
                    isLoading = loading,
                    sortKey = currentSortKey,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSearchExpandedChange = { expanded ->
                        if (expanded) searchExpanded = true else closeSearch()
                    },
                    onQueryChange = ::updateQuery,
                    onRefresh = ::upPath,
                    onGoUp = { goBackDir() },
                    onSortChange = ::changeSort,
                    onMenuAction = ::onMenuAction,
                    onItemClick = ::onItemClick,
                    onItemLongClick = ::onItemLongClick,
                    onToggleItem = ::toggleItem,
                    onSelectAll = ::selectAllVisible,
                    onInvertSelection = ::invertVisibleSelection,
                    onAddSelected = ::addSelected,
                    statuses = statuses,
                    focusedPath = viewModel.focusedPath,
                    transfer = transfer,
                    conflict = conflict,
                    actionItem = actionItem,
                    batchResult = batchResult,
                    onResolveConflict = viewModel::resolveConflict,
                    onCancelTransfer = viewModel::cancelTransfer,
                    onDismissAction = { actionItem = null },
                    onDownloadItem = { item, add -> actionItem = null; beginTransfer(listOf(item), add) },
                    onDownloadSelected = { beginTransfer(selectedItems.toList(), false) },
                    onDismissResult = { batchResult = null; viewModel.batchResult.value = null },
                )
            }
        }
    }

    private suspend fun setBookStorage() = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { localBookTreeSelectListener = null }
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

    private fun updateQuery(value: String) {
        query = value
        viewModel.updateCallBackFlow(value)
    }

    private fun closeSearch() {
        searchExpanded = false
        updateQuery("")
    }

    private fun onMenuAction(itemId: Int) {
        if (viewModel.transfer.value != null) return
        when (itemId) {
            R.id.menu_server_config -> showDialogFragment<ServersDialog>()
            R.id.menu_help -> showHelp("webDavBookHelp")
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_network_log -> showDialogFragment<NetworkLogDialog>()
        }
    }

    private fun changeSort(sortKey: RemoteBookSort) {
        if (viewModel.transfer.value != null) return
        if (viewModel.sortKey == sortKey) {
            viewModel.sortAscending = !viewModel.sortAscending
        } else {
            viewModel.sortAscending = true
            viewModel.sortKey = sortKey
        }
        currentSortKey = viewModel.sortKey
        upPath()
    }

    private fun goBackDir(): Boolean {
        if (viewModel.transfer.value != null) return false
        if (viewModel.dirList.isEmpty()) return false
        viewModel.dirList.removeLastOrNull()
        upPath()
        return true
    }

    private fun upPath() {
        if (viewModel.transfer.value != null) return
        isAtRoot = viewModel.dirList.isEmpty()
        pathText = buildList {
            add(if (viewModel.isDefaultWebdav) "books" else "/")
            addAll(viewModel.dirList.map(RemoteBook::filename))
        }.joinToString("  ›  ")
        selectedItems = emptySet()
        viewModel.loadRemoteBookList(viewModel.dirList.lastOrNull()?.path) {
            isLoading = it
            if (!it && viewModel.loadSucceeded && checkUpdate) {
                checkUpdate = false
                val target = viewModel.dataFlow.value.find { item ->
                    requestedLocation?.let { location -> webDavBookKey(location.serverId, item.path) == location.key } == true
                }
                if (target == null) toastOnUi(R.string.webdav_target_missing)
                else beginTransfer(listOf(target), true)
            }
        }
    }

    private fun onItemClick(remoteBook: RemoteBook) {
        if (viewModel.transfer.value != null) return
        when {
            remoteBook.isDir -> {
                viewModel.dirList.add(remoteBook)
                upPath()
            }

            !remoteBook.isOnBookShelf -> toggleItem(remoteBook)
            else -> startRead(remoteBook)
        }
    }

    private fun onItemLongClick(remoteBook: RemoteBook) {
        if (!remoteBook.isDir && viewModel.transfer.value == null) actionItem = remoteBook
    }

    private fun toggleItem(remoteBook: RemoteBook) {
        if (remoteBook.isDir || viewModel.transfer.value != null) return
        selectedItems = if (remoteBook in selectedItems) {
            selectedItems - remoteBook
        } else {
            selectedItems + remoteBook
        }
    }

    private fun selectAllVisible() {
        if (viewModel.transfer.value != null) return
        val selectable = items.filter { !it.isDir }
        selectedItems = if (selectable.isNotEmpty() && selectable.all(selectedItems::contains)) {
            selectedItems - selectable.toSet()
        } else {
            selectedItems + selectable
        }
    }

    private fun invertVisibleSelection() {
        var updated = selectedItems
        if (viewModel.transfer.value != null) return
        items.filter { !it.isDir }.forEach { item ->
            updated = if (item in updated) updated - item else updated + item
        }
        selectedItems = updated
    }

    private fun addSelected() {
        beginTransfer(selectedItems.toList(), true)
    }

    private fun beginTransfer(selected: List<RemoteBook>, addToShelf: Boolean) {
        if (selected.isEmpty() || viewModel.transfer.value != null) return
        lifecycleScope.launch {
            if (!setBookStorage()) return@launch
            viewModel.download(selected, addToShelf) {
                selectedItems = emptySet()
                upPath()
            }
        }
    }

    override fun onDialogDismiss(tag: String) {
        viewModel.dirList.clear()
        viewModel.initData(force = true, onSuccess = ::upPath)
    }

    private fun startRead(remoteBook: RemoteBook) {
        lifecycleScope.launch {
            viewModel.findLocal(remoteBook)?.let(::startActivityForBook)
                ?: beginTransfer(listOf(remoteBook), true)
        }
    }

}
