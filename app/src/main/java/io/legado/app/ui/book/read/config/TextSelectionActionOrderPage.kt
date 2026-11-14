package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.TextSelectionActionOrder
import io.legado.app.help.config.TextSelectionBuiltInAction
import io.legado.app.ui.book.read.readFloatingGlassStyle
import io.legado.app.ui.config.NgThemeSheetActionButton
import io.legado.app.ui.design.components.compose.NgDrawerDragHandle
import io.legado.app.ui.design.components.compose.NgDrawerDragHandleVariant
import io.legado.app.ui.design.components.compose.NgFormGroupDivider
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.components.compose.NgFormPanelVariant
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgSwitchControl
import io.legado.app.ui.design.components.compose.NgSwitchControlVariant
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.utils.toastOnUi
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
internal fun TextSelectionActionOrderPage(onBack: () -> Unit) {
    var keys by rememberSaveable { mutableStateOf(TextSelectionActionOrder.load().map { it.key }) }
    var disabled by rememberSaveable { mutableStateOf(TextSelectionActionOrder.disabledKeys().toList()) }
    var showText by rememberSaveable { mutableStateOf(TextSelectionActionOrder.showText()) }
    val actions = keys.map { key -> TextSelectionBuiltInAction.entries.first { it.key == key } }
    val front = actions.filterNot { it.key in disabled }.take(5)
    val moreStart = front.lastOrNull()?.let { actions.indexOf(it) + 1 } ?: 0
    val context = LocalContext.current
    val minLimitMessage = stringResource(R.string.text_selection_toolbar_min_limit)
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val reordered = keys.toMutableList().apply { add(to.index, removeAt(from.index)) }
        keys = reordered
        TextSelectionActionOrder.saveOrder(reordered.map { key ->
            TextSelectionBuiltInAction.entries.first { it.key == key }
        })
    }
    val color = Color(NgTheme.colors.onSurface)
    val muted = Color(NgTheme.colors.onSurfaceVariant)
    NgGlassSurface(
        modifier = Modifier.padding(top = 8.dp).fillMaxSize(),
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        style = readFloatingGlassStyle(),
    ) {
        Column(Modifier.fillMaxSize().padding(bottom = 12.dp)) {
            NgDrawerDragHandle(modifier = Modifier.padding(top = 6.dp), variant = NgDrawerDragHandleVariant.COMPACT)
            ReadToolOrderHeader(
                title = stringResource(R.string.text_selection_toolbar),
                color = color,
                onBack = onBack,
            )
            Spacer(Modifier.height(6.dp))
            NgFormPanel(
                variant = NgFormPanelVariant.LARGE_ROUNDED,
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            ) {
                val showTextLabel = stringResource(R.string.text_selection_show_text)
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 14.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.text_selection_toolbar_preview), Modifier.weight(1f),
                        color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(showTextLabel, color = muted, fontSize = 13.sp)
                    NgSwitchControl(
                        checked = showText,
                        onCheckedChange = {
                            showText = it
                            TextSelectionActionOrder.setShowText(it)
                        },
                        modifier = Modifier.semantics { contentDescription = showTextLabel },
                        variant = NgSwitchControlVariant.COMPACT,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(NgTheme.colors.surfaceVariant).copy(alpha = 0.32f)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    front.forEach { action ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(painterResource(action.iconRes()), null,
                                Modifier.size(if (showText) 24.dp else 28.dp), tint = color)
                            if (showText) {
                                Spacer(Modifier.height(4.dp))
                                Text(stringResource(action.titleRes), color = color, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (front.isEmpty()) Spacer(Modifier.weight(1f))
                    Text("⋮", color = color, fontSize = if (showText) 24.sp else 28.sp,
                        modifier = Modifier.width(28.dp), maxLines = 1)
                }
                Spacer(Modifier.height(16.dp))
                NgFormGroupDivider()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                ) {
                    itemsIndexed(actions, key = { _, action -> action.key }) { index, action ->
                        ReorderableItem(reorderState, key = action.key) { isDragging ->
                            Column {
                                if (index == 0) {
                                    TextSelectionToolbarGroupHeader(
                                        title = if (moreStart > 0) {
                                            stringResource(R.string.text_selection_toolbar_count, front.size)
                                        } else stringResource(R.string.more),
                                        color = color, muted = muted, showDragHint = true,
                                    )
                                } else if (index == moreStart) {
                                    TextSelectionToolbarGroupHeader(
                                        title = stringResource(R.string.more), color = color, muted = muted,
                                        showDragHint = false,
                                    )
                                }
                                val enabled = action.key !in disabled
                                val isLastEnabled = enabled && front.size == 1
                                val actionTitle = stringResource(action.titleRes)
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 42.dp)
                                        .background(if (isDragging) Color(NgTheme.colors.primary).copy(alpha = 0.10f) else Color.Transparent),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(painterResource(R.drawable.ic_drag_handle), stringResource(R.string.sort),
                                        Modifier.draggableHandle().size(width = 30.dp, height = 42.dp).padding(6.dp), tint = muted)
                                    Icon(painterResource(action.iconRes()), null,
                                        Modifier.padding(start = 8.dp).size(22.dp).alpha(if (enabled) 1f else 0.45f), tint = color)
                                    Text(actionTitle,
                                        modifier = Modifier.padding(start = 16.dp).weight(1f),
                                        color = if (enabled) color else muted.copy(alpha = 0.65f),
                                        fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Box(
                                        if (isLastEnabled) Modifier.clickable { context.toastOnUi(minLimitMessage) } else Modifier,
                                    ) {
                                        NgSwitchControl(
                                            checked = enabled,
                                            enabled = !isLastEnabled,
                                            onCheckedChange = { checked ->
                                                disabled = if (checked) disabled - action.key else (disabled + action.key).distinct()
                                                TextSelectionActionOrder.setDisabledKeys(disabled.toSet())
                                            },
                                            modifier = Modifier.semantics {
                                                contentDescription = actionTitle
                                                if (isLastEnabled) stateDescription = minLimitMessage
                                            },
                                            variant = NgSwitchControlVariant.COMPACT,
                                        )
                                    }
                                }
                                if (index != actions.lastIndex) NgFormGroupDivider(horizontalPadding = 0.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReadToolOrderHeader(title: String, color: Color, onBack: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp)) {
        NgThemeSheetActionButton(
            onClick = onBack,
            contentDescription = stringResource(R.string.back),
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(painterResource(R.drawable.ic_arrow_back), null, Modifier.size(22.dp), tint = color)
        }
        Text(
            title,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 78.dp).align(Alignment.Center),
            color = color, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TextSelectionToolbarGroupHeader(title: String, color: Color, muted: Color, showDragHint: Boolean) {
    Row(Modifier.fillMaxWidth().heightIn(min = 34.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = color, fontSize = 14.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showDragHint) {
            Text(stringResource(R.string.text_selection_drag_hint), color = muted, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun TextSelectionBuiltInAction.iconRes(): Int = when (this) {
    TextSelectionBuiltInAction.REPLACE -> R.drawable.ic_cfg_replace
    TextSelectionBuiltInAction.AI_PURIFY -> R.drawable.ic_text_selection_ai_purify
    TextSelectionBuiltInAction.COPY -> R.drawable.ic_text_selection_copy
    TextSelectionBuiltInAction.HIGHLIGHT -> R.drawable.ic_text_selection_highlight
    TextSelectionBuiltInAction.SEARCH -> R.drawable.ic_search
    TextSelectionBuiltInAction.READ_ALOUD -> R.drawable.ic_read_aloud
    TextSelectionBuiltInAction.CHARACTER_CARD -> R.drawable.ic_bookshelf_action_user
    TextSelectionBuiltInAction.DICTIONARY -> R.drawable.ic_translate
    TextSelectionBuiltInAction.BROWSER -> R.drawable.ic_web_outline
    TextSelectionBuiltInAction.SHARE -> R.drawable.ic_share
    TextSelectionBuiltInAction.EDIT -> R.drawable.ic_edit
}
