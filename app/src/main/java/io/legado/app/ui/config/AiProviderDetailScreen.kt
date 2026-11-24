package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import io.legado.app.R
import io.legado.app.help.ai.AiModelType
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuVariant
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgFloatingSearchToolbar
import io.legado.app.ui.design.components.compose.NgFloatingTitleToolbar
import io.legado.app.ui.design.components.compose.NgFloatingToolbarActionButton
import io.legado.app.ui.design.components.compose.NgMenuSelectionStyle
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.components.compose.NgPullRefreshIndicatorVariant
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.components.compose.NgSwipeToDelete
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme

private const val PROVIDER_DETAIL_HELP = 1
private const val PROVIDER_DETAIL_RESET = 2
private const val MODEL_FILTER = 1
private const val MODEL_TOGGLE_ALL = 2
private const val MODEL_REFRESH = 3
private const val MODEL_ADD = 4
private const val MODEL_FILTER_ITEM_BASE = 100

@Immutable
internal data class AiProviderModelItemUiModel(
    val id: String,
    val name: String,
    @param:DrawableRes val iconRes: Int,
    val capabilities: List<ModelCapabilityTag>,
    val selected: Boolean,
    val type: AiModelType?,
    val searchAliases: List<String> = emptyList(),
    val contextWindowLabel: String? = null,
    val creditMultiplierLabel: String? = null,
    val isManual: Boolean = false,
)

@Immutable
internal data class AiProviderDetailScreenState(
    val providerId: String = "",
    val providerName: String = "",
    @param:DrawableRes val providerIconRes: Int = R.drawable.ic_cfg_web,
    val selectedTab: Int = 0,
    val modelQuery: String = "",
    val modelFilter: AiProviderModelFilter = AiProviderModelFilter.ALL,
    val models: List<AiProviderModelItemUiModel> = emptyList(),
    val emptyModelNoticeRes: Int? = null,
    val isRefreshingModels: Boolean = false,
    val revealModelId: String? = null,
    val chatGpt: AiChatGptProviderUiState? = null,
)

internal sealed interface AiProviderDetailAction {
    data class TabSelected(val index: Int) : AiProviderDetailAction
    data class ModelQueryChanged(val query: String) : AiProviderDetailAction
    data class ModelFilterChanged(val filter: AiProviderModelFilter) : AiProviderDetailAction
    data object AddModelRequested : AiProviderDetailAction
    data object RefreshModels : AiProviderDetailAction
    data class SetModelsEnabled(val ids: Set<String>, val enabled: Boolean) : AiProviderDetailAction
    data object Back : AiProviderDetailAction
    data class EditModel(val modelId: String) : AiProviderDetailAction
    data class DeleteModelRequested(val modelId: String) : AiProviderDetailAction
    data class ModelRevealed(val modelId: String) : AiProviderDetailAction
    data class ToggleModel(val modelId: String, val selected: Boolean) : AiProviderDetailAction
    data class ChatGptSignIn(val addAccount: Boolean) : AiProviderDetailAction
    data class ChatGptSelectAccount(val accountId: String) : AiProviderDetailAction
    data class ChatGptEnabled(val enabled: Boolean) : AiProviderDetailAction
    data object ChatGptCancelSignIn : AiProviderDetailAction
    data object ChatGptSignOut : AiProviderDetailAction
    data object ChatGptOpenUsage : AiProviderDetailAction
    data class WorkBuddyRefreshCredits(val accountId: String) : AiProviderDetailAction
    data class WorkBuddyClaimCredits(val accountId: String) : AiProviderDetailAction
}

