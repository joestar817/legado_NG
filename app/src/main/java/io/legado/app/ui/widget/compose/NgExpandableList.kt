package io.legado.app.ui.widget.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.components.compose.NgCircularSelectionIndicator
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme

enum class NgListBadgeTone {
    Neutral,
    Primary,
    Error
}

data class NgListBadge(
    val text: String,
    val tone: NgListBadgeTone = NgListBadgeTone.Neutral
)

fun <T> SnapshotStateList<T>.toggleNgExpandedKey(key: T) {
    if (contains(key)) {
        removeAll { it == key }
    } else {
        add(key)
    }
}

enum class NgExpandableListVariant {
    CARD,
    FORM_ROW,
}

@Composable
fun NgExpandableSectionHeader(
    title: String,
    selectionState: ToggleableState,
    selectedText: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleSelection: () -> Unit,
    modifier: Modifier = Modifier,
    statusColor: Color? = null,
    variant: NgExpandableListVariant = NgExpandableListVariant.CARD,
) {
    val isFormRow = variant == NgExpandableListVariant.FORM_ROW
    Surface(
        onClick = onToggleExpanded,
        modifier = modifier,
        shape = if (isFormRow) RectangleShape else RoundedCornerShape(10.dp),
        color = if (isFormRow) Color.Transparent else ngDrawerContentCardColor(),
        contentColor = Color(NgTheme.colors.onSurface),
        border = if (isFormRow) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
        ),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = if (isFormRow) 44.dp else 56.dp)
                .padding(start = 8.dp, end = if (isFormRow) 8.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isFormRow) {
                NgCircularSelectionIndicator(
                    state = selectionState,
                    modifier = Modifier
                        .size(36.dp)
                        .triStateToggleable(
                            state = selectionState,
                            role = Role.Checkbox,
                            onClick = onToggleSelection,
                        )
                        .semantics { contentDescription = title },
                )
            } else {
                TriStateCheckbox(
                    state = selectionState,
                    onClick = onToggleSelection,
                    modifier = Modifier.size(36.dp)
                )
            }
            if (statusColor != null) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Text(
                text = title,
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        start = if (statusColor == null) 6.dp else 8.dp,
                        end = 8.dp
                    ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isFormRow) FontWeight.Normal else FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (isFormRow) {
                Text(
                    text = selectedText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                NgListBadgeView(NgListBadge(selectedText))
            }
            IconButton(
                onClick = onToggleExpanded,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(if (expanded) 0f else -90f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun NgExpandableChildRow(
    title: String,
    summary: String,
    selected: Boolean,
    badges: List<NgListBadge>,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    variant: NgExpandableListVariant = NgExpandableListVariant.CARD,
) {
    val isFormRow = variant == NgExpandableListVariant.FORM_ROW
    val selection = if (isFormRow) {
        Modifier.toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
    } else {
        Modifier.clickable(onClick = onToggle)
    }
    Row(
        modifier = modifier
            .then(selection)
            .heightIn(min = 56.dp)
            .padding(start = 28.dp, top = 6.dp, end = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (isFormRow) {
            NgCircularSelectionIndicator(
                state = if (selected) ToggleableState.On else ToggleableState.Off,
                modifier = Modifier.size(36.dp),
            )
        } else {
            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(36.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            badges.forEach { badge ->
                NgListBadgeView(badge)
            }
        }
    }
}

@Composable
fun NgListBadgeView(
    badge: NgListBadge,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (badge.tone) {
        NgListBadgeTone.Neutral -> Color.White.copy(alpha = 0.44f)
        NgListBadgeTone.Primary -> colorScheme.primaryContainer.copy(alpha = 0.62f)
        NgListBadgeTone.Error -> colorScheme.errorContainer.copy(alpha = 0.62f)
    }
    val textColor = when (badge.tone) {
        NgListBadgeTone.Neutral -> colorScheme.onSurfaceVariant
        NgListBadgeTone.Primary -> colorScheme.primary
        NgListBadgeTone.Error -> colorScheme.onErrorContainer
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor
    ) {
        Text(
            text = badge.text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1
        )
    }
}
