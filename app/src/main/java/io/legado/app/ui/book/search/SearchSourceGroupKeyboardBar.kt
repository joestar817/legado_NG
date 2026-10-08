package io.legado.app.ui.book.search

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

/** 沿用书源编辑键盘栏的单排几何，搜索范围仍由页面唯一状态负责。 */
@Composable
internal fun SearchSourceGroupKeyboardBar(
    groups: List<String>,
    scopeNames: List<String>,
    isSourceScope: Boolean,
    onApplyScope: (SearchScope) -> Unit,
) {
    val allSelected = !isSourceScope && scopeNames.isEmpty()
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        contentPadding = PaddingValues(5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all:") {
            SearchSourceGroupKeyboardItem(
                text = stringResource(R.string.all_source),
                selected = allSelected,
                onClick = {
                    if (!allSelected) onApplyScope(SearchScope(""))
                },
            )
        }
        if (isSourceScope) {
            scopeNames.firstOrNull()?.let { sourceName ->
                item(key = "source:$sourceName") {
                    SearchSourceGroupKeyboardItem(
                        text = sourceName,
                        selected = true,
                        iconRes = R.drawable.ic_check_source,
                    )
                }
            }
        }
        items(groups, key = { "group:$it" }) { group ->
            SearchSourceGroupKeyboardItem(
                text = group,
                selected = !isSourceScope && group in scopeNames,
                onClick = {
                    if (isSourceScope || scopeNames.size != 1 || scopeNames.first() != group) {
                        onApplyScope(SearchScope(listOf(group)))
                    }
                },
            )
        }
    }
}

@Composable
private fun SearchSourceGroupKeyboardItem(
    text: String,
    selected: Boolean,
    @DrawableRes iconRes: Int? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(NgTheme.shapes.smallDp.dp)
    val foreground = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurface)
    Row(
        modifier = Modifier
            .height(32.dp)
            .widthIn(min = 44.dp, max = 200.dp)
            .clip(shape)
            .background(
                Color(if (selected) NgTheme.colors.selectedContainer else NgTheme.colors.surfaceContainerHigh)
            )
            .then(if (selected) Modifier.border(1.dp, foreground, shape) else Modifier)
            .focusProperties { canFocus = false }
            .then(
                if (onClick != null) {
                    Modifier.selectable(selected = selected, onClick = onClick)
                } else {
                    Modifier.semantics(mergeDescendants = true) { this.selected = selected }
                }
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = text,
            color = foreground,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
