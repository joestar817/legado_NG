package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import io.legado.app.ui.book.read.page.PageCoverRenderer
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection
import io.legado.app.utils.screenshot

class CoverPageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {
    private val renderer = PageCoverRenderer()

    override fun onDraw(canvas: Canvas) {
        if (!isRunning) return
        val frames = readView.externalPageFrames
        if (frames != null) {
            renderer.draw(canvas, touchX - startX, mDirection,
                frames.second.width.toFloat(), frames.second.height.toFloat(),
                current = { frames.first.draw(it) },
                adjacent = { frames.second.draw(it) })
            return
        }
        renderer.draw(canvas, touchX - startX, mDirection,
            nextRecorder.width.toFloat(), nextRecorder.height.toFloat(),
            current = { curRecorder.draw(it) },
            adjacent = { if (mDirection == PageDirection.PREV) prevRecorder.draw(it) else nextRecorder.draw(it) })
    }

    override fun setBitmap() {
        // ReadView already owns and prepares the external frames until handoff.
        if (readView.externalPageFrames != null) {
            return
        }
        when (mDirection) {
            PageDirection.PREV -> {
                prevPage.screenshot(prevRecorder)
            }

            PageDirection.NEXT -> {
                nextPage.screenshot(nextRecorder)
                curPage.screenshot(curRecorder)
            }

            else -> Unit
        }
    }

    override fun setViewSize(width: Int, height: Int) {
        super.setViewSize(width, height)
        renderer.setViewSize(width, height)
    }

    override fun onAnimStop() {
        if (!isCancel) {
            readView.fillPage(mDirection)
        }
    }

    override fun onAnimStart(animationSpeed: Int) {
        val distanceX: Float
        when (mDirection) {
            PageDirection.NEXT -> distanceX =
                if (isCancel) {
                    var dis = viewWidth - startX + touchX
                    if (dis > viewWidth) {
                        dis = viewWidth.toFloat()
                    }
                    viewWidth - dis
                } else {
                    -(touchX + (viewWidth - startX))
                }

            else -> distanceX =
                if (isCancel) {
                    -(touchX - startX)
                } else {
                    viewWidth - (touchX - startX)
                }
        }
        startScroll(touchX.toInt(), 0, distanceX.toInt(), 0, animationSpeed)
    }

}
