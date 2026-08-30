package io.legado.app.ui.rss.source.manage

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.data.entities.RssSource
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgFormField
import io.legado.app.ui.design.components.compose.NgFormFieldVariant
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanel
import io.legado.app.ui.design.components.compose.NgManagementDrawerPanelVariant
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderAfterLongPress
import io.legado.app.ui.design.components.compose.ngSlideSelect
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.components.compose.rememberNgLazySlideSelectState
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.rss.RssEmptyState

private const val FILTER_PARENT_ITEM_ID = 0x55000000
private const val FILTER_ALL_ITEM_ID = 0x55000001
private const val FILTER_ENABLED_ITEM_ID = 0x55000002
private const val FILTER_DISABLED_ITEM_ID = 0x55000003
private const val FILTER_LOGIN_ITEM_ID = 0x55000004
private const val FILTER_NO_GROUP_ITEM_ID = 0x55000005
private const val GROUP_PARENT_ITEM_ID = 0x55000100
private const val GROUP_ITEM_ID_BASE = 0x55001000
private const val IMPORT_PARENT_ITEM_ID = 0x55002000
private const val GROUP_QUERY_PREFIX = "group:"

internal sealed interface RssSourceManageAction {
    data object Back : RssSourceManageAction
    data object Add : RssSourceManageAction
    data object ImportLocal : RssSourceManageAction
    data object ImportOnline : RssSourceManageAction
    data object ImportQr : RssSourceManageAction
    data object ImportDefault : RssSourceManageAction
    data object ManageGroups : RssSourceManageAction
    data object Help : RssSourceManageAction
    data object DeleteSelection : RssSourceManageAction
    data object EnableSelection : RssSourceManageAction
    data object DisableSelection : RssSourceManageAction
    data object AddSelectionToGroup : RssSourceManageAction
    data object RemoveSelectionFromGroup : RssSourceManageAction
    data object TopSelection : RssSourceManageAction
    data object BottomSelection : RssSourceManageAction
    data object ExportSelection : RssSourceManageAction
    data object ShareSelection : RssSourceManageAction
    data object CompleteSelectionInterval : RssSourceManageAction
    data object SelectAll : RssSourceManageAction
    data object InvertSelection : RssSourceManageAction
    data class QueryChanged(val query: String) : RssSourceManageAction
    data class SelectionChanged(
        val source: RssSource,
        val selected: Boolean
    ) : RssSourceManageAction
    data class ToggleEnabled(val source: RssSource, val enabled: Boolean) : RssSourceManageAction
    data class Edit(val source: RssSource) : RssSourceManageAction
    data class Delete(val source: RssSource) : RssSourceManageAction
    data class Top(val source: RssSource) : RssSourceManageAction
    data class Bottom(val source: RssSource) : RssSourceManageAction
    data class Reorder(val sources: List<RssSource>) : RssSourceManageAction
}

@Composable
internal fun RssSourceManageScreen(
    sources: List<RssSource>,
    groups: List<String>,
    query: String,
    selectedUrls: Set<String>,
    onAction: (RssSourceManageAction) -> Unit
) {
    val enabledFilter = stringResource(R.string.enabled)
    val disabledFilter = stringResource(R.string.disabled)
    val loginFilter = stringResource(R.string.need_login)
    val noGroupFilter = stringResource(R.string.no_group)
    val filterQueries = remember(
        enabledFilter,
        disabledFilter,
        loginFilter,
        noGroupFilter
    ) {
        setOf(enabledFilter, disabledFilter, loginFilter, noGroupFilter)
    }
    val searchQuery = query.takeUnless {
        it in filterQueries || it.startsWith(GROUP_QUERY_PREFIX)
    }.orEmpty()
    val scopeTitle = when {
        query.isBlank() -> stringResource(R.string.rss_source_scope_all)
        query == enabledFilter -> enabledFilter
        query == disabledFilter -> disabledFilter
        query == loginFilter -> loginFilter
        query == noGroupFilter -> noGroupFilter
        query.startsWith(GROUP_QUERY_PREFIX) -> query.substringAfter(GROUP_QUERY_PREFIX)
        else -> stringResource(R.string.rss_source_scope_search)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        RssSourceManageTopBar(
            query = searchQuery,
            activeQuery = query,
            groups = groups,
            onQueryChange = { onAction(RssSourceManageAction.QueryChanged(it)) },
            onBack = { onAction(RssSourceManageAction.Back) },
            onAction = onAction
        )
        RssSourceManagePanel(
            sources = sources,
            query = query,
            scopeTitle = scopeTitle,
            selectedUrls = selectedUrls,
            onAction = onAction,
            modifier = Modifier.weight(1f)
        )
        RssSourceManageBottomDock(
            selectedCount = selectedUrls.size,
            totalCount = sources.size,
            modifier = Modifier.padding(
                start = 14.dp,
                end = 14.dp,
                bottom = 8.dp
            ),
            onAction = onAction
        )
    }
}

