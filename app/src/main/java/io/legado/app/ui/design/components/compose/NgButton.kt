package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    shapeVariant: NgButtonShapeVariant = NgButtonShapeVariant.PILL,
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
                contentColor = Color(colors.onPrimary)
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
                contentColor = Color(colors.onError)
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
