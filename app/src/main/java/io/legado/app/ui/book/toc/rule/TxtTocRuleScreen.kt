package io.legado.app.ui.book.toc.rule

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
import io.legado.app.data.entities.TxtTocRule
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
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.components.compose.rememberNgLazySlideSelectState
import io.legado.app.ui.design.components.compose.rememberNgExpandableActionMenuContentWidth
import io.legado.app.ui.design.theme.NgTheme

private const val TOP_ACTION_ADD = 0x57200001
private const val TOP_ACTION_IMPORT_LOCAL = 0x57200003
private const val TOP_ACTION_IMPORT_ONLINE = 0x57200004
private const val TOP_ACTION_IMPORT_QR = 0x57200005
private const val TOP_ACTION_IMPORT_DEFAULT = 0x57200006
private const val TOP_ACTION_HELP = 0x57200007

private const val ROW_ACTION_TOP = 0x57200101
private const val ROW_ACTION_BOTTOM = 0x57200102
private const val ROW_ACTION_DELETE = 0x57200103

private const val BATCH_ACTION_ENABLE = 0x57200201
private const val BATCH_ACTION_DISABLE = 0x57200202
private const val BATCH_ACTION_EXPORT = 0x57200203
private const val BATCH_ACTION_DELETE = 0x57200204

