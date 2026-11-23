package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgSolidButtonPalette
import io.legado.app.ui.design.theme.NgTheme

/** 圆形选择标记。交互与无障碍语义由调用方提供。 */
@Composable
internal fun NgCircularSelectionIndicator(
    state: ToggleableState,
    modifier: Modifier,
) {
    val theme = NgTheme.snapshot
    val primary = theme.colors.primary
    val checkedColor = remember(primary, theme.isDark) {
        Color(NgSolidButtonPalette.resolveContainer(primary, Color.White.toArgb(), theme.isDark))
    }
    val indicator = if (state == ToggleableState.Off) {
        Modifier.border(1.dp, Color(theme.colors.onSurfaceVariant).copy(alpha = 0.62f), CircleShape)
    } else {
        Modifier.background(checkedColor, CircleShape)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(20.dp).then(indicator),
            contentAlignment = Alignment.Center,
        ) {
            when (state) {
                ToggleableState.On -> Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = Color.White,
                )
                ToggleableState.Indeterminate -> Box(
                    Modifier
                        .size(width = 9.dp, height = 1.5.dp)
                        .background(Color.White, CircleShape)
                )
                ToggleableState.Off -> Unit
            }
        }
    }
}
