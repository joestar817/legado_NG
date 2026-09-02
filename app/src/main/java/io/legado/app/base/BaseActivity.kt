package io.legado.app.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NgThemeLibraryStore
import io.legado.app.help.config.NgThemeModeStore
import io.legado.app.help.config.NgThemePresentationMode
import io.legado.app.help.config.NgThemeRuntimeAssets
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.transparentNavBar
import io.legado.app.ui.book.read.aloud.ReadAloudMiniPlayer
import io.legado.app.ui.design.theme.NgThemeGradientDrawable
import io.legado.app.ui.design.theme.NgThemeGradientHostView
import io.legado.app.ui.design.theme.NgThemeSceneHostView
import io.legado.app.ui.design.theme.NgThemeResolver
import io.legado.app.ui.widget.NgMenuPopup
import io.legado.app.ui.widget.TitleBar
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyBackgroundTint
import io.legado.app.utils.applyOpenTint
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.windowSize

abstract class BaseActivity<VB : ViewBinding>(
    val fullScreen: Boolean = true,
    private val theme: Theme = Theme.Auto,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true,
    private val showOpenMenuIcon: Boolean = true
) : AppCompatActivity() {

    protected abstract val binding: VB

    protected open val bindNgToolbarMenu: Boolean = true

    private var ngThemeSceneHost: NgThemeSceneHostView? = null
    private var ngThemeScenePoster: ImageView? = null
    private var ngThemeGradientBackground: NgThemeGradientHostView? = null

    val isInMultiWindow: Boolean
        @SuppressLint("ObsoleteSdkInt")
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isInMultiWindowMode
            } else {
                false
            }
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppContextWrapper.wrap(newBase))
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        if (AppConst.menuViewNames.contains(name) && parent?.parent is FrameLayout) {
            (parent.parent as View).setBackgroundColor(backgroundColor)
        }
        val view = super.onCreateView(parent, name, context, attrs)
        if (view is TextView && !attrs.hasExplicitTypeface()) {
            NgThemeRuntimeAssets.applyAppTypeface(context, view)
        }
        return view
    }

    private fun AttributeSet.hasExplicitTypeface(): Boolean {
        val androidNamespace = "http://schemas.android.com/apk/res/android"
        return getAttributeValue(androidNamespace, "fontFamily") != null ||
            getAttributeValue(androidNamespace, "typeface") != null
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.disableAutoFill()
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        setContentView(createContentRoot(binding.root))
        upBackgroundImage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            findViewById<TitleBar>(R.id.title_bar)
                ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        }
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
        observeLiveBus()
        observeReadAloudMiniPlayer()
        onActivityCreated(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        setupSystemBar()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindow, fullScreen)
        setupSystemBar()
    }

    abstract fun onActivityCreated(savedInstanceState: Bundle?)

    private fun createContentRoot(content: View): View {
        if (!imageBg) return content
        val root = FrameLayout(this)
        val sceneSource = FrameLayout(this).apply {
            id = R.id.ng_liquid_glass_backdrop_source
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
        }
        val scenePoster = ImageView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val gradientBackground = NgThemeGradientHostView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            )
            scaleType = ImageView.ScaleType.FIT_XY
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val sceneHost = NgThemeSceneHostView(this)
        sceneSource.addView(gradientBackground)
        sceneSource.addView(scenePoster)
        sceneSource.addView(
            sceneHost,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            sceneSource,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            content,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        ngThemeScenePoster = scenePoster
        ngThemeSceneHost = sceneHost
        ngThemeGradientBackground = gradientBackground
        return root
    }

    final override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val bool = onCompatCreateOptionsMenu(menu)
        if (transparentNavBar) {
            menu.applyTint(this, NgThemeResolver.resolve(this).colors.onTopBar)
        } else {
            menu.applyTint(this, toolBarTheme)
        }
        if (bindNgToolbarMenu) {
            NgMenuPopup.bindToolbarMenu(
                context = this,
                toolbar = findViewById<TitleBar>(R.id.title_bar)?.toolbar,
                menu = menu,
                prepareMenu = {
                    onPrepareOptionsMenu(menu)
                    onMenuOpened(Window.FEATURE_OPTIONS_PANEL, menu)
                },
                onItemClick = { onCompatOptionsItemSelected(it) }
            )
        }
        return bool
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.applyOpenTint(this, showOpenMenuIcon)
        return super.onMenuOpened(featureId, menu)
    }

    open fun onCompatCreateOptionsMenu(menu: Menu) = super.onCreateOptionsMenu(menu)

    final override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onHomeNavigationSelected()
            return true
        }
        return onCompatOptionsItemSelected(item)
    }

    open fun onHomeNavigationSelected() {
        supportFinishAfterTransition()
    }

    open fun onCompatOptionsItemSelected(item: MenuItem) = super.onOptionsItemSelected(item)

    open fun initTheme() {
        when (theme) {
            Theme.Transparent -> setTheme(R.style.AppTheme_Transparent)
            Theme.Dark -> {
                setTheme(R.style.AppTheme_Dark)
               window.decorView.applyBackgroundTint(backgroundColor)
            }

            Theme.Light -> {
                setTheme(R.style.AppTheme_Light)
               window.decorView.applyBackgroundTint(backgroundColor)
            }

            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(R.style.AppTheme_Light)
                } else {
                    setTheme(R.style.AppTheme_Dark)
                }
               window.decorView.applyBackgroundTint(backgroundColor)
            }
        }
    }

    open fun upBackgroundImage() {
        if (imageBg) {
            try {
                val drawable = ThemeConfig.getBgImage(this, windowManager.windowSize)
                val gradientDrawable = ThemeConfig.getGradientBgImage(this)
                val usesDynamicScene =
                    NgThemeModeStore.current(this) == NgThemePresentationMode.STANDARD &&
                        NgThemeLibraryStore.activeTheme(this)
                            ?.sceneProfile
                            ?.sceneType() != null
                val scenePoster = ngThemeScenePoster
                ngThemeGradientBackground?.run {
                    setGradientDrawable(gradientDrawable as? NgThemeGradientDrawable)
                    visibility = if (drawable == null && gradientDrawable != null) {
                        View.VISIBLE
                    } else {
                        View.GONE
                    }
                }
                if (drawable != null && scenePoster != null) {
                    // Every image background belongs to the shared backdrop source. Dynamic scenes
                    // use the renderer's center-cover geometry; static themes keep the old fill.
                    window.decorView.setBackgroundColor(backgroundColor)
                    scenePoster.scaleType = if (usesDynamicScene) {
                        ImageView.ScaleType.CENTER_CROP
                    } else {
                        ImageView.ScaleType.FIT_XY
                    }
                    scenePoster.setImageDrawable(drawable)
                    scenePoster.visibility = View.VISIBLE
                } else {
                    scenePoster?.setImageDrawable(null)
                    scenePoster?.visibility = View.GONE
                    drawable?.let { window.decorView.background = it }
                }
                if (gradientDrawable != null) {
                    window.decorView.setBackgroundColor(backgroundColor)
                }
            } catch (_: OutOfMemoryError) {
                toastOnUi("背景图片太大,内存溢出")
            } catch (e: Exception) {
                AppLog.put("加载背景出错\n${e.localizedMessage}", e)
            }
        }
    }

    open fun setupSystemBar() {
        if (fullScreen && !isInMultiWindow) {
            fullScreen()
        }
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, fullScreen)
        if (toolBarTheme == Theme.Dark) {
            setLightStatusBar(false)
        } else if (toolBarTheme == Theme.Light) {
            setLightStatusBar(true)
        } else if (transparentNavBar) {
            setLightStatusBar(!AppConfig.isNightTheme)
        }
        upNavigationBarColor()
    }

    open fun upNavigationBarColor() {
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }

    open fun observeLiveBus() {
    }

    private fun observeReadAloudMiniPlayer() {
        observeEvent<Int>(io.legado.app.constant.EventBus.ALOUD_STATE) { state ->
            ReadAloudMiniPlayer.onReadAloudStateChanged(this, state)
        }
        observeEvent<Int>(io.legado.app.constant.EventBus.TTS_PROGRESS) {
            ReadAloudMiniPlayer.refresh(this)
        }
        observeEvent<Int>(io.legado.app.constant.EventBus.AUDIO_STATE) { state ->
            ReadAloudMiniPlayer.onAudioStateChanged(this, state)
        }
    }

    override fun onResume() {
        super.onResume()
        ngThemeGradientBackground?.setHostActive(true)
        ngThemeSceneHost?.run {
            val profile = NgThemeLibraryStore.activeTheme(this@BaseActivity)
                ?.sceneProfile
                ?.takeIf {
                    NgThemeModeStore.current(this@BaseActivity) ==
                        NgThemePresentationMode.STANDARD
                }
            bind(profile)
            setHostActive(profile != null)
        }
        ReadAloudMiniPlayer.attach(this)
    }

    override fun onPause() {
        ngThemeGradientBackground?.setHostActive(false)
        ngThemeSceneHost?.setHostActive(false)
        super.onPause()
    }

    override fun onDestroy() {
        ngThemeGradientBackground?.setHostActive(false)
        ngThemeSceneHost?.setHostActive(false)
        ngThemeScenePoster?.setImageDrawable(null)
        ngThemeScenePoster = null
        ngThemeGradientBackground?.setGradientDrawable(null)
        ngThemeGradientBackground = null
        ReadAloudMiniPlayer.detach(this)
        super.onDestroy()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            false
        }
    }

    override fun finish() {
        currentFocus?.hideSoftInput()
        super.finish()
    }
}
