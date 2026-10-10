package io.legado.app.ui.book.read.epub

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.Scroller
import kotlin.math.abs

/** Owns each pointer sequence completely; Chromium never receives an unmatched DOWN. */
internal class EpubReaderGestures(
    private val slop: Int,
    private val tap: (Float, Float) -> Unit,
    private val longPress: (Float, Float) -> Unit,
    private val extend: (Float, Float) -> Unit,
    private val finishSelection: () -> Unit,
    private val swipe: (Int) -> Unit,
    private val cancelSelection: () -> Unit,
    private val scroll: (Float, Float) -> Boolean = { _, _ -> false },
    private val finishScroll: () -> Unit = {},
    private val canSelect: () -> Boolean = { true },
    private val horizontalScroll: () -> Boolean = { false },
    private val canFling: () -> Boolean = { true },
    private val cancelScroll: () -> Unit = {},
    private val onFlingStart: () -> Unit = {},
    private val onFlingStop: () -> Unit = {},
) : View.OnTouchListener {
    private val handler = Handler(Looper.getMainLooper())
    private var down = false
    private var moved = false
    private var selecting = false
    private var longPressed = false
    private var x = 0f
    private var y = 0f
    private var lastY = 0f
    private var lastX = 0f
    private var scrolling = false
    private var velocity: VelocityTracker? = null
    private var scroller: Scroller? = null
    private var flingView: View? = null
    private var flingTask: Runnable? = null
    private val longPressTask = Runnable {
        if (down && !moved) {
            longPressed = true
            selecting = canSelect()
            // Images retain their long-press action when text selection is disabled.
            longPress(x, y)
        }
    }

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancel()
                if (event.buttonState and (MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY) != 0) return true
                down = true; x = event.x; y = event.y; lastY = y; lastX = x
                velocity = VelocityTracker.obtain()
                handler.postDelayed(longPressTask, 600L) // Same threshold as NG ReadView.
            }
            MotionEvent.ACTION_MOVE -> if (down) {
                if (selecting) {
                    if (moved || abs(event.x - x) > slop || abs(event.y - y) > slop) {
                        moved = true; extend(event.x, event.y)
                    }
                }
                else if (abs(event.x - x) > slop || abs(event.y - y) > slop) {
                    moved = true; handler.removeCallbacks(longPressTask)
                    // Match ScrollPageDelegate: track MOVE velocity in pixels per second.
                    velocity?.addMovement(event)
                    velocity?.computeCurrentVelocity(1000)
                    scrolling = scroll(lastX - event.x, lastY - event.y) || scrolling
                }
                lastY = event.y; lastX = event.x
            }
            MotionEvent.ACTION_UP -> if (down) {
                handler.removeCallbacks(longPressTask)
                down = false
                if (selecting) finishSelection()
                else if (scrolling) { scrolling = false; startFling(view) }
                else if (!moved && !longPressed) tap(event.x, event.y)
                else {
                    val dx = event.x - x; val dy = event.y - y
                    if (abs(dx) > slop * 3 || abs(dy) > slop * 3) {
                        swipe(if (if (abs(dx) > abs(dy)) dx < 0 else dy < 0) 1 else -1)
                    }
                }
                recycleVelocity()
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                cancel(); cancelSelection()
            }
        }
        return true
    }

    private fun startFling(view: View) {
        val horizontal = horizontalScroll()
        val speed = (if (horizontal) velocity?.xVelocity else velocity?.yVelocity)?.toInt() ?: 0
        if (speed == 0 || !canFling()) { finishScroll(); return }
        val motion = scroller ?: Scroller(view.context, LinearInterpolator()).also { scroller = it }
        val extent = if (horizontal) view.width else view.height
        // Use the same Scroller, friction and ten-screen bounds as the TXT delegate.
        motion.fling(0, 0, if (horizontal) speed else 0, if (horizontal) 0 else speed,
            if (horizontal) -10 * extent else 0, if (horizontal) 10 * extent else 0,
            if (horizontal) 0 else -10 * extent, if (horizontal) 0 else 10 * extent)
        var previousX = 0
        var previousY = 0
        val task = object : Runnable {
            override fun run() {
                if (flingTask !== this) return
                if (!view.isAttachedToWindow || !view.isShown) { cancel(); return }
                if (!canFling() || !motion.computeScrollOffset()) { finishFling(); return }
                val dx = previousX - motion.currX
                val dy = previousY - motion.currY
                previousX = motion.currX; previousY = motion.currY
                if ((dx != 0 || dy != 0) && !scroll(dx.toFloat(), dy.toFloat())) {
                    finishFling()
                    return
                }
                // A scroll callback can synchronously replace the book or cancel the gesture.
                if (flingTask === this) view.postOnAnimation(this)
            }
        }
        flingView = view
        flingTask = task
        onFlingStart()
        if (flingTask === task) view.postOnAnimation(task)
    }

    private fun stopFling() {
        val task = flingTask ?: return
        flingTask = null
        flingView?.removeCallbacks(task)
        flingView = null
        scroller?.forceFinished(true)
        onFlingStop()
    }

    private fun finishFling() {
        if (flingTask == null) return
        stopFling()
        if (!down && flingTask == null) finishScroll()
    }

    private fun recycleVelocity() {
        velocity?.recycle()
        velocity = null
    }

    fun cancel() {
        stopFling()
        cancelScroll()
        recycleVelocity()
        handler.removeCallbacks(longPressTask)
        down = false; moved = false; selecting = false; scrolling = false; longPressed = false
    }
}
