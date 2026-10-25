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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.help.config.ReadTipConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.book.read.ReadFloatingAppearanceState
import io.legado.app.ui.config.NgInlineColorPicker
import io.legado.app.ui.design.components.compose.NgSurface
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.font.FontSelectDialog
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.hexString
import io.legado.app.utils.observeEvent
import io.legado.app.utils.postEvent
import io.legado.app.utils.putPrefBoolean

class TipConfigDialog : BaseComposeDialogFragment(), FontSelectDialog.CallBack {

    companion object {
        private const val SECTION_TITLE = 0
        private const val SECTION_HEADER = 1
        private const val SECTION_FOOTER = 2
        private const val SECTION_STYLE = 3

        private const val POSITION_LEFT = 0
        private const val POSITION_MIDDLE = 1
        private const val POSITION_RIGHT = 2
    }

    private enum class ColorPickerTarget { TIP, DIVIDER, TITLE_DAY, TITLE_NIGHT }

    private lateinit var composeView: ComposeView
    private var externalRevision by mutableIntStateOf(0)
    private var previewRegistered = false

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
            NgAppTheme(
                snapshot = ReadDrawerStyle.themeSnapshot(
                    context = requireContext(),
                    primaryStrengthPercent = primaryStrength,
                    colorStyle = colorStyle,
                ),
                updateSystemBars = false,
            ) {
                TipConfigContent()
            }
        }
        observeEvent<String>(EventBus.TIP_COLOR) { externalRevision++ }
    }

    @Composable
    private fun TipConfigContent() {
        var section by rememberSaveable { mutableIntStateOf(SECTION_TITLE) }
        var headerPosition by remember { mutableIntStateOf(POSITION_LEFT) }
        var footerPosition by remember { mutableIntStateOf(POSITION_LEFT) }
        var headerEnabled by remember { mutableStateOf(ReadTipConfig.headerMode == 1) }
        var footerEnabled by remember { mutableStateOf(ReadTipConfig.footerMode == 0) }
        var revision by remember { mutableIntStateOf(0) }
        var activePicker by remember { mutableStateOf<ColorPickerTarget?>(null) }
        val headerImageSettings = rememberReadImageSettingsState(
            ReadImagePosition.HEADER, revision + externalRevision,
        ) { revision++ }
        val footerImageSettings = rememberReadImageSettingsState(
            ReadImagePosition.FOOTER, revision + externalRevision,
        ) { revision++ }
        val titleScroll = rememberScrollState()
        val headerScroll = rememberScrollState()
        val footerScroll = rememberScrollState()
        val styleScroll = rememberScrollState()
        val focusManager = LocalFocusManager.current
        externalRevision
        revision

        fun back() {
            focusManager.clearFocus()
            if (activePicker != null) activePicker = null
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
            ReadConfigDialogTitle(getString(R.string.reading_information))
            Spacer(Modifier.height(14.dp))
            ReadConfigDock(
                labels = listOf(
                    getString(R.string.title),
                    getString(R.string.header),
                    getString(R.string.footer),
                    getString(R.string.style),
                ),
                selectedIndex = section,
                onSelected = {
                    activePicker = null
                    section = it
                },
                height = 40.dp,
                modifier = Modifier.padding(horizontal = 6.dp),
                accessibilityLabel = getString(R.string.reading_information),
            )
            Spacer(Modifier.height(10.dp))
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
                        onBack = { activePicker = null },
                        onColorChanged = { selected ->
                            applySelectedColor(target, selected or AndroidColor.BLACK)
                            revision++
                        },
                        onReset = {
                            resetSelectedColor(target)
                            revision++
                            activePicker = null
                        },
                    )
                }
            } else when (section) {
                SECTION_TITLE -> {
                    Column(
                        Modifier.weight(1f, fill = false).verticalScroll(titleScroll)
                    ) {
                        ReadTitleSettingsContent(
                            revision = revision + externalRevision,
                            onSelectFont = {
                                FontSelectDialog.forTitle().show(childFragmentManager, "titleFont")
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

                SECTION_HEADER -> Column(
                    Modifier.weight(1f, fill = false).verticalScroll(headerScroll)
                ) {
                    ReadImageSettings(headerImageSettings)
                    TipAreaContent(
                        enabledTitle = getString(R.string.read_header_information),
                        enabled = headerEnabled,
                        onEnabledChanged = { enabled ->
                            headerEnabled = enabled
                            ReadTipConfig.headerMode = if (enabled) 1 else 2
                            if (enabled && !ReadBookConfig.hideStatusBar) {
                                ReadBookConfig.hideStatusBar = true
                                putPrefBoolean(PreferKey.hideStatusBar, true)
                                postEvent(EventBus.UP_CONFIG, arrayListOf(0, 2))
                            } else {
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                            }
                        },
                        position = headerPosition,
                        onPositionChanged = { headerPosition = it },
                        section = SECTION_HEADER,
                        revision = revision,
                        onContentChanged = { revision++ },
                    )
                }

                SECTION_FOOTER -> Column(
                    Modifier.weight(1f, fill = false).verticalScroll(footerScroll)
                ) {
                    ReadImageSettings(footerImageSettings)
                    Column(Modifier.padding(horizontal = 6.dp)) {
                        TipAreaContent(
                            enabledTitle = getString(R.string.show_footer),
                            enabled = footerEnabled,
                            onEnabledChanged = { enabled ->
                                footerEnabled = enabled
                                ReadTipConfig.footerMode = if (enabled) 0 else 1
                                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                            },
                            position = footerPosition,
                            onPositionChanged = { footerPosition = it },
                            section = SECTION_FOOTER,
                            revision = revision,
                            onContentChanged = { revision++ },
                        )
                    }
                }

                SECTION_STYLE -> Column(
                    Modifier.weight(1f, fill = false).verticalScroll(styleScroll).padding(horizontal = 6.dp)
                ) {
                    StyleContent(
                        revision = revision + externalRevision,
                        onOpenPicker = { activePicker = it },
                        onChanged = { revision++ },
                    )
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
            headerImageEnabled || footerImageEnabled
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
        if (section == SECTION_HEADER) {
            HeaderInformationContent(
                enabledTitle = enabledTitle,
                enabled = enabled,
                onEnabledChanged = onEnabledChanged,
                position = position,
                onPositionChanged = onPositionChanged,
                onContentChanged = onContentChanged,
            )
            return
        }
        ReadConfigSwitchRow(
            title = enabledTitle,
            checked = enabled,
            onCheckedChange = onEnabledChanged,
        )
        if (!enabled) return
        Spacer(Modifier.height(6.dp))
        ReadConfigDock(
            labels = listOf(
                getString(R.string.left),
                getString(R.string.middle),
                getString(R.string.right),
            ),
            selectedIndex = position,
            onSelected = onPositionChanged,
            height = 38.dp,
            accessibilityLabel = getString(R.string.reading_information),
        )
        ReadTipConfig.tipValues.indices.chunked(2).forEach { rowItems ->
            Row(Modifier.fillMaxWidth()) {
                rowItems.forEach { item ->
                    val value = ReadTipConfig.tipValues[item]
                    val selected = value == currentTipValue(section, position)
                    ReadTipOption(
                        name = ReadTipConfig.tipNames[item],
                        icon = tipIcon(value),
                        selected = selected,
                        usage = if (selected) null else usagePosition(value, section, position),
                        onClick = {
                            clearRepeat(value)
                            assignTipValue(section, position, value)
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                            onContentChanged()
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    @Composable
    private fun HeaderInformationContent(
        enabledTitle: String,
        enabled: Boolean,
        onEnabledChanged: (Boolean) -> Unit,
        position: Int,
        onPositionChanged: (Int) -> Unit,
        onContentChanged: () -> Unit,
    ) {
        val tipNames = ReadTipConfig.tipNames
        NgSurface(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            ReadConfigSwitchRow(
                title = enabledTitle,
                checked = enabled,
                onCheckedChange = onEnabledChanged,
                variant = ReadConfigSwitchRowVariant.READ_INFO_COMPACT,
            )
            if (enabled) {
                ReadConfigSwitchRow(
                    title = getString(R.string.read_header_back_button),
                    checked = ReadTipConfig.showHeaderBackButton,
                    onCheckedChange = { checked ->
                        ReadTipConfig.showHeaderBackButton = checked
                        onContentChanged()
                        postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    },
                    variant = ReadConfigSwitchRowVariant.READ_INFO_COMPACT,
                )
                ReadConfigDock(
                    labels = listOf(
                        getString(R.string.left),
                        getString(R.string.middle),
                        getString(R.string.right),
                    ),
                    selectedIndex = position,
                    onSelected = onPositionChanged,
                    secondaryLabels = (POSITION_LEFT..POSITION_RIGHT).map { slot ->
                        val value = currentTipValue(SECTION_HEADER, slot)
                        val index = ReadTipConfig.tipValues.indexOf(value).takeIf { it >= 0 } ?: 0
                        tipNames[index]
                    },
                    height = (44 * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp,
                    variant = ReadConfigDockVariant.POSITION_SUMMARY,
                    accessibilityLabel = getString(R.string.header),
                )
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    ReadTipConfig.tipValues.indices.chunked(2).forEach { rowItems ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            rowItems.forEach { item ->
                                val value = ReadTipConfig.tipValues[item]
                                val selected = value == currentTipValue(SECTION_HEADER, position)
                                ReadTipOption(
                                    name = tipNames[item],
                                    icon = tipIcon(value),
                                    selected = selected,
                                    usage = if (selected) null else usagePosition(value, SECTION_HEADER, position),
                                    onClick = {
                                        clearRepeat(value)
                                        assignTipValue(SECTION_HEADER, position, value)
                                        postEvent(EventBus.UP_CONFIG, arrayListOf(2, 6))
                                        onContentChanged()
                                    },
                                    modifier = Modifier.weight(1f),
                                    variant = ReadTipOptionVariant.HEADER_CHOICE,
                                )
                            }
                            if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    @Composable
    private fun StyleContent(
        revision: Int,
        onOpenPicker: (ColorPickerTarget) -> Unit,
        onChanged: () -> Unit,
    ) {
        revision
        ColorModeSection(
            title = getString(R.string.tip_text_color),
            labels = ReadTipConfig.tipColorNames,
            selectedIndex = if (ReadTipConfig.tipColor == 0) 0 else 1,
            valueText = ReadTipConfig.tipColor.takeUnless { it == 0 }?.let { "#${it.hexString}" },
            onSelected = { mode ->
                if (mode == 0) {
                    ReadTipConfig.tipColor = 0
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    onChanged()
                } else {
                    onOpenPicker(ColorPickerTarget.TIP)
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        ColorModeSection(
            title = getString(R.string.tip_divider_color),
            labels = ReadTipConfig.tipDividerColorNames,
            selectedIndex = dividerColorModeIndex(),
            valueText = ReadTipConfig.tipDividerColor.takeUnless { it in -1..0 }
                ?.let { "#${it.hexString}" },
            onSelected = { mode ->
                if (mode <= 1) {
                    ReadTipConfig.tipDividerColor = mode - 1
                    postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                    onChanged()
                } else {
                    onOpenPicker(ColorPickerTarget.DIVIDER)
                }
            },
        )
    }

    @Composable
    private fun ColorModeSection(
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        valueText: String?,
        onSelected: (Int) -> Unit,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = Color(NgTheme.colors.onSurface),
                fontSize = 14.sp,
            )
            if (valueText != null) {
                Text(
                    text = valueText,
                    color = Color(NgTheme.colors.onSurfaceVariant),
                    fontSize = 13.sp,
                    textAlign = TextAlign.End,
                )
            }
        }
        ReadConfigDock(
            labels = labels,
            selectedIndex = selectedIndex,
            onSelected = onSelected,
            modifier = Modifier.padding(horizontal = 8.dp),
            accessibilityLabel = title,
        )
    }

    private fun dividerColorModeIndex(): Int = when (ReadTipConfig.tipDividerColor) {
        -1 -> 0
        0 -> 1
        else -> 2
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
