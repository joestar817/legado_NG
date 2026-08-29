package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.model.CheckSourceTaskState
import io.legado.app.model.CheckSourceTaskStatus
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckboxVariant
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.components.compose.ngSlideSelect
import io.legado.app.ui.design.components.compose.rememberNgLazySlideSelectState
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.splitNotBlank
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private const val ADD_JS_ITEM_ID = 0x56000001
private const val FILTER_STATUS_PARENT_ITEM_ID = 0x56000100
private const val FILTER_ALL_ITEM_ID = 0x56000101
private const val FILTER_GROUP_PARENT_ITEM_ID = 0x56000200
private const val GROUP_ITEM_ID_BASE = 0x56001000
private const val SORT_DIRECTION_ITEM_ID = 0x56003000
private const val GROUP_QUERY_PREFIX = "group:"

internal enum class BookSourceViewMode { LIST, GROUP }

internal sealed interface BookSourceManageAction {
    data object Back : BookSourceManageAction
    data object AddDeclarative : BookSourceManageAction
    data object AddJavaScript : BookSourceManageAction
    data object ImportLocal : BookSourceManageAction
    data object ImportOnline : BookSourceManageAction
    data object ImportQr : BookSourceManageAction
    data object ManageGroups : BookSourceManageAction
    data object ToggleSortDirection : BookSourceManageAction
    data object SelectAll : BookSourceManageAction
    data object InvertSelection : BookSourceManageAction
    data object ConfigureSelectionCapabilities : BookSourceManageAction
    data object AddSelectionToGroup : BookSourceManageAction
    data object ClearSelectionGroups : BookSourceManageAction
    data object AutoGroupSelection : BookSourceManageAction
    data object CheckSelection : BookSourceManageAction
    data object OpenCheckTask : BookSourceManageAction
    data object DismissCheckTask : BookSourceManageAction
    data object CompleteSelectionInterval : BookSourceManageAction
    data object TopSelection : BookSourceManageAction
    data object BottomSelection : BookSourceManageAction
    data object ExportOrShareSelection : BookSourceManageAction
    data object DeleteSelection : BookSourceManageAction
    data class QueryChanged(val query: String) : BookSourceManageAction
    data class ViewModeChanged(val mode: BookSourceViewMode) : BookSourceManageAction
    data class SortChanged(val sort: BookSourceSort) : BookSourceManageAction
    data class SelectionChanged(val source: BookSourcePart, val selected: Boolean) : BookSourceManageAction
    data class SectionSelectionChanged(val sources: List<BookSourcePart>, val selected: Boolean) : BookSourceManageAction
    data class ConfigureCapabilities(val source: BookSourcePart) : BookSourceManageAction
    data class Edit(val source: BookSourcePart) : BookSourceManageAction
    data class Login(val source: BookSourcePart) : BookSourceManageAction
    data class Search(val source: BookSourcePart) : BookSourceManageAction
    data class Debug(val source: BookSourcePart) : BookSourceManageAction
    data class Delete(val source: BookSourcePart) : BookSourceManageAction
    data class Top(val source: BookSourcePart) : BookSourceManageAction
    data class Bottom(val source: BookSourcePart) : BookSourceManageAction
    data class Reorder(val sources: List<BookSourcePart>) : BookSourceManageAction
    data class ToggleSection(val key: String) : BookSourceManageAction
    data class DeleteSection(
        val groupName: String?,
        val title: String,
        val sources: List<BookSourcePart>,
    ) : BookSourceManageAction
}

