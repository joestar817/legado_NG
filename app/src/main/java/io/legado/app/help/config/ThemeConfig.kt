package io.legado.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.Keep
import androidx.annotation.WorkerThread
import androidx.appcompat.app.AppCompatDelegate
import com.google.gson.annotations.SerializedName
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.BookCover
import io.legado.app.ui.design.theme.NgThemeGradientDrawable
import io.legado.app.ui.design.theme.NgMulticolorLightFieldDrawable
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.hexString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.postEvent
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.stackBlur
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.io.File
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.getPrefBoolean
import java.io.FileOutputStream

internal fun isEffectiveNightMode(mode: Int, systemNightMode: Boolean): Boolean {
    return when (mode) {
        AppCompatDelegate.MODE_NIGHT_YES -> true
        AppCompatDelegate.MODE_NIGHT_NO -> false
        else -> systemNightMode
    }
}

internal data class ThemeModeTransition(
    val isNightTheme: Boolean,
    val nightModeChanged: Boolean
)

internal fun resolveThemeModeTransition(
    themeMode: String,
    systemNightMode: Boolean,
    currentDelegateMode: Int,
    currentConfigurationNightMode: Boolean
): ThemeModeTransition {
    val isNightTheme = resolveThemeNightMode(themeMode, systemNightMode)
    return ThemeModeTransition(
        isNightTheme = isNightTheme,
        nightModeChanged = isEffectiveNightMode(
            currentDelegateMode,
            currentConfigurationNightMode
        ) != isNightTheme
    )
}

internal fun resolveBundledBackgroundAssetPath(assetPath: String): String = when (assetPath) {
    "defaultData/theme/reading_ng_warm.png",
    "bg/暖色渐变.png" -> "bg/暖色渐变.webp"
    "defaultData/theme/reading_ng_bamboo.png",
    "bg/竹影之韵.png" -> "bg/竹影之韵.webp"
    "defaultData/theme/reading_ng_mist.png",
    "bg/灰色雾霭.png" -> "bg/灰色雾霭.webp"
    else -> assetPath
}

@Keep
object ThemeConfig {
    private const val ASSET_BACKGROUND_PREFIX = "asset://"
    private const val EFFECT_THEME_BACKGROUND_CACHE_KEY = "ngDynamicSceneBackground"
    private const val THEME_MODE_FOLLOW_SYSTEM = "0"
    private const val THEME_MODE_DARK = "2"
    private const val THEME_MODE_EINK = "3"
    private data class BackgroundBitmapCacheKey(
        val path: String,
        val lastModified: Long,
        val width: Int,
        val height: Int,
        val blur: Int,
    )

    private data class BackgroundBitmapCache(
        val key: BackgroundBitmapCacheKey,
        val bitmap: Bitmap,
    )

    @Volatile
    private var backgroundBitmapCache: BackgroundBitmapCache? = null

    private fun resolveTheme(isNightTheme: Boolean) = when {
        AppConfig.isEInkMode -> Theme.EInk
        isNightTheme -> Theme.Dark
        else -> Theme.Light
    }

    fun getTheme(context: Context): Theme {
        val isNightTheme = resolveThemeNightMode(
            AppConfig.themeMode,
            context.resources.configuration
        )
        return resolveTheme(isNightTheme)
    }

    fun isReadingNgBackgroundTheme(context: Context): Boolean {
        if (AppConfig.isEInkMode) return false
        if (NgThemeModeStore.current(context) != NgThemePresentationMode.STANDARD) {
            return false
        }
        val backgroundKey = if (getTheme(context) == Theme.Dark) {
            PreferKey.bgImageN
        } else {
            PreferKey.bgImage
        }
        return !context.getPrefString(backgroundKey).isNullOrBlank()
    }

    fun getGradientBgImage(context: Context): Drawable? = when (NgThemeModeStore.current(context)) {
        NgThemePresentationMode.SOFT_GRADIENT ->
            NgThemeGradientDrawable(NgSoftGradientTheme.gradient(context))
        NgThemePresentationMode.MULTICOLOR -> NgMulticolorLightFieldDrawable()
        else -> null
    }

