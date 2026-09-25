package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadCatalogStyle
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.ui.widget.dialog.applyNgWindow
import io.legado.app.utils.canvasrecorder.CanvasRecorderFactory
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString

class MoreConfigDialog : BaseComposeDialogFragment() {

    private var selectedTab by mutableStateOf(ReadMoreConfigTab.INTERFACE)
    private var screenState by mutableStateOf<ReadMoreConfigUiState?>(null)
    private var themeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    private var bottomDialogRegistered = false
    private var toolbarEditorVisible by mutableStateOf(false)

    private fun changeToolbarEditorVisible(visible: Boolean) {
        toolbarEditorVisible = visible
        updateWindowHeight()
    }

    private fun updateWindowHeight() {
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            if (toolbarEditorVisible) (resources.displayMetrics.heightPixels * 0.76f).toInt()
            else READ_MORE_CONFIG_WINDOW_HEIGHT_DP.dpToPx(),
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("toolbarEditorVisible", toolbarEditorVisible)
    }

    private val readActivity: ReadBookActivity?
        get() = activity as? ReadBookActivity

    override fun onStart() {
        super.onStart()
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0.0f
                gravity = Gravity.BOTTOM
            }
            updateWindowHeight()
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        toolbarEditorVisible = savedInstanceState?.getBoolean("toolbarEditorVisible") == true
        if (!bottomDialogRegistered) {
            readActivity?.let {
                it.bottomDialog++
                bottomDialogRegistered = true
            }
        }
        refreshUi()
        themeSnapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        val actions = createActions()
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                themeSnapshot?.let { snapshot ->
                    NgAppTheme(
                        snapshot = snapshot,
                        updateSystemBars = false,
                    ) {
                        NgDismissibleDrawer(onDismiss = { dismissAllowingStateLoss() }) {
                            BackHandler(enabled = toolbarEditorVisible) { changeToolbarEditorVisible(false) }
                            if (toolbarEditorVisible) {
                                TextSelectionActionOrderPage(onBack = { changeToolbarEditorVisible(false) })
                            } else screenState?.let { state ->
                                ReadMoreConfigScreen(
                                    tab = selectedTab,
                                    state = state,
                                    actions = actions,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (bottomDialogRegistered) {
            readActivity?.let {
                it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0)
            }
            bottomDialogRegistered = false
        }
    }

    private fun createActions() = ReadMoreConfigActions(
        onTabSelected = { selectedTab = it },
        onBooleanChanged = ::changeBoolean,
        onValueChanged = ::changeValue,
        onAction = ::handleAction,
    )

    private fun changeBoolean(key: String, value: Boolean) {
        val context = requireContext()
        if (key == PreferKey.hideStatusBar && !value && ReadTipConfig.headerMode == 1) {
            ReadTipConfig.headerMode = 2
            ReadBookConfig.save()
        }
        val exclusiveKey = if (value) {
            when (key) {
                PreferKey.readBodyToLh -> PreferKey.paddingDisplayCutouts
                PreferKey.paddingDisplayCutouts -> PreferKey.readBodyToLh
                else -> null
            }
        } else {
            null
        }
        exclusiveKey?.let { otherKey ->
            val otherDefault = otherKey == PreferKey.readBodyToLh
            if (context.getPrefBoolean(otherKey, otherDefault)) {
                context.putPrefBoolean(otherKey, false)
                handlePreferenceChanged(otherKey, false)
            }
        }
        context.putPrefBoolean(key, value)
        handlePreferenceChanged(key, value)
        refreshUi()
    }

    private fun changeValue(key: String, value: String) {
        if (key == ReadMoreConfigKeys.BOOK_IMAGE_STYLE) {
            readActivity?.applyImageStyleConfig(value)
            refreshUi()
            return
        }
        requireContext().putPrefString(key, value)
        handlePreferenceChanged(key, null)
        refreshUi()
    }

    private fun handlePreferenceChanged(key: String, booleanValue: Boolean?) {
        when (key) {
            PreferKey.readBodyToLh -> {
                ReadBookConfig.readBodyToLh = booleanValue == true
                activity?.recreate()
            }

            PreferKey.hideStatusBar -> {
                ReadBookConfig.hideStatusBar = booleanValue == true
                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
            }

            PreferKey.keepLight -> postEvent(key, true)
            PreferKey.textSelectAble -> postEvent(key, booleanValue == true)
            PreferKey.screenOrientation -> readActivity?.setOrientation()

            PreferKey.textFullJustify,
            PreferKey.textBottomJustify,
            PreferKey.useZhLayout,
            PreferKey.adaptSpecialStyle -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(5))
            }

            PreferKey.showBrightnessView -> {
                postEvent(PreferKey.showBrightnessView, "")
            }

            PreferKey.doublePageHorizontal -> {
                ChapterProvider.upLayout()
                ReadBook.loadContent(false)
            }

            PreferKey.showReadTitleAddition,
            PreferKey.readBarStyleFollowPage -> {
                postEvent(EventBus.UPDATE_READ_ACTION_BAR, true)
            }

            PreferKey.progressBarBehavior -> postEvent(EventBus.UP_SEEK_BAR, true)
            PreferKey.noAnimScrollPage -> ReadBook.callBack?.upPageAnim()

            PreferKey.optimizeRender -> {
                ChapterProvider.upStyle()
                ReadBook.callBack?.upPageAnim(true)
                ReadBook.loadContent(false)
            }

            PreferKey.paddingDisplayCutouts -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    private fun handleAction(key: String) {
        when (key) {
            ReadMoreConfigKeys.TEXT_TOOLBAR -> changeToolbarEditorVisible(true)
            ReadMoreConfigKeys.CUSTOM_PAGE_KEY -> PageKeyDialog(requireContext()).show()

            ReadMoreConfigKeys.CLICK_REGIONAL_CONFIG -> {
                val activity = readActivity
                dismissAllowingStateLoss()
                activity?.window?.decorView?.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.showClickRegionalConfig()
                    }
                }
            }

            PreferKey.pageTouchSlop -> showPageTouchSlopDialog()
            PreferKey.pageTouchClick -> showPageTouchClickDialog()
        }
    }

    private fun showPageTouchSlopDialog() {
        showThresholdSliderDialog(
            title = getString(R.string.page_touch_slop_dialog_title),
            maxValue = 9999,
            initialValue = AppConfig.pageTouchSlop,
            valueLabel = {
                if (it == 0) {
                    getString(R.string.read_settings_system_default)
                } else {
                    getString(R.string.read_settings_pixels, it)
                }
            },
            onSave = {
                AppConfig.pageTouchSlop = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(4))
                refreshUi()
            },
        )
    }

