package io.legado.app.ui.file

import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.openFileUri
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.io.File

class FileManageActivity : VMBaseActivity<ComposeActivityBinding, FileManageViewModel>() {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<FileManageViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private var files by mutableStateOf<List<FileManageEntry>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var query by mutableStateOf("")
    private var searchExpanded by mutableStateOf(false)
    private var pathSegments by mutableStateOf(listOf(ROOT_LABEL))
    private var isLoading by mutableStateOf(false)
    private var sortMode by mutableIntStateOf(FILE_MANAGE_SORT_NAME)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initContent()
        onBackPressedDispatcher.addCallback(this) {
            when {
                searchExpanded -> closeSearch()
                viewModel.lastDir != viewModel.rootDoc -> goUp()
                else -> finish()
            }
        }
        loadFiles(viewModel.rootDoc)
    }

    private fun initContent() {
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed,
        )
        binding.composeView.setContent {
            NgAppTheme {
                FileManageScreen(
                    files = files,
                    query = query,
                    searchExpanded = searchExpanded,
                    pathSegments = pathSegments,
                    isLoading = isLoading,
                    sortMode = sortMode,
                    onBack = { onBackPressedDispatcher.onBackPressed() },
                    onSearchExpandedChange = { expanded ->
                        if (expanded) searchExpanded = true else closeSearch()
                    },
                    onQueryChange = { query = it },
                    onRoot = ::goRoot,
                    onPathClick = ::goToPath,
                    onSortChange = { sortMode = it },
                    onFileClick = ::openEntry,
                    onDelete = { viewModel.delFile(it.file) },
                )
            }
        }
    }

    private fun closeSearch() {
        searchExpanded = false
        query = ""
    }

    private fun goRoot() {
        viewModel.subDocs.clear()
        updatePathSegments()
        loadFiles(viewModel.rootDoc)
    }

    private fun goUp() {
        viewModel.subDocs.removeLastOrNull()
        updatePathSegments()
        loadFiles(viewModel.lastDir)
    }

    private fun goToPath(index: Int) {
        viewModel.subDocs = if (index <= 0) {
            mutableListOf()
        } else {
            viewModel.subDocs.take(index).toMutableList()
        }
        updatePathSegments()
        loadFiles(viewModel.lastDir)
    }

    private fun openEntry(entry: FileManageEntry) {
        if (entry.isDirectory) {
            viewModel.subDocs.add(entry.file)
            updatePathSegments()
            loadFiles(entry.file)
        } else {
            openFileUri(
                FileProvider.getUriForFile(
                    this,
                    AppConst.authority,
                    entry.file,
                ),
            )
        }
    }

    private fun updatePathSegments() {
        pathSegments = buildList {
            add(ROOT_LABEL)
            addAll(viewModel.subDocs.map(File::getName))
        }
    }

    private fun loadFiles(parent: File?) {
        query = ""
        viewModel.upFiles(parent)
    }

    override fun observeLiveBus() {
        viewModel.filesLiveData.observe(this) {
            files = it
        }
        viewModel.loadingLiveData.observe(this) {
            isLoading = it
        }
    }

    private companion object {
        const val ROOT_LABEL = "root"
    }
}
