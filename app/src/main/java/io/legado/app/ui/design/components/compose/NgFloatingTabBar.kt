package io.legado.app.ui.design.components.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

data class NgFloatingTabSpec(
    val text: String? = null,
    @param:DrawableRes val iconRes: Int? = null,
    val iconVector: ImageVector? = null,
    val count: Int? = null,
    val contentDescription: String? = text
)

enum class NgFloatingTabBarVariant {
    STANDARD,
    SOLID_LIGHT_CONTENT,
}

/** 与 View 版 NgFloatingTabBar 对齐的 48dp 等宽悬浮 Dock。 */
@Composable
fun NgFloatingTabBar(
    items: List<NgFloatingTabSpec>,
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    variant: NgFloatingTabBarVariant = NgFloatingTabBarVariant.STANDARD,
) {
    items.forEach { item ->
        require(item.text != null || !item.contentDescription.isNullOrBlank()) {
            "Icon-only NgFloatingTabSpec requires a contentDescription"
        }
    }
    val outerShape = RoundedCornerShape(12.dp)
    val tabs: @Composable RowScope.() -> Unit = {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex.coerceIn(items.indices)
            val contentColor = when {
                !selected -> Color(NgTheme.colors.onSurface)
                variant == NgFloatingTabBarVariant.SOLID_LIGHT_CONTENT -> Color.White
                else -> Color(NgTheme.colors.primary)
            }
            val selectedContainerColor = when (variant) {
                NgFloatingTabBarVariant.STANDARD -> Color(NgTheme.colors.selectedContainer)
                NgFloatingTabBarVariant.SOLID_LIGHT_CONTENT -> Color(NgTheme.colors.primary)
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) {
                            Modifier.background(selectedContainerColor)
                        } else {
                            Modifier
                        }
                    )
                    .selectable(
                        selected = selected,
                        role = Role.Tab,
                        onClick = { onTabSelected(index) }
                    )
                    .semantics(mergeDescendants = true) {
                        item.contentDescription?.let { contentDescription = it }
                    }
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                when {
                    item.iconVector != null -> Icon(
                        imageVector = item.iconVector,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )

                    item.iconRes != null -> Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = contentColor
                    )
                }
                val label = when {
                    item.count != null && !item.text.isNullOrEmpty() ->
                        "${item.text}\n${item.count}"
                    item.count != null -> item.count.toString()
                    else -> item.text.orEmpty()
                }
                if (label.isNotEmpty()) {
                    if (item.iconRes != null || item.iconVector != null) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(5.dp))
                    }
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = if (item.count != null && !item.text.isNullOrEmpty()) 2 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    when (variant) {
        NgFloatingTabBarVariant.STANDARD -> NgSettingsCardSurface(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp),
            cornerRadius = 12.dp,
            shape = outerShape,
            role = NgMaterialRole.CONTROL,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp)
                    .selectableGroup(),
                verticalAlignment = Alignment.CenterVertically,
                content = tabs,
            )
        }

        NgFloatingTabBarVariant.SOLID_LIGHT_CONTENT -> Row(
            modifier = modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(outerShape)
                .background(colorResource(R.color.ng_neutral_container))
                .border(
                    0.6.dp,
                    colorResource(R.color.ng_settings_item_stroke),
                    outerShape,
                )
                .padding(3.dp)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
            content = tabs,
        )
    }
}