    fun getReadingNgImageSurfaceColor(context: Context): Int {
        return if (getTheme(context) == Theme.Dark) {
            context.getPrefInt(
                PreferKey.cNBBackground,
                context.getCompatColor(R.color.md_grey_850)
            )
        } else {
            context.getPrefInt(
                PreferKey.cBBackground,
                context.getCompatColor(R.color.md_grey_200)
            )
        }
    }

    fun applyThemeMode(context: Context, themeMode: String) {
        // Activity 的 Configuration 仍可能受切换前的强制日／夜模式覆盖。
        // 选择“跟随”时必须先以 Application 的实时系统模式解析目标配色。
        val systemNightMode = AppConfig.isSystemNightTheme
        val normalizedMode = normalizeThemeMode(themeMode)
        AppConfig.themeMode = normalizedMode
        AppConfig.isEInkMode = normalizedMode == THEME_MODE_EINK
        context.putPrefString(PreferKey.themeMode, normalizedMode)
        applyDayNight(context, systemNightMode)
    }

    fun isDarkTheme(context: Context): Boolean {
        return getTheme(context) == Theme.Dark
    }

    fun applyDayNight(context: Context) {
        val configuration = context.resources.configuration
        applyDayNight(context, configuration.isNightMode)
    }

    private fun applyDayNight(context: Context, systemNightMode: Boolean) {
        val configuration = context.resources.configuration
        val transition = resolveThemeModeTransition(
            themeMode = AppConfig.themeMode,
            systemNightMode = systemNightMode,
            currentDelegateMode = AppCompatDelegate.getDefaultNightMode(),
            currentConfigurationNightMode = configuration.isNightMode
        )
        applyTheme(context, transition.isNightTheme)
        initNightMode()
        BookCover.upDefaultCover()
        if (!transition.nightModeChanged) {
            postEvent(EventBus.RECREATE, "")
        }
    }

    fun onSystemUiModeChanged(context: Context, systemNightMode: Boolean) {
        val isNightTheme = resolveThemeNightMode(AppConfig.themeMode, systemNightMode)
        applyTheme(context, isNightTheme)
        BookCover.upDefaultCover(isNightTheme)
    }

    fun applyDayNightInit(context: Context) {
        applyTheme(context)
        initNightMode()
    }

    private fun initNightMode() {
        val targetMode = when (AppConfig.themeMode) {
            THEME_MODE_FOLLOW_SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            THEME_MODE_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_NO
        }
        AppCompatDelegate.setDefaultNightMode(targetMode)
    }

    /**
     * 获取链接获取图片文件名
     */
    private fun getUrlToFile(url: String): String {
        val suffix = when {
            url.contains(".9.png", ignoreCase = true) -> ".9.png"
            url.contains(".png", ignoreCase = true) -> ".png"
            url.contains(".gif", ignoreCase = true) -> ".gif"
            url.contains("webp", ignoreCase = true) -> ".webp"
            else -> ".jpg"
        }
        return MD5Utils.md5Encode16(url) + suffix
    }

    private fun getAssetToFile(assetPath: String): String {
        val suffix = when {
            assetPath.contains(".9.png", ignoreCase = true) -> ".9.png"
            assetPath.endsWith(".png", ignoreCase = true) -> ".png"
            assetPath.endsWith(".gif", ignoreCase = true) -> ".gif"
            assetPath.endsWith(".webp", ignoreCase = true) -> ".webp"
            assetPath.endsWith(".jpg", ignoreCase = true) -> ".jpg"
            assetPath.endsWith(".jpeg", ignoreCase = true) -> ".jpg"
            else -> ".png"
        }
        return MD5Utils.md5Encode16(assetPath) + suffix
    }

