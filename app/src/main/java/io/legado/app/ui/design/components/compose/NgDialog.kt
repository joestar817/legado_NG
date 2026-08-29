package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import io.legado.app.R
import io.legado.app.ui.design.components.NgDialogVariant
import io.legado.app.ui.design.theme.NgTheme

/** Compose NG 居中弹窗内容外壳；窗口尺寸与遮罩仍由 applyNgDialogWindow 统一处理。 */
@Composable
fun NgDialog(
    title: String,
    modifier: Modifier = Modifier,
    variant: NgDialogVariant = NgDialogVariant.STANDARD,
    titleFontSize: TextUnit? = null,
    titleFontWeight: FontWeight = FontWeight.Bold,
    actions: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val metrics = dialogMetrics(variant)
    val cornerRadius = when (variant) {
        NgDialogVariant.COMPACT_CONFIRMATION -> NgTheme.shapes.largeDp
        NgDialogVariant.CLASSIC_CONFIRMATION -> NgTheme.shapes.mediumDp
        NgDialogVariant.FORM_EDITOR -> NgTheme.shapes.dialogDp
        else -> NgTheme.shapes.extraLargeDp
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(cornerRadius.dp),
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
    ) {
        Column(
            modifier = Modifier.padding(
                start = metrics.horizontalPadding,
                top = metrics.topPadding,
                end = metrics.horizontalPadding,
                bottom = metrics.bottomPadding,
            ),
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                color = Color(NgTheme.colors.onSurface),
                fontSize = titleFontSize ?: metrics.titleSize,
                lineHeight = metrics.titleLineHeight,
                fontWeight = titleFontWeight,
                textAlign = metrics.titleAlignment,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(metrics.titleSpacing))
            content()
            Spacer(Modifier.height(metrics.actionSpacing))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
                content = actions,
            )
        }
    }
}

/** 提示／确认弹窗使用的纯文字操作，保留触控面积但不绘制大按钮容器。 */
@Composable
fun NgDialogTextActionButton(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    enabled: Boolean = true,
) {
    val contentColor = Color(
        if (danger) NgTheme.colors.error else NgTheme.colors.primary
    ).let { if (enabled) it else it.copy(alpha = 0.45f) }
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 16.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.Normal,
            maxLines = 1,
        )
    }
}

/**
 * 顶栏承载保存操作的紧凑输入弹窗。
 *
 * 用于变量等少字段编辑，不套用宽松表单和底部操作栏。
 */
@Composable
fun NgCompactEditorDialog(
    title: String,
    modifier: Modifier = Modifier,
    titleAction: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colorResource(R.color.ng_surface_card),
        shape = RoundedCornerShape(NgTheme.shapes.extraLargeDp.dp),
        shadowElevation = NgTheme.effects.overlayElevationDp.dp,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    color = Color(NgTheme.colors.onSurface),
                    fontSize = 18.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                titleAction()
            }
            Column(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                content = content,
            )
        }
    }
}

/** 弹窗分区内的紧凑标题／当前值操作行。 */
@Composable
fun NgDialogValueRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.TextButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                color = Color(NgTheme.colors.onSurface),
                fontSize = 17.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Start,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                modifier = Modifier.padding(start = 12.dp),
                color = Color(NgTheme.colors.onSurfaceVariant),
                fontSize = 15.sp,
                lineHeight = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun NgDialogDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = 2.dp),
        thickness = 0.6.dp,
        color = Color(NgTheme.colors.outlineVariant).copy(alpha = 0.26f),
    )
}

private data class NgDialogMetrics(
    val horizontalPadding: androidx.compose.ui.unit.Dp,
    val topPadding: androidx.compose.ui.unit.Dp,
    val bottomPadding: androidx.compose.ui.unit.Dp,
    val titleSize: androidx.compose.ui.unit.TextUnit,
    val titleLineHeight: androidx.compose.ui.unit.TextUnit,
    val titleSpacing: androidx.compose.ui.unit.Dp,
    val actionSpacing: androidx.compose.ui.unit.Dp,
    val titleAlignment: TextAlign,
)

private fun dialogMetrics(variant: NgDialogVariant): NgDialogMetrics = when (variant) {
    NgDialogVariant.STANDARD -> NgDialogMetrics(
        horizontalPadding = 18.dp,
        topPadding = 16.dp,
        bottomPadding = 14.dp,
        titleSize = 20.sp,
        titleLineHeight = 24.sp,
        titleSpacing = 14.dp,
        actionSpacing = 14.dp,
        titleAlignment = TextAlign.Start,
    )

    NgDialogVariant.CONFIRMATION -> NgDialogMetrics(
        horizontalPadding = 18.dp,
        topPadding = 18.dp,
        bottomPadding = 16.dp,
        titleSize = 20.sp,
        titleLineHeight = 24.sp,
        titleSpacing = 14.dp,
        actionSpacing = 16.dp,
        titleAlignment = TextAlign.Center,
    )

    NgDialogVariant.COMPACT_CONFIRMATION -> NgDialogMetrics(
        horizontalPadding = 18.dp,
        topPadding = 16.dp,
        bottomPadding = 14.dp,
        titleSize = 18.sp,
        titleLineHeight = 22.sp,
        titleSpacing = 12.dp,
        actionSpacing = 14.dp,
        titleAlignment = TextAlign.Center,
    )

    NgDialogVariant.CLASSIC_CONFIRMATION -> NgDialogMetrics(
        horizontalPadding = 24.dp,
        topPadding = 18.dp,
        bottomPadding = 10.dp,
        titleSize = 20.sp,
        titleLineHeight = 26.sp,
        titleSpacing = 12.dp,
        actionSpacing = 6.dp,
        titleAlignment = TextAlign.Start,
    )

    NgDialogVariant.EDITOR -> NgDialogMetrics(
        horizontalPadding = 16.dp,
        topPadding = 18.dp,
        bottomPadding = 14.dp,
        titleSize = 20.sp,
        titleLineHeight = 26.sp,
        titleSpacing = 24.dp,
        actionSpacing = 18.dp,
        titleAlignment = TextAlign.Start,
    )

    NgDialogVariant.FORM_EDITOR -> NgDialogMetrics(
        horizontalPadding = 16.dp,
        topPadding = 16.dp,
        bottomPadding = 16.dp,
        titleSize = 24.sp,
        titleLineHeight = 30.sp,
        titleSpacing = 16.dp,
        actionSpacing = 16.dp,
        titleAlignment = TextAlign.Start,
    )

    NgDialogVariant.LONG_CONTENT -> NgDialogMetrics(
        horizontalPadding = 20.dp,
        topPadding = 18.dp,
        bottomPadding = 16.dp,
        titleSize = 20.sp,
        titleLineHeight = 24.sp,
        titleSpacing = 14.dp,
        actionSpacing = 16.dp,
        titleAlignment = TextAlign.Start,
    )
}
