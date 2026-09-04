package io.legado.app.ui.book.searchContent

import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.help.book.BookHelp
import io.legado.app.help.book.isLocal
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class ReadSearchDialog : BottomSheetDialogFragment() {

    private val searchViewModel by activityViewModels<SearchContentViewModel>()
    private var query by mutableStateOf("")
    private val chapterTargets = mutableStateListOf<SearchChapterTarget>()
    private var visibleResultCount by mutableStateOf(0)
    private var selectedResultIndex by mutableStateOf(-1)
    private var loadingBook by mutableStateOf(true)
    private var searching by mutableStateOf(false)
    private var errorMessage by mutableStateOf<String?>(null)
    private var applyReplace by mutableStateOf(SearchContentViewModel.replaceEnabled)
    private var supportRegex by mutableStateOf(SearchContentViewModel.regexReplace)
    private var searchJob: Job? = null
    private var queryDebounceJob: Job? = null
    private var searchGeneration = 0
    private var bottomDialogRegistered = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val readActivity = activity as? ReadBookActivity ?: run {
            dismissAllowingStateLoss()
            return
        }
        val book = ReadBook.book ?: run {
            dismissAllowingStateLoss()
            return
        }
        if (!bottomDialogRegistered) {
            if (readActivity.bottomDialog > 0) {
                dismissAllowingStateLoss()
                return
            }
            readActivity.bottomDialog += 1
            bottomDialogRegistered = true
        }

        query = arguments?.getString(ARG_QUERY).orEmpty()
        selectedResultIndex = arguments?.getInt(ARG_SELECTED_INDEX, -1) ?: -1
        ReadFloatingAppearanceState.refreshFromConfig()
        val snapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        (view as ComposeView).setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                ReadSearchPanel(
                    query = query,
                    resultCount = visibleResultCount,
                    chapterTargets = chapterTargets,
                    resultAt = searchViewModel.searchResultList::getOrNull,
                    selectedResultIndex = selectedResultIndex,
                    loadingBook = loadingBook,
                    searching = searching,
                    errorMessage = errorMessage,
                    applyReplace = applyReplace,
                    supportRegex = supportRegex,
                    onQueryChange = {
                        query = it
                        errorMessage = null
                        scheduleContentSearch(it)
                    },
                    onSearch = ::runContentSearchNow,
                    onStopSearch = ::stopContentSearch,
                    onToggleApplyReplace = {
                        applyReplace = !applyReplace
                        SearchContentViewModel.replaceEnabled = applyReplace
                        runContentSearchNow(query)
                    },
                    onToggleSupportRegex = {
                        supportRegex = !supportRegex
                        SearchContentViewModel.regexReplace = supportRegex
                        runContentSearchNow(query)
                    },
                    onResultClick = { index ->
                        selectedResultIndex = index
                        val currentResults = searchViewModel.searchResultList.toList()
                        if (index in currentResults.indices) {
                            readActivity.showSearchResult(currentResults, index)
                            dismissAllowingStateLoss()
                        }
                    },
                    onDismissRequest = { dismissAllowingStateLoss() },
                )
            }
        }

        if (searchViewModel.bookUrl.isNotBlank() && searchViewModel.bookUrl != book.bookUrl) {
            searchViewModel.searchResultList.clear()
            searchViewModel.searchResultBatches.clear()
            searchViewModel.cacheChapterNames.clear()
            searchViewModel.lastQuery = ""
            searchViewModel.searchResultCounts = 0
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val initializedBook = runCatching {
                withContext(Dispatchers.IO) {
                    searchViewModel.initBook(book.bookUrl)?.also {
                        searchViewModel.cacheChapterNames.clear()
                        searchViewModel.cacheChapterNames.addAll(BookHelp.getChapterFiles(it))
                    }
                }
            }.onFailure {
                AppLog.put("全文搜索初始化失败\n${it.localizedMessage}", it)
            }.getOrNull()
            loadingBook = false
            if (initializedBook == null) {
                errorMessage = getString(R.string.search_failed)
                return@launch
            }
            val cachedResults = searchViewModel.searchResultList.toList()
            if (query.isNotBlank() && searchViewModel.lastQuery == query && cachedResults.isNotEmpty()) {
                restoreVisibleResults(cachedResults)
                selectedResultIndex = selectedResultIndex.coerceIn(cachedResults.indices)
            } else if (query.isNotBlank()) {
                runContentSearchNow(query)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply { dimAmount = 0.12f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * 0.82f).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            isDraggable = false
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
        ReadDrawerStyle.installImeOnlyBottomSheetInsetsAnimation(sheet)
    }

    override fun onDismiss(dialog: DialogInterface) {
        queryDebounceJob?.cancel()
        queryDebounceJob = null
        stopContentSearch()
        super.onDismiss(dialog)
        if (bottomDialogRegistered) {
            (activity as? ReadBookActivity)?.let {
                it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0)
            }
            bottomDialogRegistered = false
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示阅读全文搜索抽屉失败 tag:$tag", it) }
    }

    private fun clearSearchResults() {
        chapterTargets.clear()
        visibleResultCount = 0
        searchViewModel.searchResultList.clear()
        searchViewModel.searchResultBatches.clear()
        searchViewModel.searchResultCounts = 0
    }

    private fun appendSearchBatch(batch: SearchResultBatch) {
        if (batch.results.isEmpty()) return
        val resultStartIndex = searchViewModel.searchResultList.size
        val lazyItemIndex = resultStartIndex + chapterTargets.size
        searchViewModel.searchResultList.addAll(batch.results)
        searchViewModel.searchResultBatches.add(batch)
        chapterTargets.add(
            SearchChapterTarget(
                chapterIndex = batch.chapterIndex,
                chapterTitle = batch.chapterTitle,
                resultCount = batch.results.size,
                resultStartIndex = resultStartIndex,
                lazyItemIndex = lazyItemIndex,
            )
        )
        visibleResultCount = searchViewModel.searchResultList.size
        searchViewModel.searchResultCounts = visibleResultCount
    }

    private fun restoreVisibleResults(cachedResults: List<SearchResult>) {
        chapterTargets.clear()
        var resultStartIndex = 0
        var lazyItemIndex = 0
        val batches = searchViewModel.searchResultBatches.takeIf { it.isNotEmpty() }
            ?: cachedResults
                .groupBy { it.chapterIndex }
                .values
                .map { chapterResults ->
                    SearchResultBatch(
                        chapterIndex = chapterResults.first().chapterIndex,
                        chapterTitle = chapterResults.first().chapterTitle,
                        results = chapterResults,
                    )
                }
                .also(searchViewModel.searchResultBatches::addAll)
        batches.forEach { batch ->
            if (batch.results.isEmpty()) return@forEach
            chapterTargets.add(
                SearchChapterTarget(
                    chapterIndex = batch.chapterIndex,
                    chapterTitle = batch.chapterTitle,
                    resultCount = batch.results.size,
                    resultStartIndex = resultStartIndex,
                    lazyItemIndex = lazyItemIndex,
                )
            )
            resultStartIndex += batch.results.size
            lazyItemIndex += batch.results.size + 1
        }
        visibleResultCount = cachedResults.size
        searchViewModel.searchResultCounts = cachedResults.size
    }

    private fun startContentSearch(rawQuery: String) {
        val normalizedQuery = rawQuery.trim()
        if (normalizedQuery.isBlank() || loadingBook) return
        if (supportRegex && runCatching { Regex(normalizedQuery) }.isFailure) {
            errorMessage = getString(R.string.search_invalid_regex)
            return
        }

        searchGeneration += 1
        val generation = searchGeneration
        searchJob?.cancel()
        query = normalizedQuery
        errorMessage = null
        searching = true
        selectedResultIndex = -1
        SearchContentViewModel.replaceEnabled = applyReplace
        SearchContentViewModel.regexReplace = supportRegex
        clearSearchResults()
        searchViewModel.lastQuery = normalizedQuery

        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            val resultQueue = Channel<SearchResultBatch>(capacity = SEARCH_RESULT_QUEUE_CAPACITY)
            try {
                coroutineScope {
                    val producer = launch(Dispatchers.IO) {
                        try {
                            appDb.bookChapterDao.getChapterList(searchViewModel.bookUrl)
                                .forEach { chapter ->
                                    ensureActive()
                                    val searchable = searchViewModel.book?.let { currentBook ->
                                        currentBook.isLocal ||
                                            searchViewModel.cacheChapterNames.contains(
                                                chapter.getFileName()
                                            ) ||
                                            BookHelp.hasContent(currentBook, chapter)
                                    } == true
                                    if (!searchable) return@forEach
                                    val chapterResults = searchViewModel.searchChapter(
                                        normalizedQuery,
                                        chapter,
                                    )
                                    ensureActive()
                                    if (chapterResults.isNotEmpty()) {
                                        resultQueue.send(
                                            SearchResultBatch(
                                                chapterIndex = chapter.index,
                                                chapterTitle = chapterResults.first().chapterTitle,
                                                results = chapterResults,
                                            )
                                        )
                                    }
                                }
                        } finally {
                            resultQueue.close()
                        }
                    }
                    for (batch in resultQueue) {
                        ensureActive()
                        if (generation == searchGeneration) {
                            appendSearchBatch(batch)
                        }
                    }
                    producer.join()
                }
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException) throw error
                AppLog.put("全文搜索出错\n${error.localizedMessage}", error)
                if (generation == searchGeneration) {
                    errorMessage = error.localizedMessage ?: getString(R.string.search_failed)
                }
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (generation == searchGeneration) searching = false
                }
            }
        }
    }

    private fun scheduleContentSearch(rawQuery: String) {
        queryDebounceJob?.cancel()
        queryDebounceJob = null
        stopContentSearch()
        val normalizedQuery = rawQuery.trim()
        if (normalizedQuery.isBlank()) {
            selectedResultIndex = -1
            clearSearchResults()
            searchViewModel.lastQuery = ""
            return
        }
        if (loadingBook) return
        searching = true
        clearSearchResults()
        queryDebounceJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(300)
            queryDebounceJob = null
            startContentSearch(normalizedQuery)
        }
    }

    private fun runContentSearchNow(rawQuery: String) {
        queryDebounceJob?.cancel()
        queryDebounceJob = null
        startContentSearch(rawQuery)
    }

    private fun stopContentSearch() {
        searchGeneration += 1
        searchJob?.cancel()
        searchJob = null
        searching = false
    }

    companion object {
        const val TAG = "readSearch"
        private const val ARG_QUERY = "query"
        private const val ARG_SELECTED_INDEX = "selectedIndex"
        private const val SEARCH_RESULT_QUEUE_CAPACITY = 8

        fun newInstance(query: String?, selectedIndex: Int): ReadSearchDialog =
            ReadSearchDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_QUERY, query.orEmpty())
                    putInt(ARG_SELECTED_INDEX, selectedIndex)
                }
            }
    }
}

