package io.legado.app.ui.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun formatHomeReadingLastRead(lastRead: Long, locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()): String =
    if (lastRead > 0L) SimpleDateFormat("yyyy-MM-dd HH:mm", locale).apply {
        this.timeZone = timeZone
    }.format(Date(lastRead)) else "—"

/** Shared reading content; the parent owns the glass surface, header and navigation. */
@Composable
internal fun HomeReadingWidget(
    variant: HomeWidgetVariant,
    state: HomeReadingState,
    bodyMinimumHeight: Dp = 0.dp,
    measuring: Boolean = false,
    interactive: Boolean = true,
) {
    CompositionLocalProvider(LocalHomeReadingPalette provides homeReadingAppearance(variant)?.palette) {
        HomeReadingContent(variant, state, bodyMinimumHeight, measuring, interactive)
    }
}

private val LocalHomeReadingPalette = staticCompositionLocalOf<HomeListeningPalette?> { null }

@Composable
private fun homeReadingForeground() = LocalHomeReadingPalette.current?.foreground ?: Color(NgTheme.colors.onSurface)

@Composable
private fun homeReadingSecondary() = LocalHomeReadingPalette.current?.secondary ?: Color(NgTheme.colors.onSurfaceVariant)

@Composable
private fun homeReadingPrimary() = LocalHomeReadingPalette.current?.primary ?: Color(NgTheme.colors.primary)

@Composable
private fun homeReadingDurationUnit(unit: HomeReadingDurationUnit): String = stringResource(
    if (unit == HomeReadingDurationUnit.DAYS) R.string.home_reading_days else R.string.home_reading_hours,
)

