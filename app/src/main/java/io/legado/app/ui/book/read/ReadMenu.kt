package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.content.Context
import android.database.ContentObserver
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.net.Uri
import android.provider.Settings
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.SeekBar
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.databinding.ViewReadMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.source.getSourceType
import io.legado.app.model.ReadBook
import io.legado.app.model.SourceCallBack
import io.legado.app.ui.book.read.config.showReadConfirmDialog
import io.legado.app.ui.browser.WebViewActivity
import io.legado.app.ui.design.components.compose.NgGlassSurface
import io.legado.app.ui.design.components.compose.NgGlassStyle
import io.legado.app.ui.design.components.compose.resolveNgFloatingGlassStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.ui.widget.NgActionPopup
import io.legado.app.ui.widget.NgActionPopupItem
import io.legado.app.ui.widget.NgIconActionPopup
import io.legado.app.ui.widget.seekbar.SeekBarChangeListener
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.activity
import io.legado.app.utils.applyNavigationBarPadding
import io.legado.app.utils.applyTint
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.invisible
import io.legado.app.utils.loadAnimation
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.startActivity
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.statusBarHeight
import io.legado.app.utils.visible
import splitties.views.onClick
import androidx.core.graphics.toColorInt
import io.legado.app.constant.BookType
import io.legado.app.utils.buildMainHandler

/**
 * 阅读界面菜单
 */
