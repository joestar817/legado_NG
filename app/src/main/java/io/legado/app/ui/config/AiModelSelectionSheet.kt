package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class AiModelSelectionItemUiModel(
    val id: String,
    val name: String,
    val searchAliases: List<String> = emptyList(),
    @param:DrawableRes val iconRes: Int,
    val capabilities: List<ModelCapabilityTag>,
)

@Immutable
internal data class AiModelSelectionProviderUiModel(
    val id: String,
    val name: String,
    @param:DrawableRes val iconRes: Int,
    val models: List<AiModelSelectionItemUiModel>,
)

@Immutable
internal data class AiModelSelectionSheetState(
    val title: String,
    val emptyText: String?,
    val providers: List<AiModelSelectionProviderUiModel>,
    val selectedProviderId: String = "",
    val selectedModelId: String = "",
    val followAssistantLabel: String? = null,
    val followAssistantSelected: Boolean = false,
)

internal fun AiModelSelectionSheetState.selectedLazyItemIndex(
    visibleProviders: List<AiModelSelectionProviderUiModel> = providers,
    includeFollowAssistant: Boolean = followAssistantLabel != null,
): Int? {
    var index = 0
    if (includeFollowAssistant && followAssistantLabel != null) {
        index += 1 // “自动”分组标题
        if (followAssistantSelected) return index
        index += 1
    }
    for (provider in visibleProviders) {
        index += 1 // 提供商标题
        for (model in provider.models) {
            if (provider.id == selectedProviderId && model.id == selectedModelId) {
                return index
            }
            index += 1
        }
    }
    return null
}

