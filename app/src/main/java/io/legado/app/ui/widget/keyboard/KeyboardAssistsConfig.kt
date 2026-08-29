package io.legado.app.ui.widget.keyboard

import android.os.Bundle
import android.view.View
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFormDensity
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.ui.widget.dialog.ngDialogMaxHeight
import io.legado.app.utils.putPrefInt
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** 共享辅助按键配置；保留增删改、行数与拖动排序，界面统一使用 Compose。 */
class KeyboardAssistsConfig(
    private val callBack: CallBack,
) : BaseComposeDialogFragment() {

    private var items by mutableStateOf<List<KeyboardAssist>>(emptyList())
    private var rowCount by mutableStateOf(AppConfig.showBoardLine)
    private var editing by mutableStateOf<KeyboardAssist?>(null)
    private var editorVisible by mutableStateOf(false)
    private var rowCountVisible by mutableStateOf(false)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(height = ngDialogMaxHeight(0.9f))
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        observeItems()
        (view as ComposeView).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NgAppTheme(updateSystemBars = false) {
                    KeyboardAssistsConfigContent(
                        items = items,
                        rowCount = rowCount,
                        onRowCountClick = { rowCountVisible = true },
                        onAdd = {
                            editing = null
                            editorVisible = true
                        },
                        onEdit = {
                            editing = it
                            editorVisible = true
                        },
                        onDelete = ::delete,
                        onReorder = ::persistOrder,
                    )
                    if (editorVisible) {
                        KeyboardAssistEditorDialog(
                            item = editing,
                            onDismiss = { editorVisible = false },
                            onSave = { key, value ->
                                editorVisible = false
                                save(editing, key, value)
                            },
                        )
                    }
                    if (rowCountVisible) {
                        KeyboardAssistRowCountDialog(
                            selected = rowCount,
                            onDismiss = { rowCountVisible = false },
                            onSelected = { count ->
                                rowCountVisible = false
                                rowCount = count
                                putPrefInt(PreferKey.showBoardLine, count)
                                callBack.requestLayout()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun observeItems() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.keyboardAssistsDao.flowAll
                .catch {
                    AppLog.put("辅助按键配置获取数据失败\n${it.localizedMessage}", it)
                }
                .flowOn(IO)
                .collect { items = it }
        }
    }

    private fun save(current: KeyboardAssist?, key: String, value: String) {
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            val updated = KeyboardAssist(
                type = current?.type ?: 0,
                key = key,
                value = value,
                serialNo = current?.serialNo ?: (appDb.keyboardAssistsDao.maxSerialNo + 1),
            )
            current?.let { appDb.keyboardAssistsDao.delete(it) }
            appDb.keyboardAssistsDao.insert(updated)
        }
    }

    private fun delete(item: KeyboardAssist) {
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            appDb.keyboardAssistsDao.delete(item)
        }
    }

    private fun persistOrder(ordered: List<KeyboardAssist>) {
        viewLifecycleOwner.lifecycleScope.launch(IO) {
            val updated = ordered.mapIndexed { index, item ->
                item.copy(serialNo = index + 1)
            }
            appDb.keyboardAssistsDao.update(*updated.toTypedArray())
        }
    }

    interface CallBack {
        fun requestLayout()
    }
}

@Composable
private fun KeyboardAssistsConfigContent(
    items: List<KeyboardAssist>,
    rowCount: Int,
    onRowCountClick: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (KeyboardAssist) -> Unit,
    onDelete: (KeyboardAssist) -> Unit,
    onReorder: (List<KeyboardAssist>) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(NgTheme.colors.dialogContainer),
        shape = RoundedCornerShape(NgTheme.shapes.dialogDp.dp),
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.assists_key_config),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 20.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.show_line_number, rowCount),
                        modifier = Modifier.clickable(onClick = onRowCountClick),
                        color = Color(NgTheme.colors.primary),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                }
                IconButton(onClick = onAdd) {
                    Icon(
                        painter = painterResource(R.drawable.ic_add),
                        contentDescription = stringResource(R.string.add),
                        tint = Color(NgTheme.colors.onSurface),
                    )
                }
            }
            val panelShape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .background(Color(NgTheme.colors.inputContainer), panelShape)
                    .border(
                        0.6.dp,
                        Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
                        panelShape,
                    ),
            ) {
                KeyboardAssistReorderList(
                    items = items,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onReorder = onReorder,
                )
            }
        }
    }
}

@Composable
private fun KeyboardAssistReorderList(
    items: List<KeyboardAssist>,
    onEdit: (KeyboardAssist) -> Unit,
    onDelete: (KeyboardAssist) -> Unit,
    onReorder: (List<KeyboardAssist>) -> Unit,
) {
    var ordered by remember(items) { mutableStateOf(items) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (from.index in ordered.indices && to.index in ordered.indices) {
            ordered = ordered.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
    ) {
        items(
            items = ordered,
            key = { item -> "${item.type}:${item.key}" },
        ) { item ->
            ReorderableItem(
                state = reorderState,
                key = "${item.type}:${item.key}",
            ) { _ ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clickable { onEdit(item) }
                        .padding(start = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_drag_handle),
                        contentDescription = stringResource(R.string.group_drag_sort),
                        modifier = Modifier
                            .size(40.dp)
                            .draggableHandle(
                                onDragStopped = { onReorder(ordered) },
                            )
                            .padding(9.dp),
                        tint = Color(NgTheme.colors.onSurfaceVariant),
                    )
                    Text(
                        text = item.key,
                        modifier = Modifier.weight(1f),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onDelete(item) }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_clear),
                            contentDescription = stringResource(R.string.delete),
                            tint = Color(NgTheme.colors.error),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardAssistEditorDialog(
    item: KeyboardAssist?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var key by remember(item) { mutableStateOf(item?.key.orEmpty()) }
    var value by remember(item) { mutableStateOf(item?.value.orEmpty()) }
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = "辅助按键",
            variant = NgDialogVariant.FORM_EDITOR,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
                NgDialogTextActionButton(
                    text = stringResource(R.string.ok),
                    onClick = { onSave(key, value) },
                )
            },
        ) {
            NgFormField(
                label = "key",
                value = key,
                onValueChange = { key = it },
                density = NgFormDensity.COMPACT,
            )
            NgFormField(
                label = "value",
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.padding(top = 10.dp),
                density = NgFormDensity.COMPACT,
            )
        }
    }
}

@Composable
private fun KeyboardAssistRowCountDialog(
    selected: Int,
    onDismiss: () -> Unit,
    onSelected: (Int) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.setting_show_line_number),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
            },
        ) {
            (1..5).forEach { count ->
                TextButton(
                    onClick = { onSelected(count) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.show_line_number, count),
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(
                            if (count == selected) NgTheme.colors.primary
                            else NgTheme.colors.onSurface
                        ),
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
