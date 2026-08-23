package io.legado.app.ui.book.character

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Female
import androidx.compose.material.icons.outlined.Male
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgTheme

enum class BookCharacterTtsPage {
    FORMAL,
    TEMPORARY,
    DEFAULTS,
}

internal enum class BookCharacterTtsRowKind {
    FORMAL,
    TEMPORARY,
    DEFAULT,
}

internal enum class BookCharacterTtsGender {
    MALE,
    FEMALE,
    UNKNOWN,
}

internal enum class BookCharacterDeleteMode {
    DELETE_ONLY,
    DELETE_AND_DISABLE,
}

internal data class BookCharacterTtsUiRow(
    val key: String,
    val title: String,
    val avatar: String,
    val gender: BookCharacterTtsGender,
    val roleLabel: String?,
    val voiceSummary: String,
    val kind: BookCharacterTtsRowKind,
)

internal data class DisabledRoleUiItem(
    val key: String,
    val name: String,
    val summary: String,
)

@Composable
internal fun BookCharacterTtsScreen(
    page: BookCharacterTtsPage,
    rows: List<BookCharacterTtsUiRow>,
    formalCount: Int,
    temporaryCount: Int,
    reassigning: Boolean,
    disabledRoles: List<DisabledRoleUiItem>,
    disabledRoleDialogVisible: Boolean,
    routeWarningVisible: Boolean,
    scrollTargetKey: String?,
    scrollToTopSignal: Int,
    onBack: () -> Unit,
    onPageSelected: (BookCharacterTtsPage) -> Unit,
    onAdd: () -> Unit,
    onReassign: () -> Unit,
    onShowDisabledRoles: () -> Unit,
    onDismissDisabledRoles: () -> Unit,
    onReenableDisabledRoles: (Set<String>) -> Unit,
    onDeleteDisabledRecords: (Set<String>) -> Unit,
    onRowClick: (String) -> Unit,
    onVoiceClick: (String) -> Unit,
    onPromote: (String) -> Unit,
    onDeleteRequested: (Set<String>, BookCharacterDeleteMode) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveFinished: () -> Unit,
    onScrollTargetConsumed: (String) -> Unit,
) {
    var selectionMode by remember(page) { mutableStateOf(false) }
    var selectedKeys by remember(page) { mutableStateOf(setOf<String>()) }
    var pendingDeleteKeys by remember { mutableStateOf(setOf<String>()) }
    val selectableKeys = rows.asSequence()
        .filter { it.kind != BookCharacterTtsRowKind.DEFAULT }
        .mapTo(linkedSetOf()) { it.key }

    BackHandler(enabled = selectionMode) {
        selectionMode = false
        selectedKeys = emptySet()
    }
    LaunchedEffect(selectableKeys) {
        selectedKeys = selectedKeys.intersect(selectableKeys)
        if (selectionMode && selectableKeys.isEmpty()) selectionMode = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        CharacterTopBar(
            page = page,
            reassigning = reassigning,
            hasFormalRoles = formalCount > 0,
            hasTemporaryRoles = temporaryCount > 0,
            disabledRoleCount = disabledRoles.size,
            selectionMode = selectionMode,
            onBack = {
                if (selectionMode) {
                    selectionMode = false
                    selectedKeys = emptySet()
                } else {
                    onBack()
                }
            },
            onAdd = onAdd,
            onEnterSelection = {
                selectionMode = true
                selectedKeys = emptySet()
            },
            onReassign = onReassign,
            onShowDisabledRoles = onShowDisabledRoles,
        )
        if (routeWarningVisible) {
            RouteWarning()
        }
        CharacterList(
            page = page,
            rows = rows,
            scrollTargetKey = scrollTargetKey,
            scrollToTopSignal = scrollToTopSignal,
            selectionMode = selectionMode,
            selectedKeys = selectedKeys,
            onToggleSelection = { key ->
                selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
            },
            onEnterSelection = { key ->
                selectionMode = true
                selectedKeys = selectedKeys + key
            },
            onRowClick = onRowClick,
            onVoiceClick = onVoiceClick,
            onPromote = onPromote,
            onDeleteRequested = { key -> pendingDeleteKeys = setOf(key) },
            onMove = onMove,
            onMoveFinished = onMoveFinished,
            onScrollTargetConsumed = onScrollTargetConsumed,
            modifier = Modifier.weight(1f),
        )
        if (selectionMode) {
            CharacterSelectionDock(
                selectedCount = selectedKeys.size,
                totalCount = selectableKeys.size,
                onSelectAll = { selectedKeys = selectableKeys },
                onInvertSelection = { selectedKeys = selectableKeys - selectedKeys },
                onDelete = { if (selectedKeys.isNotEmpty()) pendingDeleteKeys = selectedKeys },
            )
        } else {
            CharacterPageDock(
                page = page,
                formalCount = formalCount,
                temporaryCount = temporaryCount,
                onPageSelected = onPageSelected,
            )
        }
    }

    if (pendingDeleteKeys.isNotEmpty()) {
        CharacterDeleteChoiceDialog(
            count = pendingDeleteKeys.size,
            onDismiss = { pendingDeleteKeys = emptySet() },
            onDeleteOnly = {
                val keys = pendingDeleteKeys
                pendingDeleteKeys = emptySet()
                selectionMode = false
                selectedKeys = emptySet()
                onDeleteRequested(keys, BookCharacterDeleteMode.DELETE_ONLY)
            },
            onDeleteAndDisable = {
                val keys = pendingDeleteKeys
                pendingDeleteKeys = emptySet()
                selectionMode = false
                selectedKeys = emptySet()
                onDeleteRequested(keys, BookCharacterDeleteMode.DELETE_AND_DISABLE)
            },
        )
    }

    if (disabledRoleDialogVisible) {
        DisabledRoleDialog(
            roles = disabledRoles,
            onDismiss = onDismissDisabledRoles,
            onReenable = onReenableDisabledRoles,
            onDeleteRecords = onDeleteDisabledRecords,
        )
    }
}

