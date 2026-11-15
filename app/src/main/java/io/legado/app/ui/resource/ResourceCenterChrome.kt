package io.legado.app.ui.resource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Constraints
import io.legado.app.R
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.compose.NgButton
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.components.compose.NgDotsLoadingIndicator
import io.legado.app.ui.design.components.compose.NgFormSelectMenuVariant
import io.legado.app.ui.design.components.compose.NgFormSelectOption
import io.legado.app.ui.design.components.compose.NgFormSelectRow
import io.legado.app.ui.design.theme.NgTheme

internal enum class ResourceCollectionPage(val title: String) {
    LIBRARY("资源库"), DOWNLOADED("已下载"), UPDATES("可更新")
}

@Composable
internal fun ResourceTypeFilterRow(
    count: Int,
    selectedType: String,
    types: List<ResourceTypeOption>,
    onSelected: (String) -> Unit,
    complete: Boolean = true,
    loading: Boolean = false,
    pendingCount: Int = 0,
    trailingAction: (@Composable () -> Unit)? = null,
) {
    val title = when {
        complete && pendingCount > 0 -> "$count 项 · $pendingCount 待处理"
        complete -> "$count 项"
        loading -> "加载中"
        else -> "未完成"
    }
    val options = remember(types) {
        listOf(NgFormSelectOption("全部类型", "")) + types.map { NgFormSelectOption(it.title, it.key) }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            if (types.size > 1 || selectedType.isNotEmpty()) {
                NgFormSelectRow(
                    title = title,
                    selectedValue = selectedType,
                    options = options,
                    onValueChange = onSelected,
                    arrowIcon = rememberVectorPainter(Icons.Rounded.KeyboardArrowDown),
                    menuVariant = NgFormSelectMenuVariant.END_ANCHORED_FILTER,
                )
            } else {
                Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 14.sp, color = Color(NgTheme.colors.onSurface))
                }
            }
        }
        trailingAction?.let { action ->
            Box(Modifier.padding(end = 2.dp)) { action() }
        }
    }
}

/** 空态以整个分类内容区居中；顶部计数/操作仅约束最小可用位置，不参与中心计算。 */
@Composable
internal fun ResourceCollectionBody(
    empty: Boolean,
    modifier: Modifier = Modifier,
    header: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Layout(modifier = modifier.fillMaxWidth(), content = {
        Box { header() }
        Box { content() }
    }) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val headerPlaceable = measurables[0].measure(Constraints(
            minWidth = width, maxWidth = width, maxHeight = height,
        ))
        val availableHeight = (height - headerPlaceable.height).coerceAtLeast(0)
        val contentPlaceable = measurables[1].measure(Constraints(
            minWidth = width, maxWidth = width,
            minHeight = if (empty) 0 else availableHeight, maxHeight = availableHeight,
        ))
        val contentTop = if (empty) maxOf(headerPlaceable.height, (height - contentPlaceable.height) / 2)
            else headerPlaceable.height
        layout(width, height) {
            headerPlaceable.placeRelative(0, 0)
            contentPlaceable.placeRelative(0, contentTop)
        }
    }
}

