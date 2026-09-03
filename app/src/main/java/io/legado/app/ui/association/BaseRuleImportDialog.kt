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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
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
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionRow
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgLazyListFastScroller
import io.legado.app.ui.design.components.compose.NgLazyListFastScrollerVariant
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanel
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanelVariant
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.CodeDialog
import io.legado.app.utils.showDialogFragment
import kotlinx.coroutines.launch

private const val RULE_IMPORT_SHEET_HEIGHT_RATIO = 0.88f
private val RuleImportRowMinHeight = 54.dp

enum class RuleImportState(
    val labelRes: Int,
    val variant: NgStatusTagVariant,
) {
    NEW(R.string.book_source_import_state_new, NgStatusTagVariant.SUCCESS),
    UPDATE(R.string.book_source_import_state_update, NgStatusTagVariant.WARNING),
    EXISTING(R.string.book_source_import_state_existing, NgStatusTagVariant.NEUTRAL),
}

private data class RuleImportRowModel(
    val name: String,
    val comment: String?,
    val state: RuleImportState,
)

/**
 * 三类规则导入共用的 Compose 抽屉外壳。
 *
 * 只统一书源管理已经验收的选择、状态、源码查看和底部操作结构；
 * 默认选择、覆盖判定以及最终写库仍由各规则 ViewModel 决定。
 */