@Composable
private fun CharacterTopBar(
    page: BookCharacterTtsPage,
    reassigning: Boolean,
    hasFormalRoles: Boolean,
    hasTemporaryRoles: Boolean,
    disabledRoleCount: Int,
    selectionMode: Boolean,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEnterSelection: () -> Unit,
    onReassign: () -> Unit,
    onShowDisabledRoles: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val disabledRolesTitle = stringResource(
        R.string.character_disabled_count,
        disabledRoleCount,
    )
    val menuItems = buildList {
        if (page == BookCharacterTtsPage.FORMAL) {
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_add,
                    titleRes = R.string.add_character,
                    iconRes = R.drawable.ic_add,
                ),
            )
        }
        val hasDeletableRoles = when (page) {
            BookCharacterTtsPage.FORMAL -> hasFormalRoles
            BookCharacterTtsPage.TEMPORARY -> hasTemporaryRoles
            BookCharacterTtsPage.DEFAULTS -> false
        }
        if (hasDeletableRoles) {
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_batch_manage,
                    titleRes = R.string.character_batch_manage,
                    iconRes = R.drawable.ic_select_all,
                ),
            )
        }
        if (page == BookCharacterTtsPage.TEMPORARY && hasTemporaryRoles) {
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_refresh,
                    titleRes = R.string.character_reassign,
                    iconRes = R.drawable.ic_swap_horiz,
                ),
            )
        }
        if (page != BookCharacterTtsPage.DEFAULTS) {
            add(
                NgExpandableActionMenuItem(
                    itemId = R.id.menu_restore_temporary,
                    titleRes = R.string.character_disabled_manage,
                    iconRes = R.drawable.ic_block_outline,
                    title = disabledRolesTitle,
                ),
            )
        }
    }
    LaunchedEffect(page, selectionMode) {
        menuExpanded = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            color = colorResource(R.color.ng_surface_card),
            shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
            tonalElevation = 0.dp,
            shadowElevation = 1.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .width(34.dp)
                        .height(36.dp)
                        .clickable(role = Role.Button, onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left_search),
                        contentDescription = stringResource(R.string.back),
                        tint = Color(NgTheme.colors.onTopBar),
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = stringResource(
                        if (selectionMode) R.string.character_selection_title else R.string.book_characters,
                    ),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    color = Color(NgTheme.colors.onTopBar),
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (selectionMode) {
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(36.dp)
                            .clickable(role = Role.Button, onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.character_selection_done),
                            color = Color(NgTheme.colors.primary),
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }
                } else if (menuItems.isNotEmpty()) {
                    Box {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(role = Role.Button) { menuExpanded = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_grid_menu),
                                contentDescription = stringResource(R.string.menu),
                                tint = Color(NgTheme.colors.onTopBar),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        NgExpandableActionMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            items = menuItems,
                            rowMinHeight = 44.dp,
                            variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                            menuContainerColor = colorResource(R.color.ng_surface_card),
                            properties = PopupProperties(
                                focusable = true,
                                clippingEnabled = false,
                            ),
                            onItemClick = { item ->
                                menuExpanded = false
                                when (item.itemId) {
                                    R.id.menu_add -> onAdd()
                                    R.id.menu_batch_manage -> onEnterSelection()
                                    R.id.menu_refresh -> if (!reassigning) onReassign()
                                    R.id.menu_restore_temporary -> onShowDisabledRoles()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteWarning() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        color = colorResource(R.color.ng_warning_container),
        shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
    ) {
        Text(
            text = stringResource(R.string.character_tts_route_fallback),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            color = colorResource(R.color.ng_warning),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun CharacterList(
    page: BookCharacterTtsPage,
    rows: List<BookCharacterTtsUiRow>,
    scrollTargetKey: String?,
    scrollToTopSignal: Int,
    selectionMode: Boolean,
    selectedKeys: Set<String>,
    onToggleSelection: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onRowClick: (String) -> Unit,
    onVoiceClick: (String) -> Unit,
    onPromote: (String) -> Unit,
    onDeleteRequested: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onMoveFinished: () -> Unit,
    onScrollTargetConsumed: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val reorderState = rememberNgLazyReorderState(
        listState = listState,
        onMove = onMove,
        onFinished = onMoveFinished,
    )

    LaunchedEffect(page, scrollToTopSignal) {
        if (rows.isNotEmpty()) listState.scrollToItem(0)
    }
    LaunchedEffect(scrollTargetKey, rows) {
        val target = scrollTargetKey ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == target }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            onScrollTargetConsumed(target)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        if (rows.isEmpty() && page != BookCharacterTtsPage.DEFAULTS) {
            Text(
                text = stringResource(
                    if (page == BookCharacterTtsPage.FORMAL) {
                        R.string.book_character_empty
                    } else {
                        R.string.character_temporary_empty
                    },
                ),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    top = 4.dp,
                    end = 14.dp,
                    bottom = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(rows, key = { it.key }) { row ->
                    val reorderable = page == BookCharacterTtsPage.FORMAL &&
                        row.kind == BookCharacterTtsRowKind.FORMAL
                    NgSwipeToDelete(
                        deletable = !selectionMode && row.kind != BookCharacterTtsRowKind.DEFAULT,
                        reordering = reorderState.isDragging,
                        onDeleteRequested = { onDeleteRequested(row.key) },
                        modifier = Modifier
                            .ngDraggedItem(reorderState, row.key)
                            .ngReorderHandle(
                                state = reorderState,
                                key = row.key,
                                enabled = reorderable && !selectionMode,
                                contentDescription = if (reorderable) row.title else null,
                            ),
                    ) {
                        CharacterCard(
                            row = row,
                            selectionMode = selectionMode,
                            selected = row.key in selectedKeys,
                            onClick = {
                                if (selectionMode) onToggleSelection(row.key) else onRowClick(row.key)
                            },
                            onLongClick = {
                                if (row.kind != BookCharacterTtsRowKind.DEFAULT) {
                                    onEnterSelection(row.key)
                                }
                            },
                            onVoiceClick = {
                                if (!selectionMode) onVoiceClick(row.key)
                            },
                            onPromote = {
                                if (!selectionMode) onPromote(row.key)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CharacterCard(
    row: BookCharacterTtsUiRow,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onPromote: () -> Unit,
) {
    val shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(70.dp)
            .testTag("character_role_card_${row.key}")
            .then(
                if (selected) {
                    Modifier.border(1.5.dp, Color(NgTheme.colors.primary), shape)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .semantics { this.selected = selected }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = colorResource(R.color.ng_surface_card),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CharacterAvatar(row)
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.title,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    GenderIcon(row.gender)
                    row.roleLabel?.takeIf { it.isNotBlank() }?.let { label ->
                        NgStatusTag(
                            text = label,
                            variant = NgStatusTagVariant.INFO,
                            style = NgStatusTagStyle.TTS_ROLE,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Text(
                    text = row.voiceSummary,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable {
                            if (selectionMode) onClick() else onVoiceClick()
                        },
                    color = colorResource(R.color.tv_text_summary),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.size(48.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(NgTheme.colors.primary),
                        checkmarkColor = Color.White,
                    ),
                )
            } else if (row.kind == BookCharacterTtsRowKind.TEMPORARY) {
                PromoteButton(onClick = onPromote)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right_20),
                    contentDescription = null,
                    tint = Color(NgTheme.colors.onSurfaceVariant),
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(20.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun CharacterAvatar(row: BookCharacterTtsUiRow) {
    val containerColor = when {
        row.kind == BookCharacterTtsRowKind.DEFAULT &&
            row.gender == BookCharacterTtsGender.UNKNOWN -> {
            colorResource(R.color.character_avatar_narrator)
        }

        row.gender == BookCharacterTtsGender.MALE -> colorResource(R.color.character_avatar_male)
        row.gender == BookCharacterTtsGender.FEMALE -> colorResource(R.color.character_avatar_female)
        else -> colorResource(R.color.character_avatar_unknown)
    }
    Surface(
        modifier = Modifier.size(40.dp),
        color = containerColor,
        shape = CircleShape,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = row.avatar,
                color = Color.White,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun GenderIcon(gender: BookCharacterTtsGender) {
    val icon: ImageVector
    val tint: Color
    when (gender) {
        BookCharacterTtsGender.MALE -> {
            icon = Icons.Outlined.Male
            tint = colorResource(R.color.ng_tts_gender_male)
        }

        BookCharacterTtsGender.FEMALE -> {
            icon = Icons.Outlined.Female
            tint = colorResource(R.color.ng_tts_gender_female)
        }

        BookCharacterTtsGender.UNKNOWN -> return
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = tint,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(17.dp),
    )
}

@Composable
private fun PromoteButton(onClick: () -> Unit) {
    val colors = NgTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .testTag("character_promote_button")
                .clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_character_promote),
                contentDescription = stringResource(R.string.character_promote_accessibility),
                tint = Color(colors.primary),
                modifier = Modifier.size(21.dp),
            )
        }
    }
}

@Composable
private fun CharacterSelectionDock(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .height(56.dp),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        border = BorderStroke(0.6.dp, colorResource(R.color.ng_card_stroke)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.character_selected_count, selectedCount),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(12.dp))
            CharacterSelectionActionButton(
                iconRes = R.drawable.ic_select_all,
                label = stringResource(R.string.select_all),
                enabled = totalCount > 0,
                onClick = onSelectAll,
            )
            Spacer(Modifier.width(8.dp))
            CharacterSelectionActionButton(
                iconRes = R.drawable.ic_refresh_black_24dp,
                label = stringResource(R.string.revert_selection),
                enabled = totalCount > 0,
                onClick = onInvertSelection,
            )
            Spacer(Modifier.width(8.dp))
            CharacterSelectionActionButton(
                iconRes = R.drawable.ic_book_info_delete,
                label = stringResource(R.string.delete),
                enabled = selectedCount > 0,
                danger = true,
                onClick = onDelete,
            )
        }
    }
}

@Composable
private fun RowScope.CharacterSelectionActionButton(
    iconRes: Int,
    label: String,
    enabled: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val contentColor = if (danger) Color.White else Color(NgTheme.colors.onSurface)
    NgButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(36.dp),
        enabled = enabled,
        variant = if (danger) NgButtonVariant.DANGER else NgButtonVariant.NEUTRAL,
        shapeVariant = NgButtonShapeVariant.SMALL_ROUNDED,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            color = contentColor,
            fontSize = 13.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CharacterPageDock(
    page: BookCharacterTtsPage,
    formalCount: Int,
    temporaryCount: Int,
    onPageSelected: (BookCharacterTtsPage) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .height(58.dp),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(0.6.dp, colorResource(R.color.ng_card_stroke)),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            CharacterPageTab(
                title = stringResource(R.string.character_page_formal),
                count = formalCount,
                selected = page == BookCharacterTtsPage.FORMAL,
                onClick = { onPageSelected(BookCharacterTtsPage.FORMAL) },
                modifier = Modifier.weight(1f),
            )
            CharacterPageTab(
                title = stringResource(R.string.character_page_temporary),
                count = temporaryCount,
                selected = page == BookCharacterTtsPage.TEMPORARY,
                onClick = { onPageSelected(BookCharacterTtsPage.TEMPORARY) },
                modifier = Modifier.weight(1f),
            )
            CharacterPageTab(
                title = stringResource(R.string.character_page_defaults),
                count = 3,
                selected = page == BookCharacterTtsPage.DEFAULTS,
                onClick = { onPageSelected(BookCharacterTtsPage.DEFAULTS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CharacterPageTab(
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .background(
                if (selected) {
                    colorResource(R.color.ng_tts_tag_blue_container)
                } else {
                    Color.Transparent
                },
            )
            .clickable(role = Role.Tab, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val textColor = if (selected) {
            colorResource(R.color.ng_tts_tag_blue)
        } else {
            Color(NgTheme.colors.onSurface)
        }
        Text(
            text = title,
            color = textColor,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 1,
        )
        Text(
            text = count.toString(),
            color = textColor,
            fontSize = 14.sp,
            lineHeight = 17.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun CharacterDeleteChoiceDialog(
    count: Int,
    onDismiss: () -> Unit,
    onDeleteOnly: () -> Unit,
    onDeleteAndDisable: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = stringResource(R.string.character_delete_choice_title, count),
            variant = NgDialogVariant.CONFIRMATION,
            actions = {
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(84.dp)
                        .height(40.dp),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp)
                }
            },
        ) {
            DeleteChoiceOption(
                title = stringResource(R.string.character_delete_only_action),
                summary = stringResource(R.string.character_delete_only_summary),
                iconRes = R.drawable.ic_book_info_delete,
                titleColor = colorResource(R.color.ng_error),
                onClick = onDeleteOnly,
            )
            Spacer(Modifier.height(8.dp))
            DeleteChoiceOption(
                title = stringResource(R.string.character_delete_and_disable_action),
                summary = stringResource(R.string.character_delete_and_disable_summary),
                iconRes = R.drawable.ic_block_outline,
                titleColor = Color(NgTheme.colors.primary),
                onClick = onDeleteAndDisable,
            )
        }
    }
}

@Composable
private fun DeleteChoiceOption(
    title: String,
    summary: String,
    iconRes: Int,
    titleColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp),
        onClick = onClick,
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
        border = BorderStroke(0.6.dp, colorResource(R.color.ng_card_stroke)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = titleColor,
                modifier = Modifier.size(22.dp),
            )
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f),
            ) {
                Text(
                    text = title,
                    color = titleColor,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 3.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
            }
        }
    }
}

@Composable
private fun DisabledRoleDialog(
    roles: List<DisabledRoleUiItem>,
    onDismiss: () -> Unit,
    onReenable: (Set<String>) -> Unit,
    onDeleteRecords: (Set<String>) -> Unit,
) {
    var selectedKeys by remember(roles.map { it.key }) { mutableStateOf(setOf<String>()) }
    val allSelected = roles.isNotEmpty() && selectedKeys.size == roles.size
    val configuration = LocalConfiguration.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        NgDialog(
            title = stringResource(R.string.character_disabled_manage),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .heightIn(max = (configuration.screenHeightDp * 0.86f).dp),
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                if (roles.isNotEmpty()) {
                    TextButton(
                        onClick = { onDeleteRecords(selectedKeys) },
                        enabled = selectedKeys.isNotEmpty(),
                    ) {
                        Text(
                            text = stringResource(R.string.character_disabled_delete_action),
                            color = if (selectedKeys.isNotEmpty()) {
                                colorResource(R.color.ng_error)
                            } else {
                                Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.45f)
                            },
                            fontSize = 14.sp,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
                NgButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .width(76.dp)
                        .height(40.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp,
                    ),
                    variant = NgButtonVariant.OUTLINE,
                ) {
                    Text(stringResource(R.string.cancel), fontSize = 14.sp)
                }
                if (roles.isNotEmpty()) {
                    NgButton(
                        onClick = { onReenable(selectedKeys) },
                        modifier = Modifier
                            .width(92.dp)
                            .height(40.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 8.dp,
                        ),
                        enabled = selectedKeys.isNotEmpty(),
                        variant = NgButtonVariant.PRIMARY,
                    ) {
                        Text(
                            text = stringResource(R.string.character_reenable_action),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            },
        ) {
            if (roles.isEmpty()) {
                Text(
                    text = stringResource(R.string.character_disabled_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            R.string.character_disabled_selection_summary,
                            selectedKeys.size,
                            roles.size,
                        ),
                        modifier = Modifier.weight(1f),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                    TextButton(
                        onClick = {
                            selectedKeys = if (allSelected) {
                                emptySet()
                            } else {
                                roles.mapTo(mutableSetOf()) { it.key }
                            }
                        },
                    ) {
                        Text(
                            text = stringResource(
                                if (allSelected) R.string.unselect_all else R.string.select_all,
                            ),
                            fontSize = 14.sp,
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = (configuration.screenHeightDp * 0.55f).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(roles, key = { it.key }) { role ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            onClick = {
                                selectedKeys = if (role.key in selectedKeys) {
                                    selectedKeys - role.key
                                } else {
                                    selectedKeys + role.key
                                }
                            },
                            color = colorResource(R.color.ng_surface_card),
                            shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp),
                            border = BorderStroke(0.6.dp, colorResource(R.color.ng_card_stroke)),
                        ) {
                            Row(
                                modifier = Modifier.padding(end = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = role.key in selectedKeys,
                                    onCheckedChange = null,
                                    modifier = Modifier.size(48.dp),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(NgTheme.colors.primary),
                                        checkmarkColor = Color.White,
                                    ),
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = role.name,
                                        color = Color(NgTheme.colors.onSurface),
                                        fontSize = 16.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = role.summary,
                                        modifier = Modifier.padding(top = 3.dp),
                                        color = Color(NgTheme.colors.onSurfaceVariant),
                                        fontSize = 13.sp,
                                        lineHeight = 17.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
