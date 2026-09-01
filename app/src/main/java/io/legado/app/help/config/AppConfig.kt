package io.legado.app.help.config

import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import io.legado.app.BuildConfig
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.utils.GSON
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefLong
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isNightMode
import io.legado.app.utils.parseIpsFromString
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefLong
import io.legado.app.utils.putPrefString
import io.legado.app.utils.removePref
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx
import java.net.InetAddress

internal fun normalizeReadAloudWorkerCount(value: String?): Int {
    return value?.toIntOrNull()?.coerceIn(1, 5) ?: 3
}

internal const val THREAD_COUNT_MIN = 1
internal const val THREAD_COUNT_MAX = 128
internal const val THREAD_COUNT_DEFAULT = 32

internal fun normalizeThreadCount(value: Int): Int =
    value.coerceIn(THREAD_COUNT_MIN, THREAD_COUNT_MAX)

internal fun normalizeThemeMode(value: String?): String {
    return when (value) {
        "0", "1", "2", "3" -> value
        "4", "5", "6" -> "1"
        else -> "0"
    }
}

internal fun resolveThemeNightMode(themeMode: String, systemNightMode: Boolean): Boolean {
    return when (themeMode) {
        "2" -> true
        "1", "3" -> false
        else -> systemNightMode
    }
}

internal fun resolveThemeNightMode(
    themeMode: String,
    configuration: Configuration
): Boolean = resolveThemeNightModeFromUiMode(themeMode, configuration.uiMode)

