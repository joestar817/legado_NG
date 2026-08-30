package io.legado.app.ui.dict.rule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.DictRule
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgFlatActionRail
import io.legado.app.ui.design.components.compose.NgFlatActionRailItem
import io.legado.app.ui.design.components.compose.NgFlatActionRailVariant
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderAfterLongPress
import io.legado.app.ui.design.components.compose.ngSlideSelect
import io.legado.app.ui.design.components.compose.rememberNgExpandableActionMenuContentWidth
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.components.compose.rememberNgLazySlideSelectState
import io.legado.app.ui.design.theme.NgTheme

private const val TOP_ACTION_ADD = 0x57300001
private const val TOP_ACTION_IMPORT_LOCAL = 0x57300002
private const val TOP_ACTION_IMPORT_ONLINE = 0x57300003
private const val TOP_ACTION_IMPORT_QR = 0x57300004
private const val TOP_ACTION_IMPORT_DEFAULT = 0x57300005
private const val TOP_ACTION_HELP = 0x57300006

private const val BATCH_ACTION_ENABLE = 0x57300101
private const val BATCH_ACTION_DISABLE = 0x57300102
private const val BATCH_ACTION_EXPORT = 0x57300103
private const val BATCH_ACTION_DELETE = 0x57300104

private const val ROW_ACTION_TOP = 0x57300201
private const val ROW_ACTION_BOTTOM = 0x57300202
private const val ROW_ACTION_DELETE = 0x57300203

