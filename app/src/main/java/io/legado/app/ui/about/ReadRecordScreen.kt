package io.legado.app.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.ReadRecordShow
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.compose.NgBookCover
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDefaultBookCover
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme

internal data class ReadRecordUiItem(
    val record: ReadRecordShow,
    val book: Book?,
    val durationText: String,
    val lastReadText: String,
    val author: String = "",
)

@Composable
internal fun ReadRecordScreen(
    items: List<ReadRecordUiItem>,
    totalReadTime: String,
    recordCount: Int,
    query: String,
    searchExpanded: Boolean,
    sortMode: Int,
    recordEnabled: Boolean,
    deleting: Boolean,
    clearAllDialogVisible: Boolean,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (Int) -> Unit,
    onRecordEnabledChange: (Boolean) -> Unit,
    onClearAllRequest: () -> Unit,
    onClearAllDismiss: () -> Unit,
    onClearAllConfirm: () -> Unit,
    onItemClick: (ReadRecordUiItem) -> Unit,
    onDeleteConfirm: (List<String>, () -> Unit) -> Unit,
) {
    var managing by rememberSaveable { mutableStateOf(false) }
    // Filtering starts a fresh selection; sorting preserves selection by record identity.
    var selectedNames by rememberSaveable(query) { mutableStateOf(arrayListOf<String>()) }
    var deleteNames by rememberSaveable { mutableStateOf<ArrayList<String>?>(null) }
    val visibleSelection = remember(items, selectedNames) {
        val visibleNames = items.mapTo(hashSetOf()) { it.record.bookName }
        selectedNames.filterTo(linkedSetOf()) { it in visibleNames }
    }
    LaunchedEffect(items) {
        // The first frame after recreation is empty while the Activity reloads records.
        if (items.isNotEmpty()) {
            val visibleNames = items.mapTo(hashSetOf()) { it.record.bookName }
            selectedNames = ArrayList(selectedNames.filter { it in visibleNames })
        }
    }
    val exitManagement = {
        managing = false
        selectedNames = arrayListOf()
    }
    BackHandler(enabled = managing && deleteNames == null) { exitManagement() }
    Column(modifier = Modifier.fillMaxSize()) {
        ReadRecordTopBar(
            query = query,
            searchExpanded = searchExpanded,
            sortMode = sortMode,
            recordEnabled = recordEnabled,
            onBack = { if (managing) exitManagement() else onBack() },
            onSearchExpandedChange = onSearchExpandedChange,
            onQueryChange = onQueryChange,
            onSortChange = onSortChange,
            onRecordEnabledChange = onRecordEnabledChange,
            onClearAllRequest = {
                exitManagement()
                onClearAllRequest()
            },
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        ReadRecordPanel(
            items = items,
            totalReadTime = totalReadTime,
            recordCount = recordCount,
            recordEnabled = recordEnabled,
            managing = managing,
            selectedNames = visibleSelection,
            onManageClick = { if (managing) exitManagement() else managing = true },
            onToggleSelection = { name ->
                selectedNames = ArrayList(
                    if (name in selectedNames) selectedNames - name else selectedNames + name,
                )
            },
            onSelectAll = {
                val visibleNames = items.map { it.record.bookName }
                selectedNames = if (selectedNames.containsAll(visibleNames)) arrayListOf()
                else ArrayList(visibleNames)
            },
            onItemClick = onItemClick,
            onDeleteRequest = { deleteNames = ArrayList(visibleSelection) },
            modifier = Modifier
                .weight(1f)
                .navigationBarsPadding()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        )
    }

    if (clearAllDialogVisible) {
        ReadRecordConfirmationDialog(
            message = stringResource(R.string.sure_del),
            onDismiss = onClearAllDismiss,
            onConfirm = onClearAllConfirm,
        )
    }
    deleteNames?.let { names ->
        ReadRecordConfirmationDialog(
            message = stringResource(R.string.read_record_delete_selected_confirm, names.size),
            busy = deleting,
            onDismiss = { if (!deleting) deleteNames = null },
            onConfirm = {
                onDeleteConfirm(names) {
                    deleteNames = null
                    exitManagement()
                }
            },
        )
    }
}

@Composable
private fun ReadRecordTopBar(
    query: String,
    searchExpanded: Boolean,
    sortMode: Int,
    recordEnabled: Boolean,
    onBack: () -> Unit,
    onSearchExpandedChange: (Boolean) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortChange: (Int) -> Unit,
    onRecordEnabledChange: (Boolean) -> Unit,
    onClearAllRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (searchExpanded) {
        NgFloatingSearchToolbar(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.search),
            onBack = onBack,
            modifier = modifier,
        ) {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_baseline_close,
                contentDescription = stringResource(R.string.close),
                onClick = { onSearchExpandedChange(false) },
            )
        }
    } else {
        NgFloatingTitleToolbar(
            title = stringResource(R.string.read_record),
            onBack = onBack,
            modifier = modifier,
        ) {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_search,
                contentDescription = stringResource(R.string.search),
                onClick = { onSearchExpandedChange(true) },
            )
            ReadRecordSortMenu(sortMode = sortMode, onSortChange = onSortChange)
            ReadRecordMoreMenu(
                recordEnabled = recordEnabled,
                onRecordEnabledChange = onRecordEnabledChange,
                onClearAllRequest = onClearAllRequest,
            )
        }
    }
}

