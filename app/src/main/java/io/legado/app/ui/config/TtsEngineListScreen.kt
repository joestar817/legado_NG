package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.ui.design.components.NgManagementTrailing
import io.legado.app.ui.design.components.NgStatusTagSpec
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgListState
import io.legado.app.ui.design.components.compose.NgListStateContent
import io.legado.app.ui.design.components.compose.NgManagementLeadingIcon
import io.legado.app.ui.design.components.compose.NgManagementListCard
import io.legado.app.ui.design.components.compose.NgManagementTrailingIcon
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.components.compose.NgLazyReorderState
import io.legado.app.ui.design.components.compose.ngDraggedItem
import io.legado.app.ui.design.components.compose.ngReorderHandle
import io.legado.app.ui.design.components.compose.rememberNgLazyReorderState
import io.legado.app.ui.design.theme.NgTheme

/** 朗读引擎管理页只用于渲染的数据，不持有 TTS Store 实体。 */
@Immutable
data class TtsEngineListItemUiModel(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val engineTypeText: String,
    val voiceCountText: String,
    val reorderable: Boolean,
    val deletable: Boolean,
    @param:DrawableRes val iconRes: Int = R.drawable.ic_ai_capability_tts,
    val actionContentDescription: String? = null
)

@Immutable
data class TtsEngineListScreenState(
    val query: String = "",
    val listState: NgListState<TtsEngineListItemUiModel> = NgListState.Loading,
    val showDisabled: Boolean = false,
)

internal class TtsEngineSnapshotGate {
    private var revision = 0L

    fun begin(): Long = ++revision

    fun invalidate() {
        revision++
    }

    fun isCurrent(token: Long): Boolean = token == revision
}

/**
 * 页面事件全部回传宿主。创建、导入、菜单、存储和排序合并均不在 Composable 内执行。
 *
 * 拖动过程只修改 Screen 内的可见顺序，松手后通过 [ReorderCommitted] 一次提交。
 */
sealed interface TtsEngineListAction {
    data class QueryChanged(val query: String) : TtsEngineListAction
    data class SearchSubmitted(val query: String) : TtsEngineListAction
    data object Retry : TtsEngineListAction
    data class OpenEngine(val engineId: String) : TtsEngineListAction
    data class ReorderCommitted(val orderedEngineIds: List<String>) : TtsEngineListAction
    data class DeleteRequested(val engineId: String) : TtsEngineListAction
    data object Back : TtsEngineListAction
    data object CreateEngine : TtsEngineListAction
    data object ImportLocal : TtsEngineListAction
    data object ImportOnline : TtsEngineListAction
    data object ToggleShowDisabled : TtsEngineListAction
}

@Composable
fun TtsEngineListScreen(
    state: TtsEngineListScreenState,
    onAction: (TtsEngineListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TtsEngineListFloatingTopBar(
            state = state,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        NgListStateContent(
            state = state.listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onRetry = { onAction(TtsEngineListAction.Retry) }
        ) { engines ->
            var orderedEngines by remember(engines) { mutableStateOf(engines) }
            val reorderState = rememberNgLazyReorderState(
                onMove = { fromIndex, toIndex ->
                    if (fromIndex in orderedEngines.indices &&
                        toIndex in orderedEngines.indices &&
                        fromIndex != toIndex
                    ) {
                        orderedEngines = orderedEngines.toMutableList().apply {
                            add(toIndex, removeAt(fromIndex))
                        }
                    }
                },
                onFinished = {
                    onAction(
                        TtsEngineListAction.ReorderCommitted(
                            orderedEngines.map(TtsEngineListItemUiModel::id)
                        )
                    )
                }
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = reorderState.listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 8.dp,
                    end = 16.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = orderedEngines,
                    key = TtsEngineListItemUiModel::id,
                    contentType = { "tts_engine" }
                ) { engine ->
                    TtsEngineListCard(
                        item = engine,
                        canReorder = state.canRequestReorder(engine),
                        reorderState = reorderState,
                        onAction = onAction,
                        modifier = Modifier
                    )
                }
            }
        }
    }
}