abstract class BaseRuleImportDialog<T : Any> : BottomSheetDialogFragment(),
    CodeDialog.Callback {

    protected abstract val titleRes: Int
    protected abstract val allItems: MutableList<T>
    protected abstract val localItems: List<T?>
    protected abstract val selectStatus: MutableList<Boolean>
    protected abstract val errorLiveData: LiveData<String>
    protected abstract val successLiveData: LiveData<Int>

    protected abstract fun importSource(source: String)
    protected abstract fun importSelected(onFinally: () -> Unit)
    protected abstract fun itemName(item: T): String
    protected open fun itemComment(item: T): String? = null
    protected abstract fun itemState(item: T, localItem: T?): RuleImportState
    protected abstract fun serialize(item: T): String
    protected abstract fun deserialize(text: String): T?

    protected open val supportsGroupConfig: Boolean = false
    protected open fun initialGroupName(): String? = null
    protected open fun initialAddToExistingGroup(): Boolean = false
    protected open suspend fun loadGroupSuggestions(): List<String> = emptyList()
    protected open fun applyGroup(name: String?, addToExisting: Boolean) = Unit

    private var rows by mutableStateOf<List<RuleImportRowModel>>(emptyList())
    private var selectedIndices by mutableStateOf<Set<Int>>(emptySet())
    private var loading by mutableStateOf(true)
    private var importing by mutableStateOf(false)
    private var error by mutableStateOf<String?>(null)
    private var groupName by mutableStateOf<String?>(null)
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
        groupName = initialGroupName()
        addToExistingGroup = initialAddToExistingGroup()
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                RuleImportDrawer(
                    title = stringResource(titleRes),
                    rows = rows,
                    selectedIndices = selectedIndices,
                    loading = loading,
                    importing = importing,
                    error = error,
                    supportsGroupConfig = supportsGroupConfig,
                    groupName = groupName,
                    addToExistingGroup = addToExistingGroup,
                    groupSuggestions = groupSuggestions,
                    onToggle = ::toggleSelection,
                    onToggleAll = ::toggleAll,
                    onViewSource = ::viewSource,
                    onApplyGroup = { name, add ->
                        groupName = name
                        addToExistingGroup = add
                        applyGroup(name, add)
                    },
                    onDismiss = { dismissAllowingStateLoss() },
                    onImport = ::startImportSelected,
                )
            }
        }
        observeImportState()
        if (supportsGroupConfig) {
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { loadGroupSuggestions() }
                    .onSuccess { groupSuggestions = it }
                    .onFailure {
                        AppLog.put("规则导入获取分组失败\n${it.localizedMessage}", it)
                    }
            }
        }
        val source = arguments?.getString(ARG_SOURCE)
        when {
            source.isNullOrEmpty() -> dismissAllowingStateLoss()
            successLiveData.value == null && errorLiveData.value == null -> importSource(source)
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
            height = (resources.displayMetrics.heightPixels * RULE_IMPORT_SHEET_HEIGHT_RATIO).toInt()
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

    private fun observeImportState() {
        errorLiveData.observe(viewLifecycleOwner) {
            loading = false
            error = it
        }
        successLiveData.observe(viewLifecycleOwner) { count ->
            loading = false
            if (count > 0) {
                error = null
                refreshRows()
                applySelection(
                    selectStatus.indices.filterTo(linkedSetOf()) { index ->
                        selectStatus[index]
                    }
                )
            } else {
                error = getString(R.string.wrong_format)
            }
        }
    }

    private fun refreshRows() {
        rows = allItems.mapIndexed { index, item ->
            RuleImportRowModel(
                name = itemName(item),
                comment = itemComment(item)?.takeIf(String::isNotBlank),
                state = itemState(item, localItems.getOrNull(index)),
            )
        }
    }

    private fun toggleSelection(index: Int) {
        if (index !in rows.indices) return
        applySelection(selectedIndices.toMutableSet().apply {
            if (!add(index)) remove(index)
        })
    }

    private fun toggleAll() {
        applySelection(
            if (rows.isNotEmpty() && selectedIndices.size == rows.size) {
                emptySet()
            } else {
                rows.indices.toSet()
            }
        )
    }

    private fun applySelection(indices: Set<Int>) {
        selectedIndices = indices.filterTo(linkedSetOf()) { it in rows.indices }
        selectStatus.indices.forEach { index ->
            selectStatus[index] = index in selectedIndices
        }
    }

    private fun viewSource(index: Int) {
        val item = allItems.getOrNull(index) ?: return
        showDialogFragment(
            CodeDialog(
                serialize(item),
                disableEdit = false,
                requestId = index.toString(),
            )
        )
    }

    private fun startImportSelected() {
        if (importing || selectedIndices.isEmpty()) return
        updateImporting(true)
        importSelected {
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
        if (index !in allItems.indices) return
        deserialize(code)?.let { item ->
            allItems[index] = item
            refreshRows()
        }
    }

    protected companion object {
        const val ARG_SOURCE = "source"
        const val ARG_FINISH_ON_DISMISS = "finishOnDismiss"
    }
}

private enum class RuleImportDrawerScreen {
    MAIN,
    GROUP,
}

@Composable
private fun RuleImportDrawer(
    title: String,
    rows: List<RuleImportRowModel>,
    selectedIndices: Set<Int>,
    loading: Boolean,
    importing: Boolean,
    error: String?,
    supportsGroupConfig: Boolean,
    groupName: String?,
    addToExistingGroup: Boolean,
    groupSuggestions: List<String>,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onViewSource: (Int) -> Unit,
    onApplyGroup: (String?, Boolean) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    var screen by rememberSaveable { mutableStateOf(RuleImportDrawerScreen.MAIN) }
    BackHandler(enabled = screen != RuleImportDrawerScreen.MAIN) {
        screen = RuleImportDrawerScreen.MAIN
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
                RuleImportDrawerScreen.MAIN -> RuleImportMainContent(
                    title = title,
                    rows = rows,
                    selectedIndices = selectedIndices,
                    loading = loading,
                    importing = importing,
                    error = error,
                    supportsGroupConfig = supportsGroupConfig,
                    onGroupConfig = { screen = RuleImportDrawerScreen.GROUP },
                    onToggle = onToggle,
                    onToggleAll = onToggleAll,
                    onViewSource = onViewSource,
                    onDismiss = onDismiss,
                    onImport = onImport,
                )

                RuleImportDrawerScreen.GROUP -> RuleImportGroupContent(
                    initialName = groupName,
                    initialAdd = addToExistingGroup,
                    suggestions = groupSuggestions,
                    onBack = { screen = RuleImportDrawerScreen.MAIN },
                    onApply = { name, add ->
                        onApplyGroup(name, add)
                        screen = RuleImportDrawerScreen.MAIN
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.RuleImportMainContent(
    title: String,
    rows: List<RuleImportRowModel>,
    selectedIndices: Set<Int>,
    loading: Boolean,
    importing: Boolean,
    error: String?,
    supportsGroupConfig: Boolean,
    onGroupConfig: () -> Unit,
    onToggle: (Int) -> Unit,
    onToggleAll: () -> Unit,
    onViewSource: (Int) -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    if (supportsGroupConfig) {
        NgLongDrawerHeader(
            title = title,
            actionIconRes = R.drawable.ic_settings,
            actionContentDescription = stringResource(R.string.diy_source_group),
            onActionClick = onGroupConfig,
            centerTitle = true,
        )
    } else {
        NgLongDrawerHeader(title = title, centerTitle = true)
    }
    Spacer(Modifier.height(4.dp))
    RuleImportSummary(
        selectedCount = selectedIndices.size,
        totalCount = rows.size,
        newCount = rows.count { it.state == RuleImportState.NEW },
        updateCount = rows.count { it.state == RuleImportState.UPDATE },
    )
    Spacer(Modifier.height(6.dp))
    RuleImportList(
        rows = rows,
        selectedIndices = selectedIndices,
        loading = loading,
        error = error,
        onToggle = onToggle,
        onViewSource = onViewSource,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
    )
    Spacer(Modifier.height(8.dp))
    RuleImportActions(
        allSelected = rows.isNotEmpty() && selectedIndices.size == rows.size,
        selectedCount = selectedIndices.size,
        importing = importing,
        hasItems = rows.isNotEmpty(),
        onToggleAll = onToggleAll,
        onDismiss = onDismiss,
        onImport = onImport,
    )
}

@Composable
private fun RuleImportSummary(
    selectedCount: Int,
    totalCount: Int,
    newCount: Int,
    updateCount: Int,
) {
    NgManagementDrawerPanel(variant = NgManagementDrawerPanelVariant.COMPACT) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RuleImportSummaryCell(
                text = stringResource(
                    R.string.book_source_import_selected_summary,
                    selectedCount,
                    totalCount,
                ),
                emphasized = true,
                modifier = Modifier.weight(1f),
            )
            RuleImportSummaryDivider()
            RuleImportSummaryCell(
                text = stringResource(R.string.book_source_import_new_summary, newCount),
                modifier = Modifier.weight(1f),
            )
            RuleImportSummaryDivider()
            RuleImportSummaryCell(
                text = stringResource(R.string.book_source_import_update_summary, updateCount),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun RuleImportSummaryCell(
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
private fun RuleImportSummaryDivider() {
    VerticalDivider(
        modifier = Modifier.height(18.dp),
        thickness = 0.6.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.32f),
    )
}

@Composable
private fun RuleImportList(
    rows: List<RuleImportRowModel>,
    selectedIndices: Set<Int>,
    loading: Boolean,
    error: String?,
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
                loading -> RuleImportMessage(showProgress = true)
                error != null -> RuleImportMessage(text = error)
                rows.isEmpty() -> RuleImportMessage(text = stringResource(R.string.empty))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { index, _ -> index },
                    ) { index, row ->
                        RuleImportRow(
                            row = row,
                            selected = index in selectedIndices,
                            onToggle = { onToggle(index) },
                            onViewSource = { onViewSource(index) },
                            showDivider = index != rows.lastIndex,
                        )
                    }
                }
            }
        }
        if (!loading && error == null && rows.isNotEmpty()) {
            NgLazyListFastScroller(
                state = listState,
                itemCount = rows.size,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 2.dp),
                variant = NgLazyListFastScrollerVariant.FLOATING_HANDLE,
            )
        }
    }
}

@Composable
private fun RuleImportMessage(
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
private fun RuleImportRow(
    row: RuleImportRowModel,
    selected: Boolean,
    onToggle: () -> Unit,
    onViewSource: () -> Unit,
    showDivider: Boolean,
) {
    var commentExpanded by rememberSaveable(row.name) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = RuleImportRowMinHeight)
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
                text = row.name,
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
                text = stringResource(row.state.labelRes),
                variant = row.state.variant,
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
        row.comment?.let { comment ->
            Text(
                text = comment,
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
private fun RuleImportActions(
    allSelected: Boolean,
    selectedCount: Int,
    importing: Boolean,
    hasItems: Boolean,
    onToggleAll: () -> Unit,
    onDismiss: () -> Unit,
    onImport: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RuleImportSelectAllAction(
            allSelected = allSelected,
            enabled = hasItems && !importing,
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
private fun RuleImportSelectAllAction(
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
        Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
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
private fun ColumnScope.RuleImportGroupContent(
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
                RuleImportGroupModeRow(
                    title = stringResource(R.string.book_source_import_replace_group),
                    summary = stringResource(R.string.rule_import_replace_group_summary),
                    selected = !addToExisting,
                    onClick = { addToExisting = false },
                )
                NgFormGroupDivider()
                RuleImportGroupModeRow(
                    title = stringResource(R.string.book_source_import_add_group),
                    summary = stringResource(R.string.rule_import_add_group_summary),
                    selected = addToExisting,
                    onClick = { addToExisting = true },
                )
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

@Composable
private fun RuleImportGroupModeRow(
    title: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(68.dp)
            .background(
                if (selected) {
                    Color(NgTheme.colors.selectedContainer).copy(alpha = 0.16f)
                } else {
                    Color.Transparent
                }
            )
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 1.5.dp,
                    color = Color(
                        if (selected) NgTheme.colors.primary else NgTheme.colors.outline
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(NgTheme.colors.primary)),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                color = Color(
                    if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface
                ),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                modifier = Modifier.padding(top = 2.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
