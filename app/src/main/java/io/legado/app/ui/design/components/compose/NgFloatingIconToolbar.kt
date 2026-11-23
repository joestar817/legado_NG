package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.legado.app.ui.design.theme.NgTheme

@Immutable
internal data class NgFloatingIconToolbarAction(
    @param:DrawableRes val iconRes: Int,
    val label: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val emphasized: Boolean = false,
)

@Composable
internal fun NgFloatingIconToolbar(
    actions: List<NgFloatingIconToolbarAction>,
    modifier: Modifier = Modifier,
    liquidBackdrop: NgLiquidGlassBackdrop? = currentNgLiquidGlassBackdrop(),
) {
    val theme = NgTheme.snapshot
    val primary = Color(theme.colors.primary)
    val onSurface = Color(theme.colors.onSurface)
    val neutral = if (theme.isDark) Color(0xFF242321) else Color.White
    NgVisualSurface(
        modifier = modifier.height(48.dp),
        role = NgMaterialRole.CONTROL,
        cornerRadius = 24.dp,
        style = NgGlassDefaults.bookDetailStyle(containerColor = neutral).copy(
            containerTop = neutral.copy(alpha = 0.94f),
            containerBottom = neutral.copy(alpha = 0.88f),
            accentGlow = Color.Transparent,
            borderColor = Color.White.copy(alpha = if (theme.isDark) 0.22f else 0.92f),
            shadowElevation = 3.dp,
        ),
        liquidBackdrop = liquidBackdrop,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            actions.forEachIndexed { index, action ->
                if (index > 0) {
                    Spacer(
                        Modifier.width(1.dp).height(18.dp).background(primary.copy(alpha = 0.18f))
                    )
                }
                NgIconButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    val color = if (action.emphasized) primary else onSurface
                    Icon(
                        painter = painterResource(action.iconRes),
                        contentDescription = action.label,
                        modifier = Modifier.size(24.dp),
                        tint = color.copy(alpha = if (action.enabled) 1f else 0.4f),
                    )
                }
            }
        }
    }
}