@Composable
internal fun BookSourceManageScreen(
    sources: List<BookSourcePart>,
    groups: List<String>,
    query: String,
    selectedUrls: Set<String>,
    viewMode: BookSourceViewMode,
    sort: BookSourceSort,
    sortAscending: Boolean,
    expandedSections: Set<String>,
    checkTaskState: CheckSourceTaskState,
    onAction: (BookSourceManageAction) -> Unit,
) {
    val enabledFilter = stringResource(R.string.enabled)
    val disabledFilter = stringResource(R.string.disabled)
    val loginFilter = stringResource(R.string.need_login)
    val noGroupFilter = stringResource(R.string.no_group)
    val enabledExploreFilter = stringResource(R.string.enabled_explore)
    val disabledExploreFilter = stringResource(R.string.disabled_explore)
    val specialQueries = remember(
        enabledFilter,
        disabledFilter,
        loginFilter,
        noGroupFilter,
        enabledExploreFilter,
        disabledExploreFilter,
    ) {
        setOf(
            enabledFilter,
            disabledFilter,
            loginFilter,
            noGroupFilter,
            enabledExploreFilter,
            disabledExploreFilter,
        )
    }
    val searchQuery = query.takeUnless {
        it in specialQueries || it.startsWith(GROUP_QUERY_PREFIX)
    }.orEmpty()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        BookSourceManageTopBar(
            query = searchQuery,
            activeQuery = query,
            groups = groups,
            sort = sort,
            sortAscending = sortAscending,
            viewMode = viewMode,
            onQueryChange = { onAction(BookSourceManageAction.QueryChanged(it)) },
            onBack = { onAction(BookSourceManageAction.Back) },
            onAction = onAction,
        )
        BookSourceManagePanel(
            sources = sources,
            query = query,
            selectedUrls = selectedUrls,
            viewMode = viewMode,
            sort = sort,
            expandedSections = expandedSections,
            onAction = onAction,
            modifier = Modifier.weight(1f),
        )
        if (checkTaskState.showManageEntry) {
            BookSourceCheckTaskEntry(
                state = checkTaskState,
                onOpen = { onAction(BookSourceManageAction.OpenCheckTask) },
                onDismiss = { onAction(BookSourceManageAction.DismissCheckTask) },
            )
        }
        BookSourceManageBottomDock(
            selectedCount = selectedUrls.size,
            totalCount = sources.distinctBy(BookSourcePart::bookSourceUrl).size,
            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
            onAction = onAction,
        )
    }
}

@Composable
private fun BookSourceCheckTaskEntry(
    state: CheckSourceTaskState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
) {
    val running = state.status == CheckSourceTaskStatus.RUNNING
    val text = if (running) {
        stringResource(
            R.string.book_source_check_manage_running,
            state.processedCount,
            state.totalCount,
            state.currentSourceName,
        )
    } else {
        stringResource(
            R.string.book_source_check_manage_completed,
            state.passedCount,
            state.failedCount,
            state.blockedCount,
        )
    }
    val actionText = stringResource(
        if (running) {
            R.string.book_source_check_return_to_task
        } else {
            R.string.book_source_check_view_results
        }
    )
    NgGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 6.dp, end = 14.dp, bottom = 2.dp),
        role = NgMaterialRole.CONTROL,
        shape = RoundedCornerShape(12.dp),
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_surface_card),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen),
        ) {
            if (running) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(state.progressFraction.coerceIn(0f, 1f))
                        .background(Color(NgTheme.colors.primary).copy(alpha = 0.16f)),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_source),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.primary),
                    modifier = Modifier.size(19.dp),
                )
                Text(
                    text = text,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = actionText,
                    color = Color(NgTheme.colors.primary),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                if (!running) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_baseline_close),
                            contentDescription = stringResource(R.string.close),
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(10.dp))
                }
            }
        }
    }
}

