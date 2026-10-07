package io.legado.app.ui.main.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.design.theme.NgTheme

/** Each module owns its content; the home grid only owns layout and editing. */
internal data class HomeWidgetContent(
    val typeId: String,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val render: @Composable (HomeWidgetVariant, HomeWidgetContentContext) -> Unit,
)

internal data class HomeWidgetContentContext(
    val editing: Boolean,
    val listening: HomeListeningState,
    val onListeningAction: (HomeListeningState, HomeListeningAction) -> Unit,
    val onEdit: () -> Unit,
    val interactive: Boolean = true,
    val reading: HomeReadingState = HomeReadingState(),
    val measuring: Boolean = false,
    val bodyMinimumHeight: Dp = 0.dp,
    val calendar: HomeCalendarState = HomeCalendarState(),
    val calendarSelection: HomeCalendarSelection = HomeCalendarSelection(java.time.YearMonth.from(calendar.today)),
    val onCalendarAction: (HomeCalendarAction) -> Unit = {},
    val updates: HomeUpdatesState = HomeUpdatesState(),
    val onOpenUpdatesBook: (HomeUpdateBook) -> Unit = {},
    val onRetryUpdates: () -> Unit = {},
)

internal object HomeWidgetContents {
    val entries = listOf(
        HomeWidgetContent("reading", R.string.home_widget_reading, R.drawable.ic_history) { variant, context ->
            // Measure the selected skin's geometry; only image rendering is skipped.
            HomeReadingWidget(variant, context.reading, context.bodyMinimumHeight,
                measuring = context.measuring, interactive = context.interactive && !context.editing)
        },
        HomeWidgetContent("listening", R.string.home_widget_listening, R.drawable.ic_ai_capability_tts) { variant, context ->
            HomeListeningWidget(variant, context)
        },
        HomeWidgetContent("updates", R.string.home_widget_updates, R.drawable.ic_bookshelf_dock_novel) { variant, context ->
            HomeUpdatesWidget(variant, context.updates, context.bodyMinimumHeight,
                interactive = context.interactive && !context.editing && !context.measuring,
                measuring = context.measuring, onOpenBook = context.onOpenUpdatesBook,
                onRetry = context.onRetryUpdates, onEdit = context.onEdit)
        },
        HomeWidgetContent("calendar", R.string.home_widget_calendar, R.drawable.ic_home_calendar) { variant, context ->
            HomeCalendarWidget(variant, context.calendar, context.calendarSelection, context.onCalendarAction,
                interactive = context.interactive && !context.editing && !context.measuring,
                bodyMinimumHeight = context.bodyMinimumHeight, measuring = context.measuring)
        },
    )

    fun find(typeId: String): HomeWidgetContent = entries.first { it.typeId == typeId }

    private fun placeholder(
        id: String,
        @StringRes titleRes: Int,
        @DrawableRes iconRes: Int,
    ) = HomeWidgetContent(id, titleRes, iconRes) { variant, _ ->
        HomeWidgetPlaceholder(iconRes, variant.size)
    }
}

@Composable
private fun HomeWidgetPlaceholder(@DrawableRes iconRes: Int, size: HomeWidgetSize) {
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(
            min = size.standardHeight() - HomeWidgetSkinHeaderHeight,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color(NgTheme.colors.onSurfaceVariant).copy(alpha = 0.45f),
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringResource(R.string.home_widget_placeholder),
            color = Color(NgTheme.colors.onSurfaceVariant),
            fontSize = 13.sp,
        )
    }
}
