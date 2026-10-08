package io.legado.app.ui.rss.article

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.addCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.RssArticle
import io.legado.app.help.source.sortUrls
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.rss.RssComposeBinding
import io.legado.app.ui.rss.read.ReadRss
import io.legado.app.ui.rss.source.edit.RssSourceEditActivity
import io.legado.app.ui.widget.dialog.VariableDialog
import io.legado.app.utils.GSONStrict
import io.legado.app.utils.StartActivityContract
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** RSS 分类与文章页。分类、五种布局、刷新和翻页均由单一 Compose 页面承载。 */
class RssSortActivity : VMBaseActivity<RssComposeBinding, RssSortViewModel>(),
    VariableDialog.Callback {

    override val binding by viewBinding(RssComposeBinding::inflate)
    override val viewModel by viewModels<RssSortViewModel>()

    private var sorts by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var selectedSort by mutableIntStateOf(0)
    private var articles by mutableStateOf<List<RssArticle>>(
        emptyList(),
        referentialEqualityPolicy()
    )
    private var articleStyle by mutableIntStateOf(0)
    private var refreshing by mutableStateOf(false)
    private var loadingMore by mutableStateOf(false)
    private var hasMore by mutableStateOf(false)
    private var loadError by mutableStateOf<String?>(null)
    private var articleFlowError by mutableStateOf<String?>(null)
    private var title by mutableStateOf("")
    private var searchVisible by mutableStateOf(false)
    private var searchQuery by mutableStateOf("")
    private var articleFlowJob: Job? = null
    private var activeArticleModel: RssArticlesViewModel? = null
    private val loadedSorts = hashSetOf<String>()

    private val editSourceResult = registerForActivityResult(
        StartActivityContract(RssSourceEditActivity::class.java)
    ) {
        if (it.resultCode == RESULT_OK) {
            loadedSorts.clear()
            reloadSource()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        selectedSort = savedInstanceState?.getInt(CURRENT_POSITION, 0) ?: 0
        binding.root.setContent {
            NgAppTheme {
                val source = viewModel.rssSource
                RssArticlesScreen(
                    title = title,
                    sorts = sorts,
                    selectedSort = selectedSort,
                    articles = articles,
                    articleStyle = articleStyle,
                    refreshing = refreshing,
                    loadingMore = loadingMore,
                    hasMore = hasMore && articleFlowError == null,
                    loadError = articleFlowError ?: loadError,
                    searchVisible = searchVisible,
                    searchQuery = searchQuery,
                    searchEnabled = !source?.searchUrl.isNullOrBlank(),
                    loginVisible = !source?.loginUrl.isNullOrBlank(),
                    onBack = ::handleBack,
                    onSortSelected = ::selectSort,
                    onRefresh = ::refreshArticles,
                    onLoadMore = { activeArticleModel?.let(::loadMore) },
                    onRetry = ::retryArticles,
                    onOpenArticle = { ReadRss.readRss(this, it, viewModel.rssSource) },
                    onSearchQueryChange = { searchQuery = it },
                    onSearch = ::submitSearch,
                    onAction = ::handleAction
                )
            }
        }
        onBackPressedDispatcher.addCallback(this) { handleBack() }
        reloadSource()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        selectedSort = 0
        loadedSorts.clear()
        reloadSource()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(CURRENT_POSITION, selectedSort)
        super.onSaveInstanceState(outState)
    }

    private fun reloadSource() {
        viewModel.initData(intent) {
            articleStyle = viewModel.articleStyle ?: 0
            lifecycleScope.launch { rebuildSorts() }
        }
    }

    private suspend fun rebuildSorts() {
        val source = viewModel.rssSource ?: return
        val resolved = when {
            viewModel.searchKey != null -> listOf(
                getString(R.string.search) to source.searchUrl.orEmpty()
            )
            !viewModel.sortUrl.isNullOrBlank() -> parseSortUrl(viewModel.sortUrl.orEmpty())
            else -> source.sortUrls()
        }.filter { it.second.isNotBlank() }
        sorts = resolved
        selectedSort = selectedSort.coerceIn(0, (resolved.size - 1).coerceAtLeast(0))
        title = when {
            viewModel.searchKey != null -> viewModel.searchKey.orEmpty()
            resolved.size == 1 && resolved.first().first.isNotBlank() -> resolved.first().first
            else -> viewModel.sourceName.orEmpty()
        }
        if (resolved.isEmpty()) {
            activeArticleModel?.loadState?.removeObservers(this)
            activeArticleModel = null
            articleFlowJob?.cancel()
            articles = emptyList()
            refreshing = false
            loadingMore = false
            hasMore = false
            loadError = null
            articleFlowError = null
            return
        }
        if (source.preload) {
            resolved.forEachIndexed { index, _ -> activateSort(index, collect = index == selectedSort) }
        } else {
            activateSort(selectedSort, collect = true)
        }
    }

    private fun parseSortUrl(value: String): List<Pair<String, String>> {
        return try {
            if (value.isJsonObject()) {
                GSONStrict.fromJsonObject<Map<String, String>>(value)
                    .getOrThrow()
                    .map { it.key to it.value }
            } else {
                listOf("" to value)
            }
        } catch (_: Exception) {
            listOf("" to value)
        }
    }

    private fun selectSort(index: Int) {
        if (index !in sorts.indices || index == selectedSort) return
        selectedSort = index
        activateSort(index, collect = true)
    }

    private fun articleModel(index: Int): RssArticlesViewModel {
        val sort = sorts[index]
        val key = "rss_articles_${viewModel.url}_${sort.first}_${sort.second}"
        return ViewModelProvider(this).get(key, RssArticlesViewModel::class.java).apply {
            sortName = sort.first
            sortUrl = sort.second
            searchKey = viewModel.searchKey
        }
    }

    private fun activateSort(index: Int, collect: Boolean) {
        if (index !in sorts.indices) return
        val source = viewModel.rssSource ?: return
        val model = articleModel(index)
        val sort = sorts[index]
        val loadKey = source.sourceUrl + '\u0000' + sort.first + '\u0000' + sort.second +
                '\u0000' + viewModel.searchKey.orEmpty()
        if (collect) {
            activeArticleModel?.takeIf { it !== model }?.let { previous ->
                previous.loadState.removeObservers(this)
            }
            activeArticleModel = model
            articleFlowJob?.cancel()
            articles = emptyList()
            refreshing = false
            loadingMore = false
            hasMore = false
            loadError = null
            model.loadState.removeObservers(this)
            model.loadState.observe(this) { state ->
                if (activeArticleModel === model) {
                    refreshing = state.refreshing
                    loadingMore = state.loadingMore
                    hasMore = state.hasMore
                    loadError = state.error
                }
            }
            collectArticles(source.sourceUrl, sort.first, model)
        }
        if (loadedSorts.add(loadKey)) {
            model.loadArticles(source)
        }
    }

    private fun refreshArticles() {
        if (loadingMore) return
        val source = viewModel.rssSource ?: return
        val model = activeArticleModel ?: return
        if (articleFlowError != null) collectArticles(source.sourceUrl, model.sortName, model)
        model.loadArticles(source)
    }

    private fun loadMore(model: RssArticlesViewModel) {
        if (refreshing || loadingMore || !hasMore || loadError != null || articleFlowError != null) return
        val source = viewModel.rssSource ?: return
        model.loadMore(source)
    }

    private fun retryArticles() {
        if (refreshing || loadingMore) return
        val source = viewModel.rssSource ?: return
        val model = activeArticleModel ?: return
        if (articleFlowError != null) {
            collectArticles(source.sourceUrl, model.sortName, model)
        }
        if (loadError != null) model.retry(source)
    }

    private fun collectArticles(origin: String, sort: String, model: RssArticlesViewModel) {
        articleFlowJob?.cancel()
        articleFlowError = null
        articleFlowJob = lifecycleScope.launch {
            appDb.rssArticleDao.flowByOriginSort(origin, sort)
                .flowOn(IO)
                .catch {
                    AppLog.put("订阅文章界面获取数据失败\n${it.localizedMessage}", it)
                    if (activeArticleModel === model) {
                        articleFlowError = it.localizedMessage ?: getString(R.string.error)
                    }
                }
                .collect {
                    if (activeArticleModel === model) {
                        articles = it
                        articleFlowError = null
                    }
                }
        }
    }

    private fun submitSearch(query: String) {
        val sourceUrl = viewModel.rssSource?.sourceUrl ?: return
        if (query.isBlank()) return
        searchVisible = false
        start(this, null, sourceUrl, query)
    }

    private fun handleAction(actionId: Int) {
        when (actionId) {
            R.id.menu_search -> searchVisible = !searchVisible
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "rssSource")
                putExtra("key", viewModel.rssSource?.sourceUrl)
            }
            R.id.menu_refresh_sort -> {
                loadedSorts.clear()
                viewModel.clearSortCache {
                    lifecycleScope.launch { rebuildSorts() }
                }
            }
            R.id.menu_set_source_variable -> setSourceVariable()
            R.id.menu_edit_source -> viewModel.rssSource?.let {
                editSourceResult.launch { putExtra("sourceUrl", it.sourceUrl) }
            }
            R.id.menu_clear -> viewModel.clearArticles()
            R.id.menu_switch_layout -> {
                viewModel.switchLayout()
                articleStyle = viewModel.articleStyle ?: 0
            }
            R.id.menu_read_record -> showDialogFragment(
                ReadRecordDialog(viewModel.rssSource?.sourceUrl)
            )
        }
    }

    private fun handleBack() {
        if (viewModel.searchKey != null) {
            intent.removeExtra("key")
            viewModel.searchKey = null
            selectedSort = 0
            loadedSorts.clear()
            lifecycleScope.launch { rebuildSorts() }
        } else {
            finish()
        }
    }

    private fun setSourceVariable() {
        lifecycleScope.launch {
            val source = viewModel.rssSource
            if (source == null) {
                toastOnUi("源不存在")
                return@launch
            }
            val comment = source.getDisplayVariableComment(
                "源变量可在js中通过source.getVariable()获取"
            )
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

    override fun setVariable(key: String, variable: String?) {
        viewModel.rssSource?.setVariable(variable)
    }

    companion object {
        private const val CURRENT_POSITION = "CURRENT_POSITION"

        fun start(context: Context, sortUrl: String?, sourceUrl: String, key: String? = null) {
            context.startActivity<RssSortActivity> {
                putExtra("sortUrl", sortUrl)
                putExtra("sourceUrl", sourceUrl)
                putExtra("key", key)
            }
        }
    }
}
