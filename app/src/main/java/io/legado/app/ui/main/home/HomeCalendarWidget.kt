package io.legado.app.ui.main.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.os.ConfigurationCompat
import io.legado.app.R
import io.legado.app.ui.design.components.compose.NgDialog
import io.legado.app.ui.design.components.compose.NgDialogTextActionButton
import io.legado.app.ui.design.theme.NgTheme
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Content only: the shared home card owns the skin surface and common header. */
@Composable
internal fun HomeCalendarWidget(
    variant: HomeWidgetVariant,
    state: HomeCalendarState,
    selection: HomeCalendarSelection,
    onAction: (HomeCalendarAction) -> Unit,
    interactive: Boolean,
    bodyMinimumHeight: Dp,
    measuring: Boolean = false,
) {
    var chooseMonth by rememberSaveable { mutableStateOf(false) }
    val weeks = remember(selection.month) { homeCalendarWeeks(selection.month) }
    val summary = remember(state, selection.month) { state.summary(selection.month) }
    val palette = homeWidgetSkinPalette(variant.styleId)
    val primary = palette?.primary ?: Color(NgTheme.colors.primary)
    val secondary = palette?.secondary ?: Color(NgTheme.colors.onSurfaceVariant)
    val dayTextStyle = LocalTextStyle.current.copy(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp)
    val durationTextStyle = LocalTextStyle.current.copy(fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.sp)
    val weekTextStyle = LocalTextStyle.current.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val dateSlotSize = remember(textMeasurer, dayTextStyle, density) {
        val labels = (1..31).map { textMeasurer.measure(it.toString(),
            dayTextStyle.copy(fontWeight = FontWeight.SemiBold), maxLines = 1).size }
        // The circle only encloses the numeral; the whole date cell owns the touch target.
        with(density) { maxOf(24.dp, labels.maxOf { it.width }.toDp() + 4.dp,
            labels.maxOf { it.height }.toDp() + 2.dp) }
    }
    val durations = weeks.flatten().filterNotNull().associateWith {
        if (it > state.today) "" else homeCalendarDuration(state.readTime(it))
    }
    // Android's desugared NARROW weekday names can reduce every Chinese day to “星”.
    val weekdays = stringArrayResource(R.array.home_calendar_weekdays).toList()
    val minimumCellWidth = remember(textMeasurer, durationTextStyle, weekTextStyle,
        durations, weekdays, dateSlotSize, density) {
        with(density) {
            maxOf(dateSlotSize + 4.dp,
                durations.values.maxOf { textMeasurer.measure(it, durationTextStyle, maxLines = 1).size.width }.toDp() + 4.dp,
                weekdays.maxOf { textMeasurer.measure(it, weekTextStyle, maxLines = 1).size.width }.toDp() + 4.dp)
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(min = bodyMinimumHeight)
        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)) {
        HomeCalendarNavigation(selection.month, interactive,
            onChooseMonth = { chooseMonth = true }, onAction = onAction, palette = palette)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gridWidth = maxOf(maxWidth, minimumCellWidth * 7)
            if ((variant.styleId == "storybook" || variant.styleId == "night") && !measuring) {
                val weekdayHeight = remember(textMeasurer, weekdays, weekTextStyle, density) {
                    with(density) {
                        weekdays.maxOf {
                            textMeasurer.measure(it, weekTextStyle, maxLines = 1).size.height
                        }.toDp() + 4.dp
                    }
                }
                HomeCalendarGridArtwork(gridWidth, weekdayHeight, Modifier.matchParentSize(), variant.styleId)
            }
            Row(Modifier.horizontalScroll(rememberScrollState(), enabled = interactive)) {
                Column(Modifier.width(gridWidth)) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        weekdays.forEachIndexed { index, label ->
                            Text(label, Modifier.weight(1f),
                                color = if (index >= 5) primary else secondary,
                                style = weekTextStyle, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                    weeks.forEach { week ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            week.forEach { date ->
                                if (date == null) Spacer(Modifier.weight(1f)) else HomeCalendarDay(
                                    date, state, date == selection.selectedDate, interactive,
                                    onSelect = { onAction(HomeCalendarAction.SelectDate(date)) },
                                    modifier = Modifier.weight(1f), dateSlotSize = dateSlotSize,
                                    duration = durations.getValue(date),
                                    dayTextStyle = dayTextStyle, durationTextStyle = durationTextStyle,
                                    palette = palette, night = variant.styleId == "night",
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(primary.copy(alpha = 0.22f)))
        // Keep the approved single-line summary; very large fonts can pan without losing text.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val availableWidth = maxWidth
            Row(Modifier.horizontalScroll(rememberScrollState(), enabled = interactive)) {
                Row(Modifier.widthIn(min = availableWidth), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.padding(end = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(if (state.loadFailed) R.string.home_calendar_retry else R.string.home_calendar_month_total),
                            modifier = if (state.loadFailed) Modifier.then(if (interactive)
                                Modifier.clickable(role = Role.Button) { onAction(HomeCalendarAction.Retry) }
                                else Modifier).padding(vertical = 14.dp) else Modifier,
                            color = if (state.loadFailed) palette?.error ?: Color(NgTheme.colors.error)
                                else palette?.foreground ?: Color(NgTheme.colors.onSurface),
                            fontSize = 13.sp, lineHeight = 20.sp, maxLines = 1)
                        Spacer(Modifier.width(8.dp))
                        Text(homeCalendarDuration(summary?.readTime, compact = false), color = primary,
                            fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                    val days = summary?.readingDays?.toString() ?: "—"
                    val daysLabel = stringResource(R.string.home_calendar_reading_days, days)
                    Text(buildAnnotatedString {
                        append(daysLabel)
                        if (variant.styleId == "night") {
                            val daysStart = daysLabel.indexOf(days)
                            if (daysStart >= 0) addStyle(SpanStyle(color = primary), daysStart, daysLabel.length)
                        }
                    }, color = if (variant.styleId == "night") secondary else primary,
                        fontSize = 13.sp, lineHeight = 20.sp, maxLines = 1)
                }
            }
        }
    }
    // Measurement, editing and thumbnail compositions never show dialogs or fire actions.
    if (interactive && chooseMonth) HomeCalendarMonthDialog(selection.month,
        onDismiss = { chooseMonth = false }, onChoose = {
            onAction(HomeCalendarAction.ShowMonth(it))
            chooseMonth = false
        })
}

@Composable
private fun HomeCalendarNavigation(
    month: YearMonth,
    interactive: Boolean,
    onChooseMonth: () -> Unit,
    onAction: (HomeCalendarAction) -> Unit,
    palette: HomeListeningPalette?,
) {
    val foreground = palette?.foreground ?: Color(NgTheme.colors.onSurface)
    val label = stringResource(R.string.home_calendar_month, month.year, month.monthValue)
    val today = stringResource(R.string.home_calendar_today)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val textStyle = LocalTextStyle.current
    val monthWidth = with(density) { measurer.measure(label, textStyle.merge(TextStyle(fontSize = 15.sp,
        lineHeight = 20.sp, fontWeight = FontWeight.Medium)), maxLines = 1).size.width.toDp() } + 28.dp
    val todayWidth = maxOf(44.dp, with(density) {
        measurer.measure(today, textStyle.merge(TextStyle(fontSize = 14.sp)), maxLines = 1).size.width.toDp()
    } + 8.dp)
    val monthButton: @Composable () -> Unit = {
        Row(Modifier.heightIn(min = 44.dp).then(if (interactive) Modifier.clickable(
            onClickLabel = stringResource(R.string.home_calendar_choose_month),
            role = Role.Button, onClick = onChooseMonth) else Modifier),
            verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = foreground, fontSize = 15.sp,
                lineHeight = 20.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = foreground,
                modifier = Modifier.size(22.dp))
        }
    }
    val controls: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HomeCalendarArrow(false, stringResource(R.string.home_calendar_previous_month),
                enabled = month > YearMonth.of(1, 1), interactive = interactive, touchSize = 44.dp,
                palette = palette) {
                onAction(HomeCalendarAction.ShowMonth(month.minusMonths(1)))
            }
            Box(Modifier.width(todayWidth).heightIn(min = 44.dp).then(if (interactive)
                Modifier.clickable(role = Role.Button) { onAction(HomeCalendarAction.Today) } else Modifier),
                contentAlignment = Alignment.Center) {
                Text(today, color = foreground, fontSize = 14.sp, lineHeight = 20.sp)
            }
            HomeCalendarArrow(true, stringResource(R.string.home_calendar_next_month),
                enabled = month < YearMonth.of(9999, 12), interactive = interactive, touchSize = 44.dp,
                palette = palette) {
                onAction(HomeCalendarAction.ShowMonth(month.plusMonths(1)))
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= monthWidth + todayWidth + 88.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                monthButton()
                controls()
            }
        } else {
            Column(Modifier.fillMaxWidth()) {
                monthButton()
                Box(Modifier.align(Alignment.End)) { controls() }
            }
        }
    }
}

@Composable
private fun HomeCalendarArrow(next: Boolean, label: String, enabled: Boolean,
    interactive: Boolean = true, touchSize: Dp = 48.dp, palette: HomeListeningPalette? = null,
    onClick: () -> Unit) {
    Box(Modifier.size(touchSize).then(if (enabled && interactive) Modifier.clickable(role = Role.Button, onClick = onClick)
        else Modifier), contentAlignment = Alignment.Center) {
        Icon(if (next) Icons.AutoMirrored.Outlined.KeyboardArrowRight else Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            contentDescription = label,
            tint = (palette?.foreground ?: Color(NgTheme.colors.onSurface)).copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun HomeCalendarDay(
    date: LocalDate,
    state: HomeCalendarState,
    isSelected: Boolean,
    interactive: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier,
    dateSlotSize: Dp,
    duration: String,
    dayTextStyle: TextStyle,
    durationTextStyle: TextStyle,
    palette: HomeListeningPalette?,
    night: Boolean,
) {
    val primary = palette?.primary ?: Color(NgTheme.colors.primary)
    val secondary = palette?.secondary ?: Color(NgTheme.colors.onSurfaceVariant)
    val today = date == state.today
    val future = date > state.today
    val readTime = state.readTime(date)
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val dateLabel = remember(date, locale) {
        date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale))
    }
    val recordLabel = if (future) stringResource(R.string.home_calendar_future)
        else if (readTime == null) stringResource(R.string.home_calendar_unknown) else duration
    val description = dateLabel + (if (today) " ${stringResource(R.string.home_calendar_today)}" else "") + ", " + recordLabel
    Column(modifier.heightIn(min = 44.dp).then(if (interactive) Modifier.clickable(
        role = Role.Button, onClick = onSelect) else Modifier).semantics(mergeDescendants = true) {
            contentDescription = description
            selected = isSelected
            if (interactive) role = Role.Button
        }.padding(bottom = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(dateSlotSize).clip(CircleShape).then(if (today) Modifier.background(primary) else Modifier)
            .then(if (isSelected && !today) Modifier.border(1.dp, primary, CircleShape) else Modifier),
            contentAlignment = Alignment.Center) {
            Text(date.dayOfMonth.toString(), modifier = Modifier.clearAndSetSemantics {},
                color = if (today) palette?.primaryContent ?: Color.White else if (night || future) secondary
                    else palette?.foreground ?: Color(NgTheme.colors.onSurface),
                style = dayTextStyle, maxLines = 1,
                fontWeight = if (today) FontWeight.SemiBold else FontWeight.Normal)
        }
        Text(duration, Modifier.fillMaxWidth().clearAndSetSemantics {},
            color = if (readTime != null && readTime > 0) primary else secondary,
            style = durationTextStyle, maxLines = 1, textAlign = TextAlign.Center)
    }
}

@Composable
private fun homeCalendarDuration(readTime: Long?, compact: Boolean = true): String {
    val minutes = homeCalendarMinutes(readTime) ?: return "—"
    return when {
        readTime != null && readTime > 0 && minutes == 0L -> stringResource(R.string.home_calendar_under_minute)
        !compact && minutes >= 60 -> stringResource(R.string.home_calendar_hours_minutes, minutes / 60, minutes % 60)
        else -> stringResource(R.string.home_calendar_minutes, minutes)
    }
}

@Composable
private fun HomeCalendarMonthDialog(month: YearMonth, onDismiss: () -> Unit, onChoose: (YearMonth) -> Unit) {
    var year by rememberSaveable(month) { mutableIntStateOf(month.year) }
    var monthValue by rememberSaveable(month) { mutableIntStateOf(month.monthValue) }
    val maximumContentHeight = (LocalConfiguration.current.screenHeightDp * 0.6f).dp
    Dialog(onDismissRequest = onDismiss) {
        NgDialog(title = stringResource(R.string.home_calendar_choose_month), actions = {
            NgDialogTextActionButton(stringResource(R.string.home_cancel), onDismiss)
            NgDialogTextActionButton(stringResource(R.string.confirm), { onChoose(YearMonth.of(year, monthValue)) })
        }) {
            Column(Modifier.fillMaxWidth().heightIn(max = maximumContentHeight).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    HomeCalendarArrow(false, stringResource(R.string.home_calendar_previous_year), year > 1) { year-- }
                    Text(year.toString(), Modifier.weight(1f), textAlign = TextAlign.Center,
                        color = Color(NgTheme.colors.onSurface), fontSize = 18.sp, lineHeight = 24.sp)
                    HomeCalendarArrow(true, stringResource(R.string.home_calendar_next_year), year < 9999) { year++ }
                }
                Column(Modifier.selectableGroup()) {
                    (1..12).chunked(3).forEach { months ->
                        Row(Modifier.fillMaxWidth()) {
                            months.forEach { value ->
                                Box(Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (value == monthValue) Color(NgTheme.colors.primary).copy(alpha = 0.1f)
                                        else Color.Transparent)
                                    .selectable(value == monthValue, role = Role.RadioButton) { monthValue = value }
                                    .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.home_calendar_month_number, value),
                                        color = if (value == monthValue) Color(NgTheme.colors.primary) else Color(NgTheme.colors.onSurface),
                                        fontSize = 16.sp, lineHeight = 22.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
