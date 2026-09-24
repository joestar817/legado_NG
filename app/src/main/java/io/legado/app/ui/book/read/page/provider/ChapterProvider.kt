package io.legado.app.ui.book.read.page.provider

import android.graphics.Paint.FontMetrics
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.os.postDelayed
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.help.book.BookContent
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.page.entities.TextChapter
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.buildMainHandler
import io.legado.app.utils.dpToPx
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.isPad
import io.legado.app.utils.postEvent
import io.legado.app.utils.spToPx
import io.legado.app.utils.textHeight
import kotlinx.coroutines.CoroutineScope
import splitties.init.appCtx
import androidx.core.net.toUri
import java.io.File

/**
 * 解析内容生成章节和页面
 */
@Suppress("DEPRECATION", "ConstPropertyName")
object ChapterProvider {
    //用于图片字的替换
    const val srcReplaceStr = "袮" //▩▣ //这是不应该存在的汉字,会替换为祢，这个字符用来标记
    const val srcReplaceChar = '袮'
    const val srcReplacementChar = '祢'
    //用于评论按钮的替换
    const val reviewStr = "꧁"
    const val reviewChar = '꧁'
    const val indentChar = "　"

    @JvmStatic
    var viewWidth = 0
        private set

    @JvmStatic
    var viewHeight = 0
        private set

    @JvmStatic
    var paddingLeft = 0
        private set

    @JvmStatic
    var paddingTop = 0
        private set

    @JvmStatic
    var paddingRight = 0
        private set

    @JvmStatic
    var paddingBottom = 0
        private set

    @JvmStatic
    var visibleWidth = 0
        private set

    @JvmStatic
    var visibleHeight = 0
        private set

    @JvmStatic
    var visibleRight = 0
        private set

    @JvmStatic
    var visibleBottom = 0
        private set

    @JvmStatic
    var lineSpacingExtra = 0f
        private set

    @JvmStatic
    var titleLineSpacingExtra = 0f
        private set

    @JvmStatic
    var titleLineSpacingSub = 0f
        private set

    @JvmStatic
    var paragraphSpacing = 0
        private set

    @JvmStatic
    var titleTopSpacing = 0
        private set

    @JvmStatic
    var titleBottomSpacing = 0
        private set

    @JvmStatic
    var indentCharWidth = 0f
        private set

    @JvmStatic
    var titlePaintTextHeight = 0f
        private set

    @JvmStatic
    var contentPaintTextHeight = 0f
        private set

    @JvmStatic
    var titlePaintFontMetrics = FontMetrics()

    @JvmStatic
    var contentPaintFontMetrics = FontMetrics()

    @JvmStatic
    var typeface: Typeface? = Typeface.DEFAULT
        private set

    @JvmStatic
    var titlePaint: TextPaint = TextPaint()

    @JvmStatic
    var contentPaint: TextPaint = TextPaint()

    @JvmStatic
    var reviewPaint: TextPaint = TextPaint()

    @JvmStatic
    var doublePage = false
        private set

    @JvmStatic
    var visibleRect = RectF()

    private val handler by lazy {
        buildMainHandler()
    }

    private var upViewSizeRunnable: Runnable? = null

    init {
        upStyle()
    }

    fun getTextChapterAsync(
        scope: CoroutineScope,
        book: Book,
        bookChapter: BookChapter,
        displayTitle: String,
        bookContent: BookContent,
        chapterSize: Int,
    ): TextChapter {

        val textChapter = TextChapter(
            bookChapter,
            bookChapter.index, displayTitle,
            chapterSize,
            bookContent.sameTitleRemoved,
            bookContent.textList,
            bookChapter.isVip,
            bookChapter.isPay,
            bookContent.effectiveReplaceRules
        ).apply {
            contentPositionMap = bookContent.positionMap
            createLayout(scope, book, bookContent)
        }

        return textChapter
    }

