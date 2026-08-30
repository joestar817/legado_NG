package io.legado.app.ui.replace

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
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
import io.legado.app.data.entities.ReplaceRule
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
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderAfterLongPress
import io.legado.app.ui.design.components.compose.ngSlideSelect
import io.legado.app.ui.design.components.compose.rememberNgExpandableActionMenuContentWidth
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.components.compose.rememberNgLazySlideSelectState
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.splitNotBlank

private const val TOP_ACTION_ADD = 0x57500001
private const val TOP_ACTION_IMPORT_LOCAL = 0x57500002
private const val TOP_ACTION_IMPORT_ONLINE = 0x57500003
private const val TOP_ACTION_IMPORT_QR = 0x57500004
private const val TOP_ACTION_MANAGE_GROUPS = 0x57500006

private const val FILTER_ACTION_ALL = 0x57500101
private const val FILTER_ACTION_ENABLED = 0x57500102
private const val FILTER_ACTION_DISABLED = 0x57500103
private const val FILTER_ACTION_NONE = 0x57500104
private const val FILTER_ACTION_DYNAMIC_BASE = 0x57501000

private const val ROW_ACTION_ENABLE = 0x57500201
private const val ROW_ACTION_DISABLE = 0x57500202
private const val ROW_ACTION_EDIT = 0x57500203
private const val ROW_ACTION_TOP = 0x57500204
private const val ROW_ACTION_BOTTOM = 0x57500205
private const val ROW_ACTION_DELETE = 0x57500206

private const val BATCH_ACTION_ENABLE = 0x57500301
private const val BATCH_ACTION_DISABLE = 0x57500302
private const val BATCH_ACTION_TOP = 0x57500303
private const val BATCH_ACTION_BOTTOM = 0x57500304
private const val BATCH_ACTION_EXPORT = 0x57500305
private const val BATCH_ACTION_DELETE = 0x57500306

