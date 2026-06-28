package io.legado.app.ui.widget.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class NgFunctionMenuAction(
    val icon: ImageVector,
    val text: String,
    val danger: Boolean = false,
    val dividerBefore: Boolean = false,
    val onClick: () -> Unit
)

@Composable
fun NgFunctionMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    actions: List<NgFunctionMenuAction>,
    modifier: Modifier = Modifier,
    width: Dp = 132.dp
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.width(width),
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp
    ) {
        actions.forEach { action ->
            if (action.dividerBefore) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 3.dp))
            }
            NgFunctionMenuItem(
                icon = action.icon,
                text = action.text,
                danger = action.danger,
                onClick = action.onClick
            )
        }
    }
}

@Composable
private fun NgFunctionMenuItem(
    icon: ImageVector,
    text: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val color = if (danger) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color
        )
    }
}
