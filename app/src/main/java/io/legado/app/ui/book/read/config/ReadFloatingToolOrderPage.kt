package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.ReadFloatingTool
import io.legado.app.help.config.ReadFloatingToolSettings
import io.legado.app.ui.book.read.FloatingToolToggleLimit
import io.legado.app.ui.book.read.floatingToolToggleLimit
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormPanelVariant
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toastOnUi
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun ReadFloatingToolOrderPage(onBack: () -> Unit, onChanged: () -> Unit, maxEnabled: () -> Int) {
    var keys by rememberSaveable { mutableStateOf(ReadFloatingToolSettings.order().map { it.key }) }
    var disabled by rememberSaveable { mutableStateOf(ReadFloatingToolSettings.disabledKeys().toList()) }
    val tools = keys.map { key -> ReadFloatingTool.entries.first { it.key == key } }
    val enabledCount = keys.count { it !in disabled }
    val capacity = maxEnabled()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        keys = keys.toMutableList().apply { add(to.index, removeAt(from.index)) }
        ReadFloatingToolSettings.saveOrder(keys.map { key -> ReadFloatingTool.entries.first { it.key == key } })
        onChanged()
    }
    val color = Color(NgTheme.colors.onSurface)
    val muted = Color(NgTheme.colors.onSurfaceVariant)
    val maxListHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.85f - 140.dp).coerceAtLeast(100.dp)
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        ReadToolOrderHeader(stringResource(R.string.read_floating_side_tool_title), color, onBack)
        Spacer(Modifier.height(6.dp))
        NgFormPanel(
            variant = NgFormPanelVariant.LARGE_ROUNDED,
            modifier = Modifier.padding(horizontal = 16.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.read_floating_side_tool_count, enabledCount),
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(stringResource(R.string.text_selection_drag_hint), color = muted, fontSize = 12.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = maxListHeight),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
            ) {
                itemsIndexed(tools, key = { _, tool -> tool.key }) { index, tool ->
                    ReorderableItem(reorderState, key = tool.key) { isDragging ->
                        Column {
                            val enabled = tool.key !in disabled
                            val title = stringResource(tool.titleRes)
                            val limit = floatingToolToggleLimit(enabled, enabledCount, capacity)
                            val limitMessage = when (limit) {
                                FloatingToolToggleLimit.MINIMUM -> stringResource(R.string.read_floating_side_tool_min_limit)
                                FloatingToolToggleLimit.MAXIMUM -> stringResource(R.string.read_floating_side_tool_max_limit, capacity)
                                null -> ""
                            }
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                                    .background(if (isDragging) Color(NgTheme.colors.primary).copy(alpha = 0.10f) else Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_drag_handle),
                                    stringResource(R.string.sort) + " · " + title,
                                    Modifier.draggableHandle().size(width = 30.dp, height = 52.dp).padding(6.dp),
                                    tint = muted,
                                )
                                Box(
                                    Modifier.padding(start = 8.dp).size(22.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painterResource(tool.iconRes), null,
                                        Modifier.size(tool.iconSizeDp.dp).alpha(if (enabled) 1f else 0.45f),
                                        tint = color,
                                    )
                                }
                                Text(
                                    title, Modifier.padding(start = 16.dp).weight(1f),
                                    color = if (enabled) color else muted.copy(alpha = 0.65f),
                                    fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                )
                                Box(
                                    if (limit == null) Modifier else Modifier.clickable { context.toastOnUi(limitMessage) },
                                ) {
                                    NgSwitchControl(
                                        checked = enabled,
                                        enabled = limit == null,
                                        onCheckedChange = { checked ->
                                            disabled = if (checked) disabled - tool.key else (disabled + tool.key).distinct()
                                            ReadFloatingToolSettings.setDisabledKeys(disabled.toSet())
                                            onChanged()
                                        },
                                        modifier = Modifier.semantics {
                                            contentDescription = title
                                            if (limit != null) stateDescription = limitMessage
                                        },
                                        variant = NgSwitchControlVariant.COMPACT,
                                    )
                                }
                            }
                            if (index != tools.lastIndex) NgFormGroupDivider(horizontalPadding = 0.dp)
                        }
                    }
                }
            }
        }
    }
}