@Composable
private fun ReadRecordSortMenu(
    sortMode: Int,
    onSortChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NgFloatingToolbarActionButton(
            iconRes = R.drawable.ic_baseline_sort_24,
            contentDescription = stringResource(R.string.sort),
            onClick = { expanded = true },
        )
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = listOf(
                NgExpandableActionMenuItem(
                    itemId = MENU_SORT_NAME,
                    titleRes = R.string.sort_by_name,
                    iconRes = R.drawable.ic_sort,
                    checked = sortMode == SORT_NAME,
                ),
                NgExpandableActionMenuItem(
                    itemId = MENU_SORT_DURATION,
                    titleRes = R.string.reading_time_sort,
                    iconRes = R.drawable.ic_mingcute_time_line,
                    checked = sortMode == SORT_READING_DURATION,
                ),
                NgExpandableActionMenuItem(
                    itemId = MENU_SORT_LAST_READ,
                    titleRes = R.string.last_read_time_sort,
                    iconRes = R.drawable.ic_history,
                    checked = sortMode == SORT_LAST_READ,
                ),
            ),
            width = 160.dp,
            offset = DpOffset(0.dp, 4.dp),
            onItemClick = { item ->
                expanded = false
                onSortChange(
                    when (item.itemId) {
                        MENU_SORT_DURATION -> SORT_READING_DURATION
                        MENU_SORT_LAST_READ -> SORT_LAST_READ
                        else -> SORT_NAME
                    },
                )
            },
        )
    }
}

@Composable
private fun ReadRecordMoreMenu(
    recordEnabled: Boolean,
    onRecordEnabledChange: (Boolean) -> Unit,
    onClearAllRequest: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        NgFloatingToolbarActionButton(
            iconRes = R.drawable.ic_grid_menu,
            contentDescription = stringResource(R.string.menu),
            onClick = { expanded = true },
        )
        NgExpandableActionMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            items = listOf(
                NgExpandableActionMenuItem(
                    itemId = MENU_ENABLE_RECORD,
                    titleRes = R.string.enable_record,
                    iconRes = R.drawable.ic_check_circle_outline,
                    checked = recordEnabled,
                ),
                NgExpandableActionMenuItem(
                    itemId = MENU_CLEAR_RECORDS,
                    titleRes = R.string.clear_records,
                    iconRes = R.drawable.ic_book_info_delete,
                    dividerBefore = true,
                    danger = true,
                ),
            ),
            width = 148.dp,
            offset = DpOffset(0.dp, 4.dp),
            onItemClick = { item ->
                expanded = false
                when (item.itemId) {
                    MENU_ENABLE_RECORD -> onRecordEnabledChange(!recordEnabled)
                    MENU_CLEAR_RECORDS -> onClearAllRequest()
                }
            },
        )
    }
}

