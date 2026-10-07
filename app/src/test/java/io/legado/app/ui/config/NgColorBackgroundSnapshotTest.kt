package io.legado.app.ui.config

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.ui.design.components.view.NgBackdropSourceLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NgColorBackgroundSnapshotTest {

    @Test
    fun backgroundKeepsWindowCoordinatesAndExcludesForegroundIncludingSurfaces() = withActivity { activity ->
        val root = FrameLayout(activity)
        val source = NgBackdropSourceLayout(activity).apply {
            id = R.id.ng_liquid_glass_backdrop_source
            setBackgroundColor(Color.GREEN)
        }
        root.addView(source, FrameLayout.LayoutParams(30, 30).apply {
            leftMargin = 12
            topMargin = 16
        })
        root.addView(View(activity).apply { setBackgroundColor(Color.RED) })
        root.addView(SurfaceView(activity))
        layout(activity, root)
        activity.window.decorView.background = ColorDrawable(Color.BLUE)

        val bitmap = renderNgColorThemeBackground(activity, WIDTH, HEIGHT)
        try {
            val decorLocation = IntArray(2)
            val sourceLocation = IntArray(2)
            activity.window.decorView.getLocationInWindow(decorLocation)
            source.getLocationInWindow(sourceLocation)
            assertEquals(Color.BLUE, bitmap.getPixel(0, 0))
            assertEquals(
                Color.GREEN,
                bitmap.getPixel(
                    sourceLocation[0] - decorLocation[0] + 10,
                    sourceLocation[1] - decorLocation[1] + 10,
                ),
            )
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun missingBackgroundHostUsesWindowDrawableWithoutDrawingPageContent() = withActivity { activity ->
        val root = FrameLayout(activity).apply { setBackgroundColor(Color.RED) }
        layout(activity, root)
        activity.window.decorView.background = ColorDrawable(Color.BLUE)

        val bitmap = renderNgColorThemeBackground(activity, WIDTH, HEIGHT)
        try {
            assertEquals(Color.BLUE, bitmap.getPixel(WIDTH / 2, HEIGHT / 2))
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun translucentBackgroundIsFlattenedBeforeSampling() = withActivity { activity ->
        layout(activity, FrameLayout(activity).apply { setBackgroundColor(Color.RED) })
        val translucentBlue = 0x800000FF.toInt()
        activity.window.decorView.background = ColorDrawable(translucentBlue)

        val bitmap = renderNgColorThemeBackground(activity, WIDTH, HEIGHT)
        try {
            val pixel = bitmap.getPixel(WIDTH / 2, HEIGHT / 2)
            assertEquals(255, Color.alpha(pixel))
            val expected = ColorUtils.compositeColors(
                translucentBlue, activity.backgroundColor or 0xFF000000.toInt(),
            )
            // Skia's premultiplied blend may round each channel differently by one level.
            assertTrue(abs(Color.red(expected) - Color.red(pixel)) <= 1)
            assertTrue(abs(Color.green(expected) - Color.green(pixel)) <= 1)
            assertTrue(abs(Color.blue(expected) - Color.blue(pixel)) <= 1)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun captureRestoresDrawableBoundsAndDoesNotRecycleTheSharedImage() = withActivity { activity ->
        val sharedBitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.YELLOW)
        }
        val source = NgBackdropSourceLayout(activity).apply {
            id = R.id.ng_liquid_glass_backdrop_source
            addView(ImageView(activity).apply {
                scaleType = ImageView.ScaleType.FIT_XY
                setImageBitmap(sharedBitmap)
            }, FrameLayout.LayoutParams(-1, -1))
        }
        val root = FrameLayout(activity).apply { addView(source, FrameLayout.LayoutParams(-1, -1)) }
        layout(activity, root)
        val drawable = ColorDrawable(Color.BLUE)
        activity.window.decorView.background = drawable
        val originalBounds = Rect(3, 5, 23, 25)
        drawable.bounds = originalBounds
        val backgroundRevision = source.windowBackgroundRevision

        try {
            val bitmap = renderNgColorThemeBackground(activity, WIDTH, HEIGHT)
            try {
                assertEquals(Bitmap.Config.ARGB_8888, bitmap.config)
                assertEquals(Color.YELLOW, bitmap.getPixel(WIDTH / 2, HEIGHT / 2))
                assertNotSame(sharedBitmap, bitmap)
                assertEquals(originalBounds, drawable.bounds)
                assertEquals(backgroundRevision, source.windowBackgroundRevision)
            } finally {
                bitmap.recycle()
            }
            assertFalse(sharedBitmap.isRecycled)
        } finally {
            sharedBitmap.recycle()
        }
    }

    @Test
    fun failedDrawableDrawRestoresBoundsAndRejectsOversizedAllocation() = withActivity { activity ->
        layout(activity, FrameLayout(activity))
        val drawable = object : ColorDrawable(Color.BLUE) {
            override fun draw(canvas: Canvas) = throw IllegalStateException("test draw failure")
        }
        activity.window.decorView.background = drawable
        val originalBounds = Rect(1, 2, 10, 20)
        drawable.bounds = originalBounds

        val failure = assertThrows(IllegalStateException::class.java) {
            renderNgColorThemeBackground(activity, WIDTH, HEIGHT)
        }
        assertEquals("test draw failure", failure.message)
        assertEquals(originalBounds, drawable.bounds)
        assertThrows(IllegalArgumentException::class.java) {
            renderNgColorThemeBackground(activity, 4_000, 4_000)
        }
    }

    private fun layout(activity: Activity, root: View) {
        activity.setContentView(root)
        val decor = activity.window.decorView
        decor.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY),
        )
        decor.layout(0, 0, WIDTH, HEIGHT)
    }

    private fun withActivity(block: (Activity) -> Unit) {
        val controller = Robolectric.buildActivity(Activity::class.java)
        controller.get().setTheme(android.R.style.Theme_Material_Light_NoActionBar)
        controller.setup().visible()
        try {
            block(controller.get())
        } finally {
            controller.pause().stop().destroy()
        }
    }

    private companion object {
        const val WIDTH = 120
        const val HEIGHT = 160
    }
}
