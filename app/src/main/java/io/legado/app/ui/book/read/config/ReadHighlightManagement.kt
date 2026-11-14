package io.legado.app.ui.book.read.config

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.ReadHighlightRule
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormPanelVariant
import io.legado.app.ui.design.theme.NgTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Rule actions, selection and ordering all stay inside the highlight tab. */
@Composable
internal fun HighlightRuleLibrary(
    state: ReadStyleUiState,
    overview: ReadHighlightOverview,
    contentColor: Color,
    accentColor: Color,
    actions: ReadStyleActions,
    modifier: Modifier = Modifier,
) {
    val selecting = state.highlightSelectionMode != HighlightSelectionMode.NONE
    var sorting by remember { mutableStateOf(false) }
    var orderedRules by remember(state.highlightRules) { mutableStateOf(state.highlightRules) }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (sorting && from.index in orderedRules.indices && to.index in orderedRules.indices) {
            orderedRules = orderedRules.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    }
    BackHandler(enabled = sorting) { sorting = false }

    NgFormPanel(variant = NgFormPanelVariant.LARGE_ROUNDED, modifier = modifier) {
        HighlightLibraryHeader(
            selecting = selecting, sorting = sorting, selectedCount = state.selectedHighlightIds.size,
            hasRules = orderedRules.isNotEmpty(), canSort = orderedRules.size > 1,
            contentColor = contentColor, accentColor = accentColor,
            onSelect = actions.onBeginHighlightSelection,
            onCancelSelection = actions.onCancelHighlightSelection,
            onSort = { sorting = true }, onFinishSorting = { sorting = false },
        )
        if (!selecting) {
            HighlightGlobalActions(
                accentColor = accentColor,
                onCreate = actions.onCreateHighlight,
                onImport = actions.onImportHighlights,
                onRestore = actions.onRestoreBuiltInHighlights,
            )
        } else if (selecting) {
            HighlightBatchActions(
                selectedCount = state.selectedHighlightIds.size,
                allSelected = orderedRules.isNotEmpty() && orderedRules.all { it.id in state.selectedHighlightIds },
                accentColor = accentColor,
                onToggleAll = actions.onToggleAllHighlightSelection,
                onExport = { actions.onConfirmHighlightSelection(HighlightBatchAction.EXPORT) },
                onDelete = { actions.onConfirmHighlightSelection(HighlightBatchAction.DELETE) },
            )
        }
        NgFormGroupDivider()
        if (orderedRules.isEmpty()) {
            Text(stringResource(R.string.empty), Modifier.padding(16.dp), color = contentColor.copy(alpha = 0.65f), fontSize = 14.sp)
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), state = listState) {
                itemsIndexed(orderedRules, key = { _, rule -> rule.id }) { index, rule ->
                    when {
                        selecting -> HighlightBatchRuleRow(
                            rule = rule,
                            selected = rule.id in state.selectedHighlightIds,
                            contentColor = contentColor,
                            onToggle = { actions.onToggleHighlightSelection(rule.id) },
                        )
                        sorting -> ReorderableItem(state = reorderState, key = rule.id) {
                            HighlightSortRuleRow(
                                rule = rule,
                                contentColor = contentColor,
                                dragHandleModifier = Modifier.draggableHandle(
                                    onDragStopped = { actions.onReorderHighlights(orderedRules) },
                                ),
                            )
                        }
                        else -> HighlightManagedRuleRow(
                            rule = rule,
                            usesLinkedRules = overview.usesLinkedRules,
                            usedByCurrent = rule.id in overview.usedRuleIds,
                            onlyThisBook = state.onlyThisBook,
                            canRestore = rule.id in state.builtInHighlightRuleIds,
                            contentColor = contentColor,
                            accentColor = accentColor,
                            onEdit = { actions.onEditHighlight(index) },
                            onEnabledChanged = { actions.onHighlightEnabledChanged(index, it) },
                            onAction = { action ->
                                when (action) {
                                    HighlightRuleMenuAction.COPY -> actions.onCopyHighlight(rule.id)
                                    HighlightRuleMenuAction.EXPORT -> actions.onExportHighlight(rule.id)
                                    HighlightRuleMenuAction.RESTORE -> actions.onRestoreHighlight(rule.id)
                                    HighlightRuleMenuAction.DELETE -> actions.onDeleteHighlightById(rule.id)
                                }
                            },
                        )
                    }
                    if (index != orderedRules.lastIndex) NgFormGroupDivider()
                }
            }
        }
    }
}

