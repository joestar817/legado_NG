package io.legado.app.ui.book.group

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookGroup
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionRow
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanel
import io.legado.app.ui.design.components.compose.NgReorderableSwitchRow
import io.legado.app.ui.design.components.compose.NgReorderableSwitchRowDefaults
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private const val GROUP_SHEET_MAX_HEIGHT_RATIO = 0.82f

/** 书籍分组管理 NG 抽屉。管理、编辑和新建均在同一个抽屉内完成。 */
class GroupManageDialog : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_INITIAL_GROUP = "initialGroup"
        private const val ARG_START_CREATE = "startCreate"

        fun forCreate(): GroupManageDialog = GroupManageDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_START_CREATE, true) }
        }

        fun forEdit(group: BookGroup): GroupManageDialog = GroupManageDialog().apply {
            arguments = Bundle().apply { putParcelable(ARG_INITIAL_GROUP, group.copy()) }
        }
    }

    private val viewModel: GroupViewModel by viewModels()
    private val groupsState = mutableStateOf<List<BookGroup>>(emptyList())

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
        @Suppress("DEPRECATION")
        val initialGroup = arguments?.getParcelable<BookGroup>(ARG_INITIAL_GROUP)
        val startCreate = arguments?.getBoolean(ARG_START_CREATE, false) == true
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                GroupManageSheet(
                    groups = groupsState.value,
                    initialGroup = initialGroup,
                    startCreate = startCreate,
                    canAddGroup = { appDb.bookGroupDao.canAddGroup },
                    onOrderChanged = { groups -> viewModel.upGroup(*groups.toTypedArray()) },
                    onShowChanged = { group -> viewModel.upGroup(group) },
                    onAddGroup = { draft, onFinished ->
                        viewModel.addGroup(
                            groupName = draft.groupName,
                            bookSort = draft.bookSort,
                            enableRefresh = draft.enableRefresh,
                            onlyUpdateRead = draft.onlyUpdateRead,
                            finally = onFinished,
                        )
                    },
                    onUpdateGroup = { group, draft, onFinished ->
                        viewModel.upGroup(
                            group.copy(
                                groupName = if (group.isBuiltIn()) {
                                    group.groupName
                                } else {
                                    draft.groupName
                                },
                                bookSort = draft.bookSort,
                                enableRefresh = draft.enableRefresh,
                                onlyUpdateRead = draft.onlyUpdateRead,
                            ),
                            finally = onFinished,
                        )
                    },
                    onDeleteGroup = { group, onFinished ->
                        viewModel.delGroup(group, onFinished)
                    },
                    onGroupLimitReached = {
                        toastOnUi(getString(R.string.book_group_full))
                    },
                    onDismiss = { dismissAllowingStateLoss() },
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appDb.bookGroupDao.flowAll()
                    .catch {
                        AppLog.put(
                            "书籍分组管理抽屉获取分组数据失败\n${it.localizedMessage}",
                            it,
                        )
                    }
                    .flowOn(IO)
                    .conflate()
                    .collect { groupsState.value = it }
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
        val maxSheetHeight = (
            resources.displayMetrics.heightPixels * GROUP_SHEET_MAX_HEIGHT_RATIO
        ).toInt()
        sheet.layoutParams = sheet.layoutParams.apply {
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        BottomSheetBehavior.from(sheet).apply {
            maxHeight = maxSheetHeight
            skipCollapsed = true
            isFitToContents = true
            isDraggable = true
            isDraggableOnNestedScroll = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun show(manager: FragmentManager, tag: String?) {
        runCatching { super.show(manager, tag) }
            .onFailure { AppLog.put("显示分组管理抽屉失败 tag:$tag", it) }
    }
}

private sealed interface GroupDrawerScreen {
    data object Manage : GroupDrawerScreen
    data object Create : GroupDrawerScreen
    data class Edit(val group: BookGroup) : GroupDrawerScreen
}

private data class GroupDraft(
    val groupName: String,
    val bookSort: Int,
    val enableRefresh: Boolean,
    val onlyUpdateRead: Boolean,
)

@Composable
private fun GroupManageSheet(
    groups: List<BookGroup>,
    initialGroup: BookGroup?,
    startCreate: Boolean,
    canAddGroup: () -> Boolean,
    onOrderChanged: (List<BookGroup>) -> Unit,
    onShowChanged: (BookGroup) -> Unit,
    onAddGroup: (GroupDraft, () -> Unit) -> Unit,
    onUpdateGroup: (BookGroup, GroupDraft, () -> Unit) -> Unit,
    onDeleteGroup: (BookGroup, () -> Unit) -> Unit,
    onGroupLimitReached: () -> Unit,
    onDismiss: () -> Unit,
) {
    val directEditorEntry = initialGroup != null || startCreate
    var screen by remember(initialGroup?.groupId, startCreate) {
        mutableStateOf<GroupDrawerScreen>(
            when {
                initialGroup != null -> GroupDrawerScreen.Edit(initialGroup.copy())
                startCreate -> GroupDrawerScreen.Create
                else -> GroupDrawerScreen.Manage
            }
        )
    }
    val maxDrawerHeight = (
        LocalConfiguration.current.screenHeightDp * GROUP_SHEET_MAX_HEIGHT_RATIO
    ).dp
    val leaveEditor = {
        if (directEditorEntry) {
            onDismiss()
        } else {
            screen = GroupDrawerScreen.Manage
        }
    }
    BackHandler(enabled = screen !is GroupDrawerScreen.Manage) {
        leaveEditor()
    }

    NgBottomDrawerSurface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = maxDrawerHeight),
        contentCardStyle = NgDrawerContentCardStyle.ADAPTIVE,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 6.dp, end = 16.dp, bottom = 12.dp),
        ) {
            when (val current = screen) {
                GroupDrawerScreen.Manage -> GroupManageListContent(
                    groups = groups,
                    onCreate = {
                        if (canAddGroup()) {
                            screen = GroupDrawerScreen.Create
                        } else {
                            onGroupLimitReached()
                        }
                    },
                    onEdit = { screen = GroupDrawerScreen.Edit(it.copy()) },
                    onOrderChanged = onOrderChanged,
                    onShowChanged = onShowChanged,
                    onDismiss = onDismiss,
                )

                GroupDrawerScreen.Create -> GroupEditorContent(
                    group = null,
                    onBack = leaveEditor,
                    onSave = { draft ->
                        onAddGroup(draft, leaveEditor)
                    },
                )

                is GroupDrawerScreen.Edit -> GroupEditorContent(
                    group = current.group,
                    onBack = leaveEditor,
                    onSave = { draft ->
                        onUpdateGroup(current.group, draft, leaveEditor)
                    },
                    onDelete = { group ->
                        onDeleteGroup(group, leaveEditor)
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.GroupManageListContent(
    groups: List<BookGroup>,
    onCreate: () -> Unit,
    onEdit: (BookGroup) -> Unit,
    onOrderChanged: (List<BookGroup>) -> Unit,
    onShowChanged: (BookGroup) -> Unit,
    onDismiss: () -> Unit,
) {
    var orderedGroups by remember(groups) { mutableStateOf(groups) }
    val reorderState = rememberNgLazyReorderState(
        onMove = { fromIndex, toIndex ->
            if (fromIndex in orderedGroups.indices && toIndex in orderedGroups.indices) {
                orderedGroups = orderedGroups.toMutableList().apply {
                    add(toIndex, removeAt(fromIndex))
                }
            }
        },
        onFinished = {
            onOrderChanged(
                orderedGroups.mapIndexed { index, group ->
                    group.copy(order = index + 1)
                }
            )
        },
    )
    val dragDescription = stringResource(R.string.group_drag_sort)

    NgLongDrawerHeader(
        title = stringResource(R.string.group_manage),
        actionIconRes = R.drawable.ic_add,
        actionContentDescription = stringResource(R.string.group_create),
        onActionClick = onCreate,
        centerTitle = true,
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
    ) {
        val panelHeight = minOf(
            maxHeight,
            NgReorderableSwitchRowDefaults.contentHeight(orderedGroups.size),
        )
        if (panelHeight > 0.dp) {
            NgManagementDrawerPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(panelHeight),
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = reorderState.listState,
                ) {
                    itemsIndexed(
                        items = orderedGroups,
                        key = { _, group -> group.groupId },
                    ) { index, group ->
                        NgReorderableSwitchRow(
                            title = group.groupName,
                            summary = stringResource(
                                if (group.isBuiltIn()) R.string.group_type_builtin
                                else R.string.group_type_custom
                            ),
                            checked = group.show,
                            onCheckedChange = { checked ->
                                onShowChanged(group.copy(show = checked))
                            },
                            onNavigate = { onEdit(group) },
                            dragHandleModifier = Modifier.ngReorderHandle(
                                state = reorderState,
                                key = group.groupId,
                                enabled = true,
                                contentDescription = dragDescription,
                            ),
                            modifier = Modifier.ngDraggedItem(reorderState, group.groupId),
                            showDivider = index != orderedGroups.lastIndex,
                        )
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    NgFormActionButton(
        text = stringResource(R.string.complete),
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        variant = NgButtonVariant.PRIMARY,
    )
}

@Composable
private fun ColumnScope.GroupEditorContent(
    group: BookGroup?,
    onBack: () -> Unit,
    onSave: (GroupDraft) -> Unit,
    onDelete: ((BookGroup) -> Unit)? = null,
) {
    val editorKey = group?.let { "edit:${it.groupId}" } ?: "create"
    val sortOptions = stringArrayResource(R.array.book_sort).mapIndexed { index, label ->
        NgFormSelectOption(label = label, value = (index - 1).toString())
    }
    val initialBookSort = group?.bookSort?.takeIf { candidate ->
        sortOptions.any { it.value == candidate.toString() }
    } ?: -1
    var groupName by rememberSaveable(editorKey) { mutableStateOf(group?.groupName.orEmpty()) }
    var bookSort by rememberSaveable(editorKey) { mutableStateOf(initialBookSort) }
    var enableRefresh by rememberSaveable(editorKey) {
        mutableStateOf(group?.enableRefresh ?: true)
    }
    var onlyUpdateRead by rememberSaveable(editorKey) {
        mutableStateOf(group?.onlyUpdateRead ?: false)
    }
    var nameError by rememberSaveable(editorKey) { mutableStateOf(false) }
    var confirmingDelete by rememberSaveable(editorKey) { mutableStateOf(false) }
    val builtInGroup = group?.isBuiltIn() == true
    val deleteTarget = group?.takeIf { it.canDelete() && onDelete != null }
    NgLongDrawerHeader(
        title = stringResource(
            if (group == null) R.string.group_create else R.string.group_edit
        ),
        navigationIconRes = R.drawable.ic_arrow_back,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
        centerTitle = true,
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f, fill = false),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "name") {
            NgFormField(
                label = stringResource(R.string.group_name),
                value = groupName,
                onValueChange = {
                    if (!builtInGroup) {
                        groupName = it
                        if (it.isNotBlank()) nameError = false
                    }
                },
                enabled = !builtInGroup,
                isError = nameError,
                supportingText = if (nameError) {
                    stringResource(R.string.group_name_empty)
                } else {
                    null
                },
            )
        }
        item(key = "sort") {
            NgFormGroup(title = stringResource(R.string.sort)) {
                NgFormSelectRow(
                    title = stringResource(R.string.sort),
                    selectedValue = bookSort.toString(),
                    options = sortOptions,
                    onValueChange = { bookSort = it.toIntOrNull() ?: -1 },
                    arrowIcon = painterResource(R.drawable.ic_arrow_drop_down),
                )
            }
        }
        item(key = "updates") {
            NgFormGroup(title = stringResource(R.string.group_update_settings)) {
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.allow_drop_down_refresh),
                    summary = stringResource(R.string.group_allow_refresh_summary),
                    checked = enableRefresh,
                    onCheckedChange = { enableRefresh = it },
                )
                NgFormGroupDivider()
                NgFormSwitchSettingRow(
                    title = stringResource(R.string.only_update_read),
                    summary = stringResource(R.string.ps_only_update_read),
                    checked = onlyUpdateRead,
                    onCheckedChange = { onlyUpdateRead = it },
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    if (confirmingDelete && deleteTarget != null) {
        Text(
            text = stringResource(R.string.sure_del_any, deleteTarget.groupName),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            color = Color(NgTheme.colors.error),
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
        NgFormActionRow {
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = { confirmingDelete = false },
                modifier = Modifier.weight(1f),
            )
            NgFormActionButton(
                text = stringResource(R.string.delete),
                onClick = { onDelete?.invoke(deleteTarget) },
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.DANGER,
            )
        }
    } else {
        NgFormActionRow {
            if (deleteTarget != null) {
                NgFormActionButton(
                    text = stringResource(R.string.delete),
                    onClick = { confirmingDelete = true },
                    modifier = Modifier.weight(1f),
                    variant = NgButtonVariant.DANGER,
                )
            }
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = onBack,
                modifier = Modifier.weight(1f),
            )
            NgFormActionButton(
                text = stringResource(if (group == null) R.string.create else R.string.save),
                onClick = {
                    val normalizedName = if (builtInGroup) {
                        group.groupName
                    } else {
                        groupName.trim()
                    }
                    if (normalizedName.isEmpty()) {
                        nameError = true
                    } else {
                        onSave(
                            GroupDraft(
                                groupName = normalizedName,
                                bookSort = bookSort,
                                enableRefresh = enableRefresh,
                                onlyUpdateRead = onlyUpdateRead,
                            )
                        )
                    }
                },
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.PRIMARY,
            )
        }
    }
}

private fun BookGroup.canDelete(): Boolean = groupId > 0L || groupId == Long.MIN_VALUE

private fun BookGroup.isBuiltIn(): Boolean = when (groupId) {
    BookGroup.IdAll,
    BookGroup.IdLocal,
    BookGroup.IdAudio,
    BookGroup.IdVideo -> true

    else -> false
}
