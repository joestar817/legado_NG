package io.legado.app.ui.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.components.compose.ngDrawerPrimaryTextColor
import io.legado.app.ui.design.components.compose.ngDrawerSecondaryTextColor
import io.legado.app.ui.design.components.compose.ngDrawerThemedContainerColor
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.abs

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
    val needsFetch: Boolean = false,
    val noEnabledModels: Boolean = false,
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

internal fun AiModelSelectionSheetState.withUpdatedProvider(
    providerId: String,
    updatedProvider: AiModelSelectionProviderUiModel?,
): AiModelSelectionSheetState = copy(
    providers = providers.mapNotNull { provider ->
        if (provider.id == providerId) updatedProvider else provider
    },
)

internal data class AiModelSelectionTarget(
    val providerId: String? = null,
    val modelId: String? = null,
    val followAssistant: Boolean = false,
)

internal data class AiModelSelectionPosition(val pageKey: String, val row: Int)

private const val FOLLOW_ASSISTANT_PAGE = "follow-assistant"

private enum class AiModelCatalogFetchStatus { LOADING, FAILED }

private fun providerPageKey(providerId: String) = "provider:" + providerId

internal fun List<AiModelSelectionProviderUiModel>.filterModels(
    query: String,
): List<AiModelSelectionProviderUiModel> {
    val keyword = query.trim()
    return map { provider ->
        provider.copy(models = provider.models.filter { model ->
            keyword.isBlank() ||
                model.id.contains(keyword, ignoreCase = true) ||
                model.name.contains(keyword, ignoreCase = true) ||
                model.searchAliases.any { it.contains(keyword, ignoreCase = true) }
        })
    }
}

internal fun AiModelSelectionSheetState.selectedTarget(
    includeFollowAssistant: Boolean,
): AiModelSelectionTarget? {
    if (includeFollowAssistant && followAssistantLabel != null && followAssistantSelected) {
        return AiModelSelectionTarget(followAssistant = true)
    }
    val provider = providers.firstOrNull { it.id == selectedProviderId } ?: return null
    if (provider.models.none { it.id == selectedModelId }) return null
    return AiModelSelectionTarget(providerId = provider.id, modelId = selectedModelId)
}

internal fun AiModelSelectionTarget.position(
    providers: List<AiModelSelectionProviderUiModel>,
    showFollowAssistant: Boolean,
): AiModelSelectionPosition? {
    if (followAssistant) {
        return AiModelSelectionPosition(FOLLOW_ASSISTANT_PAGE, 0).takeIf { showFollowAssistant }
    }
    val provider = providers.firstOrNull { it.id == providerId } ?: return null
    val row = provider.models.indexOfFirst { it.id == modelId }
    return if (row < 0) null else AiModelSelectionPosition(providerPageKey(provider.id), row)
}