@Composable
internal fun ReplaceRuleScreen(
    rules: List<ReplaceRule>,
    groups: List<String>,
    currentBookName: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onManageGroups: () -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: () -> Unit,
    onImportQr: () -> Unit,
    onEdit: (ReplaceRule) -> Unit,
    onToggleEnabled: (ReplaceRule, Boolean) -> Unit,
    onMoveToTop: (ReplaceRule) -> Unit,
    onMoveToBottom: (ReplaceRule) -> Unit,
    onDelete: (ReplaceRule) -> Unit,
    onReorder: (List<ReplaceRule>) -> Unit,
    onEnableSelection: (List<ReplaceRule>) -> Unit,
    onDisableSelection: (List<ReplaceRule>) -> Unit,
    onMoveSelectionToTop: (List<ReplaceRule>) -> Unit,
    onMoveSelectionToBottom: (List<ReplaceRule>) -> Unit,
    onExportSelection: (List<ReplaceRule>) -> Unit,
    onDeleteSelection: (List<ReplaceRule>) -> Unit,
    onDeleteSection: (String, List<ReplaceRule>) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf<ReplaceRuleFilter>(ReplaceRuleFilter.All) }
    var viewMode by remember { mutableStateOf(ReplaceRuleViewMode.LIST) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    val noGroupLabel = stringResource(R.string.no_group)
    val normalizedQuery = query.trim()
    LaunchedEffect(groups, activeFilter) {
        val groupFilter = activeFilter as? ReplaceRuleFilter.Group
        if (groupFilter != null && groupFilter.name !in groups) {
            activeFilter = ReplaceRuleFilter.All
        }
    }
    val visibleRules = remember(
        rules,
        normalizedQuery,
        activeFilter,
        noGroupLabel,
    ) {
        val filteredRules = when (val filter = activeFilter) {
            ReplaceRuleFilter.All -> rules
            ReplaceRuleFilter.Enabled -> rules.filter(ReplaceRule::isEnabled)
            ReplaceRuleFilter.Disabled -> rules.filterNot(ReplaceRule::isEnabled)
            ReplaceRuleFilter.NoGroup -> rules.filter {
                it.group.isNullOrBlank() || it.group.orEmpty().contains(noGroupLabel)
            }
            is ReplaceRuleFilter.Group -> rules.filter {
                it.group.orEmpty().splitNotBlank(",").any { group ->
                    group.equals(filter.name, ignoreCase = true)
                }
            }
        }
        if (normalizedQuery.isEmpty()) {
            filteredRules
        } else {
            filteredRules.filter {
                it.name.contains(normalizedQuery, ignoreCase = true) ||
                    it.group.orEmpty().contains(normalizedQuery, ignoreCase = true)
            }
        }
    }
    val visibleIds = visibleRules.mapTo(linkedSetOf(), ReplaceRule::id)
    LaunchedEffect(visibleIds) {
        selectedIds = selectedIds.intersect(visibleIds)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        ReplaceRuleTopBar(
            query = query,
            groups = groups,
            activeFilter = activeFilter,
            onQueryChange = { query = it },
            onFilterChange = { activeFilter = it },
            onBack = onBack,
            onAdd = onAdd,
            onManageGroups = onManageGroups,
            onImportLocal = onImportLocal,
            onImportOnline = onImportOnline,
            onImportQr = onImportQr,
        )
        ReplaceRulePanel(
            rules = visibleRules,
            currentBookName = currentBookName,
            query = normalizedQuery,
            viewMode = viewMode,
            selectedIds = selectedIds,
            reorderEnabled = viewMode == ReplaceRuleViewMode.LIST &&
                normalizedQuery.isEmpty() && activeFilter == ReplaceRuleFilter.All,
            onViewModeChange = { viewMode = it },
            onSelectionChange = { rule, selected ->
                selectedIds = if (selected) selectedIds + rule.id else selectedIds - rule.id
            },
            onSectionSelectionChange = { sectionRules, selected ->
                val sectionIds = sectionRules.mapTo(linkedSetOf(), ReplaceRule::id)
                selectedIds = if (selected) selectedIds + sectionIds else selectedIds - sectionIds
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
            onDeleteSection = { title, sectionRules ->
                selectedIds = selectedIds - sectionRules.map(ReplaceRule::id).toSet()
                onDeleteSection(title, sectionRules)
            },
            modifier = Modifier.weight(1f),
        )
        val selectedRules = visibleRules.filter { it.id in selectedIds }
        ReplaceRuleBottomDock(
            selectedCount = selectedRules.size,
            totalCount = visibleRules.size,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            onSelectAll = { selectedIds = visibleIds },
            onInvertSelection = { selectedIds = visibleIds - selectedIds },
            onEnableSelection = { onEnableSelection(selectedRules) },
            onDisableSelection = { onDisableSelection(selectedRules) },
            onMoveSelectionToTop = { onMoveSelectionToTop(selectedRules) },
            onMoveSelectionToBottom = { onMoveSelectionToBottom(selectedRules) },
            onExportSelection = { onExportSelection(selectedRules) },
            onDeleteSelection = { onDeleteSelection(selectedRules) },
        )
    }
}

@Composable
private fun ReplaceRuleTopBar(
    query: String,
    groups: List<String>,
    activeFilter: ReplaceRuleFilter,
    onQueryChange: (String) -> Unit,
    onFilterChange: (ReplaceRuleFilter) -> Unit,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onManageGroups: () -> Unit,
    onImportLocal: () -> Unit,
    onImportOnline: () -> Unit,
    onImportQr: () -> Unit,
) {
    val filterMenuState = remember { NgPopupToggleState() }
    val mainMenuState = remember { NgPopupToggleState() }
    val filterItems = remember(groups, activeFilter) {
        buildList {
            add(
                NgExpandableActionMenuItem(
                    FILTER_ACTION_ALL,
                    R.string.all,
                    R.drawable.ic_check_circle_outline,
                    checked = activeFilter == ReplaceRuleFilter.All,
                )
            )
            add(
                NgExpandableActionMenuItem(
                    FILTER_ACTION_ENABLED,
                    R.string.enabled,
                    R.drawable.ic_check_circle_outline,
                    checked = activeFilter == ReplaceRuleFilter.Enabled,
                )
            )
            add(
                NgExpandableActionMenuItem(
                    FILTER_ACTION_DISABLED,
                    R.string.disabled,
                    R.drawable.ic_block_outline,
                    checked = activeFilter == ReplaceRuleFilter.Disabled,
                )
            )
            add(
                NgExpandableActionMenuItem(
                    FILTER_ACTION_NONE,
                    R.string.no_group,
                    R.drawable.ic_folder_open,
                    checked = activeFilter == ReplaceRuleFilter.NoGroup,
                )
            )
            groups.forEachIndexed { index, group ->
                add(
                    NgExpandableActionMenuItem(
                        itemId = FILTER_ACTION_DYNAMIC_BASE + index,
                        titleRes = 0,
                        title = group,
                        iconRes = R.drawable.ic_folder_open,
                        checked = activeFilter == ReplaceRuleFilter.Group(group),
                    )
                )
            }
        }
    }
    val mainItems = remember {
        listOf(
            NgExpandableActionMenuItem(
                TOP_ACTION_ADD,
                R.string.add_replace_rule,
                R.drawable.ic_add,
            ),
            NgExpandableActionMenuItem(
                TOP_ACTION_MANAGE_GROUPS,
                R.string.group_manage,
                R.drawable.ic_settings,
            ),
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
                    hint = stringResource(R.string.replace_purify_search),
                    modifier = Modifier.weight(1f),
                    variant = NgSearchBarVariant.TOOLBAR,
                    containerColor = Color.Transparent,
                    hideHintOnFocus = true,
                )
                Spacer(Modifier.width(4.dp))
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                mainMenuState.close()
                                filterMenuState.onAnchorClick()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_screen),
                            contentDescription = stringResource(R.string.screen),
                            tint = Color(
                                if (filterMenuState.expanded || activeFilter != ReplaceRuleFilter.All) {
                                    NgTheme.colors.primary
                                } else {
                                    NgTheme.colors.onSurface
                                }
                            ),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    NgExpandableActionMenu(
                        expanded = filterMenuState.expanded,
                        onDismissRequest = filterMenuState::onDismissRequest,
                        items = filterItems,
                        menuContainerColor = colorResource(R.color.ng_surface_card),
                        properties = PopupProperties(focusable = true),
                        onItemClick = { item ->
                            filterMenuState.close()
                            when (item.itemId) {
                                FILTER_ACTION_ALL -> onFilterChange(ReplaceRuleFilter.All)
                                FILTER_ACTION_ENABLED -> onFilterChange(ReplaceRuleFilter.Enabled)
                                FILTER_ACTION_DISABLED -> onFilterChange(ReplaceRuleFilter.Disabled)
                                FILTER_ACTION_NONE -> onFilterChange(ReplaceRuleFilter.NoGroup)
                                else -> groups.getOrNull(
                                    item.itemId - FILTER_ACTION_DYNAMIC_BASE
                                )?.let { onFilterChange(ReplaceRuleFilter.Group(it)) }
                            }
                        },
                    )
                }
                Spacer(Modifier.width(4.dp))
                Box {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                filterMenuState.close()
                                mainMenuState.onAnchorClick()
                            },
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
                        expanded = mainMenuState.expanded,
                        onDismissRequest = mainMenuState::onDismissRequest,
                        items = mainItems,
                        variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                        sideSlideEndMargin = 14.dp,
                        menuContainerColor = colorResource(R.color.ng_surface_card),
                        properties = PopupProperties(focusable = true, clippingEnabled = false),
                        onItemClick = { item ->
                            mainMenuState.close()
                            when (item.itemId) {
                                TOP_ACTION_ADD -> onAdd()
                                TOP_ACTION_MANAGE_GROUPS -> onManageGroups()
                                TOP_ACTION_IMPORT_LOCAL -> onImportLocal()
                                TOP_ACTION_IMPORT_ONLINE -> onImportOnline()
                                TOP_ACTION_IMPORT_QR -> onImportQr()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReplaceRulePanel(
    rules: List<ReplaceRule>,
    currentBookName: String,
    query: String,
    viewMode: ReplaceRuleViewMode,
    selectedIds: Set<Long>,
    reorderEnabled: Boolean,
    onViewModeChange: (ReplaceRuleViewMode) -> Unit,
    onSelectionChange: (ReplaceRule, Boolean) -> Unit,
    onSectionSelectionChange: (List<ReplaceRule>, Boolean) -> Unit,
    onEdit: (ReplaceRule) -> Unit,
    onToggleEnabled: (ReplaceRule, Boolean) -> Unit,
    onMoveToTop: (ReplaceRule) -> Unit,
    onMoveToBottom: (ReplaceRule) -> Unit,
    onDelete: (ReplaceRule) -> Unit,
    onReorder: (List<ReplaceRule>) -> Unit,
    onDeleteSection: (String, List<ReplaceRule>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var orderedRules by remember(rules) { mutableStateOf(rules) }
    var expandedSectionKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    val noGroupTitle = stringResource(R.string.no_group)
    val globalRulesTitle = stringResource(R.string.replace_scope_global_rules)
    val sections = when (viewMode) {
        ReplaceRuleViewMode.LIST -> emptyList()
        ReplaceRuleViewMode.GROUP -> groupSections(orderedRules, noGroupTitle)
        ReplaceRuleViewMode.SCOPE -> scopeSections(
            orderedRules,
            currentBookName,
            globalRulesTitle,
        )
    }
    val sectionStateKey = "$viewMode:$query:${sections.joinToString { it.key }}"
    LaunchedEffect(sectionStateKey) {
        expandedSectionKeys = if (query.isNotBlank()) {
            sections.mapTo(linkedSetOf(), ReplaceRuleSectionData::key)
        } else {
            emptySet()
        }
    }
    val displayItems: List<ReplaceRuleUiItem> = if (viewMode == ReplaceRuleViewMode.LIST) {
        orderedRules.map { ReplaceRuleUiItem.Rule(null, it) }
    } else {
        buildList {
            sections.forEach { section ->
                val expanded = section.key in expandedSectionKeys
                add(ReplaceRuleUiItem.Section(section, expanded))
                if (expanded) {
                    section.rules.forEach { rule ->
                        add(ReplaceRuleUiItem.Rule(section.key, rule))
                    }
                }
            }
        }
    }
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
            (displayItems.getOrNull(index) as? ReplaceRuleUiItem.Rule)
                ?.rule?.id?.let(selectedIds::contains) == true
        },
        onSelectionChange = { index, selected ->
            (displayItems.getOrNull(index) as? ReplaceRuleUiItem.Rule)
                ?.rule?.let { onSelectionChange(it, selected) }
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
            Column(modifier = Modifier.fillMaxSize()) {
                ReplaceRuleTabs(
                    selected = viewMode,
                    onSelected = onViewModeChange,
                )
                HorizontalDivider(
                    thickness = 0.6.dp,
                    color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.20f),
                )
                if (displayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.empty),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .ngSlideSelect(
                                state = slideSelectState,
                                enabled = !reorderState.isDragging,
                                slideAreaStart = 0.dp,
                                slideAreaEnd = 48.dp,
                            ),
                        state = reorderState.listState,
                    ) {
                        itemsIndexed(
                            items = displayItems,
                            key = { _, item -> item.key },
                        ) { index, item ->
                            when (item) {
                                is ReplaceRuleUiItem.Section -> ReplaceRuleSectionRow(
                                    item = item,
                                    selectedIds = selectedIds,
                                    onToggleExpanded = {
                                        expandedSectionKeys = if (item.section.key in expandedSectionKeys) {
                                            expandedSectionKeys - item.section.key
                                        } else {
                                            expandedSectionKeys + item.section.key
                                        }
                                    },
                                    onSelectionChange = {
                                        onSectionSelectionChange(item.section.rules, it)
                                    },
                                    onDelete = {
                                        onDeleteSection(item.section.title, item.section.rules)
                                    },
                                )
                                is ReplaceRuleUiItem.Rule -> ReplaceRuleRow(
                                    rule = item.rule,
                                    selected = item.rule.id in selectedIds,
                                    inSection = item.sectionKey != null,
                                    showDivider = index < displayItems.lastIndex,
                                    onSelectionChange = {
                                        onSelectionChange(item.rule, it)
                                    },
                                    onEdit = { onEdit(item.rule) },
                                    onToggleEnabled = {
                                        onToggleEnabled(item.rule, it)
                                    },
                                    onMoveToTop = { onMoveToTop(item.rule) },
                                    onMoveToBottom = { onMoveToBottom(item.rule) },
                                    onDelete = { onDelete(item.rule) },
                                    modifier = if (viewMode == ReplaceRuleViewMode.LIST) {
                                        Modifier.ngDraggedItem(reorderState, item.rule.id)
                                    } else {
                                        Modifier
                                    },
                                    bodyDragModifier = if (reorderEnabled) {
                                        Modifier.ngReorderAfterLongPress(
                                            state = reorderState,
                                            key = item.rule.id,
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
    }
}

@Composable
private fun ReplaceRuleTabs(
    selected: ReplaceRuleViewMode,
    onSelected: (ReplaceRuleViewMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReplaceRuleViewMode.entries.forEach { mode ->
            val isSelected = mode == selected
            val title = when (mode) {
                ReplaceRuleViewMode.LIST -> stringResource(R.string.replace_view_list)
                ReplaceRuleViewMode.GROUP -> stringResource(R.string.replace_view_group)
                ReplaceRuleViewMode.SCOPE -> stringResource(R.string.replace_view_scope)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onSelected(mode) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = title,
                        color = Color(
                            if (isSelected) NgTheme.colors.primary else NgTheme.colors.onSurface
                        ),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isSelected) Color(NgTheme.colors.primary) else Color.Transparent
                        ),
                )
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ReplaceRuleSectionRow(
    item: ReplaceRuleUiItem.Section,
    selectedIds: Set<Long>,
    onToggleExpanded: () -> Unit,
    onSelectionChange: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val allSelected = item.section.rules.isNotEmpty() &&
        item.section.rules.all { it.id in selectedIds }
    val allEnabled = item.section.rules.isNotEmpty() &&
        item.section.rules.all(ReplaceRule::isEnabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Color(NgTheme.colors.surfaceContainerHigh).copy(alpha = 0.36f)
            )
            .combinedClickable(
                onClick = onToggleExpanded,
                onLongClickLabel = stringResource(R.string.delete),
                onLongClick = onDelete,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgFileSelectionCheckbox(
            checked = allSelected,
            onCheckedChange = onSelectionChange,
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(
                    colorResource(if (allEnabled) R.color.success else R.color.error)
                ),
        )
        Text(
            text = item.section.title,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                R.string.replace_scope_rule_count,
                item.section.rules.size,
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(Color(NgTheme.colors.selectedContainer).copy(alpha = 0.62f))
                .padding(horizontal = 7.dp, vertical = 2.dp),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 1,
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onToggleExpanded),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_down),
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurfaceVariant),
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (item.expanded) 0f else -90f),
            )
        }
    }
}

@Composable
private fun ReplaceRuleRow(
    rule: ReplaceRule,
    selected: Boolean,
    inSection: Boolean,
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
    val menuItems = remember(rule.id, rule.isEnabled) {
        buildList {
            if (rule.isEnabled) {
                add(
                    NgExpandableActionMenuItem(
                        ROW_ACTION_DISABLE,
                        R.string.replace_rule_disable,
                        R.drawable.ic_block_outline,
                    )
                )
            } else {
                add(
                    NgExpandableActionMenuItem(
                        ROW_ACTION_ENABLE,
                        R.string.enable,
                        R.drawable.ic_check_circle_outline,
                    )
                )
            }
            add(
                NgExpandableActionMenuItem(
                    ROW_ACTION_EDIT,
                    R.string.edit,
                    R.drawable.ic_edit,
                )
            )
            add(
                NgExpandableActionMenuItem(
                    ROW_ACTION_TOP,
                    R.string.to_top,
                    R.drawable.ic_arrow_drop_up,
                )
            )
            add(
                NgExpandableActionMenuItem(
                    ROW_ACTION_BOTTOM,
                    R.string.to_bottom,
                    R.drawable.ic_arrow_down,
                )
            )
            add(
                NgExpandableActionMenuItem(
                    ROW_ACTION_DELETE,
                    R.string.delete,
                    R.drawable.ic_book_info_delete,
                    dividerBefore = true,
                    danger = true,
                )
            )
        }
    }
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
                )
                .padding(start = if (inSection) 12.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = onSelectionChange,
            )
            Text(
                text = rule.getDisplayNameGroup(),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bodyDragModifier ?: Modifier)
                    .clickable { onSelectionChange(!selected) }
                    .padding(vertical = 18.dp),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(
                        colorResource(if (rule.isEnabled) R.color.success else R.color.error)
                    ),
            )
            Spacer(Modifier.width(8.dp))
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
                        contentDescription = stringResource(R.string.more_menu),
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
                    items = menuItems,
                    onItemClick = { item ->
                        menuState.close()
                        when (item.itemId) {
                            ROW_ACTION_ENABLE -> onToggleEnabled(true)
                            ROW_ACTION_DISABLE -> onToggleEnabled(false)
                            ROW_ACTION_EDIT -> onEdit()
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
                modifier = Modifier.padding(start = if (inSection) 72.dp else 60.dp, end = 12.dp),
                thickness = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f),
            )
        }
    }
}