    private fun cachedBackgroundPath(context: Context, path: String, preferenceKey: String): String {
        val fileRoot = context.externalFiles
        return when {
            path.startsWith("http") -> {
                FileUtils.getPath(fileRoot, preferenceKey, getUrlToFile(path))
            }

            path.startsWith(ASSET_BACKGROUND_PREFIX) -> {
                val assetPath = path.removePrefix(ASSET_BACKGROUND_PREFIX)
                FileUtils.getPath(fileRoot, preferenceKey, getAssetToFile(assetPath))
            }

            else -> path
        }
    }

    private fun copyAssetBackgroundIfNeed(
        context: Context,
        preferenceKey: String,
        backgroundPath: String,
        forceRefresh: Boolean = false,
    ): String {
        val assetPath = resolveBundledBackgroundAssetPath(
            backgroundPath.removePrefix(ASSET_BACKGROUND_PREFIX)
        )
        val canonicalBackgroundPath = "$ASSET_BACKGROUND_PREFIX$assetPath"
        val filePath = cachedBackgroundPath(context, canonicalBackgroundPath, preferenceKey)
        val file = File(filePath)
        if (forceRefresh || !file.exists() || file.length() == 0L) {
            FileUtils.createFileIfNotExist(filePath)
            context.assets.open(assetPath).use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
        }
        return file.absolutePath
    }

    /** Resolve the current wallpaper for bounded async consumers, without decoding/copying it. */
    @WorkerThread
    internal fun drawerBackgroundImagePath(context: Context, isDark: Boolean): String? {
        val mode = NgThemeModeStore.current(context)
        if (
            mode == NgThemePresentationMode.SOFT_GRADIENT ||
            mode == NgThemePresentationMode.MULTICOLOR ||
            mode == NgThemePresentationMode.EINK
        ) return null
        val preferenceKey = if (isDark) PreferKey.bgImageN else PreferKey.bgImage
        val path = if (mode == NgThemePresentationMode.EFFECT_THEME) {
            val theme = NgEffectThemeSelection.current(context)
            (if (isDark) theme?.darkBackground else theme?.lightBackground)?.path
        } else context.getPrefString(preferenceKey)
        if (path.isNullOrBlank()) return null
        return when {
            path.startsWith(ASSET_BACKGROUND_PREFIX) -> "file:///android_asset/" +
                resolveBundledBackgroundAssetPath(path.removePrefix(ASSET_BACKGROUND_PREFIX))
            path.startsWith("http") -> cachedBackgroundPath(context, path, preferenceKey)
                .takeIf { File(it).isFile }
            else -> path
        }
    }

    @Synchronized
    fun getBgImage(context: Context, metrics: DisplayMetrics): Drawable? {
        val presentationMode = NgThemeModeStore.current(context)
        if (
            presentationMode == NgThemePresentationMode.SOFT_GRADIENT ||
            presentationMode == NgThemePresentationMode.MULTICOLOR
        ) {
            return null
        }
        val themeMode = getTheme(context)
        val preferenceKey = when (themeMode) {
            Theme.Light -> PreferKey.bgImage
            Theme.Dark -> PreferKey.bgImageN
            else -> return  null
        }
        val sceneBackground = if (
            presentationMode == NgThemePresentationMode.EFFECT_THEME
        ) {
            val theme = NgEffectThemeSelection.current(context)
            if (themeMode == Theme.Dark) theme?.darkBackground else theme?.lightBackground
        } else {
            null
        }
        var path = sceneBackground?.path ?: context.getPrefString(preferenceKey)
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http")) {
            val name = getUrlToFile(path)
            val fileRoot = context.externalFiles
            val filePath = FileUtils.getPath(fileRoot, preferenceKey, name)
            if (!FileUtils.exist(filePath)) {
                appCtx.toastOnUi("未缓存在线背景图\n请重新应用主题")
                return null
            }
            path = filePath
        }
        if (path.startsWith(ASSET_BACKGROUND_PREFIX)) {
            path = copyAssetBackgroundIfNeed(
                context,
                if (sceneBackground != null) {
                    EFFECT_THEME_BACKGROUND_CACHE_KEY
                } else {
                    preferenceKey
                },
                path,
            )
        }
        if (path.endsWith(".9.png")) {
            val bgDrawable = BitmapUtils.decodeNinePatchDrawable(path)
            return bgDrawable
        }
        val bgImgBlu = sceneBackground?.blur ?: when (themeMode) {
            Theme.Light -> context.getPrefInt(PreferKey.bgImageBlurring, 0)
            Theme.Dark -> context.getPrefInt(PreferKey.bgImageNBlurring, 0)
            Theme.EInk -> 0
        }
        val cacheKey = BackgroundBitmapCacheKey(
            path = path,
            lastModified = File(path).lastModified(),
            width = metrics.widthPixels,
            height = metrics.heightPixels,
            blur = bgImgBlu,
        )
        backgroundBitmapCache
            ?.takeIf { it.key == cacheKey && !it.bitmap.isRecycled }
            ?.let { return it.bitmap.toDrawable(context.resources) }

