package io.legado.app.ui.design.components.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.unit.dp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgDrawerAppearanceConfig
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgDrawerPalette
import io.legado.app.ui.design.theme.NgTheme

enum class NgDrawerDragHandleVariant {
    STANDARD,
    COMPACT,
}

/** 当前全局 NG 抽屉的外观快照。 */
@Immutable
data class NgDrawerAppearance(
    val transparencyPercent: Int,
    val primaryStrengthPercent: Int,
    val horizontalMarginDp: Int,
    val cornerRadiusDp: Int,
)

object NgDrawerDefaults {

    fun currentAppearance(): NgDrawerAppearance = NgDrawerAppearance(
        transparencyPercent = AppConfig.ngDrawerTransparency,
        primaryStrengthPercent = AppConfig.ngDrawerPrimaryStrength,
        horizontalMarginDp = AppConfig.ngDrawerHorizontalMarginDp,
        cornerRadiusDp = AppConfig.ngDrawerCornerRadiusDp,
    )

    @Composable
    fun style(appearance: NgDrawerAppearance): NgGlassStyle =
        NgGlassDefaults.drawerStyle(
            transparencyPercent = appearance.transparencyPercent,
            primaryStrengthPercent = appearance.primaryStrengthPercent,
        )
}

/**
 * 全局 NG 底部抽屉的公共承载面。
 *
 * 它只消费当前主题语义色，不嵌入主题背景图。边距为 0 时与屏幕等宽；边距大于 0
 * 时抽屉成为独立圆角承载面。业务页面只负责标题、筛选区和内容结构。
 */
@Composable
fun NgBottomDrawerSurface(
    modifier: Modifier = Modifier,
    appearance: NgDrawerAppearance = NgDrawerDefaults.currentAppearance(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseSnapshot = NgTheme.snapshot
    val normalized = remember(appearance) { appearance.normalized() }
    val radius = normalized.cornerRadiusDp.dp
    val shape = if (normalized.horizontalMarginDp == 0) {
        RoundedCornerShape(topStart = radius, topEnd = radius)
    } else {
        RoundedCornerShape(radius)
    }
    val semanticSnapshot = remember(baseSnapshot, normalized.primaryStrengthPercent) {
        NgDrawerPalette.applySemanticRoles(
            snapshot = baseSnapshot,
            primaryStrengthPercent = normalized.primaryStrengthPercent,
        )
    }
    NgAppTheme(
        snapshot = semanticSnapshot,
        updateSystemBars = false,
    ) {
        val nestedScrollInteropConnection = rememberNestedScrollInteropConnection()
        NgGlassSurface(
            modifier = modifier
                .padding(horizontal = normalized.horizontalMarginDp.dp)
                .nestedScroll(nestedScrollInteropConnection),
            shape = shape,
            style = NgDrawerDefaults.style(normalized),
            contentPadding = contentPadding,
            content = content,
        )
    }
}

/**
 * 全局 NG 侧边抽屉承载面。
 *
 * 颜色、透明度与主色浓度复用底部抽屉设置，并作为侧栏唯一背景层；几何由侧边
 * 业务结构显式提供，不消费全局边距或圆角参数，也不允许额外叠加背景 backdrop。
 */
@Composable
fun NgSideDrawerSurface(
    modifier: Modifier = Modifier,
    appearance: NgDrawerAppearance = NgDrawerDefaults.currentAppearance(),
    shape: Shape = RectangleShape,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val baseSnapshot = NgTheme.snapshot
    val configuration = LocalConfiguration.current
    val normalized = remember(appearance) { appearance.normalized() }
    val materialViewport = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        NgGlassMaterialViewport(
            width = configuration.screenWidthDp.dp,
            height = (configuration.screenHeightDp * 0.68f).dp,
        )
    }
    val semanticSnapshot = remember(baseSnapshot, normalized.primaryStrengthPercent) {
        NgDrawerPalette.applySemanticRoles(
            snapshot = baseSnapshot,
            primaryStrengthPercent = normalized.primaryStrengthPercent,
        )
    }
    NgAppTheme(
        snapshot = semanticSnapshot,
        updateSystemBars = false,
    ) {
        NgGlassSurface(
            modifier = modifier,
            shape = shape,
            style = NgDrawerDefaults.style(normalized),
            materialViewport = materialViewport,
            contentPadding = contentPadding,
            content = content,
        )
    }
}

private fun NgDrawerAppearance.normalized(): NgDrawerAppearance = copy(
    transparencyPercent = NgDrawerAppearanceConfig.normalizePercent(transparencyPercent),
    primaryStrengthPercent = NgDrawerAppearanceConfig.normalizePercent(primaryStrengthPercent),
    horizontalMarginDp = NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(horizontalMarginDp),
    cornerRadiusDp = NgDrawerAppearanceConfig.normalizeCornerRadiusDp(cornerRadiusDp),
)

/**
 * NG 抽屉顶部的原生拖动抓手。
 *
 * 组件只负责视觉；[NgBottomDrawerSurface] 通过 Compose / View 嵌套滚动互操作，
 * 让宿主 BottomSheetBehavior 只在内部滚动内容到顶后接管下拉手势。
 */
@Composable
fun NgDrawerDragHandle(
    modifier: Modifier = Modifier,
    variant: NgDrawerDragHandleVariant = NgDrawerDragHandleVariant.STANDARD,
) {
    val height = when (variant) {
        NgDrawerDragHandleVariant.STANDARD -> 18.dp
        NgDrawerDragHandleVariant.COMPACT -> 12.dp
    }
    val width = when (variant) {
        NgDrawerDragHandleVariant.STANDARD -> 40.dp
        NgDrawerDragHandleVariant.COMPACT -> 36.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .width(width)
                .height(4.dp)
                .background(
                    Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.52f),
                    CircleShape,
                ),
        )
    }
}
