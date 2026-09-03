package io.legado.app.ui.book.source.manage

import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionRow
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgLongDrawerHeader
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanel
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

private const val SOURCE_GROUP_SHEET_MAX_HEIGHT_RATIO = 0.82f
private val SourceGroupRowHeight = 56.dp

/** 书源分组管理抽屉，只承载新建、编辑和删除。 */
class GroupManageDialog : BottomSheetDialogFragment() {

    private val viewModel: BookSourceViewModel by activityViewModels()
    private val groupsState = mutableStateOf<List<String>>(emptyList())

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
        (view as ComposeView).setContent {
            NgAppTheme(updateSystemBars = false) {
                BookSourceGroupManageSheet(
                    groups = groupsState.value,
                    onCreate = viewModel::addGroup,
                    onRename = viewModel::upGroup,
                    onDelete = viewModel::delGroup,
                )
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appDb.bookSourceDao.flowGroups()
                    .catch {
                        AppLog.put(
                            "书源分组管理抽屉获取分组数据失败\n${it.localizedMessage}",
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
            resources.displayMetrics.heightPixels * SOURCE_GROUP_SHEET_MAX_HEIGHT_RATIO
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
            .onFailure { AppLog.put("显示书源分组管理抽屉失败 tag:$tag", it) }
    }
}

private sealed interface SourceGroupDrawerScreen {
    data object Manage : SourceGroupDrawerScreen
    data object Create : SourceGroupDrawerScreen
    data class Edit(val group: String) : SourceGroupDrawerScreen
}

@Composable
private fun BookSourceGroupManageSheet(
    groups: List<String>,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var screen by remember { mutableStateOf<SourceGroupDrawerScreen>(SourceGroupDrawerScreen.Manage) }
    val maxDrawerHeight = (
        LocalConfiguration.current.screenHeightDp * SOURCE_GROUP_SHEET_MAX_HEIGHT_RATIO
    ).dp
    val leaveEditor = { screen = SourceGroupDrawerScreen.Manage }
    BackHandler(enabled = screen !is SourceGroupDrawerScreen.Manage) { leaveEditor() }

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
                SourceGroupDrawerScreen.Manage -> SourceGroupManageContent(
                    groups = groups,
                    onCreate = { screen = SourceGroupDrawerScreen.Create },
                    onEdit = { screen = SourceGroupDrawerScreen.Edit(it) },
                    onDelete = onDelete,
                )

                SourceGroupDrawerScreen.Create -> SourceGroupEditorContent(
                    group = null,
                    onBack = leaveEditor,
                    onSave = { groupName ->
                        onCreate(groupName)
                        leaveEditor()
                    },
                )

                is SourceGroupDrawerScreen.Edit -> SourceGroupEditorContent(
                    group = current.group,
                    onBack = leaveEditor,
                    onSave = { groupName ->
                        onRename(current.group, groupName)
                        leaveEditor()
                    },
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.SourceGroupManageContent(
    groups: List<String>,
    onCreate: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var deleteTarget by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(groups, deleteTarget) {
        if (deleteTarget != null && deleteTarget !in groups) deleteTarget = null
    }
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
        val rowCount = groups.size.coerceAtLeast(1)
        val panelHeight = minOf(maxHeight, SourceGroupRowHeight * rowCount.toFloat())
        NgManagementDrawerPanel(
            modifier = Modifier
                .fillMaxWidth()
                .height(panelHeight),
        ) {
            if (groups.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.empty),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = groups,
                        key = { _, group -> group },
                    ) { index, group ->
                        SourceGroupManageRow(
                            group = group,
                            onEdit = { onEdit(group) },
                            onDelete = { deleteTarget = group },
                            showDivider = index != groups.lastIndex,
                        )
                    }
                }
            }
        }
    }
    if (deleteTarget != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.sure_del_any, deleteTarget.orEmpty()),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
            color = Color(NgTheme.colors.error),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        NgFormActionRow {
            NgFormActionButton(
                text = stringResource(R.string.cancel),
                onClick = { deleteTarget = null },
                modifier = Modifier.weight(1f),
            )
            NgFormActionButton(
                text = stringResource(R.string.delete),
                onClick = {
                    deleteTarget?.let(onDelete)
                    deleteTarget = null
                },
                modifier = Modifier.weight(1f),
                variant = NgButtonVariant.DANGER,
            )
        }
    }
}

@Composable
private fun SourceGroupManageRow(
    group: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showDivider: Boolean,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(SourceGroupRowHeight)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = group,
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(
                onClick = onEdit,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.edit),
                    color = Color(NgTheme.colors.primary),
                    fontSize = 14.sp,
                )
            }
            TextButton(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = Color(NgTheme.colors.error),
                    fontSize = 14.sp,
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 16.dp),
                thickness = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun ColumnScope.SourceGroupEditorContent(
    group: String?,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    val editorKey = group ?: "create"
    var groupName by rememberSaveable(editorKey) { mutableStateOf(group.orEmpty()) }
    var nameError by rememberSaveable(editorKey) { mutableStateOf(false) }
    NgLongDrawerHeader(
        title = stringResource(if (group == null) R.string.group_create else R.string.group_edit),
        navigationIconRes = R.drawable.ic_arrow_back,
        navigationContentDescription = stringResource(R.string.back),
        onNavigationClick = onBack,
        centerTitle = true,
    )
    Spacer(Modifier.height(8.dp))
    NgFormField(
        label = stringResource(R.string.group_name),
        value = groupName,
        onValueChange = {
            groupName = it
            if (it.isNotBlank()) nameError = false
        },
        isError = nameError,
        supportingText = if (nameError) stringResource(R.string.group_name_empty) else null,
    )
    Spacer(Modifier.height(16.dp))
    NgFormActionRow {
        NgFormActionButton(
            text = stringResource(R.string.cancel),
            onClick = onBack,
            modifier = Modifier.weight(1f),
        )
        NgFormActionButton(
            text = stringResource(if (group == null) R.string.create else R.string.save),
            onClick = {
                val normalizedName = groupName.trim()
                if (normalizedName.isEmpty()) {
                    nameError = true
                } else {
                    onSave(normalizedName)
                }
            },
            modifier = Modifier.weight(1f),
            variant = NgButtonVariant.PRIMARY,
        )
    }
}
