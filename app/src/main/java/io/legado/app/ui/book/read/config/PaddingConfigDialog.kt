package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.postEvent

class PaddingConfigDialog : BaseComposeDialogFragment() {

    private companion object {
        const val SECTION_HEADER = 0
        const val SECTION_BODY = 1
        const val SECTION_FOOTER = 2
    }

    private lateinit var composeView: ComposeView
    private var previewRegistered = false

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 20, dimAmount = 0.4f)
        dialog?.window?.setGravity(Gravity.CENTER)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        if (!previewRegistered) {
            (activity as? ReadBookActivity)?.let {
                it.beginReadConfigPreview()
                previewRegistered = true
            }
        }
        composeView = view as ComposeView
        composeView.setBackgroundColor(Color.TRANSPARENT)
        composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        composeView.setContent {
            NgAppTheme(
                snapshot = ReadDrawerStyle.themeSnapshot(requireContext()),
                updateSystemBars = false,
            ) {
                PaddingConfigContent()
            }
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

    @Composable
    private fun PaddingConfigContent() {
        var section by remember { mutableIntStateOf(SECTION_BODY) }
        var bodyTop by remember { mutableIntStateOf(ReadBookConfig.paddingTop) }
        var bodyBottom by remember { mutableIntStateOf(ReadBookConfig.paddingBottom) }
        var bodyLeft by remember { mutableIntStateOf(ReadBookConfig.paddingLeft) }
        var bodyRight by remember { mutableIntStateOf(ReadBookConfig.paddingRight) }
        var headerTop by remember { mutableIntStateOf(ReadBookConfig.headerPaddingTop) }
        var headerBottom by remember { mutableIntStateOf(ReadBookConfig.headerPaddingBottom) }
        var headerLeft by remember { mutableIntStateOf(ReadBookConfig.headerPaddingLeft) }
        var headerRight by remember { mutableIntStateOf(ReadBookConfig.headerPaddingRight) }
        var footerTop by remember { mutableIntStateOf(ReadBookConfig.footerPaddingTop) }
        var footerBottom by remember { mutableIntStateOf(ReadBookConfig.footerPaddingBottom) }
        var footerLeft by remember { mutableIntStateOf(ReadBookConfig.footerPaddingLeft) }
        var footerRight by remember { mutableIntStateOf(ReadBookConfig.footerPaddingRight) }
        var headerLine by remember { mutableStateOf(ReadBookConfig.showHeaderLine) }
        var footerLine by remember { mutableStateOf(ReadBookConfig.showFooterLine) }

        ReadConfigDialogSurface(
            contentPadding = PaddingValues(
                start = 18.dp,
                top = 22.dp,
                end = 18.dp,
                bottom = 18.dp,
            ),
        ) {
            ReadConfigDialogTitle(getString(R.string.reading_padding))
            Spacer(Modifier.height(18.dp))
            ReadConfigDock(
                labels = listOf(
                    getString(R.string.header),
                    getString(R.string.main_body),
                    getString(R.string.footer),
                ),
                selectedIndex = section,
                onSelected = { section = it },
                accessibilityLabel = getString(R.string.reading_padding),
            )
            Spacer(Modifier.height(14.dp))
            when (section) {
                SECTION_HEADER -> {
                    ReadConfigSwitchRow(
                        title = getString(R.string.divider_line),
                        checked = headerLine,
                        onCheckedChange = {
                            headerLine = it
                            ReadBookConfig.showHeaderLine = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                    MarginRows(
                        top = headerTop,
                        bottom = headerBottom,
                        left = headerLeft,
                        right = headerRight,
                        maxTop = 100,
                        onTop = {
                            headerTop = it
                            ReadBookConfig.headerPaddingTop = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onBottom = {
                            headerBottom = it
                            ReadBookConfig.headerPaddingBottom = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onLeft = {
                            headerLeft = it
                            ReadBookConfig.headerPaddingLeft = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onRight = {
                            headerRight = it
                            ReadBookConfig.headerPaddingRight = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                }

                SECTION_BODY -> MarginRows(
                    top = bodyTop,
                    bottom = bodyBottom,
                    left = bodyLeft,
                    right = bodyRight,
                    maxTop = 200,
                    onTop = {
                        bodyTop = it
                        ReadBookConfig.paddingTop = it
                        postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
                    },
                    onBottom = {
                        bodyBottom = it
                        ReadBookConfig.paddingBottom = it
                        postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
                    },
                    onLeft = {
                        bodyLeft = it
                        ReadBookConfig.paddingLeft = it
                        postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
                    },
                    onRight = {
                        bodyRight = it
                        ReadBookConfig.paddingRight = it
                        postEvent(EventBus.UP_CONFIG, arrayListOf(10, 5))
                    },
                )

                else -> {
                    ReadConfigSwitchRow(
                        title = getString(R.string.divider_line),
                        checked = footerLine,
                        onCheckedChange = {
                            footerLine = it
                            ReadBookConfig.showFooterLine = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                    MarginRows(
                        top = footerTop,
                        bottom = footerBottom,
                        left = footerLeft,
                        right = footerRight,
                        maxTop = 100,
                        onTop = {
                            footerTop = it
                            ReadBookConfig.footerPaddingTop = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onBottom = {
                            footerBottom = it
                            ReadBookConfig.footerPaddingBottom = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onLeft = {
                            footerLeft = it
                            ReadBookConfig.footerPaddingLeft = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                        onRight = {
                            footerRight = it
                            ReadBookConfig.footerPaddingRight = it
                            postEvent(EventBus.UP_CONFIG, arrayListOf(2))
                        },
                    )
                }
            }
        }
    }

    @Composable
    private fun MarginRows(
        top: Int,
        bottom: Int,
        left: Int,
        right: Int,
        maxTop: Int,
        onTop: (Int) -> Unit,
        onBottom: (Int) -> Unit,
        onLeft: (Int) -> Unit,
        onRight: (Int) -> Unit,
    ) {
        ReadConfigSliderRow(
            title = getString(R.string.padding_top),
            value = top,
            valueRange = 0..maxTop,
            onValueChange = onTop,
        )
        ReadConfigSliderRow(
            title = getString(R.string.padding_bottom),
            value = bottom,
            valueRange = 0..100,
            onValueChange = onBottom,
        )
        ReadConfigSliderRow(
            title = getString(R.string.padding_left),
            value = left,
            valueRange = 0..100,
            onValueChange = onLeft,
        )
        ReadConfigSliderRow(
            title = getString(R.string.padding_right),
            value = right,
            valueRange = 0..100,
            onValueChange = onRight,
        )
    }
}
