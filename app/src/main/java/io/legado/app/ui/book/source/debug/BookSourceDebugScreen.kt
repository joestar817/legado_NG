package io.legado.app.ui.book.source.debug

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.components.NgStatusTagStyle
import io.legado.app.ui.design.components.NgStatusTagVariant
import io.legado.app.ui.design.components.compose.NgActionChip
import io.legado.app.ui.design.components.compose.NgExpandableActionMenu
import io.legado.app.ui.design.components.compose.NgExpandableActionMenuItem
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgPopupToggleState
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.components.compose.NgSearchBarActionButton
import io.legado.app.ui.design.components.compose.NgSearchBarVariant
import io.legado.app.ui.design.components.compose.NgStatusTag
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay

@Composable
internal fun BookSourceDebugScreen(
    query: String,
    helpVisible: Boolean,
    searchExample: String,
    exploreExample: String,
    exploreOptions: List<Pair<String, String>>,
    timelineItems: List<BookSourceDebugTimelineState.DebugItem>,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAction: (Int) -> Unit,
    onExploreClick: () -> Unit,
    onExploreSelected: (String, String) -> Unit,
    onInfoClick: () -> Unit,
    onTocClick: () -> Unit,
    onContentClick: () -> Unit,
    onPhaseClick: (BookSourceDebugTimelineState.DebugItem.Phase) -> Unit,
) {
    var showExploreSelector by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val submit: (String) -> Unit = { value ->
        focusManager.clearFocus()
        onSearch(value)
    }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BookSourceDebugTopBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = submit,
            onFocusChanged = onFocusChanged,
            focusRequester = focusRequester,
            onBack = onBack,
            onAction = onAction,
        )
        if (helpVisible) {
            BookSourceDebugHelp(
                searchExample = searchExample,
                exploreExample = exploreExample,
                onSearch = submit,
                onExploreClick = onExploreClick,
                onExploreLongClick = {
                    if (exploreOptions.isNotEmpty()) showExploreSelector = true
                },
                onInfoClick = onInfoClick,
                onTocClick = onTocClick,
                onContentClick = onContentClick,
            )
        } else if (timelineItems.isNotEmpty()) {
            BookSourceDebugTimeline(
                items = timelineItems,
                onPhaseClick = onPhaseClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (showExploreSelector) {
        DebugExploreSelectorDialog(
            options = exploreOptions,
            onDismiss = { showExploreSelector = false },
            onSelected = { title, url ->
                showExploreSelector = false
                onExploreSelected(title, url)
            },
        )
    }
}

@Composable
private fun DebugExploreSelectorDialog(
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelected: (String, String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(
            title = "选择发现",
            variant = NgDialogVariant.LONG_CONTENT,
            actions = {
                NgDialogTextActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                )
            },
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
            ) {
                itemsIndexed(options) { _, (title, url) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable { onSelected(title, url) }
                            .padding(horizontal = 4.dp, vertical = 12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            text = title,
                            color = Color(NgTheme.colors.onSurface),
                            fontSize = 16.sp,
                            lineHeight = 21.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BookSourceDebugTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
    onAction: (Int) -> Unit,
) {
    val menuState = remember { NgPopupToggleState() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NgFloatingToolbarBackButton(onClick = onBack)
        NgSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            hint = stringResource(R.string.search_book_key),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            variant = NgSearchBarVariant.TOOLBAR,
            hideHintOnFocus = true,
            onFocusChanged = onFocusChanged,
            onSearch = onSearch,
        )
        Spacer(Modifier.width(8.dp))
        Box {
            NgSearchBarActionButton(
                onClick = menuState::onAnchorClick,
                contentDescription = stringResource(R.string.menu),
            )
            NgExpandableActionMenu(
                expanded = menuState.expanded,
                onDismissRequest = menuState::onDismissRequest,
                items = bookSourceDebugMenuItems(),
                onItemClick = { item ->
                    menuState.close()
                    onAction(item.itemId)
                },
            )
        }
    }
}

@Composable
private fun bookSourceDebugMenuItems(): List<NgExpandableActionMenuItem> {
    return listOf(
        NgExpandableActionMenuItem(
            R.id.menu_scan,
            R.string.scan_qr_code,
            R.drawable.ic_scan,
        ),
        NgExpandableActionMenuItem(
            R.id.menu_search_src,
            R.string.search_src,
            R.drawable.ic_code,
            dividerBefore = true,
        ),
        NgExpandableActionMenuItem(
            R.id.menu_book_src,
            R.string.boo_src,
            R.drawable.ic_code,
        ),
        NgExpandableActionMenuItem(
            R.id.menu_toc_src,
            R.string.toc_src,
            R.drawable.ic_code,
        ),
        NgExpandableActionMenuItem(
            R.id.menu_content_src,
            R.string.content_src,
            R.drawable.ic_code,
        ),
        NgExpandableActionMenuItem(
            R.id.menu_network_log,
            R.string.network_request_log,
            R.drawable.ic_network_check,
            dividerBefore = true,
        ),
        NgExpandableActionMenuItem(
            R.id.menu_refresh_explore,
            R.string.refresh_explore,
            R.drawable.ic_refresh_black_24dp,
        ),
        NgExpandableActionMenuItem(
            R.id.menu_help,
            R.string.help,
            R.drawable.ic_help,
        ),
    )
}

@Composable
private fun BookSourceDebugHelp(
    searchExample: String,
    exploreExample: String,
    onSearch: (String) -> Unit,
    onExploreClick: () -> Unit,
    onExploreLongClick: () -> Unit,
    onInfoClick: () -> Unit,
    onTocClick: () -> Unit,
    onContentClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorResource(R.color.ng_surface_card))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DebugHelpSection(title = "调试搜索>>输入关键字，如：") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NgActionChip(text = searchExample, onClick = { onSearch(searchExample) })
                NgActionChip(text = "系统", onClick = { onSearch("系统") })
            }
        }
        DebugHelpSection(title = "调试发现>>输入发现URL，如：") {
            NgActionChip(
                text = exploreExample,
                onClick = onExploreClick,
                onLongClick = onExploreLongClick,
            )
        }
        DebugHelpSection(title = "调试详情页>>输入详情页URL，如：") {
            NgActionChip(
                text = "https://m.qidian.com/book/1015609210",
                onClick = onInfoClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DebugHelpSection(title = "调试目录页>>输入目录页URL，如：") {
            NgActionChip(
                text = "++https://www.zhaishuyuan.com/read/30394",
                onClick = onTocClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DebugHelpSection(title = "调试正文页>>输入正文页URL，如：") {
            NgActionChip(
                text = "--https://www.zhaishuyuan.com/chapter/30394/20940996",
                onClick = onContentClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DebugHelpSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 14.sp,
            lineHeight = 18.sp,
        )
        content()
    }
}

@Composable
private fun BookSourceDebugTimeline(
    items: List<BookSourceDebugTimelineState.DebugItem>,
    onPhaseClick: (BookSourceDebugTimelineState.DebugItem.Phase) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val lastItem = items.lastOrNull()
    LaunchedEffect(items.size, lastItem) {
        if (items.isNotEmpty()) {
            delay(120L)
            listState.animateScrollToItem(items.lastIndex)
        }
    }
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        items(
            items = items,
            key = { item -> (item as BookSourceDebugTimelineState.DebugItem.Phase).phaseId },
        ) { item ->
            when (item) {
                is BookSourceDebugTimelineState.DebugItem.Phase -> {
                    BookSourceDebugPhaseRow(item = item, onClick = { onPhaseClick(item) })
                }
            }
        }
    }
}

@Composable
private fun BookSourceDebugPhaseRow(
    item: BookSourceDebugTimelineState.DebugItem.Phase,
    onClick: () -> Unit,
) {
    val statusColor = debugStatusColor(item.status)
    val lineColor = if (item.status == BookSourceDebugTimelineState.StepStatus.Pending) {
        Color(NgTheme.colors.outlineVariant)
    } else {
        statusColor
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .background(Color(NgTheme.colors.background))
            .padding(horizontal = 10.dp),
    ) {
        DebugPhaseRail(
            index = item.index,
            isFirst = item.index == 0,
            isLast = item.isLast,
            status = item.status,
            statusColor = statusColor,
            lineColor = lineColor,
        )
        NgFormPanel(
            modifier = Modifier
                .weight(1f)
                .padding(top = 8.dp, bottom = 8.dp)
                .clickable(enabled = item.hasRawText, onClick = onClick),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp)
                    .padding(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        modifier = Modifier.weight(1f),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    NgStatusTag(
                        text = item.durationText,
                        variant = NgStatusTagVariant.NEUTRAL,
                        style = NgStatusTagStyle.REGULAR,
                    )
                    Spacer(Modifier.width(6.dp))
                    NgStatusTag(
                        text = item.status.label,
                        variant = debugStatusVariant(item.status),
                        style = NgStatusTagStyle.REGULAR,
                    )
                }
                Text(
                    text = item.summary,
                    modifier = Modifier.padding(top = 7.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                )
                Text(
                    text = item.meta,
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                )
                if (item.detail.isNotBlank()) {
                    Text(
                        text = item.detail,
                        modifier = Modifier.padding(top = 6.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.hasRawText) {
                    Text(
                        text = "点击查看原始日志",
                        modifier = Modifier.padding(top = 6.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugPhaseRail(
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    status: BookSourceDebugTimelineState.StepStatus,
    statusColor: Color,
    lineColor: Color,
) {
    Column(
        modifier = Modifier
            .width(48.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DebugRailLine(
            visible = !isFirst,
            color = lineColor,
            modifier = Modifier.height(10.dp),
        )
        Box(
            modifier = Modifier.size(42.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (status == BookSourceDebugTimelineState.StepStatus.Running) {
                DebugStepLoadingIndicator(
                    modifier = Modifier.size(42.dp),
                    color = statusColor,
                )
            }
            val markerModifier = if (status == BookSourceDebugTimelineState.StepStatus.Pending) {
                Modifier
                    .size(24.dp)
                    .border(2.dp, statusColor, CircleShape)
            } else {
                Modifier
                    .size(24.dp)
                    .background(statusColor, CircleShape)
            }
            Box(modifier = markerModifier, contentAlignment = Alignment.Center) {
                Text(
                    text = (index + 1).toString(),
                    color = if (status == BookSourceDebugTimelineState.StepStatus.Pending) {
                        statusColor
                    } else {
                        Color.White
                    },
                    fontSize = 12.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        DebugRailLine(
            visible = !isLast,
            color = lineColor,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 30.dp),
        )
    }
}

/** 精确复刻旧版 StepLoadingView：两个 62° 圆弧，约 333ms 转一圈。 */
@Composable
private fun DebugStepLoadingIndicator(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "book_source_debug_loading")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 333, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "book_source_debug_loading_rotation",
    )
    Canvas(modifier = modifier) {
        val inset = 4.dp.toPx()
        val arcSize = Size(
            width = (size.width - inset * 2f).coerceAtLeast(0f),
            height = (size.height - inset * 2f).coerceAtLeast(0f),
        )
        val stroke = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
        val arcTopLeft = Offset(inset, inset)
        drawArc(
            color = color,
            startAngle = rotation - 135f,
            sweepAngle = 62f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke,
        )
        drawArc(
            color = color,
            startAngle = rotation + 45f,
            sweepAngle = 62f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = stroke,
        )
    }
}

@Composable
private fun DebugRailLine(
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(2.dp)
            .background(if (visible) color else Color.Transparent),
    )
}

@Composable
private fun debugStatusColor(status: BookSourceDebugTimelineState.StepStatus): Color {
    return when (status) {
        BookSourceDebugTimelineState.StepStatus.Success -> colorResource(R.color.ng_success)
        BookSourceDebugTimelineState.StepStatus.Running -> colorResource(R.color.ng_info)
        BookSourceDebugTimelineState.StepStatus.Error -> colorResource(R.color.ng_error)
        BookSourceDebugTimelineState.StepStatus.Pending -> Color(NgTheme.colors.onSurfaceVariant)
    }
}

private fun debugStatusVariant(
    status: BookSourceDebugTimelineState.StepStatus,
): NgStatusTagVariant {
    return when (status) {
        BookSourceDebugTimelineState.StepStatus.Success -> NgStatusTagVariant.SUCCESS
        BookSourceDebugTimelineState.StepStatus.Running -> NgStatusTagVariant.INFO
        BookSourceDebugTimelineState.StepStatus.Error -> NgStatusTagVariant.ERROR
        BookSourceDebugTimelineState.StepStatus.Pending -> NgStatusTagVariant.NEUTRAL
    }
}