@Composable
internal fun ResourceCollectionTabs(
    page: ResourceCollectionPage,
    updatesCount: Int,
    refreshing: Boolean,
    initialLoading: Boolean,
    onSelected: (ResourceCollectionPage) -> Unit,
    updatesReady: Boolean = true,
) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val titleStyle = LocalTextStyle.current.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium)
    val titleSize = textMeasurer.measure("资源库", titleStyle).size
    val badgeText = if (updatesCount > 99) "99+" else updatesCount.toString()
    val badgeSize = textMeasurer.measure(badgeText, LocalTextStyle.current.copy(fontSize = 10.sp)).size
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        val tabWidth = maxWidth / 3
        val titleWidth = minOf(with(density) { titleSize.width.toDp() }, tabWidth - 8.dp)
        val titleHeight = maxOf(48.dp, with(density) { titleSize.height.toDp() } + 8.dp)
        val badgeWidth = maxOf(16.dp, with(density) { badgeSize.width.toDp() } + 8.dp)
        val badgeHeight = maxOf(16.dp, with(density) { badgeSize.height.toDp() })
        val decorationWidth = maxOf(20.dp, badgeWidth + 4.dp)
        val decorationAbove = titleWidth + decorationWidth * 2 > tabWidth
        val decorationHeight = if (decorationAbove) badgeHeight + 4.dp else 0.dp
        Row(Modifier.fillMaxWidth()) {
            ResourceCollectionPage.entries.forEach { tab ->
                val selected = page == tab
                val showLoading = selected && refreshing && !initialLoading
                Box(
                    Modifier.weight(1f).height(titleHeight + decorationHeight + 2.dp)
                        .clickable(role = Role.Tab) { onSelected(tab) }
                        .semantics {
                            this.selected = selected
                            stateDescription = buildList {
                                if (tab == ResourceCollectionPage.UPDATES) add(if (updatesReady) "$updatesCount 项可更新" else "更新尚未检查完成")
                                if (showLoading) add("正在刷新")
                            }.joinToString("，")
                        },
                ) {
                    Column(Modifier.align(Alignment.BottomCenter).width(titleWidth)) {
                        Box(Modifier.fillMaxWidth().height(titleHeight), contentAlignment = Alignment.Center) {
                            Text(tab.title, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                                color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(Modifier.fillMaxWidth().height(2.dp)
                            .background(if (selected) Color(NgTheme.colors.primary) else Color.Transparent))
                    }
                    if (tab == ResourceCollectionPage.UPDATES && updatesReady && updatesCount > 0) {
                        val badgeX = if (decorationAbove) tabWidth - badgeWidth - 4.dp
                            else (tabWidth + titleWidth) / 2
                        Badge(
                            modifier = Modifier.offset(x = badgeX, y = if (decorationAbove) 0.dp else 4.dp)
                                .width(badgeWidth).clearAndSetSemantics {},
                            containerColor = Color(NgTheme.colors.primary),
                            contentColor = Color.White,
                        ) { Text(badgeText, color = Color.White, fontSize = 10.sp, maxLines = 1) }
                    }
                    if (showLoading) {
                        val indicatorX = if (decorationAbove) 4.dp else (tabWidth - titleWidth) / 2 - 20.dp
                        val indicatorY = if (decorationAbove) (badgeHeight - 14.dp) / 2 else (titleHeight - 14.dp) / 2
                        val indicator = Modifier.offset(x = indicatorX, y = indicatorY).size(14.dp)
                        if (NgTheme.snapshot.isEInk) {
                            Icon(painterResource(R.drawable.ic_refresh_black_24dp), null,
                                indicator, tint = Color(NgTheme.colors.primary))
                        } else {
                            CircularProgressIndicator(
                                modifier = indicator,
                                color = Color(NgTheme.colors.primary),
                                strokeWidth = 1.5.dp,
                                trackColor = Color.Transparent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResourceMessageRow(message: String, enabled: Boolean = true, onRetry: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(message, Modifier.weight(1f), color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 13.sp, maxLines = 3,
            overflow = TextOverflow.Ellipsis)
        NgDialogTextActionButton("重试", onRetry, enabled = enabled)
    }
}

@Composable
internal fun ResourceEmptyState(
    loading: Boolean,
    title: String,
    description: String?,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textStyle = LocalTextStyle.current.copy(
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.sp,
    )
    val buttonHeight = maxOf(36.dp, with(LocalDensity.current) { 18.sp.toDp() } + 12.dp)
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            if (loading) {
                NgDotsLoadingIndicator(Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
            }
            Text(title, style = textStyle, fontSize = 15.sp, lineHeight = 22.sp,
                color = Color(NgTheme.colors.onSurface), textAlign = TextAlign.Center)
            description?.takeIf { it.isNotBlank() }?.let {
                Text(it, Modifier.padding(top = 6.dp), style = textStyle, fontSize = 12.sp, lineHeight = 18.sp,
                    color = Color(NgTheme.colors.onSurfaceVariant), textAlign = TextAlign.Center)
            }
            if (!loading && actionLabel != null) {
                Spacer(Modifier.height(10.dp))
                // 低按钮保留48dp触控空间，字形和可见按钮均在其中居中。
                Box(Modifier.heightIn(min = 48.dp).widthIn(min = 104.dp), contentAlignment = Alignment.Center) {
                    NgButton(
                        onClick = onAction,
                        modifier = Modifier.height(buttonHeight).widthIn(min = 104.dp),
                        variant = NgButtonVariant.PRIMARY_LIGHT_CONTENT,
                        shapeVariant = NgButtonShapeVariant.COMPACT_ROUNDED,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                    ) {
                        Text(actionLabel, style = textStyle, fontSize = 13.sp, lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (NgTheme.snapshot.isDark && !NgTheme.snapshot.isEInk) {
                                LocalContentColor.current
                            } else Color.White,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
internal fun ResourceSectionHeader(title: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = Color(NgTheme.colors.primary), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Text("$count 项", color = Color(NgTheme.colors.onSurfaceVariant), fontSize = 12.sp)
    }
}

