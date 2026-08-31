package io.legado.app.help.config

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.DisplayMetrics
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.constant.Theme
import io.legado.app.help.DefaultData
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.model.BookCover
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.design.theme.NgColorGenerationMode
import io.legado.app.utils.BitmapUtils
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.externalFiles
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getFile
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
import splitties.init.appCtx
import java.io.File
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.toastOnUi
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
    "defaultData/theme/reading_ng_autumn_mountains.png" ->
        "defaultData/theme/reading_ng_autumn_mountains.webp"
    "defaultData/theme/reading_ng_autumn_mountains_dark.png" ->
        "defaultData/theme/reading_ng_autumn_mountains_dark.webp"
    else -> assetPath
}

internal fun resolveReinstalledThemeBackgroundPath(
    currentPath: String?,
    installedPath: String?,
    packageRootPath: String?,
    isFile: (String) -> Boolean = { File(it).isFile },
): String? {
    if (currentPath.isNullOrBlank() || installedPath.isNullOrBlank() ||
        packageRootPath.isNullOrBlank()
    ) {
        return currentPath
    }
    if (runCatching { isFile(currentPath) }.getOrDefault(false)) return currentPath
    val belongsToPackage = runCatching {
        val packageRoot = File(packageRootPath).canonicalFile.toPath()
        val currentFile = File(currentPath).canonicalFile.toPath()
        currentFile != packageRoot && currentFile.startsWith(packageRoot)
    }.getOrDefault(false)
    if (!belongsToPackage) return currentPath
    return installedPath.takeIf {
        runCatching { isFile(it) }.getOrDefault(false)
    } ?: currentPath
}

@Keep
object ThemeConfig {
    const val configFileName = "themeConfig.json"
    private const val ASSET_BACKGROUND_PREFIX = "asset://"
    private const val THEME_MODE_FOLLOW_SYSTEM = "0"
    private const val THEME_MODE_DARK = "2"
    private const val THEME_MODE_EINK = "3"
    val configFilePath = FileUtils.getPath(appCtx.filesDir, configFileName)

    val configList: ArrayList<Config> by lazy {
        val savedConfigs = getConfigs()
        ArrayList(savedConfigs ?: DefaultData.themeConfigs).apply {
            if (savedConfigs != null) {
                addBuiltInAssetThemes(this)
            }
        }
    }

    private var needClearImg = true
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
        val backgroundKey = if (getTheme(context) == Theme.Dark) {
            PreferKey.bgImageN
        } else {
            PreferKey.bgImage
        }
        return !context.getPrefString(backgroundKey).isNullOrBlank()
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

    private fun addBuiltInAssetThemes(configs: MutableList<Config>) {
        configs.removeAll {
            it.themeName == "阅读NG·背景一" ||
                    it.themeName == "阅读NG·背景二"
        }
        DefaultData.themeConfigs
            .filter { it.backgroundImgPath?.startsWith(ASSET_BACKGROUND_PREFIX) == true }
            .forEach { builtInConfig ->
                if (configs.none { it.themeName == builtInConfig.themeName }) {
                    configs.add(builtInConfig)
                }
            }
    }

