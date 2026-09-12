package io.legado.app.ui.main

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.lib.theme.accentColor
import io.legado.app.utils.dpToPx
import kotlin.math.abs
import kotlin.math.sin

class AiChatButtonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        maskFilter = BlurMaskFilter(6f.dp, BlurMaskFilter.Blur.NORMAL)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val breathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.2f.dp
        alpha = (255 * 0.22f).toInt()
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(NgThemeRuntimeAssets.appTypeface(context) ?: Typeface.DEFAULT, Typeface.BOLD)
    }
    private val sparkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val sparkPath = Path()
    private val frameCallback = object : Runnable {
        override fun run() {
            invalidate()
            if (isShown) {
                postOnAnimation(this)
            }
        }
    }

    private var accent = context.accentColor
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0f
    private var startY = 0f
    private var dragging = false
    private var framePosted = false

    init {
        isClickable = true
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startTicker()
    }

    override fun onDetachedFromWindow() {
        stopTicker()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startTicker()
        } else {
            stopTicker()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        if (size <= 0f) return
        val elapsed = SystemClock.uptimeMillis()
        val breathe = wave(elapsed, 3600L)
        val spark = wave(elapsed, 2800L)
        val cx = width * 0.5f
        val cy = height * 0.5f
        val radius = size * 27f / 72f
        val main = accent
        val light = ColorUtils.blendARGB(accent, Color.WHITE, 0.35f)

        shadowPaint.color = ColorUtils.setAlphaComponent(main, (255 * 0.24f).toInt())
        canvas.drawCircle(cx, cy + size * 4f / 72f, size * 24f / 72f, shadowPaint)

        bgPaint.shader = LinearGradient(
            cx - size * 18f / 72f,
            cy - size * 24f / 72f,
            cx + size * 20f / 72f,
            cy + size * 24f / 72f,
            light,
            main,
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, bgPaint)
        bgPaint.shader = null

        breathPaint.alpha = ((0.08f + 0.1f * breathe) * 255).toInt()
        canvas.drawCircle(
            width * 31f / 72f,
            height * 27f / 72f,
            size * 20f / 72f,
            breathPaint
        )
        breathPaint.alpha = 255

        canvas.drawCircle(cx, cy, size * 26.3f / 72f, strokePaint)

        textPaint.textSize = size * 24f / 72f
        canvas.drawText("AI", width * 24f / 72f, height * 45f / 72f, textPaint)

        sparkPaint.alpha = ((0.42f + 0.53f * spark) * 255).toInt()
        buildSparkPath()
        canvas.save()
        canvas.scale(
            0.94f + 0.11f * spark,
            0.94f + 0.11f * spark,
            width * 52f / 72f,
            height * 22f / 72f
        )
        canvas.drawPath(sparkPath, sparkPaint)
        canvas.restore()
        sparkPaint.alpha = 255
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val parentView = parent as? View ?: return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                startX = x
                startY = y
                dragging = false
                parentView.parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!dragging && abs(dx) + abs(dy) > touchSlop) {
                    dragging = true
                }
                if (dragging) {
                    val margin = 8.dpToPx().toFloat()
                    val maxX = (parentView.width - width - margin).coerceAtLeast(margin)
                    val maxY = (parentView.height - height - margin).coerceAtLeast(margin)
                    x = (startX + dx).coerceIn(margin, maxX)
                    y = (startY + dy).coerceIn(margin, maxY)
                }
                return true
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                parentView.parent?.requestDisallowInterceptTouchEvent(false)
                if (!dragging && event.actionMasked == MotionEvent.ACTION_UP) {
                    performClick()
                }
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun updateAccentColor(color: Int) {
        if (accent != color) {
            accent = color
            invalidate()
        }
    }

    private fun buildSparkPath() {
        fun sx(value: Float) = width * value / 72f
        fun sy(value: Float) = height * value / 72f
        sparkPath.reset()
        sparkPath.moveTo(sx(52f), sy(15f))
        sparkPath.lineTo(sx(54f), sy(20f))
        sparkPath.lineTo(sx(59f), sy(22f))
        sparkPath.lineTo(sx(54f), sy(24f))
        sparkPath.lineTo(sx(52f), sy(29f))
        sparkPath.lineTo(sx(50f), sy(24f))
        sparkPath.lineTo(sx(45f), sy(22f))
        sparkPath.lineTo(sx(50f), sy(20f))
        sparkPath.close()
    }

    private fun wave(elapsed: Long, duration: Long): Float {
        val phase = (elapsed % duration).toFloat() / duration
        return ((sin((phase * Math.PI * 2 - Math.PI / 2)).toFloat() + 1f) / 2f)
    }

    private fun startTicker() {
        if (!framePosted) {
            framePosted = true
            postOnAnimation(frameCallback)
        }
    }

    private fun stopTicker() {
        framePosted = false
        removeCallbacks(frameCallback)
    }

    private val Float.dp: Float
        get() = this * resources.displayMetrics.density
}