        val decoded = BitmapUtils.decodeBitmap(
            path,
            metrics.widthPixels,
            metrics.heightPixels,
        ) ?: return null
        val rendered = if (bgImgBlu == 0) decoded else decoded.stackBlur(bgImgBlu)
        backgroundBitmapCache = BackgroundBitmapCache(cacheKey, rendered)
        return rendered.toDrawable(context.resources)
    }

    /**
     * 一次应用完整的日间／夜间外观；特效类型由本主题决定，静态应用保留完整包。
     */
    internal fun applyManagedTheme(
        context: Context,
        theme: NgManagedTheme,
        preparedDrawer: NgPreparedDrawerProfile? = null,
        renderEffect: Boolean = true,
    ): Boolean {
        var drawerSettings = preparedDrawer
        return runCatching {
            if (drawerSettings == null) {
                drawerSettings = theme.drawerProfile?.let { NgDrawerProfileStore.prepare(context, it) }
            }
            fun materialize(background: NgThemeBackground, preferenceKey: String): String? {
                val path = background.path?.takeIf(String::isNotBlank) ?: return null
                return if (path.startsWith(ASSET_BACKGROUND_PREFIX)) {
                    copyAssetBackgroundIfNeed(
                        context = context,
                        preferenceKey = preferenceKey,
                        backgroundPath = path,
                        forceRefresh = true,
                    )
                } else {
                    path
                }
            }
            val lightBackground = materialize(theme.lightBackground, PreferKey.bgImage)
            val darkBackground = materialize(theme.darkBackground, PreferKey.bgImageN)
            theme.coverProfile?.takeIf { it.applyAlbumSelection }?.albumId?.let { albumId ->
                require(NgCoverAlbumStore.current(context).albums.any { it.id == albumId }) {
                    "主题关联的封面图集不存在"
                }
            }
            NgThemeModeStore.selectManagedTheme(context, renderEffect && theme.effect != null)
            context.putPrefString(PreferKey.dThemeName, theme.name)
            context.putPrefString(PreferKey.dNThemeName, theme.name)
            context.putPrefString(PreferKey.bgImage, lightBackground)
            context.putPrefString(PreferKey.bgImageN, darkBackground)
            context.putPrefInt(PreferKey.bgImageBlurring, theme.lightBackground.blur)
            context.putPrefInt(PreferKey.bgImageNBlurring, theme.darkBackground.blur)
            context.putPrefBoolean(PreferKey.tNavBar, true)
            val barProfile = theme.barProfile?.normalized()
            // A missing theme value uses the app default, never the previously applied theme's gap.
            AppConfig.bookshelfFloatingDockTopDistancePx = barProfile?.bookshelfFloatingDockTopDistancePx
                ?: BookshelfFloatingDockConfig.DEFAULT_TOP_DISTANCE_PX
            barProfile?.let { bars ->
                bars.useFloatingBottomBar?.let {
                    context.putPrefBoolean(PreferKey.useFloatingBottomBar, it)
                }
                bars.floatingBottomBarBottomDistancePx?.let {
                    AppConfig.floatingBottomBarBottomDistancePx = it
                }
                bars.floatingBottomBarTransparency?.let {
                    AppConfig.floatingBottomBarTransparency = it
                }
                bars.bookshelfTopBarStyle?.let {
                    AppConfig.bookshelfTopBarStyle = BookshelfTopBarStyle.fromValue(it)
                }
                bars.bookshelfFloatingDockTransparency?.let {
                    AppConfig.bookshelfFloatingDockTransparency = it
                }
                bars.bookshelfFloatingDockSearchPosition?.let {
                    AppConfig.bookshelfFloatingDockSearchPosition =
                        BookshelfFloatingDockSearchPosition.fromValue(it)
                }
            }
            NgColorConfigStore.update(context, theme.colors)
            theme.coverProfile?.let { cover ->
                if (cover.applyAlbumSelection) {
                    check(NgCoverAlbumStore.select(context, cover.albumId)) {
                        "无法应用主题封面图集"
                    }
                }
                cover.loadOnlyWifi?.let {
                    context.putPrefBoolean(PreferKey.loadCoverOnlyWifi, it)
                }
                cover.useDefault?.let {
                    context.putPrefBoolean(PreferKey.useDefaultCover, it)
                }
                cover.showName?.let {
                    context.putPrefBoolean(PreferKey.coverShowName, it)
                }
                cover.showAuthor?.let {
                    context.putPrefBoolean(PreferKey.coverShowAuthor, it)
                }
                cover.showNameDark?.let {
                    context.putPrefBoolean(PreferKey.coverShowNameN, it)
                }
                cover.showAuthorDark?.let {
                    context.putPrefBoolean(PreferKey.coverShowAuthorN, it)
                }
            }
            drawerSettings?.let { NgDrawerProfileStore.commit(context, it) }
            val themeMode = NgThemeModeStore.standardThemeMode(context)
            if (AppConfig.isEInkMode || AppConfig.themeMode != themeMode) {
                applyThemeMode(context, themeMode)
            }
            BookCover.upDefaultCover()
            postEvent(EventBus.RECREATE, "")
            true
        }.getOrElse { error ->
            AppLog.put("设置主题出错\n$error", error, true)
            false
        }.also { drawerSettings?.discard() }
    }

    fun getDurConfig(context: Context): ThemeSnapshot {
        val isNight = resolveThemeNightMode(
            AppConfig.themeMode,
            context.resources.configuration
        )
        val name = if (isNight) {
            context.getPrefString(PreferKey.dNThemeName) ?: ""
        } else {
            context.getPrefString(PreferKey.dThemeName) ?: ""
        }
        return if (isNight) {
            getNightTheme(context, name)
        } else {
            getDayTheme(context, name)
        }
    }

    private fun getDayTheme(context: Context, name: String): ThemeSnapshot {
        val primary =
            context.getPrefInt(PreferKey.cPrimary, context.getCompatColor(R.color.md_brown_500))
        val accent =
            context.getPrefInt(PreferKey.cAccent, context.getCompatColor(R.color.md_red_600))
        val background =
            context.getPrefInt(PreferKey.cBackground, context.getCompatColor(R.color.md_grey_100))
        val bBackground =
            context.getPrefInt(PreferKey.cBBackground, context.getCompatColor(R.color.md_grey_200))
        val bgImgPath =
            context.getPrefString(PreferKey.bgImage)
        val bgImgBlur =
            context.getPrefInt(PreferKey.bgImageBlurring, 0)

        return ThemeSnapshot(
            themeName = name,
            isNightTheme = false,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            transparentNavBar = true,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur
        )
    }

    private fun getNightTheme(context: Context, name: String): ThemeSnapshot {
        val primary =
            context.getPrefInt(
                PreferKey.cNPrimary,
                context.getCompatColor(R.color.md_blue_grey_600)
            )
        val accent =
            context.getPrefInt(
                PreferKey.cNAccent,
                context.getCompatColor(R.color.md_deep_orange_800)
            )
        val background =
            context.getPrefInt(PreferKey.cNBackground, context.getCompatColor(R.color.md_grey_900))
        val bBackground =
            context.getPrefInt(PreferKey.cNBBackground, context.getCompatColor(R.color.md_grey_850))
        val bgImgPath =
            context.getPrefString(PreferKey.bgImageN)
        val bgImgBlur =
            context.getPrefInt(PreferKey.bgImageNBlurring, 0)
        return ThemeSnapshot(
            themeName = name,
            isNightTheme = true,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            transparentNavBar = true,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur
        )
    }

    /**
     * 更新主题
     */
    fun applyTheme(
        context: Context,
        isNightTheme: Boolean = resolveThemeNightMode(
            AppConfig.themeMode,
            context.resources.configuration
        )
    ) = with(context) {
        if (AppConfig.isEInkMode) {
                ThemeStore.editTheme(this)
                    .primaryColor(Color.WHITE)
                    .accentColor(Color.BLACK)
                    .backgroundColor(Color.WHITE)
                    .bottomBackground(Color.WHITE)
                    .transparentNavBar(true)
                    .apply()
            return@with
        }
        val presentationMode = NgThemeModeStore.current(this)
        val softGradient = presentationMode == NgThemePresentationMode.SOFT_GRADIENT
        val multicolor = presentationMode == NgThemePresentationMode.MULTICOLOR
        val dynamicScene = presentationMode == NgThemePresentationMode.EFFECT_THEME
        val colorConfig = when {
            softGradient -> NgSoftGradientTheme.colors(this)
            multicolor -> NgMulticolorTheme.colors
            dynamicScene -> NgEffectThemeSelection.colors(this)
            else -> NgColorConfigStore.current(this)
        }
        val colors = NgThemeResolver.resolveColorScheme(
            context = this,
            colors = colorConfig,
            isDark = if (softGradient || multicolor) false else isNightTheme
        )
        val manual = colorConfig.takeIf { it.mode == NgColorGenerationMode.MANUAL }
            ?.manualColors(if (softGradient || multicolor) false else isNightTheme)
        ThemeStore.editTheme(this)
            .primaryColor(manual?.secondary ?: colors.topBarContainer)
            .accentColor(colors.primary)
            .backgroundColor(colors.background)
            .bottomBackground(manual?.labelContainer ?: colors.surfaceContainerLow)
            .transparentNavBar(true)
            .apply()
    }

    /** Current preferences exposed by the read-only JavaScript theme APIs; never persisted as a theme list. */
    @Keep
    data class ThemeSnapshot(
        @SerializedName("themeName") val themeName: String,
        @SerializedName("isNightTheme") val isNightTheme: Boolean,
        @SerializedName("primaryColor") val primaryColor: String,
        @SerializedName("accentColor") val accentColor: String,
        @SerializedName("backgroundColor") val backgroundColor: String,
        @SerializedName("bottomBackground") val bottomBackground: String,
        @SerializedName("transparentNavBar") val transparentNavBar: Boolean,
        @SerializedName("backgroundImgPath") val backgroundImgPath: String?,
        @SerializedName("backgroundImgBlur") val backgroundImgBlur: Int
    ) {

        fun toMap() = mapOf(
            "themeName" to themeName,
            "isNightTheme" to isNightTheme,
            "primaryColor" to primaryColor,
            "accentColor" to accentColor,
            "backgroundColor" to backgroundColor,
            "bottomBackground" to bottomBackground,
            "transparentNavBar" to transparentNavBar,
            "backgroundImgPath" to backgroundImgPath,
            "backgroundImgBlur" to backgroundImgBlur
        )

    }

}
