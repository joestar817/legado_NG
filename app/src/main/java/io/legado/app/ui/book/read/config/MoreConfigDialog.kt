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
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.entities.Book
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.PageKeyBindings
import io.legado.app.help.config.savePageKeyBindings
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
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

        fun forPageTurning() = MoreConfigDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_PAGE_TURNING, true) }
        }
    }

    private val pageTurning: Boolean
        get() = arguments?.getBoolean(ARG_PAGE_TURNING) == true

    private var screenState by mutableStateOf<ReadMoreConfigUiState?>(null)
    private var themeSnapshot by mutableStateOf<NgThemeSnapshot?>(null)
    private var bottomDialogRegistered = false
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
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
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
                    ReadDrawerContentTheme(snapshot = snapshot) {
                        NgDismissibleDrawer(onDismiss = { dismissAllowingStateLoss() }) {
                            screenState?.let { state ->
                                if (pageTurning) {
                                    ReadPageTurningScreen(state = state, actions = actions)
                                } else {
                                    ReadMoreConfigScreen(
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
    }

    override fun onDismiss(dialog: DialogInterface) {
        pageKeyCapture.cancel()
        super.onDismiss(dialog)
        if (pageTurning) ReadBookConfig.save()
        if (bottomDialogRegistered) {
            readActivity?.let {
                it.bottomDialog = (it.bottomDialog - 1).coerceAtLeast(0)
            }
            bottomDialogRegistered = false
        }
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
