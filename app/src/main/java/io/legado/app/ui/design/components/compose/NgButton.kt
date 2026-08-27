package io.legado.app.ui.design.components.compose

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.components.NgButtonShapeVariant
import io.legado.app.ui.design.components.NgButtonVariant
import io.legado.app.ui.design.theme.NgTheme

@Composable
fun NgButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    variant: NgButtonVariant = NgButtonVariant.PRIMARY,
    shapeVariant: NgButtonShapeVariant = NgButtonShapeVariant.ROUNDED,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = NgTheme.colors
    val shape = when (shapeVariant) {
        NgButtonShapeVariant.PILL -> ButtonDefaults.shape
        NgButtonShapeVariant.ROUNDED -> RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
        NgButtonShapeVariant.SMALL_ROUNDED -> RoundedCornerShape(NgTheme.shapes.smallDp.dp)
    }
    when (variant) {
        NgButtonVariant.PRIMARY -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(colors.primary),
                contentColor = Color.White
            ),
            content = content
        )

        NgButtonVariant.PRIMARY_LIGHT_CONTENT -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(colors.primary),
                contentColor = Color.White
            ),
            content = content
        )

        NgButtonVariant.TONAL -> FilledTonalButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color(colors.selectedContainer),
                contentColor = Color(colors.onSurface)
            ),
            content = content
        )

        NgButtonVariant.NEUTRAL -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(colors.surfaceContainerHigh).copy(
                    alpha = if (NgTheme.snapshot.isEInk) 1f else 0.38f
                ),
                contentColor = Color(colors.onSurface)
            ),
            content = content
        )

        NgButtonVariant.OUTLINE -> OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = Color(colors.primary)
            ),
            content = content
        )

        NgButtonVariant.DANGER -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(colors.error),
                contentColor = Color.White
            ),
            content = content
        )

        NgButtonVariant.ON_IMAGE -> Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Black.copy(alpha = 0.56f),
                contentColor = Color.White
            ),
            content = content
        )
    }
}

@Composable
fun NgIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        content = content
    )
}

/**
 * 透明体系保持普通图标按钮；存在页面级 backdrop 时，液态体系自动使用交互玻璃面。
 */
@Composable
fun NgVisualIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    touchSize: Dp = 48.dp,
    surfaceSize: Dp = 38.dp,
    role: NgMaterialRole = NgMaterialRole.ICON_ACTION,
    content: @Composable () -> Unit,
) {
    val usesLiquidSurface = NgTheme.usesLiquidGlass &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        hasCurrentNgLiquidGlassBackdrop()
    if (!usesLiquidSurface) {
        NgIconButton(
            onClick = onClick,
            modifier = modifier.size(touchSize),
            enabled = enabled,
            content = content,
        )
        return
    }

    val shape = CircleShape
    val style = NgGlassDefaults.style(containerAlpha = 0.52f)
    Box(
        modifier = modifier
            .size(touchSize)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        NgVisualSurface(
            modifier = Modifier
                .size(surfaceSize)
                .alpha(if (enabled) 1f else 0.45f),
            role = role,
            cornerRadius = surfaceSize / 2,
            shape = shape,
            style = style,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}
