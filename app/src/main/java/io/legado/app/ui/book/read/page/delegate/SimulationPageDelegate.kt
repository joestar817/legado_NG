package io.legado.app.ui.book.read.page.delegate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.page.PageCurlRenderer
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.utils.screenshot

class SimulationPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {
    private val renderer = PageCurlRenderer()
    private var curBitmap: Bitmap? = null
    private var prevBitmap: Bitmap? = null
    private var nextBitmap: Bitmap? = null
    private val canvas = Canvas()

    override fun setBitmap() {
        // ReadView owns these stable frames until the animation handoff ends.
        // Drawing them directly avoids two more full-screen copies per turn.
        if (readView.externalPageFrames != null) return
        when (mDirection) {
            PageDirection.PREV -> {
                prevBitmap = prevPage.screenshot(prevBitmap, canvas)
                curBitmap = curPage.screenshot(curBitmap, canvas)
            }

            PageDirection.NEXT -> {
                nextBitmap = nextPage.screenshot(nextBitmap, canvas)
                curBitmap = curPage.screenshot(curBitmap, canvas)
            }

            else -> Unit
        }
    }

    override fun setViewSize(width: Int, height: Int) {
        super.setViewSize(width, height)
        renderer.setViewSize(width, height)
    }

    override fun onTouch(event: MotionEvent) {
        super.onTouch(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                renderer.calcCornerXY(event.x, event.y)
            }

            MotionEvent.ACTION_MOVE -> {
                if ((startY > viewHeight / 3 && startY < viewHeight * 2 / 3)
                    || mDirection == PageDirection.PREV
                ) {
                    readView.touchY = viewHeight.toFloat()
                }

                if (startY > viewHeight / 3 && startY < viewHeight / 2
                    && mDirection == PageDirection.NEXT
                ) {
                    readView.touchY = 1f
                }
            }
        }
    }

    override fun setDirection(direction: PageDirection) {
        super.setDirection(direction)
        when (direction) {
            PageDirection.PREV ->
                //上一页滑动不出现对角
                if (startX > viewWidth / 2) {
                    renderer.calcCornerXY(startX, viewHeight.toFloat())
                } else {
                    renderer.calcCornerXY(viewWidth - startX, viewHeight.toFloat())
                }

            PageDirection.NEXT ->
                if (viewWidth / 2 > startX) {
                    renderer.calcCornerXY(viewWidth - startX, startY)
                }

            else -> Unit
        }
    }

    override fun onAnimStart(animationSpeed: Int) {
        var dx: Float
        val dy: Float
        // dy 垂直方向滑动的距离，负值会使滚动向上滚动
        if (isCancel) {
            dx = if (renderer.cornerX > 0 && mDirection == PageDirection.NEXT) {
                (viewWidth - touchX)
            } else {
                -touchX
            }
            if (mDirection != PageDirection.NEXT) {
                dx = -(viewWidth + touchX)
            }
            dy = if (renderer.cornerY > 0) {
                (viewHeight - touchY)
            } else {
                -touchY // 防止mTouchY最终变为0
            }
        } else {
            dx = if (renderer.cornerX > 0 && mDirection == PageDirection.NEXT) {
                -(viewWidth + touchX)
            } else {
                viewWidth - touchX
            }
            dy = if (renderer.cornerY > 0) {
                (viewHeight - touchY)
            } else {
                (1 - touchY) // 防止mTouchY最终变为0
            }
        }
        startScroll(touchX.toInt(), touchY.toInt(), dx.toInt(), dy.toInt(), animationSpeed)
    }

    override fun onAnimStop() {
        if (!isCancel) {
            readView.fillPage(mDirection)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!isRunning) return
        if (mDirection == PageDirection.NONE) return
        renderer.backgroundColor = ReadBookConfig.bgMeanColor
        readView.externalPageFrames?.takeIf { it.curlPages }?.let {
            renderer.drawPages(canvas, it.first, it.second, mDirection, touchX, touchY)
            return
        }
        val frames = readView.externalPageSnapshots
        val current = frames?.first ?: curBitmap
        val adjacent = frames?.second ?: if (mDirection == PageDirection.PREV) prevBitmap else nextBitmap
        renderer.draw(canvas, current, adjacent, mDirection, touchX, touchY)
    }
}
