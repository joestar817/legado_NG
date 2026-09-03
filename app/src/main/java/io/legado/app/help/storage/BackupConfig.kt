package io.legado.app.help.storage

import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.BookshelfLayoutProfilePreferences
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx

/**
 * 备份配置
 */
@Suppress("ConstPropertyName")
object BackupConfig {

    private val ignoreConfigPath = FileUtils.getPath(appCtx.filesDir, "restoreIgnore.json")
    val ignoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(ignoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    private const val readConfigKey = "readConfig"
    private const val themeConfigKey = "themeConfig"
    private const val coverConfigKey = "coverConfig"
    private const val localBookKey = "localBook"

    //配置忽略key
    val ignoreKeys = arrayOf(
        readConfigKey,
        PreferKey.themeMode,
        themeConfigKey,
        coverConfigKey,
        PreferKey.bookshelfLayout,
        PreferKey.showRss,
        PreferKey.threadCount,
        localBookKey
    )

    //配置忽略标题
    val ignoreTitle = arrayOf(
        appCtx.getString(R.string.read_config),
        appCtx.getString(R.string.theme_mode),
        appCtx.getString(R.string.theme_config),
        appCtx.getString(R.string.cover_config),
        appCtx.getString(R.string.bookshelf_layout),
        appCtx.getString(R.string.show_rss),
        appCtx.getString(R.string.thread_count),
        appCtx.getString(R.string.local_book)
    )

    //自动忽略keys
    private val ignorePrefKeys = arrayOf(
        PreferKey.defaultCover,
        PreferKey.defaultCoverDark,
        PreferKey.backupPath,
        PreferKey.defaultBookTreeUri,
        PreferKey.webDavDeviceName,
        PreferKey.launcherIcon,
        PreferKey.bitmapCacheSize,
        PreferKey.webServiceWakeLock,
        PreferKey.readAloudWakeLock,
        PreferKey.audioPlayWakeLock
    )

    //阅读配置
    private val readPrefKeys = arrayOf(
        PreferKey.readStyleSelect,
        PreferKey.comicStyleSelect,
        PreferKey.shareLayout,
        PreferKey.hideStatusBar,
        PreferKey.hideNavigationBar,
        PreferKey.autoReadSpeed,
        PreferKey.autoReadPageMode,
        PreferKey.clickActionTL,
        PreferKey.clickActionTC,
        PreferKey.clickActionTR,
        PreferKey.clickActionML,
        PreferKey.clickActionMC,
        PreferKey.clickActionMR,
        PreferKey.clickActionBL,
        PreferKey.clickActionBC,
        PreferKey.clickActionBR
    )

    private val themePrefKeys = BackupRestorePolicy.themeConfigPreferenceKeys

    private val coverPrefKeys = arrayOf(
        PreferKey.useDefaultCover,
        PreferKey.loadCoverOnlyWifi,
        PreferKey.coverShowName,
        PreferKey.coverShowAuthor,
        PreferKey.coverShowNameN,
        PreferKey.coverShowAuthorN
    )

    fun keyIsNotIgnore(key: String): Boolean {
        return when {
            ignorePrefKeys.contains(key) -> false
            ignoreReadConfig && readPrefKeys.contains(key) -> false
            ignoreThemeConfig && themePrefKeys.contains(key) -> false
            ignoreCoverConfig && coverPrefKeys.contains(key) -> false
            PreferKey.themeMode == key && ignoreThemeMode -> false
            key in bookshelfLayoutPreferenceKeys && ignoreBookshelfLayout -> false
            PreferKey.showRss == key && ignoreShowRss -> false
            PreferKey.threadCount == key && ignoreThreadCount -> false
            else -> true
        }
    }

    val ignoreReadConfig: Boolean
        get() = ignoreConfig[readConfigKey] == true
    private val ignoreThemeMode: Boolean
        get() = ignoreConfig[PreferKey.themeMode] == true
    private val ignoreThemeConfig: Boolean
        get() = ignoreConfig[themeConfigKey] == true
    private val ignoreCoverConfig: Boolean
        get() = ignoreConfig[coverConfigKey] == true
    private val ignoreBookshelfLayout: Boolean
        get() = ignoreConfig[PreferKey.bookshelfLayout] == true
    private val ignoreShowRss: Boolean
        get() = ignoreConfig[PreferKey.showRss] == true
    private val ignoreThreadCount: Boolean
        get() = ignoreConfig[PreferKey.threadCount] == true
    val ignoreLocalBook: Boolean
        get() = ignoreConfig[localBookKey] == true

    private val bookshelfLayoutPreferenceKeys = setOf(
        PreferKey.bookshelfLayout,
        PreferKey.bookshelfHomeMode,
        PreferKey.showBooknameLayout,
        PreferKey.bookshelfMargin,
        PreferKey.showUnread,
        PreferKey.showLastUpdateTime,
        PreferKey.showWaitUpCount,
        PreferKey.bookshelfSort,
    ) + BookshelfLayoutProfilePreferences.keys

    fun saveIgnoreConfig() {
        val json = GSON.toJson(ignoreConfig)
        FileUtils.createFileIfNotExist(ignoreConfigPath).writeText(json)
    }

}

/**
 * 整包备份只恢复数据和可移植偏好；主题由主题包管理，MD3 排版由排版包管理。
 */
internal object BackupRestorePolicy {

