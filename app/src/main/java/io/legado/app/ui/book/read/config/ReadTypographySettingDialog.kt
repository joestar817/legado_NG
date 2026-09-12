package io.legado.app.ui.book.read.config

import android.app.Dialog
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.view.View
import android.view.Window
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.ui.book.read.ReadDrawerStyle
import io.legado.app.ui.design.theme.NgAppTheme
import io.legado.app.ui.design.theme.NgTheme
import io.legado.app.ui.widget.dialog.applyNgWindow

internal object ReadTypographySettingDialog {

    fun showDiscrete(
        context: Context,
        avoidView: View,
        title: String,
        stepLabels: List<String>,
        currentValues: List<String>,
        selectedIndex: Int,
        currentValueTextSizeSp: Float = 30f,
        previewTypeface: (Int) -> Typeface? = { null },
        onSelectionChanged: (Int) -> Unit,
    ) {
        if (stepLabels.size < 2) return
        showDialog(context, avoidView) {
            var selected by remember {
                mutableIntStateOf(selectedIndex.coerceIn(stepLabels.indices))
            }
            val foreground = typographyForeground()
            val secondary = foreground.copy(
                alpha = if (ReadBookConfig.isNightTheme && !AppConfig.isEInkMode) {
                    190f / 255f
                } else {
                    170f / 255f
                }
            )
            ReadConfigDialogSurface(
                contentPadding = PaddingValues(
                    horizontal = 24.dp,
                    vertical = 26.dp,
                ),
            ) {
                TypographyTitle(title, foreground)
                Text(
                    text = currentValues.getOrElse(selected) { stepLabels[selected] },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp, bottom = 18.dp),
                    color = foreground,
                    style = TextStyle(
                        fontSize = currentValueTextSizeSp.sp,
                        fontFamily = previewTypeface(selected)?.let(::FontFamily) ?: NgTheme.fontFamily,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    textAlign = TextAlign.Center,
                )
                ReadConfigDiscreteStepBar(
                    stepCount = stepLabels.size,
                    selectedIndex = selected,
                    onSelectedIndexChanged = { index ->
                        if (index != selected) {
                            selected = index
                            onSelectionChanged(index)
                        }
                    },
                    stepColor = Color(NgTheme.colors.primary),
                    accessibilityLabel = "$title ${currentValues.getOrElse(selected) { stepLabels[selected] }}",
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    stepLabels.forEach { label ->
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            color = secondary,
                            style = TextStyle(
                                fontSize = if (stepLabels.size >= 8) 10.sp else 13.sp,
                                fontFamily = NgTheme.fontFamily,
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }

    fun showChineseConverter(
        context: Context,
        avoidView: View,
        title: String,
        labels: List<String>,
        selectedIndex: Int,
        onSelectedIndexChanged: (Int) -> Unit,
    ) {
        if (labels.isEmpty()) return
        var dialog: Dialog? = null
        dialog = showDialog(context, avoidView) {
            var selected by remember {
                mutableIntStateOf(selectedIndex.coerceIn(labels.indices))
            }
            val foreground = typographyForeground()
            val secondary = foreground.copy(
                alpha = if (ReadBookConfig.isNightTheme && !AppConfig.isEInkMode) {
                    190f / 255f
                } else {
                    170f / 255f
                }
            )
            ReadConfigDialogSurface(
                contentPadding = PaddingValues(
                    horizontal = 24.dp,
                    vertical = 26.dp,
                ),
            ) {
                TypographyTitle(title, foreground)
                Text(
                    text = context.getString(R.string.chinese_converter_hint),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 24.dp),
                    color = secondary,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = NgTheme.fontFamily,
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                    ),
                    textAlign = TextAlign.Center,
                )
                ReadConfigDock(
                    labels = labels,
                    selectedIndex = selected,
                    contentColor = foreground,
                    accessibilityLabel = title,
                    onSelected = { index ->
                        selected = index
                        onSelectedIndexChanged(index)
                        dialog?.dismiss()
                    },
                )
            }
        }
    }

    private fun showDialog(
        context: Context,
        avoidView: View,
        content: @androidx.compose.runtime.Composable () -> Unit,
    ): Dialog {
        val composeView = ComposeView(context).apply {
            setBackgroundColor(AndroidColor.TRANSPARENT)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                NgAppTheme(
                    snapshot = ReadDrawerStyle.themeSnapshot(context),
                    updateSystemBars = false,
                ) {
                    content()
                }
            }
        }
        return ComponentDialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(composeView)
            setCanceledOnTouchOutside(true)
            show()
            applyNgWindow(marginDp = 20)
            ReadDrawerStyle.positionDialogAbove(this, avoidView)
        }
    }

    @androidx.compose.runtime.Composable
    private fun TypographyTitle(title: String, color: Color) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            color = color,
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = NgTheme.fontFamily,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
            textAlign = TextAlign.Center,
        )
    }

    @androidx.compose.runtime.Composable
    private fun typographyForeground(): Color {
        val night = ReadBookConfig.isNightTheme && !AppConfig.isEInkMode
        return Color(if (night) AndroidColor.WHITE else AndroidColor.rgb(45, 43, 40))
    }
}