@Composable
private fun ReadSearchPanel(
    query: String,
    resultCount: Int,
    chapterTargets: List<SearchChapterTarget>,
    resultAt: (Int) -> SearchResult?,
    selectedResultIndex: Int,
    loadingBook: Boolean,
    searching: Boolean,
    errorMessage: String?,
    applyReplace: Boolean,
    supportRegex: Boolean,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onStopSearch: () -> Unit,
    onToggleApplyReplace: () -> Unit,
    onToggleSupportRegex: () -> Unit,
    onResultClick: (Int) -> Unit,
    onDismissRequest: () -> Unit,
) {
    val contentColor = Color(NgTheme.colors.onSurface)
    val mutedColor = Color(NgTheme.colors.onSurfaceVariant)
    val accentColor = Color(NgTheme.colors.primary)
    val dockColor = ReadDrawerStyle.dockSurfaceColor(alpha = 0.34f)
    val chapterCount = chapterTargets.size
    val showResultSummary = query.isNotBlank() || searching || resultCount > 0
    val settingsMenuState = remember { NgPopupToggleState() }

    NgGlassSurface(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        style = readFloatingGlassStyle().copy(shadowElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(top = 8.dp),
        ) {
            SearchDragHandle(
                mutedColor = mutedColor,
                onDismissRequest = onDismissRequest,
            )
            SearchInputRow(
                query = query,
                loading = loadingBook,
                contentColor = contentColor,
                mutedColor = mutedColor,
                accentColor = accentColor,
                dockColor = dockColor,
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                settingsExpanded = settingsMenuState.expanded,
                onSettingsAnchorClick = settingsMenuState::onAnchorClick,
                onSettingsDismiss = settingsMenuState::onDismissRequest,
                applyReplace = applyReplace,
                supportRegex = supportRegex,
                onToggleApplyReplace = onToggleApplyReplace,
                onToggleSupportRegex = onToggleSupportRegex,
            )
            if (showResultSummary) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.search_result_summary,
                            resultCount,
                            chapterCount,
                        ),
                        color = mutedColor.copy(alpha = 0.86f),
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    if (searching) {
                        Text(
                            text = stringResource(R.string.stop),
                            color = accentColor,
                            fontSize = 14.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClick = onStopSearch)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = mutedColor.copy(alpha = 0.12f),
                )
            }
            when {
                loadingBook -> SearchCenteredProgress(accentColor)
                errorMessage != null -> SearchEmptyState(errorMessage, mutedColor)
                resultCount == 0 && searching -> SearchCenteredProgress(accentColor)
                resultCount == 0 -> SearchEmptyState(
                    text = if (query.isBlank()) {
                        stringResource(R.string.search_input_hint)
                    } else {
                        stringResource(R.string.search_content_empty)
                    },
                    color = mutedColor,
                )
                else -> SearchResultList(
                    resultCount = resultCount,
                    chapterTargets = chapterTargets,
                    resultAt = resultAt,
                    searching = searching,
                    selectedResultIndex = selectedResultIndex,
                    contentColor = contentColor,
                    mutedColor = mutedColor,
                    accentColor = accentColor,
                    onResultClick = onResultClick,
                )
            }
        }
    }
}

