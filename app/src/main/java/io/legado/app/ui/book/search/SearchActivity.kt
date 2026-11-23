package io.legado.app.ui.book.search

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.gson.JsonObject
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.ActivityBookSearchBinding
import io.legado.app.help.ai.AI_BOOK_SEARCH_SCOPE
import io.legado.app.help.ai.AgentModeEntryContext
import io.legado.app.help.ai.AgentModeRegistry
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.config.AiChatActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import splitties.init.appCtx

class SearchActivity : VMBaseActivity<ActivityBookSearchBinding, SearchViewModel>() {

    override val binding by viewBinding(ActivityBookSearchBinding::inflate)
    override val viewModel by viewModels<SearchViewModel>()
    override val bindNgToolbarMenu: Boolean = false

    private var precisionSearch by mutableStateOf(false)
    private var blockSourceDialogs by mutableStateOf(false)
    private var focusRequestToken by mutableLongStateOf(0L)
    private var systemBackCallback: Any? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        precisionSearch = getPrefBoolean(PreferKey.precisionSearch)
        blockSourceDialogs = getPrefBoolean(PreferKey.searchBlockSourceDialogs)
        viewModel.setBlockSourceDialogs(blockSourceDialogs)
        binding.root.setOnPreImeBackListener { finish() }
        receiptIntent(intent)
        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeView.setContent {
            NgAppTheme {
                val state by viewModel.uiState.collectAsState()
                val query by viewModel.query.collectAsState()
                val history by viewModel.history.collectAsState()
                val bookshelfMatches by viewModel.bookshelfMatches.collectAsState()
                val groups by viewModel.groups.collectAsState()
                val scopeSources by viewModel.scopeSources.collectAsState()
                val scopeValue by viewModel.searchScope.stateFlow.collectAsState()
                val scopeNames = remember(scopeValue) {
                    if (scopeValue.contains("::")) {
                        listOf(scopeValue.substringBefore("::"))
                    } else {
                        scopeValue.splitNotBlank(",").toList()
                    }
                }
                SearchScreen(
                    state = state,
                    query = query,
                    history = history,
                    bookshelfMatches = bookshelfMatches,
                    groups = groups,
                    scopeSources = scopeSources,
                    scopeNames = scopeNames,
                    isSourceScope = scopeValue.contains("::"),
                    precisionSearch = precisionSearch,
                    blockSourceDialogs = blockSourceDialogs,
                    focusRequestToken = focusRequestToken,
                    onQueryChange = viewModel::updateQuery,
                    onSubmitSearch = viewModel::submitSearch,
                    onOpenAiSearch = {
                        startActivity<AiChatActivity> {
                            putExtra(AiChatActivity.EXTRA_ENTRY, AiChatActivity.ENTRY_BOOK_SEARCH)
                            putExtra(AiChatActivity.EXTRA_INITIAL_INPUT, query)
                            putExtra(AiChatActivity.EXTRA_MODE_ENTRY_CONTEXT, AgentModeEntryContext(
                                contextId = AgentModeRegistry.BOOK_SEARCH_ID,
                                title = getString(R.string.ai_book_search_title),
                                payload = JsonObject().apply { addProperty(AI_BOOK_SEARCH_SCOPE, scopeValue) },
                            ).toJson())
                        }
                    },
                    onBack = ::finish,
                    onHistoryClick = ::activateHistory,
                    onDeleteHistory = viewModel::deleteHistory,
                    onBookshelfBookClick = { book ->
                        showBookInfo(book.name, book.author, book.bookUrl)
                    },
                    onBookClick = { book ->
                        showBookInfo(book.name, book.author, book.bookUrl)
                    },
                    onBookLongClick = viewModel::showAllSources,
                    isInBookshelf = viewModel::isInBookShelf,
                    onClearHistory = ::alertClearHistory,
                    onTogglePrecisionSearch = ::togglePrecisionSearch,
                    onToggleBlockSourceDialogs = ::toggleBlockSourceDialogs,
                    onSourceManage = { startActivity<BookSourceActivity>() },
                    onScopeSourceQueryChange = viewModel::updateScopeSourceQuery,
                    onApplySearchScope = { searchScope ->
                        viewModel.searchScope.update(searchScope.toString())
                        restartActiveSearch()
                    },
                    onShowLog = { showDialogFragment(AppLogDialog()) },
                    onShowNetworkLog = { showDialogFragment(NetworkLogDialog()) },
                    onAllSources = {
                        viewModel.searchScope.update("")
                        restartActiveSearch()
                    },
                    onDynamicScope = { name, selected ->
                        if (selected) {
                            viewModel.searchScope.remove(name)
                        } else {
                            viewModel.searchScope.update(name)
                        }
                        restartActiveSearch()
                    },
                    onStopSearch = viewModel::stopManually,
                    onLoadMore = viewModel::loadMore
                )
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.resume()
                try {
                    awaitCancellation()
                } finally {
                    viewModel.pause()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receiptIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && systemBackCallback == null) {
            systemBackCallback = Api33Back.register(this) { finish() }
        }
    }

    override fun onStop() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            systemBackCallback?.let { Api33Back.unregister(this, it) }
            systemBackCallback = null
        }
        super.onStop()
    }

    override fun observeLiveBus() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.events.collect { event ->
                    when (event) {
                        is SearchViewModel.SearchEvent.Finished -> {
                            showEmptyScopeDialog(event.isEmpty)
                        }
                    }
                }
            }
        }
    }

    private fun receiptIntent(intent: Intent?) {
        intent?.getStringExtra("searchScope")?.let { scope ->
            viewModel.searchScope.update(scope, save = false)
        }
        val key = intent?.getStringExtra("key")
        if (key.isNullOrBlank()) {
            focusRequestToken++
        } else {
            viewModel.updateQuery(key)
            viewModel.submitSearch(key)
        }
    }

    private suspend fun activateHistory(key: String): Boolean {
        val shouldSearch = viewModel.query.value == key ||
            withContext(IO) { appDb.bookDao.findByName(key).isEmpty() }
        viewModel.updateQuery(key)
        if (shouldSearch) {
            viewModel.submitSearch(key)
        }
        return shouldSearch
    }

    private fun togglePrecisionSearch() {
        precisionSearch = !precisionSearch
        putPrefBoolean(PreferKey.precisionSearch, precisionSearch)
        viewModel.query.value.trim().takeIf { it.isNotEmpty() }?.let(viewModel::submitSearch)
    }

    private fun toggleBlockSourceDialogs() {
        blockSourceDialogs = !blockSourceDialogs
        putPrefBoolean(PreferKey.searchBlockSourceDialogs, blockSourceDialogs)
        viewModel.setBlockSourceDialogs(blockSourceDialogs)
    }

    private fun restartActiveSearch() {
        val query = viewModel.query.value.trim()
        if (query.isNotEmpty() && query == viewModel.uiState.value.activeQuery) {
            viewModel.submitSearch(query)
        }
    }

    private fun showEmptyScopeDialog(isEmpty: Boolean) {
        if (!isEmpty || viewModel.searchScope.isAll() || isFinishing) return
        alert("搜索结果为空") {
            val displayScope = viewModel.searchScope.display
            if (appCtx.getPrefBoolean(PreferKey.precisionSearch)) {
                setMessage("${displayScope}分组搜索结果为空，是否关闭精准搜索？")
                yesButton {
                    precisionSearch = false
                    appCtx.putPrefBoolean(PreferKey.precisionSearch, false)
                    viewModel.submitSearch(viewModel.query.value)
                }
            } else {
                setMessage("${displayScope}分组搜索结果为空，是否切换到全部分组？")
                yesButton {
                    viewModel.searchScope.update("")
                    viewModel.submitSearch(viewModel.query.value)
                }
            }
            noButton()
        }
    }

    private fun showBookInfo(name: String, author: String, bookUrl: String) {
        startActivity<BookInfoActivity> {
            putExtra("name", name)
            putExtra("author", author)
            putExtra("bookUrl", bookUrl)
        }
    }

    private fun alertClearHistory() {
        alert(R.string.draw) {
            setMessage(R.string.sure_clear_search_history)
            yesButton { viewModel.clearHistory() }
            noButton()
        }
    }

    companion object {

        fun start(context: Context, key: String?, searchScope: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", searchScope)
            }
        }

        fun start(context: Context, source: BookSource, key: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", SearchScope(source).toString())
            }
        }

        fun start(context: Context, source: BookSourcePart, key: String? = null) {
            context.startActivity<SearchActivity> {
                putExtra("key", key)
                putExtra("searchScope", SearchScope(source).toString())
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private object Api33Back {

        fun register(activity: SearchActivity, action: () -> Unit): OnBackInvokedCallback {
            val callback = OnBackInvokedCallback(action)
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback
            )
            return callback
        }

        fun unregister(activity: SearchActivity, callback: Any) {
            activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(
                callback as OnBackInvokedCallback
            )
        }
    }
}