@Composable
private fun HomeReadingContent(
    variant: HomeWidgetVariant,
    state: HomeReadingState,
    bodyMinimumHeight: Dp,
    measuring: Boolean,
    interactive: Boolean,
) {
    val large = variant.size == HomeWidgetSize.LARGE
    val unavailable = !state.loaded
    val duration = formatHomeReadingDuration(if (unavailable) 0L else state.totalReadTime)
    val timeValue = if (unavailable) "—" else duration.value
    val timeUnit = homeReadingDurationUnit(duration.unit)
    val count = if (unavailable) "—" else NumberFormat.getIntegerInstance().format(state.recordCount)
    BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = bodyMinimumHeight)) {
        val horizontalPadding = if (large) 14.dp else 12.dp
        val smallArtworkWidth = (maxWidth - horizontalPadding * 2).coerceAtLeast(0.dp)
        Column(Modifier.fillMaxWidth().padding(
            horizontal = if (large) 14.dp else 12.dp,
        ).padding(bottom = if (large) 16.dp else 8.dp)) {
            if (large) {
                BoxWithConstraints(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    val horizontal = maxWidth >= 220.dp * LocalDensity.current.fontScale
                    if (horizontal) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            HomeReadingMetric(stringResource(R.string.home_reading_total_time), timeValue,
                                timeUnit, 42, Modifier.weight(1f))
                            Box(Modifier.padding(horizontal = 16.dp).width(0.5.dp).height(46.dp)
                                .background(homeReadingPrimary().copy(alpha = 0.22f)))
                            HomeReadingRecordedMetric(variant, measuring,
                                stringResource(R.string.home_reading_recorded), count,
                                stringResource(R.string.home_reading_books), Modifier.weight(1f))
                        }
                    } else {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            HomeReadingMetric(stringResource(R.string.home_reading_total_time), timeValue,
                                timeUnit, 42)
                            HomeReadingRecordedMetric(variant, measuring,
                                stringResource(R.string.home_reading_recorded), count,
                                stringResource(R.string.home_reading_books))
                        }
                    }
                }
                HomeReadingSeparator()
                HomeReadingRecent(state, interactive && !measuring)
            } else {
                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    HomeReadingMetric(stringResource(R.string.home_reading_total_time), timeValue,
                        timeUnit, 38)
                }
                HomeReadingSeparator()
                Box(Modifier.fillMaxWidth().heightIn(min = 72.dp)) {
                    HomeReadingMetric(stringResource(R.string.home_reading_recorded), count,
                        stringResource(R.string.home_reading_books), 36)
                }
                if (unavailable && state.loadFailed) {
                    Text(stringResource(R.string.home_reading_load_failed),
                        color = homeReadingSecondary(), fontSize = 12.sp)
                }
                // Only illustrated skins need a footer; plain glass ends after its metrics.
                if (variant.styleId != "basic") Spacer(Modifier.height(72.dp))
            }
        }
        if (!measuring && !large) {
            // Match the actual shared body, not the shorter natural content measured by the probe.
            Box(Modifier.matchParentSize().padding(
                horizontal = horizontalPadding,
            ).padding(bottom = 8.dp)) {
                HomeReadingArtwork(variant, Modifier.align(Alignment.BottomEnd)
                    .width(smallArtworkWidth).height(72.dp)
                    .padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun HomeReadingRecent(state: HomeReadingState, interactive: Boolean) {
    val unavailable = !state.loaded
    Box(Modifier.fillMaxWidth().heightIn(min = 80.dp)) {
        Column(Modifier.fillMaxWidth()) {
            if (unavailable || state.recentRecords.isEmpty()) {
                Text(stringResource(when {
                    unavailable && state.loadFailed -> R.string.home_reading_load_failed
                    unavailable -> R.string.home_reading_loading
                    else -> R.string.home_reading_no_records
                }), color = homeReadingSecondary(), fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 6.dp))
            } else {
                HomeReadingRecentTable(state.recentRecords.take(3), interactive)
            }
        }
    }
}

@Composable
private fun HomeReadingSeparator() {
    Box(Modifier.fillMaxWidth().height(8.dp), contentAlignment = Alignment.Center) {
        HomeReadingDivider()
    }
}

@Composable
private fun HomeReadingRecentTable(records: List<HomeReadingRecord>, interactive: Boolean) {
    val locale = Locale.getDefault()
    val timeZone = TimeZone.getDefault()
    val lastReads = remember(records, locale, timeZone) {
        records.map { formatHomeReadingLastRead(it.lastRead, locale, timeZone) }
    }
    val durations = records.map {
        val duration = formatHomeReadingDuration(it.readTime)
        stringResource(R.string.home_reading_duration, duration.value, homeReadingDurationUnit(duration.unit))
    }
    val measurer = rememberTextMeasurer(cacheSize = 24)
    val baseStyle = LocalTextStyle.current
    val bookStyle = baseStyle.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
    val durationStyle = baseStyle.copy(fontSize = 12.sp, lineHeight = 18.sp)
    val timeStyle = baseStyle.copy(fontSize = 11.sp, lineHeight = 18.sp)
    val density = LocalDensity.current
    val foreground = homeReadingForeground()
    val secondary = homeReadingSecondary()
    val scrollState = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        fun textWidth(text: String, style: TextStyle) =
            measurer.measure(text, style, softWrap = false, maxLines = 1).size.width
        val gapPx = with(density) { 8.dp.roundToPx() }
        val roundingPx = with(density) { 2.dp.roundToPx() }
        val durationPx = durations.maxOf { textWidth(it, durationStyle) } + roundingPx
        val timePx = lastReads.maxOf { textWidth(it, timeStyle) } + roundingPx
        val bookPx = with(density) {
            maxOf((64.dp * fontScale).roundToPx(), minOf((120.dp * fontScale).roundToPx(),
                records.maxOf { textWidth(it.bookName, bookStyle) }))
        }
        val tablePx = maxOf(with(density) { maxWidth.roundToPx() }, bookPx + durationPx + timePx + gapPx * 2)
        val tableWidth = with(density) { tablePx.toDp() }
        val durationWidth = with(density) { durationPx.toDp() }
        val timeWidth = with(density) { timePx.toDp() }
        // Keep identical unbounded measurement in live cards, inert previews and probes.
        Column(Modifier.fillMaxWidth().horizontalScroll(scrollState, enabled = interactive).width(tableWidth)) {
            records.forEachIndexed { index, record ->
                if (index > 0) HomeReadingDivider(alpha = 0.10f)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(record.bookName, style = bookStyle, color = foreground,
                        modifier = Modifier.weight(1f).alignByBaseline(), maxLines = 1,
                        softWrap = false, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.width(8.dp))
                    Text(durations[index], style = durationStyle, color = foreground, textAlign = TextAlign.End,
                        modifier = Modifier.width(durationWidth).alignByBaseline(), maxLines = 1, softWrap = false)
                    Spacer(Modifier.width(8.dp))
                    Text(lastReads[index], style = timeStyle, color = secondary, textAlign = TextAlign.End,
                        modifier = Modifier.width(timeWidth).alignByBaseline(), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

@Composable
private fun HomeReadingDivider(alpha: Float = 0.18f) {
    Box(Modifier.fillMaxWidth().height(0.5.dp)
        .background(homeReadingPrimary().copy(alpha = alpha)))
}

@Composable
private fun HomeReadingRecordedMetric(variant: HomeWidgetVariant, measuring: Boolean,
    label: String, value: String, unit: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth()) {
        HomeReadingMetric(label, value, unit, 32)
        if (!measuring && (variant.styleId == "storybook" || variant.styleId == "night")) {
            // The original metric alone determines the height; decoration only uses its spare width.
            BoxWithConstraints(Modifier.matchParentSize().clipToBounds()) {
                val measurer = rememberTextMeasurer()
                val density = LocalDensity.current
                val baseStyle = LocalTextStyle.current
                val widthPx = with(density) { maxWidth.roundToPx() }
                val labelWidth = measurer.measure(label,
                    baseStyle.copy(fontSize = 12.sp, lineHeight = 16.sp), softWrap = false).size.width
                val valueWidth = measurer.measure(value,
                    baseStyle.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold), softWrap = false).size.width
                val unitWidth = measurer.measure(unit,
                    baseStyle.copy(fontSize = 12.sp), softWrap = false).size.width
                val inlineWidth = valueWidth + with(density) { 6.dp.roundToPx() } + unitWidth
                val contentWidth = maxOf(labelWidth,
                    if (inlineWidth <= widthPx) inlineWidth else maxOf(valueWidth, unitWidth))
                val artworkWidth = with(density) {
                    (widthPx - contentWidth - 8.dp.roundToPx()).coerceAtLeast(0).toDp()
                }
                if (artworkWidth >= 24.dp) {
                    HomeReadingArtwork(variant, Modifier.align(Alignment.BottomEnd)
                        .width(artworkWidth).height(minOf(maxHeight, 80.dp)).clipToBounds())
                }
            }
        }
    }
}

@Composable
private fun HomeReadingMetric(label: String, value: String, unit: String, size: Int,
    modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Text(label, color = homeReadingSecondary(), fontSize = 12.sp, lineHeight = 16.sp)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val baseStyle = LocalTextStyle.current
            val widthPx = with(density) { maxWidth.roundToPx() }
            val unitWidth = measurer.measure(unit, baseStyle.copy(fontSize = 12.sp), softWrap = false).size.width
            val gapPx = with(density) { 6.dp.roundToPx() }
            // Preserve the user's font size; move the unit below rather than shrink the number.
            val inline = measurer.measure(value, baseStyle.copy(fontSize = size.sp,
                fontWeight = FontWeight.Bold), softWrap = false).size.width + unitWidth + gapPx <= widthPx
            if (inline) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(value, color = homeReadingPrimary(), fontWeight = FontWeight.Bold,
                        fontSize = size.sp, lineHeight = (size + 8).sp, modifier = Modifier.alignByBaseline())
                    Spacer(Modifier.width(6.dp))
                    Text(unit, color = homeReadingForeground(), fontSize = 12.sp,
                        modifier = Modifier.alignByBaseline())
                }
            } else {
                Column {
                    Text(value, color = homeReadingPrimary(), fontWeight = FontWeight.Bold,
                        fontSize = size.sp, lineHeight = (size + 8).sp)
                    Text(unit, color = homeReadingForeground(), fontSize = 12.sp)
                }
            }
        }
    }
}