@Composable
internal fun TxtTocRuleScreen(
    rules: List<TxtTocRule>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: () -> Unit,
    onImportQr: () -> Unit,
    onImportDefault: () -> Unit,
    onHelp: () -> Unit,
    onEdit: (TxtTocRule) -> Unit,
    onToggleEnabled: (TxtTocRule, Boolean) -> Unit,
    onMoveToTop: (TxtTocRule) -> Unit,
    onMoveToBottom: (TxtTocRule) -> Unit,
    onDelete: (TxtTocRule) -> Unit,
    onReorder: (List<TxtTocRule>) -> Unit,
    onEnableSelection: (List<TxtTocRule>) -> Unit,
    onDisableSelection: (List<TxtTocRule>) -> Unit,
    onExportSelection: (List<TxtTocRule>) -> Unit,
    onDeleteSelection: (List<TxtTocRule>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val normalizedQuery = query.trim()
    val visibleRules = rules.filter { rule ->
        normalizedQuery.isEmpty() || listOf(
            rule.name,
            rule.rule,
            rule.replacement,
            rule.example.orEmpty(),
        ).any { it.contains(normalizedQuery, ignoreCase = true) }
    }
    val visibleIds = visibleRules.mapTo(linkedSetOf(), TxtTocRule::id)
    LaunchedEffect(visibleIds) {
        selectedIds = selectedIds.intersect(visibleIds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        TxtTocRuleTopBar(
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
        TxtTocRulePanel(
            rules = visibleRules,
            selectedIds = selectedIds,
            reorderEnabled = normalizedQuery.isEmpty(),
            onSelectionChange = { rule, selected ->
                selectedIds = if (selected) selectedIds + rule.id else selectedIds - rule.id
            },
            onEdit = onEdit,
            onToggleEnabled = onToggleEnabled,
            onMoveToTop = onMoveToTop,
            onMoveToBottom = onMoveToBottom,
            onDelete = { rule ->
                selectedIds = selectedIds - rule.id
                onDelete(rule)
            },
            onReorder = onReorder,
            modifier = Modifier.weight(1f),
        )
        val selectedRules = visibleRules.filter { it.id in selectedIds }
        TxtTocRuleBottomDock(
            selectedCount = selectedRules.size,
            totalCount = visibleRules.size,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            onSelectAll = { selectedIds = visibleIds },
            onInvertSelection = { selectedIds = visibleIds - selectedIds },
            onEnableSelection = { onEnableSelection(selectedRules) },
            onDisableSelection = { onDisableSelection(selectedRules) },
            onExportSelection = { onExportSelection(selectedRules) },
            onDeleteSelection = { onDeleteSelection(selectedRules) },
        )
    }
}

@Composable
private fun TxtTocRuleTopBar(
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
            NgExpandableActionMenuItem(
                itemId = TOP_ACTION_ADD,
                titleRes = R.string.add,
                iconRes = R.drawable.ic_add,
            ),
            NgExpandableActionMenuItem(
                itemId = TOP_ACTION_IMPORT_LOCAL,
                titleRes = R.string.import_local,
                iconRes = R.drawable.ic_folder_open,
            ),
            NgExpandableActionMenuItem(
                itemId = TOP_ACTION_IMPORT_ONLINE,
                titleRes = R.string.import_on_line,
                iconRes = R.drawable.ic_outline_cloud_24,
            ),
            NgExpandableActionMenuItem(
                itemId = TOP_ACTION_IMPORT_QR,
                titleRes = R.string.import_by_qr_code,
                iconRes = R.drawable.ic_scan,
            ),
            NgExpandableActionMenuItem(
                itemId = TOP_ACTION_IMPORT_DEFAULT,
                titleRes = R.string.import_default_rule,
                iconRes = R.drawable.ic_restore,
            ),
            NgExpandableActionMenuItem(
                itemId = TOP_ACTION_HELP,
                titleRes = R.string.help,
                iconRes = R.drawable.ic_help,
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
                    .padding(start = 0.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NgFloatingToolbarBackButton(onClick = onBack)
                NgSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    hint = stringResource(R.string.search_txt_toc_rule),
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
                        properties = PopupProperties(
                            focusable = true,
                            clippingEnabled = false,
                        ),
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
private fun TxtTocRulePanel(
    rules: List<TxtTocRule>,
    selectedIds: Set<Long>,
    reorderEnabled: Boolean,
    onSelectionChange: (TxtTocRule, Boolean) -> Unit,
    onEdit: (TxtTocRule) -> Unit,
    onToggleEnabled: (TxtTocRule, Boolean) -> Unit,
    onMoveToTop: (TxtTocRule) -> Unit,
    onMoveToBottom: (TxtTocRule) -> Unit,
    onDelete: (TxtTocRule) -> Unit,
    onReorder: (List<TxtTocRule>) -> Unit,
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
        onFinished = {
            if (reorderEnabled) onReorder(orderedRules)
        },
    )
    val slideSelectState = rememberNgLazySlideSelectState(
        listState = reorderState.listState,
        isSelected = { index ->
            orderedRules.getOrNull(index)?.id?.let(selectedIds::contains) == true
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
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
                    itemsIndexed(
                        items = orderedRules,
                        key = { _, rule -> rule.id },
                    ) { index, rule ->
                        TxtTocRuleRow(
                            rule = rule,
                            selected = rule.id in selectedIds,
                            showDivider = index < orderedRules.lastIndex,
                            onSelectionChange = { onSelectionChange(rule, it) },
                            onEdit = { onEdit(rule) },
                            onToggleEnabled = { onToggleEnabled(rule, it) },
                            onMoveToTop = { onMoveToTop(rule) },
                            onMoveToBottom = { onMoveToBottom(rule) },
                            onDelete = { onDelete(rule) },
                            modifier = Modifier.ngDraggedItem(reorderState, rule.id),
                            bodyDragModifier = if (reorderEnabled) {
                                Modifier.ngReorderAfterLongPress(
                                    state = reorderState,
                                    key = rule.id,
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
private fun TxtTocRuleRow(
    rule: TxtTocRule,
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
    val menuState = remember(rule.id) { NgPopupToggleState() }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .background(
                    if (selected) {
                        Color(NgTheme.colors.selectedContainer).copy(alpha = 0.22f)
                    } else {
                        Color.Transparent
                    },
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = onSelectionChange,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bodyDragModifier ?: Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelectionChange(!selected) }
                        .padding(vertical = 9.dp),
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
                    rule.example?.takeIf(String::isNotBlank)?.let { example ->
                        Text(
                            text = example,
                            modifier = Modifier.padding(top = 2.dp),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            NgSwitchControl(
                checked = rule.enable,
                onCheckedChange = onToggleEnabled,
                modifier = Modifier.padding(horizontal = 2.dp),
                variant = NgSwitchControlVariant.COMPACT,
            )
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.edit),
                    tint = Color(NgTheme.colors.onSurface),
                    modifier = Modifier.size(20.dp),
                )
            }
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
                            itemId = ROW_ACTION_TOP,
                            titleRes = R.string.to_top,
                            iconRes = R.drawable.ic_arrow_drop_up,
                        ),
                        NgExpandableActionMenuItem(
                            itemId = ROW_ACTION_BOTTOM,
                            titleRes = R.string.to_bottom,
                            iconRes = R.drawable.ic_arrow_down,
                        ),
                        NgExpandableActionMenuItem(
                            itemId = ROW_ACTION_DELETE,
                            titleRes = R.string.delete,
                            iconRes = R.drawable.ic_book_info_delete,
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
private fun TxtTocRuleBottomDock(
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
                itemId = BATCH_ACTION_ENABLE,
                titleRes = R.string.enable_selection,
                iconRes = R.drawable.ic_check_circle_outline,
            ),
            NgExpandableActionMenuItem(
                itemId = BATCH_ACTION_DISABLE,
                titleRes = R.string.disable_selection,
                iconRes = R.drawable.ic_block_outline,
            ),
            NgExpandableActionMenuItem(
                itemId = BATCH_ACTION_EXPORT,
                titleRes = R.string.export_selection,
                iconRes = R.drawable.ic_export,
            ),
            NgExpandableActionMenuItem(
                itemId = BATCH_ACTION_DELETE,
                titleRes = R.string.delete,
                iconRes = R.drawable.ic_book_info_delete,
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
                text = stringResource(R.string.txt_toc_rule_selected_count, selectedCount),
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
