package io.legado.app.ui.book.read

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.book.bookmark.AllBookmarkFilter
import io.legado.app.ui.book.bookmark.BookmarkNoteFilter
import io.legado.app.ui.design.components.compose.ngDrawerContentCardColor
import io.legado.app.ui.design.theme.NgTheme
import java.util.Locale

/** Compact inline tools; filtering stays in AllBookmarkFilter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CatalogBookmarkFilterPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: AllBookmarkFilter,
    colors: List<Int>,
    onFilterChanged: (AllBookmarkFilter) -> Unit,
    onClearConditions: () -> Unit,
    autoExpandNotes: Boolean,
    onAutoExpandNotesChanged: (Boolean) -> Unit,
    extraActions: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val primary = Color(NgTheme.colors.primary)
    val muted = Color(NgTheme.colors.onSurfaceVariant)
    val cardColor = ngDrawerContentCardColor()
    CatalogToolsPanel(query = query, onQueryChange = onQueryChange,
        hint = stringResource(R.string.read_catalog_search_bookmarks), modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(top = 7.dp)
            .clip(RoundedCornerShape(14.dp)).background(cardColor)
            .padding(horizontal = 10.dp, vertical = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.bookmark_note), color = muted, fontSize = 11.sp,
                    modifier = Modifier.width(28.dp))
                Spacer(Modifier.width(6.dp))
                FlowRow(Modifier.weight(1f).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BookmarkNoteFilter.entries.forEach { option ->
                        CatalogBookmarkFilterChip(
                            label = stringResource(when (option) {
                                BookmarkNoteFilter.ALL -> R.string.all
                                BookmarkNoteFilter.WITH_NOTE -> R.string.all_bookmark_filter_with_note
                                BookmarkNoteFilter.WITHOUT_NOTE -> R.string.all_bookmark_filter_without_note
                            }),
                            selected = filter.noteFilter == option,
                            onClick = { onFilterChanged(filter.copy(noteFilter = option)) },
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(top = 4.dp), thickness = 0.5.dp,
                color = muted.copy(alpha = 0.12f))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.all_bookmark_filter_color), color = muted, fontSize = 11.sp,
                    modifier = Modifier.width(28.dp))
                Spacer(Modifier.width(6.dp))
                FlowRow(Modifier.weight(1f).selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    CatalogBookmarkFilterChip(stringResource(R.string.all), filter.color == null,
                        modifier = Modifier.width(44.dp).align(Alignment.CenterVertically)) {
                        onFilterChanged(filter.copy(color = null))
                    }
                    colors.forEach { argb ->
                        val selected = filter.color == argb
                        val swatch = Color(argb)
                        val description = stringResource(R.string.all_bookmark_filter_color_value,
                            String.format(Locale.ROOT, "#%08X", argb))
                        Box(Modifier.size(44.dp).align(Alignment.CenterVertically).clip(RoundedCornerShape(8.dp))
                            .selectable(selected, role = Role.RadioButton) { onFilterChanged(filter.copy(color = argb)) }
                            .semantics { contentDescription = description },
                            contentAlignment = Alignment.Center) {
                            Box(Modifier.size(29.dp)
                                .then(if (selected) Modifier.border(1.dp, primary, CircleShape) else Modifier)
                                .padding(4.dp).background(swatch, CircleShape)
                                .border(0.5.dp, muted.copy(alpha = 0.15f), CircleShape))
                        }
                    }
                }
                CatalogToolSwitch(stringResource(R.string.bookmark_auto_expand_notes),
                    autoExpandNotes, compact = true, onCheckedChange = onAutoExpandNotesChanged)
            }
        }
        extraActions()
        if (filter.isActive || query.isNotBlank()) {
            Text(stringResource(R.string.all_bookmark_filter_clear_conditions), color = primary, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
                    .clip(RoundedCornerShape(10.dp)).clickable(onClick = onClearConditions)
                    .padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun CatalogBookmarkFilterChip(label: String, selected: Boolean,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Keep the touch target separate from the compact selection background.
    // An explicit line height keeps the text chip aligned with the 44dp swatch targets.
    Box(modifier.widthIn(min = 44.dp).heightIn(min = 44.dp).clip(RoundedCornerShape(9.dp))
        .selectable(selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(min = 40.dp).heightIn(min = 32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) catalogFilterSelectedColor() else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color(if (selected) NgTheme.colors.primary else NgTheme.colors.onSurfaceVariant),
                fontSize = 11.sp, lineHeight = 16.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}
