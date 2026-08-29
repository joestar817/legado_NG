package io.legado.app.ui.book.source.debug

import android.os.Bundle
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.ComposeActivityBinding
import io.legado.app.base.VMBaseActivity
import io.legado.app.help.source.clearExploreKindsCache
import io.legado.app.help.source.exploreKinds
import io.legado.app.ui.about.NetworkLogDialog
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.launch
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

class BookSourceDebugActivity :
    VMBaseActivity<ComposeActivityBinding, BookSourceDebugModel>() {

    override val binding by viewBinding(ComposeActivityBinding::inflate)
    override val viewModel by viewModels<BookSourceDebugModel>()

    private val timelineState = BookSourceDebugTimelineState()
    private var query by mutableStateOf("")
    private var helpVisible by mutableStateOf(true)
    private var searchExample by mutableStateOf("我的")
    private var exploreExample by mutableStateOf("系统::http://xxx")
    private var exploreOptions by mutableStateOf<List<Pair<String, String>>>(emptyList())
    private var timelineItems by mutableStateOf<List<BookSourceDebugTimelineState.DebugItem>>(
        emptyList()
    )
    private var sourceName = ""

    private val qrCodeResult = registerForActivityResult(QrCodeResult()) {
        it?.let(::startSearch)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.composeView.setContent {
            NgAppTheme {
                BookSourceDebugScreen(
                    query = query,
                    helpVisible = helpVisible,
                    searchExample = searchExample,
                    exploreExample = exploreExample,
                    exploreOptions = exploreOptions,
                    timelineItems = timelineItems,
                    onQueryChange = { query = it },
                    onSearch = ::startSearch,
                    onFocusChanged = { helpVisible = it },
                    onBack = ::finish,
                    onAction = ::onMenuAction,
                    onExploreClick = {
                        if (!exploreExample.startsWith("ERROR:")) {
                            startSearch(exploreExample)
                        }
                    },
                    onExploreSelected = { title, url ->
                        exploreExample = "$title::$url"
                        startSearch(exploreExample)
                    },
                    onInfoClick = {
                        query.takeIf(String::isNotBlank)?.let(::startSearch)
                    },
                    onTocClick = { prefixAutoComplete("++") },
                    onContentClick = { prefixAutoComplete("--") },
                    onPhaseClick = ::openRawLog,
                )
            }
        }
        viewModel.init(intent.getStringExtra("key")) {
            sourceName = viewModel.bookSource?.getDisPlayNameGroup().orEmpty()
            timelineItems = timelineState.setSourceName(sourceName)
            viewModel.bookSource?.ruleSearch?.checkKeyWord
                ?.takeIf(String::isNotBlank)
                ?.let { searchExample = it }
            initExploreKinds()
        }
        viewModel.observe { state, message ->
            lifecycleScope.launch {
                timelineItems = if (state in listOf(10, 20, 30, 40)) {
                    timelineState.addResponse(state, message)
                } else {
                    timelineState.addLog(state, message)
                }
            }
        }
    }

    private fun initExploreKinds() {
        lifecycleScope.launch {
            val kinds = viewModel.bookSource?.exploreKinds()
                ?.filter { !it.url.isNullOrBlank() }
                .orEmpty()
            exploreOptions = kinds.map { it.title to it.url.orEmpty() }
            exploreOptions.firstOrNull()?.let { (title, url) ->
                exploreExample = "$title::$url"
                if (title.startsWith("ERROR:")) {
                    timelineItems = timelineState.addLog(-1, "获取发现出错\n$url")
                    helpVisible = false
                }
            }
        }
    }

    private fun prefixAutoComplete(prefix: String) {
        when {
            query.isBlank() || query.length <= 2 -> query = prefix
            !query.startsWith(prefix) -> startSearch("$prefix$query")
            else -> startSearch(query)
        }
    }

    private fun startSearch(key: String) {
        query = key
        helpVisible = false
        timelineItems = timelineState.clearLogs()
        timelineItems = timelineState.setSourceName(sourceName)
        viewModel.startDebug(
            key,
            error = { toastOnUi("未获取到书源") },
        )
    }

    private fun openRawLog(item: BookSourceDebugTimelineState.DebugItem.Phase) {
        if (!item.hasRawText) return
        val rawText = timelineState.rawText(item.phaseId)
        if (rawText.isBlank()) return
        showDialogFragment(
            CodeDialog(
                code = rawText,
                title = item.title,
                highlightMode = CodeDialog.HighlightMode.DebugLog,
            )
        )
    }

    private fun onMenuAction(itemId: Int) {
        when (itemId) {
            R.id.menu_scan -> qrCodeResult.launch()
            R.id.menu_search_src -> showDialogFragment(
                CodeDialog(viewModel.searchSrc.orEmpty(), title = "html")
            )
            R.id.menu_book_src -> showDialogFragment(
                CodeDialog(viewModel.bookSrc.orEmpty(), title = "html")
            )
            R.id.menu_toc_src -> showDialogFragment(
                CodeDialog(viewModel.tocSrc.orEmpty(), title = "html")
            )
            R.id.menu_content_src -> showDialogFragment(
                CodeDialog(viewModel.contentSrc.orEmpty(), title = "html")
            )
            R.id.menu_network_log -> showDialogFragment(NetworkLogDialog())
            R.id.menu_refresh_explore -> lifecycleScope.launch {
                viewModel.bookSource?.clearExploreKindsCache()
                timelineItems = timelineState.clearLogs()
                helpVisible = true
                initExploreKinds()
            }
            R.id.menu_help -> showHelp("debugHelp")
        }
    }
}
