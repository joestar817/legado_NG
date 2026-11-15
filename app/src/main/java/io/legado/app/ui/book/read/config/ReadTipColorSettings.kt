package io.legado.app.ui.book.read.config

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgFormGroupDivider

@Composable
internal fun ReadTipColorSettings(
    textColor: Int,
    dividerColor: Int,
    contentColor: Color,
    onFollowTextColor: () -> Unit,
    onDividerModeChanged: (Int) -> Unit,
    onPickTextColor: () -> Unit,
    onPickDividerColor: () -> Unit,
) {
    val textOptions = stringArrayResource(R.array.tip_color).toList()
    val dividerOptions = stringArrayResource(R.array.tip_divider_color).toList()
    ReadStyleChoiceSettingRow(
        title = stringResource(R.string.tip_text_color),
        labels = textOptions,
        leadingIconRes = R.drawable.ic_read_text_color_setting,
        selectedIndex = if (textColor == 0) 0 else 1,
        contentColor = contentColor,
        onSelected = { index ->
            when (index) {
                0 -> onFollowTextColor()
                1 -> onPickTextColor()
            }
        },
    )
    NgFormGroupDivider()
    ReadStyleChoiceSettingRow(
        title = stringResource(R.string.tip_divider_color),
        labels = dividerOptions,
        leadingIconRes = R.drawable.ic_read_divider_color_setting,
        selectedIndex = when (dividerColor) { -1 -> 0; 0 -> 1; else -> 2 },
        contentColor = contentColor,
        onSelected = { index ->
            when (index) {
                0, 1 -> onDividerModeChanged(index - 1)
                2 -> onPickDividerColor()
            }
        },
    )
}
