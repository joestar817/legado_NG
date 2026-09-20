package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.TextSelectionActionOrder
import io.legado.app.help.config.TextSelectionBuiltInAction
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.theme.NgTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun TextSelectionActionOrderPage(onBack: () -> Unit) {
    var keys by rememberSaveable { mutableStateOf(TextSelectionActionOrder.load().map { it.key }) }
    var disabled by rememberSaveable { mutableStateOf(TextSelectionActionOrder.disabledKeys().toList()) }
    val actions = keys.map { key -> TextSelectionBuiltInAction.entries.first { it.key == key } }
    val front = actions.filterNot { it.key in disabled }.take(5)
    val moreStart = front.lastOrNull()?.let { actions.indexOf(it) + 1 } ?: 0
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        keys = keys.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }
    val color = Color(NgTheme.colors.onSurface)
    val muted = Color(NgTheme.colors.onSurfaceVariant)
    val shape = RoundedCornerShape(16.dp)
    val panel = Modifier.clip(shape)
        .background(Color(NgTheme.colors.surface).copy(alpha = 0.12f))
        .border(0.6.dp, color.copy(alpha = 0.10f), shape)
    NgGlassSurface(
        modifier = Modifier.padding(8.dp).fillMaxSize(),
        shape = RoundedCornerShape(20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(Modifier.fillMaxSize().padding(bottom = 4.dp)) {
            AdvancedEditorHeader(
                title = stringResource(R.string.text_selection_toolbar),
                contentColor = color,
                onBack = onBack,
                actionLabel = stringResource(R.string.save),
                actionColor = Color(NgTheme.colors.primary),
                onAction = {
                    TextSelectionActionOrder.save(actions, disabled.toSet())
                    onBack()
                },
            )
            Row(
                Modifier.padding(horizontal = 10.dp).fillMaxWidth().height(64.dp).then(panel),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                front.forEach { action ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(painterResource(action.iconRes()), null, Modifier.size(24.dp), tint = color)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(action.titleRes), color = color, fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (front.isEmpty()) Spacer(Modifier.weight(1f))
                Text("⋮", color = color, fontSize = 24.sp,
                    modifier = Modifier.width(28.dp), maxLines = 1)
            }
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(horizontal = 10.dp).fillMaxWidth().weight(1f).then(panel),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                itemsIndexed(actions, key = { _, action -> action.key }) { index, action ->
                    ReorderableItem(reorderState, key = action.key) { isDragging ->
                        Column {
                            if (index == 0 && moreStart > 0) {
                                Text(stringResource(R.string.text_selection_toolbar_count, front.size),
                                    color = muted, fontSize = 12.sp, modifier = Modifier.height(20.dp))
                            }
                            if (index == moreStart) {
                                Text(stringResource(R.string.more), color = muted, fontSize = 12.sp,
                                    modifier = Modifier.height(20.dp))
                            }
                            val enabled = action.key !in disabled
                            Row(
                                Modifier.fillMaxWidth().height(42.dp)
                                    .background(if (isDragging) Color(NgTheme.colors.primary).copy(alpha = 0.10f) else Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(painterResource(R.drawable.ic_drag_handle), stringResource(R.string.sort),
                                    Modifier.draggableHandle().width(30.dp).fillMaxHeight().padding(6.dp), tint = muted)
                                Icon(painterResource(action.iconRes()), null,
                                    Modifier.padding(start = 8.dp).size(22.dp).alpha(if (enabled) 1f else 0.45f), tint = color)
                                Text(stringResource(action.titleRes),
                                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                                    color = if (enabled) color else muted.copy(alpha = 0.65f),
                                    fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                NgSwitchControl(
                                    checked = enabled,
                                    onCheckedChange = { checked ->
                                        disabled = if (checked) disabled - action.key else (disabled + action.key).distinct()
                                    },
                                    variant = NgSwitchControlVariant.COMPACT,
                                )
                            }
                            if (index != actions.lastIndex) HorizontalDivider(
                                thickness = 0.5.dp, color = color.copy(alpha = 0.10f))
                        }
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.text_selection_system_actions_hint), color = muted,
                    fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}

private fun TextSelectionBuiltInAction.iconRes(): Int = when (this) {
    TextSelectionBuiltInAction.REPLACE -> R.drawable.ic_cfg_replace
    TextSelectionBuiltInAction.AI_PURIFY -> R.drawable.ic_ai_purify
    TextSelectionBuiltInAction.COPY -> R.drawable.ic_copy
    TextSelectionBuiltInAction.HIGHLIGHT -> R.drawable.ic_text_highlight
    TextSelectionBuiltInAction.SEARCH -> R.drawable.ic_search
    TextSelectionBuiltInAction.READ_ALOUD -> R.drawable.ic_read_aloud
    TextSelectionBuiltInAction.DICTIONARY -> R.drawable.ic_translate
    TextSelectionBuiltInAction.BROWSER -> R.drawable.ic_web_outline
    TextSelectionBuiltInAction.SHARE -> R.drawable.ic_share
    TextSelectionBuiltInAction.EDIT -> R.drawable.ic_edit
}
