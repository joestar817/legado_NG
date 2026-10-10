package io.legado.app.ui.book.read.epub

import android.app.Activity
import android.app.Application
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.After
import org.junit.Assert.*
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class, manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class EpubReaderGesturesTest {
    private lateinit var activity: ActivityController<Activity>
    private lateinit var view: View
    private lateinit var gestures: EpubReaderGestures
    private val deltas = arrayListOf<Pair<Float, Float>>()
    private var horizontal = false
    private var acceptsScroll = true
    private var allowsFling = true
    private var beforeScroll: (() -> Unit)? = null
    private var taps = 0
    private var swipes = 0
    private var longPresses = 0
    private var extensions = 0
    private var selectionsFinished = 0
    private var scrollsFinished = 0
    private var flingsStarted = 0
    private var flingsStopped = 0
    private var downTime = 0L

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(Activity::class.java).setup()
        view = View(activity.get())
        activity.get().setContentView(view)
        activity.visible()
        view.layout(0, 0, 400, 1000)
        gestures = EpubReaderGestures(
            slop = 8,
            tap = { _, _ -> taps++ },
            longPress = { _, _ -> longPresses++ },
            extend = { _, _ -> extensions++ },
            finishSelection = { selectionsFinished++ },
            swipe = { swipes++ },
            cancelSelection = {},
            scroll = { dx, dy ->
                beforeScroll?.invoke()
                if (acceptsScroll) deltas.add(dx to dy)
                acceptsScroll
            },
            finishScroll = { scrollsFinished++ },
            horizontalScroll = { horizontal },
            canFling = { allowsFling },
            onFlingStart = { flingsStarted++ },
            onFlingStop = { flingsStopped++ },
        )
    }

    @After
    fun tearDown() {
        gestures.cancel()
        activity.pause().stop().destroy()
    }

    @Test
    fun fastUpwardReleaseContinuesAcrossMultipleScreensAndFinishesOnce() {
        flickUp()
        val dragged = deltas.sumOf { it.second.toDouble() }
        advance(12_000)
        val distance = deltas.sumOf { it.second.toDouble() }
        assertTrue("Release should add more than one screen of motion", distance - dragged > 1000)
        assertTrue(deltas.all { it.second >= 0 })
        assertEquals(1, flingsStarted)
        assertEquals(1, flingsStopped)
        assertEquals(1, scrollsFinished)
        assertEquals(0, taps)
        assertEquals(0, swipes)
    }

    @Test
    fun downwardReleaseKeepsTheReverseDirection() {
        touch(MotionEvent.ACTION_DOWN, 200f, 100f)
        repeat(3) { move(200f, 250f + it * 150f) }
        release(200f, 550f)
        val count = deltas.size
        advance(100)
        assertTrue(deltas.size > count)
        assertTrue(deltas.drop(count).all { it.first == 0f && it.second <= 0f })
    }

    @Test
    fun horizontalWritingUsesHorizontalMomentumOnly() {
        horizontal = true
        touch(MotionEvent.ACTION_DOWN, 350f, 400f)
        repeat(3) { move(280f - it * 70f, 400f) }
        release(140f, 400f)
        val count = deltas.size
        advance(100)
        assertTrue(deltas.size > count)
        assertTrue(deltas.drop(count).all { it.first >= 0f && it.second == 0f })
    }

    @Test
    fun pressingAgainStopsMomentumAndPreservesTheNextDrag() {
        flickUp()
        advance(48)
        touch(MotionEvent.ACTION_DOWN, 200f, 100f)
        val count = deltas.size
        advance(200)
        assertEquals(count, deltas.size)
        assertEquals(1, flingsStopped)
        assertEquals(0, scrollsFinished)
        move(200f, 250f)
        move(200f, 400f)
        release(200f, 400f)
        advance(100)
        assertEquals(2, flingsStarted)
        assertTrue(deltas.drop(count).all { it.second <= 0f })
    }

    @Test
    fun cancellationNeverLaunchesMomentumOrCompletesABoundaryTurn() {
        touch(MotionEvent.ACTION_DOWN, 200f, 850f)
        move(200f, 700f)
        move(200f, 550f)
        touch(MotionEvent.ACTION_CANCEL, 200f, 550f)
        val count = deltas.size
        advance(1000)
        assertEquals(count, deltas.size)
        assertEquals(0, flingsStarted)
        assertEquals(0, scrollsFinished)
    }

    @Test
    fun reentrantCancellationDoesNotCompleteTheOldFling() {
        flickUp()
        acceptsScroll = false
        beforeScroll = { gestures.cancel() }
        advance(100)
        assertEquals(1, flingsStopped)
        assertEquals(0, scrollsFinished)
    }

    @Test
    fun unavailableLayoutStopsMomentumAfterTheLastAcceptedFrame() {
        flickUp()
        advance(48)
        allowsFling = false
        val count = deltas.size
        advance(1000)
        assertEquals(count, deltas.size)
        assertEquals(1, flingsStopped)
        assertEquals(1, scrollsFinished)
    }

    @Test
    fun longPressSelectionKeepsItsExistingDragAndFinishCallbacks() {
        touch(MotionEvent.ACTION_DOWN, 200f, 850f)
        advance(650)
        move(200f, 700f)
        touch(MotionEvent.ACTION_UP, 200f, 700f)
        advance(1000)
        assertEquals(1, longPresses)
        assertEquals(1, extensions)
        assertEquals(1, selectionsFinished)
        assertTrue(deltas.isEmpty())
        assertEquals(0, flingsStarted)
    }

    @Test
    fun paginatedSwipeAndNormalTapDoNotStartMomentum() {
        acceptsScroll = false
        flickUp()
        advance(100)
        assertEquals(1, swipes)
        touch(MotionEvent.ACTION_DOWN, 200f, 400f)
        release(200f, 400f)
        assertEquals(1, taps)
        assertEquals(0, flingsStarted)
        assertEquals(0, scrollsFinished)
    }

    private fun flickUp() {
        touch(MotionEvent.ACTION_DOWN, 200f, 850f)
        repeat(3) { move(200f, 700f - it * 150f) }
        release(200f, 400f)
    }

    private fun move(x: Float, y: Float) {
        advance(16)
        touch(MotionEvent.ACTION_MOVE, x, y)
    }

    private fun release(x: Float, y: Float) {
        advance(16)
        touch(MotionEvent.ACTION_UP, x, y)
    }

    private fun touch(action: Int, x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        if (action == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action, x, y, 0)
        try { gestures.onTouch(view, event) } finally { event.recycle() }
    }

    private fun advance(milliseconds: Long) {
        shadowOf(Looper.getMainLooper()).idleFor(milliseconds, TimeUnit.MILLISECONDS)
    }
}
