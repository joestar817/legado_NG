package io.legado.app.ui.widget.dialog

import android.content.Context
import android.graphics.Color
import android.view.View
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.ui.design.components.compose.NgBottomDrawerSurface
import io.legado.app.ui.design.components.compose.NgDrawerContentCardStyle
import io.legado.app.ui.design.theme.NgAppTheme

/** 将仍需保留 View 内容的旧抽屉接入 NG 公共承载面。 */
internal fun Context.createNgBottomDrawerViewHost(
    contentView: View,
    fillMaxHeight: Boolean,
    contentCardStyle: NgDrawerContentCardStyle = NgDrawerContentCardStyle.LEGACY,
): ComposeView = createNgBottomDrawerComposeHost(fillMaxHeight, contentCardStyle) {
    AndroidView(
        factory = { contentView },
        modifier = bottomDrawerHostModifier(fillMaxHeight),
    )
}

/** 让 View 页面直接提供 Compose 内容，同时继续复用 NG 公共抽屉外壳。 */
internal fun Context.createNgBottomDrawerComposeHost(
    fillMaxHeight: Boolean,
    contentCardStyle: NgDrawerContentCardStyle = NgDrawerContentCardStyle.LEGACY,
    content: @Composable ColumnScope.() -> Unit,
): ComposeView = ComposeView(this).apply {
    setBackgroundColor(Color.TRANSPARENT)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    setContent {
        NgAppTheme(updateSystemBars = false) {
            val hostModifier = bottomDrawerHostModifier(fillMaxHeight)
            NgBottomDrawerSurface(
                modifier = hostModifier,
                contentCardStyle = contentCardStyle,
            ) {
                content()
            }
        }
    }
}

private fun bottomDrawerHostModifier(fillMaxHeight: Boolean): Modifier {
    return if (fillMaxHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
}