internal fun resolveThemeNightModeFromUiMode(themeMode: String, uiMode: Int): Boolean {
    val systemNightMode = uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    return resolveThemeNightMode(themeMode, systemNightMode)
}

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig : SharedPreferences.OnSharedPreferenceChangeListener {

    const val DEFAULT_FILE_PICKER_SYSTEM = "system"
    const val DEFAULT_FILE_PICKER_BUILT_IN = "built_in"

    val isCronet = appCtx.getPrefBoolean(PreferKey.cronet)
    var useAntiAlias = appCtx.getPrefBoolean(PreferKey.antiAlias)
    var userAgent: String = getPrefUserAgent()
    var customHosts = appCtx.getPrefString(PreferKey.customHosts)
    var editTheme = appCtx.getPrefInt(PreferKey.editTheme, 0)
    var editThemeDark = appCtx.getPrefInt(PreferKey.editThemeDark, 0)
    var editTemeAuto = appCtx.getPrefBoolean(PreferKey.editTemeAuto)
    private fun getThemeModePref(): String = normalizeThemeMode(
        appCtx.getPrefString(PreferKey.themeMode, "0")
    )

    var isEInkMode = getThemeModePref() == "3"
    var clickActionTL = appCtx.getPrefInt(PreferKey.clickActionTL, 2)
    var clickActionTC = appCtx.getPrefInt(PreferKey.clickActionTC, 2)
    var clickActionTR = appCtx.getPrefInt(PreferKey.clickActionTR, 1)
    var clickActionML = appCtx.getPrefInt(PreferKey.clickActionML, 2)
    var clickActionMC = appCtx.getPrefInt(PreferKey.clickActionMC, 0)
    var clickActionMR = appCtx.getPrefInt(PreferKey.clickActionMR, 1)
    var clickActionBL = appCtx.getPrefInt(PreferKey.clickActionBL, 2)
    var clickActionBC = appCtx.getPrefInt(PreferKey.clickActionBC, 1)
    var clickActionBR = appCtx.getPrefInt(PreferKey.clickActionBR, 1)
    var themeMode = getThemeModePref()
    var useDefaultCover = appCtx.getPrefBoolean(PreferKey.useDefaultCover, false)
    var optimizeRender = CanvasRecorderFactory.isSupport
            && appCtx.getPrefBoolean(PreferKey.optimizeRender, false)
    var recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)
    var recordNetworkLog = appCtx.getPrefBoolean(PreferKey.recordNetworkLog)
    var editFontScale = appCtx.getPrefInt(PreferKey.editFontScale, 16)
    var editNonPrintable = appCtx.getPrefInt(PreferKey.editNonPrintable, 0)
    var editAutoWrap = appCtx.getPrefBoolean(PreferKey.editAutoWrap, true)
    var editAutoComplete = appCtx.getPrefBoolean(PreferKey.editAutoComplete, true)
    var showBoardLine = appCtx.getPrefInt(PreferKey.showBoardLine, 1)
    var adaptSpecialStyle = appCtx.getPrefBoolean(PreferKey.adaptSpecialStyle, true)

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            PreferKey.editFontScale -> editFontScale = appCtx.getPrefInt(PreferKey.editFontScale, 16)
            PreferKey.editNonPrintable -> editNonPrintable = appCtx.getPrefInt(PreferKey.editNonPrintable, 0)
            PreferKey.editAutoWrap -> editAutoWrap = appCtx.getPrefBoolean(PreferKey.editAutoWrap, true)
            PreferKey.editAutoComplete -> editAutoComplete = appCtx.getPrefBoolean(PreferKey.editAutoComplete, true)
            PreferKey.showBoardLine -> showBoardLine = appCtx.getPrefInt(PreferKey.showBoardLine, 1)
            PreferKey.adaptSpecialStyle -> adaptSpecialStyle = appCtx.getPrefBoolean(PreferKey.adaptSpecialStyle, true)

            PreferKey.themeMode -> {
                themeMode = getThemeModePref()
                isEInkMode = themeMode == "3"
            }

            PreferKey.clickActionTL -> clickActionTL =
                appCtx.getPrefInt(PreferKey.clickActionTL, 2)

            PreferKey.clickActionTC -> clickActionTC =
                appCtx.getPrefInt(PreferKey.clickActionTC, 2)

            PreferKey.clickActionTR -> clickActionTR =
                appCtx.getPrefInt(PreferKey.clickActionTR, 1)

            PreferKey.clickActionML -> clickActionML =
                appCtx.getPrefInt(PreferKey.clickActionML, 2)

            PreferKey.clickActionMC -> clickActionMC =
                appCtx.getPrefInt(PreferKey.clickActionMC, 0)

            PreferKey.clickActionMR -> clickActionMR =
                appCtx.getPrefInt(PreferKey.clickActionMR, 1)

            PreferKey.clickActionBL -> clickActionBL =
                appCtx.getPrefInt(PreferKey.clickActionBL, 2)

            PreferKey.clickActionBC -> clickActionBC =
                appCtx.getPrefInt(PreferKey.clickActionBC, 1)

            PreferKey.clickActionBR -> clickActionBR =
                appCtx.getPrefInt(PreferKey.clickActionBR, 1)

            PreferKey.readBodyToLh -> ReadBookConfig.readBodyToLh =
                appCtx.getPrefBoolean(PreferKey.readBodyToLh, true)

            PreferKey.useZhLayout -> ReadBookConfig.useZhLayout =
                appCtx.getPrefBoolean(PreferKey.useZhLayout)

            PreferKey.userAgent -> userAgent = getPrefUserAgent()

            PreferKey.customHosts -> {
                customHosts = appCtx.getPrefString(PreferKey.customHosts)
                _hostMap = null
                _addressCache = null
            }

            PreferKey.editTheme -> editTheme = appCtx.getPrefInt(PreferKey.editTheme, 0)

            PreferKey.editThemeDark -> editThemeDark = appCtx.getPrefInt(PreferKey.editThemeDark, 0)

            PreferKey.editTemeAuto -> editTemeAuto = appCtx.getPrefBoolean(PreferKey.editTemeAuto)

            PreferKey.antiAlias -> useAntiAlias = appCtx.getPrefBoolean(PreferKey.antiAlias)

            PreferKey.useDefaultCover -> useDefaultCover =
                appCtx.getPrefBoolean(PreferKey.useDefaultCover, false)

            PreferKey.optimizeRender -> optimizeRender = CanvasRecorderFactory.isSupport
                    && appCtx.getPrefBoolean(PreferKey.optimizeRender, false)

            PreferKey.recordLog -> recordLog = appCtx.getPrefBoolean(PreferKey.recordLog)
            PreferKey.recordNetworkLog -> recordNetworkLog =
                appCtx.getPrefBoolean(PreferKey.recordNetworkLog)

        }
    }

    //dns配置
    private var _hostMap: Map<String, Any?>? = null
    val hostMap: Map<String, Any?>
        get() = _hostMap ?: run {
            val cache = GSON.fromJsonObject<Map<String, Any?>>(customHosts).getOrNull() ?: emptyMap()
            _hostMap = cache
            cache
        }
    private var _addressCache: Map<String, List<InetAddress>>? = null
    val addressCache: Map<String, List<InetAddress>>
        get() = _addressCache ?: run {
            val cache = hostMap.mapNotNull { (host, ipValue) ->
                val addresses = when (ipValue) {
                    is String -> ipValue.parseIpsFromString()
                    is List<*> -> ipValue.parseIpsFromList()
                    else -> null
                }
                addresses?.let { host to it }
            }.toMap()
            _addressCache = cache
            cache
        }
    private fun List<*>.parseIpsFromList(): List<InetAddress> =
        mapNotNull { element ->
            (element as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?.runCatching { InetAddress.getByName(this) }
                ?.getOrNull()
        }

    var isNightTheme: Boolean
        get() = resolveThemeNightMode(themeMode, isSystemNightTheme)
        set(value) {
            if (isNightTheme != value) {
                if (value) {
                    appCtx.putPrefString(PreferKey.themeMode, "2")
                } else {
                    appCtx.putPrefString(PreferKey.themeMode, "1")
                }
            }
        }
    val isSystemNightTheme: Boolean
        get() = appCtx.resources.configuration.isNightMode

    var showBookname: Int
        get() = getBookshelfLayoutProfile(activeBookshelfLayoutMode).showBookName
        set(value) {
            updateActiveBookshelfLayoutProfile { it.copy(showBookName = value) }
        }
    var bookshelfMargin: Int
        get() = getBookshelfLayoutProfile(activeBookshelfLayoutMode).spacing
        set(value) {
            updateActiveBookshelfLayoutProfile { it.copy(spacing = value) }
        }

    var showUnread: Boolean
        get() = getBookshelfLayoutProfile(activeBookshelfLayoutMode).showUnread
        set(value) {
            updateActiveBookshelfLayoutProfile { it.copy(showUnread = value) }
        }

    var showLastUpdateTime: Boolean
        get() = activeBookshelfLayoutMode == BookshelfLayoutMode.LIST &&
            getBookshelfLayoutProfile(BookshelfLayoutMode.LIST).showLastUpdateTime
        set(value) {
            updateActiveBookshelfLayoutProfile { it.copy(showLastUpdateTime = value) }
        }

    var showWaitUpCount: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showWaitUpCount, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showWaitUpCount, value)
        }

    var readBrightness: Int
        get() = if (ReadBookConfig.isNightTheme) {
            appCtx.getPrefInt(PreferKey.nightBrightness, 100)
        } else {
            appCtx.getPrefInt(PreferKey.brightness, 100)
        }
        set(value) {
            if (ReadBookConfig.isNightTheme) {
                appCtx.putPrefInt(PreferKey.nightBrightness, value)
            } else {
                appCtx.putPrefInt(PreferKey.brightness, value)
            }
        }

    val textSelectAble: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.textSelectAble, true)

    val isTransparentStatusBar = true

    val immNavigationBar = true

    val useFloatingBottomBar: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.useFloatingBottomBar, false)

    var floatingBottomBarBottomDistancePx: Int
        get() = appCtx.getPrefInt(
            PreferKey.floatingBottomBarBottomDistancePx,
            FloatingBottomBarConfig.AUTOMATIC_BOTTOM_DISTANCE_PX
        )
        set(value) {
            appCtx.putPrefInt(PreferKey.floatingBottomBarBottomDistancePx, value)
        }

    var floatingBottomBarTransparency: Int
        get() = FloatingBottomBarConfig.normalizeTransparencyPercent(
            appCtx.getPrefInt(
                PreferKey.floatingBottomBarTransparency,
                FloatingBottomBarConfig.DEFAULT_TRANSPARENCY_PERCENT
            )
        )
        set(value) {
            appCtx.putPrefInt(
                PreferKey.floatingBottomBarTransparency,
                FloatingBottomBarConfig.normalizeTransparencyPercent(value)
            )
        }

    var ngDrawerTransparency: Int
        get() = NgDrawerAppearanceConfig.normalizePercent(
            appCtx.getPrefInt(
                PreferKey.ngDrawerTransparency,
                NgDrawerAppearanceConfig.DEFAULT_TRANSPARENCY_PERCENT
            )
        )
        set(value) {
            appCtx.putPrefInt(
                PreferKey.ngDrawerTransparency,
                NgDrawerAppearanceConfig.normalizePercent(value)
            )
        }

    var ngDrawerPrimaryStrength: Int
        get() = NgDrawerAppearanceConfig.normalizePercent(
            appCtx.getPrefInt(
                PreferKey.ngDrawerPrimaryStrength,
                NgDrawerAppearanceConfig.DEFAULT_PRIMARY_STRENGTH_PERCENT
            )
        )
        set(value) {
            appCtx.putPrefInt(
                PreferKey.ngDrawerPrimaryStrength,
                NgDrawerAppearanceConfig.normalizePercent(value)
            )
        }

    var ngDrawerHorizontalMarginDp: Int
        get() = NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(
            appCtx.getPrefInt(
                PreferKey.ngDrawerHorizontalMarginDp,
                NgDrawerAppearanceConfig.DEFAULT_HORIZONTAL_MARGIN_DP
            )
        )
        set(value) {
            appCtx.putPrefInt(
                PreferKey.ngDrawerHorizontalMarginDp,
                NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(value)
            )
        }

    var ngDrawerCornerRadiusDp: Int
        get() = NgDrawerAppearanceConfig.normalizeCornerRadiusDp(
            appCtx.getPrefInt(
                PreferKey.ngDrawerCornerRadiusDp,
                NgDrawerAppearanceConfig.DEFAULT_CORNER_RADIUS_DP
            )
        )
        set(value) {
            appCtx.putPrefInt(
                PreferKey.ngDrawerCornerRadiusDp,
                NgDrawerAppearanceConfig.normalizeCornerRadiusDp(value)
            )
        }

    val screenOrientation: String?
        get() = appCtx.getPrefString(PreferKey.screenOrientation)

    var bookshelfHomeMode: BookshelfHomeMode
        get() = BookshelfHomeMode.fromValue(
            appCtx.getPrefInt(PreferKey.bookshelfHomeMode, BookshelfHomeMode.BOOKS.value)
        )
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfHomeMode, value.value)
        }

    private var bookshelfBooksLayoutMode: BookshelfLayoutMode
        get() = BookshelfLayoutMode.fromBooksLayoutValue(
            appCtx.getPrefInt(PreferKey.bookshelfLayout, BookshelfLayoutMode.LIST.value)
        )
        set(value) {
            require(value != BookshelfLayoutMode.GROUP_GRID)
            appCtx.putPrefInt(PreferKey.bookshelfLayout, value.value)
        }

    val activeBookshelfLayoutMode: BookshelfLayoutMode
        get() = if (bookshelfHomeMode == BookshelfHomeMode.GROUP_GRID) {
            BookshelfLayoutMode.GROUP_GRID
        } else {
            bookshelfBooksLayoutMode
        }

    fun selectBookshelfLayoutMode(mode: BookshelfLayoutMode) {
        if (mode == BookshelfLayoutMode.GROUP_GRID) {
            bookshelfHomeMode = BookshelfHomeMode.GROUP_GRID
        } else {
            bookshelfBooksLayoutMode = mode
            bookshelfHomeMode = BookshelfHomeMode.BOOKS
        }
    }

    fun getBookshelfLayoutProfile(mode: BookshelfLayoutMode): BookshelfLayoutProfile {
        val defaults = BookshelfLayoutProfile.default(mode)
        return BookshelfLayoutProfile(
            columns = appCtx.getPrefInt(
                BookshelfLayoutProfilePreferences.columns(mode),
                defaults.columns,
            ),
            innerColumns = appCtx.getPrefInt(
                BookshelfLayoutProfilePreferences.innerColumns(mode),
                defaults.innerColumns,
            ),
            showBookName = appCtx.getPrefInt(
                BookshelfLayoutProfilePreferences.showBookName(mode),
                defaults.showBookName,
            ),
            coverRadius = appCtx.getPrefInt(
                BookshelfLayoutProfilePreferences.coverRadius(mode),
                defaults.coverRadius,
            ),
            spacing = appCtx.getPrefInt(
                BookshelfLayoutProfilePreferences.spacing(mode),
                defaults.spacing,
            ),
            showUnread = appCtx.getPrefBoolean(
                BookshelfLayoutProfilePreferences.showUnread(mode),
                defaults.showUnread,
            ),
            showLastUpdateTime = appCtx.getPrefBoolean(
                BookshelfLayoutProfilePreferences.showLastUpdateTime(mode),
                defaults.showLastUpdateTime,
            ),
            sort = appCtx.getPrefInt(
                BookshelfLayoutProfilePreferences.sort(mode),
                defaults.sort,
            ),
        ).normalized(mode)
    }

    fun setBookshelfLayoutProfile(
        mode: BookshelfLayoutMode,
        profile: BookshelfLayoutProfile,
    ) {
        val normalized = profile.normalized(mode)
        appCtx.putPrefInt(
            BookshelfLayoutProfilePreferences.columns(mode),
            normalized.columns,
        )
        appCtx.putPrefInt(
            BookshelfLayoutProfilePreferences.innerColumns(mode),
            normalized.innerColumns,
        )
        appCtx.putPrefInt(
            BookshelfLayoutProfilePreferences.showBookName(mode),
            normalized.showBookName,
        )
        appCtx.putPrefInt(
            BookshelfLayoutProfilePreferences.coverRadius(mode),
            normalized.coverRadius,
        )
        appCtx.putPrefInt(
            BookshelfLayoutProfilePreferences.spacing(mode),
            normalized.spacing,
        )
        appCtx.putPrefBoolean(
            BookshelfLayoutProfilePreferences.showUnread(mode),
            normalized.showUnread,
        )
        appCtx.putPrefBoolean(
            BookshelfLayoutProfilePreferences.showLastUpdateTime(mode),
            normalized.showLastUpdateTime,
        )
        appCtx.putPrefInt(
            BookshelfLayoutProfilePreferences.sort(mode),
            normalized.sort,
        )
    }

    private inline fun updateActiveBookshelfLayoutProfile(
        transform: (BookshelfLayoutProfile) -> BookshelfLayoutProfile,
    ) {
        val mode = activeBookshelfLayoutMode
        setBookshelfLayoutProfile(mode, transform(getBookshelfLayoutProfile(mode)))
    }

    var bookshelfTopBarStyle: BookshelfTopBarStyle
        get() = BookshelfTopBarStyle.fromValue(
            appCtx.getPrefInt(
                PreferKey.bookshelfTopBarStyle,
                BookshelfTopBarStyle.COMPACT_TOOLBAR.value
            )
        )
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfTopBarStyle, value.value)
        }

    var bookshelfFloatingDockTopDistancePx: Int
        get() = appCtx.getPrefInt(
            PreferKey.bookshelfFloatingDockTopDistancePx,
            BookshelfFloatingDockConfig.AUTOMATIC_TOP_DISTANCE_PX
        )
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfFloatingDockTopDistancePx, value)
        }

    var bookshelfFloatingDockTransparency: Int
        get() = BookshelfFloatingDockConfig.normalizeTransparencyPercent(
            appCtx.getPrefInt(
                PreferKey.bookshelfFloatingDockTransparency,
                BookshelfFloatingDockConfig.DEFAULT_TRANSPARENCY_PERCENT
            )
        )
        set(value) {
            appCtx.putPrefInt(
                PreferKey.bookshelfFloatingDockTransparency,
                BookshelfFloatingDockConfig.normalizeTransparencyPercent(value)
            )
        }

    var bookshelfFloatingDockSearchPosition: BookshelfFloatingDockSearchPosition
        get() = BookshelfFloatingDockSearchPosition.fromValue(
            appCtx.getPrefInt(
                PreferKey.bookshelfFloatingDockSearchPosition,
                BookshelfFloatingDockSearchPosition.LEFT.value
            )
        )
        set(value) {
            appCtx.putPrefInt(PreferKey.bookshelfFloatingDockSearchPosition, value.value)
        }

    var bookshelfLayout: Int
        get() = when (activeBookshelfLayoutMode) {
            BookshelfLayoutMode.LIST -> 0
            BookshelfLayoutMode.COMPACT -> 1
            BookshelfLayoutMode.GRID ->
                getBookshelfLayoutProfile(activeBookshelfLayoutMode).columns
            BookshelfLayoutMode.GROUP_GRID ->
                getBookshelfLayoutProfile(activeBookshelfLayoutMode).innerColumns
        }
        set(value) {
            when {
                activeBookshelfLayoutMode == BookshelfLayoutMode.GROUP_GRID -> {
                    updateActiveBookshelfLayoutProfile { it.copy(innerColumns = value) }
                }
                value <= 0 -> selectBookshelfLayoutMode(BookshelfLayoutMode.LIST)
                value == 1 -> selectBookshelfLayoutMode(BookshelfLayoutMode.COMPACT)
                else -> {
                    selectBookshelfLayoutMode(BookshelfLayoutMode.GRID)
                    updateActiveBookshelfLayoutProfile { it.copy(columns = value) }
                }
            }
        }

    var saveTabPosition: Int
        get() = appCtx.getPrefInt(PreferKey.saveTabPosition, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.saveTabPosition, value)
        }

    var bookExportFileName: String?
        get() = appCtx.getPrefString(PreferKey.bookExportFileName)
        set(value) {
            appCtx.putPrefString(PreferKey.bookExportFileName, value)
        }

    // 保存 自定义导出章节模式 文件名js表达式
    var episodeExportFileName: String?
        get() = appCtx.getPrefString(PreferKey.episodeExportFileName, "")
        set(value) {
            appCtx.putPrefString(PreferKey.episodeExportFileName, value)
        }

    var bookImportFileName: String?
        get() = appCtx.getPrefString(PreferKey.bookImportFileName)
        set(value) {
            appCtx.putPrefString(PreferKey.bookImportFileName, value)
        }

    var backupPath: String?
        get() = appCtx.getPrefString(PreferKey.backupPath)
        set(value) {
            if (value.isNullOrEmpty()) {
                appCtx.removePref(PreferKey.backupPath)
            } else {
                appCtx.putPrefString(PreferKey.backupPath, value)
            }
        }

    // 书籍保存位置
    var defaultBookTreeUri: String?
        get() = appCtx.getPrefString(PreferKey.defaultBookTreeUri)
        set(value) {
            if (value.isNullOrEmpty()) {
                appCtx.removePref(PreferKey.defaultBookTreeUri)
            } else {
                appCtx.putPrefString(PreferKey.defaultBookTreeUri, value)
            }
        }

    var defaultFilePicker: String
        get() = appCtx.getPrefString(
            PreferKey.defaultFilePicker,
            DEFAULT_FILE_PICKER_SYSTEM,
        ) ?: DEFAULT_FILE_PICKER_SYSTEM
        set(value) {
            appCtx.putPrefString(PreferKey.defaultFilePicker, value)
        }

    val showDiscovery: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showDiscovery, true)

    var exploreLayoutMode: Int
        get() = appCtx.getPrefInt(PreferKey.exploreLayoutMode, 1).coerceIn(0, 2)
        set(value) {
            appCtx.putPrefInt(PreferKey.exploreLayoutMode, value.coerceIn(0, 2))
        }

    var exploreShowLayoutMode: Int
        get() = appCtx.getPrefInt(PreferKey.exploreShowLayoutMode, 0).coerceIn(0, 1)
        set(value) {
            appCtx.putPrefInt(PreferKey.exploreShowLayoutMode, value.coerceIn(0, 1))
        }

    val showRSS: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showRss, true)

    val autoRefreshBook: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.autoRefresh)

    val onlyUpdateRead: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.onlyUpdateRead)

    var enableReview: Boolean
        get() = BuildConfig.DEBUG && appCtx.getPrefBoolean(PreferKey.enableReview, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReview, value)
        }

    var threadCount: Int
        get() = normalizeThreadCount(
            appCtx.getPrefInt(PreferKey.threadCount, THREAD_COUNT_DEFAULT)
        )
        set(value) {
            appCtx.putPrefInt(PreferKey.threadCount, normalizeThreadCount(value))
        }

    var remoteServerId: Long
        get() = appCtx.getPrefLong(PreferKey.remoteServerId)
        set(value) {
            appCtx.putPrefLong(PreferKey.remoteServerId, value)
        }

    // 添加本地选择的目录
    var importBookPath: String?
        get() = appCtx.getPrefString("importBookPath")
        set(value) {
            if (value == null) {
                appCtx.removePref("importBookPath")
            } else {
                appCtx.putPrefString("importBookPath", value)
            }
        }

    var ttsFlowSys: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.ttsFollowSys, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.ttsFollowSys, value)
        }

    val noAnimScrollPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.noAnimScrollPage, false)

    const val defaultSpeechRate = 5

    var ttsSpeechRate: Int
        get() = appCtx.getPrefInt(PreferKey.ttsSpeechRate, defaultSpeechRate)
        set(value) {
            appCtx.putPrefInt(PreferKey.ttsSpeechRate, value)
        }

    var ttsTimer: Int
        get() = appCtx.getPrefInt(PreferKey.ttsTimer, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.ttsTimer, value)
        }

    val speechRatePlay: Int get() = if (ttsFlowSys) defaultSpeechRate else ttsSpeechRate

    var readAloudScenarioMode: Int
        get() = appCtx.getPrefInt(PreferKey.readAloudScenarioMode, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.readAloudScenarioMode, value.coerceIn(0, 1))
        }

    val readAloudMultiRole: Boolean
        get() = readAloudScenarioMode == 1

    val showListeningCapsuleOnMain: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showListeningCapsuleOnMain, false)

    var chineseConverterType: Int
        get() = appCtx.getPrefInt(PreferKey.chineseConverterType)
        set(value) {
            appCtx.putPrefInt(PreferKey.chineseConverterType, value)
        }

    var systemTypefaces: Int
        get() = appCtx.getPrefInt(PreferKey.systemTypefaces)
        set(value) {
            appCtx.putPrefInt(PreferKey.systemTypefaces, value)
        }

    val elevation: Int
        get() = if (isEInkMode) 0 else 12

    var readUrlInBrowser: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readUrlOpenInBrowser)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.readUrlOpenInBrowser, value)
        }

    var exportCharset: String
        get() {
            val c = appCtx.getPrefString(PreferKey.exportCharset)
            if (c.isNullOrBlank()) {
                return "UTF-8"
            }
            return c
        }
        set(value) {
            appCtx.putPrefString(PreferKey.exportCharset, value)
        }

    var exportUseReplace: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportUseReplace, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportUseReplace, value)
        }

    var exportToWebDav: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportToWebDav)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportToWebDav, value)
        }
    var exportNoChapterName: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportNoChapterName)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportNoChapterName, value)
        }

    // 是否启用自定义导出 default->false
    var enableCustomExport: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableCustomExport, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableCustomExport, value)
        }

    var exportType: Int
        get() = appCtx.getPrefInt(PreferKey.exportType)
        set(value) {
            appCtx.putPrefInt(PreferKey.exportType, value)
        }
    var exportPictureFile: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportPictureFile, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportPictureFile, value)
        }

    var exportPlainText: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportPlainText, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportPlainText, value)
        }

    var exportFilterInteractiveImages: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.exportFilterInteractiveImages, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.exportFilterInteractiveImages, value)
        }

    var parallelExportBook: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.parallelExportBook, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.parallelExportBook, value)
        }

    var changeSourceCheckAuthor: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceCheckAuthor)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceCheckAuthor, value)
        }

    var ttsEngine: String?
        get() = appCtx.getPrefString(PreferKey.ttsEngine)
        set(value) {
            appCtx.putPrefString(PreferKey.ttsEngine, value)
        }

    var multiRoleTtsEngineId: String?
        get() = appCtx.getPrefString(PreferKey.multiRoleTtsEngineId)
        set(value) {
            appCtx.putPrefString(PreferKey.multiRoleTtsEngineId, value)
        }

    var defaultNarratorTtsEngineId: String?
        get() = appCtx.getPrefString(PreferKey.defaultNarratorTtsEngineId)
        set(value) {
            appCtx.putPrefString(PreferKey.defaultNarratorTtsEngineId, value)
        }

    var defaultNarratorTtsVoiceId: String?
        get() = appCtx.getPrefString(PreferKey.defaultNarratorTtsVoiceId)
        set(value) {
            appCtx.putPrefString(PreferKey.defaultNarratorTtsVoiceId, value)
        }

    var defaultDialogueMaleTtsVoiceId: String?
        get() = appCtx.getPrefString(PreferKey.defaultDialogueMaleTtsVoiceId)
        set(value) {
            appCtx.putPrefString(PreferKey.defaultDialogueMaleTtsVoiceId, value)
        }

    var defaultDialogueFemaleTtsVoiceId: String?
        get() = appCtx.getPrefString(PreferKey.defaultDialogueFemaleTtsVoiceId)
        set(value) {
            appCtx.putPrefString(PreferKey.defaultDialogueFemaleTtsVoiceId, value)
        }

    var webPort: Int
        get() = appCtx.getPrefInt(PreferKey.webPort, 1122)
        set(value) {
            appCtx.putPrefInt(PreferKey.webPort, value)
        }

    var mcpPort: Int
        get() = appCtx.getPrefInt(PreferKey.mcpPort, 1124)
        set(value) {
            appCtx.putPrefInt(PreferKey.mcpPort, value)
        }

    var tocUiUseReplace: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocUiUseReplace)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocUiUseReplace, value)
        }

    var tocCountWords: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.tocCountWords, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.tocCountWords, value)
        }

    var enableReadRecord: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.enableReadRecord, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableReadRecord, value)
        }

    val autoChangeSource: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.autoChangeSource, true)

    var changeSourceLoadInfo: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadInfo)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadInfo, value)
        }

    var changeSourceLoadToc: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadToc)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadToc, value)
        }

    var changeSourceLoadWordCount: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.changeSourceLoadWordCount)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.changeSourceLoadWordCount, value)
        }

    var contentSelectSpeakMod: Int
        get() = appCtx.getPrefInt(PreferKey.contentSelectSpeakMod)
        set(value) {
            appCtx.putPrefInt(PreferKey.contentSelectSpeakMod, value)
        }

    var batchChangeSourceDelay: Int
        get() = appCtx.getPrefInt(PreferKey.batchChangeSourceDelay)
        set(value) {
            appCtx.putPrefInt(PreferKey.batchChangeSourceDelay, value)
        }

    val importKeepName get() = appCtx.getPrefBoolean(PreferKey.importKeepName)
    val importKeepGroup get() = appCtx.getPrefBoolean(PreferKey.importKeepGroup)
    var importKeepEnable: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.importKeepEnable, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.importKeepEnable, value)
        }
    var importShowComment: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.importShowComment, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.importShowComment, value)
        }

    val clickImgWay: String?
        get() = appCtx.getPrefString(PreferKey.clickImgWay)

    var preDownloadNum
        get() = appCtx.getPrefInt(PreferKey.preDownloadNum, 10)
        set(value) {
            appCtx.putPrefInt(PreferKey.preDownloadNum, value)
        }

    val syncBookProgress get() = appCtx.getPrefBoolean(PreferKey.syncBookProgress, true)

    val syncBookProgressPlus get() = appCtx.getPrefBoolean(PreferKey.syncBookProgressPlus, false)

    val mediaButtonOnExit get() = appCtx.getPrefBoolean("mediaButtonOnExit", true)

    val readAloudByMediaButton
        get() = appCtx.getPrefBoolean(PreferKey.readAloudByMediaButton, false)

    val replaceEnableDefault get() = appCtx.getPrefBoolean(PreferKey.replaceEnableDefault, true)

    val webDavDir get() = appCtx.getPrefString(PreferKey.webDavDir, "legado")

    val webDavDeviceName get() = appCtx.getPrefString(PreferKey.webDavDeviceName, Build.MODEL)

    val recordHeapDump get() = appCtx.getPrefBoolean(PreferKey.recordHeapDump, false)

    val loadCoverOnlyWifi get() = appCtx.getPrefBoolean(PreferKey.loadCoverOnlyWifi, false)

    val showAddToShelfAlert get() = appCtx.getPrefBoolean(PreferKey.showAddToShelfAlert, true)

    val ignoreAudioFocus get() = appCtx.getPrefBoolean(PreferKey.ignoreAudioFocus, false)

    var pauseReadAloudWhilePhoneCalls
        get() = appCtx.getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, false)
        set(value) = appCtx.putPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, value)

    val onlyLatestBackup get() = appCtx.getPrefBoolean(PreferKey.onlyLatestBackup, true)

    val autoCheckNewBackup get() = appCtx.getPrefBoolean(PreferKey.autoCheckNewBackup, true)

    val defaultHomePage get() = appCtx.getPrefString(PreferKey.defaultHomePage, "bookshelf")

    val updateToVariant get() = appCtx.getPrefString(PreferKey.updateToVariant, "default_version")

    val readAloudWorkerCount: Int
        get() = normalizeReadAloudWorkerCount(
            appCtx.getPrefString(PreferKey.readAloudWorkerCount, "3")
        )

    val skipReadAloudChapterTitle
        get() = appCtx.getPrefBoolean(PreferKey.skipReadAloudChapterTitle, false)

    val doublePageHorizontal: String?
        get() = appCtx.getPrefString(PreferKey.doublePageHorizontal)

    val progressBarBehavior: String?
        get() = appCtx.getPrefString(PreferKey.progressBarBehavior, "page")

    val keyPageOnLongPress
        get() = appCtx.getPrefBoolean(PreferKey.keyPageOnLongPress, false)

    val volumeKeyPage
        get() = appCtx.getPrefBoolean(PreferKey.volumeKeyPage, true)

    val volumeKeyPageOnPlay
        get() = appCtx.getPrefBoolean(PreferKey.volumeKeyPageOnPlay, true)

    val mouseWheelPage
        get() = appCtx.getPrefBoolean(PreferKey.mouseWheelPage, true)

    val paddingDisplayCutouts
        get() = appCtx.getPrefBoolean(PreferKey.paddingDisplayCutouts, false)

    var searchScope: String
        get() = appCtx.getPrefString("searchScope") ?: ""
        set(value) {
            appCtx.putPrefString("searchScope", value)
        }

    var searchGroup: String
        get() = appCtx.getPrefString("searchGroup") ?: ""
        set(value) {
            appCtx.putPrefString("searchGroup", value)
        }

    var pageTouchSlop: Int
        get() = appCtx.getPrefInt(PreferKey.pageTouchSlop, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.pageTouchSlop, value)
        }

    var pageTouchClick: Int
        get() = appCtx.getPrefInt(PreferKey.pageTouchClick, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.pageTouchClick, value)
        }

    var bookshelfSort: Int
        get() = getBookshelfLayoutProfile(activeBookshelfLayoutMode).sort
        set(value) {
            updateActiveBookshelfLayoutProfile { it.copy(sort = value) }
        }

    fun getBookSortByGroupId(groupId: Long): Int {
        return appDb.bookGroupDao.getByID(groupId)?.getRealBookSort()
            ?: bookshelfSort
    }

    private fun getPrefUserAgent(): String {
        val ua = appCtx.getPrefString(PreferKey.userAgent)
        if (ua.isNullOrBlank()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + BuildConfig.Cronet_Main_Version + " Safari/537.36"
        }
        return ua
    }

    var bitmapCacheSize: Int
        get() = appCtx.getPrefInt(PreferKey.bitmapCacheSize, 50)
        set(value) {
            appCtx.putPrefInt(PreferKey.bitmapCacheSize, value)
        }

    var imageRetainNum: Int
        get() = appCtx.getPrefInt(PreferKey.imageRetainNum, 0)
        set(value) {
            appCtx.putPrefInt(PreferKey.imageRetainNum, value)
        }

    var showReadTitleBarAddition: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showReadTitleAddition, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.showReadTitleAddition, value)
        }
    var readBarStyleFollowPage: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.readBarStyleFollowPage, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.readBarStyleFollowPage, value)
        }

    var audioPlayUseWakeLock: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.audioPlayWakeLock)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.audioPlayWakeLock, value)
        }

    var brightnessVwPos: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.brightnessVwPos)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.brightnessVwPos, value)
        }

    fun detectClickArea() {
        if (clickActionTL * clickActionTC * clickActionTR
            * clickActionML * clickActionMC * clickActionMR
            * clickActionBL * clickActionBC * clickActionBR != 0
        ) {
            appCtx.putPrefInt(PreferKey.clickActionMC, 0)
            appCtx.toastOnUi("当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
    }

    //跳转到漫画界面不使用富文本模式
    val showMangaUi: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.showMangaUi, true)

    //禁用漫画缩放
    var disableMangaScale: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.disableMangaScale, true)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableMangaScale, value)
        }

    var disableMangaPageAnim: Boolean
        get() = appCtx.getPrefBoolean(PreferKey.disableMangaPageAnim, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableMangaPageAnim, value)
        }

    //漫画预加载数量
    var mangaPreDownloadNum
        get() = appCtx.getPrefInt(PreferKey.mangaPreDownloadNum, 10)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaPreDownloadNum, value)
        }

    //点击翻页
    var disableClickScroll
        get() = appCtx.getPrefBoolean(PreferKey.disableClickScroll, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableClickScroll, value)
        }

    //漫画滚动速度
    var mangaAutoPageSpeed
        get() = appCtx.getPrefInt(PreferKey.mangaAutoPageSpeed, 3)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaAutoPageSpeed, value)
        }

    //漫画页脚配置
    var mangaFooterConfig
        get() = appCtx.getPrefString(PreferKey.mangaFooterConfig, "")
        set(value) {
            appCtx.putPrefString(PreferKey.mangaFooterConfig, value)
        }

    //漫画水平滚动
    var enableMangaHorizontalScroll
        get() = appCtx.getPrefBoolean(PreferKey.enableMangaHorizontalScroll, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableMangaHorizontalScroll, value)
        }

    var mangaColorFilter
        get() = appCtx.getPrefString(PreferKey.mangaColorFilter, "")
        set(value) {
            appCtx.putPrefString(PreferKey.mangaColorFilter, value)
        }

    //禁用漫画内标题
    var hideMangaTitle
        get() = appCtx.getPrefBoolean(PreferKey.hideMangaTitle, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.hideMangaTitle, value)
        }

    //开启墨水屏模式
    var enableMangaEInk
        get() = appCtx.getPrefBoolean(PreferKey.enableMangaEInk, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableMangaEInk, value)
        }

    var mangaEInkThreshold
        get() = appCtx.getPrefInt(PreferKey.mangaEInkThreshold, 150)
        set(value) {
            appCtx.putPrefInt(PreferKey.mangaEInkThreshold, value)
        }

    var disableHorizontalPageSnap
        get() = appCtx.getPrefBoolean(PreferKey.disableHorizontalPageSnap, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.disableHorizontalPageSnap, value)
        }

    var enableMangaGray
        get() = appCtx.getPrefBoolean(PreferKey.enableMangaGray, false)
        set(value) {
            appCtx.putPrefBoolean(PreferKey.enableMangaGray, value)
        }

    val autoUpdateVariant get() = appCtx.getPrefBoolean("autoUpdateVariant", true)
}
