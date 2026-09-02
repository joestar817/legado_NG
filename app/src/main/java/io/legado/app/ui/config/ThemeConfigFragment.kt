package io.legado.app.ui.config

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.AppContextWrapper
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.LauncherIconHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.BookshelfFloatingDockConfig
import io.legado.app.help.config.BookshelfFloatingDockSearchPosition
import io.legado.app.help.config.BookshelfTopBarStyle
import io.legado.app.help.config.FloatingBottomBarConfig
import io.legado.app.help.config.NgDrawerAppearanceConfig
import io.legado.app.help.config.NgSoftGradientColorPreset
import io.legado.app.help.config.NgSoftGradientLightFieldPreset
import io.legado.app.help.config.NgSoftGradientTheme
import io.legado.app.help.config.NgThemeModeGroup
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgVisualSystem
import io.legado.app.help.config.NgVisualSystemStore
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.http.addHeaders
import io.legado.app.help.http.newCallResponse
import io.legado.app.help.http.okHttpClient
import io.legado.app.model.analyzeRule.AnalyzeUrl
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.utils.FileUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.SelectImageContract
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.getPrefString
import io.legado.app.utils.inputStream
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefInt
import io.legado.app.utils.putPrefString
import io.legado.app.utils.readUri
import io.legado.app.utils.removePref
import io.legado.app.utils.startActivity
import io.legado.app.utils.statusBarHeight
import io.legado.app.utils.sysConfiguration
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.launch
import splitties.init.appCtx
import java.io.FileOutputStream
import kotlin.math.roundToInt

@Suppress("SameParameterValue")
class ThemeConfigFragment : BaseFragment(R.layout.fragment_theme_config) {

    private val requestCodeBgLight = 121
    private val requestCodeBgDark = 122
    private var screenState by mutableStateOf(ThemeConfigScreenState())
    private var backgroundEditorState by mutableStateOf<ThemeBackgroundEditorState?>(null)
    private var fontScaleEditorState by mutableStateOf<ThemeFontScaleEditorState?>(null)

