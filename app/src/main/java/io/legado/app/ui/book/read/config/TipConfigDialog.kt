package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.DialogInterface
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.model.ReadBook
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.config.NgInlineColorPicker
import io.legado.app.ui.book.read.ReadDrawerContentTheme
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean

class TipConfigDialog : BaseComposeDialogFragment(), FontSelectDialog.CallBack {

    companion object {
        private const val SECTION_TITLE = 0
        private const val SECTION_HEADER = 1
        private const val SECTION_FOOTER = 2
        private const val TITLE_FONT_TAG = "titleFont"
        private const val ARG_INITIAL_SECTION = "initialSection"
        private const val ARG_TITLE_COLOR_NIGHT = "titleColorNight"
        private const val ARG_TIP_COLOR_DIVIDER = "tipColorDivider"

        fun forTitleColor(night: Boolean) = TipConfigDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_TITLE_COLOR_NIGHT, night) }
        }

        fun forHeader() = TipConfigDialog().apply {
            arguments = Bundle().apply { putInt(ARG_INITIAL_SECTION, SECTION_HEADER) }
        }

        fun forFooter() = TipConfigDialog().apply {
            arguments = Bundle().apply { putInt(ARG_INITIAL_SECTION, SECTION_FOOTER) }
        }

        fun forTipColor(divider: Boolean) = TipConfigDialog().apply {
            arguments = Bundle().apply { putBoolean(ARG_TIP_COLOR_DIVIDER, divider) }
        }

        private const val POSITION_LEFT = 0
        private const val POSITION_MIDDLE = 1
        private const val POSITION_RIGHT = 2
    }

    private enum class ColorPickerTarget { TIP, DIVIDER, TITLE_DAY, TITLE_NIGHT }

    private lateinit var composeView: ComposeView
    private var externalRevision by mutableIntStateOf(0)
    private var previewRegistered = false

    private val titleFontLifecycleCallbacks = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, fragment: Fragment) {
            if (fragment !is FontSelectDialog || fragment.tag != TITLE_FONT_TAG) return
            val ownerUrl = ReadBook.book?.bookUrl
            fragment.setOnDismissListener {
                val host = activity ?: return@setOnDismissListener
                if (!isAdded || isRemoving || host.isFinishing || host.isDestroyed ||
                    host.isChangingConfigurations || parentFragmentManager.isStateSaved ||
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) return@setOnDismissListener
                if (ReadBook.book?.bookUrl != ownerUrl) {
                    dismissAllowingStateLoss()
                } else {
                    dialog?.show()
                }
            }
            // Keep this fragment and its scroll state; only the font window is visible.
            dialog?.hide()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        childFragmentManager.registerFragmentLifecycleCallbacks(titleFontLifecycleCallbacks, false)
    }

    override fun onDestroy() {
        childFragmentManager.unregisterFragmentLifecycleCallbacks(titleFontLifecycleCallbacks)
        super.onDestroy()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        ComponentDialog(requireContext(), theme)

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 20, dimAmount = 0.22f)
        dialog?.window?.apply {
            setGravity(Gravity.CENTER)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            attributes = attributes.apply { x = 0; y = 0 }
            decorView.setPadding(0, 0, 0, 0)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        ReadBookConfig.save()
        if (previewRegistered) {
            (activity as? ReadBookActivity)?.endReadConfigPreview()
            previewRegistered = false
        }
        super.onDismiss(dialog)
        returnToReadStyleAdjustOnDismiss()
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (!previewRegistered) {
            (activity as? ReadBookActivity)?.let {
                it.beginReadConfigPreview()
                previewRegistered = true
            }
        }
        if (ReadBookConfig.titleMode !in 0..2) ReadBookConfig.titleMode = 0
        composeView = view as ComposeView
        composeView.setBackgroundColor(AndroidColor.TRANSPARENT)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            val primaryStrength = ReadFloatingAppearanceState.primaryStrengthPercent
            val colorStyle = ReadFloatingAppearanceState.colorStyle
            ReadDrawerContentTheme(
                snapshot = ReadDrawerStyle.themeSnapshot(
                    context = requireContext(),
                    primaryStrengthPercent = primaryStrength,
                    colorStyle = colorStyle,
                ),
            ) {
                TipConfigContent()
            }
        }
        observeEvent<String>(EventBus.TIP_COLOR) { externalRevision++ }
    }

    @Composable
    private fun TipConfigContent() {
        val section = arguments?.getInt(ARG_INITIAL_SECTION, SECTION_TITLE) ?: SECTION_TITLE
        var headerPosition by remember { mutableIntStateOf(POSITION_LEFT) }
        var footerPosition by remember { mutableIntStateOf(POSITION_LEFT) }
        var headerEnabled by remember { mutableStateOf(ReadTipConfig.headerMode == 1) }
        var footerEnabled by remember { mutableStateOf(ReadTipConfig.footerMode == 0) }
        var revision by remember { mutableIntStateOf(0) }
        val directTitleColor = arguments?.containsKey(ARG_TITLE_COLOR_NIGHT) == true
        val directTipColor = arguments?.containsKey(ARG_TIP_COLOR_DIVIDER) == true
        val directPicker = directTitleColor || directTipColor
        var activePicker by remember {
            mutableStateOf<ColorPickerTarget?>(when {
                directTitleColor -> if (arguments?.getBoolean(ARG_TITLE_COLOR_NIGHT) == true) {
                    ColorPickerTarget.TITLE_NIGHT
                } else ColorPickerTarget.TITLE_DAY
                directTipColor -> if (arguments?.getBoolean(ARG_TIP_COLOR_DIVIDER) == true) {
                    ColorPickerTarget.DIVIDER
                } else ColorPickerTarget.TIP
                else -> null
            })
        }
        val headerImageSettings = rememberReadImageSettingsState(
            ReadImagePosition.HEADER, revision + externalRevision,
        ) { revision++ }
        val footerImageSettings = rememberReadImageSettingsState(
            ReadImagePosition.FOOTER, revision + externalRevision,
        ) { revision++ }
        val titleScroll = rememberScrollState()
        val areaInfoScroll = rememberScrollState()
        val areaLayoutScroll = rememberScrollState()
        val areaImageScroll = rememberScrollState()
        var areaTab by rememberSaveable(section) { mutableIntStateOf(0) }
        val focusManager = LocalFocusManager.current
        val keyboard = LocalSoftwareKeyboardController.current
        externalRevision
        revision

        fun back() {
            focusManager.clearFocus()
            if (activePicker != null && directPicker) dismissAllowingStateLoss()
            else if (activePicker != null) activePicker = null
            else dismissAllowingStateLoss()
        }
        BackHandler(enabled = activePicker != null) { back() }

        ReadConfigDialogSurface(
            modifier = Modifier.heightIn(max = availableDialogHeight()),
            contentPadding = PaddingValues(
                start = 12.dp,
                top = 20.dp,
                end = 12.dp,
                bottom = 12.dp,
            ),
        ) {
            if (activePicker == null) {
                ReadConfigDialogTitle(getString(when (section) {
                    SECTION_HEADER -> R.string.header
                    SECTION_FOOTER -> R.string.footer
                    else -> R.string.title
                }))
                Spacer(Modifier.height(14.dp))
            }
            if (activePicker != null) {
                val target = requireNotNull(activePicker)
                Box(Modifier.weight(1f, fill = false).padding(horizontal = 6.dp)) {
                    NgInlineColorPicker(
                        title = getString(when (target) {
                            ColorPickerTarget.TIP -> R.string.tip_text_color
                            ColorPickerTarget.DIVIDER -> R.string.tip_divider_color
                            ColorPickerTarget.TITLE_DAY -> R.string.read_title_color_day
                            ColorPickerTarget.TITLE_NIGHT -> R.string.read_title_color_night
                        }),
                        initialColor = initialColorFor(target),
                        backgroundRenderer = ::renderCurrentReadBackground,
                        showAlphaSlider = false,
                        forceOpaque = true,
                        resetInContent = true,
                        compactResetAction = true,
                        onBack = { if (directPicker) dismissAllowingStateLoss() else activePicker = null },
                        onColorChanged = { selected ->
                            applySelectedColor(target, selected or AndroidColor.BLACK)
                            revision++
                        },
                        onReset = {
                            resetSelectedColor(target)
                            revision++
                            if (directPicker) dismissAllowingStateLoss() else activePicker = null
                        },
                    )
                }
            } else if (section == SECTION_HEADER || section == SECTION_FOOTER) {
                val isHeader = section == SECTION_HEADER
                ReadTipSettingsTabs(
                    selectedIndex = areaTab,
                    onSelected = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                        areaTab = it
                    },
                    accessibilityLabel = getString(if (isHeader) R.string.header else R.string.footer),
                )
                Spacer(Modifier.height(12.dp))
                val areaScroll = when (areaTab) {
                    0 -> areaInfoScroll
                    1 -> areaLayoutScroll
                    else -> areaImageScroll
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(areaScroll)) {
                    when (areaTab) {
                        0 -> TipAreaContent(
                            enabledTitle = getString(R.string.read_tip_show_information),
                            enabled = if (isHeader) headerEnabled else footerEnabled,
                            onEnabledChanged = { enabled ->
                                if (isHeader) {
                                    headerEnabled = enabled
                                    ReadTipConfig.headerMode = if (enabled) 1 else 2
                                    if (enabled && !ReadBookConfig.hideStatusBar) {
                                        ReadBookConfig.hideStatusBar = true
                                        putPrefBoolean(PreferKey.hideStatusBar, true)
                                        postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
                                    } else {
                                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                                    }
                                } else {
                                    footerEnabled = enabled
                                    ReadTipConfig.footerMode = if (enabled) 0 else 1
                                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                                }
                            },
                            position = if (isHeader) headerPosition else footerPosition,
                            onPositionChanged = {
                                if (isHeader) headerPosition = it else footerPosition = it
                            },
                            section = section,
                            revision = revision,
                            onContentChanged = { revision++ },
                        )
                        1 -> ReadAreaPaddingControls(
                            if (isHeader) ReadPaddingArea.HEADER else ReadPaddingArea.FOOTER
                        )
                        else -> ReadImageSettings(if (isHeader) headerImageSettings else footerImageSettings)
                    }
                }
            } else when (section) {
                SECTION_TITLE -> {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(titleScroll)
                    ) {
                        ReadTitleSettingsContent(
                            revision = revision + externalRevision,
                            onSelectFont = {
                                FontSelectDialog.forTitle().show(childFragmentManager, TITLE_FONT_TAG)
                            },
                            onSelectColor = { night ->
                                activePicker = if (night) ColorPickerTarget.TITLE_NIGHT
                                    else ColorPickerTarget.TITLE_DAY
                            },
                            onChanged = {
                                notifyTitleChanged()
                                revision++
                            },
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun availableDialogHeight(): Dp {
        val host = requireActivity().window.decorView
        val density = LocalDensity.current
        val fallbackHeight = LocalConfiguration.current.screenHeightDp.dp
        var visibleHeightPx by remember(host) { mutableIntStateOf(0) }
        DisposableEffect(host, composeView) {
            val frame = Rect()
            fun updateVisibleHeight() {
                // Read the Activity window, never this WRAP_CONTENT dialog's measured height.
                // Otherwise each layout could subtract the margin from its previous result.
                host.getWindowVisibleDisplayFrame(frame)
                val height = frame.height().coerceAtMost(host.height.takeIf { it > 0 } ?: Int.MAX_VALUE)
                if (height > 0) visibleHeightPx = height
            }
            val observer = host.viewTreeObserver
            val dialogObserver = composeView.viewTreeObserver
            val listener = ViewTreeObserver.OnGlobalLayoutListener { updateVisibleHeight() }
            observer.addOnGlobalLayoutListener(listener)
            dialogObserver.addOnGlobalLayoutListener(listener)
            updateVisibleHeight()
            onDispose {
                if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
                if (dialogObserver.isAlive) dialogObserver.removeOnGlobalLayoutListener(listener)
            }
        }
        val visibleHeight = if (visibleHeightPx > 0) with(density) { visibleHeightPx.toDp() }
            else fallbackHeight
        return (visibleHeight - 32.dp).coerceAtLeast(1.dp)
    }

    private fun notifyTitleChanged(fontChanged: Boolean = false) {
        ReadBookConfig.save()
        val hasImage = ReadBookConfig.config.run {
            headerImage.isNotBlank() || footerImage.isNotBlank()
        }
        postEvent(EventBus.UP_CONFIG, if (fontChanged || hasImage) {
            arrayListOf(2, 5)
        } else arrayListOf(8, 5))
    }

    override val curFontPath: String get() = ReadBookConfig.titleFont

    override fun selectFont(path: String) {
        if (path != ReadBookConfig.titleFont) {
            ReadBookConfig.titleFont = path
            notifyTitleChanged(fontChanged = true)
            externalRevision++
        }
    }

    @Composable
    private fun TipAreaContent(
        enabledTitle: String,
        enabled: Boolean,
        onEnabledChanged: (Boolean) -> Unit,
        position: Int,
        onPositionChanged: (Int) -> Unit,
        section: Int,
        revision: Int,
        onContentChanged: () -> Unit,
    ) {
        revision
        val tipNames = ReadTipConfig.tipNames
        val selectedValue = currentTipValue(section, position)
        val positionSummaries = (POSITION_LEFT..POSITION_RIGHT).map { slot ->
            val value = currentTipValue(section, slot)
            val index = ReadTipConfig.tipValues.indexOf(value).takeIf { it >= 0 } ?: 0
            tipNames[index]
        }
        val choices = ReadTipConfig.tipValues.mapIndexed { index, value ->
            val selected = value == selectedValue
            ReadTipChoiceUi(
                value = value,
                name = tipNames[index],
                icon = tipIcon(value),
                selected = selected,
                usage = if (selected) null else usagePosition(value, section, position),
            )
        }
        ReadTipInformationPanel(
            enabledTitle = enabledTitle,
            enabled = enabled,
            onEnabledChanged = onEnabledChanged,
            headerBackButton = if (section == SECTION_HEADER) ReadTipConfig.showHeaderBackButton else null,
            onHeaderBackButtonChanged = { checked ->
                ReadTipConfig.showHeaderBackButton = checked
                onContentChanged()
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            },
            position = position,
            onPositionChanged = onPositionChanged,
            positionSummaries = positionSummaries,
            choices = choices,
            onChoiceSelected = { value ->
                clearRepeat(value)
                assignTipValue(section, position, value)
                postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                onContentChanged()
            },
            areaLabel = getString(if (section == SECTION_HEADER) R.string.header else R.string.footer),
        )
    }

    private fun initialColorFor(target: ColorPickerTarget): Int = when (target) {
        ColorPickerTarget.TIP -> ReadTipConfig.tipColor.takeUnless { it == 0 }
            ?: ReadBookConfig.textColor

        ColorPickerTarget.DIVIDER -> when (val color = ReadTipConfig.tipDividerColor) {
            -1 -> ContextCompat.getColor(requireContext(), R.color.divider)
            0 -> ReadBookConfig.textColor
            else -> color
        }

        ColorPickerTarget.TITLE_DAY -> readTitleColorPreview(ReadBookConfig.config, night = false)
        ColorPickerTarget.TITLE_NIGHT -> readTitleColorPreview(ReadBookConfig.config, night = true)
    }

    private fun applySelectedColor(target: ColorPickerTarget, color: Int) {
        when (target) {
            ColorPickerTarget.TIP -> ReadTipConfig.tipColor = color
            ColorPickerTarget.DIVIDER -> ReadTipConfig.tipDividerColor = color
            ColorPickerTarget.TITLE_DAY, ColorPickerTarget.TITLE_NIGHT -> {
                if (target == ColorPickerTarget.TITLE_NIGHT) ReadBookConfig.config.titleColorNight = color
                else ReadBookConfig.config.titleColor = color
                notifyTitleChanged()
                return
            }
        }
        postEvent(EventBus.TIP_COLOR, "")
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    }

    private fun resetSelectedColor(target: ColorPickerTarget) {
        when (target) {
            ColorPickerTarget.TIP -> ReadTipConfig.tipColor = 0
            ColorPickerTarget.DIVIDER -> ReadTipConfig.tipDividerColor = -1
            ColorPickerTarget.TITLE_DAY, ColorPickerTarget.TITLE_NIGHT -> {
                if (target == ColorPickerTarget.TITLE_NIGHT) ReadBookConfig.config.titleColorNight = 0
                else ReadBookConfig.config.titleColor = 0
                notifyTitleChanged()
                return
            }
        }
        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
    }

    private fun currentTipValue(section: Int, position: Int): Int = ReadTipConfig.run {
        when (section) {
            SECTION_HEADER -> when (position) {
                POSITION_LEFT -> tipHeaderLeft
                POSITION_MIDDLE -> tipHeaderMiddle
                else -> tipHeaderRight
            }

            SECTION_FOOTER -> when (position) {
                POSITION_LEFT -> tipFooterLeft
                POSITION_MIDDLE -> tipFooterMiddle
                else -> tipFooterRight
            }

            else -> none
        }
    }

    private fun assignTipValue(section: Int, position: Int, value: Int) = ReadTipConfig.run {
        when (section) {
            SECTION_HEADER -> when (position) {
                POSITION_LEFT -> tipHeaderLeft = value
                POSITION_MIDDLE -> tipHeaderMiddle = value
                POSITION_RIGHT -> tipHeaderRight = value
            }

            SECTION_FOOTER -> when (position) {
                POSITION_LEFT -> tipFooterLeft = value
                POSITION_MIDDLE -> tipFooterMiddle = value
                POSITION_RIGHT -> tipFooterRight = value
            }
        }
    }

    private fun usagePosition(value: Int, currentSection: Int, currentPosition: Int): String? {
        if (value == ReadTipConfig.none) return null
        val slots = listOf(
            Triple(SECTION_HEADER, POSITION_LEFT, ReadTipConfig.tipHeaderLeft),
            Triple(SECTION_HEADER, POSITION_MIDDLE, ReadTipConfig.tipHeaderMiddle),
            Triple(SECTION_HEADER, POSITION_RIGHT, ReadTipConfig.tipHeaderRight),
            Triple(SECTION_FOOTER, POSITION_LEFT, ReadTipConfig.tipFooterLeft),
            Triple(SECTION_FOOTER, POSITION_MIDDLE, ReadTipConfig.tipFooterMiddle),
            Triple(SECTION_FOOTER, POSITION_RIGHT, ReadTipConfig.tipFooterRight),
        )
        val usedSlot = slots.firstOrNull { (section, position, slotValue) ->
            slotValue == value && (section != currentSection || position != currentPosition)
        } ?: return null
        val sectionName = getString(
            if (usedSlot.first == SECTION_HEADER) R.string.header else R.string.footer
        )
        val positionName = getString(
            when (usedSlot.second) {
                POSITION_LEFT -> R.string.left
                POSITION_MIDDLE -> R.string.middle
                else -> R.string.right
            }
        )
        return getString(
            R.string.read_tip_used_at,
            getString(R.string.read_tip_position, sectionName, positionName),
        )
    }

    private fun clearRepeat(repeat: Int) = ReadTipConfig.apply {
        if (repeat != none) {
            if (tipHeaderLeft == repeat) tipHeaderLeft = none
            if (tipHeaderMiddle == repeat) tipHeaderMiddle = none
            if (tipHeaderRight == repeat) tipHeaderRight = none
            if (tipFooterLeft == repeat) tipFooterLeft = none
            if (tipFooterMiddle == repeat) tipFooterMiddle = none
            if (tipFooterRight == repeat) tipFooterRight = none
        }
    }

    private fun tipIcon(value: Int): Int = when (value) {
        ReadTipConfig.none -> R.drawable.ic_block_outline
        ReadTipConfig.bookName, ReadTipConfig.chapterTitle -> R.drawable.ic_book_has
        ReadTipConfig.time,
        ReadTipConfig.timeBattery,
        ReadTipConfig.timeBatteryPercentage -> R.drawable.ic_mingcute_time_line

        ReadTipConfig.battery,
        ReadTipConfig.batteryPercentage -> R.drawable.ic_battery_outline

        else -> R.drawable.ic_chapter_list
    }

}
