package io.legado.app.ui.book.read.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

@Composable
internal fun ReadFormColorChoice(
    label: String,
    title: String,
    color: Int?,
    contentColor: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    valueLabel: String? = null,
    valueDescription: String? = null,
) {
    val choiceColor = contentColor.copy(alpha = if (enabled) 0.72f else 0.38f)
    val spokenValue = valueDescription ?: valueLabel ?: color?.let { "#%06X".format(it and 0x00FFFFFF) }
        ?: stringResource(R.string.text_default)
    Row(
        modifier = Modifier.heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$label $title"
                stateDescription = spokenValue
            }
            .padding(horizontal = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = choiceColor, fontSize = 12.sp, maxLines = 1)
        if (valueLabel != null) {
            Text(valueLabel, color = choiceColor, fontSize = 12.sp, maxLines = 1)
        } else {
            Box(
                Modifier.size(18.dp).clip(CircleShape)
                    .background(color?.let { Color(it) } ?: Color.Transparent)
                    .border(0.7.dp, choiceColor.copy(alpha = 0.25f), CircleShape),
            ) {
                if (color == null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawLine(
                            color = choiceColor.copy(alpha = 0.46f),
                            start = Offset(size.width * 0.24f, size.height * 0.76f),
                            end = Offset(size.width * 0.76f, size.height * 0.24f),
                            strokeWidth = 0.7.dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ReadFormSliderRow(
    title: String,
    valueText: String,
    labelWidth: Dp,
    valueWidth: Dp,
    contentColor: Color,
    slider: @Composable (Modifier) -> Unit,
) {
    val valueColor = contentColor.copy(alpha = 0.7f)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= labelWidth + valueWidth + 140.dp) {
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, Modifier.width(labelWidth), color = contentColor, fontSize = 14.sp,
                    fontFamily = NgTheme.fontFamily, maxLines = 1)
                slider(Modifier.weight(1f).padding(horizontal = 10.dp))
                Text(valueText, Modifier.width(valueWidth), color = valueColor, fontSize = 13.sp,
                    maxLines = 1, textAlign = TextAlign.End)
            }
        } else {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), color = contentColor, fontSize = 14.sp,
                        fontFamily = NgTheme.fontFamily)
                    Text(valueText, color = valueColor, fontSize = 13.sp)
                }
                slider(Modifier.fillMaxWidth())
            }
        }
    }
}
