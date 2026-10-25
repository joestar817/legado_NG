package io.legado.app.ui.book.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.legado.app.R
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogDivider
import io.legado.app.ui.design.components.compose.NgFormActionButton
import io.legado.app.ui.design.components.compose.NgFormActionButtonAppearance
import io.legado.app.ui.design.components.compose.NgSearchBar
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun SearchScopeDialog(
    groups: List<String>,
    sources: List<BookSourcePart>,
    onSourceQueryChange: (String) -> Unit,
    onApply: (SearchScope) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceMode by remember { mutableStateOf(false) }
    var selectedGroups by remember { mutableStateOf(emptySet<String>()) }
    var selectedSource by remember { mutableStateOf<BookSourcePart?>(null) }
    var sourceQuery by remember { mutableStateOf("") }
    var showSourceFilter by remember { mutableStateOf(false) }

    fun close() {
        onSourceQueryChange("")
        onDismiss()
    }

    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        NgDialog(
            title = null,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.75f),
            actions = {
                TextButton(
                    onClick = {
                        onApply(SearchScope(""))
                        close()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.all_source),
                        color = Color(NgTheme.colors.primary),
                        fontSize = 15.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.weight(1f))
                NgFormActionButton(
                    text = stringResource(R.string.cancel),
                    onClick = ::close,
                    modifier = Modifier.width(88.dp),
                    appearance = NgFormActionButtonAppearance.DIALOG,
                    buttonHeight = 48.dp,
                    minimumWidth = 88.dp,
                    textSize = 16.sp,
                    textLineHeight = 20.sp
                )
                NgFormActionButton(
                    text = stringResource(R.string.ok),
                    onClick = {
                        val scope = if (sourceMode) {
                            selectedSource?.let(::SearchScope) ?: SearchScope("")
                        } else {
                            SearchScope(groups.filter { it in selectedGroups })
                        }
                        onApply(scope)
                        close()
                    },
                    modifier = Modifier.width(88.dp),
                    variant = NgButtonVariant.PRIMARY,
                    appearance = NgFormActionButtonAppearance.DIALOG,
                    buttonHeight = 48.dp,
                    minimumWidth = 88.dp,
                    textSize = 16.sp,
                    textLineHeight = 20.sp
                )
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.search_scope),
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 20.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium
                )
                if (sourceMode) {
                    IconButton(
                        onClick = {
                            showSourceFilter = !showSourceFilter
                            if (!showSourceFilter) {
                                sourceQuery = ""
                                onSourceQueryChange("")
                            }
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_search),
                            contentDescription = stringResource(R.string.search),
                            tint = Color(NgTheme.colors.onSurfaceVariant),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(NgTheme.colors.primary).copy(alpha = 0.09f))
                    .selectableGroup()
                    .padding(4.dp)
            ) {
                ScopeModeTab(
                    text = stringResource(R.string.group),
                    selected = !sourceMode,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        sourceMode = false
                        showSourceFilter = false
                        sourceQuery = ""
                        onSourceQueryChange("")
                    }
                )
                ScopeModeTab(
                    text = stringResource(R.string.book_source),
                    selected = sourceMode,
                    modifier = Modifier.weight(1f),
                    onClick = { sourceMode = true }
                )
            }

            if (sourceMode && showSourceFilter) {
                NgSearchBar(
                    query = sourceQuery,
                    onQueryChange = { value ->
                        sourceQuery = value
                        onSourceQueryChange(value)
                    },
                    hint = stringResource(R.string.screen),
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            val listState = rememberLazyListState()
            val scrollbarColor = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.45f)
            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                // Leave only whole rows above the fixed actions, including when the filter opens.
                val listHeight = 48.dp * (maxHeight / 48.dp).toInt()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listHeight)
                        .scopeScrollbar(listState, scrollbarColor)
                        .then(if (sourceMode) Modifier.selectableGroup() else Modifier)
                ) {
                    if (sourceMode) {
                        items(sources, key = { it.bookSourceUrl }) { source ->
                            SearchScopeRow(
                                text = source.bookSourceName,
                                selected = selectedSource == source,
                                multiSelect = false,
                                onClick = { selectedSource = source }
                            )
                        }
                    } else {
                        items(groups, key = { it }) { group ->
                            SearchScopeRow(
                                text = group,
                                selected = group in selectedGroups,
                                multiSelect = true,
                                onClick = {
                                    selectedGroups = if (group in selectedGroups) {
                                        selectedGroups - group
                                    } else {
                                        selectedGroups + group
                                    }
                                }
                            )
                        }
                    }
                }
            }
            NgDialogDivider()
        }
    }
}

@Composable
private fun ScopeModeTab(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(
                if (selected) colorResource(R.color.ng_surface_card)
                else Color.Transparent
            )
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) Color(NgTheme.colors.primary)
            else Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun SearchScopeRow(
    text: String,
    selected: Boolean,
    multiSelect: Boolean,
    onClick: () -> Unit
) {
    val selection = if (multiSelect) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onClick() })
    } else {
        Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
    }
    val primary = Color(NgTheme.colors.primary)
    val unchecked = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.74f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(selection)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (multiSelect) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = null,
                    modifier = Modifier.size(20.dp),
                    colors = CheckboxDefaults.colors(
                        checkedColor = primary,
                        checkmarkColor = Color.White,
                        uncheckedColor = unchecked
                    )
                )
            } else {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    modifier = Modifier.size(20.dp),
                    colors = RadioButtonDefaults.colors(
                        selectedColor = primary,
                        unselectedColor = unchecked
                    )
                )
            }
            Spacer(Modifier.width(20.dp))
            Text(
                text = text,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        NgDialogDivider(Modifier.align(Alignment.BottomCenter))
    }
}

private fun Modifier.scopeScrollbar(state: LazyListState, color: Color): Modifier = drawWithContent {
    drawContent()
    val info = state.layoutInfo
    val first = info.visibleItemsInfo.firstOrNull() ?: return@drawWithContent
    val contentHeight = first.size.toFloat() * info.totalItemsCount
    if (contentHeight <= size.height || first.size == 0) return@drawWithContent
    val thumbHeight = (size.height * size.height / contentHeight)
        .coerceIn(24.dp.toPx().coerceAtMost(size.height), size.height)
    val scrollOffset = first.index * first.size - first.offset
    val fraction = (scrollOffset / (contentHeight - size.height)).coerceIn(0f, 1f)
    val width = 3.dp.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width, (size.height - thumbHeight) * fraction),
        size = Size(width, thumbHeight),
        cornerRadius = CornerRadius(width / 2f)
    )
}
