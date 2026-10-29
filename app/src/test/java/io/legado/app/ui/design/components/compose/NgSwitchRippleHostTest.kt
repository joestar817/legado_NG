package io.legado.app.ui.design.components.compose

import android.app.Activity
import android.app.Application
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.TimeUnit

/**
 * Tests ownership and drawable forwarding, not RenderNode hardware animation.
 * Robolectric 4.16 starts at API 23; API 21 and the reported Huawei/API 29 GPU
 * path still need device coverage in addition to these lifecycle regressions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [23, 29], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class NgSwitchRippleHostTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var root: FrameLayout
    private lateinit var owner: View
    private val hosts = mutableListOf<NgSwitchRippleHost>()
    private val pressedState = intArrayOf(
        android.R.attr.state_enabled,
        android.R.attr.state_checked,
        android.R.attr.state_pressed,
    )

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup()
        root = FrameLayout(activity.get())
        owner = View(activity.get())
        root.addView(owner, FrameLayout.LayoutParams(52, 36))
        activity.get().setContentView(root)
        activity.visible()
        val exactSize = View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY)
        root.measure(exactSize, exactSize)
        root.layout(0, 0, 400, 400)
        assertTrue(owner.isAttachedToWindow)
    }

    @After
    fun tearDown() {
        hosts.forEach { it.dispose() }
        activity.pause().stop().destroy()
    }

    @Test
    fun wrapperKeepsOriginalArtworkAndItsUnboundedDirtyRegion() {
        val original = RecordingDrawable()
        val wrapper = NgSwitchRippleDrawable(original)
        val bounds = Rect(0, 0, 52, 36)
        val hotspotBounds = Rect(24, 8, 44, 28)

        wrapper.bounds = bounds
        wrapper.state = pressedState
        wrapper.setHotspot(37.5f, 14.5f)
        wrapper.setHotspotBounds(
            hotspotBounds.left, hotspotBounds.top, hotspotBounds.right, hotspotBounds.bottom,
        )
        wrapper.alpha = 173
        wrapper.level = 7
        wrapper.layoutDirection = View.LAYOUT_DIRECTION_RTL
        wrapper.draw(Canvas())

        assertSame(original, wrapper.drawable)
        assertEquals(bounds, original.bounds)
        assertArrayEquals(pressedState, original.state)
        assertEquals(37.5f to 14.5f, original.hotspot)
        assertEquals(hotspotBounds, original.hotspotBoundsValue)
        val forwardedHotspot = Rect()
        wrapper.getHotspotBounds(forwardedHotspot)
        assertEquals(hotspotBounds, forwardedHotspot)
        assertEquals(Rect(-13, -17, 65, 53), wrapper.dirtyBounds)
        assertEquals(173, original.alpha)
        assertEquals(7, original.level)
        assertEquals(View.LAYOUT_DIRECTION_RTL, original.layoutDirection)
        assertEquals(1, original.drawCalls)
        assertFalse(wrapper.isProjected)

        wrapper.setVisible(false, false)
        wrapper.jumpToCurrentState()
        assertFalse(original.isVisible)
        assertEquals(1, original.jumpCalls)
        wrapper.release()
    }

    @Test
    fun wrapperForwardsInvalidationAndSchedulingWithTheHostedDrawableIdentity() {
        val original = RecordingDrawable()
        val wrapper = NgSwitchRippleDrawable(original)
        val callback = RecordingCallback()
        val task = Runnable { }
        wrapper.callback = callback

        original.invalidateSelf()
        original.scheduleSelf(task, 1234L)
        original.unscheduleSelf(task)

        assertSame(wrapper, callback.invalidated)
        assertSame(wrapper, callback.scheduledDrawable)
        assertSame(task, callback.scheduledTask)
        assertEquals(1234L, callback.scheduledAt)
        assertSame(wrapper, callback.unscheduledDrawable)
        assertSame(task, callback.unscheduledTask)

        wrapper.release()
        callback.invalidated = null
        original.invalidateSelf()
        assertNull(original.callback)
        assertNull(wrapper.callback)
        assertNull(callback.invalidated)
    }

    @Test
    fun attachedBackgroundOnlyDrawsAtTheComposeCallSiteWithoutAnotherControl() {
        val original = RecordingDrawable()
        val host = attach(original)
        val container = host.parent as ViewGroup
        val exactSize = View.MeasureSpec.makeMeasureSpec(100, View.MeasureSpec.EXACTLY)
        host.measure(exactSize, exactSize)
        container.measure(exactSize, exactSize)

        assertTrue(host.isAttachedToWindow)
        assertSame(original, (host.background as NgSwitchRippleDrawable).drawable)
        assertSame(host.background, original.callback)
        assertSame(host, host.background.callback)
        assertEquals(0, host.measuredWidth)
        assertEquals(0, host.measuredHeight)
        assertEquals(0, container.measuredWidth)
        assertEquals(0, container.measuredHeight)
        assertFalse(host.isClickable)
        assertFalse(host.isFocusable)
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, host.importantForAccessibility)
        assertEquals(
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
            container.importantForAccessibility,
        )

        host.drawRipple(Canvas(), 52, 36, pressedState)
        assertEquals(1, original.drawCalls)
        assertEquals(Rect(0, 0, 52, 36), original.bounds)
        assertArrayEquals(pressedState, original.state)
        root.draw(Canvas())
        assertEquals("The attached container must not draw the ripple a second time", 1, original.drawCalls)
    }

    @Test
    fun detachedHostDoesNotPaintAndReattachingReplaysComposeState() {
        val original = RecordingDrawable()
        val host = attach(original)
        host.drawRipple(Canvas(), 52, 36, pressedState)
        val contentParent = root.parent as ViewGroup
        val rootParams = root.layoutParams
        contentParent.removeView(root)

        assertFalse(host.isAttachedToWindow)
        assertFalse(original.isVisible)
        assertArrayEquals(intArrayOf(), original.state)
        val drawsBeforeDetachedCall = original.drawCalls
        host.drawRipple(Canvas(), 52, 36, pressedState)
        assertEquals(drawsBeforeDetachedCall, original.drawCalls)

        contentParent.addView(root, rootParams)
        assertTrue(host.isAttachedToWindow)
        host.drawRipple(Canvas(), 52, 36, pressedState)
        assertTrue(original.isVisible)
        assertArrayEquals(pressedState, original.state)
        assertEquals(drawsBeforeDetachedCall + 1, original.drawCalls)
    }

    @Test
    fun disposingOneHostKeepsItsSiblingAndLastDisposeRemovesTheContainer() {
        val initialChildCount = root.childCount
        val firstDrawable = RecordingDrawable()
        val secondDrawable = RecordingDrawable()
        var firstInvalidations = 0
        var secondInvalidations = 0
        val first = attach(firstDrawable) { firstInvalidations++ }
        val second = attach(secondDrawable) { secondInvalidations++ }
        val container = first.parent as ViewGroup
        assertSame(container, second.parent)
        assertEquals(initialChildCount + 1, root.childCount)

        first.dispose()
        val firstInvalidationsAfterDispose = firstInvalidations
        firstDrawable.invalidateSelf()
        first.drawRipple(Canvas(), 52, 36, pressedState)
        assertEquals(firstInvalidationsAfterDispose, firstInvalidations)
        assertEquals(0, firstDrawable.drawCalls)
        assertNull(firstDrawable.callback)
        assertNull(first.background)
        assertNull(first.parent)
        assertEquals(1, container.childCount)

        val siblingInvalidationsBefore = secondInvalidations
        secondDrawable.invalidateSelf()
        assertEquals(siblingInvalidationsBefore + 1, secondInvalidations)
        second.drawRipple(Canvas(), 52, 36, pressedState)
        assertEquals(1, secondDrawable.drawCalls)

        second.dispose()
        second.dispose()
        assertNull(secondDrawable.callback)
        assertNull(container.parent)
        assertEquals(initialChildCount, root.childCount)
    }

    @Test
    fun disposingHostCancelsDrawableWorkAlreadyQueuedOnTheWindow() {
        val original = RecordingDrawable()
        val host = attach(original)
        var executions = 0
        val task = Runnable { executions++ }

        original.scheduleSelf(task, SystemClock.uptimeMillis() + 50L)
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertEquals("Control check: attached drawable work must reach the window", 1, executions)

        original.scheduleSelf(task, SystemClock.uptimeMillis() + 50L)
        host.dispose()
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        assertEquals("Disposal must cancel work queued under the background token", 1, executions)
    }

    private fun attach(
        drawable: Drawable,
        onInvalidate: () -> Unit = {},
    ): NgSwitchRippleHost = NgSwitchRippleHost.attach(owner, drawable, onInvalidate).also(hosts::add)

    private class RecordingCallback : Drawable.Callback {
        var invalidated: Drawable? = null
        var scheduledDrawable: Drawable? = null
        var scheduledTask: Runnable? = null
        var scheduledAt = -1L
        var unscheduledDrawable: Drawable? = null
        var unscheduledTask: Runnable? = null

        override fun invalidateDrawable(who: Drawable) { invalidated = who }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) {
            scheduledDrawable = who
            scheduledTask = what
            scheduledAt = `when`
        }

        override fun unscheduleDrawable(who: Drawable, what: Runnable) {
            unscheduledDrawable = who
            unscheduledTask = what
        }
    }

    @Suppress("DEPRECATION")
    private class RecordingDrawable : Drawable() {
        var drawCalls = 0
        var jumpCalls = 0
        var hotspot = 0f to 0f
        val hotspotBoundsValue = Rect()
        private var alphaValue = 255
        private var colorFilterValue: ColorFilter? = null

        override fun draw(canvas: Canvas) { drawCalls++ }
        override fun setAlpha(alpha: Int) { alphaValue = alpha }
        override fun getAlpha(): Int = alphaValue
        override fun setColorFilter(colorFilter: ColorFilter?) { colorFilterValue = colorFilter }
        override fun getColorFilter(): ColorFilter? = colorFilterValue
        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        override fun isStateful(): Boolean = true
        override fun onStateChange(state: IntArray): Boolean = true
        override fun getDirtyBounds(): Rect = Rect(bounds).apply { inset(-13, -17) }
        override fun setHotspot(x: Float, y: Float) { hotspot = x to y }
        override fun setHotspotBounds(left: Int, top: Int, right: Int, bottom: Int) {
            hotspotBoundsValue.set(left, top, right, bottom)
        }
        override fun getHotspotBounds(outRect: Rect) { outRect.set(hotspotBoundsValue) }
        override fun jumpToCurrentState() { jumpCalls++ }
    }
}
