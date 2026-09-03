package io.legado.app.ui.association

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgFilterChipGroupVariant
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckboxVariant
import io.legado.app.ui.design.components.compose.NgFilterChipGroup
import io.legado.app.ui.design.components.compose.NgFilterChipItem
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionRow
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanel
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanelVariant
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BOOK_SOURCE_IMPORT_SHEET_HEIGHT_RATIO = 0.88f
private val BookSourceImportRowMinHeight = 54.dp

/** 导入书源抽屉，保留原选择、覆盖策略、分组与源码编辑语义。 */
class ImportBookSourceDialog() : BottomSheetDialogFragment(), CodeDialog.Callback {

    constructor(source: String, finishOnDismiss: Boolean = false) : this() {
        arguments = Bundle().apply {
            putString(ARG_SOURCE, source)
            putBoolean(ARG_FINISH_ON_DISMISS, finishOnDismiss)
        }
    }

    private val viewModel by viewModels<ImportBookSourceViewModel>()
    private var sources by mutableStateOf<List<BookSource>>(
        emptyList(),
        referentialEqualityPolicy(),
    )
    private var selectedIndices by mutableStateOf<Set<Int>>(emptySet())
    private var loading by mutableStateOf(true)
    private var importing by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var keepName by mutableStateOf(AppConfig.importKeepName)
    private var keepGroup by mutableStateOf(AppConfig.importKeepGroup)
    private var keepEnable by mutableStateOf(AppConfig.importKeepEnable)
    private var showComment by mutableStateOf(AppConfig.importShowComment)
    private var customGroupName by mutableStateOf<String?>(null)
    private var addToExistingGroup by mutableStateOf(false)
    private var groupSuggestions by mutableStateOf<List<String>>(emptyList())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        BottomSheetDialog(requireContext(), theme)

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
        customGroupName = viewModel.groupName
        addToExistingGroup = viewModel.isAddGroup
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                BookSourceImportDrawer(
                    sources = sources,
                    localSources = viewModel.checkSources,
                    selectedIndices = selectedIndices,
                    newSourceCount = viewModel.newSourceStatus.count { it },
                    updateSourceCount = viewModel.updateSourceStatus.count { it },
                    loading = loading,
                    importing = importing,
                    error = error,
                    keepName = keepName,
                    keepGroup = keepGroup,
                    keepEnable = keepEnable,
                    showComment = showComment,
                    customGroupName = customGroupName,
                    addToExistingGroup = addToExistingGroup,
                    groupSuggestions = groupSuggestions,
                    onToggle = ::toggleSelection,
                    onToggleAll = ::toggleAll,
                    onSelectNew = { toggleMatchingSources(viewModel.newSourceStatus) },
                    onSelectUpdate = { toggleMatchingSources(viewModel.updateSourceStatus) },
                    onViewSource = ::viewSource,
                    onKeepNameChange = {
                        keepName = it
                        putPrefBoolean(PreferKey.importKeepName, it)
                    },
                    onKeepGroupChange = {
                        keepGroup = it
                        putPrefBoolean(PreferKey.importKeepGroup, it)
                    },
                    onKeepEnableChange = {
                        keepEnable = it
                        AppConfig.importKeepEnable = it
                    },
                    onShowCommentChange = {
                        showComment = it
                        AppConfig.importShowComment = it
                    },
                    onApplyGroup = { name, add ->
                        customGroupName = name
                        addToExistingGroup = add
                        viewModel.groupName = name
                        viewModel.isAddGroup = add
                    },
                    onDismiss = { dismissAllowingStateLoss() },
                    onImport = ::importSelected,
                )
            }
        }
        observeImportState()
        loadGroupSuggestions()
        val source = arguments?.getString(ARG_SOURCE)
        when {
            source.isNullOrEmpty() -> dismissAllowingStateLoss()
            viewModel.successLiveData.value == null && viewModel.errorLiveData.value == null -> {
                viewModel.importSource(source)
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
            height = (
                resources.displayMetrics.heightPixels * BOOK_SOURCE_IMPORT_SHEET_HEIGHT_RATIO
            ).toInt()
        }
        BottomSheetBehavior.from(sheet).apply {
            skipCollapsed = true
            isFitToContents = true
            isDraggable = !importing
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (arguments?.getBoolean(ARG_FINISH_ON_DISMISS) == true) activity?.finish()
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示导入书源抽屉失败 tag:$tag", it) }
    }

    private fun observeImportState() {
        viewModel.errorLiveData.observe(viewLifecycleOwner) {
            loading = false
            error = it
        }
        viewModel.successLiveData.observe(viewLifecycleOwner) { count ->
            loading = false
            if (count > 0) {
                error = null
                sources = viewModel.allSources.toList()
                applySelection(
                    viewModel.selectStatus.indices.filterTo(linkedSetOf()) { index ->
                        viewModel.selectStatus[index]
                    }
                )
            } else {
                error = getString(R.string.wrong_format)
            }
        }
    }

    private fun loadGroupSuggestions() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                withContext(IO) { appDb.bookSourceDao.allGroups().toList() }
            }.onSuccess {
                groupSuggestions = it
            }.onFailure {
                AppLog.put("导入书源抽屉获取分组失败\n${it.localizedMessage}", it)
            }
        }
    }

    private fun toggleSelection(index: Int) {
        if (index !in sources.indices) return
        applySelection(selectedIndices.toMutableSet().apply {
            if (!add(index)) remove(index)
        })
    }

    private fun toggleAll() {
        applySelection(
            if (sources.isNotEmpty() && selectedIndices.size == sources.size) {
                emptySet()
            } else {
                sources.indices.toSet()
            }
        )
    }

    private fun toggleMatchingSources(statuses: List<Boolean>) {
        val matchingIndices = statuses.indices.filter {
            it in sources.indices && statuses[it]
        }
        if (matchingIndices.isEmpty()) return
        val shouldSelect = matchingIndices.any { it !in selectedIndices }
        applySelection(selectedIndices.toMutableSet().apply {
            matchingIndices.forEach { index ->
                if (shouldSelect) add(index) else remove(index)
            }
        })
    }

    private fun applySelection(indices: Set<Int>) {
        selectedIndices = indices.filterTo(linkedSetOf()) { it in sources.indices }
        viewModel.selectStatus.indices.forEach { index ->
            viewModel.selectStatus[index] = index in selectedIndices
        }
    }

    private fun viewSource(index: Int) {
        val source = sources.getOrNull(index) ?: return
        showDialogFragment(
            CodeDialog(
                GSON.toJson(source),
                disableEdit = false,
                requestId = index.toString(),
            )
        )
    }

    private fun importSelected() {
        if (importing || selectedIndices.isEmpty()) return
        updateImporting(true)
        viewModel.importSelect {
            updateImporting(false)
            dismissAllowingStateLoss()
        }
    }

    private fun updateImporting(value: Boolean) {
        importing = value
        isCancelable = !value
        (dialog as? BottomSheetDialog)?.apply {
            setCanceledOnTouchOutside(!value)
            behavior.isDraggable = !value
        }
    }

    override fun onCodeSave(code: String, requestId: String?) {
        val index = requestId?.toIntOrNull() ?: return
        if (index !in sources.indices) return
        GSON.fromJsonObject<BookSource>(code).getOrNull()?.let { source ->
            viewModel.allSources[index] = source
            sources = sources.toMutableList().apply { set(index, source) }
        }
    }

    private companion object {
        const val ARG_SOURCE = "source"
        const val ARG_FINISH_ON_DISMISS = "finishOnDismiss"
    }
}