@Composable
private fun ReplaceRuleBottomDock(
    selectedCount: Int,
    totalCount: Int,
    modifier: Modifier = Modifier,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onEnableSelection: () -> Unit,
    onDisableSelection: () -> Unit,
    onMoveSelectionToTop: () -> Unit,
    onMoveSelectionToBottom: () -> Unit,
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
                BATCH_ACTION_TOP,
                R.string.selection_to_top,
                R.drawable.ic_arrow_drop_up,
            ),
            NgExpandableActionMenuItem(
                BATCH_ACTION_BOTTOM,
                R.string.selection_to_bottom,
                R.drawable.ic_arrow_down,
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
                text = stringResource(R.string.replace_rule_selected_count, selectedCount),
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
                                    BATCH_ACTION_TOP -> onMoveSelectionToTop()
                                    BATCH_ACTION_BOTTOM -> onMoveSelectionToBottom()
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

private fun groupSections(
    rules: List<ReplaceRule>,
    noGroupTitle: String,
): List<ReplaceRuleSectionData> {
    return rules
        .flatMap { rule ->
            val ruleGroups = rule.group?.splitNotBlank(",").orEmpty()
            if (ruleGroups.isEmpty()) {
                listOf(ReplaceRuleSectionKey("group:", noGroupTitle, 0) to rule)
            } else {
                ruleGroups.map { ReplaceRuleSectionKey("group:$it", it, 1) to rule }
            }
        }
        .groupBy({ it.first }, { it.second })
        .entries
        .sortedWith(compareBy({ it.key.rank }, { it.key.title }))
        .map { entry ->
            ReplaceRuleSectionData(
                key = entry.key.key,
                title = entry.key.title,
                rules = entry.value.distinctBy(ReplaceRule::id),
            )
        }
}

private fun scopeSections(
    rules: List<ReplaceRule>,
    currentBookName: String,
    globalRulesTitle: String,
): List<ReplaceRuleSectionData> {
    return rules
        .map { rule ->
            scopeSectionKey(rule, currentBookName, globalRulesTitle) to rule
        }
        .groupBy({ it.first }, { it.second })
        .entries
        .sortedWith(compareBy({ it.key.rank }, { it.key.title }))
        .map { entry ->
            ReplaceRuleSectionData(
                key = entry.key.key,
                title = entry.key.title,
                rules = entry.value.distinctBy(ReplaceRule::id),
            )
        }
}

private fun scopeSectionKey(
    rule: ReplaceRule,
    currentBookName: String,
    globalRulesTitle: String,
): ReplaceRuleSectionKey {
    val scope = rule.scope.orEmpty().trim()
    val tokens = scope.split(";", ",", "\n")
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty { if (scope.isBlank()) emptyList() else listOf(scope) }
    if (scope.isBlank()) {
        return ReplaceRuleSectionKey("global", globalRulesTitle, 30)
    }
    val title = tokens.joinToString(" | ") { it.replace("\n", " ").trim() }
        .ifBlank { globalRulesTitle }
    val rank = when {
        currentBookName.isNotBlank() && tokens.any {
            it == currentBookName || it.contains(currentBookName)
        } -> 0
        tokens.any { it.contains("://") } -> 10
        else -> 20
    }
    return ReplaceRuleSectionKey(
        key = "scope:${tokens.joinToString("|").ifBlank { scope }}",
        title = title,
        rank = rank,
    )
}

private sealed interface ReplaceRuleFilter {
    data object All : ReplaceRuleFilter
    data object Enabled : ReplaceRuleFilter
    data object Disabled : ReplaceRuleFilter
    data object NoGroup : ReplaceRuleFilter
    data class Group(val name: String) : ReplaceRuleFilter
}

private enum class ReplaceRuleViewMode {
    LIST,
    GROUP,
    SCOPE,
}

private data class ReplaceRuleSectionKey(
    val key: String,
    val title: String,
    val rank: Int,
)

private data class ReplaceRuleSectionData(
    val key: String,
    val title: String,
    val rules: List<ReplaceRule>,
)

private sealed class ReplaceRuleUiItem {
    abstract val key: String

    data class Section(
        val section: ReplaceRuleSectionData,
        val expanded: Boolean,
    ) : ReplaceRuleUiItem() {
        override val key: String = "section:${section.key}"
    }

    data class Rule(
        val sectionKey: String?,
        val rule: ReplaceRule,
    ) : ReplaceRuleUiItem() {
        override val key: String = "rule:${sectionKey.orEmpty()}:${rule.id}"
    }
}
