package io.legado.app.ui.config

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.roundToInt

class TtsVoicePreviewIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * density
    }
    private val arcBounds = RectF()
    private var progress = 0f
    private var state = TtsVoicePreviewState.IDLE
    private var animator: ValueAnimator? = null

    fun setPreviewState(state: TtsVoicePreviewState, color: Int) {
        val stateChanged = this.state != state
        if (stateChanged) stopAnimator()
        this.state = state
        paint.color = color
        visibility = if (state == TtsVoicePreviewState.IDLE) GONE else VISIBLE
        if (state == TtsVoicePreviewState.IDLE) {
            stopAnimator()
        } else {
            startAnimator()
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        when (state) {
            TtsVoicePreviewState.LOADING -> drawLoadingArc(canvas)
            TtsVoicePreviewState.PLAYING -> drawPlayingRipple(canvas)
            TtsVoicePreviewState.IDLE -> Unit
        }
    }

    private fun drawLoadingArc(canvas: Canvas) {
        val inset = 3f * density
        val buttonSize = minOf(height.toFloat(), width.toFloat())
        arcBounds.set(inset, inset, buttonSize - inset, buttonSize - inset)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f * density
        paint.alpha = 255
        canvas.drawArc(arcBounds, progress * 360f - 90f, 92f, false, paint)
    }

    private fun drawPlayingRipple(canvas: Canvas) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f * density
        paint.alpha = ((1f - progress) * 160f).roundToInt()
        val startRadius = 11.5f * density
        val endRadius = minOf(width, height) / 2f - paint.strokeWidth
        val radius = startRadius + (endRadius - startRadius) * progress
        canvas.drawCircle(width / 2f, height / 2f, radius, paint)
        paint.alpha = 255
    }

    private fun startAnimator() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (state == TtsVoicePreviewState.LOADING) 900L else 1200L
            interpolator = LinearInterpolator()
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimator() {
        animator?.cancel()
        animator = null
        progress = 0f
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (state != TtsVoicePreviewState.IDLE) startAnimator()
    }

    override fun onDetachedFromWindow() {
        stopAnimator()
        super.onDetachedFromWindow()
    }
}
