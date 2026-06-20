package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.createBitmap
import io.legado.app.R
import kotlin.math.max
import kotlin.math.roundToInt

class NgGlassLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val rect = RectF()
    private val srcRect = Rect()
    private val clipPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var glassBitmap: Bitmap? = null
    private var radius = dp(24f)
    private var tintColor = Color.argb(176, 255, 255, 255)
    private var strokeColor = Color.argb(150, 255, 255, 255)
    private var strokeWidth = dp(0.8f)
    private var blurRadius = dp(18f)
    private var downSample = 0.28f

    init {
        setWillNotDraw(false)
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        val initialPaddingStart = paddingStart
        val initialPaddingTop = paddingTop
        val initialPaddingEnd = paddingEnd
        val initialPaddingBottom = paddingBottom
        background = null
        setPaddingRelative(
            initialPaddingStart,
            initialPaddingTop,
            initialPaddingEnd,
            initialPaddingBottom
        )
        context.withStyledAttributes(attrs, R.styleable.NgGlassLayout, defStyleAttr, 0) {
            radius = getDimension(R.styleable.NgGlassLayout_ngGlassRadius, radius)
            tintColor = getColor(R.styleable.NgGlassLayout_ngGlassTintColor, tintColor)
            strokeColor = getColor(R.styleable.NgGlassLayout_ngGlassStrokeColor, strokeColor)
            strokeWidth = getDimension(R.styleable.NgGlassLayout_ngGlassStrokeWidth, strokeWidth)
            blurRadius = getDimension(R.styleable.NgGlassLayout_ngGlassBlurRadius, blurRadius)
            downSample = getFloat(R.styleable.NgGlassLayout_ngGlassDownSample, downSample)
                .coerceIn(0.12f, 1f)
        }
        fillPaint.color = tintColor
        strokePaint.color = strokeColor
        strokePaint.strokeWidth = strokeWidth
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        invalidateGlass()
    }

    override fun onDetachedFromWindow() {
        glassBitmap?.recycle()
        glassBitmap = null
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidateGlass()
    }

    override fun onDraw(canvas: Canvas) {
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        clipPath.reset()
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW)

        canvas.save()
        canvas.clipPath(clipPath)
        ensureGlassBitmap()?.let { bitmap ->
            srcRect.set(0, 0, bitmap.width, bitmap.height)
            canvas.drawBitmap(bitmap, srcRect, rect, bitmapPaint)
        }
        canvas.drawRoundRect(rect, radius, radius, fillPaint)
        canvas.restore()

        val halfStroke = strokeWidth / 2f
        if (strokeWidth > 0f) {
            rect.inset(halfStroke, halfStroke)
            canvas.drawRoundRect(rect, radius, radius, strokePaint)
            rect.inset(-halfStroke, -halfStroke)
        }
        super.onDraw(canvas)
    }

    fun invalidateGlass() {
        glassBitmap?.recycle()
        glassBitmap = null
        invalidate()
    }

    private fun ensureGlassBitmap(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        glassBitmap?.let { return it }

        val root = rootView ?: return null
        val rootBackground = root.background ?: return null
        val viewLocation = IntArray(2)
        val rootLocation = IntArray(2)
        getLocationInWindow(viewLocation)
        root.getLocationInWindow(rootLocation)
        val offsetX = viewLocation[0] - rootLocation[0]
        val offsetY = viewLocation[1] - rootLocation[1]
        val sampleWidth = max(1, (width * downSample).roundToInt())
        val sampleHeight = max(1, (height * downSample).roundToInt())
        val bitmap = createBitmap(sampleWidth, sampleHeight)
        val bitmapCanvas = Canvas(bitmap)
        bitmapCanvas.scale(downSample, downSample)
        bitmapCanvas.translate(-offsetX.toFloat(), -offsetY.toFloat())
        rootBackground.setBounds(0, 0, root.width, root.height)
        rootBackground.draw(bitmapCanvas)
        blurBitmap(bitmap, max(1, (blurRadius * downSample).roundToInt()))
        glassBitmap = bitmap
        return bitmap
    }

    private fun blurBitmap(bitmap: Bitmap, radius: Int) {
        if (radius < 1) return
        val width = bitmap.width
        val height = bitmap.height
        val size = width * height
        val src = IntArray(size)
        val tmp = IntArray(size)
        val out = IntArray(size)
        bitmap.getPixels(src, 0, width, 0, 0, width, height)
        boxBlurHorizontal(src, tmp, width, height, radius)
        boxBlurVertical(tmp, out, width, height, radius)
        bitmap.setPixels(out, 0, width, 0, 0, width, height)
    }

    private fun boxBlurHorizontal(src: IntArray, out: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (y in 0 until height) {
            val row = y * width
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val color = src[row + i.coerceIn(0, width - 1)]
                a += color ushr 24
                r += color shr 16 and 0xFF
                g += color shr 8 and 0xFF
                b += color and 0xFF
            }
            for (x in 0 until width) {
                out[row + x] = (a / window shl 24) or
                    (r / window shl 16) or
                    (g / window shl 8) or
                    (b / window)
                val remove = src[row + (x - radius).coerceIn(0, width - 1)]
                val add = src[row + (x + radius + 1).coerceIn(0, width - 1)]
                a += (add ushr 24) - (remove ushr 24)
                r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    private fun boxBlurVertical(src: IntArray, out: IntArray, width: Int, height: Int, radius: Int) {
        val window = radius * 2 + 1
        for (x in 0 until width) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            for (i in -radius..radius) {
                val color = src[i.coerceIn(0, height - 1) * width + x]
                a += color ushr 24
                r += color shr 16 and 0xFF
                g += color shr 8 and 0xFF
                b += color and 0xFF
            }
            for (y in 0 until height) {
                out[y * width + x] = (a / window shl 24) or
                    (r / window shl 16) or
                    (g / window shl 8) or
                    (b / window)
                val remove = src[(y - radius).coerceIn(0, height - 1) * width + x]
                val add = src[(y + radius + 1).coerceIn(0, height - 1) * width + x]
                a += (add ushr 24) - (remove ushr 24)
                r += (add shr 16 and 0xFF) - (remove shr 16 and 0xFF)
                g += (add shr 8 and 0xFF) - (remove shr 8 and 0xFF)
                b += (add and 0xFF) - (remove and 0xFF)
            }
        }
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}