@Composable
private fun SearchDragHandle(
    mutedColor: Color,
    onDismissRequest: () -> Unit,
) {
    var draggedDownPx by remember { mutableStateOf(0f) }
    val dismissThresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .pointerInput(dismissThresholdPx) {
                detectVerticalDragGestures(
                    onDragStart = { draggedDownPx = 0f },
                    onVerticalDrag = { _, dragAmount ->
                        draggedDownPx = (draggedDownPx + dragAmount).coerceAtLeast(0f)
                    },
                    onDragCancel = { draggedDownPx = 0f },
                    onDragEnd = {
                        if (draggedDownPx >= dismissThresholdPx) onDismissRequest()
                        draggedDownPx = 0f
                    },
                )
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(mutedColor.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun SearchInputRow(
    query: String,
    loading: Boolean,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    dockColor: Color,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    settingsExpanded: Boolean,
    onSettingsAnchorClick: () -> Unit,
    onSettingsDismiss: () -> Unit,
    applyReplace: Boolean,
    supportRegex: Boolean,
    onToggleApplyReplace: () -> Unit,
    onToggleSupportRegex: () -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    var inputFocused by remember { mutableStateOf(false) }
    val menuButtonShape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .onFocusChanged { inputFocused = it.isFocused },
            enabled = !loading,
            singleLine = true,
            textStyle = TextStyle(
                color = contentColor,
                fontSize = 15.sp,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onSearch(query)
                    keyboardController?.hide()
                },
            ),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(dockColor)
                        .padding(horizontal = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable(enabled = !loading && query.isNotBlank()) {
                                onSearch(query)
                                keyboardController?.hide()
                            },
                        tint = mutedColor,
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isBlank() && !inputFocused) {
                            Text(
                                text = stringResource(R.string.search_input_hint),
                                color = mutedColor.copy(alpha = 0.72f),
                                fontSize = 15.sp,
                            )
                        }
                        innerTextField()
                    }
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = accentColor,
                            strokeWidth = 2.dp,
                        )
                    } else if (query.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear),
                            modifier = Modifier
                                .size(20.dp)
                                .clickable { onQueryChange("") },
                            tint = mutedColor,
                        )
                    }
                }
            },
        )
        Spacer(Modifier.width(2.dp))
        Box {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(menuButtonShape)
                    .background(dockColor)
                    .clickable(onClick = onSettingsAnchorClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_grid_menu),
                    contentDescription = stringResource(R.string.menu),
                    modifier = Modifier.size(20.dp),
                    tint = contentColor,
                )
            }
            DropdownMenu(
                expanded = settingsExpanded,
                onDismissRequest = onSettingsDismiss,
                modifier = Modifier.width(136.dp),
                shape = RoundedCornerShape(18.dp),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
            ) {
                SearchOptionMenuItem(
                    label = stringResource(R.string.search_apply_replace),
                    selected = applyReplace,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onClick = onToggleApplyReplace,
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    color = mutedColor.copy(alpha = 0.14f),
                )
                SearchOptionMenuItem(
                    label = stringResource(R.string.search_support_regex),
                    selected = supportRegex,
                    contentColor = contentColor,
                    accentColor = accentColor,
                    onClick = onToggleSupportRegex,
                )
            }
        }
    }
}

