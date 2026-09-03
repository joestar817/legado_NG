package io.legado.app.help.config

import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.Keep
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.PageAnim
import io.legado.app.constant.PreferKey
import io.legado.app.help.DefaultData
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.externalCache
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getFile
import io.legado.app.utils.getMeanColor
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.hexString
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.resizeAndRecycle
import splitties.init.appCtx
import java.io.File
import java.io.OutputStream

/**
 * 阅读界面配置
 */
internal fun resolveBundledReadBackgroundName(backgroundName: String): String = when (backgroundName) {
    "暖色渐变.png" -> "暖色渐变.webp"
    "竹影之韵.png" -> "竹影之韵.webp"
    "灰色雾霭.png" -> "灰色雾霭.webp"
    "秋山书意-日间.png" -> "秋山书意-日间.webp"
    "秋山书意-夜间.png" -> "秋山书意-夜间.webp"
    else -> backgroundName
}

@Suppress("ConstPropertyName")
@Keep
object ReadBookConfig {
    const val configFileName = "readConfig.json"
    const val shareConfigFileName = "shareReadConfig.json"
    const val defaultAutoReadSpeed = 40
    const val defaultAutoReadPageMode = PageAnim.scrollPageAnim
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)
    val shareConfigFilePath = FileUtils.getPath(appCtx.filesDir, shareConfigFileName)
    val configList: ArrayList<Config> = arrayListOf()
    lateinit var shareConfig: Config
    var durConfig
        get() = getConfig(styleSelect)
        set(value) {
            configList[styleSelect] = value
            if (shareLayout) {
                shareConfig = value
            }
        }

    var isComic: Boolean = false
    var bg: Drawable? = null
    var bgMeanColor: Int = 0
    val textColor: Int get() = durConfig.curTextColor()
    val textAccentColor: Int get() = durConfig.curTextAccentColor()
    val resolvedTitleColor: Int get() = config.curTitleColor()
    val textShadowColor: Int get() = config.curShadowColor()
    val tipHeaderColor: Int get() = config.curTipHeaderColor()
    val tipFooterColor: Int get() = config.curTipFooterColor()
    val resolvedUnderlineColor: Int get() = config.curUnderlineColor()
    var isNineBgImg = false

    init {
        initConfigs()
        initShareConfig()
    }

    @Synchronized
    fun getConfig(index: Int): Config {
        if (configList.isEmpty()) {
            resetAll()
        }
        return configList.getOrNull(index) ?: configList[0]
    }

    fun initConfigs() {
        val configFile = File(configFilePath)
        var configs: List<Config>? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                configs = GSON.fromJsonArray<Config>(json).getOrThrow()
            } catch (e: Exception) {
                AppLog.put("读取排版配置文件出错", e)
            }
        }
        (configs ?: DefaultData.readConfigs).let { source ->
            configList.clear()
            source.mapTo(configList) { it.detachedCopy() }
        }
    }

    fun initShareConfig() {
        val configFile = File(shareConfigFilePath)
        var c: Config? = null
        if (configFile.exists()) {
            try {
                val json = configFile.readText()
                c = GSON.fromJsonObject<Config>(json).getOrThrow()
            } catch (e: Exception) {
                e.printOnDebug()
            }
        }
        shareConfig = c?.detachedCopy() ?: configList.lastOrNull()?.detachedCopy() ?: Config()
    }

    fun upBg(width: Int, height: Int) {
        val drawable = durConfig.curBgDrawable(width, height)
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            bgMeanColor = drawable.bitmap.getMeanColor()
        } else if (drawable is ColorDrawable) {
            bgMeanColor = drawable.color
        }
        val tmp = bg
        bg = drawable
        if (tmp is BitmapDrawable) { //太快执行，可能还正在被使用，延时防崩溃
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                tmp.bitmap?.recycle()
            }
        }
    }

    fun save() {
        Coroutine.async {
            synchronized(this) {
                GSON.toJson(configList).let {
                    FileUtils.delete(configFilePath)
                    FileUtils.createFileIfNotExist(configFilePath).writeText(it)
                }
                GSON.toJson(shareConfig).let {
                    FileUtils.delete(shareConfigFilePath)
                    FileUtils.createFileIfNotExist(shareConfigFilePath).writeText(it)
                }
            }
        }
    }

    fun getAllPicBgStr(): ArrayList<String> {
        val list = arrayListOf<String>()
        configList.forEach {
            if (it.bgType == 2) {
                list.add(it.bgStr)
            }
            if (it.bgTypeNight == 2) {
                list.add(it.bgStrNight)
            }
            if (it.bgTypeEInk == 2) {
                list.add(it.bgStrEInk)
            }
        }
        return list
    }

    fun deleteDur(): Boolean {
        if (configList.size <= 1) return false
        val removeIndex = styleSelect.takeIf(configList.indices::contains) ?: 0
        configList.removeAt(removeIndex)
        readStyleSelect = 0
        comicStyleSelect = 0
        return true
    }

    fun clearBgAndCache() {
        val bgs = hashSetOf<String>()
        configList.forEach { config ->
            repeat(3) {
                config.getBgPath(it)?.let { path ->
                    bgs.add(path)
                }
            }
        }
        appCtx.externalFiles.getFile("bg").listFiles()?.forEach {
            if (!bgs.contains(it.absolutePath)) {
                it.delete()
            }
        }
        FileUtils.delete(appCtx.externalCache.getFile("readConfig"))
        val configZipPath = FileUtils.getPath(appCtx.externalCache, "readConfig.zip")
        FileUtils.delete(configZipPath)
    }

    fun hasDefaultForCurrent(): Boolean {
        val index = styleSelect.takeIf(configList.indices::contains) ?: return false
        return defaultConfig(configList[index].name) != null
    }

    fun restoreCurrentDefault(): Boolean {
        val index = styleSelect.takeIf(configList.indices::contains) ?: return false
        val default = defaultConfig(configList[index].name) ?: return false
        configList[index] = default.detachedCopy()
        save()
        return true
    }

    fun restoreAllDefaults(): Boolean {
        val defaults = DefaultData.readConfigs.map { it.detachedCopy() }
        if (defaults.isEmpty()) return false
        configList.clear()
        configList.addAll(defaults)
        readStyleSelect = 0
        comicStyleSelect = 0
        shareConfig = defaults.last().detachedCopy()
        save()
        return true
    }

    private fun defaultConfig(name: String): Config? {
        val defaultName = when (name) {
            "预设1" -> "经典纯白"
            "预设2" -> "暖纸书香"
            else -> name
        }
        return DefaultData.readConfigs.firstOrNull { it.name == defaultName }
    }

    private fun Config.detachedCopy(): Config = copy(
        bgStr = if (bgType == 1) resolveBundledReadBackgroundName(bgStr) else bgStr,
        bgStrNight = if (bgTypeNight == 1) {
            resolveBundledReadBackgroundName(bgStrNight)
        } else {
            bgStrNight
        },
        bgStrEInk = if (bgTypeEInk == 1) {
            resolveBundledReadBackgroundName(bgStrEInk)
        } else {
            bgStrEInk
        },
        highlightRules = ArrayList(highlightRules.map { it.copy() }),
        ngUnknownFields = ngUnknownFields.toMap(),
    )

    private fun resetAll() {
        restoreAllDefaults()
    }

    //配置写入读取
    var readBodyToLh = appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)
    var autoReadSpeed = appCtx.getPrefInt(PreferKey.autoReadSpeed, defaultAutoReadSpeed)
        set(value) {
            field = value
            appCtx.putPrefInt(PreferKey.autoReadSpeed, value)
        }
    var autoReadPageMode = normalizeAutoReadPageMode(
        appCtx.getPrefInt(PreferKey.autoReadPageMode, defaultAutoReadPageMode)
    )
        set(value) {
            field = normalizeAutoReadPageMode(value)
            appCtx.putPrefInt(PreferKey.autoReadPageMode, field)
        }
    var styleSelect: Int
        get() = if (isComic) comicStyleSelect else readStyleSelect
        set(value) {
            if (isComic) {
                comicStyleSelect = value
            } else {
                readStyleSelect = value
            }
        }
    var readStyleSelect = appCtx.getPrefInt(PreferKey.readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.readStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.readStyleSelect, value)
            }
        }
    var comicStyleSelect = appCtx.getPrefInt(PreferKey.comicStyleSelect, readStyleSelect)
        set(value) {
            field = value
            if (appCtx.getPrefInt(PreferKey.comicStyleSelect) != value) {
                appCtx.putPrefInt(PreferKey.comicStyleSelect, value)
            }
        }
    var shareLayout = appCtx.getPrefBoolean(PreferKey.shareLayout, false)
        set(value) {
            field = value
            if (appCtx.getPrefBoolean(PreferKey.shareLayout, false) != value) {
                appCtx.putPrefBoolean(PreferKey.shareLayout, value)
            }
        }

    private fun normalizeAutoReadPageMode(value: Int): Int = when (value) {
        PageAnim.coverPageAnim -> PageAnim.coverPageAnim
        else -> PageAnim.scrollPageAnim
    }
    var isNightTheme = appCtx.getPrefBoolean(PreferKey.readNightTheme, false)
        set(value) {
            field = value
            if (appCtx.getPrefBoolean(PreferKey.readNightTheme, false) != value) {
                appCtx.putPrefBoolean(PreferKey.readNightTheme, value)
            }
        }

    /**
     * 两端对齐
     */
    val textFullJustify get() = appCtx.getPrefBoolean(PreferKey.textFullJustify, true)

    /**
     * 底部对齐
     */
    val textBottomJustify get() = appCtx.getPrefBoolean(PreferKey.textBottomJustify, true)

    var hideStatusBar = appCtx.getPrefBoolean(PreferKey.hideStatusBar)
    var hideNavigationBar = appCtx.getPrefBoolean(PreferKey.hideNavigationBar)
    var useZhLayout = appCtx.getPrefBoolean(PreferKey.useZhLayout)

    val config get() = if (shareLayout) shareConfig else durConfig

    var bgAlpha: Int
        get() = config.bgAlpha
        set(value) {
            config.bgAlpha = value
        }

    var pageAnim: Int
        get() = config.curPageAnim()
        set(@PageAnim.Anim value) {
            config.setCurPageAnim(value)
        }

    var textFont: String
        get() = config.textFont
        set(value) {
            config.textFont = value
        }

    var titleFont: String
        get() = config.titleFont
        set(value) {
            config.titleFont = value
        }

    val headerFont: String get() = config.headerFont
    val footerFont: String get() = config.footerFont
    val headerFontSize: Int get() = config.headerFontSize
    val footerFontSize: Int get() = config.footerFontSize
    val applyHeaderStyle: Boolean get() = config.applyHeaderStyle

    var textBold: Int
        get() = config.textBold
        set(value) {
            config.textBold = value
        }

    var textSize: Int
        get() = config.textSize
        set(value) {
            config.textSize = value
        }

    val textItalic: Boolean get() = config.textItalic
    val textShadow: Boolean get() = config.textShadow
    val shadowRadius: Float get() = config.shadowRadius
    val shadowDx: Float get() = config.shadowDx
    val shadowDy: Float get() = config.shadowDy

    var letterSpacing: Float
        get() = config.letterSpacing
        set(value) {
            config.letterSpacing = value
        }

    var lineSpacingExtra: Int
        get() = config.lineSpacingExtra
        set(value) {
            config.lineSpacingExtra = value
        }

    var paragraphSpacing: Int
        get() = config.paragraphSpacing
        set(value) {
            config.paragraphSpacing = value
        }

    /**
     * 标题位置 0:居左 1:居中 2:隐藏
     */
    var titleMode: Int
        get() = config.titleMode
        set(value) {
            config.titleMode = value
        }
    var titleSize: Int
        get() = config.titleSize
        set(value) {
            config.titleSize = value
        }

    val titleBold: Int get() = config.titleBold
    val titleLineSpacingExtra: Int get() = config.titleLineSpacingExtra
    val titleLineSpacingSub: Int get() = config.titleLineSpacingSub
    val titleSegType: Int get() = config.titleSegType
    val titleSegScaling: Float get() = config.titleSegScaling
    val titleSegDistance: Int get() = config.titleSegDistance
    val titleSegFlag: String get() = config.titleSegFlag

    /**
     * 是否标题居中
     */
    val isMiddleTitle get() = titleMode == 1

    var titleTopSpacing: Int
        get() = config.titleTopSpacing
        set(value) {
            config.titleTopSpacing = value
        }

    var titleBottomSpacing: Int
        get() = config.titleBottomSpacing
        set(value) {
            config.titleBottomSpacing = value
        }

    var paragraphIndent: String
        get() = config.paragraphIndent
        set(value) {
            config.paragraphIndent = value
        }

    var fullLineUnderlineEnabled: Boolean
        get() = config.underline
        set(value) {
            config.underline = value
        }

    var underlinePadding: Int
        get() = config.underlinePadding
        set(value) {
            config.underlinePadding = value
        }

    var underlineHeight: Int
        get() = config.underlineHeight
        set(value) {
            config.underlineHeight = value
        }

    var underlineExtend: Boolean
        get() = config.underlineExtend
        set(value) {
            config.underlineExtend = value
        }

    var dottedLine: Boolean
        get() = config.dottedLine
        set(value) {
            config.dottedLine = value
        }

    var dottedBase: Float
        get() = config.dottedBase
        set(value) {
            config.dottedBase = value
        }

    var dottedRatio: Float
        get() = config.dottedRatio
        set(value) {
            config.dottedRatio = value
        }
    val highlightRules: List<ReadHighlightRule>
        get() = config.highlightRules
            .asSequence()
            .filter(ReadHighlightRule::enabled)
            .sortedBy(ReadHighlightRule::position)
            .toList()

    var paddingBottom: Int
        get() = config.paddingBottom
        set(value) {
            config.paddingBottom = value
        }

    var paddingLeft: Int
        get() = config.paddingLeft
        set(value) {
            config.paddingLeft = value
        }

    var paddingRight: Int
        get() = config.paddingRight
        set(value) {
            config.paddingRight = value
        }

    var paddingTop: Int
        get() = config.paddingTop
        set(value) {
            config.paddingTop = value
        }

    var headerPaddingBottom: Int
        get() = config.headerPaddingBottom
        set(value) {
            config.headerPaddingBottom = value
        }

    var headerPaddingLeft: Int
        get() = config.headerPaddingLeft
        set(value) {
            config.headerPaddingLeft = value
        }

    var headerPaddingRight: Int
        get() = config.headerPaddingRight
        set(value) {
            config.headerPaddingRight = value
        }

    var headerPaddingTop: Int
        get() = config.headerPaddingTop
        set(value) {
            config.headerPaddingTop = value
        }

    var footerPaddingBottom: Int
        get() = config.footerPaddingBottom
        set(value) {
            config.footerPaddingBottom = value
        }

    var footerPaddingLeft: Int
        get() = config.footerPaddingLeft
        set(value) {
            config.footerPaddingLeft = value
        }

    var footerPaddingRight: Int
        get() = config.footerPaddingRight
        set(value) {
            config.footerPaddingRight = value
        }

    var footerPaddingTop: Int
        get() = config.footerPaddingTop
        set(value) {
            config.footerPaddingTop = value
        }

    var showHeaderLine: Boolean
        get() = config.showHeaderLine
        set(value) {
            config.showHeaderLine = value
        }

    var showFooterLine: Boolean
        get() = config.showFooterLine
        set(value) {
            config.showFooterLine = value
        }

    fun getExportConfig(): Config {
        val exportConfig = durConfig.copy()
        if (shareLayout) {
            exportConfig.textFont = shareConfig.textFont
            exportConfig.titleFont = shareConfig.titleFont
            exportConfig.headerFont = shareConfig.headerFont
            exportConfig.footerFont = shareConfig.footerFont
            exportConfig.headerFontSize = shareConfig.headerFontSize
            exportConfig.footerFontSize = shareConfig.footerFontSize
            exportConfig.applyHeaderStyle = shareConfig.applyHeaderStyle
            exportConfig.textBold = shareConfig.textBold
            exportConfig.textSize = shareConfig.textSize
            exportConfig.textItalic = shareConfig.textItalic
            exportConfig.textShadow = shareConfig.textShadow
            exportConfig.shadowRadius = shareConfig.shadowRadius
            exportConfig.shadowDx = shareConfig.shadowDx
            exportConfig.shadowDy = shareConfig.shadowDy
            exportConfig.letterSpacing = shareConfig.letterSpacing
            exportConfig.lineSpacingExtra = shareConfig.lineSpacingExtra
            exportConfig.paragraphSpacing = shareConfig.paragraphSpacing
            exportConfig.titleMode = shareConfig.titleMode
            exportConfig.titleSize = shareConfig.titleSize
            exportConfig.titleTopSpacing = shareConfig.titleTopSpacing
            exportConfig.titleBottomSpacing = shareConfig.titleBottomSpacing
            exportConfig.titleBold = shareConfig.titleBold
            exportConfig.titleLineSpacingExtra = shareConfig.titleLineSpacingExtra
            exportConfig.titleLineSpacingSub = shareConfig.titleLineSpacingSub
            exportConfig.titleSegType = shareConfig.titleSegType
            exportConfig.titleSegScaling = shareConfig.titleSegScaling
            exportConfig.titleSegDistance = shareConfig.titleSegDistance
            exportConfig.titleSegFlag = shareConfig.titleSegFlag
            exportConfig.paragraphIndent = shareConfig.paragraphIndent
            exportConfig.underline = shareConfig.underline
            exportConfig.underlinePadding = shareConfig.underlinePadding
            exportConfig.underlineHeight = shareConfig.underlineHeight
            exportConfig.underlineExtend = shareConfig.underlineExtend
            exportConfig.copyUnderlineColorsFrom(shareConfig)
            exportConfig.dottedLine = shareConfig.dottedLine
            exportConfig.dottedBase = shareConfig.dottedBase
            exportConfig.dottedRatio = shareConfig.dottedRatio
            exportConfig.highlightRules = ArrayList(shareConfig.highlightRules)
            exportConfig.paddingBottom = shareConfig.paddingBottom
            exportConfig.paddingLeft = shareConfig.paddingLeft
            exportConfig.paddingRight = shareConfig.paddingRight
            exportConfig.paddingTop = shareConfig.paddingTop
            exportConfig.headerPaddingBottom = shareConfig.headerPaddingBottom
            exportConfig.headerPaddingLeft = shareConfig.headerPaddingLeft
            exportConfig.headerPaddingRight = shareConfig.headerPaddingRight
            exportConfig.headerPaddingTop = shareConfig.headerPaddingTop
            exportConfig.footerPaddingBottom = shareConfig.footerPaddingBottom
            exportConfig.footerPaddingLeft = shareConfig.footerPaddingLeft
            exportConfig.footerPaddingRight = shareConfig.footerPaddingRight
            exportConfig.footerPaddingTop = shareConfig.footerPaddingTop
            exportConfig.showHeaderLine = shareConfig.showHeaderLine
            exportConfig.showFooterLine = shareConfig.showFooterLine
            exportConfig.tipHeaderLeft = shareConfig.tipHeaderLeft
            exportConfig.tipHeaderMiddle = shareConfig.tipHeaderMiddle
            exportConfig.tipHeaderRight = shareConfig.tipHeaderRight
            exportConfig.tipFooterLeft = shareConfig.tipFooterLeft
            exportConfig.tipFooterMiddle = shareConfig.tipFooterMiddle
            exportConfig.tipFooterRight = shareConfig.tipFooterRight
            exportConfig.tipColor = shareConfig.tipColor
            exportConfig.headerMode = shareConfig.headerMode
            exportConfig.footerMode = shareConfig.footerMode
        }
        return exportConfig
    }

    fun import(byteArray: ByteArray): Config = importWithReport(byteArray).config

    internal fun importWithReport(byteArray: ByteArray): ReadStylePackageManager.ImportResult {
        return ReadStylePackageManager.import(byteArray)
    }

    internal fun exportWithReport(output: OutputStream): ReadStylePackageManager.ExportResult {
        return ReadStylePackageManager.export(getExportConfig(), output)
    }

    internal fun appendImportedConfig(config: Config): Int {
        configList.add(config)
        val index = configList.lastIndex
        readStyleSelect = index
        save()
        return index
    }

    @Keep
    data class Config(
        var name: String = "",
        var bgStr: String = "山水画.jpg",//白天背景
        var bgStrNight: String = "#000000",//夜间背景
        var bgStrEInk: String = "#FFFFFF",//EInk背景
        var bgAlpha: Int = 100,//背景透明度
        var bgType: Int = 1,//白天背景类型 0:颜色, 1:assets图片, 2其它图片
        var bgTypeNight: Int = 0,//夜间背景类型
        var bgTypeEInk: Int = 0,//EInk背景类型
        @SerializedName("readFloatingSeed") var readFloatingSeed: Int = 0,
        @SerializedName("readFloatingSeedNight") var readFloatingSeedNight: Int = 0,
        @SerializedName("readFloatingFollowAppNight")
        var readFloatingFollowAppNight: Boolean? = null,
        @SerializedName("readFloatingTransparency")
        var readFloatingTransparency: Int = ReadFloatingAppearanceConfig.DEFAULT_TRANSPARENCY_PERCENT,
        @SerializedName("readFloatingPrimaryStrength")
        var readFloatingPrimaryStrength: Int = ReadFloatingAppearanceConfig.DEFAULT_PRIMARY_STRENGTH_PERCENT,
        @SerializedName("readFloatingColorStyle")
        var readFloatingColorStyle: ReadFloatingColorStyle = ReadFloatingColorStyle.VIBRANT,
        private var darkStatusIcon: Boolean = true,//白天是否暗色状态栏
        private var darkStatusIconNight: Boolean = false,//晚上是否暗色状态栏
        private var darkStatusIconEInk: Boolean = true,
        private var textColor: String = "#3E3D3B",//白天文字颜色
        private var textColorNight: String = "#ADADAD",//夜间文字颜色
        private var textColorEInk: String = "#000000",
        private var textAccentColor: String = "#E53935",//白天强调文字颜色
        private var textAccentColorNight: String = "#FE4D55",//夜间强调文字颜色
        private var textAccentColorEInk: String = "#000000",
        private var pageAnim: Int = PageAnim.simulationPageAnim,//翻页动画
        private var pageAnimEInk: Int = 4,
        var textFont: String = "",//字体
        @SerializedName("titleFont") var titleFont: String = "",//标题字体
        @SerializedName("headerFont") var headerFont: String = "",//页眉字体
        @SerializedName("footerFont") var footerFont: String = "",//页脚字体
        @SerializedName("headerFontSize") var headerFontSize: Int = 12,
        @SerializedName("footerFontSize") var footerFontSize: Int = 12,
        @SerializedName("applyHeaderStyle") var applyHeaderStyle: Boolean = true,
        var textBold: Int = 0,//是否粗体字 0:正常, 1:粗体, 2:细体
        var textSize: Int = 18,//文字大小
        @SerializedName("textItalic") var textItalic: Boolean = false,
        @SerializedName("textShadow") var textShadow: Boolean = false,
        @SerializedName("shadowRadius") var shadowRadius: Float = 16f,
        @SerializedName("shadowDx") var shadowDx: Float = 1f,
        @SerializedName("shadowDy") var shadowDy: Float = 1f,
        @SerializedName("shadowColor") private var shadowColor: String = "#3E3D3B",
        @SerializedName("shadowColorN") private var shadowColorNight: String = "#3E3D3B",
        var letterSpacing: Float = 0.1f,//字间距
        var lineSpacingExtra: Int = 12,//行间距
        var paragraphSpacing: Int = 2,//段距
        var titleMode: Int = 0,//标题位置 0:居左 1:居中 2:隐藏
        var titleSize: Int = 4,
        var titleTopSpacing: Int = 0,
        var titleBottomSpacing: Int = 0,
        @SerializedName("titleColor") var titleColor: Int = 0,
        @SerializedName("titleColorNight") var titleColorNight: Int = 0,
        @SerializedName("titleBold") var titleBold: Int = 0,
        @SerializedName("titleLineSpacingExtra") var titleLineSpacingExtra: Int = 12,
        @SerializedName("titleLineSpacingSub") var titleLineSpacingSub: Int = 12,
        @SerializedName("titleSegType") var titleSegType: Int = 0,
        @SerializedName("titleSegScaling") var titleSegScaling: Float = 1f,
        @SerializedName("titleSegDistance") var titleSegDistance: Int = 4,
        @SerializedName("titleSegFlag") var titleSegFlag: String = "",
        var paragraphIndent: String = "　　",//段落缩进
        var underlineMode: Int = 0, //下划线
        @SerializedName("underline") var underline: Boolean = false,
        @SerializedName("underlinePadding") var underlinePadding: Int = 10,
        @SerializedName("underlineHeight") var underlineHeight: Int = 1,
        @SerializedName("underlineExtend") var underlineExtend: Boolean = false,
        @SerializedName("underlineColor") var underlineColor: String = "#3E3D3B",
        @SerializedName("underlineColorNight") var underlineColorNight: String = "#ADADAD",
        @SerializedName("dottedLine") var dottedLine: Boolean = false,
        @SerializedName("dottedBase") var dottedBase: Float = 6f,
        @SerializedName("dottedRatio") var dottedRatio: Float = 6f,
        var paddingBottom: Int = 6,
        var paddingLeft: Int = 16,
        var paddingRight: Int = 16,
        var paddingTop: Int = 6,
        var headerPaddingBottom: Int = 0,
        var headerPaddingLeft: Int = 16,
        var headerPaddingRight: Int = 16,
        var headerPaddingTop: Int = 0,
        var footerPaddingBottom: Int = 6,
        var footerPaddingLeft: Int = 16,
        var footerPaddingRight: Int = 16,
        var footerPaddingTop: Int = 6,
        var showHeaderLine: Boolean = false,
        var showFooterLine: Boolean = true,
        var tipHeaderLeft: Int = ReadTipConfig.time,
        var tipHeaderMiddle: Int = ReadTipConfig.none,
        var tipHeaderRight: Int = ReadTipConfig.battery,
        var tipFooterLeft: Int = ReadTipConfig.chapterTitle,
        var tipFooterMiddle: Int = ReadTipConfig.none,
        var tipFooterRight: Int = ReadTipConfig.pageAndTotal,
        var tipColor: Int = 0,
        @SerializedName("tipHeaderColor") var tipHeaderColor: Int = 0,
        @SerializedName("tipHeaderColorNight") var tipHeaderColorNight: Int = 0,
        @SerializedName("tipFooterColor") var tipFooterColor: Int = 0,
        @SerializedName("tipFooterColorNight") var tipFooterColorNight: Int = 0,
        var tipDividerColor: Int = -1,
        var headerMode: Int = 2,
        var footerMode: Int = 0,
        @SerializedName("highlightRules") var highlightRules: ArrayList<ReadHighlightRule> = arrayListOf(),
        @SerializedName("ngReadStyleSource") var ngReadStyleSource: String? = null,
        @SerializedName("ngUnknownFields") var ngUnknownFields: Map<String, String> = emptyMap(),
    ) {

        @Transient
        private var textColorIntEInk = -1

        @Transient
        private var textColorIntNight = -1

        @Transient
        private var textColorInt = -1

        @Transient
        private var initColorInt = false

        private fun initColorInt() {
            textColorIntEInk = textColorEInk.toColorInt()
            textColorIntNight = textColorNight.toColorInt()
            textColorInt = textColor.toColorInt()
            initColorInt = true
        }

        @Transient
        private var textAccentColorIntEInk = -1

        @Transient
        private var textAccentColorIntNight = -1

        @Transient
        private var textAccentColorInt = -1

        @Transient
        private var initAccentColorInt = false

        private fun initAccentColorInt() {
            textAccentColorIntEInk = textAccentColorEInk.toColorInt()
            textAccentColorIntNight = textAccentColorNight.toColorInt()
            textAccentColorInt = textAccentColor.toColorInt()
            initAccentColorInt = true
        }

        fun setCurTextColor(color: Int) {
            when {
                AppConfig.isEInkMode -> {
                    textColorEInk = "#${color.hexString}"
                    textColorIntEInk = color
                }

                ReadBookConfig.isNightTheme -> {
                    textColorNight = "#${color.hexString}"
                    textColorIntNight = color
                }

                else -> {
                    textColor = "#${color.hexString}"
                    textColorInt = color
                }
            }
        }

        fun curTextColor(): Int {
            if (!initColorInt) {
                initColorInt()
            }
            return when {
                AppConfig.isEInkMode -> textColorIntEInk
                ReadBookConfig.isNightTheme -> textColorIntNight
                else -> textColorInt
            }
        }

        fun setCurTextAccentColor(color: Int) {
            when {
                AppConfig.isEInkMode -> {
                    textAccentColorEInk = "#${color.hexString}"
                    textAccentColorIntEInk = color
                }

                ReadBookConfig.isNightTheme -> {
                    textAccentColorNight = "#${color.hexString}"
                    textAccentColorIntNight = color
                }

                else -> {
                    textAccentColor = "#${color.hexString}"
                    textAccentColorInt = color
                }
            }
        }

        fun curTextAccentColor(): Int {
            if (!initAccentColorInt) {
                initAccentColorInt()
            }
            return when {
                AppConfig.isEInkMode -> textAccentColorIntEInk
                ReadBookConfig.isNightTheme -> textAccentColorIntNight
                else -> textAccentColorInt
            }
        }

        fun curShadowColor(): Int {
            return runCatching {
                if (ReadBookConfig.isNightTheme) shadowColorNight.toColorInt()
                else shadowColor.toColorInt()
            }.getOrDefault(curTextColor())
        }

        fun curTitleColor(): Int = when {
            ReadBookConfig.isNightTheme && titleColorNight != 0 -> titleColorNight
            titleColor != 0 -> titleColor
            else -> curTextColor()
        }

        fun curTipHeaderColor(): Int = when {
            ReadBookConfig.isNightTheme && tipHeaderColorNight != 0 -> tipHeaderColorNight
            tipHeaderColor != 0 -> tipHeaderColor
            tipColor != 0 -> tipColor
            else -> curTextColor()
        }

        fun curTipFooterColor(): Int = when {
            ReadBookConfig.isNightTheme && tipFooterColorNight != 0 -> tipFooterColorNight
            tipFooterColor != 0 -> tipFooterColor
            tipColor != 0 -> tipColor
            else -> curTextColor()
        }

        fun curUnderlineColor(): Int = runCatching {
            if (ReadBookConfig.isNightTheme) underlineColorNight.toColorInt()
            else underlineColor.toColorInt()
        }.getOrDefault(curTextColor())

        fun setCurUnderlineColor(color: Int) {
            if (ReadBookConfig.isNightTheme) {
                underlineColorNight = "#${color.hexString}"
            } else {
                underlineColor = "#${color.hexString}"
            }
        }

        fun copyUnderlineColorsFrom(source: Config) {
            underlineColor = source.underlineColor
            underlineColorNight = source.underlineColorNight
        }

        fun curStatusIconDark(): Boolean = !ReadBookConfig.isNightTheme

        fun setCurPageAnim(@PageAnim.Anim anim: Int) {
            when {
                AppConfig.isEInkMode -> pageAnimEInk = anim
                else -> pageAnim = anim
            }
        }

        fun curPageAnim(): Int {
            return when {
                AppConfig.isEInkMode -> pageAnimEInk
                else -> pageAnim
            }
        }

        fun setCurBg(bgType: Int, bg: String) {
            when {
                AppConfig.isEInkMode -> {
                    bgTypeEInk = bgType
                    bgStrEInk = bg
                }

                ReadBookConfig.isNightTheme -> {
                    bgTypeNight = bgType
                    bgStrNight = bg
                }

                else -> {
                    this.bgType = bgType
                    bgStr = bg
                }
            }
        }

        fun setCurReadFloatingSeed(color: Int) {
            val opaqueColor = color or 0xFF000000.toInt()
            if (ReadBookConfig.isNightTheme) {
                readFloatingSeedNight = opaqueColor
                readFloatingFollowAppNight = false
            } else {
                readFloatingSeed = opaqueColor
            }
        }

        fun clearCurReadFloatingSeed() {
            if (ReadBookConfig.isNightTheme) {
                readFloatingSeedNight = 0
                readFloatingFollowAppNight = true
            } else {
                readFloatingSeed = 0
            }
        }

        fun curReadFloatingSeed(): Int = when {
            AppConfig.isEInkMode -> 0
            ReadBookConfig.isNightTheme -> readFloatingSeedNight
            else -> readFloatingSeed
        }

        /**
         * 旧内置预设只验收过日间种子，夜间的 0 不是用户显式选择“跟随应用”。
         * 新选择会写入独立标记；旧数据仅在日夜种子都为空时延续跟随语义。
         */
        fun curReadFloatingFollowsApplication(): Boolean = when {
            AppConfig.isEInkMode -> false
            ReadBookConfig.isNightTheme -> readFloatingSeedNight == 0 &&
                (readFloatingFollowAppNight ?: (readFloatingSeed == 0))
            else -> readFloatingSeed == 0
        }

        fun curReadFloatingTransparency(): Int =
            ReadFloatingAppearanceConfig.normalizePercent(readFloatingTransparency)

        fun curReadFloatingPrimaryStrength(): Int =
            ReadFloatingAppearanceConfig.normalizePercent(readFloatingPrimaryStrength)

        fun curReadFloatingColorStyle(): ReadFloatingColorStyle = readFloatingColorStyle

        fun curBgStr(): String {
            val background = when {
                AppConfig.isEInkMode -> bgStrEInk
                ReadBookConfig.isNightTheme -> bgStrNight
                else -> bgStr
            }
            return if (curBgType() == 1) {
                resolveBundledReadBackgroundName(background)
            } else {
                background
            }
        }

        fun curBgType(): Int {
            return when {
                AppConfig.isEInkMode -> bgTypeEInk
                ReadBookConfig.isNightTheme -> bgTypeNight
                else -> bgType
            }
        }

        fun curBgDrawable(width: Int, height: Int): Drawable {
            val curBgStr = curBgStr()
            isNineBgImg = curBgStr.endsWith(".9.png")
            if (width == 0 || height == 0) {
                return appCtx.getCompatColor(R.color.background).toDrawable()
            }
            var bgDrawable: Drawable? = null
            val resources = appCtx.resources
            try {
                bgDrawable = when (curBgType()) {
                    0 -> curBgStr.toColorInt().toDrawable()
                    1 -> {
                        val path = "bg" + File.separator + curBgStr
                        val bitmap = BitmapUtils.decodeAssetsBitmap(appCtx, path, width, height)
                        bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                    }

                    else -> {
                        val path = curBgStr.let {
                            if (it.contains(File.separator)) it
                            else FileUtils.getPath(appCtx.externalFiles, "bg", it)
                        }
                        if (isNineBgImg) {
                            BitmapUtils.decodeNinePatchDrawable(path)
                        } else {
                            val bitmap = BitmapUtils.decodeBitmap(path, width, height)
                            bitmap?.resizeAndRecycle(width, height)?.toDrawable(resources)
                        }
                    }
                }
            } catch (e: OutOfMemoryError) {
                e.printOnDebug()
            } catch (e: Exception) {
                e.printOnDebug()
            }
            return bgDrawable ?: appCtx.getCompatColor(R.color.background).toDrawable()
        }

        fun getBgPath(bgIndex: Int): String? {
            val bgType = when (bgIndex) {
                0 -> bgType
                1 -> bgTypeNight
                2 -> bgTypeEInk
                else -> error("unknown bgIndex: $bgIndex")
            }
            if (bgType != 2) {
                return null
            }
            val bgStr = when (bgIndex) {
                0 -> bgStr
                1 -> bgStrNight
                2 -> bgStrEInk
                else -> error("unknown bgIndex: $bgIndex")
            }
            val path = if (bgStr.contains(File.separator)) {
                bgStr
            } else {
                FileUtils.getPath(appCtx.externalFiles, "bg", bgStr)
            }
            return path
        }

        fun toMap() = mapOf(
            "name" to name,
            "bgStr" to bgStr,
            "bgStrNight" to bgStrNight,
            "bgStrEInk" to bgStrEInk,
            "bgAlpha" to bgAlpha,
            "bgType" to bgType,
            "bgTypeNight" to bgTypeNight,
            "bgTypeEInk" to bgTypeEInk,
            "darkStatusIcon" to darkStatusIcon,
            "darkStatusIconNight" to darkStatusIconNight,
            "darkStatusIconEInk" to darkStatusIconEInk,
            "textColor" to textColor,
            "textColorNight" to textColorNight,
            "textColorEInk" to textColorEInk,
            "textColorInt" to textColorInt,
            "textColorIntNight" to textColorIntNight,
            "textColorIntEInk" to textColorIntEInk,
            "textAccentColor" to textAccentColor,
            "textAccentColorNight" to textAccentColorNight,
            "textAccentColorEInk" to textAccentColorEInk,
            "textAccentColorInt" to textAccentColorInt,
            "textAccentColorIntNight" to textAccentColorIntNight,
            "textAccentColorIntEInk" to textAccentColorIntEInk,
            "pageAnim" to pageAnim,
            "pageAnimEInk" to pageAnimEInk,
            "textFont" to textFont,
            "textBold" to textBold,
            "textSize" to textSize,
            "letterSpacing" to letterSpacing,
            "lineSpacingExtra" to lineSpacingExtra,
            "paragraphSpacing" to paragraphSpacing,
            "titleMode" to titleMode,
            "titleSize" to titleSize,
            "titleTopSpacing" to titleTopSpacing,
            "titleBottomSpacing" to titleBottomSpacing,
            "paragraphIndent" to paragraphIndent,
            "underlineMode" to underlineMode,
            "paddingBottom" to paddingBottom,
            "paddingLeft" to paddingLeft,
            "paddingRight" to paddingRight,
            "paddingTop" to paddingTop,
            "headerPaddingBottom" to headerPaddingBottom,
            "headerPaddingLeft" to headerPaddingLeft,
            "headerPaddingRight" to headerPaddingRight,
            "headerPaddingTop" to headerPaddingTop,
            "footerPaddingBottom" to footerPaddingBottom,
            "footerPaddingLeft" to footerPaddingLeft,
            "footerPaddingRight" to footerPaddingRight,
            "footerPaddingTop" to footerPaddingTop,
            "showHeaderLine" to showHeaderLine,
            "showFooterLine" to showFooterLine,
            "tipHeaderLeft" to tipHeaderLeft,
            "tipHeaderMiddle" to tipHeaderMiddle,
            "tipHeaderRight" to tipHeaderRight,
            "tipFooterLeft" to tipFooterLeft,
            "tipFooterMiddle" to tipFooterMiddle,
            "tipFooterRight" to tipFooterRight,
            "tipColor" to tipColor,
            "tipDividerColor" to tipDividerColor,
            "headerMode" to headerMode,
            "footerMode" to footerMode
        )

    }

}
