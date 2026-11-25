package io.legado.app.ui.book.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormSwitchSettingRow
import io.legado.app.ui.design.components.compose.NgSlider
import io.legado.app.ui.design.components.compose.NgSliderVariant
import io.legado.app.ui.design.theme.NgTheme
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun BookInfoLayoutSettings(
    layout: BookInfoLayout,
    onChange: (BookInfoLayout) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentLayout by rememberUpdatedState(layout)
    val currentOnChange by rememberUpdatedState(onChange)
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
        val toKey = to.key as? String ?: return@rememberReorderableLazyListState
        currentOnChange(currentLayout.move(fromKey, toKey))
    }
    NgFormPanel {
        LazyColumn(state = listState) {
            item(key = "coverSize") {
                BookInfoCoverSize(
                    percent = layout.coverPercent,
                    onChange = { onChange(layout.copy(coverPercent = it)) },
                )
                NgFormGroupDivider()
            }
            item(key = "sections") {
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .heightIn(min = 36.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.book_info_layout_sections),
                        modifier = Modifier.weight(1f),
                        color = Color(NgTheme.colors.onSurface),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.text_selection_drag_hint),
                        color = Color(NgTheme.colors.onSurfaceVariant),
                        fontSize = 12.sp,
                    )
                }
            }
            itemsIndexed(layout.order, key = { _, section -> section.key }) { index, section ->
                ReorderableItem(reorderState, key = section.key) { isDragging ->
                    val title = stringResource(section.titleRes)
                    val sort = stringResource(R.string.sort)
                    val moveUp = stringResource(R.string.book_info_layout_move_up)
                    val moveDown = stringResource(R.string.book_info_layout_move_down)
                    val actions = listOfNotNull(
                        layout.order.getOrNull(index - 1)?.let { previous ->
                            CustomAccessibilityAction(moveUp) {
                                onChange(layout.move(section.key, previous.key))
                                true
                            }
                        },
                        layout.order.getOrNull(index + 1)?.let { next ->
                            CustomAccessibilityAction(moveDown) {
                                onChange(layout.move(section.key, next.key))
                                true
                            }
                        },
                    )
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(
                                if (isDragging) Color(NgTheme.colors.primary).copy(alpha = 0.1f)
                                else Color.Transparent,
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            NgFormSwitchSettingRow(
                                title = title,
                                checked = section !in layout.hidden,
                                onCheckedChange = { onChange(layout.setVisible(section, it)) },
                                modifier = Modifier.weight(1f),
                            )
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .draggableHandle()
                                    .semantics {
                                        contentDescription = "$sort · $title"
                                        customActions = actions
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_drag_handle),
                                    contentDescription = null,
                                    tint = Color(NgTheme.colors.onSurfaceVariant),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        if (index < layout.order.lastIndex) NgFormGroupDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun BookInfoCoverSize(percent: Int, onChange: (Int) -> Unit) {
    val label = stringResource(R.string.book_info_cover_size)
    val range = BookInfoLayout.COVER_PERCENT_RANGE
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.book_info_cover_percent, percent),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 13.sp,
            )
        }
        NgSlider(
            value = percent.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            variant = NgSliderVariant.CONTINUOUS,
            modifier = Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = label },
        )
    }
}