@Composable
private fun BookSourceManageTopBar(
    query: String,
    activeQuery: String,
    groups: List<String>,
    sort: BookSourceSort,
    sortAscending: Boolean,
    viewMode: BookSourceViewMode,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onAction: (BookSourceManageAction) -> Unit,
) {
    val filterMenuState = remember { NgPopupToggleState() }
    val mainMenuState = remember { NgPopupToggleState() }
    val enabledFilter = stringResource(R.string.enabled)
    val disabledFilter = stringResource(R.string.disabled)
    val loginFilter = stringResource(R.string.need_login)
    val noGroupFilter = stringResource(R.string.no_group)
    val enabledExploreFilter = stringResource(R.string.enabled_explore)
    val disabledExploreFilter = stringResource(R.string.disabled_explore)
    val statusFilterQueries = remember(
        enabledFilter,
        disabledFilter,
        loginFilter,
        enabledExploreFilter,
        disabledExploreFilter,
    ) {
        setOf(
            enabledFilter,
            disabledFilter,
            loginFilter,
            enabledExploreFilter,
            disabledExploreFilter,
        )
    }
    val filterItems = remember(
        groups,
        activeQuery,
        enabledFilter,
        disabledFilter,
        loginFilter,
        noGroupFilter,
        enabledExploreFilter,
        disabledExploreFilter,
    ) {
        listOf(
            NgExpandableActionMenuItem(
                FILTER_ALL_ITEM_ID,
                R.string.all,
                R.drawable.ic_select_all,
                checked = activeQuery.isBlank(),
            ),
            NgExpandableActionMenuItem(
                FILTER_STATUS_PARENT_ITEM_ID,
                R.string.book_source_filter_status,
                R.drawable.ic_check_circle_outline,
                children = listOf(
                    NgExpandableActionMenuItem(R.id.menu_enabled_group, R.string.enabled, R.drawable.ic_check, checked = activeQuery == enabledFilter),
                    NgExpandableActionMenuItem(R.id.menu_disabled_group, R.string.disabled, R.drawable.ic_block_outline, checked = activeQuery == disabledFilter),
                    NgExpandableActionMenuItem(R.id.menu_group_login, R.string.need_login, R.drawable.ic_lock_outline, checked = activeQuery == loginFilter),
                    NgExpandableActionMenuItem(R.id.menu_enabled_explore_group, R.string.enabled_explore, R.drawable.ic_bottom_explore_e, checked = activeQuery == enabledExploreFilter),
                    NgExpandableActionMenuItem(R.id.menu_disabled_explore_group, R.string.disabled_explore, R.drawable.ic_bottom_explore_e, checked = activeQuery == disabledExploreFilter),
                ),
            ),
            NgExpandableActionMenuItem(
                FILTER_GROUP_PARENT_ITEM_ID,
                R.string.book_source_filter_group,
                R.drawable.ic_groups,
                children = buildList {
                    add(
                        NgExpandableActionMenuItem(
                            R.id.menu_group_null,
                            R.string.no_group,
                            R.drawable.ic_clear,
                            checked = activeQuery == noGroupFilter,
                        )
                    )
                    groups.forEachIndexed { index, group ->
                        add(
                            NgExpandableActionMenuItem(
                                GROUP_ITEM_ID_BASE + index,
                                0,
                                R.drawable.ic_groups,
                                title = group,
                                checked = activeQuery == "$GROUP_QUERY_PREFIX$group",
                            )
                        )
                    }
                },
            ),
        )
    }
    val sortItems = remember(sort, sortAscending) {
        listOf(
            NgExpandableActionMenuItem(SORT_DIRECTION_ITEM_ID, R.string.sort_desc, R.drawable.ic_swap_vert, checked = !sortAscending),
            NgExpandableActionMenuItem(R.id.menu_sort_manual, R.string.sort_manual, R.drawable.ic_drag_handle, checked = sort == BookSourceSort.Default),
            NgExpandableActionMenuItem(R.id.menu_sort_auto, R.string.sort_auto, R.drawable.ic_sort, checked = sort == BookSourceSort.Weight),
            NgExpandableActionMenuItem(R.id.menu_sort_name, R.string.sort_by_name, R.drawable.ic_sort, checked = sort == BookSourceSort.Name),
            NgExpandableActionMenuItem(R.id.menu_sort_url, R.string.sort_by_url, R.drawable.ic_web_outline, checked = sort == BookSourceSort.Url),
            NgExpandableActionMenuItem(R.id.menu_sort_time, R.string.sort_by_lastUpdateTime, R.drawable.ic_history, checked = sort == BookSourceSort.Update),
            NgExpandableActionMenuItem(R.id.menu_sort_respondTime, R.string.sort_by_respondTime, R.drawable.ic_network_check, checked = sort == BookSourceSort.Respond),
            NgExpandableActionMenuItem(R.id.menu_sort_enable, R.string.is_enabled, R.drawable.ic_check_circle_outline, checked = sort == BookSourceSort.Enable),
        )
    }
    val mainMenuItems = remember(sortItems) {
        listOf(
            NgExpandableActionMenuItem(
                R.id.menu_add_book_source,
                R.string.add_book_source,
                R.drawable.ic_add,
            ),
            NgExpandableActionMenuItem(
                ADD_JS_ITEM_ID,
                R.string.book_source_add_javascript,
                R.drawable.ic_code,
            ),
            NgExpandableActionMenuItem(
                R.id.menu_import_local,
                R.string.import_local,
                R.drawable.ic_folder_open,
                dividerBefore = true,
            ),
            NgExpandableActionMenuItem(
                R.id.menu_import_onLine,
                R.string.import_on_line,
                R.drawable.ic_outline_cloud_24,
            ),
            NgExpandableActionMenuItem(
                R.id.menu_import_qr,
                R.string.import_by_qr_code,
                R.drawable.ic_scan,
            ),
            NgExpandableActionMenuItem(
                R.id.menu_group_manage,
                R.string.group_manage,
                R.drawable.ic_settings,
                dividerBefore = true,
            ),
            NgExpandableActionMenuItem(
                R.id.action_sort,
                R.string.sort,
                R.drawable.ic_sort,
                dividerBefore = true,
                children = sortItems,
            ),
        )
    }
    val filterDefaultExpandedItemIds = remember(
        activeQuery,
        noGroupFilter,
        statusFilterQueries,
    ) {
        when {
            activeQuery in statusFilterQueries -> setOf(FILTER_STATUS_PARENT_ITEM_ID)
            activeQuery == noGroupFilter || activeQuery.startsWith(GROUP_QUERY_PREFIX) -> {
                setOf(FILTER_GROUP_PARENT_ITEM_ID)
            }
            else -> emptySet()
        }
    }
    val filterActive = activeQuery in statusFilterQueries ||
        activeQuery == noGroupFilter || activeQuery.startsWith(GROUP_QUERY_PREFIX)
    val actionColor = colorResource(R.color.ng_search_icon)
    val activeActionColor = Color(NgTheme.colors.primary)
    NgGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp),
        role = NgMaterialRole.CONTROL,
        shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface)
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(start = 0.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFloatingToolbarBackButton(
                onClick = onBack,
                width = 32.dp,
            )
            Spacer(Modifier.width(12.dp))
            NgSearchBar(
                query = query,
                onQueryChange = onQueryChange,
                hint = stringResource(R.string.search_book_source),
                modifier = Modifier.weight(1f),
                variant = NgSearchBarVariant.TOOLBAR,
                containerColor = Color.Transparent,
                hideHintOnFocus = true,
            )
            Spacer(Modifier.width(8.dp))
            BookSourceTopBarPopupButton(
                iconRes = R.drawable.ic_screen,
                iconSize = 14.dp,
                contentDescription = stringResource(R.string.screen),
                tint = if (filterActive) activeActionColor else actionColor,
                state = filterMenuState,
                items = filterItems,
                defaultExpandedItemIds = filterDefaultExpandedItemIds,
                onItemClick = { item ->
                    when (item.itemId) {
                        FILTER_ALL_ITEM_ID -> onAction(BookSourceManageAction.QueryChanged(""))
                        R.id.menu_enabled_group -> onAction(BookSourceManageAction.QueryChanged(enabledFilter))
                        R.id.menu_disabled_group -> onAction(BookSourceManageAction.QueryChanged(disabledFilter))
                        R.id.menu_group_login -> onAction(BookSourceManageAction.QueryChanged(loginFilter))
                        R.id.menu_group_null -> onAction(BookSourceManageAction.QueryChanged(noGroupFilter))
                        R.id.menu_enabled_explore_group -> onAction(BookSourceManageAction.QueryChanged(enabledExploreFilter))
                        R.id.menu_disabled_explore_group -> onAction(BookSourceManageAction.QueryChanged(disabledExploreFilter))
                        else -> groups.getOrNull(item.itemId - GROUP_ITEM_ID_BASE)?.let {
                            onAction(BookSourceManageAction.QueryChanged("$GROUP_QUERY_PREFIX$it"))
                        }
                    }
                },
            )
            Spacer(Modifier.width(2.dp))
            BookSourceViewModeButton(
                viewMode = viewMode,
                tint = actionColor,
                onAction = onAction,
            )
            Spacer(Modifier.width(2.dp))
            BookSourceTopBarPopupButton(
                iconRes = R.drawable.ic_grid_menu,
                contentDescription = stringResource(R.string.menu),
                tint = actionColor,
                state = mainMenuState,
                items = mainMenuItems,
                onItemClick = { item ->
                    when (item.itemId) {
                        R.id.menu_add_book_source -> onAction(BookSourceManageAction.AddDeclarative)
                        ADD_JS_ITEM_ID -> onAction(BookSourceManageAction.AddJavaScript)
                        R.id.menu_import_local -> onAction(BookSourceManageAction.ImportLocal)
                        R.id.menu_import_onLine -> onAction(BookSourceManageAction.ImportOnline)
                        R.id.menu_import_qr -> onAction(BookSourceManageAction.ImportQr)
                        SORT_DIRECTION_ITEM_ID -> onAction(BookSourceManageAction.ToggleSortDirection)
                        R.id.menu_sort_manual -> onAction(BookSourceManageAction.SortChanged(BookSourceSort.Default))
                        R.id.menu_sort_auto -> onAction(BookSourceManageAction.SortChanged(BookSourceSort.Weight))
                        R.id.menu_sort_name -> onAction(BookSourceManageAction.SortChanged(BookSourceSort.Name))
                        R.id.menu_sort_url -> onAction(BookSourceManageAction.SortChanged(BookSourceSort.Url))
                        R.id.menu_sort_time -> onAction(BookSourceManageAction.SortChanged(BookSourceSort.Update))
                        R.id.menu_sort_respondTime -> onAction(BookSourceManageAction.SortChanged(BookSourceSort.Respond))
                        R.id.menu_sort_enable -> onAction(BookSourceManageAction.SortChanged(BookSourceSort.Enable))
                        R.id.menu_group_manage -> onAction(BookSourceManageAction.ManageGroups)
                    }
                },
            )
        }
    }
}