    /**
     * 更新样式
     */
    fun upStyle() {
        typeface = getTypeface(ReadBookConfig.textFont)
        getPaints(typeface).let {
            titlePaint = it.first
            contentPaint = it.second
//            reviewPaint.color = contentPaint.color
//            reviewPaint.textSize = contentPaint.textSize * 0.45f
//            reviewPaint.textAlign = Paint.Align.CENTER
        }
        //间距
        lineSpacingExtra = ReadBookConfig.lineSpacingExtra / 10f
        titleLineSpacingExtra = ReadBookConfig.titleLineSpacingExtra / 10f
        titleLineSpacingSub = ReadBookConfig.titleLineSpacingSub / 10f
        paragraphSpacing = ReadBookConfig.paragraphSpacing
        titleTopSpacing = ReadBookConfig.titleTopSpacing.dpToPx()
        titleBottomSpacing = ReadBookConfig.titleBottomSpacing.dpToPx()
        val bodyIndent = ReadBookConfig.paragraphIndent
        indentCharWidth = if (bodyIndent.isNotEmpty()) {
            var indentWidth = StaticLayout.getDesiredWidth(bodyIndent, contentPaint)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                indentWidth += contentPaint.letterSpacing * contentPaint.textSize
            }
            indentWidth / bodyIndent.length
        } else {
            0f
        }
        titlePaintTextHeight = titlePaint.textHeight
        contentPaintTextHeight = contentPaint.textHeight
        titlePaintFontMetrics = titlePaint.fontMetrics
        contentPaintFontMetrics = contentPaint.fontMetrics
        upLayout()
    }

    private fun getTypeface(fontPath: String): Typeface? {
        return kotlin.runCatching {
            when {
                fontPath.startsWith("assets://") -> Typeface.createFromAsset(
                    appCtx.assets,
                    fontPath.removePrefix("assets://"),
                )
                fontPath.isContentScheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                    appCtx.contentResolver
                        .openFileDescriptor(fontPath.toUri(), "r")!!
                        .use {
                            Typeface.Builder(it.fileDescriptor).build()
                        }
                }

                fontPath.isContentScheme() -> {
                    Typeface.createFromFile(RealPathUtil.getPath(appCtx, fontPath.toUri()))
                }

                fontPath.isNotEmpty() -> Typeface.createFromFile(fontPath)
                else -> when (AppConfig.systemTypefaces) {
                    1 -> Typeface.SERIF
                    2 -> Typeface.MONOSPACE
                    else -> Typeface.SANS_SERIF
                }
            }
        }.getOrElse {
            ReadBookConfig.textFont = ""
            ReadBookConfig.save()
            Typeface.SANS_SERIF
        } ?: Typeface.DEFAULT
    }

    private fun getPaints(typeface: Typeface?): Pair<TextPaint, TextPaint> {
        val titleBaseTypeface = loadOptionalTypeface(ReadBookConfig.titleFont) ?: typeface
        val textFont = applyFontWeight(typeface, ReadBookConfig.textBold)
        val titleFont = applyFontWeight(titleBaseTypeface, ReadBookConfig.titleBold)

        //标题
        val tPaint = TextPaint()
        tPaint.color = ReadBookConfig.resolvedTitleColor
        tPaint.letterSpacing = ReadBookConfig.letterSpacing
        tPaint.typeface = titleFont
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ReadBookConfig.titleBold in 100..900) {
            tPaint.setFontVariationSettings("'wght' ${ReadBookConfig.titleBold}")
        }
        tPaint.textSize = with(ReadBookConfig) { textSize + titleSize }.toFloat().spToPx()
        tPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && AppConfig.optimizeRender) {
            tPaint.isLinearText = true
        }
        if (ReadBookConfig.textItalic) tPaint.textSkewX = -0.25f
        applyTextShadow(tPaint)
        //正文
        val cPaint = TextPaint()
        cPaint.color = ReadBookConfig.textColor
        cPaint.letterSpacing = ReadBookConfig.letterSpacing
        cPaint.typeface = textFont
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && ReadBookConfig.textBold in 100..900) {
            cPaint.setFontVariationSettings("'wght' ${ReadBookConfig.textBold}")
        }
        cPaint.textSize = ReadBookConfig.textSize.toFloat().spToPx()
        cPaint.isAntiAlias = true
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && AppConfig.optimizeRender) {
            cPaint.isLinearText = true
        }
        if (ReadBookConfig.textItalic) cPaint.textSkewX = -0.25f
        applyTextShadow(cPaint)
        return Pair(tPaint, cPaint)
    }

    private fun applyFontWeight(typeface: Typeface?, weight: Int): Typeface {
        val resolvedWeight = when (weight) {
            0 -> 400
            1 -> 900
            2 -> 300
            else -> weight.coerceIn(100, 900)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Typeface.create(typeface ?: Typeface.DEFAULT, resolvedWeight, false)
        } else if (resolvedWeight >= 700) {
            Typeface.create(typeface, Typeface.BOLD)
        } else {
            Typeface.create(typeface, Typeface.NORMAL)
        }
    }

    fun loadOptionalTypeface(fontPath: String): Typeface? = runCatching {
        when {
            fontPath.startsWith("assets://") -> Typeface.createFromAsset(
                appCtx.assets,
                fontPath.removePrefix("assets://"),
            )
            fontPath.isContentScheme() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                appCtx.contentResolver.openFileDescriptor(fontPath.toUri(), "r")?.use {
                    Typeface.Builder(it.fileDescriptor).build()
                }
            }
            fontPath.isNotBlank() -> Typeface.createFromFile(File(fontPath))
            else -> null
        }
    }.getOrNull()

    fun resolveStyledTypeface(fontPath: String, fontWeight: Int, italic: Boolean): Typeface? {
        if (fontPath.isBlank() && fontWeight == 400 && !italic) return null
        val base = loadOptionalTypeface(fontPath) ?: if (fontPath.isBlank()) typeface else return null
        val weighted = applyFontWeight(base, fontWeight)
        return if (italic) Typeface.create(weighted, Typeface.ITALIC) else weighted
    }

    private fun applyTextShadow(paint: TextPaint) {
        if (ReadBookConfig.textShadow) {
            paint.setShadowLayer(
                ReadBookConfig.shadowRadius,
                ReadBookConfig.shadowDx,
                ReadBookConfig.shadowDy,
                ReadBookConfig.textShadowColor,
            )
        } else {
            paint.clearShadowLayer()
        }
    }

    /**
     * 更新View尺寸
     */
    fun upViewSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) {
            return
        }
        if (width != viewWidth || height != viewHeight) {
            if (width == viewWidth) {
                upViewSizeRunnable = handler.postDelayed(300) {
                    upViewSizeRunnable = null
                    notifyViewSizeChange(width, height)
                }
            } else {
                notifyViewSizeChange(width, height)
            }
        } else if (upViewSizeRunnable != null) {
            handler.removeCallbacks(upViewSizeRunnable!!)
            upViewSizeRunnable = null
        }
    }

    private fun notifyViewSizeChange(width: Int, height: Int) {
        viewWidth = width
        viewHeight = height
        upLayout()
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }

    /**
     * 更新绘制尺寸
     */
    fun upLayout() {
        when (AppConfig.doublePageHorizontal) {
            "0" -> doublePage = false
            "1" -> doublePage = true
            "2" -> {
                doublePage = (viewWidth > viewHeight)
                        && ReadBook.pageAnim() != 3
            }

            "3" -> {
                doublePage = (viewWidth > viewHeight || appCtx.isPad)
                        && ReadBook.pageAnim() != 3
            }
        }

        if (viewWidth <= 0 || viewHeight <= 0) {
            return
        }

        paddingLeft = ReadBookConfig.paddingLeft.dpToPx()
        paddingTop = ReadBookConfig.paddingTop.dpToPx()
        paddingRight = ReadBookConfig.paddingRight.dpToPx()
        paddingBottom = ReadBookConfig.paddingBottom.dpToPx()
        visibleWidth = if (doublePage) {
            viewWidth / 2 - paddingLeft - paddingRight
        } else {
            viewWidth - paddingLeft - paddingRight
        }
        //留1dp画最后一行下划线
        visibleHeight = viewHeight - paddingTop - paddingBottom
        visibleRight = viewWidth - paddingRight
        visibleBottom = paddingTop + visibleHeight

        if (paddingLeft >= visibleRight || paddingTop >= visibleBottom) {
            AppLog.put("边距设置过大，请重新设置", toast = true)
            setFallbackLayout()
        }

        visibleRect.set( //留余，让溢出时也显示
            paddingLeft.toFloat() - 10,
            paddingTop.toFloat() - 10,
            visibleRight.toFloat() + 10,
            visibleBottom.toFloat() + 10f.dpToPx() //下划线最远10dp
        )

    }

    private fun setFallbackLayout() {
        paddingLeft = 20.dpToPx()
        paddingTop = 5.dpToPx()
        paddingRight = 20.dpToPx()
        paddingBottom = 5.dpToPx()
        visibleWidth = if (doublePage) {
            viewWidth / 2 - paddingLeft - paddingRight
        } else {
            viewWidth - paddingLeft - paddingRight
        }
        //留1dp画最后一行下划线
        visibleHeight = viewHeight - paddingTop - paddingBottom
        visibleRight = viewWidth - paddingRight
        visibleBottom = paddingTop + visibleHeight
    }

}
