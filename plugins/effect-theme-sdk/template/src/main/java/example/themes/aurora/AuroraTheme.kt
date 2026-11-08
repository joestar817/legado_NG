package example.themes.aurora

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import io.legado.effect.api.*
import kotlin.math.sin

/** A new identity unknown to the App. Rendering does not read textures or packaged images. */
class AuroraTheme : EffectThemePlugin {
    override fun createSession(context: Context, assets: EffectThemeAssets, callbacks: EffectThemeCallbacks): EffectThemeSession = Session(context, callbacks)

    private class Session(context: Context, private val callbacks: EffectThemeCallbacks) : View(context), EffectThemeSession {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var color = Color.rgb(54, 214, 190)
        private var intensity = .6f
        private var animate = false
        private var released = false
        private var ready = false
        private var origin: Long? = null
        private val localOrigin = System.nanoTime()
        override fun getView(): View = this
        override fun update(parameters: Map<String, String>, animationAllowed: Boolean, timelineOriginNanos: Long?) {
            if (released) return
            color = parameters["color"]?.let { runCatching { Color.parseColor(it) }.getOrNull() } ?: Color.rgb(54, 214, 190)
            intensity = ((parameters["intensity"]?.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { kotlin.math.round(it).toInt() } ?: 60).coerceIn(0, 100)) / 100f
            animate = animationAllowed
            origin = timelineOriginNanos
            invalidate()
        }
        override fun onDraw(canvas: Canvas) {
            if (released) return
            val seconds = (System.nanoTime() - (origin ?: localOrigin)) / 1_000_000_000f
            val shift = if (animate) sin(seconds * .3f) * width * .35f else 0f
            canvas.drawColor(Color.rgb(19, 38, 59))
            paint.shader = LinearGradient(shift, 0f, width + shift, height.toFloat(), intArrayOf(Color.TRANSPARENT, color, Color.TRANSPARENT), floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
            paint.alpha = (intensity * 190).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            if (!ready) { ready = true; callbacks.onReady() }
            if (animate && isShown) postInvalidateOnAnimation()
        }
        override fun release(onReleased: Runnable) { released = true; animate = false; onReleased.run() }
    }
}
