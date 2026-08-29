package io.legado.app.ui.book.source.manage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import io.legado.app.constant.BookSourceType
import io.legado.app.model.CheckSourceItemState
import io.legado.app.model.CheckSourceItemStatus
import io.legado.app.model.CheckSourceResultKind
import io.legado.app.model.CheckSourceStage
import io.legado.app.model.CheckSourceTaskState
import io.legado.app.model.CheckSourceTaskStatus
import io.legado.app.ui.design.components.compose.NgFloatingToolbarBackButton
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.delay

private enum class CheckSourceResultFilter {
    ALL,
    PASSED,
    FAILED,
    BLOCKED,
}

@Composable
internal fun BookSourceCheckScreen(
    state: CheckSourceTaskState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onHandleResults: () -> Unit,
) {
    var filter by remember(state.runId) { mutableStateOf(CheckSourceResultFilter.ALL) }
    val visibleItems = remember(state.items, filter) {
        state.items.asSequence()
            .filter { item ->
                when (filter) {
                    CheckSourceResultFilter.ALL -> item.status != CheckSourceItemStatus.WAITING
                    CheckSourceResultFilter.PASSED -> item.status == CheckSourceItemStatus.PASSED
                    CheckSourceResultFilter.FAILED -> item.status == CheckSourceItemStatus.FAILED
                    CheckSourceResultFilter.BLOCKED -> item.status == CheckSourceItemStatus.BLOCKED
                }
            }
            .sortedByDescending(CheckSourceItemState::updatedAt)
            .toList()
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        BookSourceCheckTopBar(onBack)
        BookSourceCheckSummary(
            state = state,
            onCancel = onCancel,
            onHandleResults = onHandleResults,
        )
        BookSourceCheckResultList(
            items = visibleItems,
            selected = filter,
            processedCount = state.processedCount,
            passedCount = state.passedCount,
            failedCount = state.failedCount,
            blockedCount = state.blockedCount,
            onSelected = { filter = it },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BookSourceCheckTopBar(onBack: () -> Unit) {
    NgGlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 4.dp),
        role = NgMaterialRole.CONTROL,
        shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp),
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_bookshelf_manage_header_surface),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NgFloatingToolbarBackButton(onClick = onBack, width = 44.dp)
            Spacer(Modifier.width(2.dp))
            Text(
                text = stringResource(R.string.book_source_check_task_title),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BookSourceCheckSummary(
    state: CheckSourceTaskState,
    onCancel: () -> Unit,
    onHandleResults: () -> Unit,
) {
    val running = state.status == CheckSourceTaskStatus.RUNNING
    val canHandleResults = state.status == CheckSourceTaskStatus.COMPLETED &&
        state.totalCount > 0 && state.passedCount < state.totalCount
    var clockMillis by remember(state.runId) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(state.runId, state.status) {
        clockMillis = System.currentTimeMillis()
        while (state.status == CheckSourceTaskStatus.RUNNING) {
            delay(1_000L)
            clockMillis = System.currentTimeMillis()
        }
    }
    val elapsedEndMillis = if (running) {
        clockMillis
    } else {
        state.finishedAtMillis.takeIf { it > 0L } ?: clockMillis
    }
    val elapsedMillis = (elapsedEndMillis - state.startedAtMillis).coerceAtLeast(0L)
    val title = when (state.status) {
        CheckSourceTaskStatus.COMPLETED -> stringResource(R.string.book_source_check_completed)
        CheckSourceTaskStatus.CANCELLED -> stringResource(R.string.book_source_check_cancelled)
        else -> stringResource(R.string.book_source_check_running)
    }
    val currentText = when {
        running && state.currentSourceName.isNotBlank() -> stringResource(
            R.string.book_source_check_current_source,
            state.currentSourceName,
            stageLabel(state.currentStage),
        )
        state.status == CheckSourceTaskStatus.COMPLETED -> stringResource(
            R.string.book_source_check_processed_count,
            state.processedCount,
        )
        else -> ""
    }
    NgVisualSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        role = NgMaterialRole.CONTENT,
        cornerRadius = 18.dp,
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_surface_card),
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 21.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (currentText.isNotBlank()) {
                    Text(
                        text = currentText,
                        modifier = Modifier.padding(top = 5.dp),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (running || canHandleResults) {
                Surface(
                    modifier = Modifier
                        .height(30.dp)
                        .clickable(onClick = if (running) onCancel else onHandleResults),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(NgTheme.colors.primary),
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 11.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(
                                if (running) R.string.cancel
                                else R.string.book_source_check_handle_results
                            ),
                            color = Color.White,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${state.processedCount} / ${state.totalCount}",
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(
                    R.string.book_source_check_elapsed,
                    formatElapsedTime(elapsedMillis),
                ),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(NgTheme.colors.outlineVariant).copy(alpha = 0.24f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(state.progressFraction.coerceIn(0f, 1f))
                    .background(Color(NgTheme.colors.primary)),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            CheckSourceStatistic(
                label = stringResource(R.string.book_source_check_passed),
                value = state.passedCount,
                color = colorResource(R.color.ng_success),
            )
            CheckSourceStatistic(
                label = stringResource(R.string.book_source_check_failed),
                value = state.failedCount,
                color = colorResource(R.color.ng_error),
            )
            CheckSourceStatistic(
                label = stringResource(R.string.book_source_check_blocked),
                value = state.blockedCount,
                color = colorResource(R.color.ng_warning),
            )
            CheckSourceStatistic(
                label = stringResource(R.string.book_source_check_remaining),
                value = state.remainingCount,
                color = Color(NgTheme.colors.onSurfaceVariant),
            )
        }
    }
}

@Composable
private fun CheckSourceStatistic(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = color, fontSize = 12.sp, lineHeight = 16.sp)
        Text(
            text = value.toString(),
            modifier = Modifier.padding(top = 2.dp),
            color = color,
            fontSize = 19.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun BookSourceCheckFilters(
    selected: CheckSourceResultFilter,
    processedCount: Int,
    passedCount: Int,
    failedCount: Int,
    blockedCount: Int,
    onSelected: (CheckSourceResultFilter) -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        CheckSourceFilterItem(
            text = stringResource(R.string.book_source_check_filter_all, processedCount),
            selected = selected == CheckSourceResultFilter.ALL,
            onClick = { onSelected(CheckSourceResultFilter.ALL) },
            modifier = Modifier.weight(1f),
        )
        CheckSourceFilterItem(
            text = stringResource(R.string.book_source_check_filter_passed, passedCount),
            selected = selected == CheckSourceResultFilter.PASSED,
            onClick = { onSelected(CheckSourceResultFilter.PASSED) },
            modifier = Modifier.weight(1f),
        )
        CheckSourceFilterItem(
            text = stringResource(R.string.book_source_check_filter_failed, failedCount),
            selected = selected == CheckSourceResultFilter.FAILED,
            onClick = { onSelected(CheckSourceResultFilter.FAILED) },
            modifier = Modifier.weight(1f),
        )
        CheckSourceFilterItem(
            text = stringResource(
                R.string.book_source_check_filter_blocked,
                blockedCount,
            ),
            selected = selected == CheckSourceResultFilter.BLOCKED,
            onClick = { onSelected(CheckSourceResultFilter.BLOCKED) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CheckSourceFilterItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            color = Color(
                if (selected) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant
            ),
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
        Box(
            modifier = Modifier
                .padding(top = 5.dp)
                .width(42.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (selected) Color(NgTheme.colors.primary) else Color.Transparent
                ),
        )
    }
}

@Composable
private fun BookSourceCheckResultList(
    items: List<CheckSourceItemState>,
    selected: CheckSourceResultFilter,
    processedCount: Int,
    passedCount: Int,
    failedCount: Int,
    blockedCount: Int,
    onSelected: (CheckSourceResultFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    NgVisualSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, top = 2.dp, end = 14.dp, bottom = 6.dp),
        role = NgMaterialRole.CONTENT,
        cornerRadius = 18.dp,
        style = NgGlassDefaults.bookDetailStyle(
            containerColor = colorResource(R.color.ng_surface_card),
        ),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BookSourceCheckFilters(
                selected = selected,
                processedCount = processedCount,
                passedCount = passedCount,
                failedCount = failedCount,
                blockedCount = blockedCount,
                onSelected = onSelected,
            )
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.book_source_check_no_results),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    items(items, key = CheckSourceItemState::origin) { item ->
                        BookSourceCheckResultRow(item = item)
                        if (item != items.last()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                thickness = 0.6.dp,
                                color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.20f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BookSourceCheckResultRow(
    item: CheckSourceItemState,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.sourceName,
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(7.dp))
                CheckSourceTypeTag(item.sourceType)
            }
            Text(
                text = resultStatusText(item),
                modifier = Modifier.padding(top = 5.dp),
                color = resultStatusColor(item.status),
                fontSize = 13.sp,
                lineHeight = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            resultSummary(item).takeIf(String::isNotBlank)?.let { summary ->
                Text(
                    text = summary,
                    modifier = Modifier.padding(top = 2.dp),
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when (item.status) {
            CheckSourceItemStatus.PASSED -> R.drawable.ic_check_circle_outline
            CheckSourceItemStatus.BLOCKED -> R.drawable.ic_popup_blocked
            else -> null
        }?.let { iconRes ->
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = resultStatusColor(item.status),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun CheckSourceTypeTag(sourceType: Int) {
    val text = stringResource(
        when (sourceType) {
            BookSourceType.image -> R.string.book_source_tag_type_image
            BookSourceType.audio -> R.string.book_source_tag_type_audio
            BookSourceType.file -> R.string.book_source_tag_type_file
            BookSourceType.video -> R.string.book_source_tag_type_video
            else -> R.string.book_source_tag_type_text
        }
    )
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = Color(NgTheme.colors.selectedContainer),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
            color = Color(NgTheme.colors.primary),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun resultStatusText(item: CheckSourceItemState): String {
    return when (item.status) {
        CheckSourceItemStatus.RUNNING -> stringResource(
            R.string.book_source_check_checking_stage,
            stageLabel(item.stage),
        )
        CheckSourceItemStatus.PASSED -> stringResource(
            if (item.resultKind == CheckSourceResultKind.CONTENT_PARSED) {
                R.string.book_source_check_parsed
            } else {
                R.string.book_source_check_passed
            }
        )
        CheckSourceItemStatus.FAILED -> stringResource(R.string.book_source_check_failed)
        CheckSourceItemStatus.BLOCKED -> stringResource(R.string.book_source_check_popup_blocked)
        CheckSourceItemStatus.CANCELLED -> stringResource(R.string.book_source_check_cancelled)
        CheckSourceItemStatus.WAITING -> stringResource(R.string.book_source_check_waiting)
    }
}

@Composable
private fun resultSummary(item: CheckSourceItemState): String {
    return when (item.status) {
        CheckSourceItemStatus.FAILED -> item.message
        CheckSourceItemStatus.PASSED -> if (
            item.resultKind == CheckSourceResultKind.CONTENT_PARSED
        ) {
            stringResource(R.string.book_source_check_parsed_summary)
        } else {
            formatDuration(item.durationMillis)
        }
        else -> item.message
    }
}

@Composable
private fun stageLabel(stage: CheckSourceStage): String = stringResource(
    when (stage) {
        CheckSourceStage.PREPARING -> R.string.book_source_check_stage_preparing
        CheckSourceStage.DOMAIN -> R.string.domain
        CheckSourceStage.SEARCH -> R.string.search
        CheckSourceStage.DISCOVERY -> R.string.discovery
        CheckSourceStage.INFO -> R.string.source_tab_info
        CheckSourceStage.CATALOG -> R.string.chapter_list
        CheckSourceStage.CONTENT -> R.string.main_body
    }
)

@Composable
private fun resultStatusColor(status: CheckSourceItemStatus): Color = when (status) {
    CheckSourceItemStatus.PASSED, CheckSourceItemStatus.RUNNING -> colorResource(R.color.ng_success)
    CheckSourceItemStatus.FAILED -> colorResource(R.color.ng_error)
    CheckSourceItemStatus.BLOCKED -> colorResource(R.color.ng_warning)
    else -> Color(NgTheme.colors.onSurfaceVariant)
}

private fun formatElapsedTime(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d:%02d".format(hours, minutes, seconds)
}

@Composable
private fun formatDuration(durationMillis: Long): String {
    return stringResource(
        R.string.book_source_check_duration_seconds,
        durationMillis.coerceAtLeast(0L) / 1000f,
    )
}