@Composable
private fun SearchOptionMenuItem(
    label: String,
    selected: Boolean,
    contentColor: Color,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = if (selected) accentColor else contentColor.copy(alpha = 0.66f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(text = label, color = contentColor, fontSize = 15.sp)
    }
}

@Composable
private fun SearchResultList(
    resultCount: Int,
    chapterTargets: List<SearchChapterTarget>,
    resultAt: (Int) -> SearchResult?,
    searching: Boolean,
    selectedResultIndex: Int,
    contentColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onResultClick: (Int) -> Unit,
) {
    val selectedLazyItemIndex by remember(chapterTargets, selectedResultIndex) {
        derivedStateOf {
            chapterTargets.firstOrNull { target ->
                selectedResultIndex in target.resultStartIndex until
                    (target.resultStartIndex + target.resultCount)
            }?.let { target ->
                target.lazyItemIndex + 1 + selectedResultIndex - target.resultStartIndex
            } ?: -1
        }
    }
    val listState = rememberLazyListState()
    var draggedChapterOrdinal by remember { mutableStateOf<Int?>(null) }
    val visibleChapterOrdinal by remember(chapterTargets, listState) {
        derivedStateOf {
            val firstVisibleItemIndex = listState.firstVisibleItemIndex
            chapterTargets.ordinalForLazyItem(firstVisibleItemIndex).coerceAtLeast(0)
        }
    }
    val showFastNavigator by remember(chapterTargets, listState) {
        derivedStateOf {
            chapterTargets.size > 1 &&
                (listState.canScrollBackward || listState.canScrollForward)
        }
    }
    LaunchedEffect(selectedLazyItemIndex) {
        if (selectedLazyItemIndex >= 0) {
            listState.scrollToItem(selectedLazyItemIndex)
        }
    }
    LaunchedEffect(draggedChapterOrdinal) {
        draggedChapterOrdinal?.let { ordinal ->
            chapterTargets.getOrNull(ordinal)?.let { target ->
                listState.scrollToItem(target.lazyItemIndex)
            }
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 20.dp,
                end = if (chapterTargets.size > 1) 44.dp else 20.dp,
                bottom = 24.dp,
            ),
        ) {
            items(
                count = resultCount + chapterTargets.size,
                key = { lazyItemIndex ->
                    val targetOrdinal = chapterTargets.ordinalForLazyItem(lazyItemIndex)
                    val target = chapterTargets.getOrNull(targetOrdinal)
                    if (target == null || lazyItemIndex == target.lazyItemIndex) {
                        "chapter-${target?.chapterIndex ?: lazyItemIndex}"
                    } else {
                        val resultIndex = target.resultStartIndex +
                            lazyItemIndex - target.lazyItemIndex - 1
                        val result = resultAt(resultIndex)
                        "result-${result?.chapterIndex ?: target.chapterIndex}-" +
                            "${result?.queryIndexInChapter ?: resultIndex}-$resultIndex"
                    }
                },
            ) { lazyItemIndex ->
                val targetOrdinal = chapterTargets.ordinalForLazyItem(lazyItemIndex)
                val target = chapterTargets.getOrNull(targetOrdinal) ?: return@items
                if (lazyItemIndex == target.lazyItemIndex) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_bookmark),
                            contentDescription = null,
                            modifier = Modifier.size(17.dp),
                            tint = mutedColor.copy(alpha = 0.82f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = target.chapterTitle,
                            color = contentColor,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    val resultIndex = target.resultStartIndex +
                        lazyItemIndex - target.lazyItemIndex - 1
                    val result = resultAt(resultIndex) ?: return@items
                    val selected = resultIndex == selectedResultIndex
                    val itemShape = RoundedCornerShape(14.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(itemShape)
                            .background(
                                if (selected) accentColor.copy(alpha = 0.06f) else Color.Transparent
                            )
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = accentColor.copy(alpha = 0.28f),
                                        shape = itemShape,
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onResultClick(resultIndex) }
                            .padding(horizontal = 12.dp, vertical = 13.dp),
                    ) {
                        Text(
                            text = highlightedResultText(
                                result = result,
                                accentColor = accentColor,
                                useUnderline = NgTheme.snapshot.isEInk,
                            ),
                            color = contentColor,
                            fontSize = 15.sp,
                            lineHeight = 23.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        color = mutedColor.copy(alpha = 0.11f),
                    )
                }
            }
            if (!searching) item(key = "search-results-end") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chapter_list),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = mutedColor.copy(alpha = 0.68f),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.search_all_results_shown),
                        color = mutedColor.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                    )
                }
            }
        }
        if (showFastNavigator) {
            SearchChapterFastNavigator(
                targets = chapterTargets,
                currentOrdinal = visibleChapterOrdinal,
                draggedOrdinal = draggedChapterOrdinal,
                mutedColor = mutedColor,
                accentColor = accentColor,
                onDraggedOrdinalChange = { draggedChapterOrdinal = it },
            )
        }
    }
}