@Composable
internal fun AiProviderDetailScreen(
    state: AiProviderDetailScreenState,
    formState: AiProviderFormScreenState,
    onFormAction: (AiProviderFormScreenAction) -> Unit,
    onAction: (AiProviderDetailAction) -> Unit,
    onHelp: (() -> Unit)?,
) {
    val configScrollState = rememberScrollState()
    val modelListState = key(state.providerId) { rememberLazyListState() }
    LaunchedEffect(state.providerId, state.modelFilter) {
        if (state.revealModelId == null) modelListState.scrollToItem(0)
    }
    Column(modifier = Modifier.fillMaxSize()) {
        when (state.selectedTab) {
            0 -> AiProviderConfigTab(
                state = state,
                formState = formState,
                onFormAction = onFormAction,
                onAction = onAction,
                onHelp = onHelp,
                onBack = { onAction(AiProviderDetailAction.Back) },
                scrollState = configScrollState,
                modifier = Modifier.weight(1f),
            )
            else -> AiProviderModelsTab(
                state = state,
                onAction = onAction,
                listState = modelListState,
                modifier = Modifier.weight(1f),
            )
        }
        NgFloatingTabBar(
            items = listOf(
                NgFloatingTabSpec(
                    iconRes = R.drawable.ic_ai_tab_config,
                    contentDescription = stringResource(R.string.ai_tab_config),
                ),
                NgFloatingTabSpec(
                    iconRes = R.drawable.ic_ai_tab_models,
                    contentDescription = stringResource(R.string.ai_tab_models),
                ),
            ),
            selectedIndex = state.selectedTab,
            onTabSelected = { onAction(AiProviderDetailAction.TabSelected(it)) },
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun AiProviderConfigTab(
    state: AiProviderDetailScreenState,
    formState: AiProviderFormScreenState,
    onFormAction: (AiProviderFormScreenAction) -> Unit,
    onAction: (AiProviderDetailAction) -> Unit,
    onHelp: (() -> Unit)?,
    onBack: () -> Unit,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        NgFloatingTitleToolbar(
            title = state.providerName,
            onBack = onBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            titleLeadingContent = {
                ProviderToolbarIcon(state.providerIconRes)
            },
            trailingContent = {
                if (state.chatGpt == null) {
                    AiConfigHelpResetActions(
                        onHelp = onHelp,
                        onReset = { onFormAction(AiProviderFormScreenAction.ResetRequested) },
                    )
                } else if (onHelp != null) {
                    NgFloatingToolbarActionButton(
                        iconRes = R.drawable.ic_help,
                        contentDescription = stringResource(R.string.help),
                        onClick = onHelp,
                    )
                }
            },
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp, bottom = 24.dp),
        ) {
            if (state.chatGpt != null) AiChatGptProviderForm(state.chatGpt, formState.enabled, onAction)
            else AiProviderFormScreen(state = formState, onAction = onFormAction)
        }
    }
}

@Composable
internal fun AiConfigHelpResetActions(
    onHelp: (() -> Unit)?,
    onReset: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    if (onHelp == null) {
        NgFloatingToolbarActionButton(
            iconRes = R.drawable.ic_restore,
            contentDescription = stringResource(R.string.ai_provider_reset),
            onClick = {
                focusManager.clearFocus()
                onReset()
            },
        )
        return
    }

    val menuState = remember { NgPopupToggleState() }
    val menuItems = remember {
        listOf(
            NgExpandableActionMenuItem(PROVIDER_DETAIL_HELP, R.string.help, R.drawable.ic_help),
            NgExpandableActionMenuItem(PROVIDER_DETAIL_RESET, R.string.ai_provider_reset, R.drawable.ic_restore),
        )
    }
    Box {
        NgFloatingToolbarActionButton(
            iconRes = R.drawable.ic_grid_menu,
            contentDescription = stringResource(R.string.more),
            onClick = {
                focusManager.clearFocus()
                menuState.onAnchorClick()
            },
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
                    PROVIDER_DETAIL_HELP -> onHelp()
                    PROVIDER_DETAIL_RESET -> onReset()
                }
            },
        )
    }
}

