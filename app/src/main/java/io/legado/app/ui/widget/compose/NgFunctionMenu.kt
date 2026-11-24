package io.legado.app.ui.widget.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import io.legado.app.ui.design.components.compose.NgEndAlignedMenuPositionProvider

data class NgFunctionMenuAction(
    val icon: ImageVector,
    val text: String,
    val danger: Boolean = false,
    val dividerBefore: Boolean = false,
    val onClick: () -> Unit
)

enum class NgFunctionMenuVariant {
    STANDARD,
    ATTACHMENT,
}

@Composable
fun NgFunctionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<NgFunctionMenuAction>,
    modifier: Modifier = Modifier,
    width: Dp = 132.dp,
    containerColor: Color? = null,
    variant: NgFunctionMenuVariant = NgFunctionMenuVariant.STANDARD,
    openAboveAnchor: Boolean = false,
) {
    val attachmentMenu = variant == NgFunctionMenuVariant.ATTACHMENT
    val widthModifier = if (attachmentMenu) {
        val maxWidth = (LocalConfiguration.current.screenWidthDp.dp - 16.dp).coerceIn(1.dp, 208.dp)
        Modifier.widthIn(min = 152.dp.coerceAtMost(maxWidth), max = maxWidth)
    } else {
        Modifier.width(width)
    }
    val shape = RoundedCornerShape(18.dp)
    val color = containerColor ?: MaterialTheme.colorScheme.surface
    val tonalElevation = if (containerColor == null) 4.dp else 0.dp
    val items: @Composable ColumnScope.() -> Unit = {
        actions.forEach { action ->
            if (action.dividerBefore) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
            }
            NgFunctionMenuItem(
                icon = action.icon,
                text = action.text,
                danger = action.danger,
                attachmentMenu = attachmentMenu,
                onClick = action.onClick
            )
        }
    }
    if (openAboveAnchor) {
        if (!expanded) return
        val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
        val positionProvider = remember(margin) {
            NgEndAlignedMenuPositionProvider(margin, openAboveAnchor = true)
        }
        Popup(
            popupPositionProvider = positionProvider,
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            Surface(
                modifier = modifier.then(widthModifier),
                shape = shape,
                color = color,
                tonalElevation = tonalElevation,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
                    content = items,
                )
            }
        }
    } else {
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.then(widthModifier),
            shape = shape,
            containerColor = color,
            tonalElevation = tonalElevation,
            shadowElevation = 8.dp,
            content = items,
        )
    }
}

@Composable
private fun NgFunctionMenuItem(
    icon: ImageVector,
    text: String,
    danger: Boolean,
    attachmentMenu: Boolean,
    onClick: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val color = if (danger) colors.error else colors.onSurface
    val rowLayout = if (attachmentMenu) {
        Modifier.heightIn(min = 48.dp).padding(horizontal = 14.dp, vertical = 6.dp)
    } else {
        Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
    }
    val iconColor = if (attachmentMenu && !danger) colors.primary else color
    val iconLayout = if (attachmentMenu) {
        Modifier.size(34.dp)
            .background(iconColor.copy(alpha = 0.10f), CircleShape)
            .padding(7.dp)
    } else {
        Modifier.size(20.dp)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = if (attachmentMenu) Role.Button else null, onClick = onClick)
            .then(rowLayout),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (attachmentMenu) 12.dp else 10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = iconLayout,
        )
        Text(
            text = text,
            style = if (attachmentMenu) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            color = color,
            maxLines = if (attachmentMenu) 2 else Int.MAX_VALUE,
            overflow = if (attachmentMenu) TextOverflow.Ellipsis else TextOverflow.Clip,
        )
    }
}
