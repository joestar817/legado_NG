package io.legado.app.ui.book.search

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.lib.theme.accentColor

class SearchProgressTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatTextView(context, attrs, defStyleAttr) {

    private val bounds = RectF()
    private val clipPath = Path()
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.ng_surface_card)
    }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = context.accentColor
        alpha = 96
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density * 0.8f
        color = ContextCompat.getColor(context, R.color.ng_settings_item_stroke)
    }
    private val radius = resources.getDimension(R.dimen.ng_radius_m)
    private var progressFraction = 0f

    fun setProgress(current: Int, total: Int) {
        progressFraction = if (total > 0) {
            (current.toFloat() / total).coerceIn(0f, 1f)
        } else {
            0f
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) {
            super.onDraw(canvas)
            return
        }
        bounds.set(
            strokePaint.strokeWidth / 2f,
            strokePaint.strokeWidth / 2f,
            width - strokePaint.strokeWidth / 2f,
            height - strokePaint.strokeWidth / 2f
        )
        clipPath.reset()
        clipPath.addRoundRect(bounds, radius, radius, Path.Direction.CW)

        canvas.drawPath(clipPath, backgroundPaint)
        if (progressFraction > 0f) {
            canvas.save()
            canvas.clipPath(clipPath)
            canvas.drawRect(0f, 0f, width * progressFraction, height.toFloat(), progressPaint)
            canvas.restore()
        }
        canvas.drawPath(clipPath, strokePaint)
        super.onDraw(canvas)
    }
}
