package io.legado.app.ui.design.components.compose

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect

internal data class NgPopupWindowGeometry(
    val hostPositionInWindow: IntOffset,
    val visibleBoundsInWindow: IntRect,
) {
    fun anchorInWindow(boundsInRoot: IntRect): IntRect = boundsInRoot.translate(hostPositionInWindow)
}

/** Popup anchors are window-local, whereas the visible frame is reported in screen coordinates. */
internal fun popupVisibleFrameInWindow(
    screenFrame: IntRect,
    locationOnScreen: IntOffset,
    locationInWindow: IntOffset,
): IntRect {
    val originX = locationOnScreen.x - locationInWindow.x
    val originY = locationOnScreen.y - locationInWindow.y
    return IntRect(
        screenFrame.left - originX, screenFrame.top - originY,
        screenFrame.right - originX, screenFrame.bottom - originY,
    )
}

internal fun popupVisibleBoundsInWindow(view: View): IntRect? =
    readPopupWindowGeometry(view)?.visibleBoundsInWindow

@Composable
internal fun rememberNgPopupWindowGeometry(view: View): NgPopupWindowGeometry? {
    var geometry by remember(view) { mutableStateOf(readPopupWindowGeometry(view)) }
    DisposableEffect(view) {
        val observer = view.viewTreeObserver
        // A bottom sheet can offset its Compose host without relaying out the Compose anchor.
        val listener = ViewTreeObserver.OnPreDrawListener {
            geometry = readPopupWindowGeometry(view)
            true
        }
        observer.addOnPreDrawListener(listener)
        onDispose { if (observer.isAlive) observer.removeOnPreDrawListener(listener) }
    }
    return geometry
}

private fun readPopupWindowGeometry(view: View): NgPopupWindowGeometry? {
    if (!view.isAttachedToWindow) return null
    val frame = Rect()
    view.getWindowVisibleDisplayFrame(frame)
    if (frame.width() <= 0 || frame.height() <= 0) return null
    val onScreen = IntArray(2)
    val inWindow = IntArray(2)
    view.getLocationOnScreen(onScreen)
    view.getLocationInWindow(inWindow)
    val hostPositionInWindow = IntOffset(inWindow[0], inWindow[1])
    val visibleBoundsInWindow = popupVisibleFrameInWindow(
        screenFrame = IntRect(frame.left, frame.top, frame.right, frame.bottom),
        locationOnScreen = IntOffset(onScreen[0], onScreen[1]),
        locationInWindow = hostPositionInWindow,
    )
    return NgPopupWindowGeometry(hostPositionInWindow, visibleBoundsInWindow)
}
