package io.legado.app.ui.book.explore

import android.content.Context
import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.main.explore.ExploreInfoStore
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * 发现详情
 */
class ExploreShowActivity :
    VMBaseActivity<ExploreShowActivityBinding, ExploreShowViewModel>() {

    override val binding by lazy { ExploreShowActivityBinding(this) }
    override val viewModel by viewModels<ExploreShowViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private var layoutMode by mutableStateOf(
        ExploreShowLayoutMode.from(AppConfig.exploreShowLayoutMode)
    )

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        viewModel.initData(
            sourceUrl = intent.getStringExtra(EXTRA_SOURCE_URL),
            sourceName = intent.getStringExtra(EXTRA_SOURCE_NAME),
            initialExploreUrl = intent.getStringExtra(EXTRA_EXPLORE_URL),
            initialExploreName = intent.getStringExtra(EXTRA_EXPLORE_NAME)
        )
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeView.setContent {
            NgAppTheme {
                val state by viewModel.uiState.collectAsState()
                var pagePickerPage by rememberSaveable { mutableStateOf<Int?>(null) }
                ExploreShowScreen(
                    state = state,
                    layoutMode = layoutMode,
                    onBack = ::finish,
                    onRefresh = viewModel::refresh,
                    onRefreshKinds = viewModel::reloadKinds,
                    onSelectKind = viewModel::selectKind,
                    onLayoutModeChange = ::updateLayoutMode,
                    onSelectPage = { pagePickerPage = it },
                    onJumpToPage = viewModel::navigateToPage,
                    onLoadNext = viewModel::loadNextPage,
                    onRetryContent = viewModel::retryContent,
                    onOpenBook = ::showBookInfo,
                    onShowError = { showDialogFragment(TextDialog("ERROR", it)) }
                )
                pagePickerPage?.let { currentPage ->
                    ExplorePageJumpDialog(
                        currentPage = currentPage,
                        onDismiss = { pagePickerPage = null },
                        onJump = { page ->
                            pagePickerPage = null
                            if (page != currentPage) viewModel.jumpToPage(page)
                        }
                    )
                }
            }
        }
    }

    private fun updateLayoutMode(mode: ExploreShowLayoutMode) {
        if (layoutMode == mode) return
        layoutMode = mode
        AppConfig.exploreShowLayoutMode = mode.value
    }

    private fun showBookInfo(book: SearchBook) {
        startActivity<BookInfoActivity> {
            putExtra("name", book.name)
            putExtra("author", book.author)
            putExtra("bookUrl", book.bookUrl)
        }
    }

    override fun onPause() {
        saveExploreInfoMaps()
        super.onPause()
    }

    private fun saveExploreInfoMaps() {
        val infoMaps = ExploreInfoStore.infoMapList.snapshot()
            .filter { (_, infoMap) -> infoMap.needSave }
        lifecycleScope.launch {
            infoMaps.map { (_, infoMap) ->
                launch(IO) { infoMap.saveNow() }
            }.joinAll()
        }
    }

    companion object {
        const val EXTRA_SOURCE_URL = "sourceUrl"
        const val EXTRA_SOURCE_NAME = "sourceName"
        const val EXTRA_EXPLORE_URL = "exploreUrl"
        const val EXTRA_EXPLORE_NAME = "exploreName"
    }
}

class ExploreShowActivityBinding(context: Context) : ViewBinding {
    val composeView = ComposeView(context)

    override fun getRoot() = composeView
}
