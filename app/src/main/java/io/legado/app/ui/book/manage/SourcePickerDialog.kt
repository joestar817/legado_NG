package io.legado.app.ui.book.manage

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgFilterChipGroupVariant
import io.legado.app.ui.design.components.NgManagementListCardVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFilterChipGroup
import io.legado.app.ui.design.components.compose.NgFilterChipItem
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgManagementLeadingText
import io.legado.app.ui.design.components.compose.NgManagementListCard
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.splitNotBlank
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 书架管理的单目标书源选择抽屉。
 *
 * 这里只替换旧 DialogFragment 的展示层：搜索与分组只筛选可选书源，
 * 点击任一书源仍立即回调原批量换源逻辑。
 */
class SourcePickerDialog : BottomSheetDialogFragment() {

    private var sources by mutableStateOf<List<BookSourcePart>>(emptyList())
    private var sourceGroups by mutableStateOf<List<String>>(emptyList())
    private var sourceFlowJob: Job? = null
    private var sourceGroupFlowJob: Job? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setBackgroundColor(AndroidColor.TRANSPARENT)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSourceGroups()
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                SourcePickerSheetContent(
                    sources = sources,
                    sourceGroups = sourceGroups,
                    onFilterChange = ::loadSources,
                    onSourceClick = ::selectSource,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setBackgroundDrawableResource(R.color.transparent)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply { dimAmount = 0.22f }
            decorView.setPadding(0, 0, 0, 0)
        }
        val sheet = dialog?.findViewById<View>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.setBackgroundColor(AndroidColor.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        sourceFlowJob?.cancel()
        sourceFlowJob = null
        sourceGroupFlowJob?.cancel()
        sourceGroupFlowJob = null
        super.onDismiss(dialog)
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示书源选择抽屉失败 tag:$tag", it) }
    }

    private fun loadSourceGroups() {
        sourceGroupFlowJob?.cancel()
        sourceGroupFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowEnabledGroups()
                .catch {
                    AppLog.put("书源选择界面获取分组失败\n${it.localizedMessage}", it)
                }
                .flowOn(IO)
                .collect { sourceGroups = it }
        }
    }

    private fun loadSources(searchKey: String, selectedGroups: Set<String>) {
        sourceFlowJob?.cancel()
        sourceFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isEmpty() -> appDb.bookSourceDao.flowEnabled()
                else -> appDb.bookSourceDao.flowSearchEnabled(searchKey)
            }.catch {
                AppLog.put("书源选择界面获取书源数据失败\n${it.localizedMessage}", it)
            }.flowOn(IO).collect {
                sources = it.filter { source ->
                    source.matchesAnyGroup(selectedGroups)
                }
            }
        }
    }

    private fun selectSource(sourcePart: BookSourcePart) {
        sourcePart.getBookSource()?.let { source ->
            callback?.sourceOnClick(source)
        }
        dismissAllowingStateLoss()
    }

    private val callback: Callback?
        get() = (parentFragment as? Callback) ?: activity as? Callback

    interface Callback {
        fun sourceOnClick(source: BookSource)
    }

    private companion object {
        const val SHEET_HEIGHT_RATIO = 0.88f
    }
}

@Composable
private fun SourcePickerSheetContent(
    sources: List<BookSourcePart>,
    sourceGroups: List<String>,
    onFilterChange: (String, Set<String>) -> Unit,
    onSourceClick: (BookSourcePart) -> Unit,
) {
    val colors = NgTheme.colors
    val sourceBadgeColor = colorResource(R.color.ng_primary)
    var controlsExpanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedGroups by remember { mutableStateOf<Set<String>>(emptySet()) }
    val listState = rememberLazyGridState()
    LaunchedEffect(sourceGroups) {
        selectedGroups = selectedGroups.intersect(sourceGroups.toSet())
    }
    LaunchedEffect(query, selectedGroups) {
        onFilterChange(query, selectedGroups)
        listState.scrollToItem(0)
    }

    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxSize(),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 12.dp),
        ) {
            NgLongDrawerHeader(
                title = stringResource(R.string.source_picker_title),
                actionIconRes = R.drawable.ic_tts_params_grid,
                actionContentDescription = stringResource(R.string.more),
                actionActive = controlsExpanded || query.isNotBlank() || selectedGroups.isNotEmpty(),
                onActionClick = { controlsExpanded = !controlsExpanded },
            )
            if (controlsExpanded) {
                SourcePickerControls(
                    query = query,
                    sourceGroups = sourceGroups,
                    selectedGroups = selectedGroups,
                    onQueryChange = { query = it },
                    onGroupToggle = { group ->
                        selectedGroups = if (group in selectedGroups) {
                            selectedGroups - group
                        } else {
                            selectedGroups + group
                        }
                    },
                )
            }
            if (sources.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty),
                        color = Color(colors.onSurfaceVariant),
                        fontSize = 15.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(sources, key = { it.bookSourceUrl }) { source ->
                        SourcePickerCard(
                            source = source,
                            badgeColor = sourceBadgeColor,
                            onClick = { onSourceClick(source) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourcePickerControls(
    query: String,
    sourceGroups: List<String>,
    selectedGroups: Set<String>,
    onQueryChange: (String) -> Unit,
    onGroupToggle: (String) -> Unit,
) {
    val colors = NgTheme.colors
    val groupItems = remember(sourceGroups) {
        sourceGroups.map { group ->
            NgFilterChipItem(
                key = group,
                label = group,
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
    ) {
        NgSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.search_book_source),
            containerColor = Color(colors.inputContainer),
        )
        if (groupItems.isNotEmpty()) {
            NgFilterChipGroup(
                items = groupItems,
                selectedKeys = selectedGroups,
                onToggle = onGroupToggle,
                variant = NgFilterChipGroupVariant.TWO_ROW_RAIL,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

private fun BookSourcePart.matchesAnyGroup(selectedGroups: Set<String>): Boolean {
    if (selectedGroups.isEmpty()) return true
    return bookSourceGroup
        ?.splitNotBlank(AppPattern.splitGroupRegex)
        ?.any(selectedGroups::contains) == true
}

@Composable
private fun SourcePickerCard(
    source: BookSourcePart,
    badgeColor: Color,
    onClick: () -> Unit,
) {
    NgManagementListCard(
        title = source.bookSourceName,
        summary = source.bookSourceGroup?.takeIf { it.isNotBlank() },
        variant = NgManagementListCardVariant.COMPACT_GRID,
        trailing = NgManagementTrailing.NONE,
        onClick = onClick,
        leading = {
            NgManagementLeadingText(
                text = source.bookSourceName.firstOrNull()?.toString().orEmpty(),
                contentDescription = null,
                textColor = badgeColor,
                variant = NgManagementListCardVariant.COMPACT_GRID,
            )
        },
    )
}
