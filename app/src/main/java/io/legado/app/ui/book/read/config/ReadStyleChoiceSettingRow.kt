package io.legado.app.ui.book.read.config

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun ReadStyleChoiceSettingRow(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    contentColor: Color,
    @DrawableRes leadingIconRes: Int? = null,
    summary: String? = null,
    rowHeight: Dp = 48.dp,
    preferPopupBelow: Boolean = false,
    useVisibleWindowBounds: Boolean = false,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options = labels.mapIndexed { index, label -> ReadMoreConfigOption(index.toString(), label) }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = TextStyle(fontFamily = NgTheme.fontFamily, fontSize = 14.sp)
    val availableWidth = (LocalConfiguration.current.screenWidthDp.dp - 16.dp).coerceAtLeast(1.dp)
    val minimumWidth = minOf(100.dp * density.fontScale.coerceAtLeast(1f), availableWidth)
    val longest = labels.maxOfOrNull { label ->
        with(density) { measurer.measure(label, textStyle, maxLines = 1).size.width.toDp() }
    } ?: 0.dp
    val menuWidth = (longest + 40.dp).coerceIn(minimumWidth, availableWidth)

    Box(Modifier.padding(horizontal = 14.dp)) {
        EditorNavigationRow(
            title = title,
            summary = summary ?: labels.getOrNull(selectedIndex).orEmpty(),
            color = contentColor,
            leadingIconRes = leadingIconRes,
            leadingIcon = leadingIcon,
            trailingContent = trailingContent,
            enabled = enabled,
            rowHeight = rowHeight,
            onClick = { expanded = true },
        )
        Box(Modifier.align(Alignment.CenterEnd).width(1.dp).height(rowHeight)) {
            ReadChoicePopup(
                expanded = expanded,
                options = options,
                selectedValue = selectedIndex.toString(),
                onDismissRequest = { expanded = false },
                onSelected = { value ->
                    value.toIntOrNull()?.takeIf { it in labels.indices }?.let(onSelected)
                },
                preferPopupBelow = preferPopupBelow,
                scrollable = true,
                variant = ReadChoicePopupVariant.ADAPTIVE,
                menuWidth = menuWidth,
                useVisibleWindowBounds = useVisibleWindowBounds,
            )
        }
    }
}
