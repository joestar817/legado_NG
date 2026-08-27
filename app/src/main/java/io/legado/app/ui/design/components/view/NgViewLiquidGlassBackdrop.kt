/*
 * The rounded-rectangle lens shader used by this renderer is adapted from
 * Kyant0/AndroidLiquidGlass (Backdrop), licensed under Apache License 2.0.
 */
package io.legado.app.ui.design.components.view

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.help.config.NgVisualSystemStore
import io.legado.app.ui.design.components.compose.NgLiquidGlassDefaults
import io.legado.app.ui.design.components.compose.NgMaterialRole
import io.legado.app.ui.design.components.compose.ROUNDED_RECT_REFRACTION_SHADER
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * 为 View/独立 ComposeView 边界提供实时背景采样。
 *
 * [sourceView] 必须是承载面后方的真实内容 View，不能包含 [owner]，避免递归录制。
 */
internal class NgViewLiquidGlassRenderer(
    private val owner: View,
) {

    var sourceView: View? = null
        set(value) {
            if (field === value) return
            detachPreDrawListener()
            field = value
            attachPreDrawListener()
            owner.invalidate()
        }

    var role: NgMaterialRole = NgMaterialRole.NAVIGATION
    var cornerRadiusPx: Float = 0f
    @ColorInt var surfaceColor: Int = Color.TRANSPARENT
    var surfaceAlpha: Float = 0f
    var drawsSurface: Boolean = false

    private var renderNode: RenderNode? = null
    private var refractionShader: RuntimeShader? = null
    private var observedTree: ViewTreeObserver? = null
    private val ownerLocation = IntArray(2)
    private val sourceLocation = IntArray(2)
    private val rootLocation = IntArray(2)
    private val oldDrawableBounds = Rect()
    private val surfacePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clipPath = Path()
    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        owner.invalidate()
        true
    }

    fun onAttachedToWindow() {
        attachPreDrawListener()
    }

    fun onDetachedFromWindow() {
        detachPreDrawListener()
        renderNode = null
        refractionShader = null
    }

    fun isEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || AppConfig.isEInkMode) return false
        if (NgVisualSystemStore.current(owner.context) != NgVisualSystem.LIQUID_GLASS) return false
        val source = sourceView ?: return false
        return source.isAttachedToWindow && !source.containsDescendant(owner)
    }

    fun draw(canvas: Canvas): Boolean {
        if (!isEnabled() || owner.width <= 0 || owner.height <= 0) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        val source = sourceView ?: return false
        drawBackdrop(canvas, source)
        if (drawsSurface) drawSurface(canvas)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun drawBackdrop(canvas: Canvas, source: View) {
        val density = owner.resources.displayMetrics.density
        val spec = NgLiquidGlassDefaults.spec(role)
        val blurPx = spec.blurRadius.value * density
        val refractionHeightPx = spec.refractionHeight.value * density
        val refractionAmountPx = spec.refractionAmount.value * density
        // lens 不按折射位移外扩录制范围，避免把 Dock 外的列表内容折进来。
        val padding = (blurPx - refractionHeightPx).coerceAtLeast(0f)
        val paddingInt = ceil(padding).toInt()
        val paddedWidth = (owner.width + paddingInt * 2).coerceAtLeast(1)
        val paddedHeight = (owner.height + paddingInt * 2).coerceAtLeast(1)
        val node = renderNode ?: RenderNode("NgViewLiquidGlass").also { renderNode = it }
        node.setPosition(-paddingInt, -paddingInt, owner.width + paddingInt, owner.height + paddingInt)

        owner.getLocationInWindow(ownerLocation)
        source.getLocationInWindow(sourceLocation)
        val recordingCanvas = node.beginRecording(paddedWidth, paddedHeight)
        try {
            drawWindowBackground(recordingCanvas, paddingInt)
            recordingCanvas.save()
            recordingCanvas.translate(
                (paddingInt + sourceLocation[0] - ownerLocation[0]).toFloat(),
                (paddingInt + sourceLocation[1] - ownerLocation[1]).toFloat(),
            )
            source.draw(recordingCanvas)
            recordingCanvas.restore()
        } finally {
            node.endRecording()
        }

        node.setRenderEffect(
            createRenderEffect(
                width = owner.width.toFloat(),
                height = owner.height.toFloat(),
                padding = padding,
                cornerRadius = cornerRadiusPx,
                blurRadius = blurPx,
                refractionHeight = refractionHeightPx,
                refractionAmount = refractionAmountPx,
                saturation = spec.saturation,
                depthEffect = spec.depthEffect,
                chromaticAberration = spec.chromaticAberration,
            )
        )
        val saveCount = canvas.save()
        clipPath.rewind()
        clipPath.addRoundRect(
            RectF(
                0f,
                0f,
                owner.width.toFloat(),
                owner.height.toFloat(),
            ),
            cornerRadiusPx,
            cornerRadiusPx,
            Path.Direction.CW,
        )
        canvas.clipPath(clipPath)
        canvas.drawRenderNode(node)
        canvas.restoreToCount(saveCount)
    }

    private fun drawWindowBackground(canvas: Canvas, padding: Int) {
        val root = owner.rootView
        val background = root.background ?: return
        root.getLocationInWindow(rootLocation)
        background.copyBounds(oldDrawableBounds)
        val saveCount = canvas.save()
        canvas.translate(
            (padding + rootLocation[0] - ownerLocation[0]).toFloat(),
            (padding + rootLocation[1] - ownerLocation[1]).toFloat(),
        )
        background.setBounds(0, 0, root.width, root.height)
        background.draw(canvas)
        background.bounds = oldDrawableBounds
        canvas.restoreToCount(saveCount)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun createRenderEffect(
        width: Float,
        height: Float,
        padding: Float,
        cornerRadius: Float,
        blurRadius: Float,
        refractionHeight: Float,
        refractionAmount: Float,
        saturation: Float,
        depthEffect: Float,
        chromaticAberration: Float,
    ): RenderEffect? {
        val colorMatrix = ColorMatrix().apply {
            setSaturation(saturation.coerceAtLeast(0f))
        }
        var effect: RenderEffect? = RenderEffect.createColorFilterEffect(
            ColorMatrixColorFilter(colorMatrix)
        )
        if (blurRadius > 0f) {
            val inputEffect = requireNotNull(effect)
            effect = RenderEffect.createBlurEffect(
                blurRadius,
                blurRadius,
                inputEffect,
                Shader.TileMode.CLAMP,
            )
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            refractionHeight > 0f &&
            refractionAmount > 0f
        ) {
            val shader = refractionShader
                ?: RuntimeShader(ROUNDED_RECT_REFRACTION_SHADER).also {
                    refractionShader = it
                }
            shader.setFloatUniform("size", width, height)
            shader.setFloatUniform("offset", -padding, -padding)
            shader.setFloatUniform(
                "cornerRadii",
                cornerRadius,
                cornerRadius,
                cornerRadius,
                cornerRadius,
            )
            shader.setFloatUniform("refractionHeight", refractionHeight)
            shader.setFloatUniform("refractionAmount", -refractionAmount)
            shader.setFloatUniform("depthEffect", depthEffect.coerceIn(0f, 1f))
            shader.setFloatUniform(
                "chromaticAberration",
                chromaticAberration.coerceIn(0f, 1f),
            )
            val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
            effect = effect?.let { RenderEffect.createChainEffect(lens, it) } ?: lens
        }
        return effect
    }

    private fun drawSurface(canvas: Canvas) {
        val spec = NgLiquidGlassDefaults.spec(role)
        val alpha = (surfaceAlpha * spec.surfaceAlphaScale).coerceIn(0f, 1f)
        surfacePaint.style = Paint.Style.FILL
        surfacePaint.shader = null
        surfacePaint.color = ColorUtils.setAlphaComponent(
            surfaceColor,
            (alpha * 255f).roundToInt(),
        )
        canvas.drawRoundRect(
            0f,
            0f,
            owner.width.toFloat(),
            owner.height.toFloat(),
            cornerRadiusPx,
            cornerRadiusPx,
            surfacePaint,
        )

        val highlightAlpha = (150f * spec.highlightAlphaScale).roundToInt()
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = spec.highlightWidth.value * owner.resources.displayMetrics.density
        highlightPaint.shader = LinearGradient(
            0f,
            0f,
            owner.width.toFloat(),
            owner.height.toFloat(),
            intArrayOf(
                ColorUtils.setAlphaComponent(Color.WHITE, highlightAlpha),
                ColorUtils.setAlphaComponent(Color.WHITE, highlightAlpha / 3),
                Color.TRANSPARENT,
            ),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP,
        )
        val inset = highlightPaint.strokeWidth / 2f
        canvas.drawRoundRect(
            inset,
            inset,
            owner.width - inset,
            owner.height - inset,
            (cornerRadiusPx - inset).coerceAtLeast(0f),
            (cornerRadiusPx - inset).coerceAtLeast(0f),
            highlightPaint,
        )
        highlightPaint.shader = null
    }

    private fun attachPreDrawListener() {
        if (!owner.isAttachedToWindow || observedTree?.isAlive == true) return
        val tree = sourceView?.rootView?.viewTreeObserver ?: return
        tree.addOnPreDrawListener(preDrawListener)
        observedTree = tree
    }

    private fun detachPreDrawListener() {
        observedTree?.takeIf(ViewTreeObserver::isAlive)
            ?.removeOnPreDrawListener(preDrawListener)
        observedTree = null
    }

    private fun View.containsDescendant(candidate: View): Boolean {
        var current = candidate.parent
        while (current is View) {
            if (current === this) return true
            current = current.parent
        }
        return false
    }
}

internal class NgViewLiquidGlassBackdropView(context: android.content.Context) : View(context) {

    val renderer = NgViewLiquidGlassRenderer(this)

    init {
        setWillNotDraw(false)
    }

    override fun onDraw(canvas: Canvas) {
        renderer.draw(canvas)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        renderer.onAttachedToWindow()
    }

    override fun onDetachedFromWindow() {
        renderer.onDetachedFromWindow()
        super.onDetachedFromWindow()
    }
}