    val themeConfigPreferenceKeys = setOf(
        PreferKey.ngThemePresentationMode,
        PreferKey.ngStandardThemeMode,
        PreferKey.ngInternalThemeMode,
        PreferKey.ngDynamicScenePreset,
        PreferKey.ngDynamicSceneSakuraColors,
        PreferKey.ngDynamicSceneCatsColors,
        PreferKey.ngSoftGradientColor,
        PreferKey.ngSoftGradientLightField,
        PreferKey.cPrimary,
        PreferKey.cAccent,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.bgImage,
        PreferKey.bgImageBlurring,
        PreferKey.tNavBar,
        PreferKey.cNPrimary,
        PreferKey.cNAccent,
        PreferKey.cNBackground,
        PreferKey.cNBBackground,
        PreferKey.ngColorMode,
        PreferKey.ngColorLightSeed,
        PreferKey.ngColorDarkSeed,
        PreferKey.ngColorPaletteStyle,
        PreferKey.ngColorContrast,
        PreferKey.ngColorSpec,
        PreferKey.ngColorLightPrimary,
        PreferKey.ngColorLightSecondary,
        PreferKey.ngColorLightPrimaryText,
        PreferKey.ngColorLightSecondaryText,
        PreferKey.ngColorLightBackground,
        PreferKey.ngColorLightLabel,
        PreferKey.ngColorLightTopBarTextMode,
        PreferKey.ngColorDarkPrimary,
        PreferKey.ngColorDarkSecondary,
        PreferKey.ngColorDarkPrimaryText,
        PreferKey.ngColorDarkSecondaryText,
        PreferKey.ngColorDarkBackground,
        PreferKey.ngColorDarkLabel,
        PreferKey.ngColorDarkTopBarTextMode,
        PreferKey.bgImageN,
        PreferKey.bgImageNBlurring,
        PreferKey.tNavBarN,
        PreferKey.useFloatingBottomBar,
        PreferKey.floatingBottomBarBottomDistancePx,
        PreferKey.floatingBottomBarTransparency,
        PreferKey.bookshelfTopBarStyle,
        PreferKey.bookshelfFloatingDockTopDistancePx,
        PreferKey.bookshelfFloatingDockTransparency,
        PreferKey.bookshelfFloatingDockSearchPosition,
        "ngManagedThemes.v1",
        "ngActiveManagedThemeId.v1"
    )

    private val md3ReadStylePreferenceKeys = setOf(
        PreferKey.readStyleSelect,
        PreferKey.comicStyleSelect,
        PreferKey.shareLayout,
        PreferKey.showBrightnessView,
        PreferKey.brightnessVwPos
    )

    fun shouldRestoreReadConfigs(isMd3Backup: Boolean): Boolean = !isMd3Backup

    fun shouldRestorePreference(key: String, isMd3Backup: Boolean): Boolean {
        if (
            key == PreferKey.themeMode ||
            key == PreferKey.readNightTheme ||
            key == PreferKey.readThemeMode
        ) return false
        if (key in themeConfigPreferenceKeys) return false
        return !isMd3Backup || key !in md3ReadStylePreferenceKeys
    }
}
