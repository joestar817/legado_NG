package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.theme.NgTheme

/**
 * Filled-pill segmented control matching 预设 | 调整 | 高亮:
 * unselected uses [contentColor] on a translucent dock; selected uses
 * [selectedContainerColor] with [selectedContentColor].
 */
@Composable
fun NgSegmentedDock(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentColor: Color = Color(NgTheme.colors.onSurface),
    selectedContainerColor: Color = Color(NgTheme.colors.primary),
    selectedContentColor: Color = Color(NgTheme.colors.onPrimary),
    dockSurfaceColor: Color = defaultDockSurfaceColor(),
    height: Dp = 40.dp,
    fontSize: TextUnit = 13.sp,
) {
    require(labels.isNotEmpty()) { "NgSegmentedDock requires at least one label" }
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(dockSurfaceColor)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        labels.forEachIndexed { index, label ->
            val selected = index == selectedIndex.coerceIn(labels.indices)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) Modifier.background(selectedContainerColor)
                        else Modifier
                    )
                    .clickable(role = Role.Tab) { onSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) selectedContentColor else contentColor,
                    fontSize = fontSize,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun defaultDockSurfaceColor(): Color {
    val base = if (NgTheme.snapshot.isDark) Color(0xFF1F1F1F) else Color.White
    return base.copy(alpha = 0.28f)
}