private enum class BookSourceImportDrawerScreen {
    MAIN,
    SETTINGS,
    GROUP,
}

@Composable
private fun BookSourceImportDrawer(
    sources: List<BookSource>,
    localSources: List<BookSourcePart?>,
    selectedIndices: Set<Int>,
    newSourceCount: Int,
    updateSourceCount: Int,
    loading: Boolean,
    importing: Boolean,
    error: String?,
    keepName: Boolean,
    keepGroup: Boolean,
    keepEnable: Boolean,
    showComment: Boolean,
    customGroupName: String?,
    addToExistingGroup: Boolean,
    groupSuggestions: List<String>,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onSelectNew: () -> Unit,
    onSelectUpdate: () -> Unit,
    onViewSource: (Int) -> Unit,
    onKeepNameChange: (Boolean) -> Unit,
    onKeepGroupChange: (Boolean) -> Unit,
    onKeepEnableChange: (Boolean) -> Unit,
    onShowCommentChange: (Boolean) -> Unit,
    onApplyGroup: (String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    var screen by rememberSaveable {
        mutableStateOf(BookSourceImportDrawerScreen.MAIN)
    }
    BackHandler(enabled = screen != BookSourceImportDrawerScreen.MAIN) {
        screen = when (screen) {
            BookSourceImportDrawerScreen.GROUP -> BookSourceImportDrawerScreen.SETTINGS
            else -> BookSourceImportDrawerScreen.MAIN
        }
    }

    NgBottomDrawerSurface(
        modifier = Modifier.fillMaxSize(),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
        ) {
            when (screen) {
                BookSourceImportDrawerScreen.MAIN -> BookSourceImportMainContent(
                    sources = sources,
                    localSources = localSources,
                    selectedIndices = selectedIndices,
                    newSourceCount = newSourceCount,
                    updateSourceCount = updateSourceCount,
                    loading = loading,
                    importing = importing,
                    error = error,
                    showComment = showComment,
                    onSettings = { screen = BookSourceImportDrawerScreen.SETTINGS },
                    onToggle = onToggle,
                    onToggleAll = onToggleAll,
                    onViewSource = onViewSource,
                    onDismiss = onDismiss,
                    onImport = onImport,
                )

                BookSourceImportDrawerScreen.SETTINGS -> BookSourceImportSettingsContent(
                    newSourceCount = newSourceCount,
                    updateSourceCount = updateSourceCount,
                    keepName = keepName,
                    keepGroup = keepGroup,
                    keepEnable = keepEnable,
                    showComment = showComment,
                    customGroupName = customGroupName,
                    onBack = { screen = BookSourceImportDrawerScreen.MAIN },
                    onSelectNew = {
                        onSelectNew()
                        screen = BookSourceImportDrawerScreen.MAIN
                    },
                    onSelectUpdate = {
                        onSelectUpdate()
                        screen = BookSourceImportDrawerScreen.MAIN
                    },
                    onGroup = { screen = BookSourceImportDrawerScreen.GROUP },
                    onKeepNameChange = onKeepNameChange,
                    onKeepGroupChange = onKeepGroupChange,
                    onKeepEnableChange = onKeepEnableChange,
                    onShowCommentChange = onShowCommentChange,
                    onDone = { screen = BookSourceImportDrawerScreen.MAIN },
                )

                BookSourceImportDrawerScreen.GROUP -> BookSourceImportGroupContent(
                    initialName = customGroupName,
                    initialAdd = addToExistingGroup,
                    suggestions = groupSuggestions,
                    onBack = { screen = BookSourceImportDrawerScreen.SETTINGS },
                    onApply = { name, add ->
                        onApplyGroup(name, add)
                        screen = BookSourceImportDrawerScreen.SETTINGS
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.BookSourceImportMainContent(
    sources: List<BookSource>,
    localSources: List<BookSourcePart?>,
    selectedIndices: Set<Int>,
    newSourceCount: Int,
    updateSourceCount: Int,
    loading: Boolean,
    importing: Boolean,
    error: String?,
    showComment: Boolean,
    onSettings: () -> Unit,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onViewSource: (Int) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    NgLongDrawerHeader(
        title = stringResource(R.string.import_book_source),
        actionIconRes = R.drawable.ic_settings,
        actionContentDescription = stringResource(R.string.book_source_import_settings),
        onActionClick = onSettings,
        centerTitle = true,
    )
    Spacer(Modifier.height(4.dp))
    BookSourceImportSummary(
        selectedCount = selectedIndices.size,
        totalCount = sources.size,
        newSourceCount = newSourceCount,
        updateSourceCount = updateSourceCount,
    )
    Spacer(Modifier.height(6.dp))
    BookSourceImportList(
        sources = sources,
        localSources = localSources,
        selectedIndices = selectedIndices,
        loading = loading,
        error = error,
        showComment = showComment,
        onToggle = onToggle,
        onViewSource = onViewSource,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    )
    Spacer(Modifier.height(8.dp))
    BookSourceImportActions(
        allSelected = sources.isNotEmpty() && selectedIndices.size == sources.size,
        selectedCount = selectedIndices.size,
        importing = importing,
        hasSources = sources.isNotEmpty(),
        onToggleAll = onToggleAll,
        onDismiss = onDismiss,
        onImport = onImport,
    )
}

@Composable
private fun BookSourceImportSummary(
    selectedCount: Int,
    totalCount: Int,
    newSourceCount: Int,
    updateSourceCount: Int,
) {
    NgManagementDrawerPanel(
        variant = NgManagementDrawerPanelVariant.COMPACT,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookSourceImportSummaryCell(
                text = stringResource(
                    R.string.book_source_import_selected_summary,
                    selectedCount,
                    totalCount,
                ),
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
            BookSourceImportSummaryDivider()
            BookSourceImportSummaryCell(
                text = stringResource(
                    R.string.book_source_import_new_summary,
                    newSourceCount,
                ),
                modifier = Modifier.weight(1f),
            )
            BookSourceImportSummaryDivider()
            BookSourceImportSummaryCell(
                text = stringResource(
                    R.string.book_source_import_update_summary,
                    updateSourceCount,
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun BookSourceImportSummaryCell(
    text: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color(
                if (emphasized) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant
            ),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = if (emphasized) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookSourceImportSummaryDivider() {
    VerticalDivider(
        modifier = Modifier.height(18.dp),
        thickness = 0.6.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.32f),
    )
}

@Composable
private fun BookSourceImportList(
    sources: List<BookSource>,
    localSources: List<BookSourcePart?>,
    selectedIndices: Set<Int>,
    loading: Boolean,
    error: String?,
    showComment: Boolean,
    onToggle: (Int) -> Unit,
    onViewSource: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    Box(modifier = modifier) {
        NgManagementDrawerPanel(
            modifier = Modifier.fillMaxSize(),
            variant = NgManagementDrawerPanelVariant.COMPACT,
        ) {
            when {
                loading -> BookSourceImportMessage(showProgress = true)
                error != null -> BookSourceImportMessage(text = error)
                sources.isEmpty() -> BookSourceImportMessage(
                    text = stringResource(R.string.empty)
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = sources,
                        key = { index, _ -> index },
                    ) { index, source ->
                        BookSourceImportRow(
                            source = source,
                            localSource = localSources.getOrNull(index),
                            selected = index in selectedIndices,
                            showComment = showComment,
                            onToggle = { onToggle(index) },
                            onViewSource = { onViewSource(index) },
                            showDivider = index != sources.lastIndex,
                        )
                    }
                }
            }
        }
        if (!loading && error == null && sources.isNotEmpty()) {
            NgLazyListFastScroller(
                state = listState,
                itemCount = sources.size,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                variant = NgLazyListFastScrollerVariant.FLOATING_HANDLE,
            )
        }
    }
}

@Composable
private fun BookSourceImportMessage(
    text: String? = null,
    showProgress: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(NgTheme.colors.primary),
                    strokeWidth = 2.5.dp,
                )
                Spacer(Modifier.height(12.dp))
            }
            text?.let {
                Text(
                    text = it,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
private fun BookSourceImportRow(
    source: BookSource,
    localSource: BookSourcePart?,
    selected: Boolean,
    showComment: Boolean,
    onToggle: () -> Unit,
    onViewSource: () -> Unit,
    showDivider: Boolean,
) {
    var commentExpanded by rememberSaveable(source.bookSourceUrl) { mutableStateOf(false) }
    val state = when {
        localSource == null -> BookSourceImportState.NEW
        source.lastUpdateTime > localSource.lastUpdateTime -> BookSourceImportState.UPDATE
        else -> BookSourceImportState.EXISTING
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = BookSourceImportRowMinHeight)
                .clickable(role = Role.Checkbox, onClick = onToggle)
                .padding(start = 8.dp, end = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                variant = NgFileSelectionCheckboxVariant.COMPACT,
            )
            Text(
                text = source.bookSourceName,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 8.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NgStatusTag(
                text = stringResource(state.labelRes),
                variant = state.variant,
                style = NgStatusTagStyle.INLINE,
            )
            TextButton(
                onClick = onViewSource,
                modifier = Modifier.height(40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.book_source_import_view),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
        }
        if (showComment && !source.bookSourceComment.isNullOrBlank()) {
            Text(
                text = source.bookSourceComment.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { commentExpanded = !commentExpanded }
                    .padding(start = 48.dp, end = 12.dp, bottom = 8.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                maxLines = if (commentExpanded) 39 else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 48.dp, end = 10.dp),
                thickness = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
        }
    }
}

@Composable
private fun BookSourceImportActions(
    allSelected: Boolean,
    selectedCount: Int,
    importing: Boolean,
    hasSources: Boolean,
    onToggleAll: () -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookSourceImportSelectAllAction(
            allSelected = allSelected,
            enabled = hasSources && !importing,
            onClick = onToggleAll,
        )
        NgFormActionButton(
            text = stringResource(R.string.cancel),
            onClick = onDismiss,
            enabled = !importing,
            modifier = Modifier.weight(1f),
        )
        NgFormActionButton(
            text = if (importing) {
                stringResource(R.string.importing)
            } else {
                stringResource(R.string.book_source_import_action)
            },
            onClick = onImport,
            modifier = Modifier.weight(1f),
            enabled = selectedCount > 0 && !importing,
            variant = NgButtonVariant.PRIMARY,
        )
    }
}

@Composable
private fun BookSourceImportSelectAllAction(
    allSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = Color(
        if (allSelected) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant
    )
    Row(
        modifier = Modifier
            .width(112.dp)
            .height(36.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_select_all),
                contentDescription = null,
                modifier = Modifier.size(19.dp),
                tint = contentColor,
            )
        }
        Text(
            text = stringResource(
                if (allSelected) {
                    R.string.book_source_import_deselect_all
                } else {
                    R.string.book_source_import_select_all
                }
            ),
            color = contentColor,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ColumnScope.BookSourceImportSettingsContent(
    newSourceCount: Int,
    updateSourceCount: Int,
    keepName: Boolean,
    keepGroup: Boolean,
    keepEnable: Boolean,
    showComment: Boolean,
    customGroupName: String?,
    onBack: () -> Unit,
    onSelectNew: () -> Unit,
    onSelectUpdate: () -> Unit,
    onGroup: () -> Unit,
    onKeepNameChange: (Boolean) -> Unit,
    onKeepGroupChange: (Boolean) -> Unit,
    onKeepEnableChange: (Boolean) -> Unit,
    onShowCommentChange: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    NgLongDrawerHeader(
        title = stringResource(R.string.book_source_import_settings),
        navigationIconRes = R.drawable.ic_arrow_back,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
        centerTitle = true,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            NgFormGroup(title = stringResource(R.string.book_source_import_selection_scope)) {
                BookSourceImportSettingsActionRow(
                    title = stringResource(R.string.select_new_source),
                    trailingText = stringResource(
                        R.string.book_source_import_source_count,
                        newSourceCount,
                    ),
                    enabled = newSourceCount > 0,
                    onClick = onSelectNew,
                )
                NgFormGroupDivider()
                BookSourceImportSettingsActionRow(
                    title = stringResource(R.string.select_update_source),
                    trailingText = stringResource(
                        R.string.book_source_import_source_count,
                        updateSourceCount,
                    ),
                    enabled = updateSourceCount > 0,
                    onClick = onSelectUpdate,
                )
            }
        }
        item {
            NgFormGroup(title = stringResource(R.string.book_source_import_group_section)) {
                BookSourceImportSettingsActionRow(
                    title = stringResource(R.string.diy_source_group),
                    trailingText = customGroupName?.takeIf(String::isNotBlank)
                        ?: stringResource(R.string.book_source_import_group_unset),
                    onClick = onGroup,
                )
            }
        }
        item {
            NgFormGroup(title = stringResource(R.string.book_source_import_keep_section)) {
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.keep_original_name),
                    checked = keepName,
                    onCheckedChange = onKeepNameChange,
                )
                NgFormGroupDivider()
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.keep_group),
                    checked = keepGroup,
                    onCheckedChange = onKeepGroupChange,
                )
                NgFormGroupDivider()
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.keep_enable),
                    checked = keepEnable,
                    onCheckedChange = onKeepEnableChange,
                )
                NgFormGroupDivider()
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.show_source_comment),
                    checked = showComment,
                    onCheckedChange = onShowCommentChange,
                )
            }
        }
    }
    NgFormActionButton(
        text = stringResource(R.string.book_source_import_done),
        onClick = onDone,
        modifier = Modifier.fillMaxWidth(),
        variant = NgButtonVariant.PRIMARY,
    )
}

@Composable
private fun BookSourceImportSettingsActionRow(
    title: String,
    trailingText: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.45f)
            .padding(start = 14.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = trailingText,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
            lineHeight = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right_20),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = Color(NgTheme.colors.onSurfaceVariant),
        )
    }
}