@Composable
internal fun AiModelSelectionSheet(
    state: AiModelSelectionSheetState,
    onSelect: (providerId: String, modelId: String) -> Unit,
    onFollowAssistant: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var filterExpanded by remember { mutableStateOf(false) }
    var selectedProviderIds by remember { mutableStateOf(emptySet<String>()) }
    var locatingSelection by remember { mutableStateOf(false) }
    var locateRequest by remember { mutableIntStateOf(0) }
    var locateTargetIndex by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val normalizedQuery = query.trim()
    val filteredProviders = state.providers
        .filter { selectedProviderIds.isEmpty() || it.id in selectedProviderIds }
        .mapNotNull { provider ->
            val models = provider.models.filter { model ->
                normalizedQuery.isBlank() ||
                    provider.name.contains(normalizedQuery, ignoreCase = true) ||
                    model.id.contains(normalizedQuery, ignoreCase = true) ||
                    model.name.contains(normalizedQuery, ignoreCase = true) ||
                    model.searchAliases.any { alias ->
                        alias.contains(normalizedQuery, ignoreCase = true)
                    }
            }
            models.takeIf { it.isNotEmpty() }?.let { provider.copy(models = it) }
        }
    val showFollowAssistant = state.followAssistantLabel != null &&
        (normalizedQuery.isBlank() || state.followAssistantLabel.contains(
            normalizedQuery,
            ignoreCase = true,
        ))
    val filterActive = query.isNotBlank() || selectedProviderIds.isNotEmpty()
    val filterInteractionSource = remember { MutableInteractionSource() }
    val locateInteractionSource = remember { MutableInteractionSource() }
    val includeFollowAssistant = state.followAssistantLabel != null && onFollowAssistant != null
    val selectedVisibleIndex = state.selectedLazyItemIndex(
        visibleProviders = filteredProviders,
        includeFollowAssistant = showFollowAssistant && onFollowAssistant != null,
    )
    val selectedFullIndex = state.selectedLazyItemIndex(
        includeFollowAssistant = includeFollowAssistant,
    )
    LaunchedEffect(normalizedQuery, selectedProviderIds) {
        if (!locatingSelection) listState.scrollToItem(0)
    }
    LaunchedEffect(filterExpanded) {
        if (filterExpanded) listState.scrollToItem(0)
    }
    LaunchedEffect(locateRequest) {
        if (locateRequest > 0) {
            val request = locateRequest
            try {
                listState.animateScrollToItem(locateTargetIndex)
            } finally {
                if (locateRequest == request) locatingSelection = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.title,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 4.dp),
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
                    .size(40.dp)
                    .clickable(
                        enabled = selectedFullIndex != null,
                        interactionSource = locateInteractionSource,
                        indication = null,
                    ) {
                        val target = selectedVisibleIndex ?: selectedFullIndex
                        if (target != null) {
                            locatingSelection = true
                            if (selectedVisibleIndex == null) {
                                query = ""
                                selectedProviderIds = emptySet()
                            }
                            filterExpanded = false
                            locateTargetIndex = target
                            locateRequest += 1
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_locate_selected),
                    contentDescription = stringResource(R.string.ai_locate_selected_model),
                    modifier = Modifier.size(22.dp),
                    tint = colorResource(R.color.ng_on_surface).copy(
                        alpha = if (selectedFullIndex != null) 1f else 0.35f
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(
                        interactionSource = filterInteractionSource,
                        indication = null,
                    ) {
                        filterExpanded = !filterExpanded
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tts_params_grid),
                    contentDescription = stringResource(R.string.ai_filter_models),
                    modifier = Modifier.size(22.dp),
                    tint = if (filterActive) {
                        Color(NgTheme.colors.primary)
                    } else {
                        colorResource(R.color.ng_on_surface)
                    },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (filterExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colorResource(R.color.ng_settings_group))
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                NgSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hint = stringResource(R.string.ai_search_model),
                    variant = NgSearchBarVariant.COMPACT_FILTER,
                    containerColor = colorResource(R.color.ng_surface_card),
                    allowLiquidGlass = false,
                )
                if (state.providers.size > 1) {
                    Spacer(Modifier.height(8.dp))
                    state.providers.chunked(3).forEach { rowProviders ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(38.dp),
                        ) {
                            rowProviders.forEach { provider ->
                                AiProviderFilterChip(
                                    provider = provider,
                                    selected = provider.id in selectedProviderIds,
                                    modifier = Modifier.weight(1f),
                                ) {
                                    selectedProviderIds = if (provider.id in selectedProviderIds) {
                                        selectedProviderIds - provider.id
                                    } else {
                                        selectedProviderIds + provider.id
                                    }
                                }
                            }
                            repeat(3 - rowProviders.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = 10.dp),
        ) {
            if (showFollowAssistant && onFollowAssistant != null) {
                item(key = "follow-assistant-header") {
                    Text(
                        text = stringResource(R.string.ai_model_automatic_group),
                        modifier = Modifier.padding(
                            start = 2.dp,
                            top = 12.dp,
                            end = 2.dp,
                            bottom = 8.dp,
                        ),
                        color = Color(NgTheme.colors.primary),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                item(key = "follow-assistant") {
                    AiFollowAssistantModelCard(
                        label = requireNotNull(state.followAssistantLabel),
                        summary = stringResource(
                            R.string.ai_context_compaction_model_follow_summary
                        ),
                        selected = state.followAssistantSelected,
                        onClick = onFollowAssistant,
                    )
                }
            }
            filteredProviders.forEach { provider ->
                item(key = "provider:${provider.id}") {
                    Text(
                        text = provider.name,
                        modifier = Modifier.padding(
                            start = 2.dp,
                            top = 12.dp,
                            end = 2.dp,
                            bottom = 8.dp,
                        ),
                        color = Color(NgTheme.colors.primary),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(
                    items = provider.models,
                    key = { model -> "model:${provider.id}:${model.id}" },
                ) { model ->
                    AiModelSelectionCard(
                        model = model,
                        selected = state.selectedProviderId == provider.id &&
                            state.selectedModelId == model.id,
                        onClick = { onSelect(provider.id, model.id) },
                    )
                }
            }
            if (!showFollowAssistant && filteredProviders.isEmpty() && state.emptyText != null) {
                item(key = "empty") {
                    Text(
                        text = requireNotNull(state.emptyText),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 44.dp),
                        color = colorResource(R.color.ng_on_surface_variant),
                        fontSize = 15.sp,
                        lineHeight = 18.sp,
                        letterSpacing = 0.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun AiProviderFilterChip(
    provider: AiModelSelectionProviderUiModel,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .padding(horizontal = 3.dp, vertical = 3.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) Color(NgTheme.colors.selectedContainer)
                else colorResource(R.color.ng_surface_card)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(provider.iconRes),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = provider.name,
            color = Color(
                if (selected) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant
            ),
            fontSize = 13.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AiModelSelectionCard(
    model: AiModelSelectionItemUiModel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colorResource(R.color.ng_surface_card)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.ng_icon_container))
                    .padding(7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(model.iconRes),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    color = colorResource(R.color.ng_on_surface),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (model.capabilities.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        model.capabilities.forEach { capability ->
                            AiSelectionCapabilityIcon(capability)
                        }
                    }
                } else {
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        AiModelSelectionIndicator(selected)
    }
}

@Composable
private fun AiFollowAssistantModelCard(
    label: String,
    summary: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colorResource(R.color.ng_surface_card)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.ng_icon_container))
                    .padding(7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_chat_suggestion),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    tint = Color(NgTheme.colors.primary),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = colorResource(R.color.ng_on_surface),
                    fontSize = 16.sp,
                    lineHeight = 19.sp,
                    letterSpacing = 0.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 4.dp),
                    color = colorResource(R.color.ng_on_surface_variant),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    letterSpacing = 0.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AiModelSelectionIndicator(selected)
    }
}

@Composable
private fun BoxScope.AiModelSelectionIndicator(selected: Boolean) {
    if (!selected) return
    Box(
        modifier = Modifier.matchParentSize(),
    ) {
        Box(
            modifier = Modifier
                .width(6.dp)
                .fillMaxHeight()
                .background(Color(NgTheme.colors.primary)),
        )
    }
}

@Composable
private fun AiSelectionCapabilityIcon(capability: ModelCapabilityTag) {
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