private data class SearchChapterTarget(
    val chapterIndex: Int,
    val chapterTitle: String,
    val resultCount: Int,
    val resultStartIndex: Int,
    val lazyItemIndex: Int,
)

private fun List<SearchChapterTarget>.ordinalForLazyItem(lazyItemIndex: Int): Int {
    if (isEmpty()) return -1
    var low = 0
    var high = lastIndex
    var result = 0
    while (low <= high) {
        val middle = (low + high) ushr 1
        if (this[middle].lazyItemIndex <= lazyItemIndex) {
            result = middle
            low = middle + 1
        } else {
            high = middle - 1
        }
    }
    return result
}

@Composable
private fun SearchChapterFastNavigator(
    targets: List<SearchChapterTarget>,
    currentOrdinal: Int,
    draggedOrdinal: Int?,
    mutedColor: Color,
    accentColor: Color,
    onDraggedOrdinalChange: (Int?) -> Unit,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var draggedFraction by remember { mutableStateOf<Float?>(null) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val chapterTitleStyle = TextStyle(
        fontSize = 14.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    )
    val trackInsetPx = with(density) { 12.dp.toPx() }
    val latestTargets by rememberUpdatedState(targets)
    val latestOnDraggedOrdinalChange by rememberUpdatedState(onDraggedOrdinalChange)
    val safeCurrentOrdinal = currentOrdinal.coerceIn(targets.indices)
    val displayOrdinal = draggedOrdinal ?: safeCurrentOrdinal
    val displayFraction = draggedFraction ?: if (targets.lastIndex > 0) {
        displayOrdinal.toFloat() / targets.lastIndex
    } else {
        0f
    }
    val trackHeightPx = (containerSize.height - trackInsetPx * 2f).coerceAtLeast(1f)
    val pointerCenterYPx = trackInsetPx + trackHeightPx * displayFraction
    val navigatorDescription = stringResource(R.string.search_chapter_fast_navigator)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { containerSize = it },
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(48.dp)
                .fillMaxHeight()
                .semantics { contentDescription = navigatorDescription }
                .pointerInput(containerSize.height) {
                    if (latestTargets.size < 2 || containerSize.height <= 0) {
                        return@pointerInput
                    }
                    awaitEachGesture {
                        try {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val gestureTargetCount = latestTargets.size
                            val selectChapter = { y: Float ->
                                if (gestureTargetCount >= 2) {
                                    val fraction = ((y - trackInsetPx) / trackHeightPx)
                                        .coerceIn(0f, 1f)
                                    val ordinal = (
                                        fraction * (gestureTargetCount - 1)
                                        ).roundToInt()
                                    draggedFraction = fraction
                                    latestOnDraggedOrdinalChange(ordinal)
                                }
                            }
                            down.consume()
                            selectChapter(down.position.y)
                            do {
                                val event = awaitPointerEvent()
                                event.changes.firstOrNull()?.let { change ->
                                    if (change.pressed) selectChapter(change.position.y)
                                    change.consume()
                                }
                            } while (event.changes.any { it.pressed })
                        } finally {
                            draggedFraction = null
                            latestOnDraggedOrdinalChange(null)
                        }
                    }
                },
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val railX = size.width - 7.dp.toPx()
                val top = 12.dp.toPx()
                val bottom = (size.height - 12.dp.toPx()).coerceAtLeast(top)
                val tickCount = targets.size.coerceIn(18, 52)
                val normalColor = mutedColor.copy(alpha = 0.18f)
                repeat(tickCount) { tickIndex ->
                    val fraction = if (tickCount > 1) {
                        tickIndex.toFloat() / (tickCount - 1)
                    } else {
                        0f
                    }
                    val y = top + (bottom - top) * fraction
                    val major = tickIndex % 5 == 0
                    val tickLength = if (major) 9.dp.toPx() else 5.dp.toPx()
                    drawLine(
                        color = normalColor.copy(alpha = if (major) 0.34f else 0.18f),
                        start = Offset(railX - tickLength, y),
                        end = Offset(railX, y),
                        strokeWidth = if (major) 1.2.dp.toPx() else 0.8.dp.toPx(),
                    )
                }
                val pointerY = top + (bottom - top) * displayFraction
                drawLine(
                    color = accentColor.copy(alpha = 0.78f),
                    start = Offset(railX - 14.dp.toPx(), pointerY),
                    end = Offset(railX, pointerY),
                    strokeWidth = 1.3.dp.toPx(),
                )
                val pointerWidth = 24.dp.toPx()
                val pointerHeight = 10.dp.toPx()
                val pointerTipWidth = 8.dp.toPx()
                val pointerLeft = railX - pointerWidth
                val pointerBaseX = pointerLeft + pointerTipWidth
                val halfHeight = pointerHeight / 2f
                val pointerPath = Path().apply {
                    moveTo(pointerLeft, pointerY)
                    lineTo(pointerBaseX, pointerY - halfHeight)
                    lineTo(railX, pointerY - halfHeight)
                    lineTo(railX, pointerY + halfHeight)
                    lineTo(pointerBaseX, pointerY + halfHeight)
                    close()
                }
                drawPath(
                    path = pointerPath,
                    color = accentColor.copy(alpha = if (draggedOrdinal != null) 0.96f else 0.76f),
                )
            }
        }

        draggedOrdinal?.let { ordinal ->
            val target = targets.getOrNull(ordinal) ?: return@let
            val measuredTitleWidthPx = remember(target.chapterTitle, chapterTitleStyle) {
                textMeasurer.measure(
                    text = AnnotatedString(target.chapterTitle),
                    style = chapterTitleStyle,
                    maxLines = 1,
                    softWrap = false,
                ).size.width.toFloat()
            }
            val bubbleChromeWidthPx = with(density) { 58.dp.toPx() }
            val minBubbleWidthPx = with(density) { 156.dp.toPx() }
            val configuredMaxBubbleWidthPx = with(density) { 280.dp.toPx() }
            val availableBubbleWidthPx = (
                containerSize.width - with(density) { 56.dp.toPx() }
                ).coerceAtLeast(minBubbleWidthPx)
            val maxBubbleWidthPx = minOf(
                configuredMaxBubbleWidthPx,
                availableBubbleWidthPx,
            ).coerceAtLeast(minBubbleWidthPx)
            val desiredBubbleWidthPx = measuredTitleWidthPx + bubbleChromeWidthPx
            val titleWraps = desiredBubbleWidthPx > maxBubbleWidthPx
            val bubbleWidthPx = desiredBubbleWidthPx.coerceIn(
                minBubbleWidthPx,
                maxBubbleWidthPx,
            )
            val bubbleHeight = if (titleWraps) 80.dp else 64.dp
            val bubbleHeightPx = with(density) { bubbleHeight.toPx() }
            val bubbleMarginPx = with(density) { 8.dp.toPx() }
            val preferredTailCenterPx = bubbleHeightPx * 0.72f
            val bubbleTopPx = (pointerCenterYPx - preferredTailCenterPx).coerceIn(
                bubbleMarginPx,
                (containerSize.height - bubbleHeightPx - bubbleMarginPx)
                    .coerceAtLeast(bubbleMarginPx),
            )
            val tailCenterFraction = (
                (pointerCenterYPx - bubbleTopPx) / bubbleHeightPx
                ).coerceIn(0.24f, 0.76f)
            val bubbleTop = with(density) { bubbleTopPx.toDp() }
            val bubbleWidth = with(density) { bubbleWidthPx.toDp() }
            val bubbleShape = remember(tailCenterFraction) {
                SearchChapterBubbleShape(tailCenterFraction = tailCenterFraction)
            }
            NgGlassSurface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 44.dp)
                    .offset(y = bubbleTop)
                    .width(bubbleWidth)
                    .height(bubbleHeight),
                shape = bubbleShape,
                style = NgGlassDefaults.style(
                    containerAlpha = maxOf(NgTheme.effects.dialogAlpha, 0.94f),
                ).copy(
                    borderColor = accentColor.copy(alpha = 0.24f),
                    borderWidth = 0.75.dp,
                    shadowElevation = 3.dp,
                    depthEdge = Color.Transparent,
                ),
                contentPadding = PaddingValues(start = 14.dp, end = 20.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        verticalAlignment = if (titleWraps) {
                            Alignment.Top
                        } else {
                            Alignment.CenterVertically
                        },
                    ) {
                        Box(
                            modifier = Modifier
                                .then(
                                    if (titleWraps) Modifier.offset(y = 6.dp) else Modifier
                                )
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accentColor),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = target.chapterTitle,
                            color = Color(NgTheme.colors.onSurface),
                            style = chapterTitleStyle,
                            maxLines = if (titleWraps) 2 else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.search_chapter_result_count,
                            target.resultCount,
                        ),
                        color = mutedColor.copy(alpha = 0.82f),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 14.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