@Composable
private fun BookSourceTopBarActionButton(
    iconRes: Int,
    iconSize: Dp = 20.dp,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun BookSourceTopBarPopupButton(
    iconRes: Int,
    iconSize: Dp = 20.dp,
    contentDescription: String,
    tint: Color,
    state: NgPopupToggleState,
    items: List<NgExpandableActionMenuItem>,
    defaultExpandedItemIds: Set<Int> = emptySet(),
    onItemClick: (NgExpandableActionMenuItem) -> Unit,
) {
    Box {
        BookSourceTopBarActionButton(
            iconRes = iconRes,
            iconSize = iconSize,
            contentDescription = contentDescription,
            tint = tint,
            onClick = state::onAnchorClick,
        )
        NgExpandableActionMenu(
            expanded = state.expanded,
            onDismissRequest = state::onDismissRequest,
            items = items,
            defaultExpandedItemIds = defaultExpandedItemIds,
            variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
            menuContainerColor = colorResource(R.color.ng_surface_card),
            properties = PopupProperties(focusable = true, clippingEnabled = false),
            onItemClick = { item ->
                state.close()
                onItemClick(item)
            },
        )
    }
}

@Composable
private fun BookSourceViewModeButton(
    viewMode: BookSourceViewMode,
    tint: Color,
    onAction: (BookSourceManageAction) -> Unit,
) {
    val nextMode = when (viewMode) {
        BookSourceViewMode.LIST -> BookSourceViewMode.GROUP
        BookSourceViewMode.GROUP -> BookSourceViewMode.LIST
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clickable {
                onAction(BookSourceManageAction.ViewModeChanged(nextMode))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(
                when (viewMode) {
                    BookSourceViewMode.LIST -> R.drawable.ic_chapter_list
                    BookSourceViewMode.GROUP -> R.drawable.ic_groups
                }
            ),
            contentDescription = stringResource(
                when (nextMode) {
                    BookSourceViewMode.LIST -> R.string.book_source_view_list_title
                    BookSourceViewMode.GROUP -> R.string.book_source_view_group_title
                }
            ),
            tint = tint,
            modifier = Modifier.size(
                when (viewMode) {
                    BookSourceViewMode.LIST -> 20.dp
                    BookSourceViewMode.GROUP -> 17.dp
                }
            ),
        )
    }
}

@Composable
private fun BookSourceManagePanel(
    sources: List<BookSourcePart>,
    query: String,
    selectedUrls: Set<String>,
    viewMode: BookSourceViewMode,
    sort: BookSourceSort,
    expandedSections: Set<String>,
    onAction: (BookSourceManageAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    val panelStyle = NgGlassDefaults.bookDetailStyle(
        containerColor = colorResource(R.color.ng_surface_card)
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 4.dp, end = 14.dp, bottom = 8.dp)
    ) {
        when {
            sources.isEmpty() -> NgGlassSurface(
                modifier = Modifier.fillMaxSize(),
                role = NgMaterialRole.CONTENT,
                shape = panelShape,
                style = panelStyle,
                liquidCornerRadius = NgTheme.shapes.mediumDp.dp,
            ) {
                BookSourceEmptyState()
            }
            viewMode == BookSourceViewMode.GROUP -> BookSourceGroupedList(
                sources = sources,
                selectedUrls = selectedUrls,
                expandedSections = expandedSections,
                onAction = onAction,
            )
            else -> NgGlassSurface(
                modifier = Modifier.fillMaxSize(),
                role = NgMaterialRole.CONTENT,
                shape = panelShape,
                style = panelStyle,
                liquidCornerRadius = NgTheme.shapes.mediumDp.dp,
            ) {
                BookSourceFlatList(
                    sources = sources,
                    selectedUrls = selectedUrls,
                    canReorder = query.isBlank() && viewMode == BookSourceViewMode.LIST &&
                        sort == BookSourceSort.Default,
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun BookSourceEmptyState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.empty),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun BookSourceFlatList(
    sources: List<BookSourcePart>,
    selectedUrls: Set<String>,
    canReorder: Boolean,
    onAction: (BookSourceManageAction) -> Unit,
) {
    val sourceVersions = sources.map { System.identityHashCode(it) }
    var orderedSources by remember(sourceVersions) { mutableStateOf(sources) }
    val listState = rememberLazyListState()
    var isReordering by remember { mutableStateOf(false) }
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        if (canReorder && from.index in orderedSources.indices &&
            to.index in orderedSources.indices
        ) {
            orderedSources = orderedSources.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
        }
    }
    val slideSelectState = rememberNgLazySlideSelectState(
        listState = listState,
        isSelected = { index ->
            orderedSources.getOrNull(index)?.bookSourceUrl?.let(selectedUrls::contains) == true
        },
        onSelectionChange = { index, selected ->
            orderedSources.getOrNull(index)?.let {
                onAction(BookSourceManageAction.SelectionChanged(it, selected))
            }
        },
    )
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .ngSlideSelect(
                state = slideSelectState,
                enabled = orderedSources.isNotEmpty() && !isReordering,
                slideAreaStart = 0.dp,
                slideAreaEnd = 32.dp,
            ),
        state = listState,
    ) {
        itemsIndexed(
            items = orderedSources,
            key = { _, source -> source.bookSourceUrl },
        ) { index, source ->
            ReorderableItem(
                state = reorderState,
                key = source.bookSourceUrl,
            ) { _ ->
                val sortDescription = stringResource(R.string.sort)
                BookSourceManageRow(
                    source = source,
                    selected = source.bookSourceUrl in selectedUrls,
                    showDivider = index < orderedSources.lastIndex,
                    manualSort = canReorder,
                    onAction = onAction,
                    modifier = Modifier,
                    bodyDragModifier = if (canReorder) {
                        Modifier
                            .longPressDraggableHandle(
                                onDragStarted = { isReordering = true },
                                onDragStopped = {
                                    isReordering = false
                                    onAction(BookSourceManageAction.Reorder(orderedSources))
                                },
                            )
                            .semantics { contentDescription = sortDescription }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private data class BookSourceSectionUi(
    val key: String,
    val title: String,
    val sources: List<BookSourcePart>,
)

private data class BookSourceSectionKey(val key: String, val title: String)

@Composable
private fun BookSourceGroupedList(
    sources: List<BookSourcePart>,
    selectedUrls: Set<String>,
    expandedSections: Set<String>,
    onAction: (BookSourceManageAction) -> Unit,
) {
    val noGroup = stringResource(R.string.no_group)
    val sectionVersion = sources.map {
        Triple(it.bookSourceUrl, it.bookSourceName, it.bookSourceGroup)
    }
    val sections = remember(sectionVersion, noGroup) {
        sources
            .flatMap { source ->
                val sourceGroups = source.bookSourceGroup
                    ?.splitNotBlank(AppPattern.splitGroupRegex)
                    .orEmpty()
                if (sourceGroups.isEmpty()) {
                    listOf(BookSourceSectionKey("group:", noGroup) to source)
                } else {
                    sourceGroups.map { group ->
                        BookSourceSectionKey("group:$group", group) to source
                    }
                }
            }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
            .map { (section, groupedSources) ->
                BookSourceSectionUi(
                    key = section.key,
                    title = section.title,
                    sources = groupedSources.distinctBy(BookSourcePart::bookSourceUrl),
                )
            }
            .sortedWith(compareBy<BookSourceSectionUi> { it.key != "group:" }.thenBy { it.title })
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        sections.forEach { section ->
            item(key = "section:${section.key}") {
                BookSourceSectionHeader(
                    section = section,
                    expanded = section.key in expandedSections,
                    selected = section.sources.isNotEmpty() &&
                        section.sources.all { it.bookSourceUrl in selectedUrls },
                    onAction = onAction,
                )
            }
            if (section.key in expandedSections) {
                items(
                    items = section.sources,
                    key = { source -> "${section.key}:${source.bookSourceUrl}" },
                ) { source ->
                    BookSourceManageRow(
                        source = source,
                        selected = source.bookSourceUrl in selectedUrls,
                        showDivider = true,
                        manualSort = false,
                        onAction = onAction,
                        modifier = Modifier
                            .background(colorResource(R.color.ng_surface_card)),
                        bodyDragModifier = null,
                    )
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun BookSourceSectionHeader(
    section: BookSourceSectionUi,
    expanded: Boolean,
    selected: Boolean,
    onAction: (BookSourceManageAction) -> Unit,
) {
    val cardShape = RoundedCornerShape(10.dp)
    val cardStyle = NgGlassDefaults.bookDetailStyle(
        containerColor = colorResource(R.color.ng_surface_card)
    )
    Column {
        NgVisualSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            role = NgMaterialRole.CONTROL,
            cornerRadius = 10.dp,
            shape = cardShape,
            style = cardStyle,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .combinedClickable(
                        onClick = {
                            onAction(BookSourceManageAction.ToggleSection(section.key))
                        },
                        onLongClickLabel = stringResource(R.string.delete),
                        onLongClick = {
                            onAction(
                                BookSourceManageAction.DeleteSection(
                                    groupName = section.key
                                        .removePrefix(GROUP_QUERY_PREFIX)
                                        .takeIf(String::isNotBlank),
                                    title = section.title,
                                    sources = section.sources,
                                )
                            )
                        },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NgFileSelectionCheckbox(
                    checked = selected,
                    onCheckedChange = {
                        onAction(BookSourceManageAction.SectionSelectionChanged(section.sources, it))
                    },
                    variant = NgFileSelectionCheckboxVariant.COMPACT,
                )
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            colorResource(
                                if (section.title.isNormalSourceSection()) R.color.ng_success
                                else R.color.ng_error
                            ).copy(alpha = 0.9f)
                        )
                )
                Text(
                    text = section.title,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                    color = Color(NgTheme.colors.onSurface).copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    modifier = Modifier
                        .widthIn(min = 28.dp)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(NgTheme.colors.surfaceContainerLow)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = section.sources.size.toString(),
                        modifier = Modifier.padding(horizontal = 7.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.82f),
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable {
                            onAction(BookSourceManageAction.ToggleSection(section.key))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_right_20),
                        contentDescription = null,
                        tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.82f),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (expanded) 90f else 0f),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun BookSourceManageRow(
    source: BookSourcePart,
    selected: Boolean,
    showDivider: Boolean,
    manualSort: Boolean,
    onAction: (BookSourceManageAction) -> Unit,
    modifier: Modifier,
    bodyDragModifier: Modifier?,
) {
    val menuState = remember(source.bookSourceUrl) { NgPopupToggleState() }
    val titleColor = Color(NgTheme.colors.onSurface).copy(
        alpha = if (source.enabled) 1f else 0.52f,
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clickable {
                    onAction(BookSourceManageAction.SelectionChanged(source, !selected))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFileSelectionCheckbox(
                checked = selected,
                onCheckedChange = {
                    onAction(BookSourceManageAction.SelectionChanged(source, it))
                },
                variant = NgFileSelectionCheckboxVariant.COMPACT,
            )
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .then(bodyDragModifier ?: Modifier),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(start = 2.dp, end = 4.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = source.bookSourceName,
                        color = titleColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    source.formatTagLabel()?.let { labelRes ->
                        BookSourceMiniTag(
                            text = stringResource(labelRes),
                            containerColor = Color(NgTheme.colors.primaryContainer),
                            contentColor = Color(NgTheme.colors.primary),
                        )
                    }
                    BookSourceMiniTag(
                        text = stringResource(source.typeTagLabel()),
                        containerColor = Color(NgTheme.colors.primaryContainer),
                        contentColor = Color(NgTheme.colors.primary),
                    )
                    if (source.hasSearchUrl) {
                        BookSourceMiniTag(
                            text = stringResource(R.string.book_source_capability_search_short),
                            containerColor = colorResource(
                                if (source.enabled) R.color.ng_success_container else R.color.ng_error_container
                            ),
                            contentColor = colorResource(
                                if (source.enabled) R.color.ng_success else R.color.ng_error
                            ),
                        )
                    }
                    if (source.hasExploreUrl) {
                        BookSourceMiniTag(
                            text = stringResource(R.string.book_source_capability_explore_short),
                            containerColor = colorResource(
                                if (source.enabledExplore) R.color.ng_info_container else R.color.ng_error_container
                            ),
                            contentColor = colorResource(
                                if (source.enabledExplore) R.color.ng_info else R.color.ng_error
                            ),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        onAction(BookSourceManageAction.ConfigureCapabilities(source))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.book_source_switches),
                    tint = Color(NgTheme.colors.onSurface),
                    modifier = Modifier.size(18.dp),
                )
            }
            Box {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (menuState.expanded) {
                                Color(NgTheme.colors.selectedContainer)
                            } else {
                                Color.Transparent
                            }
                        )
                        .clickable { menuState.onAnchorClick() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_vert),
                        contentDescription = stringResource(R.string.menu),
                        tint = if (menuState.expanded) {
                            Color(NgTheme.colors.primary)
                        } else {
                            Color(NgTheme.colors.onSurface)
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
                NgExpandableActionMenu(
                    expanded = menuState.expanded,
                    onDismissRequest = menuState::onDismissRequest,
                    items = sourceMenuItems(source, manualSort),
                    onItemClick = {
                        menuState.close()
                        onAction(
                            when (it.itemId) {
                                R.id.menu_edit -> BookSourceManageAction.Edit(source)
                                R.id.menu_top -> BookSourceManageAction.Top(source)
                                R.id.menu_bottom -> BookSourceManageAction.Bottom(source)
                                R.id.menu_login -> BookSourceManageAction.Login(source)
                                R.id.menu_search -> BookSourceManageAction.Search(source)
                                R.id.menu_debug_source -> BookSourceManageAction.Debug(source)
                                else -> BookSourceManageAction.Delete(source)
                            }
                        )
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
private fun sourceMenuItems(
    source: BookSourcePart,
    manualSort: Boolean,
): List<NgExpandableActionMenuItem> = buildList {
    add(
        NgExpandableActionMenuItem(
            R.id.menu_edit,
            R.string.edit,
            R.drawable.ic_edit,
        )
    )
    if (source.hasSearchUrl) {
        add(NgExpandableActionMenuItem(R.id.menu_search, R.string.search, R.drawable.ic_search))
    }
    add(NgExpandableActionMenuItem(R.id.menu_debug_source, R.string.debug, R.drawable.ic_bug_report))
    if (source.hasLoginUrl) {
        add(NgExpandableActionMenuItem(R.id.menu_login, R.string.login, R.drawable.ic_lock_outline))
    }
    if (manualSort) {
        add(
            NgExpandableActionMenuItem(
                R.id.menu_top,
                R.string.to_top,
                R.drawable.ic_arrow_drop_up,
                dividerBefore = true,
            )
        )
        add(NgExpandableActionMenuItem(R.id.menu_bottom, R.string.to_bottom, R.drawable.ic_arrow_down))
    }
    add(
        NgExpandableActionMenuItem(
            R.id.menu_del,
            R.string.delete,
            R.drawable.ic_book_info_delete,
            dividerBefore = !manualSort,
            danger = true,
        )
    )
}

@Composable
private fun BookSourceMiniTag(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(shape = RoundedCornerShape(5.dp), color = containerColor) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            color = contentColor,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
        )
    }
}

private fun BookSourcePart.formatTagLabel(): Int? =
    R.string.book_source_format_js.takeIf { hasJs }

private fun BookSourcePart.typeTagLabel(): Int = when (bookSourceType) {
    BookSourceType.image -> R.string.book_source_tag_type_image
    BookSourceType.audio -> R.string.book_source_tag_type_audio
    BookSourceType.file -> R.string.book_source_tag_type_file
    BookSourceType.video -> R.string.book_source_tag_type_video
    else -> R.string.book_source_tag_type_text
}

private fun String.isNormalSourceSection(): Boolean {
    if (this == "校验超时") return false
    return listOf("失效", "异常", "错误", "无效", "规则为空").none(::contains)
}
