package io.legado.app.ui.main.chatentry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.R
import io.legado.app.ui.main.chatentry.core.ChatPetEngine
import io.legado.app.ui.main.chatentry.core.PetArmMesh
import io.legado.app.ui.main.chatentry.core.PetFrame
import io.legado.app.ui.main.chatentry.core.PetLayerDefinition
import io.legado.app.ui.main.chatentry.core.PetPoint
import io.legado.app.ui.main.chatentry.core.PetPose
import io.legado.app.ui.main.chatentry.core.WaveArmMesh
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.roundToInt

/** A transparent, app-local overlay. Blank pixels do not claim the underlying page's touch. */
class AiChatPetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private data class DrawLayer(
        val layer: PetLayerDefinition,
        val bitmap: Bitmap,
        val matrix: FloatArray,
        val opacity: Float,
        val mesh: PetArmMesh?,
    )

    private val density = resources.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val matrix = Matrix()
    private val matrixValues = FloatArray(9)
    private val localRect = RectF()
    private val bubbleTextBounds = Rect()
    private val path = Path()
    private val hitBounds = RectF()
    private val screenLocation = IntArray(2)
    private val drawLayers = ArrayList<DrawLayer>(13)
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var bitmaps: Map<String, Bitmap> = emptyMap()
    private var loadedDirectory: String? = null
    private var engine: ChatPetEngine? = null
    private var hostActive = false
    private var framePosted = false
    private var lastFrameTime = 0L
    private var insetLeft = 0f
    private var insetTop = 0f
    private var insetRight = 0f
    private var insetBottom = 0f
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var maxDragDistance = 0f
    private var lifecycleReady = false

    var onAssetLoadFailed: (() -> Unit)? = null

    private val frameCallback = object : Runnable {
        override fun run() {
            framePosted = false
            if (!canAnimate()) return
            val now = SystemClock.uptimeMillis()
            if (lastFrameTime != 0L) engine?.advance((now - lastFrameTime).coerceAtLeast(0L))
            lastFrameTime = now
            invalidate()
            scheduleFrame()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        isSaveEnabled = false // The host retains the engine and saves its placement.
        contentDescription = context.getString(R.string.ai_pet_open_chat)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (insetLeft != bars.left.toFloat() || insetTop != bars.top.toFloat() ||
                insetRight != bars.right.toFloat() || insetBottom != bars.bottom.toFloat()) {
                cancelPointer()
                insetLeft = bars.left.toFloat()
                insetTop = bars.top.toFloat()
                insetRight = bars.right.toFloat()
                insetBottom = bars.bottom.toFloat()
                resizeEngine()
            }
            insets
        }
        lifecycleReady = true
    }

    fun bindEngine(value: ChatPetEngine) {
        if (engine !== value) {
            cancelPointer()
            stopTicker()
            if (engine?.definition?.assetDirectory != value.definition.assetDirectory) {
                loadJob?.cancel()
                loadJob = null
                loadedDirectory = null
                bitmaps = emptyMap()
            }
            engine = value
            drawLayers.clear()
            hitBounds.setEmpty()
            resizeEngine()
        }
        loadArtwork()
        updateTicker()
    }

    fun setHostActive(active: Boolean) {
        if (!active && hostActive) advanceClock()
        hostActive = active
        if (!active) cancelPointer()
        updateTicker()
    }

    fun isTouchOnPet(rawX: Float, rawY: Float): Boolean {
        if (!isShown || !hostActive) return false
        getLocationOnScreen(screenLocation)
        return hitTest(toPoint(rawX - screenLocation[0], rawY - screenLocation[1]))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        loadArtwork()
        updateTicker()
    }

    override fun onDetachedFromWindow() {
        cancelPointer()
        stopTicker()
        loadJob?.cancel()
        loadJob = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != VISIBLE) cancelPointer()
        updateTicker()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (!lifecycleReady) return
        if (visibility != VISIBLE) cancelPointer()
        updateTicker()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelPointer()
        resizeEngine()
    }

    private fun resizeEngine() {
        val w = (width - insetLeft - insetRight) / density
        val h = (height - insetTop - insetBottom) / density
        if (w > 0f && h > 0f) engine?.resize(w, h)
        drawLayers.clear()
        hitBounds.setEmpty()
        invalidate()
    }

    private fun loadArtwork() {
        val definition = engine?.definition ?: return
        if (loadedDirectory == definition.assetDirectory || loadJob?.isActive == true) return
        val directory = definition.assetDirectory
        val assetManager = context.applicationContext.assets
        loadJob = loadScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    PetPose.entries.flatMap { definition.layers(it) }.map { it.file }.distinct().associateWith { file ->
                        assetManager.open("$directory/$file").use { stream ->
                            requireNotNull(BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply {
                                inScaled = false
                                inPreferredConfig = Bitmap.Config.ARGB_8888
                            })) { "Invalid pet artwork: $file" }
                        }
                    }
                }
                if (engine?.definition?.assetDirectory == directory) {
                    bitmaps = result
                    loadedDirectory = directory
                    invalidate()
                    updateTicker()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                onAssetLoadFailed?.invoke()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val currentEngine = engine ?: return
        val definition = currentEngine.definition
        if (loadedDirectory != definition.assetDirectory || bitmaps.isEmpty()) return
        val frame = currentEngine.frame()
        drawLayers.clear()
        hitBounds.setEmpty()
        canvas.save()
        canvas.translate(insetLeft, insetTop)
        canvas.scale(density, density)
        val viewportWidth = (width - insetLeft - insetRight) / density
        val viewportHeight = (height - insetTop - insetBottom) / density
        canvas.clipRect(0f, 0f, viewportWidth, viewportHeight)
        for (layer in definition.layers(frame.pose)) {
            val opacity = definition.layerOpacity(frame.motion, layer, frame.pose)
            if (opacity <= 0f) continue
            val bitmap = bitmaps[layer.file] ?: continue
            val transform = definition.layerMatrix(frame, layer)
            val mesh = if (frame.pose == PetPose.FULL) WaveArmMesh.build(layer, transform, frame.motion) else null
            paint.alpha = (opacity * 255f).roundToInt().coerceIn(0, 255)
            localRect.set(0f, 0f, layer.width, layer.height)
            if (mesh == null) {
                canvas.save()
                concat(canvas, transform)
                canvas.drawBitmap(bitmap, null, localRect, paint)
                canvas.restore()
            } else {
                for (triangle in mesh.triangles) {
                    canvas.save()
                    path.reset()
                    path.moveTo(triangle.clipDst[0].x, triangle.clipDst[0].y)
                    path.lineTo(triangle.clipDst[1].x, triangle.clipDst[1].y)
                    path.lineTo(triangle.clipDst[2].x, triangle.clipDst[2].y)
                    path.close()
                    canvas.clipPath(path)
                    concat(canvas, triangle.matrix)
                    canvas.drawBitmap(bitmap, null, localRect, paint)
                    canvas.restore()
                }
            }
            if (layer.parentId == null && opacity > .12f) {
                drawLayers.add(DrawLayer(layer, bitmap, transform, opacity, mesh))
                addBounds(layer, transform, mesh)
            }
        }
        if (frame.pose == PetPose.FULL && frame.motion.shout > 0f) drawBubble(canvas, frame)
        canvas.restore()
        if (!hitBounds.intersect(0f, 0f, viewportWidth, viewportHeight)) hitBounds.setEmpty()
    }

    private fun concat(canvas: Canvas, values: FloatArray) {
        matrixValues[0] = values[0]; matrixValues[1] = values[2]; matrixValues[2] = values[4]
        matrixValues[3] = values[1]; matrixValues[4] = values[3]; matrixValues[5] = values[5]
        matrixValues[6] = 0f; matrixValues[7] = 0f; matrixValues[8] = 1f
        matrix.setValues(matrixValues)
        canvas.concat(matrix)
    }

    private fun addBounds(layer: PetLayerDefinition, m: FloatArray, mesh: PetArmMesh?) {
        val bounds = if (mesh != null) {
            RectF(mesh.bounds.left, mesh.bounds.top, mesh.bounds.right, mesh.bounds.bottom)
        } else {
            val xs = floatArrayOf(m[4], m[0] * layer.width + m[4], m[2] * layer.height + m[4], m[0] * layer.width + m[2] * layer.height + m[4])
            val ys = floatArrayOf(m[5], m[1] * layer.width + m[5], m[3] * layer.height + m[5], m[1] * layer.width + m[3] * layer.height + m[5])
            RectF(xs.min(), ys.min(), xs.max(), ys.max())
        }
        if (hitBounds.isEmpty) hitBounds.set(bounds) else hitBounds.union(bounds)
    }

    private fun drawBubble(canvas: Canvas, frame: PetFrame) {
        val definition = engine?.definition ?: return
        val anchor = definition.headAnchor(frame.pose)
        val scale = definition.scale
        val x = frame.anchor.x + (256f - anchor.x) * scale
        val y = frame.anchor.y + (38f - anchor.y) * scale
        val alpha = (frame.motion.shout * 255f).roundToInt().coerceIn(0, 255)
        // Original artwork: a fuller 340 x 130 oval with two short inward tails.
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        path.reset()
        path.moveTo(0f, -65f)
        path.cubicTo(94f, -65f, 170f, -36f, 170f, 0f)
        path.cubicTo(170f, 22f, 146f, 40f, 112f, 49f)
        path.quadTo(105f, 74f, 83f, 88f)
        path.quadTo(96f, 69f, 95f, 54f)
        path.cubicTo(40f, 69f, -40f, 69f, -95f, 54f)
        path.quadTo(-94f, 70f, -83f, 88f)
        path.quadTo(-104f, 75f, -112f, 49f)
        path.cubicTo(-147f, 39f, -170f, 21f, -170f, 0f)
        path.cubicTo(-170f, -36f, -94f, -65f, 0f, -65f)
        path.close()
        bubblePaint.color = Color.WHITE
        bubblePaint.alpha = alpha
        bubblePaint.style = Paint.Style.FILL
        canvas.drawPath(path, bubblePaint)
        bubblePaint.color = Color.rgb(37, 37, 30)
        bubblePaint.alpha = alpha
        bubblePaint.style = Paint.Style.STROKE
        bubblePaint.strokeWidth = 3.4f
        canvas.drawPath(path, bubblePaint)
        textPaint.color = Color.rgb(37, 37, 30)
        textPaint.alpha = alpha
        textPaint.textSize = 56f
        val text = "咕咕嘎嘎！"
        val textWidth = textPaint.measureText(text)
        val maxTextWidth = 340f * .78f
        if (textWidth > maxTextWidth) textPaint.textSize *= maxTextWidth / textWidth
        textPaint.getTextBounds(text, 0, text.length, bubbleTextBounds)
        val baseline = -(bubbleTextBounds.top + bubbleTextBounds.bottom) / 2f
        canvas.drawText(text, 0f, baseline, textPaint)
        canvas.restore()
    }

    private fun toPoint(x: Float, y: Float) = PetPoint((x - insetLeft) / density, (y - insetTop) / density)

    private fun hitTest(point: PetPoint): Boolean {
        if (!hitBounds.contains(point.x, point.y)) return false
        for (item in drawLayers.asReversed()) {
            fun alphaAt(x: Float, y: Float): Int {
                if (x < 0f || y < 0f || x >= item.layer.width || y >= item.layer.height) return 0
                val px = floor(x / item.layer.width * item.bitmap.width).toInt()
                val py = floor(y / item.layer.height * item.bitmap.height).toInt()
                return Color.alpha(item.bitmap.getPixel(px, py))
            }
            val mesh = item.mesh
            if (mesh != null) {
                if (WaveArmMesh.hitTest(mesh, point, ::alphaAt)) return true
                continue
            }
            val m = item.matrix
            val determinant = m[0] * m[3] - m[1] * m[2]
            if (abs(determinant) < 1e-7f) continue
            val dx = point.x - m[4]
            val dy = point.y - m[5]
            if (alphaAt((m[3] * dx - m[2] * dy) / determinant, (-m[1] * dx + m[0] * dy) / determinant) > 32) return true
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val currentEngine = engine ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val point = toPoint(event.x, event.y)
                if (!hostActive || !hitTest(point)) return false
                advanceClock()
                pointerId = event.getPointerId(0)
                downX = event.x; downY = event.y; maxDragDistance = 0f
                currentEngine.press(point.x, point.y, event.eventTime)
                parent?.requestDisallowInterceptTouchEvent(true)
                updateTicker()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return false
                val x = event.getX(index); val y = event.getY(index)
                maxDragDistance = maxOf(maxDragDistance, hypot(x - downX, y - downY))
                val point = toPoint(x, y)
                currentEngine.move(point.x, point.y, event.eventTime, maxDragDistance > 6f * density)
                updateTicker()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) { cancelPointer(); return false }
                val x = event.getX(index); val y = event.getY(index)
                maxDragDistance = maxOf(maxDragDistance, hypot(x - downX, y - downY))
                val point = toPoint(x, y)
                currentEngine.move(point.x, point.y, event.eventTime, maxDragDistance > 6f * density)
                val clicked = currentEngine.release(point.x, point.y)
                pointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
                updateTicker()
                invalidate()
                if (clicked) performClick()
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == pointerId) cancelPointer()
                return true
            }
            MotionEvent.ACTION_CANCEL -> { cancelPointer(); return true }
            MotionEvent.ACTION_POINTER_DOWN -> return pointerId != MotionEvent.INVALID_POINTER_ID
        }
        return false
    }

    override fun performClick(): Boolean {
        if (engine == null || bitmaps.isEmpty()) return false
        super.performClick()
        return true
    }

    private fun cancelPointer() {
        if (pointerId == MotionEvent.INVALID_POINTER_ID) return
        engine?.cancel()
        pointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
        updateTicker()
        invalidate()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = "android.widget.Button"
        if (!hitBounds.isEmpty) {
            val bounds = Rect(
                (hitBounds.left * density + insetLeft).roundToInt(), (hitBounds.top * density + insetTop).roundToInt(),
                (hitBounds.right * density + insetLeft).roundToInt(), (hitBounds.bottom * density + insetTop).roundToInt(),
            )
            info.setBoundsInParent(bounds)
            getLocationOnScreen(screenLocation)
            bounds.offset(screenLocation[0], screenLocation[1])
            info.setBoundsInScreen(bounds)
        }
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean {
        if (event.actionMasked != MotionEvent.ACTION_HOVER_EXIT && !hitTest(toPoint(event.x, event.y))) return false
        return super.dispatchHoverEvent(event)
    }

    private fun canAnimate() = hostActive && isAttachedToWindow && isShown && windowVisibility == VISIBLE &&
        engine != null && loadedDirectory == engine?.definition?.assetDirectory

    private fun updateTicker() {
        if (canAnimate() && engine?.nextFrameDelayMs() != null) {
            if (lastFrameTime == 0L) lastFrameTime = SystemClock.uptimeMillis()
            scheduleFrame()
        } else stopTicker()
    }

    private fun scheduleFrame() {
        val delay = engine?.nextFrameDelayMs() ?: return
        if (!framePosted) {
            framePosted = true
            if (delay <= 16L) postOnAnimation(frameCallback) else postOnAnimationDelayed(frameCallback, delay)
        }
    }

    private fun advanceClock() {
        if (lastFrameTime == 0L) return
        val now = SystemClock.uptimeMillis()
        engine?.advance((now - lastFrameTime).coerceAtLeast(0L))
        lastFrameTime = now
    }

    private fun stopTicker() {
        framePosted = false
        lastFrameTime = 0L
        removeCallbacks(frameCallback)
    }
}
