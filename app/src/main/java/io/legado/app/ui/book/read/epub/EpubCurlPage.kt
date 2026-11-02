package io.legado.app.ui.book.read.epub

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi

/** Two pages borrow one immutable information texture until their frame owner retires. */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal class EpubCurlPage(
    body: Bitmap,
    private var information: Bitmap?,
    private val width: Int,
    private val height: Int,
    top: Int,
    bottom: Int,
    private val informationOffset: Int?,
) : EpubAnimationFrames.PageDrawing {
    private var bodyShader: BitmapShader? = BitmapShader(body, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private var informationShader: BitmapShader? = BitmapShader(checkNotNull(information), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    private val compact = informationOffset != null
    private var effect: RuntimeShader? = RuntimeShader(if (compact) COMPACT_PROGRAM else PROGRAM).apply {
        setFloatUniform("top", top + .5f)
        setFloatUniform("edge", height - bottom - .5f)
        if (compact) {
            val offset = checkNotNull(informationOffset)
            setFloatUniform("upper", offset.toFloat())
            setFloatUniform("lower", (offset + top + bottom + 2 - height).toFloat())
        }
    }
    private val brush = Paint()
    private var filtering: Boolean? = null

    override fun supportsPaint(paint: Paint?): Boolean = !compact || paint == null

    override fun draw(canvas: Canvas, paint: Paint?) {
        val shader = checkNotNull(effect)
        val filtered = paint?.isFilterBitmap == true
        if (filtering != filtered) {
            val mode = if (filtered) BitmapShader.FILTER_MODE_LINEAR else BitmapShader.FILTER_MODE_NEAREST
            checkNotNull(bodyShader).setFilterMode(mode)
            checkNotNull(informationShader).setFilterMode(mode)
            shader.setInputShader("body", checkNotNull(bodyShader))
            shader.setInputShader("chrome", checkNotNull(informationShader))
            filtering = filtered
        }
        if (paint == null) {
            brush.reset()
            brush.isAntiAlias = false
            brush.isFilterBitmap = false
            brush.isDither = false
        } else brush.set(paint)
        brush.shader = shader
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), brush)
    }

    override fun close() {
        val shader = effect ?: return
        effect = null
        brush.shader = null
        try {
            // Retired after the existing frame-commit fence; release native input references.
            shader.setInputShader("body", empty)
            shader.setInputShader("chrome", empty)
        } finally {
            bodyShader = null
            informationShader = null
            // The frame owner releases the shared bitmap after both page shaders.
            information = null
        }
    }

    companion object {
        private const val COMPACT_PROGRAM = """
            uniform shader body;
            uniform shader chrome;
            uniform float top;
            uniform float edge;
            uniform float upper;
            uniform float lower;
            half4 main(float2 p) {
                if (p.y < top) return chrome.eval(float2(p.x, p.y + upper));
                if (p.y >= edge) return chrome.eval(float2(p.x, p.y + lower));
                return body.eval(p);
            }
        """
        private const val PROGRAM = """
            uniform shader body;
            uniform shader chrome;
            uniform float top;
            uniform float edge;
            half4 main(float2 p) {
                if (p.y < top || p.y >= edge) return chrome.eval(p);
                return body.eval(p);
            }
        """
        private val empty = BitmapShader(Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8),
            Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
}
