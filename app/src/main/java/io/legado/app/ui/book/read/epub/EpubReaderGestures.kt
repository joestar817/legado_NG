package io.legado.app.ui.book.read.epub

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
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
                    scrolling = scroll(lastX - event.x, lastY - event.y) || scrolling
                }
                lastY = event.y; lastX = event.x
            }
            MotionEvent.ACTION_UP -> if (down) {
                handler.removeCallbacks(longPressTask)
                down = false
                if (selecting) finishSelection()
                else if (scrolling) { scrolling = false; finishScroll() }
                else if (!moved && !longPressed) tap(event.x, event.y)
                else {
                    val dx = event.x - x; val dy = event.y - y
                    if (abs(dx) > slop * 3 || abs(dy) > slop * 3) {
                        swipe(if (if (abs(dx) > abs(dy)) dx < 0 else dy < 0) 1 else -1)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                cancel(); cancelSelection()
            }
        }
        return true
    }

    fun cancel() {
        if (scrolling) { scrolling = false; finishScroll() }
        handler.removeCallbacks(longPressTask)
        down = false; moved = false; selecting = false; scrolling = false; longPressed = false
    }
}
