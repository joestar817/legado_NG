package io.legado.app.ui.main.home

import androidx.annotation.DrawableRes
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

internal fun homeWidgetHeaderIconSize(small: Boolean) = if (small) 20.dp else 24.dp

internal fun homeWidgetHeaderIconGap(small: Boolean) = if (small) 4.dp else 8.dp

@Composable
internal fun homeWidgetHeaderTitleStyle(small: Boolean): TextStyle = LocalTextStyle.current.copy(
    fontSize = if (small) 16.sp else 18.sp,
    lineHeight = if (small) 22.sp else 24.sp,
    fontWeight = FontWeight.SemiBold,
)

@DrawableRes
internal fun homeWidgetHeaderIconRes(typeId: String, @DrawableRes fallback: Int): Int = when (typeId) {
    "reading" -> R.drawable.ic_home_widget_header_reading
    "listening" -> R.drawable.ic_home_widget_header_listening
    "updates" -> R.drawable.ic_home_widget_header_updates
    "calendar" -> R.drawable.ic_home_widget_header_calendar
    else -> fallback
}