    @Synchronized
    fun getBgImage(context: Context, metrics: DisplayMetrics): Drawable? {
        val themeMode = getTheme(context)
        val preferenceKey = when (themeMode) {
            Theme.Light -> PreferKey.bgImage
            Theme.Dark -> PreferKey.bgImageN
            else -> return  null
        }
        var path = context.getPrefString(preferenceKey)
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
            path = copyAssetBackgroundIfNeed(context, preferenceKey, path)
        }
        if (path.endsWith(".9.png")) {
            val bgDrawable = BitmapUtils.decodeNinePatchDrawable(path)
            return bgDrawable
        }
        val bgImgBlu = when (themeMode) {
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

    fun upConfig() {
        addConfigs(getConfigs())
    }

    fun save() {
        val json = GSON.toJson(configList)
        FileUtils.delete(configFilePath)
        FileUtils.createFileIfNotExist(configFilePath).writeText(json)
    }

    fun delConfig(index: Int) {
        configList.removeAt(index)
        save()
    }

    fun addConfig(json: String): Boolean {
        GSON.fromJsonObject<Config>(json.trim { it < ' ' }).getOrNull()
            ?.let {
                if (validateConfig(it)) {
                    addConfig(it)
                    return true
                }
            }
        return false
    }

    fun addConfig(newConfig: Config) {
        if (!validateConfig(newConfig)) {
            return
        }
        var hasTheme = false
        configList.forEachIndexed { index, config ->
            if (newConfig.themeName == config.themeName) {
                configList[index] = newConfig
                hasTheme = true
                return@forEachIndexed
            }
        }
        if (!hasTheme) {
            configList.add(newConfig)
        }
        save()
    }

    fun addConfigs(newConfigs: List<Config>?) {
        val newConfigs = newConfigs?.filter{
            validateConfig(it)
        }
        if (newConfigs.isNullOrEmpty()) {
            return
        }
        newConfigs.forEach { newConfig ->
            val existingIndex = configList.indexOfFirst { it.themeName == newConfig.themeName }
            if (existingIndex != -1) {
                configList[existingIndex] = newConfig
            } else {
                configList.add(newConfig)
            }
        }
        save()
    }

    private fun validateConfig(config: Config): Boolean {
        try {
            config.primaryColor.toColorInt()
            config.accentColor.toColorInt()
            config.backgroundColor.toColorInt()
            config.bottomBackground.toColorInt()
            return true
        } catch (_: Exception) {
            return false
        }
    }

    private fun getConfigs(): List<Config>? {
        val configFile = File(configFilePath)
        if (configFile.exists()) {
            kotlin.runCatching {
                val json = configFile.readText()
                return GSON.fromJsonArray<Config>(json).getOrThrow()
            }.onFailure {
                it.printOnDebug()
            }
        }
        return null
    }

    fun applyConfig(context: Context, config: Config) {
        try {
            if (needClearImg) {
                needClearImg = false
                clearBg(context)
            }
            val primary = config.primaryColor.toColorInt()
            val accent = config.accentColor.toColorInt()
            val background = config.backgroundColor.toColorInt()
            val bBackground = config.bottomBackground.toColorInt()
            val isNightTheme = config.isNightTheme
            val backgroundPath = config.backgroundImgPath
            val preferenceKey = if (isNightTheme) {
                PreferKey.bgImageN
            } else {
                PreferKey.bgImage
            }
            if (backgroundPath != null && backgroundPath.startsWith("http")) {
                val fileRoot = context.externalFiles
                val name = getUrlToFile(backgroundPath)
                val fileFold = File(fileRoot, preferenceKey)
                if (!fileFold.exists()) {
                    fileFold.mkdirs()
                }
                val fileImg = File(fileFold, name)
                if (!fileImg.exists()) {
                    appCtx.toastOnUi("下载背景图片中...")
                    Coroutine.async {
                        kotlin.runCatching {
                            val res = okHttpClient.newCallResponse(0) {
                                url(backgroundPath)
                            }
                            res.body.byteStream().use { inputStream ->
                                FileOutputStream(fileImg).use { outputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                            }
                        }.onSuccess {
                            appCtx.toastOnUi("背景图下载成功\n请重新应用主题")
                        }.onFailure {
                            appCtx.toastOnUi(it.localizedMessage)
                        }
                    }
                    return
                }
            }
            val savedBackgroundPath = when {
                backgroundPath?.startsWith(ASSET_BACKGROUND_PREFIX) == true -> {
                    copyAssetBackgroundIfNeed(
                        context = context,
                        preferenceKey = preferenceKey,
                        backgroundPath = backgroundPath,
                        forceRefresh = true,
                    )
                }

                else -> backgroundPath
            }
            val backgroundBlur = config.backgroundImgBlur
            if (isNightTheme) {
                context.putPrefString(PreferKey.dNThemeName, config.themeName)
                context.putPrefInt(PreferKey.cNPrimary, primary)
                context.putPrefInt(PreferKey.cNAccent, accent)
                context.putPrefInt(PreferKey.cNBackground, background)
                context.putPrefInt(PreferKey.cNBBackground, bBackground)
                context.putPrefString(PreferKey.bgImageN, savedBackgroundPath)
                context.putPrefInt(PreferKey.bgImageNBlurring, backgroundBlur)
            } else {
                context.putPrefString(PreferKey.dThemeName, config.themeName)
                context.putPrefInt(PreferKey.cPrimary, primary)
                context.putPrefInt(PreferKey.cAccent, accent)
                context.putPrefInt(PreferKey.cBackground, background)
                context.putPrefInt(PreferKey.cBBackground, bBackground)
                context.putPrefString(PreferKey.bgImage, savedBackgroundPath)
                context.putPrefInt(PreferKey.bgImageBlurring, backgroundBlur)
            }
            NgColorConfigStore.adoptLegacyVariant(context, isNightTheme)
            val activeIsNightTheme = resolveThemeNightMode(
                AppConfig.themeMode,
                context.resources.configuration
            )
            if (!AppConfig.isEInkMode && activeIsNightTheme == isNightTheme) {
                applyDayNight(context)
            }
        } catch (e: Exception) {
            AppLog.put("设置主题出错\n$e", e, true)
        }
    }

    /**
     * 新主题管理一次应用完整的日间／夜间颜色和背景，不改变顶部主题模式。
     */
    internal fun applyManagedTheme(context: Context, theme: NgManagedTheme): Boolean {
        return runCatching {
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
            context.putPrefString(PreferKey.dThemeName, theme.name)
            context.putPrefString(PreferKey.dNThemeName, theme.name)
            context.putPrefString(PreferKey.bgImage, lightBackground)
            context.putPrefString(PreferKey.bgImageN, darkBackground)
            context.putPrefInt(PreferKey.bgImageBlurring, theme.lightBackground.blur)
            context.putPrefInt(PreferKey.bgImageNBlurring, theme.darkBackground.blur)
            context.putPrefBoolean(PreferKey.tNavBar, theme.transparentAppBars)
            theme.barProfile?.normalized()?.let { bars ->
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
                bars.bookshelfFloatingDockTopDistancePx?.let {
                    AppConfig.bookshelfFloatingDockTopDistancePx = it
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
            BookCover.upDefaultCover()
            postEvent(EventBus.RECREATE, "")
            true
        }.getOrElse { error ->
            AppLog.put("设置主题出错\n$error", error, true)
            false
        }
    }

    internal fun repairReinstalledThemeBackgrounds(
        context: Context,
        theme: NgManagedTheme,
    ): Boolean {
        val packageRootPath = theme.packageRootPath ?: return false
        var repaired = false
        fun repair(preferenceKey: String, installedPath: String?) {
            val currentPath = context.getPrefString(preferenceKey)
            val resolvedPath = resolveReinstalledThemeBackgroundPath(
                currentPath = currentPath,
                installedPath = installedPath,
                packageRootPath = packageRootPath,
            )
            if (resolvedPath != currentPath) {
                context.putPrefString(preferenceKey, resolvedPath)
                repaired = true
            }
        }
        repair(PreferKey.bgImage, theme.lightBackground.path)
        repair(PreferKey.bgImageN, theme.darkBackground.path)
        return repaired
    }

    fun getDurConfig(context: Context): Config {
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

    private fun getDayTheme(context: Context, name: String): Config {
        val primary =
            context.getPrefInt(PreferKey.cPrimary, context.getCompatColor(R.color.md_brown_500))
        val accent =
            context.getPrefInt(PreferKey.cAccent, context.getCompatColor(R.color.md_red_600))
        val background =
            context.getPrefInt(PreferKey.cBackground, context.getCompatColor(R.color.md_grey_100))
        val bBackground =
            context.getPrefInt(PreferKey.cBBackground, context.getCompatColor(R.color.md_grey_200))
        val transparentNavBar =
            context.getPrefBoolean(PreferKey.tNavBar, false)
        val bgImgPath =
            context.getPrefString(PreferKey.bgImage)
        val bgImgBlur =
            context.getPrefInt(PreferKey.bgImageBlurring, 0)

        return Config(
            themeName = name,
            isNightTheme = false,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            transparentNavBar = transparentNavBar,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur
        )
    }

    fun saveDayTheme(context: Context, name: String) {
        val config = getDayTheme(context, name)
        addConfig(config)
    }

    private fun getNightTheme(context: Context, name: String): Config {
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
        val transparentNavBar =
            context.getPrefBoolean(PreferKey.tNavBar, false)
        val bgImgPath =
            context.getPrefString(PreferKey.bgImageN)
        val bgImgBlur =
            context.getPrefInt(PreferKey.bgImageNBlurring, 0)
        return Config(
            themeName = name,
            isNightTheme = true,
            primaryColor = "#${primary.hexString}",
            accentColor = "#${accent.hexString}",
            backgroundColor = "#${background.hexString}",
            bottomBackground = "#${bBackground.hexString}",
            transparentNavBar = transparentNavBar,
            backgroundImgPath = bgImgPath,
            backgroundImgBlur = bgImgBlur
        )
    }

    fun saveNightTheme(context: Context, name: String) {
        val config = getNightTheme(context, name)
        addConfig(config)
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
                    .transparentNavBar(false)
                    .apply()
            return@with
        }
        val colorConfig = NgColorConfigStore.current(this)
        val colors = NgThemeResolver.resolveColorScheme(
            context = this,
            colors = colorConfig,
            isDark = isNightTheme
        )
        val manual = colorConfig.takeIf { it.mode == NgColorGenerationMode.MANUAL }
            ?.manualColors(isNightTheme)
        ThemeStore.editTheme(this)
            .primaryColor(manual?.secondary ?: colors.topBarContainer)
            .accentColor(colors.primary)
            .backgroundColor(colors.background)
            .bottomBackground(manual?.labelContainer ?: colors.surfaceContainerLow)
            .transparentNavBar(getPrefBoolean(PreferKey.tNavBar, false))
            .apply()
    }

    fun clearBg(context: Context) {
        val (nightConfigs, dayConfigs) = configList.partition { it.isNightTheme }
        val nightBackgroundImgPaths = nightConfigs.mapNotNull {
            val path = it.backgroundImgPath ?: return@mapNotNull null
            cachedBackgroundPath(context, path, PreferKey.bgImageN)
        }
        val dayBackgroundImgPaths = dayConfigs.mapNotNull {
            val path = it.backgroundImgPath ?: return@mapNotNull null
            cachedBackgroundPath(context, path, PreferKey.bgImage)
        }
        appCtx.externalFiles.getFile(PreferKey.bgImage).listFiles()?.forEach {
            if (!dayBackgroundImgPaths.contains(it.absolutePath)) {
                it.delete()
            }
        }
        appCtx.externalFiles.getFile(PreferKey.bgImageN).listFiles()?.forEach {
            if (!nightBackgroundImgPaths.contains(it.absolutePath)) {
                it.delete()
            }
        }
    }

    @Keep
    data class Config(
        var themeName: String,
        var isNightTheme: Boolean,
        var primaryColor: String,
        var accentColor: String,
        var backgroundColor: String,
        var bottomBackground: String,
        var transparentNavBar: Boolean,
        var backgroundImgPath: String?,
        var backgroundImgBlur: Int
    ) {

        override fun hashCode(): Int {
            return GSON.toJson(this).hashCode()
        }

        override fun equals(other: Any?): Boolean {
            other ?: return false
            if (other is Config) {
                return other.themeName == themeName
                        && other.isNightTheme == isNightTheme
                        && other.primaryColor == primaryColor
                        && other.accentColor == accentColor
                        && other.backgroundColor == backgroundColor
                        && other.bottomBackground == bottomBackground
                        && other.transparentNavBar == transparentNavBar
                        && other.backgroundImgPath == backgroundImgPath
                        && other.backgroundImgBlur == backgroundImgBlur
            }
            return false
        }

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
