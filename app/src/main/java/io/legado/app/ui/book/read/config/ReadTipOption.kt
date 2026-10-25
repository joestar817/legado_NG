package io.legado.app.ui.book.read.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun ReadTipOption(
    name: String,
    icon: Int,
    selected: Boolean,
    usage: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ReadTipOptionVariant = ReadTipOptionVariant.STANDARD,
) {
    if (variant == ReadTipOptionVariant.HEADER_CHOICE) {
        ReadHeaderTipChoice(name, selected, usage, onClick, modifier)
        return
    }
    Row(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color(NgTheme.colors.onSurface),
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 6.dp),
        ) {
            Text(
                text = name,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (usage != null) {
                Text(
                    text = usage,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ng_ic_popup_selected),
                contentDescription = null,
                tint = Color(NgTheme.colors.primary),
                modifier = Modifier.size(18.dp),
            )
        } else {
            Spacer(Modifier.size(18.dp))
        }
    }
}

internal enum class ReadTipOptionVariant { STANDARD, HEADER_CHOICE }

@Composable
private fun ReadHeaderTipChoice(
    name: String,
    selected: Boolean,
    usage: String?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val colors = NgTheme.colors
    val primary = Color(colors.primary)
    val selectedContent = Color(colors.onPrimary)
    Row(
        modifier = modifier
            .heightIn(min = if (usage == null) 40.dp else 44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) primary else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(16.dp)
                .then(if (selected) Modifier.background(selectedContent, CircleShape)
                    else Modifier.border(1.3.dp, Color(colors.onSurface), CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(
                text = name,
                color = if (selected) selectedContent else Color(colors.onSurface),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
            if (usage != null) {
                Text(
                    text = usage,
                    color = if (selected) selectedContent else Color(colors.onSurfaceVariant),
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}