@Composable
internal fun HighlightLibraryHeader(
    selecting: Boolean,
    sorting: Boolean,
    selectedCount: Int,
    hasRules: Boolean,
    canSort: Boolean,
    contentColor: Color,
    accentColor: Color,
    onSelect: () -> Unit,
    onCancelSelection: () -> Unit,
    onSort: () -> Unit,
    onFinishSorting: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(R.string.read_highlight_rule_library), Modifier.weight(1f),
            color = accentColor, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        when {
            selecting -> {
                Text(
                    stringResource(R.string.read_highlight_selected_count, selectedCount),
                    color = contentColor.copy(alpha = 0.62f), fontSize = 12.sp, maxLines = 1,
                )
                Text(
                    stringResource(R.string.cancel),
                    Modifier.clickable(role = Role.Button, onClick = onCancelSelection).padding(8.dp),
                    color = accentColor, fontSize = 13.sp,
                )
            }
            sorting -> Text(
                stringResource(R.string.complete),
                Modifier.clickable(role = Role.Button, onClick = onFinishSorting).padding(8.dp),
                color = accentColor, fontSize = 13.sp,
            )
            else -> {
                Text(
                    stringResource(R.string.sort),
                    Modifier.widthIn(min = 48.dp).clickable(enabled = canSort, role = Role.Button, onClick = onSort).padding(8.dp),
                    color = contentColor.copy(alpha = if (canSort) 1f else 0.4f), fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.read_highlight_multi_select),
                    Modifier.widthIn(min = 48.dp).clickable(enabled = hasRules, role = Role.Button, onClick = onSelect).padding(8.dp),
                    color = contentColor.copy(alpha = if (hasRules) 1f else 0.4f), fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun HighlightGlobalActions(accentColor: Color, onCreate: () -> Unit, onImport: () -> Unit, onRestore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HighlightGlobalAction(R.drawable.ic_add, stringResource(R.string.create), accentColor, onCreate, Modifier.weight(1f))
        HighlightGlobalAction(R.drawable.ic_import, stringResource(R.string.import_str), accentColor, onImport, Modifier.weight(1f))
        HighlightGlobalAction(R.drawable.ic_restore, stringResource(R.string.menu_restore), accentColor, onRestore, Modifier.weight(1f))
    }
}

@Composable
private fun HighlightGlobalAction(@DrawableRes icon: Int, label: String, accentColor: Color, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val actionColor = accentColor.copy(alpha = if (enabled) 1f else 0.4f)
    Row(
        modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = if (enabled) 0.12f else 0.06f))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = actionColor, modifier = Modifier.size(18.dp))
        Text(label, Modifier.padding(start = 5.dp), color = actionColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun HighlightBatchActions(
    selectedCount: Int,
    allSelected: Boolean,
    accentColor: Color,
    onToggleAll: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HighlightGlobalAction(
            R.drawable.ic_select_all, stringResource(if (allSelected) R.string.unselect_all else R.string.select_all),
            accentColor, onToggleAll, Modifier.weight(1f),
        )
        HighlightGlobalAction(
            R.drawable.ic_export, stringResource(R.string.export_str),
            accentColor, onExport, Modifier.weight(1f), enabled = selectedCount > 0,
        )
        HighlightGlobalAction(
            R.drawable.ic_book_info_delete, stringResource(R.string.delete),
            accentColor, onDelete, Modifier.weight(1f), enabled = selectedCount > 0,
        )
    }
}

@Composable
private fun HighlightBatchRuleRow(rule: ReadHighlightRule, selected: Boolean, contentColor: Color, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(value = selected, role = Role.Checkbox) { onToggle() }
            .padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = selected, onCheckedChange = null, modifier = Modifier.size(40.dp))
        Text(rule.name.ifBlank { rule.pattern.ifBlank { stringResource(R.string.highlight_rule_default_name) } }, Modifier.weight(1f), color = contentColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HighlightSortRuleRow(rule: ReadHighlightRule, contentColor: Color, dragHandleModifier: Modifier) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 8.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painterResource(R.drawable.ic_drag_handle), contentDescription = stringResource(R.string.sort),
            modifier = Modifier.size(44.dp).then(dragHandleModifier).padding(11.dp), tint = contentColor,
        )
        Text(rule.name.ifBlank { rule.pattern.ifBlank { stringResource(R.string.highlight_rule_default_name) } }, Modifier.weight(1f), color = contentColor, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal enum class HighlightRuleMenuAction { COPY, EXPORT, RESTORE, DELETE }

@Composable
internal fun HighlightManagedRuleRow(
    rule: ReadHighlightRule,
    usesLinkedRules: Boolean,
    usedByCurrent: Boolean,
    onlyThisBook: Boolean,
    canRestore: Boolean,
    contentColor: Color,
    accentColor: Color,
    onEdit: () -> Unit,
    onEnabledChanged: (Boolean) -> Unit,
    onAction: (HighlightRuleMenuAction) -> Unit,
) {
    var expanded by remember(rule.id) { mutableStateOf(false) }
    val copyLabel = stringResource(R.string.read_highlight_copy_action)
    val exportLabel = stringResource(R.string.export_str)
    val restoreLabel = stringResource(R.string.restore_default)
    val deleteLabel = stringResource(R.string.delete)
    val options = buildList {
        add(ReadMoreConfigOption(HighlightRuleMenuAction.COPY.name, copyLabel))
        add(ReadMoreConfigOption(HighlightRuleMenuAction.EXPORT.name, exportLabel))
        if (canRestore) add(ReadMoreConfigOption(HighlightRuleMenuAction.RESTORE.name, restoreLabel))
        add(ReadMoreConfigOption(HighlightRuleMenuAction.DELETE.name, deleteLabel))
    }
    val menuWidth = if (expanded) {
        val density = LocalDensity.current
        val measurer = rememberTextMeasurer()
        val textStyle = TextStyle(fontFamily = NgTheme.fontFamily, fontSize = 14.sp)
        val availableWidth = (LocalConfiguration.current.screenWidthDp.dp - 16.dp).coerceAtLeast(1.dp)
        val minimumWidth = minOf(100.dp * density.fontScale.coerceAtLeast(1f), availableWidth)
        val longest = options.maxOfOrNull { option ->
            with(density) { measurer.measure(option.label, textStyle, maxLines = 1).size.width.toDp() }
        } ?: 0.dp
        (longest + 40.dp).coerceIn(minimumWidth, availableWidth)
    } else {
        100.dp
    }
    HighlightOverviewRuleRow(
        rule = rule, usesLinkedRules = usesLinkedRules, usedByCurrent = usedByCurrent,
        onlyThisBook = onlyThisBook, contentColor = contentColor, accentColor = accentColor,
        onEdit = onEdit, onEnabledChanged = onEnabledChanged, onManage = { expanded = true },
        manageMenu = {
            ReadChoicePopup(
                expanded = expanded, options = options, selectedValue = null,
                onDismissRequest = { expanded = false },
                onSelected = { value -> HighlightRuleMenuAction.entries.firstOrNull { it.name == value }?.let(onAction) },
                preferPopupBelow = true, scrollable = true, variant = ReadChoicePopupVariant.COMPACT,
                menuWidth = menuWidth, useVisibleWindowBounds = true,
            )
        },
    )
}