@Composable
private fun ColumnScope.BookSourceImportGroupContent(
    initialName: String?,
    initialAdd: Boolean,
    suggestions: List<String>,
    onBack: () -> Unit,
    onApply: (String?, Boolean) -> Unit,
) {
    var groupName by rememberSaveable(initialName) { mutableStateOf(initialName.orEmpty()) }
    var addToExisting by rememberSaveable(initialName, initialAdd) {
        mutableStateOf(initialAdd)
    }
    val suggestionItems = remember(suggestions) {
        suggestions.map { NgFilterChipItem(key = it, label = it) }
    }
    NgLongDrawerHeader(
        title = stringResource(R.string.diy_source_group),
        navigationIconRes = R.drawable.ic_arrow_back,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
        centerTitle = true,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        contentPadding = PaddingValues(top = 8.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                NgFormField(
                    label = stringResource(R.string.group_name),
                    value = groupName,
                    onValueChange = { groupName = it },
                    placeholder = stringResource(R.string.book_source_import_group_unset),
                )
                if (suggestionItems.isNotEmpty()) {
                    NgFilterChipGroup(
                        items = suggestionItems,
                        selectedKeys = groupName.takeIf(suggestions::contains)
                            ?.let(::setOf)
                            .orEmpty(),
                        onToggle = { groupName = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        variant = NgFilterChipGroupVariant.TWO_ROW_RAIL,
                    )
                }
            }
        }
        item {
            NgFormGroup(title = stringResource(R.string.book_source_import_group_mode)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    NgFlatActionRail(
                        items = listOf(
                            NgFlatActionRailItem(
                                label = stringResource(
                                    R.string.book_source_import_replace_group
                                ),
                                emphasized = !addToExisting,
                            ),
                            NgFlatActionRailItem(
                                label = stringResource(R.string.book_source_import_add_group),
                                emphasized = addToExisting,
                            ),
                        ),
                        onItemClick = { index -> addToExisting = index == 1 },
                        variant = NgFlatActionRailVariant.TEXT_MODE_PICKER,
                    )
                    Text(
                        text = stringResource(
                            if (addToExisting) {
                                R.string.book_source_import_add_group_summary
                            } else {
                                R.string.book_source_import_replace_group_summary
                            }
                        ),
                        modifier = Modifier.padding(start = 4.dp, top = 10.dp, end = 4.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                    )
                }
            }
        }
    }
    NgFormActionRow {
        NgFormActionButton(
            text = stringResource(R.string.cancel),
            onClick = onBack,
            modifier = Modifier.weight(1f),
        )
        NgFormActionButton(
            text = stringResource(R.string.book_source_import_apply),
            onClick = {
                onApply(groupName.trim().takeIf(String::isNotBlank), addToExisting)
            },
            modifier = Modifier.weight(1f),
            variant = NgButtonVariant.PRIMARY,
        )
    }
}

private enum class BookSourceImportState(
    val labelRes: Int,
    val variant: NgStatusTagVariant,
) {
    NEW(R.string.book_source_import_state_new, NgStatusTagVariant.SUCCESS),
    UPDATE(R.string.book_source_import_state_update, NgStatusTagVariant.WARNING),
    EXISTING(R.string.book_source_import_state_existing, NgStatusTagVariant.NEUTRAL),
}