    private fun showPageTouchClickDialog() {
        showThresholdSliderDialog(
            title = getString(R.string.page_touch_click_dialog_title),
            maxValue = 399,
            initialValue = AppConfig.pageTouchClick,
            valueLabel = { getString(R.string.read_settings_pixels, it) },
            onSave = {
                AppConfig.pageTouchClick = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(12))
                refreshUi()
            },
        )
    }

    private fun showThresholdSliderDialog(
        title: String,
        maxValue: Int,
        initialValue: Int,
        valueLabel: (Int) -> String,
        onSave: (Int) -> Unit,
    ) {
        val context = requireContext()
        val safeInitialValue = initialValue.coerceIn(0, maxValue)
        var pendingValue = safeInitialValue
        val sliderDialog = ComponentDialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setCanceledOnTouchOutside(true)
            setOnDismissListener {
                if (pendingValue != safeInitialValue) {
                    onSave(pendingValue)
                }
            }
        }
        val contentView = ComposeView(context).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnDetachedFromWindow
            )
            setContent {
                NgAppTheme(
                    snapshot = ReadDrawerStyle.themeSnapshot(context),
                    updateSystemBars = false,
                ) {
                    ReadThresholdSliderDialog(
                        title = title,
                        initialValue = safeInitialValue,
                        maxValue = maxValue,
                        valueLabel = valueLabel,
                        onDismiss = sliderDialog::dismiss,
                        onValueChanged = { pendingValue = it },
                    )
                }
            }
        }
        sliderDialog.setContentView(contentView)
        sliderDialog.show()
        sliderDialog.applyNgWindow(marginDp = 20, dimAmount = 0.14f)
        view?.let {
            ReadDrawerStyle.positionDialogAbove(sliderDialog, it, gapDp = 12)
        }
    }

    private fun refreshUi() {
        if (!isAdded) return
        val context = requireContext()
        val booleanDefaults = linkedMapOf(
            PreferKey.hideStatusBar to false,
            PreferKey.readBodyToLh to true,
            PreferKey.paddingDisplayCutouts to false,
            PreferKey.showBrightnessView to true,
            PreferKey.showReadTitleAddition to true,
            PreferKey.readBarStyleFollowPage to false,
            PreferKey.mouseWheelPage to true,
            PreferKey.volumeKeyPage to true,
            // Keep the legacy Preference UI default until its runtime default is discussed.
            PreferKey.volumeKeyPageOnPlay to false,
            PreferKey.keyPageOnLongPress to false,
            PreferKey.noAnimScrollPage to false,
            ReadMoreConfigKeys.DISABLE_RETURN_KEY to false,
            PreferKey.useZhLayout to false,
            PreferKey.textFullJustify to true,
            PreferKey.textBottomJustify to true,
            PreferKey.adaptSpecialStyle to true,
            PreferKey.autoChangeSource to true,
            PreferKey.textSelectAble to true,
            PreferKey.optimizeRender to false,
        )
        val values = mapOf(
            PreferKey.screenOrientation to context.getPrefString(
                PreferKey.screenOrientation,
                "0",
            ).orEmpty(),
            PreferKey.keepLight to context.getPrefString(PreferKey.keepLight, "0").orEmpty(),
            PreferKey.progressBarBehavior to context.getPrefString(
                PreferKey.progressBarBehavior,
                "page",
            ).orEmpty(),
            PreferKey.doublePageHorizontal to context.getPrefString(
                PreferKey.doublePageHorizontal,
                "0",
            ).orEmpty(),
            PreferKey.clickImgWay to context.getPrefString(PreferKey.clickImgWay, "0").orEmpty(),
            PreferKey.readCatalogStyle to context.getPrefString(
                PreferKey.readCatalogStyle,
                ReadCatalogStyle.CARD.value,
            ).orEmpty(),
            ReadMoreConfigKeys.BOOK_IMAGE_STYLE to currentImageStyle(),
        )
        screenState = ReadMoreConfigUiState(
            booleans = booleanDefaults.mapValues { (key, default) ->
                context.getPrefBoolean(key, default)
            },
            values = values,
            options = mapOf(
                PreferKey.screenOrientation to options(
                    R.array.screen_direction_title,
                    R.array.screen_direction_value,
                ),
                PreferKey.keepLight to options(
                    R.array.screen_time_out,
                    R.array.screen_time_out_value,
                ),
                PreferKey.progressBarBehavior to options(
                    R.array.progress_bar_behavior_title,
                    R.array.progress_bar_behavior_value,
                ),
                PreferKey.doublePageHorizontal to options(
                    R.array.double_page_title,
                    R.array.double_page_value,
                ),
                PreferKey.clickImgWay to options(
                    R.array.click_image_way_title,
                    R.array.click_image_way_value,
                ),
                PreferKey.readCatalogStyle to listOf(
                    ReadMoreConfigOption(
                        ReadCatalogStyle.CARD.value,
                        getString(R.string.read_catalog_style_card),
                    ),
                    ReadMoreConfigOption(
                        ReadCatalogStyle.COMPACT_SIDE.value,
                        getString(R.string.read_catalog_style_compact_side),
                    ),
                ),
                ReadMoreConfigKeys.BOOK_IMAGE_STYLE to imageStyleOptions(),
            ),
            actionValues = buildActionValues(),
            optimizeRenderSupported = CanvasRecorderFactory.isSupport,
        )
    }

    private fun options(entriesRes: Int, valuesRes: Int): List<ReadMoreConfigOption> {
        val entries = resources.getStringArray(entriesRes)
        val values = resources.getStringArray(valuesRes)
        return List(minOf(entries.size, values.size)) { index ->
            ReadMoreConfigOption(values[index], entries[index])
        }
    }

    private fun currentImageStyle(): String = when {
        ReadBook.book?.getImageStyle().equals(Book.imgStyleFull, true) -> Book.imgStyleFull
        ReadBook.book?.getImageStyle().equals(Book.imgStyleText, true) -> Book.imgStyleText
        ReadBook.book?.getImageStyle().equals(Book.imgStyleSingle, true) -> Book.imgStyleSingle
        else -> Book.imgStyleDefault
    }

    private fun imageStyleOptions() = listOf(
        ReadMoreConfigOption(
            Book.imgStyleDefault,
            getString(R.string.image_style_original),
        ),
        ReadMoreConfigOption(
            Book.imgStyleFull,
            getString(R.string.image_style_fit_width),
        ),
        ReadMoreConfigOption(
            Book.imgStyleText,
            getString(R.string.image_style_inline),
        ),
        ReadMoreConfigOption(
            Book.imgStyleSingle,
            getString(R.string.image_style_single_page),
        ),
    )

    private fun buildActionValues(): Map<String, String> {
        val values = mutableMapOf<String, String>()
        values[PreferKey.pageTouchSlop] = if (AppConfig.pageTouchSlop == 0) {
            getString(R.string.read_settings_system_default)
        } else {
            getString(R.string.read_settings_pixels, AppConfig.pageTouchSlop)
        }
        values[PreferKey.pageTouchClick] = getString(
            R.string.read_settings_pixels,
            AppConfig.pageTouchClick,
        )
        return values
    }
}
