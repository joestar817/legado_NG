package io.legado.app.ui.main.explore

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.help.config.AppConfig
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.help.source.isOpenableExploreCategory
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchGroupVisibility
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.login.SourceLoginActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * 发现界面
 */
class ExploreFragment() : Fragment(), MainFragmentInterface {

    constructor(position: Int) : this() {
        arguments = Bundle().apply { putInt("position", position) }
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val viewModel by viewModels<ExploreViewModel>()
    private lateinit var composeView: ComposeView
    private var searchQuery by mutableStateOf("")
    private var groups by mutableStateOf<List<String>>(emptyList())
    private var selectedGroup by mutableStateOf<String?>(null)
    private var exploreLayoutMode by mutableStateOf(
        ExploreLayoutMode.from(AppConfig.exploreLayoutMode)
    )
    private var sources by mutableStateOf<List<BookSourcePart>>(emptyList())
    private var expandedSourceUrl by mutableStateOf<String?>(null)
    private val kindsBySource = mutableStateMapOf<String, List<ExploreKind>>()
    private val loadingSources = mutableStateMapOf<String, Boolean>()
    private var bottomInsetPx by mutableStateOf(0)
    private var scrollToTopToken by mutableStateOf(0L)
    private var exploreFlowJob: Job? = null
    private var groupsFlowJob: Job? = null
    private val kindJobs = mutableMapOf<String, Job>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        composeView = ComposeView(requireContext()).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    ExploreScreen(
                        sources = sources,
                        query = searchQuery,
                        groups = groups,
                        selectedGroup = selectedGroup,
                        layoutMode = exploreLayoutMode,
                        expandedSourceUrl = expandedSourceUrl,
                        kindsBySource = kindsBySource,
                        loadingSourceUrls = loadingSources.keys.toSet(),
                        bottomInsetPx = bottomInsetPx,
                        scrollToTopToken = scrollToTopToken,
                        transparentTopBar = requireContext().transparentNavBar ||
                                requireContext().getPrefBoolean(PreferKey.tNavBar, false),
                        onQueryChange = { query ->
                            searchQuery = query
                            if (query.isNotBlank()) selectedGroup = null
                            upExploreData()
                        },
                        onGroupSelected = { group ->
                            if (exploreLayoutMode != ExploreLayoutMode.GROUP_GRID || group == null) {
                                selectedGroup = group
                                searchQuery = ""
                                upExploreData()
                            }
                        },
                        onLayoutModeChange = { mode ->
                            if (exploreLayoutMode != mode) {
                                val resetGroup = mode == ExploreLayoutMode.GROUP_GRID &&
                                        selectedGroup != null
                                exploreLayoutMode = mode
                                expandedSourceUrl = null
                                AppConfig.exploreLayoutMode = mode.value
                                scrollToTopToken++
                                if (resetGroup) {
                                    selectedGroup = null
                                    upExploreData()
                                }
                            }
                        },
                        onManageSources = { startActivity<BookSourceActivity>() },
                        onToggleSource = ::toggleSource,
                        onOpenSource = ::openSourceDefault,
                        onOpenKind = ::openExploreKind,
                        onShowError = { showDialogFragment(TextDialog("ERROR", it)) },
                        onSourceAction = ::handleSourceAction,
                        onRefreshSource = ::refreshSource
                    )
                }
            }
        }
        return composeView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initGroupData()
        upExploreData()
    }

    private fun initGroupData() {
        groupsFlowJob?.cancel()
        groupsFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups = SearchGroupVisibility.visibleGroups(it)
                    delay(500)
                }
        }
    }

    private fun upExploreData() {
        if (!this::composeView.isInitialized) return
        exploreFlowJob?.cancel()
        val sourceName = searchQuery.trim()
        val group = selectedGroup
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                sourceName.isNotEmpty() -> appDb.bookSourceDao.flowExploreByName(sourceName)
                group != null -> appDb.bookSourceDao.flowGroupExplore(group)
                else -> appDb.bookSourceDao.flowExplore()
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                sources = it
                if (expandedSourceUrl != null && it.none { source ->
                        source.bookSourceUrl == expandedSourceUrl
                    }
                ) {
                    expandedSourceUrl = null
                }
                delay(500)
            }
        }
    }

    private fun toggleSource(source: BookSourcePart) {
        if (expandedSourceUrl == source.bookSourceUrl) {
            expandedSourceUrl = null
            return
        }
        expandedSourceUrl = source.bookSourceUrl
        loadKinds(source)
    }

    private fun openSourceDefault(source: BookSourcePart) {
        val cachedKinds = kindsBySource[source.bookSourceUrl]
        if (cachedKinds != null) {
            openDefaultKind(source, cachedKinds)
        } else {
            loadKinds(source) { openDefaultKind(source, it) }
        }
    }

    private fun loadKinds(
        source: BookSourcePart,
        onLoaded: ((List<ExploreKind>) -> Unit)? = null
    ) {
        val sourceUrl = source.bookSourceUrl
        kindsBySource[sourceUrl]?.let {
            onLoaded?.invoke(it)
            return
        }
        if (loadingSources[sourceUrl] == true) return
        kindJobs[sourceUrl]?.cancel()
        loadingSources[sourceUrl] = true
        kindJobs[sourceUrl] = viewLifecycleOwner.lifecycleScope.launch {
            runCatching { source.exploreKinds() }
                .onSuccess {
                    kindsBySource[sourceUrl] = it
                    onLoaded?.invoke(it)
                }
                .onFailure {
                    AppLog.put("加载发现分类失败", it)
                    toastOnUi(it.localizedMessage ?: getString(R.string.can_not_open))
                }
            loadingSources.remove(sourceUrl)
            kindJobs.remove(sourceUrl)
        }
    }

    private fun openDefaultKind(source: BookSourcePart, kinds: List<ExploreKind>) {
        val errorKind = kinds.firstOrNull { it.title.startsWith("ERROR:") }
        val hasDetailContent = kinds.any {
            it.isOpenableExploreCategory() ||
                    it.type == Type.button ||
                    it.type == Type.text ||
                    it.type == Type.toggle ||
                    it.type == Type.select
        }
        when {
            errorKind != null -> {
                showDialogFragment(TextDialog("ERROR", errorKind.url.orEmpty()))
            }

            hasDetailContent -> openExplore(
                sourceUrl = source.bookSourceUrl,
                sourceName = source.bookSourceName
            )

            else -> toastOnUi(R.string.explore_direct_entry_empty)
        }
    }

    private fun openExploreKind(source: BookSourcePart, kind: ExploreKind) {
        openExplore(
            sourceUrl = source.bookSourceUrl,
            sourceName = source.bookSourceName,
            exploreName = kind.title,
            exploreUrl = kind.url
        )
    }

    private fun openExplore(
        sourceUrl: String,
        sourceName: String,
        exploreName: String? = null,
        exploreUrl: String? = null
    ) {
        startActivity<ExploreShowActivity> {
            putExtra(ExploreShowActivity.EXTRA_SOURCE_URL, sourceUrl)
            putExtra(ExploreShowActivity.EXTRA_SOURCE_NAME, sourceName)
            putExtra(ExploreShowActivity.EXTRA_EXPLORE_NAME, exploreName)
            putExtra(ExploreShowActivity.EXTRA_EXPLORE_URL, exploreUrl)
        }
    }

    private fun handleSourceAction(source: BookSourcePart, actionId: Int) {
        when (actionId) {
            R.id.menu_edit -> startActivity<BookSourceEditActivity> {
                putExtra("sourceUrl", source.bookSourceUrl)
            }

            R.id.menu_top -> viewModel.topSource(source)
            R.id.menu_search -> SearchActivity.start(requireContext(), source)
            R.id.menu_login -> startActivity<SourceLoginActivity> {
                putExtra("type", "bookSource")
                putExtra("key", source.bookSourceUrl)
            }

            R.id.menu_refresh -> refreshSource(source)
            R.id.menu_del -> deleteSource(source)
        }
    }

    private fun refreshSource(source: BookSourcePart) {
        val sourceUrl = source.bookSourceUrl
        kindJobs[sourceUrl]?.cancel()
        loadingSources[sourceUrl] = true
        kindJobs[sourceUrl] = viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                source.clearExploreKindsCache()
                kindsBySource.remove(sourceUrl)
                if (expandedSourceUrl == sourceUrl) source.exploreKinds() else null
            }.onSuccess { kinds ->
                if (kinds != null) kindsBySource[sourceUrl] = kinds
            }.onFailure {
                AppLog.put("刷新发现分类失败", it)
                toastOnUi(it.localizedMessage ?: getString(R.string.can_not_open))
            }
            loadingSources.remove(sourceUrl)
            kindJobs.remove(sourceUrl)
        }
    }

    private fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton { viewModel.deleteSource(source) }
        }
    }

    override fun onResume() {
        super.onResume()
        (activity as? MainActivity)?.resolveFloatingBottomContentInset {
            bottomInsetPx = it
        }
    }

    override fun onPause() {
        if (this::composeView.isInitialized) composeView.clearFocus()
        saveExploreInfoMaps()
        super.onPause()
    }

    private fun saveExploreInfoMaps() {
        val infoMaps = ExploreInfoStore.infoMapList.snapshot()
            .filter { (_, infoMap) -> infoMap.needSave }
        viewLifecycleOwner.lifecycleScope.launch {
            infoMaps.map { (_, infoMap) ->
                launch(IO) { infoMap.saveNow() }
            }.joinAll()
        }
    }

    override fun onDestroyView() {
        kindJobs.values.forEach { it.cancel() }
        kindJobs.clear()
        super.onDestroyView()
    }

    fun compressExplore() {
        if (expandedSourceUrl != null) {
            expandedSourceUrl = null
        } else {
            scrollToTopToken++
        }
    }
}
