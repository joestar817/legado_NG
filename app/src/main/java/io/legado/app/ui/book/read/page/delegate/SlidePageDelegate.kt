package io.legado.app.ui.book.read.page.delegate

import android.graphics.Canvas
import androidx.core.graphics.withTranslation
import io.legado.app.ui.book.read.page.ReadView
import io.legado.app.ui.book.read.page.entities.PageDirection

class SlidePageDelegate(readView: ReadView) : HorizontalPageDelegate(readView) {

    override fun setBitmap() {
        // ReadView already owns and prepares the external frames until handoff.
        if (readView.externalPageFrames != null) return
        super.setBitmap()
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

    override fun onDraw(canvas: Canvas) {
        val offsetX = touchX - startX

        if ((mDirection == PageDirection.NEXT && offsetX > 0)
            || (mDirection == PageDirection.PREV && offsetX < 0)
        ) return
        val distanceX = if (offsetX > 0) offsetX - viewWidth else offsetX + viewWidth
        if (!isRunning) return
        val frames = readView.externalPageFrames
        if (mDirection == PageDirection.PREV) {
            canvas.withTranslation(distanceX + viewWidth) {
                if (frames != null) frames.first.draw(this)
                else curRecorder.draw(this)
            }
            canvas.withTranslation(distanceX) {
                if (frames != null) frames.second.draw(this)
                else prevRecorder.draw(this)
            }
        } else if (mDirection == PageDirection.NEXT) {
            canvas.withTranslation(distanceX) {
                if (frames != null) frames.second.draw(this)
                else nextRecorder.draw(this)
            }
            canvas.withTranslation(distanceX - viewWidth) {
                if (frames != null) frames.first.draw(this)
                else curRecorder.draw(this)
            }
        }
    }

    override fun onAnimStop() {
        if (!isCancel) {
            readView.fillPage(mDirection)
        }
    }
}
