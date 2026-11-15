package io.legado.app.ui.font

import android.graphics.Typeface
import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.NgInterfaceFontStore
import io.legado.app.help.font.FontFileEntry
import io.legado.app.ui.design.components.compose.NgDotsLoadingIndicator
import io.legado.app.ui.design.components.compose.LocalNgDialogColorMode
import io.legado.app.ui.design.components.compose.NgDialogColorMode
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckbox
import io.legado.app.ui.design.components.compose.NgFileSelectionCheckboxVariant
import io.legado.app.ui.design.components.compose.ngDialogContentCardColor
import io.legado.app.ui.design.theme.NgTheme
import kotlinx.coroutines.CancellationException

/** 字体管理的连续列表；扫描、选择范围与删除仍由宿主负责。 */
@Composable
internal fun FontManagerDialogContent(
    assets: List<FontFileEntry>,
    usages: Map<String, List<String>>,
    selectedIds: Set<String>,
    selectableIds: Set<String>,
    loading: Boolean,
    busy: Boolean,
    loadError: String?,
    previewRevision: Int,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    onInformation: (FontFileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NgTheme.colors
    val listState = rememberLazyListState()
    val enabled = !loading && !busy
    val selectionState = when {
        selectedIds.isEmpty() -> ToggleableState.Off
        selectableIds.isNotEmpty() && selectableIds.all { it in selectedIds } -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Column(modifier.fillMaxSize().padding(12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "本地字体",
                Modifier.weight(1f),
                color = Color(colors.onSurface),
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            FontManagerHeaderAction(R.drawable.ic_baseline_close, "关闭字体管理", !busy, onDismiss)
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = ngDialogContentCardColor(),
        ) {
            Column(Modifier.fillMaxSize()) {
                loadError?.let { error ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            error,
                            Modifier.weight(1f),
                            color = Color(colors.error),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(onClick = onRetry, enabled = enabled) { Text("重试") }
                    }
                }
                if (assets.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        if (loading) {
                            NgDotsLoadingIndicator()
                        } else if (loadError == null) {
                            Text("暂无本地字体", color = Color(colors.onSurfaceVariant), fontSize = 14.sp)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) {
                        itemsIndexed(assets, key = { _, asset -> asset.id }) { index, asset ->
                            FontManagerDialogRow(
                                asset = asset,
                                usage = usages[asset.id].orEmpty(),
                                selected = asset.id in selectedIds,
                                selectable = asset.id in selectableIds,
                                enabled = enabled,
                                usageUnknown = loadError != null,
                                previewRevision = previewRevision,
                                onToggle = { onToggle(asset.id) },
                                onInformation = { onInformation(asset) },
                            )
                            if (index < assets.lastIndex) FontManagerDivider(Modifier.padding(horizontal = 12.dp))
                        }
                    }
                }
                FontManagerDivider()
                FontManagerSelectionFooter(
                    selectionState = selectionState,
                    selectedCount = selectedIds.size,
                    enabled = enabled && loadError == null,
                    hasSelectable = selectableIds.isNotEmpty(),
                    onSelectAll = onSelectAll,
                    onDelete = onDelete,
                )
            }
        }
    }
}

@Composable
private fun FontManagerHeaderAction(
    @DrawableRes icon: Int,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(40.dp)) {
        Icon(
            painterResource(icon), description,
            Modifier.size(22.dp),
            tint = Color(NgTheme.colors.onSurface).copy(alpha = if (enabled) 1f else .38f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FontManagerDialogRow(
    asset: FontFileEntry,
    usage: List<String>,
    selected: Boolean,
    selectable: Boolean,
    enabled: Boolean,
    usageUnknown: Boolean,
    previewRevision: Int,
    onToggle: () -> Unit,
    onInformation: () -> Unit,
) {
    val context = LocalContext.current
    val colors = NgTheme.colors
    val typeface by produceState<Typeface?>(null, asset.uri, asset.sha256, previewRevision) {
        value = try {
            NgInterfaceFontStore.load(context, asset.uri).typeface
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }
    val summary = buildString {
        append(Formatter.formatFileSize(context, asset.size))
        when {
            usageUnknown -> append(" · 使用情况未确认")
            usage.isNotEmpty() -> append(" · ${usage.first()}")
            !asset.canDelete -> append(" · 不可删除")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(colors.primary).copy(alpha = .06f) else Color.Transparent)
            .combinedClickable(
                enabled = enabled,
                role = if (selectable) Role.Checkbox else Role.Button,
                onClick = if (selectable) onToggle else onInformation,
                onLongClick = onInformation,
                onLongClickLabel = "查看字体详情",
            )
            .then(if (selectable) Modifier.semantics {
                toggleableState = if (selected) ToggleableState.On else ToggleableState.Off
            } else Modifier)
            .heightIn(min = 72.dp)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            if (usage.isNotEmpty()) {
                Icon(
                    painterResource(R.drawable.ic_lock_outline), "字体正在使用",
                    Modifier.size(21.dp), tint = Color(colors.onSurfaceVariant).copy(alpha = .65f),
                )
            } else {
                NgFileSelectionCheckbox(
                    checked = selected,
                    onCheckedChange = null,
                    enabled = enabled && selectable,
                    variant = NgFileSelectionCheckboxVariant.COMPACT,
                    checkmarkColor = fontManagerCheckmarkColor(),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
            Text(
                "Aa", color = Color(colors.onSurface), fontSize = 23.sp,
                fontFamily = typeface?.let(::FontFamily), maxLines = 1,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                asset.name, color = Color(colors.onSurface), fontSize = 15.sp,
                lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                summary, color = Color(colors.onSurfaceVariant), fontSize = 12.sp,
                lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun FontManagerSelectionFooter(
    selectionState: ToggleableState,
    selectedCount: Int,
    enabled: Boolean,
    hasSelectable: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = NgTheme.colors
    val selectEnabled = enabled && hasSelectable
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.heightIn(min = 48.dp).triStateToggleable(
                state = selectionState,
                enabled = selectEnabled,
                role = Role.Checkbox,
                onClick = onSelectAll,
            ).padding(end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = selectionState,
                onClick = null,
                enabled = selectEnabled,
                modifier = Modifier.size(32.dp),
                colors = CheckboxDefaults.colors(
                    checkedColor = Color(colors.primary),
                    checkmarkColor = fontManagerCheckmarkColor(),
                    uncheckedColor = Color(colors.onSurfaceVariant).copy(alpha = .62f),
                    disabledUncheckedColor = Color(colors.onSurfaceVariant).copy(alpha = .28f),
                    disabledCheckedColor = Color(colors.primary).copy(alpha = .38f),
                    disabledIndeterminateColor = Color(colors.primary).copy(alpha = .38f),
                ),
            )
            Text("全选", color = Color(colors.onSurface).copy(alpha = if (selectEnabled) 1f else .38f),
                fontSize = 14.sp, maxLines = 1)
        }
        VerticalDivider(Modifier.height(18.dp), color = Color(colors.outlineVariant).copy(alpha = .3f))
        Text(
            "已选 $selectedCount", Modifier.weight(1f).padding(start = 10.dp),
            color = Color(colors.onSurfaceVariant), fontSize = 13.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        val deleteEnabled = enabled && selectedCount > 0
        TextButton(
            onClick = onDelete,
            enabled = deleteEnabled,
            contentPadding = PaddingValues(horizontal = 8.dp),
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color(colors.error),
                disabledContentColor = Color(colors.error).copy(alpha = .38f),
            ),
        ) {
            Icon(painterResource(R.drawable.ic_book_info_delete), null, Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text("删除", fontSize = 15.sp, maxLines = 1)
        }
    }
}

@Composable
private fun fontManagerCheckmarkColor(): Color = if (LocalNgDialogColorMode.current == NgDialogColorMode.INHERIT_HOST) {
    Color(NgTheme.colors.onPrimary)
} else {
    Color.White
}

@Composable
private fun FontManagerDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = .5.dp, color = Color(NgTheme.colors.outlineVariant).copy(alpha = .22f))
}
