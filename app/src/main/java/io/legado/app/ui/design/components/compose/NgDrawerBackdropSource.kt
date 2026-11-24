package io.legado.app.ui.design.components.compose

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.R
import io.legado.app.ui.design.components.view.NgBackdropSourceLayout

internal fun View.findNgDrawerBackdropSource(): View? =
    findViewById<View>(R.id.ng_drawer_backdrop_source)?.takeIf { it.isAttachedToWindow }

/** Exposes the page's existing background for drawers in a separate window. */
@Composable
internal fun NgDrawerBackdropSource(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val parentComposition = rememberCompositionContext()
    val currentContent = rememberUpdatedState(content)
    AndroidView(
        factory = { context ->
            NgBackdropSourceLayout(context).apply {
                id = R.id.ng_drawer_backdrop_source
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                val background = ComposeView(context).apply {
                    consumeWindowInsets = false
                    setParentCompositionContext(parentComposition)
                    setContent { currentContent.value() }
                }
                addView(
                    background,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
            }
        },
        modifier = modifier,
    )
}
