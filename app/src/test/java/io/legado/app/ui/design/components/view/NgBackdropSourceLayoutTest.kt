package io.legado.app.ui.design.components.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NgBackdropSourceLayoutTest {

    @Test
    fun `two hosts share tint invalidation and restore callback in either detach order`() {
        listOf(false, true).forEach { reverse ->
            val fixture = Fixture()
            val firstRevision = fixture.first.windowBackgroundRevision
            val secondRevision = fixture.second.windowBackgroundRevision
            val observer = fixture.background.callback
            repeat(4) {
                assertEquals(firstRevision, fixture.first.windowBackgroundRevision)
                assertEquals(secondRevision, fixture.second.windowBackgroundRevision)
                assertSame(observer, fixture.background.callback)
            }

            fixture.background.setTint(Color.BLUE)

            assertEquals(1, fixture.callback.invalidations)
            assertTrue(fixture.first.windowBackgroundRevision > firstRevision)
            assertTrue(fixture.second.windowBackgroundRevision > secondRevision)
            val detached = if (reverse) fixture.second else fixture.first
            val remaining = if (reverse) fixture.first else fixture.second
            detached.detachForTest()
            assertSame(observer, fixture.background.callback)
            val remainingRevision = remaining.windowBackgroundRevision

            fixture.background.color = Color.RED

            assertEquals(2, fixture.callback.invalidations)
            assertTrue(remaining.windowBackgroundRevision > remainingRevision)
            remaining.detachForTest()
            assertSame(fixture.callback, fixture.background.callback)
        }
    }

    @Test
    fun `temporary bounds are ignored by both hosts but draw invalidation is tracked`() {
        val background = object : ColorDrawable(Color.WHITE) {
            override fun draw(canvas: Canvas) {
                super.draw(canvas)
                invalidateSelf()
            }
        }
        val fixture = Fixture(background)
        val firstRevision = fixture.first.windowBackgroundRevision
        val secondRevision = fixture.second.windowBackgroundRevision

        fixture.first.withUntrackedWindowBackgroundBounds {
            background.setBounds(0, 0, 20, 20)
        }

        assertEquals(firstRevision, fixture.first.windowBackgroundRevision)
        assertEquals(secondRevision, fixture.second.windowBackgroundRevision)
        assertEquals(1, fixture.callback.invalidations)
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        try {
            background.draw(Canvas(bitmap))
        } finally {
            bitmap.recycle()
        }
        assertEquals(2, fixture.callback.invalidations)
        assertTrue(fixture.first.windowBackgroundRevision > firstRevision)
        assertTrue(fixture.second.windowBackgroundRevision > secondRevision)
        fixture.first.detachForTest()
        fixture.second.detachForTest()
    }

    @Test
    fun `replacement drawable gets one observer and restores its own callback`() {
        val fixture = Fixture()
        val firstRevision = fixture.first.windowBackgroundRevision
        val secondRevision = fixture.second.windowBackgroundRevision
        val replacement = ColorDrawable(Color.BLUE)
        val replacementCallback = CountingCallback()
        fixture.root.background = replacement
        replacement.callback = replacementCallback

        assertTrue(fixture.first.windowBackgroundRevision > firstRevision)
        assertTrue(fixture.second.windowBackgroundRevision > secondRevision)
        assertNotSame(replacementCallback, replacement.callback)
        replacement.setTint(Color.RED)
        assertEquals(1, replacementCallback.invalidations)
        assertEquals(0, fixture.callback.invalidations)
        fixture.first.detachForTest()
        fixture.second.detachForTest()
        assertSame(replacementCallback, replacement.callback)
    }

    @Test
    fun `child invalidation structural changes and own background advance content revision`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = TestSource(context)
        val child = View(context)
        var revision = source.contentRevision
        source.addView(child)
        assertTrue(source.contentRevision > revision)
        revision = source.contentRevision
        source.onDescendantInvalidated(child, child)
        assertTrue(source.contentRevision > revision)
        revision = source.contentRevision
        source.setBackgroundColor(Color.BLUE)
        assertTrue(source.contentRevision > revision)
        revision = source.contentRevision
        source.layout(0, 0, 20, 20)
        assertTrue(source.contentRevision > revision)
        revision = source.contentRevision
        source.removeView(child)
        assertTrue(source.contentRevision > revision)
    }

    @Test
    fun `drawing an unchanged source does not advance content revision`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = TestSource(context)
        source.setBackgroundColor(Color.BLUE)
        source.layout(0, 0, 20, 20)
        val bitmap = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            source.draw(canvas)
            val revision = source.contentRevision
            source.draw(canvas)
            source.draw(canvas)
            assertEquals(revision, source.contentRevision)
        } finally {
            bitmap.recycle()
        }
    }

    private class Fixture(val background: ColorDrawable = ColorDrawable(Color.WHITE)) {
        private val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = CountingCallback()
        val root = FrameLayout(context)
        val first = TestSource(context)
        val second = TestSource(context)

        init {
            background.setBounds(0, 0, 10, 10)
            root.background = background
            background.callback = callback
            root.addView(first)
            root.addView(second)
        }
    }

    private class TestSource(context: Context) : NgBackdropSourceLayout(context) {
        fun detachForTest() = onDetachedFromWindow()
    }

    private class CountingCallback : Drawable.Callback {
        var invalidations = 0

        override fun invalidateDrawable(who: Drawable) {
            invalidations++
        }

        override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit

        override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
    }
}