@Composable
private fun ReadRecordPanel(
    items: List<ReadRecordUiItem>,
    totalReadTime: String,
    recordCount: Int,
    recordEnabled: Boolean,
    managing: Boolean,
    selectedNames: Set<String>,
    onManageClick: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onItemClick: (ReadRecordUiItem) -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        NgVisualSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTENT,
            cornerRadius = NgTheme.shapes.mediumDp.dp,
            style = NgGlassDefaults.neutralStyle(),
        ) {
            ReadRecordSummary(totalReadTime, recordCount, recordEnabled)
        }
        Spacer(Modifier.height(12.dp))
        NgVisualSurface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            role = NgMaterialRole.CONTENT,
            cornerRadius = NgTheme.shapes.mediumDp.dp,
            style = NgGlassDefaults.neutralStyle(),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (managing) {
                            stringResource(R.string.read_record_selected_count, selectedNames.size)
                        } else stringResource(R.string.read_record_all),
                        modifier = Modifier.weight(1f),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    TextButton(onClick = onManageClick) {
                        Text(
                            stringResource(if (managing) R.string.complete else R.string.manage),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 14.sp,
                        )
                    }
                }
                if (items.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.read_record_empty),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(items, key = { it.record.bookName }) { item ->
                            ReadRecordRow(
                                item = item,
                                managing = managing,
                                selected = item.record.bookName in selectedNames,
                                onClick = { onItemClick(item) },
                                onToggleSelection = { onToggleSelection(item.record.bookName) },
                            )
                        }
                    }
                }
                if (managing) {
                    HorizontalDivider(
                        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.28f),
                        thickness = 0.6.dp,
                    )
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val allSelected = items.isNotEmpty() &&
                            items.all { it.record.bookName in selectedNames }
                        Row(
                            Modifier.weight(1f).clickable(
                                enabled = items.isNotEmpty(), onClick = onSelectAll,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NgFileSelectionCheckbox(
                                checked = allSelected,
                                onCheckedChange = { onSelectAll() },
                                enabled = items.isNotEmpty(),
                            )
                            Text(
                                stringResource(if (allSelected) R.string.unselect_all else R.string.select_all),
                                color = Color(NgTheme.colors.onSurface),
                                fontSize = 14.sp,
                            )
                        }
                        NgButton(
                            onClick = onDeleteRequest,
                            enabled = selectedNames.isNotEmpty(),
                            modifier = Modifier.widthIn(min = 92.dp).height(38.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp),
                            variant = NgButtonVariant.DANGER,
                        ) {
                            Text(
                                stringResource(R.string.read_record_delete_selected, selectedNames.size),
                                color = Color.White,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                    lineHeightStyle = LineHeightStyle(
                                        alignment = LineHeightStyle.Alignment.Center,
                                        trim = LineHeightStyle.Trim.Both,
                                    ),
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadRecordSummary(
    totalReadTime: String,
    recordCount: Int,
    recordEnabled: Boolean,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.all_read_time),
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
            )
            ReadRecordStatusTag(recordEnabled)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ReadRecordDurationText(totalReadTime) }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.width(0.6.dp).height(42.dp)
                    .background(Color(NgTheme.colors.outlineVariant).copy(alpha = 0.4f)),
            )
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = 28.sp, fontWeight = FontWeight.SemiBold)) {
                            append(recordCount.toString())
                        }
                        withStyle(SpanStyle(fontSize = 12.sp)) {
                            append(" ")
                            append(stringResource(R.string.read_record_books_unit))
                        }
                    },
                    color = Color(NgTheme.colors.onSurface),
                )
                Text(
                    text = stringResource(R.string.read_record_recorded),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun ReadRecordStatusTag(recordEnabled: Boolean) {
    val tint = if (recordEnabled) colorResource(R.color.ng_success)
    else Color(NgTheme.colors.onSurfaceVariant)
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(tint))
        Text(
            text = stringResource(
                if (recordEnabled) R.string.read_record_active else R.string.read_record_paused,
            ),
            color = tint,
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReadRecordDurationText(totalReadTime: String) {
    val numberColor = Color(NgTheme.colors.onSurface)
    val unitColor = Color(NgTheme.colors.onSurfaceVariant)
    val parts = remember(totalReadTime) {
        READ_DURATION_PART_REGEX.findAll(totalReadTime).map { match ->
            match.groupValues[1] to match.groupValues[2].replace("分钟", "分")
        }.toList()
    }
    // Wrap complete number/unit pairs for long durations and large interface fonts.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        parts.forEach { (number, unit) ->
            val secondary = unit == "秒" && parts.size > 1
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(
                        color = if (secondary) unitColor else numberColor,
                        fontSize = if (secondary) 16.sp else 28.sp,
                        fontWeight = FontWeight.SemiBold,
                    )) { append(number) }
                    withStyle(SpanStyle(color = unitColor, fontSize = 12.sp)) {
                        append(unit)
                    }
                },
                modifier = Modifier.alignByBaseline(),
                maxLines = 1,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ReadRecordRow(
    item: ReadRecordUiItem,
    managing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 82.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = if (managing) onToggleSelection else onClick,
            )
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.book != null) {
            NgBookCover(
                book = item.book,
                coverRadius = 6,
                contentDescription = item.record.bookName,
                modifier = Modifier.size(width = 44.dp, height = 60.dp),
            )
        } else {
            ReadRecordMissingCover(item.record.bookName)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.record.bookName,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.author.isNotBlank()) {
                Text(
                    text = item.author,
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mingcute_time_line),
                        contentDescription = null,
                        tint = Color(NgTheme.colors.onSurfaceVariant),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = item.durationText,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                    )
                }
                if (item.lastReadText.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.read_record_last_read, item.lastReadText),
                        color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.82f),
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
        if (managing) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = { onToggleSelection() },
                modifier = Modifier.size(48.dp),
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 70.dp, end = 14.dp),
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.22f),
        thickness = 0.6.dp,
    )
}

