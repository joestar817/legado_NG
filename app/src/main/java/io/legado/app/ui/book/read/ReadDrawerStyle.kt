package io.legado.app.ui.book.read

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.utils.dpToPx
import io.legado.app.utils.windowSize
import splitties.systemservices.windowManager

object ReadDrawerStyle {
    private val topRadius: Float
        get() = 18.dpToPx().toFloat()

    fun applyTopRoundedBackground(view: View, fallbackColor: Int = view.context.bottomBackground) {
        view.background = createTopRoundedBackground(view.context, fallbackColor)
    }

    fun createTopRoundedBackground(context: Context, fallbackColor: Int = context.bottomBackground): Drawable {
        val source = if (!AppConfig.isEInkMode && ThemeConfig.isReadingNgBackgroundTheme()) {
            ThemeConfig.getBgImage(context, context.windowManager.windowSize)
        } else {
            null
        }
        return wrapTopRounded(source ?: ColorDrawable(fallbackColor))
    }

    fun wrapTopRounded(source: Drawable): Drawable {
        return TopRoundedDrawable(source, topRadius)
    }
}

private class TopRoundedDrawable(
    private val source: Drawable,
    private val radius: Float
) : Drawable() {
    private val path = Path()
    private val rect = RectF()

    override fun onBoundsChange(bounds: Rect) {
        rect.set(bounds)
        path.reset()
        path.addRoundRect(
            rect,
            floatArrayOf(radius, radius, radius, radius, 0f, 0f, 0f, 0f),
            Path.Direction.CW
        )
        source.bounds = bounds
    }

    override fun draw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.clipPath(path)
        source.bounds = bounds
        source.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun setAlpha(alpha: Int) {
        source.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        source.colorFilter = colorFilter
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return -1
    }

    override fun getIntrinsicHeight(): Int {
        return -1
    }

    override fun getMinimumWidth(): Int {
        return 0
    }

    override fun getMinimumHeight(): Int {
        return 0
    }
}