    private val selectImage = registerForActivityResult(SelectImageContract()) {
        it.uri?.let { uri ->
            when (it.requestCode) {
                requestCodeBgLight -> copyBgFromUri(uri, PreferKey.bgImage) { path ->
                    updateBackgroundDraft(dark = false, path = path)
                }

                requestCodeBgDark -> copyBgFromUri(uri, PreferKey.bgImageN) { path ->
                    updateBackgroundDraft(dark = true, path = path)
                }
            }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        activity?.setTitle(titleRes())
        refreshContent()
        (view as ComposeView).apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                NgAppTheme {
                    ThemeConfigScreen(
                        state = screenState,
                        section = screenSection(),
                        onThemeModeGroupSelected = ::selectThemeModeGroup,
                        onStandardThemeModeSelected = ::selectStandardThemeMode,
                        onInternalThemeModeSelected = ::selectInternalThemeMode,
                        onSoftGradientColorSelected = ::selectSoftGradientColor,
                        onSoftGradientLightFieldSelected = ::selectSoftGradientLightField,
                        onVisualSystemSelected = ::setVisualSystem,
                        onLauncherIconClick = ::showLauncherIconSelection,
                        onFloatingBottomBarChanged = ::setFloatingBottomBar,
                        onFloatingBottomBarBottomDistanceChanged =
                            ::setFloatingBottomBarBottomDistanceDraft,
                        onFloatingBottomBarBottomDistanceChangeFinished =
                            ::saveFloatingBottomBarBottomDistance,
                        onFloatingBottomBarTransparencyChanged =
                            ::setFloatingBottomBarTransparencyDraft,
                        onFloatingBottomBarTransparencyChangeFinished =
                            ::saveFloatingBottomBarTransparency,
                        onDrawerTransparencyChanged = ::setDrawerTransparencyDraft,
                        onDrawerTransparencyChangeFinished = ::saveDrawerTransparency,
                        onDrawerPrimaryStrengthChanged = ::setDrawerPrimaryStrengthDraft,
                        onDrawerPrimaryStrengthChangeFinished = ::saveDrawerPrimaryStrength,
                        onDrawerHorizontalMarginChanged = ::setDrawerHorizontalMarginDraft,
                        onDrawerHorizontalMarginChangeFinished = ::saveDrawerHorizontalMargin,
                        onDrawerCornerRadiusChanged = ::setDrawerCornerRadiusDraft,
                        onDrawerCornerRadiusChangeFinished = ::saveDrawerCornerRadius,
                        onBookshelfTopBarStyleSelected = ::setBookshelfTopBarStyle,
                        onBookshelfFloatingDockTopDistanceChanged =
                            ::setBookshelfFloatingDockTopDistanceDraft,
                        onBookshelfFloatingDockTopDistanceChangeFinished =
                            ::saveBookshelfFloatingDockTopDistance,
                        onBookshelfFloatingDockTransparencyChanged =
                            ::setBookshelfFloatingDockTransparencyDraft,
                        onBookshelfFloatingDockTransparencyChangeFinished =
                            ::saveBookshelfFloatingDockTransparency,
                        onBookshelfFloatingDockSearchPositionSelected =
                            ::setBookshelfFloatingDockSearchPosition,
                        onTransparentAppBarsChanged = ::setTransparentAppBars,
                        onAutoRefreshChanged = ::setAutoRefresh,
                        onOnlyUpdateReadChanged = ::setOnlyUpdateRead,
                        onDefaultToReadChanged = ::setDefaultToRead,
                        onShowDiscoveryChanged = ::setShowDiscovery,
                        onShowRssChanged = ::setShowRss,
                        onDefaultHomePageSelected = ::setDefaultHomePage,
                        onOpenCustomColors = {
                            (activity as? ConfigActivity)?.openThemeColorConfigPage()
                        },
                        onOpenFontScale = ::openFontScaleEditor,
                        onOpenCoverConfig = {
                            startActivity<ConfigActivity> {
                                putExtra("configTag", ConfigTag.COVER_CONFIG)
                            }
                        },
                        onOpenThemeManager = {
                            (activity as? ConfigActivity)?.openThemeManagerPage()
                        },
                        onOpenDayBackground = { openBackgroundEditor(false) },
                        onOpenNightBackground = { openBackgroundEditor(true) }
                    )
                    backgroundEditorState?.let { editorState ->
                        ThemeBackgroundEditorSheet(
                            state = editorState,
                            onDismissRequest = { backgroundEditorState = null },
                            onSelectImage = { selectBackgroundImage(editorState.dark) },
                            onRemoveImage = {
                                backgroundEditorState = editorState.copy(path = null, blur = 0)
                            },
                            onBlurChanged = { blur ->
                                backgroundEditorState = editorState.copy(blur = blur)
                            },
                            onSave = ::saveBackgroundEditor
                        )
                    }
                    fontScaleEditorState?.let { editorState ->
                        ThemeFontScaleEditorSheet(
                            state = editorState,
                            onDismissRequest = { fontScaleEditorState = null },
                            onScaleChanged = { scale ->
                                fontScaleEditorState = editorState.copy(
                                    scale = scale,
                                    followSystem = false
                                )
                            },
                            onFollowSystem = {
                                fontScaleEditorState = editorState.copy(
                                    scale = systemFontScaleForEditor(),
                                    followSystem = true
                                )
                            },
                            onSave = ::saveFontScaleEditor
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.setTitle(titleRes())
        if (view != null) refreshContent()
    }

    private fun screenSection(): ThemeConfigSection {
        return when (activity?.intent?.getStringExtra("configTag")) {
            ConfigTag.APPEARANCE_CONFIG -> ThemeConfigSection.APPEARANCE
            ConfigTag.INTERFACE_CONFIG -> ThemeConfigSection.INTERFACE
            else -> ThemeConfigSection.ALL
        }
    }

    private fun titleRes(): Int {
        return when (screenSection()) {
            ThemeConfigSection.APPEARANCE -> R.string.appearance_setting
            ThemeConfigSection.INTERFACE -> R.string.interface_layout_setting
            ThemeConfigSection.ALL -> R.string.theme_setting
        }
    }

    private fun refreshContent() {
        val launcherIcon = getPrefString(PreferKey.launcherIcon, DEFAULT_LAUNCHER_ICON)
            ?: DEFAULT_LAUNCHER_ICON
        val displayMetrics = resources.displayMetrics
        val statusBarHeightPx = requireContext().statusBarHeight
        screenState = ThemeConfigScreenState(
            themeModeGroup = NgThemeModeStore.currentGroup(requireContext()),
            presentationMode = NgThemeModeStore.current(requireContext()),
            standardThemeMode = NgThemeModeStore.standardThemeMode(requireContext()),
            internalThemeMode = NgThemeModeStore.lastInternalMode(requireContext()),
            softGradientColor = NgSoftGradientTheme.colorPreset(requireContext()),
            softGradientLightField = NgSoftGradientTheme.lightFieldPreset(requireContext()),
            visualSystem = NgVisualSystemStore.current(requireContext()),
            showLauncherIcon = Build.VERSION.SDK_INT >= 26,
            launcherIconRes = launcherIconResource(launcherIcon),
            floatingBottomBar = getPrefBoolean(PreferKey.useFloatingBottomBar, false),
            floatingBottomBarBottomDistancePx =
                FloatingBottomBarConfig.resolveBottomDistancePx(
                    storedDistancePx = AppConfig.floatingBottomBarBottomDistancePx,
                    density = displayMetrics.density
                ),
            floatingBottomBarTransparency = AppConfig.floatingBottomBarTransparency,
            drawerTransparency = AppConfig.ngDrawerTransparency,
            drawerPrimaryStrength = AppConfig.ngDrawerPrimaryStrength,
            drawerHorizontalMarginDp = AppConfig.ngDrawerHorizontalMarginDp,
            drawerCornerRadiusDp = AppConfig.ngDrawerCornerRadiusDp,
            bookshelfTopBarStyle = AppConfig.bookshelfTopBarStyle,
            bookshelfFloatingDockMinTopDistancePx =
                BookshelfFloatingDockConfig.MIN_TOP_DISTANCE_PX,
            bookshelfFloatingDockTopDistancePx =
                BookshelfFloatingDockConfig.resolveTopDistancePx(
                    storedDistancePx = AppConfig.bookshelfFloatingDockTopDistancePx,
                    screenWidthPx = displayMetrics.widthPixels,
                    density = displayMetrics.density,
                    statusBarHeightPx = statusBarHeightPx
                ),
            bookshelfFloatingDockTransparency =
                AppConfig.bookshelfFloatingDockTransparency,
            bookshelfFloatingDockSearchPosition =
                AppConfig.bookshelfFloatingDockSearchPosition,
            transparentAppBars = getPrefBoolean(PreferKey.tNavBar, false),
            autoRefresh = AppConfig.autoRefreshBook,
            onlyUpdateRead = AppConfig.onlyUpdateRead,
            defaultToRead = getPrefBoolean(PreferKey.defaultToRead, false),
            showDiscovery = AppConfig.showDiscovery,
            showRss = AppConfig.showRSS,
            defaultHomePage = AppConfig.defaultHomePage ?: "bookshelf",
            fontScaleSummary = getString(
                R.string.font_scale_summary,
                AppContextWrapper.getFontScale(requireContext())
            ),
            dayBackgroundSummary = backgroundSummary(
                imageKey = PreferKey.bgImage,
                blurKey = PreferKey.bgImageBlurring
            ),
            nightBackgroundSummary = backgroundSummary(
                imageKey = PreferKey.bgImageN,
                blurKey = PreferKey.bgImageNBlurring
            )
        )
    }

    private fun launcherIconResource(value: String): Int {
        return resources.getIdentifier(value, "mipmap", requireContext().packageName)
            .takeIf { it != 0 }
            ?: R.mipmap.ic_launcher
    }

    private fun backgroundSummary(imageKey: String, blurKey: String): String {
        val path = getPrefString(imageKey).takeUnless { it.isNullOrBlank() }
            ?: return getString(R.string.ng_theme_background_none)
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        return getString(
            R.string.ng_theme_background_summary,
            name,
            getPrefInt(blurKey, 0)
        )
    }

    private fun selectThemeModeGroup(group: NgThemeModeGroup) {
        if (group == screenState.themeModeGroup) return
        val presentationMode = if (group == NgThemeModeGroup.STANDARD) {
            NgThemePresentationMode.STANDARD
        } else {
            screenState.internalThemeMode
        }
        screenState = screenState.copy(
            themeModeGroup = group,
            presentationMode = presentationMode,
        )
        NgThemeModeStore.activateGroup(requireContext(), group)
    }

    private fun selectStandardThemeMode(mode: String) {
        if (
            screenState.themeModeGroup == NgThemeModeGroup.STANDARD &&
            mode == screenState.standardThemeMode
        ) {
            return
        }
        screenState = screenState.copy(
            themeModeGroup = NgThemeModeGroup.STANDARD,
            presentationMode = NgThemePresentationMode.STANDARD,
            standardThemeMode = mode,
        )
        NgThemeModeStore.activateStandard(requireContext(), mode)
    }

    private fun selectInternalThemeMode(mode: NgThemePresentationMode) {
        if (
            screenState.themeModeGroup == NgThemeModeGroup.INTERNAL &&
            mode == screenState.presentationMode
        ) {
            return
        }
        screenState = screenState.copy(
            themeModeGroup = NgThemeModeGroup.INTERNAL,
            presentationMode = mode,
            internalThemeMode = mode,
        )
        NgThemeModeStore.activateInternal(requireContext(), mode)
    }

    private fun selectSoftGradientColor(preset: NgSoftGradientColorPreset) {
        if (preset == screenState.softGradientColor) return
        screenState = screenState.copy(softGradientColor = preset)
        NgSoftGradientTheme.selectColor(requireContext(), preset)
    }

    private fun selectSoftGradientLightField(preset: NgSoftGradientLightFieldPreset) {
        if (preset == screenState.softGradientLightField) return
        screenState = screenState.copy(softGradientLightField = preset)
        NgSoftGradientTheme.selectLightField(requireContext(), preset)
    }

    private fun setVisualSystem(visualSystem: NgVisualSystem) {
        if (visualSystem == screenState.visualSystem) return
        NgVisualSystemStore.update(requireContext(), visualSystem)
        screenState = screenState.copy(visualSystem = visualSystem)
    }

    private fun showLauncherIconSelection() {
        LauncherIconSelectionSheet.show(
            context = requireContext(),
            currentValue = getPrefString(PreferKey.launcherIcon, DEFAULT_LAUNCHER_ICON)
                ?: DEFAULT_LAUNCHER_ICON
        ) { value ->
            putPrefString(PreferKey.launcherIcon, value)
            LauncherIconHelp.changeIcon(value)
            screenState = screenState.copy(launcherIconRes = launcherIconResource(value))
        }
    }

    private fun setFloatingBottomBar(enabled: Boolean) {
        if (screenState.floatingBottomBar == enabled) return
        putPrefBoolean(PreferKey.useFloatingBottomBar, enabled)
        screenState = screenState.copy(floatingBottomBar = enabled)
    }

    private fun setFloatingBottomBarBottomDistanceDraft(value: Int) {
        val normalized = FloatingBottomBarConfig.normalizeBottomDistancePx(value)
        if (normalized == screenState.floatingBottomBarBottomDistancePx) return
        screenState = screenState.copy(floatingBottomBarBottomDistancePx = normalized)
    }

    private fun saveFloatingBottomBarBottomDistance() {
        AppConfig.floatingBottomBarBottomDistancePx =
            screenState.floatingBottomBarBottomDistancePx
    }

    private fun setFloatingBottomBarTransparencyDraft(value: Int) {
        val normalized = FloatingBottomBarConfig.normalizeTransparencyPercent(value)
        if (normalized == screenState.floatingBottomBarTransparency) return
        screenState = screenState.copy(floatingBottomBarTransparency = normalized)
    }

    private fun saveFloatingBottomBarTransparency() {
        AppConfig.floatingBottomBarTransparency =
            screenState.floatingBottomBarTransparency
    }

    private fun setDrawerTransparencyDraft(value: Int) {
        val normalized = NgDrawerAppearanceConfig.normalizePercent(value)
        if (normalized == screenState.drawerTransparency) return
        screenState = screenState.copy(drawerTransparency = normalized)
    }

    private fun saveDrawerTransparency() {
        AppConfig.ngDrawerTransparency = screenState.drawerTransparency
    }

    private fun setDrawerPrimaryStrengthDraft(value: Int) {
        val normalized = NgDrawerAppearanceConfig.normalizePercent(value)
        if (normalized == screenState.drawerPrimaryStrength) return
        screenState = screenState.copy(drawerPrimaryStrength = normalized)
    }

    private fun saveDrawerPrimaryStrength() {
        AppConfig.ngDrawerPrimaryStrength = screenState.drawerPrimaryStrength
    }

    private fun setDrawerHorizontalMarginDraft(value: Int) {
        val normalized = NgDrawerAppearanceConfig.normalizeHorizontalMarginDp(value)
        if (normalized == screenState.drawerHorizontalMarginDp) return
        screenState = screenState.copy(drawerHorizontalMarginDp = normalized)
    }

    private fun saveDrawerHorizontalMargin() {
        AppConfig.ngDrawerHorizontalMarginDp = screenState.drawerHorizontalMarginDp
    }

    private fun setDrawerCornerRadiusDraft(value: Int) {
        val normalized = NgDrawerAppearanceConfig.normalizeCornerRadiusDp(value)
        if (normalized == screenState.drawerCornerRadiusDp) return
        screenState = screenState.copy(drawerCornerRadiusDp = normalized)
    }

    private fun saveDrawerCornerRadius() {
        AppConfig.ngDrawerCornerRadiusDp = screenState.drawerCornerRadiusDp
    }

    private fun setBookshelfTopBarStyle(style: BookshelfTopBarStyle) {
        if (style == screenState.bookshelfTopBarStyle) return
        AppConfig.bookshelfTopBarStyle = style
        screenState = screenState.copy(bookshelfTopBarStyle = style)
    }

    private fun setBookshelfFloatingDockTopDistanceDraft(value: Int) {
        val normalized = BookshelfFloatingDockConfig.normalizeTopDistancePx(value)
        if (normalized == screenState.bookshelfFloatingDockTopDistancePx) return
        screenState = screenState.copy(bookshelfFloatingDockTopDistancePx = normalized)
    }

    private fun saveBookshelfFloatingDockTopDistance() {
        AppConfig.bookshelfFloatingDockTopDistancePx =
            screenState.bookshelfFloatingDockTopDistancePx
    }

    private fun setBookshelfFloatingDockTransparencyDraft(value: Int) {
        val normalized = BookshelfFloatingDockConfig.normalizeTransparencyPercent(value)
        if (normalized == screenState.bookshelfFloatingDockTransparency) return
        screenState = screenState.copy(bookshelfFloatingDockTransparency = normalized)
    }

    private fun saveBookshelfFloatingDockTransparency() {
        AppConfig.bookshelfFloatingDockTransparency =
            screenState.bookshelfFloatingDockTransparency
    }

    private fun setBookshelfFloatingDockSearchPosition(
        position: BookshelfFloatingDockSearchPosition
    ) {
        if (position == screenState.bookshelfFloatingDockSearchPosition) return
        AppConfig.bookshelfFloatingDockSearchPosition = position
        screenState = screenState.copy(bookshelfFloatingDockSearchPosition = position)
    }

    private fun setTransparentAppBars(enabled: Boolean) {
        if (screenState.transparentAppBars == enabled) return
        putPrefBoolean(PreferKey.tNavBar, enabled)
        screenState = screenState.copy(transparentAppBars = enabled)
        ThemeConfig.applyTheme(requireContext())
        recreateActivities()
    }

    private fun setAutoRefresh(enabled: Boolean) {
        putPrefBoolean(PreferKey.autoRefresh, enabled)
        screenState = screenState.copy(autoRefresh = enabled)
    }

    private fun setOnlyUpdateRead(enabled: Boolean) {
        putPrefBoolean(PreferKey.onlyUpdateRead, enabled)
        screenState = screenState.copy(onlyUpdateRead = enabled)
    }

    private fun setDefaultToRead(enabled: Boolean) {
        putPrefBoolean(PreferKey.defaultToRead, enabled)
        screenState = screenState.copy(defaultToRead = enabled)
    }

    private fun setShowDiscovery(enabled: Boolean) {
        putPrefBoolean(PreferKey.showDiscovery, enabled)
        screenState = screenState.copy(showDiscovery = enabled)
        postEvent(EventBus.NOTIFY_MAIN, true)
    }

    private fun setShowRss(enabled: Boolean) {
        putPrefBoolean(PreferKey.showRss, enabled)
        screenState = screenState.copy(showRss = enabled)
        postEvent(EventBus.NOTIFY_MAIN, true)
    }

    private fun setDefaultHomePage(value: String) {
        putPrefString(PreferKey.defaultHomePage, value)
        screenState = screenState.copy(defaultHomePage = value)
    }

    private fun openFontScaleEditor() {
        val storedScale = getPrefInt(PreferKey.fontScale, 0)
        val followSystem = storedScale !in 8..16
        fontScaleEditorState = ThemeFontScaleEditorState(
            scale = if (followSystem) {
                systemFontScaleForEditor()
            } else {
                storedScale / 10f
            },
            followSystem = followSystem
        )
    }

    private fun saveFontScaleEditor() {
        val editorState = fontScaleEditorState ?: return
        putPrefInt(
            PreferKey.fontScale,
            if (editorState.followSystem) {
                0
            } else {
                (editorState.scale * 10f).roundToInt().coerceIn(8, 16)
            }
        )
        fontScaleEditorState = null
        recreateActivities()
    }

    private fun systemFontScaleForEditor(): Float {
        return (sysConfiguration.fontScale * 10f)
            .roundToInt()
            .coerceIn(8, 16) / 10f
    }

    private fun openBackgroundEditor(dark: Boolean) {
        val imageKey = if (dark) PreferKey.bgImageN else PreferKey.bgImage
        val blurKey = if (dark) PreferKey.bgImageNBlurring else PreferKey.bgImageBlurring
        backgroundEditorState = ThemeBackgroundEditorState(
            dark = dark,
            path = getPrefString(imageKey).takeUnless { it.isNullOrBlank() },
            blur = getPrefInt(blurKey, 0).coerceIn(0, 25)
        )
    }

    private fun selectBackgroundImage(dark: Boolean) {
        selectImage.launch(if (dark) requestCodeBgDark else requestCodeBgLight)
    }

    private fun updateBackgroundDraft(dark: Boolean, path: String) {
        lifecycleScope.launch {
            val editorState = backgroundEditorState ?: return@launch
            if (editorState.dark != dark) return@launch
            backgroundEditorState = editorState.copy(
                path = path,
                blur = if (path.endsWith(".9.png", ignoreCase = true)) 0 else editorState.blur
            )
        }
    }

    private fun saveBackgroundEditor() {
        val editorState = backgroundEditorState ?: return
        val imageKey = if (editorState.dark) PreferKey.bgImageN else PreferKey.bgImage
        val blurKey = if (editorState.dark) {
            PreferKey.bgImageNBlurring
        } else {
            PreferKey.bgImageBlurring
        }
        editorState.path.takeUnless { it.isNullOrBlank() }?.let { path ->
            putPrefString(imageKey, path)
        } ?: removePref(imageKey)
        putPrefInt(
            blurKey,
            if (editorState.path?.endsWith(".9.png", ignoreCase = true) == true) {
                0
            } else {
                editorState.blur.coerceIn(0, 25)
            }
        )
        backgroundEditorState = null
        onBackgroundChanged(editorState.dark)
    }

    private fun onBackgroundChanged(isNightTheme: Boolean) {
        view?.post {
            if (!isAdded) return@post
            refreshContent()
            if (AppConfig.isNightTheme == isNightTheme) {
                ThemeConfig.applyTheme(requireContext())
                recreateActivities()
            }
        }
    }

    private fun recreateActivities() {
        postEvent(EventBus.RECREATE, "")
    }

    private fun copyBgFromUri(uri: Uri, storageKey: String, success: (String) -> Unit) {
        if (uri.scheme?.lowercase() in listOf("http", "https")) {
            lifecycleScope.launch {
                kotlin.runCatching {
                    appCtx.toastOnUi("下载背景图片中...")
                    val analyzeUrl = AnalyzeUrl(uri.toString())
                    val url = analyzeUrl.urlNoQuery
                    var file = requireContext().externalFiles
                    val res = okHttpClient.newCallResponse(0) {
                        addHeaders(analyzeUrl.headerMap)
                        url(url)
                    }
                    val contentType = res.header("Content-Type") ?: "image/jpeg"
                    val imageType = when {
                        contentType.contains("png", ignoreCase = true) -> "png"
                        contentType.contains("gif", ignoreCase = true) -> "gif"
                        contentType.contains("webp", ignoreCase = true) -> "webp"
                        else -> "jpg"
                    }
                    val suffix = if (url.contains(".9.png", true)) {
                        ".9.png"
                    } else {
                        ".$imageType"
                    }
                    val fileName = MD5Utils.md5Encode(url) + suffix
                    file = FileUtils.createFileIfNotExist(file, storageKey, fileName)
                    res.body.byteStream().use { inputStream ->
                        FileOutputStream(file).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    if (isAdded && context != null) success(file.absolutePath)
                }.onFailure {
                    appCtx.toastOnUi(it.localizedMessage)
                }
            }
            return
        }
        readUri(uri) { fileDoc, inputStream ->
            kotlin.runCatching {
                var file = requireContext().externalFiles
                val suffix = if (fileDoc.name.contains(".9.png", true)) {
                    ".9.png"
                } else {
                    "." + fileDoc.name.substringAfterLast(".")
                }
                val fileName = uri.inputStream(requireContext()).getOrThrow().use {
                    MD5Utils.md5Encode(it) + suffix
                }
                file = FileUtils.createFileIfNotExist(file, storageKey, fileName)
                FileOutputStream(file).use {
                    inputStream.copyTo(it)
                }
                success(file.absolutePath)
            }.onFailure {
                appCtx.toastOnUi(it.localizedMessage)
            }
        }
    }

    private companion object {
        const val DEFAULT_LAUNCHER_ICON = "ic_launcher"
    }
}