class ReadMenu @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {
    var canShowMenu: Boolean = false
    private val callBack: CallBack get() = activity as CallBack
    private val binding = ViewReadMenuBinding.inflate(LayoutInflater.from(context), this, true)
    private var confirmSkipToChapter: Boolean = false
    private var isMenuOutAnimating = false
    private var floatingToolExpansion by mutableStateOf<ReadFloatingToolExpansion?>(null)
    private var floatingBrightness by mutableIntStateOf(AppConfig.readBrightness)
    private var floatingBrightnessAutomatic by mutableStateOf(true)
    private var floatingAutoPage by mutableStateOf(false)
    private var floatingThemeMode by mutableStateOf(ReadBookConfig.currentThemeMode())
    private var floatingToolDock by mutableStateOf(
        ReadFloatingToolDock.fromStoredRight(AppConfig.brightnessVwPos)
    )
    private val menuTopIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_in)
    }
    private val menuTopOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_top_out)
    }
    private val menuBottomIn: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_in)
    }
    private val menuBottomOut: Animation by lazy {
        loadAnimation(context, R.anim.anim_readbook_bottom_out)
    }
    private val immersiveMenu: Boolean
        get() = AppConfig.readBarStyleFollowPage && ReadBookConfig.durConfig.curBgType() == 0
    private val useGradientThemeMenu: Boolean
        get() = !AppConfig.isEInkMode && ThemeConfig.isReadingNgBackgroundTheme(context)
    private var readMenuThemeSnapshot: NgThemeSnapshot = resolveReadMenuThemeSnapshot()

    private fun resolveReadMenuThemeSnapshot(): NgThemeSnapshot =
        ReadDrawerStyle.themeSnapshot(context)

    internal fun currentThemeSnapshot(): NgThemeSnapshot = readMenuThemeSnapshot

    internal fun currentFloatingGlassStyle(): NgGlassStyle = resolveNgFloatingGlassStyle(
        snapshot = readMenuThemeSnapshot,
        transparencyPercent = ReadFloatingAppearanceState.transparencyPercent,
        primaryStrengthPercent = ReadFloatingAppearanceState.primaryStrengthPercent,
        colorStyle = ReadFloatingAppearanceState.colorStyle,
    )

    private var bgColor: Int = if (useGradientThemeMenu) {
        readMenuThemeSnapshot.colors.surface
    } else if (immersiveMenu) {
        kotlin.runCatching {
            ReadBookConfig.durConfig.curBgStr().toColorInt()
        }.getOrDefault(readMenuThemeSnapshot.colors.surface)
    } else {
        readMenuThemeSnapshot.colors.surface
    }
    private var textColor: Int = if (useGradientThemeMenu) {
        readMenuThemeSnapshot.colors.onSurface
    } else if (immersiveMenu) {
        ReadBookConfig.durConfig.curTextColor()
    } else {
        readMenuThemeSnapshot.colors.onSurface
    }

    private var onMenuOutEnd: (() -> Unit)? = null
    private val showFloatingTools
        get() = context.getPrefBoolean(
            PreferKey.showBrightnessView,
            true
        )
    private val menuInListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            binding.tvCustomBtn.isGone = ReadBook.isLocalBook ||
                    ReadBook.bookSource?.customButton != true
            callBack.upSystemUiVisibility()
            upFloatingToolVisibility()
        }

        @SuppressLint("RtlHardcoded")
        override fun onAnimationEnd(animation: Animation) {
            binding.vwMenuBg.setOnClickListener { runMenuOut() }
            callBack.upSystemUiVisibility()
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }
    private val menuOutListener = object : Animation.AnimationListener {
        override fun onAnimationStart(animation: Animation) {
            isMenuOutAnimating = true
            binding.vwMenuBg.setOnClickListener(null)
        }

        override fun onAnimationEnd(animation: Animation) {
            this@ReadMenu.invisible()
            binding.titleBarContainer.invisible()
            binding.bottomMenu.invisible()
            canShowMenu = false
            isMenuOutAnimating = false
            onMenuOutEnd?.invoke()
            callBack.upSystemUiVisibility()
        }

        override fun onAnimationRepeat(animation: Animation) = Unit
    }

    init {
        initGlassSurfaces()
        initFloatingToolRail()
        initFloatingMenuInsets()
        initTopBarLayout()
        initView()
        upBrightnessState()
        bindEvent()
    }

    private fun initTopBarLayout() = binding.titleBar.run {
        val toolbarView = toolbar
        removeView(toolbarView)
        binding.topActionToolbarHost.addView(
            toolbarView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        toolbarView.navigationIcon = null
        toolbarView.background = null
        toolbarView.backgroundTintList = null
        toolbarView.minimumHeight = 0
        toolbarView.setContentInsetsRelative(0, 0)
        toolbarView.setPadding(0, 0, 0, 0)
    }

    private fun initView() = binding.run {
        initAnimation()
        seekReadPage.applyTint(
            color = readMenuThemeSnapshot.colors.primary,
            isDark = readMenuThemeSnapshot.isDark
        )
        tvCustomBtn.setColorFilter(readMenuThemeSnapshot.colors.primary)
        if (useGradientThemeMenu) {
            titleBar.setTextColor(textColor)
            titleBar.setColorFilter(textColor)
            tvChapterName.setTextColor(readMenuThemeSnapshot.colors.onSurfaceVariant)
            tvChapterUrl.setTextColor(readMenuThemeSnapshot.colors.onSurfaceVariant)
        } else if (immersiveMenu) {
            val lightTextColor = ColorUtils.withAlpha(ColorUtils.lightenColor(textColor), 0.75f)
            titleBar.setTextColor(textColor)
            titleBar.setBackgroundColor(bgColor)
            titleBar.setColorFilter(textColor)
            tvChapterName.setTextColor(lightTextColor)
            tvChapterUrl.setTextColor(lightTextColor)
        } else {
            titleBar.setTextColor(textColor)
            titleBar.setColorFilter(textColor)
            tvChapterName.setTextColor(readMenuThemeSnapshot.colors.onSurfaceVariant)
            tvChapterUrl.setTextColor(readMenuThemeSnapshot.colors.onSurfaceVariant)
        }
        tvBookTitle.setTextColor(textColor)
        updateSourceStateStyle()
        ivHeaderBack.setColorFilter(textColor)
        ivHeaderRefresh.setColorFilter(textColor)
        ivHeaderChangeSource.setColorFilter(textColor)
        ivHeaderDownload.setColorFilter(textColor)
        updateHeaderActionMode()
        if (AppConfig.isEInkMode) {
            readTopGlass.gone()
            readBottomGlass.gone()
            titleBar.setBackgroundResource(R.drawable.bg_eink_border_bottom)
            llBottomBg.setBackgroundResource(R.drawable.bg_eink_border_top)
        } else {
            readTopGlass.visible()
            readBottomGlass.visible()
            // TitleBar/AppBarLayout 默认会安装主题背景。这里必须移除 Drawable 与 tint，
            // 仅设置透明颜色仍可能让顶栏比底栏多叠一层主题表面色。
            titleBar.background = null
            titleBar.backgroundTintList = null
            bookHeaderContent.background = null
            titleBar.elevation = 0f
            titleBar.translationZ = 0f
            titleBar.stateListAnimator = null
            titleBar.outlineProvider = null
            llBottomBg.setBackgroundColor(Color.TRANSPARENT)
        }
        tvPre.setTextColor(textColor)
        tvNext.setTextColor(textColor)
        ivCatalog.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvCatalog.setTextColor(textColor)
        ivReadAloud.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvReadAloud.setTextColor(textColor)
        ivFont.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvFont.setTextColor(textColor)
        ivSetting.setColorFilter(textColor, PorterDuff.Mode.SRC_IN)
        tvSetting.setTextColor(textColor)
        if (AppConfig.showReadTitleBarAddition) {
            titleBarAddition.visible()
        } else {
            titleBarAddition.gone()
        }
        floatingToolDock = ReadFloatingToolDock.fromStoredRight(AppConfig.brightnessVwPos)
        upFloatingToolPos()
        /**
         * 确保视图不被导航栏遮挡
         */
        applyNavigationBarPadding()
    }

    fun reset() {
        ReadFloatingAppearanceState.refreshFromConfig()
        floatingThemeMode = ReadBookConfig.currentThemeMode()
        readMenuThemeSnapshot = resolveReadMenuThemeSnapshot()
        upColorConfig()
        initGlassSurfaces()
        initFloatingToolRail()
        initView()
    }

    fun refreshMenuColorFilter() {
        binding.tvBookTitle.setTextColor(textColor)
        binding.ivHeaderBack.setColorFilter(textColor)
        binding.ivHeaderRefresh.setColorFilter(textColor)
        binding.ivHeaderChangeSource.setColorFilter(textColor)
        binding.ivHeaderDownload.setColorFilter(textColor)
        updateSourceStateStyle()
        binding.titleBar.setColorFilter(textColor)
        binding.titleBar.toolbar.post {
            // overflow 图标由 Toolbar 在菜单创建后的布局阶段补建，立即着色时可能仍为空。
            // 下一帧再次应用阅读局部颜色，避免从主界面直接进入夜间阅读时沿用黑色图标。
            binding.titleBar.setColorFilter(textColor)
        }
    }

    private fun updateHeaderActionMode() = binding.run {
        val online = !ReadBook.isLocalBook
        tvSourceName.isVisible = online
        ivHeaderRefresh.isVisible = online
        ivHeaderChangeSource.isVisible = online
        ivHeaderDownload.isVisible = online
        topActionToolbarHost.updateLayoutParams<ConstraintLayout.LayoutParams> {
            width = if (online) 48.dpToPx() else 0
            startToEnd = if (online) {
                ConstraintLayout.LayoutParams.UNSET
            } else {
                R.id.iv_header_back
            }
            startToStart = ConstraintLayout.LayoutParams.UNSET
            marginStart = 0
        }
    }

    private fun upColorConfig() {
        bgColor = if (useGradientThemeMenu) {
            readMenuThemeSnapshot.colors.surface
        } else if (immersiveMenu) {
            kotlin.runCatching {
                ReadBookConfig.durConfig.curBgStr().toColorInt()
            }.getOrDefault(readMenuThemeSnapshot.colors.surface)
        } else {
            readMenuThemeSnapshot.colors.surface
        }
        textColor = if (useGradientThemeMenu) {
            readMenuThemeSnapshot.colors.onSurface
        } else if (immersiveMenu) {
            ReadBookConfig.durConfig.curTextColor()
        } else {
            readMenuThemeSnapshot.colors.onSurface
        }
    }

    private fun initGlassSurfaces() = binding.run {
        val snapshot = readMenuThemeSnapshot
        readTopGlass.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow
        )
        readTopGlass.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                NgGlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    style = readFloatingGlassStyle()
                ) {}
            }
        }
        readBottomGlass.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow
        )
        readBottomGlass.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                NgGlassSurface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(12.dp),
                    style = readFloatingGlassStyle()
                ) {}
            }
        }
    }

    private fun initFloatingToolRail() = binding.run {
        val snapshot = readMenuThemeSnapshot
        readFloatingTools.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnDetachedFromWindow
        )
        readFloatingTools.setContent {
            NgAppTheme(snapshot = snapshot, updateSystemBars = false) {
                ReadFloatingToolRail(
                    dockSide = floatingToolDock,
                    expansion = floatingToolExpansion,
                    brightness = floatingBrightness,
                    brightnessAutomatic = floatingBrightnessAutomatic,
                    autoPage = floatingAutoPage,
                    nightMode = ReadBookConfig.isNightTheme,
                    themeMode = floatingThemeMode,
                    onExpansionChange = { floatingToolExpansion = it },
                    onBrightnessChange = { value ->
                        floatingBrightness = value.coerceIn(0, 255)
                        if (!floatingBrightnessAutomatic) {
                            setScreenBrightness(floatingBrightness.toFloat())
                        }
                    },
                    onBrightnessChangeFinished = {
                        AppConfig.readBrightness = floatingBrightness
                    },
                    onToggleBrightnessAutomatic = {
                        context.putPrefBoolean("brightnessAuto", !brightnessAuto())
                        upBrightnessState()
                    },
                    onSearch = {
                        floatingToolExpansion = null
                        runMenuOut { callBack.openSearchDrawer(null) }
                    },
                    onReplace = {
                        floatingToolExpansion = null
                        callBack.openReplaceRule()
                    },
                    onAutoPage = {
                        floatingToolExpansion = null
                        runMenuOut { callBack.autoPage() }
                    },
                    onThemeModeSelected = { mode ->
                        if (mode != floatingThemeMode) {
                            floatingThemeMode = mode
                            if (ReadBookConfig.selectThemeMode(mode)) {
                                callBack.onReadThemeChanged()
                            }
                        }
                    },
                    onAiPurify = {
                        floatingToolExpansion = null
                        runMenuOut { callBack.onClickAiPurifyChapter() }
                    },
                    onAiSettings = {
                        floatingToolExpansion = null
                        runMenuOut { callBack.onOpenAiPurifySettings() }
                    },
                    onToggleDockSide = {
                        floatingToolExpansion = null
                        floatingToolDock = floatingToolDock.toggled()
                        AppConfig.brightnessVwPos = floatingToolDock.isRight
                        upFloatingToolPos()
                    }
                )
            }
        }
    }

    private fun initFloatingMenuInsets() = binding.run {
        updateFloatingMenuTopMargin()
        titleBarContainer.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            val statusBars = WindowInsetsCompat.Type.statusBars()
            val visibleTopInset = if (windowInsets.isVisible(statusBars)) {
                windowInsets.getInsets(statusBars).top
            } else {
                0
            }
            val fallbackInset = if (ReadBookConfig.hideStatusBar) {
                0
            } else {
                context.statusBarHeight
            }
            updateFloatingMenuTopMargin(maxOf(visibleTopInset, fallbackInset))
            windowInsets
        }
    }

    private fun updateFloatingMenuTopMargin(
        statusBarInset: Int = if (ReadBookConfig.hideStatusBar) 0 else context.statusBarHeight
    ) {
        binding.titleBarContainer.updateLayoutParams<ConstraintLayout.LayoutParams> {
            topMargin = statusBarInset + 4.dpToPx()
        }
    }

    fun upBrightnessState() {
        floatingBrightnessAutomatic = brightnessAuto()
        floatingBrightness = AppConfig.readBrightness
        setScreenBrightness(AppConfig.readBrightness.toFloat())
    }

    fun upFloatingToolVisibility() {
        if (!showFloatingTools) {
            floatingToolExpansion = null
        }
        binding.readFloatingTools.isVisible = showFloatingTools && isVisible
    }

    /**
     * 系统亮度监听，在高阳光亮度时启用
     */
    private var contentObserver: ContentObserver? = null
    /**
     * 设置屏幕亮度
     */
    fun setScreenBrightness(value: Float) {
        activity?.run {
            fun setBrightness(value: Float) {
                val params = window.attributes
                params.screenBrightness = value
                window.attributes = params
            }
            val autoBrightness = BRIGHTNESS_OVERRIDE_NONE
            if (brightnessAuto() || value == autoBrightness) {
                setBrightness(autoBrightness)
                return
            }
            val brightness = if (value < 1f) 0.004f else value / 255f
            var isSunMax = false
            if (brightness == 1f) {
                val sysBrightness = getCurrentBrightness(context)
                if (sysBrightness == 255) {
                    isSunMax = true
                }
            }
            if (isSunMax) {
                contentObserver = object : ContentObserver(buildMainHandler()) {
                    override fun onChange(selfChange: Boolean, uri: Uri?) {
                        super.onChange(selfChange, uri)
                        if (contentObserver == null) return
                        if (uri == Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)) {
                            val sysBrightness = getCurrentBrightness(context)
                            if (sysBrightness < 200) {
                                setBrightness(brightness)
                                contentObserver?.let {
                                    context.contentResolver.unregisterContentObserver(it)
                                }
                                contentObserver = null
                            } else if (sysBrightness < 255) {
                                setBrightness(brightness)
                            } else {
                                setBrightness(autoBrightness)
                            }
                        }
                    }
                }
                val brightnessUri = Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS)
                context.contentResolver.registerContentObserver(
                    brightnessUri,
                    false,
                    contentObserver!!
                )
                setBrightness(autoBrightness)
            } else {
                setBrightness(brightness)
            }
        }
    }

    /**
     * 获取系统亮度值
     */
    private fun getCurrentBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS
            )
        } catch (_: Settings.SettingNotFoundException) {
            -1
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        contentObserver?.let {
            context.contentResolver.unregisterContentObserver(it)
            contentObserver = null
        }
    }

    fun runMenuIn(anim: Boolean = !AppConfig.isEInkMode) {
        updateFloatingMenuTopMargin()
        callBack.onMenuShow()
        this.visible()
        binding.titleBarContainer.visible()
        binding.bottomMenu.visible()
        if (anim) {
            binding.titleBarContainer.startAnimation(menuTopIn)
            binding.bottomMenu.startAnimation(menuBottomIn)
        } else {
            menuInListener.onAnimationStart(menuBottomIn)
            menuInListener.onAnimationEnd(menuBottomIn)
        }
    }

    fun runMenuOut(anim: Boolean = !AppConfig.isEInkMode, onMenuOutEnd: (() -> Unit)? = null) {
        if (isMenuOutAnimating) {
            return
        }
        floatingToolExpansion = null
        callBack.onMenuHide()
        this.onMenuOutEnd = onMenuOutEnd
        if (this.isVisible) {
            if (anim) {
                binding.titleBarContainer.startAnimation(menuTopOut)
                binding.bottomMenu.startAnimation(menuBottomOut)
            } else {
                menuOutListener.onAnimationStart(menuBottomOut)
                menuOutListener.onAnimationEnd(menuBottomOut)
            }
        }
    }

    private fun brightnessAuto(): Boolean {
        return context.getPrefBoolean("brightnessAuto", true)
    }

    private fun bindEvent() = binding.run {
        vwMenuBg.setOnClickListener { runMenuOut() }
        tvBookTitle.setOnClickListener {
            callBack.openBookInfoActivity()
        }
        ivHeaderBack.setOnClickListener {
            (activity as? ReadBookActivity)?.onHomeNavigationSelected()
        }
        val chapterViewClickListener = OnClickListener {
            if (ReadBook.isLocalBook) {
                return@OnClickListener
            }
            val chapterUrl = tvChapterUrl.tag as? String ?: tvChapterUrl.text.toString()
            if (AppConfig.readUrlInBrowser) {
                context.openUrl(chapterUrl.substringBefore(",{"))
            } else {
                Coroutine.async {
                    context.startActivity<WebViewActivity> {
                        val bookSource = ReadBook.bookSource
                        putExtra("title", tvChapterName.text)
                        putExtra("url", chapterUrl)
                        putExtra("sourceOrigin", bookSource?.bookSourceUrl)
                        putExtra("sourceName", bookSource?.bookSourceName)
                        putExtra("sourceType", bookSource?.getSourceType())
                    }
                }
            }
        }
        val chapterViewLongClickListener = OnLongClickListener {
            if (ReadBook.isLocalBook) {
                return@OnLongClickListener true
            }
            showReadConfirmDialog(
                context = context,
                title = context.getString(R.string.open_fun),
                message = context.getString(R.string.use_browser_open),
                confirmLabel = context.getString(R.string.yes),
                cancelLabel = context.getString(R.string.no),
                onConfirm = {
                    AppConfig.readUrlInBrowser = true
                },
                onCancel = {
                    AppConfig.readUrlInBrowser = false
                },
            )
            true
        }
        tvChapterName.setOnClickListener(chapterViewClickListener)
        tvChapterName.setOnLongClickListener(chapterViewLongClickListener)
        tvChapterUrl.setOnClickListener(chapterViewClickListener)
        tvChapterUrl.setOnLongClickListener(chapterViewLongClickListener)
        ivHeaderRefresh.setOnClickListener {
            callBack.onHeaderRefresh()
        }
        ivHeaderRefresh.setOnLongClickListener {
            callBack.showReadRefreshMenu(it)
            true
        }
        ivHeaderChangeSource.setOnClickListener {
            callBack.onHeaderChangeSource()
        }
        ivHeaderChangeSource.setOnLongClickListener {
            callBack.showReadChangeSourceMenu(it)
            true
        }
        ivHeaderDownload.setOnClickListener {
            callBack.onHeaderDownload()
        }
        tvSourceName.setOnClickListener {
            showSourceActionPopup(it)
        }
        tvCustomBtn.setOnClickListener {
            val book = ReadBook.book ?: return@setOnClickListener
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
            activity?.let { activity ->
                SourceCallBack.callBackBtn(
                    activity,
                    SourceCallBack.CLICK_CUSTOM_BUTTON,
                    ReadBook.bookSource,
                    book,
                    chapter,
                    BookType.text
                )
            }
        }
        tvCustomBtn.setOnLongClickListener {
            val book = ReadBook.book ?: return@setOnLongClickListener true
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
            activity?.let { activity ->
                SourceCallBack.callBackBtn(
                    activity,
                    SourceCallBack.LONG_CLICK_CUSTOM_BUTTON,
                    ReadBook.bookSource,
                    book,
                    chapter,
                    BookType.text
                )
            }
            true
        }
        //阅读进度
        seekReadPage.setOnSeekBarChangeListener(object : SeekBarChangeListener {

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                binding.vwMenuBg.setOnClickListener(null)
            }

            override fun onStopTrackingTouch(seekBar: SeekBar) {
                binding.vwMenuBg.setOnClickListener { runMenuOut() }
                when (AppConfig.progressBarBehavior) {
                    "page" -> ReadBook.skipToPage(seekBar.progress)
                    "chapter" -> {
                        if (confirmSkipToChapter) {
                            callBack.skipToChapter(seekBar.progress)
                        } else {
                            showReadConfirmDialog(
                                context = context,
                                title = "章节跳转确认",
                                message = "确定要跳转章节吗？",
                                confirmLabel = context.getString(R.string.yes),
                                cancelLabel = context.getString(R.string.no),
                                onConfirm = {
                                    confirmSkipToChapter = true
                                    callBack.skipToChapter(seekBar.progress)
                                },
                                onCancel = {
                                    upSeekBar()
                                },
                                onOutsideDismiss = {
                                    upSeekBar()
                                },
                            )
                        }
                    }
                }
            }

        })

        //上一章
        tvPre.setOnClickListener { ReadBook.moveToPrevChapter(upContent = true, toLast = false) }

        //下一章
        tvNext.setOnClickListener { ReadBook.moveToNextChapter(true) }

        //目录
        llCatalog.setOnClickListener {
            runMenuOut {
                callBack.openChapterList()
            }
        }

        //朗读
        llReadAloud.setOnClickListener {
            runMenuOut {
                callBack.onClickReadAloud()
            }
        }
        //界面
        llFont.setOnClickListener {
            runMenuOut {
                callBack.showReadStyle()
            }
        }

        //设置
        llSetting.setOnClickListener {
            runMenuOut {
                callBack.showMoreSetting()
            }
        }
    }

    private fun initAnimation() {
        menuTopIn.setAnimationListener(menuInListener)
        menuTopOut.setAnimationListener(menuOutListener)
    }

    fun upBookView() {
        binding.titleBar.title = null
        updateHeaderActionMode()
        binding.tvBookTitle.text = ReadBook.book?.name.orEmpty()
        val sourceName = ReadBook.bookSource?.bookSourceName
            ?: ReadBook.book?.originName
            ?: context.getString(R.string.book_source)
        binding.tvSourceName.text = sourceName
        updateSourceStateStyle()
        ReadBook.curTextChapter?.let {
            binding.tvChapterName.text = it.title
            binding.tvChapterName.visible()
            if (!ReadBook.isLocalBook) {
                binding.tvChapterUrl.tag = it.chapter.getAbsoluteURL()
                binding.tvChapterUrl.text = null
                binding.tvChapterUrl.gone()
            } else {
                binding.tvChapterUrl.tag = null
                binding.tvChapterUrl.gone()
            }
            upSeekBar()
            binding.tvPre.isEnabled = ReadBook.durChapterIndex != 0
            binding.tvNext.isEnabled = ReadBook.durChapterIndex != ReadBook.simulatedChapterSize - 1
        } ?: let {
            binding.tvChapterName.gone()
            binding.tvChapterUrl.tag = null
            binding.tvChapterUrl.gone()
        }
    }

    fun upSeekBar() {
        binding.seekReadPage.apply {
            when (AppConfig.progressBarBehavior) {
                "page" -> {
                    ReadBook.curTextChapter?.let {
                        max = it.pageSize.minus(1)
                        progress = ReadBook.durPageIndex
                    }
                }

                "chapter" -> {
                    max = ReadBook.simulatedChapterSize - 1
                    progress = ReadBook.durChapterIndex
                }
            }
        }
    }

    fun setSeekPage(seek: Int) {
        binding.seekReadPage.progress = seek
    }

    fun setAutoPage(autoPage: Boolean) {
        floatingAutoPage = autoPage
    }

    private fun upFloatingToolPos() {
        binding.readFloatingTools.updateLayoutParams<ConstraintLayout.LayoutParams> {
            startToStart = ConstraintLayout.LayoutParams.UNSET
            startToEnd = ConstraintLayout.LayoutParams.UNSET
            endToStart = ConstraintLayout.LayoutParams.UNSET
            endToEnd = ConstraintLayout.LayoutParams.UNSET
            if (floatingToolDock.isRight) {
                leftToLeft = ConstraintLayout.LayoutParams.UNSET
                rightToRight = ConstraintLayout.LayoutParams.PARENT_ID
            } else {
                leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID
                rightToRight = ConstraintLayout.LayoutParams.UNSET
            }
        }
    }

    private fun showSourceActionPopup(anchor: View) {
        val showLogin = !ReadBook.bookSource?.loginUrl.isNullOrEmpty()
        val showChapterPay = showLogin
                && ReadBook.curTextChapter?.isVip == true
                && ReadBook.curTextChapter?.isPay != true
        showSourceActionPopup(showLogin, showChapterPay, anchor)
    }

    private fun showSourceActionPopup(
        showLogin: Boolean,
        showChapterPay: Boolean,
        anchor: View
    ) {
        val sourceEnabled = ReadBook.bookSource?.enabled != false
        val items = buildList {
            if (showLogin) {
                add(NgActionPopupItem(R.id.menu_login, R.string.login, R.drawable.ic_lock_outline))
            }
            add(NgActionPopupItem(R.id.menu_edit_source, R.string.edit_book_source, R.drawable.ic_edit))
            add(
                NgActionPopupItem(
                    itemId = if (sourceEnabled) R.id.menu_disable_source else R.id.menu_enable,
                    titleRes = if (sourceEnabled) R.string.disable_book_source else R.string.enable,
                    iconRes = if (sourceEnabled) {
                        R.drawable.ic_block_outline
                    } else {
                        R.drawable.ic_check_circle_outline
                    }
                )
            )
            if (showChapterPay) {
                add(NgActionPopupItem(R.id.menu_chapter_pay, R.string.chapter_pay, R.drawable.ic_check))
            }
        }
        NgIconActionPopup(context, items, readMenuThemeSnapshot) {
            when (it.itemId) {
                R.id.menu_login -> callBack.showLogin()
                R.id.menu_chapter_pay -> callBack.payAction()
                R.id.menu_edit_source -> callBack.openSourceEditActivity()
                R.id.menu_disable_source -> {
                    callBack.setSourceEnabled(false)
                    updateSourceStateStyle(false)
                }

                R.id.menu_enable -> {
                    callBack.setSourceEnabled(true)
                    updateSourceStateStyle(true)
                }
            }
        }.show(anchor)
    }

    private fun updateSourceStateStyle(
        enabled: Boolean = ReadBook.bookSource?.enabled != false
    ) = binding.tvSourceName.run {
        setTextColor(
            if (enabled) readMenuThemeSnapshot.colors.secondary
            else readMenuThemeSnapshot.colors.onSurfaceVariant
        )
        paintFlags = if (enabled) {
            paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        contentDescription = if (enabled) {
            text
        } else {
            "$text, ${context.getString(R.string.disabled)}"
        }
    }

    interface CallBack {
        fun autoPage()
        fun openReplaceRule()
        fun openChapterList()
        fun openSearchDrawer(searchWord: String?)
        fun openSourceEditActivity()
        fun openBookInfoActivity()
        fun showReadStyle()
        fun showMoreSetting()
        fun upSystemUiVisibility()
        fun onReadThemeChanged()
        fun onClickReadAloud()
        fun showLogin()
        fun payAction()
        fun setSourceEnabled(enabled: Boolean)
        fun onHeaderChangeSource()
        fun onHeaderRefresh()
        fun onHeaderDownload()
        fun showReadChangeSourceMenu(anchor: View)
        fun showReadRefreshMenu(anchor: View)
        fun skipToChapter(index: Int)
        fun onMenuShow()
        fun onMenuHide()
        fun onClickAiPurifyChapter()
        fun onOpenAiPurifySettings()
    }

}