@Composable
private fun ReadRecordMissingCover(bookName: String) {
    Surface(
        modifier = Modifier.size(width = 44.dp, height = 60.dp),
        color = Color.Transparent,
        shape = RoundedCornerShape(6.dp),
    ) {
        NgDefaultBookCover(
            title = bookName,
            author = "",
            compact = true,
            coverContentDescription = bookName,
        )
    }
}

@Composable
private fun ReadRecordConfirmationDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    busy: Boolean = false,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.delete),
            modifier = Modifier.padding(horizontal = 24.dp),
            variant = NgDialogVariant.COMPACT_CONFIRMATION,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.width(80.dp).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp, lineHeight = 20.sp)
                }
                NgButton(
                    onClick = onConfirm,
                    enabled = !busy,
                    modifier = Modifier.width(80.dp).height(36.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                    variant = NgButtonVariant.DANGER,
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                }
            },
        ) {
            Text(
                text = message,
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
            )
        }
    }
}

private const val SORT_NAME = 0
private const val SORT_READING_DURATION = 1
private const val SORT_LAST_READ = 2
private const val MENU_SORT_NAME = 0x7201
private const val MENU_SORT_DURATION = 0x7202
private const val MENU_SORT_LAST_READ = 0x7203
private const val MENU_ENABLE_RECORD = 0x7204
private const val MENU_CLEAR_RECORDS = 0x7205
private val READ_DURATION_PART_REGEX = Regex("(\\d+)(天|小时|分钟|秒)")
