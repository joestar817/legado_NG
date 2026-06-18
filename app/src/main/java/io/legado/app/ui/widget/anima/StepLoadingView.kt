package io.legado.app.ui.widget.anima

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import io.legado.app.utils.dpToPx

class StepLoadingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2.dpToPx().toFloat()
    }
    private val arcRect = RectF()
    private var rotationDegree = 0f
    private var lastFrameTime = 0L
    var loadingColor: Int = Color.parseColor("#1565C0")
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (visibility != VISIBLE) return
        advanceRotation()
        val padding = 4.dpToPx().toFloat()
        arcRect.set(
            padding,
            padding,
            width - padding,
            height - padding
        )
        val centerX = width / 2f
        val centerY = height / 2f
        paint.color = loadingColor
        canvas.save()
        canvas.rotate(rotationDegree, centerX, centerY)
        canvas.drawArc(arcRect, -135f, 62f, false, paint)
        canvas.drawArc(arcRect, 45f, 62f, false, paint)
        canvas.restore()
        postInvalidateOnAnimation()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        resetFrameClock()
        if (visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        resetFrameClock()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        resetFrameClock()
        if (visibility == VISIBLE) {
            postInvalidateOnAnimation()
        }
    }

    private fun advanceRotation() {
        val now = SystemClock.uptimeMillis()
        if (lastFrameTime == 0L) {
            lastFrameTime = now
            return
        }
        val deltaMs = (now - lastFrameTime).coerceIn(0L, MAX_FRAME_STEP_MS)
        lastFrameTime = now
        rotationDegree = (rotationDegree + deltaMs * ROTATION_DEGREES_PER_MS) % 360f
    }

    private fun resetFrameClock() {
        lastFrameTime = 0L
    }

    private companion object {
        private const val MAX_FRAME_STEP_MS = 100L
        private const val ROTATION_DEGREES_PER_MS = 1.08f
    }

}
