package io.legado.app.ui.about

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

@Composable
internal fun LegacyLogDialogLayout(
    title: String,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = legacyLogTextStyle(
                    color = colorResource(R.color.ng_on_surface),
                    fontSize = 20.sp,
                ),
                maxLines = 1,
            )
            actions()
        }
        val sectionShape = RoundedCornerShape(dimensionResource(R.dimen.ng_radius_l))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(
                    start = dimensionResource(R.dimen.ng_dialog_padding),
                    end = dimensionResource(R.dimen.ng_dialog_padding),
                    bottom = dimensionResource(R.dimen.ng_dialog_padding),
                )
                .background(colorResource(R.color.ng_surface_panel), sectionShape),
            content = content,
        )
    }
}

@Composable
internal fun LegacyLogToolbarAction(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = legacyLogTextStyle(color = color, fontSize = 14.sp),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
internal fun LegacyLogToolbarIconAction(
    iconRes: Int,
    contentDescription: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(24.dp),
            tint = color,
        )
    }
}

internal fun legacyLogTextStyle(
    color: Color,
    fontSize: TextUnit,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign = TextAlign.Unspecified,
): TextStyle = TextStyle(
    color = color,
    fontSize = fontSize,
    fontFamily = fontFamily,
    fontWeight = FontWeight.Normal,
    textAlign = textAlign,
    lineHeight = TextUnit.Unspecified,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

@Composable
internal fun LegacyRotateLoading(
    visible: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    var topDegree by remember { mutableIntStateOf(10) }
    var bottomDegree by remember { mutableIntStateOf(190) }
    var arc by remember { mutableFloatStateOf(10f) }
    var changeBigger by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            topDegree = (topDegree + 10) % 360
            bottomDegree = (bottomDegree + 10) % 360
            if (changeBigger) {
                if (arc < 160f) arc += 2.5f
            } else if (arc > 10f) {
                arc -= 5f
            }
            if (arc >= 160f || arc <= 10f) changeBigger = !changeBigger
        }
    }
    Canvas(modifier = modifier.size(36.dp)) {
        val strokeWidth = 2.dp.toPx()
        val inset = strokeWidth * 2f
        val shadowOffset = 2.dp.toPx()
        val arcSize = Size(
            width = size.width - inset * 2f,
            height = size.height - inset * 2f,
        )
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        listOf(topDegree.toFloat(), bottomDegree.toFloat()).forEach { start ->
            drawArc(
                color = Color(0x1A000000),
                startAngle = start,
                sweepAngle = arc,
                useCenter = false,
                topLeft = Offset(inset + shadowOffset, inset + shadowOffset),
                size = arcSize,
                style = stroke,
            )
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = arc,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = stroke,
            )
        }
    }
}
