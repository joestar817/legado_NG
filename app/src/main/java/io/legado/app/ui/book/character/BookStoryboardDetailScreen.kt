package io.legado.app.ui.book.character

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

internal data class StoryboardSceneUi(
    val index: Int,
    val title: String,
    val meta: String,
    val source: StoryboardScene,
    val segments: List<StoryboardSegmentUi>,
)

internal data class StoryboardSegmentUi(
    val key: String,
    val identity: String,
    val meta: String,
    val status: String?,
    val voice: String,
    val text: String,
    val details: String,
    val source: StoryboardSegment,
)

@Composable
internal fun BookStoryboardDetailScreen(
    chapterTitle: String,
    summary: StoryboardDetailSummaryUi?,
    scenes: List<StoryboardSceneUi>,
    expandedSceneIndexes: Set<Int>,
    expandedSegmentKeys: Set<String>,
    refreshEnabled: Boolean,
    loading: Boolean,
    loadingMessage: String,
    errorMessage: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onToggleScene: (Int) -> Unit,
    onToggleSegmentDetails: (String) -> Unit,
    onPreview: (StoryboardScene, StoryboardSegment) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
    ) {
        BookStoryboardDetailHeader(
            chapterTitle = chapterTitle,
            summary = summary,
            refreshEnabled = refreshEnabled && !loading,
            onBack = onBack,
            onRefresh = onRefresh,
        )
        when {
            loading -> StoryboardDetailMessage(
                message = loadingMessage,
                loading = true,
                modifier = Modifier.weight(1f),
            )

            errorMessage != null -> StoryboardDetailMessage(
                message = errorMessage,
                modifier = Modifier.weight(1f),
            )

            scenes.isEmpty() -> StoryboardDetailMessage(
                message = stringResource(R.string.book_storyboard_empty),
                modifier = Modifier.weight(1f),
            )

            else -> StoryboardSceneList(
                scenes = scenes,
                expandedSceneIndexes = expandedSceneIndexes,
                expandedSegmentKeys = expandedSegmentKeys,
                onToggleScene = onToggleScene,
                onToggleSegmentDetails = onToggleSegmentDetails,
                onPreview = onPreview,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StoryboardSceneList(
    scenes: List<StoryboardSceneUi>,
    expandedSceneIndexes: Set<Int>,
    expandedSegmentKeys: Set<String>,
    onToggleScene: (Int) -> Unit,
    onToggleSegmentDetails: (String) -> Unit,
    onPreview: (StoryboardScene, StoryboardSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 14.dp,
            top = 4.dp,
            end = 14.dp,
            bottom = 24.dp,
        ),
    ) {
        scenes.forEach { scene ->
            val expanded = scene.index in expandedSceneIndexes
            item(key = "scene_header_${scene.index}") {
                StoryboardSceneHeader(
                    scene = scene,
                    expanded = expanded,
                    onClick = { onToggleScene(scene.index) },
                )
            }
            if (expanded) {
                itemsIndexed(
                    items = scene.segments,
                    key = { _, segment -> segment.key },
                ) { index, segment ->
                    StoryboardSegmentRow(
                        segment = segment,
                        isFirst = index == 0,
                        isLast = index == scene.segments.lastIndex,
                        detailsExpanded = segment.key in expandedSegmentKeys,
                        onToggleDetails = { onToggleSegmentDetails(segment.key) },
                        onPreview = { onPreview(scene.source, segment.source) },
                    )
                }
            }
            item(key = "scene_gap_${scene.index}") {
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StoryboardSceneHeader(
    scene: StoryboardSceneUi,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val radius = NgTheme.shapes.smallDp.dp
    val shape = if (expanded) {
        RoundedCornerShape(topStart = radius, topEnd = radius)
    } else {
        RoundedCornerShape(radius)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        color = colorResource(R.color.ng_surface_card),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = scene.index.coerceAtLeast(1).toString(),
                    color = Color(NgTheme.colors.primary),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = scene.title,
                    color = colorResource(R.color.primaryText),
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = scene.meta,
                    modifier = Modifier.padding(top = 4.dp),
                    color = colorResource(R.color.tv_text_summary),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painter = painterResource(R.drawable.ic_expand_more),
                contentDescription = null,
                tint = Color(NgTheme.colors.onSurface),
                modifier = Modifier
                    .size(24.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
    }
}

@Composable
private fun StoryboardSegmentRow(
    segment: StoryboardSegmentUi,
    isFirst: Boolean,
    isLast: Boolean,
    detailsExpanded: Boolean,
    onToggleDetails: () -> Unit,
    onPreview: () -> Unit,
) {
    val radius = NgTheme.shapes.smallDp.dp
    val shape = if (isLast) {
        RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    } else {
        RoundedCornerShape(0.dp)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                enabled = segment.details.isNotBlank(),
                role = Role.Button,
                onClick = onToggleDetails,
            ),
        color = colorResource(R.color.ng_surface_card),
        shape = shape,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .heightIn(min = 88.dp),
        ) {
            StoryboardTimeline(
                isFirst = isFirst,
                isLast = isLast,
                modifier = Modifier
                    .width(28.dp)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .width(54.dp)
                    .padding(top = 16.dp, bottom = 16.dp),
            ) {
                Text(
                    text = segment.identity,
                    color = Color(NgTheme.colors.primary),
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = segment.meta,
                    modifier = Modifier.padding(top = 10.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 16.dp, start = 0.dp, end = 8.dp, bottom = 16.dp),
            ) {
                Text(
                    text = buildString {
                        segment.status?.takeIf { it.isNotBlank() }?.let {
                            append(it).append(" · ")
                        }
                        append(segment.voice)
                    },
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = segment.text,
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                )
                if (detailsExpanded && segment.details.isNotBlank()) {
                    Text(
                        text = segment.details,
                        modifier = Modifier.padding(top = 8.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .padding(top = 16.dp, end = 12.dp)
                    .size(36.dp)
                    .clickable(role = Role.Button, onClick = onPreview),
                color = Color(NgTheme.colors.primary).copy(alpha = 0.12f),
                shape = CircleShape,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = painterResource(R.drawable.ic_tts_headphones),
                        contentDescription = stringResource(R.string.tts_preview),
                        tint = Color(NgTheme.colors.primary),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoryboardTimeline(
    isFirst: Boolean,
    isLast: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = Color(NgTheme.colors.primary)
    val surface = colorResource(R.color.ng_surface_card)
    Canvas(modifier = modifier) {
        val centerX = 18.dp.toPx()
        val centerY = 21.dp.toPx()
        val lineWidth = 2.dp.toPx()
        val endpointRadius = 6.dp.toPx()
        val nodeRadius = if (isFirst || isLast) endpointRadius else 4.dp.toPx()
        if (!isFirst) {
            drawLine(
                color = color.copy(alpha = 0.52f),
                start = Offset(centerX, 0f),
                end = Offset(centerX, centerY - nodeRadius),
                strokeWidth = lineWidth,
            )
        }
        if (!isLast) {
            drawLine(
                color = color.copy(alpha = 0.52f),
                start = Offset(centerX, centerY + nodeRadius),
                end = Offset(centerX, size.height),
                strokeWidth = lineWidth,
            )
        }
        if (isFirst || isLast) {
            drawCircle(color = surface, radius = endpointRadius, center = Offset(centerX, centerY))
            drawCircle(
                color = color,
                radius = endpointRadius - 1.dp.toPx(),
                center = Offset(centerX, centerY),
                style = Stroke(width = 2.dp.toPx()),
            )
            drawCircle(color = color, radius = 2.dp.toPx(), center = Offset(centerX, centerY))
        } else {
            drawCircle(color = color, radius = 4.dp.toPx(), center = Offset(centerX, centerY))
        }
    }
}

@Composable
private fun StoryboardDetailMessage(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(34.dp),
                color = Color(NgTheme.colors.primary),
                strokeWidth = 3.dp,
            )
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = message,
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 15.sp,
            lineHeight = 20.sp,
        )
    }
}