@Composable
internal fun DictRuleScreen(
    rules: List<DictRule>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: () -> Unit,
    onImportQr: () -> Unit,
    onImportDefault: () -> Unit,
    onHelp: () -> Unit,
    onEdit: (DictRule) -> Unit,
    onToggleEnabled: (DictRule, Boolean) -> Unit,
    onMoveToTop: (DictRule) -> Unit,
    onMoveToBottom: (DictRule) -> Unit,
    onDelete: (DictRule) -> Unit,
    onReorder: (List<DictRule>) -> Unit,
    onEnableSelection: (List<DictRule>) -> Unit,
    onDisableSelection: (List<DictRule>) -> Unit,
    onExportSelection: (List<DictRule>) -> Unit,
    onDeleteSelection: (List<DictRule>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedNames by remember { mutableStateOf<Set<String>>(emptySet()) }
    val normalizedQuery = query.trim()
    val visibleRules = rules.filter { rule ->
        normalizedQuery.isEmpty() || listOf(
            rule.name,
            rule.urlRule,
            rule.showRule,
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
    }
    val visibleNames = visibleRules.mapTo(linkedSetOf(), DictRule::name)
    LaunchedEffect(visibleNames) {
        selectedNames = selectedNames.intersect(visibleNames)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        DictRuleTopBar(
            query = query,
            onQueryChange = { query = it },
            onBack = onBack,
            onAdd = onAdd,
            onImportLocal = onImportLocal,
            onImportOnline = onImportOnline,
            onImportQr = onImportQr,
            onImportDefault = onImportDefault,
            onHelp = onHelp,
        )
        DictRulePanel(
            rules = visibleRules,
            selectedNames = selectedNames,
            reorderEnabled = normalizedQuery.isEmpty(),
            onSelectionChange = { rule, selected ->
                selectedNames = if (selected) {
                    selectedNames + rule.name
                } else {
                    selectedNames - rule.name
                }
            },
            onEdit = onEdit,
            onToggleEnabled = onToggleEnabled,
            onMoveToTop = onMoveToTop,
            onMoveToBottom = onMoveToBottom,
            onDelete = { rule ->
                selectedNames = selectedNames - rule.name
                onDelete(rule)
            },
            onReorder = onReorder,
            modifier = Modifier.weight(1f),
        )
        val selectedRules = visibleRules.filter { it.name in selectedNames }
        DictRuleBottomDock(
            selectedCount = selectedRules.size,
            totalCount = visibleRules.size,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            onSelectAll = { selectedNames = visibleNames },
            onInvertSelection = { selectedNames = visibleNames - selectedNames },
            onEnableSelection = { onEnableSelection(selectedRules) },
            onDisableSelection = { onDisableSelection(selectedRules) },
            onExportSelection = { onExportSelection(selectedRules) },
            onDeleteSelection = { onDeleteSelection(selectedRules) },
        )
    }
}

@Composable
private fun DictRuleTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: () -> Unit,
    onImportQr: () -> Unit,
    onImportDefault: () -> Unit,
    onHelp: () -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(TOP_ACTION_ADD, R.string.add, R.drawable.ic_add),
            NgExpandableActionMenuItem(
                TOP_ACTION_IMPORT_LOCAL,
                R.string.import_local,
                R.drawable.ic_folder_open,
            ),
            NgExpandableActionMenuItem(
                TOP_ACTION_IMPORT_ONLINE,
                R.string.import_on_line,
                R.drawable.ic_outline_cloud_24,
            ),
            NgExpandableActionMenuItem(
                TOP_ACTION_IMPORT_QR,
                R.string.import_by_qr_code,
                R.drawable.ic_scan,
            ),
            NgExpandableActionMenuItem(
                TOP_ACTION_IMPORT_DEFAULT,
                R.string.import_default_rule,
                R.drawable.ic_restore,
            ),
            NgExpandableActionMenuItem(
                TOP_ACTION_HELP,
                R.string.help,
                R.drawable.ic_help,
                dividerBefore = true,
            ),
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            role = NgMaterialRole.CONTROL,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            style = NgGlassDefaults.bookDetailStyle(
                containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface),
            ),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NgFloatingToolbarBackButton(onClick = onBack)
                NgSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    hint = stringResource(R.string.search_dict_rule),
                    modifier = Modifier.weight(1f),
                    variant = NgSearchBarVariant.TOOLBAR,
                    containerColor = Color.Transparent,
                    hideHintOnFocus = true,
                )
                Spacer(Modifier.width(8.dp))
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { menuState.onAnchorClick() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_grid_menu),
                            contentDescription = stringResource(R.string.menu),
                            tint = colorResource(R.color.ng_search_icon),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    NgExpandableActionMenu(
                        expanded = menuState.expanded,
                        onDismissRequest = menuState::onDismissRequest,
                        items = menuItems,
                        variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                        sideSlideEndMargin = 14.dp,
                        menuContainerColor = colorResource(R.color.ng_surface_card),
                        properties = PopupProperties(focusable = true, clippingEnabled = false),
                        onItemClick = { item ->
                            menuState.close()
                            when (item.itemId) {
                                TOP_ACTION_ADD -> onAdd()
                                TOP_ACTION_IMPORT_LOCAL -> onImportLocal()
                                TOP_ACTION_IMPORT_ONLINE -> onImportOnline()
                                TOP_ACTION_IMPORT_QR -> onImportQr()
                                TOP_ACTION_IMPORT_DEFAULT -> onImportDefault()
                                TOP_ACTION_HELP -> onHelp()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DictRulePanel(
    rules: List<DictRule>,
    selectedNames: Set<String>,
    reorderEnabled: Boolean,
    onSelectionChange: (DictRule, Boolean) -> Unit,
    onEdit: (DictRule) -> Unit,
    onToggleEnabled: (DictRule, Boolean) -> Unit,
    onMoveToTop: (DictRule) -> Unit,
    onMoveToBottom: (DictRule) -> Unit,
    onDelete: (DictRule) -> Unit,
    onReorder: (List<DictRule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ruleVersions = rules.map(System::identityHashCode)
    var orderedRules by remember(ruleVersions) { mutableStateOf(rules) }
    val reorderState = rememberNgLazyReorderState(
        onMove = { from, to ->
            if (reorderEnabled && from in orderedRules.indices && to in orderedRules.indices) {
                orderedRules = orderedRules.toMutableList().apply {
                    add(to, removeAt(from))
                }
            }
        },
        onFinished = { if (reorderEnabled) onReorder(orderedRules) },
    )
    val slideSelectState = rememberNgLazySlideSelectState(
        listState = reorderState.listState,
        isSelected = { index ->
            orderedRules.getOrNull(index)?.name?.let(selectedNames::contains) == true
        },
        onSelectionChange = { index, selected ->
            orderedRules.getOrNull(index)?.let { onSelectionChange(it, selected) }
        },
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp),
    ) {
        NgGlassSurface(
            modifier = Modifier.fillMaxSize(),
            role = NgMaterialRole.CONTENT,
            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
            style = NgGlassDefaults.bookDetailStyle(
                containerColor = colorResource(R.color.ng_surface_card),
            ),
            liquidCornerRadius = NgTheme.shapes.mediumDp.dp,
        ) {
            if (orderedRules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.empty),
                        modifier = Modifier.padding(24.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .ngSlideSelect(
                            state = slideSelectState,
                            enabled = !reorderState.isDragging,
                            slideAreaStart = 0.dp,
                            slideAreaEnd = 48.dp,
                        ),
                    state = reorderState.listState,
                ) {
                    itemsIndexed(orderedRules, key = { _, rule -> rule.name }) { index, rule ->
                        DictRuleRow(
                            rule = rule,
                            selected = rule.name in selectedNames,
                            showDivider = index < orderedRules.lastIndex,
                            onSelectionChange = { onSelectionChange(rule, it) },
                            onEdit = { onEdit(rule) },
                            onToggleEnabled = { onToggleEnabled(rule, it) },
                            onMoveToTop = { onMoveToTop(rule) },
                            onMoveToBottom = { onMoveToBottom(rule) },
                            onDelete = { onDelete(rule) },
                            modifier = Modifier.ngDraggedItem(reorderState, rule.name),
                            bodyDragModifier = if (reorderEnabled) {
                                Modifier.ngReorderAfterLongPress(
                                    state = reorderState,
                                    key = rule.name,
                                    enabled = true,
                                    contentDescription = stringResource(R.string.sort),
                                )
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DictRuleRow(
    rule: DictRule,
    selected: Boolean,
    showDivider: Boolean,
    onSelectionChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    bodyDragModifier: Modifier?,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(
                    if (selected) {
                        Color(NgTheme.colors.selectedContainer).copy(alpha = 0.22f)
                    } else {
                        Color.Transparent
                    }
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFileSelectionCheckbox(checked = selected, onCheckedChange = onSelectionChange)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bodyDragModifier ?: Modifier)
                    .clickable { onSelectionChange(!selected) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = rule.name,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NgSwitchControl(
                checked = rule.enabled,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.padding(horizontal = 2.dp),
                variant = NgSwitchControlVariant.COMPACT,
            )
            DictRuleIconAction(R.drawable.ic_settings, R.string.edit, onEdit)
            DictRuleMoreAction(
                onMoveToTop = onMoveToTop,
                onMoveToBottom = onMoveToBottom,
                onDelete = onDelete,
            )
            Spacer(Modifier.width(4.dp))
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp, end = 12.dp),
                thickness = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
        }
    }
}

@Composable
private fun DictRuleIconAction(iconRes: Int, descriptionRes: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = stringResource(descriptionRes),
            tint = Color(NgTheme.colors.onSurface),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DictRuleMoreAction(
    onMoveToTop: () -> Unit,
    onMoveToBottom: () -> Unit,
    onDelete: () -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    Box {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable { menuState.onAnchorClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more_vert),
                contentDescription = stringResource(R.string.menu),
                tint = Color(
                    if (menuState.expanded) {
                        NgTheme.colors.primary
                    } else {
                        NgTheme.colors.onSurface
                    }
                ),
                modifier = Modifier.size(20.dp),
            )
        }
        NgExpandableActionMenu(
            expanded = menuState.expanded,
            onDismissRequest = menuState::onDismissRequest,
            items = listOf(
                NgExpandableActionMenuItem(
                    ROW_ACTION_TOP,
                    R.string.to_top,
                    R.drawable.ic_arrow_drop_up,
                ),
                NgExpandableActionMenuItem(
                    ROW_ACTION_BOTTOM,
                    R.string.to_bottom,
                    R.drawable.ic_arrow_down,
                ),
                NgExpandableActionMenuItem(
                    ROW_ACTION_DELETE,
                    R.string.delete,
                    R.drawable.ic_book_info_delete,
                    dividerBefore = true,
                    danger = true,
                ),
            ),
            onItemClick = { item ->
                menuState.close()
                when (item.itemId) {
                    ROW_ACTION_TOP -> onMoveToTop()
                    ROW_ACTION_BOTTOM -> onMoveToBottom()
                    ROW_ACTION_DELETE -> onDelete()
                }
            },
        )
    }
}

@Composable
private fun DictRuleBottomDock(
    selectedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onEnableSelection: () -> Unit,
    onDisableSelection: () -> Unit,
    onExportSelection: () -> Unit,
    onDeleteSelection: () -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                BATCH_ACTION_ENABLE,
                R.string.enable_selection,
                R.drawable.ic_check_circle_outline,
            ),
            NgExpandableActionMenuItem(
                BATCH_ACTION_DISABLE,
                R.string.disable_selection,
                R.drawable.ic_block_outline,
            ),
            NgExpandableActionMenuItem(
                BATCH_ACTION_EXPORT,
                R.string.export_selection,
                R.drawable.ic_export,
            ),
            NgExpandableActionMenuItem(
                BATCH_ACTION_DELETE,
                R.string.delete,
                R.drawable.ic_book_info_delete,
                dividerBefore = true,
                danger = true,
            ),
        )
    }
    val menuWidth = rememberNgExpandableActionMenuContentWidth(menuItems)
    val moreSegmentWidth = 220.dp / 3f
    NgGlassSurface(
        modifier = modifier.fillMaxWidth(),
        role = NgMaterialRole.CONTROL,
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_bookshelf_manage_control_surface),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.dict_rule_selected_count, selectedCount),
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.width(220.dp)) {
                NgFlatActionRail(
                    items = listOf(
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_select_all,
                            label = stringResource(R.string.select_all),
                            enabled = totalCount > 0,
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_refresh_black_24dp,
                            label = stringResource(R.string.revert_selection),
                            enabled = totalCount > 0,
                        ),
                        NgFlatActionRailItem(
                            iconRes = R.drawable.ic_more_horiz,
                            label = stringResource(R.string.more),
                            enabled = selectedCount > 0,
                            emphasized = menuState.expanded,
                        ),
                    ),
                    onItemClick = { index ->
                        when (index) {
                            0 -> onSelectAll()
                            1 -> onInvertSelection()
                            else -> menuState.onAnchorClick()
                        }
                    },
                    variant = NgFlatActionRailVariant.INLINE_DIVIDED,
                    trailingOverlay = {
                        NgExpandableActionMenu(
                            expanded = menuState.expanded,
                            onDismissRequest = menuState::onDismissRequest,
                            items = menuItems,
                            onItemClick = { item ->
                                menuState.close()
                                when (item.itemId) {
                                    BATCH_ACTION_ENABLE -> onEnableSelection()
                                    BATCH_ACTION_DISABLE -> onDisableSelection()
                                    BATCH_ACTION_EXPORT -> onExportSelection()
                                    BATCH_ACTION_DELETE -> onDeleteSelection()
                                }
                            },
                            width = menuWidth,
                            rowMinHeight = 36.dp,
                            bottomPointerHeight = 8.dp,
                            bottomPointerWidth = 18.dp,
                            bottomPointerEndOffset = 12.dp + moreSegmentWidth / 2f,
                            menuContainerColor = colorResource(R.color.ng_surface_card),
                            offset = DpOffset(
                                x = 12.dp + moreSegmentWidth - menuWidth,
                                y = (-8).dp,
                            ),
                        )
                    },
                )
            }
        }
    }
}
