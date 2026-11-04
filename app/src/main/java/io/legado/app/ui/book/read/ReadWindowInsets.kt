package io.legado.app.ui.book.read

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import androidx.core.view.WindowInsetsCompat
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat

/** The reader and its focused overlay must request the same system bar visibility. */
@Suppress("DEPRECATION")
internal fun applyReadSystemBarVisibility(
    window: Window,
    isInMultiWindow: Boolean,
    toolBarHide: Boolean,
    hideStatusBar: Boolean,
    hideNavigationBar: Boolean,
    hideStatusBarForOverlay: Boolean = false,
) {
    val decorView = window.decorView
    val fullscreen = hideStatusBarForOverlay || toolBarHide && hideStatusBar
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.insetsController?.run {
            if (hideNavigationBar) hide(WindowInsets.Type.navigationBars())
            else show(WindowInsets.Type.navigationBars())
            if (fullscreen) hide(WindowInsets.Type.statusBars())
            else show(WindowInsets.Type.statusBars())
        }
    }
    var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
        View.SYSTEM_UI_FLAG_IMMERSIVE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    if (!isInMultiWindow) flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
    if (hideNavigationBar) {
        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }
    if (fullscreen) flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN
    decorView.systemUiVisibility = flags
}

/** Temporary bar visibility changes must not resize the text viewport and restart speech. */
internal fun View.applyReadStatusBarPadding() {
    setOnApplyWindowInsetsListenerCompat { _, insets ->
        val top = insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
        setPadding(paddingLeft, top, paddingRight, paddingBottom)
        insets
    }
}