@Composable
private fun ProviderToolbarIcon(@DrawableRes iconRes: Int) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colorResource(R.color.ng_icon_container))
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = stringResource(R.string.ai_provider_menu),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun AiProviderModelsTab(
    state: AiProviderDetailScreenState,
    onAction: (AiProviderDetailAction) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val availableFilters = remember(state.models) { state.models.availableModelFilters() }
    val models = remember(state.models, state.modelQuery, state.modelFilter) {
        state.models.filterProviderModels(state.modelQuery, state.modelFilter)
    }
    val emptyNoticeRes = state.emptyModelNoticeRes ?: R.string.ai_model_search_empty.takeIf {
        state.modelQuery.isNotBlank() || state.modelFilter != AiProviderModelFilter.ALL
    }
    val revealModelId = state.revealModelId
    val revealIndex = remember(models, revealModelId) {
        revealModelId?.let { id -> models.indexOfFirst { it.id == id } } ?: -1
    }
    LaunchedEffect(state.providerId, revealModelId, revealIndex) {
        if (revealModelId != null && revealIndex >= 0) {
            listState.scrollToItem(revealIndex)
            onAction(AiProviderDetailAction.ModelRevealed(revealModelId))
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth(),
    ) {
        NgFloatingSearchToolbar(
            query = state.modelQuery,
            onQueryChange = { onAction(AiProviderDetailAction.ModelQueryChanged(it)) },
            hint = stringResource(R.string.ai_search_model),
            onBack = { onAction(AiProviderDetailAction.Back) },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            AiProviderModelActions(
                filter = state.modelFilter,
                availableFilters = availableFilters,
                visibleModels = models,
                allowManualModels = state.chatGpt == null,
                isRefreshing = state.isRefreshingModels,
                onAction = onAction,
            )
        }
        NgPullRefreshBox(
            isRefreshing = state.isRefreshingModels,
            onRefresh = { onAction(AiProviderDetailAction.RefreshModels) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp)
                .padding(top = 6.dp),
            // 与朗读引擎一致：下拉到请求完成始终使用同一个刷新圆环。
            indicatorVariant = NgPullRefreshIndicatorVariant.SINGLE_SPINNER,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (models.isEmpty() && !state.isRefreshingModels && emptyNoticeRes != null) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(emptyNoticeRes),
                                color = Color(NgTheme.colors.onSurfaceVariant),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                items(models, key = AiProviderModelItemUiModel::id) { item ->
                    val modelRow: @Composable () -> Unit = {
                        AiProviderModelRow(
                            item = item,
                            onClick = { onAction(AiProviderDetailAction.EditModel(item.id)) },
                            onSelectedChange = {
                                onAction(AiProviderDetailAction.ToggleModel(item.id, it))
                            },
                        )
                    }
                    if (item.isManual) {
                        NgSwipeToDelete(
                            deletable = true,
                            reordering = false,
                            onDeleteRequested = { onAction(AiProviderDetailAction.DeleteModelRequested(item.id)) },
                        ) {
                            modelRow()
                        }
                    } else {
                        modelRow()
                    }
                }
            }
        }
    }
}