@Composable
private fun RssSourceManageTopBar(
    query: String,
    activeQuery: String,
    groups: List<String>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAction: (RssSourceManageAction) -> Unit
) {
    val menuState = remember { NgPopupToggleState() }
    val enabledFilter = stringResource(R.string.enabled)
    val disabledFilter = stringResource(R.string.disabled)
    val loginFilter = stringResource(R.string.need_login)
    val noGroupFilter = stringResource(R.string.no_group)
    val actionContentColor = colorResource(R.color.ng_search_icon)
    val headerShape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val headerStyle = NgGlassDefaults.bookDetailStyle(
        containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface)
    )
    val menuItems = remember(
        groups,
        activeQuery,
        enabledFilter,
        disabledFilter,
        loginFilter,
        noGroupFilter
    ) {
        buildList {
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_add,
                    titleRes = R.string.add_rss_source,
                    iconRes = R.drawable.ic_add
                )
            )
            add(
                NgExpandableActionMenuItem(
                    itemId = FILTER_PARENT_ITEM_ID,
                    titleRes = R.string.screen,
                    iconRes = R.drawable.ic_screen,
                    children = listOf(
                        NgExpandableActionMenuItem(
                            itemId = FILTER_ALL_ITEM_ID,
                            titleRes = R.string.all,
                            iconRes = R.drawable.ic_select_all,
                            checked = activeQuery.isBlank()
                        ),
                        NgExpandableActionMenuItem(
                            itemId = FILTER_ENABLED_ITEM_ID,
                            titleRes = R.string.enabled,
                            iconRes = R.drawable.ic_check,
                            checked = activeQuery == enabledFilter
                        ),
                        NgExpandableActionMenuItem(
                            itemId = FILTER_DISABLED_ITEM_ID,
                            titleRes = R.string.disabled,
                            iconRes = R.drawable.ic_block_outline,
                            checked = activeQuery == disabledFilter
                        ),
                        NgExpandableActionMenuItem(
                            itemId = FILTER_LOGIN_ITEM_ID,
                            titleRes = R.string.need_login,
                            iconRes = R.drawable.ic_lock_outline,
                            checked = activeQuery == loginFilter
                        ),
                        NgExpandableActionMenuItem(
                            itemId = FILTER_NO_GROUP_ITEM_ID,
                            titleRes = R.string.no_group,
                            iconRes = R.drawable.ic_clear,
                            checked = activeQuery == noGroupFilter
                        )
                    )
                )
            )
            if (groups.isNotEmpty()) {
                add(
                    NgExpandableActionMenuItem(
                        itemId = GROUP_PARENT_ITEM_ID,
                        titleRes = R.string.group,
                        iconRes = R.drawable.ic_groups,
                        children = groups.mapIndexed { index, group ->
                            NgExpandableActionMenuItem(
                                itemId = GROUP_ITEM_ID_BASE + index,
                                titleRes = 0,
                                title = group,
                                iconRes = R.drawable.ic_groups,
                                checked = activeQuery == "$GROUP_QUERY_PREFIX$group"
                            )
                        }
                    )
                )
            }
            add(
                NgExpandableActionMenuItem(
                    itemId = IMPORT_PARENT_ITEM_ID,
                    titleRes = R.string.import_rss_source,
                    iconRes = R.drawable.ic_import,
                    children = listOf(
                        NgExpandableActionMenuItem(
                            itemId = R.id.menu_import_local,
                            titleRes = R.string.import_local,
                            iconRes = R.drawable.ic_folder_open
                        ),
                        NgExpandableActionMenuItem(
                            itemId = R.id.menu_import_onLine,
                            titleRes = R.string.import_on_line,
                            iconRes = R.drawable.ic_outline_cloud_24
                        ),
                        NgExpandableActionMenuItem(
                            itemId = R.id.menu_import_qr,
                            titleRes = R.string.import_by_qr_code,
                            iconRes = R.drawable.ic_scan
                        ),
                        NgExpandableActionMenuItem(
                            itemId = R.id.menu_import_default,
                            titleRes = R.string.import_default_rule,
                            iconRes = R.drawable.ic_restore
                        )
                    )
                )
            )
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_group_manage,
                    titleRes = R.string.group_manage,
                    iconRes = R.drawable.ic_groups,
                    dividerBefore = true
                )
            )
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_help,
                    titleRes = R.string.help,
                    iconRes = R.drawable.ic_help
                )
            )
        }
    }
    val defaultExpandedItemIds = remember(
        activeQuery,
        enabledFilter,
        disabledFilter,
        loginFilter,
        noGroupFilter
    ) {
        when {
            activeQuery in setOf(
                enabledFilter,
                disabledFilter,
                loginFilter,
                noGroupFilter
            ) -> setOf(FILTER_PARENT_ITEM_ID)

            activeQuery.startsWith(GROUP_QUERY_PREFIX) -> setOf(GROUP_PARENT_ITEM_ID)
            else -> emptySet()
        }
    }

    NgGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp),
        role = NgMaterialRole.CONTROL,
        shape = headerShape,
        style = headerStyle
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NgFloatingToolbarBackButton(onClick = onBack)
            NgSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                hint = stringResource(R.string.search_rss_source),
                modifier = Modifier.weight(1f),
                variant = NgSearchBarVariant.TOOLBAR,
                containerColor = Color.Transparent,
                hideHintOnFocus = true
            )
            Spacer(Modifier.width(8.dp))
            Box {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { menuState.onAnchorClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_grid_menu),
                        contentDescription = stringResource(R.string.menu),
                        tint = actionContentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                NgExpandableActionMenu(
                    expanded = menuState.expanded,
                    onDismissRequest = menuState::onDismissRequest,
                    items = menuItems,
                    defaultExpandedItemIds = defaultExpandedItemIds,
                    variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                    width = 172.dp,
                    menuContainerColor = colorResource(R.color.ng_surface_card),
                    properties = PopupProperties(
                        focusable = true,
                        clippingEnabled = false
                    ),
                    onItemClick = { item ->
                        menuState.close()
                        when (item.itemId) {
                            FILTER_ALL_ITEM_ID -> {
                                onAction(RssSourceManageAction.QueryChanged(""))
                            }

                            FILTER_ENABLED_ITEM_ID -> {
                                onAction(RssSourceManageAction.QueryChanged(enabledFilter))
                            }

                            FILTER_DISABLED_ITEM_ID -> {
                                onAction(RssSourceManageAction.QueryChanged(disabledFilter))
                            }

                            FILTER_LOGIN_ITEM_ID -> {
                                onAction(RssSourceManageAction.QueryChanged(loginFilter))
                            }

                            FILTER_NO_GROUP_ITEM_ID -> {
                                onAction(RssSourceManageAction.QueryChanged(noGroupFilter))
                            }

                            R.id.menu_add -> onAction(RssSourceManageAction.Add)
                            R.id.menu_import_local -> onAction(RssSourceManageAction.ImportLocal)
                            R.id.menu_import_onLine -> onAction(RssSourceManageAction.ImportOnline)
                            R.id.menu_import_qr -> onAction(RssSourceManageAction.ImportQr)
                            R.id.menu_import_default -> onAction(RssSourceManageAction.ImportDefault)
                            R.id.menu_group_manage -> onAction(RssSourceManageAction.ManageGroups)
                            R.id.menu_help -> onAction(RssSourceManageAction.Help)
                            else -> {
                                val index = item.itemId - GROUP_ITEM_ID_BASE
                                groups.getOrNull(index)?.let { group ->
                                    onAction(
                                        RssSourceManageAction.QueryChanged(
                                            "$GROUP_QUERY_PREFIX$group"
                                        )
                                    )
                                }
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RssSourceManagePanel(
    sources: List<RssSource>,
    query: String,
    scopeTitle: String,
    selectedUrls: Set<String>,
    onAction: (RssSourceManageAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val sourceVersions = sources.map { System.identityHashCode(it) }
    var orderedSources by remember(sourceVersions) { mutableStateOf(sources) }
    val reorderState = rememberNgLazyReorderState(
        onMove = { from, to ->
            if (query.isBlank() && from in orderedSources.indices &&
                to in orderedSources.indices
            ) {
                orderedSources = orderedSources.toMutableList().apply {
                    add(to, removeAt(from))
                }
            }
        },
        onFinished = {
            if (query.isBlank()) {
                onAction(RssSourceManageAction.Reorder(orderedSources))
            }
        }
    )
    val slideSelectState = rememberNgLazySlideSelectState(
        listState = reorderState.listState,
        isSelected = { index ->
            orderedSources.getOrNull(index)
                ?.sourceUrl
                ?.let(selectedUrls::contains) == true
        },
        onSelectionChange = { index, selected ->
            orderedSources.getOrNull(index)?.let { source ->
                onAction(RssSourceManageAction.SelectionChanged(source, selected))
            }
        }
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp)
    ) {
        val headerHeight = 44.dp
        val dividerHeight = 0.6.dp
        val bodyHeight = if (orderedSources.isEmpty()) {
            132.dp
        } else {
            70.dp * orderedSources.size.toFloat() +
                dividerHeight * (orderedSources.size - 1).coerceAtLeast(0).toFloat()
        }
        val panelHeight = minOf(maxHeight, headerHeight + dividerHeight + bodyHeight)
        NgManagementDrawerPanel(
            modifier = Modifier.height(panelHeight),
            variant = NgManagementDrawerPanelVariant.COMPACT
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = scopeTitle,
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 15.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(
                        R.string.rss_source_count,
                        orderedSources.size
                    ),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )
            }
            HorizontalDivider(
                thickness = dividerHeight,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f)
            )
            if (orderedSources.isEmpty()) {
                RssEmptyState(
                    text = stringResource(R.string.empty),
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .ngSlideSelect(
                            state = slideSelectState,
                            enabled = orderedSources.isNotEmpty() &&
                                !reorderState.isDragging,
                            slideAreaStart = 0.dp,
                            slideAreaEnd = 48.dp
                        ),
                    state = reorderState.listState
                ) {
                    itemsIndexed(
                        items = orderedSources,
                        key = { _, source -> source.sourceUrl }
                    ) { index, source ->
                        RssSourceManageRow(
                            source = source,
                            selected = source.sourceUrl in selectedUrls,
                            showDivider = index < orderedSources.lastIndex,
                            onAction = onAction,
                            modifier = Modifier.ngDraggedItem(
                                reorderState,
                                source.sourceUrl
                            ),
                            bodyDragModifier = if (query.isBlank()) {
                                Modifier.ngReorderAfterLongPress(
                                    state = reorderState,
                                    key = source.sourceUrl,
                                    enabled = true,
                                    contentDescription = stringResource(R.string.sort)
                                )
                            } else {
                                null
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceFilterChip(text: String, onClick: (String) -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick(text) },
        shape = RoundedCornerShape(14.dp),
        color = Color(NgTheme.colors.surfaceContainerLow)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = Color(NgTheme.colors.onSurface),
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun RssSourceManageRow(
    source: RssSource,
    selected: Boolean,
    showDivider: Boolean,
    onAction: (RssSourceManageAction) -> Unit,
    modifier: Modifier,
    bodyDragModifier: Modifier?
) {
    var menuExpanded by remember(source.sourceUrl) { mutableStateOf(false) }
    val dynamicAddress = stringResource(R.string.rss_source_dynamic_address)
    val summary = remember(source.sourceUrl, source.sourceGroup, dynamicAddress) {
        source.managementSummary(dynamicAddress)
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = {
                    onAction(RssSourceManageAction.SelectionChanged(source, it))
                }
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bodyDragModifier ?: Modifier),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RssSourceBadge(source = source)
                Spacer(Modifier.width(12.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onAction(RssSourceManageAction.Edit(source)) }
                        .padding(vertical = 9.dp)
                ) {
                    Text(
                        text = source.sourceName,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = summary,
                        modifier = Modifier.padding(top = 2.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            NgSwitchControl(
                checked = source.enabled,
                onCheckedChange = {
                    onAction(RssSourceManageAction.ToggleEnabled(source, it))
                },
                modifier = Modifier.padding(horizontal = 2.dp),
                variant = NgSwitchControlVariant.COMPACT
            )
            Box {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { menuExpanded = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.menu),
                        tint = Color(NgTheme.colors.onSurface),
                        modifier = Modifier.size(20.dp)
                    )
                }
                NgExpandableActionMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    items = listOf(
                        NgExpandableActionMenuItem(
                            R.id.menu_edit,
                            R.string.edit,
                            R.drawable.ic_edit
                        ),
                        NgExpandableActionMenuItem(
                            R.id.menu_top,
                            R.string.to_top,
                            R.drawable.ic_arrow_drop_up
                        ),
                        NgExpandableActionMenuItem(
                            R.id.menu_bottom,
                            R.string.to_bottom,
                            R.drawable.ic_arrow_down
                        ),
                        NgExpandableActionMenuItem(
                            R.id.menu_del,
                            R.string.delete,
                            R.drawable.ic_outline_delete,
                            dividerBefore = true
                        )
                    ),
                    onItemClick = {
                        menuExpanded = false
                        onAction(
                            when (it.itemId) {
                                R.id.menu_edit -> RssSourceManageAction.Edit(source)
                                R.id.menu_top -> RssSourceManageAction.Top(source)
                                R.id.menu_bottom -> RssSourceManageAction.Bottom(source)
                                else -> RssSourceManageAction.Delete(source)
                            }
                        )
                    },
                    width = 152.dp
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 60.dp, end = 12.dp),
                thickness = 0.6.dp,
                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f)
            )
        }
    }
}

@Composable
private fun RssSourceBadge(source: RssSource) {
    val colors = NgTheme.colors
    val paletteIndex = Math.floorMod(source.sourceUrl.hashCode(), 4)
    val containerColor = when (paletteIndex) {
        0 -> Color(colors.primaryContainer)
        1 -> colorResource(R.color.ng_warning_container)
        2 -> colorResource(R.color.ng_info_container)
        else -> colorResource(R.color.ng_success_container)
    }
    val textColor = when (paletteIndex) {
        0 -> Color(colors.primary)
        1 -> colorResource(R.color.ng_warning)
        2 -> colorResource(R.color.ng_info)
        else -> colorResource(R.color.ng_success)
    }
    val monogram = source.sourceName.trim().take(1).ifBlank { "R" }
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .semantics { contentDescription = source.sourceName },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = monogram,
            color = textColor,
            fontSize = 17.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        )
    }
}

private fun RssSource.managementSummary(dynamicAddress: String): String {
    val address = when {
        sourceUrl.contains("@js:", ignoreCase = true) -> dynamicAddress
        else -> runCatching { Uri.parse(sourceUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            ?.takeIf(String::isNotBlank)
            ?: sourceUrl.take(48)
    }
    return listOfNotNull(
        address.takeIf(String::isNotBlank),
        sourceGroup?.trim()?.takeIf(String::isNotBlank)
    ).joinToString(" · ")
}

@Composable
internal fun RssSourceTextDialog(
    title: String,
    initialValue: String = "",
    placeholder: String? = null,
    suggestions: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NgFormField(
                    label = title,
                    value = value,
                    onValueChange = { value = it },
                    placeholder = placeholder ?: title,
                    variant = NgFormFieldVariant.PLAIN_UNDERLINE
                )
                if (suggestions.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        suggestions.forEach { suggestion ->
                            SourceFilterChip(suggestion) { value = it }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
internal fun RssSourceConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
