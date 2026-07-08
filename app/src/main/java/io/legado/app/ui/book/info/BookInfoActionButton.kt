package io.legado.app.ui.book.info

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import io.legado.app.lib.theme.Selector
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

class BookInfoActionButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val iconView = ImageView(context).apply {
        layoutParams = LayoutParams(20.dpToPx(), 20.dpToPx())
        scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private val textView = TextView(context).apply {
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = 8.dpToPx()
        }
        includeFontPadding = false
        isSingleLine = true
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        addView(iconView)
        addView(textView)
    }

    fun setActionText(text: CharSequence) {
        textView.text = text
    }

    fun setActionTextSize(sizeSp: Float) {
        textView.textSize = sizeSp
    }

    fun setActionIcon(@DrawableRes iconRes: Int) {
        iconView.setImageResource(iconRes)
    }

    fun applyOutlineStyle(@ColorInt accentColor: Int, @ColorInt backgroundColor: Int) {
        background = Selector.shapeBuild()
            .setCornerRadius(12.dpToPx())
            .setStrokeWidth(1.dpToPx())
            .setDefaultBgColor(backgroundColor)
            .setDefaultStrokeColor(accentColor)
            .setPressedBgColor(ColorUtils.withAlpha(accentColor, 0.14f))
            .create()
        setContentColor(accentColor)
    }

    fun applyFilledStyle(@ColorInt accentColor: Int) {
        background = Selector.shapeBuild()
            .setCornerRadius(12.dpToPx())
            .setDefaultBgColor(accentColor)
            .setPressedBgColor(ColorUtils.darkenColor(accentColor))
            .create()
        setContentColor(if (ColorUtils.isColorLight(accentColor)) Color.BLACK else Color.WHITE)
    }

    private fun setContentColor(@ColorInt color: Int) {
        textView.setTextColor(color)
        iconView.setColorFilter(color)
    }
}
