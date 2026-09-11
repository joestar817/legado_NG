package io.legado.app.ui.main.bookshelf

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgGlassDefaults
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.NgVisualSurface
import io.legado.app.ui.design.theme.NgTheme

internal fun bookshelfContainerBottomInset(
    viewportTop: Int,
    viewportHeight: Int,
    barTop: Int?,
    gap: Int,
): Int = if (barTop == null) {
    gap.coerceAtMost(viewportHeight.coerceAtLeast(0))
} else {
    (viewportTop + viewportHeight - barTop + gap).coerceIn(0, viewportHeight.coerceAtLeast(0))
}

@Composable
internal fun BookshelfGridBackground(
    enabled: Boolean,
    initialBottomInset: Dp,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val gapPx = with(density) { 8.dp.roundToPx() }
    var bottomInsetPx by remember(view, enabled, density) {
        mutableIntStateOf(with(density) { initialBottomInset.roundToPx() })
    }
    DisposableEffect(view, enabled, gapPx) {
        if (!enabled) return@DisposableEffect onDispose { }
        val root = view.rootView
        val floatingBar = root.findViewById<View>(R.id.floating_bottom_navigation)
        val fixedBar = root.findViewById<View>(R.id.bottom_navigation_view)
        val viewportLocation = IntArray(2)
        val barLocation = IntArray(2)
        val observer = view.viewTreeObserver
        val listener = ViewTreeObserver.OnPreDrawListener {
            view.getLocationOnScreen(viewportLocation)
            val bar = floatingBar?.takeIf { it.isShown }
                ?: fixedBar?.takeIf { it.isShown }
            bar?.getLocationOnScreen(barLocation)
            bottomInsetPx = bookshelfContainerBottomInset(
                viewportTop = viewportLocation[1],
                viewportHeight = view.height,
                barTop = bar?.let { barLocation[1] },
                gap = gapPx,
            )
            true
        }
        observer.addOnPreDrawListener(listener)
        onDispose {
            if (observer.isAlive) observer.removeOnPreDrawListener(listener)
        }
    }
    val shape = RoundedCornerShape(NgTheme.shapes.mediumDp.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (enabled) Modifier
                    .padding(
                        start = 16.dp, end = 16.dp, top = 8.dp,
                        bottom = with(density) { bottomInsetPx.toDp() },
                    )
                    .clip(shape)
                else Modifier,
            ),
    ) {
        if (enabled) {
            NgVisualSurface(
                modifier = Modifier.matchParentSize(),
                role = NgMaterialRole.CONTENT,
                cornerRadius = NgTheme.shapes.mediumDp.dp,
                style = NgGlassDefaults.flatNeutralStyle().copy(shadowElevation = 0.dp),
            ) { }
        }
        content()
    }
}
