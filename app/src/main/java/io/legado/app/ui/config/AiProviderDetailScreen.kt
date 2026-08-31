package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgFloatingTabBar
import io.legado.app.ui.design.components.compose.NgFloatingTabSpec
import io.legado.app.ui.design.components.compose.NgPullRefreshBox
import io.legado.app.ui.design.components.compose.NgPullRefreshIndicatorVariant
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class AiProviderModelItemUiModel(
    val id: String,
    val name: String,
    @param:DrawableRes val iconRes: Int,
    val capabilities: List<ModelCapabilityTag>,
    val selected: Boolean,
)

@Immutable
internal data class AiProviderDetailScreenState(
    val providerName: String = "",
    @param:DrawableRes val providerIconRes: Int = R.drawable.ic_cfg_web,
    val selectedTab: Int = 0,
    val modelQuery: String = "",
    val models: List<AiProviderModelItemUiModel> = emptyList(),
    val modelSelectionActionText: String = "",
    val modelSelectionActionEnabled: Boolean = false,
    val isRefreshingModels: Boolean = false,
)

internal sealed interface AiProviderDetailAction {
    data class TabSelected(val index: Int) : AiProviderDetailAction
    data class ModelQueryChanged(val query: String) : AiProviderDetailAction
    data object RefreshModels : AiProviderDetailAction
    data object ToggleVisibleModelSelection : AiProviderDetailAction
    data class EditModel(val modelId: String) : AiProviderDetailAction
    data class ToggleModel(val modelId: String, val selected: Boolean) : AiProviderDetailAction
}

@Composable
internal fun AiProviderDetailScreen(
    state: AiProviderDetailScreenState,
    formState: AiProviderFormScreenState,
    onFormAction: (AiProviderFormScreenAction) -> Unit,
    onAction: (AiProviderDetailAction) -> Unit,
) {
    val configScrollState = rememberScrollState()
    val modelListState = rememberLazyListState()
    Column(modifier = Modifier.fillMaxSize()) {
        when (state.selectedTab) {
            0 -> AiProviderConfigTab(
                state = state,
                formState = formState,
                onFormAction = onFormAction,
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
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.ng_icon_container))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(state.providerIconRes),
                    contentDescription = stringResource(R.string.ai_provider_menu),
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(14.dp))
            Text(
                text = state.providerName,
                modifier = Modifier.weight(1f),
                color = colorResource(R.color.ng_on_surface),
                fontSize = 22.sp,
                lineHeight = 27.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        AiProviderFormScreen(
            state = formState,
            onAction = onFormAction,
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
    val selectionActionInteractionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
    ) {
        NgSearchBar(
            query = state.modelQuery,
            onQueryChange = { onAction(AiProviderDetailAction.ModelQueryChanged(it)) },
            hint = stringResource(R.string.ai_search_model),
            variant = NgSearchBarVariant.COMPACT,
            allowLiquidGlass = false,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.ai_available_models),
                modifier = Modifier.weight(1f),
                color = colorResource(R.color.ng_on_surface),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(min = 66.dp)
                    .alpha(if (state.modelSelectionActionEnabled) 1f else 0.45f)
                    .clickable(
                        interactionSource = selectionActionInteractionSource,
                        indication = null,
                        enabled = state.modelSelectionActionEnabled,
                        onClick = {
                            onAction(AiProviderDetailAction.ToggleVisibleModelSelection)
                        },
                    )
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.modelSelectionActionText,
                    color = Color(NgTheme.colors.primary),
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                )
            }
        }
        NgPullRefreshBox(
            isRefreshing = state.isRefreshingModels,
            onRefresh = { onAction(AiProviderDetailAction.RefreshModels) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
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
                items(state.models, key = AiProviderModelItemUiModel::id) { item ->
                    AiProviderModelRow(
                        item = item,
                        onClick = { onAction(AiProviderDetailAction.EditModel(item.id)) },
                        onSelectedChange = {
                            onAction(AiProviderDetailAction.ToggleModel(item.id, it))
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AiProviderModelRow(
    item: AiProviderModelItemUiModel,
    onClick: () -> Unit,
    onSelectedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(NgTheme.shapes.largeDp.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 70.dp)
            .clip(shape)
            .background(colorResource(R.color.ng_surface_card))
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
                    .background(colorResource(R.color.ng_icon_container))
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
                fontSize = 16.sp,
                lineHeight = 19.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.capabilities.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    item.capabilities.forEach { capability ->
                        AiModelCapabilityIcon(capability)
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
private fun AiModelCapabilityIcon(capability: ModelCapabilityTag) {
    val tint = Color(capability.color)
    Box(
        modifier = Modifier
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
