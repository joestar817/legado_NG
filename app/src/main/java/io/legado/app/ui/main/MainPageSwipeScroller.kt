package io.legado.app.ui.main

import android.util.Log
import android.view.animation.DecelerateInterpolator
import android.widget.Scroller
import androidx.viewpager.widget.ViewPager
import kotlin.math.abs

internal val pageSwipeSettleInterpolator = DecelerateInterpolator()

internal fun ViewPager.installMainPageSwipeScroller() {
    val pager = this
    // Leave more travel for the second half of the settle.
    val scroller = object : Scroller(context, DecelerateInterpolator(0.85f)) {
        override fun startScroll(startX: Int, startY: Int, dx: Int, dy: Int, duration: Int) {
            val pageWidth = pager.width - pager.paddingLeft - pager.paddingRight
            val settleDuration = if (pageWidth > 0 && dx != 0) {
                pageSwipeSettleDuration(abs(dx.toFloat()) / pageWidth, duration)
            } else {
                duration
            }
            super.startScroll(startX, startY, dx, dy, settleDuration)
        }
    }
    // ViewPager has no public timing hook. Keep this field in R8 and fall back if it changes.
    try {
        ViewPager::class.java.getDeclaredField("mScroller").apply { isAccessible = true }
            .set(pager, scroller)
    } catch (exception: Exception) {
        Log.w("MainPageSwipe", "Using the default ViewPager scroller", exception)
    }
}
