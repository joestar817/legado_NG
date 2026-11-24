package io.legado.app.ui.main.chatentry

import io.legado.app.help.pet.rethrowFatalPetError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.R
import io.legado.pet.api.PetActivities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

// A cancelled synchronous decoder may still be allocating until it returns.
private val petPreparation = Mutex()

/** Transparent app-local surface. Coordinates passed across the plugin boundary are viewport dp. */
class AiChatPetView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val density = resources.displayMetrics.density
    private val hitBounds = RectF()
    private val overlayAnchor = Rect()
    private val overlayViewport = Rect()
    private var overlayParked = false
    private val screenLocation = IntArray(2)
    private val loadScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var generation = 0L
    private var binding: PetBinding? = null
    private var hostActive = false
    private var visibleToPlugin: Boolean? = null
    private var menuOpen = false
    private var workActivity: String? = null
    private var lastFrameTime = 0L
    private var insetLeft = 0f
    private var insetTop = 0f
    private var insetRight = 0f
    private var insetBottom = 0f
    private val viewportWidth get() = (width - insetLeft - insetRight) / density
    private val viewportHeight get() = (height - insetTop - insetBottom) / density
    private var pointerId = MotionEvent.INVALID_POINTER_ID
    private var downX = 0f
    private var downY = 0f
    private var maxDragDistance = 0f
    private var lifecycleReady = false

    var onAssetLoadFailed: (() -> Unit)? = null
    internal var onAnchorChanged: ((Rect, Rect, Boolean) -> Unit)? = null
    internal var onDragStarted: (() -> Unit)? = null
    internal var takeCompletion: (() -> Boolean)? = null

    private val frameCallback = Runnable {
        if (visibleToPlugin == true) {
            advanceClock()
            invalidate()
            updateTicker()
        }
    }

    init {
        isClickable = true
        isFocusable = true
        isSaveEnabled = false
        contentDescription = context.getString(R.string.ai_pet_open_menu)
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            if (insetLeft != bars.left.toFloat() || insetTop != bars.top.toFloat() ||
                insetRight != bars.right.toFloat() || insetBottom != bars.bottom.toFloat()) {
                cancelPointer()
                insetLeft = bars.left.toFloat()
                insetTop = bars.top.toFloat()
                insetRight = bars.right.toFloat()
                insetBottom = bars.bottom.toFloat()
                resize()
            }
            insets
        }
        lifecycleReady = true
    }

    internal fun bindPet(value: PetBinding?) {
        if (binding !== value) {
            cancelPointer()
            cancelPreparation()
            pauseSession()
            binding?.onFailure = null
            binding = value
            visibleToPlugin = null
            hitBounds.setEmpty()
            listenForFailure()
            resize()
        }
        syncVisibility()
        invalidate()
    }

    private fun listenForFailure() {
        binding?.onFailure = {
            clearPointer()
            stopTicker()
            hitBounds.setEmpty()
            reportOverlayAnchor()
            invalidate()
            onAssetLoadFailed?.invoke()
        }
    }

    private fun cancelPreparation() {
        generation++
        loadJob?.cancel()
        loadJob = null
    }

    private fun prepare(current: PetBinding) {
        if (current.session != null || current.failed || current.retired || loadJob?.isActive == true) return
        val ticket = generation
        loadJob = loadScope.launch {
            petPreparation.withLock {
                // withContext can discard a completed result when the view detaches during preparation.
                var candidate: LeasedPetSession? = null
                try {
                    withContext(Dispatchers.IO) { candidate = current.openSession() }
                    if (generation == ticket && binding === current && !current.retired && !current.failed) {
                        val ready = requireNotNull(candidate)
                        candidate = null
                        current.attach(ready)
                        resize()
                        syncVisibility()
                        invalidate()
                    }
                } catch (cancelled: CancellationException) {
                    if (!isActive) throw cancelled
                    if (generation == ticket) current.fail("createSession", cancelled)
                } catch (failure: Throwable) {
                    failure.rethrowFatalPetError()
                    if (generation == ticket) current.fail("createSession", failure)
                } finally {
                    candidate?.let(current::closeSession)
                    if (generation == ticket) loadJob = null
                }
            }
        }
    }

    fun setHostActive(active: Boolean) {
        hostActive = active
        syncVisibility()
    }

    internal fun setMenuOpen(open: Boolean) {
        if (menuOpen == open) return
        advanceClock()
        menuOpen = open
        contentDescription = context.getString(if (open) R.string.ai_pet_close_menu else R.string.ai_pet_open_menu)
        binding?.invoke("setPositionHeld", Unit) { it.activity?.setPositionHeld(open) }
        invalidate()
        updateTicker()
    }

    internal fun showWorkActivity(activityId: String?) {
        val changed = activityId != workActivity
        workActivity = activityId
        if (visibleToPlugin != true) return
        if (changed) {
            advanceClock()
            binding?.invoke("setWorkActivity", Unit) { it.activity?.setWorkActivity(activityId) }
        }
        val played = playCompletionIfReady()
        if (changed || played) {
            invalidate()
            updateTicker()
        }
    }

    private fun playCompletionIfReady(): Boolean {
        val current = binding ?: return false
        if (visibleToPlugin != true || current.session?.activity == null || takeCompletion?.invoke() != true) {
            return false
        }
        advanceClock()
        current.invoke("playReaction", Unit) { it.activity?.playReaction(PetActivities.COMPLETED) }
        return true
    }

    fun isTouchOnPet(rawX: Float, rawY: Float): Boolean {
        if (visibleToPlugin != true) return false
        getLocationOnScreen(screenLocation)
        return hitTest(xDp(rawX - screenLocation[0]), yDp(rawY - screenLocation[1]))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
        listenForFailure()
        resize()
        syncVisibility()
    }

    override fun onDetachedFromWindow() {
        cancelPreparation()
        pauseSession()
        binding?.onFailure = null
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (lifecycleReady) syncVisibility()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (lifecycleReady) syncVisibility()
    }

    private fun syncVisibility() {
        val current = binding
        if (!hostActive || !isAttachedToWindow || !isShown || windowVisibility != VISIBLE ||
            current == null || current.retired || current.failed) {
            if (loadJob?.isActive == true) cancelPreparation()
            pauseSession()
            return
        }
        if (current.session == null) {
            prepare(current)
            return
        }
        if (visibleToPlugin != true) {
            visibleToPlugin = true
            current.invoke("setVisible", Unit) { it.setVisible(true) }
            current.invoke("setWorkActivity", Unit) { it.activity?.setWorkActivity(workActivity) }
            playCompletionIfReady()
        }
        updateTicker()
    }

    private fun pauseSession() {
        if (visibleToPlugin == true) {
            visibleToPlugin = false
            advanceClock()
            cancelPointer()
            binding?.invoke("setVisible", Unit) { it.setVisible(false) }
        }
        stopTicker()
        reportOverlayAnchor()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelPointer()
        resize()
    }

    private fun resize() {
        val w = viewportWidth
        val h = viewportHeight
        if (w > 0f && h > 0f) binding?.invoke("resize", Unit) { it.resize(w, h) }
        hitBounds.setEmpty()
        reportOverlayAnchor()
        invalidate()
        updateTicker()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (viewportWidth <= 0f || viewportHeight <= 0f) return
        val parked = binding?.invoke("draw", false) {
            val save = canvas.save()
            try {
                canvas.translate(insetLeft, insetTop)
                canvas.scale(density, density)
                canvas.clipRect(0f, 0f, viewportWidth, viewportHeight)
                it.draw(canvas)
                hitBounds.set(it.hitBounds)
                if (!hitBounds.intersect(0f, 0f, viewportWidth, viewportHeight)) hitBounds.setEmpty()
            } finally {
                canvas.restoreToCount(save)
            }
            pointerId == MotionEvent.INVALID_POINTER_ID && !hitBounds.isEmpty && it.isParked
        } == true
        reportOverlayAnchor(parked)
    }

    private fun reportOverlayAnchor(parked: Boolean = false) {
        val notify = onAnchorChanged ?: return
        if (visibleToPlugin != true || hitBounds.isEmpty) {
            if (!overlayAnchor.isEmpty) {
                overlayAnchor.setEmpty()
                overlayParked = false
                notify(overlayAnchor, overlayViewport, false)
            }
            return
        }
        val viewportLeft = left + insetLeft.roundToInt()
        val viewportTop = top + insetTop.roundToInt()
        val viewportRight = right - insetRight.roundToInt()
        val viewportBottom = bottom - insetBottom.roundToInt()
        val anchorLeft = viewportLeft + (hitBounds.left * density).roundToInt()
        val anchorTop = viewportTop + (hitBounds.top * density).roundToInt()
        val anchorRight = viewportLeft + (hitBounds.right * density).roundToInt()
        val anchorBottom = viewportTop + (hitBounds.bottom * density).roundToInt()
        if (overlayAnchor.left == anchorLeft && overlayAnchor.top == anchorTop &&
            overlayAnchor.right == anchorRight && overlayAnchor.bottom == anchorBottom &&
            overlayViewport.left == viewportLeft && overlayViewport.top == viewportTop &&
            overlayViewport.right == viewportRight && overlayViewport.bottom == viewportBottom &&
            overlayParked == parked
        ) return
        overlayAnchor.set(anchorLeft, anchorTop, anchorRight, anchorBottom)
        overlayViewport.set(viewportLeft, viewportTop, viewportRight, viewportBottom)
        overlayParked = parked
        notify(overlayAnchor, overlayViewport, parked)
    }

    private fun xDp(value: Float) = (value - insetLeft) / density
    private fun yDp(value: Float) = (value - insetTop) / density

    private fun hitTest(x: Float, y: Float) =
        hitBounds.contains(x, y) && binding?.invoke("hitTest", false) { it.hitTest(x, y) } == true

    private fun movePointer(event: MotionEvent, index: Int) {
        val x = event.getX(index)
        val y = event.getY(index)
        val threshold = 6f * density
        val wasDragging = maxDragDistance > threshold
        maxDragDistance = maxOf(maxDragDistance, hypot(x - downX, y - downY))
        val dragging = maxDragDistance > threshold
        if (dragging && !wasDragging) onDragStarted?.invoke()
        binding?.invoke("move", Unit) {
            it.move(xDp(x), yDp(y), event.eventTime, dragging)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val current = binding ?: return false
        if (current.session == null || visibleToPlugin != true) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val x = xDp(event.x)
                val y = yDp(event.y)
                if (!hitTest(x, y)) return false
                advanceClock()
                pointerId = event.getPointerId(0)
                downX = event.x
                downY = event.y
                maxDragDistance = 0f
                current.invoke("press", Unit) { it.press(x, y, event.eventTime) }
                reportOverlayAnchor()
                if (!current.failed) parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return false
                movePointer(event, index)
            }
            MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) { cancelPointer(); return false }
                movePointer(event, index)
                val clicked = current.invoke("release", false) { it.release(xDp(event.getX(index)), yDp(event.getY(index))) }
                clearPointer()
                if (clicked) performClick()
            }
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) cancelPointer()
            MotionEvent.ACTION_CANCEL -> cancelPointer()
            MotionEvent.ACTION_POINTER_DOWN -> return pointerId != MotionEvent.INVALID_POINTER_ID
            else -> return false
        }
        updateTicker()
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        if (binding?.session == null || visibleToPlugin != true) return false
        return super.performClick()
    }

    private fun clearPointer() {
        pointerId = MotionEvent.INVALID_POINTER_ID
        parent?.requestDisallowInterceptTouchEvent(false)
    }

    private fun cancelPointer() {
        if (pointerId == MotionEvent.INVALID_POINTER_ID) return
        clearPointer()
        binding?.invoke("cancel", Unit) { it.cancel() }
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
        if (event.actionMasked != MotionEvent.ACTION_HOVER_EXIT && !hitTest(xDp(event.x), yDp(event.y))) return false
        return super.dispatchHoverEvent(event)
    }

    private fun updateTicker() {
        removeCallbacks(frameCallback)
        // API 2 pets do not signal a position hold, so pause their clock for the open menu.
        val heldByHost = menuOpen && binding?.session?.activity == null
        val delay = if (visibleToPlugin == true && !heldByHost) {
            binding?.invoke("nextFrameDelayMs", null as Long?) { it.nextFrameDelayMs() }
        } else null
        if (delay == null) {
            lastFrameTime = 0L
            return
        }
        if (lastFrameTime == 0L) lastFrameTime = SystemClock.uptimeMillis()
        if (delay <= 16L) postOnAnimation(frameCallback) else postOnAnimationDelayed(frameCallback, delay)
    }

    private fun advanceClock() {
        if (lastFrameTime == 0L) return
        val now = SystemClock.uptimeMillis()
        val delta = (now - lastFrameTime).coerceAtLeast(0L)
        lastFrameTime = now
        binding?.invoke("advance", Unit) { it.advance(delta) }
    }

    private fun stopTicker() {
        removeCallbacks(frameCallback)
        lastFrameTime = 0L
    }
}
