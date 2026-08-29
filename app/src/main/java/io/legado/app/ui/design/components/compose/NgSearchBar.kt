package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.ui.design.theme.NgTheme

enum class NgSearchBarVariant {
    STANDARD,
    TOOLBAR
}

/**
 * 悬浮搜索顶栏的返回按钮。
 *
 * 返回箭头属于悬浮控件自身，不读取可自定义的普通顶栏文字色，避免液态／透明
 * 承载面上因顶栏配色变浅而失去对比度。
 */
@Composable
fun NgFloatingToolbarBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 34.dp,
) {
    Box(
        modifier = modifier
            .width(width)
            .height(36.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_chevron_left_search),
            contentDescription = stringResource(R.string.back),
            tint = colorResource(R.color.ng_search_icon),
            modifier = Modifier.size(24.dp),
        )
    }
}

/** 与 Toolbar 搜索框并排使用的 36dp 图标动作；两者共享同一视觉材质语义。 */
@Composable
fun NgSearchBarActionButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    icon: Painter = painterResource(R.drawable.ic_grid_menu),
) {
    val cornerRadius = 10.dp
    val shape = RoundedCornerShape(cornerRadius)
    val containerColor = colorResource(R.color.ng_search_surface)
    val contentColor = colorResource(R.color.ng_search_icon)
    val surfaceStyle = rememberNgSearchSurfaceStyle(
        containerColor = containerColor,
        strokeColor = Color.Transparent,
        contentColor = contentColor,
        isToolbar = true,
    )
    NgVisualSurface(
        modifier = modifier
            .size(36.dp)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick),
        role = NgMaterialRole.TOP_NAVIGATION,
        cornerRadius = cornerRadius,
        shape = shape,
        style = surfaceStyle,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
    }
}

/** 统一 NG 搜索框；列表页使用 44dp 标准规格，顶栏使用 36dp Toolbar 规格。 */
@Composable
fun NgSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    searchIcon: Painter = painterResource(R.drawable.ic_search),
    variant: NgSearchBarVariant = NgSearchBarVariant.STANDARD,
    containerColor: Color? = null,
    hideHintOnFocus: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    onSearch: (String) -> Unit = {}
) {
    val isToolbar = variant == NgSearchBarVariant.TOOLBAR
    val fieldHeight = if (isToolbar) 36.dp else 44.dp
    val shape = RoundedCornerShape(if (isToolbar) 18.dp else 22.dp)
    val contentColor = colorResource(R.color.ng_search_content)
    val iconColor = colorResource(R.color.ng_search_icon)
    val secondaryColor = colorResource(R.color.ng_search_hint)
    val resolvedContainerColor = containerColor ?: colorResource(R.color.ng_search_surface)
    val strokeColor = if (isToolbar) {
        Color.Transparent
    } else {
        colorResource(R.color.ng_card_stroke)
    }
    val surfaceStyle = rememberNgSearchSurfaceStyle(
        containerColor = resolvedContainerColor,
        strokeColor = strokeColor,
        contentColor = contentColor,
        isToolbar = isToolbar,
    )
    var focused by remember { mutableStateOf(false) }
    NgVisualSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(fieldHeight),
        role = if (isToolbar) {
            NgMaterialRole.TOP_NAVIGATION
        } else {
            NgMaterialRole.CONTROL
        },
        cornerRadius = if (isToolbar) 18.dp else 22.dp,
        shape = shape,
        style = surfaceStyle,
        visualSystemOverride = if (resolvedContainerColor.alpha == 0f) {
            NgVisualSystem.TRANSPARENT_GLASS
        } else {
            null
        },
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { state ->
                    focused = state.isFocused
                    onFocusChanged(state.isFocused)
                },
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                color = contentColor,
                fontSize = 15.sp,
                lineHeight = 18.sp
            ),
            cursorBrush = SolidColor(Color(NgTheme.colors.primary)),
            keyboardOptions = KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            ),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            decorationBox = { innerTextField ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = if (isToolbar) 14.dp else 16.dp,
                            end = if (isToolbar) 6.dp else 8.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = searchIcon,
                        contentDescription = stringResource(R.string.search),
                        modifier = Modifier.size(if (isToolbar) 20.dp else 22.dp),
                        tint = iconColor
                    )
                    Spacer(Modifier.width(if (isToolbar) 8.dp else 10.dp))
                    androidx.compose.foundation.layout.Box(Modifier.weight(1f)) {
                        if (query.isEmpty() && (!hideHintOnFocus || !focused)) {
                            Text(
                                text = hint,
                                color = secondaryColor,
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        innerTextField()
                    }
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { onQueryChange("") },
                            modifier = Modifier.size(if (isToolbar) 30.dp else 38.dp),
                            enabled = enabled
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_baseline_close),
                                contentDescription = stringResource(R.string.clear),
                                modifier = Modifier.size(if (isToolbar) 18.dp else 20.dp),
                                tint = secondaryColor
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun rememberNgSearchSurfaceStyle(
    containerColor: Color,
    strokeColor: Color,
    contentColor: Color,
    isToolbar: Boolean,
): NgGlassStyle {
    val snapshot = NgTheme.snapshot
    return remember(
        containerColor,
        strokeColor,
        contentColor,
        snapshot.isEInk,
        isToolbar,
    ) {
        NgGlassStyle(
            containerTop = containerColor,
            containerBottom = containerColor,
            accentGlow = Color.Transparent,
            borderColor = strokeColor,
            edgeHighlight = if (snapshot.isEInk) {
                Color.Transparent
            } else {
                Color.White.copy(alpha = 0.60f)
            },
            surfaceGloss = Color.Transparent,
            depthEdge = Color.Transparent,
            contentColor = contentColor,
            blurRadius = 0.dp,
            shadowElevation = 0.dp,
            borderWidth = if (isToolbar) 0.dp else 0.8.dp,
            highlightWidth = 0.dp,
        )
    }
}
