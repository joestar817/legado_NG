package io.legado.app.ui.config

import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.legado.app.BuildConfig
import java.util.concurrent.atomic.AtomicInteger

/** Observes the client-side window frame. Surface animation transforms are applied separately. */
internal class AiScaleDialogTrace private constructor(
    private val name: String,
    private val window: Window,
    private val content: View,
) : View.OnAttachStateChangeListener {
    private val id = ids.incrementAndGet()
    private val startedAt = SystemClock.uptimeMillis()
    private val decor = window.decorView
    private val position = IntArray(2)
    private val visibleFrame = Rect()
    private var observer: ViewTreeObserver? = null
    private var previousState: String? = null
    private var waitingForFirstDraw = true
    private var positionSamples = 0

    private val layout = ViewTreeObserver.OnGlobalLayoutListener {
        snapshot("layout", onlyChanges = true)
    }
    private val draw = ViewTreeObserver.OnPreDrawListener {
        if (waitingForFirstDraw) {
            waitingForFirstDraw = false
            snapshot("first_pre_draw")
            if (Build.VERSION.SDK_INT >= 29 && decor.isHardwareAccelerated) {
                decor.viewTreeObserver.registerFrameCommitCallback {
                    log("first_frame_committed")
                }
            }
        } else {
            snapshot("pre_draw", onlyChanges = true)
        }
        true
    }
    private val focus = ViewTreeObserver.OnWindowFocusChangeListener {
        snapshot("focus_changed", onlyChanges = true)
    }
    // A window move can update ViewRoot's screen origin without triggering a draw.
    private val pollPosition = object : Runnable {
        override fun run() {
            positionSamples++
            snapshot("position_poll", onlyChanges = true)
            decor.postDelayed(this, POLL_MS)
        }
    }
    private val finish = Runnable {
        snapshot("sampling_ended")
        stopSampling()
    }

    init {
        decor.addOnAttachStateChangeListener(this)
    }

    override fun onViewAttachedToWindow(v: View) {
        snapshot("attached")
        observer = decor.viewTreeObserver.also {
            it.addOnGlobalLayoutListener(layout)
            it.addOnPreDrawListener(draw)
            it.addOnWindowFocusChangeListener(focus)
        }
        decor.post(pollPosition)
        decor.postDelayed(finish, SAMPLE_MS)
    }

    override fun onViewDetachedFromWindow(v: View) {
        snapshot("detached")
        stopSampling()
        decor.removeOnAttachStateChangeListener(this)
    }

    private fun stopSampling() {
        decor.removeCallbacks(pollPosition)
        decor.removeCallbacks(finish)
        observer?.takeIf { it.isAlive }?.let {
            it.removeOnGlobalLayoutListener(layout)
            it.removeOnPreDrawListener(draw)
            it.removeOnWindowFocusChangeListener(focus)
        }
        observer = null
    }

    fun snapshot(event: String, onlyChanges: Boolean = false) {
        val params = window.attributes
        val state = buildString {
            append("attrs=${params.width}x${params.height}@${params.x},${params.y}")
            append(" gravity=${params.gravity} anim=0x${params.windowAnimations.toString(16)}")
            append(" attached=${decor.isAttachedToWindow} focus=${decor.hasWindowFocus()}")
            append(" decor=${bounds(decor)} content=${bounds(content)}")
            if (decor.isAttachedToWindow) {
                decor.getWindowVisibleDisplayFrame(visibleFrame)
                append(" visibleFrame=${visibleFrame.toShortString()}")
                val insets = ViewCompat.getRootWindowInsets(decor)
                if (insets != null) {
                    val nav = WindowInsetsCompat.Type.navigationBars()
                    val status = WindowInsetsCompat.Type.statusBars()
                    val ime = WindowInsetsCompat.Type.ime()
                    append(" nav=${insets.isVisible(nav)}/${insets.getInsets(nav).bottom}")
                    append(" status=${insets.isVisible(status)}/${insets.getInsets(status).top}")
                    append(" ime=${insets.isVisible(ime)}/${insets.getInsets(ime).bottom}")
                }
            }
        }
        if (onlyChanges && state == previousState) return
        previousState = state
        val counts = if (event == "sampling_ended" || event == "detached") {
            " positionSamples=$positionSamples"
        } else {
            ""
        }
        log(event, state + counts)
    }

    private fun bounds(view: View): String {
        view.getLocationOnScreen(position)
        val screen = "${position[0]},${position[1]}"
        view.getLocationInWindow(position)
        return "${view.width}x${view.height}@$screen/win=${position[0]},${position[1]}"
    }

    private fun log(event: String, state: String = "") {
        val now = SystemClock.uptimeMillis()
        Log.d(TAG, "id=$id type=$name event=$event uptimeMs=$now sinceOpenMs=${now - startedAt} $state")
    }

    companion object {
        private const val TAG = "AiScaleDialogTrace"
        private const val SAMPLE_MS = 2_000L
        private const val POLL_MS = 24L
        private val ids = AtomicInteger()

        fun start(name: String?, window: Window?, content: View): AiScaleDialogTrace? {
            if (!BuildConfig.DEBUG || name == null || window == null || !Log.isLoggable(TAG, Log.DEBUG)) {
                return null
            }
            return AiScaleDialogTrace(name, window, content)
        }
    }
}
