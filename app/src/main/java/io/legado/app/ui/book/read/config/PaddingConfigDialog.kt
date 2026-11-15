package io.legado.app.ui.book.read.config

import android.content.DialogInterface
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.base.BaseComposeDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadBookActivity
import io.legado.app.ui.book.read.ReadDrawerContentTheme
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.components.compose.NgFormGroup
import io.legado.app.ui.design.components.compose.NgFormPanel
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgDialogWindow
import io.legado.app.utils.postEvent

class PaddingConfigDialog : BaseComposeDialogFragment() {
    private var previewRegistered = false

    override fun onStart() {
        super.onStart()
        applyNgDialogWindow(marginDp = 12, dimAmount = 0.4f)
        dialog?.window?.apply {
            setGravity(Gravity.CENTER)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onFragmentCreated(view: View, savedInstanceState: android.os.Bundle?) {
        if (!previewRegistered) {
            (activity as? ReadBookActivity)?.let {
                it.beginReadConfigPreview()
                previewRegistered = true
            }
        }
        (view as ComposeView).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                ReadDrawerContentTheme(snapshot = ReadDrawerStyle.themeSnapshot(requireContext())) {
                    ReadConfigDialogSurface(
                        contentPadding = PaddingValues(start = 8.dp, top = 22.dp, end = 8.dp, bottom = 18.dp),
                    ) {
                        ReadConfigDialogTitle(stringResource(R.string.read_style_body_margins))
                        Spacer(Modifier.height(18.dp))
                        ReadAreaPaddingControls(ReadPaddingArea.BODY)
                    }
                }
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
}

internal enum class ReadPaddingArea { BODY, HEADER, FOOTER }

/** Uses the same stored values and refresh events as the original reading margins dialog. */
@Composable
internal fun ReadAreaPaddingControls(area: ReadPaddingArea) {
    var top by remember(area) {
        mutableIntStateOf(when (area) {
            ReadPaddingArea.BODY -> ReadBookConfig.paddingTop
            ReadPaddingArea.HEADER -> ReadBookConfig.headerPaddingTop
            ReadPaddingArea.FOOTER -> ReadBookConfig.footerPaddingTop
        })
    }
    var bottom by remember(area) {
        mutableIntStateOf(when (area) {
            ReadPaddingArea.BODY -> ReadBookConfig.paddingBottom
            ReadPaddingArea.HEADER -> ReadBookConfig.headerPaddingBottom
            ReadPaddingArea.FOOTER -> ReadBookConfig.footerPaddingBottom
        })
    }
    var left by remember(area) {
        mutableIntStateOf(when (area) {
            ReadPaddingArea.BODY -> ReadBookConfig.paddingLeft
            ReadPaddingArea.HEADER -> ReadBookConfig.headerPaddingLeft
            ReadPaddingArea.FOOTER -> ReadBookConfig.footerPaddingLeft
        })
    }
    var right by remember(area) {
        mutableIntStateOf(when (area) {
            ReadPaddingArea.BODY -> ReadBookConfig.paddingRight
            ReadPaddingArea.HEADER -> ReadBookConfig.headerPaddingRight
            ReadPaddingArea.FOOTER -> ReadBookConfig.footerPaddingRight
        })
    }
    fun notifyPaddingChanged() {
        postEvent(EventBus.UP_CONFIG, if (area == ReadPaddingArea.BODY) arrayListOf(10, 5) else arrayListOf(2))
    }

    if (area == ReadPaddingArea.BODY) {
        ReadBodyPaddingControls(
            padding = ReadBodyPadding(top, bottom, left, right),
            contentColor = ComposeColor(NgTheme.colors.onSurface),
        ) { side, value ->
            when (side) {
                ReadBodyPaddingSide.TOP -> {
                    top = value
                    ReadBookConfig.paddingTop = value
                }
                ReadBodyPaddingSide.BOTTOM -> {
                    bottom = value
                    ReadBookConfig.paddingBottom = value
                }
                ReadBodyPaddingSide.LEFT -> {
                    left = value
                    ReadBookConfig.paddingLeft = value
                }
                ReadBodyPaddingSide.RIGHT -> {
                    right = value
                    ReadBookConfig.paddingRight = value
                }
            }
            notifyPaddingChanged()
        }
        return
    }

    var showLine by remember(area) {
        mutableStateOf(if (area == ReadPaddingArea.HEADER) ReadBookConfig.showHeaderLine else ReadBookConfig.showFooterLine)
    }
    NgFormPanel {
        ReadConfigSwitchRow(
            title = stringResource(R.string.divider_line),
            checked = showLine,
            modifier = Modifier.padding(horizontal = 14.dp),
            variant = ReadConfigSwitchRowVariant.READ_INFO_COMPACT,
            onCheckedChange = {
                showLine = it
                if (area == ReadPaddingArea.HEADER) ReadBookConfig.showHeaderLine = it
                else ReadBookConfig.showFooterLine = it
                postEvent(EventBus.UP_CONFIG, arrayListOf(2))
            },
        )
    }
    Spacer(Modifier.height(16.dp))
    NgFormGroup(stringResource(R.string.read_style_margins)) {
        val metric = ReadTypographyMetric.BODY_PADDING
        val items = listOf(
            TypographyItem(stringResource(R.string.padding_top), metric, top) {
                top = it
                if (area == ReadPaddingArea.HEADER) ReadBookConfig.headerPaddingTop = it
                else ReadBookConfig.footerPaddingTop = it
                notifyPaddingChanged()
            },
            TypographyItem(stringResource(R.string.padding_bottom), metric, bottom) {
                bottom = it
                if (area == ReadPaddingArea.HEADER) ReadBookConfig.headerPaddingBottom = it
                else ReadBookConfig.footerPaddingBottom = it
                notifyPaddingChanged()
            },
            TypographyItem(stringResource(R.string.padding_left), metric, left) {
                left = it
                if (area == ReadPaddingArea.HEADER) ReadBookConfig.headerPaddingLeft = it
                else ReadBookConfig.footerPaddingLeft = it
                notifyPaddingChanged()
            },
            TypographyItem(stringResource(R.string.padding_right), metric, right) {
                right = it
                if (area == ReadPaddingArea.HEADER) ReadBookConfig.headerPaddingRight = it
                else ReadBookConfig.footerPaddingRight = it
                notifyPaddingChanged()
            },
        )
        Box(Modifier.padding(vertical = 4.dp)) {
            ReadTypographyMetricGrid(
                items = items,
                contentColor = ComposeColor(NgTheme.colors.onSurface),
                endPadding = 5.dp,
                variant = ReadTypographyGridVariant.HEADER_FOOTER_PADDING,
            )
        }
    }
    Spacer(Modifier.height(40.dp))
}
