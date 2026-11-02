package io.legado.app.ui.book.read

import android.app.Activity
import android.app.Dialog
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE, application = android.app.Application::class)
@Suppress("DEPRECATION")
class ReadWindowInsetsTest {

    @Test
    fun compactCatalogTemporarilyHidesStatusBarAndRestoresVisibleReaderPolicy() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val host = controller.get().window
            val overlay = Dialog(controller.get()).window!!
            listOf(false, true, false, true, false).forEach { catalogOpen ->
                applyReadSystemBarVisibility(host, false, !catalogOpen, false, true, catalogOpen)
                applyReadSystemBarVisibility(overlay, false, !catalogOpen, false, true, catalogOpen)
                assertEquals(host.decorView.systemUiVisibility, overlay.decorView.systemUiVisibility)
                assertEquals(catalogOpen, overlay.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
                assertTrue(overlay.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
            }
        }
    }

    @Test
    fun closingCompactCatalogPreservesOriginallyHiddenStatusBar() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val window = controller.get().window
            listOf(false, true, false).forEach { catalogOpen ->
                applyReadSystemBarVisibility(window, false, !catalogOpen, true, false, catalogOpen)
                assertTrue(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
                assertFalse(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
            }
        }
    }

    @Test
    fun compactCatalogVisibilityDoesNotResizeTheMeasuredReaderViewport() = withViewport { root, spacer, body ->
        val window = (root.context as Activity).window
        listOf(false, true, true, false, true, false).forEach { catalogOpen ->
            applyReadSystemBarVisibility(window, false, !catalogOpen, false, false, catalogOpen)
            dispatchStatusBar(spacer, !catalogOpen, 24)
            measure(root)
            assertEquals(24 to 1896, body.top to body.height)
            assertEquals(catalogOpen, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
        }
    }

    @Test
    fun hiddenStatusBarStillFollowsExistingMenuVisibility() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val window = controller.get().window
            listOf(true, false, true).forEach { toolBarHide ->
                applyReadSystemBarVisibility(window, false, toolBarHide, true, false)
                assertEquals(toolBarHide, window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN != 0)
                assertFalse(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
            }
        }
    }

    @Test
    fun multiWindowDoesNotForceFullscreenLayoutAndNavigationSettingCanChange() {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val window = controller.get().window
            applyReadSystemBarVisibility(window, true, false, false, true)
            assertFalse(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN != 0)
            applyReadSystemBarVisibility(window, true, false, false, false)
            assertFalse(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION != 0)
            assertFalse(window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_HIDE_NAVIGATION != 0)
        }
    }

    @Test
    fun temporaryStatusBarVisibilityDoesNotChangeMeasuredTextViewport() = withViewport { root, spacer, body ->
        val expected = 24 to 1896
        repeat(3) {
            listOf(true, false, true).forEach { visible ->
                dispatchStatusBar(spacer, visible, 24)
                measure(root)
                assertEquals(expected, body.top to body.height)
            }
        }
    }

    @Test
    fun realConfigurationInsetChangeStillResizesTextViewport() = withViewport { root, spacer, body ->
        dispatchStatusBar(spacer, true, 24)
        measure(root)
        assertEquals(24, body.top)
        dispatchStatusBar(spacer, false, 48)
        measure(root)
        assertEquals(48 to 1872, body.top to body.height)
        dispatchStatusBar(spacer, true, 48)
        measure(root)
        assertEquals(48 to 1872, body.top to body.height)
    }

    @Test
    fun hiddenReaderStatusBarDoesNotReserveSpaceWhenTemporarilyShown() = withViewport { root, spacer, body ->
        spacer.visibility = View.GONE
        listOf(false, true, false).forEach { visible ->
            dispatchStatusBar(spacer, visible, 24)
            measure(root)
            assertEquals(0 to 1920, body.top to body.height)
        }
    }

    @Test
    fun stableStatusBarPaddingPreservesOtherPaddingAndInsetsDispatch() = withViewport { _, spacer, _ ->
        spacer.setPadding(3, 0, 5, 7)
        val insets = statusBarInsets(false, 24)
        val dispatched = ViewCompat.dispatchApplyWindowInsets(spacer, insets)
        assertEquals(listOf(3, 24, 5, 7), listOf(spacer.paddingLeft, spacer.paddingTop, spacer.paddingRight, spacer.paddingBottom))
        assertEquals(insets, dispatched)
    }

    private fun withViewport(test: (LinearLayout, FrameLayout, View) -> Unit) {
        Robolectric.buildActivity(Activity::class.java).setup().use { controller ->
            val context = controller.get()
            val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val spacer = FrameLayout(context).apply { applyReadStatusBarPadding() }
            val body = View(context)
            root.addView(spacer, LinearLayout.LayoutParams(-1, -2))
            root.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
            context.setContentView(root)
            test(root, spacer, body)
        }
    }

    private fun measure(root: View) {
        root.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY))
        root.layout(0, 0, 1080, 1920)
    }

    private fun dispatchStatusBar(view: View, visible: Boolean, height: Int) {
        ViewCompat.dispatchApplyWindowInsets(view, statusBarInsets(visible, height))
    }

    private fun statusBarInsets(visible: Boolean, height: Int): WindowInsetsCompat =
        WindowInsetsCompat.Builder()
            .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, if (visible) height else 0, 0, 0))
            .setInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars(), Insets.of(0, height, 0, 0))
            .setVisible(WindowInsetsCompat.Type.statusBars(), visible)
            .build()
}