@Composable
private fun TtsEngineListFloatingTopBar(
    state: TtsEngineListScreenState,
    onAction: (TtsEngineListAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember(state.showDisabled) {
        listOf(
            NgExpandableActionMenuItem(
                itemId = R.id.menu_tts_engine_add,
                titleRes = R.string.add_tts_engine,
                iconRes = R.drawable.ic_add,
            ),
            NgExpandableActionMenuItem(
                itemId = R.id.menu_tts_engine_import_local,
                titleRes = R.string.import_local,
                iconRes = R.drawable.ic_import,
            ),
            NgExpandableActionMenuItem(
                itemId = R.id.menu_tts_engine_import_online,
                titleRes = R.string.import_on_line,
                iconRes = R.drawable.ic_add_online,
            ),
            NgExpandableActionMenuItem(
                itemId = R.id.menu_show_disabled,
                titleRes = R.string.show_disabled_items,
                iconRes = R.drawable.ic_visibility,
                checked = state.showDisabled,
                dividerBefore = true,
            ),
        )
    }
    NgFloatingTitleToolbar(
        title = stringResource(R.string.tts_engine_settings),
        onBack = { onAction(TtsEngineListAction.Back) },
        modifier = modifier,
    ) {
        Box {
            NgFloatingToolbarActionButton(
                iconRes = R.drawable.ic_grid_menu,
                contentDescription = stringResource(R.string.menu),
                onClick = menuState::onAnchorClick,
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = menuItems,
                variant = NgExpandableActionMenuVariant.SIDE_SLIDE,
                menuContainerColor = colorResource(R.color.ng_surface_card),
                properties = PopupProperties(focusable = true, clippingEnabled = false),
                onItemClick = { item ->
                    menuState.close()
                    when (item.itemId) {
                        R.id.menu_tts_engine_add -> onAction(TtsEngineListAction.CreateEngine)
                        R.id.menu_tts_engine_import_local -> {
                            onAction(TtsEngineListAction.ImportLocal)
                        }
                        R.id.menu_tts_engine_import_online -> {
                            onAction(TtsEngineListAction.ImportOnline)
                        }
                        R.id.menu_show_disabled -> {
                            onAction(TtsEngineListAction.ToggleShowDisabled)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun TtsEngineListCard(
    item: TtsEngineListItemUiModel,
    canReorder: Boolean,
    reorderState: NgLazyReorderState,
    onAction: (TtsEngineListAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val dragDescription = item.actionContentDescription ?: stringResource(R.string.menu)
    val themeSnapshot = NgTheme.snapshot
    val iconContainerColor = if (themeSnapshot.isDark) {
        Color(themeSnapshot.colors.selectedContainer)
    } else {
        colorResource(R.color.ng_settings_icon_bg)
    }
    val iconContentColor = if (themeSnapshot.isDark) {
        Color(themeSnapshot.colors.onPrimaryContainer)
    } else {
        Color(themeSnapshot.colors.primary)
    }
    NgSwipeToDelete(
        deletable = item.deletable,
        reordering = reorderState.isDragging,
        onDeleteRequested = { onAction(TtsEngineListAction.DeleteRequested(item.id)) },
        modifier = modifier
            .ngDraggedItem(reorderState, item.id)
            .testTag("management_item_${item.id}")
    ) {
        NgManagementListCard(
            title = item.name,
            detailTags = item.statusTags(
                enabledText = stringResource(R.string.enabled),
                disabledText = stringResource(R.string.disabled)
            ),
            trailingContent = if (canReorder) {
                {
                    NgManagementTrailingIcon(
                        trailing = NgManagementTrailing.DRAG,
                        contentDescription = dragDescription,
                        modifier = Modifier.ngReorderHandle(
                            state = reorderState,
                            key = item.id,
                            enabled = true,
                            contentDescription = dragDescription
                        )
                    )
                }
            } else {
                null
            },
            onClick = { onAction(TtsEngineListAction.OpenEngine(item.id)) },
            leading = {
                NgManagementLeadingIcon(
                    iconRes = item.iconRes,
                    contentDescription = null,
                    tint = iconContentColor,
                    containerColor = iconContainerColor
                )
            }
        )
    }
}

internal fun TtsEngineListItemUiModel.statusTags(
    enabledText: String,
    disabledText: String
): List<NgStatusTagSpec> {
    return listOf(
        NgStatusTagSpec(
            text = if (enabled) enabledText else disabledText,
            variant = if (enabled) {
                NgStatusTagVariant.SUCCESS
            } else {
                NgStatusTagVariant.WARNING
            }
        ),
        NgStatusTagSpec(
            text = engineTypeText,
            variant = NgStatusTagVariant.INFO
        ),
        NgStatusTagSpec(
            text = voiceCountText,
            variant = NgStatusTagVariant.INFO
        )
    )
}

internal fun TtsEngineListItemUiModel.trailing(): NgManagementTrailing {
    return if (reorderable) {
        NgManagementTrailing.DRAG
    } else {
        NgManagementTrailing.NONE
    }
}

internal fun TtsEngineListScreenState.canRequestReorder(
    item: TtsEngineListItemUiModel
): Boolean = query.isBlank() && item.reorderable