private data class AiModelSelectionPage(
    val key: String,
    val label: String,
    @param:DrawableRes val iconRes: Int,
    val providerId: String? = null,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AiModelSelectionSheet(
    state: AiModelSelectionSheetState,
    isLoading: Boolean = false,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFetchProvider: (suspend (String) -> Unit)? = null,
    onSelect: (providerId: String, modelId: String) -> Unit,
    onFollowAssistant: (() -> Unit)? = null,
) {
    var query by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var locatingSelection by remember { mutableStateOf(false) }
    var locateRequest by remember { mutableIntStateOf(0) }
    var locateTarget by remember { mutableStateOf<AiModelSelectionTarget?>(null) }
    val listStates = remember { mutableMapOf<String, LazyListState>() }
    val fetchStatuses = remember { mutableStateMapOf<String, AiModelCatalogFetchStatus>() }
    val attemptedProviders = remember { mutableSetOf<String>() }
    val fetchJobs = remember { mutableMapOf<String, Job>() }
    val scope = rememberCoroutineScope()
    var refreshRequested by remember { mutableStateOf(false) }
    var initialPageChosen by remember { mutableStateOf(false) }

    fun openProvider(providerId: String, retry: Boolean = false) {
        val fetch = onFetchProvider ?: return
        val provider = state.providers.firstOrNull { it.id == providerId } ?: return
        if (!provider.needsFetch || isRefreshing || refreshRequested ||
            fetchJobs.containsKey(providerId) || (!retry && providerId in attemptedProviders)
        ) return
        attemptedProviders += providerId
        fetchStatuses[providerId] = AiModelCatalogFetchStatus.LOADING
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                fetch(providerId)
                fetchStatuses.remove(providerId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fetchStatuses[providerId] = AiModelCatalogFetchStatus.FAILED
            } finally {
                fetchJobs.remove(providerId)
            }
        }
        fetchJobs[providerId] = job
        job.start()
    }

    val normalizedQuery = query.trim()
    val filteredProviders = remember(state.providers, normalizedQuery) {
        state.providers.filterModels(normalizedQuery)
    }
    val includeFollowAssistant = state.followAssistantLabel != null && onFollowAssistant != null
    val showFollowAssistant = includeFollowAssistant &&
        (normalizedQuery.isBlank() ||
            state.followAssistantLabel?.contains(normalizedQuery, ignoreCase = true) == true)
    val automaticLabel = stringResource(R.string.ai_model_automatic_group)
    val pages = remember(state.providers, includeFollowAssistant, automaticLabel) {
        buildList {
            if (includeFollowAssistant) {
                add(AiModelSelectionPage(
                    key = FOLLOW_ASSISTANT_PAGE,
                    label = automaticLabel,
                    iconRes = R.drawable.ic_ai_chat_suggestion,
                ))
            }
            state.providers.forEach { provider ->
                add(AiModelSelectionPage(
                    key = providerPageKey(provider.id),
                    label = provider.name,
                    iconRes = provider.iconRes,
                    providerId = provider.id,
                ))
            }
        }
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPages by rememberUpdatedState(pages)
    val currentOnProviderOpen by rememberUpdatedState<(String) -> Unit>({ openProvider(it) })
    val selectedTarget = remember(state, includeFollowAssistant) {
        state.selectedTarget(includeFollowAssistant)
    }
    val selectedVisible = selectedTarget?.position(filteredProviders, showFollowAssistant)
    val refreshInteractionSource = remember { MutableInteractionSource() }
    val locateInteractionSource = remember { MutableInteractionSource() }
    val searchInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(isLoading, pages) {
        if (!initialPageChosen && !isLoading && pages.isNotEmpty()) {
            initialPageChosen = true
            if (!includeFollowAssistant) {
                val firstWithModels = pages.indexOfFirst { page ->
                    state.providers.any { it.id == page.providerId && it.models.isNotEmpty() }
                }
                if (firstWithModels > 0) pagerState.scrollToPage(firstWithModels)
            }
        }
    }
    LaunchedEffect(normalizedQuery) {
        if (!locatingSelection) listStates.values.toList().forEach { it.scrollToItem(0) }
    }
    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            pages.getOrNull(pagerState.currentPage)?.let { page ->
                listStates.getOrPut(page.key) { LazyListState() }.scrollToItem(0)
            }
        }
    }
    LaunchedEffect(locateRequest, filteredProviders, showFollowAssistant, pages) {
        if (locateRequest == 0) return@LaunchedEffect
        val target = locateTarget ?: return@LaunchedEffect
        val position = target.position(filteredProviders, showFollowAssistant)
        val page = pages.indexOfFirst { it.key == position?.pageKey }
        if (position == null || page < 0) {
            locatingSelection = false
            locateTarget = null
            return@LaunchedEffect
        }
        val request = locateRequest
        try {
            pagerState.scrollToPage(page)
            listStates.getOrPut(position.pageKey) { LazyListState() }
                .animateScrollToItem(position.row)
        } finally {
            if (locateRequest == request) {
                locatingSelection = false
                locateTarget = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, top = 10.dp, end = 12.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.title,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                color = ngDrawerPrimaryTextColor(colorResource(R.color.ng_on_surface)),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                modifier = Modifier.size(40.dp).clickable(
                    enabled = !isRefreshing && !refreshRequested,
                    interactionSource = refreshInteractionSource,
                    indication = null,
                ) {
                    refreshRequested = true
                    scope.launch {
                        try {
                            fetchJobs.values.toList().joinAll()
                            onRefresh()
                        } finally {
                            refreshRequested = false
                        }
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (isRefreshing || refreshRequested) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color(NgTheme.colors.primary),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_refresh_black_24dp),
                        contentDescription = stringResource(R.string.ai_refresh_all_models),
                        modifier = Modifier.size(22.dp),
                        tint = ngDrawerPrimaryTextColor(colorResource(R.color.ng_on_surface)),
                    )
                }
            }
            Box(
                modifier = Modifier.size(40.dp).clickable(
                    enabled = selectedTarget != null,
                    interactionSource = locateInteractionSource,
                    indication = null,
                ) {
                    if (selectedTarget != null) {
                        locatingSelection = true
                        if (selectedVisible == null) query = ""
                        searchExpanded = false
                        locateTarget = selectedTarget
                        locateRequest += 1
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_ai_locate_selected),
                    contentDescription = stringResource(R.string.ai_locate_selected_model),
                    modifier = Modifier.size(22.dp),
                    tint = ngDrawerPrimaryTextColor(colorResource(R.color.ng_on_surface)).copy(
                        alpha = if (selectedTarget != null) 1f else 0.35f,
                    ),
                )
            }
            Box(
                modifier = Modifier.size(40.dp).clickable(
                    interactionSource = searchInteractionSource,
                    indication = null,
                ) { searchExpanded = !searchExpanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_search),
                    contentDescription = stringResource(R.string.ai_search_model),
                    modifier = Modifier.size(22.dp),
                    tint = if (query.isNotBlank()) Color(NgTheme.colors.primary)
                    else ngDrawerPrimaryTextColor(colorResource(R.color.ng_on_surface)),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (searchExpanded) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(ngDrawerThemedContainerColor(colorResource(R.color.ng_settings_group)))
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 10.dp),
            ) {
                NgSearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    hint = stringResource(R.string.ai_search_model),
                    variant = NgSearchBarVariant.COMPACT_FILTER,
                    containerColor = ngDrawerContentCardColor(),
                    allowLiquidGlass = false,
                )
            }
        }

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = Color(NgTheme.colors.primary),
                    strokeWidth = 2.5.dp,
                )
            }
            pages.isEmpty() -> state.emptyText?.let { message ->
                AiModelSelectionMessage(message, Modifier.weight(1f))
            }
            else -> Column(Modifier.fillMaxWidth().weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pages.forEachIndexed { index, page ->
                        key(page.key) {
                            AiModelSelectionTab(
                                page = page,
                                selected = pagerState.currentPage == index,
                                onClick = {
                                    page.providerId?.let { openProvider(it) }
                                    if (pagerState.currentPage != index) {
                                        scope.launch { pagerState.animateScrollToPage(index) }
                                    }
                                },
                            )
                        }
                    }
                }
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color(NgTheme.colors.onSurface).copy(alpha = 0.1f),
                )
                HorizontalPager(
                    state = pagerState,
                    key = { page -> pages[page].key },
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 10.dp)
                        .pointerInput(pagerState) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val startPage = pagerState.settledPage
                                var horizontal = 0f
                                var vertical = 0f
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                    event.changes.firstOrNull { it.id == down.id }?.let { change ->
                                        horizontal += change.position.x - change.previousPosition.x
                                        vertical += change.position.y - change.previousPosition.y
                                    }
                                } while (event.changes.any { it.pressed })
                                if (abs(horizontal) > viewConfiguration.touchSlop &&
                                    abs(horizontal) > abs(vertical)
                                ) {
                                    scope.launch {
                                        withFrameNanos { }
                                        snapshotFlow { pagerState.isScrollInProgress }.first { !it }
                                        val page = pagerState.settledPage
                                        if (page != startPage) {
                                            currentPages.getOrNull(page)?.providerId?.let {
                                                currentOnProviderOpen(it)
                                            }
                                        }
                                    }
                                }
                            }
                        },
                ) { pageIndex ->
                    val page = pages[pageIndex]
                    val listState = listStates.getOrPut(page.key) { LazyListState() }
                    val provider = filteredProviders.firstOrNull { it.id == page.providerId }
                    val originalProvider = state.providers.firstOrNull { it.id == page.providerId }
                    val fetchStatus = page.providerId?.let { fetchStatuses[it] }
                    when {
                        page.providerId == null && showFollowAssistant -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 10.dp),
                            ) {
                                item(key = FOLLOW_ASSISTANT_PAGE) {
                                    AiFollowAssistantModelCard(
                                        label = requireNotNull(state.followAssistantLabel),
                                        summary = stringResource(R.string.ai_context_compaction_model_follow_summary),
                                        selected = state.followAssistantSelected,
                                        onClick = requireNotNull(onFollowAssistant),
                                    )
                                }
                            }
                        }
                        page.providerId != null && fetchStatus == AiModelCatalogFetchStatus.LOADING -> {
                            AiModelCatalogMessage(
                                text = stringResource(R.string.loading),
                                loading = true,
                            )
                        }
                        page.providerId != null && originalProvider?.needsFetch == true -> {
                            val failed = fetchStatus == AiModelCatalogFetchStatus.FAILED
                            AiModelCatalogMessage(
                                text = stringResource(
                                    if (failed) R.string.ai_fetch_models_failed
                                    else R.string.ai_provider_models_not_fetched
                                ),
                                action = if (onFetchProvider != null) stringResource(
                                    if (failed) R.string.retry else R.string.ai_fetch_models
                                ) else null,
                                onAction = { openProvider(page.providerId, retry = true) },
                            )
                        }
                        page.providerId != null && originalProvider?.noEnabledModels == true -> {
                            AiModelSelectionMessage(
                                stringResource(R.string.ai_model_none_enabled), Modifier.fillMaxSize(),
                            )
                        }
                        page.providerId == null || provider == null || provider.models.isEmpty() -> {
                            val message = if (normalizedQuery.isNotBlank() &&
                                (page.providerId == null || originalProvider?.models?.isNotEmpty() == true)
                            ) {
                                stringResource(R.string.ai_model_search_empty)
                            } else {
                                state.emptyText ?: stringResource(R.string.ai_model_list_empty)
                            }
                            AiModelSelectionMessage(message, Modifier.fillMaxSize())
                        }
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 10.dp),
                        ) {
                            items(
                                items = provider.models,
                                key = { model -> "model:" + provider.id + ":" + model.id },
                            ) { model ->
                                AiModelSelectionCard(
                                    model = model,
                                    selected = state.selectedProviderId == provider.id &&
                                        state.selectedModelId == model.id,
                                    onClick = { onSelect(provider.id, model.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AiModelSelectionTab(
    page: AiModelSelectionPage,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) {
        if (selected) bringIntoView.bringIntoView()
    }
    val color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant)
    Box(
        modifier = Modifier.width(IntrinsicSize.Max).widthIn(min = 48.dp).height(48.dp)
            .bringIntoViewRequester(bringIntoView)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (page.providerId == null) {
                Icon(
                    painter = painterResource(page.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color,
                )
            } else {
                Image(
                    painter = painterResource(page.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = page.label,
                color = color,
                fontFamily = NgTheme.fontFamily,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                maxLines = 1,
            )
        }
        if (selected) {
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(color),
            )
        }
    }
}

@Composable
private fun AiModelSelectionMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = ngDrawerSecondaryTextColor(colorResource(R.color.ng_on_surface_variant)),
            fontSize = 15.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AiModelCatalogMessage(
    text: String,
    loading: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = Color(NgTheme.colors.primary),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(12.dp))
        }
        AiModelSelectionMessage(text)
        if (action != null) {
            Text(
                text = action,
                modifier = Modifier.padding(top = 12.dp).clickable(onClick = onAction)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color(NgTheme.colors.primary),
                fontSize = 14.sp,
            )
        }
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
            .background(ngDrawerContentCardColor()),
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
                    color = ngDrawerPrimaryTextColor(colorResource(R.color.ng_on_surface)),
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
            .background(ngDrawerContentCardColor()),
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
                    color = ngDrawerPrimaryTextColor(colorResource(R.color.ng_on_surface)),
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
                    color = ngDrawerSecondaryTextColor(colorResource(R.color.ng_on_surface_variant)),
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