@Composable
private fun AiProviderModelActions(
    filter: AiProviderModelFilter,
    availableFilters: List<AiProviderModelFilter>,
    visibleModels: List<AiProviderModelItemUiModel>,
    allowManualModels: Boolean,
    isRefreshing: Boolean,
    onAction: (AiProviderDetailAction) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val menuState = remember { NgPopupToggleState() }
    val filterLabel = stringResource(filter.labelRes)
    val bulkEnableTarget = remember(visibleModels) { visibleModels.bulkModelEnableTarget() }
    val menuItems = remember(filter, availableFilters, bulkEnableTarget, isRefreshing, allowManualModels) {
        val disableAll = bulkEnableTarget == false
        buildList {
            if (allowManualModels) add(NgExpandableActionMenuItem(MODEL_ADD, R.string.ai_model_add, R.drawable.ic_add))
            add(
                NgExpandableActionMenuItem(
                    itemId = MODEL_REFRESH,
                    titleRes = R.string.refresh_list,
                    iconRes = R.drawable.ic_bgm_menu_refresh,
                    enabled = !isRefreshing,
                )
            )
            add(
                NgExpandableActionMenuItem(
                    itemId = MODEL_TOGGLE_ALL,
                    titleRes = if (disableAll) R.string.ai_disable_all_models else R.string.ai_enable_all_models,
                    iconRes = if (disableAll) R.drawable.ic_block_outline else R.drawable.ic_check_circle_outline,
                    dividerBefore = true,
                    enabled = bulkEnableTarget != null,
                )
            )
            if (availableFilters.isNotEmpty()) {
                add(
                    NgExpandableActionMenuItem(
                        itemId = MODEL_FILTER,
                        titleRes = R.string.screen,
                        iconRes = R.drawable.ic_screen,
                        leadingIconSize = 18.dp,
                        dividerBefore = true,
                        children = availableFilters.map { entry ->
                            NgExpandableActionMenuItem(
                                itemId = MODEL_FILTER_ITEM_BASE + entry.ordinal,
                                titleRes = entry.labelRes,
                                iconRes = entry.iconRes,
                                checked = entry == filter,
                                selectionStyle = NgMenuSelectionStyle.COMPACT_CHECK,
                                leadingIconSize = when (entry) {
                                    AiProviderModelFilter.CHAT -> 19.dp
                                    AiProviderModelFilter.OCR -> 18.dp
                                    else -> 20.dp
                                },
                            )
                        },
                    )
                )
            }
        }
    }
    Box {
        NgFloatingToolbarActionButton(
            iconRes = R.drawable.ic_grid_menu,
            contentDescription = stringResource(R.string.more),
            onClick = {
                focusManager.clearFocus()
                menuState.onAnchorClick()
            },
            modifier = Modifier.semantics { stateDescription = filterLabel },
            tint = Color(NgTheme.colors.primary).takeUnless { filter == AiProviderModelFilter.ALL },
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
                    MODEL_ADD -> onAction(AiProviderDetailAction.AddModelRequested)
                    MODEL_TOGGLE_ALL -> bulkEnableTarget?.let { enabled ->
                        onAction(
                            AiProviderDetailAction.SetModelsEnabled(
                                ids = visibleModels.mapTo(hashSetOf()) { it.id },
                                enabled = enabled,
                            )
                        )
                    }
                    MODEL_REFRESH -> onAction(AiProviderDetailAction.RefreshModels)
                    else -> AiProviderModelFilter.entries.getOrNull(item.itemId - MODEL_FILTER_ITEM_BASE)
                        ?.let { onAction(AiProviderDetailAction.ModelFilterChanged(it)) }
                }
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AiProviderModelRow(
    item: AiProviderModelItemUiModel,
    onClick: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    val cardColor = colorResource(R.color.ng_translucent_management_card_surface)
    val cardStrokeColor = colorResource(R.color.ng_translucent_management_card_stroke)
    val avatarColor = colorResource(R.color.ng_translucent_management_avatar_surface)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(shape)
            .background(cardColor)
            .border(0.6.dp, cardStrokeColor, shape)
            .clickable(onClick = onClick)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(54.dp)
                .heightIn(min = 70.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarColor)
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(item.iconRes),
                    contentDescription = stringResource(R.string.ai_model),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = item.name,
                color = colorResource(R.color.ng_on_surface),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            val metadata = listOfNotNull(item.contextWindowLabel, item.creditMultiplierLabel)
            val hasTags = item.capabilities.isNotEmpty() || item.isManual
            if (hasTags || metadata.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    val centered = Modifier.align(Alignment.CenterVertically)
                    item.capabilities.forEach { capability ->
                        AiModelCapabilityIcon(capability, centered)
                    }
                    if (item.isManual) {
                        NgStatusTag(
                            text = stringResource(R.string.ai_model_manual),
                            variant = NgStatusTagVariant.INFO,
                            modifier = centered,
                            style = NgStatusTagStyle.COMPACT,
                        )
                    }
                    metadata.forEachIndexed { index, label ->
                        Text(
                            text = label,
                            modifier = centered.padding(start = if (hasTags || index > 0) 6.dp else 0.dp),
                            color = Color(NgTheme.colors.onSurfaceVariant),
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(4.dp))
            }
        }
        Box(
            modifier = Modifier
                .widthIn(min = 50.dp)
                .heightIn(min = 48.dp),
            contentAlignment = Alignment.Center,
        ) {
            NgSwitchControl(
                checked = item.selected,
                onCheckedChange = onSelectedChange,
            )
        }
    }
}

@Composable
internal fun AiModelCapabilityIcon(capability: ModelCapabilityTag, modifier: Modifier = Modifier) {
    val tint = Color(capability.color)
    Box(
        modifier = modifier
            .padding(end = 4.dp)
            .size(20.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(tint.copy(alpha = 24f / 255f))
            .border(1.dp, tint, RoundedCornerShape(5.dp))
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(capability.iconRes),
            contentDescription = stringResource(capability.labelRes),
            modifier = Modifier.fillMaxSize(),
            tint = tint,
        )
    }
}
