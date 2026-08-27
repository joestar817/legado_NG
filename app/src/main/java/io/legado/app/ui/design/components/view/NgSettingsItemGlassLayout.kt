package io.legado.app.ui.design.components.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.theme.NgThemeResolver

/**
 * View Preference 使用的视觉体系兼容卡片。
 *
 * 页面提供约定的 [R.id.ng_liquid_glass_backdrop_source] 后，液态体系复用公共
 * RenderNode 后端；透明体系继续绘制 XML 中原有的设置项背景。
 */
class NgSettingsItemGlassLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private val transparentBackgroundState = background?.constantState
    private val transparentBackground = background
    private val liquidRenderer = NgViewLiquidGlassRenderer(this).apply {
        role = NgMaterialRole.SETTINGS
        cornerRadiusPx = 18.dp.toFloat()
        surfaceColor = Color.WHITE
        drawsSurface = true
    }

    init {
        setWillNotDraw(false)
    }

    fun refreshVisualMaterial() {
        val snapshot = NgThemeResolver.resolve(context)
        liquidRenderer.surfaceAlpha = when {
            snapshot.isEInk -> 1f
            snapshot.isDark -> 0.18f
            else -> 0.68f
        }
        syncMaterialMode()
    }

    private fun syncMaterialMode() {
        if (liquidRenderer.sourceView == null && isAttachedToWindow) {
            liquidRenderer.sourceView = rootView.findViewById(
                R.id.ng_liquid_glass_backdrop_source,
            )
        }
        val usesLiquidGlass = liquidRenderer.isEnabled()
        if (usesLiquidGlass && background != null) {
            background = null
        } else if (!usesLiquidGlass && background == null) {
            background = newTransparentBackground()
        }
    }

    override fun draw(canvas: Canvas) {
        syncMaterialMode()
        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        liquidRenderer.draw(canvas)
        super.onDraw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        liquidRenderer.onAttachedToWindow()
        refreshVisualMaterial()
    }

    override fun onDetachedFromWindow() {
        liquidRenderer.onDetachedFromWindow()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility == View.VISIBLE) refreshVisualMaterial()
    }

    private fun newTransparentBackground(): Drawable? =
        transparentBackgroundState?.newDrawable(resources)?.mutate()
            ?: transparentBackground

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density + 0.5f).toInt()
}
