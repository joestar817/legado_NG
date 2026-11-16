package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.PageKeyBindings
import io.legado.app.help.config.savePageKeyBindings
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadBottomTool
import io.legado.app.help.config.ReadBottomToolbarSettings
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadCatalogDialog
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.page.provider.ChapterProvider
import io.legado.app.ui.book.read.ReadDrawerContentTheme
import io.legado.app.ui.design.components.compose.NgDismissibleDrawer
import io.legado.app.ui.design.theme.NgThemeSnapshot
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.getPrefString
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString

class MoreConfigDialog : BaseComposeDialogFragment() {

    companion object {
        private const val ARG_PAGE_TURNING = "pageTurning"
        private const val ARG_BOTTOM_TOOLBAR = "bottomToolbar"
        private const val STATE_OPENED_NESTED_DRAWER = "openedNestedDrawer"

        fun forPageTurning() = MoreConfigDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_PAGE_TURNING, true) }
        }
    }

    private val pageTurning: Boolean
        get() = arguments?.getBoolean(ARG_PAGE_TURNING) == true
    private val bottomToolbar: Boolean
        get() = arguments?.getBoolean(ARG_BOTTOM_TOOLBAR) == true
    private val isSettingsRoot: Boolean
        get() = !pageTurning && !bottomToolbar

    private var screenState by mutableStateOf<ReadMoreConfigUiState?>(null)
    private var themeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    private var hiddenTools by mutableStateOf(emptySet<ReadBottomTool>())
    private var bottomToolOrder by mutableStateOf<List<ReadBottomTool>>(ReadBottomTool.entries)
    private var bottomDialogRegistered = false
    private var openedNestedDrawer = false
    private val nestedDrawerCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentDetached(fm: FragmentManager, fragment: Fragment) {
            if (openedNestedDrawer && fragment is DialogFragment) {
                // A drawer can dismiss itself to open a preview. Let that transaction run first.
                view?.post { returnToMoreIfIdle() }
            }
        }
    }
    private val pageKeyCapture = PageKeyCaptureController { direction, keyCode ->
        screenState?.let { state ->
            val bindings = PageKeyBindings(
                previous = state.value(PreferKey.prevKeys),
                next = state.value(PreferKey.nextKeys),
            ).bind(direction, keyCode)
            savePageKeys(bindings.previous, bindings.next)
        }
    }

    private val readActivity: ReadBookActivity?
        get() = activity as? ReadBookActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedNestedDrawer = savedInstanceState?.getBoolean(STATE_OPENED_NESTED_DRAWER) == true
        if (isSettingsRoot) {
            parentFragmentManager.registerFragmentLifecycleCallbacks(nestedDrawerCallbacks, false)
            parentFragmentManager.setFragmentResultListener(ReadCatalogDialog.MORE_NAVIGATION, this) { _, _ ->
                if (openedNestedDrawer) {
                    openedNestedDrawer = false
                    dismissAllowingStateLoss()
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_OPENED_NESTED_DRAWER, openedNestedDrawer)
    }

    override fun onStart() {
        super.onStart()
        if (pageTurning) {
            dialog?.setOnKeyListener { _, _, event -> pageKeyCapture.onKeyEvent(event) }
        }
        dialog?.window?.run {
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setBackgroundDrawableResource(R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            attributes = attributes.apply {
                dimAmount = 0.0f
                gravity = Gravity.BOTTOM
            }
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        if (openedNestedDrawer) {
            dialog?.hide()
            view?.post { returnToMoreIfIdle() }
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (!openedNestedDrawer) setBottomDialogRegistered(true)
        hiddenTools = ReadBottomToolbarSettings.hiddenTools(requireContext())
        if (bottomToolbar) {
            bottomToolOrder = ReadBottomToolbarSettings.order(requireContext())
        } else {
            refreshUi()
        }
        themeSnapshot = ReadDrawerStyle.themeSnapshot(requireContext())
        val actions = createActions()
        (view as ComposeView).apply {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                themeSnapshot?.let { snapshot ->
                    ReadDrawerContentTheme(snapshot = snapshot) {
                        NgDismissibleDrawer(onDismiss = { dismissAllowingStateLoss() }) {
                            if (bottomToolbar) {
                                ReadBottomToolbarConfigScreen(
                                    tools = bottomToolOrder,
                                    hiddenTools = hiddenTools,
                                    onMoveTool = ::moveBottomTool,
                                    onVisibilityChanged = ::changeBottomToolVisibility,
                                    onBack = { dismissAllowingStateLoss() },
                                )
                            } else screenState?.let { state ->
                                if (pageTurning) ReadPageTurningScreen(state = state, actions = actions)
                                else ReadMoreConfigScreen(
                                    state = state,
                                    actions = actions,
                                    hiddenTools = hiddenTools,
                                    onOpenBottomToolbar = ::openBottomToolbar,
                                    onOpenShortcut = ::openToolbarShortcut,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        pageKeyCapture.cancel()
        super.onDismiss(dialog)
        if (pageTurning) ReadBookConfig.save()
        setBottomDialogRegistered(false)
    }

    override fun onDestroy() {
        if (isSettingsRoot) {
            parentFragmentManager.unregisterFragmentLifecycleCallbacks(nestedDrawerCallbacks)
        }
        super.onDestroy()
    }

    private fun setBottomDialogRegistered(registered: Boolean) {
        val host = readActivity ?: return
        if (bottomDialogRegistered == registered) return
        bottomDialogRegistered = registered
        val delta = if (registered) 1 else -1
        host.bottomDialog = (host.bottomDialog + delta).coerceAtLeast(0)
    }

    private fun changeBottomToolVisibility(tool: ReadBottomTool, visible: Boolean) {
        if (tool == ReadBottomTool.MORE) return
        hiddenTools = if (visible) hiddenTools - tool else hiddenTools + tool
        ReadBottomToolbarSettings.saveHiddenTools(requireContext(), hiddenTools)
    }

    private fun moveBottomTool(from: Int, to: Int) {
        val order = bottomToolOrder.toMutableList().apply { add(to, removeAt(from)) }
        bottomToolOrder = order
        ReadBottomToolbarSettings.saveOrder(requireContext(), order)
    }

    private fun openBottomToolbar() {
        val settings = MoreConfigDialog().apply { arguments = bundleOf(ARG_BOTTOM_TOOLBAR to true) }
        showNestedDrawer(settings, "readBottomToolbar")
    }

    private fun openToolbarShortcut(tool: ReadBottomTool) {
        val host = readActivity ?: return
        when (tool) {
            ReadBottomTool.CATALOG -> host.createReadCatalogDialog()?.let { catalog ->
                catalog.arguments = bundleOf(ReadCatalogDialog.ARG_FROM_MORE to true)
                showNestedDrawer(catalog, "readCatalog")
            }
            ReadBottomTool.READ_ALOUD -> {
                dismissAllowingStateLoss()
                host.onClickReadAloud()
            }
            ReadBottomTool.INTERFACE -> showNestedDrawer(ReadStyleDialog())
            ReadBottomTool.PAGE_TURNING -> showNestedDrawer(forPageTurning(), "readMorePageTurning")
            ReadBottomTool.CONTENT -> showNestedDrawer(ReadContentSettingsDialog())
            ReadBottomTool.FLOATING -> showNestedDrawer(ReadStyleDialog.forFloating())
            ReadBottomTool.SCROLL, ReadBottomTool.MORE -> Unit
        }
    }

    private fun showNestedDrawer(target: DialogFragment, tag: String = target.javaClass.simpleName) {
        val manager = parentFragmentManager
        if (openedNestedDrawer || manager.isStateSaved || hasOtherDialog()) return
        openedNestedDrawer = true
        dialog?.hide()
        setBottomDialogRegistered(false)
        runCatching { target.showNow(manager, tag) }.onFailure { error ->
            AppLog.put("打开阅读设置子抽屉失败", error)
            returnToMoreIfIdle()
        }
    }

    private fun hasOtherDialog(): Boolean =
        parentFragmentManager.fragments.any { it !== this && it is DialogFragment && it.isAdded }

    private fun returnToMoreIfIdle() {
        val host = readActivity ?: return
        if (!openedNestedDrawer || !isAdded || isRemoving || host.isFinishing || host.isDestroyed ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
            parentFragmentManager.isStateSaved || hasOtherDialog()
        ) return
        openedNestedDrawer = false
        hiddenTools = ReadBottomToolbarSettings.hiddenTools(host)
        refreshUi()
        themeSnapshot = ReadDrawerStyle.themeSnapshot(host)
        setBottomDialogRegistered(true)
        dialog?.show()
    }

    private fun createActions() = ReadMoreConfigActions(
        onBooleanChanged = ::changeBoolean,
        onValueChanged = ::changeValue,
        onAction = ::handleAction,
        onPageAnimChanged = ::changePageAnim,
        onThresholdChanged = ::changeThreshold,
        onPageKeysChanged = ::savePageKeys,
        pageKeyCapture = pageKeyCapture,
    )

    private fun savePageKeys(prevKeys: String, nextKeys: String) {
        requireContext().savePageKeyBindings(PageKeyBindings(prevKeys, nextKeys))
        refreshUi()
    }

    private fun changePageAnim(pageAnim: Int) {
        ReadBook.book?.setPageAnim(-1)
        ReadBookConfig.pageAnim = pageAnim
        readActivity?.upPageAnim()
        ReadBook.loadContent(false)
        refreshUi()
    }

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

    private fun changeThreshold(key: String, value: Int) {
        when (key) {
            PreferKey.pageTouchSlop -> {
                val safeValue = value.coerceIn(0, PAGE_TOUCH_SLOP_MAX)
                if (AppConfig.pageTouchSlop == safeValue) return
                AppConfig.pageTouchSlop = safeValue
                postEvent(EventBus.UP_CONFIG, arrayListOf(4))
            }
            PreferKey.pageTouchClick -> {
                val safeValue = value.coerceIn(0, PAGE_TOUCH_CLICK_MAX)
                if (AppConfig.pageTouchClick == safeValue) return
                AppConfig.pageTouchClick = safeValue
                postEvent(EventBus.UP_CONFIG, arrayListOf(12))
            }
            else -> return
        }
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
            PreferKey.screenOrientation -> readActivity?.setOrientation()

            PreferKey.doublePageHorizontal -> {
                ChapterProvider.upLayout()
                ReadBook.loadContent(false)
            }

            PreferKey.paddingDisplayCutouts -> {
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            }
        }
    }

    private fun handleAction(key: String) {
        when (key) {
            ReadMoreConfigKeys.CLICK_REGIONAL_CONFIG -> {
                val activity = readActivity
                dismissAllowingStateLoss()
                activity?.window?.decorView?.post {
                    if (!activity.isFinishing && !activity.isDestroyed) {
                        activity.showClickRegionalConfig()
                    }
                }
            }
        }
    }

    private fun refreshUi() {
        if (!isAdded) return
        val context = requireContext()
        val booleanDefaults = linkedMapOf(
            PreferKey.hideStatusBar to false,
            PreferKey.readBodyToLh to true,
            PreferKey.paddingDisplayCutouts to false,
            PreferKey.volumeKeyPage to AppConfig.volumeKeyPageDefault,
            PreferKey.keyPageOnLongPress to false,
            PreferKey.autoChangeSource to true,
        )
        val values = mapOf(
            PreferKey.prevKeys to context.getPrefString(PreferKey.prevKeys).orEmpty(),
            PreferKey.nextKeys to context.getPrefString(PreferKey.nextKeys).orEmpty(),
            PreferKey.screenOrientation to context.getPrefString(
                PreferKey.screenOrientation,
                "0",
            ).orEmpty(),
            PreferKey.keepLight to context.getPrefString(PreferKey.keepLight, "0").orEmpty(),
            PreferKey.doublePageHorizontal to context.getPrefString(
                PreferKey.doublePageHorizontal,
                "0",
            ).orEmpty(),
            PreferKey.clickImgWay to context.getPrefString(PreferKey.clickImgWay, "0").orEmpty(),
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
                PreferKey.doublePageHorizontal to options(
                    R.array.double_page_title,
                    R.array.double_page_value,
                ),
                PreferKey.clickImgWay to options(
                    R.array.click_image_way_title,
                    R.array.click_image_way_value,
                ),
                ReadMoreConfigKeys.BOOK_IMAGE_STYLE to imageStyleOptions(),
            ),
            thresholdValues = mapOf(
                PreferKey.pageTouchSlop to AppConfig.pageTouchSlop,
                PreferKey.pageTouchClick to AppConfig.pageTouchClick,
            ),
            pageAnim = ReadBook.pageAnim().coerceIn(0, 4),
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

}
