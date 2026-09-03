package io.legado.app.ui.main.explore

import android.graphics.Color as AndroidColor
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.entities.BaseSource
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.rule.ExploreKind
import io.legado.app.data.entities.rule.ExploreKind.Type
import io.legado.app.help.source.ExploreKindRenderRole
import io.legado.app.help.source.isSupportedExploreKind
import io.legado.app.help.source.renderRole
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgGlassStyle
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.design.theme.ngBackdropPrimaryTextStyle
import io.legado.app.ui.design.theme.ngBackdropSecondaryTextStyle
import io.legado.app.ui.login.SourceLoginJsExtensions
import io.legado.app.utils.InfoMap
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExploreScreen(
    sources: List<BookSourcePart>,
    query: String,
    groups: List<String>,
    selectedGroup: String?,
    layoutMode: ExploreLayoutMode,
    expandedSourceUrl: String?,
    kindsBySource: Map<String, List<ExploreKind>>,
    loadingSourceUrls: Set<String>,
    bottomInsetPx: Int,
    scrollToTopToken: Long,
    transparentTopBar: Boolean,
    onQueryChange: (String) -> Unit,
    onGroupSelected: (String?) -> Unit,
    onLayoutModeChange: (ExploreLayoutMode) -> Unit,
    onManageSources: () -> Unit,
    onToggleSource: (BookSourcePart) -> Unit,
    onOpenSource: (BookSourcePart) -> Unit,
    onOpenKind: (BookSourcePart, ExploreKind) -> Unit,
    onShowError: (String) -> Unit,
    onSourceAction: (BookSourcePart, Int) -> Unit,
    onRefreshSource: (BookSourcePart) -> Unit
) {
    val bottomInset = with(LocalDensity.current) { bottomInsetPx.toDp() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        ExploreTopBar(
            query = query,
            groups = groups,
            selectedGroup = selectedGroup,
            layoutMode = layoutMode,
            onQueryChange = onQueryChange,
            onGroupSelected = onGroupSelected,
            onLayoutModeChange = onLayoutModeChange,
            onManageSources = onManageSources,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (sources.isEmpty()) {
                Text(
                    text = stringResource(R.string.explore_empty),
                    style = MaterialTheme.typography.bodyMedium.merge(
                        ngBackdropSecondaryTextStyle(
                            fallbackColor = Color(NgTheme.colors.onSurfaceVariant),
                        ),
                    ),
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                when (layoutMode) {
                    ExploreLayoutMode.LIST -> ExploreSourceList(
                        sources = sources,
                        expandedSourceUrl = expandedSourceUrl,
                        kindsBySource = kindsBySource,
                        loadingSourceUrls = loadingSourceUrls,
                        bottomInset = bottomInset,
                        scrollToTopToken = scrollToTopToken,
                        onToggleSource = onToggleSource,
                        onOpenKind = onOpenKind,
                        onShowError = onShowError,
                        onSourceAction = onSourceAction,
                        onRefreshSource = onRefreshSource
                    )

                    ExploreLayoutMode.GRID -> ExploreSourceGrid(
                        sources = sources,
                        loadingSourceUrls = loadingSourceUrls,
                        bottomInset = bottomInset,
                        scrollToTopToken = scrollToTopToken,
                        onOpenSource = onOpenSource,
                        onSourceAction = onSourceAction
                    )

                    ExploreLayoutMode.GROUP_GRID -> {
                        if (query.isBlank() && selectedGroup == null) {
                            ExploreGroupedSourceGrid(
                                sources = sources,
                                groups = groups,
                                loadingSourceUrls = loadingSourceUrls,
                                bottomInset = bottomInset,
                                scrollToTopToken = scrollToTopToken,
                                onOpenSource = onOpenSource,
                                onSourceAction = onSourceAction
                            )
                        } else {
                            ExploreSourceGrid(
                                sources = sources,
                                loadingSourceUrls = loadingSourceUrls,
                                bottomInset = bottomInset,
                                scrollToTopToken = scrollToTopToken,
                                onOpenSource = onOpenSource,
                                onSourceAction = onSourceAction
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSourceGrid(
    sources: List<BookSourcePart>,
    loadingSourceUrls: Set<String>,
    bottomInset: androidx.compose.ui.unit.Dp,
    scrollToTopToken: Long,
    onOpenSource: (BookSourcePart) -> Unit,
    onSourceAction: (BookSourcePart, Int) -> Unit
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0L) gridState.animateScrollToItem(0)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        state = gridState,
        contentPadding = PaddingValues(bottom = bottomInset + 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(sources, key = { it.bookSourceUrl }) { source ->
            ExploreGridSourceItem(
                source = source,
                loading = source.bookSourceUrl in loadingSourceUrls,
                onClick = { onOpenSource(source) },
                onAction = { actionId -> onSourceAction(source, actionId) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ExploreGridSourceItem(
    source: BookSourcePart,
    loading: Boolean,
    onClick: () -> Unit,
    onAction: (Int) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val tileColor = remember(source.bookSourceUrl) { sourceTileColor(source.bookSourceUrl) }
    val tileContentColor = remember(tileColor) { sourceTileContentColor(tileColor) }
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = !loading,
                    onClick = onClick,
                    onLongClick = { menuExpanded = true }
                )
                .padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(tileColor)),
                contentAlignment = Alignment.Center
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = tileContentColor,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = source.bookSourceName.trim().firstOrNull()
                            ?.toString()?.uppercase() ?: "源",
                        color = tileContentColor,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = source.bookSourceName,
                style = ngBackdropPrimaryTextStyle(
                    fallbackColor = Color(NgTheme.colors.onSurfaceVariant),
                ),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.heightIn(min = 34.dp)
            )
        }
        ExploreSourceMenu(
            source = source,
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onAction = {
                menuExpanded = false
                onAction(it)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreSourceList(
    sources: List<BookSourcePart>,
    expandedSourceUrl: String?,
    kindsBySource: Map<String, List<ExploreKind>>,
    loadingSourceUrls: Set<String>,
    bottomInset: androidx.compose.ui.unit.Dp,
    scrollToTopToken: Long,
    onToggleSource: (BookSourcePart) -> Unit,
    onOpenKind: (BookSourcePart, ExploreKind) -> Unit,
    onShowError: (String) -> Unit,
    onSourceAction: (BookSourcePart, Int) -> Unit,
    onRefreshSource: (BookSourcePart) -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(expandedSourceUrl) {
        val index = sources.indexOfFirst { it.bookSourceUrl == expandedSourceUrl }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken > 0L) listState.animateScrollToItem(0)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomInset + 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(sources, key = { _, item -> item.bookSourceUrl }) { _, source ->
            ExploreListSourceItem(
                source = source,
                expanded = source.bookSourceUrl == expandedSourceUrl,
                loading = source.bookSourceUrl in loadingSourceUrls,
                kinds = kindsBySource[source.bookSourceUrl],
                onToggle = { onToggleSource(source) },
                onOpenKind = { onOpenKind(source, it) },
                onShowError = onShowError,
                onAction = { onSourceAction(source, it) },
                onRefreshSource = { onRefreshSource(source) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreListSourceItem(
    source: BookSourcePart,
    expanded: Boolean,
    loading: Boolean,
    kinds: List<ExploreKind>?,
    onToggle: () -> Unit,
    onOpenKind: (ExploreKind) -> Unit,
    onShowError: (String) -> Unit,
    onAction: (Int) -> Unit,
    onRefreshSource: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val cardShape = RoundedCornerShape(12.dp)
    val cardContainer = if (expanded) {
        Color(NgTheme.colors.cardContainer).copy(alpha = 0.82f)
    } else {
        colorResource(R.color.ng_settings_item)
    }
    val contentColor = Color(NgTheme.colors.onSurface)
    val isEInk = NgTheme.snapshot.isEInk
    val cardStyle = remember(cardContainer, contentColor, isEInk) {
        NgGlassStyle(
            containerTop = cardContainer,
            containerBottom = cardContainer,
            accentGlow = Color.Transparent,
            borderColor = Color.Transparent,
            edgeHighlight = if (isEInk) {
                Color.Transparent
            } else {
                Color.White.copy(alpha = 0.60f)
            },
            surfaceGloss = Color.Transparent,
            depthEdge = Color.Transparent,
            contentColor = contentColor,
            blurRadius = 0.dp,
            shadowElevation = 0.dp,
            borderWidth = 0.dp,
            highlightWidth = 0.dp,
        )
    }
    Box(
        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Column {
            NgVisualSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .combinedClickable(
                        onClick = onToggle,
                        onLongClick = { menuExpanded = true }
                    ),
                role = NgMaterialRole.CONTROL,
                cornerRadius = 12.dp,
                shape = cardShape,
                style = cardStyle,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 14.dp, end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = source.bookSourceName,
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(NgTheme.colors.primary),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Icon(
                        painter = painterResource(
                            if (expanded) R.drawable.ic_arrow_down else R.drawable.ic_arrow_right
                        ),
                        contentDescription = null,
                        tint = Color(NgTheme.colors.onSurfaceVariant),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (expanded && kinds != null) {
                ExploreKindRows(
                    source = source,
                    kinds = kinds,
                    onOpenKind = onOpenKind,
                    onShowError = onShowError,
                    onRefreshSource = onRefreshSource,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
        ExploreSourceMenu(
            source = source,
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onAction = {
                menuExpanded = false
                onAction(it)
            }
        )
    }
}

@Composable
private fun ExploreSourceMenu(
    source: BookSourcePart,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (Int) -> Unit
) {
    val items = remember(source.hasLoginUrl) {
        buildList {
            add(NgExpandableActionMenuItem(R.id.menu_edit, R.string.edit, R.drawable.ic_edit))
            add(
                NgExpandableActionMenuItem(
                    R.id.menu_top,
                    R.string.to_top,
                    R.drawable.ic_arrow_drop_up
                )
            )
            if (source.hasLoginUrl) {
                add(
                    NgExpandableActionMenuItem(
                        R.id.menu_login,
                        R.string.login,
                        R.drawable.ic_lock_outline
                    )
                )
            }
            add(
                NgExpandableActionMenuItem(
                    R.id.menu_search,
                    R.string.search,
                    R.drawable.ic_search,
                    dividerBefore = true
                )
            )
            add(
                NgExpandableActionMenuItem(
                    R.id.menu_refresh,
                    R.string.refresh,
                    R.drawable.ic_refresh_black_24dp
                )
            )
            add(
                NgExpandableActionMenuItem(
                    R.id.menu_del,
                    R.string.delete,
                    R.drawable.ic_outline_delete,
                    dividerBefore = true
                )
            )
        }
    }
    NgExpandableActionMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        items = items,
        onItemClick = { onAction(it.itemId) },
        width = 168.dp
    )
}

@Composable
private fun ExploreKindRows(
    source: BookSourcePart,
    kinds: List<ExploreKind>,
    onOpenKind: (ExploreKind) -> Unit,
    onShowError: (String) -> Unit,
    onRefreshSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val supportedKinds = remember(kinds) {
        kinds.filter(ExploreKind::isSupportedExploreKind)
    }
    val rows = remember(supportedKinds) { calculateExploreKindRows(supportedKinds) }
    val context = LocalContext.current
    val sourceDetail by produceState<BookSource?>(initialValue = null, source.bookSourceUrl) {
        value = withContext(IO) { source.getBookSource() }
    }
    val infoMap = remember(source.bookSourceUrl) {
        ExploreInfoStore.infoMapList[source.bookSourceUrl] ?: InfoMap(source.bookSourceUrl).also {
            ExploreInfoStore.infoMapList.put(source.bookSourceUrl, it)
        }
    }
    val composeScope = rememberCoroutineScope()
    val sourceJsExtensions = remember(sourceDetail, infoMap) {
        SourceLoginJsExtensions(
            context as? AppCompatActivity,
            sourceDetail,
            callback = object : SourceLoginJsExtensions.Callback {
                override fun upUiData(data: Map<String, Any?>?) = Unit

                override fun reUiView(deltaUp: Boolean) {
                    composeScope.launch { onRefreshSource() }
                }
            }
        )
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { (kind, span) ->
                    ExploreKindItem(
                        kind = kind,
                        source = sourceDetail,
                        infoMap = infoMap,
                        sourceJsExtensions = sourceJsExtensions,
                        onOpenKind = onOpenKind,
                        onShowError = onShowError,
                        modifier = Modifier.weight(span.toFloat())
                    )
                }
            }
        }
    }
}

@Composable
internal fun ExploreKindItem(
    kind: ExploreKind,
    source: BookSource?,
    infoMap: InfoMap,
    sourceJsExtensions: SourceLoginJsExtensions,
    onOpenKind: (ExploreKind) -> Unit,
    onShowError: (String) -> Unit,
    displayLabelTransform: (String) -> String = { it },
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val sourceLabel by rememberExploreKindLabel(kind, source, infoMap)
    val label = displayLabelTransform(sourceLabel)
    when (kind.renderRole()) {
        ExploreKindRenderRole.TEXT_INPUT -> ExploreTextKind(
            kind = kind,
            label = label,
            source = source,
            infoMap = infoMap,
            sourceJsExtensions = sourceJsExtensions,
            modifier = modifier
        )

        ExploreKindRenderRole.TOGGLE -> ExploreToggleKind(
            kind = kind,
            label = label,
            source = source,
            infoMap = infoMap,
            sourceJsExtensions = sourceJsExtensions,
            modifier = modifier
        )

        ExploreKindRenderRole.SELECT -> ExploreSelectKind(
            kind = kind,
            label = label,
            source = source,
            infoMap = infoMap,
            sourceJsExtensions = sourceJsExtensions,
            modifier = modifier
        )

        else -> ExploreKindSurface(
            text = label,
            kind = kind,
            modifier = modifier,
            onClick = {
                when (kind.renderRole()) {
                    ExploreKindRenderRole.CATEGORY -> onOpenKind(kind)
                    ExploreKindRenderRole.ERROR -> onShowError(kind.url.orEmpty())
                    ExploreKindRenderRole.BUTTON -> scope.launch(IO) {
                        evalExploreAction(
                            kind.action.orEmpty(),
                            source,
                            infoMap,
                            kind.title,
                            sourceJsExtensions
                        )
                    }

                    else -> Unit
                }
            }
        )
    }
}

@Composable
internal fun rememberExploreKindLabel(
    kind: ExploreKind,
    source: BookSource?,
    infoMap: InfoMap
) = key(kind, kind.viewName, source) {
    produceState(initialValue = literalKindLabel(kind)) {
        val viewName = kind.viewName ?: return@produceState
        if (isQuotedLabel(viewName)) return@produceState
        value = withContext(IO) {
            evalExploreLabel(viewName, source, infoMap) ?: "err"
        }
    }
}

@Composable
private fun ExploreTextKind(
    kind: ExploreKind,
    label: String,
    source: BookSource?,
    infoMap: InfoMap,
    sourceJsExtensions: SourceLoginJsExtensions,
    modifier: Modifier
) {
    var value by remember(kind.title) { mutableStateOf(infoMap[kind.title].orEmpty()) }
    var userEdited by remember(kind.title) { mutableStateOf(false) }
    LaunchedEffect(value) {
        if (!userEdited || kind.action.isNullOrBlank()) return@LaunchedEffect
        delay(600)
        withContext(IO) {
            evalExploreAction(kind.action, source, infoMap, kind.title, sourceJsExtensions)
        }
        userEdited = false
    }
    BasicTextField(
        value = value,
        onValueChange = {
            value = it
            infoMap[kind.title] = it
            userEdited = true
        },
        singleLine = true,
        textStyle = TextStyle(
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            textAlign = kindTextAlign(kind)
        ),
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(NgTheme.colors.surfaceContainerHigh).copy(alpha = 0.72f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        decorationBox = { innerTextField ->
            Box(contentAlignment = kindAlignment(kind)) {
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
private fun ExploreToggleKind(
    kind: ExploreKind,
    label: String,
    source: BookSource?,
    infoMap: InfoMap,
    sourceJsExtensions: SourceLoginJsExtensions,
    modifier: Modifier
) {
    val chars = remember(kind.chars) {
        kind.chars?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: listOf("chars", "is null")
    }
    var value by remember(kind.title) {
        mutableStateOf(
            infoMap[kind.title].takeUnless { it.isNullOrEmpty() }
                ?: (kind.default ?: chars.first()).also { infoMap[kind.title] = it }
        )
    }
    val scope = rememberCoroutineScope()
    val placeValueLeft = kind.style().layout_justifySelf != "right"
    ExploreKindSurface(
        text = if (placeValueLeft) value + label else label + value,
        kind = kind,
        modifier = modifier,
        onClick = {
            value = chars[(chars.indexOf(value).coerceAtLeast(0) + 1) % chars.size]
            infoMap[kind.title] = value
            if (!kind.action.isNullOrBlank()) {
                scope.launch(IO) {
                    evalExploreAction(
                        kind.action,
                        source,
                        infoMap,
                        kind.title,
                        sourceJsExtensions
                    )
                }
            }
        }
    )
}

@Composable
private fun ExploreSelectKind(
    kind: ExploreKind,
    label: String,
    source: BookSource?,
    infoMap: InfoMap,
    sourceJsExtensions: SourceLoginJsExtensions,
    modifier: Modifier
) {
    val chars = remember(kind.chars) {
        kind.chars?.filterNotNull()?.takeIf { it.isNotEmpty() } ?: listOf("chars", "is null")
    }
    var selected by remember(kind.title) {
        mutableStateOf(
            infoMap[kind.title].takeUnless { it.isNullOrEmpty() }
                ?: (kind.default ?: chars.first()).also { infoMap[kind.title] = it }
        )
    }
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Box(modifier) {
        ExploreKindSurface(
            text = "$label  $selected",
            kind = kind,
            modifier = Modifier.fillMaxWidth(),
            onClick = { expanded = true }
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(12.dp),
            containerColor = Color(NgTheme.colors.dialogContainer)
        ) {
            chars.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        expanded = false
                        if (selected == value) return@DropdownMenuItem
                        selected = value
                        infoMap[kind.title] = value
                        if (!kind.action.isNullOrBlank()) {
                            scope.launch(IO) {
                                evalExploreAction(
                                    kind.action,
                                    source,
                                    infoMap,
                                    kind.title,
                                    sourceJsExtensions
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExploreKindSurface(
    text: String,
    kind: ExploreKind,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(NgTheme.colors.surfaceContainerHigh).copy(alpha = 0.72f))
            .combinedClickable(onClick = onClick, onLongClick = {})
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = kindAlignment(kind)
    ) {
        Text(
            text = text,
            color = Color(NgTheme.colors.onSurface),
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = kindTextAlign(kind)
        )
    }
}

private fun literalKindLabel(kind: ExploreKind): String {
    val viewName = kind.viewName ?: return kind.title
    return if (isQuotedLabel(viewName)) {
        viewName.substring(1, viewName.lastIndex)
    } else {
        kind.title
    }
}

private fun isQuotedLabel(value: String): Boolean {
    return value.length in 3..19 && value.first() == '\'' && value.last() == '\''
}

private fun kindAlignment(kind: ExploreKind): Alignment {
    return when (kind.style().layout_justifySelf) {
        "flex_start" -> Alignment.CenterStart
        "flex_end", "right" -> Alignment.CenterEnd
        else -> Alignment.Center
    }
}

private fun kindTextAlign(kind: ExploreKind): TextAlign {
    return when (kind.style().layout_justifySelf) {
        "flex_start" -> TextAlign.Start
        "flex_end", "right" -> TextAlign.End
        else -> TextAlign.Center
    }
}

private suspend fun evalExploreLabel(
    jsStr: String,
    source: BookSource?,
    infoMap: InfoMap
): String? {
    source ?: return null
    return try {
        runScriptWithContext {
            source.evalJS(jsStr) {
                put("infoMap", infoMap)
            }.toString()
        }
    } catch (e: Exception) {
        AppLog.put(source.getTag() + " exploreUi err:" + (e.localizedMessage ?: e.toString()), e)
        null
    }
}

private suspend fun evalExploreAction(
    jsStr: String,
    source: BaseSource?,
    infoMap: InfoMap,
    name: String,
    java: SourceLoginJsExtensions
) {
    source ?: return
    try {
        runScriptWithContext {
            source.evalJS(jsStr) {
                put("java", java)
                put("infoMap", infoMap)
            }
        }
    } catch (e: Exception) {
        AppLog.put("ExploreUI Button $name JavaScript error", e)
    }
}

internal fun sourceTileColor(sourceUrl: String): Int {
    val hue = ((sourceUrl.hashCode() and Int.MAX_VALUE) % 360).toFloat()
    return ColorUtils.HSLToColor(floatArrayOf(hue, 0.46f, 0.54f))
}

internal fun sourceTileContentColor(tileColor: Int): Color {
    return if (ColorUtils.calculateLuminance(tileColor) > 0.42) {
        Color(AndroidColor.rgb(32, 32, 32))
    } else {
        Color.White
    }
}
