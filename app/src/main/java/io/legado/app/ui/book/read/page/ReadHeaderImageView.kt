package io.legado.app.ui.book.read.page

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.isGone
import com.bumptech.glide.Glide
import io.legado.app.help.glide.ImageLoader

/** A fixed decoration whose dimensions are known before its image is decoded. */
class ReadHeaderImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AppCompatImageView(context, attrs) {

    private var imagePath = ""
    private var sourceWidth = 0
    private var sourceHeight = 0
    private var heightLimit = 0
    private var loadedImage: Triple<String, Int, Int>? = null

    init {
        scaleType = ScaleType.FIT_CENTER
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun updateImage(path: String, imageWidth: Int, imageHeight: Int, enabled: Boolean) {
        val nextPath = path.takeIf { enabled && imageWidth > 0 && imageHeight > 0 }.orEmpty()
        if (imagePath == nextPath && sourceWidth == imageWidth && sourceHeight == imageHeight) return
        imagePath = nextPath
        sourceWidth = imageWidth
        sourceHeight = imageHeight
        loadedImage = null
        Glide.with(context.applicationContext).clear(this)
        setImageDrawable(null)
        isGone = nextPath.isBlank()
        requestLayout()
    }

    /** Called during the parent's measurement; it must not schedule another traversal. */
    internal fun limitHeight(height: Int): Boolean {
        val nextLimit = height.coerceAtLeast(0)
        if (heightLimit == nextLimit) return false
        heightLimit = nextLimit
        forceLayout()
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val availableHeight = if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            heightLimit
        } else {
            minOf(heightLimit, MeasureSpec.getSize(heightMeasureSpec))
        }
        val height = if (imagePath.isBlank()) 0 else readHeaderImageHeight(
            width, sourceWidth, sourceHeight, availableHeight,
        )
        setMeasuredDimension(width, height)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        loadImage()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        loadImage()
    }

    override fun onDetachedFromWindow() {
        Glide.with(context.applicationContext).clear(this)
        loadedImage = null
        super.onDetachedFromWindow()
    }

    private fun loadImage() {
        if (imagePath.isBlank() || width <= 0 || height <= 0 || !isAttachedToWindow) return
        val request = Triple(imagePath, width, height)
        if (request == loadedImage) return
        loadedImage = request
        ImageLoader.load(context.applicationContext, imagePath)
            .override(width, height)
            .fitCenter()
            .dontAnimate()
            // Page turns and EPUB also draw this view into software snapshot canvases.
            .disallowHardwareConfig()
            .into(this)
    }
}