private class SearchChapterBubbleShape(
    private val cornerRadius: Dp = 10.dp,
    private val tailWidth: Dp = 10.dp,
    private val tailHeight: Dp = 12.dp,
    private val tailCenterFraction: Float = 0.7f,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val cornerRadiusPx = with(density) { cornerRadius.toPx() }
        val tailWidthPx = with(density) { tailWidth.toPx() }
        val tailHeightPx = with(density) { tailHeight.toPx() }
        val bodyRight = (size.width - tailWidthPx).coerceAtLeast(0f)
        val tailCenterY = size.height * tailCenterFraction.coerceIn(0.2f, 0.8f)
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    rect = Rect(0f, 0f, bodyRight, size.height),
                    cornerRadius = CornerRadius(cornerRadiusPx),
                )
            )
            moveTo(bodyRight - 1f, tailCenterY - tailHeightPx / 2f)
            lineTo(size.width, tailCenterY)
            lineTo(bodyRight - 1f, tailCenterY + tailHeightPx / 2f)
            close()
        }
        return Outline.Generic(path)
    }
}

private fun highlightedResultText(
    result: SearchResult,
    accentColor: Color,
    useUnderline: Boolean,
): AnnotatedString = buildAnnotatedString {
    append(result.resultText)
    if (result.query.isEmpty()) return@buildAnnotatedString
    val ranges = if (result.isRegex) {
        runCatching {
            Regex(result.query).findAll(result.resultText).map { it.range }.toList()
        }.getOrDefault(emptyList())
    } else {
        buildList {
            var start = result.resultText.indexOf(result.query)
            while (start >= 0) {
                add(start until (start + result.query.length))
                start = result.resultText.indexOf(result.query, start + result.query.length)
            }
        }
    }
    ranges.forEach { range ->
        val start = range.first
        val end = range.last + 1
        if (start in 0 until end && end <= result.resultText.length) {
            addStyle(
                style = SpanStyle(
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (useUnderline) TextDecoration.Underline else null,
                ),
                start = start,
                end = end,
            )
        }
    }
}

@Composable
private fun SearchCenteredProgress(color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = color,
            strokeWidth = 2.dp,
        )
    }
}

@Composable
private fun SearchEmptyState(text: String, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = color.copy(alpha = 0.72f),
            fontSize = 15.sp,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}
